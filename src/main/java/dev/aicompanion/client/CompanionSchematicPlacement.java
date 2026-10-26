package dev.aicompanion.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.schematic.CompanionSchematic;
import dev.aicompanion.schematic.CompanionSchematics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * «Построй дом» as Litematica places a schematic: the building appears as a see-through ghost where the player looks, and is
 * turned, mirrored and nudged with keys until Enter sets it down for Oleg. While he builds, what is still missing stays visible.
 * Keys work only while a placement is open; all of them can be changed in Controls → «ИИ-компаньон».
 */
public final class CompanionSchematicPlacement {
    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.aicompanion.schematic_" + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, "key.categories.aicompanion");
    }
    private static final KeyMapping CONFIRM = key("confirm", GLFW.GLFW_KEY_ENTER), CANCEL = key("cancel", GLFW.GLFW_KEY_BACKSPACE),
            ROTATE = key("rotate", GLFW.GLFW_KEY_R), MIRROR = key("mirror", GLFW.GLFW_KEY_M),
            NEXT = key("next", GLFW.GLFW_KEY_RIGHT_BRACKET), PREVIOUS = key("previous", GLFW.GLFW_KEY_LEFT_BRACKET),
            FORWARD = key("forward", GLFW.GLFW_KEY_UP), BACK = key("back", GLFW.GLFW_KEY_DOWN), LEFT = key("left", GLFW.GLFW_KEY_LEFT),
            RIGHT = key("right", GLFW.GLFW_KEY_RIGHT), RAISE = key("up", GLFW.GLFW_KEY_PAGE_UP), LOWER = key("down", GLFW.GLFW_KEY_PAGE_DOWN),
            FOLLOW = key("follow", GLFW.GLFW_KEY_H);
    static final List<KeyMapping> KEYS = List.of(CONFIRM, CANCEL, ROTATE, MIRROR, NEXT, PREVIOUS, FORWARD, BACK, LEFT, RIGHT, RAISE, LOWER, FOLLOW);
    private static final double LOOK_DISTANCE = 64;
    private static final float GHOST_ALPHA = .5F, BUILD_ALPHA = .35F;

    private static Session session;
    /** The house Oleg is building now (read from the integrated server), shown with only its missing blocks. */
    private static final CompanionSchematicGhost building = new CompanionSchematicGhost();
    private static CompanionSchematic.Placement shown;
    private static int buildingTicks;

    private CompanionSchematicPlacement() { }

    private static final class Session {
        final List<String> files;
        final CompletableFuture<CompanionServerPlanPort.Choice> result;
        final BooleanSupplier cancelled;
        final CompanionSchematicGhost ghost = new CompanionSchematicGhost();
        int index;
        CompanionSchematic schematic;
        Rotation rotation = Rotation.NONE;
        Mirror mirror = Mirror.NONE;
        BlockPos origin = BlockPos.ZERO;
        /** Nudged by keys: stays put; otherwise follows the crosshair. */
        boolean locked;
        /** The last block the crosshair rested on: looking at the sky leaves the house there, not over the player's head. */
        BlockPos spot;
        int hintTicks;
        Session(List<String> files, CompletableFuture<CompanionServerPlanPort.Choice> result, BooleanSupplier cancelled) {
            this.files = files; this.result = result; this.cancelled = cancelled;
        }
        CompanionSchematic.Placement placement() { return new CompanionSchematic.Placement(files.get(index), origin, rotation, mirror); }
        void bake() { ghost.bake(schematic, placement(), pos -> false); }
        void finish(CompanionServerPlanPort.Choice choice) {
            ghost.close();
            if (session == this) session = null;
            result.complete(choice);
        }
    }

    /** A placement is open: the ghost follows the player's look. */
    public static boolean placing() { return session != null; }
    /** The ghost of the house being built is shown. */
    public static boolean showingBuild() { return shown != null; }

    /** The port asks where the house goes; answered when the player presses Enter or Backspace. */
    static CompletableFuture<CompanionServerPlanPort.Choice> choose(String hint, CompanionSchematic.Placement current, BooleanSupplier cancelled) {
        CompletableFuture<CompanionServerPlanPort.Choice> result = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> start(hint, current, cancelled, result));
        return result;
    }

    private static void start(String hint, CompanionSchematic.Placement current, BooleanSupplier cancelled, CompletableFuture<CompanionServerPlanPort.Choice> result) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) { result.complete(new CompanionServerPlanPort.Choice(null, "Мир закрыт, место для дома не выбрано.")); return; }
        if (session != null) session.finish(new CompanionServerPlanPort.Choice(null, "Выбор места отменён: началась другая стройка."));
        List<String> files = CompanionSchematics.list();
        // An empty folder is no refusal: the mod's own small house, placed like any schematic.
        boolean builtIn = files.isEmpty();
        if (builtIn) files = List.of(CompanionSchematics.DEFAULT_HOUSE);
        Session opened = new Session(files, result, cancelled);
        String named = CompanionSchematics.match(files, hint);
        int first = named != null ? files.indexOf(named) : current != null && files.contains(current.file()) ? files.indexOf(current.file()) : 0;
        String error = null;
        for (int tried = 0; tried < files.size(); tried++) {
            int index = (first + tried) % files.size();
            try { opened.schematic = CompanionSchematics.load(files.get(index)); opened.index = index; error = null; break; }
            catch (IOException broken) { if (error == null) error = broken.getMessage(); }
        }
        if (opened.schematic == null) { result.complete(new CompanionServerPlanPort.Choice(null, "Ни одна схема из папки " + CompanionSchematics.FOLDER + " не читается: " + error + ".")); return; }
        if (current != null && current.file().equals(files.get(opened.index))) {
            // The saved house is offered where it stands: Enter continues it, moving it starts another.
            opened.rotation = current.rotation(); opened.mirror = current.mirror(); opened.origin = current.origin(); opened.locked = true;
        } else follow(opened);
        session = opened;
        opened.bake();
        String missing = hint != null && !hint.isBlank() && !"resume".equals(hint) && named == null ? "Схему «" + hint + "» не нашёл, показываю «" + opened.schematic.title() + "». " : "";
        message((builtIn ? CompanionSchematics.noneFound() + " " : "") + missing + "Выбери место для «" + opened.schematic.describe() + "»" + (files.size() > 1 ? " (схем в папке: " + files.size() + ")" : "") + ". "
                + keyName(CONFIRM) + " — строить здесь, " + keyName(ROTATE) + " — повернуть, " + keyName(MIRROR) + " — отразить, "
                + "клавиши " + keyName(PREVIOUS) + " и " + keyName(NEXT) + " — другая схема, стрелки, " + keyName(RAISE) + " и " + keyName(LOWER) + " — сдвинуть, "
                + keyName(FOLLOW) + " — снова за прицелом, " + keyName(CANCEL) + " — отмена.");
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Session current = session;
        if (current == null) {
            for (KeyMapping key : KEYS) while (key.consumeClick()) { }
            tickBuilding(minecraft);
            return;
        }
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()) { current.finish(new CompanionServerPlanPort.Choice(null, "Выбор места прерван.")); return; }
        if (current.cancelled.getAsBoolean()) { current.finish(new CompanionServerPlanPort.Choice(null, "План отменён.")); return; }
        boolean gameplay = minecraft.screen == null;
        if (!gameplay) { for (KeyMapping key : KEYS) while (key.consumeClick()) { } return; }
        boolean rebake = false;
        while (ROTATE.consumeClick()) { current.rotation = current.rotation.getRotated(Rotation.CLOCKWISE_90); rebake = true; }
        while (MIRROR.consumeClick()) {
            current.mirror = switch (current.mirror) { case NONE -> Mirror.LEFT_RIGHT; case LEFT_RIGHT -> Mirror.FRONT_BACK; case FRONT_BACK -> Mirror.NONE; };
            rebake = true;
        }
        int switchBy = 0;
        while (NEXT.consumeClick()) switchBy++;
        while (PREVIOUS.consumeClick()) switchBy--;
        if (switchBy != 0 && current.files.size() > 1) rebake |= switchSchematic(current, switchBy);
        Direction facing = minecraft.player.getDirection();
        BlockPos nudge = BlockPos.ZERO;
        while (FORWARD.consumeClick()) nudge = nudge.relative(facing);
        while (BACK.consumeClick()) nudge = nudge.relative(facing.getOpposite());
        while (LEFT.consumeClick()) nudge = nudge.relative(facing.getCounterClockWise());
        while (RIGHT.consumeClick()) nudge = nudge.relative(facing.getClockWise());
        while (RAISE.consumeClick()) nudge = nudge.above();
        while (LOWER.consumeClick()) nudge = nudge.below();
        if (!nudge.equals(BlockPos.ZERO)) { current.locked = true; current.origin = current.origin.offset(nudge); }
        while (FOLLOW.consumeClick()) current.locked = false;
        if (!current.locked) follow(current);
        if (rebake) current.bake();
        while (CANCEL.consumeClick()) { current.finish(new CompanionServerPlanPort.Choice(null, "Стройку отменил: место для дома не выбрано.")); message("Ладно, не строю."); return; }
        while (CONFIRM.consumeClick()) {
            CompanionSchematic.Placement placed = current.placement();
            current.finish(new CompanionServerPlanPort.Choice(placed, null));
            show(placed);
            return;
        }
        if (current.hintTicks-- <= 0) {
            current.hintTicks = 10;
            BoundingBox box = current.placement().box(current.schematic);
            minecraft.gui.setOverlayMessage(Component.literal("«" + current.schematic.title() + "» " + (current.index + 1) + "/" + current.files.size()
                    + " · " + current.placement().rotationName() + ", " + current.placement().mirrorName() + " · " + box.minX() + " " + box.minY() + " " + box.minZ()
                    + (current.locked ? " · закреплено" : "") + " · " + keyName(CONFIRM) + " — строить, " + keyName(CANCEL) + " — отмена").withStyle(ChatFormatting.AQUA), false);
        }
    }

    private static boolean switchSchematic(Session current, int by) {
        for (int tried = 1; tried <= current.files.size(); tried++) {
            int index = Math.floorMod(current.index + by * tried, current.files.size());
            try {
                current.schematic = CompanionSchematics.load(current.files.get(index));
                current.index = index;
                return true;
            } catch (IOException broken) {
                message("«" + current.files.get(index) + "» пропускаю: " + broken.getMessage() + ".");
            }
        }
        return false;
    }

    /** The bottom of the building on the block looked at, its middle under the crosshair. */
    private static void follow(Session current) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        HitResult hit = player.pick(LOOK_DISTANCE, 1F, false);
        BlockPos spot;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK)
            spot = block.getDirection() == Direction.DOWN ? block.getBlockPos().below() : block.getBlockPos().relative(block.getDirection() == Direction.UP ? Direction.UP : block.getDirection());
        else if (current.spot != null) spot = current.spot;
        else {
            // Nothing looked at yet: on the ground a little ahead of the player.
            Vec3 ahead = player.position().add(Vec3.directionFromRotation(0, player.getYRot()).scale(12));
            spot = minecraft.level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(ahead));
        }
        current.spot = spot;
        CompanionSchematic.Placement at = new CompanionSchematic.Placement(current.files.get(current.index), BlockPos.ZERO, current.rotation, current.mirror);
        BoundingBox box = at.box(current.schematic);
        int centerX = (box.minX() + box.maxX()) / 2, centerZ = (box.minZ() + box.maxZ()) / 2;
        current.origin = spot.offset(-centerX, -box.minY(), -centerZ);
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Session current = session;
        if (current != null) {
            current.ghost.draw(event, current.origin, GHOST_ALPHA);
            CompanionSchematicGhost.outline(event, current.placement().box(current.schematic), .35F, .9F, 1F);
            return;
        }
        if (shown != null) building.draw(event, shown.origin(), BUILD_ALPHA);
    }

    /** What is left of the house he builds: rebaked every few seconds from the client world, hidden once it stands. */
    private static void tickBuilding(Minecraft minecraft) {
        if (minecraft.level == null) { show(null); return; }
        if (buildingTicks-- > 0) return;
        buildingTicks = 60;
        var server = minecraft.getSingleplayerServer();
        var player = minecraft.player;
        if (server == null || player == null) { show(null); return; }
        var id = player.getUUID();
        server.execute(() -> {
            var owner = server.getPlayerList().getPlayer(id);
            CompanionEntity companion = owner == null ? null : CompanionEvents.getOwnedCompanion(owner);
            CompanionSchematic.Placement placement = companion == null ? null : companion.unfinishedHouse();
            minecraft.execute(() -> show(placement));
        });
    }

    private static void show(CompanionSchematic.Placement placement) {
        Minecraft minecraft = Minecraft.getInstance();
        if (placement == null || minecraft.level == null || minecraft.player == null
                || minecraft.player.distanceToSqr(placement.origin().getCenter()) > 160 * 160) { building.close(); shown = null; return; }
        try {
            CompanionSchematic schematic = CompanionSchematics.load(placement.file());
            var level = minecraft.level;
            building.bake(schematic, placement, pos -> {
                if (!level.isLoaded(pos)) return true;
                var actual = level.getBlockState(pos);
                return !actual.isAir() && !actual.canBeReplaced();
            });
            shown = placement;
        } catch (IOException broken) { building.close(); shown = null; }
    }

    private static void message(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        String name = dev.aicompanion.CompanionIdentity.cleanName(dev.aicompanion.OpenRouterConfig.COMPANION_NAME.get());
        minecraft.gui.getChat().addMessage(Component.literal(name + ": " + text).withStyle(ChatFormatting.AQUA));
    }

    private static String keyName(KeyMapping key) { return key.getTranslatedKeyMessage().getString(); }
}

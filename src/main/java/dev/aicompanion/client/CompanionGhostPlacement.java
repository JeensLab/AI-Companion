package dev.aicompanion.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aicompanion.CompanionIdentity;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionBuildPlacement;
import dev.aicompanion.CompanionDiagnostics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Where a building goes: «построй <постройку>» said or typed opens the window of buildings (CompanionBuildingsScreen), and so does an
 * order from the model that waits for the player's placement (asked of the integrated server twice a second). The window's «Строить»
 * opens a see-through ghost that follows the crosshair on the ground. The ghost is no order: whatever he does goes on, and nothing is
 * among the tasks, until Enter sets the place — then the order is made with the place in it and takes its turn. Backspace gives it
 * up. R turns it, the arrows and PageUp/PageDown nudge it (and hold it there), H lets it follow the crosshair again. The keys are
 * ordinary key mappings (Controls, «ИИ-компаньон»), read only while a placement is open and shown by CompanionGhostPanel. The
 * server decides everything that matters: what comes back from it is the only truth about the site. Client only.
 */
final class CompanionGhostPlacement {
    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.aicompanion.placement_" + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, "key.categories.aicompanion");
    }
    static final KeyMapping CONFIRM = key("confirm", GLFW.GLFW_KEY_ENTER), CANCEL = key("cancel", GLFW.GLFW_KEY_BACKSPACE),
            ROTATE = key("rotate", GLFW.GLFW_KEY_R), FOLLOW = key("follow", GLFW.GLFW_KEY_H),
            FORWARD = key("forward", GLFW.GLFW_KEY_UP), BACK = key("back", GLFW.GLFW_KEY_DOWN), LEFT = key("left", GLFW.GLFW_KEY_LEFT),
            RIGHT = key("right", GLFW.GLFW_KEY_RIGHT), RAISE = key("up", GLFW.GLFW_KEY_PAGE_UP), LOWER = key("down", GLFW.GLFW_KEY_PAGE_DOWN);
    static final List<KeyMapping> KEYS = List.of(CONFIRM, CANCEL, ROTATE, FOLLOW, FORWARD, BACK, LEFT, RIGHT, RAISE, LOWER);
    private static final double LOOK_DISTANCE = 64;
    private static final float ALPHA = .5F;
    private static final int POLL_TICKS = 10;

    private static final CompanionBuildGhost ghost = new CompanionBuildGhost();
    /** The building being placed (null: no placement is open) and what the server last sent of it for the current turn. */
    private static String building;
    private static CompanionBuildPlacement.Preview preview;
    private static int rotation;
    private static BlockPos corner = BlockPos.ZERO;
    /** The building the window was opened for by the server's order (not to be offered twice). */
    private static String offered;
    /** Nudged by keys: stays put; otherwise follows the crosshair. */
    private static boolean locked;
    /** The last block the crosshair rested on: looking at the sky leaves the ghost there. */
    private static BlockPos spot;
    private static boolean confirming;
    private static int pollTicks, ticks, quiet;

    private CompanionGhostPlacement() { }

    static void forgetWorld() { close(); }

    private static void close() {
        ghost.close();
        building = null; preview = null; confirming = false; spot = null; locked = false; pollTicks = 0; offered = null;
    }

    /** The building whose ghost is open (for the panel), or null. */
    static String active() { return building == null || preview == null ? null : building; }
    static String keyName(KeyMapping key) { return name(key); }
    static List<String> moveKeys() { return List.of(name(FORWARD), name(BACK), name(LEFT), name(RIGHT)); }

    /**
     * Runs on the integrated server's thread as the player; the answer comes back on the client thread, and only for the world that asked.
     * The answer is null when the server has no such player (gone, left): every callback copes with it. False when nothing was sent at all.
     */
    static <T> boolean onServer(Function<ServerPlayer, T> job, Consumer<T> done) {
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) return false;
        UUID id = minecraft.player.getUUID();
        var world = minecraft.level;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            T answer = player == null ? null : job.apply(player);
            minecraft.execute(() -> { if (minecraft.level == world) done.accept(answer); });
        });
        return true;
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) { close(); drain(); return; }
        ticks++;
        if (pollTicks-- <= 0) { pollTicks = POLL_TICKS; poll(); }
        if (building == null || preview == null || minecraft.screen != null) { drain(); return; }
        boolean nudged = false;
        while (ROTATE.consumeClick()) { rotation = (rotation + 1) % 4; turned(); }
        Direction facing = minecraft.player.getDirection();
        BlockPos nudge = BlockPos.ZERO;
        while (FORWARD.consumeClick()) nudge = nudge.relative(facing);
        while (BACK.consumeClick()) nudge = nudge.relative(facing.getOpposite());
        while (LEFT.consumeClick()) nudge = nudge.relative(facing.getCounterClockWise());
        while (RIGHT.consumeClick()) nudge = nudge.relative(facing.getClockWise());
        while (RAISE.consumeClick()) nudge = nudge.above();
        while (LOWER.consumeClick()) nudge = nudge.below();
        if (!nudge.equals(BlockPos.ZERO)) { locked = true; corner = corner.offset(nudge); nudged = true; }
        while (FOLLOW.consumeClick()) locked = false;
        if (!locked && !nudged) follow(minecraft);
        while (CANCEL.consumeClick()) { cancelOrder(); return; }
        while (CONFIRM.consumeClick()) if (!confirming) confirm();
    }

    /** The player gives the order up (Backspace, or «Отмена» and Esc in the window): dropped on the server, the ghost closed. */
    static void cancelOrder() {
        // Said only when an order was there to drop: «Отмена» of a window opened from the tasks without an order says nothing.
        onServer(player -> { boolean had = CompanionBuildPlacement.pendingName(player) != null; CompanionBuildPlacement.cancel(player); return had; },
                had -> { if (Boolean.TRUE.equals(had)) message("Ладно, не строю."); });
        close(); quiet = ticks + POLL_TICKS * 2;
    }

    /** The window's «Строить»: the ghost of {@code name} opens. Nothing is ordered: the server only reads the building out. */
    static void show(String name) {
        onServer(player -> CompanionBuildPlacement.preview(player.serverLevel(), name, 0), first -> { if (first != null) open(name, first); });
    }

    /**
     * «Построй <постройку>» said or typed ({@code command} is the line the local parser made of it): the window opens on that building
     * instead of an order. False for anything else: the plank house, «дострой», another command.
     */
    static boolean offer(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!command.startsWith("companion work build_house ") || minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return false;
        String item = CompanionLocalCommands.actions(command).getFirst().item();
        var building = dev.aicompanion.entity.CompanionVillageBuildings.resolve(item, minecraft.level.getBiome(minecraft.player.blockPosition()));
        if (building == null) return false;
        CompanionBuildingsScreen.show(building.name());
        return true;
    }

    private static void drain() { for (KeyMapping key : KEYS) while (key.consumeClick()) { } }

    /** Does an order (one the model made) wait for a placement: the window is offered for it, once, unless a ghost is already open. */
    private static void poll() {
        onServer(CompanionBuildPlacement::pendingName, pending -> {
            if (pending == null) { offered = null; return; }
            if (building != null || pending.equals(offered) || ticks < quiet || !opensOver(Minecraft.getInstance().screen)) return;
            offered = pending;
            CompanionBuildingsScreen.show(pending);
        });
    }

    /** The window replaces no screen but the mod's own chat and tasks (the order was typed in the chat); over any other it waits. */
    private static boolean opensOver(net.minecraft.client.gui.screens.Screen screen) {
        return screen == null || screen instanceof CompanionChatScreen || screen instanceof CompanionTasksScreen;
    }

    private static void open(String name, CompanionBuildPlacement.Preview first) {
        String was = offered;
        close();
        offered = was; building = name; preview = first; rotation = 0;
        follow(Minecraft.getInstance());
        ghost.bake(first.blocks());
    }

    /** Another turn: the server turns the building (the one place that knows how), and the ghost is baked again when it is here. */
    private static void turned() {
        String name = building; int wanted = rotation;
        onServer(player -> CompanionBuildPlacement.preview(player.serverLevel(), name, wanted), turned -> {
            if (turned == null || !name.equals(building) || wanted != rotation) return;
            preview = turned;
            ghost.bake(turned.blocks());
        });
    }

    private static void confirm() {
        confirming = true;
        String name = building; int turn = rotation; BlockPos at = corner;
        // The server's reason leaves the ghost to be moved; its yes either gave the place to the order that waited, or asks for the order now.
        boolean sent = onServer(player -> CompanionBuildPlacement.settle(player, name, turn, at), answer -> {
            confirming = false;
            if (answer == null) message("Не смог связаться с миром, нажми ещё раз.");
            else if (answer.why() != null) message(answer.why());
            else {
                close(); quiet = ticks + POLL_TICKS * 2;
                if (!answer.order()) { message("Принято, строю здесь."); return; }
                CompanionChatScreen.runLocalCommand("companion work build_house 1 " + new CompanionBuildPlacement.Site(name, at, turn).item().replace(' ', '_'), CompanionDiagnostics.newTrace());
                if (answer.queued()) message("Место запомнил. Построю, как закончу то, чем занят.");
            }
        });
        if (!sent) confirming = false;
    }

    /** The block looked at is the ground the floor sinks into; the middle of the footprint is under the crosshair. */
    private static void follow(Minecraft minecraft) {
        var player = minecraft.player;
        HitResult hit = player.pick(LOOK_DISTANCE, 1F, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) spot = block.getBlockPos();
        else if (spot == null) {
            // Nothing looked at yet: on the ground a little ahead of the player.
            Vec3 ahead = player.position().add(Vec3.directionFromRotation(0, player.getYRot()).scale(12));
            spot = minecraft.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(ahead)).below();
        }
        corner = spot.offset(-preview.sizeX() / 2, 0, -preview.sizeZ() / 2);
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || building == null || preview == null) return;
        ghost.draw(event, corner, ALPHA);
        CompanionBuildGhost.outline(event, corner, preview.sizeX(), preview.sizeY(), preview.sizeZ());
    }

    private static void message(String text) {
        String companion = CompanionIdentity.cleanName(OpenRouterConfig.COMPANION_NAME.get());
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal(companion + ": " + text).withStyle(ChatFormatting.AQUA));
    }

    /** A letter key is named as the keyboard layout has it now (R, or К in the Russian one): the game's own name of a key is read once and kept. */
    private static String name(KeyMapping key) {
        var bound = key.getKey();
        if (bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() >= GLFW.GLFW_KEY_SPACE && key.getKeyModifier() == net.neoforged.neoforge.client.settings.KeyModifier.NONE) {
            String live = GLFW.glfwGetKeyName(bound.getValue(), -1);
            if (live != null && !live.isBlank()) return live.toUpperCase(java.util.Locale.ROOT);
        }
        return key.getTranslatedKeyMessage().getString();
    }
}

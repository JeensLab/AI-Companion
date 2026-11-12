package dev.aicompanion.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aicompanion.CompanionIdentity;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionBuildPlacement;
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
 * Where a building goes: when the order of a building waits for the player's placement (asked of the integrated server twice a
 * second), a see-through ghost of it follows the crosshair on the ground, and Enter confirms it where it stands, Backspace gives it
 * up. R turns it, the arrows and PageUp/PageDown nudge it (and hold it there), H lets it follow the crosshair again. The keys are
 * ordinary key mappings (Controls, «ИИ-компаньон»), read only while a placement is open. The server decides everything that matters:
 * what comes back from it is the only truth about the site. Client only.
 */
final class CompanionGhostPlacement {
    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.aicompanion.placement_" + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, "key.categories.aicompanion");
    }
    private static final KeyMapping CONFIRM = key("confirm", GLFW.GLFW_KEY_ENTER), CANCEL = key("cancel", GLFW.GLFW_KEY_BACKSPACE),
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
    /** Nudged by keys: stays put; otherwise follows the crosshair. */
    private static boolean locked;
    /** The last block the crosshair rested on: looking at the sky leaves the ghost there. */
    private static BlockPos spot;
    private static boolean confirming;
    private static int pollTicks, hintTicks, ticks, quiet;

    private CompanionGhostPlacement() { }

    static void forgetWorld() { close(); }

    private static void close() {
        ghost.close();
        building = null; preview = null; confirming = false; spot = null; locked = false; pollTicks = 0;
    }

    /** Runs on the integrated server's thread as the player; the answer comes back on the client thread, and only for the world that asked. */
    private static <T> void onServer(Function<ServerPlayer, T> job, Consumer<T> done) {
        Minecraft minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) return;
        UUID id = minecraft.player.getUUID();
        var world = minecraft.level;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            T answer = player == null ? null : job.apply(player);
            minecraft.execute(() -> { if (minecraft.level == world) done.accept(answer); });
        });
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
        while (CANCEL.consumeClick()) {
            onServer(player -> { CompanionBuildPlacement.cancel(player); return true; }, done -> { });
            close(); quiet = ticks + POLL_TICKS * 2;
            message("Ладно, не строю.");
            return;
        }
        while (CONFIRM.consumeClick()) if (!confirming) confirm();
        if (hintTicks-- <= 0) {
            hintTicks = 10;
            minecraft.gui.setOverlayMessage(Component.literal("«" + building + "» · " + name(CONFIRM) + " — строить · " + name(CANCEL) + " — отмена · " + name(ROTATE) + " — повернуть · "
                    + name(FOLLOW) + " — за прицелом").withStyle(ChatFormatting.AQUA), false);
        }
    }

    private static void drain() { for (KeyMapping key : KEYS) while (key.consumeClick()) { } }

    /** Does the server still wait for a placement: opens the ghost for a new order and closes it when the order is gone. */
    private static void poll() {
        String shown = building;
        onServer(player -> {
            String pending = CompanionBuildPlacement.pendingName(player);
            // A new order for the same building is not told from the old one: the ghost simply stays.
            return new Object[]{pending, pending == null || pending.equals(shown) ? null : CompanionBuildPlacement.preview(player.serverLevel(), pending, 0)};
        }, answer -> {
            String pending = (String) answer[0];
            if (pending == null) {
                if (building != null) { close(); message("Размещение закончилось: заказ снят или время вышло."); }
            } else if (!pending.equals(building) && answer[1] != null && ticks >= quiet) open(pending, (CompanionBuildPlacement.Preview) answer[1]);
        });
    }

    private static void open(String name, CompanionBuildPlacement.Preview first) {
        close();
        building = name; preview = first; rotation = 0;
        follow(Minecraft.getInstance());
        ghost.bake(first.blocks());
        message("Выбери место для «" + name + "»: " + name(CONFIRM) + " — строить здесь, " + name(ROTATE) + " — повернуть, стрелки, " + name(RAISE) + " и " + name(LOWER) + " — сдвинуть, "
                + name(FOLLOW) + " — снова за прицелом, " + name(CANCEL) + " — отмена.");
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
        // An empty answer is the server's yes; anything else is its reason, and the ghost stays to be moved.
        onServer(player -> { String why = CompanionBuildPlacement.confirm(player, name, turn, at); return why == null ? "" : why; }, answer -> {
            confirming = false;
            if (answer.isEmpty()) { close(); quiet = ticks + POLL_TICKS * 2; message("Принято, строю здесь."); }
            else message(answer);
        });
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

    private static String name(KeyMapping key) { return key.getTranslatedKeyMessage().getString(); }
}

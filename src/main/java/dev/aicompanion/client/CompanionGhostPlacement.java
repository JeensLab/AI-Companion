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
 * Where a building goes: when the order of a building waits for the player's placement (asked of the integrated server twice a
 * second), the window of buildings opens on it (CompanionBuildingsScreen); its «Строить» (the same order the phrase makes) opens a
 * see-through ghost that follows the crosshair on the ground, and Enter confirms it where it stands, Backspace gives it
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
    private static final int POLL_TICKS = 10, WANT_TICKS = 600;

    private static final CompanionBuildGhost ghost = new CompanionBuildGhost();
    /** The building being placed (null: no placement is open) and what the server last sent of it for the current turn. */
    private static String building;
    private static CompanionBuildPlacement.Preview preview;
    private static int rotation;
    private static BlockPos corner = BlockPos.ZERO;
    /** The building the window was opened for by the server's order (not to be offered twice), and the one the window's «Строить» asked for (the ghost opens for it, not the window). */
    private static String offered, wanted;
    private static int wantedUntil;
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
        building = null; preview = null; confirming = false; spot = null; locked = false; pollTicks = 0; offered = null; wanted = null;
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

    /**
     * The window's «Строить»: the order for {@code name} is made as the phrase makes it (a waiting order of another building is dropped first,
     * on the server; the order for this one is kept), and the ghost opens as soon as the server waits for the place.
     */
    static void order(String name) {
        wanted = name; wantedUntil = ticks + WANT_TICKS; offered = null;
        onServer(player -> CompanionBuildPlacement.replaceFor(player, name), start -> {
            if (Boolean.TRUE.equals(start)) CompanionChatScreen.runLocalCommand("companion work build_house 1 " + name.replace(' ', '_'), CompanionDiagnostics.newTrace());
            pollTicks = 0;
        });
    }

    private static void drain() { for (KeyMapping key : KEYS) while (key.consumeClick()) { } }

    /** Does the server still wait for a placement: offers the window for a new order, opens the ghost for the one the window asked for, and closes it when the order is gone. */
    private static void poll() {
        if (ticks > wantedUntil) wanted = null;
        String shown = building, asked = wanted;
        onServer(player -> {
            String pending = CompanionBuildPlacement.pendingName(player);
            return new Object[]{pending, pending == null || pending.equals(shown) || !pending.equals(asked) ? null : CompanionBuildPlacement.preview(player.serverLevel(), pending, 0)};
        }, answer -> {
            if (answer == null) return;
            String pending = (String) answer[0];
            if (pending == null) {
                offered = null;
                if (building != null) { close(); message("Размещение закончилось: заказ снят или время вышло."); }
            } else if (pending.equals(building)) wanted = null;
            else if (ticks >= quiet) {
                if (pending.equals(wanted) && answer[1] != null) { open(pending, (CompanionBuildPlacement.Preview) answer[1]); }
                else if (!pending.equals(wanted) && !pending.equals(offered) && opensOver(Minecraft.getInstance().screen)) {
                    close(); offered = pending;
                    CompanionBuildingsScreen.show(pending);
                }
            }
        });
    }

    /** The window replaces no screen but the mod's own chat and tasks (the order was typed in the chat); over any other it waits. */
    private static boolean opensOver(net.minecraft.client.gui.screens.Screen screen) {
        return screen == null || screen instanceof CompanionChatScreen || screen instanceof CompanionTasksScreen;
    }

    private static void open(String name, CompanionBuildPlacement.Preview first) {
        close();
        building = name; preview = first; rotation = 0;
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
        // An empty answer is the server's yes; anything else is its reason, and the ghost stays to be moved.
        boolean sent = onServer(player -> { String why = CompanionBuildPlacement.confirm(player, name, turn, at); return why == null ? "" : why; }, answer -> {
            confirming = false;
            if (answer == null) message("Не смог связаться с миром, нажми ещё раз.");
            else if (answer.isEmpty()) { close(); quiet = ticks + POLL_TICKS * 2; message("Принято, строю здесь."); }
            else message(answer);
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

    private static String name(KeyMapping key) { return key.getTranslatedKeyMessage().getString(); }
}

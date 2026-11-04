package dev.aicompanion.client;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Where Oleg is, at a glance: a golden outline through walls and ground once he is out of sight or farther than a few steps.
 * Only for his owner, only on this client. the player 23.10: «стрелка где он не нужна, оставь только контур, и сделай возможность
 * включить и отключать его по кнопке» — the key G (changeable in the controls), also in «Поведение».
 * Issue #64: once he is too far for the client to have him at all, the outline is gone too — then a golden mark on the screen
 * (CompanionFarMarker) shows where the integrated server says he is. Nearer, even behind the player, the outline is enough.
 */
final class CompanionMarker {
    private CompanionMarker() { }

    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AICompanionMod.MOD_ID, "far_marker");
    /** Nearer than this and in sight he needs no outline. */
    private static final double NEAR = 12;
    /** The outline's gold. */
    private static final int GOLD = 0xFFFFD54A;
    private static final Set<UUID> outlined = new HashSet<>();
    /** Whether this client has him at all (then the outline is his marker). */
    private static boolean present;
    /** His place as the server last said, the world it was asked in, and the world of a question still unanswered. */
    private static CompanionFarMarker.Spot far;
    private static Object farWorld, askedIn;
    private static int askTicks;
    /** The camera of the last rendered frame, to put him on the screen. */
    private static final Matrix4f projection = new Matrix4f(), view = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;

    static void init() {
        CompanionEntity.clientOutline = companion -> outlined.contains(companion.getUUID());
        NeoForge.EVENT_BUS.addListener(CompanionMarker::framed);
    }

    /** The key: on or off, said in the action bar. */
    static void toggle() {
        boolean on = !OpenRouterConfig.COMPANION_MARKER.get();
        OpenRouterConfig.COMPANION_MARKER.set(on); OpenRouterConfig.COMPANION_MARKER.save();
        Minecraft.getInstance().gui.setOverlayMessage(Component.literal("Индикатор Олега: " + (on ? "вкл." : "выкл.")), false);
    }

    /** Once a tick: who is outlined (a sight check per frame would be wasted work), and where he is when the client lacks him. */
    static void tick() {
        outlined.clear(); present = false;
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null || !OpenRouterConfig.COMPANION_MARKER.get()) return;
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof CompanionEntity companion) || !companion.isAlive() || !player.getUUID().equals(companion.getOwnerId())) continue;
            present = true;
            if (player.distanceTo(companion) > NEAR || !player.hasLineOfSight(companion)) outlined.add(companion.getUUID());
        }
        // Single player only: the integrated server knows where he is even far off, no packets needed. Twice a second.
        if (present) { far = null; return; }
        var server = minecraft.getSingleplayerServer();
        if (server == null || askedIn == level || ++askTicks < 10) return;
        askTicks = 0; askedIn = level;
        UUID id = player.getUUID();
        server.execute(() -> {
            var owner = server.getPlayerList().getPlayer(id);
            var spot = owner == null ? null : CompanionFarMarker.where(owner);
            minecraft.execute(() -> {
                // An answer for a world the player has left is dropped; a new question is asked there.
                if (askedIn != level) return;
                askedIn = null; far = spot; farWorld = level;
            });
        });
    }

    private static void framed(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        projection.set(event.getProjectionMatrix()); view.set(event.getModelViewMatrix());
        camera = event.getCamera().getPosition();
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        var spot = far;
        var player = minecraft.player;
        if (spot == null || present || player == null || minecraft.level == null || farWorld != minecraft.level
                || minecraft.options.hideGui || !OpenRouterConfig.COMPANION_MARKER.get()) return;
        String name = CompanionChatScreen.companionName();
        var font = minecraft.font;
        if (!spot.dimension().equals(minecraft.level.dimension())) {
            // Below one boss bar (its name at 3, the bar at 12–17): the dragon's stays readable.
            graphics.drawCenteredString(font, CompanionFarMarker.elsewhere(name, spot.dimension()), graphics.guiWidth() / 2, 24, GOLD);
            return;
        }
        // Aimed at the middle of his body.
        var mark = CompanionFarMarker.place(projection, view, spot.x() - camera.x, spot.y() + 1 - camera.y, spot.z() - camera.z,
                graphics.guiWidth(), graphics.guiHeight());
        diamond(graphics, mark.x(), mark.y(), 4, 0x90000000);
        diamond(graphics, mark.x(), mark.y(), 3, GOLD);
        String label = CompanionFarMarker.label(name, spot.x() - player.getX(), spot.y() - player.getY(), spot.z() - player.getZ());
        int width = font.width(label), left = Math.max(2, Math.min(graphics.guiWidth() - 2 - width, mark.x() - width / 2));
        graphics.drawString(font, label, left, mark.y() + 6, GOLD, true);
    }

    private static void diamond(GuiGraphics graphics, int x, int y, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int half = radius - Math.abs(row);
            graphics.fill(x - half, y + row, x + half + 1, y + row + 1, color);
        }
    }
}

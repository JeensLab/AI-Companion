package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Issue #64 (stream VOD 2916726627, 58:57): Oleg went farther than the game draws him, his outline went with him, and
 * nothing showed where he was. Past that a small golden diamond stands where he is on the screen, or at the edge on his
 * side, with «Олег · 140 ↓30»; in another world a line says which. The place and the words are worked out here, with no
 * client classes, so the game tests can check them; CompanionMarker finds him and draws.
 */
public final class CompanionFarMarker {
    private CompanionFarMarker() { }

    /**
     * Gaps from the screen edges. The bottom one keeps the label above the HUD stack in the middle: «думает…» at −84, the
     * action bar at −72, the held item's name at −59, then hearts (even in two rows), armour, food and the hotbar.
     */
    static final int EDGE = 6, BOTTOM = 100;
    /** A smaller difference in height gets no arrow. */
    private static final double HIGH = 10;

    /** A point of the screen, in GUI pixels. */
    public record Mark(int x, int y) { }

    /** Where he is: from the client while it still has him, else read on the server thread and handed over. */
    record Spot(ResourceKey<Level> dimension, double x, double y, double z) { }

    /** His place for his owner, or nothing while he is dead, unloaded or gone. Server thread. */
    static Spot where(ServerPlayer owner) {
        var companion = CompanionEvents.getOwnedCompanion(owner);
        return companion == null ? null : new Spot(companion.level().dimension(), companion.getX(), companion.getY(), companion.getZ());
    }

    /**
     * Where to draw him, for his place relative to the camera and this frame's projection and camera rotation: on him when he
     * is in front and inside the gaps, otherwise on the gaps, from the middle toward his side. Straight behind is the bottom.
     */
    public static Mark place(Matrix4f projection, Matrix4f view, double dx, double dy, double dz, int width, int height) {
        Vector4f clip = new Vector4f((float) dx, (float) dy, (float) dz, 1).mul(view).mul(projection);
        double middleX = width / 2.0, middleY = height / 2.0;
        if (clip.w > 0) {
            double x = (clip.x / clip.w + 1) * middleX, y = (1 - clip.y / clip.w) * middleY;
            if (x >= EDGE && x <= width - EDGE && y >= EDGE && y <= height - BOTTOM) return new Mark((int) Math.round(x), (int) Math.round(y));
        }
        // Without the division by w, which flips the side of what is behind, the direction holds both in front and behind.
        double toX = clip.x * middleX, toY = -clip.y * middleY;
        if (clip.w <= 0 && Math.abs(clip.x) + Math.abs(clip.y) < .01 * -clip.w) { toX = 0; toY = 1; }
        double scale = Math.min(reach(toX, middleX - EDGE, middleX - EDGE), reach(toY, middleY - EDGE, height - BOTTOM - middleY));
        return new Mark((int) Math.round(middleX + toX * scale), (int) Math.round(middleY + toY * scale));
    }

    /** How far along this axis the direction may go from the middle before it meets the gap. */
    private static double reach(double to, double back, double forward) {
        return to > 0 ? forward / to : to < 0 ? back / -to : Double.POSITIVE_INFINITY;
    }

    /** «Олег · 140 ↓30»: how far he is, and how much higher or lower when it matters (dy is his height minus the player's). */
    public static String label(String name, double dx, double dy, double dz) {
        String text = name + " · " + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        return Math.abs(dy) < HIGH ? text : text + (dy > 0 ? " ↑" : " ↓") + Math.round(Math.abs(dy));
    }

    /** «Олег в Незере»: shown instead of the mark while he is in another world than the player. */
    public static String elsewhere(String name, ResourceKey<Level> dimension) {
        String where = Level.OVERWORLD.equals(dimension) ? "в обычном мире" : Level.NETHER.equals(dimension) ? "в Незере"
                : Level.END.equals(dimension) ? "в Энде" : "в другом мире";
        return name + " " + where;
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionFarMarker;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * the player 23.10: «было бы удобно чтоб я всегда знал где он, может небольшой маркер на нем если он за стеной», then «оставь только
 * контур». The drawing is the client's; here that the outline is no server state other players would see.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMarkerTests {
    @GameTest(template = "empty")
    public static void theOutlineIsNoServerState(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2);
        var before = CompanionEntity.clientOutline;
        CompanionEntity.clientOutline = any -> true;
        try {
            h.assertTrue(!companion.isCurrentlyGlowing() && companion.getTeamColor() == 0xFFFFFF, "The server made him glow for everybody");
        } finally { CompanionEntity.clientOutline = before; }
        h.succeed();
    }

    /** Issue #64: past the outline's reach a mark stands where he is on the screen, or at the edge on his side. */
    @GameTest(template = "empty")
    public static void theFarMarkStandsOnHimOrAtTheEdgeOnHisSide(GameTestHelper h) {
        int width = 480, height = 270;
        // Facing north (yaw 180), level, the default 70° view.
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), width / (float) height, .05F, 1024);
        Matrix4f view = new Matrix4f().rotation(new Quaternionf().rotationYXZ((float) Math.PI - 180 * Mth.DEG_TO_RAD, 0, 0).conjugate());
        var ahead = CompanionFarMarker.place(projection, view, 0, 0, -200, width, height);
        h.assertTrue(ahead.x() == 240 && ahead.y() == 135, "Straight ahead he is not in the middle: " + ahead);
        var east = CompanionFarMarker.place(projection, view, 200, 0, 0, width, height);
        h.assertTrue(east.x() >= width - 10 && east.y() == 135, "East of a player facing north he is not at the right edge: " + east);
        var west = CompanionFarMarker.place(projection, view, -200, 0, -20, width, height);
        h.assertTrue(west.x() <= 10 && west.y() == 135, "West he is not at the left edge: " + west);
        var behind = CompanionFarMarker.place(projection, view, 0, 0, 200, width, height);
        h.assertTrue(behind.x() == 240 && behind.y() > 135 && behind.y() < height - 30, "Behind he is not at the bottom, above the hotbar: " + behind);
        var high = CompanionFarMarker.place(projection, view, 0, 300, -200, width, height);
        h.assertTrue(high.x() == 240 && high.y() <= 10, "High above he is not at the top edge: " + high);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theFarMarkSaysHowFarHowHighAndWhichWorld(GameTestHelper h) {
        h.assertTrue(CompanionFarMarker.label("Олег", 120, -50, 0).equals("Олег · 130 ↓50"), CompanionFarMarker.label("Олег", 120, -50, 0));
        h.assertTrue(CompanionFarMarker.label("Олег", 0, 12, -160).equals("Олег · 160 ↑12"), CompanionFarMarker.label("Олег", 0, 12, -160));
        h.assertTrue(CompanionFarMarker.label("Олег", 140, 9, 0).equals("Олег · 140"), "A few blocks of height are not worth an arrow: "
                + CompanionFarMarker.label("Олег", 140, 9, 0));
        h.assertTrue(CompanionFarMarker.elsewhere("Олег", Level.NETHER).equals("Олег в Незере")
                && CompanionFarMarker.elsewhere("Олег", Level.OVERWORLD).equals("Олег в обычном мире")
                && CompanionFarMarker.elsewhere("Олег", Level.END).equals("Олег в Энде"), "Another world is not named");
        h.succeed();
    }
}

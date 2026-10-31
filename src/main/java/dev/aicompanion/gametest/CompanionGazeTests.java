package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBond;
import dev.aicompanion.entity.CompanionBonds;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionGaze;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The owner's request of 29.10: he meets the player's eyes while he speaks or is touched, and avoids them when he is offended. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGazeTests {
    /** The rule itself: acquaintance and warmer look at the player, wary past him, offended away; speech and touch still meet wary eyes. */
    @GameTest(template = "empty")
    public static void theGazeFollowsTheBond(GameTestHelper h) {
        h.assertTrue(CompanionGaze.eyesFor(CompanionBond.BASE) == CompanionGaze.Eyes.AT_PLAYER, "A fresh bond does not look at the player");
        h.assertTrue(CompanionGaze.eyesFor(15) == CompanionGaze.Eyes.AT_PLAYER, "Acquaintance does not look at the player");
        h.assertTrue(CompanionGaze.eyesFor(14) == CompanionGaze.Eyes.PAST_PLAYER, "Wary does not look past the player");
        h.assertTrue(CompanionGaze.eyesFor(0) == CompanionGaze.Eyes.PAST_PLAYER, "The bottom of wary does not look past the player");
        h.assertTrue(CompanionGaze.eyesFor(-1) == CompanionGaze.Eyes.DOWN, "An offended companion still looks at the player");
        h.assertTrue(CompanionGaze.meetsEyes(CompanionBond.BASE), "A fresh bond does not meet the eyes when spoken to");
        h.assertTrue(CompanionGaze.meetsEyes(9), "A wary companion does not meet the eyes when spoken to");
        h.assertTrue(!CompanionGaze.meetsEyes(-1), "An offended companion meets the eyes when spoken to");
        h.assertTrue(Math.abs(CompanionGaze.yawTo(0, 0, 0, 5)) < .01F, "The yaw to the south is not zero");
        h.assertTrue(Math.abs(CompanionGaze.yawTo(0, 0, -5, 0) - 90F) < .01F, "The yaw to the west is not ninety");
        h.succeed();
    }

    /** While he says something his head turns to the player. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void heFacesThePlayerWhileHeSpeaks(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var player = owner(h, companion);
        // A wary bond keeps the stock glance switched off, so only the speech path can turn him to the player.
        CompanionBonds.get(h.getLevel().getServer()).hitByPlayer(player.getUUID(), 1, 100L, false);
        h.assertTrue(!companion.gazeLooksAtPlayer(), "The stock glance is still on, so the test proves nothing");
        float wanted = CompanionGaze.yawTo(companion.getX(), companion.getZ(), player.getX(), player.getZ());
        h.assertTrue(Math.abs(Mth.wrapDegrees(companion.getYHeadRot() - wanted)) > 20F, "He starts facing the player, so the test proves nothing");
        float[] closest = {999F};
        companion.gazeTalk();
        h.onEachTick(() -> closest[0] = Math.min(closest[0], Math.abs(Mth.wrapDegrees(companion.getYHeadRot() - wanted))));
        h.succeedWhen(() -> h.assertTrue(closest[0] <= 20F, "He did not turn to the player: " + closest[0] + " degrees off"));
    }

    /** A touch from the player turns his eyes to him, even when that touch has just dropped the bond to wary. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void aTouchTurnsHisEyesToThePlayer(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var player = owner(h, companion);
        companion.hurt(h.getLevel().damageSources().playerAttack(player), 1F);
        h.assertTrue(companion.gazeBondScore() < 15, "The touch did not drop the bond: " + companion.gazeBondScore());
        h.assertTrue(companion.gazeMeetsEyes(), "A touched companion does not meet the eyes: bond " + companion.gazeBondScore());
        float wanted = CompanionGaze.yawTo(companion.getX(), companion.getZ(), player.getX(), player.getZ());
        h.assertTrue(Math.abs(Mth.wrapDegrees(companion.getYHeadRot() - wanted)) > 20F, "He starts facing the player, so the test proves nothing");
        float[] closest = {999F};
        h.onEachTick(() -> closest[0] = Math.min(closest[0], Math.abs(Mth.wrapDegrees(companion.getYHeadRot() - wanted))));
        h.succeedWhen(() -> h.assertTrue(closest[0] <= 20F, "A touch did not turn him to the player: " + closest[0] + " degrees off"));
    }

    /** An offended companion keeps his eyes away, and speaking to him does not bring them back. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void anOffendedCompanionLooksAway(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var player = owner(h, companion);
        var bonds = CompanionBonds.get(h.getLevel().getServer());
        // Two hits a day is the daily cap, the score drifts a point a day toward the base, and hits closer than the gap are
        // ignored, so the game times must stay far apart: two days take him below zero.
        for (long day = 1; day <= 2; day++)
            for (int i = 0; i < 2; i++) bonds.hitByPlayer(player.getUUID(), day, day * 10_000L + i * 700L, false);
        h.assertTrue(companion.gazeBondScore() < 0, "The bond did not fall below zero: " + companion.gazeBondScore());
        h.assertTrue(!companion.gazeLooksAtPlayer(), "An offended companion still looks at the player");
        h.assertTrue(!companion.gazeMeetsEyes(), "An offended companion still meets the eyes when spoken to");
        float wanted = CompanionGaze.yawTo(companion.getX(), companion.getZ(), player.getX(), player.getZ());
        h.assertTrue(Math.abs(Mth.wrapDegrees(companion.getYHeadRot() - wanted)) > 20F, "He starts facing the player, so the test proves nothing");
        int[] ticks = {0}, near = {0};
        companion.gazeTalk();
        h.onEachTick(() -> {
            ticks[0]++;
            if (Math.abs(Mth.wrapDegrees(companion.getYHeadRot() - wanted)) <= 20F) near[0]++;
        });
        // A mob's pitch is reset to zero by the game every tick (LookControl.resetXRotOnTick), so the avoidance is observable
        // only as his head not facing the player: he must stay more than 20 degrees off for most of the window.
        // The gaze itself is pinned by the wanted point: it must lie below his eyes (the ground ahead), not at the player.
        h.succeedWhen(() -> h.assertTrue(ticks[0] >= 40 && near[0] <= ticks[0] * .5F
                        && companion.getLookControl().getWantedY() < companion.getEyeY(),
                "He kept the player's eyes while offended: " + near[0] + " of " + ticks[0] + " ticks"));
    }

    /** A companion of his own in the test structure, with the mock player six blocks east of him: the game spawns him facing south. */
    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 4);
        var player = h.makeMockServerPlayerInLevel();
        var at = companion.blockPosition();
        player.teleportTo(at.getX() + 6.5D, at.getY(), at.getZ() + .5D);
        companion.setOwnerId(player.getUUID());
        return companion;
    }

    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

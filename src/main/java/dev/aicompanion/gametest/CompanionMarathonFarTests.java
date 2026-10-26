package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Marathon arena «далеко»: a long valley (48 × 192) of rolling hills, crossed at z 96..101 by a rocky ridge twelve blocks high with
 * a single pass at x 30..34. Толик: «нужно, чтобы он в любой ситуации понимал, как до меня добраться». The player goes 150–170
 * blocks away over the ridge; Oleg comes on foot, not by a teleport (FollowPlayerGoal teleported him beyond 128 blocks).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMarathonFarTests {
    static final int X = 48, Y = 40, Z = 192;

    static void valley(GameTestHelper h) {
        var t = new MarathonTerrain(h, X, Y, Z);
        t.frame(30);
        var grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int x = 1; x < X - 1; x++) for (int z = 1; z < Z - 1; z++) {
            int top = 9 + (int) Math.round(MarathonTerrain.hills(x, z, 2.2, .7));
            boolean pass = x >= 30 && x <= 34;
            int ridge = z >= 96 && z <= 101 ? 12 : z == 94 || z == 103 ? 4 : z == 95 || z == 102 ? 8 : 0;
            if (!pass) top += ridge;
            t.column(x, z, top, 3, ridge >= 8 && !pass ? Blocks.STONE.defaultBlockState() : grass);
        }
        for (int z = 10; z < Z - 10; z += 13) {
            int x = 6 + (z * 7) % 36;
            if (x >= 28 && x <= 36 || z >= 92 && z <= 105) continue;
            t.oak(x, t.top(x, z, Y - 1) + 1, z, 5, false);
        }
    }

    /** «Иди за мной» over the ridge, then «стой», the player walks off 160 blocks, «иди за мной» again; and «иди ко мне» from as far. */
    @GameTest(template = "marathon_far", batch = "marathon", timeoutTicks = 20000)
    public static void overTheRidgeAndBackOnFoot(GameTestHelper h) {
        valley(h);
        var a = new MarathonArena(h, X, Y, Z, 22, 12, 24, 14, 48);
        var watch = a.watch;
        a.script
                .order("Иди за мной")
                .then("the player walks over the pass to the far end", () -> a.walkPlayer(.2, 32, 80, 32, 98, 32, 112, 24, 178), a::playerArrived, 20 * 80)
                .waitUntil("caught up at the far end", () -> a.toPlayer() < 8, 20 * 60)
                .check("followed over the ridge", () -> { watch.assertNoTeleport(); watch.assertNoRescue(); watch.assertNoLongIdle(10); })
                .order("Стой")
                .then("the player walks back 160 blocks", () -> a.walkPlayer(.25, 32, 112, 32, 98, 32, 80, 24, 16), a::playerArrived, 20 * 80)
                .check("stayed far away", () -> a.expect(a.toPlayer() > 128, "Did not stay or the walk was short: " + a.toPlayer()))
                .order("Иди за мной")
                .waitUntil("came 160 blocks on foot", () -> a.toPlayer() < 8, 20 * 120)
                .check("no teleport from beyond 128 blocks", () -> { watch.assertNoTeleport(); watch.assertNoRescue(); watch.assertNoLongIdle(10); })
                .order("Стой")
                .then("the player walks off again", () -> a.walkPlayer(.25, 32, 80, 32, 98, 32, 112, 24, 178), a::playerArrived, 20 * 80)
                .ask("Иди ко мне", null, 20 * 150)
                .check("came when called", () -> {
                    a.expect(a.chat.said.getLast().report().contains("рядом"), "No «рядом» || " + a.chat.history());
                    watch.assertWithPlayer(4);
                    watch.assertNoTeleport();
                    watch.assertNoRescue();
                });
        a.script.run("far-over-the-ridge", watch::summary);
    }
}

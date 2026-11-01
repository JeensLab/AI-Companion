package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;
import static dev.aicompanion.gametest.CompanionMineFightTests.armed;
import static dev.aicompanion.gametest.CompanionMineFightTests.chamber;
import static dev.aicompanion.gametest.CompanionMineFightTests.creeper;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;

/** The creeper guard with several creepers about, and what else may take his turn while one is near. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineGuardTests {
    private static final int FUSE_TICKS = 200, STAND = 6, LIT_AT = 8, FRESH_AT = 1;
    private static final double SLACK = .25;
    private static final float KEPT = 10;

    private CompanionMineGuardTests() { }

    /**
     * A lit fuse on his east and a creeper with no fuse yet on his west (it stands still): the way out of the lit one does not lead to the other,
     * he is never nearer to either than he was, and the blast leaves him more than half his health.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aRetreatFromALitFuseDoesNotCloseOnAnotherCreeper(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(STAND, 0, 0));
        Creeper lit = creeper(h, foot.offset(LIT_AT, 0, 0));
        Creeper fresh = creeper(h, foot.offset(FRESH_AT, 0, 0));
        fresh.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        lit.ignite();
        start(h, c, 1);
        long begun = h.getTick();
        double[] start = {-1, -1};
        h.onEachTick(() -> {
            double toFresh = c.distanceTo(fresh), toLit = c.distanceTo(lit);
            if (start[0] < 0) { start[0] = toFresh; start[1] = toLit; }
            String trace = " | to the fresh one " + String.format("%.1f", toFresh) + " from " + String.format("%.1f", start[0]) + ", to the lit one "
                    + String.format("%.1f", toLit) + (lit.isAlive() ? "" : " (gone)") + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(!fresh.isAlive() || toFresh >= start[0] - SLACK, "He walked towards the creeper behind him" + trace);
            if (lit.isAlive()) {
                h.assertTrue(h.getTick() - begun < FUSE_TICKS, "The lit creeper never blew" + trace);
                return;
            }
            h.assertTrue(c.getHealth() >= KEPT, "The blast took him below half his health" + trace);
            h.succeed();
        });
    }
}

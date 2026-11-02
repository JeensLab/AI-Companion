package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;
import static dev.aicompanion.gametest.CompanionMineFightTests.armed;
import static dev.aicompanion.gametest.CompanionMineFightTests.chamber;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;
import static dev.aicompanion.gametest.CompanionMineFightTests.zombie;

/** A mine fight at low health is left, not fought to the death (acceptance w1e: two deaths on zombies at 2-6 health); at full health the sword still kills. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK19Tests {
    private static final int SURVIVE_TICKS = 700, KILL_TICKS = 1200, HORDE = 5, HORDE_TOTAL = 10, DIG_TICKS = 600;

    private CompanionMineK19Tests() { }

    /**
     * Digging, a horde of zombies comes at him, as in the mine of the acceptance (a fallen zombie is replaced by the next, so the
     * fight has no end to win). With the sword, bread and blocks in the bag he does not fight to the last health points: at low health he
     * goes up his stairs and lives.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aHordeOnHimAtLowHealthIsLeftOnTheStairsAndHeLives(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot);
        List<Zombie> foes = new ArrayList<>();
        long[] hordeAt = {-1};
        float[] lowest = {c.getMaxHealth()};
        start(h, c, 1);
        h.onEachTick(() -> {
            lowest[0] = Math.min(lowest[0], c.getHealth());
            if (hordeAt[0] < 0 && (c.expeditionStage().equals("DESCEND") || c.expeditionStage().equals("BRANCH"))) hordeAt[0] = h.getTick();
            if (hordeAt[0] >= 0 && foes.stream().filter(Zombie::isAlive).count() < HORDE && foes.size() < HORDE_TOTAL) foes.add(zombie(h, foot.offset(3, 0, foes.size() % 4)));
            for (Zombie foe : foes) if (foe.isAlive() && foe.getTarget() == null) foe.setTarget(c);
            String trace = " | stage " + c.expeditionStage() + ", foes alive " + foes.stream().filter(Zombie::isAlive).count() + "/" + foes.size() + ", lowest health " + lowest[0]
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (hordeAt[0] < 0) { h.assertTrue(h.getTick() < DIG_TICKS, "He did not come to the digging" + trace); return; }
            if (h.getTick() - hordeAt[0] >= SURVIVE_TICKS) h.succeed();
        });
    }

    /** Full health, two zombies on him: the sword kills both, and he does not turn back from the fight. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void atFullHealthHeKillsTheZombiesAndDoesNotRunFromThem(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot);
        List<Zombie> foes = List.of(zombie(h, foot.offset(2, 0, 0)), zombie(h, foot.offset(2, 0, 1)));
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            for (Zombie foe : foes) if (foe.isAlive() && foe.getTarget() == null) foe.setTarget(c);
            String trace = " | foes alive " + foes.stream().filter(Zombie::isAlive).count() + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(!c.getLastTaskResult().contains("Прервал вылазку"), "He left the fight at full health" + trace);
            if (foes.stream().noneMatch(Zombie::isAlive)) h.succeed();
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The zombies were not killed in time" + trace);
        });
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The world probe's climb counter on the depth sequences of the runs of 31.10 (seed 20261031, run/mine-world-final2): the ticks of
 * the crossings are the probe's own MINE_WORLD_CLIMB lines, the depths between them the 100-tick snapshots of the mine journal.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineWorldClimbTests {
    /** The depth per tick (index = tick), built from the points he was known to be at. */
    private static final class Track {
        final List<Integer> depths = new ArrayList<>(List.of(0));
        final List<Integer> deaths = new ArrayList<>();

        Track to(int depth, int tick) {
            int start = depths.size() - 1, from = depths.get(start);
            for (int at = start + 1; at <= tick; at++) depths.add(from + Math.round((float) (depth - from) * (at - start) / (tick - start)));
            return this;
        }

        /** The body dies where it stands; a new one is at the entrance on the next tick. */
        Track death() { deaths.add(depths.size()); depths.add(0); return this; }

        /** The ticks on which the counter went up. */
        List<Integer> climbs() {
            List<Integer> ticks = new ArrayList<>();
            var state = CompanionMineWorldClimbs.State.START;
            for (int tick = 0; tick < depths.size(); tick++) {
                if (deaths.contains(tick)) state = state.afterDeath();
                var next = CompanionMineWorldClimbs.next(state, depths.get(tick), true);
                if (next.count() > state.count()) ticks.add(tick);
                state = next;
            }
            return ticks;
        }
    }

    /**
     * void-2: down to 129, up to the entrance (tick 12778), down to 32 and up again, reaching depth 2 at 14543 and, after a step down
     * and back, at 14654. The probe counted three climbs and failed the run; he made two.
     */
    @GameTest(template = "empty")
    public static void aStepAtTheEntranceIsNotASecondClimb(GameTestHelper h) {
        Track track = new Track().to(129, 7_000).to(129, 8_500).to(3, 12_777).to(2, 12_778).to(2, 12_790).to(32, 14_000)
                .to(3, 14_542).to(2, 14_543).to(3, 14_580).to(4, 14_600).to(3, 14_640).to(3, 14_653).to(2, 14_654).to(0, 14_680);
        h.assertTrue(track.climbs().equals(List.of(12_778, 14_543)), "void-2 climbs: " + track.climbs());
        h.succeed();
    }

    /**
     * mobs: killed by a zombie at depth 26 (tick 2646), down to 127 and up to 26 again and again, killed by a creeper at 127
     * (tick 55255). The new body stands at the entrance and walks the first stairs, down to 11 and back, at ticks 56691, 57073 and 57842.
     * The probe counted these three and failed the run; none of them is a climb out of the depth.
     */
    @GameTest(template = "empty")
    public static void walkingTheFirstStairsAfterADeathIsNoClimb(GameTestHelper h) {
        Track track = new Track().to(26, 2_646).death().to(127, 12_000).to(26, 14_000).to(127, 20_000).to(26, 24_000).to(127, 55_255).death()
                .to(11, 56_600).to(3, 56_690).to(2, 56_691).to(8, 56_900).to(3, 57_072).to(2, 57_073).to(11, 57_500).to(3, 57_841).to(2, 57_842).to(8, 58_000);
        h.assertTrue(track.climbs().isEmpty(), "mobs climbs: " + track.climbs());
        h.succeed();
    }

    /** A descent to the hover depth and back is a climb, one that stopped short of it is not, and a death forgets the descent. */
    @GameTest(template = "empty")
    public static void onlyTheWayUpFromTheHoverDepthCounts(GameTestHelper h) {
        int hover = CompanionMineWorldClimbs.HOVER_DEPTH;
        List<Integer> reached = new Track().to(hover, 400).to(0, 800).climbs();
        h.assertTrue(reached.size() == 1, "A climb from the hover depth is lost: " + reached);
        List<Integer> shallow = new Track().to(hover - 1, 400).to(0, 800).climbs();
        h.assertTrue(shallow.isEmpty(), "A climb from just above the hover depth is counted: " + shallow);
        List<Integer> died = new Track().to(hover, 400).death().to(5, 500).to(0, 800).climbs();
        h.assertTrue(died.isEmpty(), "A climb of the dead body is counted: " + died);
        List<Integer> twice = new Track().to(hover, 400).to(0, 800).to(hover, 1_200).to(0, 1_600).climbs();
        h.assertTrue(twice.size() == 2, "Two descents to the hover depth are not two climbs: " + twice);
        h.succeed();
    }
}

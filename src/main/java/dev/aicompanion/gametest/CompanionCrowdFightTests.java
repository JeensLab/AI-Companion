package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;
import static dev.aicompanion.gametest.CompanionMineFightTests.armed;
import static dev.aicompanion.gametest.CompanionMineFightTests.chamber;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;
import static dev.aicompanion.gametest.CompanionMineFightTests.zombie;

/**
 * A crowd of zombies on him, live. In the open he keeps out of their reach between two swings; in a corner or a passage there is nowhere
 * to step back to and each of them lands its blow (run ct78 20261031 mobs: pinned in a corner of a cave, three zombies on him, a blow
 * dealt at every swing and still dead). With three or more after him he gets out of their reach as a player does: two blocks from his
 * bag under his feet, and he strikes down from the column; the blocks come back to the bag when the fight is over.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCrowdFightTests {
    /** The hall is the chamber at the foot of the stairs, four cells of air: the least a column of two and he on it take. A column of three takes five. */
    private static final int HALL = 13, ROOF = 3, HIGH = 5, KILL_TICKS = 1800, BLOWS = 3, STAND = 3, FIRST_KILL_TICKS = 400, COLUMN = 2, COLUMN_TICKS = 200, LEAST_CROWD = 3, THROWN = 2;
    private static final float HURT = 12;
    /**
     * The horde: a hall east of the foot of his stairs, he this far into it, two zombies two cells beyond him and three pairs more from its
     * far end, the first of them when the two are on him; how far from there the column stands at the least, and the blows eight of
     * them may land on him in all (one that jumps at the column reaches him now and then).
     */
    private static final int LONG = 14, SIDE = 3, INTO = 8, WAVE = 20, PAIRS = 4, HORDE_TICKS = 2400, CLEAR = 3, HORDE_BLOWS = 5;
    /** Where the five stand: across the hall from his corner. */
    private static final int[][] CROWD = {{6, 0}, {6, 2}, {7, 4}, {5, 5}, {8, 1}};

    private CompanionCrowdFightTests() { }

    /**
     * Five zombies cross a hall at him, he stands in its corner with the stone sword, five cells of air over the floor: all five die, he
     * takes no more than {@link #BLOWS} blows, and the blocks he stood on are back in his bag.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void fiveZombiesAtHisCornerAreKilledFromAColumnAndTheBlocksComeBack(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        for (int dx = 0; dx <= HALL; dx++) for (int dz = -HALL / 2; dz <= HALL / 2; dz++) h.getLevel().setBlock(foot.offset(dx, HIGH - 1, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        place(c, foot.offset(HALL, 0, HALL / 2));
        int stone = count(c, Items.COBBLESTONE);
        List<Zombie> zombies = new ArrayList<>();
        for (int[] at : CROWD) zombies.add(zombie(h, foot.offset(at[0], 0, at[1])));
        long begun = h.getTick();
        float[] last = {c.getHealth()};
        int[] blows = {0};
        start(h, c, 1);
        h.onEachTick(() -> {
            if (c.getHealth() < last[0]) blows[0]++;
            last[0] = c.getHealth();
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | zombies alive " + alive + ", blows taken " + blows[0] + ", health " + c.getHealth() + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", corner " + rel(h, foot.offset(HALL, 0, HALL / 2)).toShortString() + ", supports " + c.getTemporarySupportCount() + ", stage " + c.expeditionStage() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(blows[0] <= BLOWS, "The crowd got at him" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The crowd was not killed and the column taken down in time" + trace);
            if (alive == 0 && c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports() && count(c, Items.COBBLESTONE) >= stone) h.succeed();
        });
    }

    /** Under a roof of three cells, too low for a column, a crowd of three is fought as before: no block is laid, and the first of them is dead in time. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void underALowRoofTheCrowdIsFoughtFromTheGround(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        for (int dx = 0; dx <= HALL; dx++) for (int dz = -HALL / 2; dz <= HALL / 2; dz++) h.getLevel().setBlock(foot.offset(dx, ROOF, dz), Blocks.STONE.defaultBlockState(), 2 | 16);
        place(c, foot.offset(STAND, 0, 0));
        int stone = count(c, Items.COBBLESTONE);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < LEAST_CROWD; k++) zombies.add(zombie(h, foot.offset(CROWD[k][0], 0, CROWD[k][1])));
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | zombies alive " + alive + ", health " + c.getHealth() + ", he at " + rel(h, c.blockPosition()).toShortString() + ", supports " + c.getTemporarySupportCount() + " | " + report(c);
            // Only under the roof: outside the hall, or a step dug down from it, there is a cell more over him.
            BlockPos at = c.blockPosition().subtract(foot);
            boolean inHall = at.getY() == 0 && at.getX() >= 0 && at.getX() <= HALL && Math.abs(at.getZ()) <= HALL / 2;
            h.assertTrue(!inHall || c.getTemporarySupportCount() == 0 && count(c, Items.COBBLESTONE) == stone, "A block was laid under a roof with no room for a column" + trace);
            h.assertTrue(h.getTick() - begun < FIRST_KILL_TICKS, "None of the crowd was killed in time" + trace);
            if (alive < zombies.size()) h.succeed();
        });
    }

    /**
     * Already hurt, two zombies are a crowd for him: the column is raised, two blocks under a roof of four cells, and beside the corner
     * he stands in, where the roof hangs a cell lower over him. In full health two are fought from the ground (CompanionMineFightTests).
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void hurtHeStandsOnAColumnFromTwoZombies(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        h.getLevel().setBlock(foot.offset(HALL, ROOF, HALL / 2), Blocks.STONE.defaultBlockState(), 2 | 16);
        place(c, foot.offset(HALL, 0, HALL / 2));
        c.setHealth(HURT);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < 2; k++) zombies.add(zombie(h, foot.offset(CROWD[k][0], 0, CROWD[k][1])));
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            String trace = " | health " + c.getHealth() + ", he at " + rel(h, c.blockPosition()).toShortString() + ", supports " + c.getTemporarySupportCount() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(h.getTick() - begun < COLUMN_TICKS, "No column was raised from two zombies at " + HURT + " health" + trace);
            if (c.getTemporarySupportCount() >= COLUMN) h.succeed();
        });
    }

    /**
     * Thrown off the column in the middle of the fight (here: set down on the floor two cells from it, as a blast would), he fights the
     * three on the ground, the column standing; with them dead he walks back to it and its blocks are in his bag again.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void thrownOffTheColumnHeFightsOnTheGroundAndComesBackForItsBlocks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        for (int dx = 0; dx <= HALL; dx++) for (int dz = -HALL / 2; dz <= HALL / 2; dz++) h.getLevel().setBlock(foot.offset(dx, HIGH - 1, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        BlockPos corner = foot.offset(HALL, 0, HALL / 2);
        place(c, corner);
        int stone = count(c, Items.COBBLESTONE);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < LEAST_CROWD; k++) zombies.add(zombie(h, foot.offset(CROWD[k][0], 0, CROWD[k][1])));
        long begun = h.getTick();
        boolean[] thrown = {false};
        start(h, c, 1);
        h.onEachTick(() -> {
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            if (!thrown[0] && alive > 0 && c.getTemporarySupportCount() >= COLUMN && c.onGround() && c.getY() >= corner.getY() + COLUMN) {
                thrown[0] = true;
                place(c, corner.offset(-THROWN, 0, -THROWN));
            }
            String trace = " | thrown " + thrown[0] + ", zombies alive " + alive + ", health " + c.getHealth() + ", he at " + rel(h, c.blockPosition()).toShortString() + ", corner " + rel(h, corner).toShortString()
                    + ", supports " + c.getTemporarySupportCount() + ", cobblestone " + count(c, Items.COBBLESTONE) + " of " + stone + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The three were not killed and the column taken down in time" + trace);
            if (thrown[0] && alive == 0 && c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports() && count(c, Items.COBBLESTONE) >= stone) h.succeed();
        });
    }

    /**
     * A horde that comes two by two (series ta 20261031 d3 mobs: eight killed one after another, dead at the ninth): the first two are on
     * him when the next pair makes them a crowd, and a block does not go into a cell a mob stands in. He runs clear of them over the floor
     * and raises the column there, not where they are on him: all eight die, he takes no more than {@link #HORDE_BLOWS} blows, and the
     * blocks are back in his bag.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aHordeThatComesTwoByTwoIsOutrunAndKilledFromAColumn(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = 1; dx <= LONG; dx++) for (int dz = -SIDE; dz <= SIDE; dz++) for (int dy = 0; dy < HIGH; dy++) h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        place(c, foot.offset(INTO, 0, 0));
        int stone = count(c, Items.COBBLESTONE);
        List<Zombie> zombies = new ArrayList<>();
        zombies.add(zombie(h, foot.offset(INTO + 2, 0, -1)));
        zombies.add(zombie(h, foot.offset(INTO + 2, 0, 1)));
        long begun = h.getTick();
        float[] last = {c.getHealth()};
        int[] blows = {0}, pairs = {1}, column = {0};
        // Where he was when the next pair made them a crowd, and where his column went up.
        BlockPos[] at = {null, null};
        start(h, c, 1);
        h.onEachTick(() -> {
            long ticks = h.getTick() - begun;
            if (ticks > 0 && ticks % WAVE == 0 && pairs[0] < PAIRS) {
                pairs[0]++;
                zombies.add(zombie(h, foot.offset(LONG, 0, -2)));
                zombies.add(zombie(h, foot.offset(LONG, 0, 2)));
            }
            if (c.getHealth() < last[0]) blows[0]++;
            last[0] = c.getHealth();
            column[0] = Math.max(column[0], c.getTemporarySupportCount());
            if (ticks == WAVE) at[0] = c.blockPosition();
            if (at[1] == null && c.getTemporarySupportCount() >= COLUMN) at[1] = c.blockPosition();
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | zombies alive " + alive + " of " + zombies.size() + ", blows taken " + blows[0] + ", health " + c.getHealth() + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", stood at " + rel(h, foot.offset(INTO, 0, 0)).toShortString() + ", supports " + c.getTemporarySupportCount() + " (most " + column[0] + "), stage " + c.expeditionStage() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(blows[0] <= HORDE_BLOWS, "The horde got at him" + trace);
            h.assertTrue(at[0] == null || at[1] == null || Math.hypot(at[1].getX() - at[0].getX(), at[1].getZ() - at[0].getZ()) >= CLEAR,
                    "The column went up where the two were on him, at " + (at[1] == null ? "" : rel(h, at[1]).toShortString()) + trace);
            h.assertTrue(ticks < HORDE_TICKS, "The horde was not killed and the column taken down in time" + trace);
            if (pairs[0] == PAIRS && alive == 0 && column[0] >= COLUMN && c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports() && count(c, Items.COBBLESTONE) >= stone) h.succeed();
        });
    }
}

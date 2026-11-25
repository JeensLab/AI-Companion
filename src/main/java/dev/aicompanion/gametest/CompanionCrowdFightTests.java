package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A crowd of zombies on him, live, on a flat stone floor with walls built where a test needs them. In the open he keeps out of their reach
 * between two swings; in a corner or a passage there is nowhere to step back to and each of them lands its blow (run ct78 20261031 mobs:
 * pinned in a corner of a cave, three zombies on him, a blow dealt at every swing and still dead). With three or more after him he gets out
 * of their reach as a player does: blocks from his bag under his feet, and he strikes down from the column; the blocks come back to the
 * bag when the fight is over. This grew out of the fight in PR #98 by qMiko318.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCrowdFightTests {
    /** The arena (the template "empty" is this wide), its middle, the height of the walls and of the air in a passage, and the times the checks run. */
    private static final int SIZE = 32, MIDDLE = 16, WALL = 8, HIGH = 5, KILL_TICKS = 1800, FIRST_KILL_TICKS = 400, TIMEOUT = 2600;
    private static final int BLOWS = 3, COLUMN = 2, COLUMN_TICKS = 200, LEAST_CROWD = 3, THROWN = 2, ROOF = 4;
    private static final float HURT = 12;
    /** The corner he stands in: the walls run along the cells east and south of it. */
    private static final int CORNER = 24;
    /**
     * The horde: he stands here, two zombies two cells beyond him and three pairs more from the far end, the first of them when the two are
     * on him; how far from there the column stands at the least, and the blows he may take from then until it is up (after that the fight
     * is the one from a column: a zombie that jumps at it reaches him now and then).
     */
    private static final int HERE = 18, FAR_END = HERE + 6, SIDE = 3, WAVE = 20, PAIRS = 4, HORDE_TICKS = 2400, CLEAR = 3, RUN_BLOWS = 1;
    /** How far from where the two were on him he is on the run for sure, how near its end the order comes, and how long after it no column may go up. */
    private static final int ON_THE_RUN = 2, NEAR_THE_END = 2, QUIET = 100;
    /**
     * The passage: where it opens into the open ground, he at its mouth; how far past him the nearer of the two stands. The closed one is
     * shut behind him: where it ends, and where he stands. The row of the arena it runs along.
     */
    private static final int MOUTH = 14, ON = 2, SHUT_AT = 6, SHUT_STAND = 8, LANE = 16;
    /** The zombie at the foot of his column: how tall the column is, how far from its middle a zombie pressed against its side stands, and how long he has to kill it. */
    private static final int TALL = 3, FOOT_TICKS = 600, ROOFED = 2;
    /** The moat of lava: where it starts, how long the moat test runs (two minutes), and how soon after «follow me» a support is out. */
    private static final int MOAT = 14, NO_LOOP_TICKS = 2400, ORDER_TICKS = 100;
    private static final double PRESSED = .8;
    /** Where the crowd stands, from the corner: across the floor from him. */
    private static final int[][] CROWD = {{-8, 0}, {-8, -2}, {-9, -3}, {-7, -5}, {-10, -1}};

    private CompanionCrowdFightTests() { }

    /** A stone floor under the whole arena, the owner beside him (a creative one: the zombies are after him alone), a sword in his hand and blocks in his bag, he at the given cell. */
    private static CompanionEntity armed(GameTestHelper h, int x, int z) {
        for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) h.setBlock(new BlockPos(i, 0, j), Blocks.STONE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, 1, z);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(c.getX() - 3, c.getY(), c.getZ() - 3);
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        c.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        // Cobblestone goes under his feet only with a pickaxe to take it back (CompanionPillar).
        c.setItem(2, new ItemStack(Items.STONE_PICKAXE));
        return c;
    }

    /** A zombie that does not burn in the daylight of the test world. */
    private static Zombie zombie(GameTestHelper h, int x, int z) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, x, 1, z);
        zombie.setPersistenceRequired();
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        return zombie;
    }

    private static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }

    /** Walls on the east and the south side of the corner, so that nothing stands there. */
    private static void corner(GameTestHelper h) {
        edge(h);
        fill(h, 0, 1, CORNER + 1, SIZE - 1, WALL, CORNER + 1);
    }

    /** A wall on the east side only: the floor runs on to the south. */
    private static void edge(GameTestHelper h) {
        fill(h, CORNER + 1, 1, 0, CORNER + 1, WALL, SIZE - 1);
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }

    private static void place(GameTestHelper h, CompanionEntity c, int x, int z) {
        BlockPos at = h.absolutePos(new BlockPos(x, 1, z));
        c.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
    }

    private static void target(List<Zombie> zombies, CompanionEntity c) {
        for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
    }

    /** Position relative to the arena corner; GameTestHelper.relativePos mirrors X and Z in a rotated arena, so the frame is read from absolutePos. */
    private static BlockPos rel(GameTestHelper h, BlockPos world) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO), dx = h.absolutePos(new BlockPos(1, 0, 0)).subtract(origin), dz = h.absolutePos(new BlockPos(0, 0, 1)).subtract(origin);
        BlockPos d = world.subtract(origin);
        return new BlockPos(d.getX() * dx.getX() + d.getZ() * dx.getZ(), d.getY(), d.getX() * dz.getX() + d.getZ() * dz.getZ());
    }

    /** A hall for the horde: the floor a few cells wide cut in solid stone, the length of the arena (cells of open floor beyond walls are no cells he can run to). */
    private static void hall(GameTestHelper h) {
        fill(h, 0, 1, 0, SIZE - 1, WALL, MIDDLE - SIDE - 1);
        fill(h, 0, 1, MIDDLE + SIDE + 1, SIZE - 1, WALL, SIZE - 1);
    }

    private static String at(GameTestHelper h, CompanionEntity c) { return rel(h, c.blockPosition()).toShortString(); }

    /**
     * Five zombies cross the floor at him, he stands in a corner of two walls with the stone sword: all five die, he takes no more than
     * {@link #BLOWS} blows, and the blocks he stood on are back in his bag.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void fiveZombiesAtHisCornerAreKilledFromAColumnAndTheBlocksComeBack(GameTestHelper h) {
        CompanionEntity c = armed(h, CORNER, CORNER);
        corner(h);
        int stone = count(c, Items.COBBLESTONE);
        List<Zombie> zombies = new ArrayList<>();
        for (int[] d : CROWD) zombies.add(zombie(h, CORNER + d[0], CORNER + d[1]));
        long begun = h.getTick();
        float[] last = {c.getHealth()};
        int[] blows = {0}, most = {0};
        h.onEachTick(() -> {
            if (c.getHealth() < last[0]) blows[0]++;
            last[0] = c.getHealth();
            most[0] = Math.max(most[0], c.getTemporarySupportCount());
            target(zombies, c);
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | zombies alive " + alive + ", blows taken " + blows[0] + ", health " + c.getHealth() + ", he at " + at(h, c) + ", supports " + c.getTemporarySupportCount() + " (most " + most[0] + ")";
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(blows[0] <= BLOWS, "The crowd got at him" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The crowd was not killed and the column taken down in time" + trace);
            if (alive == 0 && most[0] >= COLUMN && c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports() && count(c, Items.COBBLESTONE) >= stone) h.succeed();
        });
    }

    /** Under a roof of three cells, too low for a column, a crowd of three is fought as before: no block is laid, and the first of them is dead in time. */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void underALowRoofTheCrowdIsFoughtFromTheGround(GameTestHelper h) {
        CompanionEntity c = armed(h, CORNER, CORNER);
        corner(h);
        fill(h, 4, ROOF, 4, CORNER, ROOF, CORNER);
        int stone = count(c, Items.COBBLESTONE);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < LEAST_CROWD; k++) zombies.add(zombie(h, CORNER + CROWD[k][0], CORNER + CROWD[k][1]));
        long begun = h.getTick();
        h.onEachTick(() -> {
            target(zombies, c);
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | zombies alive " + alive + ", health " + c.getHealth() + ", he at " + at(h, c) + ", supports " + c.getTemporarySupportCount();
            h.assertTrue(c.getTemporarySupportCount() == 0 && count(c, Items.COBBLESTONE) == stone, "A block was laid under a roof with no room for a column" + trace);
            h.assertTrue(h.getTick() - begun < FIRST_KILL_TICKS, "None of the crowd was killed in time" + trace);
            if (alive < zombies.size()) h.succeed();
        });
    }

    /**
     * Already hurt, two zombies are a crowd for him: the column is raised, two blocks under a roof of four cells, and beside the corner
     * he stands in, where the roof hangs a cell lower over him.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void hurtHeStandsOnAColumnFromTwoZombies(GameTestHelper h) {
        CompanionEntity c = armed(h, CORNER, CORNER);
        corner(h);
        fill(h, CORNER, ROOF, CORNER, CORNER, ROOF, CORNER);
        c.setHealth(HURT);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < 2; k++) zombies.add(zombie(h, CORNER + CROWD[k][0], CORNER + CROWD[k][1]));
        long begun = h.getTick();
        h.onEachTick(() -> {
            target(zombies, c);
            String trace = " | health " + c.getHealth() + ", he at " + at(h, c) + ", supports " + c.getTemporarySupportCount();
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(h.getTick() - begun < COLUMN_TICKS, "No column was raised from two zombies at " + HURT + " health" + trace);
            if (c.getTemporarySupportCount() >= COLUMN) h.succeed();
        });
    }

    /**
     * Thrown off the column in the middle of the fight (here: set down on the floor two cells from it, as a blast would, when the last of
     * the three is left), he fights it on the ground, the column standing; with it dead he walks back to the column and its blocks are in
     * his bag again. With all three alive the first version of this test failed one run in six to fifteen, in a plain arena as in the mine:
     * the ground fight itself is lost then (a stone sword and twenty health against three zombies, dead with none of them killed), whatever
     * the column does; that is no claim about the column.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void thrownOffTheColumnHeFightsOnTheGroundAndComesBackForItsBlocks(GameTestHelper h) {
        CompanionEntity c = armed(h, CORNER, CORNER);
        edge(h);
        int stone = count(c, Items.COBBLESTONE);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < LEAST_CROWD; k++) zombies.add(zombie(h, CORNER + CROWD[k][0], CORNER + CROWD[k][1]));
        long begun = h.getTick();
        boolean[] thrown = {false};
        h.onEachTick(() -> {
            target(zombies, c);
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            if (!thrown[0] && alive == 1 && c.getTemporarySupportCount() >= COLUMN && c.onGround() && h.absolutePos(new BlockPos(0, 1 + COLUMN, 0)).getY() <= c.getY()) {
                thrown[0] = true;
                place(h, c, rel(h, c.blockPosition()).getX() - THROWN, rel(h, c.blockPosition()).getZ() - THROWN);
            }
            String trace = " | thrown " + thrown[0] + ", zombies alive " + alive + ", health " + c.getHealth() + ", he at " + at(h, c)
                    + ", supports " + c.getTemporarySupportCount() + ", cobblestone " + count(c, Items.COBBLESTONE) + " of " + stone;
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(thrown[0] || alive > 0, "The three were dead before he was thrown off, or two at once: the scene shows nothing" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The last of the three was not killed and the column taken down in time" + trace);
            if (thrown[0] && alive == 0 && c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports() && count(c, Items.COBBLESTONE) >= stone) h.succeed();
        });
    }

    /**
     * A horde that comes two by two (series ta 20261031 d3 mobs: eight killed one after another, dead at the ninth): the first two are on
     * him when the next pair makes them a crowd, and a block does not go into a cell a mob stands in. He runs clear of them over the floor
     * and raises the column there, not where they are on him: it is up with no more than {@link #RUN_BLOWS} blow taken on the way, all
     * eight die, and the blocks are back in his bag.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void aHordeThatComesTwoByTwoIsOutrunAndKilledFromAColumn(GameTestHelper h) {
        CompanionEntity c = armed(h, HERE, MIDDLE);
        hall(h);
        int stone = count(c, Items.COBBLESTONE);
        List<Zombie> zombies = new ArrayList<>();
        zombies.add(zombie(h, HERE + 2, MIDDLE - 1));
        zombies.add(zombie(h, HERE + 2, MIDDLE + 1));
        long begun = h.getTick();
        float[] last = {c.getHealth()};
        int[] blows = {0}, pairs = {1}, column = {0}, crowded = {0}, raised = {-1};
        // Where he was when the next pair made them a crowd, and where his column went up.
        BlockPos[] stood = {null, null};
        h.onEachTick(() -> {
            long ticks = h.getTick() - begun;
            if (ticks > 0 && ticks % WAVE == 0 && pairs[0] < PAIRS) {
                pairs[0]++;
                zombies.add(zombie(h, FAR_END, MIDDLE - 2));
                zombies.add(zombie(h, FAR_END, MIDDLE + 2));
            }
            if (c.getHealth() < last[0]) blows[0]++;
            last[0] = c.getHealth();
            column[0] = Math.max(column[0], c.getTemporarySupportCount());
            if (ticks == WAVE) { stood[0] = c.blockPosition(); crowded[0] = blows[0]; }
            if (stood[1] == null && c.getTemporarySupportCount() >= COLUMN) { stood[1] = c.blockPosition(); raised[0] = blows[0] - crowded[0]; }
            target(zombies, c);
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | zombies alive " + alive + " of " + zombies.size() + ", blows taken " + blows[0] + " (" + raised[0] + " from the crowd to the column), health " + c.getHealth()
                    + ", he at " + at(h, c) + ", supports " + c.getTemporarySupportCount() + " (most " + column[0] + ")";
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(raised[0] <= RUN_BLOWS, "The horde got at him before the column was up" + trace);
            h.assertTrue(stood[0] == null || stood[1] == null || Math.hypot(stood[1].getX() - stood[0].getX(), stood[1].getZ() - stood[0].getZ()) >= CLEAR,
                    "The column went up where the two were on him, at " + (stood[1] == null ? "" : rel(h, stood[1]).toShortString()) + trace);
            h.assertTrue(ticks < HORDE_TICKS, "The horde was not killed and the column taken down in time" + trace);
            if (pairs[0] == PAIRS && alive == 0 && column[0] >= COLUMN && c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports() && count(c, Items.COBBLESTONE) >= stone) h.succeed();
        });
    }

    /**
     * An order of the player on the run ends it, as it takes a column down: under the guard order he stays on the ground, and no block
     * is laid where the order stopped him, be it a step from the cell he ran to.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void aGuardOrderAStepFromTheEndOfTheRunLeavesNoColumn(GameTestHelper h) {
        CompanionEntity c = armed(h, HERE, MIDDLE);
        hall(h);
        List<Zombie> zombies = new ArrayList<>();
        zombies.add(zombie(h, HERE + 2, MIDDLE - 1));
        zombies.add(zombie(h, HERE + 2, MIDDLE + 1));
        long begun = h.getTick();
        BlockPos[] from = {null};
        long[] ordered = {-1};
        h.onEachTick(() -> {
            long ticks = h.getTick() - begun;
            if (ticks == WAVE) {
                zombies.add(zombie(h, FAR_END, MIDDLE - 2));
                zombies.add(zombie(h, FAR_END, MIDDLE + 2));
                from[0] = c.blockPosition();
            }
            target(zombies, c);
            String trace = " | tick " + ticks + ", ordered at " + ordered[0] + ", health " + c.getHealth() + ", he at " + at(h, c) + ", supports " + c.getTemporarySupportCount();
            if (ordered[0] < 0) {
                h.assertTrue(ticks < COLUMN_TICKS, "He did not run clear of the two on him" + trace);
                if (from[0] == null || c.getTemporarySupportCount() > 0 || Math.hypot(c.getX() - from[0].getX() - .5, c.getZ() - from[0].getZ() - .5) < ON_THE_RUN) return;
                var way = c.getNavigation().getPath();
                if (way == null || way.isDone() || way.getEndNode() == null || c.distanceToSqr(way.getEndNode().x + .5, way.getEndNode().y, way.getEndNode().z + .5) > NEAR_THE_END * NEAR_THE_END) return;
                ordered[0] = ticks;
                c.setProtecting(true);
                return;
            }
            h.assertTrue(c.getTemporarySupportCount() == 0, "A column went up after the guard order" + trace);
            if (ticks - ordered[0] >= QUIET) h.succeed();
        });
    }

    /**
     * The run clear of them does not lead through them (series te 20261031 n1 mobs: hurt, two zombies on him on a ledge, the open ground
     * beyond them; he walked into them for forty ticks, three blows taken and none dealt, and died). In a passage closed behind him, the
     * two between him and the rest of it, he stays on his side of them and the column goes up where he stands.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void theRunClearOfThemDoesNotLeadThroughThem(GameTestHelper h) {
        CompanionEntity c = armed(h, SHUT_STAND, LANE);
        passage(h, SHUT_AT);
        betweenHimAndTheOpenGround(h, c, SHUT_STAND, 0);
    }

    /**
     * And it is looked for behind him before beyond them: at the mouth of a passage, the two between him and open ground with every far
     * cell for a column, the few searches are not spent on the ways through them, and he runs back along the passage to a column there.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void theRunClearOfThemIsLookedForBehindHimFirst(GameTestHelper h) {
        CompanionEntity c = armed(h, MOUTH, LANE);
        passage(h, 0);
        betweenHimAndTheOpenGround(h, c, MOUTH, CLEAR);
    }

    /** A passage along the arena, one cell wide and five high, cut in solid stone from this cell to the mouth, open beyond it; shut at its start unless that is the arena's edge. */
    private static void passage(GameTestHelper h, int from) {
        fill(h, from, 1, 0, MOUTH, HIGH + 1, SIZE - 1);
        for (int x = from; x <= MOUTH; x++) for (int y = 1; y <= HIGH; y++) h.setBlock(new BlockPos(x, y, LANE), Blocks.AIR);
    }

    /** Hurt, he stands at this cell of the passage with two zombies next beyond him: he never gets past one of them, and his column goes up at least this far back. */
    private static void betweenHimAndTheOpenGround(GameTestHelper h, CompanionEntity c, int stand, int back) {
        c.setHealth(HURT);
        List<Zombie> zombies = List.of(zombie(h, stand + ON, LANE), zombie(h, stand + ON + 1, LANE));
        long begun = h.getTick();
        h.onEachTick(() -> {
            target(zombies, c);
            String trace = " | tick " + (h.getTick() - begun) + ", health " + c.getHealth() + ", he at " + at(h, c) + ", stood at " + stand + ", supports " + c.getTemporarySupportCount();
            h.assertTrue(c.isAlive(), "Died" + trace);
            for (Zombie zombie : zombies) h.assertTrue(!zombie.isAlive() || rel(h, c.blockPosition()).getX() < rel(h, zombie.blockPosition()).getX(), "He ran into the two on him" + trace);
            h.assertTrue(h.getTick() - begun < COLUMN_TICKS, "No column was raised clear of the two" + trace);
            if (c.getTemporarySupportCount() < COLUMN) return;
            h.assertTrue(rel(h, c.blockPosition()).getX() + .5 <= stand + .5 - back, "The column went up where the two were on him" + trace);
            h.succeed();
        });
    }

    /**
     * The order of events of the live game, with no target set by hand: «stay», three husks that stand four cells from him with a creative
     * owner a few cells away, nobody after anyone. He engages the nearest as he does without an order (a mob within four cells of him), his
     * first blow turns the others of its kind on him (HurtByTargetGoal), and only then are three of them after him; by then they are on
     * him already. In game 25.11 his feet never rose above the floor in three runs of three, and he lost 3, 6 and 15 health on the ground:
     * there was no pickaxe in his bag, and cobblestone goes under his feet only with one to take it back ({@link #armed}). With it the column
     * is up some sixty ticks after the first blow, before the three are on him; without it (the same test, the pickaxe taken out) no block
     * is laid and the three blows of the ground fight are taken.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void husksThatTurnOnHimAfterHisFirstBlowAreMetFromAColumn(GameTestHelper h) {
        CompanionEntity c = armed(h, HERE, MIDDLE);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        c.setItem(1, ItemStack.EMPTY);
        c.setFollowing(false);
        int stone = count(c, Items.COBBLESTONE);
        List<Husk> husks = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            Husk husk = h.spawn(EntityType.HUSK, HERE + 4, 1, MIDDLE + dz);
            husk.setPersistenceRequired();
            husk.teleportTo(c.getX() + 3.5, c.getY(), husk.getZ());
            husks.add(husk);
        }
        long begun = h.getTick();
        float[] last = {c.getHealth()};
        int[] blows = {0}, most = {0};
        h.onEachTick(() -> {
            if (c.getHealth() < last[0]) blows[0]++;
            last[0] = c.getHealth();
            most[0] = Math.max(most[0], c.getTemporarySupportCount());
            long alive = husks.stream().filter(Husk::isAlive).count();
            String trace = " | tick " + (h.getTick() - begun) + ", husks alive " + alive + ", blows taken " + blows[0] + ", health " + c.getHealth() + ", he at " + at(h, c)
                    + ", supports " + c.getTemporarySupportCount() + " (most " + most[0] + "), after him " + husks.stream().filter(husk -> husk.getTarget() == c).count();
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(alive == 0 || most[0] >= COLUMN || h.getTick() - begun < COLUMN_TICKS, "No column went up as the three turned on him" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The husks were not killed and the column taken down in time" + trace);
            if (alive == 0 && most[0] >= COLUMN && c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports() && count(c, Items.COBBLESTONE) >= stone) h.succeed();
        });
    }

    /** A diamond sword and armor: what these tests claim is about the column, not about who wins a ground fight. */
    private static void tough(CompanionEntity c) {
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        c.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        c.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        c.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        c.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
    }

    /**
     * A column that does not go up in the middle of the crowd is not taken down in its face: a roof is laid over it when its first support
     * stands (the third would not go up: «Над опорой не хватает свободного места»), and with three zombies on him he keeps the support and
     * fights from it. The descent (and with it the goal that holds him from striking while it goes on) waits until the last of them is
     * dead; then the block is in his bag again.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void aColumnThatDoesNotGoUpIsNotTakenDownWhileTheCrowdIsAbout(GameTestHelper h) {
        CompanionEntity c = armed(h, MIDDLE, MIDDLE);
        tough(c);
        int stone = count(c, Items.COBBLESTONE);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < LEAST_CROWD; k++) zombies.add(zombie(h, MIDDLE + 6, MIDDLE + 2 * k - 2));
        long begun = h.getTick();
        boolean[] roofed = {false};
        int[] most = {0};
        h.onEachTick(() -> {
            target(zombies, c);
            if (!roofed[0] && c.getTemporarySupportCount() == 1 && c.onGround()) {
                roofed[0] = true;
                h.setBlock(rel(h, c.blockPosition().above(ROOFED)), Blocks.STONE);
            }
            most[0] = Math.max(most[0], c.getTemporarySupportCount());
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | tick " + (h.getTick() - begun) + ", roofed " + roofed[0] + ", zombies alive " + alive + ", health " + c.getHealth() + ", he at " + at(h, c)
                    + ", supports " + c.getTemporarySupportCount() + " (most " + most[0] + ")" + ", recovering " + c.isRecoveringSupports();
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(!roofed[0] || alive == 0 || c.getTemporarySupportCount() >= 1, "The support was taken down with the crowd about" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The crowd was not killed and the support taken down in time" + trace);
            if (alive == 0 && roofed[0]) h.assertTrue(most[0] == 1, "The column went up after all: the scene shows nothing" + trace);
            if (alive == 0 && roofed[0] && c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports() && count(c, Items.COBBLESTONE) >= stone) h.succeed();
        });
    }

    /**
     * A baby zombie is out of the blade's reach from a column of three: from the eye on it (4.6 over the floor) to its head is more than three
     * blocks. With two of them among the three zombies after him the column is of two, and all five die of his blows from it.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void withBabyZombiesAmongThemTheColumnIsTheLowOne(GameTestHelper h) {
        CompanionEntity c = armed(h, MIDDLE, MIDDLE);
        tough(c);
        int stone = count(c, Items.COBBLESTONE);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < LEAST_CROWD + 2; k++) {
            Zombie zombie = zombie(h, MIDDLE + 6, MIDDLE + 2 * k - 4);
            zombie.setBaby(k >= LEAST_CROWD);
            zombies.add(zombie);
        }
        long begun = h.getTick();
        int[] most = {0};
        h.onEachTick(() -> {
            target(zombies, c);
            most[0] = Math.max(most[0], c.getTemporarySupportCount());
            long alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | tick " + (h.getTick() - begun) + ", zombies alive " + alive + ", health " + c.getHealth() + ", he at " + at(h, c)
                    + ", supports " + c.getTemporarySupportCount() + " (most " + most[0] + ")";
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(most[0] <= COLUMN, "A column of three against a baby zombie, out of the reach of his blade from it" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The five were not killed and the column taken down in time" + trace);
            if (alive == 0 && most[0] == COLUMN && c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports() && count(c, Items.COBBLESTONE) >= stone) h.succeed();
        });
    }

    /**
     * Three zombies after him that cannot get at him, across a moat of lava three cells wide the whole width of the floor, are no crowd: a
     * column against them would go up, wait out its patience with nothing in the blade's reach, come down when the wait is over and go up
     * again for ever, with his orders held all that time. No block is laid in two minutes.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void zombiesAfterHimAcrossAMoatAreNoCrowdAndNoColumnGoesUp(GameTestHelper h) {
        CompanionEntity c = armed(h, MOAT - 4, MIDDLE);
        for (int x = MOAT; x < MOAT + 3; x++) for (int z = 0; z < SIZE; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.LAVA);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < LEAST_CROWD; k++) zombies.add(zombie(h, MOAT + 5, MIDDLE + 2 * k - 2));
        long begun = h.getTick();
        h.onEachTick(() -> {
            target(zombies, c);
            long ticks = h.getTick() - begun;
            long after = zombies.stream().filter(zombie -> zombie.getTarget() == c).count();
            String trace = " | tick " + ticks + ", after him " + after + ", health " + c.getHealth() + ", he at " + at(h, c) + ", supports " + c.getTemporarySupportCount() + ", recovering " + c.isRecoveringSupports();
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports(), "A column went up against zombies that cannot cross the moat" + trace);
            if (ticks >= NO_LOOP_TICKS) h.succeed();
        });
    }

    /**
     * «Follow me» while his column stands with the crowd near: the descent starts at once, as it did before the goal that raised the
     * column held it for a fight (that wait is for the goal's own descent, not for an order of the player): a block is out within a few
     * seconds, with all three still alive.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void anOrderToFollowTakesHisColumnDownAtOnceWithTheCrowdNear(GameTestHelper h) {
        CompanionEntity c = armed(h, MIDDLE, MIDDLE);
        tough(c);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < LEAST_CROWD; k++) zombies.add(zombie(h, MIDDLE + 6, MIDDLE + 2 * k - 2));
        long begun = h.getTick();
        long[] ordered = {-1};
        h.onEachTick(() -> {
            target(zombies, c);
            long ticks = h.getTick() - begun, alive = zombies.stream().filter(Zombie::isAlive).count();
            String trace = " | tick " + ticks + ", ordered at " + ordered[0] + ", zombies alive " + alive + ", health " + c.getHealth() + ", supports " + c.getTemporarySupportCount() + ", recovering " + c.isRecoveringSupports();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (ordered[0] < 0) {
                h.assertTrue(ticks < COLUMN_TICKS, "No column went up" + trace);
                if (c.getTemporarySupportCount() >= COLUMN && c.onGround() && alive == LEAST_CROWD) { ordered[0] = ticks; c.setFollowing(true); }
                return;
            }
            h.assertTrue(ticks - ordered[0] < ORDER_TICKS, "The column was not taken down at once on the order" + trace);
            if (c.getTemporarySupportCount() < COLUMN) h.succeed();
        });
    }

    /**
     * A zombie pressed against the side of his column is under the edge of its top block: from the middle of the column his eyes do
     * not meet its eyes (in game 24.11: with the others dead he saw nobody, took the column down onto it, and went from 12 health to 3).
     * He leans over the edge as a player does: the column stands while the zombie lives, and it dies of his blows from above.
     */
    @GameTest(template = "empty", timeoutTicks = TIMEOUT)
    public static void aZombieAtTheFootOfHisColumnIsSeenAndStruckFromIt(GameTestHelper h) {
        CompanionEntity c = armed(h, MIDDLE, MIDDLE);
        c.setHealth(HURT);
        List<Zombie> zombies = new ArrayList<>();
        for (int k = 0; k < 2; k++) zombies.add(zombie(h, MIDDLE + 6, MIDDLE + 2 * k));
        long begun = h.getTick();
        long[] stood = {-1};
        double[] at = new double[3];
        Zombie[] left = {zombies.get(0)};
        h.onEachTick(() -> {
            long ticks = h.getTick() - begun;
            Zombie pressed = left[0];
            // Against the side of the column, as near as its body goes to it: set before anything asks his eyes this tick.
            if (stood[0] >= 0 && pressed.isAlive()) {
                pressed.teleportTo(at[0], at[1], at[2]);
                pressed.setDeltaMovement(0, 0, 0);
            }
            target(zombies, c);
            String trace = " | tick " + ticks + ", on the column since " + stood[0] + ", health " + c.getHealth() + ", zombie health " + pressed.getHealth() + ", in his sight " + c.hasLineOfSight(pressed)
                    + ", he at " + c.position() + ", zombie at " + pressed.position() + ", supports " + c.getTemporarySupportCount();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (stood[0] < 0) {
                h.assertTrue(ticks < COLUMN_TICKS, "No column of three was raised" + trace);
                if (c.getTemporarySupportCount() < TALL || !c.onGround()) return;
                stood[0] = ticks;
                at[0] = Math.floor(c.getX()) + .5 - PRESSED;
                at[1] = Math.floor(c.getY()) - TALL;
                at[2] = Math.floor(c.getZ()) + .5;
                left[0] = zombies.get(zombies.get(0).isAlive() ? 0 : 1);
                h.assertTrue(left[0].isAlive(), "Both zombies were dead before he stood on the column" + trace);
                for (Zombie zombie : zombies) if (zombie != left[0]) zombie.discard();
                // In the middle of the column, as he stands on it when the blow of a mob does not move him: the sight over its edge is what the scene tests.
                c.teleportTo(at[0] + PRESSED, c.getY(), at[2]);
                return;
            }
            if (!pressed.isAlive()) { h.succeed(); return; }
            if (ticks == stood[0] + 1) h.assertTrue(!c.hasLineOfSight(pressed), "The zombie at the foot of the column is in his plain sight: the scene shows nothing" + trace);
            h.assertTrue(c.getTemporarySupportCount() >= TALL && !c.isRecoveringSupports(), "He took the column down onto the zombie at its foot" + trace);
            h.assertTrue(ticks - stood[0] < FOOT_TICKS, "The zombie at the foot of his column was not killed" + trace);
        });
    }
}

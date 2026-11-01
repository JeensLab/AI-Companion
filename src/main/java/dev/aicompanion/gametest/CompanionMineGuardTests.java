package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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
import static dev.aicompanion.gametest.CompanionMineFightTests.zombie;

/** The creeper guard with several creepers about, and what else may take his turn while one is near. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineGuardTests {
    private static final int FUSE_TICKS = 200, STAND = 6, LIT_AT = 8, FRESH_AT = 1;
    private static final double SLACK = .25;
    private static final float KEPT = 10, WEAK = 5;
    private static final int PIT_FROM = 8, PIT_TO = 10, PIT_DEPTH = 4, DROP_AT = 12, PASSAGE_LENGTH = 4, PASSAGE_OFFSET = 2, GRACE_TICKS = 6, MAX_CHANGES = 2, HOLD_TICKS = 80, RELEASED_TICKS = 400;
    private static final double CLOSER = 1.5;
    private static final int ROOM_FROM = 4, ROOM_TO = 16, ROOM_SIDE = 6, RUN_TICKS = 20, CLIFF_DEPTH = 6, DEAD_END_LENGTH = 3;
    private static final double RUN_GAP = 4, FALL = .5;

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

    /**
     * The fuse is lit 1.5 blocks off, on the last step of the stairs below him, at the foot of the mine: the fuse is 30 ticks and a blast kills from full health
     * close to it, so in 20 ticks he must be 4 blocks off (about 10 damage there), not on the way somewhere that he reaches late.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseOnTheStepGetsHimFourBlocksOffInTwentyTicks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        chamber(h, stairs);
        place(c, stairs.get(stairs.size() - 2));
        Creeper creeper = creeper(h, stairs.get(stairs.size() - 1));
        watchRun(h, c, creeper, false);
    }

    /**
     * He stands in a closed room cut off from the stairs (a pocket of a cave), the stairs lie on the far side of a creeper two blocks off
     * that lights its fuse: the stairs cannot be reached, so he does not set off for them, and he keeps away from the creeper instead.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseInAClosedRoomDoesNotSendHimToTheStairsPastIt(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = carveMine(h, c, 0).get(carveMine(h, c, 0).size() - 1);
        for (int dx = ROOM_FROM; dx <= ROOM_TO; dx++) for (int dz = -ROOM_SIDE; dz <= ROOM_SIDE; dz++) for (int dy = 0; dy < 2; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, foot.offset(ROOM_TO - 2, 0, 0));
        Creeper creeper = creeper(h, foot.offset(ROOM_TO - 4, 0, 0));
        watchRun(h, c, creeper, false);
    }

    /** A drop opens along one side of the chamber and the lit creeper stands on the other: the way away from it is along the drop, and he does not fall. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseDoesNotSendHimOverAnEdge(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        for (int dx = 0; dx <= ROOM_TO - 4; dx++) for (int dz = 2; dz <= ROOM_SIDE; dz++) for (int dy = -CLIFF_DEPTH; dy < 0; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, foot.offset(STAND, 0, 0));
        Creeper creeper = creeperAt(h, foot, STAND + .5, -1.5);
        watchRun(h, c, creeper, true);
    }

    /** He is at the dead end of a one-block corridor and the lit creeper is in its mouth: there is no way off, so he strikes it (its health falls) and lives or not by that, but never stands. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseWithNoWayOffIsAnsweredWithTheSword(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        for (int dx = ROOM_FROM; dx <= ROOM_FROM + DEAD_END_LENGTH; dx++) for (int dy = 0; dy < 2; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, 0), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, foot.offset(ROOM_FROM + DEAD_END_LENGTH, 0, 0));
        Creeper creeper = creeperAt(h, foot, ROOM_FROM + DEAD_END_LENGTH + .5 - 1.5, 0);
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        creeper.ignite();
        start(h, c, 1);
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "health " + creeper.getHealth() : "gone") + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.isAlive()) {
                h.assertTrue(creeper.getHealth() < creeper.getMaxHealth() || creeper.isDeadOrDying() || h.getTick() < RUN_TICKS, "He stood and did not strike" + trace);
                if (creeper.getHealth() < creeper.getMaxHealth()) h.succeed();
                return;
            }
            h.assertTrue(false, "The creeper blew up, it was not struck" + trace);
        });
    }

    /** Hurt below what a creeper is fought at, the fuse is lit a block off, at his side: he never closes on it (a path may lead past it), is four blocks off in twenty ticks and alive. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseOneBlockOffWhileTooHurtToFightGetsHimFourBlocksOffInTwentyTicks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(STAND, 0, 0));
        c.setHealth(WEAK);
        Creeper creeper = creeperAt(h, foot, STAND - .5, 0);
        double[] first = {-1};
        watchRun(h, c, creeper, true, true, () -> {
            double gap = c.distanceTo(creeper);
            if (first[0] < 0) first[0] = gap;
            h.assertTrue(!creeper.isAlive() || gap >= first[0] - SLACK, "He closed on the lit fuse: " + String.format("%.2f", gap) + " from " + String.format("%.2f", first[0]));
        });
    }

    /** The creeper stands still and its fuse is lit; he is 4 blocks off in 20 ticks, lives and, on a level floor, never falls. */
    private static void watchRun(GameTestHelper h, CompanionEntity c, Creeper creeper, boolean level) {
        watchRun(h, c, creeper, level, true, () -> { });
    }

    private static void watchRun(GameTestHelper h, CompanionEntity c, Creeper creeper, boolean level, boolean order, Runnable each) {
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        creeper.ignite();
        double floor = c.getY();
        if (order) start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            each.run();
            long elapsed = h.getTick() - begun;
            double gap = c.distanceTo(creeper);
            String trace = " | tick " + elapsed + ", gap " + String.format("%.1f", gap) + (creeper.isAlive() ? "" : " (gone)")
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(!level || c.getY() > floor - FALL, "He fell" + trace);
            if (elapsed == RUN_TICKS) h.assertTrue(!creeper.isAlive() || gap >= RUN_GAP, "Less than 4 blocks off in 20 ticks" + trace);
            if (creeper.isAlive()) {
                h.assertTrue(elapsed < FUSE_TICKS, "The lit creeper never blew" + trace);
                return;
            }
            h.succeed();
        });
    }

    /**
     * Hurt below what a creeper is fought at (five health: a blast beyond five blocks costs him about five), the fuse is lit two and a half blocks off:
     * the dash that the sword in hand has is his too, not a walk by a path, and he is four blocks off in twenty ticks and alive.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseWhileTooHurtToFightGetsHimFourBlocksOffInTwentyTicks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(STAND, 0, 0));
        c.setHealth(WEAK);
        watchRun(h, c, creeperAt(h, foot, STAND + 3, 0), true);
    }

    /** No order and no expedition (the mine is done, a torch is being made): the fuse lit two and a half blocks off gets him four blocks off in twenty ticks too. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseWithNoOrderGetsHimFourBlocksOffInTwentyTicks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(STAND, 0, 0));
        watchRun(h, c, creeperAt(h, foot, STAND + 3, 0), true, false, () -> { });
    }

    /** A zombie stands on him and the fuse of a creeper two and a half blocks off is lit: the zombie is no reason to wait out the fuse, he is four blocks off the creeper in twenty ticks. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseWithAZombieOnHimGetsHimFourBlocksOffInTwentyTicks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(STAND, 0, 0));
        Zombie zombie = zombie(h, foot.offset(STAND - 1, 0, 0));
        watchRun(h, c, creeperAt(h, foot, STAND + 3, 0), true, true, () -> { if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c); });
    }

    private static Creeper creeperAt(GameTestHelper h, BlockPos foot, double dx, double dz) {
        Creeper creeper = creeper(h, foot);
        creeper.setPos(foot.getX() + dx, foot.getY(), foot.getZ() + .5 + dz);
        return creeper;
    }

    /**
     * A creeper stands in a pit four blocks under the floor, in sight at the pit's edge: it is no foe (too far below) but it is near, and a drop
     * lies on the far side of the pit. He does not walk to the drop while the creeper lives; once it is gone he takes the drop.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLiveCreeperNearKeepsHimFromWalkingToADrop(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(PIT_FROM - 1, 0, 0));
        Creeper creeper = creeperInAPit(h, foot);
        BlockPos at = foot.offset(DROP_AT, 0, 0);
        ItemEntity drop = new ItemEntity(h.getLevel(), at.getX() + .5, at.getY(), at.getZ() + .5, new ItemStack(Items.COBBLESTONE));
        drop.setDeltaMovement(Vec3.ZERO);
        drop.setNoPickUpDelay();
        h.getLevel().addFreshEntity(drop);
        c.claimDrops(List.of(drop));
        start(h, c, 1);
        long begun = h.getTick();
        double[] from = {-1};
        h.onEachTick(() -> {
            if (from[0] < 0) from[0] = c.distanceTo(drop);
            long elapsed = h.getTick() - begun;
            String trace = " | creeper " + (creeper.isAlive() ? "lives" : "gone") + ", drop " + (drop.isAlive() ? "lies " + String.format("%.1f", c.distanceTo(drop)) + " off, was " + String.format("%.1f", from[0]) : "taken")
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (elapsed < HOLD_TICKS) {
                h.assertTrue(drop.isAlive() && c.distanceTo(drop) > from[0] - CLOSER, "He went for the drop with the creeper near" + trace);
                return;
            }
            if (elapsed == HOLD_TICKS) creeper.discard();
            if (!drop.isAlive()) h.succeed();
            h.assertTrue(elapsed < HOLD_TICKS + RELEASED_TICKS, "SETUP: the drop was not taken once the creeper was gone" + trace);
        });
    }

    /**
     * The breach of a cave with mobs in it is being sealed and a creeper in the cave has its fuse lit: nothing is sealed meanwhile, the
     * stone sword stays in his hand (it does not go back and forth with the pickaxe) until the creeper has blown.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseNearKeepsTheSwordInHandWhileTheCaveIsBeingSealed(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = -PASSAGE_LENGTH; dx <= 1; dx++) for (int dy = 0; dy < 2; dy++)
            h.getLevel().setBlock(foot.offset(dx, dy, -PASSAGE_OFFSET), Blocks.AIR.defaultBlockState(), 2 | 16);
        place(c, stairs.get(stairs.size() - 3));
        for (int dx : new int[]{-3, -4}) zombie(h, foot.offset(dx, 0, -PASSAGE_OFFSET)).setNoAi(true);
        Creeper creeper = creeper(h, foot.offset(-1, 0, -PASSAGE_OFFSET));
        creeper.setNoAi(true);
        long begun = h.getTick();
        long[] lit = {-1};
        int[] swaps = {0};
        Item[] held = {c.getMainHandItem().getItem()};
        start(h, c, 1);
        h.onEachTick(() -> {
            String phase = c.expeditionCavePhase();
            if (lit[0] < 0 && phase.equals("SEAL")) {
                lit[0] = h.getTick();
                creeper.setNoAi(false);
                creeper.ignite();
            }
            if (c.getMainHandItem().getItem() != held[0]) { swaps[0]++; held[0] = c.getMainHandItem().getItem(); }
            String trace = " | cave phase " + phase + ", creeper " + (creeper.isAlive() ? "lit" : "gone") + ", hand " + held[0] + ", changes " + swaps[0] + ", he at "
                    + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            if (lit[0] < 0) { h.assertTrue(h.getTick() - begun < FUSE_TICKS * 5, "SETUP: the cave was never sealed" + trace); return; }
            if (!creeper.isAlive()) { h.succeed(); return; }
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (h.getTick() - lit[0] > GRACE_TICKS) h.assertTrue(held[0] == Items.STONE_SWORD, "The sword was not in his hand with a fuse lit" + trace);
            h.assertTrue(swaps[0] <= MAX_CHANGES, "The tool went back and forth" + trace);
        });
    }

    /** A creeper at the bottom of a pit dug under the floor of the chamber, still (it has no legs to climb with). */
    private static Creeper creeperInAPit(GameTestHelper h, BlockPos foot) {
        for (int dx = PIT_FROM; dx <= PIT_TO; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -PIT_DEPTH; dy < 0; dy++)
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        Creeper creeper = creeper(h, foot.offset((PIT_FROM + PIT_TO) / 2, -PIT_DEPTH, 0));
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        return creeper;
    }
}

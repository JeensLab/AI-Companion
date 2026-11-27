package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.aicompanion.gametest.CompanionMineArena.CENTER;
import static dev.aicompanion.gametest.CompanionMineArena.GROUND;
import static dev.aicompanion.gametest.CompanionMineArena.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineArena.carveMine;
import static dev.aicompanion.gametest.CompanionMineArena.count;
import static dev.aicompanion.gametest.CompanionMineArena.ended;
import static dev.aicompanion.gametest.CompanionMineArena.mountain;
import static dev.aicompanion.gametest.CompanionMineArena.rel;
import static dev.aicompanion.gametest.CompanionMineArena.report;
import static dev.aicompanion.gametest.CompanionMineArena.rig;
import static dev.aicompanion.gametest.CompanionMineArena.start;
import static dev.aicompanion.gametest.CompanionMineFightTests.armed;
import static dev.aicompanion.gametest.CompanionMineFightTests.chamber;
import static dev.aicompanion.gametest.CompanionMineFightTests.creeper;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;
import static dev.aicompanion.gametest.CompanionMineFightTests.zombie;

/**
 * A creeper on his way in the mine: the stairs are shut against one that can walk to him, with a block of his own that he takes up again,
 * and one that cannot come is let be; a lit fuse on the stairs, with other creepers about; a creeper with no fuse and a zombie on him.
 * Unless a test says so the creeper is live: it walks, it swells, it explodes.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineGuardTests {
    private static final int START_STAND = 10, CREEPER_STAND = 14, STEP_AWAY_TICKS = 200, OPEN_CAVE_TICKS = 600, CHAMBER_REACH = 13, CHAMBER_HEIGHT = 4, OPEN_CAVE_STAND = 3;
    /** The short mine: its foot, the creeper's stand beyond it, how far into the next stand he stands (a quarter of his width), the pit under the foot. */
    private static final int SHORT_FOOT = 10, BEYOND_FOOT = 4, SHUT_TICKS = 100, PIT_LENGTH = 4, PIT_DEPTH = 4, ABOVE_HIM = 5;
    private static final double ON_THE_EDGE = .07;
    /** A creeper that stays at the block this long has not wandered off as one would; it is taken away, as a despawn or a stroll would. */
    private static final int DISMISS_TICKS = 400;
    private static final int FUSE_TICKS = 200, STAND = 6, LIT_AT = 8, FRESH_AT = 1, RUN_TICKS = 20, UP_THE_STAIRS = 5, SHUT_UNDER = 4, PIT = 2;
    private static final double SLACK = .25, RUN_GAP = 4, TOWARDS_HIM = .3;
    private static final float KEPT = 10, HURT = 12, TWO_BLOWS = 9;
    private static final int FAR_STAND = 10, FAR_ZOMBIE = 8, CREEPER_SIDE = 5, FIGHT_TICKS = 100, PASSAGE_LENGTH = 4, PASSAGE_OFFSET = 2, GRACE_TICKS = 6, MAX_CHANGES = 2;
    /** The cave of run u1d/void-4 at the height of the arena: his cell in the plugs, the slit at his eye on its south side, and the creeper beyond it on a ledge. */
    private static final int NICHE_X = 24, NICHE_Y = 14, NICHE_Z = 21, CAVE_LOW_X = 20, CAVE_HIGH_X = 30, CAVE_LOW_Z = 18, CAVE_HIGH_Z = 30, CAVE_HEIGHT = 5, NICHE_ROOF = 3;
    private static final double CREEPER_OFF = 3.1;
    /** The beat of his bob and the ticks of it they saw each other, too few for the fuse to burn down; well past a fuse's 30 ticks, with some beats to strike in, it is over one way or the other. */
    private static final int BEAT_TICKS = 20, SIGHT_TICKS = 8, SETTLED_TICKS = 1200;
    private static final String SENT_OFF = "Рядом крипер, ухожу от него.", CREEPER_NEAR = "Рядом крипер, ";

    private CompanionMineGuardTests() { }

    /**
     * A creeper that stands still (alive, its speed is nil) waits four stairs below him: he shuts the stairs with a block of his own
     * between them; once it is gone he takes the block up again and digs on.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void shutsTheStairsOnACreeperBelowAndTakesTheBlockUp(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        place(c, stairs.get(START_STAND));
        Creeper creeper = still(creeper(h, stairs.get(CREEPER_STAND)));
        CompanionMineSite site = c.mineSite();
        int blocks = count(c, Items.COBBLESTONE);
        long id = start(h, c, 1);
        long begun = h.getTick();
        boolean[] shut = {false};
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            if (!shut[0]) {
                shut[0] = !site.temporary().isEmpty();
                h.assertTrue(shut[0] || h.getTick() - begun < STEP_AWAY_TICKS, "He did not shut the stairs against the creeper: at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
                if (!shut[0]) return;
                h.assertTrue(count(c, Items.COBBLESTONE) == blocks - site.temporary().size(), "The block on the stairs did not come out of his bag | " + report(c));
                creeper.discard();
                return;
            }
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = c.workResult(id);
            h.assertTrue(result != null && result.success() && count(c, Items.DIAMOND) >= 1, "He did not dig on after the creeper: " + report(c));
            h.assertTrue(site.temporary().isEmpty(), "His block is still on the stairs: " + site.temporary() + " | " + report(c));
            h.succeed();
        });
    }

    /** He digs at the foot of the stairs and a creeper comes down them behind him. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperComingDownTheStairsBehindHimLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        place(c, stairs.get(foot - 2));
        goesOn(h, c, creeper(h, stairs.get(foot - 9)), start(h, c, 1), true);
    }

    /** He walks down the stairs and a creeper comes up them to meet him. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperComingUpTheStairsToMeetHimLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        place(c, stairs.get(2));
        goesOn(h, c, creeper(h, stairs.get(12)), start(h, c, 1), true);
    }

    /** No pickaxe is left, so he climbs, and a creeper waits on the stairs above him. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperOnTheStairsDuringTheClimbLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.STONE_PICKAXE) || c.getItem(slot).is(Items.IRON_PICKAXE)) c.setItem(slot, ItemStack.EMPTY);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        int foot = stairs.size() - 1;
        place(c, stairs.get(foot - 1));
        goesOn(h, c, creeper(h, stairs.get(foot - 8)), start(h, c, 1), false);
    }

    /** Alive all along; the order ends (with diamond when {@code mines}) and the stairs still stand after the mine is checked. */
    private static void goesOn(GameTestHelper h, CompanionEntity c, Creeper creeper, long id, boolean mines) {
        CompanionMineSite site = c.mineSite();
        int stands = site.stands().size();
        double[] last = {-1, 99};
        long[] shutAt = {-1};
        h.onEachTick(() -> {
            if (creeper.isAlive()) { last[0] = creeper.distanceTo(c); last[1] = Math.min(last[1], last[0]); }
            if (shutAt[0] < 0 && !site.temporary().isEmpty()) shutAt[0] = h.getTick();
            if (creeper.isAlive() && shutAt[0] >= 0 && h.getTick() - shutAt[0] > DISMISS_TICKS) creeper.discard();
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", last distance " + Math.round(last[0] * 10) / 10.0 + ", nearest " + Math.round(last[1] * 10) / 10.0
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + ", blocks " + blocks(h, c) + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(!mines || result.success() && count(c, Items.DIAMOND) >= 1, "The order did not go on" + trace);
            int kept = site.verify(h.getLevel());
            h.assertTrue(kept == stands, "The stairs were cut: " + kept + " of " + stands + " stands" + trace);
            h.succeed();
        });
    }

    /** He is three blocks into a wide open chamber at the foot of the stairs, no order given, and a creeper comes across it from the far wall: he gets to the stairs, shuts them and lives. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperInAnOpenCaveLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = 0; dx <= CHAMBER_REACH; dx++) for (int dz = -CHAMBER_REACH / 2; dz <= CHAMBER_REACH / 2; dz++) for (int dy = 0; dy < CHAMBER_HEIGHT; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(CHAMBER_REACH, 0, 0));
        long begun = h.getTick();
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + ", creeper " + creeper.isAlive() + " | " + report(c));
            if (h.getTick() - begun < OPEN_CAVE_TICKS) return;
            h.assertTrue(creeper.isRemoved() || creeper.distanceTo(c) > 3, "The creeper is at his side: " + creeper.distanceTo(c) + " | " + report(c));
            h.succeed();
        });
    }

    /**
     * He stands half over the next stand down, as the guard stops his walk on the stairs (series m and n of PR #195), and a creeper that can walk
     * up to him waits in sight beyond the foot. The block goes into the head of that stand once he has stepped back into his own; before, his own
     * body was in the cell, every try was refused and the guard began again with the same cell for ever.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void onTheEdgeOfHisStandHeStepsBackToShutTheWay(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        List<BlockPos> stairs = shortMine(h, c, SHORT_FOOT + BEYOND_FOOT);
        BlockPos stand = stairs.get(SHORT_FOOT - 1), shut = stairs.get(SHORT_FOOT).above();
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + ON_THE_EDGE);
        h.assertTrue(c.getBoundingBox().intersects(new AABB(shut)), "SETUP: he does not stand in the cell");
        Creeper creeper = still(creeper(h, stairs.get(SHORT_FOOT + BEYOND_FOOT)));
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = trace(h, c, creeper);
            h.assertTrue(c.isAlive() && creeper.isAlive(), "SETUP: the creeper came to him" + trace);
            if (!h.getLevel().getBlockState(shut).isAir() && c.mineSite().temporary().contains(shut)) { h.succeed(); return; }
            h.assertTrue(h.getTick() - begun < SHUT_TICKS, "The way was not shut" + trace);
        });
    }

    /**
     * A creeper in sight in a pit below the foot of the stairs, four blocks down with no way up (series m and n of PR #195): no block is set
     * against it. Before, the block went in, the walk took it up again with the creeper more than three blocks from it, and the guard set it again.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperInAPitItCannotClimbOutOfIsNoReasonToShutTheWay(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        List<BlockPos> stairs = shortMine(h, c, SHORT_FOOT);
        BlockPos foot = stairs.get(SHORT_FOOT);
        Creeper creeper = byAPit(h, c, stairs);
        long begun = h.getTick();
        boolean[] seen = {false};
        h.onEachTick(() -> {
            seen[0] |= c.hasLineOfSight(creeper) && c.distanceTo(creeper) < 9;
            String trace = trace(h, c, creeper);
            h.assertTrue(c.isAlive() && creeper.isAlive() && creeper.getSwellDir() <= 0, "SETUP: the creeper got to him" + trace);
            h.assertTrue(c.mineSite().temporary().isEmpty() && h.getLevel().getBlockState(foot.above()).isAir(), "A block was set against a creeper that cannot come" + trace);
            if (h.getTick() - begun < SHUT_TICKS) return;
            h.assertTrue(seen[0], "SETUP: the creeper was never in his sight" + trace);
            h.succeed();
        });
    }

    /**
     * The creeper in the pit below is the nearer one, and a second comes down the stairs behind him: the one in the pit, which cannot come,
     * does not hide the one that can, and the way up is shut against it.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperInAPitDoesNotHideOneComingDownTheStairs(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        List<BlockPos> stairs = shortMine(h, c, SHORT_FOOT);
        BlockPos stand = stairs.get(SHORT_FOOT - 1), foot = stairs.get(SHORT_FOOT);
        Creeper pit = byAPit(h, c, stairs), stairsDown = still(creeper(h, stairs.get(SHORT_FOOT - 1 - ABOVE_HIM)));
        h.assertTrue(pit.distanceTo(c) < stairsDown.distanceTo(c), "SETUP: the creeper in the pit is not the nearer one");
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | pit " + Math.round(pit.distanceTo(c) * 10) / 10.0 + " off" + trace(h, c, stairsDown);
            h.assertTrue(c.isAlive() && pit.isAlive() && stairsDown.isAlive(), "SETUP: a creeper blew" + trace);
            h.assertTrue(h.getLevel().getBlockState(foot.above()).isAir(), "A block was set against the creeper in the pit" + trace);
            if (c.mineSite().temporary().stream().anyMatch(cell -> cell.getY() > stand.getY())) { h.succeed(); return; }
            h.assertTrue(h.getTick() - begun < SHUT_TICKS, "The way up was not shut against the creeper coming down" + trace);
        });
    }

    /** A pit under the foot of the short mine, open to his stand above the foot, and a still creeper at the bottom of it. */
    private static Creeper byAPit(GameTestHelper h, CompanionEntity c, List<BlockPos> stairs) {
        BlockPos foot = stairs.get(SHORT_FOOT);
        for (int dx = -1; dx <= 1; dx++) for (int dz = 1; dz <= PIT_LENGTH; dz++) for (int dy = -PIT_DEPTH; dy <= 1; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, -dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, stairs.get(SHORT_FOOT - 1));
        return still(creeper(h, foot.offset(0, -PIT_DEPTH, -PIT_LENGTH + 1)));
    }

    /**
     * Stairs dug {@code dug} stands down, the record knowing them only to {@link #SHORT_FOOT}: the deeper stands of the guard's search are
     * out of the route, so the one stand it can shut is the next one down.
     */
    private static List<BlockPos> shortMine(GameTestHelper h, CompanionEntity c, int dug) {
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stairs = CompanionShaftPlan.stands(entrance, Direction.NORTH, dug);
        for (BlockPos stand : stairs) for (BlockPos cell : CompanionShaftPlan.column(stand)) h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionMineSite site = c.mineSite();
        site.start(entrance, Direction.NORTH);
        for (BlockPos stand : stairs.subList(1, SHORT_FOOT + 1)) site.reached(stand);
        return stairs;
    }

    /**
     * A lit fuse on his east and a creeper with no fuse yet on his west (it stands still): the way out of the lit one does not lead to the other,
     * he is never nearer to it than he was, and the blast leaves him more than half his health.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aRetreatFromALitFuseDoesNotCloseOnAnotherCreeper(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(STAND, 0, 0));
        Creeper lit = creeper(h, foot.offset(LIT_AT, 0, 0));
        Creeper fresh = still(creeper(h, foot.offset(FRESH_AT, 0, 0)));
        lit.ignite();
        start(h, c, 1);
        long begun = h.getTick();
        double[] first = {-1};
        h.onEachTick(() -> {
            double toFresh = c.distanceTo(fresh);
            if (first[0] < 0) first[0] = toFresh;
            String trace = " | to the fresh one " + String.format("%.1f", toFresh) + " from " + String.format("%.1f", first[0]) + ", the lit one " + (lit.isAlive() ? "alive" : "gone")
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(!fresh.isAlive() || toFresh >= first[0] - SLACK, "He walked towards the creeper behind him" + trace);
            if (lit.isAlive()) {
                h.assertTrue(h.getTick() - begun < FUSE_TICKS, "The lit creeper never blew" + trace);
                return;
            }
            h.assertTrue(c.getHealth() >= KEPT, "The blast took him below half his health" + trace);
            h.succeed();
        });
    }

    /**
     * The fuse is lit 1.5 blocks off, on the last step of the stairs below him, at the foot of the mine, with blocks for the stairs in his
     * bag: no block is tried first, he is 4 blocks off in 20 ticks (about 10 damage there) and lives.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseOnTheStepGetsHimFourBlocksOffInTwentyTicks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        chamber(h, stairs);
        place(c, stairs.get(stairs.size() - 2));
        Creeper creeper = still(creeper(h, stairs.get(stairs.size() - 1)));
        creeper.ignite();
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            long elapsed = h.getTick() - begun;
            double gap = c.distanceTo(creeper);
            String trace = " | tick " + elapsed + ", gap " + String.format("%.1f", gap) + (creeper.isAlive() ? "" : " (gone)")
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (elapsed == RUN_TICKS) h.assertTrue(!creeper.isAlive() || gap >= RUN_GAP, "Less than 4 blocks off in 20 ticks" + trace);
            if (!creeper.isAlive()) { h.succeed(); return; }
            h.assertTrue(elapsed < FUSE_TICKS, "The lit creeper never blew" + trace);
        });
    }

    /**
     * Only a lit creeper has a far side. The fuse is lit on the step below him, halfway up the stairs, and a second creeper, its fuse not
     * lit, is shut in the rock three blocks under the stairs, one step up from him: it is neither in sight nor on any way, and it does not
     * take the way up from him. So he has a way out, strikes the lit one first as on any stairs, and the blast leaves him more than half his health.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void anUnlitCreeperShutInTheRockUnderTheStairsDoesNotTakeTheWayUpFromALitOne(GameTestHelper h) { struckFirstWithAnotherBy(h, false); }

    /**
     * The same stairs and lit creeper, and the second one, its fuse not lit, stands at the bottom of a pit beside the step above him, open
     * to his stand: he sees it, it cannot climb to him, and it takes no way up from him either.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void anUnlitCreeperInSightInAPitByTheStairsDoesNotTakeTheWayUpFromALitOne(GameTestHelper h) { struckFirstWithAnotherBy(h, true); }

    private static void struckFirstWithAnotherBy(GameTestHelper h, boolean seen) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int at = stairs.size() - 1 - UP_THE_STAIRS;
        BlockPos stand = stairs.get(at), pit = stand.west().south().below(PIT);
        place(c, stand);
        // The stairs go down to the north: south of his stand is the step above him.
        if (seen) for (BlockPos column : List.of(stand.west(), stand.west().south())) for (int dy = -PIT; dy <= 1; dy++) {
            h.getLevel().setBlock(column.above(dy), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        BlockPos pocket = stairs.get(at - 1).below(SHUT_UNDER);
        if (!seen) for (int dy = 0; dy < 2; dy++) h.getLevel().setBlock(pocket.above(dy), Blocks.AIR.defaultBlockState(), 2 | 16);
        Creeper other = still(creeper(h, seen ? pit : pocket));
        if (seen) other.setPos(pit.getX() + .5, pit.getY(), pit.getZ() + .5 - TOWARDS_HIM);
        h.assertTrue(c.hasLineOfSight(other) == seen, "SETUP: the second creeper is " + (seen ? "not in his sight" : "in his sight"));
        Creeper lit = still(creeper(h, stairs.get(at + 1)));
        lit.ignite();
        start(h, c, 1);
        long begun = h.getTick();
        boolean[] struck = {false};
        h.onEachTick(() -> {
            String trace = " | tick " + (h.getTick() - begun) + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (lit.isAlive()) {
                struck[0] |= lit.getHealth() < lit.getMaxHealth();
                h.assertTrue(h.getTick() - begun < FUSE_TICKS, "The lit creeper never blew" + trace);
                return;
            }
            h.assertTrue(struck[0], "He did not strike the lit creeper first: the other one took his way up" + trace);
            h.assertTrue(c.getHealth() >= KEPT, "The blast took him below half his health" + trace);
            h.succeed();
        });
    }

    /**
     * He is hurt (twelve health: a creeper is fought only above it), a zombie is on him and a creeper stands in sight with its fuse out: the
     * creeper is no reason to run, the zombie is struck twice within five seconds. The zombie may land the first blow (a blow from a standstill
     * throws it back only by the hit's own push); twelve less its three stays above the health at which a wound sends him off anyway.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperWithNoFuseDoesNotSendHimRunningFromAZombieOnHim(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(FAR_STAND, 0, 0));
        c.setHealth(HURT);
        Zombie zombie = zombie(h, foot.offset(FAR_STAND + 1, 0, 0));
        Creeper creeper = still(creeper(h, foot.offset(FAR_STAND + 3, 0, 5)));
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            String trace = " | zombie " + (zombie.isAlive() ? "health " + zombie.getHealth() : "dead") + ", creeper fuse " + creeper.getSwellDir() + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + ", target " + c.getTarget() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(!c.getLastTaskResult().contains(SENT_OFF), "The creeper sent him off" + trace);
            if (!zombie.isAlive() || zombie.getHealth() <= zombie.getMaxHealth() - TWO_BLOWS) h.succeed();
            h.assertTrue(h.getTick() - begun < FIGHT_TICKS, "The zombie on him was not struck" + trace);
        });
    }

    /** A zombie far from him (eight blocks, standing) does not hold him by an unlit creeper when he may not fight it (twelve health): the guard still sends him off or shuts the way. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aZombieFarFromHimDoesNotHoldHimByAnUnlitCreeper(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(FAR_STAND, 0, 0));
        c.setHealth(HURT);
        still(zombie(h, foot.offset(FAR_STAND - FAR_ZOMBIE, 0, 0)));
        Creeper creeper = still(creeper(h, foot.offset(FAR_STAND, 0, CREEPER_SIDE)));
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died");
            if (c.getLastTaskResult().contains(CREEPER_NEAR)) { h.succeed(); return; }
            h.assertTrue(h.getTick() - begun < FIGHT_TICKS, "The far zombie held him by the creeper | creeper fuse " + creeper.getSwellDir() + ", he at "
                    + rel(h, c.blockPosition()).toShortString() + ", target " + c.getTarget() + " | " + report(c));
        });
    }

    /**
     * The breach of a cave with mobs in it is being sealed and a creeper in the cave has its fuse lit: nothing is sealed meanwhile, the
     * stone sword stays in his hand (it does not go back and forth with the pickaxe) until the creeper has blown.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseNearKeepsTheSwordInHandWhileTheCaveIsBeingSealed(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = -PASSAGE_LENGTH; dx <= 1; dx++) for (int dy = 0; dy < 2; dy++) h.getLevel().setBlock(foot.offset(dx, dy, -PASSAGE_OFFSET), Blocks.AIR.defaultBlockState(), 2 | 16);
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

    /**
     * Run u1d/void-4: he is walled in by his own plugs, the creeper within fuse range outside, a slit between them that he bobbed up to in the
     * water. Each saw the other only at the top of a bob: the fuse lit, and went out at the bottom, it never blew, and he stood «отхожу, потом
     * добью» with his sword until the time ran out, the creeper in reach. The bob is here a slab under him and a slit that opens and shuts on
     * the beat, with the fuse lit in the tick it opens. He has no way out of here, so an order that ends counts as well as a dead creeper,
     * once he has struck it.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aFuseThatComesAndGoesWithSightOfHimDoesNotHoldHimForEver(GameTestHelper h) {
        mountain(h, false);
        for (int x = CAVE_LOW_X; x <= CAVE_HIGH_X; x++) for (int z = CAVE_LOW_Z; z <= CAVE_HIGH_Z; z++) for (int y = NICHE_Y; y < NICHE_Y + CAVE_HEIGHT; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = NICHE_X - 1; x <= NICHE_X + 1; x++) {
            for (int z = NICHE_Z - 1; z <= NICHE_Z + 1; z++) for (int y = NICHE_Y; y <= NICHE_Y + NICHE_ROOF; y++) {
                if (x != NICHE_X || z != NICHE_Z || y == NICHE_Y + NICHE_ROOF) h.setBlock(new BlockPos(x, y, z), Blocks.COBBLESTONE);
            }
            for (int z = NICHE_Z + 2; z <= NICHE_Z + 4; z++) h.setBlock(new BlockPos(x, NICHE_Y, z), Blocks.COBBLESTONE);
        }
        h.setBlock(new BlockPos(NICHE_X, NICHE_Y, NICHE_Z), Blocks.SMOOTH_STONE_SLAB);
        BlockPos slit = new BlockPos(NICHE_X, NICHE_Y + 2, NICHE_Z + 1);
        CompanionEntity c = armed(h);
        c.setFollowing(false);
        BlockPos niche = h.absolutePos(new BlockPos(NICHE_X, NICHE_Y, NICHE_Z));
        c.teleportTo(niche.getX() + .5, niche.getY() + .5, niche.getZ() + .5);
        Creeper creeper = h.spawn(net.minecraft.world.entity.EntityType.CREEPER, new net.minecraft.world.phys.Vec3(NICHE_X + .5, NICHE_Y + 1, NICHE_Z + CREEPER_OFF));
        creeper.setPersistenceRequired();
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            long beat = (h.getTick() - begun) % BEAT_TICKS;
            if (beat == 0 && creeper.isAlive()) {
                h.setBlock(slit, Blocks.AIR);
                creeper.setTarget(c);
                creeper.setSwellDir(1);
            } else if (beat == SIGHT_TICKS) h.setBlock(slit, Blocks.COBBLESTONE);
            String trace = trace(h, c, creeper);
            h.assertTrue(c.isAlive(), "Died" + trace);
            boolean struck = creeper.getHealth() < creeper.getMaxHealth(), orderOver = !c.isWorking() && !c.isGatheringTask();
            if (!creeper.isAlive() || struck && orderOver) { h.succeed(); return; }
            h.assertTrue(h.getTick() - begun < SETTLED_TICKS, "The creeper still holds him" + trace);
        });
    }

    /** It stands where it is (alive, its speed is nil): a scene that asks what he does about a creeper, not where the creeper wanders. */
    private static <M extends net.minecraft.world.entity.Mob> M still(M mob) {
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        return mob;
    }

    private static String blocks(GameTestHelper h, CompanionEntity c) { return c.mineSite().temporary().stream().map(cell -> rel(h, cell).toShortString()).toList().toString(); }

    private static String trace(GameTestHelper h, CompanionEntity c, Creeper creeper) {
        return " | creeper " + (creeper.isAlive() ? "alive, fuse " + creeper.getSwellDir() + ", " + Math.round(creeper.distanceTo(c) * 10) / 10.0 + " off" : "gone")
                + ", he at " + c.position() + ", blocks " + blocks(h, c) + " | " + report(c);
    }
}

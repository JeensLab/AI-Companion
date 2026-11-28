package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.aicompanion.gametest.CompanionMineArena.CENTER;
import static dev.aicompanion.gametest.CompanionMineArena.DEEPSLATE_TOP;
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

/**
 * Stops of the stairs that ended or froze the order: a mine whose record is left with no way back to its stairs, a pit or a ring of his own
 * plugs with no side to begin stairs to, and ore left open beside the lane he walks. Arena as in the other mine tests.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineStopsTests {
    private static final int OWN_STAND = 3, PIT = 11, FAR_ENTRANCE = 200, POCKET_AT = 8, NEAR_CELLS = 2, TUNNEL_START = 2, SPENT_RESTARTS = 3;
    private static final int BESIDE_AT = 18, OVERHEAD_AT = 14, SPARE_PICKAXES = 6, DIAGONAL_AT = 6, CEILING_AT = 9, LAKE_REACH = 7, LEDGE_LENGTH = 4;
    private static final int CORRIDOR_Y = 12, CORRIDOR_Z = 14, ENTRANCE_X = 5, START_X = 18, EDGE_X = 12, PIT_DEPTH = 6, HOLE_AT = 4, HOLE_DEPTH = 9, CHASE_WATCH = 30;
    /** How far into the cell, along z, he stands from a fence post in it: the post is 0.25 wide at the middle, he is 0.6 wide. */
    private static final double BESIDE_THE_POST = 0.93;
    /** How far from the middle of his cell his centre stands when the hitbox, 0.6 wide, reaches 0.04 into the neighbour. */
    private static final double BODY_OVER_EDGE = 0.34;
    private static final String PICKUP = "Подбираю добытые вещи";

    private CompanionMineStopsTests() { }

    /**
     * He stands in a closed pit deep under the ground; the record's entrance is far away on the surface and no stairs are left near him,
     * and no spot for a new entrance is in reach. He does not give up with "no place for an entrance": the mine starts again in the pit.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainInAPitWhenTheEntranceIsFarAway(GameTestHelper h) {
        mountain(h, true);
        for (int x = PIT - 1; x <= PIT + 1; x++) for (int z = PIT - 1; z <= PIT + 1; z++) for (int y = DEEPSLATE_TOP; y <= DEEPSLATE_TOP + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos pit = h.absolutePos(new BlockPos(PIT, DEEPSLATE_TOP, PIT));
        c.teleportTo(pit.getX() + .5, pit.getY(), pit.getZ() + .5);
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER)), Direction.NORTH);
        // The light engine runs between ticks: the order starts once the pit reads as roofed over, as it does in the world.
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(pit.above()), "The light in the pit is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not start again in the pit: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 1, "Restarts " + c.mineSite().restarts());
        });
    }

    /**
     * Run m12c/lava (seed 7741901): he stood in the water of a cave lake, deep under the ground, the record's entrance far away on the surface and no
     * stairs of his around. No side of a cell in water takes stairs, so the order ended «Я под землёй, а своей лестницы рядом нет», though a dry
     * ledge was a few strokes off. He gets out of the water onto the ledge and the mine starts again there.
     * The water is a source whose flow thins to its last cell, where he stands: a deeper pool floats him, and the work goal does not run afloat.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainOnTheShoreWhenHeStandsInACaveLake(GameTestHelper h) {
        mountain(h, true);
        BlockPos shallows = h.absolutePos(new BlockPos(PIT, DEEPSLATE_TOP, PIT));
        for (BlockPos cell : BlockPos.betweenClosed(shallows.west(LAKE_REACH), shallows)) for (BlockPos open : CompanionShaftPlan.column(cell)) h.getLevel().setBlock(open, Blocks.AIR.defaultBlockState(), 2 | 16);
        BlockPos ledge = shallows.offset(1, 1, 0);
        for (BlockPos cell : BlockPos.betweenClosed(ledge, ledge.east(LEDGE_LENGTH))) for (BlockPos open : CompanionShaftPlan.column(cell)) h.getLevel().setBlock(open, Blocks.AIR.defaultBlockState(), 2 | 16);
        h.getLevel().setBlock(shallows.west(LAKE_REACH), Blocks.WATER.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.teleportTo(shallows.getX() + .5, shallows.getY(), shallows.getZ() + .5);
        BlockPos far = h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER));
        c.mineSite().start(far, Direction.NORTH);
        for (Direction d : Direction.Plane.HORIZONTAL) c.mineSite().refuse(far, d);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(shallows.above()), "The light over the lake is not worked out yet");
                h.assertTrue(!h.getLevel().getFluidState(shallows).isEmpty(), "The water has not reached him yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He stayed in the lake: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 1, "Restarts " + c.mineSite().restarts());
        });
    }

    /**
     * The record's stairs were turned into flat stands deep down, away from the entrance, and he stands by the entrance on the surface with
     * every side of his cell refused: the way to the stands is gone and the mine cannot start again at his cell. He goes back to the entrance
     * and begins stairs to a side not tried from it, instead of ending with "could not get back to my stairs".
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainFromTheEntranceWhenTheRecordedStandsAreCutOff(GameTestHelper h) {
        mountain(h, true);
        BlockPos pocket = h.absolutePos(new BlockPos(CENTER - POCKET_AT, DEEPSLATE_TOP + 2, CENTER + POCKET_AT));
        for (BlockPos cell : CompanionShaftPlan.column(pocket)) h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h);
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), here = h.absolutePos(new BlockPos(CENTER + OWN_STAND - 1, GROUND + 1, CENTER));
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        c.mineSite().start(entrance, Direction.NORTH);
        c.mineSite().reached(pocket);
        // The search takes him over the stands: every cell above them is refused on all sides, as the log showed, the entrance cell is not.
        BlockPos above = new BlockPos(pocket.getX(), entrance.getY(), pocket.getZ());
        for (BlockPos cell : BlockPos.betweenClosed(above.offset(-NEAR_CELLS, 0, -NEAR_CELLS), above.offset(NEAR_CELLS, 0, NEAR_CELLS))) {
            for (Direction d : Direction.Plane.HORIZONTAL) c.mineSite().refuse(cell.immutable(), d);
        }
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not start again from the entrance: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 1, "Restarts " + c.mineSite().restarts());
        });
    }

    /** A closed pit of three by three cells under the deepslate top, three high; the cell in its middle is returned. */
    private static BlockPos pit(GameTestHelper h) {
        for (int x = PIT - 1; x <= PIT + 1; x++) for (int z = PIT - 1; z <= PIT + 1; z++) for (int y = DEEPSLATE_TOP; y <= DEEPSLATE_TOP + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        return h.absolutePos(new BlockPos(PIT, DEEPSLATE_TOP, PIT));
    }

    /**
     * Run p1b/lava: the cell his body is in holds a fence post of a mine shaft he broke into (the post stands beside him, not on him), so the
     * cell is no place to stand and no side was found from it: the entrance was far, the walk to it failed, and the order ended five times.
     * He starts the mine again from the open cell beside the post.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainBesideAFencePostHeStandsIn(GameTestHelper h) {
        mountain(h, true);
        BlockPos middle = pit(h);
        h.getLevel().setBlock(middle, Blocks.OAK_FENCE.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.teleportTo(middle.getX() + .5, middle.getY(), middle.getZ() + BESIDE_THE_POST);
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER)), Direction.NORTH);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(middle.above()), "The light in the pit is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not start again beside the post: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 1, "Restarts " + c.mineSite().restarts());
        });
    }

    /**
     * Runs w1a/descent: he stood at (45.16, 14, -6.17), on the corner of a block that his body only overlapped, with air under the cell of his centre. The floor of
     * that cell is air, so no side was a side to begin stairs to and the goal stopped. A player on the edge of a ledge is on the ledge.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void beginsTheStairsFromTheLedgeHisBodyStandsOn(GameTestHelper h) {
        mountain(h, false);
        for (int x = ENTRANCE_X; x <= START_X; x++) for (int y = CORRIDOR_Y; y <= CORRIDOR_Y + 2; y++) h.setBlock(new BlockPos(x, y, CORRIDOR_Z), Blocks.AIR);
        for (int x = EDGE_X + 1; x <= START_X; x++) for (int y = CORRIDOR_Y - PIT_DEPTH; y < CORRIDOR_Y; y++) h.setBlock(new BlockPos(x, y, CORRIDOR_Z), Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos ledge = h.absolutePos(new BlockPos(EDGE_X, CORRIDOR_Y, CORRIDOR_Z)), over = h.absolutePos(new BlockPos(EDGE_X + 1, CORRIDOR_Y, CORRIDOR_Z));
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), Direction.NORTH);
        c.teleportTo(over.getX() + .5 + (ledge.getX() - over.getX()) * BODY_OVER_EDGE, over.getY(), over.getZ() + .5 + (ledge.getZ() - over.getZ()) * BODY_OVER_EDGE);
        boolean[] started = {false};
        h.onEachTick(() -> {
            if (!started[0]) {
                // The sky light of a freshly built mountain lags behind it.
                if (!c.onGround() || h.getLevel().canSeeSky(over.above())) return;
                h.assertTrue(c.blockPosition().equals(over), "He did not stand with his centre over the pit: " + c.blockPosition() + " | " + report(c));
                started[0] = true;
                h.assertTrue(c.startWork("dig_for_diamonds", "", 1).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
                return;
            }
            if (c.mineSite().restarts() == 0) return;
            h.assertTrue(c.mineSite().entrance().distManhattan(ledge) <= 1, "He began the stairs at " + rel(h, c.mineSite().entrance()) + ", not from his ledge at " + rel(h, ledge) + " | " + report(c));
            h.succeed();
        });
    }

    /**
     * Runs m4b/void-3 (seed 20261102) and m4a/water (20261031): he stands on a ledge of a cave walled in by his own plugs. The cells beside
     * him are plugs forbidden for good, every side is refused, and no side of the entrance on the surface is left to begin stairs to: the goal
     * stood «Не смог дойти до входа в шахту» five times. He digs down out of the ring, the mine begins again below it and reaches the ore;
     * the ring stays shut.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void digsOutOfARingOfHisOwnPlugs(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        // The order starts once the light is worked out; waiting, he must not dig his way up towards his owner.
        c.setFollowing(false);
        BlockPos entrance = h.absolutePos(new BlockPos(PIT, GROUND + 1, PIT));
        c.mineSite().start(entrance, Direction.NORTH);
        for (Direction d : Direction.Plane.HORIZONTAL) c.mineSite().refuse(entrance, d);
        BlockPos pocket = pluggedPocket(h, c);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(pocket.above()), "The light in the pocket is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He stood in the ring of plugs: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 1, "Restarts " + c.mineSite().restarts());
            for (Direction d : Direction.Plane.HORIZONTAL) h.assertTrue(h.getLevel().getBlockState(pocket.relative(d)).is(Blocks.COBBLESTONE), "A plug of the ring was opened on the " + d);
        });
    }

    /**
     * Run f9b/void-3 (seed 20261102): three restarts spent and carried to the next steps, the walk to the entrance failed, and he stood in the
     * cave with every side free to begin stairs to: each step ended «Не смог дойти до входа в шахту» at once. The cap alone stops him, so he gets
     * the one restart past it, here, and the stairs go on.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainOnceMoreWhenOnlyTheCapStopsHim(GameTestHelper h) {
        mountain(h, true);
        for (int x = PIT - 1; x <= PIT + 1; x++) for (int z = PIT - 1; z <= PIT + 1; z++) for (int y = DEEPSLATE_TOP; y <= DEEPSLATE_TOP + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos pit = h.absolutePos(new BlockPos(PIT, DEEPSLATE_TOP, PIT));
        c.teleportTo(pit.getX() + .5, pit.getY(), pit.getZ() + .5);
        BlockPos far = h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER));
        c.mineSite().start(far, Direction.NORTH);
        spendRestarts(h, c, far, 3);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(pit.above()), "The light in the pit is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He stood with the cap alone against him: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 4, "Restarts " + c.mineSite().restarts());
        });
    }

    /** The dig out was the last restart past the cap: in the same ring again he ends at once, and burns no more restarts. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void doesNotDigOutOnceMoreWhenTheRestartsAreSpent(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        BlockPos pocket = h.absolutePos(new BlockPos(PIT, DEEPSLATE_TOP, PIT));
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER)), Direction.NORTH);
        spendRestarts(h, c, pocket, 4);
        c.mineSite().buried(true);
        pluggedPocket(h, c);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(!result.success() && result.message().contains("некуда свернуть"), "Not a stop with the restarts spent: " + result.message());
            h.assertTrue(c.mineSite().restarts() == 4, "Restarts " + c.mineSite().restarts());
            h.assertTrue(c.blockPosition().getY() == pocket.getY(), "He dug down at " + c.blockPosition());
        });
    }

    /**
     * Run p1b/void-4: three restarts spent and carried, he stands in a pit that was refused on every side, and the entrance is out of reach.
     * Neither a free side nor a plug of his own tells him he may dig, yet the one restart past the cap is his: he breaks the floor until a
     * side is free and the stairs go on.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void digsOutOfAPitRefusedOnEverySide(GameTestHelper h) {
        mountain(h, true);
        BlockPos middle = pit(h);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.teleportTo(middle.getX() + .5, middle.getY(), middle.getZ() + .5);
        BlockPos far = h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER));
        c.mineSite().start(far, Direction.NORTH);
        c.mineSite().newStep(h.getLevel().getGameTime());
        for (int i = 0; i < SPENT_RESTARTS; i++) c.mineSite().restart(far, Direction.NORTH);
        for (Direction d : Direction.Plane.HORIZONTAL) c.mineSite().refuse(middle, d);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(middle.above()), "The light in the pit is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He stood in the refused pit: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == SPENT_RESTARTS + 1, "Restarts " + c.mineSite().restarts());
        });
    }

    /**
     * Run m8b/void-4: the tunnel is two lanes wide and he walks the left one, so a diamond in the wall behind the right lane (its drop would
     * rest two columns off) and one over the right lane (not in sight through the ceiling) lay open and untaken for the whole order. Both
     * have an open face on a cell of his own tunnel: he steps to the right lane, takes them and goes on.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void takesDiamondsOpenBesideTheLaneHeWalks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        // Long tunnels in deepslate wear out more stone pickaxes than the usual bag holds.
        for (int slot = 6; slot < 6 + SPARE_PICKAXES; slot++) c.setItem(slot, new ItemStack(Items.STONE_PICKAXE));
        carveMine(h, c, TUNNEL_START);
        BlockPos foot = c.mineSite().foot();
        h.getLevel().setBlock(foot.relative(Direction.WEST, BESIDE_AT).north(2), Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 2 | 16);
        h.getLevel().setBlock(foot.relative(Direction.WEST, OVERHEAD_AT).north().above(2), Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 2 | 16);
        BlockPos end = foot.relative(Direction.WEST, TUNNEL_START);
        c.teleportTo(end.getX() + .5, end.getY(), end.getZ() + .5);
        long id = start(h, c, 2);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 2, "Diamonds left open beside the tunnel: " + count(c, Items.DIAMOND) + " | " + result.message() + " | " + report(c));
        });
    }

    /**
     * The tunnel is two cells high: a diamond in the ceiling over either lane has its open face on a cell of the advance and is taken. One
     * a cell further out (up and aside) touches no open cell, so it is honestly not seen and stays.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void takesDiamondsInTheCeilingOfBothLanesAndLeavesTheDiagonalOne(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        for (int slot = 6; slot < 6 + SPARE_PICKAXES; slot++) c.setItem(slot, new ItemStack(Items.STONE_PICKAXE));
        carveMine(h, c, TUNNEL_START);
        BlockPos foot = c.mineSite().foot();
        BlockPos diagonal = foot.relative(Direction.WEST, DIAGONAL_AT).north(2).above(2);
        h.getLevel().setBlock(diagonal, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 2 | 16);
        h.getLevel().setBlock(foot.relative(Direction.WEST, CEILING_AT).above(2), Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 2 | 16);
        h.getLevel().setBlock(foot.relative(Direction.WEST, CEILING_AT + 3).north().above(2), Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 2 | 16);
        BlockPos end = foot.relative(Direction.WEST, TUNNEL_START);
        c.teleportTo(end.getX() + .5, end.getY(), end.getZ() + .5);
        long id = start(h, c, 2);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) == 2, "Diamonds in the ceiling of the tunnel: " + count(c, Items.DIAMOND) + " | " + result.message() + " | " + report(c));
            h.assertTrue(h.getLevel().getBlockState(diagonal).is(Blocks.DEEPSLATE_DIAMOND_ORE), "The diamond with no open face was taken | " + report(c));
        });
    }

    /**
     * Run m10a/void (seed 20261031): every one of the twenty-seven slots held something, dirt and gravel and wooden tools among them, and the diamond
     * he had mined out had no slot to fall into: «Сумка полна, добытое некуда класть» five times. He throws the rubble out (real items, in the
     * world) until the diamond fits, and keeps the tools.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void throwsOutRubbleWhenTheBagIsFullAndADiamondHasNoRoom(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        net.minecraft.world.item.Item[] rubble = {Items.DIRT, Items.GRAVEL, Items.SAND, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF};
        for (int i = 0; i < rubble.length; i++) c.setItem(6 + i, new ItemStack(rubble[i], 64));
        for (int slot = 6 + rubble.length; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.WOODEN_HOE));
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "No room for the diamond: " + result.message() + " | " + report(c));
            h.assertTrue(count(c, Items.WOODEN_HOE) == c.getContainerSize() - 6 - rubble.length, "A tool was thrown out instead of the rubble | " + report(c));
            h.assertTrue(c.getOffhandItem().is(Items.SHIELD), "The shield in the left hand went with the rubble | " + report(c));
        });
    }

    /**
     * Runs w1a/void: a cobblestone he cut from the wall fell into a hole nine blocks below him, and the walk to it was the stairs up to the surface and round: the third
     * climb of the run. In the mine such a stone is left where it lies.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void leavesAStoneThatFellFarBelowHimInTheMine(GameTestHelper h) {
        mountain(h, false);
        BlockPos hole = h.absolutePos(new BlockPos(CENTER + HOLE_AT, GROUND - HOLE_DEPTH, CENTER));
        h.getLevel().setBlock(hole, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        h.assertTrue(c.startWork("dig_for_diamonds", "", 1).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
        net.minecraft.world.entity.item.ItemEntity stone = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), hole.getX() + .5, hole.getY(), hole.getZ() + .5, new ItemStack(Items.COBBLESTONE));
        stone.setDeltaMovement(0, 0, 0);
        h.getLevel().addFreshEntity(stone);
        c.claimDrops(java.util.List.of(stone));
        h.runAfterDelay(CHASE_WATCH, () -> {
            h.assertTrue(!report(c).contains(PICKUP), "He goes for a stone " + HOLE_DEPTH + " blocks below him | " + report(c));
            h.succeed();
        });
    }

    /**
     * The restarts a goal has spent: the last step ended empty just now, so the next one carries them, as it does in a world where the clock is far past
     * {@link dev.aicompanion.entity.CompanionMineSite#RESTARTS_CARRIED}. Restarts made on a record that never ended a step are given back only while the clock is still young.
     */
    private static void spendRestarts(GameTestHelper h, CompanionEntity c, BlockPos at, int count) {
        c.mineSite().newStep(h.getLevel().getGameTime());
        for (int i = 0; i < count; i++) c.mineSite().restart(at, Direction.NORTH);
    }

    /** A pocket at the top of the deepslate closed all round, feet and head, by plugs he sealed and forbade, every side refused; he stands in it. */
    static BlockPos pluggedPocket(GameTestHelper h, CompanionEntity c) {
        BlockPos here = h.absolutePos(new BlockPos(PIT, DEEPSLATE_TOP, PIT));
        for (BlockPos cell : CompanionShaftPlan.column(here)) h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (BlockPos cell : List.of(here.relative(d), here.relative(d).above())) {
                h.getLevel().setBlock(cell, Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
                c.mineSite().sealed(cell);
            }
            c.mineSite().forbid(here.relative(d));
            c.mineSite().refuse(here, d);
        }
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        return here;
    }
}

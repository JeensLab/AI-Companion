package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.DEEPSLATE_TOP;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;
import static dev.aicompanion.gametest.CompanionMineStopsTests.PIT_STEP;
import static dev.aicompanion.gametest.CompanionMineStopsTests.SURFACE_MARGIN;
import static dev.aicompanion.gametest.CompanionMineStopsTests.TUNNEL_START;

/**
 * The way up out of the stops of the stairs: the restarts spent, the plugs, the gaps in the record and the iron pickaxe kept for ore.
 * Arena as in the other mine tests; the stops themselves are in {@link CompanionMineStopsTests}.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineClimbStopsTests {
    /** Wear left of the iron pickaxe (the reserve kept for ore is just above it), the stands the record still knows, the stand he is in, and the one his plug closes. */
    private static final int IRON_LEFT = 60, KNOWN_STANDS = 3, GAP_AT = 12, PLUG_AT = 8, FAR_PLUG_AT = 4, WALL_AT = 5, DIAMOND_AT = 2;
    private static final int CAGE_AT = 8, CAGE_PAD = 7, CAGE_NEAR = 5;
    private static final int HALL_FROM = 6, HALL_TO = 18, HALL_TOP = 19, HALL_MID = 12, SHELF_AT = 8, SHELF_Y = 17, GAP = 4, LEDGE_DROP = 2, LEDGE_WATCH = 160;

    private CompanionMineClimbStopsTests() { }

    /**
     * Run m8a/void-4 (seed 20261031): three restarts spent and carried, every side of his cell refused, cobblestone under him and nothing
     * left of his stairs in the record: five steps in a row ended «Я под землёй, а своей лестницы рядом нет» at the same spot. He goes
     * up the way he came, begins stairs again on the surface, and the order reaches the ore.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void climbsOutWhenTheRestartsAreSpentAndEverySideIsRefused(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos here = carveMine(h, c, 0).get(PIT_STEP);
        h.getLevel().setBlock(here.below(), Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        spendRestartsOnTheStairs(h, c, here);
        for (Direction d : Direction.Plane.HORIZONTAL) c.mineSite().refuse(here, d);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(here.above()), "The light on the stairs is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He stood with the restarts spent: " + result.message() + " | " + report(c));
        });
    }

    /**
     * Run m8b/mobs (seed 20261102): the bottom of a deep shaft, no stone pickaxe, no wood to make one, the iron one worn to its reserve, the
     * restarts spent. «Копать нечем, поднялся за припасами» was said five times by a man who never left the bottom. He really goes up his
     * old way to the surface; given a pickaxe there, the next order reaches the ore.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void climbsToTheSurfaceForSuppliesWhenThereIsNoPickaxe(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - 4);
        c.setItem(0, ItemStack.EMPTY);
        c.setItem(1, worn);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos here = stairs.get(PIT_STEP);
        // The stairs below the spot he restarted at are rock again: it is the end of the shaft he can walk.
        for (BlockPos stand : stairs.subList(PIT_STEP + 1, stairs.size())) for (BlockPos cell : CompanionShaftPlan.column(stand)) h.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), 2 | 16);
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        spendRestartsOnTheStairs(h, c, here);
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1, 0)).getY() - SURFACE_MARGIN;
        long[] id = {0, 0};
        int[] heightWhenFirstEnded = {Integer.MIN_VALUE};
        h.onEachTick(() -> {
            if (id[0] != 0 && id[1] == 0 && heightWhenFirstEnded[0] == Integer.MIN_VALUE && c.workResult(id[0]) != null) heightWhenFirstEnded[0] = c.getBlockY();
        });
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(here.above()), "The light on the stairs is not worked out yet");
                id[0] = start(h, c, 1);
            }
            if (id[1] == 0) {
                CompanionActionResult first = ended(h, c, id[0]);
                h.assertTrue(heightWhenFirstEnded[0] >= surface, "He said «" + first.message() + "» at height " + heightWhenFirstEnded[0] + ", below the surface at " + surface + " | " + report(c));
                c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
                id[1] = start(h, c, 1);
            }
            CompanionActionResult second = ended(h, c, id[1]);
            h.assertTrue(second.success() && count(c, Items.DIAMOND) >= 1, "No diamond with a pickaxe from the surface: " + second.message() + " | " + report(c));
        });
    }

    /**
     * Run m9a/mobs (seed 20261031): the pit he stood in had lost its stairs from the record (the cells above it began a hundred blocks off) and a
     * plug of his own closed the stairs over him; the climb pushed at the far cell in a straight line and said «застрял на ходу» five times.
     * He walks the gap by a search, takes the plug out with the iron pickaxe that has wear left, and really reaches the surface.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void climbsPastAGapInTheRecordAndTakesOutHisOwnPlug(GameTestHelper h) {
        climbPastAPlug(h, PLUG_AT);
    }

    /**
     * Run m12b/void-2 and void-3 (seed 20261102): the pit at the bottom of the stairs, no stone pickaxe, the iron one at its reserve, the record lost
     * and a plug of his own on the stairs far above the pit. He pushed at the cell beyond the plug in a straight line, from the bottom or from just
     * under the plug, and said «застрял на ходу» five times. The plug is looked for when the push stalls, he walks the stairs up to it cell by cell,
     * takes it out and goes on to the surface.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void climbsUpTheStairsToAPlugOfHisOwnThatIsFarFromHim(GameTestHelper h) {
        climbPastAPlug(h, FAR_PLUG_AT);
    }

    /**
     * Run p1d/lava and p1d/void-2 (seed 20261031/20261102): a wall of his own blocks in the tunnel at feet and head, set while a creeper stood near and
     * forgotten by the record since (it keeps sixteen), no stone pickaxe, the iron one at its reserve. The walk pushed at the wall and said
     * «застрял на ходу» five times, as the goal stopped five times with the same words. He takes the wall out with the iron pickaxe and goes up.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void climbsThroughAWallOfHisOwnBlocksWithTheIronPickaxeAtItsReserve(GameTestHelper h) {
        climbThroughAWall(h, wornIron(IRON_LEFT));
    }

    /** The same wall with no pickaxe at all: cobblestone comes out by hand, slowly, as a real block, and he goes up. */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void climbsThroughAWallOfHisOwnBlocksByHandWhenHeHasNoPickaxe(GameTestHelper h) {
        climbThroughAWall(h, ItemStack.EMPTY);
    }

    private static void climbThroughAWall(GameTestHelper h, ItemStack pickaxe) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItem(0, ItemStack.EMPTY);
        c.setItem(1, pickaxe);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos here = stairs.get(GAP_AT);
        for (BlockPos stand : stairs.subList(GAP_AT + 1, stairs.size())) for (BlockPos cell : CompanionShaftPlan.column(stand)) h.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), 2 | 16);
        List<BlockPos> wall = List.of(stairs.get(WALL_AT), stairs.get(WALL_AT).above());
        for (BlockPos cell : wall) h.getLevel().setBlock(cell, Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        spendRestartsOnTheStairs(h, c, here);
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1, 0)).getY() - SURFACE_MARGIN;
        long[] id = {0};
        int[] heightWhenEnded = {Integer.MIN_VALUE};
        h.onEachTick(() -> {
            if (id[0] != 0 && heightWhenEnded[0] == Integer.MIN_VALUE && c.workResult(id[0]) != null) heightWhenEnded[0] = c.getBlockY();
        });
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(here.above()), "The light on the stairs is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(heightWhenEnded[0] >= surface, "He said «" + result.message() + "» at height " + heightWhenEnded[0] + ", below the surface at " + surface + " | " + report(c));
            for (BlockPos cell : wall) h.assertTrue(!h.getLevel().getBlockState(cell).is(Blocks.COBBLESTONE), "His wall on the stairs is still there | " + report(c));
        });
    }

    private static void climbPastAPlug(GameTestHelper h, int plugAt) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItem(0, ItemStack.EMPTY);
        c.setItem(1, wornIron(IRON_LEFT));
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos here = stairs.get(GAP_AT);
        for (BlockPos stand : stairs.subList(GAP_AT + 1, stairs.size())) for (BlockPos cell : CompanionShaftPlan.column(stand)) h.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), 2 | 16);
        c.mineSite().truncate(KNOWN_STANDS);
        for (BlockPos cell : List.of(stairs.get(plugAt), stairs.get(plugAt).above())) {
            h.getLevel().setBlock(cell, Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
            c.mineSite().sealed(cell);
        }
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        spendRestartsOnTheStairs(h, c, here);
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1, 0)).getY() - SURFACE_MARGIN;
        long[] id = {0};
        int[] heightWhenEnded = {Integer.MIN_VALUE};
        h.onEachTick(() -> {
            if (id[0] != 0 && heightWhenEnded[0] == Integer.MIN_VALUE && c.workResult(id[0]) != null) heightWhenEnded[0] = c.getBlockY();
        });
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(here.above()), "The light on the stairs is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(heightWhenEnded[0] >= surface, "He said «" + result.message() + "» at height " + heightWhenEnded[0] + ", below the surface at " + surface + " | " + report(c));
            h.assertTrue(!h.getLevel().getBlockState(stairs.get(plugAt)).is(Blocks.COBBLESTONE), "His plug on the stairs is still there | " + report(c));
        });
    }

    /**
     * Run m13a/void-4 (seed 20261031): he stood in the bottom of his stairs flooded one block deep and every walk out of it, to the shore and then up
     * the stairs, was counted lost: in water he bobs and is never on the ground, and a cell is entered only on the ground. The cell he stands in,
     * with a block of water at its feet, counts as reached, and the climb goes on from it.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void climbsOutOfOneBlockOfWaterAtTheBottomOfHisStairs(GameTestHelper h) {
        climbAboveAGap(h, stairs -> h.getLevel().setBlock(stairs.get(GAP_AT), Blocks.WATER.defaultBlockState(), 2 | 16), true, false);
    }

    /**
     * Run m13b/void (seed 20261102): the record has a gap, and in it the floor under the cells he would step on is air (a pit, a shaft of
     * his own): no walk over firm floor joins the two ends, and «Подняться не вышло: путь наверх закрыт» was said every twenty ticks to the end of
     * the clock. With blocks in the bag he lays the floor under the cells of the gap as he goes and reaches the surface.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void laysAFloorAcrossAGapOfAirInTheWayUp(GameTestHelper h) {
        climbAboveAGap(h, stairs -> airUnder(h, stairs), true, true);
    }

    /** The same gap with no block to lay: the climb ends once, said as it is, and he stays where he is. */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void endsTheClimbOnceWhenAGapOfAirHasNoBlockToBridgeIt(GameTestHelper h) {
        climbAboveAGap(h, stairs -> airUnder(h, stairs), false, true);
    }

    private static void airUnder(GameTestHelper h, List<BlockPos> stairs) {
        for (int k = GAP_AT - 6; k <= GAP_AT - 4; k++) h.getLevel().setBlock(stairs.get(k).below(), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    /** He stands deep on his stairs with no stone pickaxe and the iron one at its reserve, the record lost below the first stands; {@code twist} changes the stairs between. */
    private static void climbAboveAGap(GameTestHelper h, java.util.function.Consumer<List<BlockPos>> twist, boolean hasBlocks, boolean cutRecord) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItem(0, ItemStack.EMPTY);
        c.setItem(1, wornIron(IRON_LEFT));
        if (!hasBlocks) { c.setItem(4, ItemStack.EMPTY); c.setItem(5, ItemStack.EMPTY); }
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos here = stairs.get(GAP_AT);
        for (BlockPos stand : stairs.subList(GAP_AT + 1, stairs.size())) for (BlockPos cell : CompanionShaftPlan.column(stand)) h.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), 2 | 16);
        if (cutRecord) c.mineSite().truncate(KNOWN_STANDS);
        twist.accept(stairs);
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        spendRestartsOnTheStairs(h, c, here);
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1, 0)).getY() - SURFACE_MARGIN;
        long[] id = {0};
        int[] heightWhenEnded = {Integer.MIN_VALUE};
        h.onEachTick(() -> {
            if (id[0] != 0 && heightWhenEnded[0] == Integer.MIN_VALUE && c.workResult(id[0]) != null) heightWhenEnded[0] = c.getBlockY();
        });
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(here.above()), "The light on the stairs is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            if (hasBlocks) h.assertTrue(heightWhenEnded[0] >= surface, "He said «" + result.message() + "» at height " + heightWhenEnded[0] + ", below the surface at " + surface + " | " + report(c));
            else h.assertTrue(!result.success() && heightWhenEnded[0] < surface, "He did not end the climb at the gap: " + result.message() + " at height " + heightWhenEnded[0] + " | " + report(c));
        });
    }

    /**
     * Run m13b/void (seed 20261102): the mine was started again under the ground, so its way to the surface was a record of cells above the pit, and with the
     * stone pickaxes broke in the step he dug tunnels with the iron one, down to its reserve, and had nothing left for the gap on the way up. With the way up
     * in the record the iron pickaxe is kept for ore and he goes up for supplies.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void keepsTheIronPickaxeForOreWhenTheRecordHasAWayUp(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        ItemStack last = new ItemStack(Items.STONE_PICKAXE);
        last.setDamageValue(last.getMaxDamage() - 1);
        c.setItem(0, last);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos here = stairs.get(PIT_STEP);
        for (BlockPos stand : stairs.subList(PIT_STEP + 1, stairs.size())) for (BlockPos cell : CompanionShaftPlan.column(stand)) h.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), 2 | 16);
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        spendRestartsOnTheStairs(h, c, here);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(here.above()), "The light on the stairs is not worked out yet");
                id[0] = start(h, c, 1);
            }
            ended(h, c, id[0]);
            int worn = c.getMainHandItem().is(Items.IRON_PICKAXE) ? c.getMainHandItem().getDamageValue() : 0;
            for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.IRON_PICKAXE)) worn += c.getItem(slot).getDamageValue();
            h.assertTrue(worn == 0, "The iron pickaxe was spent on rock with the way up in the record: wear " + worn + " | " + report(c));
        });
    }

    /**
     * Run m10b/void-4 (seed 20261102): on the surface at the entrance, his plug of the last retreat on the first step of the stairs, no stone
     * pickaxe and no wood to make one, the iron pickaxe whole. The plug is rock: «Свою пробку на лестнице убрать не смог (нет подходящей кирки)»
     * ended the order five times. He takes it out with the iron pickaxe, as he does any rock in his way, and goes on to the ore.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void takesOutHisPlugAtTheEntranceWithTheIronPickaxeWhenNoStonePickaxeIsLeft(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItem(0, ItemStack.EMPTY);
        c.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        List<BlockPos> stairs = carveMine(h, c, 0);
        // The retreat closes the second stand of the record behind him.
        BlockPos plug = stairs.get(2);
        h.getLevel().setBlock(plug, Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
        c.mineSite().plugged(plug);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) id[0] = start(h, c, 1);
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The plug stopped the order: " + result.message() + " | " + report(c));
            h.assertTrue(!h.getLevel().getBlockState(plug).is(Blocks.COBBLESTONE), "His plug on the stairs is still there | " + report(c));
        });
    }

    /**
     * Run m8b/mobs (seed 20261102): the iron pickaxe worn to its reserve of sixty, no stone pickaxe and no sticks, and a diamond lying open a few
     * blocks off behind the rock. The reserve is for ore, and this is the ore: he digs the rock with the last wear instead of going up.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void spendsTheIronPickaxeOnRockForADiamondInSight(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        c.setItem(0, ItemStack.EMPTY);
        c.setItem(1, wornIron(IRON_LEFT));
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        carveMine(h, c, TUNNEL_START);
        BlockPos foot = c.mineSite().foot();
        BlockPos end = foot.relative(Direction.WEST, TUNNEL_START);
        // The diamond sits at head height on the line of his tunnel, two cells on, at the end of a slit: a slit cell beside the diamond would be a separate pocket he plugs as a cave. The wear the iron pickaxe may spend lasts about two cells of rock, so it must be close.
        BlockPos diamond = end.relative(Direction.WEST, DIAMOND_AT).above();
        h.getLevel().setBlock(diamond, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 2 | 16);
        for (int k = 1; k < DIAMOND_AT; k++) h.getLevel().setBlock(end.relative(Direction.WEST, k).above(), Blocks.AIR.defaultBlockState(), 2 | 16);
        c.teleportTo(end.getX() + .5, end.getY(), end.getZ() + .5);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He went up from a diamond in sight with the iron pickaxe in the bag: " + result.message() + " | " + report(c));
        });
    }

    private static ItemStack wornIron(int left) {
        ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
        iron.setDamageValue(iron.getMaxDamage() - left);
        return iron;
    }

    /**
     * Run m8b/retreat (seed 20261102): on the surface after the preparation, the entrance four blocks away cannot be walked to (the jungle),
     * and every side of his own cell is shut: the step ended «Не смог дойти до входа в шахту» twice, and the goal with it. He begins the
     * mine at another spot of the surface instead.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsElsewhereWhenTheEntranceCannotBeWalkedTo(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos entrance = new BlockPos(CENTER + CAGE_AT, GROUND + 1, CENTER);
        // Planks over the ground round the entrance: crafted blocks are never dug, so no side of any cell there is free to begin stairs to.
        for (int dx = -CAGE_PAD; dx <= CAGE_PAD; dx++) for (int dz = -CAGE_PAD; dz <= CAGE_PAD; dz++) h.setBlock(new BlockPos(entrance.getX() + dx, GROUND, entrance.getZ() + dz), Blocks.OAK_PLANKS);
        // The entrance itself is walled in with bedrock: the search finds no way to it and he does not move.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 3; dy++) {
            if (dx != 0 || dz != 0 || dy == 3) h.setBlock(entrance.offset(dx, dy, dz), Blocks.BEDROCK);
        }
        BlockPos stand = h.absolutePos(new BlockPos(CENTER + CAGE_AT - CAGE_NEAR, GROUND + 1, CENTER));
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.mineSite().start(h.absolutePos(entrance), Direction.NORTH);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The unreachable entrance stopped the order: " + result.message() + " | " + report(c));
        });
    }

    /**
     * Run m9b/void-2 (seed 20261102): the cell the walk to the stairs pushes him to lies across ground that gives way (the corner of a shelf in
     * a cave, 38 blocks above its floor) and the straight push walked him off it: dead of the fall at the start of the order. Here the cell is four
     * blocks across a gap in a hall; he stays on his shelf.
     */
    @GameTest(template = "marathon_cave", batch = "mine_stops_ledge", timeoutTicks = LEDGE_WATCH + 40)
    public static void doesNotPushOffHisShelfTowardsAStairsCellAcrossAGap(GameTestHelper h) {
        mountain(h, true);
        for (BlockPos cell : BlockPos.betweenClosed(h.absolutePos(new BlockPos(HALL_FROM, DEEPSLATE_TOP + 1, HALL_FROM)), h.absolutePos(new BlockPos(HALL_TO, HALL_TOP, HALL_TO)))) {
            h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        BlockPos shelf = h.absolutePos(new BlockPos(SHELF_AT, SHELF_Y, HALL_MID)), pillar = h.absolutePos(new BlockPos(SHELF_AT + GAP, SHELF_Y, HALL_MID));
        h.getLevel().setBlock(shelf, Blocks.STONE.defaultBlockState(), 2 | 16);
        h.getLevel().setBlock(pillar, Blocks.STONE.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.teleportTo(shelf.getX() + .5, shelf.getY() + 1, shelf.getZ() + .5);
        c.mineSite().start(pillar.above(), Direction.NORTH);
        long[] id = {0};
        boolean[] over = {false};
        h.onEachTick(() -> {
            if (over[0]) return;
            h.assertTrue(c.isAlive() && (id[0] == 0 || c.getY() > shelf.getY() + 1 - LEDGE_DROP), "He walked off the shelf into the gap or died: " + c.position() + " | " + report(c));
        });
        h.runAfterDelay(1, () -> id[0] = start(h, c, 1));
        h.runAfterDelay(LEDGE_WATCH, () -> h.assertTrue(c.getY() > shelf.getY() + 1 - LEDGE_DROP, "He is below the shelf: " + c.position()));
        h.runAfterDelay(LEDGE_WATCH + 1, () -> { over[0] = true; h.succeed(); });
    }

    @AfterBatch(batch = "mine_stops_ledge") public static void afterLedge(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Three restarts spent and carried to the next step, the mine buried at {@code at}: its stairs stay in the record as the way up. */
    private static void spendRestartsOnTheStairs(GameTestHelper h, CompanionEntity c, BlockPos at) {
        c.mineSite().newStep(h.getLevel().getGameTime());
        for (int i = 0; i < 3; i++) {
            c.mineSite().restart(at, Direction.NORTH);
            c.mineSite().buried(true);
        }
    }
}

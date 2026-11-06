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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;

/**
 * Run p1a/void (seed 20261031): the record of a mine started again under the ground held a pit, and above it only the entrance on the surface
 * nine blocks up, with nothing of the way between; the pit is roofed, no walk or floor reaches that cell, and the climb ended «путь наверх закрыт»
 * with an iron pickaxe and shovels in the bag. Arena as in the other mine tests; the pit is a small copy of the shape of the record.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineClimbOutTests {
    private static final String SURFACE = "surface";
    /** Where the record's entrance lies from the one on the surface, as in the run. */
    private static final int EAST = 7, SOUTH = 2, BELOW = 9;
    /** The pit reaches up to the soil under the turf, which is its roof. */
    private static final int PIT_TOP = GROUND - 2, SOIL = 31, SURFACE_MARGIN = 2;
    /** The deep pit: its feet in the deepslate over the diamond ring, east of the surface entrance, and the old stand further east through the rock. */
    private static final int DEEP_EAST = 6, DEEP_FEET = 3, OLD_STAND_EAST = 8;

    private CompanionMineClimbOutTests() { }

    /** The pit, the record and a bag of a pickaxe with wear to spare: he cuts his way through the rock and the roof, and walks to the surface. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void cutsHisWayOutOfARoofedPitWhenTheRecordHasNoWayUp(GameTestHelper h) {
        CompanionEntity c = roofedPit(h, false);
        BlockPos stand = c.mineSite().stands().get(0);
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1 - SURFACE_MARGIN, 0)).getY();
        climb(h, c, stand, result -> {
            h.assertTrue(result.success() && c.getBlockY() >= surface, "He said «" + result.message() + "» at height " + c.getBlockY() + ", below the surface at " + surface + " | " + report(c));
            h.assertTrue(count(c, Items.COBBLESTONE) > 0, "No stone of the way he cut came into the bag | " + report(c));
        });
    }

    /**
     * Runs void-5 (seed 20261031), void-2 (seed 7), restart (seed 314159) of 06.11: the record of a mine started again at the bottom holds a pit, and
     * above it a cell of the old record eight blocks off through solid rock and the entrance on the surface far up; no walk, no plug and no rescue
     * opens that, and the diamonds stayed down there with «путь наверх закрыт». He cuts a staircase of his own up to the surface instead.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void cutsAStaircaseToTheSurfaceWhenTheWayAboveIsShutByRock(GameTestHelper h) {
        CompanionEntity c = deepPit(h);
        BlockPos stand = c.mineSite().stands().get(0);
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1 - SURFACE_MARGIN, 0)).getY();
        int recorded = c.mineSite().above().size();
        climb(h, c, stand, result -> {
            h.assertTrue(result.success() && c.getBlockY() >= surface, "He said «" + result.message() + "» at height " + c.getBlockY() + ", below the surface at " + surface + " | " + report(c));
            h.assertTrue(c.mineSite().above().size() > recorded, "The staircase he cut is not in the record of the way up | " + report(c));
        });
    }

    /** The same pit in bedrock: no cut gets anywhere, the climb ends once, said as it is, and he is still down there. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void endsTheClimbOnceWhenThePitCannotBeCutOutOf(GameTestHelper h) {
        CompanionEntity c = roofedPit(h, true);
        BlockPos stand = c.mineSite().stands().get(0);
        climb(h, c, stand, result -> {
            h.assertTrue(!result.success() && result.message().contains("путь наверх закрыт"), "The climb out of bedrock ended: " + result.message() + " | " + report(c));
            h.assertTrue(c.getBlockY() < h.absolutePos(new BlockPos(0, PIT_TOP + 1, 0)).getY(), "He left a pit of bedrock | " + report(c));
        });
    }

    /** A chamber of three by three deep in the deepslate; the old record had a stand eight blocks east of it through the rock, and its entrance on the surface. */
    private static CompanionEntity deepPit(GameTestHelper h) {
        mountain(h, true);
        BlockPos entrance = new BlockPos(CENTER + DEEP_EAST, DEEP_FEET + 1, CENTER + SOUTH), stand = entrance.north().below();
        for (int x = entrance.getX() - 1; x <= entrance.getX() + 1; x++) for (int z = stand.getZ() - 1; z <= stand.getZ() + 1; z++) for (int y = stand.getY(); y <= stand.getY() + 2; y++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        BlockPos oldStand = stand.east(OLD_STAND_EAST);
        for (BlockPos cell : CompanionShaftPlan.column(oldStand)) h.setBlock(cell, Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        BlockPos from = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), standAt = h.absolutePos(stand);
        CompanionMineSite site = c.mineSite();
        site.start(from, Direction.NORTH);
        site.reached(h.absolutePos(oldStand));
        site.restart(h.absolutePos(entrance), Direction.NORTH);
        site.reached(standAt);
        site.buried(true);
        c.teleportTo(standAt.getX() + .5, standAt.getY(), standAt.getZ() + .5);
        return c;
    }

    static void climb(GameTestHelper h, CompanionEntity c, BlockPos stand, java.util.function.Consumer<CompanionActionResult> check) {
        long[] id = {0};
        h.onEachTick(() -> {
            if (id[0] == 0) {
                // The sky light of a freshly built mountain lags behind it.
                if (h.getLevel().canSeeSky(stand.above())) return;
                h.assertTrue(c.startWork("dig_for_diamonds", SURFACE, 1).success(), "The climb was not accepted: " + c.getLastTaskResult());
                id[0] = c.activeWorkId();
                return;
            }
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = c.workResult(id[0]);
            h.assertTrue(result != null, "No result for the order: " + report(c));
            check.accept(result);
            h.succeed();
        });
    }

    /** A pit of three by three under the soil of the surface, shut in {@code bedrock} on every side if asked; he stands in it with the record of the run. */
    static CompanionEntity roofedPit(GameTestHelper h, boolean bedrock) {
        mountain(h, true);
        BlockPos entrance = new BlockPos(CENTER + EAST, GROUND + 1 - BELOW, CENTER + SOUTH), stand = entrance.north().below();
        for (int x = entrance.getX() - 2; x <= entrance.getX() + 2; x++) for (int z = stand.getZ() - 2; z <= stand.getZ() + 2; z++) for (int y = stand.getY() - 1; y <= PIT_TOP + 1; y++) {
            boolean inside = Math.abs(x - entrance.getX()) <= 1 && Math.abs(z - stand.getZ()) <= 1 && y >= stand.getY() && y <= PIT_TOP;
            if (inside) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            else if (bedrock) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
        h.setBlock(entrance.below(), Blocks.STONE);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItem(0, ItemStack.EMPTY);
        c.setItem(4, new ItemStack(Items.DIRT, SOIL));
        c.setItem(5, new ItemStack(Items.IRON_SHOVEL));
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        BlockPos from = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), standAt = h.absolutePos(stand);
        CompanionMineSite site = c.mineSite();
        site.start(from, Direction.NORTH);
        site.restart(h.absolutePos(entrance), Direction.NORTH);
        site.reached(standAt);
        site.buried(true);
        c.teleportTo(standAt.getX() + .5, standAt.getY(), standAt.getZ() + .5);
        return c;
    }
}

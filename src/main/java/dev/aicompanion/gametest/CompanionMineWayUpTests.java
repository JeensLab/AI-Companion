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

import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * Runs water/2 and void-2/3 of seed 314159, lava/2 and void/3 of seed 7 (07.11): the climb up his own stairs stopped on a cell of the record that
 * held a block of his own, waited for the self-rescue, and cut a new staircase from the middle of the mine until the pickaxes were worn out.
 * The stairs here are real: the surface entrance, ten stands down and a tunnel off the foot, recorded as the expedition records them.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineWayUpTests {
    private static final int STEPS = 10, TUNNEL = 6, PLUG_AT = 3, SURFACE_MARGIN = 2;
    /** What the new staircase of a lost way is said with; the way up of his own never says it. */
    private static final String NEW_STAIRS = "прокапываю новую";

    private CompanionMineWayUpTests() { }

    /**
     * A cell of the tunnel he walks back along holds his own cobblestone, a seal he laid against a pool beside it (water/2 of seed 314159: the walk by hand
     * leaves such a block alone): it is dug out on the spot and the stairs are climbed, no new ones cut.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void climbsHisOwnStairsPastHisOwnBlockInTheWay(GameTestHelper h) {
        CompanionEntity c = mine(h);
        CompanionMineSite site = c.mineSite();
        BlockPos foot = site.foot(), end = foot.relative(Direction.WEST, TUNNEL), plug = foot.relative(Direction.WEST, PLUG_AT);
        h.getLevel().setBlock(plug, Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
        h.getLevel().setBlock(plug.north(), Blocks.WATER.defaultBlockState(), 2 | 16);
        site.sealed(plug);
        c.teleportTo(end.getX() + .5, end.getY(), end.getZ() + .5);
        StringBuilder said = new StringBuilder();
        h.onEachTick(() -> said.append(c.getLastTaskResult()).append('\n'));
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1 - SURFACE_MARGIN, 0)).getY();
        CompanionMineClimbOutTests.climb(h, c, foot, result -> {
            h.assertTrue(result.success() && c.getBlockY() >= surface, "He said «" + result.message() + "» at height " + c.getBlockY() + ", below the surface at " + surface + " | " + report(c));
            h.assertTrue(!said.toString().contains(NEW_STAIRS), "He cut a new staircase though his own was only plugged | " + report(c));
            h.assertTrue(!h.getLevel().getBlockState(plug).is(Blocks.COBBLESTONE) && !site.seals().contains(plug), "His own block stayed in the tunnel: " + h.getLevel().getBlockState(plug).getBlock() + " | " + report(c));
            h.assertTrue(site.above().isEmpty(), "A new staircase is in the record of the way up | " + report(c));
        });
    }

    /** The mine is started again where the tunnel ends; the stairs and the tunnel are still the record of the way up, and he climbs them without cutting. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aMineRestartedBelowKeepsTheOldStairsAsTheWayUp(GameTestHelper h) {
        CompanionEntity c = mine(h);
        CompanionMineSite site = c.mineSite();
        BlockPos foot = site.foot(), entrance = site.entrance(), here = foot.relative(Direction.WEST, TUNNEL);
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        site.restart(here, Direction.NORTH);
        site.buried(true);
        h.assertTrue(site.above().contains(foot) && site.above().get(site.above().size() - 1).equals(entrance) && site.stands().isEmpty(),
                "The old stairs are not the way up of the mine started again: " + site.above().size() + " cells");
        List<BlockPos> kept = List.copyOf(site.above());
        StringBuilder said = new StringBuilder();
        h.onEachTick(() -> said.append(c.getLastTaskResult()).append('\n'));
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1 - SURFACE_MARGIN, 0)).getY();
        CompanionMineClimbOutTests.climb(h, c, here, result -> {
            h.assertTrue(result.success() && c.getBlockY() >= surface, "He said «" + result.message() + "» at height " + c.getBlockY() + ", below the surface at " + surface + " | " + report(c));
            h.assertTrue(!said.toString().contains(NEW_STAIRS) && site.above().equals(kept), "He cut a new staircase though the old one was kept | " + report(c));
        });
    }

    /**
     * Runs of 07.11: the tunnels were dug until «кирок нет» and the climb began with none, so a lost stair meant a staircase dug with nothing. One stone pickaxe and nothing
     * to make another of, at the foot of the stairs: the tunnel is not dug with it, and he climbs while he can still dig, the pickaxe whole.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void turnsBackWhileAPickaxeIsLeftForTheWayUp(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        c.setItem(1, ItemStack.EMPTY);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        c.setFollowing(false);
        carveMine(h, c, 0);
        BlockPos foot = c.mineSite().foot();
        c.teleportTo(foot.getX() + .5, foot.getY(), foot.getZ() + .5);
        long id = start(h, c, 1);
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1 - SURFACE_MARGIN, 0)).getY();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.status() == CompanionActionResult.Status.SHORTAGE && result.message().contains("Кирки на исходе") && c.getBlockY() >= surface,
                    "He said «" + result.message() + "» at height " + c.getBlockY() + " | " + report(c));
            int worn = 0;
            for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.STONE_PICKAXE)) worn += c.getItem(slot).getDamageValue();
            h.assertTrue(count(c, Items.STONE_PICKAXE) == 1 && worn == 0, "The last pickaxe was spent on the tunnel: " + count(c, Items.STONE_PICKAXE) + " left, wear " + worn + " | " + report(c));
        });
    }

    /** Ten stands down from the entrance on the surface and a tunnel west off the foot, cut in the rock and recorded as the expedition records them; he stands at the entrance. */
    private static CompanionEntity mine(GameTestHelper h) {
        mountain(h, false);
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stands = CompanionShaftPlan.stands(entrance, Direction.NORTH, STEPS);
        for (BlockPos stand : stands) for (BlockPos cell : CompanionShaftPlan.column(stand)) h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        CompanionMineSite site = c.mineSite();
        site.start(entrance, Direction.NORTH);
        for (BlockPos stand : stands.subList(1, stands.size())) site.reached(stand);
        BlockPos foot = site.foot();
        site.openTunnel(Direction.WEST);
        for (int k = 1; k <= TUNNEL; k++) {
            BlockPos cell = foot.relative(Direction.WEST, k);
            h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
            h.getLevel().setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
            site.extendTunnel();
        }
        return c;
    }
}

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.DEEPSLATE_TOP;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.DIAMOND_FEET;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.SIZE;
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
    /** The chamber deep in the deepslate: its feet over the bottom of the arena, east of the surface entrance. */
    private static final int DEEP_EAST = 6, DEEP_FEET = 3;
    /** The pit under the foot of the old stairs: this many blocks lower and as many across, to the north (away from the stairs, whose floors stay whole). */
    private static final int UNDER_FOOT = 4;
    /** How far the self-rescue digs from where he stands, and how many cells of rock the record forbids. */
    private static final int RESCUE_REACH = 4, MAX_FORBIDDEN = 256;
    /** What the new staircase of a lost way is said with; the way up of his own never says it. */
    private static final String NEW_STAIRS = "прокапываю новую";
    /** What the short staircase up to his old stairs over a pit is said with. */
    private static final String TO_WAY = "прокапываю к ней ступени";
    /** The end of a climb out of the pit by the staircase cut up from it: the far gap of a pit is no gap for the rescues to chase the cell beyond across. */
    private static final String CUT_OUT = "прокопал новую к поверхности";
    /**
     * The pit under the ground of a tall mountain (the template is 36 high): the feet in it, the corner the entrance on the surface lies in, the tunnel off
     * the foot of its stairs, and the wear left on the iron pickaxe: more than the staircase out of the pit takes on top of the wear kept for ore, or worn
     * below what the staircase takes (78).
     */
    private static final int TALL_GROUND = 33, PIT_FEET = 8, CORNER = 2, PIT_TUNNEL = 2, IRON_LEFT = 150, IRON_WORN = 40, BESIDE = 3;

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

    /**
     * Run lava of seed 7 (07.11): a mine started again under the ground whose record of the way up holds only the entrance on the surface, far above through
     * solid rock. No walk, plug or rescue crosses that gap, and the climb ended «Своя лестница перекрыта» with a diamond in the bag. His old stairs do not lead up
     * from here, so a staircase of his own is the way up, as for a lost one.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void cutsNewStairsWhenTheGapToHisOldStairsCannotBeCrossed(GameTestHelper h) {
        mountain(h, false);
        BlockPos stand = h.absolutePos(new BlockPos(CENTER + DEEP_EAST, DEEP_FEET, CENTER));
        for (int y = 0; y <= 2; y++) h.getLevel().setBlock(stand.above(y), Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(1, ItemStack.EMPTY);
        c.setItem(4, new ItemStack(Items.DIAMOND));
        CompanionMineSite site = c.mineSite();
        site.start(h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), Direction.NORTH);
        site.restart(stand, Direction.NORTH);
        site.buried(true);
        h.assertTrue(site.above().size() == 1, "The record of the way up is not the entrance alone: " + site.above().size() + " cells");
        shutForRescue(h, site, stand);
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1 - SURFACE_MARGIN, 0)).getY();
        CompanionMineClimbOutTests.climb(h, c, stand, result -> {
            h.assertTrue(!result.message().contains("Своя лестница перекрыта") && !result.message().contains("путь наверх закрыт"),
                    "He ended the climb as shut: " + result.message() + " | " + report(c));
            h.assertTrue(result.success() && c.getBlockY() >= surface, "He said «" + result.message() + "» at height " + c.getBlockY() + ", below the surface at " + surface + " | " + report(c));
            h.assertTrue(count(c, Items.DIAMOND) == 1, "The diamond is not in the bag | " + report(c));
        });
    }

    /**
     * Run retreat of seed 7 (series d, 21.11): a mine started again in a pit four blocks under the foot of his old stairs, rock between. The climb took the
     * near gap for the far one out of a pit, cut a staircase from a hundred blocks down, and the pickaxes ran out at height 36. A few steps up to the foot
     * are the way: he cuts them and walks his old stairs to the surface, no staircase to the surface cut. No rescue crosses the gap here, its rock forbidden.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void cutsAShortStaircaseUpToHisOldStairsJustOverThePit(GameTestHelper h) {
        shortStairsOutOfThePit(h, foot -> foot.below(UNDER_FOOT).relative(Direction.NORTH, UNDER_FOOT));
    }

    /**
     * As above, the pit straight under the foot (review of the short staircase: its steps up toward the foot cut the floor of the foot and of the step over
     * it, and the walk up the old stairs found a hole): the steps go round the floors of his stairs.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void cutsAShortStaircaseUpToHisOldStairsStraightOverThePit(GameTestHelper h) {
        shortStairsOutOfThePit(h, foot -> foot.below(UNDER_FOOT));
    }

    /** The old stairs with no tunnel, the mine started again at {@code under} of their foot, and the climb out: by a short staircase and the old stairs, whole. */
    private static void shortStairsOutOfThePit(GameTestHelper h, java.util.function.UnaryOperator<BlockPos> under) {
        CompanionEntity c = mine(h, 0);
        CompanionMineSite site = c.mineSite();
        BlockPos foot = site.foot(), pit = under.apply(foot);
        for (int y = 0; y <= 1; y++) h.getLevel().setBlock(pit.above(y), Blocks.AIR.defaultBlockState(), 2 | 16);
        c.teleportTo(pit.getX() + .5, pit.getY(), pit.getZ() + .5);
        site.restart(pit, Direction.NORTH);
        site.buried(true);
        h.assertTrue(site.above().get(0).equals(foot), "The way up of the mine started again does not begin at the foot of the old stairs: " + site.above().get(0));
        shutForRescue(h, site, pit);
        List<BlockPos> old = List.copyOf(site.above());
        StringBuilder said = new StringBuilder();
        java.util.Set<BlockPos> holed = new java.util.HashSet<>();
        h.onEachTick(() -> {
            said.append(c.getLastTaskResult()).append('\n');
            for (BlockPos stand : old) if (h.getLevel().getBlockState(stand.below()).isAir()) holed.add(stand);
        });
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1 - SURFACE_MARGIN, 0)).getY();
        CompanionMineClimbOutTests.climb(h, c, pit, result -> {
            h.assertTrue(result.success() && c.getBlockY() >= surface, "He said «" + result.message() + "» at height " + c.getBlockY() + ", below the surface at " + surface + " | " + report(c));
            h.assertTrue(!said.toString().contains(NEW_STAIRS) && said.toString().contains(TO_WAY), "He did not cut his way up to his old stairs four blocks over the pit | " + report(c));
            h.assertTrue(holed.isEmpty(), "The floor under steps of his old stairs was cut: " + holed + " | " + report(c));
        });
    }

    /** The self-rescue digs no cell his mine forbids, so within its reach (4 blocks) of {@code stand} it finds no rock to cut; the staircases of the climb take them in a second pass. */
    private static void shutForRescue(GameTestHelper h, CompanionMineSite site, BlockPos stand) {
        Vec3 feet = new Vec3(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        int shut = 0;
        for (int x = -RESCUE_REACH; x <= RESCUE_REACH; x++) for (int y = -RESCUE_REACH; y <= RESCUE_REACH + 2; y++) for (int z = -RESCUE_REACH; z <= RESCUE_REACH; z++) {
            BlockPos cell = stand.offset(x, y, z);
            if (h.getLevel().getBlockState(cell).isAir() || cell.getCenter().distanceToSqr(feet) > RESCUE_REACH * RESCUE_REACH) continue;
            site.forbid(cell);
            shut++;
        }
        h.assertTrue(shut <= MAX_FORBIDDEN, "The rock round the chamber does not fit the forbidden cells of the record: " + shut);
    }

    /**
     * Run mobs of seed 20261031 (21.11): a mine started again in a pit under the ground, the old stairs far off across the rock. With no stone pickaxe and no wood
     * the tunnel at the foot was dug with the iron one down to the wear kept for ore; the staircase cut up out of the pit wore it out at height 38, and he stood
     * there with no pickaxe: «путь наверх закрыт». The iron pickaxe keeps the wear of that staircase as well, so he turns back while it can still cut him out.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void keepsTheWearForTheStairsOutOfAPit(GameTestHelper h) {
        outOfThePit(h, pitMine(h), PIT_TUNNEL);
    }

    /**
     * As above, the mine started again once more at the end of the pit's tunnel (a change of way, lost stairs by a tunnel cell): the way up of the new record
     * begins with the tunnel and the stairs of the pit beside him, and the gap to the surface lies beyond the pit's entrance.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void keepsTheWearForTheStairsOutOfAPitStartedAgainInside(GameTestHelper h) {
        CompanionEntity c = pitMine(h);
        CompanionMineSite site = c.mineSite();
        BlockPos end = c.blockPosition();
        site.restart(end, Direction.WEST);
        site.buried(true);
        h.assertTrue(site.above().get(0).distSqr(end) <= 1, "The way up of the mine started again does not begin beside him: " + site.above().get(0));
        outOfThePit(h, c, 0);
    }

    /**
     * As above, started again three blocks off the end of the tunnel, by a passage to it: a restart takes its way up from a cell that near, and the climb
     * walks there; it is no gap to reserve a staircase for, or the iron pickaxe would never dig the rock.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void keepsTheWearForTheStairsOutOfAPitStartedAgainBesideIt(GameTestHelper h) {
        CompanionEntity c = pitMine(h);
        CompanionMineSite site = c.mineSite();
        BlockPos end = c.blockPosition(), here = end.relative(Direction.WEST, BESIDE).relative(Direction.NORTH);
        for (BlockPos cell : List.of(end.relative(Direction.WEST), end.relative(Direction.WEST, 2), end.relative(Direction.WEST, BESIDE), here)) {
            h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
            h.getLevel().setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        site.restart(here, Direction.WEST);
        site.buried(true);
        double off = site.above().get(0).distSqr(here);
        h.assertTrue(off > 9 && off <= 12, "The way up of the mine started again does not begin three blocks off: " + site.above().get(0) + ", " + off);
        outOfThePit(h, c, 0);
    }

    /**
     * The same pit with one stone pickaxe and an iron one too worn to cut the staircase up from it: the iron pickaxe is no way up from here, so the stone one
     * is the last and the tunnel is not dug with it. Counted as a way up, the iron pickaxe let the stone one go on the tunnel, and its wear could not cut the stairs.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void aWornIronPickaxeIsNoWayUpFromAPit(GameTestHelper h) {
        CompanionEntity c = pitMine(h);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        c.getItem(1).setDamageValue(c.getItem(1).getMaxDamage() - IRON_WORN);
        long id = start(h, c, 1);
        int surface = c.mineSite().origin().getY() - SURFACE_MARGIN;
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(!result.message().contains("путь наверх закрыт") && c.getBlockY() >= surface,
                    "He said «" + result.message() + "» at height " + c.getBlockY() + ", below the surface at " + surface + " | " + report(c));
            h.assertTrue(result.message().contains("Кирки на исходе") && result.message().contains(CUT_OUT),
                    "He did not turn back while the stone pickaxe was left, or not out by the staircase he cut: " + result.message() + " | " + report(c));
        });
    }

    /**
     * Orders the mine where he stands and waits for it to end: out on the surface with the iron pickaxe whole, after a tunnel longer than {@code tunnel}
     * cells in all was dug with the iron pickaxe (a reserve too large would keep it from the rock).
     */
    private static void outOfThePit(GameTestHelper h, CompanionEntity c, int tunnel) {
        long id = start(h, c, 1);
        int surface = c.mineSite().origin().getY() - SURFACE_MARGIN;
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(!result.message().contains("путь наверх закрыт") && c.getBlockY() >= surface,
                    "He said «" + result.message() + "» at height " + c.getBlockY() + ", below the surface at " + surface + " | " + report(c));
            h.assertTrue(result.message().contains(CUT_OUT), "He did not come out by the staircase he cut: " + result.message() + " | " + report(c));
            h.assertTrue(count(c, Items.IRON_PICKAXE) == 1, "The iron pickaxe was worn out on the way | " + report(c));
            int dug = c.mineSite().tunnels().stream().mapToInt(CompanionMineSite.Tunnel::length).sum();
            h.assertTrue(dug > tunnel, "The iron pickaxe dug no tunnel: " + dug + " cells | " + report(c));
        });
    }

    /**
     * A pit low in a tall mountain, the mine started again in it, its record of the way up the entrance on the surface alone, far off in the opposite corner:
     * its stairs down to the depth of diamonds and a tunnel off the foot. He stands at the end of the tunnel with an iron pickaxe and no stone one, no wood.
     */
    private static CompanionEntity pitMine(GameTestHelper h) {
        tallMountain(h);
        // Corner to corner: the entrance on the surface is too far for the walk back to it (FAR_ENTRANCE), so the climb is the way up, as in the run.
        BlockPos origin = h.absolutePos(new BlockPos(CORNER, TALL_GROUND + 1, CORNER)), pit = h.absolutePos(new BlockPos(SIZE - 1 - CORNER, PIT_FEET, SIZE - 1 - CORNER));
        List<BlockPos> stairs = CompanionShaftPlan.stands(pit, Direction.NORTH, PIT_FEET - DIAMOND_FEET);
        for (BlockPos stand : stairs) for (BlockPos cell : CompanionShaftPlan.column(stand)) h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h, CORNER, TALL_GROUND + 1, CORNER);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        c.setItem(0, ItemStack.EMPTY);
        ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
        iron.setDamageValue(iron.getMaxDamage() - IRON_LEFT);
        c.setItem(1, iron);
        CompanionMineSite site = c.mineSite();
        site.start(origin, Direction.NORTH);
        site.restart(pit, Direction.NORTH);
        site.buried(true);
        for (BlockPos stand : stairs.subList(1, stairs.size())) site.reached(stand);
        site.openTunnel(Direction.WEST);
        BlockPos end = site.foot().relative(Direction.WEST, PIT_TUNNEL);
        for (int k = 1; k <= PIT_TUNNEL; k++) {
            BlockPos cell = site.foot().relative(Direction.WEST, k);
            h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
            h.getLevel().setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
            site.extendTunnel();
        }
        c.teleportTo(end.getX() + .5, end.getY(), end.getZ() + .5);
        return c;
    }

    /** The ground of {@link CompanionMineBacktrackTests#mountain} up to {@link #TALL_GROUND}, no ore: a pit low in it lies deeper under the surface than the wear kept for ore cuts. */
    private static void tallMountain(GameTestHelper h) {
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= TALL_GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            Block block = y == 0 || edge ? Blocks.BEDROCK : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < TALL_GROUND - 2 ? Blocks.STONE : y < TALL_GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    private static CompanionEntity mine(GameTestHelper h) { return mine(h, TUNNEL); }

    /** Ten stands down from the entrance on the surface and a tunnel west off the foot ({@code tunnel} cells, none for 0), cut in the rock and recorded as the expedition records them; he stands at the entrance. */
    private static CompanionEntity mine(GameTestHelper h, int tunnel) {
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
        if (tunnel == 0) return c;
        site.openTunnel(Direction.WEST);
        for (int k = 1; k <= tunnel; k++) {
            BlockPos cell = foot.relative(Direction.WEST, k);
            h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
            h.getLevel().setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
            site.extendTunnel();
        }
        return c;
    }
}

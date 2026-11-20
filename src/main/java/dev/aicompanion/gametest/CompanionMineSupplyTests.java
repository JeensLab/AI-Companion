package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
 * The kit of the mine and what runs short below (runs of 07.11): the torches (descent of seed 20261031: down with thirty-two, up for light at
 * y -30, and the coal in the walls on the way passed by), the bench (void-2 of seed 7: the bench of the kit stayed by the entrance after a craft
 * on the surface, and no pickaxe could be made below), and a pickaxe (void-5 of seed 20261031: a mine begun bare-handed in a cave ended buried).
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineSupplyTests {
    /** The step whose wall holds the coal, and the torches in the bag then: half the stock, far above what a look for coal used to need. */
    private static final int COAL_STEP = 5, TORCHES_SHORT = 32;
    /** The cave he is found in: below the entrance and aside of it, a chamber of three by three. */
    private static final int CAVE_BELOW = 20, CAVE_ASIDE = 10, CAVE_HALF = 1, CAVE_HEIGHT = 2, CAVE_TIMEOUT = 400;
    /** The bench of the kit stands where the kit was made, beyond the reach the mine's own temporary blocks are looked for in (void of seed 7: forty-eight blocks from the entrance). */
    private static final int BENCH_ASIDE = 20, SETTLE = 5;

    private CompanionMineSupplyTests() { }

    /** Coal in the wall of the fifth step with thirty-two torches in the bag: it is taken on the way down, not passed by until the torches are nearly gone. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void minesCoalInSightOnTheWayDownWhileTorchesAreShort(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        c.setItem(3, new ItemStack(Items.TORCH, TORCHES_SHORT));
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        BlockPos coal = CompanionShaftPlan.stands(entrance, Direction.NORTH, COAL_STEP).get(COAL_STEP).east();
        h.getLevel().setBlock(coal, Blocks.COAL_ORE.defaultBlockState(), 2 | 16);
        start(h, c, 1);
        h.succeedWhen(() -> h.assertTrue(count(c, Items.COAL) >= 1 || !h.getLevel().getBlockState(coal).is(Blocks.COAL_ORE),
                "The coal in the wall of step " + COAL_STEP + " was passed by | " + report(c)));
    }

    /** Out of torches at the foot of his stairs, with the record that the surface had no coal: he digs on in the dark to the diamonds instead of climbing for light that is not up there. */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void doesNotClimbForTorchesTheSurfaceDidNotHave(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        c.setItem(3, ItemStack.EMPTY);
        carveMine(h, c, 0);
        BlockPos foot = c.mineSite().foot();
        c.teleportTo(foot.getX() + .5, foot.getY(), foot.getZ() + .5);
        c.mineSite().coalAbove(false);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He said «" + result.message() + "» | " + report(c));
        });
    }

    /** A mine on record, and the order finds him in a cave far below with no pickaxe and nothing to make one of: the order ends short of supplies at once, and no mine is begun bare-handed down there. */
    @GameTest(template = "marathon_cave", timeoutTicks = CAVE_TIMEOUT)
    public static void doesNotStartAMineWithoutAPickaxeFromACave(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        c.setItem(0, ItemStack.EMPTY);
        c.setItem(1, ItemStack.EMPTY);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        c.setFollowing(false);
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), Direction.NORTH);
        BlockPos cave = new BlockPos(CENTER + CAVE_ASIDE, GROUND + 1 - CAVE_BELOW, CENTER);
        for (int x = -CAVE_HALF; x <= CAVE_HALF; x++) for (int z = -CAVE_HALF; z <= CAVE_HALF; z++) for (int y = 0; y <= CAVE_HEIGHT; y++) h.setBlock(cave.offset(x, y, z), Blocks.AIR);
        BlockPos at = h.absolutePos(cave);
        c.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.status() == CompanionActionResult.Status.SHORTAGE && c.mineSite().restarts() == 0 && !c.mineSite().buried(),
                    "He said «" + result.message() + "», restarts " + c.mineSite().restarts() + ", buried " + c.mineSite().buried() + " | " + report(c));
        });
    }

    /** The bench of the kit stayed where the kit was made, a walk from the entrance: before the way down it goes back into the bag, so that pickaxes can be made below. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void takesHisOwnBenchUpBeforeGoingDown(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        BlockPos bench = new BlockPos(CENTER + BENCH_ASIDE, GROUND + 1, CENTER + BENCH_ASIDE);
        h.setBlock(bench, Blocks.CRAFTING_TABLE);
        BlockPos at = h.absolutePos(bench);
        c.rememberPlaced(at);
        // A tick on the ground first: a fresh spawn is still in the air, and no walk to the bench is searched from there.
        h.runAfterDelay(SETTLE, () -> start(h, c, 1));
        h.succeedWhen(() -> h.assertTrue(count(c, Items.CRAFTING_TABLE) == 1 && !h.getLevel().getBlockState(at).is(Blocks.CRAFTING_TABLE),
                "The bench of the kit is not in his bag | " + report(c)));
    }

    /** The fact about the surface coal is saved with and without a mine on record, survives the start of a mine, and an old save loads it as unknown. */
    @GameTest(template = "marathon_cave")
    public static void theSurfaceCoalFactSurvivesASaveAndAnOldSaveLoadsAsUnknown(GameTestHelper h) {
        CompanionMineSite site = new CompanionMineSite();
        site.coalAbove(false);
        CompoundTag tag = new CompoundTag();
        site.save(tag);
        CompanionMineSite loaded = new CompanionMineSite();
        loaded.load(tag);
        h.assertTrue(loaded.noCoalAbove(), "The fact that the surface had no coal was lost over a save without a mine");
        site.start(h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), Direction.NORTH);
        site.save(tag);
        loaded.load(tag);
        h.assertTrue(loaded.noCoalAbove() && loaded.exists(), "The fact was lost over the start of a mine or a save with one");
        site.coalAbove(true);
        site.save(tag);
        loaded.load(tag);
        h.assertTrue(!loaded.noCoalAbove(), "Coal found on the surface did not clear the fact");
        CompanionMineSite old = new CompanionMineSite();
        old.load(new CompoundTag());
        h.assertTrue(!old.noCoalAbove(), "A save from before the fact does not load as unknown");
        h.succeed();
    }
}

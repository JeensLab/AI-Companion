package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
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
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * Stops of the stairs that ended or froze the order: a foe he can see and cannot reach, and a mine whose record is left
 * with no way back to its stairs. Arena as in the other mine tests.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineStopsTests {
    private static final int CHAMBER_REACH = 13, CHAMBER_HEIGHT = 9, PILLAR_AT = 6, PILLAR_HEIGHT = 5, OWN_STAND = 3, PIT = 11, FAR_ENTRANCE = 200, POCKET_AT = 8, NEAR_CELLS = 2, PIT_STEP = 8, POCKET_TIMEOUT = 24000;
    private static final int GOAL_TIMEOUT = 24000, DROP_AT = 8, DROP_Y = 5, STARTED_BY = 1200;

    private CompanionMineStopsTests() { }

    /**
     * A zombie on a pillar in a chamber at the foot of the stairs: in sight, near, and out of his reach. He does not stand facing it for
     * the rest of the order; the foe he cannot get at is let go and the order goes on to the diamond.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void doesNotStandForeverFacingAFoeHeCannotReach(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        c.setItem(6, new ItemStack(Items.STONE_SWORD));
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = 0; dx <= CHAMBER_REACH; dx++) for (int dz = -CHAMBER_REACH / 2; dz <= CHAMBER_REACH / 2; dz++) for (int dy = 0; dy < CHAMBER_HEIGHT; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        BlockPos pillar = foot.offset(PILLAR_AT, 0, 0);
        for (int dy = 0; dy < PILLAR_HEIGHT; dy++) h.getLevel().setBlock(pillar.above(dy), Blocks.STONE.defaultBlockState(), 2 | 16);
        c.teleportTo(foot.getX() + OWN_STAND + .5, foot.getY(), foot.getZ() + .5);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, rel(h, pillar.above(PILLAR_HEIGHT)));
        zombie.setPersistenceRequired();
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not get past the foe he cannot reach: " + result.message() + " | zombie " + (zombie.isAlive() ? "alive" : "gone") + " | " + report(c));
        });
    }

    /**
     * The mine was started again in a pit on his own stairs (the record's entrance is the pit), and there is no stone pickaxe and no iron one
     * with wear to spare; wood and stone lie on the surface by the first entrance. The pit is no place to make a pickaxe, so he goes up to the
     * first entrance, makes it from real logs and stone, and the goal goes on to the diamond instead of stopping for want of a pickaxe.
     */
    @GameTest(template = "marathon_cave", batch = "mine_stops_pit", timeoutTicks = POCKET_TIMEOUT)
    public static void goesUpFromAPitForWoodWhenThereIsNoPickaxe(GameTestHelper h) {
        mountain(h, true);
        for (int[] trunk : new int[][]{{CENTER - 6, CENTER + 6}, {CENTER - 4, CENTER + 8}, {CENTER - 8, CENTER + 4}}) for (int y = GROUND + 1; y <= GROUND + 5; y++) h.setBlock(new BlockPos(trunk[0], y, trunk[1]), Blocks.OAK_LOG);
        CompanionEntity c = h.spawn(dev.aicompanion.AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - 4);
        ItemStack[] bag = {worn, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 32), new ItemStack(Items.COBBLESTONE, 46),
                new ItemStack(Items.STICK), new ItemStack(Items.CRAFTING_TABLE)};
        for (int slot = 0; slot < bag.length; slot++) c.setItem(slot, bag[slot]);
        CompanionDiamondGoalTests.Rig r = CompanionDiamondGoalTests.bind(h, c, 3);
        BlockPos pit = carveMine(h, c, 0).get(PIT_STEP);
        c.teleportTo(pit.getX() + .5, pit.getY(), pit.getZ() + .5);
        c.mineSite().restart(pit, Direction.WEST);
        c.mineSite().buried(true);
        boolean[] started = {false};
        h.onEachTick(() -> {
            if (!started[0]) return;
            h.assertTrue(c.isAlive(), "Died | " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + CompanionDiamondGoalTests.report(h, r));
        });
        h.succeedWhen(() -> {
            // The light engine runs between ticks: the goal starts once the pit reads as roofed over, as it does in the world.
            if (!started[0]) {
                h.assertTrue(!h.getLevel().canSeeSky(pit.above()), "The light in the pit is not worked out yet");
                CompanionDiamondGoalTests.start(h, r, 1);
                started[0] = true;
            }
            h.assertTrue(CompanionDiamondGoalTests.state(h, r) == CompanionAgenda.State.COMPLETED && count(c, Items.DIAMOND) >= 1,
                    "No diamond from the pit: " + CompanionDiamondGoalTests.report(h, r));
        });
    }

    @AfterBatch(batch = "mine_stops_pit") public static void afterPit(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /**
     * He stands in a closed pit deep under the ground; the record's entrance is far away on the surface and no stairs are left near him,
     * and no spot for a new entrance is in reach. He does not give up with "no place for an entrance": the mine starts again in the pit.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainInAPitWhenTheEntranceIsFarAway(GameTestHelper h) {
        mountain(h, true);
        for (int x = PIT - 1; x <= PIT + 1; x++) for (int z = PIT - 1; z <= PIT + 1; z++) for (int y = DEEPSLATE_TOP; y <= DEEPSLATE_TOP + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        CompanionEntity c = rig(h);
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
     * The record's stairs were turned into flat stands deep down, away from the entrance, and he stands by the entrance on the surface with
     * every side of his cell refused: the way to the stands is gone and the mine cannot start again at his cell. He goes back to the entrance
     * and begins stairs to a side not tried from it, instead of ending with "could not get back to my stairs".
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
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

    @AfterBatch(batch = "mine_stops_frozen_drop") public static void afterFrozenDrop(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /**
     * Run m4b/lava (seed 20261102): held drops of the tunnels were flushed into chunks nobody ticks, and hung in the air with the pickup
     * delay not running down. The drop is his harvest and he cannot take it, so the pickup waited for it for ever: the step of the goal ended
     * «стоял без продвижения» and the goal waited for ever «занят другим делом». The pickup lets such a drop go after a while and the goal
     * goes on to the diamond.
     */
    @GameTest(template = "marathon_cave", batch = "mine_stops_frozen_drop", timeoutTicks = GOAL_TIMEOUT)
    public static void goesOnToTheDiamondWhenADropOfHisNeverSettles(GameTestHelper h) {
        CompanionDiamondGoalTests.Rig r = CompanionDiamondGoalTests.rig(h, false, CompanionDiamondGoalTests.supplies(32, 4));
        // While the drop hangs he wanders off the spot he began on: the ring of ore is within the reach of a mine from anywhere.
        mountain(h);
        BlockPos cell = new BlockPos(CENTER + DROP_AT, DROP_Y, CENTER + DROP_AT);
        h.setBlock(cell, Blocks.AIR);
        ItemEntity drop = new ItemEntity(h.getLevel(), h.absolutePos(cell).getX() + .5, h.absolutePos(cell).getY() + .35, h.absolutePos(cell).getZ() + .5, new ItemStack(Items.COBBLED_DEEPSLATE));
        drop.setNoGravity(true);
        drop.setDeltaMovement(0, 0, 0);
        drop.setNeverPickUp();
        drop.setUnlimitedLifetime();
        h.getLevel().addFreshEntity(drop);
        r.c().claimDrops(List.of(drop));
        CompanionDiamondGoalTests.start(h, r, 1);
        h.onEachTick(() -> {
            h.assertTrue(r.c().isAlive(), "Died | " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + CompanionDiamondGoalTests.report(h, r));
        });
        h.runAtTickTime(STARTED_BY, () -> {
            if (!r.c().mineSite().exists() || !drop.isAlive()) h.fail("The goal never began its step while the drop hangs: " + CompanionDiamondGoalTests.report(h, r));
        });
        h.succeedWhen(() -> {
            h.assertTrue(CompanionDiamondGoalTests.state(h, r) == CompanionAgenda.State.COMPLETED, "Goal incomplete | " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.count(r.c(), Items.DIAMOND) >= 1, "No diamond | " + CompanionDiamondGoalTests.report(h, r));
        });
    }
}

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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
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
    /** Corner of the arena: about thirty blocks from the entrance at its centre, twice the search range. */
    private static final int FAR_TREE_AT = 3;
    private static final int GOAL_TIMEOUT = 24000, DROP_AT = 8, DROP_Y = 5, STARTED_BY = 1200, CAGE_AT = 8, CAGE_PAD = 7, CAGE_NEAR = 5, SURFACE_MARGIN = 2, CREEPER_AT = 8;
    private static final int TUNNEL_START = 2, BESIDE_AT = 18, OVERHEAD_AT = 14, SPARE_PICKAXES = 6;
    /** Wear left of the iron pickaxe (the reserve kept for ore is just above it), the stands the record still knows, the stand he is in, and the one his plug closes. */
    private static final int IRON_LEFT = 60, KNOWN_STANDS = 3, GAP_AT = 12, PLUG_AT = 8, FAR_PLUG_AT = 4, WALL_AT = 5, DIAMOND_AT = 6;
    private static final int LAKE_REACH = 7, LEDGE_LENGTH = 4;
    private static final int CANOPY_AT = 12, CANOPY_REACH = 2;
    private static final int HALL_FROM = 6, HALL_TO = 18, HALL_TOP = 19, HALL_MID = 12, SHELF_AT = 8, SHELF_Y = 17, GAP = 4, LEDGE_DROP = 2, LEDGE_WATCH = 160;

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
        pitWithoutPickaxe(h, new int[][]{{CENTER - 6, CENTER + 6}, {CENTER - 4, CENTER + 8}, {CENTER - 8, CENTER + 4}}, 1, false, false);
    }

    /**
     * Runs m10b/mobs and m10b/void-4 (seed 20261102): a jungle, where the leaves of the trees roof the whole surface. The climb for supplies ends at the
     * entrance on the surface, but with leaves overhead he read as still down in the mine, and the next step of the goal was the mine alone,
     * without the pickaxe to make: «Каменной кирки у меня нет» five times at height 65. The leaves are no roof: he makes the pickaxe on the surface.
     */
    @GameTest(template = "marathon_cave", batch = "mine_stops_pit", timeoutTicks = POCKET_TIMEOUT)
    public static void makesHisPickaxeOnTheSurfaceUnderALeafCanopy(GameTestHelper h) {
        pitWithoutPickaxe(h, new int[][]{{CENTER - 6, CENTER + 6}, {CENTER - 4, CENTER + 8}, {CENTER - 8, CENTER + 4}}, 1, false, true);
    }

    /**
     * The same, but the only trees are a long walk from the entrance, beyond the search range of sixteen blocks (run f10e, seed 7741901: no
     * wood in range, the goal stopped five times with «копать нечем»). A goal's wood for a pickaxe is looked for farther; he walks there,
     * chops a real trunk, makes the pickaxe and the goal goes on to the diamond. No sticks in the bag either.
     */
    @GameTest(template = "marathon_cave", batch = "mine_stops_pit", timeoutTicks = POCKET_TIMEOUT)
    public static void walksFarForWoodWhenNoTreeIsNearAndThereIsNoPickaxe(GameTestHelper h) {
        pitWithoutPickaxe(h, new int[][]{{FAR_TREE_AT, FAR_TREE_AT}, {FAR_TREE_AT + 2, FAR_TREE_AT}, {FAR_TREE_AT, FAR_TREE_AT + 2}}, 0, true, false);
    }

    private static void pitWithoutPickaxe(GameTestHelper h, int[][] trunks, int sticks, boolean chopped, boolean canopy) {
        mountain(h, true);
        if (canopy) leafCanopy(h);
        List<BlockPos> cut = new java.util.ArrayList<>();
        for (int[] trunk : trunks) for (int y = GROUND + 1; y <= GROUND + 5; y++) h.setBlock(new BlockPos(trunk[0], y, trunk[1]), Blocks.OAK_LOG);
        for (int[] trunk : trunks) cut.add(h.absolutePos(new BlockPos(trunk[0], GROUND + 1, trunk[1])));
        CompanionEntity c = h.spawn(dev.aicompanion.AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - 4);
        ItemStack[] bag = {worn, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 32), new ItemStack(Items.COBBLESTONE, 46),
                new ItemStack(Items.STICK, sticks), new ItemStack(Items.CRAFTING_TABLE)};
        for (int slot = 0; slot < bag.length; slot++) c.setItem(slot, bag[slot]);
        CompanionDiamondGoalTests.Rig r = CompanionDiamondGoalTests.bind(h, c, 3);
        // The goal starts once the light is worked out; until then he must not follow his owner up the stairs, or the start is a race.
        c.setFollowing(false);
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
                if (canopy) h.assertTrue(!h.getLevel().canSeeSky(h.absolutePos(new BlockPos(CENTER, GROUND + 2, CENTER))), "The light under the leaves is not worked out yet");
                CompanionDiamondGoalTests.start(h, r, 1);
                started[0] = true;
            }
            h.assertTrue(CompanionDiamondGoalTests.state(h, r) == CompanionAgenda.State.COMPLETED && count(c, Items.DIAMOND) >= 1,
                    "No diamond from the pit: " + CompanionDiamondGoalTests.report(h, r));
            if (chopped) h.assertTrue(cut.stream().anyMatch(log -> !h.getLevel().getBlockState(log).is(Blocks.OAK_LOG)),
                    "No trunk of the far trees was chopped: " + CompanionDiamondGoalTests.report(h, r));
        });
    }

    /** A patch of leaves high over the entrance, above the trunks: the sky light does not reach the entrance, and the rest of the arena stays open. */
    private static void leafCanopy(GameTestHelper h) {
        for (int x = CENTER - CANOPY_REACH; x <= CENTER + CANOPY_REACH; x++) for (int z = CENTER - CANOPY_REACH; z <= CENTER + CANOPY_REACH; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, GROUND + CANOPY_AT, z)), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2 | 16);
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
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
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
     * Run m9b/void-4 (seed 20261102): the dry nook of a dripstone cave, dripstone on every side and the record's entrance far away: no side
     * took stairs, as dripstone was no block he digs, and the order stood «Дальше лестницу не прокопать» five times at the same spot.
     * Dripstone is rock like any other: the mine starts again in the nook.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainInANookWalledWithDripstone(GameTestHelper h) {
        mountain(h, true);
        BlockPos nook = h.absolutePos(new BlockPos(PIT, DEEPSLATE_TOP, PIT));
        for (BlockPos cell : BlockPos.betweenClosed(nook.offset(-2, 0, -2), nook.offset(2, 3, 2))) h.getLevel().setBlock(cell, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 2 | 16);
        for (BlockPos cell : CompanionShaftPlan.column(nook)) h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.teleportTo(nook.getX() + .5, nook.getY(), nook.getZ() + .5);
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER)), Direction.NORTH);
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(nook.above()), "The light in the nook is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He stood in the dripstone nook: " + result.message() + " | " + report(c));
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

    /**
     * Run m12a/void (seed 20261031): he died with everything on him and came back to the surface with empty hands, and a creeper stood by
     * the things he went for. Without a sword he cannot meet it, so he stepped away, the pickup brought him back, and so on, 979 times in
     * 57 000 ticks, with the goal waiting for the pickup. The creeper does not leave: after a few steps away he lets the things lie
     * and the goal goes on.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = GOAL_TIMEOUT)
    public static void letsADropLieWhenACreeperWillNotLeaveItAndTheGoalGoesOn(GameTestHelper h) {
        ItemStack[] bag = {new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 32), new ItemStack(Items.COBBLESTONE, 32),
                new ItemStack(Items.STICK, 8), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE)};
        CompanionDiamondGoalTests.Rig r = CompanionDiamondGoalTests.rig(h, false, bag);
        r.c().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        BlockPos dropAt = h.absolutePos(new BlockPos(CENTER - CREEPER_AT - 2, GROUND + 1, CENTER));
        Creeper creeper = h.spawn(EntityType.CREEPER, new BlockPos(CENTER - CREEPER_AT, GROUND + 1, CENTER));
        creeper.setPersistenceRequired();
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        ItemEntity drop = new ItemEntity(h.getLevel(), dropAt.getX() + .5, dropAt.getY(), dropAt.getZ() + .5, new ItemStack(Items.COBBLESTONE));
        drop.setDeltaMovement(0, 0, 0);
        drop.setNoPickUpDelay();
        drop.setUnlimitedLifetime();
        h.getLevel().addFreshEntity(drop);
        r.c().claimDrops(List.of(drop));
        CompanionDiamondGoalTests.start(h, r, 1);
        h.onEachTick(() -> {
            h.assertTrue(r.c().isAlive(), "Died | " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(creeper.isAlive() && drop.isAlive(), "SETUP: the creeper or the drop is gone | " + CompanionDiamondGoalTests.report(h, r));
        });
        h.succeedWhen(() -> h.assertTrue(!r.c().expeditionStage().isEmpty() || r.c().mineSite().exists(),
                "The goal still waits for the drop beside the creeper | " + CompanionDiamondGoalTests.report(h, r)));
    }

    /**
     * Run m12b/void-4 (seed 20261031): he came back to his things deep in the mine after a death, and the goal's next milestone was the iron
     * pickaxe, which wants boards and sticks: «Не нашёл доступного дерева для подготовки», over and over, as the search for logs does not
     * reach the surface from down there. A tree is on the surface: he goes up his own stairs first, makes the pickaxe there, and the goal
     * goes on. (The search window of the arena is too small to lose the trees from the bottom of the stairs, so this one guards the way up.)
     */
    @GameTest(template = "marathon_cave", timeoutTicks = GOAL_TIMEOUT)
    public static void makesThePickaxeOnTheSurfaceWhenHeIsDeepInTheMineAndNeedsWood(GameTestHelper h) {
        CompanionDiamondGoalTests.Rig r = deepWithoutWood(h, true);
        h.onEachTick(() -> {
            h.assertTrue(r.c().isAlive(), "Died | " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + CompanionDiamondGoalTests.report(h, r));
        });
        h.succeedWhen(() -> h.assertTrue(count(r.c(), Items.IRON_PICKAXE) >= 1, "No iron pickaxe | " + CompanionDiamondGoalTests.report(h, r)));
    }

    /**
     * The same with no tree anywhere: the goal has to stop, but not at the bottom of the mine with «Не нашёл доступного дерева» said five
     * times over: he goes up first, looks on the surface, and stops there once.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = GOAL_TIMEOUT)
    public static void stopsForWantOfWoodOnTheSurfaceNotAtTheBottomOfTheMine(GameTestHelper h) {
        CompanionDiamondGoalTests.Rig r = deepWithoutWood(h, false);
        BlockPos entrance = r.c().mineSite().entrance();
        h.onEachTick(() -> {
            h.assertTrue(r.c().isAlive(), "Died | " + CompanionDiamondGoalTests.report(h, r));
            if (CompanionDiamondGoalTests.state(h, r) != CompanionAgenda.State.BLOCKED) return;
            h.assertTrue(r.c().getY() >= entrance.getY() - SURFACE_MARGIN, "The goal stopped under the ground: " + CompanionDiamondGoalTests.report(h, r));
            h.assertTrue(CompanionDiamondGoalTests.goal(h, r).reason().contains("дерева"), "Stopped for another reason: " + CompanionDiamondGoalTests.report(h, r));
            h.succeed();
        });
    }

    /** Three iron ingots, a bench and no wood in the bag, deep down his own stairs; the logs, if any, stand on the surface by the entrance. The goal is taken up. */
    private static CompanionDiamondGoalTests.Rig deepWithoutWood(GameTestHelper h, boolean trees) {
        ItemStack[] bag = {new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 32),
                new ItemStack(Items.COBBLESTONE, 46), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE)};
        CompanionDiamondGoalTests.Rig r = CompanionDiamondGoalTests.rig(h, true, bag);
        CompanionEntity c = r.c();
        c.setFollowing(false);
        if (trees) for (int[] trunk : new int[][]{{CENTER - 6, CENTER + 6}, {CENTER - 4, CENTER + 8}, {CENTER - 8, CENTER + 4}})
            for (int y = GROUND + 1; y <= GROUND + 5; y++) h.setBlock(new BlockPos(trunk[0], y, trunk[1]), Blocks.OAK_LOG);
        BlockPos deep = carveMine(h, c, 0).get(PIT_STEP);
        c.teleportTo(deep.getX() + .5, deep.getY(), deep.getZ() + .5);
        CompanionDiamondGoalTests.start(h, r, 1);
        return r;
    }

    /**
     * Runs m4b/void-3 (seed 20261102) and m4a/water (20261031): he stands on a ledge of a cave walled in by his own plugs. The cells beside
     * him are plugs forbidden for good, every side is refused, and no side of the entrance on the surface is left to begin stairs to: the goal
     * stood «Не смог дойти до входа в шахту» five times. He digs down out of the ring, the mine begins again below it and reaches the ore;
     * the ring stays shut.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
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

    /** The water run's record: three restarts spent and carried, buried. The dig out is the one restart past the cap, and its stairs reach the ore. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void digsOutWhenTheRestartsAreSpent(GameTestHelper h) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        BlockPos pocket = h.absolutePos(new BlockPos(PIT, DEEPSLATE_TOP, PIT));
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER)), Direction.NORTH);
        spendRestarts(h, c, pocket, 3);
        c.mineSite().buried(true);
        pluggedPocket(h, c);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He stood with the restarts spent: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 4, "Restarts " + c.mineSite().restarts());
        });
    }

    /**
     * Run f9b/void-3 (seed 20261102): three restarts spent and carried to the next steps, the walk to the entrance failed, and he stood in the
     * cave with every side free to begin stairs to: each step ended «Не смог дойти до входа в шахту» at once. The cap alone stops him, so he gets
     * the one restart past it, here, and the stairs go on.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
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
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
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

    @AfterBatch(batch = "mine_stops_tunnel") public static void afterTunnel(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /**
     * Run m8b/void-4: the tunnel is two lanes wide and he walks the left one, so a diamond in the wall behind the right lane (its drop would
     * rest two columns off) and one over the right lane (not in sight through the ceiling) lay open and untaken for the whole order. Both
     * have an open face on a cell of his own tunnel: he steps to the right lane, takes them and goes on.
     */
    @GameTest(template = "marathon_cave", batch = "mine_stops_tunnel", timeoutTicks = TIMEOUT)
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
     * Run m10a/void (seed 20261031): every one of the twenty-seven slots held something, dirt and gravel and wooden tools among them, and the diamond
     * he had mined out had no slot to fall into: «Сумка полна, добытое некуда класть» five times. He throws the rubble out (real items, in the
     * world) until the diamond fits, and keeps the tools.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
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
     * The restarts a goal has spent: the last step ended empty just now, so the next one carries them, as it does in a world where the clock is far past
     * {@link dev.aicompanion.entity.CompanionMineSite#RESTARTS_CARRIED}. Restarts made on a record that never ended a step are given back only while the clock is still young.
     */
    private static void spendRestarts(GameTestHelper h, CompanionEntity c, BlockPos at, int count) {
        c.mineSite().newStep(h.getLevel().getGameTime());
        for (int i = 0; i < count; i++) c.mineSite().restart(at, Direction.NORTH);
    }

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
        BlockPos diamond = foot.relative(Direction.WEST, DIAMOND_AT);
        h.getLevel().setBlock(diamond, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), 2 | 16);
        for (BlockPos open : List.of(diamond.west(), diamond.west().above(), diamond.west(2), diamond.west(2).above())) h.getLevel().setBlock(open, Blocks.AIR.defaultBlockState(), 2 | 16);
        BlockPos end = foot.relative(Direction.WEST, TUNNEL_START);
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

    /** A pocket at the top of the deepslate closed all round, feet and head, by plugs he sealed and forbade, every side refused; he stands in it. */
    private static BlockPos pluggedPocket(GameTestHelper h, CompanionEntity c) {
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

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
    private static final int GOAL_TIMEOUT = 24000, DROP_AT = 8, DROP_Y = 5, STARTED_BY = 1200, CAGE_AT = 8, CAGE_PAD = 7, CAGE_NEAR = 5, SURFACE_MARGIN = 2;
    private static final int TUNNEL_START = 2, BESIDE_AT = 18, OVERHEAD_AT = 14, SPARE_PICKAXES = 6;
    /** Wear left of the iron pickaxe (the reserve kept for ore is just above it), the stands the record still knows, the stand he is in, and the one his plug closes. */
    private static final int IRON_LEFT = 60, KNOWN_STANDS = 3, GAP_AT = 12, PLUG_AT = 8, DIAMOND_AT = 6;

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
        pitWithoutPickaxe(h, new int[][]{{CENTER - 6, CENTER + 6}, {CENTER - 4, CENTER + 8}, {CENTER - 8, CENTER + 4}}, 1, false);
    }

    /**
     * The same, but the only trees are a long walk from the entrance, beyond the search range of sixteen blocks (run f10e, seed 7741901: no
     * wood in range, the goal stopped five times with «копать нечем»). A goal's wood for a pickaxe is looked for farther; he walks there,
     * chops a real trunk, makes the pickaxe and the goal goes on to the diamond. No sticks in the bag either.
     */
    @GameTest(template = "marathon_cave", batch = "mine_stops_pit", timeoutTicks = POCKET_TIMEOUT)
    public static void walksFarForWoodWhenNoTreeIsNearAndThereIsNoPickaxe(GameTestHelper h) {
        pitWithoutPickaxe(h, new int[][]{{FAR_TREE_AT, FAR_TREE_AT}, {FAR_TREE_AT + 2, FAR_TREE_AT}, {FAR_TREE_AT, FAR_TREE_AT + 2}}, 0, true);
    }

    private static void pitWithoutPickaxe(GameTestHelper h, int[][] trunks, int sticks, boolean chopped) {
        mountain(h, true);
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
                CompanionDiamondGoalTests.start(h, r, 1);
                started[0] = true;
            }
            h.assertTrue(CompanionDiamondGoalTests.state(h, r) == CompanionAgenda.State.COMPLETED && count(c, Items.DIAMOND) >= 1,
                    "No diamond from the pit: " + CompanionDiamondGoalTests.report(h, r));
            if (chopped) h.assertTrue(cut.stream().anyMatch(log -> !h.getLevel().getBlockState(log).is(Blocks.OAK_LOG)),
                    "No trunk of the far trees was chopped: " + CompanionDiamondGoalTests.report(h, r));
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
        for (BlockPos cell : List.of(stairs.get(PLUG_AT), stairs.get(PLUG_AT).above())) {
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
            h.assertTrue(!h.getLevel().getBlockState(stairs.get(PLUG_AT)).is(Blocks.COBBLESTONE), "His plug on the stairs is still there | " + report(c));
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

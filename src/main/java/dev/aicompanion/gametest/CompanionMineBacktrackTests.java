package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A dead end on the staircase is not the end of the mine: he backs up his own stairs, plugs the way behind him and branches
 * off sideways, and the record of caves and counters that makes the choice survives a save. Arena as in the other mine tests:
 * origin at Y -60, Oleg picks NORTH at (24,22,24), stand k is (24,22-k,24-k), diamond ore under the tunnel floor far from the middle.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineBacktrackTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2, RING_FROM = 12, TIMEOUT = 12000, DEAD_END = 12;
    private static final int CHAMBER_X0 = 22, CHAMBER_X1 = 26, CHAMBER_Z0 = 14, CHAMBER_Z1 = 18, CHAMBER_Y = 12, WEST_LODE = 12, RECOVERED = 18, FOOT_Z = 4, PIT = 11, RETARGET_EVERY = 20, SPARE_PICKAXES = 6, LONG_TUNNEL = 18, NEAR_END_TICKS = 40, STEP_AWAY_TICKS = 200, CREEPER_STAND = 14, START_STAND = 10, LANDING_AT = 6, WEST_RUN = 6, PRIOR_BACKTRACKS = 3, FLOOR_GONE_AFTER = 10;


    private CompanionMineBacktrackTests() { }

    /** Bedrock closes the stairs ahead of stand 12 and both sides of it: he goes back to stand 11, plugs stand 12 and turns off there. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void backsUpAndBranchesFromADeadEnd(GameTestHelper h) {
        mountain(h);
        BlockPos dead = under(DEAD_END).above(), before = under(DEAD_END - 1).above();
        for (int y = -1; y <= 1; y++) h.setBlock(dead.north().above(y), Blocks.BEDROCK);
        for (int y = 0; y <= 2; y++) {
            h.setBlock(dead.west().above(y), Blocks.BEDROCK);
            h.setBlock(dead.east().above(y), Blocks.BEDROCK);
        }
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The dead end stopped the mine: " + report(c));
            h.assertTrue(!h.getBlockState(dead.above()).isAir(), "No plug behind him at " + dead.above().toShortString() + " | " + report(c));
            h.assertTrue(c.mineSite().stands().contains(h.absolutePos(before.west())) || c.mineSite().stands().contains(h.absolutePos(before.east())), "No branch off stand 11: " + c.mineSite().stands().size() + " stands | " + report(c));
            h.assertTrue(c.mineSite().backtracks() == 1, "Backtracks " + c.mineSite().backtracks());
        });
    }

    /** Four zombies in a chamber the stairs break into: after the retreat the next order does not dig back into that cave but turns off above it and goes round it to the ore in the west wall. */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void doesNotReturnToACrowdedCaveAfterARetreat(GameTestHelper h) {
        mountain(h, false);
        for (int z = 1; z < SIZE - 1; z++) for (int x = 1; x <= WEST_LODE; x++) h.setBlock(new BlockPos(x, DIAMOND_FEET - 1, z), Blocks.DEEPSLATE_DIAMOND_ORE);
        for (int x = CHAMBER_X0; x <= CHAMBER_X1; x++) for (int z = CHAMBER_Z0; z <= CHAMBER_Z1; z++) for (int y = CHAMBER_Y; y <= CHAMBER_Y + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        CompanionEntity c = rig(h);
        List<Zombie> zombies = new ArrayList<>();
        for (int[] at : new int[][]{{23, 15}, {24, 16}, {25, 17}, {23, 17}}) {
            Zombie zombie = h.spawn(EntityType.ZOMBIE, at[0], CHAMBER_Y, at[1]);
            zombie.setPersistenceRequired();
            // The sky light of a freshly built mountain lags behind it: by day a mob in the chamber can burn before the cave opens.
            zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
            zombies.add(zombie);
        }
        long first = start(h, c, 1);
        long[] second = {-1};
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null && h.getTick() % RETARGET_EVERY == 0) zombie.setTarget(c);
            if (c.isWorking() || c.isGatheringTask()) {
                BlockPos at = rel(h, c.blockPosition());
                boolean inCave = at.getX() >= CHAMBER_X0 && at.getX() <= CHAMBER_X1 && at.getZ() >= CHAMBER_Z0 && at.getZ() <= CHAMBER_Z1 && at.getY() >= CHAMBER_Y && at.getY() <= CHAMBER_Y + 2;

                h.assertTrue(second[0] < 0 || !inCave, "Back in the cave at " + at.toShortString() + " | " + report(c));
                return;
            }
            if (second[0] < 0) {
                CompanionActionResult result = c.workResult(first);
                if (result == null) return;
                h.assertTrue(result.message().contains("отступил") && c.getHealth() >= RECOVERED, "The first order did not end in a retreat: " + report(c));
                second[0] = start(h, c, 1);
                return;
            }
            CompanionActionResult result = c.workResult(second[0]);
            if (result == null) return;
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The second order found nothing, retreats " + c.mineSite().retreats() + ": " + report(c));
            h.assertTrue(c.mineSite().retreats() == 1, "Retreats " + c.mineSite().retreats() + " | " + report(c));
            for (BlockPos stand : c.mineSite().stands()) h.assertTrue(!c.mineSite().shunned(stand), "A stand in the cave box at " + rel(h, stand).toShortString());
            h.succeed();
        });
    }

    /** Bedrock at the arena's edge ends the tunnels north, west and east of the stairs' foot: the base moves to the end of the long west one and the ore south of it is found. */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void movesTheBaseWhenAllTunnelsAreDone(GameTestHelper h) {
        mountain(h, false);
        for (int x = 2; x <= 4; x++) for (int z = 26; z <= 28; z++) h.setBlock(new BlockPos(x, DIAMOND_FEET - 1, z), Blocks.DEEPSLATE_DIAMOND_ORE);
        CompanionEntity c = rig(h);
        // Three long tunnels in deepslate wear out more stone pickaxes than the usual bag holds.
        for (int slot = 6; slot < 6 + SPARE_PICKAXES; slot++) c.setItem(slot, new ItemStack(Items.STONE_PICKAXE));
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The mine ended without the ore: " + report(c));
            h.assertTrue(c.mineSite().stands().size() > GROUND - DIAMOND_FEET + 10, "The base did not move: " + c.mineSite().stands().size() + " stands | " + report(c));
        });
    }

    /** He stands in a closed pit well below the entrance, his stairs are not there: the mine starts again from the pit and still gets to the ore. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainWhereHeStandsWhenTheStairsAreLost(GameTestHelper h) {
        mountain(h, true);
        for (int x = PIT - 1; x <= PIT + 1; x++) for (int z = PIT - 1; z <= PIT + 1; z++) for (int y = DEEPSLATE_TOP; y <= DEEPSLATE_TOP + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        CompanionEntity c = rig(h, PIT, DEEPSLATE_TOP, PIT);
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), Direction.NORTH);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not start again: " + report(c));
            h.assertTrue(c.mineSite().restarts() == 1, "Restarts " + c.mineSite().restarts());
        });
    }

    /** The pit is a cave he fled from, every cell around him is remembered as shut: the mine still starts again from where he stands, and forgets that cave. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void restartsBesideACaveHeFled(GameTestHelper h) {
        mountain(h, true);
        List<BlockPos> pit = new ArrayList<>();
        for (int x = PIT - 1; x <= PIT + 1; x++) for (int z = PIT - 1; z <= PIT + 1; z++) for (int y = DEEPSLATE_TOP; y <= DEEPSLATE_TOP + 2; y++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            pit.add(h.absolutePos(new BlockPos(x, y, z)));
        }
        CompanionEntity c = rig(h, PIT, DEEPSLATE_TOP, PIT);
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), Direction.NORTH);
        c.mineSite().shun(pit);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The caves shut every side of the restart: " + report(c));
            h.assertTrue(c.mineSite().restarts() == 1, "Restarts " + c.mineSite().restarts());
        });
    }

    /**
     * A flat plate of open ground at the foot of the stairs, its floor and walls bedrock: every step down is shut and the landings
     * would lead him from the stairs' foot west, north, east and back onto it. A turn onto a stand of his own stairs is refused,
     * so he backs up and branches higher, and no stand is ever recorded twice.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void doesNotCircleOnItsOwnStairs(GameTestHelper h) {
        mountain(h);
        BlockPos dead = under(DEAD_END).above();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 1; dz++) {
            if (dx == 0 && dz == 1) continue;
            boolean plate = Math.abs(dx) <= 1 && dz <= 0 && dz >= -1;
            for (int y = -1; y <= 3; y++) h.setBlock(dead.offset(dx, y, dz), plate && y >= 0 && y <= 2 ? Blocks.AIR : Blocks.BEDROCK);
        }
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.onEachTick(() -> {
            List<BlockPos> stands = c.mineSite().stands();
            h.assertTrue(stands.stream().distinct().count() == stands.size(), "A stand is recorded twice: " + stands.size() + " stands | " + report(c));
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = c.workResult(id);
            h.assertTrue(result != null, "No result for the order: " + report(c));
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The plate stopped the mine: " + report(c));
            h.assertTrue(c.mineSite().backtracks() >= 1, "He did not back up: " + c.mineSite().backtracks());
            h.succeed();
        });
    }

    /** A big cave that he fled from lies on both sides of the stairs, its box would hold them: only its own cells are shut, the stairs and the next step down are not. */
    @GameTest(template = "marathon_cave")
    public static void aBigCaveShutsOnlyWhereItIsNotTheWholeStairs(GameTestHelper h) {
        mountain(h);
        var level = h.getLevel();
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stairs = CompanionShaftPlan.stands(entrance, Direction.NORTH, 10);
        for (BlockPos stand : stairs) for (BlockPos cell : CompanionShaftPlan.column(stand)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionMineSite site = new CompanionMineSite();
        site.start(entrance, Direction.NORTH);
        for (BlockPos stand : stairs.subList(1, stairs.size())) site.reached(stand);
        BlockPos foot = site.foot();
        List<BlockPos> cave = new ArrayList<>();
        for (int side : new int[]{-9, 9}) for (int dx = 0; dx < 3; dx++) for (int dy = -2; dy < 3; dy++) for (int dz = 0; dz < 3; dz++) cave.add(foot.offset(side + dx, dy, dz));
        site.shun(cave);
        h.assertTrue(site.firstShunned() == -1, "A stand of the stairs is shut by a cave beside them: " + site.firstShunned());
        h.assertTrue(site.shunned(foot.offset(9, 0, 1)) && site.shunned(foot.offset(-8, 0, 1)), "The cave itself is not shut");
        h.assertTrue(CompanionShaftPlan.assessStep(level, foot, Direction.NORTH, site.seals(), site.avoid()).kind() != CompanionShaftPlan.Kind.BLOCKED, "The next step down is shut by a cave beside the stairs");
        h.succeed();
    }

    /** The order starts at the far end of a tunnel of eighteen cells, the ore lies in the rock beyond it: he digs on from where he stands instead of walking to its first cell and back. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void reentersALongTunnelFromItsFarEnd(GameTestHelper h) {
        mountain(h, false);
        h.setBlock(new BlockPos(3, DIAMOND_FEET, FOOT_Z), Blocks.DEEPSLATE_DIAMOND_ORE);
        CompanionEntity c = rig(h);
        carveMine(h, c, LONG_TUNNEL);
        BlockPos end = c.mineSite().foot().relative(Direction.WEST, LONG_TUNNEL);
        c.teleportTo(end.getX() + .5, end.getY(), end.getZ() + .5);
        long id = start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            if (h.getTick() - begun == NEAR_END_TICKS) h.assertTrue(c.blockPosition().distManhattan(end) <= 3, "Walked away from the far end to " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = c.workResult(id);
            h.assertTrue(result != null && result.success() && count(c, Items.DIAMOND) >= 1, "The far end of the tunnel was not mined: " + report(c));
            h.succeed();
        });
    }

    /** The stone pickaxes are gone and the iron one has wear to spare: he digs the rock with it instead of going up for supplies. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void digsStoneWithTheIronPickaxeWhenItIsTheLastOne(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        c.setItem(0, ItemStack.EMPTY);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The iron pickaxe was not used for the rock: " + report(c));
            h.assertTrue(count(c, Items.IRON_PICKAXE) == 1, "The iron pickaxe was worn out: " + report(c));
        });
    }

    /** A creeper that does not move waits four stairs below him: he goes up the stairs, away from it, and when it is gone he digs on. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void stepsAwayFromACreeperOnTheStairs(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos from = stairs.get(START_STAND), at = stairs.get(CREEPER_STAND);
        c.teleportTo(from.getX() + .5, from.getY(), from.getZ() + .5);
        Creeper creeper = h.spawn(EntityType.CREEPER, rel(h, at));
        creeper.setNoAi(true);
        creeper.setPersistenceRequired();
        long id = start(h, c, 1);
        long begun = h.getTick();
        boolean[] away = {false};
        h.onEachTick(() -> {
            if (!away[0]) {
                away[0] = c.getY() >= from.getY() + 4;
                h.assertTrue(away[0] || h.getTick() - begun < STEP_AWAY_TICKS, "He did not go up away from the creeper: at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
                if (away[0]) creeper.discard();
                return;
            }
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = c.workResult(id);
            h.assertTrue(result != null && result.success() && count(c, Items.DIAMOND) >= 1, "He did not dig on after the creeper: " + report(c));
            h.succeed();
        });
    }

    /**
     * Stairs north with a landing west and a second run down west, bedrock closing the end of it. Once he is on his way up the floor
     * under the landing is gone: the cell hangs in the air and cannot be entered, so the walk leaves it out and he goes on up to the stand to turn off at.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void walksUpPastAStandWithoutAFloor(GameTestHelper h) {
        mountain(h);
        var level = h.getLevel();
        CompanionEntity c = rig(h);
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stands = new ArrayList<>(CompanionShaftPlan.stands(entrance, Direction.NORTH, LANDING_AT));
        BlockPos landing = stands.get(LANDING_AT).relative(Direction.WEST);
        stands.add(landing);
        for (int step = 1; step <= WEST_RUN; step++) stands.add(landing.relative(Direction.WEST, step).below(step));
        for (BlockPos stand : stands) for (BlockPos cell : CompanionShaftPlan.column(stand)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        BlockPos dead = stands.get(stands.size() - 1);
        for (int y = -1; y <= 1; y++) level.setBlock(dead.west().above(y), Blocks.BEDROCK.defaultBlockState(), 2 | 16);
        for (int y = 0; y <= 2; y++) {
            level.setBlock(dead.north().above(y), Blocks.BEDROCK.defaultBlockState(), 2 | 16);
            level.setBlock(dead.south().above(y), Blocks.BEDROCK.defaultBlockState(), 2 | 16);
        }
        c.mineSite().start(entrance, Direction.NORTH);
        for (BlockPos stand : stands.subList(1, stands.size())) c.mineSite().reached(stand);
        // Past a few backtracks the next one goes back eight stands: above the landing.
        for (int i = 0; i < PRIOR_BACKTRACKS; i++) c.mineSite().backtracked();
        c.teleportTo(dead.getX() + .5, dead.getY(), dead.getZ() + .5);
        long id = start(h, c, 1);
        // After the first tick, which checks the record against the world, and before he is up at the landing.
        h.runAfterDelay(FLOOR_GONE_AFTER, () -> {
            h.assertTrue(c.mineSite().stands().size() == stands.size() - 1 && c.getY() < landing.getY(), "The setup is off: " + c.mineSite().stands().size() + " stands, he is at " + c.blockPosition().toShortString());
            level.setBlock(landing.below(), Blocks.AIR.defaultBlockState(), 2 | 16);
        });
        h.onEachTick(() -> {
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = c.workResult(id);
            h.assertTrue(result != null && result.success() && count(c, Items.DIAMOND) >= 1, "He did not get past the landing without a floor: " + report(c));
            h.assertTrue(c.mineSite().restarts() == 0 && c.mineSite().backtracks() == PRIOR_BACKTRACKS + 1, "Restarts " + c.mineSite().restarts() + ", backtracks " + c.mineSite().backtracks() + " | " + report(c));
            h.succeed();
        });
    }

    /** Digs out the stairs to the diamond depth and a tunnel of {@code tunnel} cells west of the foot and puts them in the record; returns the stands, the entrance first. */
    private static List<BlockPos> carveMine(GameTestHelper h, CompanionEntity c, int tunnel) {
        var level = h.getLevel();
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stairs = CompanionShaftPlan.stands(entrance, Direction.NORTH, GROUND + 1 - DIAMOND_FEET);
        for (BlockPos stand : stairs) for (BlockPos cell : CompanionShaftPlan.column(stand)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionMineSite site = c.mineSite();
        site.start(entrance, Direction.NORTH);
        for (BlockPos stand : stairs.subList(1, stairs.size())) site.reached(stand);
        if (tunnel > 0) site.openTunnel(Direction.WEST);
        for (int k = 1; k <= tunnel; k++) {
            BlockPos cell = site.foot().relative(Direction.WEST, k);
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
            level.setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
            site.extendTunnel();
        }
        return stairs;
    }

    /** Caves, counters and a restart survive a save; a save without them loads as before; a flat stand of a tunnel base is two cells and stays. */
    @GameTest(template = "marathon_cave")
    public static void mineSiteKeepsCavesAndCountersOverASaveAndAFlatStand(GameTestHelper h) {
        mountain(h);
        var level = h.getLevel();
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stairs = CompanionShaftPlan.stands(entrance, Direction.NORTH, 10);
        for (BlockPos stand : stairs) for (BlockPos cell : CompanionShaftPlan.column(stand)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionMineSite site = new CompanionMineSite();
        site.start(entrance, Direction.NORTH);
        for (BlockPos stand : stairs.subList(1, stairs.size())) site.reached(stand);
        BlockPos inside = stairs.get(4).offset(1, 0, 0), outside = stairs.get(4).offset(9, 0, 0);
        site.shun(List.of(stairs.get(4).offset(-1, 0, 0), stairs.get(4).offset(1, 0, 0)));
        site.backtracked();
        site.backtracked();
        site.retreated();
        h.assertTrue(site.shunned(inside) && !site.shunned(outside) && site.shunned(stairs.get(4)) && site.firstShunned() >= 0 && site.firstShunned() <= 3, "Shun cells: first shunned stand " + site.firstShunned());
        h.assertTrue(site.avoid().shunned().test(inside) && !site.avoid().shunned().test(outside), "avoid() does not show the caves");
        CompoundTag tag = new CompoundTag();
        site.save(tag);
        CompanionMineSite loaded = loadedFrom(tag);
        h.assertTrue(loaded.shunned(inside) && !loaded.shunned(outside) && loaded.backtracks() == 2 && loaded.retreats() == 1 && loaded.restarts() == 0,
                "The caves or counters were lost over the save: " + loaded.backtracks() + "/" + loaded.retreats());
        // Eight blocks apart and inside one cell each: 2100 cells are more than the cap, so the oldest 52 and the earlier cave drop.
        BlockPos far = new BlockPos(1, 5, 1);
        for (int i = 0; i < 2100; i++) loaded.shun(List.of(far.offset(i * 8, 0, 0)));
        h.assertTrue(!loaded.shunned(inside) && !loaded.shunned(far) && loaded.shunned(far.offset(2099 * 8, 0, 0)) && loaded.shunned(far.offset(60 * 8, 0, 0)), "The oldest cells are not the ones dropped past the cap");

        CompanionMineSite fresh = new CompanionMineSite();
        fresh.start(entrance, Direction.NORTH);
        CompoundTag plain = new CompoundTag();
        fresh.save(plain);
        CompoundTag record = plain.getCompound(CompanionMineSite.KEY);
        h.assertTrue(!record.contains("Caves") && !record.contains("Backtracks") && !record.contains("Retreats") && !record.contains("Restarts"), "An empty memory is saved");
        CompanionMineSite fromOld = loadedFrom(plain);
        h.assertTrue(fromOld.exists() && !fromOld.shunned(inside) && fromOld.firstShunned() == -1 && fromOld.backtracks() == 0 && fromOld.restarts() == 0, "A save without the keys loads wrong");

        BlockPos here = entrance.offset(0, -8, 3), distant = inside.offset(40, 0, 0);
        site.shun(List.of(distant));
        site.restart(here, Direction.WEST);
        h.assertTrue(site.entrance().equals(here) && site.stands().isEmpty() && !site.shunned(inside) && site.shunned(distant) && site.restarts() == 1 && site.backtracks() == 0 && site.stairDirection() == Direction.WEST,
                "restart() did not start over at the spot forgetting the caves beside it only: restarts " + site.restarts());
        site.clear();
        h.assertTrue(!site.shunned(distant) && site.restarts() == 0, "clear() keeps the memory");

        CompanionMineSite flat = new CompanionMineSite();
        flat.start(entrance, Direction.NORTH);
        for (BlockPos stand : stairs.subList(1, stairs.size())) flat.reached(stand);
        flat.openTunnel(Direction.WEST);
        BlockPos foot = flat.foot();
        for (int k = 1; k <= 3; k++) {
            level.setBlock(foot.relative(Direction.WEST, k), Blocks.AIR.defaultBlockState(), 2 | 16);
            level.setBlock(foot.relative(Direction.WEST, k).above(), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        h.assertTrue(flat.rebase(3, Direction.WEST) && flat.stands().size() == 13 && flat.tunnels().isEmpty() && flat.foot().equals(foot.relative(Direction.WEST, 3)), "rebase() put the base at " + flat.foot());
        h.assertTrue(!flat.rebase(600, Direction.WEST) && flat.stands().size() == 13, "rebase() went past the cap");
        h.assertTrue(flat.verify(level) == 13, "A flat stand of two open cells was cut: " + flat.stands().size());
        level.setBlock(foot.relative(Direction.WEST, 2).above(), Blocks.STONE.defaultBlockState(), 2 | 16);
        h.assertTrue(flat.verify(level) == 11, "A flat stand with a closed head was kept: " + flat.stands().size());
        h.succeed();
    }

    private static CompanionMineSite loadedFrom(CompoundTag tag) {
        CompanionMineSite site = new CompanionMineSite();
        site.load(tag);
        return site;
    }

    /** The floor cell under stand {@code step}. */
    private static BlockPos under(int step) { return new BlockPos(CENTER, GROUND - step, CENTER - step); }

    /** Bedrock floor and walls, deepslate up to Y -52, stone, dirt, grass; deepslate diamond ore in a ring under the tunnel floor. */
    private static void mountain(GameTestHelper h) { mountain(h, true); }

    private static void mountain(GameTestHelper h, boolean ring) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            Block block = y == 0 || edge ? Blocks.BEDROCK
                    : y == DIAMOND_FEET - 1 && ring && Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER)) >= RING_FROM ? Blocks.DEEPSLATE_DIAMOND_ORE
                    : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    private static CompanionEntity rig(GameTestHelper h) { return rig(h, CENTER, GROUND + 1, CENTER); }

    private static CompanionEntity rig(GameTestHelper h, int x, int y, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 16),
                new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64)};
        for (int slot = 0; slot < bag.length; slot++) companion.setItem(slot, bag[slot]);
        return companion;
    }

    private static long start(GameTestHelper h, CompanionEntity c, int count) {
        h.assertTrue(c.startWork("dig_for_diamonds", "", count).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
        return c.activeWorkId();
    }

    private static CompanionActionResult ended(GameTestHelper h, CompanionEntity c, long id) {
        h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "Work still active: " + c.getLastTaskResult());
        CompanionActionResult result = c.workResult(id);
        h.assertTrue(result != null, "No result for the order: " + report(c));
        return result;
    }

    /** Position relative to the arena corner; GameTestHelper.relativePos mirrors X and Z in a rotated arena, so the frame is read from absolutePos. */
    private static BlockPos rel(GameTestHelper h, BlockPos world) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO), dx = h.absolutePos(new BlockPos(1, 0, 0)).subtract(origin), dz = h.absolutePos(new BlockPos(0, 0, 1)).subtract(origin);
        BlockPos d = world.subtract(origin);
        return new BlockPos(d.getX() * dx.getX() + d.getZ() * dx.getZ(), d.getY(), d.getX() * dz.getX() + d.getZ() * dz.getZ());
    }

    private static String report(CompanionEntity companion) { return companion.getDeedsForAi() + " | " + companion.getLastTaskResult() + " | bag: " + companion.getStoredItemsSummary(); }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMarathonFixTests.player;
import static dev.aicompanion.gametest.CompanionMarathonFixTests.spawn;

/**
 * Swimming after the player like a player: a long way through deep water at a sprint, lying flat, and out onto a bank a block
 * over the water in one go. Seen in the owner's game: the player swimming with Ctrl left him behind (about 2 blocks a second
 * against 4), and at the bank he bobbed — here three seconds and four lifts before he got out.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSwimTests {
    private CompanionSwimTests() { }

    /**
     * A deep pool (water y 1..4) with a stone bank from x 24, its top at y 6 — a block over the water, as a river bank often is.
     * Walls across the water at the given z keep lanes apart.
     */
    static void pool(GameTestHelper h, int... laneWalls) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 8; y++) {
            int across = z;
            boolean wall = x < 2 || x > 30 || z < 2 || z > 29 || x < 24 && java.util.Arrays.stream(laneWalls).anyMatch(lane -> lane == across);
            var block = y == 0 || wall && y <= 7 ? Blocks.BEDROCK : x >= 24 ? (y < 6 ? Blocks.STONE : Blocks.AIR) : y <= 4 ? Blocks.WATER : Blocks.AIR;
            h.setBlock(new BlockPos(x, y, z), block);
        }
    }

    /** Oleg in the water following the player on the bank, watched every tick. */
    private static final class Crossing {
        final GameTestHelper h;
        final CompanionEntity oleg;
        long contact = -1, out = -1, fastFrom = -1, fastTo = -1;
        double fastFromAt, fastToAt;
        int inWater, lying, sprintChanges, outOfWaterMidway;
        boolean sprinting, wasInWater;
        final StringBuilder changes = new StringBuilder();

        Crossing(GameTestHelper h, Vec3 from, Vec3 to) {
            this.h = h;
            oleg = spawn(h, (int) from.x, (int) from.y, (int) from.z);
            var at = h.absoluteVec(to);
            player(h, oleg).teleportTo(at.x, at.y, at.z);
            oleg.setFollowing(true);
            double base = h.absolutePos(BlockPos.ZERO).getY();
            h.onEachTick(() -> {
                long t = h.getTick();
                Vec3 rel = oleg.position().subtract(h.absoluteVec(Vec3.ZERO));
                double swum = Math.hypot(rel.x - from.x, rel.z - from.z);
                if (oleg.isInWater()) { inWater++; if (oleg.getPose() == Pose.SWIMMING) lying++; }
                // Out of the water and back in on the way, once settled after the start: lying on the water instead of in it. Spawned
                // under water, he bobs up and over it the first two times or so (6 of 100 swims within 4 blocks, at any sinking).
                if (wasInWater && !oleg.isInWater() && rel.x < 23 && swum >= 6) outOfWaterMidway++;
                wasInWater = oleg.isInWater();
                if (oleg.isSprinting() != sprinting) {
                    sprinting = oleg.isSprinting(); sprintChanges++;
                    changes.append(String.format(" %s at t=%d x=%.1f y=%.2f;", sprinting ? "on" : "off", t, rel.x, rel.y));
                }
                if (fastFrom < 0 && swum >= 6) { fastFrom = t; fastFromAt = swum; }
                if (fastTo < 0 && swum >= 16) { fastTo = t; fastToAt = swum; }
                // Pressed against the bank (its face at x 24, he is 0.6 wide).
                if (contact < 0 && rel.x >= 23.65) contact = t;
                if (out < 0 && oleg.onGround() && oleg.getY() - base >= 6 - .01 && rel.x >= 24) out = t;
            });
        }

        /** Blocks a second over the middle of the way, from 6 to 16 blocks off the start. */
        double speed() { return fastTo > fastFrom && fastFrom >= 0 ? (fastToAt - fastFromAt) / ((fastTo - fastFrom) / 20.0) : 0; }
        long climb() { return out - contact; }
        String where() { return oleg.position().subtract(h.absoluteVec(Vec3.ZERO)).toString(); }
    }

    /** Twenty blocks of deep water after the player: at a player's sprint, lying flat, upright again on the bank. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aLongSwimIsAPlayersSprintLyingFlat(GameTestHelper h) {
        pool(h);
        var run = new Crossing(h, new Vec3(4.5, 4, 16.5), new Vec3(29.5, 6, 16.5));
        h.succeedWhen(() -> {
            h.assertTrue(run.out >= 0, "Not out on the bank: " + run.where());
            h.assertTrue(h.getTick() >= run.out + 5, "Just out");
            h.assertTrue(run.speed() >= 3.2, "Swam " + String.format("%.2f", run.speed()) + " blocks a second; a player with Ctrl, 3.9");
            h.assertTrue(run.lying * 2 >= run.inWater, "Lay flat only " + run.lying + " of " + run.inWater + " ticks in the water");
            h.assertTrue(run.sprintChanges <= 2, "The fast stroke went on and off " + run.sprintChanges + " times:" + run.changes);
            h.assertTrue(run.outOfWaterMidway == 0, "Bobbed out of the water " + run.outOfWaterMidway + " times on the way");
            h.assertTrue(!run.oleg.isSprinting() && run.oleg.getPose() == Pose.STANDING, "Still swimming on the bank: " + run.oleg.getPose());
            h.assertTrue(run.climb() <= 30, "From the bank to standing on it " + run.climb() + " ticks");
        });
    }

    /**
     * Straight at a bank a block over the water: one push out, as a player holding the jump. Whether the old push came in time
     * hung on where in his bob he met the bank (the float jumps at random), so four of him in four lanes, from four distances.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void straightOutOntoABankABlockHigh(GameTestHelper h) {
        pool(h, 7, 13, 19, 25);
        outInOneGo(h, java.util.stream.IntStream.range(0, 4)
                .mapToObj(lane -> new Crossing(h, new Vec3(12.5 + lane, 4, 4.5 + 6 * lane), new Vec3(29.5, 6, 4.5 + 6 * lane))).toList());
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void slantwiseOutOntoABankABlockHigh(GameTestHelper h) {
        pool(h);
        outInOneGo(h, java.util.List.of(new Crossing(h, new Vec3(8.5, 4, 6.5), new Vec3(29.5, 6, 26.5))));
    }

    /** Swimming along the bank to the player further up it. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void alongTheBankAndOutOntoIt(GameTestHelper h) {
        pool(h);
        outInOneGo(h, java.util.List.of(new Crossing(h, new Vec3(21.5, 4, 4.5), new Vec3(27.5, 6, 27.5))));
    }

    private static void outInOneGo(GameTestHelper h, java.util.List<Crossing> runs) {
        h.succeedWhen(() -> {
            for (Crossing run : runs) h.assertTrue(run.out >= 0, "Not out on the bank: " + run.where());
            String climbs = runs.stream().map(run -> String.valueOf(run.climb())).collect(java.util.stream.Collectors.joining(", "));
            for (Crossing run : runs) h.assertTrue(run.climb() <= 30, "From the bank to standing on it, ticks: " + climbs);
        });
    }

    /**
     * Across a lake, the player a hundred blocks off: farther than 32 he is followed leg by leg (CompanionTravel), and every
     * leg's end is not the end of the way — he keeps the fast stroke the whole way over.
     */
    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void farAcrossALakeHeKeepsTheFastStroke(GameTestHelper h) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) for (int y = 0; y <= 4; y++) {
            boolean edge = x == 0 || x == 127 || z == 0 || z == 127;
            h.setBlock(new BlockPos(x, y, z), y == 0 || edge || x >= 104 ? Blocks.BEDROCK : Blocks.WATER);
        }
        CompanionEntity oleg = spawn(h, 6, 4, 64);
        var at = h.absoluteVec(new Vec3(112.5, 5, 64.5));
        player(h, oleg).teleportTo(at.x, at.y, at.z);
        oleg.setFollowing(true);
        int[] inWater = {0}, lying = {0}, changes = {0};
        boolean[] sprinting = {false};
        long[] from = {-1}, to = {-1};
        h.onEachTick(() -> {
            double x = oleg.getX() - h.absoluteVec(Vec3.ZERO).x;
            if (x >= 99) return;
            if (oleg.isInWater() && x >= 10) { inWater[0]++; if (oleg.getPose() == Pose.SWIMMING) lying[0]++; }
            if (oleg.isSprinting() != sprinting[0]) { sprinting[0] = oleg.isSprinting(); changes[0]++; }
            if (from[0] < 0 && x >= 20) from[0] = h.getTick();
            if (to[0] < 0 && x >= 90) to[0] = h.getTick();
        });
        h.succeedWhen(() -> {
            h.assertTrue(to[0] >= 0, "Not across yet: " + oleg.blockPosition().subtract(h.absolutePos(BlockPos.ZERO)).toShortString());
            double speed = 70 / ((to[0] - from[0]) / 20.0);
            h.assertTrue(speed >= 3.2, "Crossed at " + String.format("%.2f", speed) + " blocks a second");
            h.assertTrue(lying[0] * 10 >= inWater[0] * 9, "Lay flat only " + lying[0] + " of " + inWater[0] + " ticks in the water");
            h.assertTrue(changes[0] <= 1, "The fast stroke went on and off " + changes[0] + " times on the way");
        });
    }

    /**
     * Issue 58 (owner's stream 01.11): the player swimming on across a lake, Oleg after him stood up in the water and lay down
     * again every second or so. Now each stroke is kept for a while: after a sprinting player the fast one all the way (it ended
     * at the end of every route to where the player had been), after a slow one the ordinary one, the fast only to catch up
     * (it caught him up in seconds and stopped at his heels).
     */
    @GameTest(template = "large_search", timeoutTicks = 1200)
    public static void afterAPlayerSwimmingAtASprintHeStaysFlat(GameTestHelper h) { afterASwimmingPlayer(h, 3.9, true); }

    @GameTest(template = "large_search", timeoutTicks = 1200)
    public static void afterAPlayerSwimmingSlowlyHeSwimsAtHisPace(GameTestHelper h) { afterASwimmingPlayer(h, 2.0, false); }

    private static void afterASwimmingPlayer(GameTestHelper h, double speed, boolean sprint) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) for (int y = 0; y <= 4; y++) {
            boolean edge = x == 0 || x == 127 || z == 0 || z == 127;
            h.setBlock(new BlockPos(x, y, z), y == 0 || edge ? Blocks.BEDROCK : Blocks.WATER);
        }
        CompanionEntity oleg = spawn(h, 6, 3, 64);
        var owner = player(h, oleg);
        owner.setSprinting(sprint);
        if (sprint) owner.setPose(Pose.SWIMMING);
        oleg.setFollowing(true);
        long[] since = {-1}, shortest = {Long.MAX_VALUE};
        int[] measured = {0}, lying = {0};
        double[] farthest = {0};
        Pose[] pose = {null};
        StringBuilder log = new StringBuilder();
        h.onEachTick(() -> {
            long t = h.getTick();
            var at = h.absoluteVec(new Vec3(Math.min(118, 14 + speed * t / 20.0), 3.6, 64.5));
            owner.teleportTo(at.x, at.y, at.z);
            double x = oleg.getX() - h.absoluteVec(Vec3.ZERO).x;
            // Out in the lake, till the player stops at the far end.
            if (x < 20 || at.x - h.absoluteVec(Vec3.ZERO).x >= 118 || !oleg.isInWater()) return;
            if (pose[0] != null && oleg.getPose() != pose[0]) {
                if (since[0] >= 0) shortest[0] = Math.min(shortest[0], t - since[0]);
                since[0] = t;
                log.append(String.format(" %s at t=%d x=%.1f gap=%.1f;", oleg.getPose(), t, x, oleg.distanceTo(owner)));
            }
            pose[0] = oleg.getPose();
            measured[0]++;
            if (oleg.getPose() == Pose.SWIMMING) lying[0]++;
            farthest[0] = Math.max(farthest[0], oleg.distanceTo(owner));
        });
        h.runAfterDelay(1100, () -> {
            h.assertTrue(pose[0] != null, "Never out in the lake: " + oleg.blockPosition().subtract(h.absolutePos(BlockPos.ZERO)).toShortString());
            h.assertTrue(shortest[0] >= 40, "Stood up and lay down again within " + shortest[0] + " ticks after the swimming player:" + log);
            // After the sprinting player flat all the way; after the slow one he still keeps up (he catches up from 12 blocks).
            if (sprint) h.assertTrue(lying[0] * 10 >= measured[0] * 9, "Lay flat only " + lying[0] + " of " + measured[0] + " ticks");
            else h.assertTrue(farthest[0] <= 14, "Fell " + String.format("%.1f", farthest[0]) + " blocks behind the slow swimmer");
            h.succeed();
        });
    }

    /**
     * The player on a jetty three blocks over the water, five blocks back from its edge: no way up. At its foot he treads water
     * upright — the short new routes the following makes there every moment are not a swim.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void atABankTooHighToClimbHeTreadsWaterUpright(GameTestHelper h) {
        pool(h);
        for (int x = 24; x <= 30; x++) for (int z = 2; z <= 29; z++) for (int y = 6; y <= 7; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        CompanionEntity oleg = spawn(h, 8, 4, 16);
        var at = h.absoluteVec(new Vec3(29.5, 8, 16.5));
        player(h, oleg).teleportTo(at.x, at.y, at.z);
        oleg.setFollowing(true);
        long[] arrived = {-1};
        int[] fast = {0};
        h.onEachTick(() -> {
            if (arrived[0] < 0 && oleg.getX() - h.absoluteVec(Vec3.ZERO).x >= 22.5) arrived[0] = h.getTick();
            if (arrived[0] >= 0 && h.getTick() > arrived[0] + 40 && (oleg.isSprinting() || oleg.getPose() == Pose.SWIMMING)) fast[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(arrived[0] >= 0 && h.getTick() >= arrived[0] + 200, "Not at the jetty long enough");
            h.assertTrue(fast[0] == 0, "Lay flat at the foot of the jetty for " + fast[0] + " of 160 ticks");
        });
    }

    /** «Стой» in the middle of a fast swim: he treads water upright at once, not gliding on flat. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void toldToStayMidSwimHeTreadsWaterUpright(GameTestHelper h) {
        pool(h);
        CompanionEntity oleg = spawn(h, 4, 4, 16);
        var owner = player(h, oleg);
        var at = h.absoluteVec(new Vec3(29.5, 6, 16.5));
        owner.teleportTo(at.x, at.y, at.z);
        oleg.setFollowing(true);
        int[] fast = {0};
        boolean[] wasFast = {false};
        h.runAfterDelay(60, () -> { wasFast[0] = oleg.isSprinting(); CompanionEvents.executeAction(owner, "wait", "", 1); });
        h.onEachTick(() -> { if (h.getTick() >= 63 && (oleg.isSprinting() || oleg.getPose() == Pose.SWIMMING)) fast[0]++; });
        h.runAfterDelay(200, () -> {
            h.assertTrue(wasFast[0], "Was not swimming fast when told to stay");
            h.assertTrue(fast[0] == 0, "Still flat for " + fast[0] + " ticks after «стой»");
            h.succeed();
        });
    }

    /** Water to the knees: wading upright, no sprint. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void inShallowWaterHeWadesUpright(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 6; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || (x < 2 || x > 29 || z < 2 || z > 29) && y <= 5 ? Blocks.BEDROCK : y <= 3 ? Blocks.STONE : y == 4 ? Blocks.WATER : Blocks.AIR);
        CompanionEntity oleg = spawn(h, 4, 4, 16);
        var at = h.absoluteVec(new Vec3(27.5, 4, 16.5));
        player(h, oleg).teleportTo(at.x, at.y, at.z);
        oleg.setFollowing(true);
        int[] fast = {0};
        h.onEachTick(() -> { if (oleg.isSprinting() || oleg.getPose() == Pose.SWIMMING) fast[0]++; });
        h.succeedWhen(() -> {
            h.assertTrue(oleg.distanceToSqr(player(h, oleg)) < 5 * 5, "Not by the player: " + oleg.blockPosition());
            h.assertTrue(fast[0] == 0, "Swam fast in knee-deep water for " + fast[0] + " ticks");
        });
    }

    /**
     * A column of falling water in a shaft at x 12, z 16 (y 1..14, in stone up to y 11), a plateau beside its top and an opening to it
     * in the shaft's wall at x 13 from {@code low} to {@code high}. The route of an order does not take him out of the fall, and
     * nothing else takes him sideways (the mine world, a drowning at a waterfall with air one step away).
     */
    private static void fall(GameTestHelper h, Block wall, int low, int high) {
        for (int x = 8; x <= 24; x++) for (int z = 12; z <= 20; z++) for (int y = 0; y <= 16; y++) {
            boolean shaft = x == 12 && z == 16, side = Math.abs(x - 12) + Math.abs(z - 16) == 1;
            boolean opening = x == 13 && z == 16 && y >= low && y <= high;
            BlockState block = y <= 11 && !(shaft && y >= 1) ? Blocks.STONE.defaultBlockState()
                    : shaft && y >= 1 && y <= 14 ? Fluids.FLOWING_WATER.getFlowing(8, true).createLegacyBlock()
                    : side && !opening ? wall.defaultBlockState() : Blocks.AIR.defaultBlockState();
            h.setBlock(new BlockPos(x, y, z), block);
        }
    }

    /** The water of the scene stands as it was placed, as a fall does, and spreads nowhere: its ticks are cleared. Called after the last block is set. */
    private static void still(GameTestHelper h) {
        ((LevelTicks<Fluid>) h.getLevel().getFluidTicks()).clearArea(BoundingBox.fromCorners(h.absolutePos(new BlockPos(8, 0, 12)), h.absolutePos(new BlockPos(24, 16, 20))));
    }

    /** The owner on the plateau and the order to come to him. */
    private static ServerPlayer called(GameTestHelper h, CompanionEntity oleg) {
        var owner = player(h, oleg);
        var at = h.absoluteVec(new Vec3(20.5, 12, 16.5));
        owner.teleportTo(at.x, at.y, at.z);
        h.assertTrue(CompanionEvents.executeAction(owner, "come", "", 1).success(), "The order was not accepted");
        return owner;
    }

    /** His way out of falling water has the turn. The goals of a mob are the game's own and closed to a test: read as the game holds them. */
    private static boolean leaving(CompanionEntity c) {
        try {
            var field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return ((GoalSelector) field.get(c)).getAvailableGoals().stream()
                    .anyMatch(goal -> goal.isRunning() && goal.getGoal().getClass().getSimpleName().equals("CompanionWaterExit"));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static String where(GameTestHelper h, CompanionEntity c) { return c.blockPosition().subtract(h.absolutePos(BlockPos.ZERO)).toShortString(); }

    /** The opening three blocks high: he gets out onto the plateau and loses neither health nor all his air. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void inAColumnOfFallingWaterWithAirOneStepAwayHeGetsOutAndDoesNotDrown(GameTestHelper h) {
        fall(h, Blocks.STONE, 12, 14);
        still(h);
        CompanionEntity oleg = spawn(h, 12, 7, 16);
        called(h, oleg);
        float[] lowestHealth = {oleg.getMaxHealth()};
        int[] lowestAir = {oleg.getAirSupply()};
        h.onEachTick(() -> {
            lowestHealth[0] = Math.min(lowestHealth[0], oleg.getHealth());
            lowestAir[0] = Math.min(lowestAir[0], oleg.getAirSupply());
        });
        h.succeedWhen(() -> {
            h.assertTrue(!oleg.isInWater() && oleg.onGround(), "Still in the water at " + where(h, oleg));
            h.assertTrue(lowestHealth[0] >= oleg.getMaxHealth() && lowestAir[0] > 0, "Drowned: health " + lowestHealth[0] + ", air " + lowestAir[0]);
        });
    }

    /**
     * The foot of a low fall (water over a lip a block up, falling water to his neck, the shaft shut above), a niche beside it and ore
     * in the wall at the height of his head, with a wooden pickaxe: a swing of 68 ticks. A swing that goes on is not treading water,
     * and he is not taken off it: taken to the niche at tick 60, he had no stand for the ore from there and the order ended with nothing.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aSwingAtOreFromTheFallIsNotBrokenOffByTheWayOut(GameTestHelper h) {
        fall(h, Blocks.STONE, 0, -1);
        for (int y = 3; y <= 14; y++) h.setBlock(new BlockPos(12, y, 16), Blocks.STONE);
        h.setBlock(new BlockPos(12, 2, 16), Fluids.FLOWING_WATER.getFlowing(7, false).createLegacyBlock());
        for (int x = 13; x <= 14; x++) for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(x, y, 16), Blocks.AIR);
        BlockPos ore = new BlockPos(11, 2, 16);
        h.setBlock(ore, Blocks.DEEPSLATE_COAL_ORE);
        still(h);
        CompanionEntity oleg = spawn(h, 12, 1, 16);
        oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        h.assertTrue(oleg.startWork("mine_ore", "coal", 1).success(), "The order was not accepted");
        long[] taken = {-1};
        h.onEachTick(() -> { if (taken[0] < 0 && h.getBlockState(ore).is(Blocks.DEEPSLATE_COAL_ORE) && leaving(oleg)) taken[0] = h.getTick(); });
        h.succeedWhen(() -> {
            h.assertTrue(!h.getBlockState(ore).is(Blocks.DEEPSLATE_COAL_ORE), "The ore is not mined, he is at " + where(h, oleg) + ": " + oleg.getLastTaskResult());
            h.assertTrue(taken[0] < 0, "Taken off the ore at tick " + taken[0] + ", before it broke at " + h.getTick());
        });
    }

    /** «Стой» on the way out: the way out is the order's, and ends with it. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void theWayOutOfTheFallEndsWithTheOrder(GameTestHelper h) {
        fall(h, Blocks.STONE, 12, 14);
        still(h);
        CompanionEntity oleg = spawn(h, 12, 7, 16);
        var owner = called(h, oleg);
        long[] told = {-1};
        int[] after = {0};
        h.onEachTick(() -> {
            if (!leaving(oleg)) return;
            if (told[0] < 0) { told[0] = h.getTick(); CompanionEvents.executeAction(owner, "stop", "", 1); }
            else if (h.getTick() > told[0] + 2) after[0]++;
        });
        h.runAfterDelay(200, () -> {
            h.assertTrue(told[0] >= 0, "The way out of the fall never had the turn");
            h.assertTrue(!oleg.isWorking(), "«Стой» did not end the order");
            h.assertTrue(after[0] == 0, "Went on out of the water for " + after[0] + " ticks after «стой»");
            h.succeed();
        });
    }

    /**
     * The opening is under the water line, a lip over it: the dry cell beyond is in sight and a step away, and afloat (he starts at the
     * top of the water: on his way up he would pass the opening and walk out) he cannot duck under the lip. The walls are bedrock. The
     * pocket of an aquifer under a ceiling (runs descent and void of seed 7): the push at the bank went on with the work shut out of its
     * turn until the order timed out. The push is given up after a while, and the order has its turn back while it is still an order.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aDryCellInSightButOutOfReachDoesNotHoldTheOrderForGood(GameTestHelper h) {
        fall(h, Blocks.BEDROCK, 12, 13);
        still(h);
        CompanionEntity oleg = spawn(h, 12, 13, 16);
        called(h, oleg);
        boolean[] left = {false}, back = {false};
        h.onEachTick(() -> {
            boolean now = leaving(oleg);
            left[0] |= now;
            back[0] |= left[0] && !now && oleg.isWorking();
        });
        h.succeedWhen(() -> {
            h.assertTrue(left[0], "The way out of the fall never had the turn");
            h.assertTrue(back[0], "The order has not had its turn back, he is at " + where(h, oleg) + (oleg.isWorking() ? "" : ", and it is over: " + oleg.getLastTaskResult()));
        });
    }
}

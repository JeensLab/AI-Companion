package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
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
                // Out of the water and back in on the way, once settled after the start: lying on the water instead of in it.
                if (wasInWater && !oleg.isInWater() && rel.x < 23 && swum >= 4) outOfWaterMidway++;
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
}

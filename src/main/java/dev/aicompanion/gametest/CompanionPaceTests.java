package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ticket 10: he walks and runs at a player's speed (walking 4.317 blocks a second, sprinting 5.612), runs on a long way and when
 * the player he follows runs, and follows over any distance on foot. A strip of flat ground 32 wide and 172 long.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPaceTests {
    /** Blocks a tick: a player walking and sprinting on level ground (see thePlayerReferenceIsWhatTheTestsAssume). */
    private static final double WALK = 0.2158, SPRINT = 0.2806;

    /** A player's own pace, measured the same way as his, so the numbers above are not taken on trust. */
    @GameTest(template = "marathon_far", timeoutTicks = 400)
    public static void thePlayerReferenceIsWhatTheTestsAssume(GameTestHelper h) {
        strip(h);
        var player = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(new BlockPos(56, 2, 10));
        player.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        double[] pace = new double[2];
        Vec3[] last = {null};
        h.onEachTick(() -> {
            boolean sprint = h.getTick() >= 100;
            player.setSprinting(sprint);
            player.setYRot(0); player.setXRot(0);
            player.travel(new Vec3(0, 0, .98));
            Vec3 now = player.position();
            // Ticks 40 to 99 walking, 140 to 199 sprinting: the speed has settled by then.
            if (last[0] != null && (h.getTick() >= 40 && h.getTick() < 100 || h.getTick() >= 140 && h.getTick() < 200)) pace[sprint ? 1 : 0] += now.z - last[0].z;
            last[0] = now;
        });
        h.runAtTickTime(201, () -> {
            double walk = pace[0] / 60, sprint = pace[1] / 60;
            h.assertTrue(Math.abs(walk - WALK) < WALK * .02 && Math.abs(sprint - SPRINT) < SPRINT * .02,
                    String.format("A player walks %.4f and sprints %.4f blocks a tick", walk, sprint));
            h.succeed();
        });
    }

    /** At the walking pace of the move control (nothing sprinting): within a twentieth of a player's walk. */
    @GameTest(template = "marathon_far", timeoutTicks = 400)
    public static void heWalksAtAPlayersPace(GameTestHelper h) {
        strip(h);
        CompanionEntity c = spawn(h, 56, 2, 10, 56, 2, 12);
        // Not following: eight blocks from the player he would turn back to him.
        c.setFollowing(false);
        double[] moved = {0};
        double[] lastZ = {c.getZ()};
        h.onEachTick(() -> {
            BlockPos far = h.absolutePos(new BlockPos(56, 2, 60));
            c.getMoveControl().setWantedPosition(far.getX() + .5, far.getY(), far.getZ() + .5, 1.0);
            // Ticks 20 to 49: the speed has settled.
            if (h.getTick() >= 20 && h.getTick() < 50) moved[0] += c.getZ() - lastZ[0];
            lastZ[0] = c.getZ();
            h.assertTrue(!c.isSprinting(), "Sprinting without a reason");
        });
        h.runAtTickTime(51, () -> {
            double pace = moved[0] / 30;
            h.assertTrue(Math.abs(pace - WALK) <= WALK * .05, String.format("Walked %.4f blocks a tick, a player %.4f", pace, WALK));
            h.succeed();
        });
    }

    /** A hundred blocks to go: he runs, at a player's sprint (the sprint's own +30% over a walk is kept, not squared). */
    @GameTest(template = "marathon_far", timeoutTicks = 400)
    public static void aLongWayIsRunAtAPlayersSprint(GameTestHelper h) {
        strip(h);
        CompanionEntity c = spawn(h, 56, 2, 10, 56, 2, 150);
        h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        double[] moved = {0};
        double[] lastZ = {c.getZ()};
        int[] running = {0};
        h.onEachTick(() -> {
            if (h.getTick() >= 60 && h.getTick() < 140) { moved[0] += c.getZ() - lastZ[0]; if (c.isSprinting()) running[0]++; }
            lastZ[0] = c.getZ();
        });
        h.runAtTickTime(141, () -> {
            double pace = moved[0] / 80;
            h.assertTrue(running[0] >= 78, "Sprinting only " + running[0] + " of 80 ticks");
            h.assertTrue(Math.abs(pace - SPRINT) <= SPRINT * .1, String.format("Ran %.4f blocks a tick, a player %.4f", pace, SPRINT));
            h.succeed();
        });
    }

    /** The player ten blocks off, standing: he comes to him at a run if the player is sprinting, at a walk if not, though no farther off than a walk. */
    @GameTest(template = "marathon_far", timeoutTicks = 200)
    public static void heRunsWhenThePlayerRuns(GameTestHelper h) { followed(h, true); }

    @GameTest(template = "marathon_far", timeoutTicks = 200)
    public static void heWalksWhenThePlayerWalks(GameTestHelper h) { followed(h, false); }

    private static void followed(GameTestHelper h, boolean playerRuns) {
        strip(h);
        CompanionEntity c = spawn(h, 56, 2, 10, 56, 2, 20);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        player.setSprinting(playerRuns);
        int[] near = {0}, running = {0};
        boolean[] came = {false};
        StringBuilder seen = new StringBuilder();
        Vec3[] last = {c.position()};
        h.onEachTick(() -> {
            // On the way (moving, a few ticks in): nothing but the player's own pace says how he comes (a run is held three ticks past a gap over twelve).
            // Only the coming: once beside the player the follow is over, and a step he takes after it is not the follow's.
            boolean moving = c.position().distanceTo(last[0]) > .1;
            last[0] = c.position();
            if (c.distanceTo(player) <= 4.5) came[0] = true;
            if (!came[0] && h.getTick() >= 5 && moving && c.distanceTo(player) <= 11) { near[0]++; if (c.isSprinting()) running[0]++; }
            if (h.getTick() < 60 && h.getTick() % 4 == 0) seen.append(String.format(" t%d:%.1f%s%s", h.getTick(), c.distanceTo(player), moving ? "m" : "", c.isSprinting() ? "s" : ""));
        });
        h.runAtTickTime(150, () -> {
            h.assertTrue(near[0] >= 10, "Not near enough to tell: " + near[0] + " ticks, gap " + Math.round(c.distanceTo(player)));
            if (playerRuns) h.assertTrue(running[0] >= near[0] * .9, "Sprinted " + running[0] + " of " + near[0] + " ticks beside a sprinting player:" + seen);
            else h.assertTrue(running[0] == 0, "Sprinted " + running[0] + " ticks beside a walking player");
            h.succeed();
        });
    }

    /** A world saved before the pace changed keeps the old speed in the companion's attributes: loaded, he walks at the new one. */
    @GameTest(template = "empty")
    public static void aCompanionSavedAtTheOldPaceLoadsAtAPlayersPace(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 1, 2, 1);
        var speed = c.getAttribute(Attributes.MOVEMENT_SPEED);
        double pace = speed.getBaseValue();
        speed.setBaseValue(.30);
        c.load(c.saveWithoutId(new CompoundTag()));
        h.assertTrue(speed.getBaseValue() == pace, "Loaded at " + speed.getBaseValue() + ", not " + pace);
        h.succeed();
    }

    /** A player sprinting away for fifteen seconds: he runs after him, and the gap does not grow past where he caught up. */
    @GameTest(template = "marathon_far", timeoutTicks = 400)
    public static void heKeepsUpWithARunningPlayer(GameTestHelper h) {
        strip(h);
        CompanionEntity c = spawn(h, 56, 2, 10, 56, 2, 20);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        double[] widest = {0};
        h.onEachTick(() -> {
            player.setSprinting(true);
            player.setYRot(0); player.setXRot(0);
            player.travel(new Vec3(0, 0, .98));
            // From tick 100: both up to speed, he following.
            if (h.getTick() >= 100) widest[0] = Math.max(widest[0], c.distanceTo(player));
        });
        h.runAtTickTime(320, () -> {
            h.assertTrue(widest[0] <= 12, String.format("Fell %.1f blocks behind a running player", widest[0]));
            h.succeed();
        });
    }

    /** The player a hundred and fifty blocks off: on foot all the way, no jump of the position, no teleport. */
    @GameTest(template = "marathon_far", timeoutTicks = 1500)
    public static void aPlayerHundredAndFiftyBlocksOffIsWalkedTo(GameTestHelper h) {
        strip(h);
        CompanionEntity c = spawn(h, 56, 2, 10, 56, 2, 160);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        Vec3[] last = {c.position()};
        double[] worst = {0};
        h.onEachTick(() -> { worst[0] = Math.max(worst[0], c.position().distanceTo(last[0])); last[0] = c.position(); });
        h.succeedWhen(() -> {
            h.assertTrue(worst[0] < 1.0, String.format("Jumped %.2f blocks in a tick", worst[0]));
            h.assertTrue(c.distanceToSqr(player) <= 36, "Still " + Math.round(c.distanceTo(player)) + " blocks off");
        });
    }

    private static void strip(GameTestHelper h) {
        box(h, 40, 2, 0, 71, 12, 171, Blocks.AIR);
        box(h, 40, 1, 0, 71, 1, 171, Blocks.STONE);
        box(h, 39, 1, 0, 39, 14, 171, Blocks.BARRIER); box(h, 72, 1, 0, 72, 14, 171, Blocks.BARRIER);
    }

    private static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2))) h.setBlock(pos, block);
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int y, int z, int playerX, int playerY, int playerZ) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(playerX, playerY, playerZ));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
}

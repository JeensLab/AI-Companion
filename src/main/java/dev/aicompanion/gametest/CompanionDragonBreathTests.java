package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The dragon's breath lies on the ground as a cloud (DragonFireball.onHit, DragonSittingFlamingPhase) and hurts past any armour:
 * he gets out of it and keeps out, the way a player does. The clouds here are the fireball's own, without a dragon.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDragonBreathTests {
    /** Out within this many ticks: the cloud waits a second before it hurts at all (AreaEffectCloud.waitTime). */
    private static final int OUT_IN = 40;
    /** What one touch of the fireball's cloud costs: Instant Damage II at half strength. */
    private static final float ONE_HIT = 6;

    /**
     * The breath falls on him where he stands by the player: he walks out of it and is not in it again while it lasts. Growing, it
     * may come into his margin by the player; then he steps out again before it reaches him.
     */
    @GameTest(template = "tall_tree", batch = "breath", timeoutTicks = 400)
    public static void heStepsOutOfTheBreathThatFallsOnHim(GameTestHelper h) {
        floor(h);
        CompanionEntity c = companion(h, 15, 15, 9, 15);
        float[] lowest = {c.getHealth()};
        AreaEffectCloud[] cloud = new AreaEffectCloud[1];
        boolean[] backIn = {false};
        h.onEachTick(() -> {
            lowest[0] = Math.min(lowest[0], c.getHealth());
            if (cloud[0] != null && cloud[0].tickCount > OUT_IN && insideOf(cloud[0], c)) backIn[0] = true;
        });
        h.runAfterDelay(20, () -> cloud[0] = fireballCloud(h, c.position()));
        h.runAfterDelay(20 + OUT_IN, () -> h.assertTrue(outOf(cloud[0], c), "Still in the breath: " + where(h, c, cloud[0])));
        h.runAfterDelay(20 + 300, () -> {
            h.assertTrue(!backIn[0], "Back in the breath");
            h.assertTrue(c.isAlive() && c.getMaxHealth() - lowest[0] <= ONE_HIT, "Hurt by the breath: lowest health " + lowest[0]);
            h.succeed();
        });
    }

    /** Told to stay, he still walks out of the breath, and stands where he got out to. */
    @GameTest(template = "tall_tree", batch = "breath", timeoutTicks = 400)
    public static void heStepsOutOfTheBreathWhenToldToStay(GameTestHelper h) {
        floor(h);
        CompanionEntity c = companion(h, 15, 15, 9, 15);
        c.setFollowing(false);
        AreaEffectCloud[] cloud = new AreaEffectCloud[1];
        h.runAfterDelay(20, () -> cloud[0] = fireballCloud(h, c.position()));
        h.runAfterDelay(20 + OUT_IN, () -> h.assertTrue(outOf(cloud[0], c), "Still in the breath: " + where(h, c, cloud[0])));
        h.runAfterDelay(20 + 200, () -> {
            h.assertTrue(outOf(cloud[0], c), "Back in the breath: " + where(h, c, cloud[0]));
            h.assertTrue(c.getNavigation().isDone(), "Still walking, told to stay");
            h.succeed();
        });
    }

    /** The breath falls on him halfway through «come to me», with the player beyond it: he walks out, and the order goes on. */
    @GameTest(template = "tall_tree", batch = "breath", timeoutTicks = 700)
    public static void anOrderWaitsWhileHeStepsOutOfTheBreath(GameTestHelper h) {
        floor(h);
        CompanionEntity c = companion(h, 3, 15, 3, 15);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        float[] lowest = {c.getHealth()};
        h.onEachTick(() -> lowest[0] = Math.min(lowest[0], c.getHealth()));
        AreaEffectCloud[] cloud = new AreaEffectCloud[1];
        boolean[] backIn = {false};
        h.runAfterDelay(10, () -> {
            BlockPos far = h.absolutePos(new BlockPos(28, 2, 15));
            player.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
            h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        });
        // A few blocks into his walk, the breath falls on him.
        h.onEachTick(() -> {
            if (cloud[0] == null && c.getX() > h.absolutePos(new BlockPos(8, 2, 15)).getX()) cloud[0] = fireballCloud(h, c.position());
            else if (cloud[0] != null && insideOf(cloud[0], c) && cloud[0].tickCount > OUT_IN) backIn[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(cloud[0] != null, "The breath has not fallen yet");
            h.assertTrue(!c.isWorking() && c.didLastTaskSucceed() && c.distanceToSqr(player) <= 16, "Not with the player yet: " + c.getLastTaskResult() + ", " + where(h, c, cloud[0]));
            h.assertTrue(cloud[0].isAlive(), "Waited for the breath to go instead of going round it");
            h.assertTrue(!backIn[0], "Walked through the breath");
            h.assertTrue(c.isAlive() && c.getMaxHealth() - lowest[0] <= ONE_HIT, "Hurt by the breath: lowest health " + lowest[0]);
        });
    }

    /**
     * The player stands in the breath: following, he waits at its edge instead of going in, and says why, once in the fight. The
     * player out of it, he comes; in it again, he waits again without saying it again.
     */
    @GameTest(template = "tall_tree", batch = "breath", timeoutTicks = 800)
    public static void heWaitsAtTheEdgeWhileThePlayerStandsInTheBreath(GameTestHelper h) {
        floor(h);
        CompanionEntity c = companion(h, 1, 15, 15, 15);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        // It lasts past the last look at tick 700.
        AreaEffectCloud cloud = sittingFlame(h, h.absolutePos(new BlockPos(15, 2, 15)), 900);
        double[] nearest = {Double.MAX_VALUE};
        java.util.List<String> said = new java.util.ArrayList<>();
        int[] serial = {c.autonomyLineSerial()};
        boolean[] rescued = {false};
        h.onEachTick(() -> {
            double dx = c.getX() - cloud.getX(), dz = c.getZ() - cloud.getZ();
            nearest[0] = Math.min(nearest[0], Math.sqrt(dx * dx + dz * dz) - cloud.getRadius());
            if (c.autonomyLineSerial() != serial[0]) { serial[0] = c.autonomyLineSerial(); said.add(c.autonomyLine()); }
            rescued[0] |= c.isSelfRescuing();
        });
        String line = "В драконье дыхание не полезу — подожду тут.";
        h.runAfterDelay(160, () -> {
            h.assertTrue(c.distanceToSqr(player) > 16, "Went into the breath to the player: " + where(h, c, cloud));
            h.assertTrue(gap(cloud, c) <= 7, "Did not come up to its edge: " + where(h, c, cloud));
            h.assertTrue(said.equals(java.util.List.of(line)), "Not told once why he waits: " + said);
            BlockPos out = h.absolutePos(new BlockPos(15, 2, 28));
            player.teleportTo(out.getX() + .5, out.getY(), out.getZ() + .5);
        });
        // The way to the player round the breath's walls is searched again every two seconds: he is waited for, up to 400 ticks, not looked at on one tick.
        long[] came = {-1};
        h.onEachTick(() -> {
            if (came[0] >= 0 || h.getTick() <= 160) return;
            if (c.distanceToSqr(player) <= 25) {
                came[0] = h.getTick();
                BlockPos in = h.absolutePos(new BlockPos(15, 2, 15));
                player.teleportTo(in.getX() + .5, in.getY(), in.getZ() + .5);
            } else h.assertTrue(h.getTick() < 560, "Did not come once the player was out: " + where(h, c, cloud));
        });
        h.runAfterDelay(700, () -> {
            h.assertTrue(came[0] >= 0, "Did not come once the player was out: " + where(h, c, cloud));
            h.assertTrue(c.distanceToSqr(player) > 16, "Went into the breath to the player the second time: " + where(h, c, cloud));
            h.assertTrue(said.equals(java.util.List.of(line)), "Told it again in the same fight: " + said);
            h.assertTrue(nearest[0] > 1.5, "Came within " + Math.round(nearest[0] * 10) / 10.0 + " blocks of the breath");
            h.assertTrue(!rescued[0], "Took the breath's edge for a trap: dug or pillared there");
            h.succeed();
        });
    }

    /**
     * The breath is a layer half a block high on the ground: standing on a block beside it he is over it and it does not reach him.
     * Told to stay, he stays there.
     */
    @GameTest(template = "tall_tree", batch = "breath", timeoutTicks = 300)
    public static void theBreathBelowHisFeetIsLeftAlone(GameTestHelper h) {
        floor(h);
        box(h, 12, 2, 14, 18, 2, 18, Blocks.STONE);
        CompanionEntity c = companion(h, 15, 15, 9, 22);
        c.teleportTo(c.getX(), c.getY() + 1, c.getZ());
        c.setFollowing(false);
        h.runAfterDelay(20, () -> {
            var start = c.position();
            AreaEffectCloud cloud = sittingFlame(h, h.absolutePos(new BlockPos(15, 2, 12)), 600);
            h.runAfterDelay(120, () -> {
                h.assertTrue(c.position().distanceTo(start) < .5, "Walked off over the breath: " + where(h, c, cloud));
                h.assertTrue(c.getHealth() >= c.getMaxHealth(), "Hurt by the breath below him");
                h.succeed();
            });
        });
    }

    /**
     * «Come to me» with the player standing in the breath, for longer than an order may stand still (12 s): he goes to its edge and
     * the order waits there; the player out of it, the order ends by the player.
     */
    @GameTest(template = "tall_tree", batch = "breath", timeoutTicks = 700)
    public static void comeToThePlayerInTheBreathWaitsAtItsEdge(GameTestHelper h) {
        floor(h);
        CompanionEntity c = companion(h, 1, 15, 15, 15);
        c.setFollowing(false);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        AreaEffectCloud cloud = sittingFlame(h, h.absolutePos(new BlockPos(15, 2, 15)), 600);
        double[] nearest = {Double.MAX_VALUE};
        boolean[] rescued = {false}, moved = {false};
        // How far from the player the order ended: once it is done he is free to stroll (he is not told to follow), so the tick 600 is too late to measure.
        double[] ended = {-1};
        h.onEachTick(() -> {
            double dx = c.getX() - cloud.getX(), dz = c.getZ() - cloud.getZ();
            nearest[0] = Math.min(nearest[0], Math.sqrt(dx * dx + dz * dz) - cloud.getRadius());
            rescued[0] |= c.isSelfRescuing();
            if (moved[0] && ended[0] < 0 && !c.isWorking()) ended[0] = c.distanceToSqr(player);
        });
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted"));
        h.runAfterDelay(330, () -> {
            h.assertTrue(c.isWorking(), "Gave the order up: " + c.getLastTaskResult());
            h.assertTrue(c.distanceToSqr(player) > 16, "Went into the breath to the player: " + where(h, c, cloud));
            h.assertTrue(gap(cloud, c) <= 7, "Did not come up to its edge: " + where(h, c, cloud));
            BlockPos out = h.absolutePos(new BlockPos(15, 2, 28));
            player.teleportTo(out.getX() + .5, out.getY(), out.getZ() + .5);
            moved[0] = true;
        });
        h.runAfterDelay(600, () -> {
            h.assertTrue(!c.isWorking() && c.didLastTaskSucceed() && ended[0] >= 0 && ended[0] <= 16, "Not with the player once out: " + c.getLastTaskResult()
                    + ", ended " + (ended[0] < 0 ? "never" : String.format("%.1f", Math.sqrt(ended[0])) + " blocks from him") + ", " + where(h, c, cloud));
            h.assertTrue(nearest[0] > 1.5, "Came within " + Math.round(nearest[0] * 10) / 10.0 + " blocks of the breath");
            h.assertTrue(!rescued[0], "Took the breath's edge for a trap: dug or pillared there");
            h.succeed();
        });
    }

    /** A cloud that is not the dragon's breath (a lingering potion's: whether it hurts or heals does not show) is left alone. */
    @GameTest(template = "tall_tree", batch = "breath", timeoutTicks = 300)
    public static void aCloudThatIsNotTheDragonsBreathIsLeftAlone(GameTestHelper h) {
        floor(h);
        CompanionEntity c = companion(h, 15, 15, 9, 22);
        c.setFollowing(false);
        h.runAfterDelay(20, () -> {
            var start = c.position();
            AreaEffectCloud cloud = new AreaEffectCloud(h.getLevel(), start.x, start.y, start.z);
            cloud.setRadius(3.0F);
            cloud.setDuration(600);
            cloud.setPotionContents(new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.HEALING));
            h.getLevel().addFreshEntity(cloud);
            h.runAfterDelay(100, () -> {
                h.assertTrue(c.position().distanceTo(start) < .5, "Walked out of a potion's cloud: " + where(h, c, cloud));
                h.succeed();
            });
        });
    }

    /** The breath lies on the straight way to the player: he goes round it, clear of it all the way, while it is still there. */
    @GameTest(template = "tall_tree", batch = "breath", timeoutTicks = 700)
    public static void heGoesRoundTheBreathOnHisWay(GameTestHelper h) {
        floor(h);
        CompanionEntity c = companion(h, 3, 15, 3, 15);
        walkPast(h, c, sittingFlame(h, h.absolutePos(new BlockPos(15, 2, 15)), 600), true);
    }

    /**
     * The breath fills the only way, a walled lane, for longer than an order may stand still (12 s): he waits for it to go,
     * without giving the order up, and then walks on.
     */
    @GameTest(template = "tall_tree", batch = "breath", timeoutTicks = 800)
    public static void heWaitsForTheBreathThatFillsTheOnlyWay(GameTestHelper h) {
        lane(h);
        CompanionEntity c = companion(h, 3, 15, 3, 15);
        walkPast(h, c, sittingFlame(h, h.absolutePos(new BlockPos(15, 2, 15)), 400), false);
    }

    /** Following, the same: no «no way to you» for the breath, and he comes once it is gone. */
    @GameTest(template = "tall_tree", batch = "breath", timeoutTicks = 800)
    public static void heFollowsDownTheLaneOnceTheBreathIsGone(GameTestHelper h) {
        lane(h);
        CompanionEntity c = companion(h, 3, 15, 28, 15);
        AreaEffectCloud cloud = sittingFlame(h, h.absolutePos(new BlockPos(15, 2, 15)), 400);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        int serial = c.autonomyLineSerial();
        h.succeedWhen(() -> {
            h.assertTrue(c.autonomyLineSerial() == serial, "Said something about the way: " + c.autonomyLine());
            h.assertTrue(!cloud.isAlive() && c.distanceToSqr(player) <= 25, "Not with the player yet: " + where(h, c, cloud));
        });
    }

    /**
     * «Come to me» with the player at the far end of the yard and the breath in between. Never in its margin (a block of slack for
     * his width), unhurt; there while it is still there, or only after it is gone.
     */
    private static void walkPast(GameTestHelper h, CompanionEntity c, AreaEffectCloud cloud, boolean whileThere) {
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        float[] lowest = {c.getHealth()};
        double[] nearest = {Double.MAX_VALUE};
        h.onEachTick(() -> {
            lowest[0] = Math.min(lowest[0], c.getHealth());
            if (cloud.isAlive()) {
                double dx = c.getX() - cloud.getX(), dz = c.getZ() - cloud.getZ();
                nearest[0] = Math.min(nearest[0], Math.sqrt(dx * dx + dz * dz) - cloud.getRadius());
            }
        });
        h.runAfterDelay(10, () -> {
            BlockPos far = h.absolutePos(new BlockPos(28, 2, 15));
            player.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
            h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 10 && !c.isWorking(), "Still on the way: " + where(h, c, cloud));
            h.assertTrue(c.didLastTaskSucceed() && c.distanceToSqr(player) <= 16, "Gave the order up: " + c.getLastTaskResult());
            h.assertTrue(cloud.isAlive() == whileThere, whileThere ? "Waited for the breath to go instead of going round it" : "Got through while the breath filled the way");
            h.assertTrue(nearest[0] > 1.5, "Came within " + Math.round(nearest[0] * 10) / 10.0 + " blocks of the breath");
            h.assertTrue(c.isAlive() && lowest[0] >= c.getMaxHealth(), "Hurt by the breath: lowest health " + lowest[0]);
        });
    }

    // ---- Fixture ----

    /** The cloud a dragon's fireball leaves where it lands (DragonFireball.onHit): 3 blocks growing to 7 over its 30 seconds. */
    private static AreaEffectCloud fireballCloud(GameTestHelper h, Vec3 at) {
        AreaEffectCloud cloud = new AreaEffectCloud(h.getLevel(), at.x, at.y, at.z);
        cloud.setParticle(ParticleTypes.DRAGON_BREATH);
        cloud.setRadius(3.0F);
        cloud.setDuration(600);
        cloud.setRadiusPerTick((7.0F - cloud.getRadius()) / cloud.getDuration());
        cloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
        h.getLevel().addFreshEntity(cloud);
        return cloud;
    }

    /** The flame the dragon breathes sitting on the portal (DragonSittingFlamingPhase): 5 blocks across, for {@code ticks}. */
    private static AreaEffectCloud sittingFlame(GameTestHelper h, BlockPos at, int ticks) {
        AreaEffectCloud cloud = new AreaEffectCloud(h.getLevel(), at.getX() + .5, at.getY(), at.getZ() + .5);
        cloud.setRadius(5.0F);
        cloud.setDuration(ticks);
        cloud.setParticle(ParticleTypes.DRAGON_BREATH);
        cloud.addEffect(new MobEffectInstance(MobEffects.HARM));
        h.getLevel().addFreshEntity(cloud);
        return cloud;
    }

    /** Clear of the cloud by two blocks across: where the cloud would not reach him even after a few seconds of growing. */
    private static boolean outOf(AreaEffectCloud cloud, CompanionEntity c) {
        double dx = c.getX() - cloud.getX(), dz = c.getZ() - cloud.getZ();
        return Math.sqrt(dx * dx + dz * dz) > cloud.getRadius() + 2;
    }

    /** How far he is from the cloud's edge across (negative: in it). He starts some 9 blocks off; at the edge he waits within 3 of a cell clear of its margin. */
    private static double gap(AreaEffectCloud cloud, CompanionEntity c) {
        return Math.hypot(c.getX() - cloud.getX(), c.getZ() - cloud.getZ()) - cloud.getRadius();
    }

    /** Where the cloud reaches him: what the game hurts. */
    private static boolean insideOf(AreaEffectCloud cloud, CompanionEntity c) {
        double dx = c.getX() - cloud.getX(), dz = c.getZ() - cloud.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= cloud.getRadius();
    }

    private static String where(GameTestHelper h, CompanionEntity c, AreaEffectCloud cloud) {
        double dx = c.getX() - cloud.getX(), dz = c.getZ() - cloud.getZ();
        return "at " + h.relativePos(c.blockPosition()).toShortString() + ", " + Math.round(Math.sqrt(dx * dx + dz * dz) * 10) / 10.0
                + " blocks from the middle of a cloud of " + Math.round(cloud.getRadius() * 10) / 10.0;
    }

    /** A lane three blocks wide along z 15, walled with bedrock: he digs no way round (CompanionRecovery digs stone). */
    private static void lane(GameTestHelper h) {
        floor(h);
        box(h, 0, 2, 13, 31, 4, 13, Blocks.BEDROCK);
        box(h, 0, 2, 17, 31, 4, 17, Blocks.BEDROCK);
        box(h, 0, 2, 13, 0, 4, 17, Blocks.BEDROCK);
        box(h, 31, 2, 13, 31, 4, 17, Blocks.BEDROCK);
    }

    /** Bedrock, a stone floor, walkers on y 2, open sky over 32 by 32 blocks. */
    private static void floor(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 10, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 1, 31, Blocks.STONE);
    }

    /** Oleg at x, z and his player at px, pz, both on the floor; he follows the player. */
    private static CompanionEntity companion(GameTestHelper h, int x, int z, int px, int pz) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, 2, z);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(true);
        BlockPos stand = h.absolutePos(new BlockPos(px, 2, pz));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        c.setSearchRadius(16);
        return c;
    }

    private static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(new BlockPos(x, y, z), block);
    }
}

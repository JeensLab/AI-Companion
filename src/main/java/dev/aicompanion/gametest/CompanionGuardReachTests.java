package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Issue #105, «охраняй меня»: on the guard order Oleg watches fifty blocks around the PLAYER and goes for the monster nearest to
 * the player, not the one nearest to himself. The arena is a long road along x with the player at its middle (x = 0), in chunks
 * that are force-loaded, since the template is only a few blocks wide.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGuardReachTests {
    private static final int HALF_ROAD = 60, HALF_WIDTH = 4, MIDDLE_X = 2, MIDDLE_Z = 2;
    private static final List<ChunkPos> FORCED_CHUNKS = new ArrayList<>();

    @AfterBatch(batch = "guard_reach_order")
    public static void releaseOrder(ServerLevel level) { release(level); }
    @AfterBatch(batch = "guard_reach_behind")
    public static void releaseBehind(ServerLevel level) { release(level); }
    @AfterBatch(batch = "guard_reach_nearest")
    public static void releaseNearest(ServerLevel level) { release(level); }
    @AfterBatch(batch = "guard_reach_pet")
    public static void releasePet(ServerLevel level) { release(level); }
    @AfterBatch(batch = "guard_reach_wall")
    public static void releaseWall(ServerLevel level) { release(level); }
    @AfterBatch(batch = "guard_reach_leave")
    public static void releaseLeave(ServerLevel level) { release(level); }
    @AfterBatch(batch = "guard_reach_creeper")
    public static void releaseCreeper(ServerLevel level) { release(level); }
    @AfterBatch(batch = "guard_reach_switch")
    public static void releaseSwitch(ServerLevel level) { release(level); }
    @AfterBatch(batch = "guard_reach_fenced")
    public static void releaseFenced(ServerLevel level) { release(level); }
    @AfterBatch(batch = "guard_reach_fenced_first")
    public static void releaseFencedFirst(ServerLevel level) { release(level); }

    /** The issue's scene: zombies 30 and 10 blocks from the player, the one at 10 dies first, then he goes for the one at 30. */
    @GameTest(template = "empty", timeoutTicks = 900, batch = "guard_reach_order")
    public static void theZombieNearestToThePlayerFallsFirstThenTheFarOne(GameTestHelper h) {
        h.startSequence().thenExecute(() -> arena(h)).thenIdle(20).thenExecute(() -> {
            CompanionEntity companion = guard(h, 1);
            var near = zombie(h, 10);
            var far = zombie(h, -30);
            List<String> deaths = new ArrayList<>();
            h.onEachTick(() -> {
                if (!near.isAlive() && !deaths.contains("near")) deaths.add("near");
                if (!far.isAlive() && !deaths.contains("far")) deaths.add("far");
            });
            h.succeedWhen(() -> {
                h.assertTrue(deaths.size() == 2, "Both zombies must fall; fell " + deaths + ", target=" + companion.getTarget()
                        + " nearHp=" + near.getHealth() + " farHp=" + far.getHealth());
                h.assertTrue(deaths.get(0).equals("near"),
                        "The zombie 30 blocks from the player fell first: " + deaths + " " + spots(h, player(h, companion), companion));
            });
        });
    }

    /** A zombie forty blocks behind the player: he sets out for it, and the player is not touched. */
    @GameTest(template = "empty", timeoutTicks = 260, batch = "guard_reach_behind")
    public static void aZombieFortyBlocksBehindThePlayerIsGoneForAndThePlayerIsNotHurt(GameTestHelper h) {
        h.startSequence().thenExecute(() -> arena(h)).thenIdle(20).thenExecute(() -> {
            CompanionEntity companion = guard(h, 1);
            ServerPlayer player = player(h, companion);
            var zombie = zombie(h, -40);
            double start = companion.distanceTo(zombie);
            h.runAfterDelay(80, () -> {
                h.assertTrue(companion.getTarget() == zombie || !zombie.isAlive(), "Target was " + companion.getTarget() + " " + spots(h, player, companion));
                h.assertTrue(!zombie.isAlive() || companion.distanceTo(zombie) < start - 10,
                        "He did not set out: " + companion.distanceTo(zombie) + " from " + start);
            });
            h.runAtTickTime(220, () -> {
                h.assertTrue(player.getHealth() == player.getMaxHealth(), "The player was hurt: " + player.getHealth());
                h.succeed();
            });
        });
    }

    /** One zombie is nearer to Oleg (10 blocks), the other nearer to the player (12 blocks): the player's side counts. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "guard_reach_nearest")
    public static void theTargetIsTheMonsterNearestToThePlayerNotToHim(GameTestHelper h) {
        h.startSequence().thenExecute(() -> arena(h)).thenIdle(20).thenExecute(() -> {
            CompanionEntity companion = guard(h, 35);
            var nearToHim = zombie(h, 45);
            var nearToPlayer = zombie(h, -12);
            h.runAtTickTime(60, () -> {
                h.assertTrue(companion.getTarget() == nearToPlayer,
                        "Target was " + companion.getTarget() + ", wanted the zombie 12 blocks from the player; " + spots(h, player(h, companion), companion));
                h.assertTrue(nearToHim.isAlive(), "The zombie 45 blocks from the player fell first");
                h.succeed();
            });
        });
    }

    /** The player's tamed wolf and the player himself are never targets. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "guard_reach_pet")
    public static void thePlayersWolfAndThePlayerAreNeverTargets(GameTestHelper h) {
        h.startSequence().thenExecute(() -> arena(h)).thenIdle(20).thenExecute(() -> {
            CompanionEntity companion = guard(h, 1);
            ServerPlayer player = player(h, companion);
            var wolf = h.spawn(EntityType.WOLF, at(5));
            wolf.tame(player);
            h.onEachTick(() -> h.assertTrue(companion.getTarget() == null, "He took a target with no monster around: " + companion.getTarget()));
            h.runAtTickTime(120, h::succeed);
        });
    }

    /** A creeper far from the player stays out of the watch: the creeper rules are the creeper guard's, as before. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "guard_reach_creeper")
    public static void aCreeperFarFromThePlayerIsNotGoneFor(GameTestHelper h) {
        h.startSequence().thenExecute(() -> arena(h)).thenIdle(20).thenExecute(() -> {
            CompanionEntity companion = guard(h, 1);
            var creeper = h.spawn(EntityType.CREEPER, at(30));
            creeper.setNoAi(true);
            h.onEachTick(() -> h.assertTrue(companion.getTarget() != creeper, "He went for a creeper 30 blocks from the player"));
            h.runAtTickTime(120, h::succeed);
        });
    }

    /** The nearest zombie is walled in after he chose it: neither he nor the player sees it, so he lets it go for the one in the open. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "guard_reach_wall")
    public static void aMonsterNobodySeesAnyMoreIsLetGoForOneInTheOpen(GameTestHelper h) {
        h.startSequence().thenExecute(() -> arena(h)).thenIdle(20).thenExecute(() -> {
            CompanionEntity companion = guard(h, 1);
            var walled = zombie(h, 10);
            var open = zombie(h, -30);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(companion.getTarget() == walled, "He did not choose the nearest zombie: " + companion.getTarget()))
                    .thenExecute(() -> wallIn(h, 10))
                    .thenWaitUntil(() -> h.assertTrue(companion.getTarget() == open, "He kept the zombie nobody sees: target=" + companion.getTarget()))
                    .thenSucceed();
        });
    }

    /** A creeper that was by the player and walks off to thirty blocks is let go: a blow out there may end in its blast. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "guard_reach_leave")
    public static void aCreeperThatLeavesThePlayerIsLetGo(GameTestHelper h) {
        h.startSequence().thenExecute(() -> arena(h)).thenIdle(20).thenExecute(() -> {
            CompanionEntity companion = guard(h, 1);
            var creeper = h.spawn(EntityType.CREEPER, at(-6));
            creeper.setNoAi(true);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(companion.getTarget() == creeper, "He did not choose the creeper: " + companion.getTarget()))
                    .thenExecute(() -> {
                        BlockPos far = h.absolutePos(at(-30));
                        creeper.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
                    })
                    .thenIdle(40)
                    .thenExecute(() -> h.assertTrue(companion.getTarget() == null, "He kept the creeper that left: target=" + companion.getTarget()))
                    .thenSucceed();
        });
    }

    /** The only monster around stands behind a fence he cannot cross: he goes to it, gives it up and comes back to the player. */
    @GameTest(template = "empty", timeoutTicks = 900, batch = "guard_reach_fenced")
    public static void aMonsterHeCannotReachIsGivenUpAndHeStaysByThePlayer(GameTestHelper h) {
        h.startSequence().thenExecute(() -> arena(h)).thenIdle(20).thenExecute(() -> {
            CompanionEntity companion = guard(h, 1);
            ServerPlayer player = player(h, companion);
            var fenced = zombie(h, -20);
            fenceIn(h, -20);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(companion.getTarget() == fenced, "He did not go for the fenced zombie: " + companion.getTarget()))
                    .thenWaitUntil(() -> h.assertTrue(companion.getTarget() == null, "He holds a zombie he cannot reach: " + spots(h, player, companion)))
                    .thenWaitUntil(() -> h.assertTrue(companion.distanceTo(player) < 6, "He did not come back: " + spots(h, player, companion)))
                    .thenExecute(() -> h.assertTrue(fenced.isAlive(), "The fenced zombie fell: he reached what cannot be reached"))
                    .thenSucceed();
        });
    }

    /** The monster nearer to the player is unreachable, the one farther is in the open: after giving up the first he kills the second. */
    @GameTest(template = "empty", timeoutTicks = 1500, batch = "guard_reach_fenced_first")
    public static void anUnreachableMonsterNearerToThePlayerDoesNotHideAReachableOne(GameTestHelper h) {
        h.startSequence().thenExecute(() -> arena(h)).thenIdle(20).thenExecute(() -> {
            CompanionEntity companion = guard(h, 1);
            ServerPlayer player = player(h, companion);
            var fenced = zombie(h, -10);
            fenceIn(h, -10);
            var open = zombie(h, 18);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(companion.getTarget() == fenced, "He did not choose the fenced zombie first: " + companion.getTarget()))
                    .thenWaitUntil(() -> h.assertTrue(!open.isAlive(), "The reachable zombie stayed alive: target=" + companion.getTarget() + " "
                            + spots(h, player, companion) + " openHp=" + open.getHealth()))
                    .thenExecute(() -> h.assertTrue(fenced.isAlive(), "The fenced zombie fell"))
                    .thenSucceed();
        });
    }

    /**
     * With the switch off the guard order picks as before: the monster nearest to Oleg (right beside him, 9 blocks from the player),
     * not the one nearest to the player (6 blocks). Both stay within ten blocks of the player, so that the old rule keeps taking them
     * while he walks back towards the player; the first target he takes and the order of the two are what counts, not the kill time.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "guard_reach_switch")
    public static void withTheSwitchOffTheTargetIsTheMonsterNearestToHim(GameTestHelper h) {
        h.startSequence().thenExecute(() -> arena(h)).thenIdle(20).thenExecute(() -> {
            CompanionEntity companion = guard(h, 8);
            companion.setGuardByPlayer(false);
            var nearToPlayer = zombie(h, -6);
            var nearToHim = zombie(h, 9);
            List<net.minecraft.world.entity.LivingEntity> first = new ArrayList<>();
            h.onEachTick(() -> {
                if (first.isEmpty() && companion.getTarget() != null) first.add(companion.getTarget());
                h.assertTrue(companion.getTarget() != nearToPlayer || !nearToHim.isAlive(),
                        "He went for the zombie nearest to the player while the one beside him lived; " + spots(h, player(h, companion), companion));
            });
            h.runAtTickTime(100, () -> {
                h.assertTrue(!first.isEmpty() && first.get(0) == nearToHim,
                        "First target was " + (first.isEmpty() ? null : first.get(0)) + ", wanted the zombie beside him; " + spots(h, player(h, companion), companion));
                h.succeed();
            });
        });
    }

    /** The switch lives in the save with him; an old save has it on. */
    @GameTest(template = "empty", batch = "guard_reach_switch")
    public static void theGuardSwitchIsSavedAndAnOldSaveHasItOn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 4);
        c.setGuardByPlayer(false);
        CompanionEntity other = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        other.load(c.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(!other.guardByPlayer(), "The switch was not saved");
        CompanionEntity third = h.spawn(AICompanionMod.COMPANION.get(), 12, 1, 12);
        third.setGuardByPlayer(false);
        third.load(new net.minecraft.nbt.CompoundTag());
        h.assertTrue(third.guardByPlayer(), "An old save loaded with the switch off");
        h.succeed();
    }

    /** A ring of fences three blocks from x, round the zombie standing there: a mob cannot jump 1.5 blocks, and a blade does not reach over three. */
    private static void fenceIn(GameTestHelper h, int x) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (Math.abs(dx) != 3 && Math.abs(dz) != 3) continue;
            h.getLevel().setBlockAndUpdate(h.absolutePos(at(x).offset(dx, 0, dz)), Blocks.OAK_FENCE.defaultBlockState());
        }
    }

    /** Stone round the foot of the road at x, up to the roof, leaving the one cell the mob stands in. */
    private static void wallIn(GameTestHelper h, int x) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = 1; y <= 4; y++) {
            boolean mobCell = dx == 0 && dz == 0 && y < 4;
            if (!mobCell) h.getLevel().setBlockAndUpdate(h.absolutePos(at(x).offset(dx, y - 1, dz)), Blocks.STONE.defaultBlockState());
        }
    }

    /** Stone road with air above it, in chunks held loaded for the test. */
    private static void arena(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos middle = h.absolutePos(new BlockPos(MIDDLE_X, 0, MIDDLE_Z));
        for (int cx = SectionPos.blockToSectionCoord(middle.getX() - HALF_ROAD); cx <= SectionPos.blockToSectionCoord(middle.getX() + HALF_ROAD); cx++)
            for (int cz = SectionPos.blockToSectionCoord(middle.getZ() - HALF_WIDTH); cz <= SectionPos.blockToSectionCoord(middle.getZ() + HALF_WIDTH); cz++) {
                level.setChunkForced(cx, cz, true);
                FORCED_CHUNKS.add(new ChunkPos(cx, cz));
                level.getChunk(cx, cz);
            }
        clearMobs(level);
        for (int x = -HALF_ROAD; x <= HALF_ROAD; x++) for (int z = -HALF_WIDTH; z <= HALF_WIDTH; z++) {
            level.setBlockAndUpdate(middle.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            for (int y = 1; y <= 4; y++) level.setBlockAndUpdate(middle.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    /** The road is outside the template, so the game does not sweep it: what an earlier test left there would join this fight. */
    private static void clearMobs(ServerLevel level) {
        for (ChunkPos chunk : FORCED_CHUNKS)
            for (var mob : level.getEntitiesOfClass(Mob.class, new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                    chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1))) mob.discard();
    }

    private static void release(ServerLevel level) {
        clearMobs(level);
        for (ChunkPos chunk : FORCED_CHUNKS) level.setChunkForced(chunk.x, chunk.z, false);
        FORCED_CHUNKS.clear();
    }

    /** A point of the road on the ground, x counted from the player (x = 0). */
    private static BlockPos at(int x) { return new BlockPos(MIDDLE_X + x, 1, MIDDLE_Z); }

    /** Oleg on the guard order, at the given x of the road; the player stands at x = 0. */
    private static CompanionEntity guard(GameTestHelper h, int x) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), at(x));
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = h.absolutePos(at(0));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        companion.setProtecting(true);
        return companion;
    }

    /** A helmet: the test world is in daylight, and a burning zombie is no evidence of a hit. */
    private static Zombie zombie(GameTestHelper h, int x) {
        var zombie = h.spawn(EntityType.ZOMBIE, at(x));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setNoAi(true);
        return zombie;
    }

    /** Where the player and Oleg stand on the road, for the failure messages. */
    private static String spots(GameTestHelper h, ServerPlayer player, CompanionEntity companion) {
        double zero = h.absoluteVec(at(0).getCenter()).x;
        return "player x=" + (player.getX() - zero) + " oleg x=" + (companion.getX() - zero);
    }

    private static ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

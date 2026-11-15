package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Second review of the Nether errand (15.11): the tunnel through a wall belongs to the walk out of the Nether alone, a walk stopped by the
 * next step of a plan is no failed walk, what he says to a stop is what he does, and a work is lost to a world load only when the
 * errand's crossing was in progress. The arena and the restored errand are those of CompanionNetherErrandTests, in batches of their own.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionNetherErrandReviewTests {
    private static final long MOST = 20 * 60 * 5;

    @AfterBatch(batch = "errand16")
    public static void afterErrand16(ServerLevel level) { CompanionNetherErrandTests.afterErrand(level); }

    @AfterBatch(batch = "errand17")
    public static void afterErrand17(ServerLevel level) { CompanionNetherErrandTests.afterErrand(level); }

    @AfterBatch(batch = "errand18")
    public static void afterErrand18(ServerLevel level) { CompanionNetherErrandTests.afterErrand(level); }

    /**
     * Following the player with a rock wall four blocks thick between them and no way round (a hill, a base): route recovery digs the
     * cells round him as it always did, but never bores a passage through the land, run after run.
     */
    @GameTest(template = "empty", timeoutTicks = 700)
    public static void followingThePlayerBehindARockWallHeDoesNotBoreThroughIt(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y <= 8; y++) {
            boolean shell = x == 0 || x == 15 || z == 0 || z == 15 || y == 0 || y == 8;
            h.setBlock(new BlockPos(x, y, z), shell ? Blocks.BEDROCK : z >= 6 && z <= 9 ? Blocks.STONE : Blocks.AIR);
        }
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 3);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(8, 1, 12));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        c.setFollowing(true);
        h.runAtTickTime(600, () -> {
            for (int x = 1; x <= 14; x++) for (int y = 1; y <= 2; y++) for (int z = 7; z <= 9; z++)
                h.assertTrue(h.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE), "A passage bored into the wall at " + new BlockPos(x, y, z) + ": " + c.getLastTaskResult());
            h.succeed();
        });
    }

    /**
     * A walk out begun in the gap between two steps of a plan and stopped by the next step is not a failed walk: after more such steps
     * than the walks he is allowed, with nothing ever tried to the end, he is still going out (not "cannot get there"). He is shut in a
     * cell of bedrock with a quartz at its end, so no walk gets him anywhere, and each step digs that quartz again.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000, batch = "errand16")
    public static void aWalkStoppedByTheNextStepOfAPlanIsNoFailedWalk(GameTestHelper h) {
        CompanionNetherErrandTests.ground(h);
        CompanionNetherErrandTests.lightPortal(h);
        var server = h.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = CompanionNetherErrandTests.arenaCell(h);
        CompanionNetherErrandTests.arena(nether, cell[0], cell[1]);
        int x = cell[0] + 5, z = cell[1] + 4;
        for (int i = x - 1; i <= x + 1; i++) for (int j = z - 1; j <= z + 3; j++) for (int y = 150; y <= 153; y++)
            nether.setBlockAndUpdate(new BlockPos(i, y, j), (i == x && j >= z && j <= z + 2 && y >= 151 && y <= 152 ? Blocks.AIR : Blocks.BEDROCK).defaultBlockState());
        BlockPos ore = new BlockPos(x, 151, z + 2);
        CompanionEntity there = CompanionNetherErrandTests.restored(h, nether, cell, new BlockPos(cell[0], 151, cell[1]).asLong(), MOST);
        there.moveTo(x + .5, 151, z + .5, 0, 0);
        ServerPlayer owner = server.getPlayerList().getPlayer(there.getOwnerId());
        UUID id = there.getUUID();
        boolean[] added = {false};
        int[] digs = {0}, phase = {0};
        long[] since = {0};
        h.onEachTick(() -> {
            if (!added[0]) {
                if (!CompanionNetherErrandTests.FORCED.stream().allMatch(chunk -> nether.areEntitiesLoaded(chunk.toLong()))) return;
                nether.addFreshEntity(there); added[0] = true;
            }
            if (phase[0] == 0) {
                nether.setBlockAndUpdate(ore, Blocks.NETHER_QUARTZ_ORE.defaultBlockState());
                var said = CompanionEvents.executeAction(owner, "mine_ore", "minecraft:quartz_ore", 1);
                h.assertTrue(said.success() && there.hasActiveWorkOrder(), "The step was not taken: " + said.message() + " / " + CompanionNetherErrandTests.describe(h, nether, id));
                digs[0]++; phase[0] = 1;
            } else if (phase[0] == 1) {
                if (there.hasActiveWorkOrder() || !nether.getBlockState(ore).isAir()) return;
                since[0] = there.tickCount; phase[0] = 2;
            } else if (phase[0] == 2 && there.tickCount - since[0] >= 80) {
                // After the gap he has set out again: the next step comes while he is on the way.
                if (digs[0] < 5) phase[0] = 0;
                else {
                    var said = CompanionEvents.executeAction(owner, "wait", "", 1);
                    h.assertTrue(said.success() && said.message().equals("Возвращаюсь из Незера."), "Said: " + said.message());
                    phase[0] = 3;
                }
            }
        });
        h.succeedWhen(() -> h.assertTrue(phase[0] == 3 && nether.getEntity(id) == there, "Not yet: digs " + digs[0] + ", phase " + phase[0]));
    }

    /** Given up on the way out (no portal at all), a stop is taken as a stop and answered by what he does: he stays. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000, batch = "errand17")
    public static void aStopWhileStuckInTheNetherSaysHeStaysHere(GameTestHelper h) {
        CompanionNetherErrandTests.ground(h);
        CompanionNetherErrandTests.lightPortal(h);
        var server = h.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = CompanionNetherErrandTests.arenaCell(h);
        CompanionNetherErrandTests.arena(nether, cell[0], cell[1]);
        for (int x = cell[0]; x <= cell[0] + 1; x++) for (int y = 151; y <= 153; y++) nether.setBlockAndUpdate(new BlockPos(x, y, cell[1]), Blocks.AIR.defaultBlockState());
        CompanionEntity there = CompanionNetherErrandTests.restored(h, nether, cell, null, MOST);
        ServerPlayer owner = server.getPlayerList().getPlayer(there.getOwnerId());
        boolean[] added = {false};
        h.onEachTick(() -> {
            if (!added[0] && CompanionNetherErrandTests.FORCED.stream().allMatch(chunk -> nether.areEntitiesLoaded(chunk.toLong()))) { nether.addFreshEntity(there); added[0] = true; }
        });
        h.succeedWhen(() -> {
            h.assertTrue(added[0] && there.tickCount >= 100, "Ticked " + there.tickCount + " times only");
            var said = CompanionEvents.executeAction(owner, "wait", "", 1);
            h.assertTrue(said.success() && said.message().equals("Не могу дойти до портала. Стою здесь."), "Said: " + said.message());
        });
    }

    /**
     * A building of the player's in the Nether (a chest he set down within sixteen blocks) is no rock to bore through: shut in a pocket
     * with four blocks of netherrack between him and the portal's room, he does not tunnel there.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 9000, batch = "errand18")
    public static void theWalkOutDoesNotTunnelNearThePlayersBuilding(GameTestHelper h) {
        CompanionNetherErrandTests.ground(h);
        CompanionNetherErrandTests.lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = CompanionNetherErrandTests.arenaCell(h);
        CompanionNetherPitTests.walledArena(nether, cell[0], cell[1], 7);
        CompanionEntity there = CompanionNetherErrandTests.restored(h, nether, cell, null, MOST);
        there.setItem(0, ItemStack.EMPTY);
        there.moveTo(cell[0] + .5, 151, cell[1] + 7.5, 0, 0);
        BlockPos chest = new BlockPos(cell[0] + 3, 151, cell[1] + 2);
        nether.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        var player = (ServerPlayer) h.getLevel().getPlayerByUUID(there.getOwnerId());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(Level.NETHER, nether, chest), Blocks.NETHERRACK.defaultBlockState(), player));
        boolean[] added = {false};
        h.onEachTick(() -> {
            if (!added[0] && CompanionNetherErrandTests.FORCED.stream().allMatch(chunk -> nether.areEntitiesLoaded(chunk.toLong()))) { nether.addFreshEntity(there); added[0] = true; }
        });
        h.succeedWhen(() -> {
            h.assertTrue(added[0] && there.tickCount >= 450, "Not yet ticked long enough: " + (added[0] ? there.tickCount : -1));
            for (int y = 151; y <= 152; y++)
                h.assertTrue(nether.getBlockState(new BlockPos(cell[0], y, cell[1] + 5)).is(Blocks.NETHERRACK), "A tunnel bored through his building's ground at y " + y);
        });
    }

    /**
     * A work found active on a load that carries no mark of the errand's crossing (the entity unloaded and loaded again in a running world)
     * is told nothing about the world: the plan's poll decides as before.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aWorkActiveOnAPlainLoadIsNotAnsweredAsLostToTheWorld(GameTestHelper h) {
        CompanionEntity saved = h.spawn(AICompanionMod.COMPANION.get(), 2, 2, 2);
        CompoundTag tag = new CompoundTag();
        saved.addAdditionalSaveData(tag);
        tag.putBoolean("WorkActive", true);
        tag.putLong("WorkSequence", 5);
        CompanionEntity reloaded = h.spawn(AICompanionMod.COMPANION.get(), 4, 2, 4);
        reloaded.readAdditionalSaveData(tag);
        var result = reloaded.workResult(5);
        h.assertTrue(result == null || !result.message().contains("выходом из мира"), "A world-load result made up for a plain load: " + result);
        h.succeed();
    }
}

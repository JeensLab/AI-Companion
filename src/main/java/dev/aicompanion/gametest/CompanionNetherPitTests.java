package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The errand's walk out of the Nether from the bottom of a pit (live game 15.11): the arena and the restored errand are those of
 * CompanionNetherErrandTests, in a batch of their own.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionNetherPitTests {
    private static final long MOST = 20 * 60 * 5;

    @AfterBatch(batch = "errand12")
    public static void afterErrand12(ServerLevel level) { cleanUp(level); }

    @AfterBatch(batch = "errand13")
    public static void afterErrand13(ServerLevel level) { cleanUp(level); }

    @AfterBatch(batch = "errand14")
    public static void afterErrand14(ServerLevel level) { cleanUp(level); }

    @AfterBatch(batch = "errand15")
    public static void afterErrand15(ServerLevel level) { cleanUp(level); }

    private static void cleanUp(ServerLevel level) {
        // The raised boxes and their portals would stay in the Nether for the tests that look for portals there: the other arenas are lower.
        var nether = level.getServer().getLevel(Level.NETHER);
        if (nether != null) for (ChunkPos chunk : CompanionNetherErrandTests.FORCED)
            for (BlockPos pos : BlockPos.betweenClosed(chunk.getMinBlockX(), 140, chunk.getMinBlockZ(), chunk.getMaxBlockX(), 165, chunk.getMaxBlockZ()))
                nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        CompanionNetherErrandTests.afterErrand(level);
    }

    /**
     * The quartz tunnels went down, and afterwards he stood in a pit seven deep with the exit portal at the top level aside: the walk out
     * logged «no leg found» for minutes and he stayed in the Nether for good. With no work the pit is dug out of as under any task
     * (route recovery), towards the portal he walks to.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000, batch = "errand12")
    public static void outOfADeepNetherrackPitHeWalksToThePortalAndCrosses(GameTestHelper h) {
        CompanionNetherErrandTests.ground(h);
        CompanionNetherErrandTests.lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = CompanionNetherErrandTests.arenaCell(h);
        pitArena(nether, cell[0], cell[1]);
        CompanionEntity there = CompanionNetherErrandTests.restored(h, nether, cell, null, MOST);
        there.moveTo(cell[0] + 5.5, 150, cell[1] + 3.5, 0, 0);
        crossesWithin(h, nether, there);
    }

    /**
     * Live game 15.11, the second stranding: the stairs cut out of the pit ended at the portal's level with four blocks of netherrack
     * still between him and its entry, and the walk out gave up after three tries of a minute each. With no pickaxe at all, he digs
     * those four by hand and crosses.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000, batch = "errand13")
    public static void walledOffFromThePortalByFourBlocksHeDigsByHandAndCrosses(GameTestHelper h) {
        CompanionNetherErrandTests.ground(h);
        CompanionNetherErrandTests.lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = CompanionNetherErrandTests.arenaCell(h);
        walledArena(nether, cell[0], cell[1], 7);
        CompanionEntity there = CompanionNetherErrandTests.restored(h, nether, cell, null, MOST);
        there.setItem(0, ItemStack.EMPTY);
        there.moveTo(cell[0] + .5, 151, cell[1] + 7.5, 0, 0);
        crossesWithin(h, nether, there);
    }

    /** The same with one block of rock left and the entry within four blocks: close by is no reason to stand there for good. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000, batch = "errand15")
    public static void oneBlockOfRockWithinFourBlocksOfTheEntryIsDugThroughToo(GameTestHelper h) {
        CompanionNetherErrandTests.ground(h);
        CompanionNetherErrandTests.lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = CompanionNetherErrandTests.arenaCell(h);
        walledArena(nether, cell[0], cell[1], 4);
        CompanionEntity there = CompanionNetherErrandTests.restored(h, nether, cell, null, MOST);
        there.setItem(0, ItemStack.EMPTY);
        there.moveTo(cell[0] + .5, 151, cell[1] + 4.5, 0, 0);
        crossesWithin(h, nether, there);
    }

    /**
     * What he digs in the Nether lies no deeper than two blocks below the exit portal's floor: the quartz five blocks lower and nearer
     * to him is left (the pits he could not climb out of came from tunnels like that), the one at the portal's level farther off is taken.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000, batch = "errand14")
    public static void quartzFarBelowTheExitPortalIsLeftAndTheOneAtItsLevelIsTaken(GameTestHelper h) {
        CompanionNetherErrandTests.ground(h);
        CompanionNetherErrandTests.lightPortal(h);
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        int[] cell = CompanionNetherErrandTests.arenaCell(h);
        BlockPos[] level = CompanionNetherErrandTests.arena(nether, cell[0], cell[1]);
        // The arena's ores come in pairs of sides: the near side goes, the far side keeps its three. Beside him a ledge a block below
        // the floor, and a basin four blocks below that, with the quartz five below the portal's floor in it.
        for (int i = 1; i < level.length; i += 2) nether.setBlockAndUpdate(level[i], Blocks.AIR.defaultBlockState());
        int floor = 149;
        for (int x = cell[0] + 3; x <= cell[0] + 7; x++) for (int z = cell[1] + 3; z <= cell[1] + 7; z++) {
            int bottom = x <= cell[0] + 5 ? floor - 1 : floor - 4;
            for (int y = bottom; y <= floor; y++) nether.setBlockAndUpdate(new BlockPos(x, y, z), (y == bottom ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState());
        }
        BlockPos deep = new BlockPos(cell[0] + 6, floor - 3, cell[1] + 5);
        nether.setBlockAndUpdate(deep, Blocks.NETHER_QUARTZ_ORE.defaultBlockState());
        CompanionEntity there = CompanionNetherErrandTests.restored(h, nether, cell, new BlockPos(cell[0], floor + 2, cell[1]).asLong(), MOST);
        UUID id = there.getUUID();
        boolean[] started = {false};
        h.onEachTick(() -> {
            if (started[0] || !CompanionNetherErrandTests.FORCED.stream().allMatch(chunk -> nether.areEntitiesLoaded(chunk.toLong()))) return;
            nether.addFreshEntity(there);
            var work = there.startWork("mine_ore", "minecraft:quartz_ore", 1);
            h.assertTrue(work.success() && there.activeWorkId() != 0, "No dig: " + work.message());
            started[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(started[0] && !there.hasActiveWorkOrder(), "Still digging: " + CompanionNetherErrandTests.describe(h, nether, id));
            h.assertTrue(nether.getBlockState(deep).is(Blocks.NETHER_QUARTZ_ORE), "He took the quartz far below the portal");
            int bag = 0, taken = 0;
            for (int slot = 0; slot < there.getContainerSize(); slot++) if (there.getItem(slot).is(Items.QUARTZ)) bag += there.getItem(slot).getCount();
            for (int i = 0; i < level.length; i += 2) if (!nether.getBlockState(level[i]).is(Blocks.NETHER_QUARTZ_ORE)) taken++;
            h.assertTrue(bag == 1 && taken == 1, "Nothing taken at the portal's level: bag " + bag + ", ores broken " + taken);
        });
    }

    /** Added to the Nether once its chunks are in; done when he is in the overworld again with no errand left. */
    private static void crossesWithin(GameTestHelper h, ServerLevel nether, CompanionEntity there) {
        UUID id = there.getUUID();
        boolean[] added = {false};
        h.onEachTick(() -> {
            if (!added[0] && CompanionNetherErrandTests.FORCED.stream().allMatch(chunk -> nether.areEntitiesLoaded(chunk.toLong()))) { nether.addFreshEntity(there); added[0] = true; }
        });
        h.succeedWhen(() -> {
            h.assertTrue(added[0] && h.getLevel().getEntity(id) instanceof CompanionEntity, "Still in the Nether: " + CompanionNetherErrandTests.describe(h, nether, id));
            CompanionEntity home = (CompanionEntity) h.getLevel().getEntity(id);
            CompoundTag tag = new CompoundTag();
            home.addAdditionalSaveData(tag);
            h.assertTrue(!tag.contains("Errand") && home.isFollowing() && nether.getEntity(id) == null, "The errand is not over: " + tag.get("Errand"));
        });
    }

    /**
     * The arena as a solid mass of netherrack, a room round the portal (its entry cell is in it), and a pocket two high {@code pocket}
     * blocks from the portal, at its level: seven is four blocks of rock between the pocket and the room, four is one.
     */
    static void walledArena(ServerLevel nether, int cx, int cz, int pocket) {
        CompanionNetherErrandTests.arena(nether, cx, cz);
        int floor = 149;
        for (int x = cx - 7; x <= cx + 7; x++) for (int z = cz - 7; z <= cz + 7; z++) for (int y = floor + 1; y <= floor + 8; y++)
            nether.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.NETHERRACK.defaultBlockState());
        for (int x = cx - 2; x <= cx + 3; x++) for (int z = cz - 2; z <= cz + 2; z++) for (int y = floor + 2; y <= floor + 6; y++)
            nether.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        for (int x = cx - 1; x <= cx + 2; x++) for (int y = floor + 1; y <= floor + 5; y++)
            if (x == cx - 1 || x == cx + 2 || y == floor + 1 || y == floor + 5) nether.setBlockAndUpdate(new BlockPos(x, y, cz), Blocks.OBSIDIAN.defaultBlockState());
        for (int x = cx; x <= cx + 1; x++) for (int y = floor + 2; y <= floor + 4; y++)
            nether.setBlockAndUpdate(new BlockPos(x, y, cz), Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
        for (int y = floor + 2; y <= floor + 3; y++) nether.setBlockAndUpdate(new BlockPos(cx, y, cz + pocket), Blocks.AIR.defaultBlockState());
    }

    /**
     * The arena as a solid mass of netherrack seven blocks thick with the portal let into its top, and a one-wide shaft down to the floor
     * three blocks and five aside from it: the only way up is to dig. The box is raised to make room.
     */
    private static void pitArena(ServerLevel nether, int cx, int cz) {
        CompanionNetherErrandTests.arena(nether, cx, cz);
        int floor = 149, top = floor + 7;
        for (int x = cx - 8; x <= cx + 8; x++) for (int z = cz - 8; z <= cz + 8; z++) for (int y = floor + 1; y <= floor + 16; y++) {
            boolean shell = x == cx - 8 || x == cx + 8 || z == cz - 8 || z == cz + 8 || y == floor + 16;
            nether.setBlockAndUpdate(new BlockPos(x, y, z), (shell ? Blocks.BEDROCK : y <= top ? Blocks.NETHERRACK : Blocks.AIR).defaultBlockState());
        }
        for (int x = cx - 1; x <= cx + 2; x++) for (int y = top; y <= top + 4; y++)
            if (x == cx - 1 || x == cx + 2 || y == top || y == top + 4) nether.setBlockAndUpdate(new BlockPos(x, y, cz), Blocks.OBSIDIAN.defaultBlockState());
        for (int x = cx; x <= cx + 1; x++) for (int y = top + 1; y <= top + 3; y++)
            nether.setBlockAndUpdate(new BlockPos(x, y, cz), Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
        for (int y = floor + 1; y <= top; y++) nether.setBlockAndUpdate(new BlockPos(cx + 5, y, cz + 3), Blocks.AIR.defaultBlockState());
    }
}

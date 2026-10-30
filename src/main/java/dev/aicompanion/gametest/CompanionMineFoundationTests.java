package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionChunkTicket;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionSeal;
import dev.aicompanion.entity.CompanionShaftPlan;
import dev.aicompanion.entity.CompanionShaftPlan.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The parts the diamond mine is built from: geometry, the mine record, sealing and the chunk ticket. The arena starts at Y -60. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineFoundationTests {
    private static final int SIZE = 32, TOP = 20;

    /** Twenty steps east and down: each column three cells high, the way straight, and the block under a stand never dug. */
    @GameTest(template = "marathon_cave")
    public static void stairColumnsAreStraightAndNeverUnderTheStand(GameTestHelper h) {
        BlockPos entrance = new BlockPos(0, 100, 0);
        List<BlockPos> stands = CompanionShaftPlan.stands(entrance, Direction.EAST, 20);
        Set<BlockPos> under = new HashSet<>();
        for (BlockPos stand : stands) under.add(stand.below());
        for (int k = 0; k < stands.size(); k++) {
            BlockPos stand = stands.get(k);
            h.assertTrue(stand.getZ() == 0 && stand.getX() == k && stand.getY() == 100 - k, "Stand " + k + " off the diagonal: " + stand);
            List<BlockPos> column = CompanionShaftPlan.column(stand);
            h.assertTrue(column.get(1).equals(stand.above()) && column.get(2).equals(stand.above(2)), "Column of " + k + " is not three high: " + column);
            for (BlockPos cell : column) h.assertTrue(!under.contains(cell), "Column cell " + cell + " is the floor of a stand");
            if (k == stands.size() - 1) continue;
            List<BlockPos> advance = CompanionShaftPlan.advanceCells(stand, Direction.EAST);
            h.assertTrue(new HashSet<>(advance).equals(new HashSet<>(CompanionShaftPlan.column(stands.get(k + 1)))), "Step " + k + " opens other cells than the next column: " + advance);
            h.assertTrue(advance.get(0).equals(stand.east()) && advance.get(1).equals(stand.east().below()), "Break order of step " + k + ": " + advance);
            h.assertTrue(CompanionShaftPlan.nextStand(stand, Direction.EAST).equals(stands.get(k + 1)), "Next stand of " + k);
        }
        h.succeed();
    }

    /** Water that can be seen from the stand is plugged first; water inside the rock cannot be, so the way is blocked. */
    @GameTest(template = "marathon_cave")
    public static void liquidIsSealedWhenSeenAndBlocksWhenHidden(GameTestHelper h) {
        BlockPos stand = buried(h);
        var level = h.getLevel();
        Set<BlockPos> own = Set.of();
        var clear = CompanionShaftPlan.assessStep(level, stand, Direction.EAST, own);
        h.assertTrue(clear.kind() == Kind.OK, "A plain step is " + clear);
        BlockPos seen = stand.offset(1, 2, 0); // above the head-height cell of the step: in the open column's line of sight
        level.setBlock(seen, Blocks.WATER.defaultBlockState(), 2 | 16);
        var seal = CompanionShaftPlan.assessStep(level, stand, Direction.EAST, own);
        h.assertTrue(seal.kind() == Kind.SEAL && seen.equals(seal.cell()), "Visible water beside the step: " + seal);
        level.setBlock(seen, Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
        var sealed = CompanionShaftPlan.assessStep(level, stand, Direction.EAST, own);
        h.assertTrue(sealed.kind() == Kind.OK, "Step after the seal: " + sealed);
        level.setBlock(seen, Blocks.GRAVEL.defaultBlockState(), 2 | 16);
        h.assertTrue(CompanionShaftPlan.assessStep(level, stand, Direction.EAST, own).kind() == Kind.BLOCKED, "Gravel over the head-height cell");
        level.setBlock(seen, Blocks.STONE.defaultBlockState(), 2 | 16);
        level.setBlock(stand.offset(1, 0, 1), Blocks.WATER.defaultBlockState(), 2 | 16);
        var hidden = CompanionShaftPlan.assessStep(level, stand, Direction.EAST, own);
        h.assertTrue(hidden.kind() == Kind.BLOCKED, "Water inside the rock: " + hidden);
        h.succeed();
    }

    /** East is blocked by hidden water and so is the left; the turn takes the free right side, and none when both are blocked. */
    @GameTest(template = "marathon_cave")
    public static void forcedTurnTakesTheFreePerpendicularSide(GameTestHelper h) {
        BlockPos stand = buried(h);
        var level = h.getLevel();
        level.setBlock(stand.offset(2, 0, 0), Blocks.WATER.defaultBlockState(), 2 | 16);
        level.setBlock(stand.offset(0, 0, -2), Blocks.LAVA.defaultBlockState(), 2 | 16);
        h.assertTrue(CompanionShaftPlan.assessStep(level, stand, Direction.EAST, Set.of()).kind() == Kind.BLOCKED, "East is not blocked");
        Direction turn = CompanionShaftPlan.chooseTurn(level, stand, Direction.EAST, Set.of());
        h.assertTrue(turn == Direction.SOUTH, "Turn from east with the north side blocked: " + turn);
        level.setBlock(stand.offset(0, 0, 2), Blocks.LAVA.defaultBlockState(), 2 | 16);
        h.assertTrue(CompanionShaftPlan.chooseTurn(level, stand, Direction.EAST, Set.of()) == null, "Both sides blocked still turns");
        h.succeed();
    }

    /** The tunnel is two wide and two high at diamond depth and the first one goes on in the direction of the stairs. */
    @GameTest(template = "marathon_cave")
    public static void tunnelCellsAreTwoByTwoAtDiamondDepth(GameTestHelper h) {
        h.assertTrue(CompanionShaftPlan.targetFeetY(-64) == -58, "Target feet Y for a -64 world: " + CompanionShaftPlan.targetFeetY(-64));
        h.assertTrue(CompanionShaftPlan.targetFeetY(0) == 6, "Target feet Y for a 0 world: " + CompanionShaftPlan.targetFeetY(0));
        BlockPos foot = h.absolutePos(new BlockPos(10, 2, 10));
        h.assertTrue(foot.getY() == -58, "Arena foot is at " + foot.getY());
        List<Direction> order = CompanionShaftPlan.tunnelOrder(Direction.EAST);
        h.assertTrue(order.equals(List.of(Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST)), "Tunnel order " + order);
        List<BlockPos> cells = CompanionShaftPlan.tunnelCells(foot, order.get(0));
        Set<Integer> xs = new HashSet<>(), ys = new HashSet<>(), zs = new HashSet<>();
        for (BlockPos cell : cells) { xs.add(cell.getX()); ys.add(cell.getY()); zs.add(cell.getZ()); }
        h.assertTrue(new HashSet<>(cells).size() == 4 && xs.equals(Set.of(foot.getX() + 1)) && ys.equals(Set.of(-58, -57)) && zs.size() == 2, "Not a 2x2 section: " + cells);
        h.assertTrue(zs.contains(foot.getZ()) && zs.contains(foot.getZ() + 1), "Lanes are not the stand line and its right: " + cells);
        h.succeed();
    }

    /** The record survives a save, and the stair is cut back to its open prefix when a stand's cell is filled in. */
    @GameTest(template = "marathon_cave")
    public static void mineSiteSavesAndVerifiesItsOpenPrefix(GameTestHelper h) {
        BlockPos entrance = ground(h);
        var level = h.getLevel();
        List<BlockPos> stands = CompanionShaftPlan.stands(entrance, Direction.EAST, 20);
        for (BlockPos stand : stands) for (BlockPos cell : CompanionShaftPlan.column(stand)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionMineSite site = new CompanionMineSite();
        site.start(entrance, Direction.EAST);
        for (BlockPos stand : stands.subList(1, stands.size())) site.reached(stand);
        site.openTunnel(Direction.EAST);
        for (int i = 0; i < 3; i++) site.extendTunnel();
        site.openTunnel(Direction.NORTH);
        site.extendTunnel();
        BlockPos seal = stands.get(5).below(), plug = stands.get(6).below();
        site.sealed(seal);
        site.plugged(plug);
        CompoundTag tag = new CompoundTag();
        site.save(tag);
        CompanionMineSite loaded = new CompanionMineSite();
        loaded.load(tag);
        h.assertTrue(loaded.exists() && entrance.equals(loaded.entrance()) && loaded.stairDirection() == Direction.EAST, "Entrance lost: " + loaded.entrance());
        h.assertTrue(loaded.stands().equals(site.stands()) && loaded.stands().size() == 20, "Stands differ: " + loaded.stands().size());
        h.assertTrue(loaded.tunnels().equals(site.tunnels()) && loaded.tunnels().size() == 2, "Tunnels differ: " + loaded.tunnels());
        h.assertTrue(loaded.seals().equals(site.seals()) && plug.equals(loaded.plug()), "Seals or plug differ");
        h.assertTrue(loaded.foot().equals(stands.get(20)), "Foot is " + loaded.foot());
        h.assertTrue(!new CompanionMineSite().exists() && !loadedFrom(new CompoundTag()).exists(), "A save without the key has a mine");
        h.assertTrue(loaded.verify(level) == 20 && loaded.tunnels().size() == 2, "An untouched mine was cut: " + loaded.stands().size());

        level.setBlock(stands.get(12).above(), Blocks.STONE.defaultBlockState(), 2 | 16);
        int kept = loaded.verify(level);
        h.assertTrue(kept == 11 && loaded.stands().size() == 11 && loaded.foot().equals(stands.get(11)), "Prefix after filling stand 12: " + kept);
        h.assertTrue(loaded.tunnels().isEmpty() && loaded.seals().equals(Set.of(seal)), "Tunnels hang on the foot: " + loaded.tunnels());

        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 2, TOP + 3, 2);
        companion.mineSite().start(entrance, Direction.EAST);
        for (BlockPos stand : stands.subList(1, 6)) companion.mineSite().reached(stand);
        CompoundTag saved = new CompoundTag();
        companion.saveWithoutId(saved);
        h.assertTrue(saved.contains(CompanionMineSite.KEY), "The companion does not save his mine");
        companion.mineSite().clear();
        companion.load(saved);
        h.assertTrue(companion.mineSite().exists() && companion.mineSite().stands().equals(stands.subList(1, 6)), "The companion lost his mine: " + companion.mineSite().stands().size());
        companion.mineSite().clear();
        CompoundTag empty = new CompoundTag();
        companion.saveWithoutId(empty);
        h.assertTrue(!empty.contains(CompanionMineSite.KEY), "A mine-less companion saves a mine");
        h.succeed();
    }

    /** One real block from the bag fills a lava cell; a cell in rock or out of reach takes nothing. */
    @GameTest(template = "marathon_cave")
    public static void sealConsumesOneCobblestoneAndPlugsLava(GameTestHelper h) {
        BlockPos stand = buried(h);
        var level = h.getLevel();
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 1, 1, 1);
        companion.setPos(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        companion.setItem(1, new ItemStack(Items.DIRT, 1));
        h.assertTrue(CompanionSeal.available(companion) == 4, "Sealing blocks: " + CompanionSeal.available(companion));
        BlockPos lava = stand.above(2);
        level.setBlock(lava, Blocks.LAVA.defaultBlockState(), 2 | 16);
        var result = CompanionSeal.place(companion, lava);
        h.assertTrue(result.placed(), "Lava not sealed: " + result);
        h.assertTrue(level.getBlockState(lava).is(Blocks.COBBLESTONE) && level.getFluidState(lava).isEmpty(), "Lava cell holds " + level.getBlockState(lava));
        h.assertTrue(companion.getItem(0).getCount() == 2 && companion.getItem(1).getCount() == 1, "Bag after one seal: " + companion.getStoredItemsSummary());
        var rock = CompanionSeal.place(companion, stand.south(2));
        h.assertTrue(rock == CompanionSeal.Result.OCCUPIED && companion.getItem(0).getCount() == 2, "Rock cell result " + rock);
        BlockPos far = stand.offset(0, 0, -8);
        level.setBlock(far, Blocks.LAVA.defaultBlockState(), 2 | 16);
        var tooFar = CompanionSeal.place(companion, far);
        h.assertTrue(tooFar == CompanionSeal.Result.OUT_OF_REACH && companion.getItem(0).getCount() == 2, "Far lava result " + tooFar);
        companion.setItem(0, ItemStack.EMPTY);
        companion.setItem(1, ItemStack.EMPTY);
        level.setBlock(lava, Blocks.LAVA.defaultBlockState(), 2 | 16);
        h.assertTrue(CompanionSeal.place(companion, lava) == CompanionSeal.Result.NO_BLOCK && CompanionSeal.available(companion) == 0, "An empty bag sealed");
        h.succeed();
    }

    /** The ticket follows him across a chunk border and leaves no forced chunk behind when released. */
    // Own batch: the level-wide check below must not see the tickets of expeditions running beside it.
    @GameTest(template = "marathon_cave", batch = "mine_chunk_ticket")
    public static void chunkTicketHoldsFollowsAndReleases(GameTestHelper h) {
        var level = h.getLevel();
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 4, 2, 4);
        var id = companion.getUUID();
        try {
            h.assertTrue(!CompanionChunkTicket.held(id), "Held before hold");
            boolean baseline = ForcedChunkManager.hasForcedChunks(level);
            h.assertTrue(CompanionChunkTicket.hold(companion) && CompanionChunkTicket.held(id), "Not held after hold");
            h.assertTrue(companion.chunkPosition().equals(CompanionChunkTicket.heldChunk(id)), "Wrong chunk held");
            h.assertTrue(ForcedChunkManager.hasForcedChunks(level) && entityTickets(level), "No forced chunk after hold");
            companion.setPos(companion.getX() + 32, companion.getY(), companion.getZ());
            CompanionChunkTicket.follow(companion);
            h.assertTrue(CompanionChunkTicket.held(id) && companion.chunkPosition().equals(CompanionChunkTicket.heldChunk(id)), "Ticket did not follow to " + companion.chunkPosition());
            CompanionChunkTicket.release(companion);
            h.assertTrue(!CompanionChunkTicket.held(id), "Held after release");
            // The GameTest harness force-loads its own arenas, so the level-wide answer is compared with the one before the hold.
            h.assertTrue(!entityTickets(level) && ForcedChunkManager.hasForcedChunks(level) == baseline, "A forced chunk is left after release; before hold: " + baseline);
        } finally {
            CompanionChunkTicket.release(id);
        }
        h.succeed();
    }

    /** An entrance is solid natural ground away from planks and chests. */
    @GameTest(template = "marathon_cave")
    public static void entranceKeepsAwayFromBuildings(GameTestHelper h) {
        BlockPos spot = ground(h);
        var level = h.getLevel();
        var clean = CompanionShaftPlan.chooseEntrance(level, spot, 8);
        h.assertTrue(clean != null && clean.feet().equals(spot), "Clean ground at " + spot + " gave " + clean);
        level.setBlock(spot.east(), Blocks.OAK_PLANKS.defaultBlockState(), 2 | 16);
        var moved = CompanionShaftPlan.chooseEntrance(level, spot, 8);
        h.assertTrue(moved != null && Math.max(Math.abs(moved.feet().getX() - spot.east().getX()), Math.abs(moved.feet().getZ() - spot.getZ())) > 2, "Entrance next to planks: " + moved);
        h.succeed();
    }

    private static boolean entityTickets(ServerLevel level) {
        var data = level.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        return data != null && !data.getEntityForcedChunks().isEmpty();
    }

    private static CompanionMineSite loadedFrom(CompoundTag tag) {
        CompanionMineSite site = new CompanionMineSite();
        site.load(tag);
        return site;
    }

    /** Stone below TOP, grass at TOP, air above; returns the feet cell on the surface. */
    private static BlockPos ground(GameTestHelper h) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y < 36; y++) {
            Block block = y < TOP ? Blocks.STONE : y == TOP ? Blocks.GRASS_BLOCK : Blocks.AIR;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
        return h.absolutePos(new BlockPos(6, TOP + 1, SIZE / 2));
    }

    /** A stand ten blocks under the surface with its three cells open, in solid stone. */
    private static BlockPos buried(GameTestHelper h) {
        ground(h);
        BlockPos stand = h.absolutePos(new BlockPos(10, 10, SIZE / 2));
        for (BlockPos cell : CompanionShaftPlan.column(stand)) h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        return stand;
    }
}

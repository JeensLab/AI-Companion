package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Where each player builds: sites of the blocks he set down himself, chests above all. Kept in the world's save and not in Oleg:
 * the base often stands before he is summoned, and he may be dead or unloaded while the player builds.
 */
public final class CompanionPlayerBuilds extends SavedData {
    private static final Factory<CompanionPlayerBuilds> FACTORY = new Factory<>(CompanionPlayerBuilds::new, CompanionPlayerBuilds::load, null);
    /** What is set down this close to the middle of a site belongs to it. */
    private static final int SITE_RADIUS = 16;
    /** One chest on the surface reaches it alone; plain blocks need a real small building, a bridge or a shaft of cobble stays below it. */
    static final int HOME_SCORE = 40;
    /** This much natural ground over a chest makes it a stash in a mine, not a home; a player's roof is not ground, and a dug-in base has its own blocks to count. */
    private static final int GROUND_COVER = 3;
    private static final int STATION_POINTS = 8;
    private static final int MAX_POINTS = 4096;
    private static final int MAX_OWNERS = 64;
    private static final int MAX_SITES = 8;
    private static final int MAX_CHESTS = 16;

    private static final class Site {
        final String dimension;
        long sumX, sumY, sumZ;
        int blocks, points;
        long lastBuilt;
        final List<BlockPos> chests = new ArrayList<>();
        final List<BlockPos> stashes = new ArrayList<>();

        Site(String dimension) { this.dimension = dimension; }

        /** Chests in a mine weigh as one station however many: a sorting room down there is still no home. */
        int score() { return points + chests.size() * HOME_SCORE + (stashes.isEmpty() ? 0 : STATION_POINTS); }
        BlockPos middle() {
            int n = Math.max(1, blocks);
            return new BlockPos((int)(sumX / n), (int)(sumY / n), (int)(sumZ / n));
        }
        boolean takesIn(BlockPos pos) {
            BlockPos middle = middle();
            return Math.abs(pos.getY() - middle.getY()) <= SITE_RADIUS
                    && Math.hypot(pos.getX() - middle.getX(), pos.getZ() - middle.getZ()) <= SITE_RADIUS;
        }
        void add(BlockPos pos, boolean chest, boolean buried, int worth, long now) {
            sumX += pos.getX(); sumY += pos.getY(); sumZ += pos.getZ(); blocks++;
            lastBuilt = now;
            List<BlockPos> kept = buried ? stashes : chests;
            if (chest) { if (kept.size() < MAX_CHESTS && !kept.contains(pos)) kept.add(pos.immutable()); }
            else points = Math.min(MAX_POINTS, points + worth);
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension);
            tag.putLong("SumX", sumX); tag.putLong("SumY", sumY); tag.putLong("SumZ", sumZ);
            tag.putInt("Blocks", blocks); tag.putInt("Points", points); tag.putLong("LastBuilt", lastBuilt);
            tag.put("Chests", new LongArrayTag(chests.stream().mapToLong(BlockPos::asLong).toArray()));
            tag.put("Stashes", new LongArrayTag(stashes.stream().mapToLong(BlockPos::asLong).toArray()));
            return tag;
        }
        static Site load(CompoundTag tag) {
            Site site = new Site(tag.getString("Dimension"));
            site.sumX = tag.getLong("SumX"); site.sumY = tag.getLong("SumY"); site.sumZ = tag.getLong("SumZ");
            site.blocks = Math.max(0, tag.getInt("Blocks"));
            site.points = Math.clamp(tag.getInt("Points"), 0, MAX_POINTS);
            site.lastBuilt = tag.getLong("LastBuilt");
            long[] chests = tag.getLongArray("Chests");
            for (int i = 0; i < Math.min(MAX_CHESTS, chests.length); i++) site.chests.add(BlockPos.of(chests[i]));
            long[] stashes = tag.getLongArray("Stashes");
            for (int i = 0; i < Math.min(MAX_CHESTS, stashes.length); i++) site.stashes.add(BlockPos.of(stashes[i]));
            return site;
        }
    }

    private final Map<UUID, List<Site>> owners = new LinkedHashMap<>();

    public static CompanionPlayerBuilds get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "aicompanion_player_builds");
    }

    /** A block the player set down himself. Filling a hole, a pillar of soil and torches in a mine say nothing about a home. */
    void record(UUID player, ServerLevel level, BlockPos pos, BlockState state) {
        boolean chest = state.getBlock() instanceof ChestBlock;
        if (!chest && (filler(state) || state.getCollisionShape(level, pos).isEmpty())) return;
        String dimension = level.dimension().location().toString();
        List<Site> sites = owners.remove(player);
        if (sites == null) sites = new ArrayList<>();
        owners.put(player, sites); // the newest builder last: the oldest goes first below
        Site site = sites.stream().filter(known -> known.dimension.equals(dimension) && known.takesIn(pos))
                .min(Comparator.comparingDouble(known -> known.middle().distSqr(pos))).orElse(null);
        if (site == null) {
            site = new Site(dimension);
            sites.add(site);
        }
        site.add(pos, chest, chest && buried(level, pos), station(state) ? STATION_POINTS : 1, level.getGameTime());
        Site kept = site;
        while (sites.size() > MAX_SITES) sites.stream().filter(other -> other != kept).min(Comparator.comparingInt(Site::score)).ifPresent(sites::remove);
        while (owners.size() > MAX_OWNERS) owners.remove(owners.keySet().iterator().next());
        setDirty();
    }

    private static boolean buried(ServerLevel level, BlockPos pos) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        int cover = 0;
        for (BlockPos.MutableBlockPos at = pos.mutable(); at.move(0, 1, 0).getY() < top && cover < GROUND_COVER; )
            if (level.getBlockState(at).is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)) cover++;
        return cover >= GROUND_COVER;
    }

    private static boolean filler(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.NETHERRACK);
    }

    private static boolean station(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof BedBlock || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE);
    }

    /** His home in this dimension: the site with the most behind it, its first chest if it has one; null while nothing reaches a home. */
    BlockPos home(UUID player, ServerLevel level) {
        Site best = here(player, level).stream().filter(site -> site.score() >= HOME_SCORE)
                .max(Comparator.comparingInt(Site::score).thenComparingLong(site -> site.lastBuilt)).orElse(null);
        if (best == null) return null;
        return !best.chests.isEmpty() ? best.chests.getFirst() : !best.stashes.isEmpty() ? best.stashes.getFirst() : best.middle();
    }

    /** The chests he set down in this dimension, as far as the loaded world still shows them. */
    List<BlockPos> chests(UUID player, ServerLevel level) {
        return here(player, level).stream().flatMap(site -> Stream.concat(site.chests.stream(), site.stashes.stream())).toList();
    }

    /** His buildings in this dimension (sites that reach a home), as a test of a cell: what lies there is his (CompanionObsidian leaves it). Taken once for many cells. */
    java.util.function.Predicate<BlockPos> sites(UUID player, ServerLevel level) {
        // A few cobblestones set by a lava flow are no building: they hid the lava from him (second review 08.11).
        List<Site> here = here(player, level).stream().filter(site -> site.score() >= HOME_SCORE).toList();
        return pos -> here.stream().anyMatch(site -> site.takesIn(pos));
    }

    private List<Site> here(UUID player, ServerLevel level) {
        List<Site> sites = owners.get(player);
        if (sites == null) return List.of();
        String dimension = level.dimension().location().toString();
        // Not ||: both lists are pruned every time.
        for (Site site : sites) if (site.dimension.equals(dimension) && (site.chests.removeIf(pos -> gone(level, pos)) | site.stashes.removeIf(pos -> gone(level, pos)))) setDirty();
        return sites.stream().filter(site -> site.dimension.equals(dimension)).toList();
    }

    private static boolean gone(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && !(level.getBlockState(pos).getBlock() instanceof ChestBlock);
    }

    private static CompanionPlayerBuilds load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanionPlayerBuilds data = new CompanionPlayerBuilds();
        ListTag saved = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_OWNERS, saved.size()); i++) {
            CompoundTag entry = saved.getCompound(i);
            if (!entry.hasUUID("Id")) continue;
            List<Site> sites = new ArrayList<>();
            ListTag list = entry.getList("Sites", Tag.TAG_COMPOUND);
            for (int j = 0; j < Math.min(MAX_SITES, list.size()); j++) sites.add(Site.load(list.getCompound(j)));
            data.owners.put(entry.getUUID("Id"), sites);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag saved = new ListTag();
        owners.forEach((id, sites) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            ListTag list = new ListTag();
            sites.forEach(site -> list.add(site.save()));
            entry.put("Sites", list);
            saved.add(entry);
        });
        tag.put("Owners", saved);
        return tag;
    }
}

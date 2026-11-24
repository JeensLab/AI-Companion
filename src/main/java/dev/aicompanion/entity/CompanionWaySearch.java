package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The one search for a block the player asked for (idea PATH7, #105: «для любой цели в мире, а не для отдельных предметов»):
 * what lies about him that is the item or gives it when broken, wherever it is in the loaded area about him — hanging in the
 * air exactly like lying on the ground, under his feet or over his head, and never a list of names. The blocks are the game's
 * own: a block whose item is the item itself, and a block whose drop is the item (CompanionPickable's own tables). The nearest
 * are asked of the one geometry answer (CompanionReach): walked to, taken from under his feet, up a column of his own soil or
 * across a floor he lays — so a block no walk reaches is still his when a column or a bridge is there.
 *
 * The box is the loaded area about him: {@link #RADIUS} blocks across and {@link #DEPTH} above and below, every section of it,
 * not a small box at his feet. One line of the journal per search says the box, how many blocks it found, which one was chosen
 * and, when none, that it looked and found nothing — «искал в радиусе 32, ничего».
 */
final class CompanionWaySearch {
    /**
     * The box searched: this many blocks across — never more than the leash and scan his own work is set to — and this many
     * above and below him. A block farther is another place's, not this order's.
     */
    static final int RADIUS = 32, DEPTH = 32;
    /** How many of the nearest blocks the way is asked about: a few dozen is every order's, and a scan of all is not. */
    private static final int LOOK = 24;
    /** The reach a block is worked from, as the one geometry answer's own (CompanionReach). */
    private static final double REACH = 3.5D;

    /** A block that gives the item: where it lies, how much one of it gives, and the way to it. */
    record Found(BlockPos pos, int yield, CompanionReach.How how) { }

    /**
     * What the search found: how many blocks about him give the item, how many of them a way reaches, how much they give, the
     * soil a column still asks, and the nearest block with its way (null when no way reaches any of them).
     */
    record Result(int candidates, int looked, int count, int soil, Found nearest) {
        boolean some() { return count > 0; }
    }

    private CompanionWaySearch() { }

    /**
     * Every block about him that gives this item, nearest first, and how much of it the ones a way reaches give. {@code mine}:
     * whether a block there is his to take at all (the player's builds, a portal's frame, lava beside it: the caller's own rule).
     */
    static Result search(CompanionEntity c, ServerLevel level, Item item, Predicate<BlockPos> mine) {
        Map<Block, Integer> gives = giving(level, item);
        if (gives.isEmpty()) return journal(c, level, item, List.<Found>of(), 0, 0, 0);
        List<BlockPos> found = scan(c, level, gives, mine);
        found.sort(Comparator.comparingDouble(pos -> c.distanceToSqr(pos.getCenter())));
        int looked = Math.min(found.size(), LOOK);
        List<Found> reached = new ArrayList<>();
        int count = 0, soil = 0;
        for (int index = 0; index < looked; index++) {
            BlockPos pos = found.get(index);
            CompanionReach.How how = CompanionReach.how(c, level, pos, REACH, true, true, CompanionPillar.WAY_SUPPORTS);
            if (!how.yes()) continue;
            int yield = gives.getOrDefault(level.getBlockState(pos).getBlock(), 1);
            reached.add(new Found(pos, yield, how));
            count += yield;
            if (how.up()) soil = Math.max(soil, Math.max(0, how.steps() - c.temporaryPillar.soilCarried()));
            if (how.bridge()) soil = Math.max(soil, Math.max(0, how.gap() - CompanionReach.bridgeBlocks(c)));
        }
        if (soil > 0 && !CompanionReach.soilAbout(c, level)) soil = 0;
        return journal(c, level, item, reached, found.size(), count, soil);
    }

    /** The nearest block about him that is the item or gives it, no way asked: the real distance a refusal names. */
    static BlockPos nearestBlock(CompanionEntity c, ServerLevel level, Item item, Predicate<BlockPos> mine) {
        Map<Block, Integer> gives = giving(level, item);
        if (gives.isEmpty()) return null;
        List<BlockPos> found = scan(c, level, gives, mine);
        return found.stream().min(Comparator.comparingDouble(pos -> c.distanceToSqr(pos.getCenter()))).orElse(null);
    }

    /** How much of the item the world about him really gives by the one geometry answer: the count and the soil a column asks. */
    static CompanionReach.Available available(CompanionEntity c, ServerLevel level, Item item, Predicate<BlockPos> mine) {
        Result result = search(c, level, item, mine);
        return new CompanionReach.Available(result.count(), result.soil());
    }

    /** The blocks about him that are the item or give it: the game's own block-to-item and drop tables, never a list of names. */
    private static Map<Block, Integer> giving(ServerLevel level, Item item) {
        Map<Block, Integer> gives = new LinkedHashMap<>();
        // What a block gives when broken (sugar cane, a flower, clay: the picking's own table), and the blocks that ARE the
        // item (a planks block, a lapis block, a pumpkin), counted once each.
        for (CompanionPickable.Source source : CompanionPicking.allowed(level, CompanionPickable.sources(level, item)))
            gives.put(source.block(), source.yield());
        for (CompanionPickable.Source source : CompanionPickable.identity(level, item))
            gives.putIfAbsent(source.block(), source.yield());
        return gives;
    }

    /** Every block of these kinds in the box about him: the loaded chunks only, each section's palette saying which hold one. */
    private static List<BlockPos> scan(CompanionEntity c, ServerLevel level, Map<Block, Integer> gives, Predicate<BlockPos> mine) {
        BlockPos center = c.blockPosition();
        int radius = Math.min(RADIUS, c.getSearchRadius());
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - DEPTH);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + DEPTH);
        double far = (double) radius * radius;
        Predicate<BlockState> kind = state -> gives.containsKey(state.getBlock());
        List<BlockPos> found = new ArrayList<>();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++)
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++)
                for (int index = level.getSectionIndex(minY); index <= level.getSectionIndex(maxY); index++) {
                    var chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    var section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(kind)) continue;
                    int bottom = level.getSectionYFromSectionIndex(index) << 4;
                    for (int y = Math.max(minY, bottom); y <= Math.min(maxY, bottom + 15); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                        if (!kind.test(section.getBlockState(x, y & 15, z))) continue;
                        BlockPos cell = new BlockPos((cx << 4) + x, y, (cz << 4) + z);
                        if (cell.distSqr(center) > far || !c.errand.within(cell) || !mine.test(cell)) continue;
                        found.add(cell.immutable());
                    }
                }
        return found;
    }

    /** One line of the journal per search: the box, what it found, the nearest one chosen, or that it found nothing. */
    private static Result journal(CompanionEntity c, ServerLevel level, Item item, List<Found> reached, int candidates, int count, int soil) {
        Found nearest = reached.isEmpty() ? null : reached.getFirst();
        int radius = Math.min(RADIUS, c.getSearchRadius());
        String why = count > 0 ? "" : candidates == 0 ? "искал в радиусе " + radius + ", ничего"
                : "вижу " + candidates + ", ни к одному нет пути";
        Object[] fields = {"entity", c.getUUID(), "item", BuiltInRegistries.ITEM.getKey(item), "radius", radius, "depth", DEPTH,
                "candidates", candidates, "looked", Math.min(candidates, LOOK), "count", count, "soil", soil,
                "chosen", nearest == null ? "" : nearest.pos().toShortString(),
                "id", nearest == null ? "" : BuiltInRegistries.BLOCK.getKey(level.getBlockState(nearest.pos()).getBlock()),
                "shape", nearest == null ? "none" : nearest.how().shape(), "why", why};
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), "way_search", fields);
        return new Result(candidates, Math.min(candidates, LOOK), count, soil, nearest);
    }
}

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
 * not a small box at his feet — a cylinder, so a block far to the side and high up is in it as one at his own level is. One line
 * per search goes to the game log as well as the journal: the box, how many blocks it found, the nearest three with their
 * distance up and across and the way's answer for each, which one was chosen and, when none, the reason with its numbers —
 * «искал в радиусе 32, ничего». A block of the item that exists but lies outside the box is said as its own line: that is the
 * difference between «нет блока» and «блок есть, но не вижу».
 */
final class CompanionWaySearch {
    /**
     * The box searched: this many blocks across — never more than the leash and scan his own work is set to — and this many
     * above and below him. A block farther is another place's, not this order's.
     */
    static final int RADIUS = 32, DEPTH = 32;
    /** How many of the nearest blocks the way is asked about: a few dozen is every order's, and a scan of all is not. */
    private static final int LOOK = 24;
    /** How many of the nearest are named in the line: enough to read why each was refused, few enough for one line. */
    private static final int SHOWN = 3;
    /** The reach a block is worked from, as the one geometry answer's own (CompanionReach). */
    private static final double REACH = 3.5D;
    /** The wider box asked only when the box holds nothing: whether a block of the item is there at all, just not seen here. */
    private static final int BEYOND = 64;

    /** A block that gives the item: where it lies, how much one of it gives, and the way to it. */
    record Found(BlockPos pos, int yield, CompanionReach.How how) { }

    /** One block the way was asked about, as the line names it: where it lies and what the answer was. */
    private record Examined(BlockPos pos, CompanionReach.How how) { }

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
        if (gives.isEmpty()) return journal(c, level, item, List.<Found>of(), List.<Examined>of(), 0, 0, 0, List.<BlockPos>of());
        List<BlockPos> found = scan(c, level, gives, mine, Math.min(RADIUS, c.getSearchRadius()), DEPTH);
        found.sort(Comparator.comparingDouble(pos -> c.distanceToSqr(pos.getCenter())));
        int looked = Math.min(found.size(), LOOK);
        List<Found> reached = new ArrayList<>();
        List<Examined> examined = new ArrayList<>();
        int count = 0, soil = 0;
        for (int index = 0; index < looked; index++) {
            BlockPos pos = found.get(index);
            CompanionReach.How how = CompanionReach.how(c, level, pos, REACH, true, true, CompanionPillar.WAY_SUPPORTS);
            if (examined.size() < SHOWN) examined.add(new Examined(pos, how));
            if (!how.yes()) continue;
            int yield = gives.getOrDefault(level.getBlockState(pos).getBlock(), 1);
            reached.add(new Found(pos, yield, how));
            count += yield;
            if (how.up()) soil = Math.max(soil, Math.max(0, how.steps() - c.temporaryPillar.soilCarried()));
            if (how.bridge()) soil = Math.max(soil, Math.max(0, how.gap() - CompanionReach.bridgeBlocks(c)));
        }
        if (soil > 0 && !CompanionReach.soilAbout(c, level)) soil = 0;
        // Nothing in the box: a block of the item farther out is said as its own line — the block exists, it is just not seen.
        List<BlockPos> beyond = found.isEmpty() ? beyond(c, level, gives, mine) : List.of();
        return journal(c, level, item, reached, examined, found.size(), count, soil, beyond);
    }

    /** The nearest block about him that is the item or gives it, no way asked: the real distance a refusal names. */
    static BlockPos nearestBlock(CompanionEntity c, ServerLevel level, Item item, Predicate<BlockPos> mine) {
        Map<Block, Integer> gives = giving(level, item);
        if (gives.isEmpty()) return null;
        List<BlockPos> found = scan(c, level, gives, mine, Math.min(RADIUS, c.getSearchRadius()), DEPTH);
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

    /**
     * Every block of these kinds in the box about him: the loaded chunks only, each section's palette saying which hold one.
     * The box is the one the line names — {@code radius} across, measured flat, so a block far to the side and high up is in
     * it as one at his own level is, and {@code depth} above and below his feet.
     */
    private static List<BlockPos> scan(CompanionEntity c, ServerLevel level, Map<Block, Integer> gives, Predicate<BlockPos> mine,
                                       int radius, int depth) {
        BlockPos center = c.blockPosition();
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - depth);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + depth);
        long far = (long) radius * radius;
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
                        long dx = cell.getX() - center.getX(), dz = cell.getZ() - center.getZ();
                        if (dx * dx + dz * dz > far || Math.abs(cell.getY() - center.getY()) > depth
                                || !c.errand.within(cell) || !mine.test(cell)) continue;
                        found.add(cell.immutable());
                    }
                }
        return found;
    }

    /** The nearest blocks of the item outside the box, nearest first, at most the three the line names: the box held none. */
    private static List<BlockPos> beyond(CompanionEntity c, ServerLevel level, Map<Block, Integer> gives, Predicate<BlockPos> mine) {
        BlockPos center = c.blockPosition();
        int radius = Math.min(RADIUS, c.getSearchRadius());
        List<BlockPos> wider = scan(c, level, gives, mine, Math.max(BEYOND, radius + 1), DEPTH);
        wider.removeIf(pos -> {
            long dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
            return dx * dx + dz * dz <= (long) radius * radius && Math.abs(pos.getY() - center.getY()) <= DEPTH;
        });
        wider.sort(Comparator.comparingDouble(pos -> c.distanceToSqr(pos.getCenter())));
        return wider.size() > SHOWN ? List.copyOf(wider.subList(0, SHOWN)) : List.copyOf(wider);
    }

    /**
     * One line per search, written to the game log as well as the journal: the box, what it found, the nearest three with their
     * distance up and across and the way's answer, the one chosen, or the reason with its numbers — and, when the box held
     * nothing, whether a block of the item exists just outside it.
     */
    private static Result journal(CompanionEntity c, ServerLevel level, Item item, List<Found> reached, List<Examined> examined,
                                  int candidates, int count, int soil, List<BlockPos> beyond) {
        Found nearest = reached.isEmpty() ? null : reached.getFirst();
        int radius = Math.min(RADIUS, c.getSearchRadius());
        String why = count > 0 ? "" : candidates == 0 ? "искал в радиусе " + radius + ", ничего"
                : "вижу " + candidates + ", ни к одному нет пути";
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        StringBuilder text = new StringBuilder("«").append(id).append("»: коробка ").append(radius).append(" вбок, ")
                .append(DEPTH).append(" вверх и ").append(DEPTH).append(" вниз")
                .append(radius < RADIUS ? " (урезана моим поиском " + c.getSearchRadius() + ")" : "").append("; ");
        if (candidates == 0) text.append("блоков не нашёл");
        else {
            text.append("нашёл ").append(candidates).append(", осмотрел ").append(Math.min(candidates, LOOK))
                    .append(", с путём ").append(reached.size()).append(" (даёт ").append(count).append("), земля ").append(soil);
            if (!examined.isEmpty()) {
                text.append("; ближайшие: ");
                for (int index = 0; index < examined.size(); index++) {
                    if (index > 0) text.append("; ");
                    text.append(describe(c, examined.get(index).pos(), examined.get(index).how()));
                }
            }
        }
        if (nearest == null) text.append("; выбран: нет — ").append(why.isEmpty() ? "блоков этого предмета нет" : why);
        else text.append("; выбран ").append(describe(c, nearest.pos(), nearest.how()));
        Object[] fields = {"entity", c.getUUID(), "item", id, "radius", radius, "depth", DEPTH,
                "candidates", candidates, "looked", Math.min(candidates, LOOK), "count", count, "soil", soil,
                "chosen", nearest == null ? "" : nearest.pos().toShortString(),
                "id", nearest == null ? "" : BuiltInRegistries.BLOCK.getKey(level.getBlockState(nearest.pos()).getBlock()),
                "shape", nearest == null ? "none" : nearest.how().shape(), "why", why};
        dev.aicompanion.CompanionDiagnostics.say(c.diagnosticTrace(), "way_search", text.toString(), fields);
        if (!beyond.isEmpty()) {
            BlockPos first = beyond.getFirst();
            String out = "«" + id + "»: за коробкой " + radius + " вбок есть " + beyond.size() + ", ближайший "
                    + describe(c, first, CompanionReach.how(c, level, first, REACH, true, true, CompanionPillar.WAY_SUPPORTS))
                    + " — он вне коробки, отсюда не вижу его; подойди ближе или скажи искать дальше";
            dev.aicompanion.CompanionDiagnostics.say(c.diagnosticTrace(), "way_search", out,
                    "entity", c.getUUID(), "item", id, "radius", radius, "outside", beyond.size(),
                    "outPos", first.toShortString(), "outRise", first.getY() - c.blockPosition().getY(),
                    "outAcross", Math.round(Math.hypot(first.getX() + .5D - c.getX(), first.getZ() + .5D - c.getZ()) * 10) / 10D);
        }
        return new Result(candidates, Math.min(candidates, LOOK), count, soil, nearest);
    }

    /** «(20, 11, 16) +10 вверх/20.0 вбок → column 7 шагов земли»: where a block lies and what the way answered, as one reads it. */
    private static String describe(CompanionEntity c, BlockPos pos, CompanionReach.How how) {
        BlockPos feet = c.blockPosition();
        int rise = pos.getY() - feet.getY();
        double across = Math.hypot(pos.getX() + .5D - c.getX(), pos.getZ() + .5D - c.getZ());
        String where = pos.toShortString() + " " + (rise >= 0 ? "+" + rise : String.valueOf(rise)) + " вверх/"
                + Math.round(across * 10) / 10D + " вбок";
        if (!how.yes()) return where + " → нет (" + how.why() + ")";
        return where + " → " + how.shape()
                + (how.steps() > 0 ? " " + how.steps() + " шагов земли" : how.gap() > 0 ? " " + how.gap() + " клеток пола" : "");
    }
}

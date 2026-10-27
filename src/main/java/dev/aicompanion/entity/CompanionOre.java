package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Ore like a player mines it: the right pickaxe, only ore he sees or saw earlier (never through stone), a short tunnel
 * when it is out of reach on foot, never a shaft under the feet.
 * Kinds, their real drops and pickaxe level come from the game tags; nothing here moves the companion.
 */
public final class CompanionOre {
    public enum Kind {
        COAL("уголь", "угольная руда", "угля", BlockTags.COAL_ORES, Blocks.COAL_ORE, Items.COAL, Items.WOODEN_PICKAXE),
        IRON("железо", "железная руда", "железа", BlockTags.IRON_ORES, Blocks.IRON_ORE, Items.RAW_IRON, Items.STONE_PICKAXE),
        COPPER("медь", "медная руда", "меди", BlockTags.COPPER_ORES, Blocks.COPPER_ORE, Items.RAW_COPPER, Items.STONE_PICKAXE),
        LAPIS("лазурит", "лазуритовая руда", "лазурита", BlockTags.LAPIS_ORES, Blocks.LAPIS_ORE, Items.LAPIS_LAZULI, Items.STONE_PICKAXE),
        GOLD("золото", "золотая руда", "золота", BlockTags.GOLD_ORES, Blocks.GOLD_ORE, Items.RAW_GOLD, Items.IRON_PICKAXE),
        REDSTONE("редстоун", "редстоуновая руда", "редстоуна", BlockTags.REDSTONE_ORES, Blocks.REDSTONE_ORE, Items.REDSTONE, Items.IRON_PICKAXE),
        DIAMOND("алмазы", "алмазная руда", "алмазов", BlockTags.DIAMOND_ORES, Blocks.DIAMOND_ORE, Items.DIAMOND, Items.IRON_PICKAXE),
        EMERALD("изумруды", "изумрудная руда", "изумрудов", BlockTags.EMERALD_ORES, Blocks.EMERALD_ORE, Items.EMERALD, Items.IRON_PICKAXE);

        public final String word;
        public final String oreName;
        /** «железа», «угля»: for «накопал железа». */
        public final String genitive;
        final TagKey<Block> blocks;
        final Block sample;
        public final Item drop;
        public final Item pickaxe;
        Kind(String word, String oreName, String genitive, TagKey<Block> blocks, Block sample, Item drop, Item pickaxe) {
            this.word = word; this.oreName = oreName; this.genitive = genitive; this.blocks = blocks; this.sample = sample; this.drop = drop; this.pickaxe = pickaxe;
        }
        boolean is(BlockState state) { return state.is(blocks); }
        /** The kind of this ore block (deepslate and nether variants too), or null for anything else. */
        static Kind of(BlockState state) {
            for (Kind kind : values()) if (kind.is(state)) return kind;
            return null;
        }
        /** Nether gold ore breaks with any pickaxe; the iron one is for the overworld gold (review 22.10, М9). */
        private boolean netherGold(net.minecraft.world.level.Level level) { return this == GOLD && level.dimension() == net.minecraft.world.level.Level.NETHER; }
        Block sampleIn(net.minecraft.world.level.Level level) { return netherGold(level) ? Blocks.NETHER_GOLD_ORE : sample; }
        Item pickaxeIn(net.minecraft.world.level.Level level) { return netherGold(level) ? Items.WOODEN_PICKAXE : pickaxe; }
        public String key() { return name().toLowerCase(Locale.ROOT); }

        /**
         * The ore the model or player named: a kind word, the ore block, its raw drop or the smelted result.
         * Null for anything else, so a wrong id never starts digging.
         */
        public static Kind of(String item) {
            if (item == null) return null;
            String id = item.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
            id = id.replaceFirst("^deepslate_", "").replaceFirst("^raw_", "").replaceFirst("_ore$", "")
                    .replaceFirst("_ingot$", "").replaceFirst("^nether_", "");
            return switch (id) {
                case "coal" -> COAL;
                case "iron" -> IRON;
                case "copper" -> COPPER;
                case "lapis", "lapis_lazuli" -> LAPIS;
                case "gold" -> GOLD;
                case "redstone" -> REDSTONE;
                case "diamond", "diamonds" -> DIAMOND;
                case "emerald", "emeralds" -> EMERALD;
                default -> null;
            };
        }
    }

    /** How far below the stand a tunnel may lead, and how far sideways from it. */
    static final int MAX_DROP = 8;
    static final int MAX_REACH = 10;
    static final int MAX_CELLS = 30;
    private CompanionOre() { }

    /** Natural ground a tunnel may go through: stone of the world, soil and any ore. Built blocks are never dug. */
    static boolean tunnelable(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || CompanionQuarry.soil(state)
                || state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES);
    }

    /** Water or lava next to the ore: digging it would let the liquid in. */
    static boolean liquidNear(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) if (!level.getFluidState(pos.relative(side)).isEmpty()) return true;
        return !level.getFluidState(pos).isEmpty();
    }

    /** A cell of the own tunnel: no liquid, sand or gravel around, no chest or furnace beside it. */
    static boolean safeTunnelCell(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return false;
        if (!level.getFluidState(pos).isEmpty() || level.getBlockEntity(pos) != null) return false;
        for (Direction side : Direction.values()) {
            BlockPos near = pos.relative(side);
            if (!level.hasChunkAt(near) || !level.getFluidState(near).isEmpty() || level.getBlockEntity(near) != null
                    || level.getBlockState(near).is(Blocks.CRAFTING_TABLE) || CompanionBuildings.crafted(level.getBlockState(near))) return false;
            if (side != Direction.DOWN && level.getBlockState(near).getBlock() instanceof FallingBlock) return false;
        }
        return true;
    }

    /**
     * A player's tunnel from a standing cell to the ore: first stairs down (one block sideways per block down, three
     * cells high so the way back up is a walk), then two-high straight to the cell beside the ore. The ore ends up at
     * feet or head height right in front. Cells already open are skipped. Null when the way is unsafe or too long.
     */
    static List<BlockPos> tunnel(ServerLevel level, BlockPos stand, BlockPos ore) {
        int foot = ore.getY() <= stand.getY() ? ore.getY() : ore.getY() - 1;
        if (foot > stand.getY() || stand.getY() - foot > MAX_DROP) return null;
        List<BlockPos> cells = new ArrayList<>();
        BlockPos at = stand;
        for (int step = 0; step < MAX_REACH + MAX_DROP; step++) {
            int dx = ore.getX() - at.getX(), dz = ore.getZ() - at.getZ();
            if (at.getY() == foot && Math.abs(dx) + Math.abs(dz) == 1) return cells.size() > MAX_CELLS ? null : List.copyOf(cells);
            // Each step must leave the ore in front, not underfoot: enough sideways room for the remaining descent.
            if (Math.abs(dx) + Math.abs(dz) <= at.getY() - foot) return null;
            Direction direction = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            boolean down = at.getY() > foot;
            BlockPos next = at.relative(direction).below(down ? 1 : 0);
            if (!level.getBlockState(next.below()).isFaceSturdy(level, next.below(), Direction.UP)) return null;
            BlockPos[] body = down ? new BlockPos[]{next.above(2), next.above(), next} : new BlockPos[]{next.above(), next};
            for (BlockPos cell : body) {
                BlockState state = level.getBlockState(cell);
                if (state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()) continue;
                if (cell.equals(ore) || !tunnelable(state) || !safeTunnelCell(level, cell)) return null;
                cells.add(cell.immutable());
            }
            at = next;
        }
        return null;
    }

    /** Where a tunnel to this ore could start: standing cells above or level with it, nearest digging first. */
    static List<BlockPos> stands(CompanionEntity owner, BlockPos ore) {
        List<BlockPos> result = new ArrayList<>();
        for (int dy = -1; dy <= MAX_DROP; dy++) for (int dx = -MAX_REACH; dx <= MAX_REACH; dx++) for (int dz = -MAX_REACH; dz <= MAX_REACH; dz++) {
            int sideways = Math.abs(dx) + Math.abs(dz);
            BlockPos stand = ore.offset(dx, dy, dz);
            int drop = stand.getY() - (ore.getY() <= stand.getY() ? ore.getY() : ore.getY() - 1);
            if (sideways < 1 || sideways > MAX_REACH + MAX_DROP || sideways <= drop) continue;
            if (CompanionMiningNavigation.standable(owner, stand)) result.add(stand.immutable());
        }
        // Fewest cells to dig first: sideways steps plus the extra third cell of each step down.
        result.sort(Comparator.comparingInt((BlockPos stand) -> Math.abs(stand.getX() - ore.getX()) + Math.abs(stand.getZ() - ore.getZ())
                + Math.max(0, stand.getY() - ore.getY())).thenComparingDouble(stand -> stand.distSqr(owner.blockPosition())));
        return result;
    }

    /**
     * Incremental search of loaded sections for one kind of ore around the start, nearest first. Like a player without x-ray:
     * only ore he sees now (CompanionSight) or saw earlier, never ore buried in stone.
     */
    static final class Search {
        private record Section(int cx, int cz, int index) { }
        private final CompanionEntity owner;
        private final Kind kind;
        private final BlockPos origin;
        private final int radius;
        private final int minY;
        private final int maxY;
        private final Deque<Section> pending = new ArrayDeque<>();
        private final List<BlockPos> found = new ArrayList<>();

        Search(CompanionEntity owner, ServerLevel level, BlockPos origin, int radius, Kind kind) {
            this.owner = owner; this.kind = kind; this.origin = origin.immutable(); this.radius = Math.clamp(radius, 8, 48);
            minY = Math.max(level.getMinBuildHeight(), origin.getY() - MAX_DROP - 1);
            maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + 16);
            for (int cx = (origin.getX() - this.radius) >> 4; cx <= (origin.getX() + this.radius) >> 4; cx++)
                for (int cz = (origin.getZ() - this.radius) >> 4; cz <= (origin.getZ() + this.radius) >> 4; cz++) {
                    var chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    for (int index = level.getSectionIndex(minY); index <= level.getSectionIndex(maxY); index++) {
                        var section = chunk.getSection(index);
                        if (!section.hasOnlyAir() && section.maybeHas(kind::is)) pending.add(new Section(cx, cz, index));
                    }
                }
        }

        boolean scan(ServerLevel level) {
            for (int budget = 0; budget < 4 && !pending.isEmpty(); budget++) {
                Section next = pending.removeFirst();
                var chunk = level.getChunkSource().getChunkNow(next.cx, next.cz);
                if (chunk == null) continue;
                var section = chunk.getSection(next.index);
                int bottom = level.getSectionYFromSectionIndex(next.index) << 4;
                for (int y = Math.max(minY, bottom); y <= Math.min(maxY, bottom + 15); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    if (!kind.is(section.getBlockState(x, y & 15, z))) continue;
                    BlockPos pos = new BlockPos((next.cx << 4) + x, y, (next.cz << 4) + z);
                    long dx = pos.getX() - origin.getX(), dz = pos.getZ() - origin.getZ();
                    if (dx * dx + dz * dz <= (long) radius * radius && CompanionSight.inSight(owner, pos)) {
                        found.add(pos);
                        owner.sight.saw(pos, kind);
                    }
                }
            }
            return pending.isEmpty();
        }

        /** The ore in sight and the ore he remembers seeing within the radius, nearest first. */
        List<BlockPos> results() {
            var all = new java.util.LinkedHashSet<>(found);
            all.addAll(owner.sight.ore(kind, origin, radius));
            return all.stream().sorted(Comparator.comparingDouble(pos -> pos.distSqr(origin))).limit(512).toList();
        }
        int radius() { return radius; }
    }
}

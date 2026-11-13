package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Scans only loaded chunk sections whose palette contains logs; does not load or generate chunks. */
public final class CompanionLogSearch {
    private CompanionLogSearch() { }

    public static List<BlockPos> find(ServerLevel level, BlockPos origin, int radius) { return find(level, origin, radius, BlockTags.LOGS); }

    /** The logs of the tag joined to {@code seed} (diagonals too) within 12 blocks sideways and 24 up or down, at most 512, in the order they were reached: one tree, or one building's wall. */
    static java.util.Set<BlockPos> connected(ServerLevel level, BlockPos seed, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag) {
        java.util.Set<BlockPos> found = new java.util.LinkedHashSet<>(), visited = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(seed.immutable()); visited.add(seed.immutable());
        while (!queue.isEmpty() && found.size() < 512) {
            BlockPos pos = queue.removeFirst();
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(tag)) continue;
            found.add(pos.immutable());
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos next = pos.offset(dx, dy, dz);
                if (Math.abs(next.getX() - seed.getX()) > 12 || Math.abs(next.getZ() - seed.getZ()) > 12 || Math.abs(next.getY() - seed.getY()) > 24 || !visited.add(next)) continue;
                if (level.hasChunkAt(next) && level.getBlockState(next).is(tag)) queue.addLast(next);
            }
        }
        return found;
    }

    /** Only blocks of the tag: a named species, or all logs. */
    public static List<BlockPos> find(ServerLevel level, BlockPos origin, int radius, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag) {
        List<BlockPos> found = new ArrayList<>();
        int limit = Math.max(16, Math.min(CompanionEntity.MAX_SEARCH_RADIUS, radius));
        int minY = Math.max(level.getMinBuildHeight(), origin.getY() - 16);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + 48);
        for (int cx = (origin.getX() - limit) >> 4; cx <= (origin.getX() + limit) >> 4; cx++) {
            for (int cz = (origin.getZ() - limit) >> 4; cz <= (origin.getZ() + limit) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sectionIndex = level.getSectionIndex(minY); sectionIndex <= level.getSectionIndex(maxY); sectionIndex++) {
                    var section = chunk.getSection(sectionIndex);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(tag))) continue;
                    int bottom = level.getSectionYFromSectionIndex(sectionIndex) << 4;
                    for (int y = Math.max(bottom, minY); y <= Math.min(bottom + 15, maxY); y++) {
                        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                            int worldX = (cx << 4) + x, worldZ = (cz << 4) + z;
                            long dx = worldX - origin.getX(), dz = worldZ - origin.getZ();
                            if (dx * dx + dz * dz > (long) limit * limit) continue;
                            if (section.getBlockState(x, y & 15, z).is(tag)) found.add(new BlockPos(worldX, y, worldZ));
                        }
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin) + Math.max(0, pos.getY() - origin.getY()) * 4));
        return found;
    }
}

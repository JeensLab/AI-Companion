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

    public static List<BlockPos> find(ServerLevel level, BlockPos origin, int radius) {
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
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(BlockTags.LOGS))) continue;
                    int bottom = level.getSectionYFromSectionIndex(sectionIndex) << 4;
                    for (int y = Math.max(bottom, minY); y <= Math.min(bottom + 15, maxY); y++) {
                        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                            int worldX = (cx << 4) + x, worldZ = (cz << 4) + z;
                            long dx = worldX - origin.getX(), dz = worldZ - origin.getZ();
                            if (dx * dx + dz * dz > (long) limit * limit) continue;
                            if (section.getBlockState(x, y & 15, z).is(BlockTags.LOGS)) found.add(new BlockPos(worldX, y, worldZ));
                        }
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin) + Math.max(0, pos.getY() - origin.getY()) * 4));
        return found;
    }
}

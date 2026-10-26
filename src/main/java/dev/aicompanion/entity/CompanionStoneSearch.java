package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Incremental loaded-section search. Keeps exposed targets spread across the search area. */
final class CompanionStoneSearch {
    private record Section(int cx, int cz, int index) { }
    private final BlockPos origin;
    private final int radius;
    private final int minY;
    private final int maxY;
    private final Deque<Section> pending = new ArrayDeque<>();
    private final Map<BlockPos, BlockPos> samples = new LinkedHashMap<>();

    CompanionStoneSearch(ServerLevel level, BlockPos origin, int radius) {
        this.origin = origin.immutable(); this.radius = Math.clamp(radius, 16, CompanionEntity.MAX_SEARCH_RADIUS);
        minY = Math.max(level.getMinBuildHeight(), origin.getY() - 24);
        maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + 32);
        List<Section> sections = new ArrayList<>();
        for (int cx = (origin.getX() - radius) >> 4; cx <= (origin.getX() + radius) >> 4; cx++) for (int cz = (origin.getZ() - radius) >> 4; cz <= (origin.getZ() + radius) >> 4; cz++) {
            var chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (int index = level.getSectionIndex(minY); index <= level.getSectionIndex(maxY); index++) {
                var section = chunk.getSection(index);
                if (!section.hasOnlyAir() && section.maybeHas(CompanionWork::isStone)) sections.add(new Section(cx, cz, index));
            }
        }
        sections.sort(Comparator.comparingDouble(section -> new BlockPos((section.cx << 4) + 8, (level.getSectionYFromSectionIndex(section.index) << 4) + 8, (section.cz << 4) + 8).distSqr(origin)));
        pending.addAll(sections);
    }

    boolean scan(ServerLevel level) {
        for (int budget = 0; budget < 4 && !pending.isEmpty(); budget++) {
            Section next = pending.removeFirst();
            var chunk = level.getChunkSource().getChunkNow(next.cx, next.cz);
            if (chunk == null) continue;
            var section = chunk.getSection(next.index);
            int bottom = level.getSectionYFromSectionIndex(next.index) << 4;
            for (int y = Math.max(minY, bottom); y <= Math.min(maxY, bottom + 15); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                if (!CompanionWork.isStone(section.getBlockState(x, y & 15, z))) continue;
                BlockPos pos = new BlockPos((next.cx << 4) + x, y, (next.cz << 4) + z);
                long dx = pos.getX() - origin.getX(), dz = pos.getZ() - origin.getZ();
                if (dx * dx + dz * dz > (long)radius * radius || !exposed(level, pos)) continue;
                // One starting face per 3x3x3 cell prevents an inaccessible cave nearby from
                // crowding every reachable cliff out of the result. New faces are added after mining.
                BlockPos cell = new BlockPos(Math.floorDiv(pos.getX(), 3), Math.floorDiv(pos.getY(), 3), Math.floorDiv(pos.getZ(), 3));
                BlockPos old = samples.get(cell);
                if (old == null || pos.distSqr(origin) < old.distSqr(origin)) samples.put(cell, pos);
            }
        }
        return pending.isEmpty();
    }

    List<BlockPos> results() {
        return samples.values().stream().sorted(Comparator.comparingDouble(pos -> pos.distSqr(origin))).limit(4096).toList();
    }

    private static boolean exposed(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (side == Direction.DOWN) continue;
            BlockPos adjacent = pos.relative(side);
            if (level.hasChunkAt(adjacent) && level.getFluidState(adjacent).isEmpty() && level.getBlockState(adjacent).getCollisionShape(level, adjacent).isEmpty()) return true;
        }
        return false;
    }
}

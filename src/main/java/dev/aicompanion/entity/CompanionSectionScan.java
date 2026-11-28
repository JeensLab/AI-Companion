package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The one walk over the loaded sections of a box about him: the way's search and the ore's search look through the same box,
 * {@link CompanionWaySearch#RADIUS} across and {@link CompanionWaySearch#DEPTH} above and below his feet, cut by the player's
 * own search distance. A section's palette says whether any of its cells holds a kind before a cell is read, so a box of
 * sixty-four costs no more than the blocks in it.
 */
final class CompanionSectionScan {
    /** A loaded section of the box, by chunk and section index. */
    record Section(int cx, int cz, int index) { }

    private CompanionSectionScan() { }

    /** The sections of the box that hold a block of these kinds; a section of an unloaded chunk is no section. */
    static List<Section> sections(ServerLevel level, BlockPos center, int radius, int depth, Predicate<BlockState> kind) {
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - depth);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + depth);
        List<Section> found = new ArrayList<>();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++)
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int index = level.getSectionIndex(minY); index <= level.getSectionIndex(maxY); index++) {
                    var section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(kind)) continue;
                    found.add(new Section(cx, cz, index));
                }
            }
        return found;
    }

    /** Every cell of this section that holds a block of these kinds and lies in the box: the flat radius and the depth, as the line names them. */
    static List<BlockPos> cells(ServerLevel level, Section section, BlockPos center, int radius, int depth, Predicate<BlockState> kind) {
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - depth);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + depth);
        var chunk = level.getChunkSource().getChunkNow(section.cx(), section.cz());
        if (chunk == null) return List.of();
        var data = chunk.getSection(section.index());
        int bottom = level.getSectionYFromSectionIndex(section.index()) << 4;
        long far = (long) radius * radius;
        List<BlockPos> found = new ArrayList<>();
        for (int y = Math.max(minY, bottom); y <= Math.min(maxY, bottom + 15); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            if (!kind.test(data.getBlockState(x, y & 15, z))) continue;
            BlockPos cell = new BlockPos((section.cx() << 4) + x, y, (section.cz() << 4) + z);
            long dx = cell.getX() - center.getX(), dz = cell.getZ() - center.getZ();
            if (dx * dx + dz * dz > far || Math.abs(cell.getY() - center.getY()) > depth) continue;
            found.add(cell.immutable());
        }
        return found;
    }
}

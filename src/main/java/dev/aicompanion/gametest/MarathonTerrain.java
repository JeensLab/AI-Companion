package dev.aicompanion.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Terrain for the marathon arenas that are built in code: hills, a coast with an island, a cave, a dense forest, a long valley
 * with a ridge. Blocks are set without neighbour updates (the arena is cleared air before), so a 96×96 field is quick to build.
 * Everything is deterministic: the same arena every run.
 */
final class MarathonTerrain {
    private final GameTestHelper h;
    final int sx, sy, sz;
    /** The empty field round a marathon arena (MarathonArena.M); none for a small test arena. */
    private final int margin;

    MarathonTerrain(GameTestHelper h, int sx, int sy, int sz) { this(h, sx, sy, sz, MarathonArena.M); }
    MarathonTerrain(GameTestHelper h, int sx, int sy, int sz, int margin) { this.h = h; this.sx = sx; this.sy = sy; this.sz = sz; this.margin = margin; }

    void set(int x, int y, int z, BlockState state) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return;
        h.getLevel().setBlock(h.absolutePos(new BlockPos(x + margin, y, z + margin)), state, 2 | 16);
    }
    void set(int x, int y, int z, net.minecraft.world.level.block.Block block) { set(x, y, z, block.defaultBlockState()); }
    BlockState get(int x, int y, int z) { return h.getBlockState(new BlockPos(x + margin, y, z + margin)); }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, state);
    }
    void fill(int x0, int y0, int z0, int x1, int y1, int z1, net.minecraft.world.level.block.Block block) { fill(x0, y0, z0, x1, y1, z1, block.defaultBlockState()); }

    /** Bedrock floor and a bedrock wall round the arena this high: nothing walks or flows out. */
    void frame(int wallTop) {
        fill(0, 0, 0, sx - 1, 0, sz - 1, Blocks.BEDROCK);
        for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++)
            if (x == 0 || z == 0 || x == sx - 1 || z == sz - 1) for (int y = 1; y <= wallTop; y++) set(x, y, z, Blocks.BEDROCK);
    }

    /** A column of ground: stone up to top-soilDepth, then dirt, then the surface block at top. */
    void column(int x, int z, int top, int soilDepth, BlockState surface) {
        for (int y = 1; y <= top; y++)
            set(x, y, z, y == top ? surface : y > top - soilDepth ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
    }

    /** Smooth deterministic hills: a sum of a few waves, so slopes and hollows look natural, not like boxes. */
    static double hills(int x, int z, double scale, double seed) {
        return scale * (Math.sin(x * .071 + seed) * .55 + Math.cos(z * .063 + seed * 1.7) * .5 + Math.sin((x + z) * .039 + seed * .3) * .65
                + Math.cos((x - z) * .11 + seed * 2.1) * .25);
    }

    static BlockState leaves(net.minecraft.world.level.block.Block block) { return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true); }

    /** An ordinary oak: trunk of this height on the ground at y, a round crown; low leaves if bushy (a crown down to the ground). */
    void oak(int x, int y, int z, int height, boolean bushy) {
        BlockState leaf = leaves(Blocks.OAK_LEAVES);
        int crown = y + height - 3;
        for (int dy = bushy ? -(height - 3) + 1 : 0; dy <= 3; dy++) {
            int r = dy >= 2 ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) == r && Math.abs(dz) == r && (dy >= 2 || (x * 31 + z * 17 + dy) % 3 == 0)) continue;
                int ly = crown + dy;
                if (ly <= y) continue;
                if (get(x + dx, ly, z + dz).isAir()) set(x + dx, ly, z + dz, leaf);
            }
        }
        for (int dy = 0; dy < height; dy++) set(x, y + dy, z, Blocks.OAK_LOG);
    }

    /** A tall spruce: a narrow cone from a third of the way up, the trunk up to the tip. */
    void spruce(int x, int y, int z, int height) {
        BlockState leaf = leaves(Blocks.SPRUCE_LEAVES);
        for (int dy = height / 3; dy <= height; dy++) {
            int r = Math.max(0, Math.min(3, (height - dy) / 3 + ((height - dy) % 2 == 0 ? 0 : -1)));
            if (dy == height) r = 0;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > r + 1) continue;
                if (get(x + dx, y + dy, z + dz).isAir()) set(x + dx, y + dy, z + dz, leaf);
            }
        }
        for (int dy = 0; dy < height; dy++) set(x, y + dy, z, Blocks.SPRUCE_LOG);
    }

    /** A dark oak: a trunk of 2×2, a wide flat dense crown that shuts the light out. */
    void darkOak(int x, int y, int z, int height) {
        BlockState leaf = leaves(Blocks.DARK_OAK_LEAVES);
        for (int dy = height - 3; dy <= height + 1; dy++) {
            int r = dy >= height + 1 ? 2 : 3;
            for (int dx = -r; dx <= r + 1; dx++) for (int dz = -r; dz <= r + 1; dz++) {
                if ((dx == -r || dx == r + 1) && (dz == -r || dz == r + 1)) continue;
                if (get(x + dx, y + dy, z + dz).isAir()) set(x + dx, y + dy, z + dz, leaf);
            }
        }
        for (int dy = 0; dy < height; dy++) for (int ox = 0; ox <= 1; ox++) for (int oz = 0; oz <= 1; oz++) set(x + ox, y + dy, z + oz, Blocks.DARK_OAK_LOG);
    }

    /** A giant jungle tree: 2×2 trunk this tall, a big crown on top and a few side crowns lower down. */
    void jungleGiant(int x, int y, int z, int height) {
        BlockState leaf = leaves(Blocks.JUNGLE_LEAVES);
        for (int dy = height - 3; dy <= height + 1; dy++) {
            int r = dy == height + 1 ? 2 : 4 - Math.max(0, dy - height + 1);
            for (int dx = -r; dx <= r + 1; dx++) for (int dz = -r; dz <= r + 1; dz++) {
                if (dx * dx + dz * dz > (r + 1) * (r + 1)) continue;
                if (get(x + dx, y + dy, z + dz).isAir()) set(x + dx, y + dy, z + dz, leaf);
            }
        }
        for (int side = 0; side < 2; side++) {
            int ly = y + height / 2 + side * 3, ox = side == 0 ? -3 : 4;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (get(x + ox + dx, ly, z + dz).isAir()) set(x + ox + dx, ly, z + dz, leaf);
            set(x + ox, ly - 1, z, Blocks.JUNGLE_LOG);
        }
        for (int dy = 0; dy < height; dy++) for (int ox = 0; ox <= 1; ox++) for (int oz = 0; oz <= 1; oz++) set(x + ox, y + dy, z + oz, Blocks.JUNGLE_LOG);
    }

    /** The top solid block of a column at or below fromY (leaves and logs ignored), or 0. */
    int top(int x, int z, int fromY) {
        for (int y = Math.min(fromY, sy - 1); y > 0; y--) {
            BlockState state = get(x, y, z);
            if (!state.isAir() && !state.is(net.minecraft.tags.BlockTags.LEAVES) && !state.is(net.minecraft.tags.BlockTags.LOGS) && state.getFluidState().isEmpty()) return y;
        }
        return 0;
    }
}

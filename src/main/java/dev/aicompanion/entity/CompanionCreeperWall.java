package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What {@link CompanionCreeperGuard} does against a lit fuse it cannot run from; it keeps no state of its own.
 * <p>
 * Under rock a lit fuse he cannot run from is walled out: two blocks from the bag on the next step towards the creeper, which stands while
 * its fuse burns. Nobody takes such a wall out, so it is set only where it does no harm (see {@link #raise}). In water, which has no floor for
 * a way out, he swims straight off the creeper.
 */
final class CompanionCreeperWall {
    /** How far ahead of him the point lies that he swims for, straight off a lit creeper in water. */
    private static final double SWIM_OFF = 4;
    /** A wall is set no nearer the player than this (it would shut him off from the way out), and no nearer a block of his making than this (it would stay there for good). */
    private static final double PLAYER_NEAR = 8;
    private static final int BUILT_NEAR = 4;

    final CompanionEntity c;

    CompanionCreeperWall(CompanionEntity c) { this.c = c; }

    /**
     * Sets the wall: blocks from the bag at his feet and head on the next step towards the creeper, which stands while its fuse burns. True when
     * a block of it was set just now; false where there is no block, no free cell, or the creeper or the reach stand in the way of it. Nobody
     * takes such a wall out, so it is set only under a roof of rock (leaves and water are none), away from the buildings of the player, and not
     * with the player by (it would cut him off from the way out); a cell that holds water or light is no free cell anywhere.
     */
    boolean raise(ServerLevel level, Creeper creeper) {
        if (CompanionSeal.available(c) <= 0 || playerNear(level) || !roofed(level) || byABuilding(level)) return false;
        boolean shut = false;
        for (BlockPos cell : wallCells(level, creeper)) {
            CompanionSeal.Result result = CompanionSeal.place(c, cell);
            if (result == CompanionSeal.Result.PLACED) shut = true;
            else if (result == CompanionSeal.Result.NO_BLOCK) return false;
            else if (!free(level, cell)) shut = true;
        }
        return shut;
    }

    /** A cell a wall may take: air or a plant, with no water in it (a chest, a stair or a plate of the player's that holds water is his) and no light block. */
    static boolean free(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty() && !state.is(Blocks.LIGHT);
    }

    /** A block that stops light and a step above his head, below the surface of the column (the leaves of a tree and the water of a pond are no roof). */
    private boolean roofed(ServerLevel level) {
        BlockPos head = c.blockPosition().above(2);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, head.getX(), head.getZ());
        BlockPos.MutableBlockPos cell = head.mutable();
        for (int y = head.getY(); y < top; y++) if (level.getBlockState(cell.setY(y)).canOcclude()) return true;
        return false;
    }

    private boolean playerNear(ServerLevel level) {
        var player = c.getOwnerPlayer();
        return player != null && player.level() == level && c.distanceTo(player) <= PLAYER_NEAR;
    }

    /** A block of a player's making ({@link CompanionShaftPlan#built}) within a few blocks of him. */
    private boolean byABuilding(ServerLevel level) {
        BlockPos at = c.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-BUILT_NEAR, -BUILT_NEAR, -BUILT_NEAR), at.offset(BUILT_NEAR, BUILT_NEAR, BUILT_NEAR))) {
            if (level.hasChunkAt(pos) && CompanionShaftPlan.built(level, pos)) return true;
        }
        return false;
    }

    private List<BlockPos> wallCells(ServerLevel level, Creeper creeper) {
        Vec3 towards = creeper.position().subtract(c.position());
        Direction side = Math.abs(towards.x) > Math.abs(towards.z) ? (towards.x > 0 ? Direction.EAST : Direction.WEST) : (towards.z > 0 ? Direction.SOUTH : Direction.NORTH);
        for (int rise : new int[]{0, 1, -1}) {
            BlockPos feet = c.blockPosition().relative(side).above(rise);
            if (free(level, feet) && free(level, feet.above())) return List.of(feet, feet.above());
        }
        return List.of();
    }

    /**
     * Where he swims or wades to, straight off the creeper, or null out of water or with rock or lava ahead: a push into those is worse than
     * a blow. A few blocks halve the blast; a blow throws the creeper back by less than one (run r314159-d3/retreat: in a lake, three blows,
     * the fourth too late, dead at sixteen).
     */
    Vec3 swimOff(ServerLevel level, Creeper creeper) {
        Vec3 away = c.position().subtract(creeper.position()).multiply(1, 0, 1);
        if (!c.isInWater() || away.lengthSqr() <= 1.0E-4) return null;
        Vec3 way = away.normalize();
        BlockPos ahead = BlockPos.containing(c.position().add(way));
        if (!level.noCollision(c, c.getBoundingBox().move(way)) || CompanionLava.at(level, ahead) || CompanionLava.at(level, ahead.above())) return null;
        return c.position().add(way.scale(SWIM_OFF));
    }
}

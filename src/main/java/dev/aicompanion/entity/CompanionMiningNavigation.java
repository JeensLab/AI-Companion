package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

final class CompanionMiningNavigation {
    private CompanionMiningNavigation() { }

    static boolean supportsLivingEntity(CompanionEntity owner, BlockPos pos) {
        return !owner.level().getEntities((net.minecraft.world.entity.Entity)null,
                new AABB(pos.above()).inflate(.05, .1, .05), entity -> entity instanceof net.minecraft.world.entity.LivingEntity && entity.isAlive()).isEmpty();
    }

    static boolean visible(CompanionEntity owner, Vec3 eye, BlockPos block) {
        if (!(owner.level() instanceof ServerLevel level)) return false;
        double x = Math.clamp(eye.x, block.getX() + .01, block.getX() + .99);
        double y = Math.clamp(eye.y, block.getY() + .01, block.getY() + .99);
        double z = Math.clamp(eye.z, block.getZ() + .01, block.getZ() + .99);
        for (Vec3 point : new Vec3[]{block.getCenter(), new Vec3(x, y, z), new Vec3(x, block.getY() + .5, z)}) {
            var hit = level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(block)) return true;
        }
        return false;
    }

    static BlockPos obstructingLeaves(CompanionEntity owner, BlockPos block) {
        var level = owner.level();
        var hit = level.clip(new ClipContext(owner.getEyePosition(), block.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.BLOCK && level.hasChunkAt(hit.getBlockPos())
                && level.getBlockState(hit.getBlockPos()).is(BlockTags.LEAVES) ? hit.getBlockPos().immutable() : null;
    }

    static Path pathTo(CompanionEntity owner, BlockPos block, double reach, boolean clearLeaves) {
        return pathTo(owner, block, reach, clearLeaves, false);
    }

    static Path pathTo(CompanionEntity owner, BlockPos block, double reach, boolean clearLeaves, boolean avoidStandingOnTarget) {
        return pathTo(owner, block, reach, clearLeaves, avoidStandingOnTarget, false);
    }

    /** forTheDrop: only cells from which the mined block's drop can be picked up (stone and ore, see dropCollectable). */
    static Path pathTo(CompanionEntity owner, BlockPos block, double reach, boolean clearLeaves, boolean avoidStandingOnTarget, boolean forTheDrop) {
        Set<BlockPos> feet = standCells(owner, block, reach, clearLeaves, avoidStandingOnTarget);
        if (forTheDrop) feet.removeIf(stand -> !dropCollectable(owner.level(), block, stand));
        if (feet.isEmpty()) { CompanionNavigation.approachFailed(owner, block, "no stand cell"); return null; }
        Path path = owner.getNavigation().createPath(feet, 0);
        if (path == null || !path.canReach()) CompanionNavigation.approachFailed(owner, block, "no route");
        return path;
    }

    /**
     * Where the drop of this block comes to rest once it is mined: its own cell if solid ground is under it, else as far as it
     * falls (six blocks at most). Null when it falls into water or down a shaft.
     */
    static BlockPos dropRest(net.minecraft.world.level.Level level, BlockPos block) {
        BlockPos cell = block;
        for (int fall = 0; fall < 6; fall++) {
            BlockPos under = cell.below();
            if (!level.hasChunkAt(under) || !level.getFluidState(under).isEmpty() || !level.getFluidState(cell).isEmpty() && fall > 0) return null;
            if (!level.getBlockState(under).getCollisionShape(level, under).isEmpty()) return cell;
            cell = under;
        }
        return null;
    }

    /**
     * Like a player who mines for the drop (marathon 23.10: at the bottom of the ravine of game log 05cdd3a7 he mined twenty
     * stones from one spot and picked up none — they fell into crevices and shafts). From this stand the drop is touched where
     * it rests (a column aside, from his feet to two blocks over them), or it rests in a free cell beside him, level with his
     * feet or a step down, where he simply steps to take it.
     */
    static boolean dropCollectable(net.minecraft.world.level.Level level, BlockPos block, BlockPos stand) {
        BlockPos rest = dropRest(level, block);
        if (rest == null || Math.abs(rest.getX() - stand.getX()) > 1 || Math.abs(rest.getZ() - stand.getZ()) > 1) return false;
        int dy = rest.getY() - stand.getY();
        if (dy >= 0 && dy <= 2) return true;
        if (dy != -1) return false;
        // A step down into the cell the drop lies in: room for a body there once the block is gone, and for the head on the way
        // in (walking in at his own level and then dropping a block).
        for (BlockPos cell : new BlockPos[]{rest, rest.above(), rest.above(2)})
            if (!cell.equals(block) && !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) return false;
        if (!level.getFluidState(rest).isEmpty()) return false;
        // And out again: a step up to a side cell (not across a corner, the game walks no corner it cannot cut) with room for
        // the body there (marathon 23.10, «долина»: a stone mined across the corner a block below his feet lay in its own hole,
        // whose only side ways out had a block over the head; nobody got it).
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos out = rest.relative(side).above();
            if (out.below().equals(block) || !level.getBlockState(out.below()).isFaceSturdy(level, out.below(), Direction.UP)) continue;
            if ((out.equals(block) || level.getBlockState(out).getCollisionShape(level, out).isEmpty())
                    && (out.above().equals(block) || level.getBlockState(out.above()).getCollisionShape(level, out.above()).isEmpty())) return true;
        }
        return false;
    }

    /** Cells he can stand on and work the block from: within reach, with the block in sight (or behind leaves, if those may go). */
    static Set<BlockPos> standCells(CompanionEntity owner, BlockPos block, double reach, boolean clearLeaves, boolean avoidStandingOnTarget) {
        ServerLevel level = (ServerLevel)owner.level();
        Set<BlockPos> feet = new HashSet<>();
        for (int dy = -4; dy <= 1; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos pos = block.offset(dx, dy, dz);
            if (Vec3.atBottomCenterOf(pos).distanceToSqr(block.getCenter()) > reach * reach || !standable(owner, pos)) continue;
            if (avoidStandingOnTarget && new AABB(pos.getX() + .2, pos.getY(), pos.getZ() + .2,
                    pos.getX() + .8, pos.getY() + owner.getBbHeight(), pos.getZ() + .8).intersects(new AABB(block.above()).inflate(0, .1, 0))) continue;
            Vec3 eye = Vec3.atBottomCenterOf(pos).add(0, owner.getEyeHeight(), 0);
            if (!visible(owner, eye, block)) {
                var hit = level.clip(new ClipContext(eye, block.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
                if (!clearLeaves || hit.getType() != HitResult.Type.BLOCK || !level.getBlockState(hit.getBlockPos()).is(BlockTags.LEAVES)) continue;
            }
            feet.add(pos);
        }
        return feet;
    }

    /** A block chosen by the walk to a cell it can be worked from, that walk, and its length in blocks. */
    record Choice(BlockPos block, Path path, double length) { }

    /**
     * The block a player would go to first (game log 22.10: «камень перед ним, а идёт далеко в другую сторону»): one route
     * search to the cells of all of them at once. The pathfinder stops at the first cell it reaches, so the nearest block on
     * foot wins, not the nearest by the straight line, and a climb counts. Null when none can be reached from here.
     */
    static Choice nearestOnFoot(CompanionEntity owner, java.util.Collection<BlockPos> blocks, double reach, boolean clearLeaves, boolean avoidStandingOnTarget) {
        return nearestOnFoot(owner, blocks, reach, clearLeaves, avoidStandingOnTarget, false);
    }

    /** forTheDrop: stand cells only where the block's drop can then be picked up (dropCollectable). */
    static Choice nearestOnFoot(CompanionEntity owner, java.util.Collection<BlockPos> blocks, double reach, boolean clearLeaves, boolean avoidStandingOnTarget, boolean forTheDrop) {
        java.util.Map<BlockPos, BlockPos> blockAt = new java.util.HashMap<>();
        for (BlockPos block : blocks) for (BlockPos stand : standCells(owner, block, reach, clearLeaves, avoidStandingOnTarget)) {
            if (forTheDrop && !dropCollectable(owner.level(), block, stand)) continue;
            BlockPos other = blockAt.get(stand);
            if (other == null || stand.distSqr(block) < stand.distSqr(other)) blockAt.put(stand, block.immutable());
        }
        if (blockAt.isEmpty()) { if (!blocks.isEmpty()) CompanionNavigation.approachFailed(owner, blocks.iterator().next(), "no stand cell"); return null; }
        Path path = owner.getNavigation().createPath(blockAt.keySet(), 0);
        if (path == null || !path.canReach() || !blockAt.containsKey(path.getTarget())) { CompanionNavigation.approachFailed(owner, blocks.iterator().next(), "no route"); return null; }
        return new Choice(blockAt.get(path.getTarget()), path, length(owner, path));
    }

    /**
     * A route down into a hole: a drop of two blocks or more on the way (walked down, not walked back up) and the end three or
     * more blocks below the start. A drop into a pit at his own level is no such hole: the way back is round it.
     */
    static boolean oneWay(Path path) {
        if (path.getNodeCount() < 2 || path.getNodePos(0).getY() - path.getEndNode().y < 3) return false;
        for (int index = 1; index < path.getNodeCount(); index++)
            if (path.getNodePos(index - 1).getY() - path.getNodePos(index).getY() >= 2) return true;
        return false;
    }

    /**
     * Whether a player standing at {@code from} walks back up to the height {@code toY} (a step up of one block at a time,
     * drops of up to three, through cells with room for a body), within a few thousand cells round him. The pit of game log
     * b65ec97a: down by a two-block drop, back up the slope on foot; the ravine of 05cdd3a7: down its ledges, no way back.
     */
    static boolean canWalkBackUp(CompanionEntity owner, BlockPos from, int toY) { return canWalkBackUp(owner, from, toY, null); }

    /** The same, as if the block {@code removed} were already mined (null: as the world is). */
    static boolean canWalkBackUp(CompanionEntity owner, BlockPos from, int toY, BlockPos removed) {
        return canWalkBackUp(owner, from, toY, removed, null);
    }

    /** An autonomous change needs a proven exit, including when the bounded search cannot establish the old one. */
    static boolean preservesAutonomousReturn(CompanionEntity owner, BlockPos removed) {
        if (!owner.autonomy().protectsReturn()) return true;
        // Own mining still needs the saved exit. A player's explicit preparation can start below an already inaccessible
        // old camp: preserve the climb that exists now, rather than making the first pickaxe impossible there.
        int surface = owner.autonomy().running() ? owner.autonomy().center().getY() : provenReturnHeight(owner);
        if (owner.getY() < surface - .5 && !canWalkBackUp(owner, owner.blockPosition(), surface, removed)) return false;
        // A plug over an old shaft can be mined safely from the surface, yet its drop makes pickup step into a one-way fall.
        // Check that landing too, before creating the physical item; being above the surface is not permission to open it.
        BlockPos landing = dropRest(owner.level(), removed);
        return landing != null && (landing.getY() >= owner.getBlockY() || canWalkBackUp(owner, landing, surface, removed));
    }

    /** Highest already proven climb, computed once per placement search. No route to an old summit must not ban a bench. */
    static int provenReturnHeight(CompanionEntity owner) {
        int low = owner.getBlockY();
        if (!owner.autonomy().protectsReturn()) return low;
        int high = Math.max(low, owner.autonomy().center().getY());
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (canWalkBackUp(owner, owner.blockPosition(), middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    static boolean preservesReturnOnPlacement(CompanionEntity owner, BlockPos placed, int returnHeight) {
        return owner.getY() >= returnHeight - .5 || canWalkBackUp(owner, owner.blockPosition(), returnHeight, null, placed);
    }

    private static boolean canWalkBackUp(CompanionEntity owner, BlockPos from, int toY, BlockPos removed, BlockPos filled) {
        if (from.getY() >= toY) return true;
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        open.add(from.immutable()); seen.add(from.immutable());
        while (!open.isEmpty() && seen.size() < 3000) {
            BlockPos at = open.removeFirst();
            if (at.getY() >= toY) return true;
            for (Direction side : Direction.Plane.HORIZONTAL) for (int dy = 1; dy >= -3; dy--) {
                BlockPos next = at.relative(side).above(dy);
                if (Math.abs(next.getX() - from.getX()) > 24 || Math.abs(next.getZ() - from.getZ()) > 24 || seen.contains(next)) continue;
                // Up a step needs the head room over the cell he steps from; down, the cells he passes on the way down.
                if (dy == 1 && (at.above(2).equals(filled) || solid(owner, at.above(2), removed))) continue;
                if (dy < 0) {
                    boolean clear = true;
                    for (int pass = 1; pass >= dy; pass--) if (at.relative(side).above(pass).equals(filled) || solid(owner, at.relative(side).above(pass), removed)) { clear = false; break; }
                    if (!clear) continue;
                }
                // A rejected transition (e.g. a low ceiling over this side) must not blacklist a cell that another side reaches.
                if (!next.below().equals(removed) && !next.equals(filled) && !next.above().equals(filled)
                        && standable(owner, next)) { seen.add(next); open.addLast(next); break; }
            }
        }
        return false;
    }

    private static boolean solid(CompanionEntity owner, BlockPos pos, BlockPos removed) {
        return !pos.equals(removed) && !owner.level().getBlockState(pos).getCollisionShape(owner.level(), pos).isEmpty();
    }

    /** Walking length of a route; every block of climbing counts once more, like the effort of a jump. */
    static double length(CompanionEntity owner, Path path) {
        double length = 0;
        Vec3 at = owner.position();
        for (int index = 0; index < path.getNodeCount(); index++) {
            Vec3 next = Vec3.atBottomCenterOf(path.getNodePos(index));
            length += at.distanceTo(next) + Math.max(0, next.y - Math.floor(at.y));
            at = next;
        }
        return length;
    }

    static boolean standable(CompanionEntity owner, BlockPos pos) {
        var level = owner.level();
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below()) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return false;
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        return level.noCollision(owner, new AABB(pos.getX() + .2, pos.getY(), pos.getZ() + .2, pos.getX() + .8, pos.getY() + owner.getBbHeight(), pos.getZ() + .8));
    }

    static void walk(CompanionEntity owner, Path path) {
        owner.getNavigation().moveTo(path, 1D);
        var end = path.getEndNode();
        if (end != null && owner.distanceToSqr(end.x + .5, end.y, end.z + .5) < 1.5) {
            owner.getNavigation().stop();
            owner.getMoveControl().setWantedPosition(end.x + .5, end.y, end.z + .5, .7);
        }
    }
}

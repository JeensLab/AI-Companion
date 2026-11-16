package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The walk to where a building is, before the work at it begins. A building the player sets down may be a hundred blocks from where he
 * stands, and the routes of the work (CompanionRoute) look for a way only within one search, for the short last steps: far off they find
 * none, and every block of the site looked unreachable. Far from the site he walks to it in legs, like a player crossing country
 * (CompanionTravel), to the ground beside it; once there, a way into the site is looked for once. No way found after the walk is «no way»:
 * that is the site, not a single block of it.
 */
final class CompanionSiteWalk {
    /** A walk begins this far from the site (the gap to its box) and ends this near it. Between the two the work's own routes do. */
    static final int START = 12, AT = 6;
    /** The walk is tried this many times (each ends after half a minute without coming closer) before it is told as no way. */
    private static final int ATTEMPTS = 2;
    private final CompanionEntity owner;
    private boolean walking;
    private BlockPos goal;
    private int failed;

    CompanionSiteWalk(CompanionEntity owner) { this.owner = owner; }

    void reset() { if (goal != null) owner.travel().reset(); walking = false; goal = null; failed = 0; }

    /**
     * One tick: null when he is at the place of the work (the scaffolding to take down, else the building at {@code base}) and the work goes
     * on, else the result of this tick of the walk: working, or the end of the order when there is no way there.
     */
    CompanionHouse.Result go(ServerLevel level, List<BlockPos> scaffolding, BlockPos base, CompanionTemplates.Loaded loaded, String building) {
        if (scaffolding.isEmpty() && (base == null || loaded == null)) return null;
        List<BlockPos> zone = !scaffolding.isEmpty() ? scaffolding : List.of(base, base.offset(loaded.sizeX() - 1, loaded.sizeY() - 1, loaded.sizeZ() - 1));
        String what = !scaffolding.isEmpty() ? "своих лесов" : "места стройки «" + building + "»";
        BlockPos low = new BlockPos(zone.stream().mapToInt(BlockPos::getX).min().orElseThrow(), zone.stream().mapToInt(BlockPos::getY).min().orElseThrow(), zone.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
        BlockPos high = new BlockPos(zone.stream().mapToInt(BlockPos::getX).max().orElseThrow(), zone.stream().mapToInt(BlockPos::getY).max().orElseThrow(), zone.stream().mapToInt(BlockPos::getZ).max().orElseThrow());
        if (gap(low, high) > (walking ? AT : START)) {
            walking = true;
            if (goal == null) goal = nearest(level, low, high);
            owner.workStatus("Иду к стройке на " + low.toShortString() + ".");
            switch (owner.travel().toward(goal, AT)) {
                case WALKING -> { return CompanionHouse.Result.working(); }
                case FAILED -> {
                    owner.travel().reset(); goal = null;
                    return ++failed >= ATTEMPTS ? noWay(low, what) : CompanionHouse.Result.working();
                }
                case NEAR -> { }
            }
        } else if (!walking) return null;
        // Arrived after a walk: is there a way into the site from here at all?
        owner.travel().arrived();
        owner.getNavigation().stop();
        Set<BlockPos> ring = ring(level, low, high);
        if (!ring.isEmpty()) {
            int busy = owner.work.route.busyAnswers();
            boolean way = owner.work.route.toCells(ring) != null;
            // This tick's cells went elsewhere: asked again next tick.
            if (!way && owner.work.route.busyAnswers() != busy) return CompanionHouse.Result.working();
            if (!way) return noWay(low, what);
        }
        walking = false; goal = null; failed = 0;
        return null;
    }

    private CompanionHouse.Result noWay(BlockPos low, String what) {
        walking = false; goal = null; failed = 0;
        return CompanionHouse.Result.failed("Не могу дойти до " + what + " на " + low.toShortString() + ": пути туда не нашёл. Стройку сохранил; освободи проход и скажи «дострой».");
    }

    /** How far he is from the box of the zone (zero inside it). */
    private double gap(BlockPos low, BlockPos high) {
        AABB box = new AABB(low.getX(), low.getY(), low.getZ(), high.getX() + 1, high.getY() + 1, high.getZ() + 1);
        double dx = Math.max(Math.max(box.minX - owner.getX(), 0), owner.getX() - box.maxX);
        double dy = Math.max(Math.max(box.minY - owner.getY(), 0), owner.getY() - box.maxY);
        double dz = Math.max(Math.max(box.minZ - owner.getZ(), 0), owner.getZ() - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The cells to stand on round the zone, one block out and within three blocks of the level of its foot: where he can stand beside it (not the top of a tree or a roof over it). */
    private static Set<BlockPos> ring(ServerLevel level, BlockPos low, BlockPos high) {
        Set<BlockPos> ring = new HashSet<>();
        for (int x = low.getX() - 1; x <= high.getX() + 1; x++) for (int z = low.getZ() - 1; z <= high.getZ() + 1; z++) {
            if (x > low.getX() - 1 && x < high.getX() + 1 && z > low.getZ() - 1 && z < high.getZ() + 1) continue;
            for (int dy : new int[]{1, 2, 0, 3, -1, 4, -2}) {
                BlockPos feet = new BlockPos(x, low.getY() + dy, z);
                if (!level.hasChunkAt(feet)) break;
                if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP) && level.getFluidState(feet).isEmpty()
                        && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) { ring.add(feet); break; }
            }
        }
        return ring;
    }

    /** The cell of the ring nearest to him: where the walk ends. */
    private BlockPos nearest(ServerLevel level, BlockPos low, BlockPos high) {
        return ring(level, low, high).stream().min(java.util.Comparator.comparingDouble(cell -> cell.distSqr(owner.blockPosition()))).orElse(low);
    }
}

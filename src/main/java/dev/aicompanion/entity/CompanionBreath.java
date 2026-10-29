package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Air like a player's: with his eyes in water and less than a third of his air left, he drops what he is doing (the work is only
 * paused), swims to the nearest cell where his head is out of the water, waits there for a full breath and goes back to it.
 * A flooded tunnel with a ceiling does not let him float up on his own (FloatGoal), and nothing else counts his air.
 * <p>
 * The way is his own plain search over the cells he fits in, not the route search: that one starts from the top of the water
 * column, and under a ceiling a two-high flooded tunnel has no cell to start from.
 */
final class CompanionBreath {
    /** How far around him air is looked for, in blocks across and above/below, and how many cells the look may visit. */
    private static final int RADIUS = 12, DOWN = 2, UP = 10, VISITED = 12000;
    /** The way is looked for again this often (ticks), or at once when he is off it. */
    private static final int AGAIN = 10;
    private final CompanionEntity c;
    private boolean active;
    private int unreachable;
    private long nextSearch;
    /** The cells to swim through, his own first; empty with no way. */
    private List<BlockPos> way = List.of();
    private BlockPos nearest;

    CompanionBreath(CompanionEntity c) { this.c = c; }

    private boolean eyesInWater() { return c.isEyeInFluid(FluidTags.WATER); }

    // No order cancels this: «стоп» or a new order stops the work, and he still swims up for his breath like a player would.
    boolean canUse() { return c.isAlive() && (active || eyesInWater() && c.getAirSupply() < c.getMaxAirSupply() / 3); }
    boolean canContinue() { return active && c.isAlive() && (eyesInWater() || c.getAirSupply() < c.getMaxAirSupply()); }

    void begin() {
        active = true; unreachable = 0; nextSearch = 0; way = List.of(); nearest = null;
        c.getNavigation().stop();
        if (c.hasActiveWorkOrder()) c.workStatus("Выныриваю подышать, потом вернусь к делу.");
    }
    void end() { active = false; way = List.of(); nearest = null; c.getNavigation().stop(); }

    void tick() {
        // In the air: the stroke is over, the rest is waiting for the lungs to fill.
        if (!eyesInWater()) { c.getNavigation().stop(); return; }
        c.getNavigation().stop();
        long now = c.level().getGameTime();
        int at = way.indexOf(c.blockPosition());
        if (now >= nextSearch || !way.isEmpty() && at < 0) { search(now); at = way.indexOf(c.blockPosition()); }
        if (way.size() > at + 1 && at >= 0) {
            BlockPos next = way.get(at + 1);
            c.getMoveControl().setWantedPosition(next.getX() + .5, next.getY(), next.getZ() + .5, 1.0);
            if (next.getY() > c.getBlockY()) c.getJumpControl().jump();
        // No way to any air: still towards the nearest through the walls, not standing still.
        } else if (way.isEmpty() && nearest != null) c.getMoveControl().setWantedPosition(nearest.getX() + .5, nearest.getY(), nearest.getZ() + .5, 1.0);
    }

    private void search(long now) {
        nextSearch = now + AGAIN;
        way = wayToAir();
        if (!way.isEmpty()) { unreachable = 0; return; }
        nearest = nearestAir();
        // No way for three looks: the work stops and says why, and he keeps swimming to the air he sees.
        if (++unreachable == 3 && c.work.isActive()) {
            c.work.finish(false, "Не могу выбраться к воздуху: путь наверх закрыт, а воздух на исходе. Остановил работу.");
        }
    }

    /** From his cell to the nearest cell (by steps) where his head is out of the water, through the water and air he fits in; empty if none. */
    private List<BlockPos> wayToAir() {
        BlockPos at = c.blockPosition();
        Map<BlockPos, BlockPos> from = new HashMap<>();
        from.put(at, at);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(at));
        while (!queue.isEmpty() && from.size() < VISITED) {
            BlockPos p = queue.poll();
            if (headOut(p)) {
                List<BlockPos> way = new ArrayList<>();
                for (BlockPos step = p; !step.equals(at); step = from.get(step)) way.add(step);
                way.add(at);
                Collections.reverse(way);
                return way;
            }
            for (Direction d : Direction.values()) {
                BlockPos next = p.relative(d);
                if (Math.abs(next.getX() - at.getX()) > RADIUS || Math.abs(next.getZ() - at.getZ()) > RADIUS || next.getY() < at.getY() - DOWN || next.getY() > at.getY() + UP
                        || !c.level().hasChunkAt(next) || !fits(next) || from.containsKey(next)) continue;
                from.put(next.immutable(), p);
                queue.add(next.immutable());
            }
        }
        return List.of();
    }

    /** The nearest cell, walls or not, where his head would be out of the water; null if none within the look. */
    private BlockPos nearestAir() {
        BlockPos at = c.blockPosition();
        BlockPos best = null;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-RADIUS, -DOWN, -RADIUS), at.offset(RADIUS, UP, RADIUS)))
            if (c.level().hasChunkAt(p) && headOut(p) && (best == null || p.distSqr(at) < best.distSqr(at))) best = p.immutable();
        return best;
    }

    /** He fits in a cell if it and the cell over it are open (no block, no lava). */
    private boolean fits(BlockPos p) { return passable(p) && c.level().hasChunkAt(p.above()) && passable(p.above()); }
    private boolean passable(BlockPos p) { return c.level().getBlockState(p).getCollisionShape(c.level(), p).isEmpty() && !c.level().getFluidState(p).is(FluidTags.LAVA); }

    /** A cell he fits in with his eyes over the water: they are 1.62 above his feet, in the cell over them. */
    private boolean headOut(BlockPos p) {
        if (!fits(p)) return false;
        BlockPos eyes = p.above();
        var fluid = c.level().getFluidState(eyes);
        return !fluid.is(FluidTags.WATER) || p.getY() + 1.62 >= eyes.getY() + fluid.getHeight(c.level(), eyes);
    }
}

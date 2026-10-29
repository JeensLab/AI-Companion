package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the mine has broken into: air that is not the record of his own mine, a cave. It walks that air in bounded steps (loaded
 * chunks only, never loading one), reads the facts that matter for a decision, and puts them on the scale of the plan:
 * safe, dangerous, very dangerous. Every threshold of the scale is a constant here.
 */
final class CompanionCaveSense {
    enum Danger { SAFE, DANGEROUS, VERY_DANGEROUS }

    static final int MAX_CELLS = 1500, RADIUS = 20, LAVA_RADIUS = 8, WATCH_RADIUS = 16, MAX_DROP = 32;
    /** Very dangerous: health at or below this, this many hostile mobs in the cave, a creeper closer than this, no sealing block. */
    static final float HEALTH_VERY = 8, HEALTH_WARY = 13;
    static final int CROWD = 3, TORCHES_MIN = 8, DROP_DANGEROUS = 3;
    static final double CREEPER_CLOSE = 7;

    private CompanionCaveSense() { }

    /** The air of a cave: from the cells beside the broken block, without the cells of the mine. {@code surface}: it opens to the sky, so it is the outside. */
    record Cave(BlockPos breach, List<BlockPos> mouth, Set<BlockPos> air, boolean truncated, boolean surface, boolean lava, int drop) { }

    record Facts(int hostiles, double creeper, boolean dark, float health, boolean burning, int torches, int seals) { }

    /** Every cell the mine owns: the columns of the entrance and the stands, and the cells of the tunnels. */
    static Set<BlockPos> mineCells(CompanionMineSite mine) {
        Set<BlockPos> cells = new HashSet<>();
        if (!mine.exists()) return cells;
        cells.addAll(CompanionShaftPlan.column(mine.entrance()));
        for (BlockPos stand : mine.stands()) cells.addAll(CompanionShaftPlan.column(stand));
        for (CompanionMineSite.Tunnel tunnel : mine.tunnels()) {
            for (int k = 1; k <= tunnel.length(); k++) cells.addAll(CompanionShaftPlan.tunnelCells(mine.foot().relative(tunnel.direction(), k - 1), tunnel.direction()));
        }
        return cells;
    }

    /** Walks the open air from the mouth, at most {@link #MAX_CELLS} cells and {@link #RADIUS} blocks from the breach. */
    static Cave fill(ServerLevel level, BlockPos breach, Collection<BlockPos> mouth, Set<BlockPos> mine) {
        Set<BlockPos> air = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        boolean truncated = false, surface = false;
        for (BlockPos start : mouth) if (passable(level, start, breach, mine) && air.add(start)) queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            surface |= level.canSeeSky(at);
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (air.contains(next) || !passable(level, next, breach, mine)) continue;
                if (air.size() >= MAX_CELLS) { truncated = true; continue; }
                air.add(next);
                queue.add(next);
            }
        }
        return new Cave(breach, List.copyOf(mouth), air, truncated, surface, lavaNear(level, breach), drop(level, mouth));
    }

    private static boolean passable(ServerLevel level, BlockPos pos, BlockPos breach, Set<BlockPos> mine) {
        return !mine.contains(pos) && pos.distSqr(breach) <= RADIUS * RADIUS && level.isLoaded(pos) && CompanionShaftPlan.open(level, pos);
    }

    private static boolean lavaNear(ServerLevel level, BlockPos breach) {
        for (BlockPos pos : BlockPos.betweenClosed(breach.offset(-LAVA_RADIUS, -LAVA_RADIUS, -LAVA_RADIUS), breach.offset(LAVA_RADIUS, LAVA_RADIUS, LAVA_RADIUS))) {
            if (level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    /** The longest free fall below a cell of the mouth. */
    private static int drop(ServerLevel level, Collection<BlockPos> mouth) {
        int worst = 0;
        for (BlockPos start : mouth) {
            int fall = 0;
            while (fall < MAX_DROP && level.isLoaded(start.below(fall + 1)) && CompanionShaftPlan.open(level, start.below(fall + 1))) fall++;
            worst = Math.max(worst, fall);
        }
        return worst;
    }

    /** What stands in the cave and in him now. {@code torchesAside}: do not count the torches (laid as he explores). */
    static Facts sense(CompanionWork w, ServerLevel level, Cave cave, boolean torchesAside) {
        CompanionEntity owner = w.owner;
        int hostiles = 0;
        double creeper = Double.MAX_VALUE;
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(WATCH_RADIUS), e -> e instanceof Enemy && e.isAlive())) {
            if (!cave.air().contains(mob.blockPosition()) && !cave.air().contains(mob.blockPosition().above())) continue;
            hostiles++;
            if (mob instanceof Creeper) creeper = Math.min(creeper, mob.distanceTo(owner));
        }
        int dark = 0;
        for (BlockPos cell : cave.air()) if (level.getBrightness(LightLayer.BLOCK, cell) == 0) dark++;
        int torches = torchesAside ? Integer.MAX_VALUE : w.items.countCarried(Items.TORCH);
        return new Facts(hostiles, creeper, dark * 2 > cave.air().size(), owner.getHealth(), owner.isOnFire() || owner.isInLava(), torches, CompanionSeal.available(owner));
    }

    static Danger judge(Cave cave, Facts f) {
        if (f.health() <= HEALTH_VERY || f.hostiles() >= CROWD || f.creeper() < CREEPER_CLOSE || f.burning() || f.seals() == 0) return Danger.VERY_DANGEROUS;
        if (f.hostiles() > 0 || cave.lava() || cave.drop() > DROP_DANGEROUS || f.health() <= HEALTH_WARY || f.torches() < TORCHES_MIN) return Danger.DANGEROUS;
        return Danger.SAFE;
    }

    /** The facts as the report tells them, in Russian. */
    static String describe(Cave cave, Facts f) {
        List<String> parts = new ArrayList<>();
        parts.add((cave.truncated() ? "большая, " : "") + "объём около " + cave.air().size() + (f.dark() ? ", тёмная" : ", с освещением"));
        if (f.hostiles() > 0) parts.add("враждебных: " + f.hostiles() + (f.creeper() < Double.MAX_VALUE ? ", среди них крипер" : ""));
        if (cave.lava()) parts.add("рядом лава");
        if (cave.drop() > DROP_DANGEROUS) parts.add("обрыв " + cave.drop() + " блоков");
        if (f.seals() == 0) parts.add("заделывать нечем");
        return String.join(", ", parts);
    }
}

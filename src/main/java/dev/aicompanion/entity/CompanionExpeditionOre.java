package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The ore of the mine expedition: finds diamond (and coal while torches run low) with an open face, walks to it when the mode
 * asks for that, and mines it with the best pickaxe he has. Ore that cannot be taken in time is skipped, not retried.
 */
final class CompanionExpeditionOre {
    /**
     * NEAR: what the hands reach from the feet while digging. WALK: up to twelve blocks off, walking there. SIGHT: what he sees from here, walking there.
     * TUNNEL: NEAR, and when that takes nothing, a diamond open on a cell of his tunnel that the lane he walks does not reach, stepping to it.
     */
    enum Mode { NEAR, WALK, SIGHT, TUNNEL }

    /** NONE: no ore to take; WORKING: busy with one; DONE: one taken this tick; FULL: a diamond, and the bag has no room for it. */
    enum Result { NONE, WORKING, DONE, FULL }

    private static final int SCAN_RADIUS = 4, TUNNEL_BESIDE = 2, WALK_RADIUS = 12, SIGHT_RADIUS = 16, ORE_PATIENCE = 600, TORCHES_ENOUGH = 16;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionDig dig;
    private final Set<BlockPos> skipped = new HashSet<>();
    private BlockPos target;
    /** The target is walked to before it is mined. */
    private boolean walking;
    private Predicate<BlockPos> allowed = pos -> true;
    private int ticks;
    /** Ore is worth looking for: set after every advance, cleared when a look finds none. */
    boolean scan;
    /** A diamond with an open face was seen that no pickaxe he has can take. */
    boolean missedDiamond;

    CompanionExpeditionOre(CompanionWork w, CompanionExpeditionDig dig) {
        this.w = w;
        this.owner = w.owner;
        this.dig = dig;
    }

    boolean pending() { return scan || target != null; }

    boolean busy() { return target != null; }

    void limit(Predicate<BlockPos> allowed) { this.allowed = allowed; }

    void reset() {
        skipped.clear();
        target = null;
        walking = false;
        allowed = pos -> true;
        ticks = 0;
        scan = missedDiamond = false;
    }

    /** One tick of mining the ore in sight. */
    Result step(ServerLevel level, Mode mode) {
        if (target == null) {
            Mode looking = mode == Mode.TUNNEL ? Mode.NEAR : mode;
            target = find(level, looking);
            walking = looking != Mode.NEAR;
            if (target == null && mode == Mode.TUNNEL) {
                target = findBesideTunnel(level);
                walking = target != null;
            }
            ticks = 0;
            if (target == null) {
                if (scan) report(level, looking);
                scan = false;
                return Result.NONE;
            }
        }
        if (++ticks > ORE_PATIENCE) { skip(); return Result.WORKING; }
        if (walking) {
            int reach = w.items.reachBlock(level, target, true);
            if (reach < 0) skip();
            if (reach != 1) return Result.WORKING;
        }
        switch (dig.dig(level, target)) {
            case WORKING -> { }
            case DONE -> { dig.flush(); target = null; walking = false; return Result.DONE; }
            case FULL -> {
                if (level.getBlockState(target).is(BlockTags.DIAMOND_ORES)) return dig.makeRoom(level) ? Result.WORKING : Result.FULL;
                skip();
            }
            default -> skip();
        }
        return Result.WORKING;
    }

    /** A look that found nothing: where he stood, the pickup state, and why each ore block in range was turned down. */
    private void report(ServerLevel level, Mode mode) {
        if (!dev.aicompanion.CompanionDiagnostics.enabled()) return;
        BlockPos feet = owner.blockPosition();
        int radius = mode == Mode.NEAR ? SCAN_RADIUS : mode == Mode.WALK ? WALK_RADIUS : SIGHT_RADIUS;
        Vec3 eye = owner.getEyePosition();
        StringBuilder ores = new StringBuilder();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-radius, -radius, -radius), feet.offset(radius, radius, radius))) {
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(BlockTags.DIAMOND_ORES)) continue;
            ores.append('[').append(pos.toShortString()).append(' ').append(rejection(level, mode, eye, feet, pos)).append(']');
        }
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "ore_scan_ended", "entity", owner.getUUID(), "mode", mode,
                "feet", feet.toShortString(), "diamonds", ores.length() == 0 ? "none" : ores, "loot", owner.loot.diagnosticState());
    }

    /** The first reason {@link #find} would turn this ore down for, or "taken". */
    private String rejection(ServerLevel level, Mode mode, Vec3 eye, BlockPos feet, BlockPos pos) {
        if (skipped.contains(pos)) return "skipped";
        if (!exposed(level, pos)) return "closed";
        if (CompanionOre.liquidNear(level, pos)) return "liquid";
        if (!allowed.test(pos)) return "outside";
        if (!dig.hasTool(level.getBlockState(pos))) return "no tool";
        if (mode == Mode.NEAR) {
            double distance = eye.distanceToSqr(pos.getCenter());
            if (w.mine.carriesStand(pos)) return "under a step";
            if (underFeet(feet, pos) && !floorHolds(level, pos)) return "under feet";
            if (distance > CompanionSeal.REACH * CompanionSeal.REACH) return "out of reach";
            if (!CompanionMiningNavigation.visible(owner, eye, pos)) return "not visible";
            if (!CompanionMiningNavigation.dropCollectable(level, pos, feet)) return "drop not collectable";
        }
        if (mode != Mode.NEAR && !CompanionMiningNavigation.visible(owner, eye, pos)) return "not visible";
        return "taken";
    }

    private void skip() {
        skipped.add(target);
        target = null;
        walking = false;
        dig.forget();
    }

    private BlockPos find(ServerLevel level, Mode mode) {
        BlockPos feet = owner.blockPosition();
        int radius = mode == Mode.NEAR ? SCAN_RADIUS : mode == Mode.WALK ? WALK_RADIUS : SIGHT_RADIUS;
        boolean coal = mode == Mode.NEAR && w.items.countCarried(net.minecraft.world.item.Items.TORCH) < TORCHES_ENOUGH;
        Vec3 eye = owner.getEyePosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-radius, -radius, -radius), feet.offset(radius, radius, radius))) {
            if (!level.hasChunkAt(pos) || skipped.contains(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.DIAMOND_ORES) && !(coal && state.is(BlockTags.COAL_ORES))) continue;
            double distance = eye.distanceToSqr(pos.getCenter());
            if (distance >= bestDistance || !exposed(level, pos) || CompanionOre.liquidNear(level, pos) || !allowed.test(pos)) continue;
            if (!dig.hasTool(state)) { missedDiamond |= state.is(BlockTags.DIAMOND_ORES); continue; }
            if (mode == Mode.NEAR && !withinHands(level, eye, feet, pos, distance)) continue;
            if (mode != Mode.NEAR && !CompanionMiningNavigation.visible(owner, eye, pos)) continue;
            best = pos.immutable();
            bestDistance = distance;
        }
        return best;
    }

    /**
     * A diamond the lane he walks does not reach (its drop rests two columns off, or the ceiling hides it) but that lies open on a cell
     * of his own tunnel beside him: not through the rock, and not into a cave that is shut.
     */
    private BlockPos findBesideTunnel(ServerLevel level) {
        BlockPos feet = owner.blockPosition();
        Vec3 eye = owner.getEyePosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS), feet.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {
            if (!level.hasChunkAt(pos) || skipped.contains(pos) || !level.getBlockState(pos).is(BlockTags.DIAMOND_ORES)) continue;
            double distance = eye.distanceToSqr(pos.getCenter());
            if (distance >= bestDistance || !exposed(level, pos) || CompanionOre.liquidNear(level, pos) || !allowed.test(pos) || w.mine.carriesStand(pos)) continue;
            if (!dig.hasTool(level.getBlockState(pos)) || !opensOnTunnel(level, feet, pos)) continue;
            best = pos.immutable();
            bestDistance = distance;
        }
        return best;
    }

    /** An open face on a cell within two blocks of his feet that no cave record has shut. */
    private boolean opensOnTunnel(ServerLevel level, BlockPos feet, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (Math.abs(next.getX() - feet.getX()) <= TUNNEL_BESIDE && Math.abs(next.getY() - feet.getY()) <= TUNNEL_BESIDE && Math.abs(next.getZ() - feet.getZ()) <= TUNNEL_BESIDE
                    && level.hasChunkAt(next) && CompanionShaftPlan.open(level, next) && !w.mine.shunned(next)) return true;
        }
        return false;
    }

    /** In reach and sight, and its drop to be picked up; the floor under his own feet only when what lies under that ore holds him, and never the floor of a step of his own stairs. */
    private boolean withinHands(ServerLevel level, Vec3 eye, BlockPos feet, BlockPos pos, double distanceSqr) {
        return !w.mine.carriesStand(pos) && (!underFeet(feet, pos) || floorHolds(level, pos)) && distanceSqr <= CompanionSeal.REACH * CompanionSeal.REACH
                && CompanionMiningNavigation.visible(owner, eye, pos) && CompanionMiningNavigation.dropCollectable(level, pos, feet);
    }

    private static boolean underFeet(BlockPos feet, BlockPos pos) {
        return pos.getX() == feet.getX() && pos.getZ() == feet.getZ() && pos.getY() < feet.getY();
    }

    /** Taking the ore he stands over drops him one block: solid and dry ground must be under it, or he would fall into a shaft or the water. */
    private static boolean floorHolds(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getFluidState(below).isEmpty();
    }

    /** An open face to mine it from, and no gravel or sand above to come down on him. */
    private static boolean exposed(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos.above()).getBlock() instanceof FallingBlock) return false;
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (level.hasChunkAt(next) && CompanionShaftPlan.open(level, next)) return true;
        }
        return false;
    }
}

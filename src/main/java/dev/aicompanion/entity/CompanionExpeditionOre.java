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
    /** NEAR: what the hands reach from the feet while digging. WALK: up to twelve blocks off, walking there. SIGHT: what he sees from here, walking there. */
    enum Mode { NEAR, WALK, SIGHT }

    /** NONE: no ore to take; WORKING: busy with one; DONE: one taken this tick; FULL: a diamond, and the bag has no room for it. */
    enum Result { NONE, WORKING, DONE, FULL }

    private static final int SCAN_RADIUS = 4, WALK_RADIUS = 12, SIGHT_RADIUS = 16, ORE_PATIENCE = 600, TORCHES_ENOUGH = 16;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionDig dig;
    private final Set<BlockPos> skipped = new HashSet<>();
    private BlockPos target;
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

    /** One ore is being worked on. */
    boolean busy() { return target != null; }

    /** Only ore this accepts is taken (the cave keeps him within its bounds). */
    void limit(Predicate<BlockPos> allowed) { this.allowed = allowed; }

    void reset() {
        skipped.clear();
        target = null;
        allowed = pos -> true;
        ticks = 0;
        scan = missedDiamond = false;
    }

    /** One tick of mining the ore in sight. */
    Result step(ServerLevel level, Mode mode) {
        if (target == null) {
            target = find(level, mode);
            ticks = 0;
            if (target == null) { scan = false; return Result.NONE; }
        }
        if (++ticks > ORE_PATIENCE) { skip(); return Result.WORKING; }
        if (mode != Mode.NEAR) {
            int reach = w.items.reachBlock(level, target, true);
            if (reach < 0) skip();
            if (reach != 1) return Result.WORKING;
        }
        switch (dig.dig(level, target)) {
            case WORKING -> { }
            case DONE -> { dig.flush(); target = null; return Result.DONE; }
            case FULL -> {
                if (level.getBlockState(target).is(BlockTags.DIAMOND_ORES)) return Result.FULL;
                skip();
            }
            default -> skip();
        }
        return Result.WORKING;
    }

    private void skip() {
        skipped.add(target);
        target = null;
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
            if (mode == Mode.SIGHT && !CompanionMiningNavigation.visible(owner, eye, pos)) continue;
            best = pos.immutable();
            bestDistance = distance;
        }
        return best;
    }

    /** In reach and sight, its drop to be picked up, and not the floor under his own feet. */
    private boolean withinHands(ServerLevel level, Vec3 eye, BlockPos feet, BlockPos pos, double distanceSqr) {
        boolean underFeet = pos.getX() == feet.getX() && pos.getZ() == feet.getZ() && pos.getY() < feet.getY();
        return !underFeet && distanceSqr <= CompanionSeal.REACH * CompanionSeal.REACH && CompanionMiningNavigation.visible(owner, eye, pos)
                && CompanionMiningNavigation.dropCollectable(level, pos, feet);
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

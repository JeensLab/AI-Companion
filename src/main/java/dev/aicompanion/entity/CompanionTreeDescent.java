package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.HashMap;

/** A fallback when following on foot fails from a tree: walk onto the trunk, then descend one safe drop at a time. */
final class CompanionTreeDescent extends Goal {
    private static final int CANOPY_RADIUS = 4;
    private static final int MAX_DROP = 3;
    private static final int MAX_DEPTH = 32;
    private final CompanionEntity c;
    private final CompanionBlockBreaking breaking;
    private Path approach;
    private BlockPos floor;
    private long approachUntil, retryAt;

    CompanionTreeDescent(CompanionEntity c) {
        this.c = c;
        breaking = new CompanionBlockBreaking(c);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    boolean abovePlayerOnTree(ServerPlayer player) {
        if (c.getY() - player.getY() <= MAX_DROP) return false;
        BlockState support = c.level().getBlockState(c.blockPosition().below());
        return support.is(BlockTags.LOGS) || naturalLeaves(support);
    }

    /** Called after the ordinary walking route fails; true also covers a search waiting for its next tick's budget. */
    boolean request(ServerPlayer player) {
        if (!(c.level() instanceof ServerLevel level) || !abovePlayerOnTree(player) || !c.onGround()) return false;
        long now = level.getGameTime();
        if (now < retryAt) return false;
        if (floor != null && canUse()) return true;
        BlockPos feet = c.blockPosition();
        BlockPos underfoot = columnFloor(level, feet.below());
        if (underfoot != null) {
            approach = null;
            floor = underfoot;
            approachUntil = now + 80;
            return true;
        }
        var columns = new HashMap<BlockPos, BlockPos>();
        for (BlockPos entry : BlockPos.betweenClosed(feet.offset(-CANOPY_RADIUS, -1, -CANOPY_RADIUS),
                feet.offset(CANOPY_RADIUS, 1, CANOPY_RADIUS))) {
            BlockPos ground = columnFloor(level, entry.below());
            if (ground != null && dryCell(level, entry) && dryCell(level, entry.above())
                    && level.noCollision(c, c.getBoundingBox().move(entry.getX() + .5 - c.getX(),
                    entry.getY() - c.getY(), entry.getZ() + .5 - c.getZ()))) columns.put(entry.immutable(), ground);
        }
        if (!columns.isEmpty()) {
            int busy = c.work.route.busyAnswers();
            Path path = c.work.route.toCells(columns.keySet());
            if (path != null) {
                approach = path;
                floor = columns.get(path.getTarget());
                approachUntil = now + 80;
                return true;
            }
            if (busy != c.work.route.busyAnswers()) return true;
        }
        retryAt = now + 100;
        return false;
    }

    @Override public boolean canUse() {
        return c.level().getGameTime() < approachUntil && canContinueToUse();
    }

    @Override public boolean canContinueToUse() {
        if (floor == null || !c.canCollectItems() || c.isPassenger() || c.isInWater() || c.isInLava()) return false;
        if (c.work.job != CompanionWork.Job.COME && ((!c.following && !c.protecting)
                || c.autonomy.staysAtWork() || c.hasActiveWorkOrder())) return false;
        ServerPlayer player = c.getOwnerPlayer();
        return player != null && player.isAlive() && c.getY() > Math.max(floor.getY() + 1, player.getY());
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void start() {
        if (approach != null) c.work.route.walk(approach, .75);
        else c.getNavigation().stop();
    }

    @Override public void tick() {
        if (!(c.level() instanceof ServerLevel level) || floor == null) return;
        if (approach != null) {
            if (!c.blockPosition().equals(approach.getTarget())) {
                if (level.getGameTime() >= approachUntil || c.work.route.legOver()) floor = null;
                return;
            }
            approach = null;
            c.getNavigation().stop();
        }
        if (!c.onGround()) return;
        BlockPos support = c.blockPosition().below();
        if (support.getX() != floor.getX() || support.getZ() != floor.getZ() || !cuttable(level, support)
                || landingBelow(level, support) == null) { floor = null; return; }
        double x = support.getX() + .5, z = support.getZ() + .5;
        if (Math.hypot(c.getX() - x, c.getZ() - z) > .12) {
            breaking.reset();
            c.getMoveControl().setWantedPosition(x, c.getY(), z, .45);
            return;
        }
        // His own feet are expected here; never break the support of another living entity.
        if (!level.getEntities(c, new AABB(support.above()).inflate(.05, .1, .05),
                e -> e instanceof LivingEntity && e.isAlive()).isEmpty()) { floor = null; return; }
        c.takeToolForBlock(level.getBlockState(support));
        if (breaking.tick(level, support)) {
            if (!c.gathering.mineLog(support, false)) floor = null;
            breaking.reset();
        }
    }

    @Override public void stop() {
        breaking.reset();
        c.putToolAway();
        c.getNavigation().stop();
        approach = null;
        floor = null;
        retryAt = c.level().getGameTime() + 40;
    }

    private BlockPos columnFloor(ServerLevel level, BlockPos top) {
        if (!cuttable(level, top)) return null;
        boolean crown = false, trunk = false;
        BlockPos support = top;
        while (top.getY() - support.getY() < MAX_DEPTH) {
            BlockState state = level.getBlockState(support);
            trunk |= state.is(BlockTags.LOGS);
            crown |= naturalLeaves(state) || CompanionBuildings.crownBeside(level, support);
            BlockPos landing = landingBelow(level, support);
            if (landing == null) return null;
            if (!cuttable(level, landing)) {
                BlockState ground = level.getBlockState(landing);
                return crown && trunk && top.getY() - landing.getY() > MAX_DROP && !ground.is(BlockTags.LOGS)
                        && !ground.is(BlockTags.LEAVES) ? landing : null;
            }
            support = landing;
        }
        return null;
    }

    private static boolean cuttable(ServerLevel level, BlockPos pos) {
        if (!dryCell(level, pos)) return false;
        BlockState state = level.getBlockState(pos);
        return (state.is(BlockTags.LOGS) || naturalLeaves(state)) && state.getDestroySpeed(level, pos) >= 0
                && state.isCollisionShapeFullBlock(level, pos) && !CompanionBuildings.nearCrafted(level, pos);
    }

    private static BlockPos landingBelow(ServerLevel level, BlockPos support) {
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            BlockPos below = support.below(drop);
            if (!dryCell(level, below)) return null;
            BlockState state = level.getBlockState(below);
            if (state.isAir()) continue;
            return (state.isFaceSturdy(level, below, Direction.UP) || naturalLeaves(state) && state.isCollisionShapeFullBlock(level, below))
                    && !state.is(Blocks.MAGMA_BLOCK)
                    && !state.is(BlockTags.CAMPFIRES) ? below : null;
        }
        return null;
    }

    private static boolean dryCell(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos)
                && level.getFluidState(pos).isEmpty();
    }

    private static boolean naturalLeaves(BlockState state) {
        return state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
    }
}

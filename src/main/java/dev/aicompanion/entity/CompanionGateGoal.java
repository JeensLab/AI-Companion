package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

/**
 * A shut wooden fence gate on his route, opened, walked through and shut behind him as a door is (idea BDGLW, #105). The game's
 * own OpenDoorGoal knows only doors; a shut gate is a fence to it and to the game's judgement of cells. A gate a redstone signal
 * decides is never touched, and only the gate he opened himself is shut again when he gives up, walks past it or leaves the world
 * in the middle of it: a gate another hand opens in front of him is not his to shut.
 */
final class CompanionGateGoal extends Goal {
    /** The time he is given to walk through a gate he opened, before it is shut behind him; topped up while he stands in it. */
    private static final int FORGET_TICKS = 20;
    private final CompanionEntity companion;
    /** The gate he is walking through, and whether he opened it and has walked past it. */
    private BlockPos gate = BlockPos.ZERO;
    private boolean opened, passed;
    private float gateDirX, gateDirZ;
    private int forgetTime;

    CompanionGateGoal(CompanionEntity companion) { this.companion = companion; }

    @Override public boolean canUse() {
        if (!companion.fenceGates()) return false;
        // The gate the movement control opened under him (that control runs every tick, this goal's canUse only every other):
        // take it over, so it stays open while he is in it and is shut behind him.
        if (opened && !passed) return true;
        // A shut gate in the cell he stands in: the movement control hops a mob whose feet are below the top of the block it stands
        // in, and a fence gate's 1.5-high shape is in neither the doors' nor the fences' tag. Its leaf is 4/16 thick in the middle of
        // the cell, so he may stand in the cell without touching it, and no collision starts the goal.
        if (shutByHand(companion.level(), companion.blockPosition())) { gate = companion.blockPosition(); return true; }
        if (!companion.horizontalCollision) return false;
        Path path = companion.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        for (int i = 0; i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); i++) {
            Node node = path.getNode(i);
            BlockPos at = new BlockPos(node.x, node.y, node.z);
            if (companion.distanceToSqr(at.getX() + .5, companion.getY(), at.getZ() + .5) > 2.25) continue;
            if (shutByHand(companion.level(), at)) { gate = at; return true; }
        }
        return false;
    }

    /** Not while he stands in the gate he walks through: a gate shut under him is a step to the movement control, and he is hopped. */
    @Override public boolean canContinueToUse() { return !passed && (forgetTime > 0 || companion.blockPosition().equals(gate)); }

    @Override public void start() {
        forgetTime = FORGET_TICKS; passed = false;
        gateDirX = (float)(gate.getX() + .5 - companion.getX());
        gateDirZ = (float)(gate.getZ() + .5 - companion.getZ());
        set(true);
    }

    /**
     * A shut wooden gate in the cell he stands in, opened before the game's movement control reads its 1.5-high shape as a step:
     * that control runs every tick while this goal's canUse is asked only every other, so the tick he steps into the cell can fall
     * between them and he is hopped there. The gate is his: the goal takes it over and shuts it behind him.
     */
    boolean openUnder() {
        BlockPos in = companion.blockPosition();
        if (!companion.fenceGates() || !shutByHand(companion.level(), in)) return false;
        if (!in.equals(gate)) {
            gate = in;
            gateDirX = (float)(gate.getX() + .5 - companion.getX());
            gateDirZ = (float)(gate.getZ() + .5 - companion.getZ());
            passed = false;
            forgetTime = Math.max(forgetTime, FORGET_TICKS);
        }
        set(true);
        return true;
    }

    @Override public void tick() {
        // Standing in the gate's cell he has not cleared it: the time he is given to walk through is topped up there, so the gate
        // he stands in is never shut under him — a shut gate there is a step to the game's movement control, which hops him — and
        // after he is pushed back out of the cell he still has that time to come back into it.
        if (companion.blockPosition().equals(gate)) forgetTime = FORGET_TICKS; else forgetTime--;
        float dx = (float)(gate.getX() + .5 - companion.getX()), dz = (float)(gate.getZ() + .5 - companion.getZ());
        // Past it and clear of its cell: a gate shut while he still stands in it is a step to the game's movement control (a fence
        // gate's 1.5-high shape is in neither the doors' nor the fences' tag), which hops him there, so he jumped beside the gate.
        if (gateDirX * dx + gateDirZ * dz < 0 && !companion.blockPosition().equals(gate)) passed = true;
        // Shut by someone else while he is on his way through: open it once more, as he would.
        if (!passed) set(true);
    }

    @Override public void stop() { shutBehind(); }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    /**
     * Shut the gate he opened himself: when he gives up, walks past it, or is taken out of the world in the middle of it
     * (a goal's stop() is not called for an entity removed with the gate still open). A gate he did not open is left as it is.
     */
    void shutBehind() {
        if (opened && companion.level().isLoaded(gate)) set(false);
        opened = false; passed = false; forgetTime = 0;
    }

    /** A shut wooden gate he may open by hand: no redstone signal decides it. */
    private static boolean shutByHand(net.minecraft.world.level.Level level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN) && !level.hasNeighborSignal(at);
    }

    /** Open or shut the gate he found, unless a redstone signal decides it now: the signal's gate is left as it is. */
    private void set(boolean open) {
        var level = companion.level();
        BlockState state = level.getBlockState(gate);
        if (!(state.getBlock() instanceof FenceGateBlock block)) return;
        // Already as he wants it: not his doing, so it is not his to shut later.
        if (state.getValue(FenceGateBlock.OPEN) == open) return;
        if (level.hasNeighborSignal(gate)) return;
        // Flag 10 is UPDATE_CLIENTS|UPDATE_IMMEDIATE, the value DoorBlock.setOpen uses: the clients are told of the change at
        // once. UPDATE_KNOWN_SHAPE is not among the flags, so the cells around the gate are re-shaped as well, and the way
        // through the opened gate is walked rather than judged against the shut gate he was stopped at.
        level.setBlock(gate, state.setValue(FenceGateBlock.OPEN, open), 10);
        level.playSound(null, gate, open ? block.openSound : block.closeSound, SoundSource.BLOCKS, 1F, 1F);
        // He opens and shuts it with his own hand, as a player does: the arm swings with the block change.
        companion.swing(InteractionHand.MAIN_HAND);
        if (open) opened = true;
    }
}

package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
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
    private final CompanionEntity companion;
    /** The gate he is walking through, and whether he opened it and has walked past it. */
    private BlockPos gate = BlockPos.ZERO;
    private boolean opened, passed;
    private float gateDirX, gateDirZ;
    private int forgetTime;

    CompanionGateGoal(CompanionEntity companion) { this.companion = companion; }

    @Override public boolean canUse() {
        if (!companion.fenceGates() || !companion.horizontalCollision) return false;
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

    @Override public boolean canContinueToUse() { return forgetTime > 0 && !passed; }

    @Override public void start() {
        forgetTime = 20; opened = false; passed = false;
        gateDirX = (float)(gate.getX() + .5 - companion.getX());
        gateDirZ = (float)(gate.getZ() + .5 - companion.getZ());
        set(true);
    }

    @Override public void tick() {
        forgetTime--;
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
        if (open) opened = true;
    }
}

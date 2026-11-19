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
 * decides is never touched, and the gate he opened is shut again when he gives up or walks past it.
 */
final class CompanionGateGoal extends Goal {
    private final CompanionEntity companion;
    /** The gate he is walking through, where he found it shut, and whether he is the one who opened it. */
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
        if (gateDirX * dx + gateDirZ * dz < 0) passed = true;
        // Shut by someone else while he is on his way through: open it once more, as he would.
        if (!passed) set(true);
    }

    @Override public void stop() { if (opened) set(false); opened = false; }

    @Override public boolean requiresUpdateEveryTick() { return true; }

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
        if (state.getValue(FenceGateBlock.OPEN) == open) { if (open) opened = true; return; }
        if (level.hasNeighborSignal(gate)) return;
        level.setBlock(gate, state.setValue(FenceGateBlock.OPEN, open), 3);
        level.playSound(null, gate, open ? block.openSound : block.closeSound, SoundSource.BLOCKS, 1F, 1F);
        if (open) opened = true;
    }
}

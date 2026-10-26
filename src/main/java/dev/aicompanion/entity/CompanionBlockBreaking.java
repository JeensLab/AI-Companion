package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** A continuous survival-mode swing. Moving away, changing tools/blocks or cancelling resets cracks. */
final class CompanionBlockBreaking {
    private final CompanionEntity owner;
    private BlockPos target;
    private BlockState state;
    private ItemStack tool = ItemStack.EMPTY;
    private int ticks;
    private long lastTick = Long.MIN_VALUE;
    private int abandoned;
    CompanionBlockBreaking(CompanionEntity owner) { this.owner = owner; }
    String diagnosticState() { return "target=" + target + ",ticks=" + ticks + ",block=" + state; }

    boolean tick(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        ItemStack hand = owner.getMainHandItem();
        if (!pos.equals(target) || current != state || !ItemStack.isSameItemSameComponents(tool, hand)) {
            reset(target == null ? "begin" : !pos.equals(target) ? "target_changed" : current != state ? "block_changed" : "tool_changed");
            target = pos.immutable(); state = current; tool = hand.copy();
            dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "mining_started", "entity", owner.getUUID(),
                    "target", target, "block", current, "tool", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hand.getItem()));
        }
        float hardness = current.getDestroySpeed(level, pos);
        if (hardness < 0 || current.isAir()) { reset(); return false; }
        if (lastTick == level.getGameTime()) return false;
        lastTick = level.getGameTime();
        float speed = Math.max(1, hand.getDestroySpeed(current));
        float divisor = !current.requiresCorrectToolForDrops() || hand.isCorrectToolForDrops(current) ? 30 : 100;
        int duration = Math.max(1, (int)Math.ceil(hardness * divisor / speed));
        ticks++;
        if (ticks == duration) dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "mining_duration_reached",
                "entity", owner.getUUID(), "target", target, "ticks", ticks);
        owner.miningAnimation(true);
        if (ticks % 5 == 1) owner.swing(InteractionHand.MAIN_HAND);
        if (ticks % 4 == 1) {
            var sound = current.getSoundType(level, pos, owner);
            level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 8, sound.getPitch() * .5F);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, current),
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 2, .25, .25, .25, .01);
        }
        level.destroyBlockProgress(owner.getId(), pos, Math.min(9, ticks * 10 / duration));
        return ticks >= duration;
    }

    /** A swing that already left cracks on a still-unchanged block and was continued on the last tick or two. */
    boolean swinging() {
        return target != null && ticks > 0 && owner.level() instanceof ServerLevel level
                && level.getGameTime() - lastTick <= 2 && level.getBlockState(target) == state;
    }

    void reset() {
        reset("stage_pause_or_target_release");
    }
    /** Swings stopped with cracks on a block that is still there: that work was thrown away. */
    int abandoned() { return abandoned; }

    private void reset(String reason) {
        if (target != null && ticks > 0 && owner.level() instanceof ServerLevel level && level.getBlockState(target) == state) abandoned++;
        if (target != null && ticks > 0) dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "mining_reset",
                "entity", owner.getUUID(), "target", target, "ticks", ticks, "reason", reason);
        owner.miningAnimation(false);
        if (target != null && owner.level() instanceof ServerLevel level) level.destroyBlockProgress(owner.getId(), target, -1);
        target = null; state = null; tool = ItemStack.EMPTY; ticks = 0; lastTick = Long.MIN_VALUE;
    }
}

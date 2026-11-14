package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /sit (the owner asked, 09.11): the player sits down on the block he stands on, as Oleg does by the fire. The seat is an invisible
 * point under him that he rides; it is never saved and goes as soon as nobody sits on it or the block under it is gone. Standing up:
 * sneak, or /sit again.
 */
public final class CompanionSeat extends Entity {
    public CompanionSeat(EntityType<? extends CompanionSeat> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvisible(true);
    }

    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sit").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (player.getVehicle() instanceof CompanionSeat) { player.stopRiding(); return 1; }
            if (player.isPassenger() || !player.onGround() || player.isSleeping() || player.isFallFlying() || player.getAbilities().flying
                    || player.isInWater() || player.isSwimming()) {
                context.getSource().sendFailure(Component.literal("Сесть можно, только стоя на твёрдом блоке."));
                return 0;
            }
            CompanionSeat seat = new CompanionSeat(AICompanionMod.SEAT.get(), player.level());
            seat.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
            if (!player.level().addFreshEntity(seat) || !player.startRiding(seat, true)) { seat.discard(); return 0; }
            return 1;
        }));
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        var under = blockPosition().below();
        // Nobody on it, or the block he sat on was broken: the seat goes, and he with it falls as a player would.
        if (getPassengers().isEmpty() || level().getBlockState(under).getCollisionShape(level(), under).isEmpty()
                && level().getBlockState(blockPosition()).getCollisionShape(level(), blockPosition()).isEmpty()) discard();
    }

    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }
    /** He stands up where he sat, not on top of anything. */
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) { return position(); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
}

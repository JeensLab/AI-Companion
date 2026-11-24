package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The bobber of his rod (CompanionFishing). The game's own FishingHook lives only on a player's line: with any other owner it
 * removes itself on the server and on the client. This one is a FishingHook to the game (the treasure of its loot table asks the
 * hook for open water) and keeps the hook's flight, its bobbing and its wait for a bite, which are private there, as steps of
 * its own. It hooks no mob and no item. Never saved: a world loaded again has no line out.
 */
public final class CompanionBobber extends FishingHook {
    private static final double GRAVITY = .03, DRAG = .92;
    private boolean bobbing, openWater = true;
    private int outOfWater, nibble, untilLured, untilHooked, lure, luck, grounded;
    private float fishAngle;

    public CompanionBobber(EntityType<? extends CompanionBobber> type, Level level) { super(type, level); }

    /** Cast from his hand so that it comes down on {@code water}; the rod's own Lure and Luck of the Sea count as a player's do. */
    static CompanionBobber cast(CompanionEntity c, ServerLevel level, ItemStack rod, Vec3 water) {
        CompanionBobber bobber = new CompanionBobber(AICompanionMod.BOBBER.get(), level);
        bobber.setOwner(c);
        bobber.lure = Math.max(0, (int) (EnchantmentHelper.getFishingTimeReduction(level, rod, c) * 20F));
        bobber.luck = Math.max(0, EnchantmentHelper.getFishingLuckBonus(level, rod, c));
        Vec3 from = c.getEyePosition();
        bobber.moveTo(from.x, from.y, from.z, c.getYRot(), 0);
        // The throw that lands there: the flight is the hook's (gravity before each move, drag after it), solved for its first speed.
        int ticks = Mth.clamp((int) (from.distanceTo(water) * 2.5) + 6, 10, 30);
        double reach = 0, fall = 0, speed = 1, sunk = 0;
        for (int tick = 0; tick < ticks; tick++) { sunk += GRAVITY; reach += speed; fall += sunk; speed *= DRAG; sunk *= DRAG; }
        bobber.setDeltaMovement((water.x - from.x) / reach, (water.y - from.y + fall) / reach, (water.z - from.z) / reach);
        level.addFreshEntity(bobber);
        return bobber;
    }

    /** A fish is on the line now: the half second to two the game gives to pull. */
    boolean biting() { return nibble > 0; }

    /** It floats on water. */
    boolean afloat() { return bobbing; }

    /** Ticks it has lain on the ground or hung with no water under it. */
    int grounded() { return grounded; }

    @Override
    public boolean isOpenWaterFishing() { return openWater; }

    @Override
    public void tick() {
        // Not FishingHook.tick: it discards a hook with no player on its line. Entity.tick is this one call.
        baseTick();
        if (level().isClientSide) return;
        if (!(getOwner() instanceof CompanionEntity c) || !c.isAlive() || c.level() != level() || distanceToSqr(c) > 1024 || !c.getMainHandItem().is(Items.FISHING_ROD)) { discard(); return; }
        BlockPos at = blockPosition();
        FluidState fluid = level().getFluidState(at);
        float height = fluid.is(FluidTags.WATER) ? fluid.getHeight(level(), at) : 0;
        boolean wet = height > 0;
        if (!bobbing) {
            if (wet) { setDeltaMovement(getDeltaMovement().multiply(.3, .2, .3)); bobbing = true; return; }
            if (onGround() || horizontalCollision || tickCount > 60) grounded++;
        } else {
            Vec3 speed = getDeltaMovement();
            double over = getY() + speed.y - at.getY() - height;
            if (Math.abs(over) < .01) over += Math.signum(over) * .1;
            setDeltaMovement(speed.x * .9, speed.y - over * random.nextFloat() * .2, speed.z * .9);
            openWater = nibble <= 0 && untilHooked <= 0 || openWater && outOfWater < 10 && openWaterAbout(at);
            if (wet) { outOfWater = Math.max(0, outOfWater - 1); grounded = 0; await((ServerLevel) level(), at); }
            else { outOfWater = Math.min(10, outOfWater + 1); grounded++; }
        }
        if (!fluid.is(FluidTags.WATER)) setDeltaMovement(getDeltaMovement().add(0, -GRAVITY, 0));
        move(MoverType.SELF, getDeltaMovement());
        if (!bobbing && (onGround() || horizontalCollision)) setDeltaMovement(Vec3.ZERO);
        setDeltaMovement(getDeltaMovement().scale(DRAG));
        reapplyPosition();
    }

    /** FishingHook.catchingFish: the wait for a fish (shorter in the rain and with Lure, longer under a roof), its wake, the bite. */
    private void await(ServerLevel level, BlockPos at) {
        int pace = 1;
        if (random.nextFloat() < .25F && level.isRainingAt(at.above())) pace++;
        if (random.nextFloat() < .5F && !level.canSeeSky(at.above())) pace--;
        if (nibble > 0) {
            if (--nibble <= 0) untilLured = untilHooked = 0;
        } else if (untilHooked > 0) {
            untilHooked -= pace;
            if (untilHooked > 0) {
                fishAngle += (float) random.triangle(0, 9.188);
                float angle = fishAngle * Mth.DEG_TO_RAD, sin = Mth.sin(angle), cos = Mth.cos(angle);
                double x = getX() + sin * untilHooked * .1F, y = Mth.floor(getY()) + 1F, z = getZ() + cos * untilHooked * .1F;
                if (level.getBlockState(BlockPos.containing(x, y - 1, z)).is(Blocks.WATER)) {
                    if (random.nextFloat() < .15F) level.sendParticles(ParticleTypes.BUBBLE, x, y - .1F, z, 1, sin, .1, cos, 0);
                    level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, cos * .04F, .01, -sin * .04F, 1);
                    level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, -cos * .04F, .01, sin * .04F, 1);
                }
            } else {
                playSound(SoundEvents.FISHING_BOBBER_SPLASH, .25F, 1F + (random.nextFloat() - random.nextFloat()) * .4F);
                level.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + .5, getZ(), (int) (1F + getBbWidth() * 20F), getBbWidth(), 0, getBbWidth(), .2F);
                level.sendParticles(ParticleTypes.FISHING, getX(), getY() + .5, getZ(), (int) (1F + getBbWidth() * 20F), getBbWidth(), 0, getBbWidth(), .2F);
                nibble = Mth.nextInt(random, 20, 40);
                // The pull under, as the game's hook is pulled when its bite is told to it.
                setDeltaMovement(getDeltaMovement().x, -.4F * Mth.nextFloat(random, .6F, 1F), getDeltaMovement().z);
            }
        } else if (untilLured > 0) {
            untilLured -= pace;
            if (untilLured <= 0) { fishAngle = Mth.nextFloat(random, 0, 360); untilHooked = Mth.nextInt(random, 20, 80); }
        } else untilLured = Mth.nextInt(random, 100, 600) - lure;
    }

    /** FishingHook.calculateOpenWater: five by five of water sources with the air (or lily pads) over them, nothing else in the way. */
    private boolean openWaterAbout(BlockPos at) {
        int last = -1;
        for (int dy = -1; dy <= 2; dy++) {
            int layer = -1;
            for (BlockPos cell : BlockPos.betweenClosed(at.offset(-2, dy, -2), at.offset(2, dy, 2))) {
                BlockState state = level().getBlockState(cell);
                FluidState fluid = state.getFluidState();
                int kind = state.isAir() || state.is(Blocks.LILY_PAD) ? 1 : fluid.is(FluidTags.WATER) && fluid.isSource() && state.getCollisionShape(level(), cell).isEmpty() ? 0 : 2;
                if (kind == 2 || layer >= 0 && layer != kind) return false;
                layer = kind;
            }
            // Water under the air, never the other way round, and the lowest layer is water.
            if (layer == 1 ? last == -1 : last == 1) return false;
            last = layer;
        }
        return true;
    }

    /**
     * Pulled in. With a fish on the line the catch of the game's fishing table flies out of the water to him, as it does to a
     * player, and experience falls at his feet; the item entities are returned for him to pick up. Empty otherwise.
     */
    List<ItemEntity> reel(CompanionEntity c, ItemStack rod) {
        List<ItemEntity> catches = new ArrayList<>();
        if (nibble > 0 && level() instanceof ServerLevel level) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, position()).withParameter(LootContextParams.TOOL, rod)
                    .withParameter(LootContextParams.THIS_ENTITY, this).withParameter(LootContextParams.ATTACKING_ENTITY, c).withLuck(luck).create(LootContextParamSets.FISHING);
            for (ItemStack stack : level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params)) {
                ItemEntity item = new ItemEntity(level, getX(), getY(), getZ(), stack);
                double dx = c.getX() - getX(), dy = c.getY() - getY(), dz = c.getZ() - getZ();
                item.setDeltaMovement(dx * .1, dy * .1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * .08, dz * .1);
                if (level.addFreshEntity(item)) catches.add(item);
                level.addFreshEntity(new ExperienceOrb(level, c.getX(), c.getY() + .5, c.getZ() + .5, random.nextInt(6) + 1));
            }
        }
        discard();
        return catches;
    }

    /** As Entity does: the server's place every tick. FishingHook ignores it and flies by itself on the client, which this one does not. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        setPos(x, y, z);
        setRot(yRot, xRot);
    }

    /** Entity's and Projectile's steps: FishingHook's own kills on the client a hook whose owner is no player. */
    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        syncPacketPositionCodec(packet.getX(), packet.getY(), packet.getZ());
        moveTo(packet.getX(), packet.getY(), packet.getZ());
        setXRot(packet.getXRot());
        setYRot(packet.getYRot());
        setId(packet.getId());
        setUUID(packet.getUUID());
        Entity owner = level().getEntity(packet.getData());
        if (owner != null) setOwner(owner);
    }
}

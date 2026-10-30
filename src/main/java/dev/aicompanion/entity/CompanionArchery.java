package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The bow in a fight, as a player has it: a bow and arrows from his bag, a full draw, a real arrow that is gone from the bag and lies
 * where it fell, wear on the bow. An enemy at range gets arrows, one that comes close the sword. A fight reflex like the melee, no step
 * of a plan, and never a shot with someone who is no enemy on the line. Things he carries for the player stay untouched.
 */
final class CompanionArchery {
    /** A full draw of a player's bow. */
    private static final int DRAW_TICKS = 20, BETWEEN_SHOTS = 6;
    /** Inside this the sword comes out; back to the bow only from farther off, so the two do not change hands at one distance. */
    private static final double BLADE_RANGE = 3.5, BOW_RANGE = 6;
    /** Past this he does not shoot; past the second he walks closer while drawing. */
    private static final double FARTHEST = 32, WALK_UP_FROM = 20;
    /** A full draw's arrow, as BowItem shoots it; the game's gravity on arrows. */
    private static final float ARROW_SPEED = 3F, SPREAD = 1F;
    private static final double ARROW_GRAVITY = .05;
    /** Someone stays on the line for this long: he goes in with the blade for the next ticks instead of standing there with a drawn bow. */
    private static final int HOLD_FIRE_TICKS = 30, BLADE_AFTER_HOLD_TICKS = 60;

    final CompanionEntity c;
    private boolean aiming;
    private int pause, held, bladeUntil;

    CompanionArchery(CompanionEntity c) { this.c = c; }

    private boolean ownBow(ItemStack stack) { return stack.getItem() instanceof BowItem && !c.carriedForPlayer(stack); }

    /** A bow in the hand or the bag, and an arrow in the bag, none of them the player's. */
    boolean armed() {
        if (c.inventoryViewers != 0) return false;
        boolean bow = ownBow(c.getMainHandItem());
        boolean arrows = false;
        for (ItemStack stack : c.companionInventory) {
            bow |= ownBow(stack);
            arrows |= arrow(stack);
        }
        return bow && arrows;
    }

    private boolean arrow(ItemStack stack) { return stack.is(ItemTags.ARROWS) && stack.getItem() instanceof ArrowItem && !c.carriedForPlayer(stack); }

    /** This target is for the bow now. Only enemies: the hunt's animals are taken by hand, their drops counted where they fall. */
    boolean wants(LivingEntity target) {
        aiming = aiming(target);
        return aiming;
    }

    private boolean aiming(LivingEntity target) {
        if (target == null || !target.isAlive() || !(target instanceof Enemy) || c.tickCount < bladeUntil || !armed()) return false;
        double distance = c.distanceTo(target);
        if (distance > FARTHEST || !c.getSensing().hasLineOfSight(target)) return false;
        return target instanceof FlyingMob || distance >= (aiming ? BLADE_RANGE : BOW_RANGE);
    }

    /** While the bow is out the shield stays down: both are held with the same use of the hands. */
    boolean aiming() { return aiming; }

    void lowerBow() {
        if (c.isUsingItem() && c.getUsedItemHand() == InteractionHand.MAIN_HAND && c.getMainHandItem().getItem() instanceof BowItem) c.stopUsingItem();
        held = 0;
    }

    private void tick(LivingEntity target) {
        c.getLookControl().setLookAt(target, 30F, 30F);
        c.lookAt(target, 30F, 30F);
        if (c.distanceTo(target) > WALK_UP_FROM) { if (c.getNavigation().isDone()) c.getNavigation().moveTo(target, 1D); }
        else c.getNavigation().stop();
        ItemStack bow = c.getMainHandItem();
        if (!(bow.getItem() instanceof BowItem)) { c.selectWeaponForCombat(); return; }
        if (!c.isUsingItem()) {
            if (--pause <= 0) c.startUsingItem(InteractionHand.MAIN_HAND);
            return;
        }
        if (c.getUsedItemHand() != InteractionHand.MAIN_HAND) { c.stopUsingItem(); return; }
        if (c.getTicksUsingItem() < DRAW_TICKS || !(c.level() instanceof ServerLevel level)) return;
        Vec3 aim = aim(target);
        if (someoneOnTheLine(level, aim, target)) {
            if (++held >= HOLD_FIRE_TICKS) { bladeUntil = c.tickCount + BLADE_AFTER_HOLD_TICKS; held = 0; }
            return;
        }
        held = 0;
        shoot(level, bow, aim);
        c.stopUsingItem();
        pause = BETWEEN_SHOTS;
    }

    /** Where the arrow is sent: the body of the target where it will be, as high over it as the arrow sinks on the way. */
    private Vec3 aim(LivingEntity target) {
        Vec3 at = target.getBoundingBox().getCenter();
        double flight = c.getEyePosition().distanceTo(at) / ARROW_SPEED;
        Vec3 moves = target.getDeltaMovement();
        return at.add(moves.x * flight, target.onGround() ? 0 : moves.y * flight, moves.z * flight).add(0, ARROW_GRAVITY / 2 * flight * flight, 0);
    }

    /** Anyone alive who is no enemy, near the way of the arrow: the player, his pets, a cow. */
    private boolean someoneOnTheLine(ServerLevel level, Vec3 aim, LivingEntity target) {
        Vec3 from = c.getEyePosition();
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(from, aim).inflate(1), other -> other != c && other != target
                && !(other instanceof Enemy) && !other.isSpectator() && other.getBoundingBox().inflate(.6).clip(from, aim).isPresent()).isEmpty();
    }

    private void shoot(ServerLevel level, ItemStack bow, Vec3 aim) {
        ItemStack ammo = ItemStack.EMPTY;
        for (ItemStack stack : c.companionInventory) if (arrow(stack)) { ammo = stack; break; }
        if (ammo.isEmpty()) return;
        AbstractArrow arrow = ((ArrowItem) ammo.getItem()).createArrow(level, ammo.copyWithCount(1), c, bow);
        int spent = EnchantmentHelper.processAmmoUse(level, bow, ammo, 1);
        // Lies where it fell for whoever picks it up, like a player's; an arrow of an Infinity bow left no gap in the bag to fill.
        arrow.pickup = spent > 0 ? AbstractArrow.Pickup.ALLOWED : AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.setCritArrow(true);
        arrow.shoot(aim.x - arrow.getX(), aim.y - arrow.getY(), aim.z - arrow.getZ(), ARROW_SPEED, SPREAD);
        level.addFreshEntity(arrow);
        level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.ARROW_SHOOT, c.getSoundSource(), 1F, 1F / (c.getRandom().nextFloat() * .4F + 1.2F) + .5F);
        bow.hurtAndBreak(1, c, EquipmentSlot.MAINHAND);
        ammo.shrink(spent);
        c.refreshInventorySummary();
    }

    /** The game looks only four blocks up and down for a target: a ghast or a phantom hangs higher. */
    static final class FlierTargetGoal extends NearestAttackableTargetGoal<FlyingMob> {
        FlierTargetGoal(CompanionEntity c) { super(c, FlyingMob.class, 10, true, false, target -> c.archery.armed() && c.combat.fliesAtOwner(target)); }
        @Override protected AABB getTargetSearchArea(double range) { return mob.getBoundingBox().inflate(range); }
    }

    /** Beside the melee goal: only one of the two has a target at a time, and they hand it over by distance. */
    static final class BowAttackGoal extends Goal {
        final CompanionEntity c;
        BowAttackGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return c.archery.wants(c.getTarget()); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { c.selectWeaponForCombat(); }
        @Override public void tick() { if (c.getTarget() != null) c.archery.tick(c.getTarget()); }
        @Override public void stop() { c.archery.lowerBow(); c.getNavigation().stop(); }
    }
}

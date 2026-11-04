package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

final class CompanionCombat {
    final CompanionEntity c;
    final CompanionFootwork footwork;

    CompanionCombat(CompanionEntity c) { this.c = c; footwork = new CompanionFootwork(c); }

    /**
     * Swings as often as the weapon in hand lets a player (a stone sword 1.6 a second, an axe 0.8), not the mob's once a second
     * (marathon 23.10, night: a zombie and a skeleton killed him with a stone sword — the zombie struck as often as he did and
     * he fell a single swing short). The damage is the weapon's, as before.
     */
    static final class PlayerPaceAttackGoal extends MeleeAttackGoal {
        final CompanionEntity c;
        PlayerPaceAttackGoal(CompanionEntity c) { super(c, 1.15D, true); this.c = c; }
        private int nextSwing;
        /** A foe already in reach is met this tick, not at vanilla's next once-a-second check: every tick of delay is a blow taken. At range the bow has him, when he has one (CompanionArchery). */
        @Override public boolean canUse() {
            LivingEntity target = c.getTarget();
            if (c.archery.wants(target) || target == null || !target.isAlive() || c.work.expedition.shuns(target) || c.combat.unarmedOnTask()) return false;
            return c.isWithinMeleeAttackRange(target) || c.work.expedition.engages(target) || super.canUse();
        }
        @Override public boolean canContinueToUse() {
            return !c.archery.wants(c.getTarget()) && super.canContinueToUse() && !c.work.expedition.shuns(c.getTarget()) && !c.combat.unarmedOnTask();
        }
        /** The best weapon before the first swing, not after the ten-tick check: otherwise the first blow goes with the digging pickaxe. */
        @Override public void start() { c.work.expedition.fightBegan(); c.combat.footwork.begin(); super.start(); c.selectWeaponForCombat(); }
        /** What is on him is struck before what is not; and between two swings he steps out of the foe's reach (CompanionFootwork). */
        @Override public void tick() {
            if (c.level() instanceof ServerLevel level) {
                LivingEntity nearer = c.combat.footwork.nearer(level, c.getTarget());
                if (nearer != null) c.setTarget(nearer);
                LivingEntity target = c.getTarget();
                if (target != null && c.combat.footwork.keepOut(level, c.tickCount >= nextSwing)) {
                    c.getLookControl().setLookAt(target);
                    c.work.expedition.fightGoing();
                    return;
                }
            }
            super.tick();
            c.work.expedition.fightGoing();
        }
        @Override protected void checkAndPerformAttack(LivingEntity target) {
            if (c.tickCount < nextSwing || !c.isWithinMeleeAttackRange(target) || !c.getSensing().hasLineOfSight(target)) return;
            nextSwing = c.tickCount + c.combat.weaponCooldownTicks();
            c.swing(InteractionHand.MAIN_HAND);
            c.doHurtTarget(target);
        }
    }

    /**
     * With no sword or axe a fist is no fight: a zombie has the better of it (one health a blow, half of them missing, against three). On an expedition
     * or a long goal, where there is work to go on with, he leaves a monster alone and walks off from one that is on him (see {@link StepOffGoal}).
     * Near the player, and with no goal, nothing changes. An ordered hunt is still fought.
     */
    boolean unarmedOnTask() {
        if (c.work.isHunting() || !(c.work.expedition.active() || c.level() instanceof ServerLevel level && CompanionAgenda.goal(level.getServer(), c.ownerId) != null)) return false;
        return !c.equipment.hasMeleeWeapon();
    }

    /** Unarmed on a task, with a monster on him: he walks away from it, the faster of the two, until it is left behind, and the work goes on from there. */
    static final class StepOffGoal extends Goal {
        private static final int GIVE_UP_TICKS = 40;
        final CompanionEntity c;
        private boolean cornered;
        private int retryAt;
        StepOffGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            return c.tickCount >= retryAt && c.level() instanceof ServerLevel level && c.combat.footwork.onHim(level) && c.combat.unarmedOnTask();
        }
        @Override public boolean canContinueToUse() {
            return !cornered && c.level() instanceof ServerLevel level && c.combat.footwork.near(level, CompanionFootwork.SAFE) && c.combat.unarmedOnTask();
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { cornered = false; c.combat.footwork.begin(); }
        @Override public void tick() {
            if (c.level() instanceof ServerLevel level && !c.combat.footwork.withdraw(level)) {
                // Nowhere to step to: the work goes on for a while instead of this goal taking its turn every tick.
                cornered = true;
                retryAt = c.tickCount + GIVE_UP_TICKS;
            }
        }
    }

    int weaponCooldownTicks() { return Math.max(6, Math.min(40, (int) Math.ceil(20 / Math.max(.5, attackSpeed(c.getMainHandItem()))))); }

    static double attackSpeed(net.minecraft.world.item.ItemStack stack) {
        double speed = 4.0;
        for (var entry : stack.getAttributeModifiers().modifiers())
            if (entry.attribute().is(Attributes.ATTACK_SPEED) && entry.slot().test(EquipmentSlot.MAINHAND)
                    && entry.modifier().operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) speed += entry.modifier().amount();
        return speed;
    }

    /** A visible recent attacker of the owner interrupts work without waiting for an LLM. */
    static final class OwnerDefenseGoal extends Goal {
        final CompanionEntity c;
        private int handledTimestamp = Integer.MIN_VALUE;
        private LivingEntity attacker;
        private int timestamp;
        OwnerDefenseGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.TARGET)); }
        @Override public boolean canUse() {
            ServerPlayer player = c.getOwnerPlayer();
            if (player == null || c.inventoryViewers != 0) return false;
            timestamp = player.getLastHurtByMobTimestamp();
            attacker = player.getLastHurtByMob();
            return timestamp != handledTimestamp && player.tickCount - timestamp < 100 && !c.combat.unarmedOnTask()
                    && hostile(attacker) && c.combat.fairTarget(attacker, true) && c.distanceToSqr(attacker) <= 18 * 18 && c.hasLineOfSight(attacker);
        }
        @Override public void start() {
            handledTimestamp = timestamp; c.setTarget(attacker); c.selectWeaponForCombat();
            dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), "owner_defense", "entity", c.getUUID(), "attacker", attacker.getUUID(), "reason", "recent_visible_attack");
        }
        @Override public boolean canContinueToUse() {
            var player = c.getOwnerPlayer();
            return player != null && attacker != null && attacker.isAlive() && c.getTarget() == attacker
                    && c.distanceToSqr(player) <= 24 * 24 && attacker.distanceToSqr(player) <= 18 * 18;
        }
        @Override public void stop() { if (c.getTarget() == attacker) c.setTarget(null); attacker = null; }
    }

    /** A hoglin attacks on sight like any monster, but the game's classes make it an Animal. */
    static boolean hostile(net.minecraft.world.entity.Entity entity) {
        return entity instanceof Monster || entity instanceof Hoglin;
    }

    /**
     * Who may be fought (review 22.10, В1–В2): never the player, his pets or another companion; never the warden. Neutral mobs
     * — endermen, (zombified) piglins, spiders — only once they attack (provoked): hitting a zombified piglin raises the whole group.
     */
    boolean fairTarget(LivingEntity entity, boolean provoked) {
        if (entity == null || !entity.isAlive() || entity == c || entity instanceof Player || entity instanceof CompanionEntity) return false;
        if (c.ownerId != null && entity instanceof net.minecraft.world.entity.OwnableEntity pet && c.ownerId.equals(pet.getOwnerUUID())) return false;
        if (entity instanceof net.minecraft.world.entity.monster.warden.Warden) return false;
        boolean neutral = entity instanceof net.minecraft.world.entity.NeutralMob || entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
                || entity instanceof net.minecraft.world.entity.monster.Spider;
        return !neutral || provoked;
    }

    /** The neutral mob is going for the player or for Oleg himself. */
    boolean provokes(LivingEntity entity) {
        if (!(entity instanceof net.minecraft.world.entity.Mob mob) || mob.getTarget() == null) return false;
        return mob.getTarget() == c || c.ownerId != null && c.ownerId.equals(mob.getTarget().getUUID());
    }

    /**
     * Without a guard order, like a friend (the player 22.10: «когда рядом со мной монстр, он его не бьёт, только если он уже на меня
     * напал»): a monster at the player's side, or one going for the player or for Oleg, is met first. Only while Oleg is near the
     * player; never his own hunt, and never a creeper unless he is on an expedition and fights it with the sword (a hit beside the player
     * may end in its blast — wolves leave them alone too). In an expedition any hostile mob near him is met.
     */
    boolean comesForOwner(net.minecraft.world.entity.LivingEntity target) {
        if (!(c.level() instanceof ServerLevel serverLevel) || c.ownerId == null || c.inventoryViewers != 0 || c.work.isHunting()
                || !hostile(target) || !(target instanceof Mob monster) || monster instanceof net.minecraft.world.entity.monster.Creeper && !c.creeperGuard.fights() || unarmedOnTask()) return false;
        if (!fairTarget(monster, provokes(monster))) return false;
        if (c.work.expedition.engages(monster)) return true;
        // Deep in his own mine, the player far above: a monster that has chosen him is met before its first blow, not after it;
        // waiting for the hit gives a zombie the lead in an even trade.
        if (monster.getTarget() == c && c.work.expedition.underground() && monster.distanceToSqr(c) <= MINE_RANGE * MINE_RANGE) return true;
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(c.ownerId);
        if (owner == null || owner.level() != c.level() || owner.isSpectator() || c.distanceToSqr(owner) > GUARD_RANGE * GUARD_RANGE) return false;
        return monster.distanceToSqr(owner) <= 6 * 6 || monster.distanceToSqr(c) <= 4 * 4
                || (monster.getTarget() == owner || monster.getTarget() == c) && monster.distanceToSqr(owner) <= GUARD_RANGE * GUARD_RANGE;
    }
    /** A flier near the player, while Oleg is near him too: no Monster, so comesForOwner does not see it. */
    boolean fliesAtOwner(net.minecraft.world.entity.LivingEntity target) {
        if (!(c.level() instanceof ServerLevel serverLevel) || c.ownerId == null || c.work.isHunting()
                || !(target instanceof net.minecraft.world.entity.monster.Enemy) || !fairTarget(target, true)) return false;
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(c.ownerId);
        return owner != null && owner.level() == c.level() && !owner.isSpectator() && c.distanceToSqr(owner) <= GUARD_RANGE * GUARD_RANGE
                && target.distanceToSqr(owner) <= FLIER_RANGE * FLIER_RANGE;
    }

    /** How far from the player Oleg still steps in for him, and how far a fight may take him; how close a monster hunting him in his mine is met. */
    private static final int GUARD_RANGE = 16, MINE_RANGE = 8, FLIER_RANGE = 32;

    boolean isThreateningOwner(net.minecraft.world.entity.LivingEntity target) {
        if (!(c.level() instanceof ServerLevel serverLevel) || c.ownerId == null || !hostile(target) || !(target instanceof Mob monster)) return false;
        if (c.work.isHunting()) return c.work.isHuntTarget(target.getUUID());
        if (!fairTarget(monster, provokes(monster))) return false;
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(c.ownerId);
        return owner != null && owner.level() == c.level()
                && (monster.getTarget() == owner || monster.getTarget() == c || monster.distanceToSqr(owner) <= 100.0D || monster.distanceToSqr(c) < 36);
    }
}

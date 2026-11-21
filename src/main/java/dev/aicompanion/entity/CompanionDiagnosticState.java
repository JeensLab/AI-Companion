package dev.aicompanion.entity;

import dev.aicompanion.CompanionDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server-thread snapshots. Stationary is an observation, not proof of a navigation failure. */
final class CompanionDiagnosticState {
    private final CompanionEntity owner;
    private String trace = "";
    private Vec3 previous;
    private int stationarySamples;
    private String previousState = "";

    CompanionDiagnosticState(CompanionEntity owner) { this.owner = owner; }
    void bind(String next) { trace = next; stationarySamples = 0; previous = owner.position(); }
    String trace() { return trace; }

    void tick() {
        if (!CompanionDiagnostics.enabled() || owner.tickCount % 100 != 0) return;
        String state = owner.diagnosticState();
        if (previous != null && owner.position().distanceToSqr(previous) < .09) stationarySamples++;
        else stationarySamples = 0;
        previous = owner.position();
        boolean active = owner.isGatheringTask() || owner.isFollowing() || owner.isProtecting() || owner.isRoaming();
        if (!state.equals(previousState) || active && owner.tickCount % 600 == 0) {
            snapshot("state_snapshot"); previousState = state;
        }
        if (active && stationarySamples >= 2 && stationarySamples % 6 == 2)
            snapshot("stationary_observed");
    }

    String diagnosticState() {
        return "wood=" + owner.gatheringLogs + ",dirt=" + owner.gatheringDirt + ",remaining=" + owner.logsRemaining
                + ",following=" + owner.following + ",protecting=" + owner.protecting + ",roaming=" + owner.roaming
                + ",inventoryViewers=" + owner.inventoryViewers + ",supportSoil=" + owner.supportSoilTask
                + ",pillar=" + owner.temporaryPillar.isActive() + ",cleanup=" + owner.temporaryPillar.isRecovering()
                + ",recovery=" + owner.recovery.active() + ",loot=" + owner.loot.diagnosticState() + ",movementIntent=" + owner.movementIntent
                + ",target=" + (owner.getTarget() == null ? "none" : owner.getTarget().getType().toString() + ":" + owner.getTarget().getId())
                + ",goals=" + owner.goalSelector().getAvailableGoals().stream().filter(goal -> goal.isRunning()).map(goal -> goal.getGoal().getClass().getSimpleName()).toList()
                + ",woodBreaking=" + owner.woodBreaking.diagnosticState() + ",dirtBreaking=" + owner.dirtBreaking.diagnosticState()
                + ",work=" + owner.work.diagnosticState();
    }

    void snapshot(String event) {
        var path = owner.getNavigation().getPath();
        var player = owner.getOwnerPlayer();
        CompanionDiagnostics.event(trace, event, "entity", owner.getUUID(), "dimension", owner.level().dimension().location(),
                "tick", owner.level().getGameTime(), "position", owner.position(),
                "playerDistance", player == null ? -1 : owner.distanceTo(player),
                "state", owner.diagnosticState(), "activity", owner.getActivityForAi(), "places", owner.getPlacesForAi(), "result", owner.getLastTaskResult(),
                "lastSuccess", owner.didLastTaskSucceed(), "inventory", inventory(), "equipment", equipment(),
                "navigationDone", owner.getNavigation().isDone(), "pathReachable", path != null && path.canReach(),
                "pathTarget", path == null ? "none" : path.getTarget(), "pathNode", path == null ? -1 : path.getNextNodeIndex(),
                "pathNodes", path == null ? 0 : path.getNodeCount(), "stationarySeconds", stationarySamples * 5,
                "stage", owner.work.expedition.stageName(), "cavePhase", owner.work.expedition.cavePhase(), "air", owner.getAirSupply());
    }

    /** What hit him, from where and what it left of him: the hits of the mine are read back from this, a creeper's blast included. */
    void hurt(DamageSource source, float amount) {
        if (!CompanionDiagnostics.enabled()) return;
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        Vec3 from = attacker != null ? attacker.position() : source.getSourcePosition();
        CompanionDiagnostics.event(trace, "companion_hurt", "entity", owner.getUUID(), "tick", owner.level().getGameTime(),
                "source", source.getMsgId(), "attacker", attacker == null ? "none" : attacker.getType().getDescriptionId(),
                "damage", amount, "health", owner.getHealth(), "position", owner.blockPosition(),
                "sourcePosition", from == null ? "none" : BlockPos.containing(from), "distance", from == null ? -1 : owner.position().distanceTo(from),
                "stage", owner.work.expedition.stageName(), "cavePhase", owner.work.expedition.cavePhase(), "air", owner.getAirSupply(),
                "eating", owner.eatTicks > 0, "goals", owner.runningGoals());
    }

    /** A blow he struck, or swung and missed: with what, at whom and what it left of the foe; the pace of a fight is read back from this. */
    void struck(Entity target, boolean hit) {
        if (!CompanionDiagnostics.enabled()) return;
        float left = target instanceof net.minecraft.world.entity.LivingEntity living ? living.getHealth() : -1;
        CompanionDiagnostics.event(trace, "companion_strike", "entity", owner.getUUID(), "tick", owner.level().getGameTime(),
                "weapon", BuiltInRegistries.ITEM.getKey(owner.getMainHandItem().getItem()).toString(), "target", target.getType().getDescriptionId(),
                "hit", hit, "left", left, "health", owner.getHealth(), "distance", owner.distanceTo(target), "stage", owner.work.expedition.stageName(),
                "targetInvulnerable", target.invulnerableTime, "targetHurtTime", target instanceof net.minecraft.world.entity.LivingEntity living ? living.hurtTime : -1,
                "targetInWall", target.isInWall(), "targetAt", target.position(), "at", owner.position(), "eating", owner.eatTicks > 0, "pushable", target.isPushable(), "goals", owner.runningGoals(),
                "targetTicks", target.tickCount, "targetTicking", target.level() instanceof net.minecraft.server.level.ServerLevel level && level.isPositionEntityTicking(target.blockPosition()),
                "ownTicking", owner.level() instanceof net.minecraft.server.level.ServerLevel own && own.isPositionEntityTicking(owner.blockPosition()));
    }

    void died(DamageSource source) {
        if (!CompanionDiagnostics.enabled()) return;
        Entity attacker = source.getEntity();
        CompanionDiagnostics.event(trace, "death", "entity", owner.getUUID(), "tick", owner.level().getGameTime(),
                "source", source.getMsgId(), "attacker", attacker == null ? "none" : attacker.getType().getDescriptionId(), "position", owner.blockPosition(),
                "stage", owner.work.expedition.stageName(), "cavePhase", owner.work.expedition.cavePhase(), "air", owner.getAirSupply());
    }

    private String inventory() {
        var entries = new java.util.ArrayList<String>();
        for (int i = 0; i < owner.getContainerSize(); i++) if (!owner.getItem(i).isEmpty()) entries.add(i + ":" + item(owner.getItem(i)));
        return entries.toString();
    }
    private String equipment() {
        var entries = new java.util.ArrayList<String>();
        for (EquipmentSlot slot : EquipmentSlot.values()) entries.add(slot.getName() + ":" + item(owner.getItemBySlot(slot)));
        return entries.toString();
    }
    private static String item(ItemStack stack) {
        return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount()
                + (stack.isDamageableItem() ? " damage=" + stack.getDamageValue() + "/" + stack.getMaxDamage() : "");
    }
}

package dev.aicompanion.entity;

import dev.aicompanion.CompanionDiagnostics;
import net.minecraft.core.registries.BuiltInRegistries;
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
                "pathNodes", path == null ? 0 : path.getNodeCount(), "stationarySeconds", stationarySamples * 5);
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

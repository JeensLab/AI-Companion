package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMarkers;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * What the client knows of the player's named markers: their names, for recognising a phrase without a model request,
 * and whether the feature is on. Refreshed by every settings sync from the server, which owns the list.
 * <p>
 * The settings screen reads the list itself through {@code CompanionMarkerBoard}; this copy only feeds recognition, so
 * a stale name costs a walk, never a wrong marker.
 */
public final class CompanionMarkerView {
    /**
     * One read of the copy: the names and the switch as the server published them together. Two separate fields could
     * pair a new list with the old switch, which would recognise a name of a feature that is off, or miss one that is on.
     */
    public record Snapshot(List<String> names, boolean enabled) {
        public Snapshot { names = List.copyOf(names); }
    }

    private static volatile Snapshot state = new Snapshot(List.of(), true);

    private CompanionMarkerView() { }

    /** The names and the switch from the same publication: route and clarify a phrase against this pair only. */
    public static Snapshot snapshot() { return state; }

    public static List<String> names() { return state.names(); }

    public static boolean enabled() { return state.enabled(); }

    static void clear() { state = new Snapshot(List.of(), true); }

    /** Publishes the copy as one immutable snapshot, so a reader never sees half of a settings change. */
    public static void deliver(List<String> known, boolean on) { state = new Snapshot(known, on); }

    /**
     * Runs on the server thread after the settings screen changed the list: the chat must recognise the new names at
     * once, not at the next slow sync.
     */
    public static void refresh(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null) return;
        List<String> known = new ArrayList<>();
        for (CompanionMarkers.Marker marker : CompanionMarkers.get(server).all(player.getUUID())) known.add(marker.name());
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        deliver(List.copyOf(known), companion == null ? state.enabled() : companion.markersEnabled());
    }

    /** Reads the names from the world on the server thread, with the switch the companion was given. */
    public static void pull(ServerPlayer player, boolean on, BiConsumer<List<String>, Boolean> to) {
        var server = player.level().getServer();
        if (server == null) { to.accept(List.of(), on); return; }
        // The store is the server's: its list is copied there, never on the client thread that reads the copy.
        server.execute(() -> {
            CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
            List<String> known = new ArrayList<>();
            if (companion != null && companion.getOwnerId() != null)
                for (CompanionMarkers.Marker marker : CompanionMarkers.get(server).all(companion.getOwnerId()))
                    known.add(marker.name());
            to.accept(List.copyOf(known), on);
        });
    }
}

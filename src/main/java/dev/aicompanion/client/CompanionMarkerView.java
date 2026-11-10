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
    private static volatile List<String> names = List.of();
    private static volatile boolean enabled = true;

    private CompanionMarkerView() { }

    public static List<String> names() { return names; }

    public static boolean enabled() { return enabled; }

    static void clear() { names = List.of(); enabled = true; }

    /** Sets the copy; both fields are volatile, so the server thread may deliver after a settings change too. */
    static void deliver(List<String> known, boolean on) { names = List.copyOf(known); enabled = on; }

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
        deliver(List.copyOf(known), companion == null ? enabled : companion.markersEnabled());
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

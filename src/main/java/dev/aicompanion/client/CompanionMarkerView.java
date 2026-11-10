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

    /** Runs on the client thread: the names and the switch of this sync. */
    static void deliver(List<String> known, boolean on) { names = List.copyOf(known); enabled = on; }

    /** Runs on the server thread: reads the names from the world, with the switch the companion was given. */
    public static void pull(ServerPlayer player, boolean on, BiConsumer<List<String>, Boolean> to) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        List<String> known = new ArrayList<>();
        if (companion != null && companion.getOwnerId() != null && player.level().getServer() != null)
            for (CompanionMarkers.Marker marker : CompanionMarkers.get(player.level().getServer()).all(companion.getOwnerId()))
                known.add(marker.name());
        to.accept(List.copyOf(known), on);
    }
}

package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The markers as the settings screen shows them: named places of this player, oldest first, with the id that renames,
 * moves or deletes one. The list is read even when the feature is switched off, so the player can turn it back on and
 * still see what is remembered.
 * <p>
 * Immutable snapshots only: the screen is client-side and reads these through the server.
 */
public final class CompanionMarkerBoard {
    private CompanionMarkerBoard() { }

    /** One marker of the list: what it is called, where it is, and the id the buttons name. */
    public record Place(int number, UUID id, String name, String world, int x, int y, int z) { }

    /** A marker added where the player stands, and the honest line about it (including the oldest one it evicted). */
    public record AddResult(Place place, String message) { }

    /** Every marker of this player, oldest first. */
    public static List<Place> places(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null) return List.of();
        List<CompanionMarkers.Marker> markers = CompanionMarkers.get(server).all(player.getUUID());
        List<Place> places = new ArrayList<>();
        for (int index = 0; index < markers.size(); index++) {
            CompanionMarkers.Marker marker = markers.get(index);
            places.add(new Place(index + 1, marker.id(), marker.name(), CompanionMarkers.worldName(marker.dimension()),
                    marker.pos().getX(), marker.pos().getY(), marker.pos().getZ()));
        }
        return List.copyOf(places);
    }

    /** Renames one marker; false when the name is empty, longer than 24 or already taken by another marker. */
    public static boolean rename(ServerPlayer player, UUID markerId, String name) {
        var server = player.level().getServer();
        if (server == null || markerId == null || !CompanionMarkers.get(server).rename(player.getUUID(), markerId, name)) return false;
        refresh(player);
        return true;
    }

    /** Forgets one marker by the id the screen showed; only that one goes. */
    public static boolean forget(ServerPlayer player, UUID markerId) {
        var server = player.level().getServer();
        if (server == null || markerId == null || !CompanionMarkers.get(server).remove(player.getUUID(), markerId)) return false;
        refresh(player);
        return true;
    }

    /** Moves a marker to the given point, clamped to the world border and the build height of its dimension. */
    public static boolean move(ServerPlayer player, UUID markerId, int x, int y, int z) {
        var server = player.level().getServer();
        if (server == null || markerId == null) return false;
        CompanionMarkers store = CompanionMarkers.get(server);
        CompanionMarkers.Marker marker = store.byId(player.getUUID(), markerId);
        if (marker == null) return false;
        ServerLevel level = server.getLevel(marker.dimension());
        if (level == null) return false;
        boolean moved = store.move(player.getUUID(), markerId, clamp(level, x, y, z));
        if (moved) refresh(player);
        return moved;
    }

    /** Saves a marker where the player stands now; null when the name is refused. */
    public static AddResult addHere(ServerPlayer player, String name) {
        var server = player.level().getServer();
        if (server == null) return null;
        String clean = CompanionMarkerNames.clean(name);
        if (!CompanionMarkerNames.valid(clean)) return null;
        CompanionMarkers.Added added = CompanionMarkers.get(server)
                .add(player.getUUID(), clean, player.level().dimension(), player.blockPosition(), player.level().getGameTime());
        List<Place> places = places(player);
        Place place = null;
        for (int index = 0; index < places.size(); index++) if (places.get(index).id().equals(added.marker().id()))
            place = new Place(index + 1, added.marker().id(), added.marker().name(), CompanionMarkers.worldName(added.marker().dimension()),
                    added.marker().pos().getX(), added.marker().pos().getY(), added.marker().pos().getZ());
        if (place == null) return null;
        String message = added.moved() ? "Метка «" + place.name() + "» теперь здесь: " + place.x() + ", " + place.y() + ", " + place.z() + "."
                : "Запомнил место «" + place.name() + "» здесь: " + place.x() + ", " + place.y() + ", " + place.z() + ".";
        if (added.evicted() != null)
            message += " Меток было " + CompanionMarkers.MOST + ", самую старую «" + added.evicted().name() + "» забыл.";
        refresh(player);
        return new AddResult(place, message);
    }

    /** The model's own line of known places must hear of the change at once, not at the next slow refresh. */
    private static void refresh(ServerPlayer player) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        if (companion != null) companion.refreshPlaces();
    }

    /** The point a marker may have: inside the world border and the build height of its dimension. */
    static BlockPos clamp(Level level, int x, int y, int z) {
        WorldBorder border = level.getWorldBorder();
        int clampedX = Math.clamp(x, (int) Math.floor(border.getMinX()), (int) Math.ceil(border.getMaxX()) - 1);
        int clampedZ = Math.clamp(z, (int) Math.floor(border.getMinZ()), (int) Math.ceil(border.getMaxZ()) - 1);
        int clampedY = Math.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        return new BlockPos(clampedX, clampedY, clampedZ);
    }
}

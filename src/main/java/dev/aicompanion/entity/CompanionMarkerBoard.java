package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

import dev.aicompanion.client.CompanionMarkerView;

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

    /** A marker added where the player stands, and the honest line about it (including the oldest one it evicted);
     * {@code place} is null when nothing was added, and the line says the cause. */
    public record AddResult(Place place, String message) { }

    /** The outcome of the screen's save: the rename first, the move only when the rename was accepted, and the honest
     * line for the cause — a vanished marker and a name that did not fit are said apart, never as one garbled sentence. */
    public record Saved(boolean renamed, boolean moved, String message) { }

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

    /** Saves a rename and a move together, as the settings screen does: a refused name leaves the point untouched. */
    public static Saved save(ServerPlayer player, UUID markerId, String name, int x, int y, int z) {
        if (player == null) return new Saved(false, false, "Тебя нет на сервере — метку сохранить не удалось.");
        var server = player.level().getServer();
        if (server == null) return new Saved(false, false, "Мир не загружен — метку сохранить не удалось.");
        CompanionMarkers store = CompanionMarkers.get(server);
        String clean = CompanionMarkerNames.clean(name);
        // The store answers the cause itself: a vanished marker, an empty, long or coordinate name, or a taken one.
        CompanionMarkers.Renamed outcome = store.renameChecked(player.getUUID(), markerId, clean);
        if (outcome != CompanionMarkers.Renamed.RENAMED) return new Saved(false, false, refusal(outcome, store, player, markerId, clean));
        boolean moved = move(player, markerId, x, y, z);
        return new Saved(true, moved, moved ? "Метка сохранена." : "Название сохранено, но координаты сохранить не удалось.");
    }

    /** The line for a refused save, one cause at a time: «метка не найдена» and «имя не подошло» are never one sentence. */
    private static String refusal(CompanionMarkers.Renamed outcome, CompanionMarkers store, ServerPlayer player, UUID markerId, String clean) {
        if (outcome == CompanionMarkers.Renamed.NOT_FOUND) return "Такой метки уже нет: возможно, её удалили.";
        if (outcome == CompanionMarkers.Renamed.EMPTY_NAME) return "Название метки не может быть пустым.";
        if (outcome == CompanionMarkers.Renamed.TOO_LONG) return "Название метки — до " + CompanionMarkerNames.MAX + " символов.";
        if (outcome == CompanionMarkers.Renamed.COORDINATES) return "«" + clean + "» — это координаты, а не название.";
        for (CompanionMarkers.Marker other : store.all(player.getUUID()))
            if (!other.id().equals(markerId) && other.name().equalsIgnoreCase(clean))
                return "Такое название уже есть у метки «" + other.name() + "».";
        return "Такое название уже есть у другой метки.";
    }

    /** Adds a marker where the player stands; {@code place} is null when nothing was added, and the line says the cause. */
    public static AddResult addHere(ServerPlayer player, String name) {
        if (player == null) return new AddResult(null, "Тебя нет на сервере — метку добавить не удалось.");
        var server = player.level().getServer();
        if (server == null) return new AddResult(null, "Мир не загружен — метку добавить не удалось.");
        String clean = CompanionMarkerNames.clean(name);
        CompanionMarkers.Added added = CompanionMarkers.get(server)
                .add(player.getUUID(), clean, player.level().dimension(), player.blockPosition(), player.level().getGameTime());
        // The store answers the cause itself: an empty, long or coordinate name is said as it refused it.
        if (added.refused()) return new AddResult(null, added.refusal());
        List<Place> places = places(player);
        Place place = null;
        for (int index = 0; index < places.size(); index++) if (places.get(index).id().equals(added.marker().id()))
            place = new Place(index + 1, added.marker().id(), added.marker().name(), CompanionMarkers.worldName(added.marker().dimension()),
                    added.marker().pos().getX(), added.marker().pos().getY(), added.marker().pos().getZ());
        if (place == null) return new AddResult(null, "Метка «" + added.marker().name() + "» сохранена, но в списке её нет.");
        String message = added.moved() ? "Метка «" + place.name() + "» теперь здесь: " + place.x() + ", " + place.y() + ", " + place.z() + "."
                : "Запомнил место «" + place.name() + "» здесь: " + place.x() + ", " + place.y() + ", " + place.z() + ".";
        if (added.evicted() != null)
            message += " Меток было " + CompanionMarkers.MOST + ", самую старую «" + added.evicted().name() + "» забыл.";
        refresh(player);
        return new AddResult(place, message);
    }

    /** The model's own line of known places must hear of the change at once, and so must the chat's recognition copy. */
    private static void refresh(ServerPlayer player) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        if (companion != null) companion.refreshPlaces();
        // The names the client recognises without a model request follow the change now, not at the next slow sync.
        CompanionMarkerView.refresh(player);
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

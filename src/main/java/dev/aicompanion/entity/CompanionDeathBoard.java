package dev.aicompanion.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The places the owner still remembers, as the death settings screen shows them: dearest first, numbered from one. The
 * number here is the number the companion says, so the player reads the place in the list and hears the same place.
 * <p>
 * Immutable snapshots only: the screen is client-side and reads these through the server, never a live server entity.
 */
public final class CompanionDeathBoard {
    private CompanionDeathBoard() { }

    /** One remembered place: its number in the list, the world and the point, and the id the delete names. */
    public record Place(int number, UUID id, String world, int x, int y, int z) { }

    /** The places this player's companion still remembers; empty when there is nothing left to go back for. */
    public static List<Place> places(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null) return List.of();
        List<CompanionDeathSpots.Spot> ranked = CompanionDeathSpots.get(server).ranked(player.getUUID());
        List<Place> places = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            CompanionDeathSpots.Spot spot = ranked.get(index);
            places.add(new Place(index + 1, spot.id(), worldName(spot.dimension()),
                    spot.pos().getX(), spot.pos().getY(), spot.pos().getZ()));
        }
        return List.copyOf(places);
    }

    /** Forgetting one place of the list, by the id the screen showed: only that place goes, a neighbour stays. */
    public static boolean forget(ServerPlayer player, UUID spotId) {
        var server = player.level().getServer();
        if (server == null || spotId == null) return false;
        CompanionDeathSpots store = CompanionDeathSpots.get(server);
        if (store.byId(player.getUUID(), spotId) == null) return false;
        store.taken(player.getUUID(), spotId);
        return true;
    }

    /** The world of a place as the player reads it in the settings: the three dimensions by their everyday names. */
    private static String worldName(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.OVERWORLD)) return "world";
        if (dimension.equals(Level.NETHER)) return "nether";
        if (dimension.equals(Level.END)) return "end";
        return dimension.location().getPath();
    }
}

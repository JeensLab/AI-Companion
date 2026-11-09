package dev.aicompanion.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.phys.Vec3;

/**
 * Riding with the player (idea 5949G «Лодки», #105, decided 09.11): the player in a boat or raft with a seat free, he comes and gets
 * in as a player does, by a click within reach, and the player steers; the player out of it, he steps out too, onto land where the
 * boat has any beside it. A boat without the player is not his to sit in (the game seats a mob in an empty boat it bumps into); the
 * boat of his fall (CompanionBoatClutch) keeps its own rules. Off in the settings: as before, he swims after the boat.
 */
final class CompanionBoatRide {
    /** A player's reach for an entity, as for the boat of his fall: a boat is got into by clicking it. */
    private static final double REACH = 3.0;
    static final String NO_SEAT = "Места в лодке нет — поплыву рядом.";

    private final CompanionEntity c;
    /** The boat he said had no seat for him, while the player sits in it: said once a ride. */
    private Boat toldNoSeat;

    CompanionBoatRide(CompanionEntity c) { this.c = c; }

    void tick() {
        if (!c.boatRide()) return;
        ServerPlayer owner = c.getOwnerPlayer();
        if (c.getVehicle() instanceof Boat boat) {
            if (!c.boatClutch.holds(boat) && (owner == null || owner.getVehicle() != boat)) stepOut(boat, owner);
            return;
        }
        if (owner == null || !(owner.getVehicle() instanceof Boat boat) || !goesAfterPlayer()) { toldNoSeat = null; return; }
        if (!seatFree(boat)) {
            if (toldNoSeat != boat) { toldNoSeat = boat; c.autonomySay(NO_SEAT, false); event("boat_ride_no_seat", boat); }
            return;
        }
        if (boat.getBoundingBox().distanceToSqr(c.getEyePosition()) <= REACH * REACH && c.startRiding(boat)) event("boat_ride_in", boat);
    }

    /** The player's boat with a seat free for him, while he goes after the player: the follow takes him to it from any distance. */
    Boat boatToJoin(ServerPlayer owner) {
        return c.boatRide() && !c.isPassenger() && owner.getVehicle() instanceof Boat boat && seatFree(boat) && goesAfterPlayer() ? boat : null;
    }

    /** Following or guarding the player, and nothing of his own on: not «стой», his workplace, an order, a fight, his drops, his bag open. */
    private boolean goesAfterPlayer() {
        return (c.following || c.protecting) && !c.autonomy.staysAtWork() && !c.gazeBusy() && !c.boatClutch.breaking();
    }

    // ponytail: the seats of the game's boats (a chest one has one); a modded boat with other seats is refused by startRiding anyway.
    private static boolean seatFree(Boat boat) { return boat.getPassengers().size() < (boat instanceof ChestBoat ? 1 : 2); }

    /**
     * Out as a player steps out, where he looks (Boat.getDismountLocationForPassenger): he looks round for the side with land to stand
     * on, the one nearest the player. None (open water): out towards the player, as the game puts him.
     */
    private void stepOut(Boat boat, ServerPlayer owner) {
        Vec3 aim = owner != null ? owner.position() : boat.position();
        Vec3 fallback = new Vec3(boat.getX(), boat.getBoundingBox().maxY, boat.getZ());
        float best = (float) Math.toDegrees(Math.atan2(boat.getX() - aim.x, aim.z - boat.getZ()));
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            c.setYRot(i * 22.5F);
            Vec3 spot = boat.getDismountLocationForPassenger(c);
            if (spot.equals(fallback) || spot.distanceToSqr(aim) >= nearest) continue;
            nearest = spot.distanceToSqr(aim);
            best = i * 22.5F;
        }
        c.setYRot(best);
        c.stopRiding();
        event("boat_ride_out", boat, "land", nearest < Double.MAX_VALUE, "player", owner == null ? "" : owner.blockPosition().toShortString());
    }

    private void event(String name, Boat boat, Object... more) {
        Object[] fields = {"at", c.blockPosition().toShortString(), "boat", boat.blockPosition().toShortString(), "riders", boat.getPassengers().size()};
        Object[] all = java.util.Arrays.copyOf(fields, fields.length + more.length);
        System.arraycopy(more, 0, all, fields.length, more.length);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, all);
    }
}

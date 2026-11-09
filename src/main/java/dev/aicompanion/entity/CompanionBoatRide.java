package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Riding with the player (idea 5949G «Лодки», #105, decided 09.11): the player in a boat or raft with a seat free, he comes and gets
 * in as a player does, by a click within reach, and the player steers; the player out of it, he steps out too, onto land beside the
 * boat where there is any. A boat without the player is not his to sit in: the game seats a mob in an empty boat it bumps into, and he
 * is not seated (canRide); the boat of his fall (CompanionBoatClutch) keeps its own rules. Off in the settings: as before, he swims
 * after the boat.
 */
final class CompanionBoatRide {
    /** A player's reach for an entity, as for the boat of his fall: a boat is got into by clicking it. */
    private static final double REACH = 3.0;
    /** How far from the boat he steps out: off its side onto the bank, as a player does. */
    private static final double STEP = 2.5;
    static final String NO_SEAT = "Места в лодке нет — поплыву рядом.";

    private final CompanionEntity c;
    /**
     * The player's boat he got into: he gets out of it with the player even if the switch was turned off on the way.
     * ponytail: not kept over a reload; switched off, reloaded and then left by the player, he stays in it till an order.
     */
    private Boat joined;
    /** The player's boat with no seat for him, while the player sits in it and no one gets out: said once a ride, and not gone to. */
    private Boat noSeat;
    private int noSeatRiders;

    CompanionBoatRide(CompanionEntity c) { this.c = c; }

    void tick() {
        ServerPlayer owner = c.getOwnerPlayer();
        Boat theirs = owner != null && owner.getVehicle() instanceof Boat boat ? boat : null;
        if (noSeat != theirs || noSeat != null && noSeat.getPassengers().size() < noSeatRiders) noSeat = null;
        if (c.getVehicle() instanceof Boat seat) {
            if (seat != theirs && !c.boatClutch.holds(seat) && (c.boatRide() || seat == joined)) stepOut(seat, owner);
            return;
        }
        joined = null;
        if (!c.boatRide() || theirs == null || theirs == noSeat || !goesAfterPlayer()) return;
        if (!seatFree(theirs)) { noRoom(theirs); return; }
        if (theirs.getBoundingBox().distanceToSqr(c.getEyePosition()) > REACH * REACH) return;
        if (c.startRiding(theirs)) { joined = theirs; event("boat_ride_in", theirs); }
        // A boat going under, or another mod's with fewer seats: the game does not seat him.
        else if (!c.justGotOut()) noRoom(theirs);
    }

    /** The player's boat with a seat free for him, while he goes after the player: the follow takes him to it from any distance. */
    Boat boatToJoin(ServerPlayer owner) {
        return c.boatRide() && !c.isPassenger() && owner.getVehicle() instanceof Boat boat && boat != noSeat && seatFree(boat)
                && goesAfterPlayer() ? boat : null;
    }

    /** Switched on, he gets into a boat only with the player in it, or into the boat of his fall. */
    boolean mayRide(Entity vehicle) {
        if (!(vehicle instanceof Boat boat) || !c.boatRide() || c.boatClutch.ours(boat)) return true;
        ServerPlayer owner = c.getOwnerPlayer();
        return owner != null && owner.getVehicle() == boat;
    }

    /**
     * Following or guarding the player, and nothing of his own on: not «стой», his workplace, an order, a fight, his drops, his bag
     * open, his things after a death, the water of his bucket, the boat of his fall.
     */
    private boolean goesAfterPlayer() {
        return (c.following || c.protecting) && !c.autonomy.staysAtWork() && !c.gazeBusy() && !c.deathTrip.active()
                && !c.waterBucket.goesForWater() && !c.boatClutch.breaking();
    }

    // ponytail: the seats of the game's boats (a chest one has one); another mod's boat with fewer is found out by getting in.
    private static boolean seatFree(Boat boat) { return boat.getPassengers().size() < (boat instanceof ChestBoat ? 1 : 2); }

    private void noRoom(Boat boat) {
        noSeat = boat;
        noSeatRiders = boat.getPassengers().size();
        c.autonomySay(NO_SEAT, false);
        event("boat_ride_no_seat", boat);
    }

    /**
     * Out as a player gets out by the shore: onto land beside the boat, level with the water or a block over it (a player steps onto
     * the bank from the boat), the place nearest the player; no land within a step, into the water beside it, towards the player.
     * The game's own place (Boat.getDismountLocationForPassenger) is looked for only where he faces and level with the boat: by a
     * bank a block high, the most common one, it was the boat's top, and an empty boat takes whoever stands at it (review 09.11).
     */
    private void stepOut(Boat boat, ServerPlayer owner) {
        Vec3 aim = owner != null ? owner.position() : boat.position();
        Vec3 land = null, water = null;
        BlockPos at = boat.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 2, 2))) {
            double dx = pos.getX() + .5 - boat.getX(), dz = pos.getZ() + .5 - boat.getZ();
            boolean dry = c.level().getFluidState(pos).isEmpty();
            if (dx * dx + dz * dz > STEP * STEP || !dry && pos.getY() != at.getY()) continue;
            Vec3 spot = dry ? DismountHelper.findSafeDismountLocation(c.getType(), c.level(), pos, true) : Vec3.atBottomCenterOf(pos);
            if (spot == null || !stepTo(boat, spot)) continue;
            if (dry && (land == null || spot.distanceToSqr(aim) < land.distanceToSqr(aim))) land = spot;
            if (!dry && (water == null || spot.distanceToSqr(aim) < water.distanceToSqr(aim))) water = spot;
        }
        Vec3 out = land != null ? land : water;
        c.stopRiding();
        if (out != null) c.dismountTo(out.x, out.y, out.z);
        event("boat_ride_out", boat, "land", land != null, "player", owner == null ? "" : owner.blockPosition().toShortString());
    }

    /**
     * A place he steps to from the seat as a player can: clear of the boat, no higher than a jump from its top (a bank a block over
     * the water, a ledge a block over ice), and over its side, not through a wall or a fence (review 09.11: a canal between walls).
     */
    private boolean stepTo(Boat boat, Vec3 spot) {
        var box = c.getType().getDimensions().makeBoundingBox(spot);
        if (spot.y > boat.getBoundingBox().minY + 1.8 || box.intersects(boat.getBoundingBox()) || !DismountHelper.canDismountTo(c.level(), c, box)) return false;
        var sight = new ClipContext(c.getEyePosition(), spot.add(0, c.getBbHeight() / 2, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c);
        return c.level().clip(sight).getType() == HitResult.Type.MISS;
    }

    private void event(String name, Boat boat, Object... more) {
        Object[] fields = {"at", c.blockPosition().toShortString(), "boat", boat.blockPosition().toShortString(), "riders", boat.getPassengers().size()};
        Object[] all = java.util.Arrays.copyOf(fields, fields.length + more.length);
        System.arraycopy(more, 0, all, fields.length, more.length);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, all);
    }
}

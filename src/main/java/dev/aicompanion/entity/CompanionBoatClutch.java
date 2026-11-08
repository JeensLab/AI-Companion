package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The speedrunners' boat, for a fall the water bucket does not catch: in the Nether, where water boils away, or with no water bucket in
 * his bag (decided 08.11). Falling to a landing that would hurt, with a boat in his bag, he puts it down where he
 * lands and gets into it in the tick he lands, as a player's two clicks do; a rider's fall is reset (LivingEntity.rideTick), so the
 * landing costs nothing. A moment in it, he steps out, breaks it and picks it up. The bucket goes first: water it poured in this tick
 * is about his body as he lands, so no landing is found for the boat.
 */
final class CompanionBoatClutch {
    /** A player's reach for an entity, the entity interaction range: a boat is got into by clicking it. */
    private static final double SEAT_REACH = 3;
    /** How long he sits in it before he steps out (ticks), so that he is seen in it. */
    private static final int SIT = 10;
    /** How often he hits the boat (ticks), as a player's fist recovers; and the hits he gives it before he gives it up. */
    private static final int HIT_EVERY = 5, HITS = 20;
    /** How long the broken boat may lie before he gives it up (ticks): ten seconds; and how often a way to it is looked for. */
    private static final int PICK = 200, AGAIN = 10;
    /** He says what the boat did at most this often (ticks), as of the bucket. */
    private static final int TALK = 1200;
    /** Whose clutch a boat is, kept with the boat: one he sits in after a reload is still his to take back. */
    private static final String MARK = "aicompanion:clutch_boat";
    private static final String LEFT = "Лодку не забрал, осталась там.";
    private static final List<String> SAVED = List.of("Лодка лучше парашюта, видал?", "Спидранерская лодка — и ни царапины.",
            "Лодочный клатч, как у спидранеров.");

    private final CompanionEntity c;
    /** The boat of his last clutch, until it is broken or left; then the boat it dropped, until it is in his bag or left. */
    private Boat boat;
    private ItemEntity drop;
    /** When he sat down in it, then when he last hit it, then when it broke; and how many hits it has had. */
    private long at;
    private int hits;
    private long lookedAt, saidAt = Long.MIN_VALUE / 2;

    CompanionBoatClutch(CompanionEntity c) { this.c = c; }

    /** Every server tick, after the bucket's: no order or «стоп» holds a reflex back. */
    void tick() {
        if (!(c.level() instanceof ServerLevel level) || !c.isAlive()) return;
        if (boat == null && c.getVehicle() instanceof Boat seat && ours(seat)) { boat = seat; at = level.getGameTime(); } // after a reload
        if (boat != null) settle(level);
        else if (drop != null) lying(level);
        if (c.onGround() || c.isInWater() || c.isPassenger() || c.isFallFlying() || c.getDeltaMovement().y >= 0) return;
        int slot = boatSlot();
        if (slot < 0) return;
        CompanionWaterBucket.Landing landing = c.waterBucket.landing(level);
        if (landing == null) return;
        float loss = c.waterBucket.loss(level, landing);
        // The bucket's measure: two hearts and more, or his death (decided 05.11).
        if (loss < CompanionWaterBucket.WORTH && loss < c.getHealth()) return;
        put(level, slot, landing, loss);
    }

    private boolean ours(Boat seat) {
        var data = seat.getPersistentData();
        return data.hasUUID(MARK) && data.getUUID(MARK).equals(c.getUUID());
    }

    private int boatSlot() {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).getItem() instanceof BoatItem) return slot;
        return -1;
    }

    /**
     * Puts the boat down where he lands, as a player's goes onto the block clicked (BoatItem), and gets into it. Not where it has no
     * room, nor in lava, nor beyond a player's reach for getting in: then it stays in his bag. Within that reach of the boat the block
     * under it is within a player's reach for a block too. His own body is left out of the room it needs: a player puts the boat down a
     * tick before, still above it, and gets in as he comes down; here both are done in the landing tick.
     */
    private void put(ServerLevel level, int slot, CompanionWaterBucket.Landing landing, float loss) {
        ItemStack stack = c.getItem(slot);
        Vec3 spot = new Vec3(landing.box().getCenter().x, landing.box().minY, landing.box().getCenter().z);
        Boat made = boatOf(level, stack, spot);
        if (made == null) return;
        made.setYRot(c.getYRot());
        AABB box = made.getBoundingBox();
        if (box.distanceToSqr(c.getEyePosition()) > SEAT_REACH * SEAT_REACH || !level.noBlockCollision(made, box)
                || !level.getEntities(made, box, other -> other != c && made.canCollideWith(other)).isEmpty()
                || !level.getWorldBorder().isWithinBounds(box) || level.containsAnyLiquid(box)) return;
        if (!level.addFreshEntity(made)) return;
        if (!c.startRiding(made)) { made.discard(); return; }
        if (boat != null || drop != null) leave("again");
        made.getPersistentData().putUUID(MARK, c.getUUID());
        level.gameEvent(c, GameEvent.ENTITY_PLACE, spot);
        c.animateAction(1, landing.on(), stack);
        c.removeItem(slot, 1);
        // Seated: the rest of this tick's move is not his to make, and a rider's fall is reset each tick (LivingEntity.rideTick).
        c.setDeltaMovement(Vec3.ZERO);
        c.resetFallDistance();
        boat = made;
        at = level.getGameTime();
        hits = 0;
        event("boat_clutch_seated", "loss", loss);
        if (at - saidAt >= TALK) {
            saidAt = at;
            c.autonomySay(SAVED.get(c.getRandom().nextInt(SAVED.size())), false);
        }
    }

    /** The boat this item puts down, of its wood and with a chest if it has one, as BoatItem makes it; null for another item. */
    private static Boat boatOf(ServerLevel level, ItemStack stack, Vec3 spot) {
        for (boolean chest : new boolean[] {false, true}) for (Boat.Type type : Boat.Type.values()) {
            Boat boat = chest ? new ChestBoat(level, spot.x, spot.y, spot.z) : new Boat(level, spot.x, spot.y, spot.z);
            boat.setVariant(type);
            if (boat.getDropItem() != stack.getItem()) continue;
            EntityType.<Boat>createDefaultStackConfig(level, stack, null).accept(boat);
            boat.setVariant(type); // the item's wood over its entity data, as BoatItem sets it last
            return boat;
        }
        return null;
    }

    /** His boat: a moment in it, then out and hit till it breaks. Gone, taken by someone or out of his reach, it is not his to take. */
    private void settle(ServerLevel level) {
        long now = level.getGameTime();
        if (boat.isRemoved()) { event("boat_clutch_gone"); boat = null; return; }
        // A player got into it: it is their ride now, no more his to take back, and he steps out (the first rider steers it). A mob
        // that got in is put out as it breaks.
        if (boat.getPassengers().stream().anyMatch(rider -> rider instanceof Player)) {
            event("boat_clutch_taken");
            boat.getPersistentData().remove(MARK);
            if (c.getVehicle() == boat) c.stopRiding();
            boat = null;
            return;
        }
        if (c.getVehicle() == boat) {
            if (now - at >= SIT) { c.stopRiding(); at = now - HIT_EVERY; }
            return;
        }
        if (c.isPassenger()) return;
        if (boat.getBoundingBox().distanceToSqr(c.getEyePosition()) > SEAT_REACH * SEAT_REACH) { leave("far"); return; }
        // A fight comes first: the boat waits.
        if (now - at < HIT_EVERY || fighting()) return;
        at = now;
        Item item = boat.getDropItem();
        c.getLookControl().setLookAt(boat);
        c.doHurtTarget(boat);
        hits++;
        if (boat.isRemoved()) {
            // Broken, it lies where it stood, or a step lower: he walks over it and the pickup takes it, as a player's does
            // (CompanionLoot.collectLoose). Not as his harvest: from a spot with no route that pickup gives a drop up (AgentTest 08.11).
            event("boat_clutch_broken", "hits", hits);
            drop = level.getEntitiesOfClass(ItemEntity.class, boat.getBoundingBox().inflate(1), it -> it.getItem().is(item) && it.getAge() == 0)
                    .stream().findFirst().orElse(null);
            boat = null;
            at = now;
        } else if (hits >= HITS) leave("hits");
    }

    /**
     * The boat he broke, lying: gone (into a bag, his or the player's, or burnt, or with its chunk), or after ten seconds not his to
     * wait for. While the player has his bag open he takes nothing up, and the ten seconds start again.
     */
    private void lying(ServerLevel level) {
        if (!drop.isAlive()) { event("boat_clutch_drop_gone", "why", String.valueOf(drop.getRemovalReason())); drop = null; }
        else if (c.inventoryViewers > 0) at = level.getGameTime();
        else if (level.getGameTime() - at > PICK) leave("lying");
    }

    /** Over to the boat he broke, by the single route, till the pickup takes it; a search this tick had no share for is asked again. */
    void walkToDrop() {
        long now = c.level().getGameTime();
        if (drop == null || !c.work.route.legOver() || now - lookedAt < AGAIN) return;
        int busy = c.work.route.busyAnswers();
        Path path = c.work.route.toCells(Set.of(drop.blockPosition()), 1);
        if (c.work.route.busyAnswers() != busy) return;
        lookedAt = now;
        if (path != null) c.work.route.walk(path);
    }

    /**
     * Out of it and at it till it is broken and picked up: the following and the work wait, as he takes it back (decided 08.11). Not
     * in a fight, nor with his bag open (he takes nothing up then), nor riding.
     */
    boolean breaking() {
        return !fighting() && c.inventoryViewers == 0 && !c.isPassenger() && (boat != null || drop != null);
    }

    /** A live enemy before him: a dead one stays his target till a goal lets it go. */
    private boolean fighting() { return c.getTarget() != null && c.getTarget().isAlive(); }

    /** He leaves the boat where it stands and says so (as the bucket's water, decided 05.11). */
    private void leave(String why) {
        event("boat_clutch_left", "why", why);
        c.autonomySay(LEFT, false);
        boat = null;
        drop = null;
    }

    /**
     * Beside the boat he stepped out of while he breaks it (its hits are given in tick), then over to where it fell: the follow and the
     * work would walk him off first. Beside the bucket's fetch in rank, under his air.
     */
    static final class BreakGoal extends Goal {
        private final CompanionEntity c;
        BreakGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return c.boatClutch.breaking(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void start() { c.getNavigation().stop(); }
        @Override public void tick() { c.boatClutch.walkToDrop(); }
    }

    /** The journal's line: where he is, where his boat is, his health and fall. */
    private void event(String name, Object... more) {
        Object[] fields = {"at", c.blockPosition().toShortString(), "boat", boat == null ? "" : boat.blockPosition().toShortString(),
                "health", c.getHealth(), "fall", c.fallDistance};
        Object[] all = java.util.Arrays.copyOf(fields, fields.length + more.length);
        System.arraycopy(more, 0, all, fields.length, more.length);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, all);
    }
}

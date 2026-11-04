package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The walk back to where he fell. He sets out for the dearest place that is worth the walk, walks from thing to thing
 * and lets the game's own pickup take what he stands on; the place is forgotten only when nothing of his is left there.
 * <p>
 * The place is often in a chunk nobody has loaded since he died there: the walk goes to its coordinates and the chunk
 * loads on the way, so what lies there is looked at only once it can be seen. What is looked at is what survived —
 * a pile burned down or picked over on the way stops being worth the rest of the walk.
 */
public final class CompanionDeathTrip {
    /** Close enough to stand on a thing and let the pickup take it. */
    private static final double NEAR = 1.0;
    /** What fell belongs to the place if it lies this close to it: a stack that slid downhill or drifted in water still counts. */
    private static final double SEARCH = 32;
    /** A walk longer than this, with a block's worth of time for every block of the way, is a walk that got stuck. */
    private static final int PATIENCE_TICKS = 20 * 120;
    private static final int TICKS_PER_BLOCK = 4;
    /** Standing on one thing this long: the pickup did not take it, try the next. */
    private static final int WAIT_ON_ONE = 40;
    /** A second order inside this window means the player insists, and his own walk gives way. */
    private static final long INSIST_WINDOW = 20 * 20;
    /** A place that keeps something behind is not tried again for this long (his bag may be full). */
    private static final int RETRY_TICKS = 20 * 300;
    /** A place in another dimension is worth a crossing only for things that are really dear: a portal costs more. */
    private static final double CROSS_WORTH = 20;

    private final CompanionEntity c;
    private CompanionDeathSpots.Spot spot;
    private long giveUpAt;
    private int waited;
    private UUID standingOn;
    private final Set<UUID> givenUpOn = new HashSet<>();
    private long refusedAt;
    private boolean refused;
    private boolean crossing;
    /** How many things he carried when the place could first be seen: the bag is the proof of what he took. */
    private int bagBefore = -1;
    /** When this place was last tried, so a place that keeps refusing is not tried again at once. */
    private final java.util.Map<BlockPos, Integer> triedAt = new java.util.HashMap<>();
    private String lastLine = "";

    CompanionDeathTrip(CompanionEntity c) { this.c = c; }

    public boolean active() { return spot != null; }

    /** The last thing he said about the walk, for the tests that check he does not claim what he did not take. */
    public String lastLine() { return lastLine; }

    /** What he is about, for the task screen. */
    public String describe() {
        return spot == null ? "" : "Иду за своими вещами: " + spot.pos().toShortString() + ".";
    }

    /** Sets out for the dearest place worth the walk; false when there is nothing worth or possible to walk to. */
    public boolean begin() {
        if (spot != null || !(c.level() instanceof ServerLevel level) || c.getOwnerId() == null) return false;
        var server = level.getServer();
        if (server == null) return false;
        CompanionDeathSpots data = CompanionDeathSpots.get(server);
        List<CompanionDeathSpots.Spot> places = new ArrayList<>(data.all(c.getOwnerId()));
        places.sort(Comparator.comparingDouble(CompanionDeathSpots.Spot::worth).reversed());
        for (CompanionDeathSpots.Spot place : places) {
            Integer tried = triedAt.get(place.pos());
            if (tried != null && c.tickCount - tried < RETRY_TICKS) continue;
            // Only what survived counts: burned, blown up or taken by a mob, the rest is priced as it lies now.
            double left = survivingWorth(level, place);
            if (left < CompanionBelongings.WORTH_GOING) { data.taken(c.getOwnerId(), place); continue; }
            if (!place.dimension().equals(level.dimension())) {
                // Only the Nether can be crossed on foot: the End needs its own portal, so a place there is left.
                if (left < CROSS_WORTH || !place.dimension().equals(net.minecraft.world.level.Level.NETHER)) {
                    data.taken(c.getOwnerId(), place);
                    continue;
                }
                var entered = c.portals.enter(level);
                if (!entered.success()) { data.taken(c.getOwnerId(), place); continue; }
                spot = place;
                crossing = true;
                waited = 0;
                standingOn = null;
                givenUpOn.clear();
                refused = false;
                bagBefore = -1;
                giveUpAt = level.getGameTime() + PATIENCE_TICKS * 2;
                say("Иду за своими вещами через портал: " + place.pos().toShortString() + ".");
                return true;
            }
            if (!reachable(level, place.pos())) { data.taken(c.getOwnerId(), place); continue; }
            spot = place;
            crossing = false;
            waited = 0;
            standingOn = null;
            givenUpOn.clear();
            refused = false;
            bagBefore = -1;
            giveUpAt = level.getGameTime() + PATIENCE_TICKS
                    + (int) (Math.sqrt(c.blockPosition().distSqr(place.pos())) * TICKS_PER_BLOCK);
            say("Иду за своими вещами: " + place.pos().toShortString() + ".");
            return true;
        }
        return false;
    }

    /** One tick of the walk: true while it goes on. */
    boolean tick() {
        if (spot == null) return false;
        if (!(c.level() instanceof ServerLevel level)) return false;
        if (crossing) {
            if (level.dimension().equals(spot.dimension())) { crossing = false; say("Перешёл, иду к вещам."); return true; }
            if (!c.portals.walking()) { cancel("Не смог перейти в другое измерение."); return false; }
            if (level.getGameTime() > giveUpAt) { cancel("Портал не пустил."); return false; }
            return true;
        }
        // Moved out of the dimension by something else: back through a portal, or the place is left.
        if (!level.dimension().equals(spot.dimension())) {
            if (!c.portals.walking()) {
                var entered = c.portals.enter(level);
                if (!entered.success()) { cancel("Оказался в другом измерении, портала рядом нет."); return false; }
                crossing = true;
                giveUpAt = level.getGameTime() + PATIENCE_TICKS * 2;
                say("Возвращаюсь к своим вещам через портал.");
            }
            return true;
        }
        if (level.getGameTime() > giveUpAt) { finish(level, "Не дошёл до всех своих вещей: дорога не пустила."); return false; }
        // Not loaded yet: the walk goes to the coordinates and the chunk loads on the way; nothing can be seen before that.
        if (!level.hasChunkAt(spot.pos())) {
            // No path can be found to a chunk that is not loaded yet: the walk goes on and the patience decides.
            c.travel().toward(spot.pos(), NEAR);
            return true;
        }
        if (bagBefore < 0) bagBefore = carried();
        if (standingOn != null && his(level, spot.pos()).stream().noneMatch(item -> item.getUUID().equals(standingOn))) {
            standingOn = null;
            waited = 0;
        }
        ItemEntity next = nearest(level);
        if (next == null) { finish(level, carried() > bagBefore ? "Забрал свои вещи." : "Вещей на месте уже нет."); return false; }
        if (c.blockPosition().distSqr(next.blockPosition()) > NEAR * NEAR) {
            standingOn = null;
            waited = 0;
            CompanionTravel.Step step = c.travel().toward(next.blockPosition(), NEAR);
            if (step == CompanionTravel.Step.FAILED) {
                givenUpOn.add(next.getUUID());
                c.travel().reset();
                return true;
            }
            return true;
        }
        c.getNavigation().stop();
        c.travel().reset();
        if (!next.getUUID().equals(standingOn)) {
            standingOn = next.getUUID();
            waited = 0;
        }
        waited++;
        // The pickup runs by itself while he stands there; a thing it cannot take is left for the next walk.
        if (waited >= WAIT_ON_ONE) { givenUpOn.add(next.getUUID()); standingOn = null; waited = 0; }
        return true;
    }

    /**
     * An order arrived. True: he is busy with his own things and says so once. False: the player asked a second time
     * and the walk gives way to the order.
     */
    boolean refuseOrder() {
        if (spot == null) return false;
        long now = c.level().getGameTime();
        if (refused && now - refusedAt <= INSIST_WINDOW) {
            CompanionDeathSpots.get(c.level().getServer()).taken(c.getOwnerId(), spot);
            spot = null;
            say("Ладно, твоё дело важнее.");
            return false;
        }
        refused = true;
        refusedAt = now;
        return true;
    }

    public String refusalLine() { return "Сейчас только вещи заберу свои после смерти."; }

    /** Stops the walk but keeps the place: he died on the way, and his things are still there. */
    void stop() {
        spot = null;
        c.travel().reset();
    }

    void cancel(String why) {
        if (spot != null && c.level().getServer() != null) CompanionDeathSpots.get(c.level().getServer()).taken(c.getOwnerId(), spot);
        spot = null;
        c.travel().reset();
        if (why != null && !why.isEmpty()) say(why);
    }

    /** The nearest thing of his he has not given up on, within the search round the place. */
    private ItemEntity nearest(ServerLevel level) {
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity item : his(level, spot.pos())) {
            if (givenUpOn.contains(item.getUUID())) continue;
            double distance = item.distanceToSqr(c);
            if (distance < bestDistance) { bestDistance = distance; best = item; }
        }
        return best;
    }

    /** The price of what is still lying there now: a partly burned or stolen pile is worth what remains, not what fell. */
    private double survivingWorth(ServerLevel level, CompanionDeathSpots.Spot place) {
        ServerLevel there = place.dimension().equals(level.dimension()) ? level : level.getServer().getLevel(place.dimension());
        // A place in a chunk nobody has loaded cannot be looked at: the record is trusted, and the walk looks on the way.
        if (there == null || !there.hasChunkAt(place.pos())) return place.worth();
        double total = 0;
        for (ItemEntity item : his(there, place.pos())) total += CompanionBelongings.worth(item.getItem());
        return total;
    }

    /** What he dropped: the game tags every stack that falls off him with its owner, and nothing else counts. */
    private java.util.List<ItemEntity> his(ServerLevel level, BlockPos place) {
        UUID owner = c.getOwnerId();
        return level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(place).inflate(SEARCH),
                item -> item.isAlive() && !item.getItem().isEmpty()
                        && owner.equals(item.getPersistentData().getUUID(CompanionBag.BELONGINGS)));
    }

    /** Done: the place is forgotten, and what happened there is said plainly. */
    private void finish(ServerLevel level, String line) {
        boolean left = !his(level, spot.pos()).isEmpty();
        if (left) {
            // Something of his is still there (a full bag, a stack the pickup will not take): the place waits for next time.
            triedAt.put(spot.pos(), c.tickCount);
            spot = null;
            c.travel().reset();
            say("Часть моих вещей осталась на месте.");
            return;
        }
        CompanionDeathSpots.get(level.getServer()).taken(c.getOwnerId(), spot);
        spot = null;
        c.travel().reset();
        say(line);
    }

    /** How many things he carries: the bag, not the ground, is the proof of what he took. */
    private int carried() {
        int total = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) total += c.getItem(slot).getCount();
        return total;
    }

    /** Lava or fire takes what falls; the void leaves nothing to walk to. */
    private boolean reachable(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() + 4) return false;
        var state = level.getBlockState(pos);
        return !state.is(Blocks.LAVA) && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)
                && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CAMPFIRE) && !state.is(Blocks.SOUL_CAMPFIRE);
    }

    private void say(String line) {
        lastLine = line;
        var owner = c.getOwnerPlayer();
        if (owner != null) owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(line));
    }
}

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
    private static final double NEAR = 1.2;
    /** What fell belongs to the place if it lies this close to it: a stack that slid downhill or drifted in water still counts. */
    private static final double SEARCH = 64;
    /** A walk longer than this, with a block's worth of time for every block of the way, is a walk that got stuck. */
    private static final int PATIENCE_TICKS = 20 * 120;
    private static final int TICKS_PER_BLOCK = 4;
    /** Standing on one thing this long: the pickup did not take it, try the next. */
    private static final int WAIT_ON_ONE = 40;
    /** A second order inside this window means the player insists, and his own walk gives way. */
    private static final long INSIST_WINDOW = 20 * 20;
    /** A place that keeps something behind is not tried again for this long (his bag may be full). */
    private static final int RETRY_TICKS = 20 * 30;
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
    private boolean attempted;
    /** How many things he carried when the place could first be seen: the bag is the proof of what he took. */
    private int takenBefore = -1;
    /** Walking at one stack this long without getting into its reach: stand beside it and let the pickup try. */
    private static final int APPROACH_PATIENCE = 60;
    private int approach;
    private UUID approachTarget;
    private int startedAt;
    /** Passes over the same place in one trip: a stack that could not be taken once is tried again. */
    private static final int ROUNDS = 1;
    /** A walk that has been at it this long ends, whatever the game clock says. */
    private static final int MOST_TICKS = 20 * 60;
    private int rounds;
    /** How many of his stacks disappeared beside him: the proof he took them, not just that the bag grew. */
    private int taken;
    private final java.util.Map<UUID, BlockPos> seen = new java.util.HashMap<>();
    /** When this place was last tried, so a place that keeps refusing is not tried again at once. */
    private final java.util.Map<BlockPos, Integer> triedAt = new java.util.HashMap<>();
    private String lastLine = "";

    CompanionDeathTrip(CompanionEntity c) { this.c = c; }

    public boolean active() { return spot != null; }

    /** Where he is walking to, for the death hook. */
    public BlockPos place() { return spot == null ? null : spot.pos(); }

    /** The dimension of the place he is walking to, for the death hook. */
    public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension() {
        return spot == null ? null : spot.dimension();
    }

    /** The last thing he said about the walk, for the tests that check he does not claim what he did not take. */
    public String lastLine() { return lastLine; }

    /** What he is about, for the task screen. */
    public String describe() {
        return spot == null ? "" : "Иду за своими вещами: " + spot.pos().toShortString() + ".";
    }

    /** Sets out for the dearest place worth the walk; false when there is nothing worth or possible to walk to. */
    public boolean begin() {
        if (spot != null || !c.deathRecovery() || !(c.level() instanceof ServerLevel level) || c.getOwnerId() == null) {
            dev.aicompanion.AICompanionMod.LOGGER.info("[death] begin skipped: active={} on={} level={} owner={}",
                    spot != null, c.deathRecovery(), c.level() instanceof ServerLevel, c.getOwnerId() != null);
            return false;
        }
        var server = level.getServer();
        if (server == null) return false;
        CompanionDeathSpots data = CompanionDeathSpots.get(server);
        List<CompanionDeathSpots.Spot> places = new ArrayList<>(data.all(c.getOwnerId()));
        places.sort(Comparator.comparingDouble(CompanionDeathSpots.Spot::worth).reversed());
        for (CompanionDeathSpots.Spot place : places) {
            if (place.attempts() >= c.deathRecoveryAttempts() || place.deaths() >= c.deathRecoveryDeaths()) {
                data.taken(c.getOwnerId(), place.dimension(), place.pos());
                say("Ну и подавитесь своим лутом.");
                continue;
            }
            Integer tried = triedAt.get(place.pos());
            if (tried != null && c.tickCount - tried < RETRY_TICKS) continue;
            // What survived counts: burned, blown up or taken by a mob, nothing is left and the place goes. Whatever is
            // still marked as his is fetched, junk too, because he was there for it and the mark is the truth.
            double left = survivingWorth(level, place);
            if (left < CompanionBelongings.WORTH_GOING && !(level.hasChunkAt(place.pos()) && !his(level, place.pos()).isEmpty())) {
                data.taken(c.getOwnerId(), place.dimension(), place.pos());
                continue;
            }
            if (!place.dimension().equals(level.dimension())) {
                // Only the Nether can be crossed on foot: the End needs its own portal, so a place there is left.
                if (left < CROSS_WORTH || !place.dimension().equals(net.minecraft.world.level.Level.NETHER)) {
                    data.taken(c.getOwnerId(), place.dimension(), place.pos());
                    continue;
                }
                // No portal within reach right now is not a reason to forget a dear place: it waits for next time.
                var entered = c.portals.enter(level);
                if (!entered.success()) continue;
                // Charged against the place's own dimension, or the record is never found and the try is lost.
                data.attempted(c.getOwnerId(), place.dimension(), place.pos());
                spot = place;
                crossing = true;
                waited = 0;
                standingOn = null;
                givenUpOn.clear();
                refused = false;
                takenBefore = -1;
                attempted = false;
                rounds = 0;
                giveUpAt = level.getGameTime() + PATIENCE_TICKS * 2;
                say("Иду за своими вещами через портал.");
                return true;
            }
            if (!reachable(level, place.pos())) { data.taken(c.getOwnerId(), place.dimension(), place.pos()); continue; }
            makeRoom();
            data.attempted(c.getOwnerId(), level.dimension(), place.pos());
            spot = place;
            startedAt = c.tickCount;
            crossing = false;
            waited = 0;
            standingOn = null;
            givenUpOn.clear();
            refused = false;
            takenBefore = -1;
            attempted = false;
            rounds = 0;
            giveUpAt = level.getGameTime() + PATIENCE_TICKS
                    + (int) (Math.sqrt(c.blockPosition().distSqr(place.pos())) * TICKS_PER_BLOCK);
            say("Иду за своими вещами.");
            return true;
        }
        dev.aicompanion.AICompanionMod.LOGGER.info("[death] nothing to walk for: places={}", places.size());
        return false;
    }

    /** One tick of the walk: true while it goes on. */
    public boolean tick() {
        if (spot == null) return false;
        if (!(c.level() instanceof ServerLevel level)) return false;
        // The player turned the feature off: the walk ends here, as he asked.
        if (!c.deathRecovery()) { stop(); say("Ладно, вещи подождут."); return false; }
        if (crossing) {
            if (level.dimension().equals(spot.dimension())) { crossing = false; say("Перешёл, иду к вещам."); return true; }
            if (!c.portals.walking()) { stop(); say("Не смог перейти в другое измерение."); return false; }
            if (c.tickCount - startedAt > MOST_TICKS || level.getGameTime() > giveUpAt) { stop(); say("Портал не пустил."); return false; }
            return true;
        }
        // Moved out of the dimension by something else: back through a portal, or the place is left.
        if (!level.dimension().equals(spot.dimension())) {
            if (!c.portals.walking()) {
                var entered = c.portals.enter(level);
                if (!entered.success()) { stop(); say("Оказался в другом измерении, портала рядом нет."); return false; }
                crossing = true;
                giveUpAt = level.getGameTime() + PATIENCE_TICKS * 2;
                say("Возвращаюсь к своим вещам через портал.");
            }
            return true;
        }
        if (c.tickCount - startedAt > MOST_TICKS || level.getGameTime() > giveUpAt) {
            // A place nobody could look at is not judged by an empty look: it waits for next time.
            if (!level.hasChunkAt(spot.pos())) {
                triedAt.put(spot.pos(), c.tickCount);
                spot = null;
                c.travel().reset();
                say("Не дошёл до своих вещей: дорога не пустила.");
                return false;
            }
            finish(level, "Не дошёл до всех своих вещей: дорога не пустила.");
            return false;
        }
        // Not loaded yet: the walk goes to the coordinates and the chunk loads on the way; nothing can be seen before that.
        if (!level.hasChunkAt(spot.pos())) {
            // No path can be found to a chunk that is not loaded yet: the walk goes on and the patience decides.
            c.travel().toward(spot.pos(), NEAR);
            return true;
        }
        if (takenBefore < 0) takenBefore = taken();
        java.util.Map<UUID, BlockPos> now = new java.util.HashMap<>();
        for (ItemEntity item : his(level, spot.pos())) now.put(item.getUUID(), item.blockPosition().immutable());
        // A stack of his that vanished beside him was picked up by him; one that vanished elsewhere was lost.
        for (var entry : seen.entrySet())
            if (!now.containsKey(entry.getKey()) && entry.getValue().distToCenterSqr(c.position()) < 16) taken++;
        seen.clear();
        seen.putAll(now);
        if (standingOn != null && his(level, spot.pos()).stream().noneMatch(item -> item.getUUID().equals(standingOn))) {
            standingOn = null;
            waited = 0;
        }
        ItemEntity next = nearest(level);
        if (next == null) {
            // Everything left was given up on: one more pass over it, a few times, before he walks home with a word.
            if (rounds < ROUNDS) {
                rounds++;
                givenUpOn.clear();
                standingOn = null;
                waited = 0;
                c.travel().reset();
                return true;
            }
            finish(level, taken() > takenBefore ? "Забрал свои вещи." : "Вещей на месте уже нет.");
            return false;
        }
        // Straight at the stack itself: the game's own navigation knows water, steps and ladders, and two blocks is
        // close enough for the pickup to reach it.
        double gap = c.position().distanceToSqr(next.position());
        // The patience of an approach belongs to the stack it is walking at, not to the walking itself.
        if (!next.getUUID().equals(approachTarget)) { approachTarget = next.getUUID(); approach = 0; }
        if (gap > 4.0) {
            approach++;
            if (approach > 200) { givenUpOn.add(next.getUUID()); approach = 0; return true; }
            if (approach % 10 == 1 || c.getNavigation().isDone())
                c.getNavigation().moveTo(next.getX(), next.getY(), next.getZ(), 1.2D);
            standingOn = null;
            waited = 0;
            return true;
        }
        c.getNavigation().stop();
        if (!next.getUUID().equals(standingOn)) {
            standingOn = next.getUUID();
            waited = 0;
        }
        waited++;
        if (waited == 1 && room() == 0) makeRoom();
        // The pickup runs by itself while he stands there; a thing it cannot take is left for the next walk.
        if (waited >= WAIT_ON_ONE) { givenUpOn.add(next.getUUID()); standingOn = null; waited = 0; approach = 0; }
        return true;
    }

    /**
     * An order arrived        return true;
    }

    /**
     * An order arrived. True: he is busy with his own things and says so once. False: the player asked a second time
     * and the walk gives way to the order.
     */
    boolean refuseOrder() {
        if (spot == null) return false;
        long now = c.level().getGameTime();
        if (refused && now - refusedAt <= INSIST_WINDOW) {
            // The walk gives way, but the place stays: his things are still lying there.
            spot = null;
            c.travel().reset();
            say("Ладно, твоё дело важнее.");
            return false;
        }
        refused = true;
        refusedAt = now;
        return true;
    }

    public String refusalLine() { return "Сейчас только вещи заберу свои после смерти."; }

    /** A new fall at a place clears the wait that a previous walk there left behind. */
    public void freshPlace(net.minecraft.core.BlockPos pos) { triedAt.remove(pos); }

    /** Stops the walk but keeps the place: he died on the way, and his things are still there. */
    void stop() {
        spot = null;
        c.travel().reset();
    }

    void cancel(String why) {
        if (spot != null && c.level().getServer() != null) CompanionDeathSpots.get(c.level().getServer()).taken(c.getOwnerId(), spot.dimension(), spot.pos());
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

    /** What he dropped: his own stacks and the player's things he was carrying, and nothing else counts. */
    private java.util.List<ItemEntity> his(ServerLevel level, BlockPos place) {
        UUID owner = c.getOwnerId();
        return level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(place).inflate(SEARCH),
                item -> item.isAlive() && !item.getItem().isEmpty() && mine(item, owner));
    }

    private boolean mine(ItemEntity item, UUID owner) {
        var data = item.getPersistentData();
        // Exactly the stacks of this death: another pile at the same spot is not this walk's business.
        if (data.hasUUID(CompanionBag.BELONGINGS_SPOT)) return spot != null && spot.id().equals(data.getUUID(CompanionBag.BELONGINGS_SPOT));
        if (data.hasUUID(CompanionBag.BELONGINGS) && owner.equals(data.getUUID(CompanionBag.BELONGINGS))) return true;
        if (data.getBoolean(CompanionPlayersThings.MARK)) return true;
        // Taken up by the player and thrown back: no mark at all, but it lies where his things fell.
        return !data.hasUUID(CompanionBag.BELONGINGS) && item.getTarget() == null;
    }

    /** Done: the place is forgotten, and what happened there is said plainly. */
    private void finish(ServerLevel level, String line) {
        // Any marked stack still lying there means part of it stayed: the mark is the truth, whatever it is worth.
        boolean left = !his(level, spot.pos()).isEmpty();
        if (left) {
            // Something of his is still there (a full bag, a stack the pickup will not take): the place waits for next time.
            triedAt.put(spot.pos(), c.tickCount);
            spot = null;
            c.travel().reset();
            say("Часть моих вещей осталась на месте.");
            return;
        }
        CompanionDeathSpots.get(level.getServer()).taken(c.getOwnerId(), level.dimension(), spot.pos());
        spot = null;
        c.travel().reset();
        say(line);
    }

    /** A full bag is made room in by letting the cheapest junk go, not by leaving a diamond behind. */
    private void makeRoom() {
        while (room() == 0) {
            int worst = -1;
            double worstWorth = Double.MAX_VALUE;
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                var stack = c.getItem(slot);
                if (stack.isEmpty()) continue;
                double worth = CompanionBelongings.worth(stack);
                if (worth < worstWorth) { worstWorth = worth; worst = slot; }
            }
            if (worst < 0 || worstWorth >= CompanionBelongings.WORTH_GOING) return;
            var stack = c.getItem(worst);
            c.setItem(worst, net.minecraft.world.item.ItemStack.EMPTY);
            c.spawnAtLocation(stack, .5F);
        }
    }

    /** An empty slot: a new stack needs one, a half stack of dirt will not take a diamond. */
    private int room() {
        int free = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).isEmpty()) free++;
        return free;
    }

    /** What still lies there, priced: the reason to come back or not. */
    private double leftWorth(ServerLevel level) {
        double total = 0;
        for (ItemEntity item : his(level, spot.pos())) total += CompanionBelongings.worth(item.getItem());
        return total;
    }

    /** How many marked belongings the pickup itself took: the exact proof, not a guess from the bag. */
    private int taken() { return c.loot.belongingsTaken(); }

    /** Lava or fire takes what falls; the void leaves nothing to walk to. */
    private boolean reachable(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() + 4) return false;
        var state = level.getBlockState(pos);
        return !state.is(Blocks.LAVA) && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)
                && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CAMPFIRE) && !state.is(Blocks.SOUL_CAMPFIRE);
    }

    private void say(String line) {
        lastLine = line;
        c.autonomySay(line);
        dev.aicompanion.AICompanionMod.LOGGER.info("[death] says: {}", line);
    }
}

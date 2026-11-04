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
 * The walk back to where he fell. He sets out for the dearest place he can reach, then walks from thing to thing:
 * what the game's own pickup takes as he stands on it is his again, and the place is forgotten only when nothing of
 * his is left there. A place he cannot reach at all (another dimension, lava, the void) is dropped without a walk.
 */
public final class CompanionDeathTrip {
    /** Close enough to stand on a thing and let the pickup take it. */
    private static final double NEAR = 1.0;
    /** What fell belongs to the place if it lies this close to it; a stack that slid downhill still counts. */
    private static final double SEARCH = 16;
    /** A walk longer than this is a walk that got stuck. */
    private static final int PATIENCE_TICKS = 20 * 120;
    /** Standing on one thing this long: the pickup did not take it, try the next. */
    private static final int WAIT_ON_ONE = 40;
    /** A second order inside this window means the player insists, and his own walk gives way. */
    private static final long INSIST_WINDOW = 20 * 20;

    private final CompanionEntity c;
    private CompanionDeathSpots.Spot spot;
    private long giveUpAt;
    private int waited;
    private UUID standingOn;
    private final Set<UUID> givenUpOn = new HashSet<>();
    private long refusedAt;
    private boolean refused;

    CompanionDeathTrip(CompanionEntity c) { this.c = c; }

    public boolean active() { return spot != null; }

    /** What he is about, for the task screen. */
    public String describe() {
        return spot == null ? "" : "Иду за своими вещами: " + spot.pos().toShortString() + ".";
    }

    /** Sets out for the dearest place in this dimension; false when there is nothing worth or possible to walk to. */
    public boolean begin() {
        if (spot != null || !(c.level() instanceof ServerLevel level) || c.getOwnerId() == null) return false;
        var server = level.getServer();
        if (server == null) return false;
        CompanionDeathSpots data = CompanionDeathSpots.get(server);
        List<CompanionDeathSpots.Spot> places = new ArrayList<>(data.all(c.getOwnerId()));
        places.sort(Comparator.comparingDouble(CompanionDeathSpots.Spot::worth).reversed());
        for (CompanionDeathSpots.Spot place : places) {
            if (!place.dimension().equals(level.dimension())) { data.taken(c.getOwnerId(), place); continue; }
            if (!reachable(level, place.pos())) { data.taken(c.getOwnerId(), place); continue; }
            spot = place;
            waited = 0;
            standingOn = null;
            givenUpOn.clear();
            refused = false;
            giveUpAt = level.getGameTime() + PATIENCE_TICKS;
            say("Иду за своими вещами: " + place.pos().toShortString() + ".");
            return true;
        }
        return false;
    }

    /** One tick of the walk: true while it goes on. */
    boolean tick() {
        if (spot == null) return false;
        if (!(c.level() instanceof ServerLevel level)) { cancel("Место смерти в другом измерении."); return false; }
        if (level.getGameTime() > giveUpAt) { finish(level, "Не дошёл до всех своих вещей: дорога не пустила."); return false; }
        ItemEntity next = nearest(level);
        if (next == null) { finish(level, "Забрал свои вещи."); return false; }
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
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(spot.pos()).inflate(SEARCH),
                found -> found.isAlive() && !found.getItem().isEmpty() && !givenUpOn.contains(found.getUUID()))) {
            double distance = item.distanceToSqr(c);
            if (distance < bestDistance) { bestDistance = distance; best = item; }
        }
        return best;
    }

    /** Done: the place is forgotten; what is left there is named, so a half-finished walk is not a quiet lie. */
    private void finish(ServerLevel level, String line) {
        CompanionDeathSpots.get(level.getServer()).taken(c.getOwnerId(), spot);
        spot = null;
        c.travel().reset();
        say(line);
    }

    /** Lava or fire takes what falls; the void leaves nothing to walk to. */
    private boolean reachable(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() + 4) return false;
        var state = level.getBlockState(pos);
        return !state.is(Blocks.LAVA) && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)
                && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CAMPFIRE) && !state.is(Blocks.SOUL_CAMPFIRE);
    }

    private void say(String line) {
        var owner = c.getOwnerPlayer();
        if (owner != null) owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(line));
    }
}

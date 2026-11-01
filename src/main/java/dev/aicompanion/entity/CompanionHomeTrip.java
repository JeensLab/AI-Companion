package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.pathfinder.Path;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The walk of a work order to what he remembers beyond the search round him: a chest or furnace, or home, where a new one is set down.
 * Bounded by distance, time and count: stale memory costs a walk, not a loop. Direct orders to place a block never come here.
 */
final class CompanionHomeTrip {
    static final int STATION_TRIP = 128;
    /** A step of a longer chain turns back no farther than this: the vein or the goal's work stays where it was left. */
    static final int CHAIN_TRIP = 32;
    /** Farther than this a walk goes in legs: the plain approach only knows the loaded neighbourhood. */
    static final int FAR_WALK = 24;
    /** Within this of a home whose last steps failed (a door, a wall) he is home all the same. */
    private static final int HOME_RADIUS = 16;
    private static final int NEAR_STATION = 8;
    private static final int NEAR_HOME = 3;
    /** The walk in legs stops short of a door; the last steps go by the route that knows doors. */
    private static final int ENTER_TICKS = 200;
    private static final int TICKS_PER_BLOCK = 8;
    private static final int START_TICKS = 200;
    private static final int MAX_TRIPS = 2;

    private final CompanionWork w;
    private final Set<BlockPos> visited = new HashSet<>();
    private BlockPos goal;
    private boolean homeward;
    private boolean intoHouse;
    private boolean entering;
    private boolean homeTried;
    private boolean atHome;
    private int trips;
    private long giveUpAt;
    private String note = "";

    CompanionHomeTrip(CompanionWork work) { w = work; }

    void reset() {
        if (goal != null) w.owner.travel().reset();
        goal = null; homeward = intoHouse = entering = homeTried = atHome = false; trips = 0; note = ""; visited.clear();
    }

    /** Why the order was done where it was and not at home; empty when nothing got in the way. */
    String note() { return note; }

    /** One tick of the walk under way: true while it goes on, false once it is over or there is none. */
    boolean walk() {
        if (goal == null) return false;
        if (entering) return enter();
        var step = w.owner.level().getGameTime() > giveUpAt ? CompanionTravel.Step.FAILED
                : w.owner.travel().toward(goal, homeward && !intoHouse ? NEAR_HOME : NEAR_STATION);
        if (step == CompanionTravel.Step.WALKING) return true;
        w.owner.travel().reset();
        w.resetApproach();
        if (intoHouse && distance(goal) <= HOME_RADIUS) {
            entering = true; giveUpAt = w.owner.level().getGameTime() + ENTER_TICKS;
            return true;
        }
        if (homeward && (step == CompanionTravel.Step.NEAR || distance(goal) <= HOME_RADIUS)) arrive(goal);
        else if (step != CompanionTravel.Step.NEAR) note = homeward ? " Домой дойти не смог: нет пути." : " До места на " + goal.toShortString() + " дойти не смог: нет пути.";
        goal = null;
        return false;
    }

    private boolean enter() {
        if (!w.house.inside(w.owner.blockPosition()) && w.owner.level().getGameTime() <= giveUpAt) {
            Path route = w.route.cells(Set.of(goal), 0).path();
            if (route == null) w.owner.getNavigation().stop(); else w.route.walk(route);
            return true;
        }
        w.owner.getNavigation().stop();
        w.resetApproach();
        arrive(goal);
        goal = null; entering = false;
        return false;
    }

    /** Sets out for the nearest remembered block of this kind he may use and the search round him cannot see. */
    boolean toStation(String block) {
        if (!(w.owner.level() instanceof ServerLevel level) || goal != null || w.insideHouse || trips >= MAX_TRIPS) return false;
        boolean chest = block.equals("minecraft:chest");
        Set<BlockPos> tried = chest ? w.triedChests : w.triedFurnaces;
        int limit = limit();
        for (BlockPos pos : w.owner.places.stations(block)) {
            if (distance(pos) > limit) break;
            if (visited.contains(pos) || tried.contains(pos) || searched(level, pos)) continue;
            if (w.owner.ownPlacesOnly() && (!w.owner.placedByMe(pos) || !w.owner.leashAllows(pos))) continue;
            if (!chest && w.owner.batches().reserved(level, pos)) continue;
            visited.add(pos);
            return setOut(pos, false, "Иду к " + (chest ? "сундуку" : "печи") + " на " + pos.toShortString() + ".");
        }
        return false;
    }

    /** With a new block in hand: home first, so it stands there and not under his feet. A deed of his own stays on its leash. */
    boolean toHome() {
        if (!(w.owner.level() instanceof ServerLevel level) || goal != null || atHome || homeTried || w.insideHouse || w.owner.ownPlacesOnly()) return false;
        CompanionPlaces.Home home = w.owner.places.home();
        if (home == null) return false;
        homeTried = true;
        BlockPos house = w.house.finishedAnchor(level);
        BlockPos to = house != null && house.distSqr(home.pos()) <= HOME_RADIUS * HOME_RADIUS ? house : home.pos();
        intoHouse = to.equals(house);
        if (intoHouse ? w.house.inside(w.owner.blockPosition()) : distance(to) <= NEAR_HOME) { arrive(to); return false; }
        if (distance(to) > limit()) { note = " До дома " + (int)distance(to) + " блоков, это слишком далеко для похода."; return false; }
        return trips < MAX_TRIPS && setOut(to, true, "Иду домой.");
    }

    /** A new block where he is now: at home inside the house if there is room, else beside it, never in the way of a door. */
    CompanionActionResult place(String block) {
        if (w.insideHouse) return w.owner.placeBlockNearResult(block, w.placementAnchor, w.house::inside);
        if (!atHome || !(w.owner.level() instanceof ServerLevel level)) return w.owner.placeBlockNearResult(block, w.owner.blockPosition());
        BlockPos interior = w.house.finishedAnchor(level);
        Predicate<BlockPos> inside = pos -> w.house.inside(pos) && clearOfDoors(level, pos, 1);
        if (interior != null && interior.equals(w.placementAnchor) && w.owner.placement.canPlaceNear(block, interior, inside))
            return w.owner.placeBlockNearResult(block, interior, inside);
        return w.owner.placeBlockNearResult(block, w.owner.blockPosition(), pos -> clearOfDoors(level, pos, 2));
    }

    private void arrive(BlockPos at) { atHome = true; w.placementAnchor = at; }

    private boolean setOut(BlockPos to, boolean home, String status) {
        long now = w.owner.level().getGameTime(), budget = START_TICKS + (long)(distance(to) * TICKS_PER_BLOCK);
        if (now + budget >= w.deadline) { note = " На поход до " + to.toShortString() + " не хватило бы времени."; return false; }
        goal = to; homeward = home; giveUpAt = now + budget; trips++;
        w.owner.workStatus(status);
        return true;
    }

    private int limit() {
        boolean chain = w.ironPrep || w.owner.getOwnerId() != null && CompanionAgenda.running(w.owner.getOwnerId());
        return chain ? CHAIN_TRIP : STATION_TRIP;
    }

    /** The search round him has looked there already and turned it down (full, busy, without the thing asked for). */
    private boolean searched(ServerLevel level, BlockPos pos) {
        int radius = Math.min(32, w.owner.getSearchRadius());
        return level.hasChunkAt(pos) && pos.distSqr(w.owner.blockPosition()) <= radius * radius;
    }

    private double distance(BlockPos pos) { return Math.sqrt(w.owner.distanceToSqr(pos.getCenter())); }

    private static boolean clearOfDoors(ServerLevel level, BlockPos pos, int margin) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-margin, -1, -margin), pos.offset(margin, 1, margin)))
            if (level.getBlockState(near).getBlock() instanceof DoorBlock) return false;
        return true;
    }
}

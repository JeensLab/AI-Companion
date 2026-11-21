package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The way up when the supplies are gone: back along the last tunnel and the stairs he dug to the entrance, one cell at a time.
 * Where the record has a gap (the stairs below a restart are forgotten, the cells above it begin far off) the gap is walked by a
 * route search, and a plug of his own that shuts it comes out with the pickaxe he has. Rock that shuts it is cut through by the
 * self-rescue, which is let work for that alone; a step that cannot be done ends the climb where he stands, said as it is. Out of a pit with his old
 * stairs a few blocks over it, a short staircase of his own is cut up to them, and the climb walks on along them. The steps of a staircase he cuts at a
 * gap of the way above go into the record in place of it, and the cells of the way they lead past are dropped from it: the next climb, and the way down, walk them.
 */
final class CompanionExpeditionClimb {
    enum Result { WORKING, DONE }

    private enum Phase { WALK, TRAVEL, UNPLUG, CUT_OUT, STAIRS_TO_WAY, STAIRS_UP }

    /** Two cells of the way further apart than this (squared) are not neighbours: the record has a gap between them. */
    private static final double GAP = 9;
    private static final int OWN_STEPS = 4, MAX_PLUGS = 8, IRON_FOR_PLUGS = 8;
    private static final double TRAVEL_NEAR = 1.0;
    /** Rescues to try for one climb, and the ticks given to all of them to start: the rescue waits a standstill of three seconds and an answer of the route search. */
    private static final int MAX_CUTS = 3, CUT_PATIENCE = 600;
    static final String CLOSED = " Подняться не вышло: путь наверх закрыт, остался где стою.";
    /** How far round him the ground is looked at, and how many of the eight columns there must lie open for the ground under the sky to be the surface. */
    private static final int OPEN_REACH = 4, OPEN_SIDES = 3;

    private final CompanionWork w;
    private final CompanionExpeditionDig dig;
    private final CompanionExpeditionWalk walk;
    /** The walk up the stairs to the plug that shuts the way, cell by cell: a plug far off is not reached by pushing at it in a straight line. */
    private final CompanionExpeditionWalk toPlug;
    /** Stone from the walls where he stands, for the floor of the way up when the bag has none. */
    private final CompanionExpeditionStock stone;
    private final CompanionExpeditionSupply supply;
    /** The staircase cut up to the surface once the rescues are spent: the way up whatever it takes. */
    private final CompanionExpeditionStairsUp stairs;
    /** The short staircase up to a near cell of his old way across the gap out of a pit ({@link #shortTo}): the walk goes on from that cell. */
    private final CompanionExpeditionStairsUp toWay;
    /** Where in {@link CompanionMineSite#above} the next step of a staircase of this climb goes ({@link #recordFrom}); below 0, before the entrance on the surface. */
    private int recordAt;
    /** The short staircase was begun in this climb: the same gap is not cut to again. */
    private boolean cutToWay;
    /** The index in {@link #cells} of the cell the short staircase goes to, and the steps it may take: past them it goes round, not there. */
    private int toWayCell, toWaySteps;
    private int stairSteps;
    private String why = "", outcome = "";
    private List<BlockPos> cells = List.of();
    /** The end (exclusive) of the stretch being walked: the cell there, if any, lies across a gap. */
    private int stretchEnd;
    private Phase phase = Phase.WALK;
    private CompanionExpeditionPlugs.Way plug;
    private int plugs;
    private int cuts, cutWait, rescuesBefore;
    /** No cell of his own to start from: the whole way to the entrance is a gap, so the staircase cut up to the surface is all there is. */
    private boolean lost;
    /** What the staircase cut up to the surface was cut for, said as it is. */
    private String newStairsWhy = "";

    CompanionExpeditionClimb(CompanionWork w, CompanionExpeditionDig dig, CompanionExpeditionSupply supply) {
        this.w = w;
        this.dig = dig;
        this.supply = supply;
        this.stairs = new CompanionExpeditionStairsUp(w, dig, this::record, null, false);
        this.toWay = new CompanionExpeditionStairsUp(w, dig, this::record, TO_WAY, true);
        this.walk = new CompanionExpeditionWalk(dig);
        this.toPlug = new CompanionExpeditionWalk(dig);
        this.stone = new CompanionExpeditionStock(w, dig);
    }

    static final String LAST_PICKAXE = "Кирки на исходе: поднимаюсь, пока есть чем.";
    static final String NO_TORCHES = "Факелы кончились, а сделать их не из чего: копать в темноте не буду.";
    static final String TO_WAY = "До своей лестницы не дойти, прокапываю к ней ступени.";

    /** What ran out, for the report. */
    static String shortage(boolean lackedOre, boolean hadStone) {
        if (lackedOre) return "Эту руду на пути нечем взять: нужна кирка покрепче, чем у меня.";
        if (!hadStone) return "Каменной кирки у меня нет, а камень копаю только ею.";
        return "Подходящих кирок не осталось: для камня нужна каменная или алмазная, для алмазов железная.";
    }

    /** Plans the way up from where he stands; with no way (not on his stairs, or already at the entrance) the climb is over at once. */
    void begin(String reason, BlockPos here) {
        reset();
        why = reason;
        w.owner.getNavigation().stop();
        CompanionMineSite mine = w.mine;
        if (!mine.exists()) { outcome = " Своей лестницы нет, остался на месте."; return; }
        // The entrance of a mine started again under the ground is a pit, no way to the surface: only the cells left above it lead there.
        if (mine.buried() && mine.above().isEmpty()) { outcome = " Свою лестницу к поверхности потерял, остался где стою."; return; }
        cells = wayToSurface(mine);
        int nearest = CompanionExpeditionRecord.nearest(cells, here, CompanionMineSite.NEAR_STAND);
        if (nearest < 0) outcome = " Свою лестницу рядом не нашёл, остался где стою.";
        else if (nearest == cells.size() - 1) outcome = mine.buried() ? " Выше меня своей лестницы нет, остался где стою." : " Я и так у входа, остался на месте.";
        else {
            stretchEnd = gapAfter(cells, nearest);
            walk.set(cells.subList(0, stretchEnd), nearest);
            phase = Phase.WALK;
        }
    }

    /** As {@link #begin} for a stop that came again: with no cell of his own to start from, the whole way to the entrance on the surface is one gap to walk, open or cut. */
    void beginAnyway(String reason, BlockPos here) {
        begin(reason, here);
        BlockPos origin = w.mine.origin();
        if (walking() || origin == null) return;
        outcome = "";
        lost = true;
        cells = List.of(origin);
        stretchEnd = 0;
        phase = Phase.TRAVEL;
    }

    boolean walking() { return outcome.isEmpty(); }

    /** The way up to the surface: {@link #wayUp}, and for a mine started again under the ground the ways above its pit. */
    static List<BlockPos> wayToSurface(CompanionMineSite mine) {
        List<BlockPos> cells = wayUp(mine);
        if (mine.buried()) cells.addAll(mine.above());
        return cells;
    }

    /** Blocks a step of {@link CompanionExpeditionStairsUp} takes out of the rock: the cell over his head, the step and the cell over it. */
    private static final int STEP_WEAR = 3;
    /** How far a climbing rescue gets through the rock: it digs at most 60 blocks ({@link CompanionRecovery}), a staircase of some 20 steps; the bound of a short staircase too. */
    private static final int RESCUE_REACH = 20;
    /** Steps a short staircase may take beyond the count of its way: a side refused, a floor of his stairs gone round. */
    private static final int DETOUR = 4;

    /**
     * The wear of the staircase the climb cuts up out of a pit under the ground ({@link #gapOutOfPit}): the short one up to his old way where it is near
     * ({@link #shortTarget}), else a step for each block of height from the top of the pit up to the entrance on the surface. None for a mine whose way up
     * is walked to the surface. A short staircase that fails leaves the staircase to the surface with only its wear kept.
     */
    static int cutWear(CompanionMineSite mine) {
        PitGap gap = gapOutOfPit(mine);
        if (gap == null) return 0;
        int to = shortTarget(mine.above(), gap.beyond(), gap.top(), mine);
        return to >= 0 ? shortWear(gap.top(), mine.above().get(to)) : surfaceWear(gap.top(), mine);
    }

    /**
     * The gap out of a pit under the ground: the last cell of the way up walked to from its entrance, and the index in {@link CompanionMineSite#above} of the
     * first one across the gap from it (its size: the entrance on the surface past the end of the way).
     */
    private record PitGap(BlockPos top, int beyond) { }

    /**
     * The first gap of the way up out of a pit under the ground, past the cells walked to from its entrance (the entrance, or the stairs of an earlier pit
     * when the mine was started again inside it); cells as near as a restart takes its way up from ({@link CompanionMineSite#NEAR_STAND}) are walked to. A way
     * that ends short of the entrance on the surface has its gap to it. Null for a mine whose way up is walked to the surface.
     */
    private static PitGap gapOutOfPit(CompanionMineSite mine) {
        if (!mine.buried() || mine.origin() == null) return null;
        BlockPos last = mine.entrance();
        List<BlockPos> above = mine.above();
        for (int i = 0; i < above.size(); i++) {
            if (above.get(i).distSqr(last) > CompanionMineSite.NEAR_STAND) return new PitGap(last, i);
            last = above.get(i);
        }
        return last.equals(mine.origin()) ? null : new PitGap(last, above.size());
    }

    /**
     * The cell of his old way ({@code way} from {@code first} on) that a short staircase from {@code from} gets to for the least wear, its index, or -1: as
     * near as a rescue digs, no lower (the staircase only climbs or goes level), for less wear than the staircase to the surface (never the entrance on the
     * surface itself), and with the way on from it walked to the end with no other gap (that one would be crossed as any gap, by rescues or a staircase to the
     * surface: the reserve is that staircase's). Not the first cell across alone: a restart far from his old stairs keeps the whole old way, from the end of
     * its tunnel.
     */
    private static int shortTarget(List<BlockPos> way, int first, BlockPos from, CompanionMineSite mine) {
        int best = -1, least = surfaceWear(from, mine);
        for (int i = way.size() - 1; i >= first; i--) {
            if (i + 1 < way.size() && way.get(i).distSqr(way.get(i + 1)) > CompanionMineSite.NEAR_STAND) break;
            BlockPos to = way.get(i);
            if (to.getY() < from.getY() || to.distSqr(from) > RESCUE_REACH * RESCUE_REACH) continue;
            int wear = shortWear(from, to);
            if (wear < least) {
                best = i;
                least = wear;
            }
        }
        return best;
    }

    /** A step up for each block of height, and a level step for each block across left over (or back, where the steps up overshoot it), each a step's wear at most. */
    private static int shortWear(BlockPos from, BlockPos to) {
        int up = to.getY() - from.getY(), across = Math.abs(to.getX() - from.getX()) + Math.abs(to.getZ() - from.getZ());
        return STEP_WEAR * (up + Math.abs(across - up));
    }

    /** A step for each block of height up to the entrance on the surface: the staircase ends where he comes out under the sky. */
    private static int surfaceWear(BlockPos from, CompanionMineSite mine) { return STEP_WEAR * Math.max(0, mine.origin().getY() - from.getY()); }

    /** The last tunnel's cells from its end, then the stands from the foot up, then the entrance. */
    static List<BlockPos> wayUp(CompanionMineSite mine) {
        List<BlockPos> cells = new ArrayList<>();
        List<CompanionMineSite.Tunnel> tunnels = mine.tunnels();
        if (!tunnels.isEmpty()) {
            CompanionMineSite.Tunnel last = tunnels.get(tunnels.size() - 1);
            for (int k = last.length(); k >= 1; k--) cells.add(mine.foot().relative(last.direction(), k));
        }
        List<BlockPos> stands = mine.stands();
        for (int i = stands.size() - 1; i >= 0; i--) cells.add(stands.get(i));
        cells.add(mine.entrance());
        return cells;
    }

    /** The first index after {@code from} whose cell is no neighbour of the one before it, or the size of the list: where the next stretch of cells to walk ends. */
    private static int gapAfter(List<BlockPos> cells, int from) {
        for (int i = Math.max(1, from + 1); i < cells.size(); i++) if (cells.get(i - 1).distSqr(cells.get(i)) > GAP) return i;
        return cells.size();
    }

    Result tick() {
        if (!outcome.isEmpty()) return Result.DONE;
        // Across a gap he may come out on the surface any way at all (a route over the ground, the walk of a pickup, the rescue's wait):
        // there the climb is over. Run water of seed 7 (20.11): waiting for the rescue on a slope, the rescue dug him down a pit
        // toward the cell beyond the gap below him, and from the pit no tree was in reach.
        if ((phase == Phase.TRAVEL || phase == Phase.UNPLUG || phase == Phase.CUT_OUT || phase == Phase.STAIRS_TO_WAY) && onSurface()) {
            // A short staircase out under the sky short of his old stairs (a slope) is his way up to the surface, as a staircase cut up to it.
            if (phase == Phase.STAIRS_TO_WAY) cutPast(top());
            return finish(" Копать нечем, поднялся за припасами.");
        }
        return switch (phase) {
            case WALK -> walked();
            case TRAVEL -> travel();
            case UNPLUG -> unplug();
            case CUT_OUT -> cutting();
            case STAIRS_TO_WAY -> climbingToWay();
            case STAIRS_UP -> climbingStairs();
        };
    }

    private Result walked() {
        CompanionExpeditionWalk.Result ticked = walk.tick();
        return switch (ticked) {
            case WORKING -> Result.WORKING;
            case DONE -> {
                if (stretchEnd >= cells.size()) yield finish(" Копать нечем, поднялся за припасами.");
                phase = Phase.TRAVEL;
                yield Result.WORKING;
            }
            case STUCK, NO_BLOCK -> {
                // The cell he cannot enter is a gap of its own: a plug over his head or across the step comes out, and rock is cut.
                stretchEnd = walk.index();
                yield openWay(cells.get(stretchEnd));
            }
        };
    }

    /** Across a gap of the record: his own steps once near the cell beyond it, a route search until then, and a plug of his that shuts it, near or far, out of the way. */
    private Result travel() {
        BlockPos beyond = cells.get(stretchEnd);
        // A straight push goes up his stairs or a step down; two down or more is rock he has no way through, the route search finds the way round.
        if (w.owner.blockPosition().distManhattan(beyond) <= OWN_STEPS && w.owner.blockPosition().getY() - beyond.getY() <= 1) {
            if (dig.reach(beyond)) return crossed();
            return dig.walkStuck() ? openWay(beyond) : Result.WORKING;
        }
        CompanionTravel.Step leg = w.route.legs(beyond, TRAVEL_NEAR);
        return switch (leg) {
            case NEAR -> crossed();
            case FAILED -> {
                w.route.legsEnd(false);
                yield openWay(beyond);
            }
            case WALKING -> Result.WORKING;
        };
    }

    /**
     * Out under the open sky at the height of the entrance on the surface, or on open ground lower down (a slope below the entrance): the climb has done its
     * work, whatever cell of the record lies beyond the gap. Run water of seed 7 (20.11): out on a slope five blocks below the entrance, the climb went on to a
     * cell of his old stairs below him, and the self-rescue dug him down into a pit with no tree in reach.
     */
    private boolean onSurface() {
        ServerLevel level = (ServerLevel) w.owner.level();
        return surface(level, w.mine, CompanionExpeditionRecord.standingCell(level, w.owner));
    }

    /**
     * The rule of {@link #onSurface} for any cell he stands in: the expedition starts a mine on the surface by it too. At the height of the entrance
     * the ground must lead on at that height. Run descent of seed 7 (21.11): the step of his new stairs two under the entrance had its turf taken off
     * over it, and the climb ended under the sky at the bottom of the slot of his own stairs, with no walk out of it to the player.
     */
    static boolean surface(ServerLevel level, CompanionMineSite mine, BlockPos here) {
        BlockPos top = mine.origin() != null ? mine.origin() : mine.entrance();
        if (top == null || !CompanionExpeditionRecord.underSky(level, here.above())) return false;
        int lowest = top.getY() - CompanionExpedition.UNDERGROUND_DEPTH;
        return here.getY() >= lowest && !CompanionClimbToPlayer.walledIn(level, here, lowest) || openGround(level, here);
    }

    /**
     * Ground open to the sides as well as above, not the floor of a ravine, a pit or a cave under a hole in its roof: of the eight columns
     * {@link #OPEN_REACH} blocks round him, at least {@link #OPEN_SIDES} have ground to stand on under the sky within a step of his feet. On a slope
     * the columns down and across it are open; a trench is open only along itself; air over a drop, water and a cave floor are not ground.
     * Not a heightmap: those count the barriers over a GameTest arena as ground.
     */
    // ponytail: eight columns at one reach, no walk checked; a ravine wider than twice the reach reads as open ground. Upgrade: a short route search out to a column of the ring.
    private static boolean openGround(ServerLevel level, BlockPos here) {
        int open = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos cell = here.offset(dx * OPEN_REACH, dy, dz * OPEN_REACH);
                if (!room(level, cell) || !room(level, cell.above()) || level.getBlockState(cell.below()).getCollisionShape(level, cell.below()).isEmpty()) continue;
                if (CompanionExpeditionRecord.underSky(level, cell.above())) open++;
                break;
            }
        }
        return open >= OPEN_SIDES;
    }

    /** Room for a body: nothing to bump into and no water or lava. */
    private static boolean room(ServerLevel level, BlockPos cell) {
        return level.getBlockState(cell).getCollisionShape(level, cell).isEmpty() && level.getFluidState(cell).isEmpty();
    }

    private Result crossed() {
        w.route.legsEnd(true);
        int from = stretchEnd;
        stretchEnd = gapAfter(cells, from);
        walk.set(cells.subList(0, stretchEnd), from);
        phase = Phase.WALK;
        return Result.WORKING;
    }

    private Result openWay(BlockPos beyond) {
        ServerLevel level = (ServerLevel) w.owner.level();
        BlockPos here = CompanionExpeditionRecord.standingCell(level, w.owner);
        plug = plugs >= MAX_PLUGS ? null : CompanionExpeditionPlugs.way(level, w.mine, here, beyond);
        boolean bridging = plug == null && plugs < MAX_PLUGS;
        if (bridging) plug = CompanionExpeditionPlugs.bridge(level, w.mine, here, beyond);
        if (plug == null) return cutOut();
        toPlug.bridging(bridging);
        toPlug.set(plug.walk(), 0);
        if (plug.cell() != null) dig.ironForRock(IRON_FOR_PLUGS);
        phase = Phase.UNPLUG;
        return Result.WORKING;
    }

    /**
     * No walk and no block of his own joins the two ends. A near cell of his old way over a pit is cut up to ({@link #shortTo}); elsewhere any pickaxe he
     * has cuts the way up, the wear kept for ore too (out is worth more than ore); the rescues spent (or none across the far gap out of a pit, {@link #pitGap}),
     * a staircase of his own to the surface where the record has none.
     */
    private Result cutOut() {
        int to = cutToWay ? -1 : shortTo();
        if (to >= 0) return stairsToWay(to);
        if (cuts >= MAX_CUTS || !rockTool() || pitGap()) return noWay();
        cuts++;
        rescuesBefore = w.owner.recovery.begun();
        w.owner.getNavigation().stop();
        w.expedition.climbTime();
        phase = Phase.CUT_OUT;
        return Result.WORKING;
    }

    /** The gap he stands at is the one out of a pit under the ground ({@link #gapOutOfPit}), or the whole way of a lost one. */
    private boolean atPitGap(PitGap gap) {
        return gap != null && (lost || stretchEnd > 0 && cells.get(stretchEnd - 1).distSqr(gap.top()) <= CompanionMineSite.NEAR_STAND);
    }

    /**
     * At the gap out of a pit, a cell beyond that lies farther than a rescue digs ({@link #RESCUE_REACH}): the rescues would spend the staircase's wear
     * ({@link #cutWear}) and not get there. Run void-4 of seed 314159 (21.11): three rescues toward the old way 35 blocks off spent some 170 wear of the iron
     * pickaxe, the climb walked back down from the steps they cut, and its staircase ran out of wear ten blocks under the surface.
     */
    private boolean pitGap() { return atPitGap(gapOutOfPit(w.mine)) && cells.get(stretchEnd).distSqr(w.owner.blockPosition()) > RESCUE_REACH * RESCUE_REACH; }

    /**
     * At the gap out of a pit itself (its first cell across, not a cell of the pit's own stairs the walk stuck at), the cell of his old way a short staircase
     * from where he stands gets to ({@link #shortTarget}), its index in {@link #cells}; -1 elsewhere. Run retreat of seed 7 (series d, 21.11) stood by his old
     * stairs four blocks over the pit when a staircase to the surface was begun from a hundred blocks down, and the pickaxes ran out at height 36.
     */
    private int shortTo() {
        PitGap gap = gapOutOfPit(w.mine);
        List<BlockPos> above = w.mine.above();
        if (lost || !atPitGap(gap) || gap.beyond() >= above.size() || !cells.get(stretchEnd).equals(above.get(gap.beyond()))) return -1;
        return shortTarget(cells, stretchEnd, w.owner.blockPosition(), w.mine);
    }

    /** A short staircase of his own up to the cell {@code to} of the way, once a climb: as a player digs up to the stairs he knows are just over him. */
    private Result stairsToWay(int to) {
        if (!rockTool() && !makeTool()) return finish(CLOSED);
        BlockPos cell = cells.get(to);
        cutToWay = true;
        toWayCell = to;
        toWaySteps = shortWear(w.owner.blockPosition(), cell) / STEP_WEAR + DETOUR;
        recordFrom();
        toWay.begin(cell);
        stairSteps = 0;
        w.owner.getNavigation().stop();
        w.expedition.climbTime();
        phase = Phase.STAIRS_TO_WAY;
        w.owner.workStatus(TO_WAY);
        return Result.WORKING;
    }

    /**
     * At the cell of his old way the walk goes on along it from there. A staircase that cannot get there, or takes more steps than the way there does (at
     * its height a blocked cell turned it back, and every step after led away), leaves the staircase to the surface, at once: the walk back to the gap, the
     * route search and the rescues after it were waits that ended there (beside his old stairs no rescue begins), and lower down (Oleg, 21.11).
     */
    private Result climbingToWay() {
        return switch (toWay.tick((ServerLevel) w.owner.level())) {
            case WORKING -> {
                if (toWay.steps() != stairSteps) {
                    stairSteps = toWay.steps();
                    w.expedition.climbTime();
                    yield Result.WORKING;
                }
                // Counted a tick after the step: that tick tells an arrival first.
                yield stairSteps > toWaySteps ? noWay() : Result.WORKING;
            }
            case ARRIVED -> {
                cutPast(cells.get(toWayCell));
                stretchEnd = toWayCell;
                yield crossed();
            }
            case NO_TOOL -> makeTool() ? Result.WORKING : finish(" " + shortage(false, w.items.countCarried(Items.STONE_PICKAXE) > 0) + CLOSED);
            case STUCK -> noWay();
        };
    }

    /** Stands still with the cell beyond the gap as his destination until the rescue takes over; it stops the work while it runs, and this goes on when it is over. */
    private Result cutting() {
        if (w.owner.recovery.begun() != rescuesBefore) {
            phase = Phase.TRAVEL;
            return Result.WORKING;
        }
        if (++cutWait > CUT_PATIENCE) return noWay();
        w.owner.movementIntent(cells.get(stretchEnd));
        return Result.WORKING;
    }

    /**
     * The record's way up is shut where he stands and no rescue opens it. A staircase of his own is the way up whatever it takes where the record has a gap
     * beyond the blocked cell (or none at all), and where the blocked cell itself lies across a gap from the cell before it: his stairs then do not lead up
     * from here, whole or not. Only where his stairs run on whole to the surface and are merely shut at that cell are they known; a new staircase cut from
     * the middle of the mine would wear the pickaxes out, so the climb ends said as it is.
     */
    private Result noWay() {
        BlockPos beyond = cells.get(stretchEnd);
        boolean acrossGap = stretchEnd == 0 || cells.get(stretchEnd - 1).distSqr(beyond) > GAP;
        if (lost || acrossGap || gapAfter(cells, stretchEnd) < cells.size()) {
            newStairsWhy = lost ? "Своей лестницы наверх нет" : acrossGap ? "До своей лестницы не добраться" : "Своя лестница перекрыта на высоте " + beyond.getY();
            return stairsUp();
        }
        return finish(" Своя лестница перекрыта на высоте " + beyond.getY() + "." + CLOSED);
    }

    private boolean rockTool() { return w.items.countCarried(Items.STONE_PICKAXE) > 0 || dig.ironLeft(0) || dig.hardPickaxe() != null; }

    /** A stone pickaxe made on the spot, else the iron one down to its last wear: out is worth more than ore. */
    private boolean makeTool() {
        if (supply.craftStonePickaxe()) return true;
        if (!dig.ironLeft(0) && dig.hardPickaxe() == null) return false;
        dig.ironForRock(0);
        return true;
    }

    /** The record's way up is cut and no rescue opens it: a staircase of his own up to the entrance on the surface, as a player digs out of a lost mine. */
    private Result stairsUp() {
        if (!rockTool() && !makeTool()) return finish(CLOSED);
        // After a short staircase that failed, the steps go on from where it stopped: the cell across the gap may be one of them by now.
        if (phase != Phase.STAIRS_TO_WAY) recordFrom();
        stairs.begin(top());
        stairSteps = 0;
        w.owner.getNavigation().stop();
        w.expedition.climbTime();
        phase = Phase.STAIRS_UP;
        w.owner.workStatus(newStairsWhy + ", прокапываю новую к поверхности.");
        return Result.WORKING;
    }

    private Result climbingStairs() {
        ServerLevel level = (ServerLevel) w.owner.level();
        return switch (stairs.tick(level)) {
            case WORKING -> {
                if (stairs.steps() == stairSteps) yield Result.WORKING;
                stairSteps = stairs.steps();
                w.expedition.climbTime();
                yield onSurface() ? cutOutOnStairs() : Result.WORKING;
            }
            case ARRIVED -> cutOutOnStairs();
            case NO_TOOL -> makeTool() ? Result.WORKING : finish(" " + shortage(false, w.items.countCarried(Items.STONE_PICKAXE) > 0) + CLOSED);
            case STUCK -> finish(stairs.why().equals("путь наверх закрыт") ? CLOSED : " Подняться не вышло: " + stairs.why() + ", остался где стою.");
        };
    }

    /** Out by the staircase he cut: in the record it is the way up from where it was begun to the entrance on the surface, the cells across the gap left out. */
    private Result cutOutOnStairs() {
        cutPast(top());
        return finish(" " + newStairsWhy + ", прокопал новую к поверхности.");
    }

    private BlockPos top() { return w.mine.origin() != null ? w.mine.origin() : w.mine.entrance(); }

    /**
     * The steps of the staircase begun now go into the record before the cell he could not get to (the entrance on the surface for a lost one). A cell off
     * the way above (a stand of the pit's own stairs, which the walk meets again before any step) keeps that way whole: the steps go in before the entrance
     * on the surface and nothing is left out, as when the record is full.
     */
    // ponytail: the cell is found by its first place in the record; a way that passes one cell twice (the ways of two restarts that cross) takes the steps there. Upgrade: the index of the cell in the way above kept with cells.
    private void recordFrom() { recordAt = w.mine.above().indexOf(cells.get(stretchEnd)); }

    private void record(BlockPos step) {
        if (recordAt < 0) w.mine.wayCut(step);
        else if (w.mine.wayCut(recordAt, step)) recordAt++;
        else recordAt = -1;
    }

    /** The staircase came out at {@code to}: the cells of the way it led past are left out of the record, if its steps went in in their place. */
    private void cutPast(BlockPos to) { if (recordAt >= 0) w.mine.wayCutPast(recordAt, to); }

    /** The one time the expedition lets the self-rescue work: its walls are the plan at any other. */
    boolean cuttingOut() { return phase == Phase.CUT_OUT && outcome.isEmpty(); }

    private Result unplug() {
        ServerLevel level = (ServerLevel) w.owner.level();
        switch (toPlug.tick()) {
            case WORKING -> { return Result.WORKING; }
            case STUCK -> { return cutOut(); }
            case NO_BLOCK -> {
                if (stone.tickAround(level, w.owner.blockPosition()) || CompanionSeal.available(w.owner) > 0) return Result.WORKING;
                return finish(" Подняться не вышло: в пути наверх нет пола, а блоков, чтобы его настелить, нет, остался где стою.");
            }
            case DONE -> { }
        }
        if (plug.cell() == null) {
            plugs++;
            phase = Phase.TRAVEL;
            return Result.WORKING;
        }
        // No pickaxe that may be spent on rock is no reason to leave a plug of his own where it is: it comes out by hand.
        CompanionExpeditionDig.Status taken = dig.hasTool(level.getBlockState(plug.cell())) ? dig.dig(level, plug.cell()) : dig.takeOut(level, plug.cell());
        return switch (taken) {
            case WORKING -> Result.WORKING;
            case DONE -> {
                dig.flush();
                w.mine.unsealed(plug.cell());
                w.mine.taken(plug.cell());
                plugs++;
                phase = Phase.TRAVEL;
                yield Result.WORKING;
            }
            case FULL -> finish(" Подняться не вышло: свою пробку на пути наверх убрать некуда, сумка полна, остался где стою.");
            case NO_TOOL, NO_HAND, STUCK -> {
                dig.diagnoseDig(plug.cell());
                dig.forget();
                yield cutOut();
            }
        };
    }

    private Result finish(String text) {
        outcome = text;
        dig.keepIron();
        return Result.DONE;
    }

    String text() { return why + outcome; }

    void reset() {
        walk.clear();
        toPlug.clear();
        stone.reset();
        stairs.reset();
        toWay.reset();
        stairSteps = 0;
        why = outcome = "";
        cells = List.of();
        stretchEnd = plugs = cuts = cutWait = rescuesBefore = toWayCell = toWaySteps = recordAt = 0;
        lost = cutToWay = false;
        newStairsWhy = "";
        phase = Phase.WALK;
        plug = null;
    }
}

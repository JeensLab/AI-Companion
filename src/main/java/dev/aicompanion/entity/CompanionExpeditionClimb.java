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
 * self-rescue, which is let work for that alone; a step that cannot be done ends the climb where he stands, said as it is.
 */
final class CompanionExpeditionClimb {
    enum Result { WORKING, DONE }

    private enum Phase { WALK, TRAVEL, UNPLUG, CUT_OUT }

    private static final double SAME_SPOT = 12;
    /** Two cells of the way further apart than this (squared) are not neighbours: the record has a gap between them. */
    private static final double GAP = 9;
    private static final int OWN_STEPS = 4, MAX_PLUGS = 8, IRON_FOR_PLUGS = 8;
    private static final double TRAVEL_NEAR = 1.0;
    /** Rescues to try for one climb, and the ticks given to one to start: it waits a standstill of three seconds and an answer of the route search. */
    private static final int MAX_CUTS = 3, CUT_PATIENCE = 200;
    private static final String CLOSED = " Подняться не вышло: путь наверх закрыт, остался где стою.";

    private final CompanionWork w;
    private final CompanionExpeditionDig dig;
    private final CompanionExpeditionWalk walk;
    /** The walk up the stairs to the plug that shuts the way, cell by cell: a plug far off is not reached by pushing at it in a straight line. */
    private final CompanionExpeditionWalk toPlug;
    /** Stone from the walls where he stands, for the floor of the way up when the bag has none. */
    private final CompanionExpeditionStock stone;
    private String why = "", outcome = "";
    private List<BlockPos> cells = List.of();
    /** The end (exclusive) of the stretch being walked: the cell there, if any, lies across a gap. */
    private int stretchEnd;
    private Phase phase = Phase.WALK;
    private CompanionExpeditionPlugs.Way plug;
    private int plugs;
    private int cuts, cutWait, rescuesBefore;

    CompanionExpeditionClimb(CompanionWork w, CompanionExpeditionDig dig) {
        this.w = w;
        this.dig = dig;
        this.walk = new CompanionExpeditionWalk(dig);
        this.toPlug = new CompanionExpeditionWalk(dig);
        this.stone = new CompanionExpeditionStock(w, dig);
    }

    /** What ran out, for the report. */
    static String shortage(boolean lackedOre, boolean hadStone) {
        if (lackedOre) return "Эту руду на пути нечем взять: нужна кирка покрепче, чем у меня.";
        if (!hadStone) return "Каменной кирки у меня нет, а камень копаю только ею.";
        return "Подходящих кирок не осталось: для камня нужна каменная, для алмазов железная.";
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
        int nearest = nearest(cells, here);
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
        cells = List.of(origin);
        stretchEnd = 0;
        phase = Phase.TRAVEL;
    }

    /** True while the climb has a way to walk; false when {@link #begin} found none and said so. */
    boolean walking() { return outcome.isEmpty(); }

    /** The way up to the surface: {@link #wayUp}, and for a mine started again under the ground the ways above its pit. */
    static List<BlockPos> wayToSurface(CompanionMineSite mine) {
        List<BlockPos> cells = wayUp(mine);
        if (mine.buried()) cells.addAll(mine.above());
        return cells;
    }

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

    /** The index of the cell he stands at (within a few blocks), or -1. */
    static int nearest(List<BlockPos> cells, BlockPos here) {
        int nearest = -1;
        double best = SAME_SPOT;
        for (int i = 0; i < cells.size(); i++) {
            double distance = cells.get(i).distSqr(here);
            if (distance < best || nearest < 0 && distance <= best) { nearest = i; best = distance; }
        }
        return nearest;
    }

    /** The first index after {@code from} whose cell is no neighbour of the one before it, or the size of the list: where the next stretch of cells to walk ends. */
    private static int gapAfter(List<BlockPos> cells, int from) {
        for (int i = Math.max(1, from + 1); i < cells.size(); i++) if (cells.get(i - 1).distSqr(cells.get(i)) > GAP) return i;
        return cells.size();
    }

    Result tick() {
        if (!outcome.isEmpty()) return Result.DONE;
        return switch (phase) {
            case WALK -> walked();
            case TRAVEL -> travel();
            case UNPLUG -> unplug();
            case CUT_OUT -> cutting();
        };
    }

    private Result walked() {
        switch (walk.tick()) {
            case WORKING -> { return Result.WORKING; }
            case DONE -> {
                if (stretchEnd >= cells.size()) return finish(" Копать нечем, поднялся за припасами.");
                phase = Phase.TRAVEL;
                return Result.WORKING;
            }
            default -> {
                // The cell he cannot enter is a gap of its own: a plug over his head or across the step comes out, and rock is cut.
                stretchEnd = walk.index();
                return openWay(cells.get(stretchEnd));
            }
        }
    }

    /** Across a gap of the record: his own steps once near the cell beyond it, a route search until then, and a plug of his that shuts it, near or far, out of the way. */
    private Result travel() {
        if (onSurface()) return finish(" Копать нечем, поднялся за припасами.");
        BlockPos beyond = cells.get(stretchEnd);
        if (w.owner.blockPosition().distManhattan(beyond) <= OWN_STEPS) {
            if (dig.reach(beyond)) return crossed();
            return dig.walkStuck() ? openWay(beyond) : Result.WORKING;
        }
        switch (w.route.legs(beyond, TRAVEL_NEAR)) {
            case NEAR -> { return crossed(); }
            case FAILED -> {
                w.route.legsEnd(false);
                return openWay(beyond);
            }
            default -> { return Result.WORKING; }
        }
    }

    /** Out under the open sky at the height of the entrance on the surface: the climb has done its work, whatever cell of the record lies beyond the gap. */
    private boolean onSurface() {
        ServerLevel level = (ServerLevel) w.owner.level();
        BlockPos top = w.mine.origin() != null ? w.mine.origin() : w.mine.entrance();
        BlockPos here = CompanionExpeditionRecord.standingCell(level, w.owner);
        return here.getY() >= top.getY() - CompanionExpedition.UNDERGROUND_DEPTH && CompanionExpeditionRecord.underSky(level, here.above());
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

    /** No walk and no block of his own joins the two ends. Any pickaxe he has cuts the way up, the wear kept for ore too (out is worth more than ore): without one the rock stays, and so does he. */
    private Result cutOut() {
        if (cuts >= MAX_CUTS || w.items.countCarried(Items.STONE_PICKAXE) == 0 && !dig.ironLeft(0)) return finish(CLOSED);
        cuts++;
        cutWait = 0;
        rescuesBefore = w.owner.recovery.begun();
        w.owner.getNavigation().stop();
        w.expedition.climbTime();
        phase = Phase.CUT_OUT;
        return Result.WORKING;
    }

    /** Stands still with the cell beyond the gap as his destination until the rescue takes over; it stops the work while it runs, and this goes on when it is over. */
    private Result cutting() {
        if (w.owner.recovery.begun() != rescuesBefore) {
            phase = Phase.TRAVEL;
            return Result.WORKING;
        }
        if (++cutWait > CUT_PATIENCE) return finish(CLOSED);
        w.owner.movementIntent(cells.get(stretchEnd));
        return Result.WORKING;
    }

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
            default -> { }
        }
        if (plug.cell() == null) {
            plugs++;
            phase = Phase.TRAVEL;
            return Result.WORKING;
        }
        // No pickaxe that may be spent on rock is no reason to leave a plug of his own where it is: it comes out by hand.
        CompanionExpeditionDig.Status taken = dig.hasTool(level.getBlockState(plug.cell())) ? dig.dig(level, plug.cell()) : dig.takeOut(level, plug.cell());
        switch (taken) {
            case WORKING -> { return Result.WORKING; }
            case DONE -> {
                dig.flush();
                w.mine.unsealed(plug.cell());
                plugs++;
                phase = Phase.TRAVEL;
                return Result.WORKING;
            }
            case FULL -> { return finish(" Подняться не вышло: свою пробку на пути наверх убрать некуда, сумка полна, остался где стою."); }
            default -> {
                dig.diagnoseDig(plug.cell());
                dig.forget();
                return cutOut();
            }
        }
    }

    private Result finish(String text) {
        outcome = text;
        dig.keepIron();
        return Result.DONE;
    }

    /** The reason and how the climb ended. */
    String text() { return why + outcome; }

    void reset() {
        walk.clear();
        toPlug.clear();
        stone.reset();
        why = outcome = "";
        cells = List.of();
        stretchEnd = plugs = cuts = cutWait = rescuesBefore = 0;
        phase = Phase.WALK;
        plug = null;
    }
}

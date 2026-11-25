package dev.aicompanion.entity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The walk to the player has no way at all (a pit, a basin walled on every side), or failed with the player well above him, and he stands in a place
 * that is walled in on every side (a pit, a canyon, a ravine, a hole with a roof over it: the cells he can walk to are few): as a player digs out of a
 * hole, he cuts stairs of his own up towards him (at his own height the goal is raised to the lip of the hole), with the tools he carries (not what
 * he is handing over), a floor laid where there is none and never the block the player stands on. The stairs end once he is out of the pit under the
 * sky, and the ordinary walk goes on from there, or as soon as a walk is possible. In the open (a player on a pillar, a tree or in the air, with ground
 * round him) none is cut: no stairs and no bridge into the air. One climb per order; when the cut gets nowhere the order ends with its honest text. It
 * is kept by the hand-over and used by «иди ко мне» too, and writes nothing into the mine's record.
 */
final class CompanionClimbToPlayer {
    private static final int MAX_STEPS = 48, RECHECK_TICKS = 40, ABOVE = 3, RIM = 6, DROP = 3, LIP_REACH = 64, SUPPORT = 64;
    /**
     * The most cells under the sky a pit may hold: more is ground that leads on. Counted, not a reach round him: the ravines of seed 7 (21.11)
     * held some sixty cells, six wide and fifteen long, and a reach of six took them for open ground. An arena of the GameTests holds some two thousand.
     */
    // ponytail: a closed basin larger than this (a ravine six wide and longer than some eighty blocks) reads as open ground. Upgrade: the route search's own proof of a closed region.
    private static final int BASIN = 512;
    /** A drop of any depth, for the question whether he could leave by jumping down: a ledge, a mesa or a roof is no pit. */
    private static final int FALL = 512;
    private static final String STATUS = "Пути к тебе нет, прорубаю ступени наверх.";

    private final CompanionWork w;
    private CompanionExpeditionStairsUp stairs;
    /** The order the climb was begun for: one per order, running or over. */
    private long task = -1;
    private boolean running;
    private long nextCheck;
    /** The cell the pit was last looked at from: the look is taken again only from another one. */
    private BlockPos lastCheck;
    /** Why the last climb ended where it did, said to the player with the order's own ending; empty when it did not fail or was not begun. */
    private String why = "";

    CompanionClimbToPlayer(CompanionWork w) { this.w = w; }

    /** The climb is cutting stairs for the order that is on now; the self-rescue leaves him alone then. */
    boolean running() { return running && task == w.taskId; }

    /**
     * Begins the climb where the walk has no way, or has failed with the player well above him: farther off than the search sees
     * («no way» is told only within one and a half follow ranges) a pit is a failed walk, never a proven lack of one.
     */
    boolean startWhenStuck(Player player, boolean noWay) {
        boolean beyond = noWay || player.getBlockY() >= w.owner.getBlockY() + ABOVE;
        return (beyond || inPit()) && start(player);
    }

    /** What to add to the order's ending: the honest reason the stairs were not cut (no floor blocks, a full bag, nothing to dig with), for this order only. */
    String whyText() { return task == w.taskId && !why.isEmpty() ? " Ступени наверх не вышли: " + why + "." : ""; }

    /**
     * Begins the climb if he is walled in (not a spot in the open) and this order had none yet. False: the order goes on
     * to its own ending. Seed 7, run restart: with the diamond he came up into a hole five deep, the player at the entrance far off and a block
     * lower, and the hand-over ended «Не смог к тебе подойти» until the goal said «не получилось отдать алмазы».
     */
    private boolean start(Player player) {
        if (task == w.taskId || !closed((ServerLevel) w.owner.level(), w.owner.blockPosition(), Integer.MIN_VALUE, FALL, Integer.MAX_VALUE, BASIN, false)) return false;
        if (stairs == null) {
            var dig = new CompanionExpeditionDig(w);
            // This climb is the way out itself: only the wear for ore is kept, none for a staircase out of a pit.
            dig.ironForRock(CompanionExpeditionDig.SPARE_DURABILITY);
            stairs = new CompanionExpeditionStairsUp(w, dig, false, STATUS);
        }
        if (w.owner.isSelfRescuing()) w.owner.recovery.cancel();
        task = w.taskId;
        running = true;
        nextCheck = 0;
        w.owner.getNavigation().stop();
        w.owner.travel().reset();
        lastCheck = null;
        why = "";
        stairs.begin(goal(player));
        w.owner.workStatus(STATUS);
        return true;
    }

    /**
     * The player's cell, raised to the lip of the hole he stands in: the top of the rock that rises from his feet in the cells round them, taken
     * again at every step. In a pit wider than three the cells round him are its floor until he stands at its wall; taken once there, the lip was
     * the floor and the stairs went level into the wall. Not the heightmap: it stands on a trunk, on leaves and on the barrier roof of a GameTest.
     */
    private BlockPos goal(Player player) {
        var level = (ServerLevel) w.owner.level();
        BlockPos at = w.owner.blockPosition();
        int lip = player.getBlockY();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            BlockPos.MutableBlockPos cell = at.offset(dx, 0, dz).mutable();
            int top = at.getY();
            while (top < at.getY() + LIP_REACH && rock(level, cell.setY(top))) top++;
            lip = Math.max(lip, top);
        }
        return new BlockPos(player.getBlockX(), lip, player.getBlockZ());
    }

    /** A block of the pit's wall: anything a body cannot pass but a trunk or leaves. */
    private static boolean rock(ServerLevel level, BlockPos cell) {
        var state = level.getBlockState(cell);
        return !CompanionShaftPlan.open(level, cell) && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES);
    }

    /** At the bottom of a pit under the sky: no way out of it but up, not even a drop of any depth (a ledge, a mesa or a roof is none). */
    private boolean inPit() { return closed((ServerLevel) w.owner.level(), w.owner.blockPosition(), Integer.MIN_VALUE, FALL, Integer.MAX_VALUE, BASIN, true); }

    /**
     * No walk under the sky leads more than {@link #RIM} cells away from {@code at}: a pit, not a spot beside a trunk, a wall or a cliff, where the
     * ground goes on round them. A walk steps a block up or drops down to {@link #DROP}, never with its feet below {@code lowest}; a cell under a
     * roof (a tunnel, an overhang) is no way out.
     */
    static boolean walledIn(ServerLevel level, BlockPos at, int lowest) { return closed(level, at, lowest, DROP, RIM, Integer.MAX_VALUE, true); }

    /**
     * The walk from {@code at} (drops down to {@code drop}, feet never below {@code lowest}) stays within {@code rim} cells of it and {@code cells} cells;
     * with {@code sky} only under the sky (a cell under a roof is no way out), else anywhere (a hole with a roof over it is walled in too).
     */
    private static boolean closed(ServerLevel level, BlockPos at, int lowest, int drop, int rim, int cells, boolean sky) {
        if (sky && !CompanionExpeditionRecord.underSky(level, at)) return false;
        ArrayDeque<BlockPos> next = new ArrayDeque<>(Set.of(at));
        Set<BlockPos> seen = new HashSet<>(next);
        int walked = 0;
        while (!next.isEmpty()) {
            BlockPos cell = next.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos feet = feetIn(level, cell.relative(d), drop);
                if (feet == null || feet.getY() < lowest || !seen.add(feet) || sky && !CompanionExpeditionRecord.underSky(level, feet)) continue;
                if (++walked > cells || Math.abs(feet.getX() - at.getX()) > rim || Math.abs(feet.getZ() - at.getZ()) > rim) return false;
                next.add(feet);
            }
        }
        return true;
    }

    /** Where the feet go walking into {@code cell}: a block up, level, or down onto the first floor (no deeper than {@code drop}); null for a wall. */
    private static BlockPos feetIn(ServerLevel level, BlockPos cell, int drop) {
        if (!CompanionShaftPlan.open(level, cell)) return CompanionShaftPlan.open(level, cell.above()) && CompanionShaftPlan.open(level, cell.above(2)) ? cell.above() : null;
        if (!CompanionShaftPlan.open(level, cell.above())) return null;
        for (int down = 0; down <= drop && cell.getY() - down > level.getMinBuildHeight(); down++) if (!CompanionShaftPlan.open(level, cell.below(down + 1))) return cell.below(down);
        return null;
    }

    /** One tick of the cut: true while it holds the tick; false once it is over (arrived, a walk is possible, or stuck), and the caller starts its walk again. */
    boolean tick(ServerLevel level, Player player) {
        w.orderProgressPosition = null;
        // In the air the search is skipped and says «no way» to nobody: asked on the ground only.
        if (level.getGameTime() >= nextCheck && w.owner.onGround()) {
            nextCheck = level.getGameTime() + RECHECK_TICKS;
            var reach = w.route.cells(Set.of(player.blockPosition()), 1);
            if (reach.found()) return over();
            // The search is not through (this tick's cells are spent): not «no way», so no cut yet; asked again at the next tick.
            if (reach.busy()) { nextCheck = level.getGameTime() + 1; return true; }
        }
        // Out is under the sky where the walk leads on: a step of his stairs under the rock of the pit's wall is still in it (run water of seed 7, 21.11).
        if (w.owner.onGround() && !w.owner.blockPosition().equals(lastCheck)) {
            lastCheck = w.owner.blockPosition();
            if (CompanionExpeditionRecord.underSky(level, lastCheck) && !closed(level, lastCheck, Integer.MIN_VALUE, DROP, Integer.MAX_VALUE, BASIN, true)) return over();
        }
        stairs.aim(goal(player));
        stairs.keep(support(level, player));
        if (stairs.tick(level) != CompanionExpeditionStairsUp.Result.WORKING || stairs.steps() >= MAX_STEPS) return over();
        return true;
    }

    /** The blocks the player stands on, down the column of rock or soil under his feet (a pillar of his own too): the climb leaves them be. */
    private static Set<BlockPos> support(ServerLevel level, Player player) {
        var box = player.getBoundingBox();
        Set<BlockPos> cells = new HashSet<>();
        int top = Mth.floor(box.minY - 1.0E-3);
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
            for (int y = top; y > top - SUPPORT && !CompanionShaftPlan.open(level, new BlockPos(x, y, z)); y--) cells.add(new BlockPos(x, y, z));
        }
        return cells;
    }

    private boolean over() {
        if (!stairs.why().isEmpty()) why = stairs.why();
        running = false;
        stairs.reset();
        w.owner.getNavigation().stop();
        return false;
    }
}

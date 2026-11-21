package dev.aicompanion.entity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The walk to the player has no way at all (a pit, a basin walled on every side) and the player stands no lower than he does (at his own
 * height the goal is raised to the lip of the hole), or he is at the bottom of a pit under the sky (then the stairs end once he is out of it):
 * as a player digs out of a hole, he cuts stairs of his own up towards him,
 * with the tools he carries and a floor laid where there is none, and the ordinary walk goes on from where the stairs end, or as soon as a walk is possible. One climb per order; when the cut gets nowhere the order ends with its
 * honest text. It is kept by the hand-over and used by «иди ко мне» too, and writes nothing into the mine's record.
 */
final class CompanionClimbToPlayer {
    private static final int MAX_STEPS = 48, RECHECK_TICKS = 40, ABOVE = 3, RIM = 6, DROP = 3;
    private static final String STATUS = "Пути к тебе нет, прорубаю ступени наверх.";

    private final CompanionWork w;
    private CompanionExpeditionStairsUp stairs;
    /** The order the climb was begun for: one per order, running or over. */
    private long task = -1;
    private boolean running;
    private long nextCheck;
    /** Begun for the pit alone (the player lower, or a failed walk to one not well above): it ends once he is out, never level towards him over the land. */
    private boolean pitOnly;
    /** The cell the pit was last looked at from: the look is taken again only from another one. */
    private BlockPos lastCheck;

    CompanionClimbToPlayer(CompanionWork w) { this.w = w; }

    /** The climb is cutting stairs for the order that is on now; the self-rescue leaves him alone then. */
    boolean running() { return running && task == w.taskId; }

    /**
     * Begins the climb where the walk has no way, or has failed with the player well above him: farther off than the search sees
     * («no way» is told only within one and a half follow ranges) a pit is a failed walk, never a proven lack of one.
     */
    boolean startWhenStuck(Player player, boolean noWay) {
        boolean beyond = noWay || player.getBlockY() >= w.owner.getBlockY() + ABOVE;
        return (beyond || inPit()) && start(player, !beyond);
    }

    /**
     * Begins the climb if the player stands no lower, or he is at the bottom of a pit, and this order had none yet. False: the order goes on
     * to its own ending. Seed 7, run restart: with the diamond he came up into a hole five deep, the player at the entrance far off and a block
     * lower, and the hand-over ended «Не смог к тебе подойти» until the goal said «не получилось отдать алмазы».
     */
    private boolean start(Player player, boolean pit) {
        boolean lower = player.getBlockY() < w.owner.getBlockY();
        if (task == w.taskId || lower && !inPit()) return false;
        if (stairs == null) {
            var dig = new CompanionExpeditionDig(w);
            dig.ironForRock = true;
            stairs = new CompanionExpeditionStairsUp(w, dig, false, STATUS);
        }
        if (w.owner.isSelfRescuing()) w.owner.recovery.cancel();
        task = w.taskId;
        running = true;
        nextCheck = 0;
        w.owner.getNavigation().stop();
        w.owner.travel().reset();
        pitOnly = pit || lower;
        lastCheck = null;
        stairs.begin(goal(player));
        w.owner.workStatus(STATUS);
        return true;
    }

    /** The player's cell, raised to the lip of the hole he stands in: the highest top block over the cells round his feet. */
    private BlockPos goal(Player player) {
        var level = w.owner.level();
        BlockPos at = w.owner.blockPosition();
        int lip = player.getBlockY();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dz != 0) lip = Math.max(lip, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX() + dx, at.getZ() + dz));
        return new BlockPos(player.getBlockX(), lip, player.getBlockZ());
    }

    /** At the bottom of a pit under the sky. */
    private boolean inPit() { return walledIn((ServerLevel) w.owner.level(), w.owner.blockPosition(), Integer.MIN_VALUE); }

    /**
     * No walk under the sky leads more than {@link #RIM} cells away from {@code at}: a pit, not a spot beside a trunk, a wall or a cliff, where the
     * ground goes on round them. A walk steps a block up or drops down to {@link #DROP}, never with its feet below {@code lowest}; a cell under a
     * roof (a tunnel, an overhang) is no way out.
     */
    static boolean walledIn(ServerLevel level, BlockPos at, int lowest) {
        if (!CompanionExpeditionRecord.underSky(level, at)) return false;
        ArrayDeque<BlockPos> next = new ArrayDeque<>(Set.of(at));
        Set<BlockPos> seen = new HashSet<>(next);
        while (!next.isEmpty()) {
            BlockPos cell = next.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos feet = feetIn(level, cell.relative(d));
                if (feet == null || feet.getY() < lowest || !seen.add(feet) || !CompanionExpeditionRecord.underSky(level, feet)) continue;
                if (Math.abs(feet.getX() - at.getX()) > RIM || Math.abs(feet.getZ() - at.getZ()) > RIM) return false;
                next.add(feet);
            }
        }
        return true;
    }

    /** Where the feet go walking into {@code cell}: a block up, level, or down onto the first floor (no deeper than {@link #DROP}); null for a wall. */
    private static BlockPos feetIn(ServerLevel level, BlockPos cell) {
        if (!CompanionShaftPlan.open(level, cell)) return CompanionShaftPlan.open(level, cell.above()) && CompanionShaftPlan.open(level, cell.above(2)) ? cell.above() : null;
        if (!CompanionShaftPlan.open(level, cell.above())) return null;
        for (int down = 0; down <= DROP; down++) if (!CompanionShaftPlan.open(level, cell.below(down + 1))) return cell.below(down);
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
        }
        if (pitOnly && w.owner.onGround() && !w.owner.blockPosition().equals(lastCheck) && !walledIn(level, lastCheck = w.owner.blockPosition(), Integer.MIN_VALUE)) return over();
        if (stairs.tick(level) != CompanionExpeditionStairsUp.Result.WORKING || stairs.steps() >= MAX_STEPS) return over();
        return true;
    }

    private boolean over() {
        running = false;
        stairs.reset();
        w.owner.getNavigation().stop();
        return false;
    }
}

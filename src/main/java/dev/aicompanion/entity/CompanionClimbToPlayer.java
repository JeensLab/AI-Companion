package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * The walk to the player has no way at all (a pit, a basin walled on every side) and the player stands higher: as a player digs out of a
 * hole, he cuts stairs of his own up towards him, with the tools he carries and a floor laid where there is none, and the ordinary walk goes
 * on from where the stairs end, or as soon as a walk is possible. One climb per order; when the cut gets nowhere the order ends with its
 * honest text. It is kept by the hand-over and used by «иди ко мне» too, and writes nothing into the mine's record.
 */
final class CompanionClimbToPlayer {
    private static final int MAX_STEPS = 48, RECHECK_TICKS = 40;
    private static final String STATUS = "Пути к тебе нет, прорубаю ступени наверх.";

    private final CompanionWork w;
    private CompanionExpeditionStairsUp stairs;
    /** The order the climb was begun for: one per order, running or over. */
    private long task = -1;
    private boolean running;
    private long nextCheck;

    CompanionClimbToPlayer(CompanionWork w) { this.w = w; }

    /** The climb is cutting stairs for the order that is on now; the self-rescue leaves him alone then. */
    boolean running() { return running && task == w.taskId; }

    /** Begins the climb if the player stands higher and this order had none yet. False: the order goes on to its own ending. */
    boolean start(Player player) {
        if (task == w.taskId || player.getBlockY() <= w.owner.getBlockY()) return false;
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
        stairs.begin(player.blockPosition());
        w.owner.workStatus(STATUS);
        return true;
    }

    /** One tick of the cut: true while it holds the tick; false once it is over (arrived, a walk is possible, or stuck), and the caller starts its walk again. */
    boolean tick(ServerLevel level, Player player) {
        w.orderProgressPosition = null;
        // In the air the search is skipped and says «no way» to nobody: asked on the ground only.
        if (level.getGameTime() >= nextCheck && w.owner.onGround()) {
            nextCheck = level.getGameTime() + RECHECK_TICKS;
            if (Boolean.FALSE.equals(w.route.noWay(player.blockPosition()))) return over();
        }
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

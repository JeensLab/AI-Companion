package dev.aicompanion.entity;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The walk to the player has no way at all (a pit, a basin walled on every side) and the player stands no lower than he does (at his own
 * height the goal is raised to the lip of the hole), or he is at the bottom of a pit under the sky (then the stairs end at its lip):
 * as a player digs out of a hole, he cuts stairs of his own up towards him,
 * with the tools he carries and a floor laid where there is none, and the ordinary walk goes on from where the stairs end, or as soon as a walk is possible. One climb per order; when the cut gets nowhere the order ends with its
 * honest text. It is kept by the hand-over and used by «иди ко мне» too, and writes nothing into the mine's record.
 */
final class CompanionClimbToPlayer {
    private static final int MAX_STEPS = 48, RECHECK_TICKS = 40, ABOVE = 3, PIT = 2;
    private static final String STATUS = "Пути к тебе нет, прорубаю ступени наверх.";

    private final CompanionWork w;
    private CompanionExpeditionStairsUp stairs;
    /** The order the climb was begun for: one per order, running or over. */
    private long task = -1;
    private boolean running;
    private long nextCheck;
    /** For a player lower than him, the lip of the pit: the stairs end there and the walk goes on, never level towards him at that height. */
    private int lipEnd = Integer.MAX_VALUE;

    CompanionClimbToPlayer(CompanionWork w) { this.w = w; }

    /** The climb is cutting stairs for the order that is on now; the self-rescue leaves him alone then. */
    boolean running() { return running && task == w.taskId; }

    /**
     * Begins the climb where the walk has no way, or has failed with the player well above him: farther off than the search sees
     * («no way» is told only within one and a half follow ranges) a pit is a failed walk, never a proven lack of one.
     */
    boolean startWhenStuck(Player player, boolean noWay) {
        return (noWay || player.getBlockY() >= w.owner.getBlockY() + ABOVE || inPit()) && start(player);
    }

    /**
     * Begins the climb if the player stands no lower, or he is at the bottom of a pit, and this order had none yet. False: the order goes on
     * to its own ending. Seed 7, run restart: with the diamond he came up into a hole five deep, the player at the entrance far off and a block
     * lower, and the hand-over ended «Не смог к тебе подойти» until the goal said «не получилось отдать алмазы».
     */
    boolean start(Player player) {
        if (task == w.taskId || player.getBlockY() < w.owner.getBlockY() && !inPit()) return false;
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
        BlockPos goal = goal(player);
        lipEnd = player.getBlockY() < w.owner.getBlockY() ? goal.getY() : Integer.MAX_VALUE;
        stairs.begin(goal);
        w.owner.workStatus(STATUS);
        return true;
    }

    /** The player's cell, raised to the lip of the hole he stands in. */
    private BlockPos goal(Player player) {
        return new BlockPos(player.getBlockX(), Math.max(player.getBlockY(), lip(w.owner.blockPosition())), player.getBlockZ());
    }

    /** The lip of the hole at {@code at}: the highest top block over the cells round it. */
    private int lip(BlockPos at) {
        var level = w.owner.level();
        int lip = Integer.MIN_VALUE;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dz != 0) lip = Math.max(lip, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX() + dx, at.getZ() + dz));
        return lip;
    }

    /** Under the sky with ground round him {@link #PIT} and more over his feet: the bottom of a pit, out of which the walk has no step up. */
    private boolean inPit() {
        BlockPos at = w.owner.blockPosition();
        return CompanionExpeditionRecord.underSky(w.owner.level(), at) && lip(at) >= at.getY() + PIT;
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
        if (w.owner.onGround() && w.owner.getBlockY() >= lipEnd) return over();
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

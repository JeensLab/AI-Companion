package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/** Edits of the mine record the expedition makes when a move fails: what to refuse, where the tunnels go on from, where the mine starts again. */
final class CompanionExpeditionRecord {
    /** A tunnel at least this long is worth moving the base to; the last cells stay beyond the new foot, so its first advances are through open cells. */
    private static final int LONG_TUNNEL = 8, KEPT_AHEAD = 2, MAX_RESTARTS = 3;

    /** Where the tunnels go on from: the way the new base is walked to, and the stairs' direction there. */
    record Base(Direction direction, List<BlockPos> path) { }

    private CompanionExpeditionRecord() { }

    /** A retreat counts, and the move under way is refused so that the next order takes another one instead of walking into the same trouble. */
    static void rememberMove(CompanionMineSite mine, Direction tunnel, CompanionExpeditionStep plan, BlockPos stand, Direction dir) {
        mine.retreated();
        if (tunnel != null) mine.refuse(mine.foot(), tunnel);
        else if (plan != null) mine.refuse(plan.from, plan.turn);
        else if (stand != null && dir != null) mine.refuse(stand, dir);
    }

    /**
     * Every tunnel off the foot is done: the base moves along the longest one, up to two cells short of its end, and the tunnels
     * are forgotten. {@code atFoot}: he stands at the foot, not at the end of the last tunnel. Null when no tunnel is long
     * enough, or the new base would be in a cave he fled from or past the record's cap.
     */
    static Base moveBase(CompanionMineSite mine, boolean atFoot) {
        List<CompanionMineSite.Tunnel> tunnels = mine.tunnels();
        BlockPos foot = mine.foot();
        int best = -1;
        for (int i = 0; i < tunnels.size(); i++) {
            CompanionMineSite.Tunnel t = tunnels.get(i);
            boolean usable = t.length() >= LONG_TUNNEL && !mine.shunned(foot.relative(t.direction(), t.length() - KEPT_AHEAD));
            if (usable && (best < 0 || t.length() > tunnels.get(best).length())) best = i;
        }
        if (best < 0) return null;
        CompanionMineSite.Tunnel longest = tunnels.get(best);
        int last = tunnels.size() - 1, k = longest.length() - KEPT_AHEAD;
        Direction d = longest.direction();
        List<BlockPos> path = new ArrayList<>();
        if (!atFoot) {
            CompanionMineSite.Tunnel end = tunnels.get(last);
            for (int i = end.length(); i >= (best == last ? k : 0); i--) path.add(foot.relative(end.direction(), i));
        }
        if (atFoot || best != last) for (int i = 1; i <= k; i++) path.add(foot.relative(d, i));
        return mine.rebase(k, d) ? new Base(d, path) : null;
    }

    /** The side the mine starts again to from where he stands, or null: restarts are used up, or he is not on firm ground with room to stand. */
    static Direction restartSide(ServerLevel level, CompanionMineSite mine, BlockPos here) {
        BlockPos floor = here.below();
        if (!mine.exists() || mine.restarts() >= MAX_RESTARTS || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return null;
        if (!CompanionShaftPlan.open(level, here) || !CompanionShaftPlan.open(level, here.above())) return null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (CompanionShaftPlan.assessStep(level, here, d, mine.seals(), mine.avoid()).kind() != CompanionShaftPlan.Kind.BLOCKED) return d;
        }
        return null;
    }
}

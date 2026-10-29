package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The way up when the supplies are gone: back along the last tunnel and the stairs he dug to the entrance, one cell at a time.
 * It breaks nothing and places nothing; the record of the mine is the route, and a step that cannot be walked ends the climb
 * where he stands, said as it is.
 */
final class CompanionExpeditionClimb {
    enum Result { WORKING, DONE }

    private static final double SAME_SPOT = 12;

    private final CompanionWork w;
    private final CompanionExpeditionWalk walk;
    private String why = "", outcome = "";

    CompanionExpeditionClimb(CompanionWork w, CompanionExpeditionDig dig) {
        this.w = w;
        this.walk = new CompanionExpeditionWalk(dig);
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
        List<BlockPos> cells = new ArrayList<>();
        List<CompanionMineSite.Tunnel> tunnels = mine.tunnels();
        if (!tunnels.isEmpty()) {
            CompanionMineSite.Tunnel last = tunnels.get(tunnels.size() - 1);
            for (int k = last.length(); k >= 1; k--) cells.add(mine.foot().relative(last.direction(), k));
        }
        List<BlockPos> stands = mine.stands();
        for (int i = stands.size() - 1; i >= 0; i--) cells.add(stands.get(i));
        cells.add(mine.entrance());
        int nearest = -1;
        double best = SAME_SPOT;
        for (int i = 0; i < cells.size(); i++) {
            double distance = cells.get(i).distSqr(here);
            if (distance < best || nearest < 0 && distance <= best) { nearest = i; best = distance; }
        }
        if (nearest < 0) outcome = " Свою лестницу рядом не нашёл, остался где стою.";
        else if (nearest == cells.size() - 1) outcome = " Я и так у входа, остался на месте.";
        else walk.set(cells, nearest);
    }

    Result tick() {
        if (!outcome.isEmpty()) return Result.DONE;
        switch (walk.tick()) {
            case WORKING -> { return Result.WORKING; }
            case DONE -> outcome = " Копать нечем, поднялся за припасами.";
            case STUCK -> outcome = " Подняться по своей лестнице не вышло: застрял на ходу, остался где стою.";
        }
        return Result.DONE;
    }

    /** The reason and how the climb ended. */
    String text() { return why + outcome; }

    void reset() {
        walk.clear();
        why = outcome = "";
    }
}

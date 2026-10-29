package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Up his own staircase when it is too dangerous below: back along the last tunnel and the stairs, a plug of real blocks in the
 * step he has just left so that nothing follows him, then rest at the entrance (he eats, the health comes back) until he is fit
 * again or the time is up. The next step takes the plug out ({@link CompanionMineSite#plug}).
 */
final class CompanionExpeditionRetreat {
    enum Result { WORKING, DONE }

    private enum Phase { CLIMB, PLUG, UP, REST }

    /** The fit health he rests up to, the longest rest and the ticks a plug may take. */
    static final float FIT = 18;
    static final int REST_TICKS = 20 * 240, PLUG_TRIES = 60;
    private static final float HUNGRY = 4;

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionWalk walk;
    private Phase phase = Phase.REST;
    private String reason = "";
    private BlockPos plugCell;
    private boolean climbed, plugged, plugFailed, stuck;
    private long restEnd;
    private int tries;

    CompanionExpeditionRetreat(CompanionWork w, CompanionExpeditionDig dig) {
        this.w = w;
        this.owner = w.owner;
        this.walk = new CompanionExpeditionWalk(dig);
    }

    /** Plans the way up from where he stands; with no way (not on his stairs, or already at the entrance) he rests where he is. */
    void begin(String why) {
        reset();
        reason = why;
        owner.getNavigation().stop();
        CompanionMineSite mine = w.mine;
        if (!mine.exists()) return;
        List<BlockPos> way = CompanionExpeditionClimb.wayUp(mine);
        int at = CompanionExpeditionClimb.nearest(way, owner.blockPosition());
        plugCell = mine.stands().size() >= 2 ? mine.stands().get(1) : null;
        int hold = plugCell == null ? way.size() - 1 : way.size() - 2;
        if (at < 0 || at > hold) { plugCell = null; return; }
        climbed = at < way.size() - 1;
        walk.set(way.subList(0, hold + 1), at);
        phase = Phase.CLIMB;
    }

    Result tick(ServerLevel level) {
        switch (phase) {
            case CLIMB -> climb();
            case PLUG -> plug(level);
            case UP -> up();
            case REST -> { return rest(level); }
        }
        return Result.WORKING;
    }

    private void climb() {
        switch (walk.tick()) {
            case WORKING -> { }
            case DONE -> phase = plugCell == null ? Phase.UP : Phase.PLUG;
            case STUCK -> { stuck = true; phase = Phase.REST; }
        }
    }

    /** The first step up is closed behind him, from the stand above it; nothing is made up, the block is from the bag. */
    private void plug(ServerLevel level) {
        // A torch he hung on the stair wall may stand in the cell: it comes out like a plant, as in a cave plug.
        if (!CompanionSeal.replaceable(level, plugCell) && CompanionShaftPlan.open(level, plugCell)) { level.destroyBlock(plugCell, true, owner); return; }
        CompanionSeal.Result result = CompanionSeal.place(owner, plugCell);
        if (result == CompanionSeal.Result.PLACED) { w.mine.plugged(plugCell); plugged = true; }
        else if (result == CompanionSeal.Result.NO_BLOCK || ++tries > PLUG_TRIES) plugFailed = true;
        else return;
        walk.set(List.of(w.mine.entrance()), 0);
        phase = Phase.UP;
    }

    private void up() {
        switch (walk.tick()) {
            case WORKING -> { }
            case DONE -> phase = Phase.REST;
            case STUCK -> { stuck = true; phase = Phase.REST; }
        }
    }

    private Result rest(ServerLevel level) {
        if (restEnd == 0) restEnd = level.getGameTime() + REST_TICKS;
        boolean fit = owner.getHealth() >= FIT;
        return fit || level.getGameTime() >= restEnd || cannotMend() ? Result.DONE : Result.WORKING;
    }

    /** Nothing left to heal him: no food to eat, and a stomach too empty to heal on its own. */
    private boolean cannotMend() {
        boolean eating = owner.eatTicks > 0;
        boolean wouldEat = owner.getMaxHealth() - owner.getHealth() >= HUNGRY && CompanionEating.hasFood(owner);
        return owner.satiety <= 0 && !eating && !wouldEat;
    }

    /** What he did and how it ended, for the report. */
    String text() {
        StringBuilder text = new StringBuilder("Прервал вылазку и ").append(climbed ? "отступил наверх" : "остановился отдохнуть").append(": ").append(reason).append('.');
        if (plugged) text.append(" Поставил пробку на лестнице.");
        if (plugFailed) text.append(" Пробку на лестнице поставить не вышло.");
        if (stuck) text.append(" Подняться по своей лестнице до конца не вышло, остался где стою.");
        int health = (int) owner.getHealth();
        text.append(owner.getHealth() >= FIT ? " Отдохнул, здоровье " + health + "." : " Отдохнуть до " + (int) FIT + " здоровья не вышло, сейчас " + health + ".");
        return text.toString();
    }

    void reset() {
        walk.clear();
        phase = Phase.REST;
        reason = "";
        plugCell = null;
        climbed = plugged = plugFailed = stuck = false;
        restEnd = 0;
        tries = 0;
    }
}

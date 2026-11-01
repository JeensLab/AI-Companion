package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** The order «иди ко мне»: he walks to the player in legs and says so when there is no way or he got stuck. */
final class CompanionComeBack {
    private final CompanionWork w;
    private BlockPos comeFailedAt;
    private long comeFailedTime;

    CompanionComeBack(CompanionWork w) { this.w = w; }

    void forget() { comeFailedAt = null; }

    void tick(ServerLevel level) {
        var player = w.owner.getOwnerPlayer();
        if (player == null) { w.finish(false, "Не вижу тебя в этом измерении."); return; }
        w.owner.movementIntent(player.blockPosition());
        if (w.owner.distanceToSqr(player) <= 9 && Math.abs(w.owner.getY() - player.getY()) <= 1.25 && w.owner.hasLineOfSight(player) && (w.owner.onGround() || !w.owner.onClimbable())) {
            w.finish(true, "Я рядом."); return;
        }
        w.owner.workStatus("Пытаюсь добраться до игрока.");
        // In legs round walls and ridges, as far as the way goes (CompanionTravel): the player may be near by the straight line and
        // far by the way. Legs that came to nothing are not started again from the same spot at once: the self-rescue takes him over
        // there (down a cave: up through the rock), and the order ends only when he stands still (stalled). Five seconds on, they
        // are tried again even from the same spot: the rescue may have opened the way a few blocks off, and he must walk it.
        if (comeFailedAt == null || level.getGameTime() - comeFailedTime >= 100 || !comeFailedAt.closerThan(w.owner.blockPosition(), 3)) {
            var step = w.route.follow(player.blockPosition(), 2);
            if (step == CompanionTravel.Step.WALKING) { if (!w.owner.travel().searchOnly()) w.orderProgressPosition = null; return; }
            w.owner.travel().reset();
            if (step == CompanionTravel.Step.FAILED) { comeFailedAt = w.owner.blockPosition(); comeFailedTime = level.getGameTime(); }
        }
        if (w.orderHasStalled(level, 240)) {
            // No way at all is told as such; «застрял» is for a way that exists and that he could not get on along.
            Boolean none = w.route.noWay(player.blockPosition());
            if (none == null) return;
            if (none) { w.finish(false, "Не могу к тебе пройти."); return; }
            w.finish(false, "Не смог добраться до тебя. Застрял на " + w.owner.blockPosition().toShortString() + ". Иду за тобой дальше, как получится."); return;
        }
    }
}

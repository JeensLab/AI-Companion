package dev.aicompanion.entity;

import dev.aicompanion.terrain.LandmarkMemory;
import dev.aicompanion.terrain.LandmarkObserver;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;

import java.util.Set;

/** Lead the owner to the remembered standing place. Only a new observation can confirm what is there now. */
final class CompanionLandmarkGuide {
    private static final int MAX_LEAD = 16, REJOIN = 8, SHOW_DISTANCE = 6;
    private final CompanionWork work;
    final LandmarkMemory.Entry landmark;
    private long waitingSince = -1;
    private long finalStepUntil = -1;

    CompanionLandmarkGuide(CompanionWork work, LandmarkMemory.Entry landmark) {
        this.work = work;
        this.landmark = landmark;
    }

    void tick(ServerLevel level) {
        CompanionEntity c = work.owner;
        var player = c.getOwnerPlayer();
        if (player == null || player.level() != level || !landmark.dimension().equals(level.dimension().location().toString())) {
            work.finish(false, "Мы в разных местах. Остановился.");
            return;
        }
        if (c.fighting()) return;
        boolean near = c.distanceToSqr(landmark.viewpoint().getCenter()) <= 1.6 * 1.6;
        int limit = near ? SHOW_DISTANCE : waitingSince >= 0 ? REJOIN : MAX_LEAD;
        if (c.distanceToSqr(player) > limit * limit || near && !c.hasLineOfSight(player)) {
            if (waitingSince < 0) {
                waitingSince = level.getGameTime();
                work.route.stop();
                c.travel().reset();
                finalStepUntil = -1;
                c.workStatus("Остановился, жду тебя. Подойди поближе.");
            }
            c.getLookControl().setLookAt(player, 30, 30);
            if (level.getGameTime() - waitingSince >= 20 * 60)
                work.finish(false, "Ты отстал. Я остановился и пока не показал место.");
            return;
        }
        if (waitingSince >= 0) {
            waitingSince = -1;
            c.workStatus("Идём дальше.");
        }
        if (!near) {
            if (work.route.legs(landmark.viewpoint(), 1.6) == CompanionTravel.Step.FAILED)
                work.finish(false, "Помню, где это было, но до места осмотра сейчас не добраться.");
            return;
        }
        work.route.legsEnd(true);
        c.lookAt(EntityAnchorArgument.Anchor.EYES, landmark.pos().getCenter());
        if (LandmarkObserver.stillVisible(c, landmark.kind(), landmark.pos())) {
            c.swing(InteractionHand.MAIN_HAND);
            work.finish(true, "Вот, посмотри сюда. Вижу " + landmark.label() + ".");
            return;
        }
        // A corner or the lip of a bank can hide the landmark even one block short of the original viewpoint.
        if (!c.blockPosition().equals(landmark.viewpoint()) && takeFinalStep(level)) return;
        if (LandmarkObserver.visiblyGone(c, landmark.kind(), landmark.pos())) {
            c.landmarks().forget(landmark);
            work.finish(false, "Мы у места, где я видел " + landmark.label() + ". Здесь всё изменилось.");
        } else {
            work.finish(false, "Мы у места, где я видел " + landmark.label() + ". Отсюда сейчас не разглядеть.");
        }
    }

    private boolean takeFinalStep(ServerLevel level) {
        CompanionNavigation nav = (CompanionNavigation)work.owner.getNavigation();
        if (finalStepUntil < 0) {
            var path = nav.search(Set.of(landmark.viewpoint()), 0, 6);
            if (path == CompanionNavigation.BUSY) return true;
            if (path == null || !path.canReach() || path.getEndNode() == null ||
                    !path.getEndNode().asBlockPos().equals(landmark.viewpoint()) || !nav.moveTo(path, 1.0)) return false;
            finalStepUntil = level.getGameTime() + 40;
        }
        return !nav.isDone() && level.getGameTime() < finalStepUntil;
    }
}

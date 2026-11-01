package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CompanionHunting {
    final CompanionWork w;

    CompanionHunting(CompanionWork w) { this.w = w; }

    void fightNearby(ServerLevel level) {
        java.util.List<net.minecraft.world.entity.Mob> remaining = new ArrayList<>();
        boolean unavailable = false;
        for (java.util.UUID id : w.huntTargets) {
            if (w.huntDead.contains(id)) continue;
            var entity = level.getEntity(id);
            if (entity == null) { unavailable = true; continue; }
            if (entity instanceof net.minecraft.world.entity.Mob monster && monster.isAlive()) remaining.add(monster);
            else w.huntDead.add(id);
        }
        if (remaining.isEmpty()) {
            // Removed entities may have died or unloaded; only our hits give an exact kill count.
            w.finish(!unavailable, unavailable ? "Часть целей исчезла или выгрузилась. Их гибель не подтверждаю. Моих добивающих ударов: " + w.huntKills + "."
                    : "С выбранными ближайшими монстрами закончил. Моих добивающих ударов: " + w.huntKills + "."); return;
        }
        if (w.orderHasStalled(level, 200)) { w.finish(false, "Не смог добраться до оставшихся монстров. Убил: " + w.huntKills + "."); return; }
        var target = remaining.stream().min(Comparator.comparingDouble(w.owner::distanceToSqr)).orElseThrow();
        w.owner.selectWeaponForCombat(); w.owner.setTarget(target); w.owner.movementIntent(target.blockPosition());
        w.owner.workStatus("Разбираюсь с ближайшими монстрами, осталось целей: " + remaining.size() + ".");
        // In legs, as far as the way goes; on a leg that fails the stall check above ends the order.
        if (w.route.follow(target.blockPosition(), 2) != CompanionTravel.Step.WALKING) w.owner.travel().reset();
    }

    /**
     * hunt_animals, like a player: the nearest grown animal on foot, chased and hit with the best weapon; the meat, leather,
     * wool and feathers fall into the world and are picked up. With «any» the herd of the first kill is kept to while it lasts,
     * so the meat goes into one furnace. Calves and somebody's animals (named, leashed) are spared, and the report says so.
     */
    void hunt(ServerLevel level) {
        if (w.gameKills >= w.gameWanted) { w.finish(true, gameReport(true)); return; }
        var current = level.getEntity(w.gameTarget == null ? CompanionWork.NO_ID : w.gameTarget) instanceof net.minecraft.world.entity.animal.Animal animal && animal.isAlive() ? animal : null;
        if (current == null) {
            if (w.owner.getTarget() instanceof net.minecraft.world.entity.animal.Animal) w.owner.setTarget(null);
            // Vanilla builds no route at all while he is in the air: that is no evidence against any animal.
            if (!w.owner.onGround() && !w.owner.isInWater()) return;
            if (w.gameTravel != null && travelToGame(level)) return;
            List<net.minecraft.world.entity.animal.Animal> game = huntable(level);
            if (!game.isEmpty()) current = nearestGameOnFoot(game);
            // The tick's cells went elsewhere: the animals are asked again next tick, none is skipped.
            if (current == null && !game.isEmpty() && gameBusy) return;
            if (current == null && w.gameTravel != null) { travelToGame(level); return; }
            if (current == null) {
                // None within one route: walk towards the nearest in legs (game log 23.10: «пускай ищет даже если еда далеко»).
                w.gameSkipped.clear();
                List<net.minecraft.world.entity.animal.Animal> any = huntable(level);
                if (any.isEmpty()) { finishHunt(level); return; }
                if (w.gameTravels >= 3) { w.finish(false, gameReport(false) + " К остальным животным не нашёл пути."); return; }
                // Not towards one the wide search has gone round and found no way to: that walk only ends in a corner.
                BlockPos to = null;
                for (var animal : any.stream().sorted(Comparator.comparingDouble(w.owner::distanceToSqr)).toList()) {
                    Boolean none = w.route.provenNoWay(animal.blockPosition());
                    if (none == null) return;
                    if (!none) { to = animal.blockPosition(); break; }
                    w.gameSkipped.put(animal.getUUID(), level.getGameTime() + 20 * 30);
                }
                if (to == null) { w.finish(w.gameKills >= w.gameWanted || w.gameForFood && w.gameKills > 0, gameReport(false) + " К остальным животным пути нет."); return; }
                w.gameTravel = to;
                travelToGame(level);
                return;
            }
            w.gameTarget = current.getUUID(); w.gameSince = level.getGameTime(); w.gameBest = Double.MAX_VALUE; w.gameHealth = current.getHealth();
        }
        w.owner.selectWeaponForCombat(); w.owner.setTarget(current); w.owner.movementIntent(current.blockPosition());
        var kind = CompanionGame.Kind.of(current);
        w.owner.workStatus("Охочусь на " + (kind == null ? "животное" : kind.one) + ", добыл " + w.gameKills + " из " + w.gameWanted + ".");
    }

    /** The walk to game far off; true while walking. On arrival the nearest is chosen again; a failed walk skips them for a while. */
    // The walk to game is to an animal itself (one beyond a search of the game's), not to a place near the herd.
    private boolean gameTravelFar;
    private boolean travelToGame(ServerLevel level) {
        var step = w.route.legs(w.gameTravel, gameTravelFar ? 3 : 10);
        if (step == CompanionTravel.Step.WALKING) { w.owner.workStatus("Иду к животным подальше, добыл " + w.gameKills + " из " + w.gameWanted + "."); return true; }
        w.owner.travel().reset();
        // Each trip counts, arrived or not: an animal near but unreachable (in water) must not send him back and forth.
        w.gameTravels++;
        if (step == CompanionTravel.Step.FAILED) {
            for (var animal : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, new AABB(w.gameTravel).inflate(12)))
                w.gameSkipped.put(animal.getUUID(), level.getGameTime() + 20 * 30);
        }
        w.gameTravel = null; gameTravelFar = false;
        return false;
    }

    /**
     * Every tick, also while the melee goal has taken the legs: a chase that brings him no closer and no hit for 15 seconds,
     * or that runs off further than the hunting ground, lets that animal go for half a minute (it ran into water, onto a cliff).
     */
    void watchChase() {
        if (w.job != CompanionWork.Job.GAME || w.gameTarget == null || !(w.owner.level() instanceof ServerLevel level)) return;
        if (!(level.getEntity(w.gameTarget) instanceof net.minecraft.world.entity.animal.Animal animal) || !animal.isAlive()) return;
        double distance = w.owner.distanceToSqr(animal);
        if (distance + .5 < w.gameBest || animal.getHealth() < w.gameHealth) { w.gameBest = distance; w.gameHealth = animal.getHealth(); w.gameSince = level.getGameTime(); }
        boolean tooFar = !w.owner.leashAllows(animal.blockPosition()) || animal.distanceToSqr(w.gameAnchor.getCenter()) > Math.pow(w.huntRadius() + 16, 2);
        if (level.getGameTime() - w.gameSince <= CompanionWork.CHASE_TICKS && !tooFar) return;
        w.gameSkipped.put(animal.getUUID(), level.getGameTime() + 20 * 30);
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "hunt_target_dropped", "entity", w.owner.getUUID(), "animal", animal.getUUID(),
                "reason", tooFar ? "too_far" : "no_progress", "position", animal.blockPosition());
        w.gameTarget = null;
        if (w.owner.getTarget() == animal) w.owner.setTarget(null);
        w.owner.getNavigation().stop();
    }

    private List<net.minecraft.world.entity.animal.Animal> huntable(ServerLevel level) {
        long now = level.getGameTime();
        w.gameSkipped.values().removeIf(until -> until < now);
        int radius = w.huntRadius();
        List<net.minecraft.world.entity.animal.Animal> found = level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,
                new AABB(w.owner.autonomy().running() ? w.gameAnchor : w.owner.blockPosition()).inflate(radius, 12, radius), animal -> {
                    var kind = CompanionGame.Kind.of(animal);
                    return kind != null && (w.game == null || kind == w.game) && !w.gameSkipped.containsKey(animal.getUUID())
                            && w.owner.leashAllows(animal.blockPosition()) && w.owner.fairTarget(animal, false) && CompanionGame.huntable(animal, kind);
                });
        // A food run accepts every kind. An unreachable member of the previous herd must not hide reachable food.
        // Keep the herd preference only for the ordinary «any» hunt.
        if (!w.gameForFood && w.game == null && w.gameLastKind != null && found.stream().anyMatch(animal -> CompanionGame.Kind.of(animal) == w.gameLastKind))
            return found.stream().filter(animal -> CompanionGame.Kind.of(animal) == w.gameLastKind).toList();
        return found;
    }

    /** One route search to all of them at once: the animal nearest on foot, not across the river by the straight line. */
    private boolean gameBusy;
    private net.minecraft.world.entity.animal.Animal nearestGameOnFoot(List<net.minecraft.world.entity.animal.Animal> game) {
        gameBusy = false;
        java.util.Map<BlockPos, net.minecraft.world.entity.animal.Animal> at = new java.util.HashMap<>();
        for (var animal : game) at.putIfAbsent(animal.blockPosition(), animal);
        var reach = w.route.cells(at.keySet(), 1);
        if (reach.busy()) { gameBusy = true; return null; }
        var best = reach.found() ? at.get(reach.path().getTarget()) : null;
        if (best == null && reach.found()) best = game.stream().min(Comparator.comparingDouble(w.owner::distanceToSqr)).orElse(null);
        if (best == null) {
            // That search may spend its steps on one it cannot reach: the nearest few on their own.
            for (var animal : game.stream().sorted(Comparator.comparingDouble(w.owner::distanceToSqr)).limit(3).toList()) {
                var single = w.route.cells(java.util.Set.of(animal.blockPosition()), 1);
                if (single.busy()) { gameBusy = true; break; }
                if (single.found()) { best = animal; reach = single; break; }
                w.gameSkipped.put(animal.getUUID(), w.owner.level().getGameTime() + 20 * 10);
            }
        }
        // Beyond one search: not lost but far, walked to in legs (travelToGame), then chosen again from close by.
        if (best != null && reach.kind() == CompanionRoute.Kind.FAR) { w.gameTravel = best.blockPosition(); gameTravelFar = true; return null; }
        return best;
    }

    /** Nothing more to take here: an honest account of who was left and why. */
    private void finishHunt(ServerLevel level) {
        int radius = w.huntRadius();
        int young = 0, others = 0;
        for (var animal : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, new AABB(w.owner.blockPosition()).inflate(radius, 12, radius),
                animal -> animal.isAlive() && CompanionGame.Kind.of(animal) != null && (w.game == null || CompanionGame.Kind.of(animal) == w.game))) {
            if (animal.isBaby()) young++;
            else if (animal.hasCustomName() || animal.isLeashed()) others++;
        }
        String why = young + others == 0 ? " В пределах " + radius + " блоков " + (w.game == null ? "животных" : w.game.many) + " больше не нашёл."
                : " Больше брать не стал:" + (young > 0 ? " " + young + " — детёныши" : "")
                + (others > 0 ? (young > 0 ? "," : "") + " " + others + " — с именем или на поводке, чьи-то" : "") + ".";
        w.finish(w.gameKills >= w.gameWanted || w.gameForFood && w.gameKills > 0, gameReport(false) + why);
    }

    /** «Добыл 3 коровы: в сумке прибавилось 5 «Сырая говядина», 2 «Кожа».» — counted in the bag, not promised. */
    private String gameReport(boolean all) {
        int last = w.gameKills % 10, tens = w.gameKills % 100;
        // «Любые», but all of one kind: say which («добыл 1 свинью», not «1 животное»).
        var named = w.game != null ? w.game : w.gameKinds.size() == 1 ? w.gameKinds.iterator().next() : null;
        String animals = named != null ? named.count(w.gameKills) : w.gameKills + " " + (last == 1 && tens != 11 ? "животное" : "животных");
        if (w.gameKills == 0) return CompanionWork.NOTHING_HUNTED + " из " + w.gameWanted + ".";
        java.util.Map<net.minecraft.world.item.Item, Integer> now = new java.util.LinkedHashMap<>();
        for (ItemStack stack : w.items.carriedItems()) if (!stack.isEmpty()) now.merge(stack.getItem(), stack.getCount(), Integer::sum);
        List<String> gained = new ArrayList<>();
        now.forEach((item, count) -> { int more = count - w.gameBagBefore.getOrDefault(item, 0); if (more > 0) gained.add(more + " «" + new ItemStack(item).getHoverName().getString() + "»"); });
        return (all ? "Готово, добыл " : "Добыл ") + animals + (all ? "" : " из " + w.gameWanted) + (gained.isEmpty() ? "." : ": в сумке прибавилось " + String.join(", ", gained) + ".");
    }
}

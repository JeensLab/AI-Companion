package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CompanionStoneMining {
    final CompanionWork w;

    CompanionStoneMining(CompanionWork w) { this.w = w; }

    void mine(ServerLevel level) {
        // On a long walk to far stone the way back is not to the old spot: mining starts anew where he arrives.
        if (w.stoneTravel == null && w.owner.getY() < w.workOrigin.getY() - 1.5) {
            // Vanilla returns no path at all while the mob is airborne, and going down stairs is a series of hops.
            // Such a "no route" is not evidence of a closed exit: ask only on the ground, and believe it twice in a row.
            if (level.getGameTime() >= w.nextReturnCheck && w.owner.onGround()) {
                w.nextReturnCheck = level.getGameTime() + 40;
                Path returnRoute = w.owner.getNavigation().createPath(w.workOrigin, 1);
                boolean blocked = returnRoute == null || !returnRoute.canReach();
                w.returnMisses = blocked ? w.returnMisses + 1 : 0;
                boolean confirmed = w.returnMisses >= 2;
                if (confirmed && !w.returnRouteBlocked) w.returnBlockedSince = level.getGameTime();
                w.returnRouteBlocked = confirmed;
            }
            // Game log 23.10 (05cdd3a7): down a ravine by its ledges, four stones mined at hand, and this check stopped the work
            // («не смог безопасно выбраться») with the two stones for the sword right there. A player takes what he came for and
            // climbs out afterwards; no way back only forbids going deeper (no stairs down below), the climb is the self-rescue
            // once the work no longer holds him here.
        } else { w.returnRouteBlocked = false; w.returnMisses = 0; }
        w.gathered = w.owner.collectedStone() - w.stoneCollectedBefore;
        if (w.gathered >= w.wantedStone) {
            // One more round for the drops given up from where they could not be reached, before leaving the spot (marathon 23.10:
            // a stone in a narrow dip of the ravine stayed behind), as the ore does.
            if (!w.stoneLootRetried) { w.stoneLootRetried = true; w.owner.retryHarvestPickup(); return; }
            // Like a player: walk back up the own stairs first, then craft or store at the surface where the bench and chest are.
            w.resetTarget(); w.quarrySoil.clear();
            if (w.exitCell != null && w.owner.getY() < w.exitCell.getY() - .5) { w.phase = CompanionWork.Phase.EXIT; w.resetApproach(); return; }
            // Down a slope or a cut for the stone, not by his own stairs: the bench and the furnace are set up where he came from,
            // not in the cut (marathon 23.10, «долина»: the bench stood on the step of his way back, no room was left for the
            // furnace and he had to dig his way out).
            if (w.job != CompanionWork.Job.STONE && w.workOrigin != null && w.owner.getY() < w.workOrigin.getY() - 1.5 && w.owner.onGround()) {
                Path back = w.owner.getNavigation().createPath(w.workOrigin, 0);
                if (back != null && back.canReach()) { w.exitCell = w.workOrigin; w.phase = CompanionWork.Phase.EXIT; w.resetApproach(); return; }
            }
            afterMining();
            return;
        }
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        if (w.stoneCandidates == null) {
            if (!w.stoneSearch.scan(level)) { w.owner.workStatus("Ищу выходы камня в пределах " + w.owner.getSearchRadius() + " блоков."); return; }
            w.stoneCandidates = new ArrayList<>(w.stoneSearch.results());
        }
        // Game log 21.10: every surface face was checked one by one while he stood still for two minutes, and the
        // stairs come only after the last one. Stone or walking somewhere new is progress; otherwise go down instead.
        // Game log 22.10: two minutes walking from one far face to the next without a single stone. Walking counts as
        // progress only for a minute; after that without stone the own stairs are dug as well.
        if (w.gathered != w.dryGathered) w.dryWalkTicks = 0;
        boolean walkedInVain = w.quarrySoil.isEmpty() && !w.quarryTried && ++w.dryWalkTicks > CompanionWork.SURFACE_WALK_TICKS && !w.stoneCandidates.isEmpty();
        if (!walkedInVain && (w.gathered != w.dryGathered || w.dryAnchor == null || w.owner.position().distanceToSqr(w.dryAnchor) >= 4 * 4)) {
            w.dryGathered = w.gathered; w.dryAnchor = w.owner.position(); w.dryTicks = 0;
        } else if (walkedInVain || w.quarrySoil.isEmpty() && !w.quarryTried && ++w.dryTicks > CompanionWork.SURFACE_STONE_TICKS && !w.stoneCandidates.isEmpty()) {
            w.dryWalkTicks = 0;
            dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "stone_surface_given_up", "entity", w.owner.getUUID(),
                    "candidates", w.stoneCandidates.size(), "rejected", w.rejected.size(), "seconds", CompanionWork.SURFACE_STONE_TICKS / 20);
            w.stoneCandidates.clear(); w.resetTarget(); w.dryTicks = 0;
        }
        if (w.target != null && !w.quarrySoil.isEmpty() && w.target.equals(w.quarrySoil.peekFirst()) && !CompanionQuarry.diggable(level.getBlockState(w.target))) {
            if (level.getBlockState(w.target).isAir()) { w.quarrySoil.removeFirst(); w.resetTarget(); }
            else { w.finish(false, "Проход к камню изменился. Не буду ломать чужие блоки."); return; }
        }
        if (w.target == null && w.stoneTravel != null && travelToStone(level)) return;
        boolean stairTarget = w.target != null && !w.quarrySoil.isEmpty() && w.target.equals(w.quarrySoil.peekFirst());
        if (w.target == null || !safeToMine(level, w.target) && !isSafeStoneExceptOwnFeet(level, w.target) || !stairTarget && deepensOwnHole(w.target)) {
            w.clearCracks();
            if (w.target != null) w.rejected.add(w.target);
            w.resetTarget();
            while (!w.quarrySoil.isEmpty() && level.getBlockState(w.quarrySoil.peekFirst()).isAir()) w.quarrySoil.removeFirst();
            // A finished staircase may be extended once the stone at its bottom level is used up.
            if (w.quarrySoil.isEmpty() && w.quarryTried && w.stairs < CompanionWork.MAX_STAIRS && w.exitCell != null) w.quarryTried = false;
            if (!w.quarrySoil.isEmpty()) {
                w.target = w.quarrySoil.peekFirst();
                // Someone stepped onto the planned stairs after they were chosen (e7fd0ff7).
                // resetTarget() used to reset the progress clock every tick, followed by a retreat to
                // the very cell he already occupied: indefinite turning until the player pushed him.
                if (!safeToMine(level, w.target) && !safeToMine(level, w.target, true)) {
                    if (!w.target.equals(w.blockedStair)) { w.blockedStair = w.target.immutable(); w.blockedStairSince = level.getGameTime(); }
                    w.owner.getNavigation().stop();
                    w.owner.workStatus("Ступенька занята или небезопасна, ищу другой подход.");
                    if (level.getGameTime() - w.blockedStairSince < 40) return;
                    if (w.blockedStairReplans++ >= 2) { w.finish(false, "Проход к камню занят или небезопасен. Не буду копать под стоящими рядом."); return; }
                    dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "work_replanned", "entity", w.owner.getUUID(),
                            "reason", "stair_became_unsafe", "blocked", w.blockedStair, "attempt", w.blockedStairReplans);
                    w.quarrySoil.clear(); w.quarryTried = false; w.blockedStair = null; w.resetTarget();
                    return;
                }
                w.blockedStair = null;
                // After a pickup Oleg often stands on the very next stair cell. That is his own feet, not a
                // hazard: step off it the same way as for stone instead of abandoning the whole descent.
                if (!safeToMine(level, w.target) && safeToMine(level, w.target, true) && CompanionQuarry.diggable(level.getBlockState(w.target))) {
                    if (w.path == null || w.owner.getNavigation().isDone() || w.owner.getNavigation().isStuck())
                        w.path = CompanionMiningNavigation.pathTo(w.owner, w.target, 3.5, false, true);
                    if (w.path != null && w.path.canReach()) { CompanionMiningNavigation.walk(w.owner, w.path); return; }
                }
                if (!safeToMine(level, w.target) && w.lastHarvestStand != null && CompanionMiningNavigation.standable(w.owner, w.lastHarvestStand)) {
                    Path retreat = w.owner.getNavigation().createPath(w.lastHarvestStand, 0);
                    if (retreat != null && retreat.canReach()) { CompanionMiningNavigation.walk(w.owner, retreat); return; }
                }
                if (!CompanionQuarry.diggable(level.getBlockState(w.target)) || !CompanionQuarry.safeCell(level, w.target) || !safeToMine(level, w.target)) {
                    w.finish(false, "Не могу безопасно продолжить проход к камню. Вещи сохранил."); return;
                }
            } else w.target = nextStone(level);
            if (w.target == null) {
                if (w.selectingStone) { w.owner.workStatus("Проверяю подход к камню, ищу другой выход."); return; }
                if (!w.quarryTried && !w.returnRouteBlocked && (w.exitCell == null || w.workOrigin.getY() - w.owner.getY() < CompanionWork.MAX_MINE_DEPTH)) {
                    w.quarryTried = true;
                    var quarry = CompanionQuarry.find(w.owner, w.wantedStone - w.gathered, w.rejected);
                    if (quarry != null && quarry.cells().stream().allMatch(w.stoneMining::withinWorkDepth)) {
                        w.quarrySoil.addAll(quarry.cells()); w.quarrySupports.addAll(quarry.supports());
                        if (w.exitCell == null) w.exitCell = quarry.start();
                        w.stairs++;
                        w.owner.workStatus(w.stairs == 1 ? "Камня рядом на моём уровне нет. Копаю лестницу вниз, камень со ступенек тоже беру."
                                : "Здесь камень кончился. Продолжаю лестницу ещё ниже."); return;
                    }
                }
                // No stairs possible here (rock or bedrock under him, the stairs done): the far faces, walked to in legs.
                if (startStoneTravel(level)) return;
                w.finish(false, "Добыл " + w.gathered + " из " + w.wantedStone + ". Проверил доступные выходы камня в пределах " + w.owner.getSearchRadius() + " блоков; безопасного подхода или неглубокого спуска не нашёл."); return;
            }
        }
        if (w.items.prepareMiningTool(level.getBlockState(w.target))) return;
        boolean soil = !w.quarrySoil.isEmpty() && w.target.equals(w.quarrySoil.peekFirst()); // a stair cell, soil or stone
        boolean stairStone = soil && isStone(level.getBlockState(w.target));
        // Pickup/movement can put our feet over a queued stair between ticks. Its old queue membership is not permission
        // to swing: re-enter the existing step-off/blocked-stair handling before making any cracks.
        if (soil && !safeToMine(level, w.target)) { w.breaking.reset(); w.resetTarget(); return; }
        if (!soil && !safeToMine(level, w.target)) {
            // Pickup can leave our feet partly over the next stone. Step off it before mining
            // instead of declaring the entire newly opened seam inaccessible.
            if (w.breaking.swinging()) { w.rejectTarget(); return; }
            w.breaking.reset();
            if (w.path == null || w.owner.getNavigation().isDone() || w.owner.getNavigation().isStuck())
                w.path = CompanionMiningNavigation.pathTo(w.owner, w.target, 3.5, false, true);
            if (w.path == null || !w.path.canReach()) { w.rejectTarget(); return; }
            CompanionMiningNavigation.walk(w.owner, w.path); return;
        }
        w.owner.movementIntent(w.target);
        if (!soil && !isStone(level.getBlockState(w.target))) { w.rejectTarget(); return; }
        if ((!soil || stairStone) && !w.items.usablePickaxe(w.owner.getMainHandItem())) {
            w.owner.selectToolForBlock(level.getBlockState(w.target));
            if (!w.items.usablePickaxe(w.owner.getMainHandItem())) { w.resetTarget(); w.phase = CompanionWork.Phase.PICKAXE; return; }
        }
        if (soil && !stairStone) {
            w.owner.selectToolForBlock(level.getBlockState(w.target));
            if (!w.owner.getMainHandItem().isEmpty() && !w.owner.getMainHandItem().is(ItemTags.SHOVELS)) {
                if (!w.owner.unequipForSupport("mainhand").success()) { w.finish(false, "Нет места в сумке, чтобы убрать кирку и освободить руку."); return; }
            }
        }
        w.owner.getLookControl().setLookAt(w.target.getX() + 0.5D, w.target.getY() + 0.5D, w.target.getZ() + 0.5D);
        if (w.owner.distanceToSqr(w.target.getCenter()) > 3.8D * 3.8D || !CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), w.target)
                || !soil && !CompanionMiningNavigation.dropCollectable(level, w.target, w.owner.blockPosition())) {
            // A grazing view from a corner can alternate every tick as he centres himself. Once a real swing loses
            // its approach, choose another face instead of restarting the same cracks dozens of times.
            if (w.breaking.swinging()) {
                if (!soil) w.rejectTarget();
                else {
                    w.rejected.add(w.target.immutable()); w.breaking.reset(); w.quarrySoil.clear(); w.quarryTried = false;
                    if (w.blockedStairReplans++ >= 2) w.finish(CompanionActionResult.blocked("Подход к ступенькам нестабилен. Проверил другие спуски, вещи сохранил."));
                    else { w.resetTarget(); w.owner.workStatus("Потерял безопасный подход к ступеньке, ищу другой спуск."); }
                }
                return;
            }
            w.breaking.reset();
            double distance = w.owner.distanceToSqr(w.target.getCenter());
            if (distance + .05 < w.bestDistance) { w.bestDistance = distance; w.lastProgressTick = level.getGameTime(); }
            if (level.getGameTime() - w.lastProgressTick > 160) { if (soil) w.finish(false, "Не получилось подойти к спуску за камнем."); else w.rejectTarget(); return; }
            if (w.path == null || w.owner.getNavigation().isDone() || w.owner.getNavigation().isStuck()) w.path = CompanionMiningNavigation.pathTo(w.owner, w.target, 3.5, false, false, !soil);
            if (w.path == null || !w.path.canReach()) { if (soil) w.finish(false, "Проход к камню закрыт, остановился."); else w.rejectTarget(); return; }
            CompanionMiningNavigation.walk(w.owner, w.path);
            return;
        }
        if (!soil) {
            // Game log 22.10: a face with stone above was rejected and a face with soil above had the soil dug off first
            // («зачем-то добывает всю землю вокруг камня»). A player just mines the face: the drop falls out towards him and
            // is picked up from the cell in front. Only sand or gravel above is left alone, it would pour into the hole.
            BlockPos overhead = w.target.above();
            BlockState cover = level.getBlockState(overhead);
            if (cover.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) { w.rejectTarget(); return; }
            // Stone on stone at his feet: the upper one first, like a player's 1x2. It is stone too, and the lower drop stays in sight.
            if (isStone(cover) && w.target.getY() <= w.owner.getBlockY() && !w.dug.contains(overhead) && safeToMine(level, overhead)
                    && w.owner.distanceToSqr(overhead.getCenter()) <= 3.8 * 3.8 && CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), overhead)) {
                w.breaking.reset(); w.target = overhead.immutable(); return;
            }
        }
        w.owner.getNavigation().stop();
        BlockState state = level.getBlockState(w.target);
        ItemStack pickaxe = w.owner.getMainHandItem();
        if ((!soil || stairStone) && !pickaxe.isCorrectToolForDrops(state)) { w.phase = CompanionWork.Phase.PICKAXE; w.clearCracks(); return; }
        // Marathon 23.10, «пещера»: for the furnace stone he mined the steps of the passage he had come down by and cut off his own
        // way up («не смог добраться до верстака»). A player leaves the steps he goes back by.
        if ((!soil || w.owner.autonomy().running()) && !w.target.equals(w.stepChecked) && w.workOrigin != null
                && w.owner.getY() < w.workOrigin.getY() - (w.owner.autonomy().running() ? .5 : 1.5)
                && (w.owner.autonomy().running() || w.target.getY() <= w.owner.getBlockY())) {
            w.stepChecked = w.target.immutable();
            BlockPos feet = w.owner.blockPosition();
            if (CompanionMiningNavigation.canWalkBackUp(w.owner, feet, w.workOrigin.getY())
                    && !CompanionMiningNavigation.canWalkBackUp(w.owner, feet, w.workOrigin.getY(), w.target)) {
                if (w.owner.autonomy().running() && soil) w.finish(CompanionActionResult.blocked("Дальнейшая добыча разрушит обратные ступеньки. Остановился, вещи сохранил."));
                else w.rejectTarget();
                return;
            }
        }
        if (!autonomousReturnAllows(w.target)) { if (soil) w.finish(CompanionActionResult.blocked("Безопасный обратный ход не подтверждён. Дальше не копаю, вещи сохранил.")); else w.rejectTarget(); return; }
        w.owner.workStatus(stairStone ? "Копаю ступеньку вниз, камень: " + w.gathered + " из " + w.wantedStone + "."
                : soil ? "Убираю землю для спуска к камню." : "Добываю камень: " + w.gathered + " из " + w.wantedStone + ".");
        if (!w.breaking.tick(level, w.target)) return;
        var drops = Block.getDrops(state, level, w.target, level.getBlockEntity(w.target), w.owner, pickaxe);
        if (drops.isEmpty() || !w.items.canStore(drops)) { w.finish(false, "В сумке нет места для добычи. Освободи место или попроси сложить ресурсы в сундук."); return; }
        w.lastHarvestStand = w.owner.blockPosition();
        if (!autonomousReturnAllows(w.target)) { if (soil) w.finish(CompanionActionResult.blocked("Дальнейшая добыча разрушит обратные ступеньки. Остановился, вещи сохранил.")); else w.rejectTarget(); return; }
        if (!safeToMine(level, w.target) || !level.destroyBlock(w.target, false, w.owner, 512)) { w.rejectTarget(); return; }
        pickaxe.getItem().mineBlock(pickaxe, level, state, w.target, w.owner);
        w.owner.dropHarvest(w.target, drops);
        if (!soil || stairStone) w.owner.observeEvent("stone", "Сломал камень и вижу выпавший материал. Работа ещё может продолжаться.");
        if (soil) w.quarrySoil.removeFirst();
        w.dug.add(w.target.immutable());
        for (Direction side : Direction.values()) {
            BlockPos neighbor = w.target.relative(side);
            if (level.hasChunkAt(neighbor) && isStone(level.getBlockState(neighbor))) {
                w.rejected.remove(neighbor);
                if (!w.stoneCandidates.contains(neighbor)) w.stoneCandidates.add(neighbor.immutable());
            }
        }
        w.rejected.add(w.target);
        w.resetTarget();
        w.cooldown = 3;
    }

    /**
     * Never deepen an own hole: the block under one he already dug makes a pit, repeated a shaft (game log 22.10). Stairs go
     * down instead. Stone lower than his feet is fine otherwise — the ground beside him, the hollow down the slope (game log 22.10:
     * «рядом куча камня», ignored because it lay below his feet). The block right under his body is kept by {@link #safeToMine}.
     * Only lower than his feet is it a pit: the lower half of a face whose upper half he took is the rest of a player's 1x2
     * (game log 22.10, b65ec97a: every such half was refused, the stone around his pit «ran out» and he walked off down the slope).
     */
    private boolean deepensOwnHole(BlockPos pos) { return w.dug.contains(pos.above()) && pos.getY() < w.owner.getBlockY(); }

    private void afterMining() {
        if (w.job == CompanionWork.Job.CRAFT || w.job == CompanionWork.Job.PLACE_CHEST || w.job == CompanionWork.Job.SMELT || w.job == CompanionWork.Job.ORE) {
            w.phase = w.afterStone != null ? w.afterStone : w.job == CompanionWork.Job.CRAFT || w.job == CompanionWork.Job.ORE ? CompanionWork.Phase.CRAFT : w.job == CompanionWork.Job.SMELT ? CompanionWork.Phase.FURNACE : CompanionWork.Phase.STORE;
            w.afterStone = null; w.resetTarget();
        }
        else w.finish(true, mined() + ". В сумке: " + w.owner.getStoredItemsSummary() + ".");
    }

    /** More than ordered only to make up for the stone a tool took from the bag: said, so the larger number is no surprise. */
    private String mined() {
        int extra = w.wantedStone - w.requestedCount;
        return "Добыл " + w.gathered + " блоков камня" + (extra > 0 ? ": " + w.requestedCount + " по заказу и " + extra + " взамен ушедших на инструмент" : "");
    }

    void exitMine(ServerLevel level) {
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        // Up, or at the end of the way a step short of the very cell (the place where mining began is often a block higher
        // than where the route ends beside it: marathon 23.10, he stood there fifteen seconds).
        boolean up = w.owner.getY() >= w.exitCell.getY() - .5 || w.owner.getY() >= w.exitCell.getY() - 1.5 && w.owner.getNavigation().isDone()
                && Vec3.atBottomCenterOf(w.exitCell).distanceToSqr(w.owner.position()) < 3 * 3;
        if (up) { w.owner.getNavigation().stop(); afterMining(); return; }
        if (w.orderHasStalled(level, 300)) {
            // Stone stays in the bag either way; report the position honestly instead of pretending to be out.
            if (w.job == CompanionWork.Job.STONE) { w.finish(true, mined() + ", но наверх пока не выбрался: стою на "
                    + w.owner.blockPosition().toShortString() + ". В сумке: " + w.owner.getStoredItemsSummary() + "."); return; }
            afterMining(); return;
        }
        w.owner.movementIntent(w.exitCell);
        w.owner.workStatus("Поднимаюсь по своей лестнице наверх.");
        if (w.path == null || w.owner.getNavigation().isDone() || w.owner.getNavigation().isStuck()) w.path = w.owner.getNavigation().createPath(w.exitCell, 0);
        if (w.path != null && w.path.canReach()) CompanionMiningNavigation.walk(w.owner, w.path);
    }

    /**
     * Like a player (game log 22.10: «для добычи камня, который перед ним, зачем-то идёт далеко в другую сторону»): first stone
     * he can hit from where he stands, then the face nearest on foot — compared by the real walk and its climbing, not the
     * straight line. Faces are compared in groups by one route search each; a farther group only while it could still be shorter.
     */
    /** A deed of his own: stone and the place to stand for it at most this much below where the stone work began. */
    static final int KEEP_UP = 2;
    private BlockPos nextStone(ServerLevel level) {
        w.selectingStone = false;
        BlockPos here = w.owner.blockPosition();
        // A deed of his own keeps out of pits and ditches (autonomy 23.10, «долина»: down a natural ditch by the quarry he mined its
        // floor, the steps he came down by went, and he had to dig himself out — 6 runs of 9). Stone of his own level or a little
        // lower, or from his own stairs (they are the way back); down a slope he takes the walls, never the floor.
        boolean keepUp = w.owner.ownPlacesOnly() && w.exitCell == null && w.workOrigin != null;
        // The floor stays even below his own stairs: the stairs went out into the same ditch, he dug its floor, and the way back up
        // his stairs was gone (1 run of 10). The stairs themselves go deeper by their own steps (quarrySoil), not by this choice.
        boolean keepFloor = w.owner.ownPlacesOnly() && w.workOrigin != null && w.owner.getY() < w.workOrigin.getY() - 1.5;
        List<BlockPos> sorted = w.stoneCandidates.stream().filter(pos -> !w.rejected.contains(pos) && !deepensOwnHole(pos) && level.hasChunkAt(pos) && w.owner.leashAllows(pos)
                && !(keepFloor && pos.getY() < w.owner.getBlockY()) && !(keepUp && pos.getY() < w.workOrigin.getY() - KEEP_UP)
                && !(w.offFoot.containsKey(pos) && w.offFoot.get(pos).distSqr(here) < CompanionWork.OFF_FOOT_MOVE * CompanionWork.OFF_FOOT_MOVE)
                && !CompanionBuildings.nearCrafted(level, pos)
                && isStone(level.getBlockState(pos)) && safeToMine(level, pos, true))
                .sorted(Comparator.comparingDouble(pos -> w.owner.distanceToSqr(pos.getCenter()))).toList();
        for (BlockPos pos : sorted) {
            if (w.owner.distanceToSqr(pos.getCenter()) > 3.8 * 3.8) break;
            if (safeToMine(level, pos) && CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), pos)
                    && CompanionMiningNavigation.dropCollectable(level, pos, here)) { endStoneChoice(); return pos; }
        }
        for (int searches = 0; ; searches++) {
            // No route is shorter than the straight line minus the reach: what is farther cannot beat the face already found.
            java.util.function.Predicate<BlockPos> canBeat = pos -> w.stoneChoice == null || Math.sqrt(w.owner.distanceToSqr(pos.getCenter())) - 4.5 < w.stoneChoice.length();
            w.stoneSingles.removeIf(pos -> !canBeat.test(pos) || !sorted.contains(pos));
            List<BlockPos> group = new ArrayList<>();
            boolean single = !w.stoneSingles.isEmpty();
            if (single) group.add(w.stoneSingles.getFirst());
            else for (BlockPos pos : sorted) {
                if (w.stoneCompared.contains(pos)) continue;
                if (!canBeat.test(pos)) break;
                group.add(pos);
                if (group.size() >= CompanionWork.STONE_GROUP) break;
            }
            if (group.isEmpty()) {
                CompanionMiningNavigation.Choice best = w.stoneChoice;
                endStoneChoice();
                if (best == null) return null;
                w.path = best.path();
                return best.block();
            }
            if (searches >= 5) { w.selectingStone = true; return null; }
            if (single) w.stoneSingles.removeFirst();
            w.stoneCompared.addAll(group);
            CompanionMiningNavigation.Choice found = CompanionMiningNavigation.nearestOnFoot(w.owner, group, 3.5, false, true, true);
            // His own deed does not walk down into a pit for stone either: such faces are left, his own stairs come instead.
            if (found != null && keepUp && found.path().getTarget().getY() < w.workOrigin.getY() - KEEP_UP) {
                for (BlockPos pos : group) w.offFoot.put(pos.immutable(), here);
                continue;
            }
            // Marathon 23.10, the ravine of game log 05cdd3a7: for the stone he jumped down its ledges to the flooded shafts at the bottom
            // and could not climb back. Down a drop with no way back up on foot, a player digs his own stairs where he stands instead
            // (the way back is the stairs); such a walk is for when no stairs can be dug here.
            if (found != null && (w.owner.autonomy().running()
                    ? !CompanionMiningNavigation.canWalkBackUp(w.owner, found.path().getTarget(), w.owner.autonomy().center().getY())
                    : !w.quarryTried && CompanionMiningNavigation.oneWay(found.path())
                    && !CompanionMiningNavigation.canWalkBackUp(w.owner, found.path().getTarget(), here.getY()))) {
                // The faces of this group lie down the same hole: not tried one by one (that only kept him standing and choosing).
                for (BlockPos pos : group) w.offFoot.put(pos.immutable(), here);
                continue;
            }
            if (found != null) { if (w.stoneChoice == null || found.length() < w.stoneChoice.length()) w.stoneChoice = found; }
            // One search for many targets can spend its steps on a near face that cannot be reached (under the floor, behind
            // a wall) and miss a reachable one: then each face of the group gets its own search, like before.
            else if (group.size() > 1) w.stoneSingles.addAll(group);
            else w.offFoot.put(group.get(0).immutable(), here);
        }
    }
    /**
     * No face within one route, but faces farther off: walk towards the nearest in legs, then choose again from there (game log
     * 23.10: whatever lay beyond one search of the pathfinder counted as unreachable). At most a few trips per order.
     */
    private boolean startStoneTravel(ServerLevel level) {
        if (w.stoneTravels >= 5 || w.quarrySoil.size() > 0) return false;
        BlockPos here = w.owner.blockPosition();
        w.stoneTravel = w.stoneCandidates.stream().filter(pos -> !w.rejected.contains(pos) && level.hasChunkAt(pos) && w.owner.leashAllows(pos)
                        && !(w.owner.ownPlacesOnly() && w.workOrigin != null && pos.getY() < w.workOrigin.getY() - KEEP_UP)
                        // A trip that did not end at stone writes off that whole spot, not one face of it.
                        && w.travelFailed.stream().noneMatch(spot -> spot.distSqr(pos) <= CompanionWork.TRAVEL_SPOT * CompanionWork.TRAVEL_SPOT)
                        && Math.hypot(pos.getX() - here.getX(), pos.getZ() - here.getZ()) > CompanionWork.FAR_STONE && isStone(level.getBlockState(pos))
                        && !CompanionBuildings.nearCrafted(level, pos) && safeToMine(level, pos, true))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(here))).orElse(null);
        return w.stoneTravel != null && travelToStone(level);
    }
    /** True while walking; on arrival the faces are chosen again from there, a failed walk leaves that face out. */
    private boolean travelToStone(ServerLevel level) {
        var step = w.owner.travel().toward(w.stoneTravel, 6);
        if (step == CompanionTravel.Step.WALKING) {
            w.dryTicks = w.dryWalkTicks = 0; // the walk is the progress of this moment, not «walking in vain»
            w.owner.workStatus("Иду к камню подальше: " + w.gathered + " из " + w.wantedStone + ".");
            return true;
        }
        w.owner.travel().reset();
        w.stoneTravels++;
        w.travelFailed.add(w.stoneTravel); // on arrival the faces there are chosen again; if none is reachable, the spot is done
        if (step != CompanionTravel.Step.FAILED) { w.workOrigin = miningOrigin(); w.returnRouteBlocked = false; w.nextReturnCheck = w.returnBlockedSince = 0; w.returnMisses = 0; }
        w.stoneTravel = null; w.offFoot.clear(); endStoneChoice(); w.dryAnchor = null;
        return false;
    }
    void endStoneChoice() { w.stoneChoice = null; w.stoneCompared.clear(); w.stoneSingles.clear(); }

    static boolean isStone(BlockState state) {
        // Natural stone only: cobblestone is what walls and towers are made of (review 22.10, К2).
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.BLACKSTONE);
    }

    private boolean safeToMine(ServerLevel level, BlockPos pos) {
        return safeToMine(level, pos, false);
    }
    private boolean isSafeStoneExceptOwnFeet(ServerLevel level, BlockPos pos) {
        return isStone(level.getBlockState(pos)) && safeToMine(level, pos, true);
    }
    private boolean safeToMine(ServerLevel level, BlockPos pos, boolean ignoreOwner) {
        if (!w.owner.leashAllows(pos)) return false;
        if (!withinWorkDepth(pos)) return false;
        if (CompanionLighting.supportsLight(level, pos)) return false;
        if (w.quarrySupports.contains(pos)) return false;
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos).isEmpty()) return false;
        if (level.getBlockEntity(pos.above()) != null || level.getBlockState(pos.above()).is(Blocks.CRAFTING_TABLE)) return false;
        boolean exposed = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor) || !level.getFluidState(neighbor).isEmpty()) return false;
            if (direction != Direction.DOWN && level.getBlockState(neighbor).getCollisionShape(level, neighbor).isEmpty()) exposed = true;
        }
        // Nobody standing on it, and no drop lying on it: mined from under a drop, the drop falls on into the hole below (marathon
        // 23.10, «долина»: a stone of his own fell two blocks down a crack of the ravine, where nobody could get it).
        return exposed && level.getEntities((net.minecraft.world.entity.Entity)null, new AABB(pos).move(0, 1, 0).inflate(0, 0.1D, 0),
                e -> e.isAlive() && (e instanceof LivingEntity && (!ignoreOwner || e != w.owner) || e instanceof net.minecraft.world.entity.item.ItemEntity)).isEmpty();
    }

    /** A failed return must not make a previous batch's pit floor the next batch's surface. */
    BlockPos miningOrigin() {
        return w.owner.autonomy().running() ? w.owner.autonomy().center() : w.owner.blockPosition();
    }
    private boolean withinWorkDepth(BlockPos pos) {
        return !w.owner.autonomy().running() || pos.getY() >= w.owner.autonomy().center().getY() - CompanionWork.MAX_MINE_DEPTH;
    }
    private boolean autonomousReturnAllows(BlockPos pos) {
        return CompanionMiningNavigation.preservesAutonomousReturn(w.owner, pos);
    }
}

package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class CompanionStoneMining {
    /** Where stone is worked from: within reach, in sight, not on the stone's own block, where its drop can be picked up. */
    private static final CompanionRoute.Need APPROACH = new CompanionRoute.Need(3.5, false, true, true);
    /** Off a cell he stands on and is about to mine: any cell in reach and sight of it. */
    private static final CompanionRoute.Need STEP_OFF = new CompanionRoute.Need(3.5, false, true, false);
    final CompanionWork w;

    CompanionStoneMining(CompanionWork w) { this.w = w; }

    void mine(ServerLevel level) {
        // On a long walk to far stone the way back is not to the old spot: mining starts anew where he arrives.
        if (w.stoneTravel == null && w.owner.getY() < w.workOrigin.getY() - 1.5) {
            // Vanilla returns no path at all while the mob is airborne, and going down stairs is a series of hops.
            // Such a "no route" is not evidence of a closed exit: ask only on the ground, and believe it twice in a row.
            if (level.getGameTime() >= w.nextReturnCheck && w.owner.onGround()) {
                w.nextReturnCheck = level.getGameTime() + 40;
                // A way back longer than one search is a way back (the wide search finds it).
                var back = w.route.cells(Set.of(w.workOrigin), 1);
                boolean blocked = !back.found();
                // Not searched (the tick's cells went elsewhere) is neither a miss nor a way: asked again in two seconds.
                w.returnMisses = back.busy() ? w.returnMisses : blocked ? w.returnMisses + 1 : 0;
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
                var home = w.route.cells(Set.of(w.workOrigin), 0);
                if (home.busy()) return;
                if (home.found()) { w.exitCell = w.workOrigin; w.phase = CompanionWork.Phase.EXIT; w.resetApproach(); return; }
            }
            afterMining();
            return;
        }
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        if (generatorStep(level)) return;
        if (w.stoneCandidates == null) {
            if (!w.stoneSearch.scan(level)) { w.owner.workStatus("Ищу выходы камня в пределах " + w.owner.getSearchRadius() + " блоков."); return; }
            w.stoneCandidates = new ArrayList<>(w.stoneSearch.results());
        }
        // Game log 21.10: every surface face was checked one by one while he stood still for two minutes, and the
        // stairs come only after the last one. Stone or walking somewhere new is progress; otherwise go down instead.
        // Game log 22.10: two minutes walking from one far face to the next without a single stone. Walking counts as
        // progress only for a minute; after that without stone the own stairs are dug as well.
        w.choosingTicks = w.selectingStone ? w.choosingTicks + 1 : 0;
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
                    w.route.stop();
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
                    if (w.path == null || w.route.legOver()) {
                        var step = w.route.to(w.target, STEP_OFF);
                        if (step.busy()) return;
                        w.path = step.path();
                    }
                    if (w.path != null && w.path.canReach()) { w.route.walk(w.path); return; }
                }
                if (!safeToMine(level, w.target) && w.lastHarvestStand != null && CompanionMiningNavigation.standable(w.owner, w.lastHarvestStand)) {
                    int busy = w.route.busyAnswers();
                    Path retreat = w.route.toCells(Set.of(w.lastHarvestStand));
                    if (retreat != null) { w.route.walk(retreat); return; }
                    // This tick's cells went elsewhere: not "no way back" yet, asked again next tick.
                    if (w.route.busyAnswers() != busy) return;
                }
                if (!CompanionQuarry.diggable(level.getBlockState(w.target)) || !CompanionQuarry.safeCell(level, w.target) || !safeToMine(level, w.target)) {
                    w.finish(false, "Не могу безопасно продолжить проход к камню. Вещи сохранил."); return;
                }
            } else w.target = nextStone(level);
            // Past the limit with a face already found: that face (the best so far, as at the search limit), not the stairs, which would
            // throw the choice away.
            if (w.target == null && w.selectingStone && w.stoneChoice != null && w.choosingTicks > CompanionWork.STONE_CHOICE_TICKS) w.target = takeChoice();
            if (w.target == null) {
                // Faces that no route reaches can keep him choosing for twenty seconds (marathon: a plain with the stone below the dirt);
                // after a few the stairs are tried first, and the choice goes on if none can be dug.
                if (w.selectingStone && (w.choosingTicks <= CompanionWork.STONE_CHOICE_TICKS || w.quarryTried)) { w.owner.workStatus("Проверяю подход к камню, ищу другой выход."); return; }
                if (!w.quarryTried && !w.returnRouteBlocked && (w.exitCell == null || w.workOrigin.getY() - w.owner.getY() < CompanionWork.MAX_MINE_DEPTH)) {
                    w.quarryTried = true;
                    int busy = w.route.busyAnswers();
                    var quarry = CompanionQuarry.find(w.owner, w.wantedStone - w.gathered, w.rejected);
                    // A start it could not search to for want of this tick's cells is not one without a way: the plan is made again next tick.
                    if (quarry == null && w.route.busyAnswers() != busy) { w.quarryTried = false; return; }
                    if (quarry != null && quarry.cells().stream().allMatch(w.stoneMining::withinWorkDepth)) {
                        w.quarrySoil.addAll(quarry.cells()); w.quarrySupports.addAll(quarry.supports());
                        if (w.exitCell == null) w.exitCell = quarry.start();
                        w.stairs++;
                        endStoneChoice();
                        w.owner.workStatus(w.stairs == 1 ? "Камня рядом на моём уровне нет. Копаю лестницу вниз, камень со ступенек тоже беру."
                                : "Здесь камень кончился. Продолжаю лестницу ещё ниже."); return;
                    }
                }
                if (w.selectingStone) { w.owner.workStatus("Проверяю подход к камню, ищу другой выход."); return; }
                // No stairs possible here (rock or bedrock under him, the stairs done): the far faces, walked to in legs.
                if (startStoneTravel(level)) return;
                w.finish(false, "Добыл " + w.gathered + " из " + w.wantedStone + (madeUp() > 0 ? " (" + orderAndMadeUp() + ")" : "") + ". Проверил доступные выходы камня в пределах " + w.owner.getSearchRadius() + " блоков; безопасного подхода или неглубокого спуска не нашёл."); return;
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
            if (w.path == null || w.route.legOver()) {
                var step = w.route.to(w.target, STEP_OFF);
                if (step.busy()) return;
                w.path = step.path();
            }
            if (w.path == null || !w.path.canReach()) { w.rejectTarget(); return; }
            w.route.walk(w.path); return;
        }
        w.owner.movementIntent(w.target);
        if (!soil && !isStone(level.getBlockState(w.target))) { w.rejectTarget(); return; }
        if ((!soil || stairStone) && !w.items.usablePickaxe(w.owner.getMainHandItem())) {
            w.owner.selectToolForBlock(level.getBlockState(w.target));
            if (!w.items.usablePickaxe(w.owner.getMainHandItem())) { w.resetTarget(); w.phase = CompanionWork.Phase.PICKAXE; return; }
        }
        if (soil && !stairStone) w.owner.selectToolForBlock(level.getBlockState(w.target));
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
            // The walk (in legs when it is longer than one search) and its patience are the route's.
            if (w.route.approach(level, w.target, new CompanionRoute.Need(3.5, false, false, !soil)) < 0) {
                if (soil) w.finish(false, "Проход к камню закрыт, остановился."); else w.rejectTarget();
            }
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
        w.route.stop();
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
        var drops = CompanionTools.drops(state, level, w.target, level.getBlockEntity(w.target), w.owner, pickaxe);
        if (drops.isEmpty() || !w.items.canStore(drops)) { w.finish(false, "В сумке нет места для добычи. Освободи место или попроси сложить ресурсы в сундук."); return; }
        w.lastHarvestStand = w.owner.blockPosition();
        if (!autonomousReturnAllows(w.target)) { if (soil) w.finish(CompanionActionResult.blocked("Дальнейшая добыча разрушит обратные ступеньки. Остановился, вещи сохранил.")); else w.rejectTarget(); return; }
        if (!safeToMine(level, w.target) || !level.destroyBlock(w.target, false, w.owner, 512)) { w.rejectTarget(); return; }
        CompanionTools.wearOnBreak(pickaxe, level, state, w.target, w.owner);
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

    /** A generator that has not grown its stone back in this long is broken. */
    private static final int GENERATOR_REGROW_TICKS = 100;
    /** A walk to the dry cell by the generator that takes longer than this is no way to it. */
    private static final int GENERATOR_WALK_TICKS = 200;
    private CompanionStoneSearch generatorOrder; // the search of the order the state below belongs to
    private BlockPos generator;
    private long generatorEmptySince = -1;
    private int generatorWalking;
    private int generatorTaken = -1; // the stone he had picked up when the last generator block was mined
    private int generatorLost; // the generator blocks in a row after which no stone was picked up

    /**
     * A cobblestone generator beside him, like a player works one: the stone is mined from a cell that touches no lava and no
     * water, then he waits there until the stone has grown back in the same place and mines it again. {@link #safeToMine} refuses
     * such a stone (lava and water beside it), so this runs before the ordinary search and mines it on its own terms. True while
     * it holds the tick; false when there is no generator, or it is broken (no stone back in time, no dry cell to work it from).
     */
    private boolean generatorStep(ServerLevel level) {
        // His own deeds leave a wall of cobblestone between lava and water alone: it is the player's lava trap, not a generator.
        if (w.owner.ownPlacesOnly()) return false;
        if (generatorOrder != w.stoneSearch) {
            generatorOrder = w.stoneSearch; generatorEmptySince = -1; generatorWalking = 0; generatorTaken = -1; generatorLost = 0;
            generator = nearestGenerator(level);
        }
        if (generator == null) return false;
        BlockState inCell = level.getBlockState(generator);
        if (!(inCell.is(Blocks.COBBLESTONE) || inCell.is(Blocks.STONE)) || !isGeneratorStone(level, generator)) {
            long now = level.getGameTime();
            if (generatorEmptySince < 0) generatorEmptySince = now;
            // Air or a fluid in the cell is the stone still growing (a stone generator fills it with water first); anything else is not.
            boolean growing = inCell.isAir() || inCell.getBlock() instanceof LiquidBlock;
            if (!growing || now - generatorEmptySince > GENERATOR_REGROW_TICKS) { generator = null; return false; }
            w.owner.workStatus("Жду, пока в генераторе вырастет камень: " + w.gathered + " из " + w.wantedStone + ".");
            return true;
        }
        generatorEmptySince = -1;
        BlockPos here = w.owner.blockPosition();
        if (!workableFrom(level, here)) {
            if (++generatorWalking > GENERATOR_WALK_TICKS) { generator = null; return false; }
            if (generatorWalking == 1) w.path = null; // a path left from other work is no way here
            if (w.path == null || w.route.legOver()) {
                Set<BlockPos> stands = new java.util.HashSet<>();
                for (BlockPos cell : CompanionMiningNavigation.standCells(w.owner, generator, 3.5, false, false)) if (workableFrom(level, cell)) stands.add(cell);
                int busy = w.route.busyAnswers();
                w.path = stands.isEmpty() ? null : w.route.toCells(stands);
                // This tick's cells went elsewhere: not "no way" yet, asked again next tick.
                if (w.path == null && !stands.isEmpty() && w.route.busyAnswers() != busy) return true;
            }
            if (w.path == null || !w.path.canReach()) { w.path = null; generator = null; return false; }
            w.owner.workStatus("Иду к сухому месту у генератора камня.");
            w.route.walk(w.path);
            return true;
        }
        generatorWalking = 0;
        w.route.stop();
        BlockState state = level.getBlockState(generator);
        w.target = generator;
        if (w.items.prepareMiningTool(state)) return true;
        if (!w.items.usablePickaxe(w.owner.getMainHandItem())) {
            w.owner.selectToolForBlock(state);
            if (!w.items.usablePickaxe(w.owner.getMainHandItem())) { w.resetTarget(); w.phase = CompanionWork.Phase.PICKAXE; return true; }
        }
        ItemStack pickaxe = w.owner.getMainHandItem();
        if (!pickaxe.isCorrectToolForDrops(state)) { w.resetTarget(); w.phase = CompanionWork.Phase.PICKAXE; return true; }
        w.owner.movementIntent(generator);
        w.owner.getLookControl().setLookAt(generator.getX() + 0.5D, generator.getY() + 0.5D, generator.getZ() + 0.5D);
        w.owner.workStatus("Копаю генератор булыжника: " + w.gathered + " из " + w.wantedStone + ".");
        if (!w.breaking.tick(level, generator)) return true;
        var drops = CompanionTools.drops(state, level, generator, null, w.owner, pickaxe);
        if (drops.isEmpty() || !w.items.canStore(drops)) { w.finish(false, "В сумке нет места для добычи. Освободи место или попроси сложить ресурсы в сундук."); return true; }
        if (!level.destroyBlock(generator, false, w.owner, 512)) { w.resetTarget(); generator = null; return false; }
        CompanionTools.wearOnBreak(pickaxe, level, state, generator, w.owner);
        w.owner.dropHarvest(generator, drops);
        w.owner.observeEvent("stone", "Сломал камень генератора и вижу выпавший материал. Работа ещё может продолжаться.");
        generatorEmptySince = level.getGameTime();
        // The drop of the block before this one was not taken: lost in the pit or the lava. Twice in a row, and he stops feeding it.
        int taken = w.owner.collectedStone();
        if (generatorTaken >= 0) generatorLost = taken > generatorTaken ? 0 : generatorLost + 1;
        generatorTaken = taken;
        if (generatorLost >= 2) generator = null;
        w.resetTarget();
        w.cooldown = 3;
        return true;
    }

    /** Cobblestone or stone with both lava and water beside or over it, the nearest within a few blocks of him. */
    private BlockPos nearestGenerator(ServerLevel level) {
        BlockPos here = w.owner.blockPosition();
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(here.offset(-6, -3, -6), here.offset(6, 3, 6)))
            if (level.hasChunkAt(pos) && isGeneratorStone(level, pos) && w.owner.leashAllows(pos) && (best == null || pos.distSqr(here) < best.distSqr(here))) best = pos.immutable();
        return best;
    }

    private static boolean isGeneratorStone(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.COBBLESTONE) && !state.is(Blocks.STONE)) return false;
        boolean lava = false, water = false;
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (!level.hasChunkAt(next)) return false;
            // Vanilla (LiquidBlock.shouldSpreadLiquid, 1.21.1) hardens lava only by water over it or beside it, and lava comes into
            // the cell from over it or from beside it: a fluid below the stone is neither, the cell would stay lava.
            if (side == Direction.DOWN) continue;
            lava |= level.getFluidState(next).is(FluidTags.LAVA);
            water |= level.getFluidState(next).is(FluidTags.WATER);
        }
        return lava && water;
    }

    /** A cell to work the generator from: his feet and head touch neither lava nor water, the stone is in reach and sight, its drop lands where he can take it. */
    private boolean workableFrom(ServerLevel level, BlockPos feet) {
        if (!CompanionMiningNavigation.standable(w.owner, feet) || Vec3.atBottomCenterOf(feet).distanceToSqr(generator.getCenter()) > 3.5 * 3.5) return false;
        // Not on the generator itself (the stone mined from under his feet) and not on a floor that lava touches.
        BlockPos floor = feet.below();
        if (floor.equals(generator)) return false;
        for (Direction side : Direction.values()) if (level.getFluidState(floor.relative(side)).is(FluidTags.LAVA)) return false;
        for (BlockPos cell : new BlockPos[]{feet, feet.above()}) {
            if (!level.getFluidState(cell).isEmpty()) return false;
            for (Direction side : Direction.values()) if (!level.getFluidState(cell.relative(side)).isEmpty()) return false;
        }
        Vec3 eye = Vec3.atBottomCenterOf(feet).add(0, w.owner.getEyeHeight(), 0);
        return CompanionMiningNavigation.visible(w.owner, eye, generator) && CompanionMiningNavigation.dropCollectable(level, generator, feet);
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
        if (w.job == CompanionWork.Job.CRAFT || w.job == CompanionWork.Job.PLACE_CHEST || w.job == CompanionWork.Job.SMELT || w.job == CompanionWork.Job.ORE || w.job == CompanionWork.Job.HOUSE) {
            w.phase = w.afterStone != null ? w.afterStone : w.job == CompanionWork.Job.CRAFT || w.job == CompanionWork.Job.ORE ? CompanionWork.Phase.CRAFT : w.job == CompanionWork.Job.SMELT ? CompanionWork.Phase.FURNACE : CompanionWork.Phase.STORE;
            w.afterStone = null; w.resetTarget();
        }
        else w.finish(true, mined() + ". В сумке: " + w.owner.getStoredItemsSummary() + ".");
    }

    /** More than ordered only to make up for the stone a tool took from the bag: said, so the larger number is no surprise. */
    private String mined() {
        return "Добыл " + w.gathered + " блоков камня" + (madeUp() > 0 ? ": " + orderAndMadeUp() : "");
    }
    /** Stone added to a stone order for the tools it took; other jobs reuse wantedStone for their own needs. */
    private int madeUp() { return w.job == CompanionWork.Job.STONE ? w.wantedStone - w.requestedCount : 0; }
    private String orderAndMadeUp() { return w.requestedCount + " по заказу и " + madeUp() + " взамен ушедших на инструмент"; }

    void exitMine(ServerLevel level) {
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        // Up, or at the end of the way a step short of the very cell (the place where mining began is often a block higher
        // than where the route ends beside it: marathon 23.10, he stood there fifteen seconds).
        boolean up = w.owner.getY() >= w.exitCell.getY() - .5 || w.owner.getY() >= w.exitCell.getY() - 1.5 && w.route.done()
                && Vec3.atBottomCenterOf(w.exitCell).distanceToSqr(w.owner.position()) < 3 * 3;
        if (up) { w.route.stop(); afterMining(); return; }
        if (w.orderHasStalled(level, 300)) {
            // Stone stays in the bag either way; report the position honestly instead of pretending to be out.
            if (w.job == CompanionWork.Job.STONE) { w.finish(true, mined() + ", но наверх пока не выбрался: стою на "
                    + w.owner.blockPosition().toShortString() + ". В сумке: " + w.owner.getStoredItemsSummary() + "."); return; }
            afterMining(); return;
        }
        w.owner.movementIntent(w.exitCell);
        w.owner.workStatus("Поднимаюсь по своей лестнице наверх.");
        // Up his own stairs, and in legs when they are longer than one search (the way out of a long mine).
        if (w.path == null || w.route.legOver()) w.path = w.route.cells(Set.of(w.exitCell), 0).path();
        if (w.path != null) w.route.walk(w.path);
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
            if (group.isEmpty() || w.stoneChoice != null && w.stoneSearches >= CompanionWork.STONE_SEARCHES) return takeChoice();
            if (searches >= 5 || w.route.busy()) { w.selectingStone = true; return null; }
            if (single) w.stoneSingles.removeFirst();
            w.stoneCompared.addAll(group);
            CompanionRoute.Reach found = w.route.nearest(group, APPROACH);
            // The search goes on next tick: the group is asked again then, not counted as compared.
            if (found.busy()) { w.stoneCompared.removeAll(group); if (single) w.stoneSingles.addFirst(group.get(0)); w.selectingStone = true; return null; }
            if (found.found() || !"no stand cell".equals(found.reason())) w.stoneSearches++;
            if (!found.found()) found = null;
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
        var step = w.route.legs(w.stoneTravel, 6);
        if (step == CompanionTravel.Step.WALKING) {
            w.dryTicks = w.dryWalkTicks = 0; // the walk is the progress of this moment, not «walking in vain»
            w.owner.workStatus("Иду к камню подальше: " + w.gathered + " из " + w.wantedStone + ".");
            return true;
        }
        w.route.legsEnd(step == CompanionTravel.Step.NEAR);
        w.stoneTravels++;
        w.travelFailed.add(w.stoneTravel); // on arrival the faces there are chosen again; if none is reachable, the spot is done
        if (step != CompanionTravel.Step.FAILED) { w.workOrigin = miningOrigin(); w.returnRouteBlocked = false; w.nextReturnCheck = w.returnBlockedSince = 0; w.returnMisses = 0; }
        w.stoneTravel = null; w.offFoot.clear(); endStoneChoice(); w.dryAnchor = null;
        return false;
    }
    /** The best face found by the choice so far (null if none), walked to by its path; the choice is over. */
    private BlockPos takeChoice() {
        CompanionRoute.Reach best = w.stoneChoice;
        endStoneChoice();
        if (best == null) return null;
        w.path = best.path();
        return best.block();
    }
    /** The choice is over (a face taken, stairs begun, the route recovered): its searches and its waiting clock start anew. */
    void endStoneChoice() { w.stoneSearches = 0; w.stoneChoice = null; w.stoneCompared.clear(); w.stoneSingles.clear(); w.selectingStone = false; w.choosingTicks = 0; }

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
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || CompanionLava.at(level, pos)) return false;
        // A stone that holds a pond or a lake back is left, as for ore, unless he stands in water already: there is nothing dry to flood.
        if (!w.owner.isInWater() && CompanionLava.floods(level, pos, List.of())) return false;
        if (level.getBlockEntity(pos.above()) != null || level.getBlockState(pos.above()).is(Blocks.CRAFTING_TABLE)) return false;
        boolean exposed = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor) || CompanionLava.at(level, neighbor)) return false;
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

package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CompanionGatherGoals {
    private CompanionGatherGoals() {}

    static final class GatherLogsGoal extends Goal {
        final CompanionEntity c;
        private BlockPos target;
        private Path approachPath;
        private int retryDelay;
        private int noLogsTicks;
        private int elapsedTicks;
        private int gathered;
        private int inventoryTarget;
        private final CompanionToolSense axeSense;
        private int soilAttempts;
        private int pathFailTicks;
        private int lastProgressTick;
        private int targetStartedTick;
        private double bestTargetDistance;
        private boolean resumeAfterCleanup;
        private int taskVersion;
        private String accessIssue = "";
        private static final int CROWN_ATTEMPTS = 2;
        /** Farther than this a tree with no route yet is walked to, nearer it is climbed or skipped as before. */
        private static final int FAR_TREE = 12;
        /** Most logs a bench and an axe cost. */
        private static final int AXE_WOOD = 3;
        private int crownFailures;
        private final Set<BlockPos> unreachableLogs = new HashSet<>();
        /** Logs of a player's building (no living crown, touching planks, doors, glass…): never chopped. */
        private final Set<BlockPos> builtLogs = new HashSet<>();
        private final Set<BlockPos> currentTreeLogs = new HashSet<>();
        /** Trunks no walk reached from where he stood: not compared on foot again during this request. */
        private final Set<BlockPos> walkTried = new HashSet<>();
        private List<BlockPos> searchCandidates;
        private BlockPos searchOrigin;
        /** The trees round the player were looked at too (once per request). */
        private boolean searchedByPlayer;

        GatherLogsGoal(CompanionEntity c) {
            this.c = c;
            axeSense = new CompanionToolSense(c, "axe", ItemTags.AXES, Blocks.OAK_LOG.defaultBlockState());
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public boolean canUse() {
            return c.gatheringLogs && c.inventoryViewers == 0 && !c.temporaryPillar.isRecovering();
        }

        @Override
        public boolean canContinueToUse() {
            return c.gatheringLogs && taskVersion == c.woodTaskVersion && c.inventoryViewers == 0 && !c.temporaryPillar.isRecovering();
        }

        @Override
        public void start() {
            c.woodBreaking.reset();
            target = null;
            approachPath = null;
            retryDelay = 0;
            if (resumeAfterCleanup && taskVersion == c.woodTaskVersion) {
                resumeAfterCleanup = false;
                return;
            }
            taskVersion = c.woodTaskVersion;
            resumeAfterCleanup = false;
            accessIssue = "";
            noLogsTicks = 0;
            elapsedTicks = 0;
            gathered = Math.max(0, c.requestedLogs - c.logsRemaining);
            // What is still missing: after a reload the logs already chopped are not asked for again (review 22.10, М1).
            inventoryTarget = CompanionTools.count(c, ItemTags.LOGS) + c.logsRemaining;
            axeSense.reset(); soilAttempts = 0;
            pathFailTicks = 0;
            unreachableLogs.clear(); builtLogs.clear(); walkTried.clear();
            currentTreeLogs.clear();
            searchOrigin = c.blockPosition();
            searchCandidates = null; searchedByPlayer = false;
        }

        @Override
        public void tick() {
            if (!c.gatheringLogs) return;
            elapsedTicks++;
            if (c.actionAnimation() != 0 && !c.temporaryPillar.isActive() && !c.temporaryPillar.isRecovering()) return;
            if (elapsedTicks > 20 * 180) {
                finish("Не успел закончить сбор древесины, попробуем позже.");
                return;
            }
            ServerPlayer owner = c.getOwnerPlayer();
            if (owner == null) {
                finish("Я потерял связь с тобой и остановил сбор.");
                return;
            }
            // A just-spawned or falling mob cannot build a ground path yet; this is temporary, not an unreachable tree.
            if (!c.onGround() && !c.isInWater() && !c.temporaryPillar.isActive()) return;
            // Marathon 23.10: with the bag full to the last slot he went from log to log, «не удалось сломать, пропускаю», and never
            // said why. A player sees the full bag first: said at once, nothing thrown away.
            if (!c.hasRoomForLogs()) { finish(CompanionEntity.FULL_BAG_FOR_LOGS); return; }
            if (retryDelay > 0) {
                retryDelay--;
                return;
            }
            c.logsRemaining = Math.max(0, inventoryTarget - CompanionTools.count(c, ItemTags.LOGS));
            c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, c.logsRemaining);
            // On a pillar the hand alternates soil and axe per jump/swing by itself; forcing the axe every tick only flickered.
            // Game log 27.10: past the requested count he chopped the rest of a 32-log tree by hand. The axe for the rest
            // of the tree comes from logs beyond the request, so an unreachable crown or a lone tree never costs the order,
            // and only while the tree still holds as many logs as the axe costs. The tree repays wood, so the axe is a wooden one.
            boolean spareWood = currentTreeLogs.size() >= AXE_WOOD && CompanionTools.count(c, ItemTags.LOGS) - inventoryTarget >= AXE_WOOD;
            if (!c.temporaryPillar.isActive() && (c.logsRemaining > 0 ? axeSense.tick() : spareWood && axeSense.tickWooden())) { c.woodBreaking.reset(); return; }
            if (c.logsRemaining > 0 && !c.temporaryPillar.isActive()) CompanionTools.equip(c, ItemTags.AXES, Blocks.OAK_LOG.defaultBlockState());
            if (target == null || !c.level().getBlockState(target).is(BlockTags.LOGS)) {
                c.woodBreaking.reset();
                if (target != null) currentTreeLogs.remove(target);
                target = selectNextLog();
                approachPath = null;
                pathFailTicks = 0;
                lastProgressTick = targetStartedTick = c.tickCount;
                bestTargetDistance = Double.MAX_VALUE;
            }
            if (target == null) {
                if (c.temporaryPillar.isRecovering()) return;
                if (c.loot.hasPending()) return;
                if (c.logsRemaining == 0 && currentTreeLogs.isEmpty()) {
                    String accessNote = unreachableLogs.isEmpty() ? "Текущее дерево закончил." : "До части брёвен не удалось добраться, поэтому оставил их.";
                    finish("Закончил сбор: срубил " + gathered + " брёвен (цель была " + c.requestedLogs + "). "
                            + accessNote + " У меня в сумке: " + c.getStoredItemsSummary() + ".");
                } else if (++noLogsTicks >= 100) finish(gathered == 0
                        ? c.autonomy.running() ? "В " + CompanionAutonomy.LEASH + " блоках от рабочего места не нашёл доступных деревьев."
                        : "Сбор завершён: в пределах " + c.searchRadius + " блоков не нашёл доступных деревьев."
                        : "Сбор завершён раньше цели: срубил " + gathered + " из " + c.requestedLogs + " брёвен; "
                        + (unreachableLogs.isEmpty() ? "рядом больше нет доступных брёвен. "
                        : "оставшиеся брёвна не удалось достать по пути. ")
                        + "У меня в сумке: " + c.getStoredItemsSummary() + ".");
                return;
            }
            noLogsTicks = 0;
            c.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            c.movementIntent(target);
            if (c.temporaryPillar.isActive()) {
                CompanionPillar.Outcome outcome = c.temporaryPillar.tickToward(target);
                if (switchToBranch()) return;
                if (outcome == CompanionPillar.Outcome.FAILED) {
                    rejectTarget(true);
                    return;
                }
                if (outcome == CompanionPillar.Outcome.WORKING) return;
            }
            double distance = c.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            BlockPos leaves = CompanionMiningNavigation.obstructingLeaves(c, target);
            if (leaves != null && c.distanceToSqr(leaves.getCenter()) <= CompanionEntity.LOG_INTERACTION_RANGE_SQR) {
                c.getNavigation().stop();
                if (c.woodBreaking.tick((ServerLevel)c.level(), leaves)) {
                    c.gathering.mineLog(leaves); c.woodBreaking.reset();
                }
                return;
            }
            if (distance <= CompanionEntity.LOG_INTERACTION_RANGE_SQR && CompanionMiningNavigation.visible(c, c.getEyePosition(), target)) {
                c.getNavigation().stop();
                // With no axe, put away held building supplies and use a real empty hand.
                if (c.getOffhandItem().is(ItemTags.AXES)) c.unequipForSupport("offhand");
                c.selectToolForBlock(c.level().getBlockState(target));
                if (!c.getMainHandItem().isEmpty() && !c.getMainHandItem().is(ItemTags.AXES)) {
                    CompanionActionResult emptyHand = c.unequipForSupport("mainhand");
                    if (!emptyHand.success()) { finish("В сумке нет места, чтобы освободить руку для рубки."); return; }
                }
                if (!c.woodBreaking.tick((ServerLevel)c.level(), target)) return;
                if (c.gathering.mineLog(target)) {
                    gathered++;
                    c.logsRemaining = Math.max(0, inventoryTarget - CompanionTools.count(c, ItemTags.LOGS));
                    c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, c.logsRemaining);
                    c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, gathered >= c.requestedLogs
                            ? "Цель по брёвнам выполнена; заканчиваю текущее дерево."
                            : "Срубил " + gathered + " из " + c.requestedLogs + " брёвен.");
                } else {
                    unreachableLogs.add(target.immutable());
                    currentTreeLogs.remove(target);
                    c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "Не удалось сломать это бревно, пропускаю его и ищу доступное.");
                }
                c.woodBreaking.reset();
                currentTreeLogs.remove(target);
                target = null;
                approachPath = null;
                retryDelay = 4;
                pathFailTicks = 0;
            } else {
                c.woodBreaking.reset();
                if (distance + 0.1D < bestTargetDistance) {
                    bestTargetDistance = distance;
                    lastProgressTick = c.tickCount;
                }
                // Accepting a path is not evidence that the mob is actually getting closer.
                if (c.tickCount - targetStartedTick > 20 * 30) { rejectTarget(false); return; }
                boolean noProgress = c.tickCount - lastProgressTick >= 60;
                // Navigation considers a waypoint reached before the mob is exactly centered.
                // Give the movement controller time to line up a visible face before trying a pillar.
                if (approachPath != null && (c.getNavigation().isStuck() || noProgress)) {
                    tryClimbing();
                    return;
                }
                if (approachPath == null) approachPath = findApproachPath(target);
                // A tree far off over hills: walked to in legs first, not declared out of reach (game log 23.10, aa07e605).
                if (approachPath == null && Math.hypot(target.getX() + .5 - c.getX(), target.getZ() + .5 - c.getZ()) > FAR_TREE) {
                    // To the foot of the trunk, as a player walks to a tree: a route to a log up in the crown is never found, and its parts
                    // led him off swimming round an island (marathon 23.10, «берег»).
                    BlockPos foot = target;
                    while (foot.getY() > c.level().getMinBuildHeight() && c.level().getBlockState(foot.below()).is(BlockTags.LOGS)) foot = foot.below();
                    var step = c.travel.toward(foot, FAR_TREE - 4);
                    if (step == CompanionTravel.Step.WALKING) {
                        targetStartedTick = lastProgressTick = c.tickCount;
                        c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "Иду к дереву подальше.");
                        return;
                    }
                    c.travel.reset();
                    if (step == CompanionTravel.Step.FAILED) { rejectTarget(false); return; }
                    approachPath = findApproachPath(target);
                }
                if (approachPath == null) { tryClimbing(); return; }
                CompanionMiningNavigation.walk(c, approachPath);
            }
        }

        private void tryClimbing() {
            CompanionPillar.Outcome outcome = c.temporaryPillar.tickToward(target);
            if (switchToBranch()) return;
            if (outcome == CompanionPillar.Outcome.FAILED && c.temporaryPillar.failureReason().contains("земли") && soilAttempts++ < 2) {
                c.savedWoodRequested = c.requestedLogs; c.savedWoodRemaining = c.logsRemaining;
                c.supportSoilTask = true;
                c.startGatheringDirtForWork(Math.max(1, 6 - CompanionTools.soil(c)));
                c.workStatus("Для верхушки нужна опора. Сначала добуду немного земли.");
            }
            else if (outcome == CompanionPillar.Outcome.FAILED) rejectTarget(true);
            else if (outcome == CompanionPillar.Outcome.READY && !CompanionMiningNavigation.visible(c, c.getEyePosition(), target)) rejectTarget(false);
            else c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "Поднимаюсь на временной опоре, чтобы достать верхние брёвна.");
        }

        /** A crown log over the pillar is chopped from where Oleg already stands, instead of abandoning the crown. */
        private boolean switchToBranch() {
            BlockPos branch = c.temporaryPillar.takeBranch();
            if (branch == null || unreachableLogs.contains(branch)) return false;
            c.woodBreaking.reset();
            currentTreeLogs.add(branch);
            target = branch;
            approachPath = null;
            lastProgressTick = targetStartedTick = c.tickCount;
            bestTargetDistance = Double.MAX_VALUE;
            c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "Над опорой ветка, срублю её отсюда.");
            return true;
        }

        private void rejectTarget(boolean skipHigherCrown) {
            c.woodBreaking.reset();
            // One failed climb is not proof that the whole crown is out of reach: try another log of it once more.
            // A crown that is simply too tall, or a second failure on the same tree, still ends with the honest skip.
            String reason = c.temporaryPillar.failureReason();
            if (skipHigherCrown && ++crownFailures < CROWN_ATTEMPTS && !reason.contains("слишком высокая")) skipHigherCrown = false;
            if (skipHigherCrown) accessIssue = reason;
            if (target != null) {
                unreachableLogs.add(target.immutable());
                if (skipHigherCrown) {
                    for (BlockPos log : currentTreeLogs) if (log.getY() >= target.getY()) unreachableLogs.add(log);
                }
                currentTreeLogs.removeIf(unreachableLogs::contains);
            }
            c.temporaryPillar.requestCleanup();
            c.getEntityData().set(CompanionEntity.DATA_GATHERING_LOGS, true);
            c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "До этой части дерева безопасно не добраться, ищу другую.");
            target = null;
            approachPath = null;
            c.getNavigation().stop();
            retryDelay = 2;
        }

        @Override
        public void stop() {
            c.woodBreaking.reset();
            c.getNavigation().stop();
            target = null;
            c.temporaryPillar.requestCleanup();
            resumeAfterCleanup = c.gatheringLogs || c.supportSoilTask;
        }

        private BlockPos findNearestLog() {
            if (!(c.level() instanceof ServerLevel serverLevel)) return null;
            if (searchCandidates == null) searchCandidates = CompanionLogSearch.find(serverLevel, searchOrigin, c.searchRadius);
            List<BlockPos> nearest = searchCandidates.stream().filter(candidate -> !unreachableLogs.contains(candidate) && !builtLogs.contains(candidate)
                    && c.leashAllows(candidate) && c.level().hasChunkAt(candidate) && c.level().getBlockState(candidate).is(BlockTags.LOGS))
                    .sorted(Comparator.comparingDouble(candidate -> candidate.distSqr(c.blockPosition()) + Math.max(0, candidate.getY() - c.getY()) * 4))
                    .limit(64).toList();
            // Marathon 23.10, «берег»: the trees were looked for once, from the mainland; on the island the two farther ones were beyond
            // that radius and «рядом больше нет доступных брёвен» with them in sight. Walked far from where he looked: look again here.
            if (nearest.isEmpty() && searchOrigin != null && searchOrigin.distSqr(c.blockPosition()) > 4 * 4) {
                // What was seen from the first place stays on the list; what is seen from here is added.
                searchOrigin = c.blockPosition();
                Set<BlockPos> merged = new java.util.LinkedHashSet<>(searchCandidates);
                merged.addAll(CompanionLogSearch.find(serverLevel, searchOrigin, c.searchRadius));
                searchCandidates = new java.util.ArrayList<>(merged);
                return findNearestLog();
            }
            // The order was «near the player»: the trees round him count too (marathon 23.10, night in «долина»: driving monsters off
            // he had gone twelve blocks from the player, and from there the forest by the player was two blocks beyond the radius).
            var player = c.getOwnerPlayer();
            if (nearest.isEmpty() && !searchedByPlayer && !c.autonomy.running() && player != null && player.level() == c.level() && player.blockPosition().distSqr(c.blockPosition()) > 4 * 4) {
                searchedByPlayer = true;
                Set<BlockPos> merged = new java.util.LinkedHashSet<>(searchCandidates);
                merged.addAll(CompanionLogSearch.find(serverLevel, player.blockPosition(), c.searchRadius));
                searchCandidates = new java.util.ArrayList<>(merged);
                return findNearestLog();
            }
            if (nearest.isEmpty()) return null;
            // Like stone (game log 22.10): of the nearest trunks, the one nearest on foot, not across a ravine by the straight line.
            // None reachable on foot: the nearest, for the climbing and support logic, as before.
            Set<Long> trunks = new HashSet<>();
            List<BlockPos> group = nearest.stream().filter(candidate -> !walkTried.contains(candidate)
                    && trunks.add(BlockPos.asLong(candidate.getX(), 0, candidate.getZ()))).limit(8).toList();
            var walk = group.isEmpty() ? null : CompanionMiningNavigation.nearestOnFoot(c, group, 3.7, true, false);
            if (walk == null) walkTried.addAll(group);
            return walk == null ? nearest.get(0) : walk.block();
        }
        /** Keep working on the connected tree after the requested minimum is reached. */
        private BlockPos selectNextLog() {
            currentTreeLogs.removeIf(pos -> unreachableLogs.contains(pos) || !c.level().hasChunkAt(pos)
                    || !c.level().getBlockState(pos).is(BlockTags.LOGS));
            if (currentTreeLogs.isEmpty()) {
                if (c.temporaryPillar.hasSupports()) { c.temporaryPillar.requestCleanup(); return null; }
                // Do not start another tree and its climbing prerequisites while the last
                // already harvested log is still waiting for its ground pickup delay.
                if (c.logsRemaining > 0 && c.loot.incomingLogs() >= c.logsRemaining) return null;
                if (CompanionTools.count(c, ItemTags.LOGS) >= inventoryTarget) return null;
                // Review 22.10, К2: a log cabin is not a tree. A few groups a tick, nearest first.
                for (int tries = 0; tries < 8 && currentTreeLogs.isEmpty(); tries++) {
                    BlockPos seed = findNearestLog();
                    if (seed == null) return null;
                    collectConnectedLogs(seed);
                    if (!CompanionBuildings.builtLogs((ServerLevel) c.level(), currentTreeLogs)
                            && !(c.ownPlacesOnly() && CompanionBuildings.decorated((ServerLevel) c.level(), currentTreeLogs))) break;
                    dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "building_logs_skipped", "entity", c.getUUID(), "seed", seed, "logs", currentTreeLogs.size());
                    builtLogs.addAll(currentTreeLogs); currentTreeLogs.clear();
                }
                if (currentTreeLogs.isEmpty()) return null;
            }
            BlockPos best = null;
            double bestScore = Double.MAX_VALUE;
            BlockPos origin = c.blockPosition();
            for (BlockPos log : currentTreeLogs) {
                double score = c.distanceToSqr(log.getX() + 0.5D, log.getY() + 0.5D, log.getZ() + 0.5D)
                        + Math.max(0, log.getY() - origin.getY()) * 4.0D;
                if (score < bestScore) { bestScore = score; best = log; }
            }
            return best;
        }

        private void collectConnectedLogs(BlockPos seed) {
            crownFailures = 0;
            Deque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();
            queue.add(seed.immutable());
            visited.add(seed.immutable());
            while (!queue.isEmpty() && currentTreeLogs.size() < 512) {
                BlockPos pos = queue.removeFirst();
                if (!c.level().hasChunkAt(pos) || !c.level().getBlockState(pos).is(BlockTags.LOGS)) continue;
                currentTreeLogs.add(pos.immutable());
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos next = pos.offset(dx, dy, dz);
                            if (Math.abs(next.getX() - seed.getX()) > 12 || Math.abs(next.getZ() - seed.getZ()) > 12
                                    || Math.abs(next.getY() - seed.getY()) > 24 || !visited.add(next)) continue;
                            if (c.level().hasChunkAt(next) && c.level().getBlockState(next).is(BlockTags.LOGS)) queue.addLast(next);
                        }
                    }
                }
            }
        }

        /** Find a reachable standing spot next to the log; never path into the solid log itself. */
        private Path findApproachPath(BlockPos log) {
            Path path = CompanionMiningNavigation.pathTo(c, log, 3.7, true);
            return path != null && path.canReach() ? path : null;
        }

        private void finish(String message) {
            c.woodBreaking.reset();
            if (!accessIssue.isBlank()) message += " " + accessIssue + ".";
            c.lastTaskSucceeded = CompanionTools.count(c, ItemTags.LOGS) >= inventoryTarget;
            c.gatheringLogs = false;
            c.logsRemaining = 0;
            c.getEntityData().set(CompanionEntity.DATA_GATHERING_LOGS, false);
            c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, 0);
            c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, message);
            target = null;
            // As a step of a work the work's own result is the deed.
            if (c.work.isActive()) c.syncWorkState();
            else { c.noteDeed(message, c.lastTaskSucceeded); c.finishFollowing(); }
        }
    }

    static final class GatherDirtGoal extends Goal {
        final CompanionEntity c;
        private BlockPos dirtOrigin;
        private BlockPos target;
        private Path approachPath;
        private final Set<BlockPos> unreachable = new HashSet<>();
        private int retryDelay;
        private int noDirtTicks;
        private int elapsedTicks;
        private int gathered;
        private int pathFailTicks;
        private int inventoryTarget;
        private int taskVersion;
        private boolean resume;
        private final CompanionToolSense shovelSense;
        private int targetSince, targetProgress;
        private double targetBest;

        GatherDirtGoal(CompanionEntity c) {
            this.c = c;
            shovelSense = new CompanionToolSense(c, "shovel", ItemTags.SHOVELS, Blocks.DIRT.defaultBlockState());
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public boolean canUse() { return c.gatheringDirt && c.inventoryViewers == 0; }
        @Override public boolean canContinueToUse() { return c.gatheringDirt && c.inventoryViewers == 0; }

        @Override public void start() {
            c.dirtBreaking.reset();
            target = null;
            approachPath = null;
            if (resume && taskVersion == c.dirtTaskVersion) { resume = false; return; }
            resume = false; taskVersion = c.dirtTaskVersion;
            dirtOrigin = c.blockPosition();
            retryDelay = noDirtTicks = elapsedTicks = pathFailTicks = 0;
            gathered = Math.max(0, c.requestedLogs - c.logsRemaining);
            inventoryTarget = CompanionTools.soil(c) + c.logsRemaining;
            shovelSense.reset();
            unreachable.clear();
        }

        @Override public void tick() {
            if (!c.gatheringDirt) return;
            if (c.actionAnimation() != 0) return;
            if (++elapsedTicks > 20 * 180) { finish("Остановил добычу земли по тайм-ауту."); return; }
            if (c.getOwnerPlayer() == null) { finish("Я потерял связь с тобой и остановил добычу земли."); return; }
            c.logsRemaining = Math.max(0, inventoryTarget - CompanionTools.soil(c));
            c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, c.logsRemaining);
            if (c.logsRemaining <= 0 && !c.loot.hasPending()) { finish("Добыча земли завершена: добыл " + gathered + " блоков. В сумке: " + c.getStoredItemsSummary() + "."); return; }
            if (c.logsRemaining > 0 && c.loot.incomingSoil() >= c.logsRemaining) { c.dirtBreaking.reset(); c.getNavigation().stop(); return; }
            // Soil for a support under a crown: by hand, a shovel would eat the wood just cut (marathon 23.10: «добудь дерева» ended
            // with fewer logs than asked, one gone into a shovel for six blocks of dirt).
            if (!c.supportSoilTask && shovelSense.tick()) { c.dirtBreaking.reset(); approachPath = null; return; }
            CompanionTools.equip(c, ItemTags.SHOVELS, Blocks.DIRT.defaultBlockState());
            if (retryDelay > 0) { retryDelay--; return; }

            if (target != null && (CompanionMiningNavigation.supportsLivingEntity(c, target)
                    || !CompanionQuarry.safeCell((ServerLevel)c.level(), target))) {
                unreachable.add(target); c.dirtBreaking.reset(); target = null;
            }
            if (target == null || !c.level().hasChunkAt(target) || !CompanionGathering.isDirtBlock(c.level().getBlockState(target))) {
                c.dirtBreaking.reset();
                target = findNearestDirt();
                approachPath = null;
                pathFailTicks = 0;
                targetSince = targetProgress = c.tickCount; targetBest = Double.MAX_VALUE;
            }
            if (target == null) {
                if (++noDirtTicks >= 80) finish("Нашёл только " + gathered + " из " + c.requestedLogs
                        + " блоков земли поблизости. Поставь меня у открытого участка земли и попроси снова.");
                return;
            }
            noDirtTicks = 0;
            c.movementIntent(target);
            c.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            double distance = c.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
            if (!CompanionMiningNavigation.preservesAutonomousReturn(c, target)) {
                unreachable.add(target.immutable()); c.dirtBreaking.reset(); target = null; approachPath = null; c.getNavigation().stop(); return;
            }
            if (distance + .05 < targetBest) { targetBest = distance; targetProgress = c.tickCount; }
            if (c.tickCount - targetSince > 20 * 30 || c.tickCount - targetProgress > 160) {
                unreachable.add(target.immutable()); c.dirtBreaking.reset(); target = null; approachPath = null; c.getNavigation().stop(); return;
            }
            if (distance <= CompanionEntity.LOG_INTERACTION_RANGE_SQR && CompanionMiningNavigation.visible(c, c.getEyePosition(), target)
                    && CompanionMiningNavigation.dropCollectable((ServerLevel)c.level(), target, c.blockPosition())) {
                c.getNavigation().stop();
                c.selectToolForBlock(c.level().getBlockState(target));
                if (!c.dirtBreaking.tick((ServerLevel)c.level(), target)) return;
                if (c.gathering.mineDirt(target)) {
                    gathered++;
                    c.logsRemaining = Math.max(0, inventoryTarget - CompanionTools.soil(c));
                    c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, c.logsRemaining);
                    c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "Добыл " + gathered + " из " + c.requestedLogs + " блоков земли.");
                } else {
                    unreachable.add(target.immutable());
                    c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "Не удалось сломать этот блок земли, ищу другой.");
                }
                c.dirtBreaking.reset();
                target = null;
                approachPath = null;
                retryDelay = 8;
                pathFailTicks = 0;
                return;
            }

            c.dirtBreaking.reset();

            if (c.getNavigation().isStuck()) {
                unreachable.add(target.immutable());
                target = null;
                approachPath = null;
                c.getNavigation().stop();
                retryDelay = 10;
                pathFailTicks = 0;
                return;
            }
            if (approachPath == null || c.getNavigation().isDone()) approachPath = findDirtApproachPath(target);
            if (approachPath == null || !c.getNavigation().moveTo(approachPath, 1.0D)) {
                if (++pathFailTicks >= 20) {
                    unreachable.add(target.immutable());
                    target = null;
                    approachPath = null;
                    c.getNavigation().stop();
                    retryDelay = 10;
                    pathFailTicks = 0;
                } else approachPath = null;
            } else pathFailTicks = 0;
        }

        private BlockPos findNearestDirt() {
            BlockPos origin = c.blockPosition();
            BlockPos best = null;
            double bestScore = Double.MAX_VALUE;
            MutableBlockPos cursor = new MutableBlockPos();
            for (int x = origin.getX() - 12; x <= origin.getX() + 12; x++) {
                for (int z = origin.getZ() - 12; z <= origin.getZ() + 12; z++) {
                    for (int y = Math.max(origin.getY() - 1, dirtOrigin.getY() - 1); y <= origin.getY() + 5; y++) {
                        cursor.set(x, y, z);
                        if (!c.level().hasChunkAt(cursor) || !CompanionGathering.isDirtBlock(c.level().getBlockState(cursor)) || !c.leashAllows(cursor)
                                || unreachable.contains(cursor) || (x == origin.getX() && z == origin.getZ() && y <= origin.getY())) continue;
                        double score = c.distanceToSqr(x + 0.5D, y + 0.5D, z + 0.5D);
                        if (score >= bestScore) continue;
                        boolean exposed = false;
                        for (Direction direction : Direction.values()) {
                            BlockPos adjacent = cursor.relative(direction);
                            if (c.level().hasChunkAt(adjacent)
                                    && c.level().getBlockState(adjacent).getCollisionShape(c.level(), adjacent).isEmpty()) {
                                exposed = true;
                                break;
                            }
                        }
                        if (!exposed) continue;
                        if (CompanionMiningNavigation.supportsLivingEntity(c, cursor)
                                || !CompanionQuarry.safeCell((ServerLevel)c.level(), cursor)) continue;
                        if (score < bestScore) { bestScore = score; best = cursor.immutable(); }
                    }
                }
            }
            return best;
        }

        private Path findDirtApproachPath(BlockPos block) {
            // The previous adjacent-cell search could end behind a ledge with no view of the block, endlessly alternating
            // a single swing and the same walk. Use the common visible approach with a collectable physical drop.
            return CompanionMiningNavigation.pathTo(c, block, 3.5, false, true, true);
        }

        private void finish(String message) {
            c.lastTaskSucceeded = CompanionTools.soil(c) >= inventoryTarget;
            if (c.supportSoilTask) {
                c.supportSoilTask = false; c.gatheringDirt = false; c.gatheringLogs = true;
                c.requestedLogs = c.savedWoodRequested; c.logsRemaining = c.savedWoodRemaining;
                c.workStatus(c.lastTaskSucceeded ? "Земля для опоры есть, возвращаюсь к дереву." : "Доступной земли не нашёл, попробую другое дерево.");
                c.getNavigation().stop(); return;
            }
            c.gatheringDirt = false;
            c.gatheringLogs = false;
            c.logsRemaining = 0;
            c.getEntityData().set(CompanionEntity.DATA_GATHERING_LOGS, false);
            c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, 0);
            c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, message);
            target = null;
            if (c.work.isActive()) c.syncWorkState(); else { c.noteDeed(message, c.lastTaskSucceeded); c.finishFollowing(); }
        }

        @Override public void stop() { resume = c.gatheringDirt; c.dirtBreaking.reset(); c.getNavigation().stop(); target = null; }
    }
}

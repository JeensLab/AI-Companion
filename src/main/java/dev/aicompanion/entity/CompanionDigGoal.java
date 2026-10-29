package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** Digs soil or a loose block (sand, gravel) for the ordered amount; the ground comes from the gathering state. */
final class CompanionDigGoal extends Goal {
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
    private final CompanionFlint flint;
    private int targetSince, targetProgress;
    private int farUntil;
    private boolean farApproach;
    private net.minecraft.world.phys.Vec3 farMoved;
    /** Dirt is dug from within reach and sight, off its own cell, where its drop can be picked up. */
    private static final CompanionRoute.Need DIRT = new CompanionRoute.Need(3.5, false, true, true);
    private double targetBest;

    CompanionDigGoal(CompanionEntity c) {
        this.c = c;
        shovelSense = new CompanionToolSense(c, "shovel", ItemTags.SHOVELS, Blocks.DIRT.defaultBlockState());
        flint = new CompanionFlint(c);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    /** Loose blocks above a cell fall into it once it is dug: the top block of a column is the easier to take. */
    private static final double BURIED_PENALTY = 2.0;

    private CompanionGround ground() { return c.gathering.ground; }
    private boolean flintMode() { return ground() == CompanionGround.FLINT; }

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
        inventoryTarget = ground().count(c) + c.logsRemaining;
        shovelSense.reset();
        unreachable.clear();
        flint.begin();
    }

    @Override public void tick() {
        if (!c.gatheringDirt) return;
        if (c.actionAnimation() != 0) return;
        if (++elapsedTicks > (flintMode() ? CompanionFlint.timeoutTicks(c.requestedLogs) : 20 * 180)) {
            finish(flintMode() ? flint.report(c.requestedLogs, "время вышло. Попроси ещё раз.") : "Остановил добычу " + ground().genitive() + " по тайм-ауту."); return;
        }
        if (c.getOwnerPlayer() == null) { finish("Я потерял связь с тобой и остановил добычу " + ground().genitive() + "."); return; }
        c.logsRemaining = Math.max(0, inventoryTarget - ground().count(c));
        c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, c.logsRemaining);
        if (c.logsRemaining <= 0 && !c.loot.hasPending()) {
            finish("Добыча " + ground().genitive() + " завершена: " + (flintMode() ? "выпало " + flint.found() + " кремня" : "добыл " + gathered + " блоков") + ". В сумке: " + c.getStoredItemsSummary() + "."); return;
        }
        if (c.logsRemaining > 0 && c.loot.incoming(ground()) >= c.logsRemaining) { c.dirtBreaking.reset(); c.work.route.stop(); return; }
        // Soil for a support under a crown: by hand, a shovel would eat the wood just cut (marathon 23.10: «добудь дерева» ended
        // with fewer logs than asked, one gone into a shovel for six blocks of dirt).
        if (!c.supportSoilTask && shovelSense.tick()) { c.dirtBreaking.reset(); approachPath = null; return; }
        if (flintMode()) flint.arm(); else CompanionTools.equip(c, ItemTags.SHOVELS, Blocks.DIRT.defaultBlockState());
        if (retryDelay > 0) { retryDelay--; return; }

        if (target != null && !ground().safeCell((ServerLevel)c.level(), target, c)) {
            unreachable.add(target); c.dirtBreaking.reset(); target = null;
        }
        if (target == null || !c.level().hasChunkAt(target) || !ground().matches(c.level().getBlockState(target))) {
            c.dirtBreaking.reset();
            target = flintMode() ? flintTarget() : findNearestDirt();
            approachPath = null;
            pathFailTicks = 0;
            targetSince = targetProgress = c.tickCount; targetBest = Double.MAX_VALUE; farUntil = 0;
        }
        if (target == null) {
            if (flintMode() && flintHolds()) return;
            if (++noDirtTicks >= 80) finish(flintMode() ? flint.report(c.requestedLogs, "гравия или места для него поблизости больше нет. Отведи меня к открытому участку гравия и попроси снова.")
                    : ground().isLoose()
                    ? "Нашёл только " + gathered + " из " + c.requestedLogs + " блоков " + ground().genitive()
                            + " поблизости. Отведи меня от воды к открытому участку " + ground().genitive() + " и попроси снова."
                    : "Нашёл только " + gathered + " из " + c.requestedLogs
                            + " блоков земли поблизости. Поставь меня у открытого участка земли и попроси снова.");
            return;
        }
        noDirtTicks = 0;
        c.movementIntent(target);
        c.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        double distance = c.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (!CompanionMiningNavigation.preservesAutonomousReturn(c, target)) {
            unreachable.add(target.immutable()); c.dirtBreaking.reset(); target = null; approachPath = null; c.work.route.stop(); return;
        }
        if (distance + .05 < targetBest) { targetBest = distance; targetProgress = c.tickCount; }
        if (c.tickCount - targetSince > 20 * 30 || c.tickCount - targetProgress > 160) {
            unreachable.add(target.immutable()); c.dirtBreaking.reset(); target = null; approachPath = null; c.work.route.stop(); return;
        }
        if (distance <= CompanionEntity.LOG_INTERACTION_RANGE_SQR && CompanionMiningNavigation.visible(c, c.getEyePosition(), target)
                && CompanionMiningNavigation.dropCollectable((ServerLevel)c.level(), target, c.blockPosition())) {
            c.work.route.stop();
            if (flintMode()) flint.arm(); else c.selectToolForBlock(c.level().getBlockState(target));
            if (!c.dirtBreaking.tick((ServerLevel)c.level(), target)) return;
            if (c.gathering.mineDirt(target)) {
                gathered++;
                if (flintMode()) flint.countBreak();
                c.logsRemaining = Math.max(0, inventoryTarget - ground().count(c));
                c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, c.logsRemaining);
                c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, flintMode() ? "Выпало " + flint.found() + " из " + c.requestedLogs + " кремня, разбито блоков гравия: " + gathered + "."
                        : "Добыл " + gathered + " из " + c.requestedLogs + " блоков " + ground().genitive() + ".");
            } else {
                unreachable.add(target.immutable());
                c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "Не удалось сломать этот блок " + ground().genitive() + ", ищу другой.");
            }
            c.dirtBreaking.reset();
            target = null;
            approachPath = null;
            retryDelay = 8;
            pathFailTicks = 0;
            return;
        }

        c.dirtBreaking.reset();

        // Stuck on his own way to this block, not on a walk before it (following, a hand-over).
        if (approachPath != null && c.work.route.stuck()) {
            unreachable.add(target.immutable());
            target = null;
            approachPath = null;
            c.work.route.stop();
            retryDelay = 10;
            pathFailTicks = 0;
            return;
        }
        if (approachPath == null || c.work.route.done()) approachPath = findDirtApproachPath(target);
        // The search goes on at the next tick: not a failed try.
        if (approachPath == null && searching) return;
        // A far route first leads away from the block: walking on is progress.
        if (approachPath != null && farApproach && c.tickCount < farUntil && (farMoved == null || c.position().distanceToSqr(farMoved) > .7 * .7)) {
            farMoved = c.position(); targetProgress = c.tickCount;
        }
        if (approachPath == null) {
            if (++pathFailTicks >= 20) {
                unreachable.add(target.immutable());
                target = null;
                approachPath = null;
                c.work.route.stop();
                retryDelay = 10;
                pathFailTicks = 0;
            } else approachPath = null;
        } else { c.work.route.walk(approachPath); pathFailTicks = 0; }
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
                    if (!c.level().hasChunkAt(cursor) || !ground().matches(c.level().getBlockState(cursor)) || !c.leashAllows(cursor)
                            || unreachable.contains(cursor) || (x == origin.getX() && z == origin.getZ() && y <= origin.getY())) continue;
                    double score = c.distanceToSqr(x + 0.5D, y + 0.5D, z + 0.5D)
                            + (ground().isLoose() && CompanionGround.isLooseBlock(c.level().getBlockState(cursor.above())) ? BURIED_PENALTY : 0);
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
                    if (!ground().safeCell((ServerLevel)c.level(), cursor, c)) continue;
                    if (score < bestScore) { bestScore = score; best = cursor.immutable(); }
                }
            }
        }
        return best;
    }

    /** Flint mode: gravel he put down comes first, then one from the bag goes down; natural gravel is dug only while he carries none. */
    private BlockPos flintTarget() {
        ServerLevel level = (ServerLevel)c.level();
        BlockPos placed = flint.placedTarget(level, unreachable);
        if (placed != null) return placed;
        if (flint.exhausted(c.requestedLogs) || c.loot.incoming(CompanionGround.GRAVEL) > 0) return null;
        BlockPos spot = flint.carriesGravel() ? flint.place(level) : null;
        if (spot == null) return findNearestDirt();
        unreachable.remove(spot);
        return spot;
    }

    /** Between two cells of the flint loop: the budget is spent, or the last gravel is still on its way to the bag. */
    private boolean flintHolds() {
        if (flint.exhausted(c.requestedLogs)) {
            if (!c.loot.hasPending()) finish(flint.exhaustedReport(c.requestedLogs));
            return true;
        }
        return c.loot.incoming(CompanionGround.GRAVEL) > 0;
    }

    private boolean searching;
    private Path findDirtApproachPath(BlockPos block) {
        // The previous adjacent-cell search could end behind a ledge with no view of the block, endlessly alternating
        // a single swing and the same walk. Use the common visible approach with a collectable physical drop.
        var reach = c.work.route.to(block, DIRT);
        searching = reach.busy();
        farApproach = reach.kind() == CompanionRoute.Kind.FAR;
        if (farApproach && farUntil == 0) farUntil = c.tickCount + (int)CompanionRoute.farLimit(reach.length());
        return reach.path();
    }

    private void finish(String message) {
        c.lastTaskSucceeded = ground().count(c) >= inventoryTarget;
        if (c.supportSoilTask) {
            c.supportSoilTask = false; c.gatheringDirt = false; c.gatheringLogs = true;
            c.requestedLogs = c.savedWoodRequested; c.logsRemaining = c.savedWoodRemaining;
            c.workStatus(c.lastTaskSucceeded ? "Земля для опоры есть, возвращаюсь к дереву." : "Доступной земли не нашёл, попробую другое дерево.");
            c.work.route.stop(); return;
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

    @Override public void stop() { resume = c.gatheringDirt; c.dirtBreaking.reset(); c.work.route.stop(); target = null; }
}

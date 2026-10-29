package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

final class CompanionGathering {
    final CompanionEntity c;

    /** What the digging goal gathers now; soil unless an order says otherwise (also in saves that never knew loose ground). */
    CompanionGround ground = CompanionGround.SOIL;

    CompanionGathering(CompanionEntity c) { this.c = c; }

    void startGatheringLogs(int count) {
        c.work.cancel();
        startGatheringLogsForWork(count);
    }

    void startGatheringLogsForWork(int count) {
        c.loot.beginCollection();
        c.woodTaskVersion++;
        c.temporaryPillar.requestCleanup();
        c.roaming = false;
        c.getEntityData().set(CompanionEntity.DATA_ROAMING, false);
        c.lastTaskSucceeded = false;
        c.gatheringDirt = false;
        c.following = false;
        c.getEntityData().set(CompanionEntity.DATA_FOLLOWING, false);
        c.protecting = false;
        c.getEntityData().set(CompanionEntity.DATA_PROTECTING, false);
        c.gatheringLogs = true;
        c.logsRemaining = Math.max(1, Math.min(64, count));
        c.requestedLogs = c.logsRemaining;
        c.getEntityData().set(CompanionEntity.DATA_GATHERING_LOGS, true);
        c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, c.logsRemaining);
        c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "Ищу дерево, нужно собрать " + c.requestedLogs + " брёвен.");
        c.getNavigation().stop();
    }

    void startGatheringDirtForWork(int count) { startGatheringDirtForWork(count, CompanionGround.SOIL); }

    void startGatheringDirtForWork(int count, CompanionGround ground) {
        this.ground = ground;
        c.loot.beginCollection();
        c.dirtTaskVersion++;
        c.temporaryPillar.requestCleanup();
        c.roaming = false;
        c.getEntityData().set(CompanionEntity.DATA_ROAMING, false);
        c.lastTaskSucceeded = false;
        c.gatheringLogs = false;
        c.gatheringDirt = true;
        c.following = false;
        c.protecting = false;
        c.getEntityData().set(CompanionEntity.DATA_FOLLOWING, false);
        c.getEntityData().set(CompanionEntity.DATA_PROTECTING, false);
        c.logsRemaining = Math.max(1, Math.min(64, count));
        c.requestedLogs = c.logsRemaining;
        c.getEntityData().set(CompanionEntity.DATA_GATHERING_LOGS, true);
        c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, c.logsRemaining);
        c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, ground == CompanionGround.SOIL ? "Ищу землю, нужно добыть " + c.requestedLogs + " блоков."
                : ground == CompanionGround.FLINT ? "Ищу гравий, нужно добыть " + c.requestedLogs + " кремня."
                : "Ищу " + ground.accusative() + ", нужно добыть " + c.requestedLogs + " блоков.");
        c.getNavigation().stop();
    }

    void stopGathering() {
        if (c.gatheringLogs && !c.work.isActive()) c.noteDeed("Прервал рубку дерева: брёвен было нужно ещё " + c.logsRemaining + ".", false);
        if (c.gatheringDirt && !c.work.isActive()) c.noteDeed("Прервал добычу " + ground.genitive() + ": было нужно ещё " + c.logsRemaining + ".", false);
        c.lastTaskSucceeded = false;
        c.gatheringLogs = false;
        c.gatheringDirt = false;
        c.logsRemaining = 0;
        c.requestedLogs = 0;
        c.getEntityData().set(CompanionEntity.DATA_GATHERING_LOGS, false);
        c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, 0);
        c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "Предыдущую задачу остановил. В сумке: " + c.getStoredItemsSummary() + ".");
        c.getNavigation().stop();
        c.setFollowing(true);
    }

    /** Room for one more log: an empty slot, or a stack of logs not yet full. */
    boolean hasRoomForLogs() {
        for (ItemStack stack : c.companionInventory) if (stack.isEmpty() || stack.is(ItemTags.LOGS) && stack.getCount() < stack.getMaxStackSize()) return true;
        return false;
    }

    boolean mineLog(BlockPos pos) {
        if (!(c.level() instanceof ServerLevel serverLevel)) return false;
        BlockState state = serverLevel.getBlockState(pos);
        if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) return false;
        ItemStack tool = c.getMainHandItem();
        var drops = Block.getDrops(state, serverLevel, pos, serverLevel.getBlockEntity(pos), c, tool);
        if (!canStoreDrops(drops)) return false;
        if (!serverLevel.destroyBlock(pos, false, c, 512)) return false;
        tool.getItem().mineBlock(tool, serverLevel, state, pos, c);
        c.swing(InteractionHand.MAIN_HAND);
        c.dropHarvest(pos, drops);
        if (state.is(BlockTags.LOGS)) c.observeEvent("wood", "Срубил и увидел выпавшее бревно. Добыча дерева ещё может продолжаться.");
        return true;
    }

    boolean mineDirt(BlockPos pos) {
        if (!(c.level() instanceof ServerLevel serverLevel)) return false;
        BlockState state = serverLevel.getBlockState(pos);
        if (!ground.matches(state) || !ground.safeCell(serverLevel, pos, c)) return false;
        if (!CompanionMiningNavigation.preservesAutonomousReturn(c, pos)) return false;
        if (ground != CompanionGround.FLINT) c.selectToolForBlock(state);
        ItemStack tool = c.getMainHandItem();
        var drops = Block.getDrops(state, serverLevel, pos, serverLevel.getBlockEntity(pos), c, tool);
        if (!canStoreDrops(drops)) return false;
        if (!serverLevel.destroyBlock(pos, false, c, 512)) return false;
        tool.getItem().mineBlock(tool, serverLevel, state, pos, c);
        c.swing(InteractionHand.MAIN_HAND);
        c.dropHarvest(pos, drops);
        return true;
    }

    boolean canStoreDrops(List<ItemStack> drops) {
        var bag = c.copyBag();
        for (ItemStack offered : drops) {
            ItemStack remaining = offered.copy();
            for (ItemStack stored : bag) if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, remaining)) {
                int moved = Math.min(remaining.getCount(), stored.getMaxStackSize() - stored.getCount());
                if (moved > 0) { stored.grow(moved); remaining.shrink(moved); }
            }
            for (int slot = 0; slot < bag.size() && !remaining.isEmpty(); slot++) if (bag.get(slot).isEmpty()) {
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                bag.set(slot, remaining.copyWithCount(moved)); remaining.shrink(moved);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    static boolean isDirtBlock(BlockState state) {
        return CompanionQuarry.soil(state); // one definition of natural soil for digging
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionParkLink;
import dev.aicompanion.client.CompanionTaskQueue;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Fixtures shared by the multitask GameTest classes: one companion beside a furnace, the park link and the readers of the batches. */
final class CompanionMultitaskSupport {
    private CompanionMultitaskSupport() { }

    static final List<CompanionParkLink> OPEN = new ArrayList<>();

    record Rig(CompanionEntity companion, BlockPos furnace, CompanionParkLink link, CompletableFuture<String> smelt) { }

    /** Four raw beef in one furnace: long enough to park. */
    static Rig smeltRig(GameTestHelper h, String trace) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        CompanionParkLink link = link(h, companion);
        return new Rig(companion, furnace, link, link.submit(List.of(new PlannedAction("smelt_items", 0, 4, "minecraft:beef")), trace, () -> false));
    }

    /** The failure count of the first batch as it is written to the save; -1 when there is no batch. */
    static int failures(CompanionEntity companion) {
        CompoundTag tag = new CompoundTag();
        companion.addAdditionalSaveData(tag);
        var list = tag.getList("FurnaceBatches", 10);
        return list.isEmpty() ? -1 : list.getCompound(0).getInt("Failures");
    }

    static void walls(GameTestHelper h) {
        for (int i = 0; i < 32; i++) for (int y = 1; y <= 2; y++) {
            h.setBlock(new BlockPos(i, y, 0), Blocks.STONE); h.setBlock(new BlockPos(i, y, 31), Blocks.STONE);
            h.setBlock(new BlockPos(0, y, i), Blocks.STONE); h.setBlock(new BlockPos(31, y, i), Blocks.STONE);
        }
    }

    static CompanionFurnaceBatches.State batchState(CompanionEntity companion) {
        var batches = companion.batches().summaries();
        return batches.isEmpty() ? null : companion.batchState(batches.getFirst().id());
    }

    static CompanionFurnaceBatches.State batchState(CompanionEntity companion, String input) {
        return companion.batches().summaries().stream().filter(batch -> batch.input().equals(input)).findFirst().map(batch -> companion.batchState(batch.id())).orElse(null);
    }

    static CompanionTaskQueue.Status smeltStatus(CompanionParkLink link, String item) {
        return link.tasks().stream().filter(view -> view.steps().getFirst().item().equals(item)).map(CompanionTaskQueue.View::status).findFirst().orElse(null);
    }

    static CompanionTaskQueue.Status status(CompanionParkLink link, long id) {
        return link.tasks().stream().filter(view -> view.id() == id).map(CompanionTaskQueue.View::status).findFirst().orElse(null);
    }

    static CompanionTaskQueue.Status status(CompanionParkLink link, String firstStep) {
        return link.tasks().stream().filter(view -> view.steps().getFirst().type().equals(firstStep)).map(CompanionTaskQueue.View::status).findFirst().orElse(null);
    }

    /** A failed earlier test leaves its link behind: close those first, or they keep ticking on this server. The tests sit in separate batches, so none of them is running now. */
    static CompanionParkLink link(GameTestHelper h, CompanionEntity companion) {
        OPEN.forEach(CompanionParkLink::close);
        OPEN.clear();
        CompanionParkLink link = new CompanionParkLink();
        link.bind(h.getLevel().getServer(), companion.getOwnerId());
        OPEN.add(link);
        return link;
    }

    static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        return companion;
    }
    static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
    static Container container(GameTestHelper h, BlockPos pos) { return (Container) h.getBlockEntity(pos); }
    static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

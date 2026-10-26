package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game log 22.10 (build f21ae3e) and Tolik's screenshot: «Нужно подойти к известному верстаку. Известные места: minecraft:furnace
 * на 21, 70, -129 …» — the house was refused because a bench was remembered somewhere far away, and the list was read aloud.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBenchTests {
    /** A bench remembered 21 blocks away is too far to walk to: like the other orders, he sets down his own and builds. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void farRememberedBenchDoesNotStopTheHouse(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos far = new BlockPos(1, 1, 1);
        h.setBlock(far, Blocks.CRAFTING_TABLE);
        companion.rememberPlaced(h.absolutePos(far));
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 32));
        h.assertTrue(companion.startWork("build_house", "", 1).success(), "House work rejected");
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().startsWith("Дом готов"), companion.getLastTaskResult());
            h.assertTrue(h.getBlockState(far).is(Blocks.CRAFTING_TABLE), "The remembered bench was taken");
        });
    }

    /** A bench eight blocks away: he walks to it and makes the ladder, instead of «go to the known bench» and a list of places. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void benchAFewStepsAwayIsWalkedToForAnyRecipe(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos bench = new BlockPos(24, 1, 16);
        h.setBlock(bench, Blocks.CRAFTING_TABLE);
        companion.rememberPlaced(h.absolutePos(bench));
        companion.setItem(0, new ItemStack(Items.STICK, 7));
        var result = CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:ladder", 1);
        h.assertTrue(result.success(), "Craft order refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.LADDER) == 3 && count(companion, Items.STICK) == 0, companion.getLastTaskResult());
            int benches = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31))) if (h.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) benches++;
            h.assertTrue(benches == 1, "Another bench was set down: " + benches);
        });
    }

    private static CompanionEntity setup(GameTestHelper h) {
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

    private static ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

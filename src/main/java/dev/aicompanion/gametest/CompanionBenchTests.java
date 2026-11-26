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

    /**
     * The bench of a neighbouring test arena stands twelve blocks beyond the edge of his own: within his sight, with no way to it.
     * With an empty bag the house order ended «Помню верстак неподалёку, но пройти к нему сейчас не могу, а дерева на новый не
     * хватает» instead of cutting the tree beside him.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void benchBeyondTheEdgeOfTheArenaDoesNotStopTheHouseFromAnEmptyBag(GameTestHelper h) {
        CompanionEntity companion = CompanionHouseTests.groundedSetup(h);
        CompanionHouseTests.noTrees(h);
        CompanionHouseTests.tools(companion);
        CompanionHouseTests.playerAway(h);
        // He stands at the north edge of his arena with an oak beside him; the bench is twelve blocks past the edge, in the arena next door.
        BlockPos start = h.absolutePos(new BlockPos(16, CompanionHouseTests.GROUND + 1, 3));
        companion.teleportTo(start.getX() + .5, start.getY(), start.getZ() + .5);
        for (int y = 2; y <= 5; y++) h.setBlock(new BlockPos(20, y, 4), Blocks.OAK_LOG);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(19, 5, 3), new BlockPos(21, 6, 5))) if (h.getBlockState(pos).isAir()) h.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        h.getLevel().setBlock(h.absolutePos(new BlockPos(16, 2, -9)), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        h.assertTrue(companion.startWork("build_house", "", 1).success(), "House work rejected");
        h.succeedWhen(() -> h.assertTrue(count(companion, Items.OAK_LOG) > 0, "No log cut yet: " + companion.getLastTaskResult()));
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

    /**
     * Game 15.11 (AgentTest, a pit under dark oak crowns): afloat in water with planks and logs, the pickaxe order said
     * «Сейчас подготовлюсь» and stood 90 s until the stall stop, the bench never set down nor the pickaxe made.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void afloatHeStillMakesThePickaxe(GameTestHelper h) { afloatPickaxe(h, false, 4, 40); }

    /** A bench known at the base makes his new one a travelling bench: set down from the water by a bank in reach, he takes it back afloat too. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void afloatHeTakesHisTravellingBenchBack(GameTestHelper h) { afloatPickaxe(h, true, 2, 0); }

    private static void afloatPickaxe(GameTestHelper h, boolean baseBench, int depth, int settle) {
        CompanionEntity companion = setup(h);
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 1; y <= depth; y++)
            h.setBlock(new BlockPos(x, y, z), x >= 24 && x <= 28 && z >= 24 && z <= 28 ? Blocks.WATER : Blocks.STONE);
        if (baseBench) { h.setBlock(new BlockPos(8, depth + 1, 8), Blocks.CRAFTING_TABLE); companion.rememberPlaced(h.absolutePos(new BlockPos(8, depth + 1, 8))); }
        companion.teleportTo(h.absolutePos(new BlockPos(26, depth, 26)).getX() + .5, h.absolutePos(new BlockPos(26, depth, 26)).getY(), h.absolutePos(new BlockPos(26, depth, 26)).getZ() + .5);
        owner(h, companion).teleportTo(h.absolutePos(new BlockPos(26, depth + 1, 21)).getX() + .5, h.absolutePos(new BlockPos(26, depth + 1, 21)).getY(), h.absolutePos(new BlockPos(26, depth + 1, 21)).getZ() + .5);
        companion.setItem(0, new ItemStack(Items.SPRUCE_PLANKS, 4));
        companion.setItem(1, new ItemStack(Items.SPRUCE_LOG, 5));
        // Settled afloat in deep water (40 ticks), bobbing a little under the surface as in the game, the bank is two blocks over his feet:
        // out of reach for the bench. Ordered at once in shallow water, it is in reach and the bench is set down from the water.
        Runnable order = () -> h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:wooden_pickaxe", 1).success(), "Craft order refused");
        if (settle == 0) order.run(); else h.runAfterDelay(settle, order);
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() >= settle && !companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.WOODEN_PICKAXE) == 1, companion.getLastTaskResult());
            if (baseBench) h.assertTrue(companion.getLastTaskResult().contains("забрал"), companion.getLastTaskResult());
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

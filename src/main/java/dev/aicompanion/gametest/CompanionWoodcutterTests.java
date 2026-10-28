package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Woodcutting like a player: finish the swing before picking up, replace and upgrade the axe, reach hanging crowns. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWoodcutterTests {
    /** Game log 22.10: «каменный топор + тридцать дерева + в сундук» was rejected whole because one step asked for 30 logs. */
    @GameTest(template = "empty")
    public static void bigWoodRequestIsSplitInsteadOfRejectingWholePlan(GameTestHelper h) {
        List<PlannedAction> plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"craft_item\",\"item\":\"minecraft:stone_axe\"},"
                + "{\"action\":\"gather_wood\",\"count\":30},{\"action\":\"store_items\",\"item\":\"logs\"}]"));
        h.assertTrue(plan.size() == 4 && plan.get(1).count() == 16 && plan.get(2).count() == 14
                && plan.get(1).type().equals("gather_wood") && plan.get(2).type().equals("gather_wood")
                && plan.get(0).item().equals("minecraft:stone_axe") && plan.get(3).type().equals("store_items"), "Wood request not split in order: " + plan);
        h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"gather_wood\",\"count\":64}]")).stream()
                .mapToInt(PlannedAction::count).sum() == 64, "Split changed the requested amount");
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"gather_wood\",\"count\":65}]")); } catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected, "Over-limit amount accepted");
        rejected = false;
        StringBuilder long_ = new StringBuilder("[");
        for (int i = 0; i < 9; i++) long_.append("{\"action\":\"follow\"},");
        long_.append("{\"action\":\"gather_wood\",\"count\":64}]");
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString(long_.toString())); } catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected, "Split plan longer than the step limit accepted");
        h.succeed();
    }

    /** Game log 22.10: a fresh drop's pickup pulled Oleg off the next log mid-swing, so its cracks started over. */
    @GameTest(template = "tall_tree", timeoutTicks = 700)
    public static void pickupWaitsUntilTheCurrentLogIsBroken(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(18, y, 16), Blocks.OAK_LOG);
        companion.startGatheringLogs(3);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(count(companion, Items.OAK_LOG) == 3 && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(companion.abandonedWoodSwings() == 0, "Swings thrown away mid-block: " + companion.abandonedWoodSwings());
        });
    }

    /** Game log 22.10: the axe broke far from the remembered bench and Oleg kept chopping by hand. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void brokenAxeIsReplacedWithStoneAxeAwayFromRememberedBench(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(2, 1, 2), Blocks.CRAFTING_TABLE); // remembered, but more than a short walk away
        ItemStack axe = new ItemStack(Items.WOODEN_AXE); axe.setDamageValue(axe.getMaxDamage() - 1);
        companion.setItemSlot(EquipmentSlot.MAINHAND, axe);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 3)); companion.setItem(1, new ItemStack(Items.OAK_PLANKS, 8));
        for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(18, y, 16), Blocks.OAK_LOG);
        companion.startGatheringLogs(3);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && trunkLeft(h, 18, 16) == 0, companion.getLastTaskResult());
            ItemStack stone = companion.getMainHandItem();
            h.assertTrue(stone.is(Items.STONE_AXE) && stone.getDamageValue() > 0, "No real stone axe used after the break: " + companion.getStoredItemsSummary());
            h.assertTrue(count(companion, Items.WOODEN_AXE) == 0 && count(companion, Items.COBBLESTONE) == 0, "Tool or stone appeared for free: " + companion.getStoredItemsSummary());
            h.assertTrue(blocks(h, Blocks.CRAFTING_TABLE) == 2 && h.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.CRAFTING_TABLE), "Expected a new bench from own wood next to the work");
        });
    }

    /** Game log 27.10: past the requested count Oleg chopped the rest of a 32-log dark oak by hand, though spare logs make a bench and an axe. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void axeIsMadeForTheRestOfTheTreeAfterTheTarget(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.IRON_INGOT, 3)); // the tree repays wood, not iron
        for (int x = 18; x <= 19; x++) for (int y = 1; y <= 3; y++) for (int z = 16; z <= 17; z++) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        companion.startGatheringLogs(1);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && trunkLeft(h, 18, 16) + trunkLeft(h, 19, 16) + trunkLeft(h, 18, 17) + trunkLeft(h, 19, 17) == 0, companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 3, "Iron spent on an axe nobody ordered: " + companion.getStoredItemsSummary());
            h.assertTrue(companion.getMainHandItem().is(Items.WOODEN_AXE) && companion.getMainHandItem().getDamageValue() > 0, "Rest of the tree chopped by hand: " + companion.getStoredItemsSummary());
            // Bench and axe come from 3 of the 12 logs: 12 planks less 4 for the bench, 3 for the head and 2 for 4 sticks.
            h.assertTrue(count(companion, Items.OAK_LOG) == 9 && count(companion, Items.OAK_PLANKS) == 3 && count(companion, Items.STICK) == 2
                    && blocks(h, Blocks.CRAFTING_TABLE) == 1, "Axe not paid with real wood: " + companion.getStoredItemsSummary());
        });
    }

    /** Three spare logs but two left on the tree: an axe would cost more than it saves, so the spare logs stay the player's. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void noAxeIsMadeForTheLastLogsOfATree(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        for (int x = 18; x <= 19; x++) for (int y = 1; y <= 2; y++) for (int z = 16; z <= 17; z++) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        companion.startGatheringLogs(3);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) == 8 && count(companion, Items.WOODEN_AXE) == 0
                    && blocks(h, Blocks.CRAFTING_TABLE) == 0, companion.getLastTaskResult() + " Bag=" + companion.getStoredItemsSummary());
        });
    }

    /** Logs in the bag before the order are not spare: counted from the order alone, two of them would buy an axe for the last logs. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void carriedLogsAreNotSpentOnAnAxeAfterTheTarget(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 2)); // 8 planks: short of a bench and an axe while the log is owed
        for (int x = 18; x <= 19; x++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, 16), Blocks.OAK_LOG);
        companion.startGatheringLogs(1);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) == 8 && count(companion, Items.WOODEN_AXE) == 0
                    && blocks(h, Blocks.CRAFTING_TABLE) == 0, companion.getLastTaskResult() + " Bag=" + companion.getStoredItemsSummary());
        });
    }

    /** Stone and a bench a few steps away: Oleg walks over and makes the better axe before chopping on. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void woodenAxeIsUpgradedAtNearbyWorkbench(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos bench = new BlockPos(10, 1, 16); h.setBlock(bench, Blocks.CRAFTING_TABLE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 3)); companion.setItem(1, new ItemStack(Items.OAK_PLANKS, 8));
        for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(21, y, 16), Blocks.OAK_LOG);
        boolean[] visited = {false};
        h.onEachTick(() -> visited[0] |= companion.position().distanceToSqr(h.absoluteVec(bench.getCenter())) < 4.2 * 4.2);
        companion.startGatheringLogs(3);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && trunkLeft(h, 21, 16) == 0, companion.getLastTaskResult());
            h.assertTrue(visited[0] && blocks(h, Blocks.CRAFTING_TABLE) == 1, "Did not walk to the existing bench or placed another one");
            h.assertTrue(companion.getMainHandItem().is(Items.STONE_AXE) && companion.getMainHandItem().getDamageValue() > 0, "Stone axe not made or not used");
            h.assertTrue(count(companion, Items.WOODEN_AXE) == 1 && count(companion, Items.COBBLESTONE) == 0, "Old axe lost or stone not spent: " + companion.getStoredItemsSummary());
        });
    }

    /** Iron and diamond upgrades are for the pickaxe only; a player does not forge an iron axe for chopping. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void spareIngotsAreNotMadeIntoAnAxeWhileChopping(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos bench = new BlockPos(10, 1, 16); h.setBlock(bench, Blocks.CRAFTING_TABLE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        companion.setItem(0, new ItemStack(Items.IRON_INGOT, 4)); companion.setItem(1, new ItemStack(Items.STICK, 4));
        for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(21, y, 16), Blocks.OAK_LOG);
        companion.startGatheringLogs(3);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && trunkLeft(h, 21, 16) == 0, companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.IRON_AXE) == 0 && count(companion, Items.IRON_INGOT) == 4 && count(companion, Items.STONE_AXE) == 1,
                    "Iron axe made on its own: " + companion.getStoredItemsSummary());
        });
    }

    /** The trunk is gone and the crown hangs: the only climb is right under it, which used to be excluded. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void hangingCrownIsReachedFromDirectlyBelow(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        companion.setItem(0, new ItemStack(Items.DIRT, 6));
        // A stone ceiling with one hole: every side column is blocked, only the cell under the logs stays open.
        for (int x = 16; x <= 20; x++) for (int z = 14; z <= 18; z++) if (x != 18 || z != 16) h.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
        h.setBlock(new BlockPos(18, 5, 16), Blocks.OAK_LOG); h.setBlock(new BlockPos(18, 6, 16), Blocks.OAK_LOG);
        boolean[] climbed = {false};
        h.onEachTick(() -> climbed[0] |= companion.getTemporarySupportCount() > 0);
        companion.startGatheringLogs(2);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(h.getBlockState(new BlockPos(18, 5, 16)).isAir() && h.getBlockState(new BlockPos(18, 6, 16)).isAir(), "Hanging logs left: " + companion.getLastTaskResult());
            h.assertTrue(climbed[0] && count(companion, Items.OAK_LOG) == 2, "No real climb or logs lost: " + companion.getStoredItemsSummary());
            h.assertTrue(companion.getTemporarySupportCount() == 0 && count(companion, Items.DIRT) == 6, "Supports not recovered: " + companion.getStoredItemsSummary());
        });
    }

    /**
     * Game log 22.10: Oleg stood 2.5 blocks from his own bench and still reported "cannot reach the bench".
     * Usability looked only at the bench centre. A player uses a bench whose top is visible over a block in front of it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void benchIsUsableWhenOnlyItsTopIsVisible(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        h.setBlock(new BlockPos(16, 1, 16), Blocks.STONE); // a block in front hides the bench's side and centre
        BlockPos bench = new BlockPos(17, 1, 16); h.setBlock(bench, Blocks.CRAFTING_TABLE);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), new net.minecraft.world.phys.Vec3(15.5, 1, 16.5));
        BlockPos absoluteBench = h.absolutePos(bench);
        h.runAfterDelay(20, () -> {
            h.assertTrue(companion.onGround(), "Fixture: companion is not standing");
            int blockedCentre = 0;
            for (double fx = .35; fx <= .651; fx += .05) for (double fz = .35; fz <= .651; fz += .05) {
                var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(15 + fx, 1, 16 + fz));
                companion.setPos(at.x, at.y, at.z);
                var hit = h.getLevel().clip(new net.minecraft.world.level.ClipContext(companion.getEyePosition(), absoluteBench.getCenter(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, companion));
                if (!hit.getBlockPos().equals(absoluteBench)) blockedCentre++;
                h.assertTrue(companion.hasCraftingTableNearby(), "Bench with a visible top not usable from " + String.format("%.2f, %.2f", fx, fz));
            }
            h.assertTrue(blockedCentre > 0, "Fixture no longer hides the bench centre");
            h.succeed();
        });
    }

    /** Game log 22.10: down a slope from his bench Oleg stopped the whole order instead of setting down a new bench from his logs. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void unreachableRememberedBenchIsReplacedFromOwnWood(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos walled = new BlockPos(27, 1, 16); // farther than the "never replace" distance of 8
        for (int x = 26; x <= 28; x++) for (int y = 1; y <= 3; y++) for (int z = 15; z <= 17; z++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        h.setBlock(walled, Blocks.CRAFTING_TABLE); // remembered and close, but no way to walk to it
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 3)); companion.setItem(1, new ItemStack(Items.OAK_LOG, 4));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(owner, "craft_item", "minecraft:stone_pickaxe", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.STONE_PICKAXE) == 1, companion.getLastTaskResult());
            // The bench set down from own wood is a travelling one: used, then picked up and carried.
            h.assertTrue(blocks(h, Blocks.CRAFTING_TABLE) == 1 && h.getBlockState(walled).is(Blocks.CRAFTING_TABLE) && count(companion, Items.CRAFTING_TABLE) == 1,
                    "Expected own bench used and carried along: " + companion.getStoredItemsSummary());
            h.assertTrue(count(companion, Items.COBBLESTONE) == 0 && count(companion, Items.OAK_LOG) < 4, "Materials not really spent: " + companion.getStoredItemsSummary());
        });
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking() && !companion.isRecoveringSupports(), "Work still active: " + companion.getLastTaskResult());
    }
    private static int trunkLeft(GameTestHelper h, int x, int z) {
        int left = 0; for (int y = 1; y <= 8; y++) if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_LOG)) left++; return left;
    }
    private static int blocks(GameTestHelper h, Block block) {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31))) if (h.getBlockState(pos).is(block)) result++;
        return result;
    }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

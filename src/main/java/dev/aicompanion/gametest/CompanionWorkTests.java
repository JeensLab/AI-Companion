package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionIdentity;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWorkTests {
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void makesWoodenPickaxeThenMinesWithRealDurability(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 4));
        vein(helper);
        helper.assertTrue(companion.startWork("gather_stone", "", 3).success(), "Stone work was not accepted");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(companion, Items.COBBLESTONE) == 3, "Wrong amount of real cobblestone");
            helper.assertTrue(companion.getMainHandItem().is(Items.WOODEN_PICKAXE) && companion.getMainHandItem().getDamageValue() == 3, "Mining did not use a real wooden pickaxe");
            helper.assertTrue(count(companion, Items.OAK_LOG) < 4 && countTables(helper) == 1, "Ingredients or crafting station were not used");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void stonePickaxeRequestGathersWoodAndStoneItself(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        tree(helper);
        vein(helper);
        ServerPlayer owner = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        helper.assertTrue(CompanionEvents.executeAction(owner, "craft_item", "minecraft:stone_pickaxe", 1).success(), "Automatic crafting not started");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(companion.getMainHandItem().is(Items.STONE_PICKAXE), "Crafted stone pickaxe was not equipped");
            helper.assertTrue(count(companion, Items.WOODEN_PICKAXE) == 1, "Preparation pickaxe was lost");
            helper.assertTrue(countTables(helper) == 1, "Did not reuse the workbench after mining");
            helper.assertTrue(helper.getBlockState(new BlockPos(18, 1, 18)).isAir(), "Wood was not gathered from the world");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void brokenPickaxeIsReplacedFromRealMaterials(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 4));
        ItemStack pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        companion.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
        vein(helper);
        companion.startWork("gather_stone", "", 4);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) == 4, companion.getLastTaskResult());
            helper.assertTrue(companion.getMainHandItem().is(Items.WOODEN_PICKAXE) && companion.getMainHandItem().getDamageValue() == 3, "Broken pickaxe was not replaced correctly");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void stopInterruptsMiningAndDoesNotGenerateMoreDrops(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        vein(helper);
        long[] stopped = {-1};
        int[] countAtStop = {0};
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && companion.getLastTaskResult().startsWith("Добываю камень")) {
                companion.stopGathering(); countAtStop[0] = count(companion, Items.COBBLESTONE); stopped[0] = helper.getTick();
            }
            if (stopped[0] >= 0) helper.assertTrue(count(companion, Items.COBBLESTONE) == countAtStop[0], "Mining continued after stop");
        });
        companion.startWork("gather_stone", "", 5);
        helper.succeedWhen(() -> {
            helper.assertTrue(stopped[0] >= 0 && helper.getTick() - stopped[0] > 40, "Wait for cancellation");
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed(), "Cancelled mining claimed success");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 700)
    public static void noWoodDoesNotCreateFreeToolsOrMineWithHands(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        vein(helper);
        companion.startWork("gather_stone", "", 3);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed(), "Missing materials claimed success");
            helper.assertTrue(count(companion, Items.WOODEN_PICKAXE) == 0 && count(companion, Items.COBBLESTONE) == 0, "Tool or stone appeared for free");
            helper.assertTrue(helper.getBlockState(new BlockPos(20, 1, 16)).is(Blocks.STONE), "Stone mined without a pickaxe");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 700)
    public static void depositsIntoExistingChestWithoutOverwritingAndKeepsSupplies(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container chest = chest(helper, new BlockPos(24, 1, 16));
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 60));
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        companion.setItem(1, new ItemStack(Items.OAK_LOG, 5));
        companion.setItem(2, new ItemStack(Items.DIRT, 12));
        companion.setItem(3, new ItemStack(Items.WOODEN_AXE));
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        double initialX = companion.getX();
        companion.startWork("store_items", "resources", 1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(chest, Items.COBBLESTONE) == 70 && count(chest, Items.OAK_LOG) == 5 && count(chest, Items.DIRT) == 4, "Chest transfer replaced or lost existing items");
            helper.assertTrue(count(companion, Items.DIRT) == 8 && count(companion, Items.WOODEN_AXE) == 1 && companion.getMainHandItem().is(Items.WOODEN_SWORD), "Useful supplies or equipment were unloaded");
            helper.assertTrue(companion.getX() > initialX + 3, "Items were transferred without walking to the chest");
            helper.assertTrue(!companion.getStorageForAi().equals("пока не использовал"), "Chest location is not synchronized");
            CompoundTag saved = new CompoundTag();
            companion.addAdditionalSaveData(saved);
            CompanionEntity restored = AICompanionMod.COMPANION.get().create(helper.getLevel());
            restored.readAdditionalSaveData(saved);
            helper.assertTrue(restored.getStorageForAi().equals(companion.getStorageForAi()), "Chest memory lost after reload");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void craftsAndPlacesChestFromOwnWoodBeforeDepositing(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 3));
        companion.setItem(1, new ItemStack(Items.COBBLESTONE, 10));
        companion.startWork("store_items", "resources", 1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(countChests(helper) == 1 && countTables(helper) == 1, "Chest and workbench not crafted/placed");
            helper.assertTrue(totalChestItems(helper, Items.COBBLESTONE) == 10 && count(companion, Items.COBBLESTONE) == 0, "Items not deposited in the crafted chest");
            helper.assertTrue(count(companion, Items.OAK_LOG) == 0, "Chest did not consume its wood");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void fullChestIsPreservedAndAnotherCarriedChestIsUsed(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container full = chest(helper, new BlockPos(20, 1, 20));
        for (int slot = 0; slot < full.getContainerSize(); slot++) full.setItem(slot, new ItemStack(Items.DIAMOND, 64));
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        companion.setItem(1, new ItemStack(Items.CHEST));
        companion.startWork("store_items", "resources", 1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(full, Items.DIAMOND) == 27 * 64 && countChests(helper) == 2, "Full chest was overwritten or another chest was not placed");
            helper.assertTrue(totalChestItems(helper, Items.COBBLESTONE) == 10, "Items disappeared instead of reaching another chest");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void placeChestWithEmptyBagGathersItsOwnWood(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        tree(helper);
        // Tool + table + chest use five logs; provide a second low tree on the bedrock fixture.
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(23, y, 18), Blocks.OAK_LOG);
        companion.startWork("place_chest", "minecraft:chest", 1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && countChests(helper) == 1, companion.getLastTaskResult());
            helper.assertTrue(helper.getBlockState(new BlockPos(18, 1, 18)).isAir(), "Chest did not gather its wood");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 700)
    public static void usesFreeHalfOfDoubleChest(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        BlockPos left = new BlockPos(23, 1, 16), right = left.east();
        helper.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        Container doubleChest = ChestBlock.getContainer((ChestBlock)Blocks.CHEST, helper.getBlockState(left), helper.getLevel(), helper.absolutePos(left), false);
        helper.assertTrue(doubleChest != null && doubleChest.getContainerSize() == 54, "Fixture double chest was not joined");
        for (int slot = 0; slot < 27; slot++) doubleChest.setItem(slot, new ItemStack(Items.DIAMOND, 64));
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        companion.startWork("store_items", "resources", 1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && count(doubleChest, Items.COBBLESTONE) == 10, companion.getLastTaskResult());
            helper.assertTrue(count(doubleChest, Items.DIAMOND) == 27 * 64 && countChests(helper) == 2, "Double chest contents or size changed");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void partialTransferKeepsRemainderWhenNoWoodForAnotherChest(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container destination = chest(helper, new BlockPos(21, 1, 16));
        for (int slot = 0; slot < 27; slot++) destination.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        destination.setItem(0, new ItemStack(Items.COBBLESTONE, 63));
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        companion.startWork("store_items", "stone", 1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed(), "Partial storage claimed complete success");
            helper.assertTrue(count(destination, Items.COBBLESTONE) == 27 * 64 && count(companion, Items.COBBLESTONE) == 9, "Partial transfer lost or duplicated items");
            helper.assertTrue(countChests(helper) == 1 && companion.getLastTaskResult().contains("Сложил 1"), "Free chest or misleading result");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 700)
    public static void explicitAllUnloadsBagToolsButKeepsEquippedWeapon(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container destination = chest(helper, new BlockPos(22, 1, 16));
        companion.setItem(0, new ItemStack(Items.WOODEN_AXE));
        companion.setItem(1, new ItemStack(Items.DIRT, 4));
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        companion.startWork("store_items", "all", 1);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && count(destination, Items.WOODEN_AXE) == 1 && count(destination, Items.DIRT) == 4, companion.getLastTaskResult());
            helper.assertTrue(companion.getMainHandItem().is(Items.WOODEN_SWORD) && count(companion, Items.DIRT) == 0, "Equipped weapon was removed or explicit-all kept bag resources");
        });
    }

    @GameTest(template = "empty")
    public static void olegWorkCommandsAndProtocolAreValidated(GameTestHelper helper) {
        helper.assertTrue("Олег".equals(CompanionIdentity.DEFAULT_NAME), "Default name mismatch");
        helper.assertTrue(CompanionWorkCommands.parse("Олег, добудь 10 камня", "Олег").count() == 10, "Stone command count lost");
        helper.assertTrue("craft_item".equals(CompanionWorkCommands.parse("сделай себе каменную кирку", "Олег").type()), "Stone crafting command not parsed");
        helper.assertTrue("all".equals(CompanionWorkCommands.parse("сложи все вещи в сундук", "Олег").item()), "All-items request lost");
        helper.assertTrue(CompanionWorkCommands.parse("не добывай камень", "Олег") == null && CompanionWorkCommands.parse("добудь камень и вернись", "Олег") == null, "Negative or compound request partially executed");
        var actions = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"gather_stone\",\"count\":3},{\"action\":\"store_items\",\"item\":\"resources\"}]"));
        helper.assertTrue(actions.size() == 2, "New work protocol rejected");
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"store_items\",\"item\":\"bad filter\"}]")); } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Invalid storage filter accepted");
        helper.succeed();
    }

    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var owner = helper.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
    private static void vein(GameTestHelper helper) { for (int z = 16; z < 22; z++) helper.setBlock(new BlockPos(20, 1, z), Blocks.STONE); }
    private static void tree(GameTestHelper helper) { for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(18, y, 18), Blocks.OAK_LOG); }
    private static Container chest(GameTestHelper helper, BlockPos pos) { helper.setBlock(pos, Blocks.CHEST); return (Container)helper.getBlockEntity(pos); }
    private static int count(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(item)) count += container.getItem(slot).getCount();
        if (container instanceof CompanionEntity companion) for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
    private static int countChests(GameTestHelper helper) { return countBlocks(helper, Blocks.CHEST); }
    private static int countTables(GameTestHelper helper) { return countBlocks(helper, Blocks.CRAFTING_TABLE); }
    private static int countBlocks(GameTestHelper helper, Block block) {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31))) if (helper.getBlockState(pos).is(block)) result++;
        return result;
    }
    private static int totalChestItems(GameTestHelper helper, Item item) {
        int total = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31))) {
            if (helper.getBlockState(pos).is(Blocks.CHEST) && helper.getBlockEntity(pos) instanceof ChestBlockEntity chest) total += count(chest, item);
        }
        return total;
    }
    private static void done(GameTestHelper helper, CompanionEntity companion) {
        helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult() + " at " + companion.position());
    }
}

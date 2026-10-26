package dev.aicompanion.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Regression scenarios run in a real server with vanilla recipes and inventories. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGameTests {
    @GameTest(template = "empty")
    public static void swordFromJungleLogsBuildsWorkbench(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        companion.setItem(0, new ItemStack(Items.JUNGLE_LOG, 5));
        var result = companion.craftItemResult("minecraft:wooden_sword", 1);
        helper.assertTrue(result.success(), result.message());
        helper.assertTrue(count(companion, Items.WOODEN_SWORD) == 1, "Sword missing");
        helper.assertTrue(count(companion, Items.JUNGLE_LOG) == 3, "Wrong wood consumption");
        helper.assertTrue(count(companion, Items.STICK) == 3, "Stick batch leftovers missing");
        helper.assertTrue(tables(helper) == 1 && companion.hasCraftingTableNearby(), "One placed workbench required");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allWoodenToolsUseExistingWorkbench(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.BIRCH_LOG, 12));
        for (String tool : new String[]{"sword", "pickaxe", "axe", "shovel", "hoe"}) {
            var result = companion.craftItemResult("minecraft:wooden_" + tool, 1);
            helper.assertTrue(result.success(), tool + ": " + result.message());
        }
        for (Item tool : new Item[]{Items.WOODEN_SWORD, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE}) {
            helper.assertTrue(count(companion, tool) == 1, "Missing tool: " + tool);
        }
        helper.assertTrue(tables(helper) == 1, "Existing workbench must be reused");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedCraftPreservesMaterialsAndWorld(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        companion.setItem(0, new ItemStack(Items.JUNGLE_LOG, 1));
        var result = companion.craftItemResult("minecraft:wooden_sword", 1);
        helper.assertTrue(!result.success(), "One log cannot supply sword and workbench");
        helper.assertTrue(count(companion, Items.JUNGLE_LOG) == 1 && count(companion, Items.WOODEN_SWORD) == 0,
                "Failed craft must be atomic");
        helper.assertTrue(tables(helper) == 0, "Failed craft must not place a workbench");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void batchesAndFullBagNeverLoseItems(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        helper.assertTrue(!companion.craftItemResult("minecraft:wooden_sword", 4).success(), "Insufficient batch should fail");
        helper.assertTrue(count(companion, Items.OAK_LOG) == 2 && count(companion, Items.WOODEN_SWORD) == 0,
                "Partial batch must roll back");
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 5));
        for (int slot = 1; slot < companion.getContainerSize(); slot++) companion.setItem(slot, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(!companion.craftItemResult("minecraft:wooden_sword", 1).success(), "Full bag should fail safely");
        helper.assertTrue(count(companion, Items.OAK_LOG) == 5 && count(companion, Items.DIRT) == 26 * 64, "Full bag lost items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitChainCraftsPlacesAndKeepsLeftovers(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        companion.setItem(0, new ItemStack(Items.JUNGLE_LOG, 5));
        helper.assertTrue(companion.craftItemResult("minecraft:jungle_planks", 1).success(), "Planks failed");
        helper.assertTrue(count(companion, Items.JUNGLE_PLANKS) == 4, "Recipe yield must be four");
        helper.assertTrue(companion.craftItemResult("minecraft:crafting_table", 1).success(), "Workbench failed");
        helper.assertTrue(companion.placeBlockResult("minecraft:crafting_table").success(), "Place failed");
        helper.assertTrue(companion.craftItemResult("minecraft:wooden_pickaxe", 1).success(), "Pickaxe failed");
        helper.assertTrue(count(companion, Items.WOODEN_PICKAXE) == 1 && tables(helper) == 1, "Chain result wrong");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stopAndInventorySurviveReload(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        companion.setItem(0, new ItemStack(Items.JUNGLE_LOG, 5));
        companion.startGatheringLogs(8);
        companion.setFollowing(false);
        helper.assertTrue(!companion.isGatheringTask() && !companion.isFollowing(), "Stay must cancel mining");
        companion.setProtecting(true);
        companion.setProtecting(false);
        helper.assertTrue(companion.isFollowing(), "Guard cancellation must synchronize following");
        CompoundTag saved = new CompoundTag();
        companion.addAdditionalSaveData(saved);
        CompanionEntity restored = AICompanionMod.COMPANION.get().create(helper.getLevel());
        restored.readAdditionalSaveData(saved);
        helper.assertTrue(count(restored, Items.JUNGLE_LOG) == 5 && restored.getOwnerId().equals(companion.getOwnerId()), "Inventory or owner lost on reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modelProtocolsAndInvalidPlans(GameTestHelper helper) {
        JsonObject nativeCall = JsonParser.parseString("{\"content\":null,\"tool_calls\":[{\"function\":{\"name\":\"queue_companion_plan\",\"arguments\":\"{\\\"steps\\\":[{\\\"action\\\":\\\"craft_item\\\",\\\"item\\\":\\\"minecraft:wooden_sword\\\"}]}\"}}]}").getAsJsonObject();
        helper.assertTrue(CompanionPlanCodec.decode(nativeCall).actions().size() == 1, "Native tool with null content lost");
        JsonObject dsml = new JsonObject();
        dsml.addProperty("content", "<|DSML|calls><|DSML|invoke name=\"queue_companion_plan\"><|DSML|parameter name=\"steps\" string=\"false\">[{\"action\":\"craft_item\",\"item\":\"minecraft:crafting_table\"}]</|DSML|parameter></|DSML|invoke></|DSML|calls>");
        var decoded = CompanionPlanCodec.decode(dsml);
        helper.assertTrue(decoded.actions().getFirst().count() == 1 && !decoded.text().contains("DSML"), "DSML or default count wrong");
        JsonObject envelope = new JsonObject();
        envelope.addProperty("content", "{\"reply\":\"Сделаю\",\"steps\":[{\"action\":\"craft_item\",\"item\":\"minecraft:stick\",\"count\":4}]}");
        helper.assertTrue(CompanionPlanCodec.decode(envelope).actions().getFirst().count() == 4, "JSON fallback lost");
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"follow\"},{\"action\":\"craft_item\",\"item\":\"bad id\"}]")); }
        catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Invalid plan must not partially execute");
        rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"craft_item\",\"item\":\"minecraft:stick\",\"count\":1.5}]")); }
        catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Fractional quantities must not be silently rounded");
        rejected = false;
        try { CompanionPlanCodec.decode(new JsonObject()); } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Empty model reply must not become fake success");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void namespacedCommandArgumentsParse(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        for (String command : new String[]{"companion craft minecraft:jungle_planks 1", "companion craft minecraft:wooden_sword 1", "companion place minecraft:crafting_table"}) {
            var parsed = dispatcher.parse(command, server.createCommandSourceStack());
            helper.assertTrue(!parsed.getReader().canRead() && parsed.getContext().getLastChild().getCommand() != null, "Command rejected: " + command);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void serverAcknowledgesRealCraftAndFailure(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(companion.position());
        companion.setOwnerId(player.getUUID());
        companion.setItem(0, new ItemStack(Items.JUNGLE_LOG, 5));
        companion.setNoAi(false);
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        var result = CompanionEvents.executeAction(player, "craft_item", "minecraft:wooden_sword", 1);
        // Logging in the mock player can auto-create the canonical companion; actions merge old duplicates into it.
        companion = CompanionEvents.getOwnedCompanion(player);
        helper.assertTrue(result.success() && result.status() == dev.aicompanion.entity.CompanionActionResult.Status.ACCEPTED,
                "Crafting must return accepted before completion");
        final var actual = companion;
        helper.succeedWhen(() -> {
            helper.assertTrue(!actual.isWorking(), "Craft still running");
            helper.assertTrue(count(actual, Items.WOODEN_SWORD) + (actual.getMainHandItem().is(Items.WOODEN_SWORD) ? 1 : 0) == 1, "Server bridge did not craft sword: " + actual.getLastTaskResult());
            var failure = CompanionEvents.executeAction(player, "craft_item", "minecraft:diamond_pickaxe", 1);
            helper.assertTrue(!failure.success() && !actual.didLastTaskSucceed(), "Missing diamonds must return failure");
            helper.assertTrue(count(actual, Items.WOODEN_SWORD) + (actual.getMainHandItem().is(Items.WOODEN_SWORD) ? 1 : 0) == 1, "Failure lost existing sword");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(CompanionEntity.class,
                    actual.getBoundingBox().inflate(128), entity -> entity.isAlive() && player.getUUID().equals(entity.getOwnerId())).size() == 1,
                    "Server actions must keep only one owned companion");
        });
    }

    @GameTest(template = "empty")
    public static void blockedWorkbenchPlacementIsAtomic(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 5));
        // A shaft three deep: a player at its bottom reaches no free cell to set a bench on (two deep, he sets it on the rim).
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) for (int y = 1; y <= 3; y++) {
            if (x != 2 || z != 2) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        helper.assertTrue(!companion.craftItemResult("minecraft:wooden_sword", 1).success(), "No room for workbench should fail");
        helper.assertTrue(count(companion, Items.OAK_LOG) == 5 && count(companion, Items.WOODEN_SWORD) == 0, "Failed placement consumed crafting inputs");
        helper.assertTrue(tables(helper) == 0, "Failed placement left a workbench");
        helper.succeed();
    }

    private static CompanionEntity companion(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        CompanionEntity result = helper.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2);
        result.setNoAi(true);
        result.setOwnerId(UUID.randomUUID());
        return result;
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        return count;
    }

    private static int tables(GameTestHelper helper) {
        int count = 0;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) for (int y = 1; y <= 3; y++) {
            if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.CRAFTING_TABLE)) count++;
        }
        return count;
    }
}

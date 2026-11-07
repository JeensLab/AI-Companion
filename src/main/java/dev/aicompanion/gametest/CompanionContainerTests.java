package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Barrels, shulker boxes, furnaces, hoppers and dispensers as places to put things in and take things from.
 * Every test has a batch of its own: the tests of one batch run side by side, and a container of the next test is within his reach.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionContainerTests {
    private static final BlockPos NEAR = new BlockPos(22, 1, 16);

    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "containers_store_barrel")
    public static void storesTheAskedAmountIntoANamedBarrel(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container barrel = container(helper, NEAR, Blocks.BARREL);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        helper.assertTrue(companion.startWork("store_items", "stone", 10, "minecraft:barrel").success(), "The order for the barrel was refused");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            int inBarrel = count(barrel, Items.COBBLESTONE), inBag = count(companion, Items.COBBLESTONE);
            helper.assertTrue(inBarrel == 10 && inBag == 6, "Wrong split: " + inBarrel + "/" + inBag);
            AABB around = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "Items were dropped");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "containers_take_barrel")
    public static void takesEverythingFromANamedBarrel(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container barrel = container(helper, NEAR, Blocks.BARREL);
        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        barrel.setItem(5, new ItemStack(Items.OAK_LOG, 6));
        helper.assertTrue(companion.startWork("take_items", "all", 64, "minecraft:barrel").success(), "The order for the barrel was refused");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(barrel.isEmpty() && count(companion, Items.COBBLESTONE) == 10 && count(companion, Items.OAK_LOG) == 6,
                    "The barrel was not emptied into the bag");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "containers_take_furnace")
    public static void takesOnlyTheResultFromAFurnace(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container furnace = container(helper, NEAR, Blocks.FURNACE);
        // Sticks do not smelt: the furnace stays cold and its slots stay as set.
        furnace.setItem(0, new ItemStack(Items.STICK, 3));
        furnace.setItem(1, new ItemStack(Items.COAL, 2));
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(companion.startWork("take_items", "all", 64, "minecraft:furnace").success(), "The order for the furnace was refused");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(companion, Items.IRON_INGOT) == 1 && furnace.getItem(2).isEmpty(), "The result was not taken");
            helper.assertTrue(furnace.getItem(0).getCount() == 3 && furnace.getItem(1).getCount() == 2, "Input or fuel was taken");
            helper.assertTrue(count(companion, Items.STICK) == 0 && count(companion, Items.COAL) == 0, "Input or fuel reached the bag");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "containers_store_furnace")
    public static void neverPutsIntoTheResultSlotOfAFurnace(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container furnace = container(helper, NEAR, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        helper.assertTrue(companion.startWork("store_items", "all", 0, "minecraft:furnace").success(), "The order for the furnace was refused");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(furnace.getItem(2).isEmpty() && furnace.getItem(1).isEmpty() && furnace.getItem(0).is(Items.IRON_INGOT),
                    "The furnace slots broke their rules");
            helper.assertTrue(furnace.getItem(0).getCount() + count(companion, Items.IRON_INGOT) == 4, "Ingots were lost or doubled");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "containers_store_hopper")
    public static void fillsAHopperToItsFiveSlotsAndNoMore(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container hopper = container(helper, NEAR, Blocks.HOPPER);
        Item[] kinds = {Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG, Items.SAND, Items.GRAVEL, Items.ANDESITE};
        for (int slot = 0; slot < kinds.length; slot++) companion.setItem(slot, new ItemStack(kinds[slot], 64));
        helper.assertTrue(companion.startWork("store_items", "all", 0, "minecraft:hopper").success(), "The order for the hopper was refused");
        helper.succeedWhen(() -> {
            done(helper, companion);
            int inHopper = 0, inBag = 0;
            for (Item kind : kinds) { inHopper += count(hopper, kind); inBag += count(companion, kind); }
            helper.assertTrue(inHopper == 5 * 64 && inBag == 64, "Hopper " + inHopper + ", bag " + inBag);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "containers_kind_missing")
    public static void refusesToStoreWhenTheNamedKindIsNotThere(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container chest = container(helper, NEAR, Blocks.CHEST);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        helper.assertTrue(companion.startWork("store_items", "stone", 5, "minecraft:barrel").success(), "The order was refused at the start");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed() && chest.isEmpty() && count(companion, Items.COBBLESTONE) == 5,
                    "A chest took what a barrel was asked for");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "containers_barrel_as_storage")
    public static void anOrderForAChestStillUsesABarrelAsStorage(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container barrel = container(helper, NEAR, Blocks.BARREL);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        helper.assertTrue(companion.startWork("store_items", "stone", 1, "near_player").success(), "Store order not accepted");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(barrel, Items.COBBLESTONE) == 5 && count(companion, Items.COBBLESTONE) == 0, "The barrel did not take the stone");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "containers_shulker_as_storage")
    public static void takeWithoutAKindReadsAShulkerBox(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container shulker = container(helper, NEAR, Blocks.SHULKER_BOX);
        shulker.setItem(3, new ItemStack(Items.OAK_LOG, 4));
        helper.assertTrue(companion.startWork("take_items", "logs", 4).success(), "Take order not accepted");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(shulker.isEmpty() && count(companion, Items.OAK_LOG) == 4, "The shulker box was not emptied into the bag");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3000, batch = "containers_sorting")
    public static void sortingLeavesFurnacesAndHoppersAlone(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container a = container(helper, new BlockPos(9, 1, 13), Blocks.CHEST);
        Container b = container(helper, new BlockPos(12, 1, 13), Blocks.CHEST);
        Container furnace = container(helper, new BlockPos(14, 1, 13), Blocks.FURNACE);
        Container hopper = container(helper, new BlockPos(10, 1, 15), Blocks.HOPPER);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        a.setItem(1, new ItemStack(Items.OAK_LOG, 5));
        a.setItem(2, new ItemStack(Items.BONE, 3));
        b.setItem(0, new ItemStack(Items.BREAD, 10));
        b.setItem(1, new ItemStack(Items.COBBLESTONE, 3));
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(furnace.isEmpty() && hopper.isEmpty(), "A furnace or a hopper was used as a home chest");
            helper.assertTrue(count(a, Items.COBBLESTONE) + count(b, Items.COBBLESTONE) == 67, "Cobblestone was lost or doubled");
            helper.assertTrue(count(a, Items.OAK_LOG) + count(b, Items.OAK_LOG) == 5, "Logs were lost or doubled");
            helper.assertTrue(count(a, Items.BONE) + count(b, Items.BONE) == 3 && count(a, Items.BREAD) + count(b, Items.BREAD) == 10,
                    "Bones or bread were lost or doubled");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 100, batch = "containers_commands")
    public static void containerNamesAreUnderstoodInCommandsAndPlans(GameTestHelper helper) {
        PlannedAction store = CompanionWorkCommands.parse("Олег, сложи 10 камня в бочку", "Олег");
        helper.assertTrue(store != null && "store_items".equals(store.type()) && store.count() == 10 && "stone".equals(store.item())
                && "minecraft:barrel".equals(store.location()), "Store command: " + store);
        PlannedAction take = CompanionWorkCommands.parse("забери все из бочки", "Олег");
        helper.assertTrue(take != null && "take_items".equals(take.type()) && "all".equals(take.item())
                && "minecraft:barrel".equals(take.location()), "Take command: " + take);
        PlannedAction oven = CompanionWorkCommands.parse("забери вещи из печи", "Олег");
        helper.assertTrue(oven != null && "minecraft:furnace".equals(oven.location()), "Furnace command: " + oven);
        PlannedAction old = CompanionWorkCommands.parse("сложи камень в сундук", "Олег");
        helper.assertTrue(old != null && old.count() == 1 && "near_player".equals(old.location()), "The old chest command changed: " + old);
        PlannedAction oldTake = CompanionWorkCommands.parse("забери 5 камня из сундука", "Олег");
        helper.assertTrue(oldTake != null && oldTake.count() == 5 && "near_player".equals(oldTake.location()), "The old take command changed: " + oldTake);
        String hopperPlan = "[{\"action\":\"store_items\",\"item\":\"stone\",\"count\":4,\"location\":\"minecraft:hopper\"}]";
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(hopperPlan));
        helper.assertTrue("minecraft:hopper".equals(plan.get(0).location()), "A hopper location was not kept");
        String[] refused = {"[{\"action\":\"craft_item\",\"item\":\"minecraft:stick\",\"location\":\"minecraft:barrel\"}]",
                "[{\"action\":\"store_items\",\"item\":\"stone\",\"location\":\"minecraft:diamond_block\"}]"};
        for (String bad : refused) {
            boolean rejected = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(bad)); } catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "Accepted: " + bad);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "containers_chat_path")
    public static void theChatPathKeepsTheContainerKind(GameTestHelper helper) {
        Object[][] orders = {
                {"положи вещи в бочку", "store_items", PlannedAction.ALL_CARRIED, "minecraft:barrel"},
                {"Олег, сложи 10 камня в бочку", "store_items", 10, "minecraft:barrel"},
                {"забери всё из печи", "take_items", 64, "minecraft:furnace"},
                {"забери 5 камня из шалкера", "take_items", 5, "minecraft:shulker_box"},
                {"сложи камень в сундук", "store_items", 1, "near_player"},
                {"забери 5 камня из сундука", "take_items", 5, "near_player"}};
        for (Object[] order : orders) {
            String command = CompanionLocalCommands.route((String)order[0], "Олег");
            helper.assertTrue(command != null, "Not routed locally: " + order[0]);
            PlannedAction step = CompanionLocalCommands.action(command);
            helper.assertTrue(step.type().equals(order[1]) && step.count() == (int)order[2] && step.location().equals(order[3]),
                    order[0] + " -> " + command + " -> " + step);
        }
        PlannedAction barrelOrder = CompanionLocalCommands.action(CompanionLocalCommands.route("положи вещи в бочку", "Олег"));
        helper.assertTrue(CompanionDialogue.acknowledge(List.of(barrelOrder)).contains("в бочку")
                && CompanionDialogue.describe(barrelOrder).contains("в бочку"), "The words still say a chest: " + CompanionDialogue.describe(barrelOrder));
        PlannedAction chestOrder = CompanionLocalCommands.action(CompanionLocalCommands.route("сложи камень в сундук", "Олег"));
        helper.assertTrue(CompanionDialogue.acknowledge(List.of(chestOrder)).contains("в сундук"), "The chest wording changed");
        PlannedAction oldForm = CompanionLocalCommands.action("companion work store_items 1 resources");
        helper.assertTrue("near_player".equals(oldForm.location()) && oldForm.count() == 1 && "resources".equals(oldForm.item()), "Old form: " + oldForm);
        helper.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "containers_chat_barrel")
    public static void aSpokenOrderForABarrelReachesTheBarrel(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container barrel = container(helper, NEAR, Blocks.BARREL);
        Container chest = container(helper, new BlockPos(24, 1, 16), Blocks.CHEST);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        PlannedAction step = CompanionLocalCommands.action(CompanionLocalCommands.route("сложи 10 камня в бочку", "Олег"));
        helper.assertTrue(companion.startWork(step.type(), step.item(), step.count(), step.location()).success(), "The spoken order was refused");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(barrel, Items.COBBLESTONE) == 10 && count(companion, Items.COBBLESTONE) == 6 && chest.isEmpty(),
                    "Wrong split: barrel " + count(barrel, Items.COBBLESTONE) + ", bag " + count(companion, Items.COBBLESTONE));
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "containers_blocked_shulker")
    public static void aShulkerBoxWithABlockOnItsLidIsNotOpened(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        Container shulker = container(helper, NEAR, Blocks.SHULKER_BOX);
        helper.setBlock(NEAR.above(), Blocks.STONE);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        helper.assertTrue(companion.startWork("store_items", "stone", 5, "minecraft:shulker_box").success(), "The order was refused at the start");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed() && shulker.isEmpty() && count(companion, Items.COBBLESTONE) == 5,
                    "The closed shulker box was opened: " + companion.getLastTaskResult());
        });
    }

    private static Container container(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        return (Container)helper.getBlockEntity(pos);
    }

    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        return companion;
    }

    private static int count(Container bag, Item item) {
        int count = 0;
        for (int slot = 0; slot < bag.getContainerSize(); slot++) if (bag.getItem(slot).is(item)) count += bag.getItem(slot).getCount();
        return count;
    }

    private static void done(GameTestHelper helper, CompanionEntity companion) {
        helper.assertTrue(!companion.isGatheringTask(), companion.getLastTaskResult());
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionCraftCosts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Iron for a tool inside one order: iron ore, the furnace, the bench, then back to what was asked. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionIronTests {
    /** Raw iron in the bag: three ingots are smelted in the nearby furnace, then the pickaxe is made at the bench. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void rawIronBecomesAnIronPickaxe(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        h.setBlock(new BlockPos(14, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3)); companion.setItem(1, new ItemStack(Items.COAL, 1)); companion.setItem(2, new ItemStack(Items.STICK, 2));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:iron_pickaxe", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.IRON_PICKAXE) == 1, report(companion));
            h.assertTrue(count(companion, Items.RAW_IRON) == 0 && count(companion, Items.IRON_INGOT) == 0 && ((Container) h.getBlockEntity(furnace)).isEmpty(),
                    "Iron left over or in the furnace: " + companion.getStoredItemsSummary());
            h.assertTrue(companion.getDeedsForAi().contains("переплавил"), "Smelting step not journaled: " + companion.getDeedsForAi());
        });
    }

    /**
     * Iron ore in a wall, no furnace anywhere, a stone pickaxe, sticks, two planks and six cobblestone: the ore is mined,
     * the two missing stones for a furnace come from the wall, the planks burn, and the iron pickaxe comes out at the bench.
     * (With no cobblestone at all he digs the wall full of holes and his pillar escape eats the stone; see the report.)
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void ironOreAloneBecomesAnIronPickaxe(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        for (int x = 20; x <= 22; x++) h.setBlock(new BlockPos(x, 1, 16), Blocks.IRON_ORE);
        h.setBlock(new BlockPos(14, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.STICK, 2)); companion.setItem(1, new ItemStack(Items.OAK_PLANKS, 2));
        companion.setItem(2, new ItemStack(Items.COBBLESTONE, 6));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:iron_pickaxe", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.IRON_PICKAXE) == 1, report(companion));
            int furnaces = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(30, 4, 30))) if (h.getBlockState(pos).is(Blocks.FURNACE)) furnaces++;
            h.assertTrue(furnaces == 1, "Expected one own furnace, found " + furnaces);
            for (int x = 20; x <= 22; x++) h.assertTrue(h.getBlockState(new BlockPos(x, 1, 16)).isAir(), "Iron left in the wall at x=" + x);
        });
    }

    /**
     * Review 22.10, В8: the same order with no cobblestone at all. His escape pillars used to eat the stone dug for the furnace
     * (one run in three ended «Для печи не хватает камня»). Supports now come from soil or spare stone only.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 4800)
    public static void ironChainWithoutCobblestoneKeepsTheFurnaceStone(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        for (int x = 20; x <= 22; x++) h.setBlock(new BlockPos(x, 1, 16), Blocks.IRON_ORE);
        h.setBlock(new BlockPos(14, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.STICK, 2)); companion.setItem(1, new ItemStack(Items.OAK_PLANKS, 2));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:iron_pickaxe", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.IRON_PICKAXE) == 1, report(companion));
        });
    }

    /** «Добудь золото» with a stone pickaxe: iron first, smelted, the iron pickaxe made, then the gold is really mined. */
    @GameTest(template = "tall_tree", timeoutTicks = 3200)
    public static void goldOrderGoesThroughIronFirst(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        BlockPos gold = new BlockPos(20, 1, 19); h.setBlock(gold, Blocks.GOLD_ORE);
        for (int x = 20; x <= 22; x++) h.setBlock(new BlockPos(x, 1, 13), Blocks.IRON_ORE);
        h.setBlock(new BlockPos(14, 1, 16), Blocks.CRAFTING_TABLE); h.setBlock(new BlockPos(14, 1, 18), Blocks.FURNACE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.STICK, 2)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:gold_ore", 1).success(), "Gold order not accepted");
        long id = companion.activeWorkId();
        h.succeedWhen(() -> {
            done(h, companion);
            CompanionActionResult result = companion.workResult(id);
            h.assertTrue(result != null && result.success() && result.message().contains("прибавилось 1 «"), report(companion));
            h.assertTrue(count(companion, Items.RAW_GOLD) == 1 && count(companion, Items.IRON_PICKAXE) == 1 && h.getBlockState(gold).isAir(),
                    "No gold or no iron pickaxe: " + companion.getStoredItemsSummary());
        });
    }

    /** No iron anywhere: the iron pickaxe order ends honestly as blocked, with the reason, and nothing is spent. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void withoutIronTheToolIsRefusedHonestly(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        h.setBlock(new BlockPos(14, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.STICK, 2)); companion.setItem(1, new ItemStack(Items.IRON_INGOT, 1));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:iron_pickaxe", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("3 железных слитков"), report(companion));
            h.assertTrue(count(companion, Items.IRON_INGOT) == 1 && count(companion, Items.STICK) == 2 && count(companion, Items.STONE_PICKAXE) == 1,
                    "Something spent: " + companion.getStoredItemsSummary());
        });
    }

    /** «Стоп» while the iron cooks: nothing is lost, and the answer says the iron is in the furnace. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void stopWhileIronCooksLosesNothing(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        BlockPos furnace = new BlockPos(19, 1, 16); h.setBlock(furnace, Blocks.FURNACE);
        h.setBlock(new BlockPos(14, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3)); companion.setItem(1, new ItemStack(Items.COAL, 1)); companion.setItem(2, new ItemStack(Items.STICK, 2));
        var owner = owner(h, companion);
        h.assertTrue(CompanionEvents.executeAction(owner, "craft_item", "minecraft:iron_pickaxe", 1).success(), "Craft order not accepted");
        long id = companion.activeWorkId();
        boolean[] stopped = {false};
        h.onEachTick(() -> {
            if (!stopped[0] && ((Container) h.getBlockEntity(furnace)).getItem(0).is(Items.RAW_IRON)) {
                stopped[0] = true;
                h.assertTrue(CompanionEvents.executeAction(owner, "stop", "", 1).success(), "Stop not accepted");
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0] && !companion.isWorking(), "Not stopped yet");
            Container oven = (Container) h.getBlockEntity(furnace);
            int iron = count(companion, Items.RAW_IRON) + count(companion, Items.IRON_INGOT) + oven.getItem(0).getCount() + oven.getItem(2).getCount();
            h.assertTrue(iron == 3, "Iron lost or doubled: " + iron);
            CompanionActionResult result = companion.workResult(id);
            h.assertTrue(result != null && result.status() == CompanionActionResult.Status.CANCELLED && result.message().contains("печи"), "Result: " + result);
        });
    }

    /** Which iron tools the chain accepts, their ingot cost, and the quick command. */
    @GameTest(template = "tall_tree")
    public static void ironToolProtocol(GameTestHelper h) {
        h.assertTrue(CompanionCraftCosts.supportsCraft("minecraft:iron_pickaxe", 1) && CompanionCraftCosts.supportsCraft("minecraft:iron_sword", 8)
                && !CompanionCraftCosts.supportsCraft("minecraft:iron_pickaxe", 6) && !CompanionCraftCosts.supportsCraft("minecraft:iron_chestplate", 1), "Supported iron tools");
        h.assertTrue(CompanionCraftCosts.ingotCost("minecraft:iron_pickaxe") == 3 && CompanionCraftCosts.ingotCost("minecraft:iron_shovel") == 1, "Ingot cost");
        PlannedAction sword = CompanionWorkCommands.parse("Олег, сделай железный меч", "Олег");
        h.assertTrue(sword != null && sword.type().equals("craft_item") && sword.item().equals("minecraft:iron_sword"), "Quick command: " + sword);
        PlannedAction pickaxe = CompanionWorkCommands.parse("скрафти железную кирку", "Олег");
        h.assertTrue(pickaxe != null && pickaxe.item().equals("minecraft:iron_pickaxe"), "Quick command: " + pickaxe);
        h.succeed();
    }

    private static CompanionEntity flat(GameTestHelper h) {
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
    /** A stone wall three high across the arena at x=20..23, facing Oleg. */
    private static void wall(GameTestHelper h) {
        for (int x = 20; x <= 23; x++) for (int z = 10; z <= 22; z++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }
    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
    private static String report(CompanionEntity companion) { return companion.getDeedsForAi() + " | bag: " + companion.getStoredItemsSummary(); }
    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
    }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

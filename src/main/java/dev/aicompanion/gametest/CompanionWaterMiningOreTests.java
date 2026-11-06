package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.support.WaterMiningKit.*;

import java.util.List;

/** Mining ore and stone beside water: what is picked up, what is refused and which drops count. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWaterMiningOreTests {
    private CompanionWaterMiningOreTests() { }

    /** Iron with a water source right behind it in the wall and a plug in the bag: the spring is plugged as the ore goes, the raw iron is in his bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void oreBesideWaterIsMinedAndPickedUp(GameTestHelper h) {
        CompanionEntity c = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.IRON_ORE);
        h.setBlock(new BlockPos(21, 1, 16), Blocks.WATER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c));
            h.assertTrue(!h.getBlockState(ore).is(Blocks.IRON_ORE), "Ore still there");
            h.assertTrue(c.isAlive() && c.getHealth() >= c.getMaxHealth(), "Hurt: " + c.getHealth());
        });
    }

    /**
     * A stone island, two high, in a pool one block deep: the top layer is dry, the lower one is washed by the water, and what he
     * mines from it floats. All eight stones are mined and picked up, those that lay in the water too.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void stoneAtTheWaterIsMinedAndPickedUp(GameTestHelper h) {
        CompanionEntity c = flat(h);
        box(h, 13, 1, 13, 23, 1, 21, Blocks.STONE_BRICKS);
        box(h, 14, 1, 14, 22, 1, 20, Blocks.WATER);
        box(h, 18, 1, 16, 19, 2, 17, Blocks.STONE);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("gather_stone", "", 8).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.COBBLESTONE) >= 8, report(c) + " " + c.getLastTaskResult());
            h.assertTrue(c.isAlive() && c.getHealth() >= c.getMaxHealth(), "Hurt: " + c.getHealth());
        });
    }

    /** Lava beside the ore and no plug in the bag: refused; the reason names lava only. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void lavaBesideTheOreIsStillRefusedAndTheReasonIsLavaOnly(GameTestHelper h) {
        CompanionEntity c = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.IRON_ORE);
        h.setBlock(new BlockPos(21, 1, 16), Blocks.LAVA);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            String said = c.getLastTaskResult();
            h.assertTrue(!c.didLastTaskSucceed() && said.contains("рядом лава") && !said.contains("вода"), said);
            h.assertTrue(h.getBlockState(ore).is(Blocks.IRON_ORE), "Ore dug");
        });
    }

    /** Review 4: a Silk Touch pickaxe brings the ore block itself: that is a mined ore, not «выпавшее не достал». */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void anOreTakenWithSilkTouchIsDone(GameTestHelper h) {
        CompanionEntity c = flat(h);
        BlockPos ore = new BlockPos(20, 1, 16);
        h.setBlock(ore, Blocks.IRON_ORE);
        c.setFollowing(false);
        ItemStack pick = new ItemStack(Items.STONE_PICKAXE);
        pick.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        c.setItemSlot(EquipmentSlot.MAINHAND, pick);
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.IRON_ORE) == 1 && !c.getLastTaskResult().contains("Выпавшее не достал"), report(c) + " " + c.getLastTaskResult());
        });
    }

    /** Review 4: Nether gold ore (in the gold ore tag) drops nuggets, not raw gold: the nuggets in the bag are the ore mined. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void aNetherGoldOreGivingNuggetsIsDone(GameTestHelper h) {
        CompanionEntity c = flat(h);
        BlockPos ore = new BlockPos(20, 1, 16);
        h.setBlock(ore, Blocks.NETHER_GOLD_ORE);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:gold_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.GOLD_NUGGET) > 0 && !c.getLastTaskResult().contains("Выпавшее не достал"), report(c) + " " + c.getLastTaskResult());
        });
    }

    /**
     * Review 5, finding 2: a Silk Touch pickaxe in the iron preparation brings iron ore blocks, no raw iron. That is no iron for the
     * pickaxe the diamonds need: an honest refusal, not «Готово» with no diamond.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void silkTouchIronOreBlocksAreNoIronForThePickaxeOfTheDiamonds(GameTestHelper h) {
        CompanionEntity c = flat(h);
        box(h, 20, 1, 10, 23, 3, 22, Blocks.STONE);
        for (int z : new int[]{11, 13, 19, 21}) h.setBlock(new BlockPos(20, 1, z), Blocks.IRON_ORE);
        BlockPos diamond = new BlockPos(20, 1, 16); h.setBlock(diamond, Blocks.DIAMOND_ORE);
        c.setFollowing(false);
        ItemStack pick = new ItemStack(Items.STONE_PICKAXE);
        pick.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        c.setItemSlot(EquipmentSlot.MAINHAND, pick);
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:diamond_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(!c.didLastTaskSucceed() && c.getLastTaskResult().contains("железа я не набрал") && count(c, Items.DIAMOND) == 0, "False success: " + report(c) + " " + c.getLastTaskResult());
            // Review 6: the ore blocks are in his bag, so «не достал» would be false; the reason is the enchantment.
            String said = CompanionDialogue.summarize(List.of(new PlannedAction("mine_ore", 0, 1, "minecraft:diamond_ore")), "План остановлен: " + c.getLastTaskResult());
            h.assertTrue(c.getLastTaskResult().contains("Шёлков") && !c.getLastTaskResult().contains("не достал") && !said.contains("не достал"), "Not the Silk Touch reason: " + c.getLastTaskResult() + " | " + said);
        });
    }
}

package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionObtain;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * the player 23.10: one general «добыть» instead of code for every item («не поставь факел, а просто функции — поставить, добыть…»).
 * obtain_item plans the item down to the skills Oleg has, from the game's recipes and drops, and succeeds only by the bag.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionObtainTests {
    private static final int GROUND = 5;

    @GameTest(template = "empty")
    public static void chainsArePlannedFromTheGameData(GameTestHelper h) {
        var torch = CompanionObtain.plan(h.getLevel(), List.of(), false, "minecraft:torch", 4);
        h.assertTrue(!torch.refused() && new java.util.HashSet<>(types(torch)).equals(java.util.Set.of("gather_wood", "mine_ore", "craft_item"))
                && torch.steps().stream().anyMatch(step -> step.item().equals("minecraft:coal_ore")) && torch.steps().getLast().count() == 4
                && torch.steps().getLast().type().equals("craft_item"), "Torch: " + torch);
        var ingots = CompanionObtain.plan(h.getLevel(), List.of(), false, "minecraft:iron_ingot", 3);
        h.assertTrue(types(ingots).equals(List.of("mine_ore", "smelt_items")) && ingots.steps().get(0).count() == 3
                && ingots.steps().get(1).item().equals("minecraft:iron_ingot"), "Ingots: " + ingots);
        var leather = CompanionObtain.plan(h.getLevel(), List.of(), false, "minecraft:leather", 2);
        h.assertTrue(types(leather).equals(List.of("hunt_animals")) && leather.steps().get(0).item().equals("minecraft:cow"), "Leather: " + leather);
        var carried = CompanionObtain.plan(h.getLevel(), List.of(new ItemStack(Items.COAL, 2), new ItemStack(Items.STICK, 2)), false, "minecraft:torch", 8);
        h.assertTrue(types(carried).equals(List.of("craft_item")), "Coal and sticks in the bag, yet: " + carried);
        var had = CompanionObtain.plan(h.getLevel(), List.of(new ItemStack(Items.TORCH, 5)), false, "minecraft:torch", 3);
        h.assertTrue(!had.refused() && had.steps().isEmpty(), "Already carried: " + had);
        var pearl = CompanionObtain.plan(h.getLevel(), List.of(), false, "minecraft:ender_pearl", 1);
        h.assertTrue(pearl.refused() && pearl.steps().isEmpty(), "Ender pearl: " + pearl);
        var glass = CompanionObtain.plan(h.getLevel(), List.of(), false, "minecraft:glass", 1);
        h.assertTrue(glass.refused(), "Glass needs sand, which he cannot dig yet: " + glass);
        h.succeed();
    }

    /** Torches from an empty bag: wood, a pickaxe for the coal, the coal, sticks, the torches — all real. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void torchesFromNothing(GameTestHelper h) {
        ground(h);
        for (int y = GROUND + 1; y <= GROUND + 5; y++) { h.setBlock(new BlockPos(20, y, 12), Blocks.OAK_LOG); h.setBlock(new BlockPos(20, y, 20), Blocks.OAK_LOG); }
        h.setBlock(new BlockPos(10, GROUND + 1, 20), Blocks.COAL_ORE); h.setBlock(new BlockPos(11, GROUND + 1, 20), Blocks.COAL_ORE);
        CompanionEntity companion = spawn(h);
        var future = run(h, companion, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:torch\",\"count\":4}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            var outcome = future.join();
            h.assertTrue(count(companion, Items.TORCH) >= 4 && outcome.skipped().isEmpty() && outcome.report().contains("Готово"), outcome.report());
        });
    }

    /** Wool: sheep are hunted for it. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void woolFromSheep(GameTestHelper h) {
        ground(h);
        for (int i = 0; i < 4; i++) h.spawn(EntityType.SHEEP, 20 + i * 2, GROUND + 1, 12 + i * 3);
        CompanionEntity companion = spawn(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        var future = run(h, companion, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:white_wool\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.WHITE_WOOL) >= 2, future.join().report());
        });
    }

    /** Iron ingots: the ore mined, the raw iron smelted in the furnace nearby. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void ironIngotsFromOre(GameTestHelper h) {
        ground(h);
        h.setBlock(new BlockPos(20, GROUND + 1, 16), Blocks.IRON_ORE); h.setBlock(new BlockPos(20, GROUND + 1, 17), Blocks.IRON_ORE);
        h.setBlock(new BlockPos(13, GROUND + 1, 19), Blocks.FURNACE);
        CompanionEntity companion = spawn(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.COAL, 2));
        var future = run(h, companion, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:iron_ingot\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.IRON_INGOT) >= 2, future.join().report());
        });
    }

    /** What none of his skills gets is said at once, and nothing is started. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void anUnobtainableItemIsRefusedAtOnce(GameTestHelper h) {
        ground(h);
        CompanionEntity companion = spawn(h);
        var future = run(h, companion, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:ender_pearl\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone(), "Plan still running");
            var outcome = future.join();
            h.assertTrue(outcome.report().contains("Не знаю, где взять") && !companion.isWorking() && !companion.isGatheringTask(), outcome.report());
        });
    }

    private static List<String> types(CompanionObtain.Plan plan) { return plan.steps().stream().map(CompanionObtain.Step::type).toList(); }

    private static java.util.concurrent.CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity companion, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-obtain", () -> false));
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            if (y <= GROUND) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            else if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 14, GROUND + 1, 16);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(12, GROUND + 1, 14));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(32);
        return companion;
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

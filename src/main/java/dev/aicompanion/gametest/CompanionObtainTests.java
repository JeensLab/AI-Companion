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
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.item.ItemEntity;
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
        int sand = indexOf(glass, "gather_loose", "minecraft:sand"), melt = indexOf(glass, "smelt_items", "minecraft:glass");
        h.assertTrue(!glass.refused() && sand >= 0 && melt > sand, "Glass is dug sand, then smelted: " + glass);
        h.succeed();
    }

    /** Plan only: a flint and steel from an empty bag is iron ore, the furnace, flint dug out of gravel, then the craft. */
    @GameTest(template = "empty")
    public static void flintAndSteelIsPlannedFromOreAndGravel(GameTestHelper h) {
        var plan = CompanionObtain.plan(h.getLevel(), List.of(), false, "minecraft:flint_and_steel", 1);
        int ore = indexOf(plan, "mine_ore", "minecraft:iron_ore"), ingot = indexOf(plan, "smelt_items", "minecraft:iron_ingot");
        int flint = indexOf(plan, "gather_loose", "minecraft:flint"), craft = indexOf(plan, "craft_item", "minecraft:flint_and_steel");
        h.assertTrue(!plan.refused() && ore >= 0 && ingot > ore && flint >= 0 && craft > flint && craft > ingot && craft == plan.steps().size() - 1, "Flint and steel: " + plan);
        h.assertTrue(plan.steps().get(flint).count() == 1, "One flint for one flint and steel: " + plan);
        var many = CompanionObtain.plan(h.getLevel(), List.of(), false, "minecraft:flint", 20);
        h.assertTrue(!many.refused() && many.steps().stream().allMatch(step -> step.type().equals("gather_loose") && step.item().equals("minecraft:flint") && step.count() <= 8)
                && many.steps().stream().mapToInt(CompanionObtain.Step::count).sum() == 20, "Twenty flint come in steps of at most eight: " + many);
        h.succeed();
    }

    /**
     * A real flint and steel: the ingot is in the bag, the flint is dug out of a gravel patch, the craft follows. A gravel block dug is
     * one gravel or one flint item, and the craft eats one flint, so blocks plus items plus the flint inside the steel stay constant.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void flintAndSteelFromAnIngotAndGravel(GameTestHelper h) {
        ground(h);
        for (int x = 18; x <= 22; x++) for (int z = 19; z <= 23; z++) h.setBlock(new BlockPos(x, GROUND, z), Blocks.GRAVEL);
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.IRON_INGOT));
        ItemStack shovel = new ItemStack(Items.STONE_SHOVEL);
        shovel.enchant(h.getLevel().registryAccess().holderOrThrow(Enchantments.FORTUNE), 3);
        companion.setItem(1, shovel);
        int units = gravelUnits(h, companion);
        var future = run(h, companion, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:flint_and_steel\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.FLINT_AND_STEEL) == 1 && count(companion, Items.IRON_INGOT) == 0 && !future.join().report().contains("План остановлен"), future.join().report());
            h.assertTrue(gravelUnits(h, companion) == units, "Gravel units " + units + " became " + gravelUnits(h, companion) + ": " + companion.getStoredItemsSummary());
        });
    }

    private static int gravelUnits(GameTestHelper h, CompanionEntity companion) {
        int total = count(companion, Items.GRAVEL) + count(companion, Items.FLINT) + count(companion, Items.FLINT_AND_STEEL);
        for (int x = 0; x < 32; x++) for (int y = 1; y <= GROUND + 6; y++) for (int z = 0; z < 32; z++) if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.GRAVEL)) total++;
        for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(3), found -> found.getItem().is(Items.GRAVEL) || found.getItem().is(Items.FLINT)))
            total += item.getItem().getCount();
        return total + h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, h.getBounds().inflate(3)).size();
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

    /** An ingot leaves the bag after the smelting (a pickaxe upgrade in the game): the missing ore is dug up and smelted too. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void ingotsLostOnTheWayAreMinedAgain(GameTestHelper h) {
        ground(h);
        for (int z = 16; z <= 18; z++) h.setBlock(new BlockPos(20, GROUND + 1, z), Blocks.IRON_ORE);
        h.setBlock(new BlockPos(13, GROUND + 1, 19), Blocks.FURNACE);
        CompanionEntity companion = spawn(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.COAL, 2));
        var server = h.getLevel().getServer();
        var real = new CompanionServerPlanPort(server, companion.getOwnerId(), "gametest-obtain", () -> false);
        boolean[] eaten = {false};
        var port = new CompanionPlanRunner.Port() {
            @Override public String trace() { return real.trace(); }
            @Override public boolean cancelled() { return false; }
            @Override public java.util.concurrent.CompletableFuture<CompanionPlanRunner.State> state(dev.aicompanion.client.PlannedAction a) { return real.state(a); }
            @Override public java.util.concurrent.CompletableFuture<Void> pause(int seconds) { return real.pause(seconds); }
            @Override public java.util.concurrent.CompletableFuture<CompanionPlanRunner.Expansion> expand(dev.aicompanion.client.PlannedAction a) { return real.expand(a); }
            @Override public java.util.concurrent.CompletableFuture<CompanionPlanRunner.Attempt> run(dev.aicompanion.client.PlannedAction a) {
                var out = new java.util.concurrent.CompletableFuture<CompanionPlanRunner.Attempt>();
                real.run(a).thenAccept(attempt -> server.execute(() -> {
                    if (a.type().equals("smelt_items") && !eaten[0]) {
                        for (int slot = 0; slot < companion.getContainerSize() && !eaten[0]; slot++)
                            if (companion.getItem(slot).is(Items.IRON_INGOT)) { companion.getItem(slot).shrink(1); eaten[0] = true; }
                    }
                    out.complete(attempt);
                }));
                return out;
            }
        };
        var future = CompanionPlanRunner.run(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"obtain_item\",\"item\":\"minecraft:iron_ingot\",\"count\":2}]")), port);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(eaten[0] && count(companion, Items.IRON_INGOT) >= 2, future.join().report());
        });
    }

    /** «Скрафтить» with no materials is turned into digging them up, like obtain_item. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void craftWithoutMaterialsIsObtained(GameTestHelper h) {
        CompanionEntity companion = craftArena(h);
        var future = run(h, companion, "[{\"action\":\"craft_item\",\"item\":\"minecraft:shears\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.SHEARS) == 1 && !future.join().report().contains("План остановлен"), future.join().report());
        });
    }

    /** The craft count is new ones: 1 carried + 1 asked = 2. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void craftCountsNewOnesOnTopOfTheBag(GameTestHelper h) {
        CompanionEntity companion = craftArena(h);
        companion.setItem(1, new ItemStack(Items.SHEARS));
        var future = run(h, companion, "[{\"action\":\"craft_item\",\"item\":\"minecraft:shears\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.SHEARS) == 2 && !future.join().report().contains("План остановлен"), future.join().report());
        });
    }

    /** A full bag is not a shortage: the craft is refused as before, nothing is dug. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void craftWithAFullBagIsNotObtained(GameTestHelper h) {
        CompanionEntity companion = craftArena(h);
        for (int slot = 0; slot < companion.getContainerSize(); slot++) companion.setItem(slot, new ItemStack(Items.WOODEN_SWORD));
        companion.setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
        var future = run(h, companion, "[{\"action\":\"craft_item\",\"item\":\"minecraft:oak_button\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            var outcome = future.join();
            h.assertTrue(outcome.report().contains("нет места") && count(companion, Items.OAK_PLANKS) == 5 && count(companion, Items.OAK_BUTTON) == 0
                    && count(companion, Items.IRON_INGOT) == 0, outcome.report());
        });
    }

    /** Ore for the shears only: the bucket cannot be got, so its steps are skipped and the shears are still taken in hand. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void aPartMissingDoesNotDropTheRestOfThePlan(GameTestHelper h) {
        CompanionEntity companion = craftArena(h);
        var future = run(h, companion, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:shears\",\"count\":1},"
                + "{\"action\":\"obtain_item\",\"item\":\"minecraft:bucket\",\"count\":1},"
                + "{\"action\":\"equip_item\",\"item\":\"minecraft:shears\",\"slot\":\"mainhand\"},"
                + "{\"action\":\"give_items\",\"item\":\"minecraft:bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            var outcome = future.join();
            h.assertTrue(companion.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.SHEARS) && count(companion, Items.BUCKET) == 0
                    && outcome.report().contains(dev.aicompanion.client.CompanionDialogue.PARTIAL_PREFIX) && !outcome.report().contains("План остановлен")
                    && outcome.skipped().stream().anyMatch(a -> a.type().equals("give_items")) && outcome.skipped().stream().anyMatch(a -> a.type().equals("obtain_item")), outcome.toString());
        });
    }

    /** Iron ore, a furnace and coal at hand, an empty bag: shears take two ingots. */
    private static CompanionEntity craftArena(GameTestHelper h) {
        ground(h);
        for (int z = 16; z <= 18; z++) h.setBlock(new BlockPos(20, GROUND + 1, z), Blocks.IRON_ORE);
        h.setBlock(new BlockPos(13, GROUND + 1, 19), Blocks.FURNACE);
        CompanionEntity companion = spawn(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.COAL, 2));
        return companion;
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

    private static int indexOf(CompanionObtain.Plan plan, String type, String item) {
        for (int i = 0; i < plan.steps().size(); i++) if (plan.steps().get(i).type().equals(type) && plan.steps().get(i).item().equals(item)) return i;
        return -1;
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

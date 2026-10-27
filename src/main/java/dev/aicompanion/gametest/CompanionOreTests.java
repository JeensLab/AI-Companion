package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Ore like a player: the right pickaxe, only ore seen (now or before), a short tunnel to it, the whole vein, back up, nothing lost. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionOreTests {
    private static final int SURFACE = 6; // feet level above the top layer of the buried arena

    /** Iron in a stone wall in sight: mined with the stone pickaxe, the raw iron picked up, the pickaxe worn. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void ironInAWallIsMinedWithAStonePickaxe(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.IRON_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        long id = companion.activeWorkId();
        h.succeedWhen(() -> {
            done(h, companion);
            CompanionActionResult result = companion.workResult(id);
            h.assertTrue(result != null && result.success() && result.message().contains("прибавилось 1"), report(companion));
            h.assertTrue(h.getBlockState(ore).isAir() && count(companion, Items.RAW_IRON) == 1, "No real raw iron: " + companion.getStoredItemsSummary());
            h.assertTrue(companion.getMainHandItem().is(Items.STONE_PICKAXE) && companion.getMainHandItem().getDamageValue() >= 1, "Pickaxe not used");
        });
    }

    /** Three iron blocks in a row into the wall, only the first one in sight: the whole vein is taken. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void theWholeVeinIsMined(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        for (int x = 20; x <= 22; x++) h.setBlock(new BlockPos(x, 1, 16), Blocks.IRON_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(companion.startWork("mine_ore", "iron", 3).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.RAW_IRON) == 3, report(companion));
            for (int x = 20; x <= 22; x++) h.assertTrue(h.getBlockState(new BlockPos(x, 1, 16)).isAir(), "Vein block left at x=" + x);
        });
    }

    /** Iron buried in the ground, no cave, never seen: no x-ray, so the order ends honestly and the ore stays where it is. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void buriedOreIsNotSeenThroughStone(GameTestHelper h) {
        CompanionEntity companion = buried(h);
        BlockPos ore = new BlockPos(16, 3, 23); h.setBlock(ore, Blocks.IRON_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        long id = companion.activeWorkId();
        h.succeedWhen(() -> {
            done(h, companion);
            CompanionActionResult result = companion.workResult(id);
            h.assertTrue(result != null && !result.success() && result.message().contains("не вижу"), "Not an honest refusal: " + result);
            h.assertTrue(h.getBlockState(ore).is(Blocks.IRON_ORE) && !companion.remembersOre(h.absolutePos(ore)), "Buried ore was found through stone");
            h.assertTrue(count(companion, Items.COBBLESTONE) + count(companion, Items.DIRT) == 0, "Dug without seeing any ore: " + report(companion));
        });
    }

    /**
     * Iron he once saw at the bottom of a trench, and the trench filled in since: remembered (and saved), and reached like a
     * player — a tunnel, stairs down and then straight, never a shaft. The stone of the tunnel is picked up too, and Oleg
     * walks back up his own stairs.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void oreSeenEarlierIsReachedByATunnelNotAShaft(GameTestHelper h) {
        CompanionEntity companion = buried(h);
        companion.setFollowing(false);
        BlockPos ore = new BlockPos(16, 3, 23); h.setBlock(ore, Blocks.IRON_ORE);
        // A trench down to the ore, open towards him: its top face is in sight from where he stands.
        for (int x = 15; x <= 17; x++) for (int z = 19; z <= 23; z++) for (int y = 4; y < SURFACE; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.remembersOre(h.absolutePos(ore)), "Ore in sight not noticed")).thenExecute(() -> {
            CompoundTag saved = new CompoundTag(); companion.addAdditionalSaveData(saved);
            h.assertTrue(saved.getList("SeenOre", 10).size() == 1, "Seen ore not saved: " + saved.get("SeenOre"));
            for (int x = 15; x <= 17; x++) for (int z = 19; z <= 23; z++) for (int y = 4; y < SURFACE; y++)
                h.setBlock(new BlockPos(x, y, z), y == SURFACE - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            h.assertTrue(companion.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        }).thenWaitUntil(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.RAW_IRON) == 1, report(companion));
            h.assertTrue(h.getBlockState(ore).isAir(), "Ore still there");
            int deepestColumn = 0;
            for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) {
                int column = 0;
                for (int y = 1; y < SURFACE; y++) if (h.getBlockState(new BlockPos(x, y, z)).isAir()) column++;
                deepestColumn = Math.max(deepestColumn, column);
            }
            h.assertTrue(deepestColumn <= 3, "Dug a shaft: deepest column " + deepestColumn);
            h.assertTrue(count(companion, Items.COBBLESTONE) + count(companion, Items.DIRT) > 0, "Tunnel blocks not picked up");
            h.assertTrue(companion.getY() >= h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() - .01, "Did not climb back out: " + companion.position());
            h.assertTrue(!companion.remembersOre(h.absolutePos(ore)), "Mined ore still remembered");
        }).thenSucceed();
    }

    /** Lava right behind the ore: it is not dug, the reason is said, nothing spills. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void oreNextToLavaIsLeftAlone(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.IRON_ORE);
        h.setBlock(new BlockPos(21, 1, 16), Blocks.LAVA);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("лава"), companion.getLastTaskResult());
            h.assertTrue(h.getBlockState(ore).is(Blocks.IRON_ORE) && h.getBlockState(new BlockPos(19, 1, 16)).isAir(), "Ore dug or lava spilled");
        });
    }

    /** Only a wooden pickaxe: iron needs stone, so a stone pickaxe is made at the bench from the cobblestone, then the iron is mined. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void woodenPickaxeIsReplacedForIron(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.IRON_ORE);
        h.setBlock(new BlockPos(14, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 3)); companion.setItem(1, new ItemStack(Items.STICK, 2));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.RAW_IRON) == 1, report(companion));
            // The 3 cobblestone and 2 sticks went into the pickaxe; one new cobblestone comes from the block over the ore.
            h.assertTrue(count(companion, Items.STONE_PICKAXE) == 1 && count(companion, Items.STICK) == 0 && count(companion, Items.COBBLESTONE) <= 1,
                    "No stone pickaxe made: " + companion.getStoredItemsSummary());
        });
    }

    /** Gold with only a stone pickaxe and no iron: refused at once as blocked, with the reason; the gold is not wasted. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void goldWithoutAnIronPickaxeIsRefusedHonestly(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.GOLD_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:gold_ore", 1).success(), "Ore order not accepted");
        long id = companion.activeWorkId();
        h.succeedWhen(() -> {
            done(h, companion);
            CompanionActionResult result = companion.workResult(id);
            h.assertTrue(result != null && result.status() == CompanionActionResult.Status.BLOCKED && result.message().contains("железная кирка"), "Result: " + result);
            h.assertTrue(h.getBlockState(ore).is(Blocks.GOLD_ORE) && count(companion, Items.STONE_PICKAXE) == 1, "Gold wasted or pickaxe lost");
        });
    }

    /** Three iron ingots in the bag are enough: an iron pickaxe is made, and the gold is really mined. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void ironIngotsBecomeAPickaxeForGold(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.GOLD_ORE);
        h.setBlock(new BlockPos(14, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.IRON_INGOT, 3)); companion.setItem(1, new ItemStack(Items.STICK, 2));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:gold_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.RAW_GOLD) == 1, report(companion));
            h.assertTrue(count(companion, Items.IRON_PICKAXE) == 1 && count(companion, Items.IRON_INGOT) == 0, "No iron pickaxe: " + companion.getStoredItemsSummary());
        });
    }

    /** «Добудь и переплавь железо» as the model plans it: ore first, then everything raw into the furnace. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void mineThenSmeltAsOnePlan(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        h.setBlock(new BlockPos(20, 1, 16), Blocks.IRON_ORE); h.setBlock(new BlockPos(20, 2, 16), Blocks.IRON_ORE);
        h.setBlock(new BlockPos(14, 1, 18), Blocks.FURNACE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.COAL, 1));
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "[{\"action\":\"mine_ore\",\"item\":\"minecraft:iron_ore\",\"count\":2},{\"action\":\"smelt_items\",\"item\":\"minecraft:raw_iron\"}]"));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-ore-plan", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            var outcome = future.join();
            h.assertTrue(outcome.skipped().isEmpty() && outcome.report().contains("прибавилось 2") && outcome.report().contains("Переплавил"), outcome.report());
            h.assertTrue(count(companion, Items.IRON_INGOT) == 2 && count(companion, Items.RAW_IRON) == 0, "Wrong result: " + companion.getStoredItemsSummary());
        });
    }

    /** Leaving the world in the middle: the save says the work was active, the bag keeps what was mined, nothing is doubled. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void savingMidWayLosesNothing(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        for (int x = 20; x <= 22; x++) h.setBlock(new BlockPos(x, 1, 16), Blocks.IRON_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:iron_ore", 3).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(companion.isWorking() && count(companion, Items.RAW_IRON) >= 1, "Waiting for the first raw iron");
            CompoundTag saved = new CompoundTag();
            companion.addAdditionalSaveData(saved);
            CompanionEntity reloaded = AICompanionMod.COMPANION.get().create(h.getLevel());
            reloaded.readAdditionalSaveData(saved);
            h.assertTrue(saved.getBoolean("WorkActive") && !reloaded.isWorking(), "Work state not saved honestly");
            h.assertTrue(count(reloaded, Items.RAW_IRON) == count(companion, Items.RAW_IRON) && count(reloaded, Items.STONE_PICKAXE) == 1, "Bag changed on reload");
            h.assertTrue(reloaded.getLastTaskResult().contains("прервалась"), "No honest note: " + reloaded.getLastTaskResult());
        });
    }

    /** The plan format, the quick command and the wording: kinds by any name, large counts split, wrong kinds refused. */
    @GameTest(template = "tall_tree")
    public static void oreProtocolAndLocalCommand(GameTestHelper h) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"mine_ore\",\"item\":\"minecraft:raw_iron\",\"count\":40}]"));
        h.assertTrue(plan.size() == 3 && plan.stream().allMatch(step -> step.item().equals("minecraft:iron_ore"))
                && plan.stream().mapToInt(PlannedAction::count).sum() == 40, "Split or kind wrong: " + plan);
        h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"mine_ore\",\"item\":\"minecraft:deepslate_diamond_ore\"}]"))
                .getFirst().item().equals("minecraft:diamond_ore"), "Deepslate ore not understood");
        boolean refused = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"mine_ore\",\"item\":\"minecraft:obsidian\"}]")); } catch (IllegalStateException e) { refused = true; }
        h.assertTrue(refused, "Unknown ore accepted");
        PlannedAction five = CompanionWorkCommands.parse("Олег, добудь 5 железа", "Олег");
        h.assertTrue(five != null && five.type().equals("mine_ore") && five.item().equals("minecraft:iron_ore") && five.count() == 5, "Quick command: " + five);
        PlannedAction coal = CompanionWorkCommands.parse("добудь угля", "Олег");
        h.assertTrue(coal != null && coal.item().equals("minecraft:coal_ore") && coal.count() == 4, "Coal without number: " + coal);
        PlannedAction stone = CompanionWorkCommands.parse("добудь камень", "Олег");
        h.assertTrue(stone != null && stone.type().equals("gather_stone"), "Stone command broken: " + stone);
        h.assertTrue(CompanionDialogue.describe(plan.getFirst()).startsWith("добыть железо"), "Skipped-step wording");
        h.succeed();
    }

    private static CompanionEntity flat(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        bind(h, companion);
        return companion;
    }
    /** A stone wall three high across the arena at x=20..22, facing Oleg. */
    private static void wall(GameTestHelper h) {
        for (int x = 20; x <= 23; x++) for (int z = 10; z <= 22; z++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }
    private static CompanionEntity buried(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y < SURFACE; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? Blocks.BEDROCK
                    : y <= 3 ? Blocks.STONE : y == SURFACE - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, SURFACE, 16);
        bind(h, companion);
        return companion;
    }
    private static void bind(GameTestHelper h, CompanionEntity companion) {
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
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

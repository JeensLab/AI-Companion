package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Ore like a player: the right pickaxe, ore in sight or behind a short tunnel, the whole vein, back up, nothing lost. */
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

    /**
     * Iron buried in the ground, no cave: a player's tunnel, stairs down and then straight, never a shaft.
     * The stone of the tunnel is picked up too, and Oleg walks back up his own stairs.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void buriedOreIsReachedByATunnelNotAShaft(GameTestHelper h) {
        CompanionEntity companion = buried(h);
        BlockPos ore = new BlockPos(16, 3, 23); h.setBlock(ore, Blocks.IRON_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
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
        });
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

    /** Nether quartz ore two blocks into a netherrack wall: dug through the netherrack with a wooden pickaxe; a second ore beside lava is left alone. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void quartzOreInNetherrackIsMinedThroughTheRock(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        for (int x = 20; x <= 23; x++) for (int z = 10; z <= 22; z++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.NETHERRACK);
        BlockPos ore = new BlockPos(22, 1, 16); h.setBlock(ore, Blocks.NETHER_QUARTZ_ORE);
        BlockPos lavaOre = new BlockPos(22, 1, 20); h.setBlock(lavaOre, Blocks.NETHER_QUARTZ_ORE);
        h.setBlock(new BlockPos(23, 1, 20), Blocks.LAVA);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:quartz_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.QUARTZ) >= 1, report(companion) + " " + companion.getLastTaskResult());
            h.assertTrue(h.getBlockState(ore).isAir() && h.getBlockState(lavaOre).is(Blocks.NETHER_QUARTZ_ORE) && h.getBlockState(new BlockPos(23, 1, 20)).is(Blocks.LAVA), "Ore not dug or lava touched");
            h.assertTrue(count(companion, Items.NETHERRACK) > 0, "No tunnel through the netherrack: " + companion.getStoredItemsSummary());
        });
    }

    /** Plan only: quartz is named by its item, its ore or its word; the plan and the codec dig one block of ore per quartz. */
    @GameTest(template = "tall_tree")
    public static void quartzIsAnOreKindAndPlannedAsMining(GameTestHelper h) {
        for (String name : new String[]{"quartz", "minecraft:quartz", "minecraft:nether_quartz_ore"})
            h.assertTrue(dev.aicompanion.entity.CompanionOre.Kind.of(name) == dev.aicompanion.entity.CompanionOre.Kind.QUARTZ, "Kind of " + name);
        // From the overworld the quartz is mined in the Nether, through a lit portal (CompanionErrand); in the Nether on the errand, there.
        var portal = new dev.aicompanion.entity.CompanionObtain.Trip(h.absolutePos(new BlockPos(3, 2, 3)), false);
        var plan = dev.aicompanion.entity.CompanionObtain.plan(h.getLevel(), java.util.List.of(), false, "minecraft:quartz", 5, null, null, portal);
        h.assertTrue(!plan.refused() && plan.steps().stream().anyMatch(step -> step.type().equals("mine_ore") && step.item().equals("minecraft:quartz_ore") && step.count() == 5),
                "Quartz plan: " + plan);
        h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"mine_ore\",\"item\":\"minecraft:quartz\"}]"))
                .getFirst().item().equals("minecraft:quartz_ore"), "Codec does not take quartz");
        h.succeed();
    }

    /** A stone pickaxe in his hand is no reason to leave the iron one in the bag: the ore is taken with the pickaxe that breaks it fastest. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void theBetterPickaxeInTheBagTakesTheOre(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.IRON_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(companion.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        long id = companion.activeWorkId();
        h.succeedWhen(() -> {
            done(h, companion);
            CompanionActionResult result = companion.workResult(id);
            h.assertTrue(result != null && result.success(), report(companion));
            h.assertTrue(h.getBlockState(ore).isAir() && count(companion, Items.RAW_IRON) == 1, "No real raw iron: " + companion.getStoredItemsSummary());
            h.assertTrue(damage(companion, Items.IRON_PICKAXE) >= 1, "The iron pickaxe in the bag did not take the ore: " + companion.getStoredItemsSummary());
            h.assertTrue(damage(companion, Items.STONE_PICKAXE) == 0, "The stone pickaxe from his hand took the ore: " + companion.getStoredItemsSummary());
        });
    }

    // ---- the promise is what is really there, and the bag decides (the owner, 26.11) ----

    /** The words of the orders below, the ore blocks and the drops they give; every kind is taken with the same iron pickaxe. */
    private record Ore(String word, Block ore, Item drop, String phrase) { }

    private static final List<Ore> KINDS = List.of(
            new Ore("угля", Blocks.COAL_ORE, Items.COAL, "добудь угля"),
            new Ore("железа", Blocks.IRON_ORE, Items.RAW_IRON, "добудь железа"),
            new Ore("лазурита", Blocks.LAPIS_ORE, Items.LAPIS_LAZULI, "добудь лазурита"),
            new Ore("редстоуна", Blocks.REDSTONE_ORE, Items.REDSTONE, "добудь редстоуна"),
            new Ore("алмазов", Blocks.DIAMOND_ORE, Items.DIAMOND, "добудь алмазов"));

    /**
     * The owner's own report (26.11): «добудь редстоуна» over the one ore block the world gives said «добыть редстоун (4 бл.
     * руды)», mined the block, took it, and answered «с этим пока не справился». An order without a number promises what is really
     * there — one block is one — and it is done by the bag: the drop is in it, the work succeeded, and the player is not told «из 4».
     * Every ore kind is taken the same way, not only redstone.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void everyOreOrderOverOneBlockIsDoneNotFailed(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        BlockPos ore = new BlockPos(20, 1, 16);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        int[] at = {0};
        boolean[] started = {false};
        h.onEachTick(() -> {
            if (at[0] >= KINDS.size()) return;
            if (companion.isWorking() || companion.hasPendingLoot()) return;
            Ore kind = KINDS.get(at[0]);
            if (!started[0]) {
                // The scene of this kind: its block, the bag as it was, him at his place.
                h.setBlock(ore, kind.ore());
                for (int slot = 0; slot < companion.getContainerSize(); slot++)
                    if (companion.getItem(slot).is(kind.drop())) companion.setItem(slot, ItemStack.EMPTY);
                BlockPos home = h.absolutePos(new BlockPos(16, 1, 16));
                companion.teleportTo(home.getX() + .5, home.getY(), home.getZ() + .5);
                companion.setHealth(companion.getMaxHealth());
                // The number the router sends for a phrase without one: the wish the work must cut down to what is there.
                h.assertTrue(companion.startWork("mine_ore", BuiltInRegistries.ITEM.getKey(kind.ore().asItem()).toString(), 4).success(),
                        "«" + kind.phrase() + "» not accepted");
                started[0] = true;
                return;
            }
            String report = companion.getLastTaskResult();
            h.assertTrue(companion.didLastTaskSucceed(), "«" + kind.phrase() + "» failed over the one block: " + report);
            h.assertTrue(count(companion, kind.drop()) >= 1, "«" + kind.phrase() + "»: no " + kind.drop() + " in the bag: " + report);
            h.assertTrue(!report.contains("из 4"), "«" + kind.phrase() + "» promised the wish, not the block: " + report);
            h.assertBlockPresent(Blocks.AIR, ore);
            started[0] = false;
            at[0]++;
        });
        h.succeedWhen(() -> h.assertTrue(at[0] >= KINDS.size(), "Only " + at[0] + " of " + KINDS.size() + " ore kinds ran"));
    }

    /** «Добудь 4 редстоуна» over the one block the world gives: the promise is that block, and the bag meets it. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void fourRedstoneOverOneBlockPromisesOne(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.REDSTONE_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        var actions = CompanionLocalCommands.actions("companion work mine_ore 4 minecraft:redstone_ore");
        var future = say(h, companion, actions);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(companion, Items.REDSTONE) >= 1, "No redstone in the bag: " + report);
            h.assertTrue(!report.contains("План остановлен") && !report.contains("Часть не вышла"), "Failed over the one block: " + report);
            h.assertTrue(!report.contains("из 4"), "Four was promised over one block: " + report);
            h.assertBlockPresent(Blocks.AIR, ore);
        });
    }

    /** «Добудь 4 редстоуна» with four blocks about him: the promise is four, and four is in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void fourRedstoneOverFourBlocksIsFour(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        for (int x = 20; x <= 23; x++) h.setBlock(new BlockPos(x, 1, 16), Blocks.REDSTONE_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        var actions = CompanionLocalCommands.actions("companion work mine_ore 4 minecraft:redstone_ore");
        var future = say(h, companion, actions);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(companion, Items.REDSTONE) >= 4, "Not four redstone: " + count(companion, Items.REDSTONE) + ", " + report);
            h.assertTrue(!report.contains("План остановлен") && !report.contains("Часть не вышла"), "Failed over four blocks: " + report);
            // The bag met the promise: a block of redstone ore gives four or five of it, so some blocks may stand — one was taken.
            h.assertTrue(java.util.stream.IntStream.rangeClosed(20, 23).anyMatch(x -> h.getBlockState(new BlockPos(x, 1, 16)).isAir()),
                    "No ore block was mined: " + report);
        });
    }

    /**
     * The owner's own words (26.11): «добудь редстоуна» with the one ore block the world gives. The router sends the wish of
     * four; the work promises the one block that is really there, mines it, and the bag's redstone is the answer — no «1 из 4»,
     * no «с этим пока не справился». The phrase itself is the entry path, not the command it builds.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void thePhraseForRedstoneOverOneBlockIsDoneByItsDrop(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.REDSTONE_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        String command = CompanionLocalCommands.route("добудь редстоуна", "Олег", item -> new ItemStack(item).getHoverName().getString());
        h.assertTrue("companion work mine_ore 4 minecraft:redstone_ore".equals(command), "The phrase left the router: " + command);
        var actions = CompanionLocalCommands.actions(command);
        var future = say(h, companion, actions);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(companion, Items.REDSTONE) >= 1, "No redstone in the bag: " + report);
            h.assertTrue(!report.contains("План остановлен") && !report.contains("Часть не вышла"), "Failed over the one block: " + report);
            h.assertTrue(!report.contains("из 4"), "The wish of four was promised over one block: " + report);
            String said = CompanionDialogue.summarize(actions, report);
            h.assertTrue(!said.contains("не справился") && !said.contains("из 4"), "The player heard: " + said);
            h.assertBlockPresent(Blocks.AIR, ore);
        });
    }

    /**
     * The bag counts towards the promise, as the obtain does: two redstone carried and two ore blocks about are four, so «добудь 4
     * редстоуна» is met by the four in the bag at the end — the promise is four, not the two blocks alone.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void theBagCountsTowardsThePromiseOfABatchOrder(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        h.setBlock(new BlockPos(20, 1, 16), Blocks.REDSTONE_ORE); h.setBlock(new BlockPos(21, 1, 16), Blocks.REDSTONE_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        companion.setItem(0, new ItemStack(Items.REDSTONE, 2));
        var actions = CompanionLocalCommands.actions("companion work mine_ore 4 minecraft:redstone_ore");
        var future = say(h, companion, actions);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(!report.contains("План остановлен") && !report.contains("Часть не вышла"), "The four were not met: " + report);
            h.assertTrue(count(companion, Items.REDSTONE) >= 4, "Not four redstone: " + count(companion, Items.REDSTONE) + ", " + report);
            h.assertTrue(!report.contains("из 4"), "The wish was promised, not the promise: " + report);
        });
    }

    /** The bag answers first, as every obtain does: four redstone carried, an ore block about — the order is done and nothing is dug. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void anOrderTheBagAlreadyMeetsDigsNothing(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.REDSTONE_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        companion.setItem(0, new ItemStack(Items.REDSTONE, 4));
        var actions = CompanionLocalCommands.actions("companion work mine_ore 4 minecraft:redstone_ore");
        var future = say(h, companion, actions);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(!report.contains("План остановлен") && !report.contains("Часть не вышла"), "The met order failed: " + report);
            h.assertTrue(count(companion, Items.REDSTONE) == 4, "The bag changed: " + count(companion, Items.REDSTONE) + ", " + report);
            // The line is the bag's own count, not «С рудой закончил» over an order nothing was dug for.
            String said = CompanionDialogue.summarize(actions, report);
            h.assertTrue(said.contains("4") && !said.contains("не справился"), "The met order was not said with its count: " + said);
            h.assertTrue(damage(companion, Items.IRON_PICKAXE) == 0, "A pickaxe was worn for nothing: " + report);
            h.assertBlockPresent(Blocks.REDSTONE_ORE, ore);
        });
    }

    /** A redstone block in the bag is its nine: «добудь 4 редстоуна» is met by it, and the ore about him is not dug. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aRedstoneBlockInTheBagIsItsNine(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.REDSTONE_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        companion.setItem(0, new ItemStack(Items.REDSTONE_BLOCK, 1));
        var actions = CompanionLocalCommands.actions("companion work mine_ore 4 minecraft:redstone_ore");
        var future = say(h, companion, actions);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(!report.contains("План остановлен") && !report.contains("Часть не вышла"), "The nine redstone of the block were not counted: " + report);
            h.assertTrue(count(companion, Items.REDSTONE_BLOCK) == 1, "The redstone block left the bag: " + report);
            h.assertTrue(report.contains("9 шт."), "The bag's nine were not said: " + report);
            h.assertBlockPresent(Blocks.REDSTONE_ORE, ore);
        });
    }

    /**
     * The owner's exact scene (26.11): a redstone block lies about and «добудь 4 редстоуна» was asked. The block is the order —
     * it is taken as one block, and its nine redstone are what the bag holds; the line is «Собрал: «Блок редстоуна», 1 шт.»,
     * never «1 из 4» and never «с этим пока не справился».
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aRedstoneBlockLyingAboutIsTheWholeOrder(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        BlockPos block = new BlockPos(20, 1, 16);
        h.setBlock(block, Blocks.REDSTONE_BLOCK);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        var actions = CompanionLocalCommands.actions("companion work mine_ore 4 minecraft:redstone_ore");
        var future = say(h, companion, actions);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(companion, Items.REDSTONE_BLOCK) == 1, "The block lying about is not in the bag: " + report);
            h.assertTrue(!report.contains("План остановлен") && !report.contains("Часть не вышла"), "Failed over the block: " + report);
            h.assertTrue(!report.contains("из 4"), "The four redstone were promised over one block: " + report);
            String said = CompanionDialogue.summarize(actions, report);
            h.assertTrue(!said.contains("не справился") && !said.contains("из 4"), "The player heard: " + said);
            h.assertBlockPresent(Blocks.AIR, block);
        });
    }

    /** «Добудь 10 камня» is still ten: the ore's promise changed nothing for the stone order (a regression pin, green at base). */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void tenStoneIsStillTen(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        wall(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        String command = CompanionLocalCommands.route("добудь 10 камня", "Олег", item -> new ItemStack(item).getHoverName().getString());
        h.assertTrue("companion work gather_stone 10 resources".equals(command), "The phrase lost its ten: " + command);
        var future = say(h, companion, CompanionLocalCommands.actions(command));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(!report.contains("План остановлен") && !report.contains("Часть не вышла"), "Ten stone failed: " + report);
            h.assertTrue(count(companion, Items.COBBLESTONE) >= 10, "Not ten cobblestone: " + count(companion, Items.COBBLESTONE) + ", " + report);
        });
    }

    /**
     * The autonomy's own ore deed is a batch (gather this many more), not the player's total: «добыть угля» for eight more with
     * eight coal already in the bag still digs the ore about him. A bag that answers a batch would end it with nothing gained, and
     * the autonomy's stock deed would ask for it again and again.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void anAutonomyBatchStillDigsWithTheBagAlreadyFull(GameTestHelper h) {
        CompanionEntity companion = flat(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.COAL_ORE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        companion.setItem(0, new ItemStack(Items.COAL, 8));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var said = CompanionEvents.executeAutonomous(owner, "mine_ore", "minecraft:coal", 8);
        h.assertTrue(said.success(), "The batch was not taken: " + said.message());
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking(), "Still working: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.COAL) > 8, "The batch gained nothing, the bag answered it: " + companion.getLastTaskResult());
            h.assertBlockPresent(Blocks.AIR, ore);
        });
    }

    /** The words of the phrase above, through the player's own router, run by the plan runner. */
    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity companion, List<PlannedAction> actions) {
        return CompanionPlanRunner.run(actions,
                new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-ore", () -> false));
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
    /** The wear on the stack of this item, in the bag or in either hand; -1 when he has none. */
    private static int damage(CompanionEntity companion, Item item) {
        int found = -1;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) found = Math.max(found, companion.getItem(slot).getDamageValue());
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) found = Math.max(found, companion.getItemBySlot(slot).getDamageValue());
        return found;
    }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

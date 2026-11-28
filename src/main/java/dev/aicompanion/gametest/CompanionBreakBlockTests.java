package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * «Сломай печку и отдай мне» (BRKBK): a standing block is broken as a player breaks it, the block and what it held are picked
 * up, and with «отдай» all of it is tossed to the player. The block is the nearest of its kind to the player, within 16 blocks of
 * him; what cannot be broken (bedrock) or lacks the tool is refused with the world untouched.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBreakBlockTests {
    /** The Russian names the player sees; the server of a test runs en_us. */
    private static final Map<String, String> NAMES = Map.of("furnace", "Печь", "crafting_table", "Верстак", "chest", "Сундук", "barrel", "Бочка");
    private static final Function<Item, String> RU = item -> {
        String name = NAMES.get(BuiltInRegistries.ITEM.getKey(item).getPath());
        return name != null ? name : new ItemStack(item).getHoverName().getString();
    };

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aStandingFurnaceIsBrokenAndGivenNotTheBench(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ServerPlayer player = player(h, companion);
        companion.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        BlockPos furnace = h.absolutePos(new BlockPos(12, 1, 8)), bench = h.absolutePos(new BlockPos(12, 1, 11));
        h.getLevel().setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(bench, Blocks.CRAFTING_TABLE.defaultBlockState());
        var plan = say(h, companion, "Олег, сломай печку и отдай мне", "break-furnace");
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + companion.getLastTaskResult());
            h.assertTrue(h.getLevel().getBlockState(furnace).isAir(), "The furnace still stands");
            h.assertTrue(h.getLevel().getBlockState(bench).is(Blocks.CRAFTING_TABLE), "The bench was broken instead");
            h.assertTrue(atPlayer(h, player, Items.FURNACE) == 1, "The furnace did not come to the player: " + atPlayer(h, player, Items.FURNACE) + ", " + plan.join().report());
            h.assertTrue(stored(companion, Items.FURNACE) == 0, "He kept the furnace");
            h.assertTrue(companion.didLastTaskSucceed(), "No success: " + companion.getLastTaskResult());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void theStandingBenchIsBrokenEvenWhenAChestHoldsOne(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ServerPlayer player = player(h, companion);
        BlockPos bench = h.absolutePos(new BlockPos(12, 1, 8)), chest = h.absolutePos(new BlockPos(12, 1, 11));
        h.getLevel().setBlockAndUpdate(bench, Blocks.CRAFTING_TABLE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        ((net.minecraft.world.Container) h.getLevel().getBlockEntity(chest)).setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        var plan = say(h, companion, "сломай верстак и отдай мне", "break-bench");
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + companion.getLastTaskResult());
            h.assertTrue(h.getLevel().getBlockState(bench).isAir(), "The standing bench still stands");
            h.assertTrue(h.getLevel().getBlockState(chest).is(Blocks.CHEST), "The chest was touched");
            h.assertTrue(((net.minecraft.world.Container) h.getLevel().getBlockEntity(chest)).getItem(0).is(Items.CRAFTING_TABLE), "The chest lost its bench");
            h.assertTrue(atPlayer(h, player, Items.CRAFTING_TABLE) == 1, "Not the one bench to the player: " + atPlayer(h, player, Items.CRAFTING_TABLE));
            h.assertTrue(companion.didLastTaskSucceed(), "No success: " + companion.getLastTaskResult());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aChestWithThingsComesWholeToHim(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        BlockPos chest = h.absolutePos(new BlockPos(12, 1, 8));
        h.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        var inside = (net.minecraft.world.Container) h.getLevel().getBlockEntity(chest);
        inside.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        inside.setItem(1, new ItemStack(Items.STICK, 3));
        var plan = say(h, companion, "сломай сундук", "break-chest");
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + companion.getLastTaskResult());
            h.assertTrue(h.getLevel().getBlockState(chest).isAir(), "The chest still stands");
            h.assertTrue(stored(companion, Items.CHEST) == 1 && stored(companion, Items.COBBLESTONE) == 5 && stored(companion, Items.STICK) == 3,
                    "The bag: chest " + stored(companion, Items.CHEST) + ", stone " + stored(companion, Items.COBBLESTONE) + ", sticks " + stored(companion, Items.STICK));
            h.assertTrue(onTheGround(h) == 0, "Things lie on the ground: " + onTheGround(h));
            h.assertTrue(companion.didLastTaskSucceed(), "No success: " + companion.getLastTaskResult());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aChestIsGivenWithEverythingInIt(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ServerPlayer player = player(h, companion);
        BlockPos chest = h.absolutePos(new BlockPos(12, 1, 8));
        h.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        var inside = (net.minecraft.world.Container) h.getLevel().getBlockEntity(chest);
        inside.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        inside.setItem(1, new ItemStack(Items.STICK, 3));
        var plan = say(h, companion, "разбей сундук и дай мне", "break-chest-give");
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + companion.getLastTaskResult());
            h.assertTrue(h.getLevel().getBlockState(chest).isAir(), "The chest still stands");
            h.assertTrue(atPlayer(h, player, Items.CHEST) == 1 && atPlayer(h, player, Items.COBBLESTONE) == 5 && atPlayer(h, player, Items.STICK) == 3,
                    "At the player: chest " + atPlayer(h, player, Items.CHEST) + ", stone " + atPlayer(h, player, Items.COBBLESTONE)
                            + ", sticks " + atPlayer(h, player, Items.STICK) + "; " + plan.join().report());
            h.assertTrue(stored(companion, Items.CHEST) + stored(companion, Items.COBBLESTONE) + stored(companion, Items.STICK) == 0, "He kept some of it");
            h.assertTrue(companion.didLastTaskSucceed(), "No success: " + companion.getLastTaskResult());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void giveHandsOverTheStackOfTheBlockNotHisOwnOfTheSameItem(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ServerPlayer player = player(h, companion);
        ItemStack mine = new ItemStack(Items.IRON_PICKAXE);
        mine.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("mine"));
        companion.setItem(0, mine);
        BlockPos chest = h.absolutePos(new BlockPos(12, 1, 8));
        h.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        ((net.minecraft.world.Container) h.getLevel().getBlockEntity(chest)).setItem(0, new ItemStack(Items.IRON_PICKAXE));
        var plan = say(h, companion, "разбей сундук и дай мне", "break-same-item");
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + companion.getLastTaskResult());
            List<ItemStack> given = new java.util.ArrayList<>();
            for (ItemStack stack : player.getInventory().items) if (stack.is(Items.IRON_PICKAXE)) given.add(stack);
            for (ItemEntity drop : h.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2), found -> found.getItem().is(Items.IRON_PICKAXE))) given.add(drop.getItem());
            h.assertTrue(given.size() == 1 && !given.getFirst().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME), "Given: " + given + "; " + plan.join().report());
            h.assertTrue(companion.getItem(0).is(Items.IRON_PICKAXE) && companion.getItem(0).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME), "He lost his own pickaxe");
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 200)
    public static void aBlockThatDropsNothingIsRefusedAndStands(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        BlockPos glass = h.absolutePos(new BlockPos(10, 1, 8));
        h.getLevel().setBlockAndUpdate(glass, Blocks.GLASS.defaultBlockState());
        CompanionActionResult result = CompanionEvents.executeAction(player(h, companion), "break_block", "minecraft:glass", 1);
        h.assertTrue(!result.success() && result.message().contains("ничего не даёт"), "Glass: " + result.status() + " " + result.message());
        h.assertTrue(h.getLevel().getBlockState(glass).is(Blocks.GLASS) && !companion.isWorking(), "The glass was touched");
        h.succeed();
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void whatSomeoneElseTookFirstIsSaidAndTheRestIsGiven(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ServerPlayer player = player(h, companion);
        BlockPos chest = h.absolutePos(new BlockPos(12, 1, 8));
        h.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        var inside = (net.minecraft.world.Container) h.getLevel().getBlockEntity(chest);
        inside.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        inside.setItem(1, new ItemStack(Items.STICK, 3));
        // Someone beside the chest takes the stone the very moment it spills: the stone never joins the world (no race with his pickup).
        Thief.area = h.getBounds().inflate(2); Thief.taken = Items.COBBLESTONE;
        var plan = say(h, companion, "разбей сундук и отдай мне", "break-taken");
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + companion.getLastTaskResult());
            h.assertTrue(atPlayer(h, player, Items.CHEST) == 1 && atPlayer(h, player, Items.STICK) == 3, "Not given what he got: " + plan.join().report());
            h.assertTrue(plan.join().report().contains("кто-то другой") && !plan.join().report().contains("подобрал только"), "Not said honestly: " + plan.join().report());
            Thief.taken = null;
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void theBlockUnderThePlayerIsNeverTheOneBroken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        BlockPos under = h.absolutePos(new BlockPos(5, 0, 8)), far = h.absolutePos(new BlockPos(12, 1, 8));
        h.getLevel().setBlockAndUpdate(under, Blocks.FURNACE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(far, Blocks.FURNACE.defaultBlockState());
        var plan = say(h, companion, "сломай печку", "break-under");
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + companion.getLastTaskResult());
            h.assertTrue(h.getLevel().getBlockState(under).is(Blocks.FURNACE), "The block under the player was broken");
            h.assertTrue(h.getLevel().getBlockState(far).isAir(), "The other furnace still stands");
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aBedIsBrokenFromItsFootToo(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        // The foot (x 12) is the half nearest the player; only the head (x 13) carries the bed's loot.
        BlockPos foot = h.absolutePos(new BlockPos(12, 1, 8)), head = h.absolutePos(new BlockPos(13, 1, 8));
        var bed = Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST);
        h.getLevel().setBlock(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
        h.getLevel().setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
        var started = CompanionEvents.executeAction(player(h, companion), "break_block", "minecraft:red_bed", 1);
        h.assertTrue(started.success(), "Refused: " + started.message());
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking() && companion.didLastTaskSucceed(), "Not done: " + companion.getLastTaskResult());
            h.assertTrue(!h.getLevel().getBlockState(foot).is(Blocks.RED_BED) && !h.getLevel().getBlockState(head).is(Blocks.RED_BED), "The bed still stands");
            h.assertTrue(stored(companion, Items.RED_BED) == 1, "The bed is not in his bag: " + stored(companion, Items.RED_BED));
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void theChestThePlayerStandsOnIsNeverBroken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ServerPlayer player = player(h, companion);
        BlockPos under = h.absolutePos(new BlockPos(5, 1, 8)), far = h.absolutePos(new BlockPos(12, 1, 8));
        h.getLevel().setBlockAndUpdate(under, Blocks.CHEST.defaultBlockState());
        h.getLevel().setBlockAndUpdate(far, Blocks.CHEST.defaultBlockState());
        // A chest is lower than a block: he stands in its cell, not above it.
        player.teleportTo(under.getX() + .5, under.getY() + .875, under.getZ() + .5);
        var plan = say(h, companion, "сломай сундук", "break-stood-on");
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + companion.getLastTaskResult());
            h.assertTrue(h.getLevel().getBlockState(under).is(Blocks.CHEST), "The chest under the player was broken");
            h.assertTrue(h.getLevel().getBlockState(far).isAir(), "The other chest still stands");
        });
    }

    @GameTest(template = "tall_tree", batch = "multitask_break_parked", timeoutTicks = 3600)
    public static void aFurnaceHoldingHisParkedSmeltIsNotTheOneBroken(GameTestHelper h) {
        CompanionEntity companion = CompanionMultitaskSupport.setup(h);
        // The parked smelt's furnace is the one nearer the player; the other one is farther.
        BlockPos parked = new BlockPos(19, 1, 16), other = new BlockPos(10, 1, 24);
        h.setBlock(parked, Blocks.FURNACE);
        h.setBlock(other, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 4)); companion.setItem(1, new ItemStack(Items.COAL, 2)); companion.setItem(2, new ItemStack(Items.STONE_PICKAXE));
        var link = CompanionMultitaskSupport.link(h, companion);
        link.submit(List.of(new dev.aicompanion.client.PlannedAction("smelt_items", 0, 4, "minecraft:beef")), "gametest-break-parked", () -> false);
        boolean[] started = {false};
        h.succeedWhen(() -> {
            if (!started[0]) {
                h.assertTrue(CompanionMultitaskSupport.status(link, "smelt_items") == dev.aicompanion.client.CompanionTaskQueue.Status.WAITING, "Not parked yet: " + companion.getLastTaskResult());
                h.assertTrue(!companion.batches().summaries().isEmpty(), "No batch");
                var result = CompanionEvents.executeAction(CompanionMultitaskSupport.owner(h, companion), "break_block", "minecraft:furnace", 1);
                h.assertTrue(result.success(), "Refused: " + result.message());
                started[0] = true;
            }
            h.assertTrue(!companion.isWorking(), "Still breaking: " + companion.workPhase());
            h.assertTrue(h.getBlockState(parked).is(Blocks.FURNACE), "The furnace of the parked smelt was broken");
            h.assertTrue(h.getBlockState(other).isAir(), "The other furnace still stands: " + companion.getLastTaskResult());
            link.close();
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void theFurnaceNearestToThePlayerIsTheOneBroken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        // Near him (he stands at 8,8, the player at 5,8) and far from the player, then near the player and far from him.
        BlockPos nearHim = h.absolutePos(new BlockPos(9, 1, 11)), nearPlayer = h.absolutePos(new BlockPos(3, 1, 8));
        h.getLevel().setBlockAndUpdate(nearHim, Blocks.FURNACE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(nearPlayer, Blocks.FURNACE.defaultBlockState());
        var plan = say(h, companion, "сломай печку", "break-nearest");
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + companion.getLastTaskResult());
            h.assertTrue(h.getLevel().getBlockState(nearPlayer).isAir(), "The furnace nearest the player still stands");
            h.assertTrue(h.getLevel().getBlockState(nearHim).is(Blocks.FURNACE), "The other furnace was broken");
            h.assertTrue(stored(companion, Items.FURNACE) == 1, "The furnace is not in his bag: " + stored(companion, Items.FURNACE));
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 200)
    public static void bedrockAndNoToolAreRefusedWithTheWorldUntouched(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        BlockPos rock = h.absolutePos(new BlockPos(10, 1, 8)), furnace = h.absolutePos(new BlockPos(10, 1, 10));
        h.getLevel().setBlockAndUpdate(rock, Blocks.BEDROCK.defaultBlockState());
        h.getLevel().setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
        ServerPlayer player = player(h, companion);
        CompanionActionResult bedrock = CompanionEvents.executeAction(player, "break_block", "minecraft:bedrock", 1);
        h.assertTrue(!bedrock.success() && bedrock.message().contains("не сломать"), "Bedrock: " + bedrock.status() + " " + bedrock.message());
        // No pickaxe in the bag: a furnace gives nothing to a bare hand, so it is not broken.
        CompanionActionResult bare = CompanionEvents.executeAction(player, "break_block", "minecraft:furnace", 1);
        h.assertTrue(!bare.success() && bare.message().contains("инструмент"), "No tool: " + bare.status() + " " + bare.message());
        h.assertTrue(h.getLevel().getBlockState(rock).is(Blocks.BEDROCK) && h.getLevel().getBlockState(furnace).is(Blocks.FURNACE), "The world changed");
        h.assertTrue(!companion.isWorking(), "A work was left running: " + companion.workPhase());
        h.succeed();
    }

    @GameTest(template = "large_search", timeoutTicks = 200)
    public static void nothingWithinSixteenOfThePlayerIsAnHonestRefusal(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        ServerPlayer player = player(h, companion);
        CompanionActionResult none = CompanionEvents.executeAction(player, "break_block", "minecraft:furnace", 1);
        h.assertTrue(!none.success() && none.message().contains("нет"), "No furnace at all: " + none.status() + " " + none.message());
        // 17 blocks from the player (5,8): out of his 16, though a step from the companion who is sent to it.
        BlockPos far = h.absolutePos(new BlockPos(22, 1, 8));
        h.getLevel().setBlockAndUpdate(far, Blocks.FURNACE.defaultBlockState());
        companion.teleportTo(far.getX() - 1.5, far.getY(), far.getZ() + .5);
        CompanionActionResult tooFar = CompanionEvents.executeAction(player, "break_block", "minecraft:furnace", 1);
        h.assertTrue(!tooFar.success() && h.getLevel().getBlockState(far).is(Blocks.FURNACE), "A furnace 17 blocks off: " + tooFar.status() + " " + tooFar.message());
        h.succeed();
    }

    @GameTest(template = "large_search", timeoutTicks = 400)
    public static void stopLeavesTheBlockStanding(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        BlockPos furnace = h.absolutePos(new BlockPos(14, 1, 8));
        h.getLevel().setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
        ServerPlayer player = player(h, companion);
        CompanionActionResult started = CompanionEvents.executeAction(player, "break_block", "minecraft:furnace", 1);
        h.assertTrue(started.success() && companion.isWorking(), "Not started: " + started.status() + " " + started.message());
        h.startSequence().thenIdle(10).thenExecute(() -> CompanionEvents.executeAction(player, "stop", "", 1))
                .thenIdle(5).thenExecute(() -> {
                    h.assertTrue(!companion.isWorking(), "Still working after the stop: " + companion.workPhase());
                    h.assertTrue(h.getLevel().getBlockState(furnace).is(Blocks.FURNACE), "The furnace was broken after the stop");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void theCodecRejectsAnUnknownOrEmptyBlock(GameTestHelper h) {
        for (String bad : List.of("{\"action\":\"break_block\",\"item\":\"minecraft:no_such_block\"}", "{\"action\":\"break_block\",\"item\":\"\"}",
                "{\"action\":\"break_block\"}", "{\"action\":\"break_block\",\"item\":\"minecraft:air\"}",
                "{\"action\":\"break_block\",\"item\":\"minecraft:furnace\",\"count\":3}",
                "{\"action\":\"break_block\",\"item\":\"minecraft:cave_air\"}", "{\"action\":\"break_block\",\"item\":\"minecraft:void_air\"}",
                "{\"action\":\"break_block\",\"item\":\"minecraft:furnace\",\"give\":\"yes\"}", "{\"action\":\"break_block\",\"item\":\"minecraft:furnace\",\"give\":1}"))
            h.assertTrue(rejected("[" + bad + ",{\"action\":\"come\"}]"), "The plan with " + bad + " was not rejected as a whole");
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"break_block\",\"item\":\"minecraft:furnace\",\"give\":true}]"));
        h.assertTrue(plan.size() == 1 && "break_block".equals(plan.getFirst().type()) && "minecraft:furnace".equals(plan.getFirst().item())
                && "give".equals(plan.getFirst().slot()), "The step: " + plan);
        var keep = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"break_block\",\"item\":\"minecraft:furnace\"}]"));
        h.assertTrue("auto".equals(keep.getFirst().slot()), "Without give: " + keep);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theDirectCommandReadsTheBreakAndKeepsTheOldPhrases(GameTestHelper h) {
        String broken = CompanionLocalCommands.route("сломай печку", "Олег", RU);
        h.assertTrue("companion work break_block 1 minecraft:furnace".equals(broken), "сломай печку -> " + broken);
        String given = CompanionLocalCommands.route("Олег, разбей верстак и отдай мне", "Олег", RU);
        h.assertTrue("companion work break_block 1 minecraft:crafting_table give".equals(given), "разбей верстак и отдай мне -> " + given);
        String removed = CompanionLocalCommands.route("разбей бочку и дай мне", "Олег", RU);
        h.assertTrue("companion work break_block 1 minecraft:barrel give".equals(removed), "разбей бочку и дай мне -> " + removed);
        var step = CompanionLocalCommands.actions(given);
        h.assertTrue(step.size() == 1 && "break_block".equals(step.getFirst().type()) && "give".equals(step.getFirst().slot())
                && "minecraft:crafting_table".equals(step.getFirst().item()), "The step of the command: " + step);
        String field = CompanionLocalCommands.route("разбей огород", "Олег", RU);
        h.assertTrue(field != null && field.startsWith("companion work farm_crops "), "разбей огород -> " + field);
        String chests = CompanionLocalCommands.route("убери вещи в сундук", "Олег", RU);
        h.assertTrue(chests != null && !chests.contains("break_block"), "убери вещи в сундук -> " + chests);
        // «Убери» is «put away» (a torch in his hand), and a name many blocks answer to is the model's.
        String put = CompanionLocalCommands.route("убери верстак", "Олег", RU);
        h.assertTrue(put == null || !put.contains("break_block"), "убери верстак -> " + put);
        Function<Item, String> beds = item -> BuiltInRegistries.ITEM.getKey(item).getPath().endsWith("_bed") ? "Кровать " + BuiltInRegistries.ITEM.getKey(item).getPath() : RU.apply(item);
        String bed = CompanionLocalCommands.route("сломай кровать", "Олег", beds);
        h.assertTrue(bed == null || !bed.contains("break_block"), "сломай кровать -> " + bed);
        String fetch = CompanionLocalCommands.route("принеси верстак", "Олег", RU);
        h.assertTrue(fetch == null || !fetch.contains("break_block"), "принеси верстак -> " + fetch);
        h.succeed();
    }

    /** Takes an item as it spills in the area of one test: it is never added to the world. */
    @net.neoforged.fml.common.EventBusSubscriber(modid = AICompanionMod.MOD_ID)
    public static final class Thief {
        static volatile Item taken;
        static volatile net.minecraft.world.phys.AABB area;

        @net.neoforged.bus.api.SubscribeEvent
        public static void join(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
            Item item = taken;
            if (item != null && event.getEntity() instanceof ItemEntity drop && drop.getItem().is(item) && area != null && area.contains(drop.position())) event.setCanceled(true);
        }
    }

    private static boolean rejected(String json) {
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString(json)); return false; }
        catch (IllegalStateException refused) { return true; }
    }

    /** The player's phrase through the router and the runner: a local order, no model. */
    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity companion, String phrase, String trace) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» is left to the model");
        return CompanionPlanRunner.run(CompanionLocalCommands.actions(command),
                new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-" + trace, () -> false));
    }

    /** What lies at the player: in his hands' reach on the ground, or already in his inventory. */
    private static int atPlayer(GameTestHelper h, ServerPlayer player, Item item) {
        int total = player.getInventory().countItem(item);
        for (ItemEntity drop : h.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2), found -> found.getItem().is(item)))
            total += drop.getItem().getCount();
        return total;
    }

    private static int onTheGround(GameTestHelper h) {
        int total = 0;
        for (ItemEntity drop : h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(2))) total += drop.getItem().getCount();
        return total;
    }

    private static int stored(CompanionEntity companion, Item item) {
        int total = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) total += companion.getItem(slot).getCount();
        return total;
    }

    private static ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(5, 1, 8));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        companion.setFollowing(false);
        return companion;
    }
}

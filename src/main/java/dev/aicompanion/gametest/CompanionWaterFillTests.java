package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFacts;
import dev.aicompanion.entity.CompanionMilestones;
import dev.aicompanion.entity.CompanionPlaybooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Water for his bucket (decided 05.11): on an order, for the dragon goal and by himself, from water the game fills again by itself (a
 * lake, a river), so the world is as it was after him.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWaterFillTests {
    private static final int GROUND = 5;
    /** What he says as he steps aside for water (decided 05.11). */
    private static final String GOING = "Наберу воды в ведро.";

    /**
     * The dragon goal wants one bucket of water, right after the flint and steel and before the obsidian he cannot get yet, so it is
     * reached; met by a bucket of water in his bag, got by obtain_item. The Nether goal does without it: water boils there.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theDragonGoalWantsABucketOfWaterBeforeTheObsidian(GameTestHelper h) {
        var book = CompanionPlaybooks.of("defeat_ender_dragon", 0);
        List<String> labels = book.milestones().stream().map(milestone -> milestone.label(false)).toList();
        int water = labels.indexOf("ведро воды"), flint = labels.indexOf("огниво");
        h.assertTrue(flint >= 0 && flint < water && water < labels.indexOf("10 обсидиана"), "Not between the flint and the obsidian: " + labels);
        var milestone = book.milestones().get(water);
        h.assertTrue(milestone.supported(), "He is said not to know how to get water");
        h.assertTrue(CompanionPlaybooks.of("prepare_nether", 0).milestones().stream().noneMatch(m -> m.label(false).equals("ведро воды")), "The Nether goal wants water");
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 4);
        c.setItem(0, new ItemStack(Items.BUCKET));
        var facts = CompanionFacts.of(c);
        h.assertTrue(!milestone.satisfied(facts, false, book.milestones()), "An empty bucket meets it");
        h.assertTrue(milestone.method(facts, false, book.milestones()).equals(List.of(new CompanionMilestones.Step("obtain_item", "minecraft:water_bucket", 1, "auto"))),
                "Not got by obtain_item: " + milestone.method(facts, false, book.milestones()));
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(milestone.satisfied(CompanionFacts.of(c), false, book.milestones()), "A bucket of water does not meet it");
        h.succeed();
    }

    /** «Набери воды»: his empty bucket comes back full from the lake, and the lake is whole again behind him. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void anOrderFillsHisEmptyBucketFromALakeAndLeavesTheLakeWhole(GameTestHelper h) {
        ground(h);
        List<BlockPos> lake = lake(h, 21, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:water_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.BUCKET) == 0, "No water in his bucket: " + future.join().report());
            for (BlockPos cell : lake) h.assertTrue(h.getLevel().getFluidState(h.absolutePos(cell)).isSourceOfType(Fluids.WATER), "The lake lost " + cell);
        });
    }

    /**
     * «Принеси воды» and «набери воды» need no model (FWR29, decided 07.11): the same plan the model makes, a bucket of water got and,
     * when it is for the player («принеси», «дай», «мне»), handed over; up to sixteen. What else is said goes to the model.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fetchingWaterNeedsNoModel(GameTestHelper h) {
        String give = "[{\"action\":\"obtain_item\",\"item\":\"minecraft:water_bucket\",\"count\":%d},{\"action\":\"give_items\",\"item\":\"minecraft:water_bucket\",\"count\":%<d}]";
        String keep = "[{\"action\":\"obtain_item\",\"item\":\"minecraft:water_bucket\",\"count\":%d}]";
        Object[][] said = {
                {"Олег, принеси воды", give, 1}, {"принеси мне ведро воды", give, 1}, {"Принеси 3 ведра воды", give, 3}, {"принеси шестнадцать ведер воды", give, 16},
                {"Олег, набери воды и принеси", give, 1}, {"набери мне воды", give, 1}, {"набери ведро воды и дай", give, 1}, {"набери воды и отдай мне", give, 1},
                {"дай мне воды", give, 1}, {"дай ведро воды", give, 1}, {"сходи за водой", give, 1},
                {"Олег, набери воды", keep, 1}, {"набери ведро воды", keep, 1}, {"набери воды в ведро", keep, 1}, {"наполни ведро водой", keep, 1},
                {"набери два ведра воды", keep, 2}};
        for (Object[] phrase : said) {
            var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(String.format((String) phrase[1], (int) phrase[2])));
            String route = CompanionLocalCommands.route((String) phrase[0], "Олег");
            h.assertTrue(route != null && CompanionLocalCommands.actions(route).equals(plan), "«" + phrase[0] + "» is not " + plan + " but " + route);
            h.assertTrue(!CompanionLocalCommands.cancelsPlans(route), "«" + phrase[0] + "» drops the plans in the queue");
        }
        // «Вёдра» with no number are all his buckets, as «во все вёдра»: how many only the server knows.
        for (String model : List.of("набери воды во все ведра", "наполни вёдра водой", "набери воды в вёдра", "принеси вёдер воды", "принеси 0 ведер воды",
                "принеси 20 ведер воды", "принеси воды сколько сможешь", "принеси еще воды", "не набирай воды", "набери воды и полей огород", "набери воды или принеси молока"))
            h.assertTrue(CompanionLocalCommands.route(model, "Олег") == null, "«" + model + "» is not left to the model: " + CompanionLocalCommands.route(model, "Олег"));
        h.succeed();
    }

    /** «Олег, принеси воды» without the model: his empty bucket filled at the lake and tossed to the player's feet, the lake whole. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void bringWaterFillsHisBucketAndTossesItToThePlayer(GameTestHelper h) {
        ground(h);
        List<BlockPos> lake = lake(h, 21, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        var plan = CompanionLocalCommands.actions(CompanionLocalCommands.route("Олег, принеси воды", "Олег"));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-water-fetch", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 0 && count(c, Items.BUCKET) == 0, "His bucket is still his: " + c.getStoredItemsSummary() + " · " + future.join().report());
            boolean tossed = !h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, owner.getBoundingBox().inflate(4),
                    item -> item.getItem().is(Items.WATER_BUCKET)).isEmpty();
            h.assertTrue(tossed || owner.getInventory().contains(new ItemStack(Items.WATER_BUCKET)), "No bucket of water at the player's feet: " + future.join().report());
            for (BlockPos cell : lake) h.assertTrue(h.getLevel().getFluidState(h.absolutePos(cell)).isSourceOfType(Fluids.WATER), "The lake lost " + cell);
        });
    }

    /**
     * Water the game does not fill again — a single source (the player's field water) and a pair of them (a channel) — is not his to
     * take: he says he found no water, and his bucket stays empty.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aSingleSourceOrAPairIsNotTakenAndHeSaysHeFoundNoWater(GameTestHelper h) {
        ground(h);
        List<BlockPos> water = List.of(new BlockPos(16, GROUND, 16), new BlockPos(20, GROUND, 12), new BlockPos(21, GROUND, 12));
        for (BlockPos cell : water) h.setBlock(cell, Blocks.WATER);
        CompanionEntity c = spawn(h);
        // The least radius: the lakes of the tests beside this one, seen beyond its walls, would be water he cannot get to.
        c.setSearchRadius(16);
        c.setItem(0, new ItemStack(Items.BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:water_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Не нашёл, где набрать воды: рядом ни озера, ни реки."), "He does not say he found no water: " + report);
            h.assertTrue(count(c, Items.BUCKET) == 1 && count(c, Items.WATER_BUCKET) == 0, "His bucket is not as it was: " + report);
            for (BlockPos cell : water) h.assertTrue(h.getLevel().getFluidState(h.absolutePos(cell)).isSourceOfType(Fluids.WATER), "The water at " + cell + " was taken");
        });
    }

    /** «Набери воды в два ведра» with one bucket and three iron ingots: he makes the second bucket, then fills both. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void withABucketShortHeMakesOneOfIronAndFillsThemAll(GameTestHelper h) {
        ground(h);
        lake(h, 21, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        c.setItem(1, new ItemStack(Items.IRON_INGOT, 3));
        c.setItem(2, new ItemStack(Items.CRAFTING_TABLE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:water_bucket\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 2 && count(c, Items.BUCKET) == 0 && count(c, Items.IRON_INGOT) == 0,
                    "Not two buckets of water out of his iron: " + c.getStoredItemsSummary() + " · " + future.join().report());
        });
    }

    /**
     * By himself, with no order: two empty buckets and a lake eleven blocks off. He says he goes for water, fills one bucket — a
     * spare for a fall — and leaves the other empty.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void byHimselfHeStepsAsideAndFillsOneBucket(GameTestHelper h) {
        ground(h);
        List<BlockPos> lake = lake(h, 21, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET, 2));
        h.succeedWhen(() -> {
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.BUCKET) == 1, "Not one bucket of water and one empty: " + c.getStoredItemsSummary());
            h.assertTrue(GOING.equals(c.autonomyLine()), "Said: " + c.autonomyLine());
            for (BlockPos cell : lake) h.assertTrue(h.getLevel().getFluidState(h.absolutePos(cell)).isSourceOfType(Fluids.WATER), "The lake lost " + cell);
        });
    }

    /** A bucket of water already in his bag: he does not go for more, however near the lake (he may stroll about idly meanwhile). */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void withWaterInHisBagHeDoesNotGoForMore(GameTestHelper h) {
        ground(h);
        lake(h, 21, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        boolean[] going = {false};
        h.onEachTick(() -> going[0] |= GOING.equals(c.autonomyLine()));
        h.runAtTickTime(300, () -> {
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.BUCKET) == 1, "He went for more water: " + c.getStoredItemsSummary());
            h.assertTrue(!going[0], "He set off for water");
            h.succeed();
        });
    }

    /**
     * «Сделай ведро, подожди и дай мне» beside water: the plan makes the bucket to give it, so between its steps — a pause of the plan
     * too — he does not fill it by himself, and the player gets it empty.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void aBucketMadeToBeGivenIsNotFilledOnTheWay(GameTestHelper h) {
        ground(h);
        lake(h, 11, 13, 3, 7);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.IRON_INGOT, 3));
        c.setItem(2, new ItemStack(Items.CRAFTING_TABLE));
        boolean[] water = {false};
        h.onEachTick(() -> water[0] |= count(c, Items.WATER_BUCKET) > 0);
        var future = run(h, c, "[{\"action\":\"craft_item\",\"item\":\"minecraft:bucket\",\"count\":1},{\"action\":\"wait\",\"seconds\":2},"
                + "{\"action\":\"give_items\",\"item\":\"minecraft:bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(!water[0], "He filled the bucket made for the player");
            h.assertTrue(count(c, Items.BUCKET) == 0 && count(c, Items.IRON_INGOT) == 0, "The bucket was not made and given: " + c.getStoredItemsSummary() + " · " + future.join().report());
        });
    }

    /** After «стой» he takes no step aside: a lake eleven blocks off stays where it is and so does he. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void afterStayHeTakesNoStepAside(GameTestHelper h) {
        ground(h);
        lake(h, 21, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        CompanionEvents.executeAction(owner(h, c), "wait", "", 1);
        var start = c.position();
        h.runAtTickTime(300, () -> {
            h.assertTrue(count(c, Items.WATER_BUCKET) == 0, "He went for water after «стой»");
            h.assertTrue(c.position().distanceTo(start) < 1, "He walked off after «стой»: " + c.position().distanceTo(start) + " blocks");
            h.succeed();
        });
    }

    /** «Стоп» leaves him following the player, yet until the next order he takes no step aside for water either. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void afterStopHeTakesNoStepAsideEither(GameTestHelper h) {
        ground(h);
        lake(h, 21, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        CompanionEvents.executeAction(owner(h, c), "stop", "", 1);
        boolean[] going = {false};
        h.onEachTick(() -> going[0] |= GOING.equals(c.autonomyLine()));
        h.runAtTickTime(300, () -> {
            h.assertTrue(c.isFollowing(), "«Стоп» did not leave him following");
            h.assertTrue(!going[0] && count(c, Items.WATER_BUCKET) == 0, "He went for water after «стоп»");
            h.succeed();
        });
    }

    /** After «стой» water his hand reaches is still taken, without a word: he does not move for it. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void afterStayHeFillsFromWaterHisHandReaches(GameTestHelper h) {
        ground(h);
        List<BlockPos> lake = lake(h, 12, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        CompanionEvents.executeAction(owner(h, c), "wait", "", 1);
        var start = c.position();
        h.succeedWhen(() -> {
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1, "No water from the lake at his hand");
            h.assertTrue(c.position().distanceTo(start) < 1, "He moved for it: " + c.position().distanceTo(start) + " blocks");
            h.assertTrue(!GOING.equals(c.autonomyLine()), "He said he goes for water");
            for (BlockPos cell : lake) h.assertTrue(h.getLevel().getFluidState(h.absolutePos(cell)).isSourceOfType(Fluids.WATER), "The lake lost " + cell);
        });
    }

    /** Lily pads on the near side of the lake: he takes his water where nothing floats, and every pad is still there after him. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void lilyPadsOnTheLakeStayWhereTheyAre(GameTestHelper h) {
        ground(h);
        lake(h, 21, 14, 3, 5);
        for (int z = 14; z < 19; z++) h.setBlock(new BlockPos(21, GROUND + 1, z), Blocks.LILY_PAD);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:water_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1, "No water in his bucket: " + future.join().report());
            for (int z = 14; z < 19; z++) h.assertTrue(h.getBlockState(new BlockPos(21, GROUND + 1, z)).is(Blocks.LILY_PAD), "The lily pad at z " + z + " is gone");
        });
    }

    /** His bag full and his empty buckets in one stack: the water has nowhere to go, and he says so at once instead of walking off. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aFullBagIsSaidAtOnce(GameTestHelper h) {
        ground(h);
        lake(h, 12, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET, 2));
        for (int slot = 1; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIRT, 64));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:water_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Сумка забита — некуда налить воду."), "He does not say his bag is full: " + future.join().report());
            h.assertTrue(count(c, Items.BUCKET) == 2 && count(c, Items.WATER_BUCKET) == 0, "His buckets are not as they were: " + c.getStoredItemsSummary());
        });
    }

    /**
     * Left standing by a plan («иди за мной, потом подожди»: its pause is no «стой» of the player) he takes no step aside for water
     * eleven blocks off: nothing would bring him back where he was left.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void leftStandingByAPlanHeTakesNoStepAside(GameTestHelper h) {
        ground(h);
        lake(h, 21, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        boolean[] going = {false};
        h.onEachTick(() -> going[0] |= GOING.equals(c.autonomyLine()));
        var future = run(h, c, "[{\"action\":\"follow\"},{\"action\":\"wait\",\"seconds\":1}]");
        h.runAtTickTime(450, () -> {
            h.assertTrue(future.isDone() && !c.isFollowing(), "He is not left standing by the plan: " + c.getLastTaskResult());
            h.assertTrue(!going[0] && count(c, Items.WATER_BUCKET) == 0, "He stepped aside for water while left standing");
            h.succeed();
        });
    }

    /**
     * The lake is eleven blocks off as the crow flies, but a wall is between and the way round is near thirty blocks: by himself he
     * takes no such walk (decided 05.11: sixteen blocks), and says nothing about going.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aLongWayRoundIsNotTakenByHimself(GameTestHelper h) {
        ground(h);
        lake(h, 21, 14, 3, 5);
        for (int z = 1; z < 29; z++) for (int y = GROUND + 1; y <= GROUND + 3; y++) h.setBlock(new BlockPos(16, y, z), Blocks.BEDROCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        boolean[] going = {false};
        h.onEachTick(() -> going[0] |= GOING.equals(c.autonomyLine()));
        h.runAtTickTime(500, () -> {
            h.assertTrue(!going[0] && count(c, Items.WATER_BUCKET) == 0, "He went the long way round for water");
            h.succeed();
        });
    }

    /**
     * The nearest water is a pond in a closed cave under the ground (air over it, earth over that: seen in AgentTest 05.11), the lake
     * farther off he can walk to: he does not give up at the cave, he passes the whole of it over and goes to the lake.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void waterHeCannotGetToIsPassedForWaterHeCan(GameTestHelper h) {
        ground(h);
        // Under most of the arena, all of it nearer than the lake: more cave water than 32 cells a look would pass over in all his looks.
        cave(h, 1, 1, 18, 20);
        List<BlockPos> lake = lake(h, 27, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:water_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1, "No water from the open lake: " + future.join().report());
            for (BlockPos cell : lake) h.assertTrue(h.getLevel().getFluidState(h.absolutePos(cell)).isSourceOfType(Fluids.WATER), "The lake lost " + cell);
        });
    }

    /** The game's rule that refills water is off for this batch alone (the batches run one after another) and on again after it. */
    @BeforeBatch(batch = "water_rule_off") public static void ruleOff(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_WATER_SOURCE_CONVERSION).set(false, level.getServer());
    }
    @AfterBatch(batch = "water_rule_off") public static void ruleOn(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_WATER_SOURCE_CONVERSION).set(true, level.getServer());
        // A batch with its own hook gets no cleanup of its own otherwise (CompanionTestSelection.cleanAfterEveryBatch).
        CompanionFixtureCleanup.afterAtomicTests(level);
    }

    /** With that rule off, «набери воды» is refused at once and honestly: no bucket is made out of his iron for water he would not take. */
    @GameTest(template = "tall_tree", batch = "water_rule_off", timeoutTicks = 600)
    public static void withTheRuleOffAnOrderIsRefusedHonestly(GameTestHelper h) {
        ground(h);
        lake(h, 12, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        c.setItem(1, new ItemStack(Items.CRAFTING_TABLE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:water_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Вода здесь сама не восполняется — набирать не буду, останется яма."), "Not refused as it is: " + future.join().report());
            h.assertTrue(count(c, Items.IRON_INGOT) == 3 && count(c, Items.BUCKET) == 0, "A bucket was made for it: " + c.getStoredItemsSummary());
        });
    }

    /** With the game's rule that refills water off, a bucket from the pond at his hand would leave a hole for good: he takes none. */
    @GameTest(template = "tall_tree", batch = "water_rule_off", timeoutTicks = 300)
    public static void withTheRuleThatRefillsWaterOffHeTakesNone(GameTestHelper h) {
        ground(h);
        List<BlockPos> lake = lake(h, 12, 14, 3, 5);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BUCKET));
        h.runAtTickTime(200, () -> {
            h.assertTrue(count(c, Items.WATER_BUCKET) == 0, "He took water that does not come back");
            for (BlockPos cell : lake) h.assertTrue(h.getLevel().getFluidState(h.absolutePos(cell)).isSourceOfType(Fluids.WATER), "The pond lost " + cell);
            h.succeed();
        });
    }

    /** Only a pond in a closed cave near him: he says he sees water he cannot get to, and his bucket stays empty. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aClosedCavePondIsWaterHeCannotGetTo(GameTestHelper h) {
        ground(h);
        cave(h, 11, 11, 17, 20);
        CompanionEntity c = spawn(h);
        c.setSearchRadius(16);
        c.setItem(0, new ItemStack(Items.BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:water_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Вижу воду, но не могу к ней подойти."), "He does not say the water is out of his way: " + future.join().report());
            h.assertTrue(count(c, Items.BUCKET) == 1 && count(c, Items.WATER_BUCKET) == 0, "His bucket is not as it was: " + c.getStoredItemsSummary());
        });
    }

    /**
     * A pond in a closed cave under the ground, as in AgentTest 05.11, from (x0, z0) to (x1, z1): water two deep a block in from the
     * edge, two blocks of air over it and its rim to stand in, earth over that, no way in.
     */
    private static void cave(GameTestHelper h, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = GROUND - 2; y <= GROUND - 1; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = x0 + 1; x < x1; x++) for (int z = z0 + 1; z < z1; z++) for (int y = GROUND - 4; y <= GROUND - 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
    }

    /** A lake two deep, {@code width} by {@code length} from (x, z), cut into the ground: every cell of it a source the game fills again. */
    private static List<BlockPos> lake(GameTestHelper h, int x, int z, int width, int length) {
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < length; dz++) for (int y = GROUND - 1; y <= GROUND; y++) {
            BlockPos cell = new BlockPos(x + dx, y, z + dz);
            h.setBlock(cell, Blocks.WATER);
            cells.add(cell);
        }
        return cells;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-water-fill", () -> false));
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            if (y <= GROUND) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            else if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
            else h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 10, GROUND + 1, 16);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(8, GROUND + 1, 14));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(32);
        return c;
    }

    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity c) {
        return (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

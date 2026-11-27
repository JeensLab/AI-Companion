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
import dev.aicompanion.entity.CompanionObtain;
import dev.aicompanion.entity.CompanionPlaybooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Obsidian like a player gets it (decided 08.11): lava he can walk to, his bucket of water over it, the obsidian mined with a pickaxe
 * that takes it and picked up, his water back in the bucket; another's portal left alone always, and the player's buildings only
 * while free-standing obsidian is about (the second tier, 20.11).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionObsidianTests {
    private static final String OBSIDIAN = "minecraft:obsidian";
    private static final int GROUND = 5;

    /**
     * obtain_item minecraft:obsidian is one step of its own, after a bucket of water if he has none. Refused at once, before a bucket
     * is made: without a diamond or netherite pickaxe (he does not go for diamonds), in the Nether (water boils) and in the End (no lava).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void obsidianIsPlannedOnlyWithAPickaxeThatTakesItAndNotInTheNetherOrTheEnd(GameTestHelper h) {
        var level = h.getLevel();
        var ready = CompanionObtain.plan(level, List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.WATER_BUCKET)), false, OBSIDIAN, 3);
        h.assertTrue(ready.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 3))), "Not one obsidian step: " + ready);
        var dry = CompanionObtain.plan(level, List.of(new ItemStack(Items.NETHERITE_PICKAXE)), false, OBSIDIAN, 10);
        h.assertTrue(!dry.refused() && dry.steps().getLast().equals(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 10))
                && dry.steps().contains(new CompanionObtain.Step("fill_bucket", "minecraft:water_bucket", 1)), "No water got first: " + dry);
        var some = CompanionObtain.plan(level, List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.OBSIDIAN, 6)), false, OBSIDIAN, 10);
        h.assertTrue(some.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 4))), "Not the rest of what he has: " + some);

        var iron = CompanionObtain.plan(level, List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.DIAMOND, 3)), false, OBSIDIAN, 10);
        h.assertTrue(iron.steps().isEmpty() && "Обсидиан берёт только алмазная кирка — дай мне её.".equals(iron.refusal()), "Not refused without the pickaxe: " + iron);
        var nether = CompanionObtain.plan(level.getServer().getLevel(Level.NETHER), List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.WATER_BUCKET)), false, OBSIDIAN, 10);
        h.assertTrue(nether.steps().isEmpty() && "В Незере обсидиан не сделать: вода здесь испаряется.".equals(nether.refusal()), "Not refused in the Nether: " + nether);
        var end = CompanionObtain.plan(level.getServer().getLevel(Level.END), List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.WATER_BUCKET)), false, OBSIDIAN, 10);
        h.assertTrue(end.steps().isEmpty() && "В Энде обсидиан не сделать: лавы здесь нет.".equals(end.refusal()), "Not refused in the End: " + end);
        h.succeed();
    }

    /**
     * Obsidian lying about him is mined where it lies: no lava to cool, so no bucket of water, no iron mined and smelted for it,
     * and no log chopped for the bench that makes one.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void obsidianLyingAboutIsMinedWithoutABucketOrIron(GameTestHelper h) {
        var plan = CompanionObtain.plan(h.getLevel(), List.of(new ItemStack(Items.DIAMOND_PICKAXE)), false, OBSIDIAN, 2,
                item -> item == Items.OBSIDIAN, item -> false);
        h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))), "Not mined where it lies: " + plan);
        h.succeed();
    }

    /** The plan for the player's own case: obsidian blocks set down beside him, a diamond pickaxe in his bag, no bucket. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void obsidianBlocksAboutHimGoIntoHisBagWithoutABucket(GameTestHelper h) {
        ground(h);
        List<BlockPos> blocks = List.of(new BlockPos(12, GROUND + 1, 16), new BlockPos(12, GROUND + 1, 18));
        for (BlockPos cell : blocks) h.setBlock(cell, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))), "Not mined where it lies: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian: " + count(c, Items.OBSIDIAN) + ", " + future.join().report());
            for (BlockPos cell : blocks) h.assertBlockPresent(Blocks.AIR, cell);
            h.assertTrue(count(c, Items.BUCKET) == 0 && count(c, Items.WATER_BUCKET) == 0, "A bucket was made for nothing: " + future.join().report());
        });
    }

    /** No obsidian about, lava there: it is still cooled with his bucket of water, and the plan says so first. */
    @GameTest(template = "tall_tree", timeoutTicks = 20)
    public static void lavaWithoutObsidianStillNeedsHisWaterBucket(GameTestHelper h) {
        ground(h);
        lava(h, 20, 14, 2, 1);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.BUCKET));
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("fill_bucket", "minecraft:water_bucket", 1),
                new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))), "Not cooled with his water: " + plan);
        h.succeed();
    }

    /** Neither obsidian nor lava about: he says what is missing, and no bucket is made of iron for it. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void withNeitherObsidianNorLavaHeNamesWhatIsMissing(GameTestHelper h) {
        var plan = CompanionObtain.plan(h.getLevel(), List.of(new ItemStack(Items.DIAMOND_PICKAXE)), false, OBSIDIAN, 10,
                item -> false, item -> false);
        String refusal = plan.refusal();
        h.assertTrue(plan.steps().isEmpty() && refusal != null && refusal.contains("обсидиан") && refusal.contains("лав")
                && !refusal.contains("желез"), "Not refused with what is missing: " + plan);
        h.succeed();
    }

    /** The dragon goal gets its ten obsidian by obtain_item now: no more where it stops and says he cannot. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void theDragonGoalGetsItsTenObsidianByObtainItem(GameTestHelper h) {
        var book = CompanionPlaybooks.of("defeat_ender_dragon", 0);
        var milestone = book.milestones().stream().filter(m -> m.label(false).equals("10 обсидиана")).findFirst().orElse(null);
        h.assertTrue(milestone != null && milestone.supported(), "He is said not to know how to get obsidian: " + milestone);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 4);
        c.setItem(0, new ItemStack(Items.OBSIDIAN, 9));
        var facts = CompanionFacts.of(c);
        h.assertTrue(!milestone.satisfied(facts, false, book.milestones()), "Nine obsidian meet it");
        h.assertTrue(milestone.method(facts, false, book.milestones()).equals(List.of(new CompanionMilestones.Step("obtain_item", OBSIDIAN, 10, "auto"))),
                "Not got by obtain_item: " + milestone.method(facts, false, book.milestones()));
        c.setItem(0, new ItemStack(Items.OBSIDIAN, 10));
        h.assertTrue(milestone.satisfied(CompanionFacts.of(c), false, book.milestones()), "Ten obsidian do not meet it");
        h.succeed();
    }

    /**
     * «Добудь 3 обсидиана» by a pond of lava two deep: his water over it, three blocks mined and all three in his bag (none burnt in
     * the lava under them), his water back in the bucket and none of it left about; mined with his diamond pickaxe, not the golden one.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void anOrderSetsLavaWithHisWaterMinesTheObsidianAndScoopsTheWaterBack(GameTestHelper h) {
        ground(h);
        List<BlockPos> pond = lava(h, 18, 15, 3, 2);
        CompanionEntity c = spawn(h);
        // A golden pickaxe is faster on obsidian and drops none: the diamond one is taken (review 08.11).
        c.setItem(0, new ItemStack(Items.GOLDEN_PICKAXE));
        c.setItem(2, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        // Two cobblestones the player set by the pond are no building of his: the lava is still to be had (second review 08.11).
        for (BlockPos cell : List.of(new BlockPos(17, GROUND + 1, 14), new BlockPos(17, GROUND + 1, 18))) placedByPlayer(h, c, cell, Blocks.COBBLESTONE);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":3}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OBSIDIAN) == 3, "Not three obsidian: " + count(c, Items.OBSIDIAN) + ", " + future.join().report());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.BUCKET) == 0, "His water is not back in the bucket");
            long mined = pond.stream().map(h::absolutePos).filter(pos -> !h.getLevel().getBlockState(pos).is(Blocks.OBSIDIAN) && !h.getLevel().getBlockState(pos).is(Blocks.LAVA)).count();
            h.assertTrue(mined == 3, mined + " cells of the pond mined for three obsidian: some burnt in the lava");
            for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(new BlockPos(1, GROUND - 2, 1)), h.absolutePos(new BlockPos(30, GROUND + 3, 30))))
                h.assertTrue(!h.getLevel().getFluidState(pos).is(FluidTags.WATER), "His water is left at " + pos.toShortString());
        });
    }

    /** The player's portals nearer to him than the lava, a lit one and one not lit yet: their frames are no obsidian lying about. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = CompanionPortalBuildTests.PORTALS)
    public static void thePlayersPortalBesideTheLavaIsLeftWhole(GameTestHelper h) {
        ground(h);
        lava(h, 18, 15, 3, 2);
        List<BlockPos> frame = new ArrayList<>();
        for (int x = 8; x <= 11; x++) for (int y = GROUND + 1; y <= GROUND + 5; y++)
            if (x == 8 || x == 11 || y == GROUND + 1 || y == GROUND + 5) { h.setBlock(new BlockPos(x, y, 21), Blocks.OBSIDIAN); frame.add(new BlockPos(x, y, 21)); }
        h.setBlock(new BlockPos(9, GROUND + 2, 21), Blocks.FIRE);
        h.assertBlockPresent(Blocks.NETHER_PORTAL, new BlockPos(10, GROUND + 4, 21));
        // And one not lit, without its corners, as a player leaves it before the flint and steel (second review 08.11).
        for (int z = 8; z <= 11; z++) for (int y = GROUND + 1; y <= GROUND + 5; y++)
            if ((z == 8 || z == 11) != (y == GROUND + 1 || y == GROUND + 5)) { h.setBlock(new BlockPos(13, y, z), Blocks.OBSIDIAN); frame.add(new BlockPos(13, y, z)); }
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian: " + future.join().report());
            for (BlockPos cell : frame) h.assertBlockPresent(Blocks.OBSIDIAN, cell);
            for (int x = 9; x <= 10; x++) for (int y = GROUND + 2; y <= GROUND + 4; y++) h.assertBlockPresent(Blocks.NETHER_PORTAL, new BlockPos(x, y, 21));
            CompanionPortalBuildTests.putOut(h);
        });
    }

    /**
     * A portal's frame and a dry block over lava, both on a site of the player's (20.11): under the second tier — no free-standing
     * obsidian about, so the player's own blocks are his to take — the frame is still a portal's and the block over lava still has
     * its drop unsafe. Both stay, and only the plain block of the site is mined.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800, batch = CompanionPortalBuildTests.PORTALS)
    public static void aPortalFrameAndObsidianOverLavaOnHisSiteStayUntouched(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        placedByPlayer(h, c, new BlockPos(14, GROUND + 1, 20), Blocks.CHEST);
        List<BlockPos> frame = new ArrayList<>();
        for (int x = 8; x <= 11; x++) for (int y = GROUND + 1; y <= GROUND + 5; y++)
            if (x == 8 || x == 11 || y == GROUND + 1 || y == GROUND + 5) { h.setBlock(new BlockPos(x, y, 21), Blocks.OBSIDIAN); frame.add(new BlockPos(x, y, 21)); }
        h.setBlock(new BlockPos(9, GROUND + 2, 21), Blocks.FIRE);
        h.assertBlockPresent(Blocks.NETHER_PORTAL, new BlockPos(10, GROUND + 4, 21));
        // A dry block over lava on the site: its drop would burn, so it is no obsidian lying about.
        BlockPos lava = new BlockPos(16, GROUND, 24);
        BlockPos overLava = lava.above();
        h.setBlock(lava, Blocks.LAVA);
        h.setBlock(overLava, Blocks.OBSIDIAN);
        BlockPos plain = new BlockPos(12, GROUND + 1, 16);
        placedByPlayer(h, c, plain, Blocks.OBSIDIAN);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        var plan = c.obtainPlan(OBSIDIAN, 1);
        h.assertTrue(!plan.refused() && plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 1))),
                "Not the one plain block of his site: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not the one plain block: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertBlockPresent(Blocks.AIR, plain);
            for (BlockPos cell : frame) h.assertBlockPresent(Blocks.OBSIDIAN, cell);
            for (int x = 9; x <= 10; x++) for (int y = GROUND + 2; y <= GROUND + 4; y++) h.assertBlockPresent(Blocks.NETHER_PORTAL, new BlockPos(x, y, 21));
            h.assertBlockPresent(Blocks.OBSIDIAN, overLava);
            h.assertBlockPresent(Blocks.LAVA, lava);
            CompanionPortalBuildTests.putOut(h);
        });
    }

    /** No lava and no obsidian within his reach: he says so at once, before any bucket is made, and pours nothing. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void withNoLavaAboutHeSaysSoAndKeepsHisWater(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":10}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Рядом нечего копать: ни обсидиана, ни лавы"), "He does not say what is missing: " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.BUCKET) == 0, "His water is not in his bucket: " + report);
        });
    }

    /**
     * «Добудь обсидиан» and «накопай 5 обсидиана» need no model (decided 08.11): the plan the model makes, ten when no number is
     * said, a stack at most. Anything more is said goes to the model.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gettingObsidianNeedsNoModel(GameTestHelper h) {
        Object[][] said = {{"Олег, добудь обсидиан", 10}, {"накопай обсидиана", 10}, {"накопай 5 обсидиана", 5}, {"Добудь десять обсидиана!", 10},
                {"выкопай 14 блоков обсидиана", 14}, {"выкопай блок обсидиана", 1}, {"сделай обсидиан", 10}, {"набери 64 обсидиана", 64}};
        for (Object[] phrase : said) {
            var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":" + phrase[1] + "}]"));
            String route = CompanionLocalCommands.route((String) phrase[0], "Олег");
            h.assertTrue(route != null && CompanionLocalCommands.actions(route).equals(plan), "«" + phrase[0] + "» is not " + plan + " but " + route);
        }
        for (String model : List.of("построй стену из обсидиана", "добудь обсидиан и принеси", "не копай обсидиан", "добудь 100 обсидиана", "сломай обсидиан",
                "где обсидиан", "добудь обсидиан и железо", "принеси обсидиан", "добудь 0 обсидиана"))
            h.assertTrue(CompanionLocalCommands.route(model, "Олег") == null, "«" + model + "» is not left to the model: " + CompanionLocalCommands.route(model, "Олег"));
        h.succeed();
    }

    /** The lava ran out short of the order: what he got is in his bag and he says how far he got, at once, without looking again. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void whenTheLavaRunsOutHeSaysHowFarHeGot(GameTestHelper h) {
        ground(h);
        lava(h, 18, 15, 1, 1);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":3}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Накопал 1 из 3: лава рядом кончилась.") && !report.contains("Не нашёл лавы"), "Not how far he got: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && count(c, Items.WATER_BUCKET) == 1, "Not the one obsidian and his water: " + report);
        });
    }

    /**
     * A bag with no room for the bucket the plan counted (20.11): a bucket of lava lies within his hand, «добудь 1 обсидиан»
     * counts it, but begin stores nothing. He says the bag is full and the bucket stays lying where it was dropped — never
     * «Не нашёл лавы рядом» over a bucket at his feet — and no bucket is lost or doubled.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aFullBagLeavesTheBucketAtHisFeetAndSaysSo(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        for (int slot = 2; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.STONE, 64));
        lying(h, h.absolutePos(new BlockPos(10, GROUND + 1, 16)), Items.LAVA_BUCKET, 1);
        var plan = c.obtainPlan(OBSIDIAN, 1);
        h.assertTrue(!plan.refused() && plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 1))),
                "The bucket at his feet was not counted: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Сумка забита") && report.contains("ведро лавы осталось лежать"),
                    "He does not say the bag is full and the bucket stays: " + report);
            h.assertTrue(!report.contains("Не нашёл лавы"), "The lava at his feet is said to be missing: " + report);
            h.assertTrue(count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.WATER_BUCKET) == 1,
                    "His bag changed: " + c.getStoredItemsSummary() + ", " + report);
            h.assertTrue(lyingCount(h, c, Items.LAVA_BUCKET) == 1,
                    "The bucket at his feet was lost or doubled: " + lyingCount(h, c, Items.LAVA_BUCKET) + ", " + report);
        });
    }

    /**
     * One bucket of lava and one of water in his bag, nothing about to mine or cool: «добудь 2 обсидиана» promises the one block
     * his buckets can give, not two it cannot keep, and the order ends with that one — his lava spent, his water back in its bucket.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void thePlanPromisesOnlyTheObsidianHisLavaBucketCanMake(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(!plan.refused() && plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 1))),
                "The plan promises two blocks of one bucket of lava: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not the one obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(report.contains("Накопал обсидиана: 1."), "Not the one the plan promised: " + report);
            h.assertTrue(count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.BUCKET) == 1 && count(c, Items.WATER_BUCKET) == 1,
                    "His buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
        });
    }

    /**
     * Every hole beside him walled in — bedrock all round, no ground to dig and no wall for the air over it to hold lava in: he says
     * there is no site, and neither his lava nor his water is spent (no bucket emptied, no block poured).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void withNoHoleForHisLavaHeSaysSoAndSpendsNoBucket(GameTestHelper h) {
        ground(h);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) h.setBlock(new BlockPos(10 + dx, GROUND, 16 + dz), Blocks.BEDROCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Негде вылить лаву: рядом нет ямки, где она не растечётся."), "He does not say there is no site: " + report);
            h.assertTrue(count(c, Items.LAVA_BUCKET) == 1 && count(c, Items.WATER_BUCKET) == 1 && count(c, Items.BUCKET) == 0,
                    "A bucket was spent for nothing: " + c.getStoredItemsSummary() + ", " + report);
            h.assertBlockPresent(Blocks.BEDROCK, new BlockPos(9, GROUND, 16));
            h.assertBlockPresent(Blocks.BEDROCK, new BlockPos(10, GROUND, 15));
        });
    }

    /**
     * The four holes nearest him are taken — a chest in three of them and the player standing over the fourth, as he does beside his
     * companion: he digs the next cell instead, makes the block there and mines it, and none of the taken cells is dug.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aHoleTakenBesideHimIsPassedOverForAnother(GameTestHelper h) {
        ground(h);
        List<BlockPos> taken = List.of(new BlockPos(9, GROUND, 16), new BlockPos(11, GROUND, 16), new BlockPos(10, GROUND, 15));
        for (BlockPos cell : taken) h.setBlock(cell, Blocks.CHEST);
        CompanionEntity c = spawn(h);
        var owner = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        BlockPos stand = h.absolutePos(new BlockPos(10, GROUND + 1, 17));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not one obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.BUCKET) == 1 && count(c, Items.WATER_BUCKET) == 1,
                    "His buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
            for (BlockPos cell : taken) h.assertBlockPresent(Blocks.CHEST, cell);
            // The player's own hole under his feet is not dug either.
            h.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(10, GROUND, 17));
            // The next free hole was dug, the block made in it and mined out, and its grass filled back in.
            h.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(9, GROUND, 15));
        });
    }

    /**
     * Lava where the player builds is his: no water poured over it and no block made of it. The obsidian standing there is taken
     * only because nothing free-standing is about — his building is protected while other obsidian exists
     * (freeObsidianBesideTheSiteIsTakenFirst), and the blocks he set down himself are still minable when he asks for them — blocks
     * lying apart, not a wall: three and more touching are something he builds (aFrameHalfSetOnHisSiteIsLeftAndALoneBlockTaken).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void lavaOnThePlayersSiteIsLeftAloneAndItsObsidianTakenOnlyWhenNothingElseIsAbout(GameTestHelper h) {
        ground(h);
        List<BlockPos> pond = lava(h, 18, 15, 3, 1);
        CompanionEntity c = spawn(h);
        // A chest of his on the surface makes it a building of his (CompanionPlayerBuilds.HOME_SCORE); three blocks he set down apart lie in it.
        placedByPlayer(h, c, new BlockPos(16, GROUND + 1, 22), Blocks.CHEST);
        List<BlockPos> wall = List.of(new BlockPos(12, GROUND + 1, 20), new BlockPos(14, GROUND + 1, 20), new BlockPos(16, GROUND + 1, 20));
        for (BlockPos cell : wall) placedByPlayer(h, c, cell, Blocks.OBSIDIAN);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(!plan.refused() && plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))),
                "Not the obsidian standing on his site: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian from the wall: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(!report.contains("Рядом нечего копать"), "The old line over obsidian that stood there: " + report);
            // No more than the two asked for: the rest of the wall stands.
            long standing = wall.stream().filter(cell -> h.getLevel().getBlockState(h.absolutePos(cell)).is(Blocks.OBSIDIAN)).count();
            h.assertTrue(standing == 1, standing + " of the three stand: he took more or less than the two asked for");
            // The lava of the site is not his: no water over it, none of it cooled, his water still in its bucket.
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.BUCKET) == 0, "He poured or made a bucket: " + report);
            for (BlockPos cell : pond) h.assertBlockPresent(Blocks.LAVA, cell);
        });
    }

    /**
     * The player's own case (19.11): two buckets of water and two of lava in his bag, no lava anywhere about. «добудь 2 обсидиана»
     * and «сделай обсидиан» are no refusal any more: the plan asks for the obsidian (his water he has), because his own bucket of
     * lava is a source too. Before this only a lava source in the world counted, and the order was refused with «Рядом нечего
     * копать: ни обсидиана, ни лавы» while he held four buckets.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 20)
    public static void hisOwnBucketsOfLavaAreASourceForThePlan(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET, 2));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET, 2));
        var two = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(!two.refused() && two.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))),
                "Refused with two buckets of lava in his bag: " + two);
        var ten = c.obtainPlan(OBSIDIAN, 10);
        // Two buckets of lava make two blocks: the plan promises those, not ten it cannot keep (the rest is planned again after).
        h.assertTrue(!ten.refused() && ten.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))),
                "Ten promised of two buckets of lava: " + ten);
        // And the phrase reaches the same order without a model.
        String route = CompanionLocalCommands.route("Олег, добудь 2 обсидиана", "Олег");
        var asked = CompanionLocalCommands.actions(route);
        h.assertTrue(asked.size() == 1 && "obtain_item".equals(asked.getFirst().type()) && asked.getFirst().count() == 2
                && OBSIDIAN.equals(asked.getFirst().item()), "«добудь 2 обсидиана» is not the order: " + route + " -> " + asked);
        h.succeed();
    }

    /**
     * «Добудь 2 обсидиана» by the player's phrase with two buckets of water and two of lava in his bag and no lava in the world
     * (the player 19.11): he digs a hole beside him, pours his lava into it, pours his water over it, waits for the block to set,
     * scoops his water back so it does not lie over the dig, mines the obsidian and puts his buckets away — two of water back in the
     * bag and two empty ones from his lava, none lost and none doubled, and nothing left standing in the world.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void hisOwnBucketsMakeTheObsidianTheOrderAskedFor(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET, 2));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET, 2));
        String route = CompanionLocalCommands.route("Олег, добудь 2 обсидиана", "Олег");
        var plan = CompanionLocalCommands.actions(route);
        h.assertTrue(plan != null && plan.size() == 1, "The phrase is not one step: " + route + " -> " + plan);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-buckets", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 2 && count(c, Items.BUCKET) == 2 && count(c, Items.LAVA_BUCKET) == 0,
                    "His buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
            for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(new BlockPos(1, GROUND - 3, 1)), h.absolutePos(new BlockPos(30, GROUND + 4, 30)))) {
                h.assertTrue(!h.getLevel().getFluidState(pos).is(FluidTags.WATER), "His water is left at " + pos.toShortString() + ": " + report);
                h.assertTrue(!h.getLevel().getFluidState(pos).is(FluidTags.LAVA), "His lava is left at " + pos.toShortString() + ": " + report);
            }
        });
    }

    /**
     * The obsidian he stands on is his to mine, as a player digs the block under his feet: its drop comes to rest in the hole it
     * leaves and he steps down into it. The guard that kept the block under his feet out of the minable ones left it in the world
     * (the player 19.11: of several placed blocks he mined all but one). Here two blocks are set down, one of them the ground he
     * stands on: the other one he mines from where he stands, so the block under his feet is the last one — and it stayed.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void theObsidianUnderHisFeetIsMinedToo(GameTestHelper h) {
        ground(h);
        BlockPos under = new BlockPos(10, GROUND, 16);
        BlockPos beside = new BlockPos(11, GROUND + 1, 16);
        h.setBlock(under, Blocks.OBSIDIAN);
        h.setBlock(beside, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))), "Not mined where it lies: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian, the block he stood on left: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertBlockPresent(Blocks.AIR, under);
            h.assertBlockPresent(Blocks.AIR, beside);
        });
    }

    /**
     * His water gone after the lava is poured — a fall took the bucket, or the player did mid-make: the lava of the pour is taken back
     * into the empty bucket the pour left him, so no bucket is spent for a block that was not made and nothing is left standing in the
     * hole. Before this the guard ended the order with his lava bucket spent and the lava standing in the hole.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void theLavaOfAPourWithNoWaterLeftGoesBackIntoHisBucket(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        BlockPos[] poured = new BlockPos[1];
        h.succeedWhen(() -> {
            // The lava of the pour is in the hole now: the water he would pour over it is taken away, as a fall or the player would.
            if (poured[0] == null) poured[0] = pouredLava(h, c);
            if (poured[0] != null) takeWaterAway(c);
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.LAVA_BUCKET) == 1 && count(c, Items.BUCKET) == 0 && count(c, Items.WATER_BUCKET) == 0,
                    "His lava bucket was spent and left: " + c.getStoredItemsSummary() + ", " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 0, "A block was made of nothing: " + report);
            h.assertTrue(poured[0] != null, "His lava was never poured: " + report);
            h.assertTrue(h.getLevel().getFluidState(poured[0]).isEmpty(), "His lava is left standing at " + poured[0].toShortString() + ": " + report);
        });
    }

    /**
     * The whole make driven into its take-back: the game sets lava under water at once, so the test lifts the water off the hole while
     * the block waits and puts it back with the lava on the last tick of the wait, and the wait runs out. His water is scooped into its
     * own bucket FIRST (over the emptied hole it would run into it), the lava of the pour taken back after, and no bucket is doubled or
     * lost — before this the take-back was tested by calling it directly, never through the make that does it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void theTakeBackScoopsHisWaterFirstThenTheLavaOfThePour(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        var future = CompanionPlanRunner.run(CompanionPlanCodec.parsePlan(JsonParser.parseString(
                        "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]")),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-takeback", () -> false));
        BlockPos[] hole = new BlockPos[1];
        long[] pouredAt = {-1};
        boolean[] restored = {false};
        h.succeedWhen(() -> {
            if (hole[0] == null) hole[0] = pouredLava(h, c);
            if (hole[0] != null) {
                BlockPos above = hole[0].above();
                if (pouredAt[0] < 0 && h.getLevel().getFluidState(above).isSourceOfType(Fluids.WATER)) pouredAt[0] = h.getLevel().getGameTime();
                long waited = h.getLevel().getGameTime() - pouredAt[0];
                // The game sets lava under water at once: while the block waits, the water is lifted off the hole and stone stands in it
                // (a lava source there for the whole wait would burn him), so the make waits its sixty ticks out.
                if (pouredAt[0] >= 0 && waited < 60) {
                    if (!h.getLevel().getFluidState(above).isEmpty()) h.getLevel().setBlock(above, Blocks.AIR.defaultBlockState(), 2);
                    if (!h.getLevel().getBlockState(hole[0]).is(Blocks.STONE)) h.getLevel().setBlock(hole[0], Blocks.STONE.defaultBlockState(), 2);
                }
                // The lava goes back on the last tick but one of the wait (no water over it yet, so it stays lava), his water on the last:
                // the take-back of the make then finds both, and the lava is not under water long enough to set.
                if (pouredAt[0] >= 0 && waited >= 59 && !restored[0] && !h.getLevel().getBlockState(hole[0]).is(Blocks.LAVA))
                    h.getLevel().setBlock(hole[0], Blocks.LAVA.defaultBlockState(), 2);
                if (pouredAt[0] >= 0 && waited >= 60 && !restored[0]) {
                    h.getLevel().setBlock(above, Blocks.WATER.defaultBlockState(), 2);
                    restored[0] = true;
                }
            }
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Лава не застыла — обсидиан не вышел."), "He does not say the block did not set: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 0, "A block was made after all: " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.LAVA_BUCKET) == 1 && count(c, Items.BUCKET) == 0,
                    "His buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
            h.assertTrue(hole[0] != null, "His lava was never poured: " + report);
            h.assertTrue(h.getLevel().getFluidState(hole[0]).isEmpty() && !h.getLevel().getBlockState(hole[0]).is(Blocks.OBSIDIAN),
                    "Something is left standing at " + hole[0].toShortString() + ": " + report);
            assertWaterScoopedBeforeTheLava(h, c);
        });
    }

    /**
     * A hole his lava would stay in with no way to it — walled in with not one cell he can stand at: he passes it over and digs the next
     * one instead of digging at it again every tick until the watchdog. Before this the nearest hole was taken again each tick and the
     * order stood there a minute and a half.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aHoleHeCannotWalkToIsPassedOverForOneHeCan(GameTestHelper h) {
        ground(h);
        // Bedrock is no hole his lava stays in: the two dirt cells below are the only ground he can dig here.
        for (int x = 1; x <= 30; x++) for (int z = 1; z <= 30; z++) h.setBlock(new BlockPos(x, GROUND, z), Blocks.BEDROCK);
        BlockPos walled = new BlockPos(12, GROUND, 16);
        h.setBlock(walled, Blocks.DIRT);
        for (BlockPos cell : List.of(new BlockPos(11, GROUND + 1, 16), new BlockPos(13, GROUND + 1, 16),
                new BlockPos(12, GROUND + 1, 15), new BlockPos(12, GROUND + 1, 17), new BlockPos(12, GROUND + 2, 16)))
            h.setBlock(cell, Blocks.BEDROCK);
        BlockPos reachable = new BlockPos(10, GROUND, 13);
        h.setBlock(reachable, Blocks.DIRT);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not one obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            // The hole with no way to it is left as it was; the next one is dug, the block made in it and mined out, and its dirt
            // goes back into the hole so the ground is as it was.
            h.assertBlockPresent(Blocks.DIRT, walled);
            h.assertBlockPresent(Blocks.DIRT, reachable);
        });
    }

    /**
     * The player's own case (20.11, world test3): four buckets dropped on the ground at his feet, two of water and two of lava, no
     * lava and no water anywhere about. «добудь 2 обсидиана» is no refusal — a bucket lying within his hand is a source as much as
     * one in his bag. He takes it up when the work starts, and the buckets come out of the make as they went in: his water back in
     * its own buckets and the empty ones of his lava, none lost and none doubled, nothing left lying.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void bucketsLyingOnTheGroundAreASourceForTheOrder(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos feet = h.absolutePos(new BlockPos(10, GROUND + 1, 16));
        lying(h, feet, Items.LAVA_BUCKET, 2);
        lying(h, feet.offset(1, 0, 0), Items.WATER_BUCKET, 2);
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(!plan.refused() && plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))),
                "Refused over four buckets lying at his feet: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 2 && count(c, Items.BUCKET) == 2 && count(c, Items.LAVA_BUCKET) == 0,
                    "His buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
            for (var drop : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(8)))
                h.assertTrue(!drop.getItem().is(Items.LAVA_BUCKET) && !drop.getItem().is(Items.WATER_BUCKET) && !drop.getItem().is(Items.BUCKET),
                        "A bucket is left on the ground: " + drop.getItem());
        });
    }

    /**
     * The player's own case (20.11, world test3): «добудь 2 обсидиана» with two buckets of water and two of lava lying at his feet and
     * no lava about. The hole is dug beside him, his lava goes into it while he stands beside it and his water is poured from beside
     * it too: he never steps into the lava cell, so his health stays full for the whole make («зачем то туда сам залезает в неё
     * теряя здоровье, когда нужно просто рядом воду разлить не заходя в лаву»).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void heNeverStepsIntoHisLavaHoleAndTakesNoDamage(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos feet = h.absolutePos(new BlockPos(10, GROUND + 1, 16));
        lying(h, feet, Items.LAVA_BUCKET, 2);
        lying(h, feet.offset(1, 0, 0), Items.WATER_BUCKET, 2);
        float full = c.getHealth();
        int grassBefore = 0;
        for (int x = 1; x <= 30; x++) for (int z = 1; z <= 30; z++)
            if (h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, GROUND, z))).is(Blocks.GRASS_BLOCK)) grassBefore++;
        final int grassWhole = grassBefore;
        String route = CompanionLocalCommands.route("Олег, добудь 2 обсидиана", "Олег");
        var plan = CompanionLocalCommands.actions(route);
        h.assertTrue(plan != null && plan.size() == 1, "The phrase is not one step: " + route + " -> " + plan);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-beside", () -> false));
        h.succeedWhen(() -> {
            BlockPos at = c.blockPosition();
            h.assertTrue(!h.getLevel().getFluidState(at).is(FluidTags.LAVA),
                    "He stands in the lava at " + at.toShortString() + ", health " + c.getHealth() + " of " + full + ": " + c.getLastTaskResult());
            h.assertTrue(c.getHealth() == full, "He lost health in the make: " + c.getHealth() + " of " + full + " at " + at.toShortString());
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 2 && count(c, Items.BUCKET) == 2 && count(c, Items.LAVA_BUCKET) == 0,
                    "His buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
            for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(new BlockPos(1, GROUND - 3, 1)), h.absolutePos(new BlockPos(30, GROUND + 4, 30)))) {
                h.assertTrue(!h.getLevel().getFluidState(pos).is(FluidTags.WATER), "His water is left at " + pos.toShortString() + ": " + report);
                h.assertTrue(!h.getLevel().getFluidState(pos).is(FluidTags.LAVA), "His lava is left at " + pos.toShortString() + ": " + report);
            }
            // The holes he dug are filled back with their grass: the drop of the dig was not burnt in the poured lava, it went back
            // into the ground, and nothing of it lies about.
            int grassAfter = 0;
            for (int x = 1; x <= 30; x++) for (int z = 1; z <= 30; z++)
                if (h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, GROUND, z))).is(Blocks.GRASS_BLOCK)) grassAfter++;
            h.assertTrue(grassAfter == grassWhole,
                    "A hole is left in the ground: " + grassAfter + " grass of " + grassWhole + ", " + c.getStoredItemsSummary() + ", " + report);
            for (var drop : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(30)))
                h.assertTrue(!drop.getItem().is(Items.DIRT) && !drop.getItem().is(Items.GRASS_BLOCK),
                        "The hole's drop is left lying at " + drop.blockPosition().toShortString() + ": " + report);
        });
    }

    /**
     * The player's own case (20.11, world test3, the live test after #208): two buckets of water and two of lava lying two to four
     * blocks away on open ground, no lava in the world. «добудь 2 обсидиана» by the phrase is no refusal and no «не дотянуться»:
     * the buckets lie within his sight and a way leads to them, so he walks to each, takes it up, and makes the two blocks of them.
     * The buckets come out of the make as they went in — his water back in its bucket, the empty ones of the two lava buckets he
     * poured out — none lost, none doubled; the second water bucket is not needed (one serves both blocks) and lies where the player
     * dropped it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void bucketsLyingWithinSightAreWalkedToAndTakenUp(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos start = c.blockPosition();
        lying(h, h.absolutePos(new BlockPos(12, GROUND + 1, 16)), Items.LAVA_BUCKET, 1);
        lying(h, h.absolutePos(new BlockPos(12, GROUND + 1, 18)), Items.WATER_BUCKET, 1);
        lying(h, h.absolutePos(new BlockPos(13, GROUND + 1, 14)), Items.LAVA_BUCKET, 1);
        lying(h, h.absolutePos(new BlockPos(14, GROUND + 1, 16)), Items.WATER_BUCKET, 1);
        String route = CompanionLocalCommands.route("Олег, добудь 2 обсидиана", "Олег");
        var plan = CompanionLocalCommands.actions(route);
        h.assertTrue(plan != null && plan.size() == 1, "The phrase is not one step: " + route + " -> " + plan);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-fetch", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            // Both buckets of lava he fetched were poured out: two empty ones are his, none of it is lost or doubled.
            h.assertTrue(count(c, Items.BUCKET) == 2 && count(c, Items.LAVA_BUCKET) == 0,
                    "His lava buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
            // One bucket of water is enough for both blocks (it is scooped back each time): the other lies where the player dropped it,
            // so his water is still two buckets, one in the bag and one on the ground — none lost, none doubled.
            int lyingWater = 0;
            for (var drop : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(16))) {
                if (drop.getItem().is(Items.WATER_BUCKET)) lyingWater += drop.getItem().getCount();
                h.assertTrue(!drop.getItem().is(Items.LAVA_BUCKET) && !drop.getItem().is(Items.BUCKET),
                        "A lava or empty bucket is left on the ground: " + drop.getItem());
            }
            h.assertTrue(count(c, Items.WATER_BUCKET) + lyingWater == 2,
                    "His water is lost or doubled: " + c.getStoredItemsSummary() + ", lying " + lyingWater + ", " + report);
            h.assertTrue(c.blockPosition().distSqr(start) >= 4, "He did not walk to the buckets: " + c.blockPosition());
        });
    }

    /**
     * A stack of two buckets lying at his feet with room for one (20.11): the plan counts both, and taking them up leaves the rest
     * of the stack lying where it was dropped, out of the loose pickup's way until the order is done — the bag and the ground
     * together hold both buckets, none lost and none doubled. The bag has no free cell after the take, so the dirt of the hole and
     * the mined obsidian go into the part stacks he already carries.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void theRestOfAStackAtHisFeetStaysLyingAndIsNotLost(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.OBSIDIAN));
        c.setItem(3, new ItemStack(Items.DIRT));
        for (int slot = 4; slot < 26; slot++) c.setItem(slot, new ItemStack(Items.STONE, 64));
        BlockPos stack = h.absolutePos(new BlockPos(11, GROUND + 1, 16));
        lying(h, stack, Items.LAVA_BUCKET, 2).setPickUpDelay(20 * 300);
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(!plan.refused() && plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 1))),
                "The stack was not counted: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not the one more obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            // The stack was two lava buckets: one poured out (its empty bucket is his) and one still lying, so the bag and the ground
            // hold two buckets together, never one lost or doubled.
            int buckets = count(c, Items.LAVA_BUCKET) + count(c, Items.BUCKET) + lyingCount(h, c, Items.LAVA_BUCKET) + lyingCount(h, c, Items.BUCKET);
            h.assertTrue(buckets == 2, "His buckets are lost or doubled: " + buckets + ", " + c.getStoredItemsSummary()
                    + ", lying " + lyingCount(h, c, Items.LAVA_BUCKET) + ", " + report);
            h.assertTrue(lyingCount(h, c, Items.LAVA_BUCKET) == 1, "The rest of the stack is not lying where it was: "
                    + lyingCount(h, c, Items.LAVA_BUCKET) + ", " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1, "His water is lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
        });
    }

    /**
     * The same buckets, but no way to them: two and two lying behind a wall across the arena, within his search radius. No walk
     * leads there (a climb of three is no step of his, and the bedrock border closes both ends of the wall), so the order says the
     * buckets are there and he cannot walk to them — «Вижу рядом вёдра лавы и воды, но не дотянуться» — never «Рядом нечего
     * копать: ни обсидиана, ни лавы», and no bucket is made or spent for nothing.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void bucketsWithNoWayToThemAreNamedInsteadOfNothingToDig(GameTestHelper h) {
        ground(h);
        // A wall three high across the whole arena: the bedrock border closes both its ends, so there is no way round.
        for (int z = 1; z <= 30; z++) for (int y = GROUND + 1; y <= GROUND + 3; y++) h.setBlock(new BlockPos(14, y, z), Blocks.STONE);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos far = h.absolutePos(new BlockPos(18, GROUND + 1, 16));
        lying(h, far, Items.LAVA_BUCKET, 2);
        lying(h, far.offset(0, 0, 2), Items.WATER_BUCKET, 2);
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(!plan.refused(), "Refused before he walked up to the wall: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("не дотянуться") && !report.contains("Рядом нечего копать"), "He does not name the buckets: " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 0 && count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.BUCKET) == 0,
                    "A bucket was made or spent for nothing: " + c.getStoredItemsSummary() + ", " + report);
        });
    }

    /**
     * The same buckets at the bottom of a pit four blocks deep and one wide: his walk takes a drop of three blocks at most, so no
     * way leads down to them. The order says «не дотянуться» — the buckets are there, his hand is not — and nothing is spent.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void bucketsAtTheBottomOfAPitAreNamedInsteadOfNothingToDig(GameTestHelper h) {
        ground(h);
        for (int y = GROUND; y >= GROUND - 3; y--) {
            h.setBlock(new BlockPos(18, y, 16), Blocks.AIR);
            h.setBlock(new BlockPos(18, y, 18), Blocks.AIR);
        }
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        lying(h, h.absolutePos(new BlockPos(18, GROUND - 3, 16)), Items.LAVA_BUCKET, 2);
        lying(h, h.absolutePos(new BlockPos(18, GROUND - 3, 18)), Items.WATER_BUCKET, 2);
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(!plan.refused(), "Refused before he walked up to the pit: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("не дотянуться") && !report.contains("Рядом нечего копать"), "He does not name the buckets: " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 0 && count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.BUCKET) == 0,
                    "A bucket was made or spent for nothing: " + c.getStoredItemsSummary() + ", " + report);
        });
    }

    /**
     * The player's own obsidian set down in his base (20.11, world test3): two blocks standing inside a building of his — a chest on
     * the surface makes the place a site (CompanionPlayerBuilds.HOME_SCORE) — and no free-standing obsidian about. The plan mines
     * them and both come into his bag. Before this every block on a site was left alone, so the obsidian the player had placed
     * himself was invisible to him.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void obsidianOnThePlayersSiteIsMinedWhenNoOtherIsAbout(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        placedByPlayer(h, c, new BlockPos(14, GROUND + 1, 20), Blocks.CHEST);
        List<BlockPos> site = List.of(new BlockPos(12, GROUND + 1, 16), new BlockPos(13, GROUND + 1, 16));
        for (BlockPos cell : site) placedByPlayer(h, c, cell, Blocks.OBSIDIAN);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(!plan.refused() && plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))),
                "Not the obsidian standing on his site: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian from his site: " + count(c, Items.OBSIDIAN) + ", " + report);
            for (BlockPos cell : site) h.assertBlockPresent(Blocks.AIR, cell);
        });
    }

    /**
     * A portal's frame the player is still setting on his site, nine of its ten blocks, is no obsidian lying about: it is not yet a
     * whole frame, so the frame check does not see it, and the site's blocks were his to take — he took two of the frame (review in
     * game 24.11). Three blocks and more touching, corners too, are something being built; a lone block of the site farther off is
     * taken instead, and the frame stands.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800, batch = CompanionPortalBuildTests.PORTALS)
    public static void aFrameHalfSetOnHisSiteIsLeftAndALoneBlockTaken(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        placedByPlayer(h, c, new BlockPos(14, GROUND + 1, 20), Blocks.CHEST);
        List<BlockPos> frame = new ArrayList<>();
        for (int x = 9; x <= 12; x++) for (int y = GROUND + 1; y <= GROUND + 5; y++) {
            boolean side = x == 9 || x == 12, edge = y == GROUND + 1 || y == GROUND + 5;
            // No corners, and the last block of the top not set yet.
            if (side == edge || x == 11 && y == GROUND + 5) continue;
            placedByPlayer(h, c, new BlockPos(x, y, 21), Blocks.OBSIDIAN);
            frame.add(new BlockPos(x, y, 21));
        }
        BlockPos lone = new BlockPos(20, GROUND + 1, 8);
        placedByPlayer(h, c, lone, Blocks.OBSIDIAN);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            for (BlockPos cell : frame) h.assertBlockPresent(Blocks.OBSIDIAN, cell);
            h.assertTrue(frame.size() == 9, "Not a frame of nine: " + frame.size());
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not the lone block: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertBlockPresent(Blocks.AIR, lone);
        });
    }

    /**
     * Free-standing obsidian beside the site is taken first: the player's building is protected while other obsidian exists, and the
     * two blocks he set down inside it are left standing. The site here reaches sixteen blocks around its chest, so the free blocks
     * lie outside it and still within his search radius.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void freeObsidianBesideTheSiteIsTakenFirst(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        placedByPlayer(h, c, new BlockPos(10, GROUND + 1, 30), Blocks.CHEST);
        List<BlockPos> site = List.of(new BlockPos(10, GROUND + 1, 29), new BlockPos(11, GROUND + 1, 29));
        for (BlockPos cell : site) placedByPlayer(h, c, cell, Blocks.OBSIDIAN);
        List<BlockPos> free = List.of(new BlockPos(10, GROUND + 1, 2), new BlockPos(11, GROUND + 1, 2));
        for (BlockPos cell : free) h.setBlock(cell, Blocks.OBSIDIAN);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(!plan.refused() && plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))),
                "Not the free obsidian: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            for (BlockPos cell : free) h.assertBlockPresent(Blocks.AIR, cell);
            for (BlockPos cell : site) h.assertBlockPresent(Blocks.OBSIDIAN, cell);
        });
    }

    /**
     * The player's own case (20.11, world test3, one lava bucket): «сделай 2 обсидиана» with one bucket of lava and one of water
     * lying on the ground beside him in his own yard — a chest set down there makes it a site of the player's — and no lava in the
     * world. One bucket of lava makes one block, so the plan promises the one and the order makes it — «Накопал обсидиана: 1.»
     * — and the buckets lie there, counted as the source they are. Before this he picked both buckets up and said «Негде вылить
     * лаву: рядом нет ямки, где она не растечётся.» over open ground, and made nothing: the site of his own yard was taken for no
     * hole at all. The rest of the order is not planned again and named as missing: the promise was the one block, and it was
     * made (PATH7; the older wording said «Обсидиана у меня 1, а лавы рядом и в вёдрах больше нет»).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void oneLavaBucketOnTheGroundMakesTheOneBlockItCanAndSaysSo(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        placedByPlayer(h, c, new BlockPos(14, GROUND + 1, 20), Blocks.CHEST);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos feet = h.absolutePos(new BlockPos(10, GROUND + 1, 16));
        lying(h, feet.offset(2, 0, 0), Items.LAVA_BUCKET, 1);
        lying(h, feet.offset(0, 0, 2), Items.WATER_BUCKET, 1);
        String route = CompanionLocalCommands.route("Олег, сделай 2 обсидиана", "Олег");
        var plan = CompanionLocalCommands.actions(route);
        h.assertTrue(plan != null && plan.size() == 1, "«сделай 2 обсидиана» is not one step: " + route + " -> " + plan);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-one-lava", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not the one obsidian his lava can make: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(!report.contains("Негде вылить лаву"), "He says there is no hole over open ground: " + report);
            h.assertTrue(!report.contains("не дотянуться") && !report.contains("Не нашёл лавы"), "He says a bucket beside him is missing: " + report);
            h.assertTrue(report.contains("Накопал обсидиана: 1."), "He does not say what he made: " + report);
            h.assertTrue(!report.contains("Рядом нечего копать") && !report.contains("Обсидиана у меня 1, а лавы рядом и в вёдрах больше нет"),
                    "The old line over the block his buckets made: " + report);
            h.assertTrue(count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.BUCKET) == 1 && count(c, Items.WATER_BUCKET) == 1,
                    "His buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
        });
    }

    /**
     * The same scene after the player drops a second bucket of lava, as in world test3: the repeated «сделай 2 обсидиана» takes
     * the new bucket, makes the second block in a hole again and the bag holds both. The first order made the one block its lava
     * gave and said what was missing; the second one is what the player brings for it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void afterASecondLavaBucketAppearsTheOrderMakesBoth(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        placedByPlayer(h, c, new BlockPos(14, GROUND + 1, 20), Blocks.CHEST);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos feet = h.absolutePos(new BlockPos(10, GROUND + 1, 16));
        lying(h, feet.offset(2, 0, 0), Items.LAVA_BUCKET, 1);
        lying(h, feet.offset(0, 0, 2), Items.WATER_BUCKET, 1);
        String route = CompanionLocalCommands.route("Олег, сделай 2 обсидиана", "Олег");
        var plan = CompanionLocalCommands.actions(route);
        h.assertTrue(plan != null && plan.size() == 1, "«сделай 2 обсидиана» is not one step: " + route + " -> " + plan);
        var first = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-one-lava", () -> false));
        var second = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>>();
        h.succeedWhen(() -> {
            h.assertTrue(first.isDone() && !first.isCompletedExceptionally(), "First plan still running: " + c.getLastTaskResult());
            if (second.get() == null) {
                h.assertTrue(count(c, Items.OBSIDIAN) == 1, "The first order did not make the one block its lava gives: "
                        + count(c, Items.OBSIDIAN) + ", " + first.join().report());
                lying(h, feet.offset(-2, 0, 0), Items.LAVA_BUCKET, 1);
                second.set(CompanionPlanRunner.run(CompanionLocalCommands.actions(route),
                        new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-second-lava", () -> false)));
            }
            h.assertTrue(second.get().isDone() && !second.get().isCompletedExceptionally(), "Second plan still running: " + c.getLastTaskResult());
            String report = second.get().join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian after the second bucket of lava: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(!report.contains("Негде вылить лаву"), "He says there is no hole over open ground: " + report);
            h.assertTrue(count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.BUCKET) == 2 && count(c, Items.WATER_BUCKET) == 1,
                    "His buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
        });
    }

    /**
     * The hole the first block was made in is the hole the second one is made in: the arena has one cell of ground he can dig, the
     * rest bedrock. The block is mined out of the hole first, the hole is filled back with its dirt, and the next make digs that
     * same cell again — never refused because obsidian stood in it a moment ago. Two buckets of lava make two blocks in that one
     * hole, and the ground is whole again at the end.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void theHoleOfTheFirstBlockIsUsedForTheSecond(GameTestHelper h) {
        ground(h);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) h.setBlock(new BlockPos(10 + dx, GROUND, 16 + dz), Blocks.BEDROCK);
        BlockPos hole = new BlockPos(12, GROUND, 16);
        h.setBlock(hole, Blocks.DIRT);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET, 2));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian out of the one hole: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(!report.contains("Негде вылить лаву"), "He says there is no hole with the one he used standing empty: " + report);
            h.assertBlockPresent(Blocks.DIRT, hole);
        });
    }

    /**
     * The same yard of the player's site with the buckets in his bag instead of lying: the site is the whole yard round his home,
     * so every cell beside him lies on it, and that was taken for no hole at all — «Негде вылить лаву» over the open ground he
     * stands on. The hole is dug there as anywhere else, and one bucket of lava makes the one block.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void thePlayersSiteIsNoReasonToSayThereIsNoHole(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        placedByPlayer(h, c, new BlockPos(14, GROUND + 1, 20), Blocks.CHEST);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not one obsidian in the yard of his site: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(!report.contains("Негде вылить лаву"), "He says there is no hole in the yard of the player's site: " + report);
            h.assertTrue(report.contains("Накопал обсидиана: 1."), "He does not say the one block is made: " + report);
            h.assertBlockPresent(Blocks.CHEST, new BlockPos(14, GROUND + 1, 20));
        });
    }

    /**
     * «Добудь 1 обсидиан» inside the player's own house: a chest makes the yard his site, the floor is planks, and the one block
     * comes of his own buckets. The hole he digs in that floor is filled back with the very plank the dig gave him once the
     * obsidian is mined and his water is scooped, so the floor is whole again and no plank is gained or lost. The judge's nit
     * on #208: a bare floor cell of the player's was fair game for the lava hole, and the pit stayed after the make.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void theHoleInThePlayersFloorIsFilledBackAfterTheMake(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        plankRoom(h, c, new BlockPos(12, GROUND + 1, 12));
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        int planksBefore = count(c, Items.OAK_PLANKS);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not one obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            for (int x = 4; x <= 16; x++) for (int z = 10; z <= 22; z++)
                h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(x, GROUND, z));
            h.assertTrue(count(c, Items.OAK_PLANKS) == planksBefore,
                    "A plank was gained or lost: " + count(c, Items.OAK_PLANKS) + " of " + planksBefore + ", " + c.getStoredItemsSummary() + ", " + report);
        });
    }

    /**
     * The merge's fix commit: the drop of the dig lies in the hole the lava is poured into, and poured over it the lava burns
     * it — and that drop is what fills the hole back. The lava must wait while the drop is still there, so the pickup takes it
     * first; the order then makes its obsidian and the floor is whole again. The journal proves the order of the two events:
     * the drop in his bag before the lava goes in.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void theLavaWaitsForTheDropOfTheDigBeforeItIsPoured(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        plankRoom(h, c, new BlockPos(12, GROUND + 1, 12));
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        int planksBefore = count(c, Items.OAK_PLANKS);
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]"));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(),
                "gametest-obsidian-lava-wait", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not one obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            for (int x = 4; x <= 16; x++) for (int z = 10; z <= 22; z++)
                h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(x, GROUND, z));
            h.assertTrue(count(c, Items.OAK_PLANKS) == planksBefore,
                    "A plank was gained or lost: " + count(c, Items.OAK_PLANKS) + " of " + planksBefore + ", " + report);
            int picked = -1, poured = -1;
            for (var row : journalRows(c)) {
                String event = row.get("event").getAsString();
                if (event.equals("item_picked_up") && row.get("item").getAsString().equals("minecraft:oak_planks"))
                    picked = row.get("sequence").getAsInt();
                if (event.equals("obsidian_lava_poured")) poured = row.get("sequence").getAsInt();
            }
            h.assertTrue(picked >= 0, "The drop of the dig was never picked up: " + report);
            h.assertTrue(poured >= 0, "The lava was never poured: " + report);
            h.assertTrue(picked < poured, "The lava was poured before the drop of the dig was taken out of the hole: picked at "
                    + picked + ", poured at " + poured + ", " + report);
        });
    }

    /**
     * The hole is filled back only once the drop in it is taken: a drop the pickup never gets (aimed at the player, no room in
     * the bag) must not hold the order open for ever. A netherite scrap with the game's never-expiring pickup delay lies in the
     * hole cell; the order must still end within the bound and leave the hole as it is — no block buried under the drop.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aDropLeftInTheHoleDoesNotHoldTheOrderOpenForEver(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        plankRoom(h, c, new BlockPos(12, GROUND + 1, 12));
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        int planksBefore = count(c, Items.OAK_PLANKS);
        BlockPos[] hole = {null};
        long[] stuckAt = {0};
        h.onEachTick(() -> {
            if (hole[0] != null) return;
            for (var drop : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(4)))
                if (drop.isAlive() && drop.getItem().is(Items.OBSIDIAN)) {
                    hole[0] = drop.blockPosition().immutable();
                    var stuck = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), drop.getX(), drop.getY(), drop.getZ(),
                            new ItemStack(Items.NETHERITE_SCRAP));
                    // 32767 is the game's own "never picked up" delay.
                    stuck.setPickUpDelay(32767);
                    h.getLevel().addFreshEntity(stuck);
                    stuckAt[0] = h.getLevel().getGameTime();
                    return;
                }
        });
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(
                "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]"));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(),
                "gametest-obsidian-hole-bound", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(),
                    "A drop in the hole held the order open: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not one obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(hole[0] != null, "The make never mined an obsidian to leave a hole: " + report);
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(hole[0])).isAir(),
                    "The hole was filled over the drop: " + h.getLevel().getBlockState(h.absolutePos(hole[0])) + ", " + report);
            long waited = h.getLevel().getGameTime() - stuckAt[0];
            h.assertTrue(waited <= 260, "The order waited " + waited + " ticks for the drop in the hole, past the bound (stuck at "
                    + stuckAt[0] + ", now " + h.getLevel().getGameTime() + "): " + report);
            h.assertTrue(count(c, Items.OAK_PLANKS) == planksBefore + 1,
                    "The plank the dig gave was not kept when the hole stayed: " + count(c, Items.OAK_PLANKS) + " of "
                            + (planksBefore + 1) + ", " + report);
        });
    }

    /**
     * The same house: no plank of the floor ignites or burns while the block is made. Lava in a plank hole can set the planks
     * around it alight for the tick it stands there; his water must go over it before that tick ends. No fire is allowed anywhere
     * in the room at any tick, and after the make every floor cell must be the very block it was before.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void noPlankOfTheFloorBurnsWhileTheBlockIsMade(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        plankRoom(h, c, new BlockPos(12, GROUND + 1, 12));
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        Map<BlockPos, BlockState> before = new HashMap<>();
        for (int x = 4; x <= 16; x++) for (int z = 10; z <= 22; z++) {
            BlockPos floor = new BlockPos(x, GROUND, z);
            before.put(floor, h.getLevel().getBlockState(h.absolutePos(floor)));
        }
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":1}]");
        h.succeedWhen(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(4, GROUND, 10), new BlockPos(16, GROUND + 4, 22)))
                h.assertTrue(!h.getLevel().getBlockState(h.absolutePos(pos)).is(Blocks.FIRE),
                        "A plank is on fire at " + pos.toShortString() + ": " + c.getLastTaskResult());
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not one obsidian: " + count(c, Items.OBSIDIAN) + ", " + report);
            for (Map.Entry<BlockPos, BlockState> floor : before.entrySet())
                h.assertTrue(h.getLevel().getBlockState(h.absolutePos(floor.getKey())).equals(floor.getValue()),
                        "The floor at " + floor.getKey().toShortString() + " is not what it was: "
                                + h.getLevel().getBlockState(h.absolutePos(floor.getKey())) + ", was " + floor.getValue());
        });
    }

    /**
     * The live bug of #105 (PATH7): an obsidian ten blocks above his feet, nothing under it, so no stand cell reaches it.
     * With a diamond pickaxe and soil in his bag, «Олег, добудь обсидиан»: he builds a column of his own soil up to the block,
     * mines it from the top, and comes down with every block of the column back in the bag — the ground left as it was.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 5000)
    public static void obsidianTenAboveHisFeetIsMinedFromAColumnOfHisOwnSoil(GameTestHelper h) {
        ground(h);
        BlockPos obsidian = new BlockPos(12, GROUND + 11, 16);
        h.setBlock(obsidian, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        double groundY = c.getY();
        int[] supports = {0};
        double[] highest = {0};
        h.onEachTick(() -> { supports[0] = Math.max(supports[0], c.getTemporarySupportCount()); highest[0] = Math.max(highest[0], c.getY()); });
        String command = CompanionLocalCommands.route("Олег, добудь обсидиан", "Олег");
        h.assertTrue(command != null, "«Олег, добудь обсидиан» went to the model");
        var plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-way", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not the obsidian above him: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertBlockPresent(Blocks.AIR, obsidian);
            h.assertTrue(supports[0] >= 7, "He never built up to it: supports " + supports[0] + ", highest y " + highest[0] + ", " + report);
            h.assertTrue(highest[0] > groundY + 5, "He never stood above the ground: highest y " + highest[0] + ", " + report);
            h.assertTrue(c.onGround() && Math.abs(c.getY() - groundY) < 0.6, "He did not come back down: y " + c.getY() + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column is still standing: " + c.getTemporarySupportCount());
            h.assertTrue(dirtAboveGround(h) == 0, "A block of his column was left standing");
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of his column was lost: " + count(c, Items.DIRT));
            h.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(12, GROUND, 16));
        });
    }
    /**
     * The live report of PATH7: «добудь обсидиан» without his name and «олег добудь обсидиан» are the same order of ten
     * (a portal frame), and one obsidian hangs ten blocks above each of two identical scenes. The phrasing must not decide
     * the outcome: both promise only what is there — one block — mine it, come down, take the drop and end with
     * «Накопал обсидиана: 1.» instead of refusing for the nine blocks that are not.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void thePhraseWithAndWithoutHisNameGivesTheSameOutcome(GameTestHelper h) {
        ground(h);
        BlockPos bareBlock = new BlockPos(8, GROUND + 11, 8);
        BlockPos namedBlock = new BlockPos(24, GROUND + 11, 24);
        h.setBlock(bareBlock, Blocks.OBSIDIAN);
        h.setBlock(namedBlock, Blocks.OBSIDIAN);
        CompanionEntity bare = spawnAt(h, 6, 8);
        CompanionEntity named = spawnAt(h, 22, 24);
        for (CompanionEntity c : List.of(bare, named)) {
            c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            c.setItem(1, new ItemStack(Items.DIRT, 20));
        }
        String bareCommand = CompanionLocalCommands.route("добудь обсидиан", "Олег");
        String namedCommand = CompanionLocalCommands.route("Олег, добудь обсидиан", "Олег");
        h.assertTrue(bareCommand != null && namedCommand != null, "A phrase went to the model: " + bareCommand + " / " + namedCommand);
        h.assertTrue(bareCommand.equals(namedCommand), "The phrasing decides the command: " + bareCommand + " / " + namedCommand);
        var bareFuture = CompanionPlanRunner.run(CompanionLocalCommands.actions(bareCommand),
                new CompanionServerPlanPort(h.getLevel().getServer(), bare.getOwnerId(), "gametest-obsidian-bare", () -> false));
        var namedFuture = CompanionPlanRunner.run(CompanionLocalCommands.actions(namedCommand),
                new CompanionServerPlanPort(h.getLevel().getServer(), named.getOwnerId(), "gametest-obsidian-named", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(bareFuture.isDone() && namedFuture.isDone() && !bareFuture.isCompletedExceptionally() && !namedFuture.isCompletedExceptionally(),
                    "A plan still running: " + bare.getLastTaskResult() + " / " + named.getLastTaskResult());
            String bareReport = bareFuture.join().report(), namedReport = namedFuture.join().report();
            h.assertTrue(bareReport.equals(namedReport), "The phrasing changed the outcome:\n" + bareReport + "\n---\n" + namedReport);
            h.assertTrue(bareReport.contains("Накопал обсидиана: 1"), "Not the one block that was there: " + bareReport);
            h.assertTrue(!bareReport.contains("Не нашёл лавы") && !bareReport.contains("Полторы минуты"), "A wrong end: " + bareReport);
            h.assertTrue(count(bare, Items.OBSIDIAN) == 1 && count(named, Items.OBSIDIAN) == 1,
                    "The drop is not in the bag: " + count(bare, Items.OBSIDIAN) + " / " + count(named, Items.OBSIDIAN) + ", " + bareReport);
            h.assertBlockPresent(Blocks.AIR, bareBlock);
            h.assertBlockPresent(Blocks.AIR, namedBlock);
            h.assertTrue(bare.getTemporarySupportCount() == 0 && named.getTemporarySupportCount() == 0,
                    "A column stands: " + bare.getTemporarySupportCount() + " / " + named.getTemporarySupportCount());
            h.assertTrue(count(bare, Items.DIRT) == 20 && count(named, Items.DIRT) == 20,
                    "The soil of a column was lost: " + count(bare, Items.DIRT) + " / " + count(named, Items.DIRT));
            h.assertTrue(dirtAboveGround(h) == 0, "A block of a column was left standing");
            h.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(8, GROUND, 8));
            h.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(24, GROUND, 24));
        });
    }
    /**
     * The live order of PATH7, with the owner's own correction (26.11): mine, the drop comes to rest on the top of the column he
     * stands on and is taken there, then he steps down dismantling the column behind him, then done. Standing on his own column
     * while the drop lies below is not progress, and the watchdog must not close the order for it: no «Полторы минуты» and no
     * «Не нашёл лавы» for the nine blocks that were never there.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 5000)
    public static void obsidianAboveIsTakenFromHisColumnThenItComesDown(GameTestHelper h) {
        ground(h);
        BlockPos obsidian = new BlockPos(12, GROUND + 11, 16);
        h.setBlock(obsidian, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        long[] minedAt = {-1}, downAt = {-1}, bagAt = {-1}, doneAt = {-1}; int[] supportsAtBag = {-1}, most = {0};
        String command = CompanionLocalCommands.route("добудь обсидиан", "Олег");
        h.assertTrue(command != null, "«добудь обсидиан» went to the model");
        var future = CompanionPlanRunner.run(CompanionLocalCommands.actions(command),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-order", () -> false));
        h.onEachTick(() -> {
            most[0] = Math.max(most[0], c.getTemporarySupportCount());
            if (minedAt[0] < 0 && h.getBlockState(obsidian).is(Blocks.AIR)) minedAt[0] = h.getTick();
            if (minedAt[0] >= 0 && downAt[0] < 0 && c.getTemporarySupportCount() < most[0]) downAt[0] = h.getTick();
            if (bagAt[0] < 0 && count(c, Items.OBSIDIAN) > 0) { bagAt[0] = h.getTick(); supportsAtBag[0] = c.getTemporarySupportCount(); }
            if (doneAt[0] < 0 && future.isDone()) doneAt[0] = h.getTick();
        });
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(most[0] >= 7, "He never built up to it: supports " + most[0] + ", " + report);
            h.assertTrue(minedAt[0] >= 0, "The obsidian above him was never mined: " + report);
            h.assertTrue(bagAt[0] > minedAt[0], "The drop was not taken after mining: mined " + minedAt[0] + ", bag " + bagAt[0] + ", " + report);
            // The drop rests on the top of his own column, at his feet: it is taken there, and only then the column comes down.
            h.assertTrue(supportsAtBag[0] > 0, "He took the drop only after the column came down: " + supportsAtBag[0] + ", " + report);
            h.assertTrue(downAt[0] > bagAt[0], "The column came down before the drop was taken: bag " + bagAt[0] + ", down " + downAt[0] + ", " + report);
            h.assertTrue(doneAt[0] >= bagAt[0], "The order ended before the drop was in the bag: " + report);
            h.assertTrue(report.contains("Накопал обсидиана: 1"), "Not an honest count: " + report);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("Не нашёл лавы"), "A wrong end: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "The drop is not in the bag: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && dirtAboveGround(h) == 0, "A block of the column was left standing");
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of the column was lost: " + count(c, Items.DIRT));
        });
    }
    /**
     * An obsidian two blocks up on a stone ledge beside him, its drop resting on the ledge: a walk reaches a stand cell, so no
     * column is built and none of his soil is spent.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void obsidianHeCanWalkToIsNotBuiltUpTo(GameTestHelper h) {
        ground(h);
        BlockPos ledge = new BlockPos(12, GROUND + 1, 16);
        BlockPos obsidian = ledge.above();
        h.setBlock(ledge, Blocks.STONE);
        h.setBlock(obsidian, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        String command = CompanionLocalCommands.route("Олег, добудь обсидиан", "Олег");
        h.assertTrue(command != null, "«Олег, добудь обсидиан» went to the model");
        var plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-walkable", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not the obsidian he could walk to: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertBlockPresent(Blocks.AIR, obsidian);
            h.assertTrue(supports[0] == 0, "A column was built to a block the walk reached: " + supports[0]);
            h.assertTrue(dirtAboveGround(h) == 0, "A block of his column was left standing");
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil was spent on a reachable block: " + count(c, Items.DIRT));
        });
    }
    /** The switch off: the same obsidian above him is left, no column is raised and none of his soil is spent. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void withTheWaySwitchedOffTheObsidianAboveIsLeft(GameTestHelper h) {
        ground(h);
        BlockPos obsidian = new BlockPos(12, GROUND + 11, 16);
        h.setBlock(obsidian, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setBuildWay(false);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        String command = CompanionLocalCommands.route("Олег, добудь обсидиан", "Олег");
        h.assertTrue(command != null, "«Олег, добудь обсидиан» went to the model");
        var plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-way-off", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OBSIDIAN) == 0, "The obsidian above was taken with the switch off: " + count(c, Items.OBSIDIAN));
            h.assertBlockPresent(Blocks.OBSIDIAN, obsidian);
            h.assertTrue(supports[0] == 0, "A column was built with the switch off: " + supports[0]);
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil was spent with the switch off: " + count(c, Items.DIRT));
        });
    }
    /**
     * One obsidian lies about, a bucket of lava in his bag and no water at all: the order for two promises only the block that
     * lies — the bucket cannot be used without water — so he mines it, ends with «Накопал обсидиана: 1.» and his lava bucket
     * stays in the bag, never spent on a block that was not promised (PATH7: an order promises only what is there).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void oneObsidianAndALavaBucketWithoutWaterPromisesOnlyTheBlock(GameTestHelper h) {
        ground(h);
        h.setBlock(new BlockPos(12, GROUND + 1, 16), Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.LAVA_BUCKET));
        // Obsidian lies about, so the plan asks for no water and promises only that one block.
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 1))), "Not the one that lies: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Накопал обсидиана: 1."), "Not an honest count: " + report);
            h.assertTrue(!report.contains("Нет ведра воды") && !report.contains("Не нашёл лавы") && !report.contains("лава рядом кончилась"),
                    "A block that was never promised is said to be missing: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && count(c, Items.LAVA_BUCKET) == 1 && count(c, Items.BUCKET) == 0,
                    "His lava was spent: " + c.getStoredItemsSummary() + ", " + report);
        });
    }
    /**
     * «Добудь 2 обсидиана» with one bucket of lava and one of water and nothing about to mine or cool: the buckets make one
     * block, so the order promises one, makes it and ends with «Накопал обсидиана: 1.» — his lava spent, the empty bucket and
     * his water back — never «не выполнено» for the second block that nothing could give.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void anOrderForTwoWithOneLavaBucketPromisesOneAndMakesIt(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        String route = CompanionLocalCommands.route("Олег, добудь 2 обсидиана", "Олег");
        var plan = CompanionLocalCommands.actions(route);
        h.assertTrue(plan != null && plan.size() == 1, "The phrase is not one step: " + route + " -> " + plan);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-buckets", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Накопал обсидиана: 1."), "Not the one block his buckets make: " + report);
            h.assertTrue(!report.contains("Рядом нечего копать") && !report.contains("Полторы минуты"), "A wrong end: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.BUCKET) == 1
                    && count(c, Items.WATER_BUCKET) == 1, "His buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
        });
    }
    /**
     * The obsidian the player set down himself is the order's target: the two blocks he set down on his site, lying apart by the
     * chest of his, are mined for «добудь 2 обсидиана», while the lava pond on his site is still his — no water is poured over it
     * and his bucket comes back untouched (PATH7: «do not break the player's build» keeps the soil a way lays and the lava of his
     * pond, never the block the order named). A wall of three and more touching is his building and keeps it (#208), so the
     * blocks here lie apart as his lone ones do.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void obsidianThePlayerSetDownIsMinedForHisOrder(GameTestHelper h) {
        ground(h);
        List<BlockPos> pond = lava(h, 18, 15, 3, 1);
        CompanionEntity c = spawn(h);
        // A chest of his on the surface makes it a building of his (CompanionPlayerBuilds.HOME_SCORE), the two blocks lie in it.
        placedByPlayer(h, c, new BlockPos(16, GROUND + 1, 22), Blocks.CHEST);
        List<BlockPos> wall = List.of(new BlockPos(14, GROUND + 1, 20), new BlockPos(16, GROUND + 1, 20));
        for (BlockPos cell : wall) placedByPlayer(h, c, cell, Blocks.OBSIDIAN);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian from the blocks he set down: " + count(c, Items.OBSIDIAN) + ", " + report);
            h.assertTrue(!report.contains("Рядом нечего копать"), "He refused over obsidian of his own: " + report);
            for (BlockPos cell : wall) h.assertBlockPresent(Blocks.AIR, cell);
            // The lava of his pond is his still: no water poured over it, his bucket in the bag.
            for (BlockPos cell : pond) h.assertBlockPresent(Blocks.LAVA, cell);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1, "He poured his water over the pond: " + report);
        });
    }

    /**
     * One obsidian lies about, a bucket of lava in his bag and no water at all: «добудь 2 обсидиана» promises only the block that
     * lies — his lava makes no second without water — so he mines it and the order is what the world gave: «Накопал обсидиана: 1.»
     * and his lava bucket still in the bag, never spent on a block that was not promised (PATH7: an order promises only what is
     * there; the old wording named the water for the second block, and the plan promised two over one block and one bucket).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void withHisLavaBucketAndNoWaterTheBlockThatLiesIsTheWholeOrder(GameTestHelper h) {
        ground(h);
        h.setBlock(new BlockPos(12, GROUND + 1, 16), Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.LAVA_BUCKET));
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 1))), "Not the one block that lies: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Накопал обсидиана: 1."), "Not the one block he mined: " + report);
            h.assertTrue(!report.contains("Нет ведра воды") && !report.contains("Не нашёл лавы") && !report.contains("лава рядом кончилась"),
                    "A block that was never promised is said to be missing: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && count(c, Items.LAVA_BUCKET) == 1 && count(c, Items.BUCKET) == 0,
                    "His lava was spent: " + c.getStoredItemsSummary() + ", " + report);
        });
    }

    /**
     * «Добудь 2 обсидиана» with one bucket of lava and one of water and nothing about to mine or cool: the buckets make one
     * block, so the order promises one and makes it — «Накопал обсидиана: 1.» — his lava spent, the empty bucket and his water
     * back. The order is what the buckets gave, never «не выполнено» for a second block nothing could give (the old wording
     * planned the rest again and said «Обсидиана у меня 1, а лавы рядом и вёдрах больше нет»).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void afterHisOwnBucketsMadeWhatTheyCouldTheOrderIsTheOneBlockTheyGave(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        String route = CompanionLocalCommands.route("Олег, добудь 2 обсидиана", "Олег");
        var plan = CompanionLocalCommands.actions(route);
        h.assertTrue(plan != null && plan.size() == 1, "The phrase is not one step: " + route + " -> " + plan);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian-buckets", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Накопал обсидиана: 1."), "He does not say what he made: " + report);
            h.assertTrue(!report.contains("Рядом нечего копать") && !report.contains("Обсидиана у меня 1, а лавы рядом и в вёдрах больше нет"),
                    "The old line over the block his buckets made: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.BUCKET) == 1
                    && count(c, Items.WATER_BUCKET) == 1, "His buckets are lost or doubled: " + c.getStoredItemsSummary() + ", " + report);
        });
    }

    /** The soil of a column above the ground of the test: what a column of his leaves behind if it is not taken back. */
    private static int dirtAboveGround(GameTestHelper h) {
        int count = 0;
        for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) for (int y = GROUND + 1; y <= GROUND + 12; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) count++;
        return count;
    }

    /** The same companion of a scene of its own, at this cell, with a player of its own beside it (two scenes in one test). */
    private static CompanionEntity spawnAt(GameTestHelper h, int x, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        bind(h, c, x - 2, z);
        c.setSearchRadius(16);
        return c;
    }

    private static void bind(GameTestHelper h, CompanionEntity c, int playerX, int playerZ) {
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(playerX, GROUND + 1, playerZ));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
    }

    /**
     * A room of the player's in the test ground: a plank floor, plank walls three high and a plank roof, with a chest set down by
     * the player so the yard is his site. The floor is what the companion stands on and what his own lava hole is dug in.
     */
    private static void plankRoom(GameTestHelper h, CompanionEntity c, BlockPos chest) {
        for (int x = 4; x <= 16; x++) for (int z = 10; z <= 22; z++) {
            h.setBlock(new BlockPos(x, GROUND, z), Blocks.OAK_PLANKS);
            h.setBlock(new BlockPos(x, GROUND + 4, z), Blocks.OAK_PLANKS);
            for (int y = GROUND + 1; y <= GROUND + 3; y++)
                if (x == 4 || x == 16 || z == 10 || z == 22) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
        }
        placedByPlayer(h, c, chest, Blocks.CHEST);
    }

    /**
     * Buckets of the item lying at this cell, as the player drops them: with the game's pickup delay, so his passing pickup leaves
     * them for the order to take up. The entity is returned, so a test may hold it past the loose pickup.
     */
    private static net.minecraft.world.entity.item.ItemEntity lying(GameTestHelper h, BlockPos at, Item item, int count) {
        var drop = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), at.getX() + .5, at.getY() + .2, at.getZ() + .5, new ItemStack(item, count));
        drop.setPickUpDelay(40);
        h.getLevel().addFreshEntity(drop);
        return drop;
    }

    /** Buckets of this item lying about him, as the world holds them now (the bag is counted apart). */
    private static int lyingCount(GameTestHelper h, CompanionEntity c, Item item) {
        int count = 0;
        for (var drop : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(16)))
            if (drop.isAlive() && drop.getItem().is(item)) count += drop.getItem().getCount();
        return count;
    }

    /** The lava of the make in the world beside him, or null while it is not poured yet. */
    private static BlockPos pouredLava(GameTestHelper h, CompanionEntity c) {
        for (BlockPos pos : BlockPos.betweenClosed(c.blockPosition().offset(-4, -2, -4), c.blockPosition().offset(4, 2, 4)))
            if (h.getLevel().getFluidState(pos).isSourceOfType(Fluids.LAVA)) return pos.immutable();
        return null;
    }

    /** His water buckets gone, as a fall or the player would take them mid-make. */
    private static void takeWaterAway(CompanionEntity c) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.WATER_BUCKET)) c.setItem(slot, ItemStack.EMPTY);
    }

    /** The journal rows of this companion's trace, parsed so a test can compare the order of two events. */
    private static List<com.google.gson.JsonObject> journalRows(CompanionEntity c) {
        dev.aicompanion.CompanionDiagnostics.flush();
        var directory = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion");
        List<com.google.gson.JsonObject> rows = new ArrayList<>();
        // The files a long run may have rolled it into, then the latest one.
        for (String name : List.of("companion-3.jsonl", "companion-2.jsonl", "companion-1.jsonl", "companion-latest.jsonl")) {
            java.nio.file.Path file = directory.resolve(name);
            if (!java.nio.file.Files.exists(file)) continue;
            try {
                for (String line : java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    var row = JsonParser.parseString(line).getAsJsonObject();
                    if (row.has("trace") && row.get("trace").getAsString().equals(c.diagnosticTrace())) rows.add(row);
                }
            } catch (java.io.IOException | com.google.gson.JsonParseException ignored) { }
        }
        return rows;
    }

    /** The take-back order as the diagnostics keep it: his water scooped into its bucket before the lava of the pour went back. */
    private static void assertWaterScoopedBeforeTheLava(GameTestHelper h, CompanionEntity c) {
        dev.aicompanion.CompanionDiagnostics.flush();
        java.nio.file.Path directory = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion");
        String trace = "\"trace\":\"" + c.diagnosticTrace() + "\"";
        int water = -1, lava = -1;
        // The newest file first, and the one a long run may have rolled it into.
        for (String name : List.of("companion-latest.jsonl", "companion-1.jsonl")) {
            java.nio.file.Path file = directory.resolve(name);
            if (!java.nio.file.Files.exists(file)) continue;
            try {
                var lines = java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8);
                for (int line = 0; line < lines.size(); line++) {
                    if (!lines.get(line).contains(trace)) continue;
                    if (lines.get(line).contains("\"event\":\"obsidian_water_scooped\"")) water = line;
                    if (lines.get(line).contains("\"event\":\"obsidian_lava_scooped\"")) lava = line;
                }
            } catch (java.io.IOException error) {
                h.assertTrue(false, "The diagnostics could not be read: " + error);
            }
        }
        h.assertTrue(water >= 0 && lava > water, "His water was not scooped before the lava went back: water at " + water + ", lava at " + lava);
    }

    /** A pond of lava sources {@code depth} deep and {@code size} square, its top level with the ground, its cells (relative). */
    private static List<BlockPos> lava(GameTestHelper h, int x0, int z0, int size, int depth) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = x0; x < x0 + size; x++) for (int z = z0; z < z0 + size; z++) for (int y = GROUND - depth + 1; y <= GROUND; y++) {
            BlockPos cell = new BlockPos(x, y, z);
            h.setBlock(cell, Blocks.LAVA);
            cells.add(cell);
        }
        return cells;
    }

    /** A block set down by the player, as the game tells it (CompanionPlayerBuilds learns his buildings so). */
    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos cell, net.minecraft.world.level.block.Block block) {
        h.setBlock(cell, block);
        var player = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(cell)), Blocks.GRASS_BLOCK.defaultBlockState(), player));
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
        // The least radius: the lava and obsidian of the tests beside this one, beyond its walls, would be out of his way.
        c.setSearchRadius(16);
        return c;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-obsidian", () -> false));
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

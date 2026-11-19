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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Obsidian like a player gets it (decided 08.11): lava he can walk to, his bucket of water over it, the obsidian mined with a pickaxe
 * that takes it and picked up, his water back in the bucket; another's portal and the player's buildings left alone.
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
     * One obsidian lies about, a bucket of lava in his bag and no water at all: he mines the block that lies and then names the water
     * for the second — not the lava he is holding. Before this he said «Накопал 1 из 2: лава рядом кончилась.» over a bag with a
     * bucket of lava in it, and the player went for lava he already had.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void withHisLavaBucketAndNoWaterHeNamesTheWater(GameTestHelper h) {
        ground(h);
        h.setBlock(new BlockPos(12, GROUND + 1, 16), Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.LAVA_BUCKET));
        // Obsidian lies about, so the plan asks for no water: the second block would be his own, and that is where the water is missed.
        var plan = c.obtainPlan(OBSIDIAN, 2);
        h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("mine_obsidian", OBSIDIAN, 2))), "Not the two asked for: " + plan);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Нет ведра воды — нечем залить лаву."), "The water is not named: " + report);
            h.assertTrue(!report.contains("Не нашёл лавы") && !report.contains("лава рядом кончилась"),
                    "The lava he holds is said to be missing: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && count(c, Items.LAVA_BUCKET) == 1 && count(c, Items.BUCKET) == 0,
                    "His lava was spent: " + c.getStoredItemsSummary() + ", " + report);
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
            // The next free hole was dug, the block made in it and mined out of it.
            h.assertBlockPresent(Blocks.AIR, new BlockPos(9, GROUND, 15));
        });
    }

    /** Lava and obsidian where the player has a building are his: no water poured there, nothing mined, and he says what is missing. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void lavaAndObsidianWhereThePlayerBuildsAreLeftAlone(GameTestHelper h) {
        ground(h);
        List<BlockPos> pond = lava(h, 18, 15, 3, 1);
        CompanionEntity c = spawn(h);
        // A chest of his on the surface makes it a building of his (CompanionPlayerBuilds.HOME_SCORE), the obsidian wall is part of it.
        placedByPlayer(h, c, new BlockPos(16, GROUND + 1, 22), Blocks.CHEST);
        List<BlockPos> wall = List.of(new BlockPos(14, GROUND + 1, 20), new BlockPos(15, GROUND + 1, 20), new BlockPos(16, GROUND + 1, 20), new BlockPos(17, GROUND + 1, 20));
        for (BlockPos cell : wall) placedByPlayer(h, c, cell, Blocks.OBSIDIAN);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Рядом нечего копать: ни обсидиана, ни лавы"), "He does not say what is missing: " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.OBSIDIAN) == 0, "He poured or mined: " + report);
            for (BlockPos cell : wall) h.assertBlockPresent(Blocks.OBSIDIAN, cell);
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

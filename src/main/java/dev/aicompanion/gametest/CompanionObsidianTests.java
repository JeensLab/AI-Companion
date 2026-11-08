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

    /** The player's lit portal nearer to him than the lava: its frame is no obsidian lying about, he leaves it whole. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void thePlayersPortalBesideTheLavaIsLeftWhole(GameTestHelper h) {
        ground(h);
        lava(h, 18, 15, 3, 2);
        List<BlockPos> frame = new ArrayList<>();
        for (int x = 8; x <= 11; x++) for (int y = GROUND + 1; y <= GROUND + 5; y++)
            if (x == 8 || x == 11 || y == GROUND + 1 || y == GROUND + 5) { h.setBlock(new BlockPos(x, y, 21), Blocks.OBSIDIAN); frame.add(new BlockPos(x, y, 21)); }
        h.setBlock(new BlockPos(9, GROUND + 2, 21), Blocks.FIRE);
        h.assertBlockPresent(Blocks.NETHER_PORTAL, new BlockPos(10, GROUND + 4, 21));
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OBSIDIAN) == 2, "Not two obsidian: " + future.join().report());
            for (BlockPos cell : frame) h.assertBlockPresent(Blocks.OBSIDIAN, cell);
            for (int x = 9; x <= 10; x++) for (int y = GROUND + 2; y <= GROUND + 4; y++) h.assertBlockPresent(Blocks.NETHER_PORTAL, new BlockPos(x, y, 21));
        });
    }

    /** No lava within his reach: he says so at once and pours nothing. */
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
            h.assertTrue(report.contains("Не нашёл лавы рядом — отведи меня к ней."), "He does not say he found no lava: " + report);
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
        for (String model : List.of("построй портал из обсидиана", "добудь обсидиан и принеси", "не копай обсидиан", "добудь 100 обсидиана", "сломай обсидиан",
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

    /** Lava and obsidian on a site where the player builds are his: no water poured there, nothing mined, and he says he found no lava. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void lavaAndObsidianWhereThePlayerBuildsAreLeftAlone(GameTestHelper h) {
        ground(h);
        List<BlockPos> pond = lava(h, 18, 15, 3, 1);
        CompanionEntity c = spawn(h);
        var player = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        List<BlockPos> wall = List.of(new BlockPos(14, GROUND + 1, 20), new BlockPos(15, GROUND + 1, 20), new BlockPos(16, GROUND + 1, 20), new BlockPos(17, GROUND + 1, 20));
        for (BlockPos cell : wall) {
            h.setBlock(cell, Blocks.OBSIDIAN);
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(cell)), Blocks.GRASS_BLOCK.defaultBlockState(), player));
        }
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:obsidian\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Не нашёл лавы рядом — отведи меня к ней."), "He does not say he found no lava: " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.OBSIDIAN) == 0, "He poured or mined: " + report);
            for (BlockPos cell : wall) h.assertBlockPresent(Blocks.OBSIDIAN, cell);
            for (BlockPos cell : pond) h.assertBlockPresent(Blocks.LAVA, cell);
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

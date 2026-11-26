package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The PATH7 matrix (idea PATH7, #105): «добудь» a block no walk reaches — a way up of his own soil, mined, the column
 * dismantled behind him, the drop taken and the ground left as it was. Every row here goes through the player's phrase
 * (CompanionLocalCommands.route) or the plan the chat hands the runner for it; none of them calls an internal helper to do
 * the work. The heights are the whole supported range and both boundaries, the negative half proves that a block a walk
 * reaches places no block at all, the material rows prove what the soil in the bag and in a chest really gives, and the
 * interruptions prove what stays in the world and what in the bag.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWayMatrixTests {
    private static final int GROUND = 5;
    private static final int FEET = GROUND + 1;
    private static final int SIDE = 32, TOP = 24;

    // ---- heights above him: a gourd, obsidian and a leaf crown ----

    /** A pumpkin two above his feet, two across: a stand cell reaches it, no column, the ground as it was. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void pumpkinTwoAboveIsMinedWithNoColumn(GameTestHelper h) {
        gourdAbove(h, 2, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    /** Three above: still a swing from the ground, no column. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void pumpkinThreeAboveIsMinedWithNoColumn(GameTestHelper h) {
        gourdAbove(h, 3, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    /** Four above: the first height a stand cell cannot reach; the column comes back down and the ground is restored. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void pumpkinFourAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 4, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void pumpkinFiveAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 5, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void pumpkinSixAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 6, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void pumpkinSevenAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 7, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void pumpkinEightAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 8, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void pumpkinNineAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 9, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void pumpkinTenAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 10, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void pumpkinElevenAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 11, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void pumpkinTwelveAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 12, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    /** Thirteen above: ten steps of soil, the height the old search of twelve never saw. */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void pumpkinThirteenAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 13, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void pumpkinFourteenAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 14, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void pumpkinFifteenAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 15, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    /**
     * Sixteen above his feet: a block at the height the owner's scene hung at, thirteen steps of the column and every one of
     * them back in the bag.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void pumpkinSixteenAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 16, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    /** Seventeen above: past the height the old way stopped at, now inside it — the owner's blocks hung this high. */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void pumpkinSeventeenAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        gourdAbove(h, 17, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void obsidianFourAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        obsidianAbove(h, 4);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void obsidianSixAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        obsidianAbove(h, 6);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void obsidianEightAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        obsidianAbove(h, 8);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void obsidianTenAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        obsidianAbove(h, 10);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void obsidianTwelveAboveIsMinedFromAColumnThatComesDown(GameTestHelper h) {
        obsidianAbove(h, 12);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void melonFourAboveGivesItsSlicesFromAColumn(GameTestHelper h) {
        melonAbove(h, 4);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void melonSixAboveGivesItsSlicesFromAColumn(GameTestHelper h) {
        melonAbove(h, 6);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void melonTenAboveGivesItsSlicesFromAColumn(GameTestHelper h) {
        melonAbove(h, 10);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void leafCrownFiveAboveIsCutFromAColumn(GameTestHelper h) {
        crownAbove(h, 5);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void leafCrownSixAboveIsCutFromAColumn(GameTestHelper h) {
        crownAbove(h, 6);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void leafCrownSevenAboveIsCutFromAColumn(GameTestHelper h) {
        crownAbove(h, 7);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void leafCrownEightAboveIsCutFromAColumn(GameTestHelper h) {
        crownAbove(h, 8);
    }

    /** The top of a leaf crown's short column: nine above his feet is five steps, the most the crown's own limit takes. */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void leafCrownNineAboveIsCutFromAColumn(GameTestHelper h) {
        crownAbove(h, 9);
    }

    // ---- boundaries ----

    /** One block up is a step of the walk, not a column: mined from where he stands. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aBlockOneAboveIsAStepAndNoColumnIsRaised(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(15, FEET + 1, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "Not the pumpkin one above: " + future.join().report());
            h.assertBlockPresent(Blocks.AIR, target);
            h.assertTrue(most[0] == 0, "A column was raised for a step: " + most[0]);
            assertRestored(h, before, target);
        });
    }

    /** One above on bare stone: a step needs no soil at all. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aBlockOneAboveOnStoneIsAStepWithNoSoil(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos target = new BlockPos(15, FEET + 1, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] == 0 && count(c, Items.DIRT) == 0, "A step asked for soil: " + future.join().report());
        });
    }

    /** Two above on bare stone: still within a swing, no soil, no column. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aBlockTwoAboveOnStoneIsMinedWithoutSoil(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos target = new BlockPos(16, FEET + 2, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] == 0, "A column for two above: " + future.join().report());
            assertRestored(h, before, target);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aBlockThreeAboveOnStoneIsMinedWithoutSoil(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos target = new BlockPos(16, FEET + 3, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] == 0, "A column for three above: " + future.join().report());
        });
    }

    /** Four above on bare stone is the first height that asks for soil: with none he says so and builds nothing. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void aBlockFourAboveOnStoneIsRefusedWithNoColumn(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos target = new BlockPos(16, FEET + 4, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 0 && most[0] == 0, "A column stood without soil: " + future.join().report());
            h.assertBlockPresent(Blocks.PUMPKIN, target);
            assertRestored(h, before);
        });
    }

    /** The first height past the way's bound: thirty-three above is refused honestly, the block left and no column started. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void aBlockTwentyOneAboveIsRefusedHonestly(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 33, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 24));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 0 && most[0] == 0, "Twenty-one above was taken: " + report);
            h.assertBlockPresent(Blocks.PUMPKIN, target);
            h.assertTrue(count(c, Items.DIRT) == 24, "The soil was spent on a refusal: " + report);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
            assertRestored(h, before);
        });
    }

    // ---- phrasing symmetry on one scene ----

    /**
     * One scene — one obsidian ten above, a diamond pickaxe and twenty soil — and four ways of saying it: «добудь обсидиан»,
     * «олег добудь обсидиан», «добудь 2 обсидиана», «добудь 10 обсидиана». Each promises only the one block the world gives
     * (min(order, available)) and answers «Накопал обсидиана: 1», never «не выполнено»; the block is gone, the column comes
     * down, the soil is back and the scene is as it was before every run.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 16000)
    public static void fourPhrasingsOnOneSceneGiveTheSameHonestOne(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        List<String> phrases = List.of("добудь обсидиан", "олег добудь обсидиан", "добудь 2 обсидиана", "добудь 10 обсидиана");
        String[] said = new String[phrases.size()];
        int[] promise = new int[phrases.size()];
        int[] at = {0};
        int[] settleFrom = new int[phrases.size()];
        List<CompletableFuture<CompanionPlanRunner.Outcome>> futures = new java.util.ArrayList<>();
        for (int index = 0; index < phrases.size(); index++) futures.add(null);
        h.onEachTick(() -> {
            if (at[0] >= phrases.size()) return;
            if (futures.get(at[0]) == null) {
                promise[at[0]] = c.obtainAvailable("minecraft:obsidian").count();
                futures.set(at[0], say(h, c, phrases.get(at[0])));
                return;
            }
            if (!futures.get(at[0]).isDone()) return;
            String phrase = phrases.get(at[0]);
            String report = futures.get(at[0]).join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "«" + phrase + "»: " + count(c, Items.OBSIDIAN) + " obsidian, " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            h.assertTrue(report.contains("Накопал обсидиана: 1"), "«" + phrase + "» not the honest one: " + report);
            h.assertTrue(!report.contains("не выполнено") && !report.contains("Полторы минуты"), "«" + phrase + "»: " + report);
            // The drop is taken from the top of the column, then the column comes down, and the soil of it comes back as the
            // descent's own drops are picked up — a few ticks after the last support, not on the tick the order ends.
            if (c.getTemporarySupportCount() != 0 || count(c, Items.DIRT) != 20) {
                if (settleFrom[at[0]] == 0) settleFrom[at[0]] = (int) h.getTick();
                h.assertTrue(h.getTick() - settleFrom[at[0]] < 100, "«" + phrase + "»: the column or the soil stayed: supports "
                        + c.getTemporarySupportCount() + ", dirt " + count(c, Items.DIRT) + ", " + report);
                return;
            }
            assertRestored(h, before, target);
            said[at[0]] = "one";
            // The next phrasing starts on the same scene: the block back, the bag as it was, him at his place.
            h.setBlock(target, Blocks.OBSIDIAN);
            for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.OBSIDIAN)) c.setItem(slot, ItemStack.EMPTY);
            BlockPos home = h.absolutePos(new BlockPos(14, FEET, 16));
            c.teleportTo(home.getX() + .5, home.getY(), home.getZ() + .5);
            c.setHealth(c.getMaxHealth());
            at[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(at[0] >= phrases.size(), "Only " + at[0] + " of four phrasings ran");
            for (int run = 0; run < phrases.size(); run++) h.assertTrue("one".equals(said[run]), "«" + phrases.get(run) + "» ended another way: " + said[run]);
            h.assertTrue(java.util.Arrays.stream(promise).allMatch(count -> count == 1), "The promises differ: " + java.util.Arrays.toString(promise));
        });
    }

    // ---- target kinds, one scene each ----

    /** The owner's live case: a pumpkin ten above, no stand under it; up his own soil, break it, down and take it. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aPumpkinHangingIsTakenByTheWay(GameTestHelper h) {
        gourdAbove(h, 10, Blocks.PUMPKIN, Items.PUMPKIN, 1);
    }

    /** A melon above: a hand gives its slices, the column reaches it and every slice is in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aMelonHangingGivesItsSlicesByTheWay(GameTestHelper h) {
        melonAbove(h, 8);
    }

    /** The owner's tall spruce: a crown above every stand cell is cut from his own column, the soil comes back. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aLeafCrownIsCutFromHisOwnColumn(GameTestHelper h) {
        crownAbove(h, 8);
    }

    /** Iron in a wall at eye level: a walk reaches it, no column, no soil spent, two ore blocks really mined. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void anOreInAWallIsMinedOnFoot(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        for (int y = FEET; y <= FEET + 3; y++) for (int z = 14; z <= 18; z++) h.setBlock(new BlockPos(18, y, z), Blocks.STONE);
        BlockPos first = new BlockPos(18, FEET, 16), second = new BlockPos(18, FEET + 1, 16);
        h.setBlock(first, Blocks.IRON_ORE);
        h.setBlock(second, Blocks.IRON_ORE);
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь 2 железа");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.RAW_IRON) == 2, "Not two raw iron: " + count(c, Items.RAW_IRON) + ", " + report);
            h.assertBlockPresent(Blocks.AIR, first);
            h.assertBlockPresent(Blocks.AIR, second);
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 10, "A column was raised for an ore in a wall: " + report);
        });
    }

    /** A diamond lying on a ledge over his head: the pickup walks to it, no column is raised. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aLyingItemOnALedgeIsTakenOnFoot(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos ledge = new BlockPos(17, FEET + 1, 16);
        h.setBlock(ledge, Blocks.STONE);
        BlockPos at = h.absolutePos(ledge.above());
        h.getLevel().addFreshEntity(new ItemEntity(h.getLevel(), at.getX() + .5, at.getY() + .2, at.getZ() + .5, new ItemStack(Items.DIAMOND, 1)));
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "подбери алмаз");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.DIAMOND) == 1, "The diamond on the ledge is not in the bag: " + future.join().report());
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 10, "A column was raised for a lying item: " + most[0]);
            h.assertBlockPresent(Blocks.STONE, ledge);
        });
    }

    /** Sand stacked in a column: the bottom block is dug, the column falls and the sand is collected — no column of his. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void sandStackedInAColumnFallsAndIsTaken(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos bottom = new BlockPos(17, FEET, 16);
        for (int up = 0; up < 8; up++) h.setBlock(bottom.above(up), Blocks.SAND);
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SHOVEL));
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь 3 песка");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.SAND) >= 3, "Not three sand: " + count(c, Items.SAND) + ", " + report);
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 10, "A column was raised for falling sand: " + most[0]);
            h.assertBlockPresent(Blocks.AIR, bottom.above(7));
        });
    }

    /** A pumpkin on the ground: a walk takes it, no block placed, nothing spent. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aPumpkinOnTheGroundIsTakenOnFoot(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] == 0, "A column for a pumpkin on the ground: " + future.join().report());
            assertRestored(h, before, target);
        });
    }

    /** Obsidian on a ledge two above: the walk reaches a stand cell, no column, the soil untouched. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void obsidianOnALedgeIsTakenOnFoot(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos ledge = new BlockPos(17, FEET, 16);
        h.setBlock(ledge, Blocks.STONE);
        BlockPos target = ledge.above();
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь обсидиан");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && most[0] == 0, "A column to a ledge: " + future.join().report());
            assertRestored(h, before, target);
            h.assertTrue(count(c, Items.DIRT) == 10, "The soil was spent on a reachable ledge");
        });
    }

    // ---- the negative half: a target a walk reaches places no block at all ----

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aPumpkinOneBlockAwayPlacesNoBlock(GameTestHelper h) {
        pumpkinOnFootAt(h, 1);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aPumpkinTwoBlocksAwayPlacesNoBlock(GameTestHelper h) {
        pumpkinOnFootAt(h, 2);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aPumpkinThreeBlocksAwayPlacesNoBlock(GameTestHelper h) {
        pumpkinOnFootAt(h, 3);
    }

    /** Around a corner: a wall between him and the pumpkin, the way round its end — the walk, no column. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aPumpkinAroundACornerPlacesNoBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        for (int z = 12; z <= 18; z++) for (int y = FEET; y <= FEET + 1; y++) h.setBlock(new BlockPos(16, y, z), Blocks.STONE);
        BlockPos target = new BlockPos(18, FEET, 19);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin round the corner is not in the bag: " + future.join().report());
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 10, "A column was raised round a corner: " + most[0]);
            assertRestored(h, before, target);
        });
    }

    /** On a slope: a step up of one block at a time is the walk, no column. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aPumpkinUpASlopePlacesNoBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        for (int x = 17; x < SIDE - 1; x++) for (int z = 1; z < SIDE - 1; z++) {
            h.setBlock(new BlockPos(x, GROUND + 1, z), Blocks.GRASS_BLOCK);
            if (x >= 19) h.setBlock(new BlockPos(x, GROUND + 2, z), Blocks.GRASS_BLOCK);
        }
        BlockPos target = new BlockPos(20, GROUND + 3, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin up the slope is not in the bag: " + future.join().report());
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 10, "A column was raised up a slope: " + most[0]);
            assertRestored(h, before, target);
        });
    }

    /** Behind a shut gate (#209): he opens it, walks in, takes the pumpkin and shuts it — no block placed. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aPumpkinBehindAClosedGatePlacesNoBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        garden(h, 16, 12, true, false);
        BlockPos gate = new BlockPos(16, FEET, 12 + 5);
        gate(h, gate, Direction.EAST, false, false);
        BlockPos target = new BlockPos(22, FEET, 17);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h, 11, 17);
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin behind the gate is not in the bag: " + future.join().report());
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 10, "A column was raised behind a gate: " + most[0]);
            h.assertFalse(isOpen(h, gate), "The gate was left open");
            assertRestored(h, before, target);
        });
    }

    /** A gate already open: the free way in is taken and the gate is not touched, no block placed. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aPumpkinBehindAnOpenGatePlacesNoBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        garden(h, 16, 12, true, false);
        BlockPos gate = new BlockPos(16, FEET, 12 + 5);
        gate(h, gate, Direction.EAST, true, false);
        BlockPos target = new BlockPos(22, FEET, 17);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h, 11, 17);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] == 0, "The open gate cost a column: " + future.join().report());
            h.assertTrue(isOpen(h, gate), "He shut a gate that was open");
        });
    }

    /** A gap in the wall: the walk goes through it, no column, the gate left alone. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aPumpkinBehindAWallWithAGapPlacesNoBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        garden(h, 16, 12, true, true);
        BlockPos gate = new BlockPos(16, FEET, 12 + 5);
        gate(h, gate, Direction.EAST, false, false);
        BlockPos target = new BlockPos(22, FEET, 17);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h, 11, 17);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] == 0, "The gap cost a column: " + future.join().report());
            h.assertFalse(isOpen(h, gate), "He opened the shut gate beside a free way");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aMelonOnTheGroundPlacesNoBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(17, FEET, 16);
        h.setBlock(target, Blocks.MELON);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь 3 ломтика арбуза", melonPlan(3));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.MELON_SLICE) >= 3 && most[0] == 0, "A column for a melon on the ground: " + future.join().report());
            assertRestored(h, before, target);
        });
    }

    /** A crown over the player's own stair: the stair is a stand like any other, no column of his is raised. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aLeafCrownOverThePlayersStairPlacesNoBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos trunk = new BlockPos(18, FEET, 16);
        for (int up = 0; up < 8; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 7; dy <= 8; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) h.setBlock(cell, Blocks.OAK_LEAVES);
        }
        for (int step = 0; step < 9; step++) h.setBlock(new BlockPos(18, FEET + step, 5 + step), Blocks.DIRT);
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь 8 листвы", leavesPlan(8));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OAK_LEAVES) >= 8, "Not eight leaves from the stair: " + future.join().report());
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 0, "A column was raised beside a stair: " + most[0]);
        });
    }

    /** The block under his feet: worked from where he stands, the drop rests in the hole he steps into — no column. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void theBlockUnderHisFeetPlacesNoBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos under = new BlockPos(14, GROUND, 16);
        h.setBlock(under, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin under his feet is not in the bag: " + future.join().report());
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 10, "A column was raised over a block under his feet: " + most[0]);
            assertRestored(h, before, under);
        });
    }

    // ---- material: the soil in the bag, none at all, and only in a chest ----

    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void soilInTheBagIsUsedAndComesBack(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 12, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] >= 8, "Not twelve above on his own soil: " + future.join().report());
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of the column did not come back: " + count(c, Items.DIRT));
            assertRestored(h, before, target);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void grassBlocksInTheBagAreUsedAsSoil(GameTestHelper h) {
        soilKindAbove(h, new ItemStack(Items.GRASS_BLOCK, 12), 8, Items.GRASS_BLOCK, 12);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void coarseDirtInTheBagIsUsedAsSoil(GameTestHelper h) {
        soilKindAbove(h, new ItemStack(Items.COARSE_DIRT, 12), 8, Items.COARSE_DIRT, 12);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void rootedDirtInTheBagIsUsedAsSoil(GameTestHelper h) {
        soilKindAbove(h, new ItemStack(Items.ROOTED_DIRT, 12), 8, Items.ROOTED_DIRT, 12);
    }

    /** Stone is a support only with a pickaxe at hand: broken by hand it would drop nothing. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void stoneInTheBagIsASupportWithAPickaxe(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(1, new ItemStack(Items.COBBLESTONE, 12));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] > 0, "Stone was not used as a support: " + future.join().report());
            h.assertTrue(count(c, Items.COBBLESTONE) == 12, "The stone of the column did not come back: " + count(c, Items.COBBLESTONE));
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void soilInTheHandCounts(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIRT, 12));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] > 0, "Soil in the hand was not counted: " + future.join().report());
            h.assertTrue(count(c, Items.DIRT) == 12, "The soil of the hand did not come back: " + count(c, Items.DIRT));
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void soilInTheOffhandCounts(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.DIRT, 12));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] > 0, "Soil in the offhand was not counted: " + future.join().report());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void soilSplitBetweenSlotsIsCounted(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 6));
        c.setItem(2, new ItemStack(Items.DIRT, 6));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] > 0, "Soil split between slots was not counted: " + future.join().report());
            h.assertTrue(count(c, Items.DIRT) == 12, "The soil split between slots did not come back: " + count(c, Items.DIRT));
        });
    }

    /** No soil in the bag, the flat ground about him is dirt: he digs it, climbs and the column comes down. */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void noSoilInTheBagButDirtAboutIsDugAndTheColumnComesDown(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "Not the pumpkin over soil he could dig: " + report);
            h.assertTrue(most[0] > 0, "He never built up on the soil he dug: " + report);
            h.assertTrue(count(c, Items.DIRT) > 0, "He dug no soil for the column: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && c.onGround(), "The column was left standing: " + report);
            h.assertTrue(!report.contains("дай земли") && !report.contains("Полторы минуты"), "A refusal over soil he could dig: " + report);
        });
    }

    /** Bare stone and nothing in the bag: an honest refusal, no column started, the world as it was. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void noSoilAnywhereIsRefusedAndNothingIsBuilt(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 0 && most[0] == 0, "A column without soil: " + report);
            // The pumpkin is seen and no column is built for want of soil: the refusal names its distance and says it saw it —
            // never «Рядом не вижу» and a distance in one sentence (the judge read both together).
            h.assertTrue(report.contains("вижу, но не достать") && report.contains("8 блоков вверх"), "Not the chain's own refusal: " + report);
            h.assertTrue(!report.contains("Рядом не вижу"), "The refusal claims it saw nothing over a block it names: " + report);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
            h.assertBlockPresent(Blocks.PUMPKIN, target);
            assertRestored(h, before);
        });
    }

    /** Five soil for a column of nine steps on stone: not enough, so he says so and spends none of it. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void tooLittleSoilForTwelveAboveIsRefused(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos target = new BlockPos(16, FEET + 12, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 5));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 0 && most[0] == 0 && count(c, Items.DIRT) == 5, "Half a column was built: " + future.join().report());
            assertRestored(h, before);
        });
    }

    /** The same short bag over diggable dirt: the missing soil is dug, so the column is raised after all. */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void tooLittleSoilButDirtAboutIsDug(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 12, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 5));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] >= 5, "The missing soil was not dug: " + future.join().report());
            h.assertTrue(count(c, Items.DIRT) >= 9, "The soil of the column did not come back: " + count(c, Items.DIRT));
        });
    }

    /**
     * Soil only in a chest and bare stone about (slice S2): today he says so honestly, builds nothing and leaves the chest
     * alone — the chest is not pretended to be the bag.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void soilOnlyInAChestIsAnHonestLineAndNothingIsBuilt(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        BlockPos chestAt = new BlockPos(12, FEET, 14);
        h.setBlock(chestAt, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chestAt));
        chest.setItem(0, new ItemStack(Items.DIRT, 20));
        CompanionEntity c = spawn(h);
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 0 && most[0] == 0, "A column stood on soil still in the chest: " + report);
            h.assertTrue(count(c, Items.DIRT) == 0, "Soil left the chest into his bag: " + count(c, Items.DIRT) + ", " + report);
            h.assertTrue(chest.getItem(0).getCount() == 20, "The chest was emptied: " + chest.getItem(0));
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
            assertRestored(h, before);
        });
    }

    /** Soil only in a chest under a crown: the leaf order's own honest line, the chest untouched (S2). */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void soilOnlyInAChestForLeavesSaysDaiZemli(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos trunk = new BlockPos(18, FEET, 16);
        for (int up = 0; up < 5; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 5; dy <= 6; dy++)
            h.setBlock(trunk.offset(dx, dy, dz), Blocks.SPRUCE_LEAVES);
        BlockPos chestAt = new BlockPos(12, FEET, 14);
        h.setBlock(chestAt, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chestAt));
        chest.setItem(0, new ItemStack(Items.DIRT, 20));
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь листву");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(CompanionLocalCommands.actions(CompanionLocalCommands.route("добудь листву", "Олег")), report);
            h.assertTrue(said.equals("До листвы не достану — дай земли."), "Not the soil line: " + said + ", " + report);
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 0, "A column stood on soil still in the chest: " + most[0]);
            h.assertTrue(chest.getItem(0).getCount() == 20, "The chest was emptied: " + chest.getItem(0));
        });
    }

    /** A chest of soil is not pretended while the ground is dirt: he digs the ground and the chest keeps its twenty. */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void soilInAChestIsNotPretendedWhileTheGroundIsDug(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        BlockPos chestAt = new BlockPos(12, FEET, 14);
        h.setBlock(chestAt, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chestAt));
        chest.setItem(0, new ItemStack(Items.DIRT, 20));
        CompanionEntity c = spawn(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] > 0, "The crown over diggable dirt was refused: " + future.join().report());
            h.assertTrue(count(c, Items.DIRT) > 0 && count(c, Items.DIRT) <= 6, "The dug soil is not his: " + count(c, Items.DIRT));
            h.assertTrue(chest.getItem(0).getCount() == 20, "The chest was used before the ground: " + chest.getItem(0));
        });
    }

    /** A pumpkin a walk reaches with a chest of soil beside him: the chest is not touched at all. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void soilInAChestIsNotTakenForAReachableBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        BlockPos chestAt = new BlockPos(12, FEET, 14);
        h.setBlock(chestAt, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chestAt));
        chest.setItem(0, new ItemStack(Items.DIRT, 20));
        CompanionEntity c = spawn(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] == 0 && count(c, Items.DIRT) == 0, "The chest was opened for nothing: " + future.join().report());
            h.assertTrue(chest.getItem(0).getCount() == 20, "The chest was emptied: " + chest.getItem(0));
        });
    }

    // ---- order and the watchdog ----

    /** Mine, take the drop off the top of his own column, come down dismantling it behind him, «готово»: the sequence, not only the end state. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void theOrderMinesTakesTheDropThenDescendsAndSaysGotovo(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] most = {0}, minedAt = {-1}, downAt = {-1}, bagAt = {-1}, doneAt = {-1}, supportsAtBag = {-1};
        h.onEachTick(() -> {
            most[0] = Math.max(most[0], c.getTemporarySupportCount());
            if (minedAt[0] < 0 && h.getBlockState(target).isAir()) minedAt[0] = (int) h.getTick();
            if (minedAt[0] >= 0 && downAt[0] < 0 && c.getTemporarySupportCount() < most[0]) downAt[0] = (int) h.getTick();
            if (bagAt[0] < 0 && count(c, Items.PUMPKIN) > 0) { bagAt[0] = (int) h.getTick(); supportsAtBag[0] = c.getTemporarySupportCount(); }
            if (doneAt[0] < 0 && c.getTemporarySupportCount() == 0 && count(c, Items.PUMPKIN) > 0 && c.onGround()) doneAt[0] = (int) h.getTick();
        });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(most[0] >= 6, "He never built up: supports " + most[0] + ", " + report);
            h.assertTrue(minedAt[0] >= 0, "The pumpkin above him was never mined: " + report);
            // The drop comes to rest on the top of his own column, at his feet: it is taken there, and the column comes down after
            // it is in his bag — never torn down first, leaving the drop above (the owner, 26.11).
            h.assertTrue(bagAt[0] > minedAt[0], "The drop was not taken after mining: mined " + minedAt[0] + ", bag " + bagAt[0] + ", " + report);
            h.assertTrue(supportsAtBag[0] > 0, "The drop was taken only after the column came down: " + supportsAtBag[0] + ", " + report);
            h.assertTrue(downAt[0] > bagAt[0], "The column came down before the drop was taken: bag " + bagAt[0] + ", down " + downAt[0] + ", " + report);
            h.assertTrue(doneAt[0] >= bagAt[0], "The order ended before the drop was in the bag: " + report);
            h.assertTrue(report.contains("Готово"), "Not «готово»: " + report);
            h.assertTrue(!report.contains("не выполнено") && !report.contains("Полторы минуты"), "A wrong end: " + report);
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && count(c, Items.DIRT) == 20 && c.getTemporarySupportCount() == 0, "Bag or column wrong: " + report);
        });
    }

    /** A drop resting on the top of his own column is taken there, at his feet — not left for the ground it would fall to. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void theDropOnHisOwnColumnIsTakenThere(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] minedAt = {-1}, bagAt = {-1}, supportsAtBag = {-1};
        h.onEachTick(() -> {
            if (minedAt[0] < 0 && h.getBlockState(target).isAir()) minedAt[0] = (int) h.getTick();
            if (bagAt[0] < 0 && count(c, Items.PUMPKIN) > 0) { bagAt[0] = (int) h.getTick(); supportsAtBag[0] = c.getTemporarySupportCount(); }
        });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(minedAt[0] >= 0 && bagAt[0] > minedAt[0], "The drop was not taken after mining: " + future.join().report());
            h.assertTrue(supportsAtBag[0] > 0, "The drop was left for the ground under a column of " + supportsAtBag[0] + " supports");
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && c.getTemporarySupportCount() == 0, "Not the drop, or the column stayed: " + future.join().report());
        });
    }

    /** The watchdog does not fire while he descends his own column for his own drop — a long way is not a stall. */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void theWatchdogDoesNotFireWhileHeDescendsForHisOwnDrop(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 16, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] stalled = {false};
        int[] most = {0}, descendingTicks = {0};
        h.onEachTick(() -> {
            most[0] = Math.max(most[0], c.getTemporarySupportCount());
            if (c.isRecoveringSupports()) descendingTicks[0]++;
            if (c.getLastTaskResult().contains("Полторы минуты")) stalled[0] = true;
        });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(most[0] >= 12, "He never built up to sixteen above: " + most[0] + ", " + report);
            h.assertTrue(descendingTicks[0] > 10, "The descent was too short to judge: " + descendingTicks[0]);
            h.assertTrue(!stalled[0] && !report.contains("Полторы минуты"), "The watchdog fired while he came down: " + report);
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && count(c, Items.DIRT) == 20, "The drop or the soil is missing: " + report);
        });
    }

    /** The same while he walks to the drop of what he just broke: no stall line, no closed order. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void theWatchdogDoesNotFireWhileHeWalksToHisDrop(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(17, FEET + 6, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] stalled = {false};
        h.onEachTick(() -> { if (c.getLastTaskResult().contains("Полторы минуты")) stalled[0] = true; });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(!stalled[0] && !report.contains("Полторы минуты"), "The watchdog fired on the walk to the drop: " + report);
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The drop is not in the bag: " + report);
        });
    }

    /** The owner's obsidian order: mined, the drop taken from the top of the column he stood on, then the column down, then «Накопал обсидиана: 1». */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void theObsidianOrderTakesTheDropThenComesDown(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] most = {0}, minedAt = {-1}, downAt = {-1}, bagAt = {-1}, supportsAtBag = {-1};
        h.onEachTick(() -> {
            most[0] = Math.max(most[0], c.getTemporarySupportCount());
            if (minedAt[0] < 0 && h.getBlockState(target).isAir()) minedAt[0] = (int) h.getTick();
            if (minedAt[0] >= 0 && downAt[0] < 0 && c.getTemporarySupportCount() < most[0]) downAt[0] = (int) h.getTick();
            if (bagAt[0] < 0 && count(c, Items.OBSIDIAN) > 0) { bagAt[0] = (int) h.getTick(); supportsAtBag[0] = c.getTemporarySupportCount(); }
        });
        var future = say(h, c, "добудь обсидиан");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(most[0] >= 7, "He never built up: supports " + most[0] + ", " + report);
            // The drop of what he broke comes first: it falls onto the top of his own column, his body touches it there, and the
            // column comes down once it is in his bag — the owner's «сразу же ломает своё строение, а потом забирает» (26.11).
            h.assertTrue(minedAt[0] >= 0 && bagAt[0] > minedAt[0], "The drop was not taken after mining: mined " + minedAt[0] + ", bag " + bagAt[0] + ", " + report);
            h.assertTrue(supportsAtBag[0] > 0, "The drop was not taken from the column he stood on: " + supportsAtBag[0] + ", " + report);
            h.assertTrue(downAt[0] > bagAt[0], "The column came down before the drop was taken: bag " + bagAt[0] + ", down " + downAt[0] + ", " + report);
            h.assertTrue(report.contains("Накопал обсидиана: 1"), "Not the honest count: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && count(c, Items.DIRT) == 20, "Bag or soil wrong: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column still stands: " + c.getTemporarySupportCount());
        });
    }

    /** The highest way does not end on the watchdog either: thirteen steps up and down inside the order's own time. */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void aLongWayIsNotClosedByTheWatchdog(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 16, 16);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] stalled = {false};
        int[] most = {0};
        h.onEachTick(() -> {
            most[0] = Math.max(most[0], c.getTemporarySupportCount());
            if (c.getLastTaskResult().contains("Полторы минуты")) stalled[0] = true;
        });
        var future = say(h, c, "добудь обсидиан");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(most[0] >= 12, "Not thirteen steps of soil: " + most[0] + ", " + report);
            h.assertTrue(!stalled[0] && !report.contains("Полторы минуты"), "The watchdog closed the longest way: " + report);
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && count(c, Items.DIRT) == 20 && c.getHealth() == c.getMaxHealth(), "The longest way cost him: " + report);
        });
    }

    // ---- interruptions ----

    /** «Стоп» in the middle of the column: the block stays, the column comes down and the soil is back in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void cancelMidColumnLeavesTheBlockAndReturnsTheSoil(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 12, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] stopped = {false};
        h.onEachTick(() -> {
            if (!stopped[0] && c.getTemporarySupportCount() >= 2) {
                stopped[0] = true;
                CompanionEvents.executeAction(owner(h, c), "stop", "", 1);
            }
        });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0], "The column never stood long enough to stop");
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports(), "The column is still standing: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of the stopped column is not back: " + count(c, Items.DIRT));
            h.assertTrue(count(c, Items.PUMPKIN) == 0, "The pumpkin came into the bag after the stop: " + count(c, Items.PUMPKIN));
            h.assertBlockPresent(Blocks.PUMPKIN, target);
            h.assertTrue(dirtAboveGround(h) == 0, "A block of the stopped column was left in the world");
        });
    }

    /** «Стоп» in the middle of the descent: the block is gone, the drop is on the ground or in the bag, the column comes down. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void cancelMidDescentLeavesTheDropAndReturnsTheSoil(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] stopped = {false};
        h.onEachTick(() -> {
            if (!stopped[0] && h.getBlockState(target).isAir() && c.isRecoveringSupports()) {
                stopped[0] = true;
                CompanionEvents.executeAction(owner(h, c), "stop", "", 1);
            }
        });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0], "The descent never began");
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports(), "The column is still standing: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of the stopped descent is not back: " + count(c, Items.DIRT));
            h.assertBlockPresent(Blocks.AIR, target);
            h.assertTrue(count(c, Items.PUMPKIN) <= 1, "The pumpkin was duplicated: " + count(c, Items.PUMPKIN));
            h.assertTrue(dirtAboveGround(h) == 0, "A block of the stopped descent was left in the world");
        });
    }

    /** Death in the middle of the column: his soil is left standing in the world, nothing is silently deleted. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void deathMidColumnLeavesTheColumnInTheWorld(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 12, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] died = {false};
        int[] stood = {0};
        h.onEachTick(() -> {
            if (!died[0] && c.getTemporarySupportCount() >= 2) {
                died[0] = true;
                stood[0] = c.getTemporarySupportCount();
                c.hurt(c.damageSources().generic(), 1000F);
            }
        });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(died[0], "He never climbed far enough to die on the column");
            h.assertTrue(c.isDeadOrDying(), "He is not dead");
            h.assertTrue(c.getTemporarySupportCount() == stood[0], "The column was taken from the dead: " + c.getTemporarySupportCount() + " of " + stood[0]);
            h.assertTrue(dirtAboveGround(h) == stood[0], "The column is not in the world: " + dirtAboveGround(h) + " of " + stood[0]);
        });
    }

    /** Death in the middle of the descent: the block is gone and the drop is in the world, not duplicated into the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void deathMidDescentLeavesTheDropInTheWorld(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] died = {false};
        h.onEachTick(() -> {
            if (!died[0] && h.getBlockState(target).isAir() && c.isRecoveringSupports()) {
                died[0] = true;
                c.hurt(c.damageSources().generic(), 1000F);
            }
        });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(died[0], "The descent never began");
            h.assertTrue(c.isDeadOrDying(), "He is not dead");
            h.assertBlockPresent(Blocks.AIR, target);
            h.assertTrue(count(c, Items.PUMPKIN) <= 1, "The pumpkin was duplicated on death: " + count(c, Items.PUMPKIN));
        });
    }

    /** The target removed in the middle of the column: the order ends honestly, the column comes down, the soil returns. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void targetRemovedMidColumnEndsHonestlyAndTheColumnComesDown(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 12, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] removed = {false};
        h.onEachTick(() -> {
            if (!removed[0] && c.getTemporarySupportCount() >= 2) { removed[0] = true; h.setBlock(target, Blocks.AIR); }
        });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(removed[0], "The column never stood long enough");
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(c.getTemporarySupportCount() == 0 && count(c, Items.DIRT) == 20, "The column or the soil stayed: " + report);
            h.assertTrue(count(c, Items.PUMPKIN) == 0, "A pumpkin came from nowhere: " + report);
            h.assertTrue(!report.contains("Полторы минуты"), "The order stalled over a target the player took: " + report);
            h.assertTrue(dirtAboveGround(h) == 0, "A block of the column was left in the world");
        });
    }

    /** The target replaced by the player's stone: the order does not claim it as a pumpkin. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void targetReplacedMidColumnEndsHonestly(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] replaced = {false};
        h.onEachTick(() -> {
            if (!replaced[0] && c.getTemporarySupportCount() >= 2) { replaced[0] = true; h.setBlock(target, Blocks.STONE); }
        });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(replaced[0], "The column never stood long enough");
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertBlockPresent(Blocks.STONE, target);
            h.assertTrue(count(c, Items.PUMPKIN) == 0, "The player's stone was counted as a pumpkin: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && count(c, Items.DIRT) == 20, "The column or the soil stayed: " + report);
        });
    }

    /** «Стоп» on the obsidian column: the block stays, the pickaxe stays, the soil comes back. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void cancelMidColumnObsidianLeavesTheBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] stopped = {false};
        h.onEachTick(() -> {
            if (!stopped[0] && c.getTemporarySupportCount() >= 2) { stopped[0] = true; CompanionEvents.executeAction(owner(h, c), "stop", "", 1); }
        });
        var future = say(h, c, "добудь обсидиан");
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0], "The column never stood long enough to stop");
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertBlockPresent(Blocks.OBSIDIAN, target);
            h.assertTrue(count(c, Items.OBSIDIAN) == 0 && count(c, Items.DIRT) == 20, "The stop lost the soil or the block: " + count(c, Items.DIRT));
            h.assertTrue(count(c, Items.DIAMOND_PICKAXE) == 1, "The pickaxe was lost on the stop");
        });
    }

    /** Death on the obsidian column: the block stays and his soil is left standing, as on any column. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void deathMidColumnObsidianLeavesTheColumn(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] died = {false};
        int[] stood = {0};
        h.onEachTick(() -> {
            if (!died[0] && c.getTemporarySupportCount() >= 2) { died[0] = true; stood[0] = c.getTemporarySupportCount(); c.hurt(c.damageSources().generic(), 1000F); }
        });
        var future = say(h, c, "добудь обсидиан");
        h.succeedWhen(() -> {
            h.assertTrue(died[0], "He never climbed far enough to die on the column");
            h.assertBlockPresent(Blocks.OBSIDIAN, target);
            h.assertTrue(c.getTemporarySupportCount() == stood[0], "The column was taken from the dead: " + c.getTemporarySupportCount() + " of " + stood[0]);
        });
    }

    /** The column is in the entity's own save: a reload in the middle of it knows the supports it stands on. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void theColumnSurvivesAReload(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 12, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        boolean[] checked = {false};
        int[] reloaded = {-1};
        h.onEachTick(() -> {
            if (checked[0] || c.getTemporarySupportCount() < 2) return;
            checked[0] = true;
            CompoundTag saved = new CompoundTag();
            c.saveWithoutId(saved);
            CompanionEntity other = h.spawn(AICompanionMod.COMPANION.get(), 4, FEET, 4);
            other.load(saved);
            reloaded[0] = other.getTemporarySupportCount();
            // The reloaded copy carries the same UUID: it is read and put away, so the owner's companion stays the one working.
            other.discard();
        });
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(checked[0], "The column never stood long enough to reload");
            h.assertTrue(reloaded[0] > 0, "The reloaded entity knows no column: " + reloaded[0]);
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && c.getTemporarySupportCount() == 0 && count(c, Items.DIRT) == 20, "The reloaded order lost the drop or the soil: " + future.join().report());
        });
    }

    // ---- the switch ----

    /** The switch off: a pumpkin above is left, no column raised, the soil untouched, an honest line. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void withTheWayOffAPumpkinAboveIsLeft(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setBuildWay(false);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 0 && most[0] == 0 && count(c, Items.DIRT) == 20, "The way ran with the switch off: " + report);
            h.assertBlockPresent(Blocks.PUMPKIN, target);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
            assertRestored(h, before);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void withTheWayOffObsidianAboveIsLeft(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setBuildWay(false);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь обсидиан");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OBSIDIAN) == 0 && most[0] == 0 && count(c, Items.DIRT) == 20, "The way ran with the switch off: " + future.join().report());
            h.assertBlockPresent(Blocks.OBSIDIAN, target);
        });
    }

    /** The switch off restores the previous behaviour exactly: a leaf crown is still climbed, as it always was. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void withTheWayOffALeafCrownIsStillClimbed(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos trunk = new BlockPos(18, FEET, 16);
        for (int up = 0; up < 5; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 5; dy <= 6; dy++)
            h.setBlock(trunk.offset(dx, dy, dz), Blocks.SPRUCE_LEAVES);
        CompanionEntity c = spawn(h);
        c.setBuildWay(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
        c.setItem(1, new ItemStack(Items.DIRT, 8));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь листву");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) >= 16, "The crown was not cut with the way off: " + report);
            h.assertTrue(most[0] > 0, "The crown's own climb was switched off too: " + report);
            h.assertTrue(count(c, Items.DIRT) == 8, "The crown's soil did not come back: " + report);
        });
    }

    /** The switch off survives a save and a load. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theSwitchOffSurvivesAReload(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 4);
        c.setBuildWay(false);
        CompoundTag saved = new CompoundTag();
        c.saveWithoutId(saved);
        CompanionEntity other = h.spawn(AICompanionMod.COMPANION.get(), 6, 1, 6);
        other.load(saved);
        h.assertTrue(!other.buildWay(), "The switch off did not survive the reload");
        h.succeed();
    }

    /** The switch on survives a save and a load. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theSwitchOnSurvivesAReload(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 4);
        c.setBuildWay(true);
        CompoundTag saved = new CompoundTag();
        c.saveWithoutId(saved);
        CompanionEntity other = h.spawn(AICompanionMod.COMPANION.get(), 6, 1, 6);
        other.load(saved);
        h.assertTrue(other.buildWay(), "The switch on did not survive the reload");
        h.succeed();
    }

    /** An old world whose save has no BuildWay keeps the way on, as the field's own default says. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anOldWorldWithoutTheTagBuildsAWay(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 4);
        CompoundTag saved = new CompoundTag();
        c.saveWithoutId(saved);
        saved.remove("BuildWay");
        CompanionEntity other = h.spawn(AICompanionMod.COMPANION.get(), 6, 1, 6);
        other.load(saved);
        h.assertTrue(other.buildWay(), "An old world lost the way");
        h.succeed();
    }

    // ---- the way is not raised where it is not needed, and refuses honestly where it cannot stand ----

    /** A stair the player built to the crown: a stand like any other, no column of his. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aWayIsNotRaisedToAStairHeCanWalkUp(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos trunk = new BlockPos(18, FEET, 16);
        for (int up = 0; up < 8; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos cell = trunk.offset(dx, 8, dz);
            if (!cell.equals(trunk.above(8))) h.setBlock(cell, Blocks.OAK_LEAVES);
        }
        for (int step = 0; step < 9; step++) h.setBlock(new BlockPos(18, FEET + step, 5 + step), Blocks.DIRT);
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь 8 листвы", leavesPlan(8));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OAK_LEAVES) >= 8, "Not eight leaves from the stair: " + future.join().report());
            h.assertTrue(most[0] == 0, "A column was raised beside a stair: " + most[0]);
        });
    }

    /** A ceiling over the column: the way cannot stand, the refusal is honest and nothing is half-built. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aCeilingOverTheColumnIsAnHonestRefusal(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) h.setBlock(new BlockPos(16 + dx, FEET + 2, 16 + dz), Blocks.STONE);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 0, "The pumpkin under the ceiling was taken: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && count(c, Items.DIRT) == 20, "A column stood under the ceiling: " + report);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
            assertRestored(h, before);
        });
    }

    /**
     * Five across, ten up: no stand cell reaches it, and the column stands at its own foot under the block — he walks there,
     * builds up and takes it (PATH7: a block up and to the side is reached from the foot under it, as a player does).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aBlockFiveAcrossIsTakenFromAColumnAtItsFoot(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(19, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin five across is not in the bag: " + report);
            h.assertTrue(most[0] > 0, "No column was raised for a block five across: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            h.assertTrue(count(c, Items.DIRT) == 20 && c.getTemporarySupportCount() == 0, "The soil of the column was lost: " + report);
            assertRestored(h, before, target);
        });
    }

    /** The way does not spend his tool: the diamond pickaxe is in the bag after the obsidian order. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void theWayDoesNotSpendTheTool(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        var future = say(h, c, "добудь обсидиан");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.DIAMOND_PICKAXE) == 1 && count(c, Items.OBSIDIAN) == 1, "The pickaxe was spent on the way: " + future.join().report());
        });
    }

    /** The longest way keeps his health: thirteen steps down are stepped, not fallen. */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void theLongestWayKeepsHisHealth(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 16, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        float[] least = {c.getMaxHealth()};
        h.onEachTick(() -> least[0] = Math.min(least[0], c.getHealth()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "The way cost him health: " + least[0] + " of " + c.getMaxHealth() + ", " + future.join().report());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && count(c, Items.DIRT) == 20, "The drop or the soil is missing: " + future.join().report());
        });
    }

    /** The player's own blocks beside the column are left exactly as they were. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void theColumnDoesNotTouchThePlayersBlocks(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        BlockPos beside = new BlockPos(18, FEET, 16);
        h.setBlock(beside, Blocks.CRAFTING_TABLE);
        BlockPos aboveBeside = new BlockPos(18, FEET + 1, 16);
        h.setBlock(aboveBeside, Blocks.TORCH);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "Not the pumpkin: " + future.join().report());
            assertRestored(h, before, target);
            h.assertBlockPresent(Blocks.CRAFTING_TABLE, beside);
            h.assertBlockPresent(Blocks.TORCH, aboveBeside);
        });
    }

    /** The way never builds for an item that lies on the ground, nor for ore or sand: those walks take them. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void theWayIsNotRaisedForOreOnTheGround(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos ore = new BlockPos(17, FEET, 16);
        h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var oreOrder = say(h, c, "добудь 1 железо");
        h.succeedWhen(() -> {
            h.assertTrue(oreOrder.isDone() && !oreOrder.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.RAW_IRON) == 1 && most[0] == 0, "A column was raised for an ore on the ground: " + oreOrder.join().report());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void theWayIsNotRaisedForSandOnTheGround(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos sand = new BlockPos(17, FEET, 16);
        h.setBlock(sand, Blocks.SAND);
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SHOVEL));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var order = say(h, c, "добудь 1 песок");
        h.succeedWhen(() -> {
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.SAND) == 1 && most[0] == 0, "A column was raised for sand on the ground: " + order.join().report());
        });
    }

    /** Obsidian two above on bare stone: still a swing from the ground, no column and no soil at all. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void obsidianTwoAboveIsMinedOnFoot(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos target = new BlockPos(16, FEET + 2, 16);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь обсидиан");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && most[0] == 0 && count(c, Items.DIRT) == 0, "A column for two above: " + future.join().report());
            assertRestored(h, before, target);
        });
    }

    /** Obsidian three above on bare stone: the last height a stand cell reaches, no soil needed. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void obsidianThreeAboveIsMinedOnFoot(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos target = new BlockPos(16, FEET + 3, 16);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь обсидиан");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.OBSIDIAN) == 1 && most[0] == 0 && count(c, Items.DIRT) == 0, "A column for three above: " + future.join().report());
        });
    }

    /** The soil he digs for the column is all in his bag after it: none of it is left lying in the world. */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void theDugSoilIsNotLeftInTheWorld(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] > 0, "Not the pumpkin over soil he dug: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && dirtAboveGround(h) == 0, "A dug block of the column was left standing: " + dirtAboveGround(h));
            h.assertTrue(count(c, Items.DIRT) > 0, "The dug soil is neither in the bag nor in the world: " + report);
        });
    }

    /** The owner's bag: pickaxe, axe, shovel, shears and soil — the pumpkin is taken and every tool stays. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aPumpkinAboveIsTakenWithTheOwnersToolsInTheBag(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(1, new ItemStack(Items.IRON_AXE));
        c.setItem(2, new ItemStack(Items.IRON_SHOVEL));
        c.setItem(3, new ItemStack(Items.SHEARS));
        c.setItem(4, new ItemStack(Items.DIRT, 12));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "Not the pumpkin: " + report);
            h.assertTrue(count(c, Items.STONE_PICKAXE) == 1 && count(c, Items.IRON_AXE) == 1 && count(c, Items.IRON_SHOVEL) == 1
                    && count(c, Items.SHEARS) == 1 && count(c, Items.DIRT) == 12, "A tool or the soil was spent: " + c.getStoredItemsSummary());
        });
    }

    /** Two pumpkins, eight and ten above: two climbs in one order, both drops, the soil back once. */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void twoPumpkinsAboveAreTakenInOneOrder(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos low = new BlockPos(16, FEET + 8, 16), high = new BlockPos(16, FEET + 10, 16);
        h.setBlock(low, Blocks.PUMPKIN);
        h.setBlock(high, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        var future = sayModel(h, c, "добудь 2 тыквы", pumpkinPlan(2));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 2, "Not two pumpkins: " + count(c, Items.PUMPKIN) + ", " + report);
            h.assertBlockPresent(Blocks.AIR, low);
            h.assertBlockPresent(Blocks.AIR, high);
            assertRestored(h, before, low, high);
            h.assertTrue(count(c, Items.DIRT) == 20 && c.getTemporarySupportCount() == 0, "The soil of two climbs is not back: " + report);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
        });
    }

    /** A pumpkin above and one on the ground in one order: the walk takes one, the way the other, nothing duplicated. */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void aPumpkinAboveAndOneOnTheGroundAreTakenInOneOrder(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos above = new BlockPos(16, FEET + 8, 16), ground = new BlockPos(18, FEET, 16);
        h.setBlock(above, Blocks.PUMPKIN);
        h.setBlock(ground, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        var future = sayModel(h, c, "добудь 2 тыквы", pumpkinPlan(2));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 2, "Not two pumpkins: " + report);
            assertRestored(h, before, above, ground);
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of the way is not back: " + report);
        });
    }

    /** The longest way leaves no drop behind: nothing of the pumpkin lies in the world after «готово». */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void theWayLeavesNoDropBehind(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 16, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin is not in the bag: " + future.join().report());
            int lying = 0;
            for (ItemEntity drop : h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(3), found -> found.getItem().is(Items.PUMPKIN))) lying += drop.getItem().getCount();
            h.assertTrue(lying == 0, "A pumpkin drop was left lying in the world: " + lying);
        });
    }

    /** The target removed before the climb begins: an honest refusal, no column started, the world as it was. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void theTargetRemovedBeforeTheClimbIsAnHonestRefusal(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 10, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        h.runAfterDelay(2, () -> h.setBlock(target, Blocks.AIR));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 0 && most[0] == 0 && count(c, Items.DIRT) == 20, "A column over nothing: " + report);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
            assertRestored(h, before, target);
        });
    }

    /** A pumpkin on a ledge beside him, a step from the ground: no column. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aPumpkinOnALedgeBesideHimPlacesNoBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos ledge = new BlockPos(17, FEET, 16);
        h.setBlock(ledge, Blocks.STONE);
        BlockPos target = ledge.above();
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] == 0 && count(c, Items.DIRT) == 10, "A column for a ledge: " + future.join().report());
            assertRestored(h, before, target);
        });
    }

    /** A crown above even the longest leaf column: the height is named, no column is built and no soil is spent. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aLeafCrownAboveAnyColumnIsRefusedHonestly(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos trunk = new BlockPos(18, FEET, 16);
        for (int up = 0; up <= 11; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 11; dy <= 12; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) h.setBlock(cell, Blocks.SPRUCE_LEAVES);
        }
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
        c.setItem(1, new ItemStack(Items.DIRT, 8));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь листву");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(CompanionLocalCommands.actions(CompanionLocalCommands.route("добудь листву", "Олег")), report);
            h.assertTrue(said.equals("До листвы не достану даже со столбом."), "Not the height line: " + said + ", " + report);
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 8 && count(c, Items.SPRUCE_LEAVES) == 0, "A column was built for a crown out of reach: " + report);
        });
    }

    /** The switch off leaves a melon above too: the previous behaviour, an honest refusal, no column. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aMelonAboveWithTheWayOffIsLeft(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.MELON);
        CompanionEntity c = spawn(h);
        c.setBuildWay(false);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь 3 ломтика арбуза", melonPlan(3));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.MELON_SLICE) == 0 && most[0] == 0 && count(c, Items.DIRT) == 20, "The way ran with the switch off: " + future.join().report());
            h.assertBlockPresent(Blocks.MELON, target);
            assertRestored(h, before);
        });
    }

    /** The answer names the count it really took: one pumpkin, and the line says one — not ten, eleven or twenty-one. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void theOrderReportsTheCountItTook(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + 8, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(CompanionPlanCodec.parsePlan(JsonParser.parseString(pumpkinPlan(1))), report);
            String counted = "Готово: «" + new ItemStack(Items.PUMPKIN).getHoverName().getString() + "» у меня, 1 шт.";
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && said.equals(counted), "The answer hides the count: " + said + ", not " + counted + ", " + report);
        });
    }

    // ---- helpers ----

    /** A gourd {@code height} above his feet, two across; the plan the chat hands the runner for «добудь …». */
    private static void gourdAbove(GameTestHelper h, int height, Block block, Item item, int expected) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + height, 16);
        h.setBlock(target, block);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        String json = item == Items.PUMPKIN ? pumpkinPlan(1) : melonPlan(3);
        String phrase = item == Items.PUMPKIN ? "добудь тыкву" : "добудь 3 ломтика арбуза";
        var seen = c.obtainAvailable(BuiltInRegistries.ITEM.getKey(item).toString());
        BlockPos absolute = h.absolutePos(target);
        var how = dev.aicompanion.entity.CompanionReach.how(c, h.getLevel(), absolute, 3.5D, true, true, 16);
        h.assertTrue(seen.some(), "The reach answer does not see the block " + height + " above: " + seen + ", how " + how
                + ", rise " + (absolute.getY() - c.blockPosition().getY()) + ", feet " + c.blockPosition() + ", block " + absolute
                + ", dirt " + count(c, Items.DIRT) + ", buildWay " + c.buildWay() + ", radius " + c.getSearchRadius());
        var future = sayModel(h, c, phrase, json);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, item) >= expected, "Not " + expected + " " + item + ": " + count(c, item) + ", available " + c.obtainAvailable(BuiltInRegistries.ITEM.getKey(item).toString()) + ", " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            assertRestored(h, before, target);
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of the column was lost: " + count(c, Items.DIRT));
            h.assertTrue(height >= 4 ? most[0] > 0 : most[0] == 0, "The column for " + height + " above is wrong: " + most[0] + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column is still standing: " + c.getTemporarySupportCount() + ", recovering " + c.isRecoveringSupports() + ", last: " + c.getLastTaskResult());
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "The climb hurt him: " + c.getHealth() + " of " + c.getMaxHealth());
        });
    }

    private static void obsidianAbove(GameTestHelper h, int height) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + height, 16);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь 1 обсидиан");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "Not the obsidian " + height + " above: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            assertRestored(h, before, target);
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of the column was lost: " + count(c, Items.DIRT));
            h.assertTrue(height >= 4 ? most[0] > 0 : most[0] == 0, "The column for " + height + " above is wrong: " + most[0] + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && !report.contains("Полторы минуты"), "A wrong end: " + report);
        });
    }

    private static void melonAbove(GameTestHelper h, int height) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + height, 16);
        h.setBlock(target, Blocks.MELON);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь 3 ломтика арбуза", melonPlan(3));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.MELON_SLICE) >= 3, "Not the melon's slices: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            assertRestored(h, before, target);
            h.assertTrue(count(c, Items.DIRT) == 20 && most[0] >= (height >= 4 ? 1 : 0), "The soil or the column is wrong: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && !report.contains("Полторы минуты"), "A wrong end: " + report);
        });
    }

    /** A spruce trunk with its crown {@code height} above his feet; «добудь листву» with the shears and the soil. */
    private static void crownAbove(GameTestHelper h, int height) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos trunk = new BlockPos(18, FEET, 16);
        for (int up = 0; up <= height; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos cell = trunk.offset(dx, height, dz);
            if (!cell.equals(trunk.above(height))) h.setBlock(cell, Blocks.SPRUCE_LEAVES);
        }
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
        c.setItem(1, new ItemStack(Items.DIRT, 8));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь листву");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) >= 16, "Not sixteen leaves " + height + " above: " + report);
            h.assertTrue(most[0] > 0, "No column for the crown: " + report);
            // The crown's own soil comes back, and so does what was dug for a column longer than the bag held.
            h.assertTrue(count(c, Items.DIRT) >= 8, "The soil of the crown's column was lost: " + count(c, Items.DIRT));
            h.assertTrue(c.getTemporarySupportCount() == 0 && !report.contains("Полторы минуты"), "A wrong end: " + report);
        });
    }

    private static void soilKindAbove(GameTestHelper h, ItemStack soil, int height, Item soilItem, int expected) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(16, FEET + height, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, soil);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 1 && most[0] > 0, "The soil of " + soilItem + " was not used: " + report);
            h.assertTrue(count(c, soilItem) == expected, "The soil did not come back: " + count(c, soilItem) + " of " + expected);
            h.assertTrue(dirtAboveGround(h) == 0, "A support was left in the world");
        });
    }

    /** A pumpkin on the ground {@code away} blocks from him, two across at most: the walk takes it. */
    private static void pumpkinOnFootAt(GameTestHelper h, int away) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(14 + away, FEET, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 10));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", pumpkinPlan(1));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin " + away + " away is not in the bag: " + future.join().report());
            h.assertTrue(most[0] == 0 && count(c, Items.DIRT) == 10, "A column was raised " + away + " blocks away: " + most[0]);
            assertRestored(h, before, target);
        });
    }

    private static String pumpkinPlan(int count) {
        return "[{\"action\":\"obtain_item\",\"item\":\"minecraft:pumpkin\",\"count\":" + count + "}]";
    }

    private static String melonPlan(int count) {
        return "[{\"action\":\"obtain_item\",\"item\":\"minecraft:melon_slice\",\"count\":" + count + "}]";
    }

    private static String leavesPlan(int count) {
        return "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":" + count + "}]";
    }

    /** The Russian name of an item as the player's client shows it; the test server runs en_us. */
    private static final java.util.function.Function<Item, String> RU = item ->
            item == Items.DIAMOND ? "Алмаз" : new ItemStack(item).getHoverName().getString();

    /** The player's phrase through the router and the runner: a local order, no model. */
    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» went to the model");
        return CompanionPlanRunner.run(CompanionLocalCommands.actions(command),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-matrix", () -> false));
    }

    /** A phrase the router leaves to the model, with the plan the chat would hand the runner for it. */
    private static CompletableFuture<CompanionPlanRunner.Outcome> sayModel(GameTestHelper h, CompanionEntity c, String phrase, String json) {
        var reply = CompanionPlanRequirements.apply(phrase, new CompanionReply("", CompanionPlanCodec.parsePlan(JsonParser.parseString(json))));
        return CompanionPlanRunner.run(reply.actions(),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-matrix-model", () -> false));
    }

    private static CompanionEntity spawn(GameTestHelper h) { return spawn(h, 14, 16); }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, FEET, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(Math.max(1, x - 2), FEET, Math.max(1, z)));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(24);
        c.setLifestyle(false, false);
        return c;
    }

    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity c) {
        return h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
    }

    private static void flat(GameTestHelper h, Block floor) {
        for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) for (int y = 0; y < TOP; y++) {
            boolean border = x == 0 || x == SIDE - 1 || z == 0 || z == SIDE - 1;
            if (y == 0 || border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
            else if (y == GROUND) h.setBlock(new BlockPos(x, y, z), floor);
            else if (y < GROUND) h.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
            else h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    private static int[] snapshot(GameTestHelper h) {
        int[] cells = new int[SIDE * TOP * SIDE];
        int at = 0;
        for (int y = 0; y < TOP; y++) for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++)
            cells[at++] = Block.getId(h.getBlockState(new BlockPos(x, y, z)));
        return cells;
    }

    /** Every cell of the test as it was, but the cells that were mined: those stand empty. */
    private static void assertRestored(GameTestHelper h, int[] before, BlockPos... mined) {
        Set<BlockPos> allowed = new HashSet<>(List.of(mined));
        int at = 0;
        for (int y = 0; y < TOP; y++) for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            int now = Block.getId(h.getBlockState(pos));
            int was = before[at++];
            if (now == was) continue;
            h.assertTrue(allowed.contains(pos) && h.getBlockState(pos).isAir(),
                    "A cell changed at " + pos + ": " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(h.getBlockState(pos).getBlock())
                            + ", allowed " + allowed);
        }
    }

    private static int dirtAboveGround(GameTestHelper h) {
        int count = 0;
        for (int x = 1; x < SIDE - 1; x++) for (int z = 1; z < SIDE - 1; z++) for (int y = GROUND + 1; y < TOP - 1; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) count++;
        return count;
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }

    // ---- the garden behind a gate (#209) ----

    private static final int GARDEN = 10;

    private static void garden(GameTestHelper h, int x0, int z0, boolean gateInWest, boolean gap) {
        for (int x = 0; x <= GARDEN; x++) for (int z = 0; z <= GARDEN; z++) {
            if (x != 0 && x != GARDEN && z != 0 && z != GARDEN) continue;
            if (gateInWest && x == 0 && z == 5) continue;
            if (!gateInWest && x == 5 && z == GARDEN) continue;
            if (!gateInWest && x == 0 && z == 5) continue;
            if (gap && x == 0 && z == 2) continue;
            h.setBlock(new BlockPos(x0 + x, FEET, z0 + z), Blocks.OAK_FENCE);
        }
        for (int x = 1; x < GARDEN; x++) for (int z = 1; z < GARDEN; z++)
            h.setBlock(new BlockPos(x0 + x, GROUND, z0 + z), Blocks.GRASS_BLOCK);
    }

    private static void gate(GameTestHelper h, BlockPos at, Direction facing, boolean open, boolean powered) {
        h.setBlock(at, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, facing)
                .setValue(FenceGateBlock.OPEN, open).setValue(FenceGateBlock.POWERED, powered));
    }

    private static boolean isOpen(GameTestHelper h, BlockPos gate) { return h.getBlockState(gate).getValue(FenceGateBlock.OPEN); }
}

package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

import static dev.aicompanion.gametest.CompanionTraderTests.GROUND;
import static dev.aicompanion.gametest.CompanionTraderTests.ground;
import static dev.aicompanion.gametest.CompanionTraderTests.run;
import static dev.aicompanion.gametest.CompanionTraderTests.spawn;
import static dev.aicompanion.gametest.CompanionTraderTests.trader;

/**
 * «Купи стрел»: the companion walks to a trader who has the deal, pays from his bag at the deal's own price, the trader's use,
 * experience and level count, and the goods are dropped by the trader and picked up. A fresh villager is traded with only on the
 * player's word; one with the same deal who is already fixed in his trade is chosen without it.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionTraderTradeTests {
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade1")
    public static void after1(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade2")
    public static void after2(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade3")
    public static void after3(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade4")
    public static void after4(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade5")
    public static void after5(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade6")
    public static void after6(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade7")
    public static void after7(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade8")
    public static void after8(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade9")
    public static void after9(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade10")
    public static void after10(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade11")
    public static void after11(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade12")
    public static void after12(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade13")
    public static void after13(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade14")
    public static void after14(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade15")
    public static void after15(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "trade16")
    public static void after16(net.minecraft.server.level.ServerLevel level) { CompanionTraderTests.afterVillagers(level); }

    private static final String ARROWS = "[{\"action\":\"trade_items\",\"item\":\"minecraft:arrow\"}]";

    /** One emerald for four arrows with a fixed fletcher: one emerald left, four arrows picked up, the deal used once, the villager gained experience. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "trade1")
    public static void aFixedTraderSellsArrowsForARealEmerald(GameTestHelper h) {
        ground(h);
        Villager fletcher = trader(h, VillagerProfession.FLETCHER, 14, 12, arrowsForEmerald(5));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 2));
        var future = run(h, companion, ARROWS);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(future.join().skipped().isEmpty(), future.join().report());
            h.assertTrue(count(companion, Items.EMERALD) == 1 && count(companion, Items.ARROW) == 4, "Emeralds " + count(companion, Items.EMERALD) + ", arrows " + count(companion, Items.ARROW));
            h.assertTrue(fletcher.getOffers().getFirst().getUses() == 1 && fletcher.getVillagerXp() == 5, "Uses " + fletcher.getOffers().getFirst().getUses() + ", xp " + fletcher.getVillagerXp());
            var entry = companion.traders().entry(fletcher.getUUID());
            h.assertTrue(entry != null && !entry.fresh() && entry.deals().getFirst().left() == 2, "Memory: " + entry);
        });
    }

    /** The only fletcher is fresh: without the word nothing is paid and the refusal says the goods will be fixed; with it the trade is made and he is fixed. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "trade2")
    public static void aFreshVillagerIsTradedWithOnlyOnTheWord(GameTestHelper h) {
        ground(h);
        Villager fresh = freshFletcher(h, 14, 12);
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 2));
        var refused = run(h, companion, ARROWS);
        var agreed = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<dev.aicompanion.client.CompanionPlanRunner.Outcome>>();
        h.succeedWhen(() -> {
            h.assertTrue(refused.isDone() && !refused.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            if (agreed.get() == null) {
                String report = refused.join().report();
                h.assertTrue(report.contains("закрепится"), report);
                h.assertTrue(count(companion, Items.EMERALD) == 2 && fresh.getVillagerXp() == 0 && fresh.getOffers().getFirst().getUses() == 0, "The refused order cost something");
                agreed.set(run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:arrow\",\"consent\":true}]"));
            }
            h.assertTrue(agreed.get().isDone() && !agreed.get().isCompletedExceptionally(), "Second plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.EMERALD) == 1 && count(companion, Items.ARROW) == 4 && fresh.getVillagerXp() > 0, "Emeralds " + count(companion, Items.EMERALD) + ", arrows " + count(companion, Items.ARROW) + ", xp " + fresh.getVillagerXp());
            h.assertTrue(companion.traders().entry(fresh.getUUID()) != null && !companion.traders().entry(fresh.getUUID()).fresh(), "Still remembered as fresh");
        });
    }

    /** The same deal at a fresh fletcher (nearer) and at a fixed one: the fixed one is taken, with no word given. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "trade3")
    public static void aFixedTraderIsChosenOverAFreshOneWithTheSameDeal(GameTestHelper h) {
        ground(h);
        Villager fresh = freshFletcher(h, 12, 14);
        Villager fixed = trader(h, VillagerProfession.FLETCHER, 20, 18, arrowsForEmerald(5));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 2));
        // With the word given: the fresh one is not dropped for want of it, so only the preference decides.
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:arrow\",\"consent\":true}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.ARROW) == 4 && fixed.getOffers().getFirst().getUses() == 1 && fresh.getVillagerXp() == 0 && fresh.getOffers().getFirst().getUses() == 0,
                    future.join().report() + " | arrows " + count(companion, Items.ARROW) + ", fixed uses " + fixed.getOffers().getFirst().getUses() + ", fresh xp " + fresh.getVillagerXp());
        });
    }

    /** The deal has no uses left: the order says it is out and he keeps his emeralds. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = "trade4")
    public static void anExhaustedDealIsSaidSoAndNothingIsPaid(GameTestHelper h) {
        ground(h);
        MerchantOffer spent = arrowsForEmerald(5);
        spent.setToOutOfStock();
        Villager fletcher = trader(h, VillagerProfession.FLETCHER, 14, 12, spent);
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 2));
        var future = run(h, companion, ARROWS);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Кончилось") && count(companion, Items.EMERALD) == 2 && count(companion, Items.ARROW) == 0 && fletcher.getVillagerXp() == 0, report);
        });
    }

    /**
     * The player has the fletcher's window open: the offers carry the player's own discount then, and the game lets nobody else trade
     * with him. Neither does the companion: he says the trader is busy and keeps his emeralds.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = "trade7")
    public static void aTraderBusyWithThePlayerIsLeftAlone(GameTestHelper h) {
        ground(h);
        Villager fletcher = trader(h, VillagerProfession.FLETCHER, 14, 12, arrowsForEmerald(5));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 2));
        fletcher.setTradingPlayer(h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId()));
        var future = run(h, companion, ARROWS);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("занят") && count(companion, Items.EMERALD) == 2 && fletcher.getOffers().getFirst().getUses() == 0, report);
        });
    }

    /** A trading hall: the villager in a 1x1 glass cell with one gap at head height. He trades through the gap (the ID written without minecraft:), the glass stays. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "trade5")
    public static void aVillagerInAGlassCellIsTradedWithThroughTheGap(GameTestHelper h) {
        ground(h);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            for (int y = GROUND + 1; y <= GROUND + 2; y++) h.setBlock(new BlockPos(20 + dx, y, 20 + dz), Blocks.GLASS);
        }
        h.setBlock(new BlockPos(20, GROUND + 2, 19), Blocks.AIR);
        Villager fletcher = trader(h, VillagerProfession.FLETCHER, 20, 20, arrowsForEmerald(5));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 2));
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"arrow\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.ARROW) == 4 && count(companion, Items.EMERALD) == 1 && fletcher.getOffers().getFirst().getUses() == 1,
                    future.join().report() + " | arrows " + count(companion, Items.ARROW));
            int glass = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = GROUND + 1; y <= GROUND + 2; y++)
                if (h.getBlockState(new BlockPos(20 + dx, y, 20 + dz)).is(Blocks.GLASS)) glass++;
            h.assertTrue(glass == 8 * 2 - 1, "Glass left: " + glass);
        });
    }

    /** "Buy 10 arrows" at one emerald for four arrows: three trades (the last one whole, rounded up), twelve arrows, two emeralds left. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600, batch = "trade8")
    public static void aNumberOfPiecesRepeatsTheDealRoundedUp(GameTestHelper h) {
        ground(h);
        Villager fletcher = trader(h, VillagerProfession.FLETCHER, 14, 12, arrowsForEmerald(5));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 5));
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:arrow\",\"count\":10}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(future.join().skipped().isEmpty(), future.join().report());
            h.assertTrue(count(companion, Items.ARROW) == 12 && count(companion, Items.EMERALD) == 2 && fletcher.getOffers().getFirst().getUses() == 3,
                    "Arrows " + count(companion, Items.ARROW) + ", emeralds " + count(companion, Items.EMERALD) + " | " + future.join().report());
        });
    }

    /** The first fletcher has one use left; a second one has the same deal: the rest is had from him. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600, batch = "trade9")
    public static void whenOneDealRunsOutTheNextTraderWithTheSameDealIsUsed(GameTestHelper h) {
        ground(h);
        Villager first = trader(h, VillagerProfession.FLETCHER, 14, 12, arrowsForEmerald(5, 1));
        Villager second = trader(h, VillagerProfession.FLETCHER, 20, 20, arrowsForEmerald(5, 3));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 5));
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:arrow\",\"count\":8}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.ARROW) == 8 && first.getOffers().getFirst().getUses() == 1 && second.getOffers().getFirst().getUses() == 1,
                    "Arrows " + count(companion, Items.ARROW) + ", uses " + first.getOffers().getFirst().getUses() + "/" + second.getOffers().getFirst().getUses() + " | " + future.join().report());
        });
    }

    /** "Sell the wheat" with no number: wheat goes while the farmer's limit lasts (two deals of twenty), ten is left, and the report says it was the limit. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600, batch = "trade10")
    public static void aSaleWithNoNumberGivesAwayWhatTheLimitTakes(GameTestHelper h) {
        ground(h);
        Villager farmer = trader(h, VillagerProfession.FARMER, 14, 12, new MerchantOffer(new ItemCost(Items.WHEAT, 20), Optional.empty(), new ItemStack(Items.EMERALD), 2, 2, 0f));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.WHEAT, 50));
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:emerald\",\"pay\":\"minecraft:wheat\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(companion, Items.EMERALD) == 2 && count(companion, Items.WHEAT) == 10 && farmer.getOffers().getFirst().getUses() == 2 && report.contains("лимит"),
                    "Emeralds " + count(companion, Items.EMERALD) + ", wheat " + count(companion, Items.WHEAT) + " | " + report);
        });
    }

    /** The price runs out half way: two emeralds, ten arrows asked: eight arrows, and the report says 8 of 10 and why. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600, batch = "trade11")
    public static void whenThePriceRunsOutTheReportSaysHowFarItGot(GameTestHelper h) {
        ground(h);
        Villager fletcher = trader(h, VillagerProfession.FLETCHER, 14, 12, arrowsForEmerald(5));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 2));
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:arrow\",\"count\":10}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(companion, Items.ARROW) == 8 && count(companion, Items.EMERALD) == 0 && fletcher.getOffers().getFirst().getUses() == 2
                    && report.contains("8 из 10") && report.contains("плата"), "Arrows " + count(companion, Items.ARROW) + " | " + report);
        });
    }

    /** The bag has no cell for the goods and the price frees none: nothing is paid, the deal is not used, and the report says the bag is full. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = "trade12")
    public static void aFullBagRefusesTheTradeBeforePaying(GameTestHelper h) {
        ground(h);
        Villager fletcher = trader(h, VillagerProfession.FLETCHER, 14, 12, arrowsForEmerald(5));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 2));
        for (int slot = 1; slot < companion.getContainerSize(); slot++) companion.setItem(slot, new ItemStack(Items.DIRT, 64));
        var future = run(h, companion, ARROWS);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Сумка полна") && count(companion, Items.EMERALD) == 2 && count(companion, Items.ARROW) == 0
                    && fletcher.getOffers().getFirst().getUses() == 0 && fletcher.getVillagerXp() == 0, "Emeralds " + count(companion, Items.EMERALD) + " | " + report);
        });
    }

    /** "Sell 100 wheat": a number over 64 passes the codec and the server (it rides in the order), five deals of twenty are made. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600, batch = "trade13")
    public static void aSaleOfMoreThanSixtyFourPiecesIsAccepted(GameTestHelper h) {
        ground(h);
        Villager farmer = trader(h, VillagerProfession.FARMER, 14, 12, new MerchantOffer(new ItemCost(Items.WHEAT, 20), Optional.empty(), new ItemStack(Items.EMERALD), 5, 2, 0f));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.WHEAT, 64));
        companion.setItem(1, new ItemStack(Items.WHEAT, 36));
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:emerald\",\"pay\":\"minecraft:wheat\",\"count\":100}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(future.join().skipped().isEmpty(), future.join().report());
            h.assertTrue(count(companion, Items.EMERALD) == 5 && count(companion, Items.WHEAT) == 0 && farmer.getOffers().getFirst().getUses() == 5,
                    "Emeralds " + count(companion, Items.EMERALD) + ", wheat " + count(companion, Items.WHEAT) + " | " + future.join().report());
        });
    }

    /**
     * «Продай пшеницу» with no number, all of it sold before the deal runs out: that is the order done, not a stop — game 18.11 said
     * «Продал морковь × 22 за изумруд: плата кончилась», as if he had come up short.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "trade15")
    public static void aSaleOfAllHeHasEndsWithoutAReason(GameTestHelper h) {
        ground(h);
        trader(h, VillagerProfession.FARMER, 14, 12, new MerchantOffer(new ItemCost(Items.WHEAT, 20), Optional.empty(), new ItemStack(Items.EMERALD), 5, 2, 0f));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.WHEAT, 40));
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:emerald\",\"pay\":\"minecraft:wheat\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(companion, Items.EMERALD) == 2 && count(companion, Items.WHEAT) == 0 && !report.contains("плата") && !report.contains("кончил"), report);
        });
    }

    /** The same with 45: two deals, and the five left (less than a deal) are said, not hidden (review round 3). */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "trade16")
    public static void aSaleOfAllHeHasSaysWhatIsLeft(GameTestHelper h) {
        ground(h);
        trader(h, VillagerProfession.FARMER, 14, 12, new MerchantOffer(new ItemCost(Items.WHEAT, 20), Optional.empty(), new ItemStack(Items.EMERALD), 5, 2, 0f));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setItem(0, new ItemStack(Items.WHEAT, 45));
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:emerald\",\"pay\":\"minecraft:wheat\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(companion, Items.EMERALD) == 2 && count(companion, Items.WHEAT) == 5 && report.contains("осталось 5"), report);
        });
    }

    /** Five renamed emeralds and sixty-four plain ones, a price of fifteen: the renamed ones count and the rest is taken from the plain stack. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "trade14")
    public static void aPriceIsTakenFromStacksOfDifferentComponents(GameTestHelper h) {
        ground(h);
        Villager fletcher = trader(h, VillagerProfession.FLETCHER, 14, 12, new MerchantOffer(new ItemCost(Items.EMERALD, 15), Optional.empty(), new ItemStack(Items.ARROW, 4), 3, 5, 0f));
        CompanionEntity companion = spawn(h, 10, 16);
        ItemStack renamed = new ItemStack(Items.EMERALD, 5);
        renamed.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Lucky"));
        companion.setItem(0, renamed);
        companion.setItem(1, new ItemStack(Items.EMERALD, 64));
        var future = run(h, companion, ARROWS);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.EMERALD) == 54 && count(companion, Items.ARROW) == 4 && fletcher.getOffers().getFirst().getUses() == 1,
                    "Emeralds " + count(companion, Items.EMERALD) + ", arrows " + count(companion, Items.ARROW) + " | " + future.join().report());
        });
    }

    /** Every wrong field spoils the whole plan: an unknown item or enchantment, a level out of range, a flag that is no flag, a profession that does not exist, no item. */
    @GameTest(template = "empty")
    public static void theTradeStepIsCheckedByTheCodec(GameTestHelper h) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"trade_items\",\"item\":\"enchanted_book\",\"enchantment\":\"mending\",\"level\":1,\"pay\":\"emerald\",\"profession\":\"librarian\",\"consent\":true}]"));
        h.assertTrue(plan.size() == 1 && plan.getFirst().type().equals("trade_items"), "A full step was refused");
        for (String bad : new String[]{"\"item\":\"minecraft:no_such_thing\"", "\"item\":\"minecraft:enchanted_book\",\"enchantment\":\"minecraft:no_such_spell\"",
                "\"item\":\"minecraft:enchanted_book\",\"enchantment\":\"minecraft:mending\",\"level\":0", "\"item\":\"minecraft:enchanted_book\",\"enchantment\":\"minecraft:mending\",\"level\":99",
                "\"item\":\"minecraft:enchanted_book\"", "\"item\":\"minecraft:arrow\",\"pay\":\"minecraft:no_such_thing\"", "\"item\":\"minecraft:arrow\",\"profession\":\"wizard\"",
                "\"item\":\"minecraft:arrow\",\"consent\":\"yes\"", "\"item\":\"minecraft:arrow\",\"level\":2", "\"item\":\"\"", "\"item\":\"minecraft:arrow\",\"count\":0"}) {
            try {
                CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"come\"},{\"action\":\"trade_items\"," + bad + "}]"));
                h.fail("Accepted a trade step with " + bad);
            } catch (IllegalStateException expected) { /* the whole plan is refused */ }
        }
        h.succeed();
    }

    /** Stopped on the way to the trader: nothing is paid, the trader is as he was. */
    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "trade6")
    public static void stoppingOnTheWayCostsNothing(GameTestHelper h) {
        ground(h);
        Villager far = trader(h, VillagerProfession.FLETCHER, 28, 28, arrowsForEmerald(5));
        CompanionEntity companion = spawn(h, 4, 4);
        companion.setItem(0, new ItemStack(Items.EMERALD, 2));
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(player, "trade_items", new dev.aicompanion.entity.CompanionTradeOrder("minecraft:arrow", "", 0, "", "", false, 0).pack(), 1).success(), "Not accepted");
        h.runAfterDelay(30, () -> {
            h.assertTrue(companion.isWorking(), "Finished before the stop");
            companion.stopGathering();
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(!companion.isWorking() && count(companion, Items.EMERALD) == 2 && count(companion, Items.ARROW) == 0, "Still working or paid: emeralds " + count(companion, Items.EMERALD));
            h.assertTrue(far.getOffers().getFirst().getUses() == 0 && far.getVillagerXp() == 0, "The trader was touched");
            h.succeed();
        });
    }

    private static MerchantOffer arrowsForEmerald(int xp) { return arrowsForEmerald(xp, 3); }

    private static MerchantOffer arrowsForEmerald(int xp, int maxUses) {
        return new MerchantOffer(new ItemCost(Items.EMERALD), Optional.empty(), new ItemStack(Items.ARROW, 4), maxUses, xp, 0f);
    }

    private static Villager freshFletcher(GameTestHelper h, int x, int z) {
        Villager villager = trader(h, VillagerProfession.FLETCHER, x, z, arrowsForEmerald(5));
        villager.setVillagerData(villager.getVillagerData().setLevel(1));
        return villager;
    }

    private static int count(CompanionEntity companion, Item item) {
        int total = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) total += companion.getItem(slot).getCount();
        return total;
    }
}

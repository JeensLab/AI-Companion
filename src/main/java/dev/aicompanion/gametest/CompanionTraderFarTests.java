package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

import static dev.aicompanion.gametest.CompanionTraderTests.GROUND;
import static dev.aicompanion.gametest.CompanionTraderTests.mendingBook;
import static dev.aicompanion.gametest.CompanionTraderTests.run;
import static dev.aicompanion.gametest.CompanionTraderTests.spawn;
import static dev.aicompanion.gametest.CompanionTraderTests.trader;

/**
 * The round of a trader remembered far off: when nobody trades within 64 blocks he walks to the nearest remembered one (same
 * dimension, at most 500 blocks), does the round there and comes back; farther off or in another dimension he says so and stays;
 * a remembered trader who is not at his spot is forgotten, near or far.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionTraderFarTests {
    private static final Set<ChunkPos> forced = new HashSet<>();
    /** The corners of the strips laid this batch: the ground left outside the structure is taken away after it, or a later test looking for dirt would find it. */
    private static final java.util.List<BlockPos[]> strips = new java.util.ArrayList<>();

    @net.minecraft.gametest.framework.AfterBatch(batch = "tradersfar1")
    public static void after1(ServerLevel level) { cleanUp(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tradersfar2")
    public static void after2(ServerLevel level) { cleanUp(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tradersfar3")
    public static void after3(ServerLevel level) { cleanUp(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tradersfar4")
    public static void after4(ServerLevel level) { cleanUp(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tradersfar7")
    public static void after7(ServerLevel level) { cleanUp(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tradersfar5")
    public static void after5(ServerLevel level) { cleanUp(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "tradersfar6")
    public static void after6(ServerLevel level) { cleanUp(level); }

    private static void cleanUp(ServerLevel level) {
        for (BlockPos[] box : strips) for (BlockPos pos : BlockPos.betweenClosed(box[0], box[1])) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        strips.clear();
        for (ChunkPos chunk : forced) level.setChunkForced(chunk.x, chunk.z, false);
        forced.clear();
        // The far villagers stand outside the test structure, which the framework does not clear: a later scout would find them.
        for (var villager : java.util.List.copyOf(level.getEntities(EntityType.VILLAGER, v -> true))) villager.discard();
        CompanionFixtureCleanup.clean(level);
    }

    /** A remembered armorer 86 blocks off, offers changed since: he walks there, reads the new ones, says so, and is back by the player. */
    @GameTest(template = "tall_tree", timeoutTicks = 20 * 240, batch = "tradersfar1")
    public static void aRememberedTraderFarOffIsWalkedToAndAsked(GameTestHelper h) {
        strip(h, 110);
        Villager far = trader(h, VillagerProfession.ARMORER, 90, 16, new MerchantOffer(new ItemCost(Items.EMERALD, 15), new ItemStack(Items.DIAMOND_HELMET), 3, 1, 0));
        CompanionEntity companion = spawn(h, 4, 16);
        companion.traders().remember(far, h.getLevel());
        MerchantOffers changed = new MerchantOffers();
        changed.add(new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.DIAMOND_CHESTPLATE), 3, 1, 0));
        far.setOffers(changed);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var future = run(h, companion, "[{\"action\":\"scout_traders\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Бронник"), report);
            var entry = companion.traders().entry(far.getUUID());
            h.assertTrue(entry != null && entry.deals().getFirst().get().item().equals("minecraft:diamond_chestplate"), "Memory: " + entry);
            h.assertTrue(companion.distanceTo(player) < 12, "Not back by the player: " + companion.distanceTo(player));
        });
    }

    /** The remembered one is not at his spot (gone), another stands there: the gone is forgotten and the report says so. */
    @GameTest(template = "tall_tree", timeoutTicks = 20 * 240, batch = "tradersfar2")
    public static void aRememberedTraderWhoIsNotThereIsForgottenFarOff(GameTestHelper h) {
        strip(h, 110);
        Villager gone = trader(h, VillagerProfession.ARMORER, 90, 16, mendingBook(h));
        Villager there = trader(h, VillagerProfession.LIBRARIAN, 94, 18, mendingBook(h));
        CompanionEntity companion = spawn(h, 4, 16);
        companion.traders().remember(gone, h.getLevel());
        gone.discard();
        var future = run(h, companion, "[{\"action\":\"scout_traders\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Забыл") && report.contains("Библиотекарь"), report);
            h.assertTrue(companion.traders().entry(gone.getUUID()) == null && companion.traders().entry(there.getUUID()) != null, "Memory: " + companion.traders().entries());
        });
    }

    /** The same for a round near him, no walk: a remembered trader gone from his spot within the round's 64 blocks is forgotten. */
    @GameTest(template = "tall_tree", timeoutTicks = 20 * 120, batch = "tradersfar3")
    public static void aRememberedTraderWhoIsNotThereIsForgottenNearToo(GameTestHelper h) {
        strip(h, 31);
        Villager gone = trader(h, VillagerProfession.ARMORER, 20, 18, mendingBook(h));
        Villager there = trader(h, VillagerProfession.LIBRARIAN, 14, 12, mendingBook(h));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.traders().remember(gone, h.getLevel());
        gone.discard();
        var future = run(h, companion, "[{\"action\":\"scout_traders\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Забыл"), future.join().report());
            h.assertTrue(companion.traders().entry(gone.getUUID()) == null && companion.traders().entry(there.getUUID()) != null, "Memory: " + companion.traders().entries());
        });
    }

    /** Remembered only beyond 500 blocks, or only in another dimension: he refuses at once, asks to be led, and does not walk. */
    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "tradersfar4")
    public static void tooFarOrAnotherDimensionIsRefusedWithoutAWalk(GameTestHelper h) {
        strip(h, 31);
        CompanionEntity companion = spawn(h, 10, 16);
        BlockPos here = h.absolutePos(new BlockPos(10, GROUND + 1, 16));
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 3, 1, 0));
        Villager beyond = unplaced(h, here.getX() + 600, here.getZ(), offers);
        companion.traders().remember(beyond, h.getLevel());
        var first = run(h, companion, "[{\"action\":\"scout_traders\"}]");
        java.util.List<java.util.concurrent.CompletableFuture<dev.aicompanion.client.CompanionPlanRunner.Outcome>> second = new java.util.ArrayList<>();
        h.succeedWhen(() -> {
            h.assertTrue(first.isDone(), "No answer to the first order");
            if (second.isEmpty()) {
                h.assertTrue(first.join().report().contains("Далеко, отведи меня") && !companion.isWorking(), "Beyond 500: " + first.join().report());
                companion.traders().forget(beyond.getUUID());
                companion.traders().remember(unplaced(h, 0, 0, offers), h.getLevel().getServer().getLevel(Level.NETHER));
                second.add(run(h, companion, "[{\"action\":\"scout_traders\"}]"));
            }
            h.assertTrue(second.getFirst().isDone(), "No answer to the second order");
            h.assertTrue(second.getFirst().join().report().contains("Далеко, отведи меня") && !companion.isWorking(), "Other dimension: " + second.getFirst().join().report());
        });
    }

    /** Chunk loading off: a remembered village 90 blocks off is not walked to (he would stop where the world stops), he says why. */
    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "tradersfar7")
    public static void withoutChunkLoadingAFarScoutIsRefused(GameTestHelper h) {
        strip(h, 31);
        CompanionEntity companion = spawn(h, 10, 16);
        companion.setLifestyle(false, false);
        BlockPos here = h.absolutePos(new BlockPos(10, GROUND + 1, 16));
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 3, 1, 0));
        companion.traders().remember(unplaced(h, here.getX() + 90, here.getZ(), offers), h.getLevel());
        var future = run(h, companion, "[{\"action\":\"scout_traders\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone(), "No answer");
            h.assertTrue(future.join().report().contains("подгрузка чанков") && !companion.isWorking(), future.join().report());
        });
    }

    private static MerchantOffer arrowsFor(int emeralds) {
        return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), java.util.Optional.empty(), new ItemStack(Items.ARROW, 4), 3, 5, 0f);
    }

    private static int count(CompanionEntity companion, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) total += companion.getItem(slot).getCount();
        return total;
    }

    /** Nobody trades within 64 blocks, a fletcher is remembered 86 off with the deal: he walks there, buys by the live offer, the goods are in his bag, and he is back by the player. */
    @GameTest(template = "tall_tree", timeoutTicks = 20 * 240, batch = "tradersfar5")
    public static void aTradeIsMadeWithARememberedTraderFarOff(GameTestHelper h) {
        strip(h, 110);
        Villager far = trader(h, VillagerProfession.FLETCHER, 90, 16, arrowsFor(1));
        CompanionEntity companion = spawn(h, 4, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 2));
        companion.traders().remember(far, h.getLevel());
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:arrow\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(future.join().skipped().isEmpty(), future.join().report());
            h.assertTrue(count(companion, Items.EMERALD) == 1 && count(companion, Items.ARROW) == 4 && far.getOffers().getFirst().getUses() == 1,
                    "Emeralds " + count(companion, Items.EMERALD) + ", arrows " + count(companion, Items.ARROW) + ", uses " + far.getOffers().getFirst().getUses());
            h.assertTrue(companion.distanceTo(player) < 12, "Not back by the player: " + companion.distanceTo(player));
        });
    }

    /** The cheaper remembered fletcher is gone from his spot: he is forgotten, the next remembered one with the deal is walked to and traded with. */
    @GameTest(template = "tall_tree", timeoutTicks = 20 * 240, batch = "tradersfar6")
    public static void aRememberedTraderWhoIsNotThereIsForgottenAndTheNextIsUsed(GameTestHelper h) {
        strip(h, 110);
        Villager gone = trader(h, VillagerProfession.FLETCHER, 80, 16, arrowsFor(1));
        Villager there = trader(h, VillagerProfession.FLETCHER, 100, 16, arrowsFor(2));
        CompanionEntity companion = spawn(h, 4, 16);
        companion.setItem(0, new ItemStack(Items.EMERALD, 3));
        companion.traders().remember(gone, h.getLevel());
        companion.traders().remember(there, h.getLevel());
        gone.discard();
        var future = run(h, companion, "[{\"action\":\"trade_items\",\"item\":\"minecraft:arrow\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(future.join().skipped().isEmpty(), future.join().report());
            h.assertTrue(companion.traders().entry(gone.getUUID()) == null && companion.traders().entry(there.getUUID()) != null, "Memory: " + companion.traders().entries());
            h.assertTrue(count(companion, Items.EMERALD) == 1 && count(companion, Items.ARROW) == 4 && there.getOffers().getFirst().getUses() == 1,
                    "Emeralds " + count(companion, Items.EMERALD) + ", arrows " + count(companion, Items.ARROW));
        });
    }

    /** A villager that is not in any world: only its spot and offers are read into the memory. */
    private static Villager unplaced(GameTestHelper h, int x, int z, MerchantOffers offers) {
        Villager villager = EntityType.VILLAGER.create(h.getLevel());
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.ARMORER, 2));
        villager.setPos(x, GROUND + 1, z);
        villager.setOffers(offers);
        return villager;
    }

    /** A flat strip of ground {@code length} blocks along x, through the walls round the test structures; its chunks are kept loaded for the test. */
    private static void strip(GameTestHelper h, int length) {
        strips.add(new BlockPos[]{h.absolutePos(new BlockPos(0, GROUND - 1, 0)), h.absolutePos(new BlockPos(length, GROUND, 31))});
        for (int x = 0; x <= length; x++) for (int z = 0; z < 32; z++) {
            h.setBlock(new BlockPos(x, GROUND - 1, z), Blocks.DIRT);
            h.setBlock(new BlockPos(x, GROUND, z), Blocks.GRASS_BLOCK);
            // The strip runs through the barrier walls round the neighbouring test structures: the air over it is cleared.
            for (int y = GROUND + 1; y <= GROUND + 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int x = 0; x <= length + 8; x += 8) for (int z = 0; z < 32; z += 8) {
            ChunkPos chunk = new ChunkPos(h.absolutePos(new BlockPos(Math.min(x, length), GROUND, z)));
            if (forced.add(chunk)) h.getLevel().setChunkForced(chunk.x, chunk.z, true);
        }
    }
}

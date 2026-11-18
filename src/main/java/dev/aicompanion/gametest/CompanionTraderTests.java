package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionTradeWords;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * «Сходи узнай, что у торговцев»: the companion walks to every trader round him, reads the real offers on the server and says
 * what he found; the sleeping are counted, not asked, and nobody near is said so. Villagers stand still (no AI) so that the
 * test does not depend on where they would wander.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionTraderTests {
    static final int GROUND = 5;

    // One batch for each test that has villagers: the scout looks 64 blocks round, so the villagers of a neighbour test must be gone first.
    /**
     * The villagers of a finished batch stay in the world, and the next batch stands some 40 blocks off: within a scout's or a trade's
     * 64 blocks (one left with a deal in stock sent the next test's trade to him) and a village search's 24. They are taken away.
     */
    static void afterVillagers(net.minecraft.server.level.ServerLevel level) {
        for (var villager : java.util.List.copyOf(level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.npc.AbstractVillager.class), v -> true))) villager.discard();
        CompanionFixtureCleanup.afterAtomicTests(level);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = "traders1")
    public static void after1(net.minecraft.server.level.ServerLevel level) { afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "traders2")
    public static void after2(net.minecraft.server.level.ServerLevel level) { afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "traders3")
    public static void after3(net.minecraft.server.level.ServerLevel level) { afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "traders4")
    public static void after4(net.minecraft.server.level.ServerLevel level) { afterVillagers(level); }

    @net.minecraft.gametest.framework.AfterBatch(batch = "traders5")
    public static void after5(net.minecraft.server.level.ServerLevel level) { afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "traders6")
    public static void after6(net.minecraft.server.level.ServerLevel level) { afterVillagers(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "traders7")
    public static void after7(net.minecraft.server.level.ServerLevel level) { afterVillagers(level); }

    /** Two traders with their own offers, a jobless villager and a child: the report names the two and the memory holds their offers. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "traders1")
    public static void scoutingNamesTheTradersAndRemembersTheirOffers(GameTestHelper h) {
        ground(h);
        Villager librarian = trader(h, VillagerProfession.LIBRARIAN, 14, 12, mendingBook(h));
        Villager armorer = trader(h, VillagerProfession.ARMORER, 20, 18, new MerchantOffer(new ItemCost(Items.EMERALD, 15), new ItemStack(Items.DIAMOND_HELMET), 3, 1, 0));
        Villager jobless = trader(h, VillagerProfession.NONE, 8, 20, new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 3, 1, 0));
        Villager child = trader(h, VillagerProfession.FARMER, 22, 8, new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 3, 1, 0));
        child.setAge(-24000);
        CompanionEntity companion = spawn(h, 10, 16);
        var future = run(h, companion, "[{\"action\":\"scout_traders\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Библиотекарь") && report.contains("Бронник") && !report.contains("Фермер"), report);
            var memory = companion.traders();
            h.assertTrue(memory.entries().size() == 2 && memory.entry(jobless.getUUID()) == null && memory.entry(child.getUUID()) == null, "Memory: " + memory.entries());
            var book = memory.entry(librarian.getUUID());
            h.assertTrue(book != null && book.kind().equals("librarian") && book.deals().size() == 1 && book.deals().getFirst().get().item().equals("minecraft:enchanted_book")
                    && book.deals().getFirst().get().enchantment().equals("minecraft:mending") && book.deals().getFirst().left() == 3, "Librarian: " + book);
            var helmet = memory.entry(armorer.getUUID());
            h.assertTrue(helmet != null && helmet.deals().getFirst().give().count() == 15 && helmet.deals().getFirst().get().item().equals("minecraft:diamond_helmet"), "Armorer: " + helmet);
        });
    }

    /** A trader asleep in his bed is not asked: no offers of his are remembered and the report says he sleeps. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "traders2")
    public static void aSleepingTraderIsCountedNotAsked(GameTestHelper h) {
        ground(h);
        Villager awake = trader(h, VillagerProfession.LIBRARIAN, 14, 12, mendingBook(h));
        Villager asleep = trader(h, VillagerProfession.ARMORER, 20, 18, new MerchantOffer(new ItemCost(Items.EMERALD, 15), new ItemStack(Items.DIAMOND_HELMET), 3, 1, 0));
        BlockPos bed = h.absolutePos(new BlockPos(20, GROUND + 1, 19));
        h.getLevel().setBlockAndUpdate(bed, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD));
        asleep.startSleeping(bed);
        h.assertTrue(asleep.isSleeping(), "The villager did not go to sleep");
        CompanionEntity companion = spawn(h, 10, 16);
        var future = run(h, companion, "[{\"action\":\"scout_traders\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("спит") && report.contains("Библиотекарь"), report);
            h.assertTrue(companion.traders().entries().size() == 1 && companion.traders().entry(awake.getUUID()) != null && companion.traders().entry(asleep.getUUID()) == null,
                    "Memory: " + companion.traders().entries());
        });
    }

    /** No villager round him: the order fails at once and says so, with a way on. */
    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "traders3")
    public static void noVillagersNearIsSaidSo(GameTestHelper h) {
        ground(h);
        CompanionEntity companion = spawn(h, 10, 16);
        var future = run(h, companion, "[{\"action\":\"scout_traders\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running");
            String report = future.join().report();
            h.assertTrue(report.contains("Деревни рядом не вижу") && companion.traders().entries().isEmpty(), report);
        });
    }

    /** The step carries no item, count, place or other number: whatever else is written spoils the whole plan. */
    @GameTest(template = "empty")
    public static void scoutTradersStepIsCheckedByTheCodec(GameTestHelper h) {
        h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"scout_traders\"}]")).getFirst().type().equals("scout_traders"), "A plain step was refused");
        for (String bad : new String[]{"\"count\":2", "\"count\":0", "\"item\":\"minecraft:cow\"", "\"location\":\"minecraft:chest\""}) {
            try {
                CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"come\"},{\"action\":\"scout_traders\"," + bad + "}]"));
                h.fail("Accepted a scout step with " + bad);
            } catch (IllegalStateException expected) { /* the whole plan is refused */ }
        }
        h.succeed();
    }

    /** Stopped on the way to a trader: the work ends as stopped, nothing is remembered, the villager is as he was. */
    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "traders4")
    public static void stoppingTheRoundEndsItQuietly(GameTestHelper h) {
        ground(h);
        Villager far = trader(h, VillagerProfession.LIBRARIAN, 28, 28, mendingBook(h));
        CompanionEntity companion = spawn(h, 4, 4);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(player, "scout_traders", "", 1).success(), "Not accepted");
        long id = companion.activeWorkId();
        h.runAfterDelay(30, () -> {
            h.assertTrue(companion.isWorking(), "Finished before the stop");
            companion.stopGathering();
        });
        h.runAfterDelay(60, () -> {
            CompanionActionResult result = companion.workResult(id);
            h.assertTrue(!companion.isWorking() && result != null && !result.success(), "Still working or reported success: " + result);
            h.assertTrue(companion.traders().entries().isEmpty() && far.isAlive() && far.getOffers().size() == 1, "Memory or villager changed");
            h.succeed();
        });
    }

    /** The memory goes into the saved tag and comes out the same on a new entity; a tag without the key is an empty memory; the one who comes back after death has it too. */
    @GameTest(template = "tall_tree", timeoutTicks = 20 * 40, batch = "traders5")
    public static void theMemoryIsSavedAndComesBackWithTheCompanion(GameTestHelper h) {
        ground(h);
        Villager librarian = trader(h, VillagerProfession.LIBRARIAN, 14, 12, mendingBook(h));
        Villager armorer = trader(h, VillagerProfession.ARMORER, 20, 18, new MerchantOffer(new ItemCost(Items.EMERALD, 15), java.util.Optional.of(new ItemCost(Items.IRON_INGOT, 2)), new ItemStack(Items.DIAMOND_HELMET), 3, 1, 0));
        CompanionEntity companion = spawn(h, 10, 16);
        companion.traders().remember(librarian, h.getLevel());
        companion.traders().remember(armorer, h.getLevel());
        var before = companion.traders().entries();
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        companion.saveWithoutId(tag);
        CompanionEntity copy = AICompanionMod.COMPANION.get().create(h.getLevel());
        copy.load(tag);
        h.assertTrue(copy.traders().entries().size() == 2 && copy.traders().entries().containsAll(before), "Loaded: " + copy.traders().entries());
        tag.remove("Traders");
        CompanionEntity old = AICompanionMod.COMPANION.get().create(h.getLevel());
        old.load(tag);
        h.assertTrue(old.traders().entries().isEmpty(), "An old save has traders: " + old.traders().entries());
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        companion.kill();
        h.succeedWhen(() -> {
            CompanionEntity back = CompanionEvents.getOwnedCompanion(owner);
            h.assertTrue(back != null && back.isAlive() && back != companion, "He did not come back");
            h.assertTrue(back.traders().entries().size() == 2 && back.traders().entries().containsAll(before), "Memory lost at death: " + back.traders().entries());
        });
    }

    /** The 31st trader pushes out the one seen longest ago. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "traders6")
    public static void theThirtyFirstTraderPushesOutTheOldest(GameTestHelper h) {
        ground(h);
        CompanionEntity companion = spawn(h, 10, 16);
        Villager first = trader(h, VillagerProfession.LIBRARIAN, 3, 3, mendingBook(h));
        companion.traders().remember(first, h.getLevel());
        h.runAfterDelay(5, () -> {
            Villager last = null;
            for (int i = 0; i < 30; i++) {
                last = trader(h, VillagerProfession.ARMORER, 4 + i % 25, 6 + i / 25 * 3, mendingBook(h));
                companion.traders().remember(last, h.getLevel());
            }
            h.assertTrue(companion.traders().entries().size() == 30 && companion.traders().entry(first.getUUID()) == null && companion.traders().entry(last.getUUID()) != null,
                    "Size " + companion.traders().entries().size() + ", the oldest is still there: " + (companion.traders().entry(first.getUUID()) != null));
            h.succeed();
        });
    }

    /** 30 traders of 10 deals each: the line the entity syncs stays under the string limit of the data, and is there on the client side getter. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "traders7")
    public static void theSyncedLineOfThirtyFullTradersFitsTheDataLimit(GameTestHelper h) {
        ground(h);
        CompanionEntity companion = spawn(h, 10, 16);
        for (int i = 0; i < 30; i++) {
            Villager villager = trader(h, VillagerProfession.LIBRARIAN, 3 + i % 25, 6 + i / 25 * 3, mendingBook(h));
            MerchantOffers offers = new MerchantOffers();
            for (int d = 0; d < 10; d++) offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 15), java.util.Optional.of(new ItemCost(Items.PAPER, 24)), mendingBook(h).getResult(), 3, 1, 0));
            villager.setOffers(offers);
            companion.traders().remember(villager, h.getLevel());
        }
        h.succeedWhen(() -> {
            String line = companion.getTradersForAi();
            h.assertTrue(!line.isEmpty() && line.length() <= 32767, "Synced length " + line.length());
            h.assertTrue(line.contains("15 emerald+24 paper->1 enchanted_book[mending 1]") && line.contains("librarian") && line.contains("только что"), "Line: " + line.substring(0, Math.min(400, line.length())));
        });
    }

    /** What the snapshot adds only for trade talk. */
    @GameTest(template = "empty")
    public static void tradeWordsPickTheMessagesAboutTrading(GameTestHelper h) {
        for (String yes : new String[]{"купи книгу на починку", "что продают жители", "Обменяй пшеницу на изумруды", "сколько стоит зачарованная книга"})
            h.assertTrue(CompanionTradeWords.about(yes), "Not about trading: " + yes);
        for (String no : new String[]{"пойдём домой", "руби дерево", "что у тебя в сумке"})
            h.assertTrue(!CompanionTradeWords.about(no), "Taken for trading: " + no);
        // «Да» to «он ещё не торговал… меняем?» has no trade word: the list still rides along for a while after trade talk.
        long t = 1_000_000L;
        h.assertTrue(CompanionTradeWords.relevant("купи стрелы", t) && CompanionTradeWords.relevant("да", t + 60_000), "The answer right after trade talk lost the list");
        h.assertTrue(!CompanionTradeWords.relevant("да", t + CompanionTradeWords.FOLLOW_UP_MILLIS + 1), "The list stayed long after trade talk");
        h.succeed();
    }

    // ---- the arena ----

    static java.util.concurrent.CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity companion, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-scout", () -> false));
    }

    static MerchantOffer mendingBook(GameTestHelper h) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var mending = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(mending, 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return new MerchantOffer(new ItemCost(Items.EMERALD, 15), book, 3, 1, 0);
    }

    /** A villager that stands where it is put, with the profession, the level and the one offer the test gives it. */
    static Villager trader(GameTestHelper h, VillagerProfession profession, int x, int z, MerchantOffer offer) {
        Villager villager = h.spawn(EntityType.VILLAGER, x, GROUND + 1, z);
        villager.setNoAi(true);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, profession, 2));
        MerchantOffers offers = new MerchantOffers();
        offers.add(offer);
        villager.setOffers(offers);
        return villager;
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            if (y <= GROUND) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            else if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
    }

    static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x - 3, GROUND + 1, z - 3));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
}

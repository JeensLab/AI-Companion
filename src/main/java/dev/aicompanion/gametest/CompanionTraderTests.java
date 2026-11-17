package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
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
    private static final int GROUND = 5;

    // One batch for each test that has villagers: the scout looks 64 blocks round, so the villagers of a neighbour test must be gone first.
    @net.minecraft.gametest.framework.AfterBatch(batch = "traders1")
    public static void after1(net.minecraft.server.level.ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "traders2")
    public static void after2(net.minecraft.server.level.ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "traders3")
    public static void after3(net.minecraft.server.level.ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch = "traders4")
    public static void after4(net.minecraft.server.level.ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

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

    // ---- the arena ----

    private static java.util.concurrent.CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity companion, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-scout", () -> false));
    }

    private static MerchantOffer mendingBook(GameTestHelper h) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var mending = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(mending, 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return new MerchantOffer(new ItemCost(Items.EMERALD, 15), book, 3, 1, 0);
    }

    /** A villager that stands where it is put, with the profession, the level and the one offer the test gives it. */
    private static Villager trader(GameTestHelper h, VillagerProfession profession, int x, int z, MerchantOffer offer) {
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

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
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

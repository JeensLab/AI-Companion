package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionJokeBank;
import dev.aicompanion.CompanionJokeBank.Joke;
import dev.aicompanion.CompanionJokeBank.Theme;
import dev.aicompanion.CompanionJokes;
import dev.aicompanion.entity.CompanionHappenings;
import dev.aicompanion.entity.CompanionJokeContext;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionJokeBankTests {
    @GameTest(template = "empty")
    public static void oldLinesAndMultilineBlocksNormalizeAndCountRejections(GameTestHelper h) {
        var old = CompanionJokeBank.parse("\uFEFF# comment\r\nБайка 123.\r\nБайка  123.\nПлохо\u0000.\n" + "я ".repeat(151));
        h.assertTrue(old.jokes().size() == 1 && old.duplicates() == 1 && old.invalid() == 2, "Legacy validation/counts");
        var blocks = CompanionJokeBank.parse("# format: blocks\nПервая строка.\n\nВторая строка.\n---\nИстория 2.\n");
        h.assertTrue(blocks.jokes().size() == 2 && blocks.jokes().getFirst().text().equals("Первая строка. Вторая строка."), "Blocks were split into lines");
        h.assertTrue(CompanionJokeBank.valid("я ".repeat(149) + "я"), "150 words rejected");
        h.assertTrue(!CompanionJokeBank.valid("я ".repeat(150) + "я"), "151 words accepted");
        h.assertTrue(CompanionJokeBank.valid("я".repeat(1500)) && !CompanionJokeBank.valid("я".repeat(1501)), "Character boundary");
        h.assertTrue(CompanionJokeBank.combine(blocks.jokes(), blocks.jokes()).size() == 2, "Merge duplicates");
        var many = new StringBuilder();
        for (int i = 0; i < 10002; i++) many.append("История ").append(i).append('\n');
        var parsed = CompanionJokeBank.parse(many.toString());
        h.assertTrue(parsed.jokes().size() == 10000 && parsed.overflow() == 2, "Bank limit");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void optionalFileIsStrictBoundedAndNeverOverwritten(GameTestHelper h) throws Exception {
        var directory = Files.createTempDirectory("aicompanion-jokes-"); var file = directory.resolve("jokes.txt");
        try {
            h.assertTrue(CompanionJokeBank.load(file).custom().isEmpty() && !Files.exists(file), "Missing file changed");
            CompanionJokeBank.createTemplate(file);
            h.assertTrue(CompanionJokeBank.load(file).custom().isEmpty(), "Commented template became a story");
            Files.writeString(file, "История 123."); CompanionJokeBank.createTemplate(file);
            h.assertTrue(Files.readString(file).equals("История 123."), "Owner file overwritten");
            h.assertTrue(CompanionJokeBank.load(file).custom().size() == 1, "File not read");
            Files.write(file, new byte[]{(byte)0xc3, 0x28});
            boolean rejected = false;
            try { CompanionJokeBank.load(file); } catch (java.io.IOException expected) { rejected = true; }
            h.assertTrue(rejected, "Invalid UTF-8 accepted");
            Files.write(file, new byte[CompanionJokeBank.MAX_BYTES + 1]); rejected = false;
            try { CompanionJokeBank.load(file); } catch (java.io.IOException expected) { rejected = true; }
            h.assertTrue(rejected, "Oversized file accepted");
        } finally { Files.deleteIfExists(file); Files.delete(directory); }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void bankReloadKeepsCadenceAndHourlyHistory(GameTestHelper h) {
        var a = new Joke("История первая.", Set.of()); var b = new Joke("История вторая.", Set.of());
        var jokes = new CompanionJokes(); jokes.reloadBank(List.of(a,b)); jokes.reset(0, 0);
        String first = jokes.poll(6000, 5, true, 0);
        h.assertTrue(first != null, "First story missing");
        jokes.reloadBank(List.of(a,b));
        h.assertTrue(jokes.poll(11999, 5, true, 0) == null, "Reload shortened cadence");
        String second = jokes.poll(12000, 5, true, 0);
        h.assertTrue(second != null && !second.equals(first), "Reload lost previous story");
        jokes.reloadBank(List.of()); jokes.reloadBank(List.of(a,b));
        h.assertTrue(jokes.poll(18000, 5, true, 0) == null, "Language/reload lost hourly history");
        jokes.reset(0, 0); h.assertTrue(jokes.poll(6000, 5, true, 0) != null, "World reset retained history");
        h.assertTrue(CompanionJokeBank.allowBundled("ru_ru", "en", false)
                && CompanionJokeBank.allowBundled("en_us", "ru", false)
                && CompanionJokeBank.allowBundled("en_us", "en", true)
                && !CompanionJokeBank.allowBundled("en_us", "en", false), "Language signals");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void weightsUseRecentEventsBeforeLocationAndSelectionIsReproducible(GameTestHelper h) {
        var mine = new Joke("Шахта.", Set.of(Theme.MINE)); var neutral = new Joke("Дорога.", Set.of());
        h.assertTrue(CompanionJokes.weight(mine, Set.of(Theme.MINE), Set.of(Theme.MINE)) == 5
                && CompanionJokes.weight(mine, Set.of(Theme.MINE), Set.of()) == 3
                && CompanionJokes.weight(neutral, Set.of(Theme.MINE), Set.of(Theme.MINE)) == 1, "Incorrect weights");
        var random = new java.util.Random(145); int matched = 0;
        for (int i = 0; i < 1000; i++) {
            var jokes = new CompanionJokes(); jokes.reloadBank(List.of(mine,neutral)); jokes.reset(0,0);
            if ("Шахта.".equals(jokes.poll(1200,1,true,0,Set.of(),Set.of(Theme.MINE),random))) matched++;
        }
        h.assertTrue(matched > 780 && matched < 880, "Weight five was not reflected in draws: " + matched);
        var events = new CompanionHappenings(); events.add("joke_mine", "Руда", 100, CompanionHappenings.Importance.LOW);
        h.assertTrue(CompanionJokeContext.recent(events,12100).contains(Theme.MINE)
                && CompanionJokeContext.recent(events,12101).isEmpty()
                && CompanionJokeContext.recent(events,99).isEmpty(), "Recent event boundary");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void realOreBreakAndTradeEventReachTheContext(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.getPersistentData().putUUID("aicompanion:canonical_companion", java.util.UUID.randomUUID());
        h.assertTrue(!CompanionJokeContext.capture(player).surroundings().contains(Theme.TRAVEL)
                && CompanionJokeContext.capture(player,true).surroundings().contains(Theme.TRAVEL), "Stationary player got travel bonus");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
        var pos = h.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
        h.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.DIAMOND_ORE.defaultBlockState());
        h.assertTrue(player.gameMode.destroyBlock(pos), "Ore was not broken");
        h.assertTrue(CompanionJokeContext.capture(player).recent().contains(Theme.MINE), "Real ore break did not reach context");
        var bonds = dev.aicompanion.entity.CompanionBonds.get(player.server);
        for (int i = 0; i < 12; i++) bonds.happened(player.getUUID(), "advancement:test_" + i, "Другое событие",
                player.server.overworld().getGameTime(), CompanionHappenings.Importance.LOW);
        h.assertTrue(CompanionJokeContext.capture(player).recent().contains(Theme.MINE), "Journal eviction lost a recent mining bonus");
        var villager = h.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2,2,2);
        var offer = new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD), 10, 1, 0);
        villager.setTradingPlayer(player); villager.getOffers().clear(); villager.getOffers().add(offer);
        var container = new net.minecraft.world.inventory.MerchantContainer(villager);
        container.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD));
        var slot = new net.minecraft.world.inventory.MerchantResultSlot(player, villager, container, 2, 0, 0);
        var bread = slot.remove(1); slot.onTake(player, bread);
        h.assertTrue(bread.is(net.minecraft.world.item.Items.BREAD) && offer.getUses() == 1 && container.getItem(0).isEmpty(), "Trade did not consume emerald");
        h.assertTrue(CompanionJokeContext.capture(player).recent().contains(Theme.VILLAGE), "Trade event did not reach context");
        var creeper = h.spawn(net.minecraft.world.entity.EntityType.CREEPER, 3,2,3);
        player.setHealth(0); player.die(h.getLevel().damageSources().explosion(creeper,creeper));
        h.assertTrue(!player.isAlive(), "Player did not die");
        h.assertTrue(CompanionJokeContext.capture(player).recent().contains(Theme.CREEPER), "Creeper death did not reach context");
        h.succeed();
    }
}

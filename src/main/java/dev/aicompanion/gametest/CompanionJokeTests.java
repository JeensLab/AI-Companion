package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionConversation;
import dev.aicompanion.CompanionJokes;
import dev.aicompanion.CompanionJokeBank;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.HashMap;
import java.util.HashSet;

/** Offline admission and cadence checks; no real-time waits and no paid speech or model requests. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionJokeTests {
    @GameTest(template = "empty")
    public static void missingCustomFileUsesExactlyFortyMinutes(GameTestHelper h) {
        var jokes = new CompanionJokes();
        jokes.setBank(CompanionJokes.ALL, false, 0, Integer.MAX_VALUE);
        long fortyMinutes = 40L * 60 * 20;
        h.assertTrue(jokes.poll(fortyMinutes - 1, 1, true, 9999) == null, "Spoke before forty minutes");
        h.assertTrue(jokes.poll(fortyMinutes, 60, true, 9999) != null, "First forty-minute opportunity was missed");
        h.assertTrue(jokes.poll(fortyMinutes * 2 - 1, 1, true, 9999) == null, "Next gap was shortened");
        h.assertTrue(jokes.poll(fortyMinutes * 2, 60, true, 9999) != null, "Random jitter extended fixed gap");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bundledJokesRequireOneRussianLanguageSignal(GameTestHelper h) {
        h.assertTrue(CompanionJokeBank.allowBundled("ru_ru", "en", false), "Russian game was ignored");
        h.assertTrue(CompanionJokeBank.allowBundled("en_us", "ru", false), "Russian system was ignored");
        h.assertTrue(CompanionJokeBank.allowBundled("en_us", "en", true), "Russian player was ignored");
        h.assertTrue(!CompanionJokeBank.allowBundled("en_us", "en", false), "No Russian signal still enabled defaults");
        h.assertTrue(!CompanionJokeBank.allowBundled("rue", "sr", false), "Other locale was mistaken for Russian");
        h.assertTrue(CompanionJokeBank.allowBundled("RU-ru", "en", false), "Regional tag was ignored");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void customBankAddsUniqueShortLinesAndKeepsDefaults(GameTestHelper h) throws Exception {
        var folder = java.nio.file.Files.createTempDirectory("joke-bank-test-");
        var file = folder.resolve("jokes.txt");
        try {
            h.assertTrue(CompanionJokeBank.load(file, true).lines().equals(CompanionJokes.ALL), "Missing file lost defaults");
            h.assertTrue(!java.nio.file.Files.exists(file), "Optional file was created");
            h.assertTrue(CompanionJokeBank.load(file, false).lines().isEmpty(), "Non-Russian mode loaded defaults without a file");
            String extra = "Утка открыла магазин. Торгует слухами.";
            String text = "\uFEFF# Comment\r\n\r\n" + extra + "\r\n" + extra + "\n"
                    + CompanionJokes.ALL.getFirst() + "\nЦифра 123\n" + "слово ".repeat(16) + "\nПлохой\tтекст\n";
            java.nio.file.Files.writeString(file, text);
            var loaded = CompanionJokeBank.load(file, true).lines();
            h.assertTrue(loaded.size() == CompanionJokes.ALL.size() + 1 && loaded.contains(extra)
                    && loaded.containsAll(CompanionJokes.ALL), "Merge, validation or deduplication failed");
            h.assertTrue(java.nio.file.Files.readString(file).equals(text), "Owner's file was changed");
            h.assertTrue(CompanionJokeBank.load(file, false).lines().equals(java.util.List.of(extra, CompanionJokes.ALL.getFirst())), "Non-Russian mode included bundled lines");
            h.assertTrue(CompanionJokeBank.hasRussian("Привет") && !CompanionJokeBank.hasRussian("Hello"), "Player language detection failed");
            java.nio.file.Files.writeString(file, "");
            h.assertTrue(CompanionJokeBank.load(file, true).lines().equals(CompanionJokes.ALL), "Empty file lost defaults");
            java.nio.file.Files.write(file, new byte[]{(byte) 0xc3, (byte) 0x28});
            h.assertTrue(CompanionJokeBank.load(file, true).lines().equals(CompanionJokes.ALL), "Invalid UTF-8 lost defaults");
            java.nio.file.Files.write(file, new byte[CompanionJokeBank.MAX_BYTES + 1]);
            h.assertTrue(CompanionJokeBank.load(file, true).lines().equals(CompanionJokes.ALL), "Oversized file lost defaults");
            var many = new StringBuilder();
            for (int i = 0; i < 1000; i++) many.append("Шутка ").append((char) ('а' + i / 32))
                    .append((char) ('а' + i % 32)).append(".\n");
            java.nio.file.Files.writeString(file, many.toString());
            h.assertTrue(CompanionJokeBank.load(file, true).lines().size() == CompanionJokes.ALL.size() + 1000,
                    "A thousand custom jokes were not loaded");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.delete(folder);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void customBankIsUsedByTheSchedulerAndReloadResetsItsHistory(GameTestHelper h) {
        var jokes = new CompanionJokes();
        String first = "Утка открыла магазин. Торгует слухами.";
        String second = "Гусь стал дирижёром. Теперь все ходят гуськом.";
        jokes.setBank(java.util.List.of(first, second), true, 0, 0);
        h.assertTrue(first.equals(jokes.poll(6000, 5, true, 0)), "Custom line was not delivered");
        h.assertTrue(second.equals(jokes.poll(12000, 5, true, 0)), "Custom line repeated consecutively");
        h.assertTrue(jokes.poll(18000, 5, true, 0) == null, "Custom cooldown ignored");
        jokes.setBank(java.util.List.of(second), true, 0, 0);
        h.assertTrue(second.equals(jokes.poll(6000, 5, true, 0)), "Reload retained old bank or history");
        h.assertTrue(second.equals(jokes.poll(6000 + CompanionJokes.HOUR, 5, true, 0)), "Singleton never repeats");
        jokes.setBank(java.util.List.of(), true, 0, 0);
        h.assertTrue(jokes.poll(6000, 5, true, 0) == null, "Empty bank did not stay silent");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void jokesAreShortBankLinesWithoutNumbers(GameTestHelper h) {
        h.assertTrue(new HashSet<>(CompanionJokes.ALL).size() == CompanionJokes.ALL.size(), "Duplicate bank lines");
        for (String joke : CompanionJokes.ALL) {
            h.assertTrue(CompanionConversation.fitsMurmur(joke), "Not a short murmur: " + joke);
            h.assertTrue(joke.chars().filter(c -> c == '.' || c == '!' || c == '?').count() <= 3, "Too many sentences");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void jokesWaitTheSampledIntervalAndRespectItsSetting(GameTestHelper h) {
        var jokes = new CompanionJokes(); jokes.reset(100, 6000);
        h.assertTrue(jokes.poll(100 + 11999, 5, true, 0) == null, "Joke before its sampled delay");
        h.assertTrue(jokes.poll(100 + 12000, 5, true, 0) != null, "Due joke missing");
        h.assertTrue(jokes.poll(12100 + 5999, 5, true, 0) == null, "Back-to-back joke");
        h.assertTrue(jokes.poll(12100 + 6000, 5, true, 0) != null, "Minimum delay ignored");
        jokes.reset(0, 0);
        h.assertTrue(jokes.poll(6000, 10, true, 0) == null, "Longer interval ignored");
        h.assertTrue(jokes.poll(12000, 10, true, 0) != null, "Changed interval never reached");
        for (int jitter : new int[]{Integer.MIN_VALUE, -1, 0, 6000, Integer.MAX_VALUE}) {
            long gap = CompanionJokes.gap(5, jitter);
            h.assertTrue(gap >= 6000 && gap <= 12000, "Outside five to ten minutes: " + gap);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void disabledOrBusyOpportunitiesWaitWithoutConsumingAJoke(GameTestHelper h) {
        var jokes = new CompanionJokes(); jokes.reset(0, 0);
        h.assertTrue(jokes.poll(6000, 5, false, 0) == null, "A blocked opportunity spoke");
        h.assertTrue(jokes.poll(6001, 5, true, 0) != null, "Blocking lost the next quiet opportunity");
        var conversation = new CompanionConversation(); conversation.reset(0, 0);
        conversation.playerMessage(10000, 0);
        h.assertTrue(jokes.poll(10000, 1, conversation.ready(10000), 0) == null, "Interrupted the player");
        conversation.initiativeDelivered("Короткая реплика.", 20000, 0);
        h.assertTrue(!conversation.ready(20001), "Joke did not share the ordinary conversation pause");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void jokesDoNotRepeatWithinAnHourEvenWhenTheBankRunsOut(GameTestHelper h) {
        var jokes = new CompanionJokes(); jokes.reset(0, 0);
        var seen = new HashMap<String, Long>(); String previous = "";
        int refused = 0;
        for (long now = 1200; now <= CompanionJokes.HOUR * 2; now += 1200) {
            String line = jokes.poll(now, 1, true, 0);
            if (line == null) { refused++; continue; }
            Long last = seen.put(line, now);
            h.assertTrue(last == null || now - last >= CompanionJokes.HOUR, "Repeated before an hour");
            h.assertTrue(!line.equals(previous), "Repeated consecutively"); previous = line;
        }
        h.assertTrue(refused > 0 && seen.size() == CompanionJokes.ALL.size(), "Exhausted bank was not exercised");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void worldResetStartsAFreshDelayAndDiscardsOldWorldTime(GameTestHelper h) {
        var jokes = new CompanionJokes(); jokes.reset(0, 0);
        h.assertTrue(jokes.poll(6000, 5, true, 0) != null, "First joke missing");
        jokes.reset(20, 0);
        h.assertTrue(jokes.poll(21, 5, true, 0) == null, "Old world joke replayed on join");
        h.assertTrue(jokes.poll(6020, 5, true, 0) != null, "Old world clock prevented jokes");
        h.succeed();
    }
}

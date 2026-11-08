package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionConversation;
import dev.aicompanion.CompanionJokes;
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

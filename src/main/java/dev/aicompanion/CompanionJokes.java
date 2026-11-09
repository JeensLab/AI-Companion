package dev.aicompanion;

import java.util.Arrays;
import java.util.List;

/** Local jokes share the conversation's free slots, but have their own slower cadence and hourly deduplication. */
public final class CompanionJokes {
    public static final long HOUR = 20L * 60 * 60;
    public static final List<String> ALL = CompanionJokeBank.defaults();
    private List<String> bank = ALL;
    private long[] toldAt = new long[bank.size()];
    private long lastJoke;
    private int delayJitter, previous = -1;

    public void setBank(List<String> lines, long now, int jitter) {
        bank = List.copyOf(lines);
        toldAt = new long[bank.size()];
        reset(now, jitter);
    }

    public void reset(long now, int jitter) {
        Arrays.fill(toldAt, Long.MIN_VALUE);
        lastJoke = now; delayJitter = jitter; previous = -1;
    }

    /** Between the configured minimum and twice that time; one sampled delay, not a new lottery every tick. */
    public static long gap(int minutes, int jitter) {
        long minimum = Math.clamp(minutes, 1, 60) * 60L * 20;
        return minimum + Math.floorMod((long) jitter, minimum + 1);
    }

    public boolean due(long now, int minutes) { return now >= lastJoke + gap(minutes, delayJitter); }

    /** Refused opportunities consume neither the interval nor a joke, so a quiet moment can use them later. */
    public String poll(long now, int minutes, boolean allowed, int jitter) {
        if (!allowed || bank.isEmpty() || !due(now, minutes)) return null;
        int start = Math.floorMod(jitter, bank.size());
        for (int offset = 0; offset < bank.size(); offset++) {
            int index = (start + offset) % bank.size();
            if ((bank.size() > 1 && index == previous) || toldAt[index] != Long.MIN_VALUE && now - toldAt[index] < HOUR) continue;
            String joke = bank.get(index);
            // Validate before delivery regardless of the ordinary murmur setting.
            if (!CompanionConversation.fitsMurmur(joke)) continue;
            toldAt[index] = now; previous = index; lastJoke = now; delayJitter = jitter;
            return joke;
        }
        return null;
    }
}

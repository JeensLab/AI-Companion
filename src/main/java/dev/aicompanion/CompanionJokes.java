package dev.aicompanion;

import java.util.HashMap;
import java.util.List;

/** Local jokes share the conversation's free slots, but have their own slower cadence and hourly deduplication. */
public final class CompanionJokes {
    public static final long HOUR = 20L * 60 * 60;
    public static final List<String> ALL = CompanionJokeBank.defaults();
    private List<String> bank = ALL;
    private boolean customBank = true;
    private final HashMap<String, Long> toldAt = new HashMap<>();
    private long lastJoke;
    private int delayJitter;
    private String previous;

    public void setBank(List<String> lines, boolean customFile, long now, int jitter) {
        bank = List.copyOf(lines);
        customBank = customFile;
        reset(now, jitter);
    }

    /** Keep cooldowns even for lines temporarily hidden by a language change. */
    public void reloadBank(List<String> lines, boolean customFile, int jitter) {
        bank = List.copyOf(lines);
        if (customBank != customFile) delayJitter = customFile ? jitter : 0;
        customBank = customFile;
    }

    public void reset(long now, int jitter) {
        toldAt.clear();
        lastJoke = now; delayJitter = customBank ? jitter : 0; previous = null;
    }

    /** Between the configured minimum and twice that time; one sampled delay, not a new lottery every tick. */
    public static long gap(int minutes, int jitter) {
        long minimum = Math.clamp(minutes, 1, 60) * 60L * 20;
        return minimum + Math.floorMod((long) jitter, minimum + 1);
    }

    public boolean due(long now, int minutes) { return now >= lastJoke + gap(customBank ? minutes : 40, delayJitter); }

    /** Refused opportunities consume neither the interval nor a joke, so a quiet moment can use them later. */
    public String poll(long now, int minutes, boolean allowed, int jitter) {
        if (!allowed || bank.isEmpty() || !due(now, minutes)) return null;
        int start = Math.floorMod(jitter, bank.size());
        for (int offset = 0; offset < bank.size(); offset++) {
            int index = (start + offset) % bank.size();
            String joke = bank.get(index);
            long last = toldAt.getOrDefault(joke, Long.MIN_VALUE);
            if ((bank.size() > 1 && joke.equals(previous)) || last != Long.MIN_VALUE && now - last < HOUR) continue;
            // Validate before delivery regardless of the ordinary murmur setting.
            if (!CompanionConversation.fitsMurmur(joke)) continue;
            toldAt.put(joke, now); previous = joke; lastJoke = now; delayJitter = customBank ? jitter : 0;
            return joke;
        }
        return null;
    }
}

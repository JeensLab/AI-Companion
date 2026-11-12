package dev.aicompanion;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import dev.aicompanion.CompanionJokeBank.Joke;
import dev.aicompanion.CompanionJokeBank.Theme;

/** Local stories share conversation slots, with their own cadence and hourly history. */
public final class CompanionJokes {
    public static final long HOUR = 20L * 60 * 60;
    public static final List<Joke> BUNDLED = CompanionJokeBank.defaults();
    public static final List<String> ALL = BUNDLED.stream().map(Joke::text).toList();
    private List<Joke> bank = BUNDLED;
    private final HashMap<String, Long> toldAt = new HashMap<>();
    private long lastJoke;
    private int delayJitter;
    private String previous;

    public void reloadBank(List<Joke> lines) { bank = List.copyOf(lines); }
    public void reset(long now, int jitter) {
        toldAt.clear(); lastJoke = now; delayJitter = jitter; previous = null;
    }
    public static long gap(int minutes, int jitter) {
        long minimum = Math.clamp(minutes, 1, 60) * 60L * 20;
        return minimum + Math.floorMod((long) jitter, minimum + 1);
    }
    public boolean due(long now, int minutes) { return now >= lastJoke + gap(minutes, delayJitter); }
    public static int weight(Joke joke, Set<Theme> surroundings, Set<Theme> recent) {
        if (joke.themes().stream().anyMatch(recent::contains)) return 5;
        return joke.themes().stream().anyMatch(surroundings::contains) ? 3 : 1;
    }
    private boolean eligible(Joke joke, long now) {
        Long last = toldAt.get(joke.text());
        return !joke.text().equals(previous) && (last == null || now - last >= HOUR)
                && CompanionJokeBank.valid(joke.text());
    }
    public String poll(long now, int minutes, boolean allowed, int jitter) {
        return poll(now, minutes, allowed, jitter, Set.of(), Set.of(), new java.util.Random(jitter));
    }
    public String poll(long now, int minutes, boolean allowed, int jitter,
                       Set<Theme> surroundings, Set<Theme> recent, RandomGenerator random) {
        if (!allowed || !due(now, minutes)) return null;
        var eligible = bank.stream().filter(joke -> eligible(joke, now)).toList();
        int total = eligible.stream().mapToInt(joke -> weight(joke, surroundings, recent)).sum();
        if (total == 0) return null;
        int selected = random.nextInt(total);
        for (Joke joke : eligible) {
            selected -= weight(joke, surroundings, recent);
            if (selected >= 0) continue;
            toldAt.put(joke.text(), now); previous = joke.text(); lastJoke = now; delayJitter = jitter;
            return joke.text();
        }
        throw new IllegalStateException("Weighted joke selection failed");
    }
}

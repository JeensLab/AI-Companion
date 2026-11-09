package dev.aicompanion;

import java.util.Arrays;
import java.util.List;

/** Local jokes share the conversation's free slots, but have their own slower cadence and hourly deduplication. */
public final class CompanionJokes {
    public static final long HOUR = 20L * 60 * 60;
    public static final List<String> ALL = List.of(
            "Крипер пошёл обниматься. Дружба получилась взрывная.",
            "Скелет решил подкачаться. Но так и остался костлявым.",
            "Зомби открыл ресторан. Посетителям сразу начали выносить мозг.",
            "Паук устроился ткачом. Сказал, опыт работы с сетями имеется.",
            "Ведьма открыла аптеку. Побочные эффекты стали главным товаром.",
            "Шахтёр нашёл выход из пещеры. Оказалось, это вход в следующую.",
            "Кирка попросила отпуск. Начальство сказало: сначала разгреби завал.",
            "Рыбак поймал сапог. Теперь ищет рыбу подходящего размера.",
            "Курица решила стать шахтёром. С тех пор несёт только породу.",
            "Лопата решила сменить профессию. Но её опять закопали в работу.",
            "Факел устроился сторожем. Работает с огоньком.",
            "Сундук пошёл к психологу. Слишком много всего держал в себе.",
            "Бухгалтер перепутала зарплаты Пупы и Лупы: Лупа получил за Пупу, а Пупа за Лупу.",
            "Шли папа-помидор, мама-помидор и сын-помидор. Сын отстал. Папа наступил на него: «Догоняй, кетчуп!»");
    private final long[] toldAt = new long[ALL.size()];
    private long lastJoke;
    private int delayJitter, previous = -1;

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
        if (!allowed || !due(now, minutes)) return null;
        int start = Math.floorMod(jitter, ALL.size());
        for (int offset = 0; offset < ALL.size(); offset++) {
            int index = (start + offset) % ALL.size();
            if (index == previous || toldAt[index] != Long.MIN_VALUE && now - toldAt[index] < HOUR) continue;
            String joke = ALL.get(index);
            // Validate before delivery regardless of the ordinary murmur setting.
            if (!CompanionConversation.fitsMurmur(joke)) continue;
            toldAt[index] = now; previous = index; lastJoke = now; delayJitter = jitter;
            return joke;
        }
        return null;
    }
}

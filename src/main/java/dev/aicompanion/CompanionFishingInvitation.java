package dev.aicompanion;

import java.util.Locale;

/** Local, one-shot consent. Merely reaching the next invitation time never starts an order. */
public final class CompanionFishingInvitation {
    public static final String ORDER = "companion work fish 3 fish";
    private static final int MINUTE = 20 * 60;
    private static final int ANSWER_TICKS = MINUTE;
    private long dueAt, retryAt, answerUntil = -1;
    private int interval;

    public void reset() {
        interval = 0;
        dueAt = retryAt = 0;
        cancel();
    }

    public boolean due(long now, int minutes, int random) {
        if (minutes != interval) {
            interval = minutes;
            schedule(now, random);
            cancel();
        }
        return now >= dueAt && now >= retryAt && now > answerUntil;
    }

    public void checked(long now) { retryAt = now + 30 * 20; }

    public void offered(long now, int minutes, int random) {
        interval = minutes;
        schedule(now, random);
        answerUntil = now + ANSWER_TICKS;
    }

    public void cancel() { answerUntil = -1; }

    public enum Answer { ACCEPT, DECLINE, OTHER }

    public Answer answer(String text, long now) {
        boolean waiting = answerUntil >= now;
        cancel();
        if (!waiting || text.contains("?")) return Answer.OTHER;
        String value = normalize(text)
                .replaceFirst("^ну+ ", "")
                .replaceFirst("(?: +(?:брат|бро|коллега|олег|олеж|тогда|уже|емае|блин))+$", "")
                .replaceFirst("^да(?: +да)+(?= |$)|^дада(?= |$)", "да")
                .replaceFirst("^(да )?(давай|погнали|пойдем|идем|пошли|поехали)(?: +\\2)+(?= |$)", "$1$2");
        if (value.matches("да|ага|угу|конечно|согласен|(?:да )?(?:давай|погнали|пойдем|идем|пошли|поехали)(?: (?:рыбачить|порыбачить|на рыбалку))?"))
            return Answer.ACCEPT;
        if (value.matches("нет|неа|не сейчас|потом|не хочу(?: рыбачить)?|передумал|да но не сейчас")) return Answer.DECLINE;
        return Answer.OTHER;
    }

    /** Only imperative invitations without a quantity. Exact counted commands stay with the work parser. */
    public static boolean proposesTrip(String text) {
        String value = normalize(text);
        return value.matches("(?:(?:а теперь|ну|да) )?(?:порыбачим|(?:давай|пойдем|идем|пошли|айда|погнали|пагнали|го)"
                + "(?: к (?:реке|речке|озеру|воде))? (?:порыбачим|порыбачить|рыбачить|рыбалить|на рыбалку|(?:рыбки|рыбу) половим|закинем удочки|половим рыбу))"
                + "(?: (?:вместе|рядом|тут|брат|бро))?");
    }

    public static boolean asksAboutTrip(String text) {
        return normalize(text).matches("(?:вот бы|может|неплохо бы|как насчет) (?:порыбачить|порыбачим|рыбки половить|посидим с удочками|рыбалки)");
    }

    private void schedule(long now, int random) {
        long minimum = (long) interval * MINUTE;
        dueAt = now + minimum + Math.floorMod((long) random, minimum + 1);
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^а-я0-9]+", " ").strip();
    }
}

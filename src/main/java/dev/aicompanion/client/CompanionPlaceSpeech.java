package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionMarkerNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the player says about a named place: «запомни это место, это дом» saves a marker, «иди к колодцу», «проведи меня
 * до железного рудника», «отведи меня к даче», «охраняй дачу» and «отнеси ресы в дом» walk to one, and «забудь дачу»,
 * «забудь метку дачу», «удали точку дачу» or «забудь всё» take one or every marker off his list. A forget word about a
 * thing, not a place — «забудь, что я говорил», «забудь, где дача», «забудь про молоко», «забудь всё про метку» — names
 * no marker and stays the model's. A name that fits no marker, or one place that more than one marker fits, is answered
 * with one honest question and no walk; two markers
 * named as two legs («иди на второй остров потом на первый остров») are left to the model, which plans the whole route.
 * <p>
 * No client classes, so the chat screen and the GameTests read the same phrases. Names are the player's own words: the
 * comparison is generic (stems and Russian case forms), never a list of known place words. What the phrase still says
 * after the name («потом домой») is the {@link Result#leftover()}, so the router can leave a two-step request to the
 * model instead of walking only its first leg.
 */
public final class CompanionPlaceSpeech {
    public enum Kind { NONE, REMEMBER, GO, GUARD, CARRY, FORGET, FORGET_ALL, UNKNOWN, AMBIGUOUS, HOME, EMPTY_NAME, TOO_LONG, COORDINATES }

    /** kind: what the phrase means; name: the marker it names (for a forget of several, the phrase as said, which the
     * store resolves to every marker it means); question: the one honest question or line, or null; leftover: what the
     * phrase still says after the name («потом домой» in «иди к даче, потом домой»). */
    public record Result(Kind kind, String name, String question, String leftover) {
        public Result(Kind kind, String name, String question) { this(kind, name, question, ""); }
        static Result none() { return new Result(Kind.NONE, "", null); }
    }

    private static final Pattern REMEMBER = Pattern.compile("(?U)^(?:запомни|запомните|запиши|запишите|отметь|отметьте)\\b"
            + "\\s*[,:;—-]?\\s*(?:(?:это|вот\\s+это|здесь|тут)\\b)?\\s*(?:место\\b)?\\s*[,:;—-]?\\s*(?:(?:это|вот)\\b\\s*)?(.*)$");
    /** «Забудь дачу»: the bare word names a saved marker to take off the list. */
    private static final Pattern FORGET_BARE = Pattern.compile("(?U)^(?:забудь|забудьте)\\b"
            + "\\s*[,:;—-]?\\s*(?:(?:эту|ту|мою|нашу|это)\\s+)?(.*)$");
    /** «Забудь метку дачу», «удали точку дачу»: the object word says the name is a marker, not a thing to forget about. */
    private static final Pattern FORGET_NAMED = Pattern.compile("(?U)^(?:забудь|забудьте|удали|удалите)\\b"
            + "\\s*[,:;—-]?\\s*(?:(?:эту|ту|мою|нашу|это)\\s+)?(?:метк[ауие]|точк[ауие]|мест[оа])\\b\\s*[,:;—-]?\\s*(.*)$");
    /** «Забудь всё», «забудь все метки»: every marker of the list goes. The tail is what follows «всё». */
    private static final Pattern FORGET_ALL = Pattern.compile("(?U)^(?:забудь|забудьте|удали|удалите)\\b\\s*[,:;—-]?\\s*вс[её]\\b\\s*(.*)$");
    private static final Pattern GO = Pattern.compile("(?U)^(?:иди|идите|пойди|пойдите|сходи|сходите|сбегай|беги|бегите|шагай|шагайте"
            + "|ступай|ступайте|веди|ведите|проведи|проведите|проводи|проводите|отведи|отведите|доведи|доведите|подведи|подведите"
            + "|дойди|дойдите|доберись|доберитесь|подойди|подойдите|вернись|вернитесь|возвращайся|пошли|пойдем|идем)\\b"
            + "(?:\\s+\\p{L}+){0,3}?\\s+(?:к|ко|до|в|на|у)\\s+(.+)$");
    private static final Pattern GUARD = Pattern.compile("(?U)^(?:охраняй|охраняйте|охранять|охраняешь|сторожи|сторожите"
            + "|защищай|защищайте|защити|защитить|защищаешь|прикрывай|прикрывайте|береги|берегите)\\b"
            + "(?:\\s+(?:наш|наша|наше|наши|мою|мой|моя|мои|ту|тот|этот|эту|нашу|нашего)\\b)?\\s+(.+)$");
    private static final Pattern CARRY = Pattern.compile("(?U)^(?:отнеси|отнесите|отнести|занеси|занесите|занести|унеси|унесите|унести"
            + "|перенеси|перенесите|перенести|отвези|отвезите|отвезти|снеси|снесите|снести|доставь|доставьте|доставить)\\b"
            + "(?:\\s+\\p{L}+){0,4}?\\s+(?:в|во|к|ко|до|на)\\s+(.+)$");
    /** Words that point at a person, not at a place: «охраняй меня», «иди ко мне», «веди меня ко мне» name no marker. */
    private static final Set<String> PEOPLE = Set.of("меня", "нас", "себя", "его", "нее", "ее", "их", "вас", "тебя",
            "мне", "нам", "себе", "ему", "ей", "им", "вам", "тебе");
    /** «Забудь про молоко», «забудь об этом»: the word is a thing he is told about, not a place to take off the list. */
    private static final Set<String> PREPOSITIONS = Set.of("про", "о", "об", "обо");
    /** «Забудь, что я говорил», «забудь, где дача»: a clause opener says what he is told about, not a place on the list. */
    private static final Set<String> CLAUSE_WORDS = Set.of("что", "как", "когда", "где");
    /** «Забудь цель», «забудь задачу»: an order he is doing, not a place on the list — the goal and stop grammar own them. */
    private static final Set<String> ORDER_WORDS = Set.of("цель", "цели", "дракона", "задачу", "задачи");
    /** «дом» and «домик» are the home he already knows: with no marker of that name the phrase stays the model's. */
    private static final Set<String> HOME_WORDS = Set.of("дом", "домик");
    /** Words that say the request has more than one leg: «и», «потом», «затем», «обратно», «снова», «ещё раз». */
    private static final Set<String> STEPS = Set.of("и", "потом", "затем", "обратно", "снова", "опять", "еще", "раз");
    /** Words that still mean the list itself when they follow «забудь всё»: «забудь все метки», «забудь всё точки». */
    private static final Set<String> LIST_WORDS = Set.of("метки", "метку", "меток", "точки", "точку", "точек",
            "места", "место", "метками", "точками", "все", "всё");
    /** Polite or filler words that do not turn «забудь всё» into a compound order. */
    private static final Set<String> SOFTENERS = Set.of("пожалуйста", "давай", "ну", "и", "всё", "все");

    private CompanionPlaceSpeech() { }

    /** The marker order of the phrase, or NONE when the phrase says nothing about a named place. */
    public static Result parse(String text, String companionName, List<String> knownNames, boolean enabled) {
        if (text == null) return Result.none();
        String said = plain(text, companionName);
        if (said.isEmpty()) return Result.none();
        // «Забудь дачу» is a word about the list, not about walking: it works with the feature switched off.
        Result forgotten = forget(said, knownNames);
        if (forgotten != null) return forgotten;
        if (!enabled) return Result.none();
        var remembered = REMEMBER.matcher(said);
        if (remembered.matches()) return remember(remembered.group(1));
        var carried = CARRY.matcher(said);
        if (carried.matches()) return place(Kind.CARRY, carried.group(1), knownNames);
        var guarded = GUARD.matcher(said);
        if (guarded.matches()) return place(Kind.GUARD, guarded.group(1), knownNames);
        var went = GO.matcher(said);
        if (went.matches()) return place(Kind.GO, went.group(1), knownNames);
        return Result.none();
    }

    /** Only the remembering part of the phrase: used before the work grammar, so «запомни это дом2» keeps its digits. */
    public static Result remember(String text, String companionName, boolean enabled) {
        if (!enabled || text == null) return Result.none();
        String said = plain(text, companionName);
        var remembered = REMEMBER.matcher(said);
        return remembered.matches() ? remember(remembered.group(1)) : Result.none();
    }

    private static Result remember(String tail) {
        String name = CompanionMarkerNames.clean(tail);
        if (name.isEmpty()) return new Result(Kind.EMPTY_NAME, "", "Как назвать это место? Скажи «запомни это место, " + "название».");
        // One validator for speech, the plan codec and the screen: a name they refuse is not a marker here either.
        if (!CompanionMarkerNames.valid(name)) {
            if (name.length() > CompanionMarkerNames.MAX)
                return new Result(Kind.TOO_LONG, name, "Название метки — до " + CompanionMarkerNames.MAX + " символов. Как назвать это место покороче?");
            return new Result(Kind.COORDINATES, name, "«" + name + "» — это координаты, а не название. Как назвать это место?");
        }
        return new Result(Kind.REMEMBER, name, null);
    }

    /** The forget order of the phrase, or null when the phrase is not about taking a marker off the list. */
    private static Result forget(String said, List<String> knownNames) {
        var all = FORGET_ALL.matcher(said);
        if (all.matches()) {
            // «Забудь всё про молоко», «забудь всё про метку»: «всё» with a preposition after it is a subject he is told
            // about, not the list, so the model answers and no marker goes. The bare «забудь всё» still empties it.
            if (PREPOSITIONS.contains(firstWord(all.group(1)))) return Result.none();
            // «Забудь всё и добудь дерево» is a compound order: only the whole message counts as «всё».
            if (!onlyTheList(all.group(1))) return Result.none();
            return new Result(Kind.FORGET_ALL, "", null);
        }
        var named = FORGET_NAMED.matcher(said);
        String target;
        if (named.matches()) target = named.group(1).strip();
        else {
            var bare = FORGET_BARE.matcher(said);
            if (!bare.matches()) return null;
            target = bare.group(1).strip();
        }
        List<String> words = CompanionMarkerNames.tokens(target);
        if (words.isEmpty()) return new Result(Kind.EMPTY_NAME, "", forgetEmpty(knownNames));
        // A person word, a clause opener, a preposition or an order's own word names no marker: «забудь меня»,
        // «забудь, что я говорил», «забудь про молоко», «забудь задачу» stay the model's.
        if (personWord(words) || CLAUSE_WORDS.contains(words.getFirst()) || PREPOSITIONS.contains(words.getFirst())
                || ORDER_WORDS.contains(words.getFirst())) return Result.none();
        List<String> matches = matching(target, knownNames);
        if (matches.isEmpty()) {
            // «Дом» is the home he knows: with no marker of that name the line says so, and the home is never forgotten.
            if (!stemOfFirstHomeWord(words).isEmpty()) return new Result(Kind.HOME, CompanionMarkerNames.clean(target), homeLine(target));
            return new Result(Kind.UNKNOWN, target, forgetUnknown(target, knownNames));
        }
        if (matches.size() == 1) return new Result(Kind.FORGET, matches.getFirst(), null);
        // One place that several markers fit is a question; names that take different words are different markers, and
        // both go («забудь дачу и колодец»), never a question.
        if (sameWords(matches, target)) return new Result(Kind.AMBIGUOUS, target, ambiguous(target, matches));
        return new Result(Kind.FORGET, CompanionMarkerNames.clean(target), null);
    }

    private static Result place(Kind kind, String tail, List<String> knownNames) {
        String target = tail.strip();
        List<String> words = CompanionMarkerNames.tokens(target);
        if (words.isEmpty() || personWord(words)) return Result.none();
        List<String> matches = matching(target, knownNames);
        if (matches.size() == 1) return new Result(kind, matches.getFirst(), null, CompanionMarkerNames.without(matches.getFirst(), target));
        if (matches.size() > 1) {
            // Two markers named as two legs are no ambiguity: with a step word («потом», «и», «обратно», «так 5 раз») the
            // model plans the whole route. Only one place reference that several markers fit is a question.
            if (!sameWords(matches, target) && hasSteps(target)) return Result.none();
            return new Result(Kind.AMBIGUOUS, target, ambiguous(target, matches));
        }
        // No marker: «дом» and «домик» are the home he knows, not an unknown name to ask about.
        if (HOME_WORDS.contains(stemOfFirstHomeWord(words))) return Result.none();
        return new Result(Kind.UNKNOWN, target, unknown(target, knownNames));
    }

    private static List<String> matching(String target, List<String> knownNames) {
        List<String> matches = new ArrayList<>();
        for (String known : knownNames) if (CompanionMarkerNames.matches(known, target)) matches.add(known);
        return matches;
    }

    /**
     * True when every matched name takes the same words of the phrase: one place reference that several markers fit
     * («колодец» and «колодцы» for «к колодцу»). Names on different words are different places of the phrase.
     */
    private static boolean sameWords(List<String> matches, String target) {
        int[] first = null;
        for (String name : matches) {
            int[] span = CompanionMarkerNames.span(name, target);
            if (span == null) continue;
            if (first == null) { first = span; continue; }
            if (span[0] > first[1] || span[1] < first[0]) return false;
        }
        return true;
    }

    /** True when the phrase names more than one leg: a comma or a step word («и», «потом», «обратно», «ещё раз»). */
    private static boolean hasSteps(String said) {
        if (said.contains(",")) return true;
        for (String word : CompanionMarkerNames.tokens(said)) if (STEPS.contains(word)) return true;
        return false;
    }

    /** The first word of a tail, or "" when it has none: what «забудь всё про …» says after «всё». */
    private static String firstWord(String tail) {
        List<String> words = CompanionMarkerNames.tokens(tail);
        return words.isEmpty() ? "" : words.getFirst();
    }

    /** True when «забудь всё» is the whole message: nothing after it but words about the list itself. */
    private static boolean onlyTheList(String tail) {
        for (String word : CompanionMarkerNames.tokens(tail)) {
            if (!LIST_WORDS.contains(word) && !SOFTENERS.contains(word)) return false;
        }
        return true;
    }

    /** A word that points at a person is never a marker name, whatever the list holds. */
    private static boolean personWord(List<String> words) {
        for (String word : words) if (PEOPLE.contains(word)) return true;
        return false;
    }

    /** The head word of the target when it is a home word: «до дома», «к домику», «в дом». */
    private static String stemOfFirstHomeWord(List<String> words) {
        for (String word : words) {
            for (String home : HOME_WORDS) if (CompanionMarkerNames.matches(home, word)) return home;
        }
        return "";
    }

    private static String ambiguous(String target, List<String> matches) {
        return "Под «" + CompanionMarkerNames.clean(target) + "» подходят метки: " + quoted(matches)
                + ". Какую ты имеешь в виду?";
    }

    private static String unknown(String target, List<String> knownNames) {
        String clean = CompanionMarkerNames.clean(target);
        if (knownNames.isEmpty()) return "Меток пока нет. Скажи «запомни это место, " + clean + "», и я запомню.";
        return "Не знаю метки «" + clean + "». Известные метки: " + quoted(knownNames) + ". К какой идти?";
    }

    /** «Забудь дом» with no marker of that name: it is the home he knows, and the home is never forgotten silently. */
    private static String homeLine(String target) {
        return "«" + CompanionMarkerNames.clean(target) + "» — это наш дом, а не метка: дом я не забываю.";
    }

    private static String forgetEmpty(List<String> knownNames) {
        if (knownNames.isEmpty()) return "Меток пока нет: забывать нечего.";
        return "Какую метку забыть? Известные метки: " + quoted(knownNames) + ".";
    }

    private static String forgetUnknown(String target, List<String> knownNames) {
        String clean = CompanionMarkerNames.clean(target);
        if (knownNames.isEmpty()) return "Меток пока нет: «" + clean + "» забывать нечего.";
        return "Не знаю метки «" + clean + "». Известные метки: " + quoted(knownNames) + ". Какую забыть?";
    }

    private static String quoted(List<String> names) {
        return String.join(", ", names.stream().map(name -> "«" + name + "»").toList());
    }

    /** Lower case, ё as е, without his name in front and the full stop or «!» at the end. */
    private static String plain(String text, String companionName) {
        String value = text.toLowerCase(Locale.ROOT).replace('ё', 'е').strip();
        if (companionName != null && !companionName.isBlank())
            value = value.replaceFirst("^" + Pattern.quote(companionName.toLowerCase(Locale.ROOT).replace('ё', 'е')) + "[,! ]+", "");
        return value.replaceAll("[.!]+$", "").strip();
    }
}

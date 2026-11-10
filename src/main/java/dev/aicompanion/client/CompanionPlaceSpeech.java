package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionMarkerNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the player says about a named place: «запомни это место, это дом» saves a marker, «иди к колодцу», «проведи меня
 * до железного рудника», «отведи меня к даче», «охраняй дачу» and «отнеси ресы в дом» walk to one. A name that fits no
 * marker, or more than one, is answered with one honest question and no walk.
 * <p>
 * No client classes, so the chat screen and the GameTests read the same phrases. Names are the player's own words: the
 * comparison is generic (stems and Russian case forms), never a list of known place words.
 */
public final class CompanionPlaceSpeech {
    public enum Kind { NONE, REMEMBER, GO, GUARD, CARRY, UNKNOWN, AMBIGUOUS, EMPTY_NAME, TOO_LONG, COORDINATES }

    /** kind: what the phrase means; name: the marker it names; question: the one honest question, or null. */
    public record Result(Kind kind, String name, String question) {
        static Result none() { return new Result(Kind.NONE, "", null); }
    }

    private static final Pattern REMEMBER = Pattern.compile("(?U)^(?:запомни|запомните|запиши|запишите|отметь|отметьте)\\b"
            + "\\s*[,:;—-]?\\s*(?:(?:это|вот\\s+это|здесь|тут)\\b)?\\s*(?:место\\b)?\\s*[,:;—-]?\\s*(?:(?:это|вот)\\b\\s*)?(.*)$");
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
    /** Words that point at a person, not at a place: «охраняй меня» is the guard order, not a marker. */
    private static final Set<String> PEOPLE = Set.of("меня", "нас", "себя", "его", "нее", "ее", "их", "вас", "тебя");
    /** «дом» and «домик» are the home he already knows: with no marker of that name the phrase stays the model's. */
    private static final Set<String> HOME_WORDS = Set.of("дом", "домик");

    private CompanionPlaceSpeech() { }

    /** The marker order of the phrase, or NONE when the phrase says nothing about a named place. */
    public static Result parse(String text, String companionName, List<String> knownNames, boolean enabled) {
        if (!enabled || text == null) return Result.none();
        String said = plain(text, companionName);
        if (said.isEmpty()) return Result.none();
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

    private static Result place(Kind kind, String tail, List<String> knownNames) {
        String target = tail.strip();
        List<String> words = CompanionMarkerNames.tokens(target);
        if (words.isEmpty() || PEOPLE.contains(words.getFirst())) return Result.none();
        List<String> matches = new ArrayList<>();
        for (String known : knownNames) if (CompanionMarkerNames.matches(known, target)) matches.add(known);
        if (matches.size() == 1) return new Result(kind, matches.getFirst(), null);
        if (matches.size() > 1) return new Result(Kind.AMBIGUOUS, target, ambiguous(target, matches));
        // No marker: «дом» and «домик» are the home he knows, not an unknown name to ask about.
        if (HOME_WORDS.contains(stemOfFirstHomeWord(words))) return Result.none();
        return new Result(Kind.UNKNOWN, target, unknown(target, knownNames));
    }

    /** The head word of the target when it is a home word: «до дома», «к домику», «в дом». */
    private static String stemOfFirstHomeWord(List<String> words) {
        for (String word : words) {
            for (String home : HOME_WORDS) if (CompanionMarkerNames.matches(home, word)) return home;
        }
        return "";
    }

    private static String ambiguous(String target, List<String> matches) {
        List<String> quoted = matches.stream().map(name -> "«" + name + "»").toList();
        return "Под «" + CompanionMarkerNames.clean(target) + "» подходят метки: " + String.join(", ", quoted)
                + ". Какую ты имеешь в виду?";
    }

    private static String unknown(String target, List<String> knownNames) {
        String clean = CompanionMarkerNames.clean(target);
        if (knownNames.isEmpty()) return "Меток пока нет. Скажи «запомни это место, " + clean + "», и я запомню.";
        return "Не знаю метки «" + clean + "». Известные метки: "
                + String.join(", ", knownNames.stream().map(name -> "«" + name + "»").toList()) + ". К какой идти?";
    }

    /** Lower case, ё as е, without his name in front and the full stop or «!» at the end. */
    private static String plain(String text, String companionName) {
        String value = text.toLowerCase(Locale.ROOT).replace('ё', 'е').strip();
        if (companionName != null && !companionName.isBlank())
            value = value.replaceFirst("^" + Pattern.quote(companionName.toLowerCase(Locale.ROOT).replace('ё', 'е')) + "[,! ]+", "");
        return value.replaceAll("[.!]+$", "").strip();
    }
}

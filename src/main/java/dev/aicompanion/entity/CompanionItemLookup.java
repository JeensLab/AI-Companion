package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The word the player used for a thing, resolved against the game's own registry: the name the game shows for an item in
 * the player's language, the stems of its words and its item tags. Nothing is listed by hand here, so a drop of any mod
 * answers to the name its own lang file gives it: «осколок» is the amethyst shard, «подзорная труба» the spyglass, and a
 * modded «рубин» its ruby, without a line of code for any of them.
 *
 * A word that names a group names every member of it: «брёвна» are all logs, «доски» all planks. A word the registry
 * cannot answer — «кура», «тухлое мясо» — is left to the model, which picks among the drops actually lying here
 * ({@link dev.aicompanion.client.CompanionPickUpCommands#ask}).
 *
 * Two ways the live router failed at 92b91d91 (owner, 27.11: «добудь тыкву» said «имени нет в игре»): the names it
 * matched were the game's names in whatever language the client shows (en_us on his client, so every Russian word named
 * nothing), and a bare kind of several name shapes («забор»: «Дубовый забор» beside «Забор из тёмного дуба») was no
 * group at all, so one species answered and the rest went to the model's wood chain. The Russian names of the game's own
 * lang files ({@link dev.aicompanion.client.CompanionClientNames}) and a tag rule that does not count words fix both.
 */
public final class CompanionItemLookup {
    private CompanionItemLookup() { }

    /** The names the game shows: the player's language on the client, en_us on the server. */
    public static final Function<Item, String> GAME_NAMES = item -> new ItemStack(item).getHoverName().getString();

    /** The id of the item the phrase names, or null when nothing in the registry answers to it. */
    public static String itemFor(String phrase) { return itemFor(phrase, GAME_NAMES); }

    /**
     * The id of the item the phrase names, by the names the game shows (or a stand-in for the language in a test). A
     * fried meat is its raw one: «жареная свинина» and «свинина» are one meaning, and the plan names it once.
     */
    public static String itemFor(String phrase, Function<Item, String> names) {
        List<Item> found = resolve(phrase, names);
        if (found.isEmpty()) return null;
        Item picked = found.getFirst();
        var key = BuiltInRegistries.ITEM.getKey(picked);
        if (key.getPath().startsWith("cooked_")) {
            var raw = BuiltInRegistries.ITEM.getOptional(uncooked(key));
            if (raw.isPresent()) picked = raw.get();
        }
        return BuiltInRegistries.ITEM.getKey(picked).toString();
    }

    /**
     * The raw form of a fried thing, in its own namespace: a mod's «cooked_beef» is «beef» of that mod, not the game's
     * beef. Only for an id whose path starts with «cooked_».
     */
    public static ResourceLocation uncooked(ResourceLocation key) {
        return ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getPath().substring("cooked_".length()));
    }

    /**
     * Every item the phrase names as well as the best one: a tie is a group, so «брёвна» gives all the logs and
     * «чернила» both ink sacs. The plainest name comes first: «ведро» is the empty bucket, not «ведро воды».
     *
     * A word the player said names the thing whose whole name it is: «тыквы» is the gourd «Тыква», never «Семена тыквы»,
     * whose own name the word «тыквы» leaves half said (owner, 27.11: «добудь блок тыквы» came back with seeds). The forms
     * of a Russian word are tried by its stem ({@link #stems}), so «тыкву», «тыквой» and «забора» are the same word as
     * «тыква» and «забор», and no list of forms is kept here.
     */
    public static List<Item> resolve(String phrase, Function<Item, String> names) {
        List<Match> ranked = ranked(phrase, names);
        List<Item> found = new ArrayList<>();
        for (Match match : ranked) found.add(match.item());
        return found;
    }

    /** An item the phrase names, its name, and how much of that name the phrase said (1.0: the whole name). */
    private record Match(Item item, String name, double said, int score) { }

    /**
     * The items the phrase names, best first: the whole name said beats a part of a longer one («Тыква» before «Семена
     * тыквы»), then the stronger match, then the plainest name. Only the best rank is a tie: what is not named as well is
     * no answer of the phrase at all.
     */
    private static List<Match> ranked(String phrase, Function<Item, String> names) {
        List<String> said = words(phrase);
        if (said.isEmpty()) return List.of();
        List<Match> found = new ArrayList<>();
        double bestSaid = 0;
        int bestScore = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String name = names.apply(item);
            if (name == null || name.isBlank()) continue;
            List<String> parts = words(name);
            int score = score(said, parts);
            if (score == 0) continue;
            double whole = whole(said, parts);
            if (whole > bestSaid + 1e-9) { bestSaid = whole; bestScore = score; found.clear(); }
            if (Math.abs(whole - bestSaid) <= 1e-9 && score >= bestScore) {
                if (score > bestScore) { bestScore = score; found.clear(); }
                found.add(new Match(item, name, whole, score));
            }
        }
        found.sort(Comparator.comparingInt(match -> match.name().length()));
        return found;
    }

    /** How much of the name the phrase said: the share of the name's words it answers, 1.0 for the whole name. */
    private static double whole(List<String> said, List<String> name) {
        if (name.isEmpty()) return 0;
        int answered = 0;
        for (String part : name) if (answered(part, said)) answered++;
        return (double) answered / name.size();
    }

    /**
     * The id of the block the phrase names: «блок лазурита» is the lapis block, «блок золота» the gold block — the item whose
     * name carries the word «блок» and answers the rest of the phrase, never the thing the block is made of («лазурит» is the
     * lapis its own block gives). Null when no block answers it: «блок тыквы» is the pumpkin itself, whose item is named
     * without «блок», and the plain name is the answer there.
     */
    public static String blockFor(String phrase, Function<Item, String> names) {
        List<String> said = words(phrase);
        if (said.isEmpty()) return null;
        int best = 0;
        Item picked = null;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String name = names.apply(item);
            if (name == null || name.isBlank()) continue;
            List<String> parts = words(name);
            List<String> rest = new ArrayList<>();
            boolean block = false;
            for (String part : parts) {
                if (part.startsWith("блок")) block = true;
                else rest.add(part);
            }
            if (!block) continue;
            int score = score(said, rest);
            if (score > best) { best = score; picked = item; }
        }
        return picked == null ? null : BuiltInRegistries.ITEM.getKey(picked).toString();
    }

    /**
     * The item tag the phrase names as a kind, or null when it names one thing: «забор» is every fence, «ступеньки» every
     * stair, «шерсть» every wool, so an order for the kind takes any of them that stands. The tag is the game's own — the one
     * whose members the phrase names together — never a list here.
     *
     * Only a bare kind is a group: «забор» names the kind, while «дубовый забор» and «дубовая калитка» name a member of it
     * (one whose whole name the phrase says), and a tie that is one thing and a longer name of it («тыква» and
     * «вырезанная тыква», «ведро» and «ведро воды») is one item, as it always was. The names of a kind need not have one
     * shape — «Дубовый забор» and «Забор из тёмного дуба» are both the fence — so the group is read from the tag that holds
     * every member the phrase names, never from the words' count (owner, 27.11: the fence order of one species went into a
     * wood chain while a fence of another stood about him).
     */
    public static String groupFor(String phrase, Function<Item, String> names) {
        List<String> said = words(phrase);
        if (said.size() != 1) return null;
        List<Item> tied = resolve(phrase, names);
        if (tied.size() < 2) return kindFor(phrase);
        java.util.Set<TagKey<Item>> common = null;
        for (Item item : tied) {
            java.util.Set<TagKey<Item>> tags = BuiltInRegistries.ITEM.wrapAsHolder(item).tags().collect(java.util.stream.Collectors.toSet());
            if (common == null) common = new java.util.HashSet<>(tags);
            else common.retainAll(tags);
            if (common.isEmpty()) return null;
        }
        // The game's own tag is the kind asked for — «#minecraft:fences», «#minecraft:fence_gates» — never a convention
        // tag of another namespace that happens to hold the same members; a mod's own thing that the game's tags do not
        // hold still answers by the tag it is a member of.
        TagKey<Item> exact = null, widest = null, exactOther = null, widestOther = null;
        long widestSize = 0, widestOtherSize = 0;
        for (TagKey<Item> tag : common) {
            var named = BuiltInRegistries.ITEM.getTag(tag).orElse(null);
            if (named == null) continue;
            long size = named.stream().count();
            boolean game = tag.location().getNamespace().equals("minecraft");
            if (game) {
                if (size == tied.size() && exact == null) exact = tag;
                if (size > widestSize) { widestSize = size; widest = tag; }
            } else {
                if (size == tied.size() && exactOther == null) exactOther = tag;
                if (size > widestOtherSize) { widestOtherSize = size; widestOther = tag; }
            }
        }
        TagKey<Item> picked = exact != null ? exact : widest != null ? widest : exactOther != null ? exactOther : widestOther;
        if (picked != null) return "#" + picked.location();
        return kindFor(phrase);
    }

    /**
     * The kinds the game's own names do not say: the player calls a fence gate «калитка» while the game names it «ворота»,
     * so no item name answers the word. The tag is the game's own ({@code #minecraft:fence_gates}); nothing of the world's
     * things is listed here, only the few words the player uses for a kind the game names otherwise.
     */
    private static final String[][] KIND_WORDS = {{"калитк", "#minecraft:fence_gates"}, {"ворот", "#minecraft:fence_gates"},
            {"забор", "#minecraft:fences"}, {"ступеньк", "#minecraft:stairs"}, {"плит", "#minecraft:slabs"},
            {"шерст", "#minecraft:wool"}, {"доск", "#minecraft:planks"}, {"бревн", "#minecraft:logs"}};

    /** The tag of a kind word the names cannot answer («калитка»), or null when the word is no kind at all. */
    private static String kindFor(String phrase) {
        List<String> said = words(phrase);
        if (said.size() != 1) return null;
        return kindOf(said.getFirst());
    }

    /** The tag a single word names as a kind the game's names cannot answer («калитку» the fence gates), or null. */
    private static String kindOf(String word) {
        for (String stem : stems(word))
            for (String[] kind : KIND_WORDS)
                if (stem.startsWith(kind[0]) && !members(kind[1]).isEmpty()) return kind[1];
        return null;
    }

    /**
     * The member of a kind a phrase names by a species word and the kind's own word («дубовую калитку» the oak gate, «каменные
     * ступеньки» the stone stair): the kind word is one the game's names do not answer, and the species word names one of its
     * members. Null when the phrase is no such pair, and the plainest member wins when a species word answers two of them
     * («дубовую» both the oak gate and the dark oak one, whose name says «из тёмного дуба»).
     */
    public static String memberFor(String phrase, Function<Item, String> names) {
        List<String> said = words(phrase);
        if (said.size() < 2) return null;
        String best = null;
        int bestAnswers = 0, bestLength = Integer.MAX_VALUE;
        for (String word : said) {
            String tag = kindOf(word);
            if (tag == null) continue;
            // A kind word the game's names answer («шерсть», «ступеньки», «доски») is theirs, not this table's: the plain
            // name rules above take it («белую шерсть» is the white wool by its own name).
            if (!resolve(word, names).isEmpty()) continue;
            for (Item member : members(tag)) {
                String name = names.apply(member);
                List<String> parts = words(name);
                int answers = 0;
                for (String other : said) if (!other.equals(word) && answered(other, parts)) answers++;
                // A member whose name answers none of the other words is no answer here (its name is another language's).
                if (answers == 0) continue;
                if (answers > bestAnswers || answers == bestAnswers && name.length() < bestLength) {
                    best = BuiltInRegistries.ITEM.getKey(member).toString();
                    bestAnswers = answers;
                    bestLength = name.length();
                }
            }
        }
        return best;
    }

    /** The items a tag names («#minecraft:fences» every fence), in the registry's own order; empty for an unknown tag. */
    public static List<Item> members(String tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.startsWith("#") ? tag.substring(1) : tag);
        if (id == null) return List.of();
        var named = BuiltInRegistries.ITEM.getTag(TagKey.create(net.minecraft.core.registries.Registries.ITEM, id)).orElse(null);
        return named == null ? List.of() : named.stream().map(net.minecraft.core.Holder::value).toList();
    }

    /**
     * The thing a craft order makes: the item itself, or, for a kind («сделай забор»), the member of it the chain can make
     * of what he carries — a fence of his planks, never the first fence of the registry whose own wood he has not. Null when
     * no member of the kind can be made.
     */
    public static String made(CompanionEntity companion, String item, int count) {
        if (item == null || !item.startsWith("#")) return item;
        for (Item member : members(item)) {
            String id = BuiltInRegistries.ITEM.getKey(member).toString();
            if (!companion.obtainPlan(id, Math.max(1, count)).refused()) return id;
        }
        return null;
    }

    /**
     * How well the phrase names this item: the sum of its words' best matches, 0 for an item it does not name at all. A
     * word is tried in every form it takes in Russian ({@link #stems}), so «забора» answers «забор» and «тыквой» «тыква».
     */
    private static int score(List<String> said, List<String> name) {
        int total = 0;
        for (String word : said) {
            int best = 0;
            for (String form : stems(word)) for (String part : name) best = Math.max(best, match(form, part));
            total += best;
        }
        return total;
    }

    /**
     * The endings a Russian word takes, longest first: «тыкву» and «тыквой» give «тыкв», «забора» «забор», «калитку»
     * «калитк», «вырезанную» «вырезанн». A stem is at least four letters, so a short word keeps its whole self («кура» is
     * no «курица», and that is what the model is for).
     */
    private static final String[] ENDINGS = {"ями", "ами", "ыми", "ими", "ого", "его", "ому", "ему", "ая", "яя", "ое", "ее",
            "ые", "ие", "ым", "им", "ых", "их", "ой", "ей", "ою", "ею", "ую", "юю", "ам", "ям", "ах", "ях", "ов", "ев",
            "ий", "ия", "ию", "ии", "ом", "ем", "а", "я", "у", "ю", "ы", "и", "е", "о", "ь"};

    /** The forms of a word: itself, and what is left when one of its Russian endings is taken off. */
    static List<String> stems(String word) {
        List<String> forms = new ArrayList<>();
        forms.add(word);
        for (String ending : ENDINGS) {
            if (!word.endsWith(ending)) continue;
            String stem = word.substring(0, word.length() - ending.length());
            if (stem.length() >= 4) forms.add(stem);
            break;
        }
        return forms;
    }

    /** Whether one of the said words is this word of a name, in any of its forms. */
    private static boolean answered(String part, List<String> said) {
        for (String word : said) for (String stem : stems(word)) if (match(stem, part) > 0) return true;
        return false;
    }

    /**
     * One word against one word of a name: the same word, one beginning the other, a common stem («свининка» and
     * «свинина», «камня» and «камень» by their consonants), or nothing. A short word needs its whole stem: «кура» is no
     * «курица», and that is what the model is for.
     */
    private static int match(String said, String part) {
        if (said.equals(part)) return 100;
        if (said.length() >= 4 && part.startsWith(said)) return 80;
        if (part.length() >= 4 && said.startsWith(part)) return 80;
        if (said.length() >= 5 && part.length() >= 5 && common(said, part) >= 4) return 60;
        String one = consonants(said), other = consonants(part);
        return one.length() >= 3 && one.equals(other) ? 50 : 0;
    }

    private static int common(String one, String other) {
        int same = 0;
        while (same < one.length() && same < other.length() && one.charAt(same) == other.charAt(same)) same++;
        return same;
    }

    /** «камня» and «камень» are one stem by their consonants; a word of two of them («кура», «мясо») says too little. */
    private static String consonants(String word) {
        StringBuilder skeleton = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char letter = word.charAt(i);
            if ("аеёиоуыэюяьъ".indexOf(letter) < 0) skeleton.append(letter);
        }
        return skeleton.toString();
    }

    /** The words of a phrase: lower case, ё as е, letters and digits only. */
    static List<String> words(String phrase) {
        List<String> words = new ArrayList<>();
        if (phrase == null) return words;
        for (String word : phrase.toLowerCase(Locale.ROOT).replace('ё', 'е').split("[^\\p{L}\\p{N}]+")) {
            if (!word.isBlank()) words.add(word);
        }
        return words;
    }
}

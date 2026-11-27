package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
     */
    public static List<Item> resolve(String phrase, Function<Item, String> names) {
        List<String> said = words(phrase);
        if (said.isEmpty()) return List.of();
        int best = 0;
        Map<Item, String> tied = new LinkedHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String name = names.apply(item);
            if (name == null || name.isBlank()) continue;
            int score = score(said, words(name));
            if (score == 0) continue;
            if (score > best) { best = score; tied.clear(); }
            if (score == best) tied.put(item, name);
        }
        List<Item> found = new ArrayList<>(tied.keySet());
        found.sort(Comparator.comparingInt(item -> tied.get(item).length()));
        return found;
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
     * whose members the phrase names together — never a list here. A tie that is one thing and a longer name of it («тыква»
     * and «вырезанная тыква», «ведро» and «ведро воды») is one item, as it always was.
     */
    public static String groupFor(String phrase, Function<Item, String> names) {
        List<Item> tied = resolve(phrase, names);
        if (tied.size() < 2) return null;
        // A kind is one and the same name but for one word: «еловый забор» and «дубовый забор» are fences, while «тыква» and
        // «вырезанная тыква» are one thing and its carving, and «ведро» and «ведро воды» one thing and its filling — the
        // plainer name is the answer there, as it always was.
        int words = -1;
        java.util.Set<String> shared = null;
        for (Item item : tied) {
            List<String> name = words(names.apply(item));
            if (name.isEmpty()) return null;
            if (words == -1) { words = name.size(); shared = new java.util.HashSet<>(name); }
            else if (name.size() != words) return null;
            else shared.retainAll(name);
        }
        if (shared.size() < words - 1) return null;
        java.util.Set<TagKey<Item>> common = null;
        for (Item item : tied) {
            java.util.Set<TagKey<Item>> tags = BuiltInRegistries.ITEM.wrapAsHolder(item).tags().collect(java.util.stream.Collectors.toSet());
            if (common == null) common = new java.util.HashSet<>(tags);
            else common.retainAll(tags);
            if (common.isEmpty()) return null;
        }
        // The tag that names exactly this group is the kind asked for; else the widest tag that holds every member, so a
        // name only some of the kind answer to (a test's stand-in language, a mod's own fence) still names the kind.
        TagKey<Item> exact = null, widest = null;
        long widestSize = 0;
        for (TagKey<Item> tag : common) {
            var named = BuiltInRegistries.ITEM.getTag(tag).orElse(null);
            if (named == null) continue;
            long size = named.stream().count();
            if (size == tied.size() && exact == null) exact = tag;
            if (size > widestSize) { widestSize = size; widest = tag; }
        }
        TagKey<Item> picked = exact != null ? exact : widest;
        return picked == null ? null : "#" + picked.location();
    }

    /** The items a tag names («#minecraft:fences» every fence), in the registry's own order; empty for an unknown tag. */
    public static List<Item> members(String tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.startsWith("#") ? tag.substring(1) : tag);
        if (id == null) return List.of();
        var named = BuiltInRegistries.ITEM.getTag(TagKey.create(net.minecraft.core.registries.Registries.ITEM, id)).orElse(null);
        return named == null ? List.of() : named.stream().map(net.minecraft.core.Holder::value).toList();
    }

    /**
     * How well the phrase names this item: the sum of its words' best matches, 0 for an item it does not name at all. */
    private static int score(List<String> said, List<String> name) {
        int total = 0;
        for (String word : said) {
            int best = 0;
            for (String part : name) best = Math.max(best, match(word, part));
            total += best;
        }
        return total;
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

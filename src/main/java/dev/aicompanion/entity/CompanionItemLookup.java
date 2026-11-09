package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
        String path = BuiltInRegistries.ITEM.getKey(picked).getPath();
        if (path.startsWith("cooked_")) {
            var raw = BuiltInRegistries.ITEM.getOptional(ResourceLocation.withDefaultNamespace(path.substring("cooked_".length())));
            if (raw.isPresent()) picked = raw.get();
        }
        return BuiltInRegistries.ITEM.getKey(picked).toString();
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

    /** How well the phrase names this item: the sum of its words' best matches, 0 for an item it does not name at all. */
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

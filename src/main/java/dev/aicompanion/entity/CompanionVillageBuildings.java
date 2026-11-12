package dev.aicompanion.entity;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The closed catalogue of buildings he can build from the game's own village structure templates (every biome). Only buildings he can
 * finish from the ground and from dirt scaffolding without a fluid, a farmland or a plant pot: every block is an item. A name that is
 * not here (a model's guess included) names no building; a kind («кузница») names the building of that kind that suits the place.
 */
public final class CompanionVillageBuildings {
    /** {@code path} is the template under {@code village/}; the size is the template's (the catalogue test compares it): width, depth, height. */
    public record Building(String name, String kind, String path, int width, int depth, int height) {
        public ResourceLocation template() { return ResourceLocation.withDefaultNamespace("village/" + path); }
        /** The village type of the template: plains, desert, savanna, taiga or snowy. */
        public String biome() { return path.substring(0, path.indexOf('/')); }
    }

    /** A kind of building: what a player calls it (forms «построй кузницу»), and the stem that tells a model's invention is of that kind. */
    private record Kind(String label, String stem, List<String> forms) { }

    /** The ceiling of a building: its width x depth x height in blocks, and its height alone (the tallest one he finished from dirt scaffolding when the catalogue was chosen). */
    public static final int MAX_VOLUME = 1500, MAX_HEIGHT = 11;

    private static final List<Kind> KINDS = List.of(
            new Kind("дом", "дом", List.of()),
            new Kind("кузница", "кузн", List.of("кузница", "кузницу")),
            new Kind("храм", "храм", List.of("храм", "церковь")));

    private static final List<Building> ALL = List.of(
            new Building("кузница равнин", "кузница", "plains/houses/plains_armorer_house_1", 9, 8, 8),
            new Building("деревенский дом", "дом", "plains/houses/plains_small_house_1", 7, 7, 7),
            new Building("деревенский дом 2", "дом", "plains/houses/plains_small_house_2", 7, 7, 7),
            new Building("бревенчатый дом", "дом", "plains/houses/plains_small_house_3", 7, 7, 7),
            new Building("деревенский дом 3", "дом", "plains/houses/plains_small_house_4", 7, 7, 7),
            new Building("деревенский дом 4", "дом", "plains/houses/plains_small_house_6", 7, 7, 7),
            new Building("деревенский дом 5", "дом", "plains/houses/plains_small_house_7", 7, 8, 7),
            new Building("кузница равнин 2", "кузница", "plains/houses/plains_tool_smith_1", 9, 12, 6),
            new Building("пустынный дом", "дом", "desert/houses/desert_small_house_4", 5, 5, 5),
            new Building("саванный дом", "дом", "savanna/houses/savanna_small_house_6", 7, 7, 7),
            new Building("саванный дом 2", "дом", "savanna/houses/savanna_small_house_8", 6, 7, 7),
            new Building("храм саванны", "храм", "savanna/houses/savanna_temple_2", 7, 9, 7),
            new Building("таёжный дом 2", "дом", "taiga/houses/taiga_medium_house_2", 7, 8, 11),
            new Building("таёжный дом", "дом", "taiga/houses/taiga_small_house_4", 7, 8, 6),
            new Building("кузница тайги", "кузница", "taiga/houses/taiga_tool_smith_1", 11, 8, 6),
            new Building("снежный дом", "дом", "snowy/houses/snowy_medium_house_3", 5, 7, 5));

    private CompanionVillageBuildings() { }

    public static List<Building> all() { return ALL; }

    /** Lower case, «е» for «ё», one plain space for any run of spaces, tabs and no-break spaces. */
    private static String normalize(String text) { return text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[\\s\\u00A0\\u2007\\u202F]+", " ").trim(); }

    /** The plain 5×5 house by any common name a model may use for it: «дом», «домик», «маленький дом», «дом 5×5», «small house»; no catalogue building is meant. */
    public static boolean plankHouse(String said) {
        if (said == null) return false;
        String key = normalize(said).replace('×', 'x').replace('х', 'x');
        return key.matches("(?:(?:небольшой|маленький|малый|простой|деревянный|одноэтажный) )*(?:дом|домик)(?: 5 ?x ?5)?|(?:(?:small|little|simple|wooden|plank) )*(?:house|home|cabin)(?: 5 ?x ?5)?");
    }

    /** The building of that exact name (any case, «е» for «ё»), or null. */
    public static Building find(String name) {
        if (name == null) return null;
        String key = normalize(name);
        for (Building building : ALL) if (normalize(building.name()).equals(key)) return building;
        return null;
    }

    /** The label of the kind that {@code said} names exactly (its label, and the forms a player says when {@code spoken}), or null. */
    private static String kindLabel(String said, boolean spoken) {
        if (said == null) return null;
        String key = normalize(said);
        for (Kind kind : KINDS) {
            if (kind.label().equals(key) && !kind.forms().isEmpty()) return kind.label();
            if (spoken && kind.forms().contains(key)) return kind.label();
        }
        return null;
    }

    /** The catalogue name or the kind label {@code said} names («кузницу» is the kind «кузница» when {@code spoken}), or null: then it is nothing of the catalogue. */
    public static String named(String said, boolean spoken) {
        Building building = find(said);
        return building != null ? building.name() : kindLabel(said, spoken);
    }

    /**
     * The building {@code item} stands for: the building of that name, or of that kind the one whose village type suits {@code here}, else the first
     * of the kind (plains first). No randomness: the same words in the same place are the same building. Null when it is none of the catalogue.
     */
    public static Building resolve(String item, Holder<Biome> here) {
        Building exact = find(item);
        if (exact != null) return exact;
        String label = kindLabel(item, false);
        if (label == null) return null;
        Building first = null;
        for (Building building : ALL) {
            if (!building.kind().equals(label)) continue;
            if (first == null) first = building;
            if (here != null && here.is(switch (building.biome()) {
                case "desert" -> BiomeTags.HAS_VILLAGE_DESERT;
                case "savanna" -> BiomeTags.HAS_VILLAGE_SAVANNA;
                case "snowy" -> BiomeTags.HAS_VILLAGE_SNOWY;
                case "taiga" -> BiomeTags.HAS_VILLAGE_TAIGA;
                default -> BiomeTags.HAS_VILLAGE_PLAINS;
            })) return building;
        }
        return first;
    }

    /** {@link #resolve(String, Holder)} for the place the player stands on (his own, without a player). */
    static Building resolve(String item, CompanionEntity owner) {
        var player = owner.getOwnerPlayer();
        return resolve(item, owner.level().getBiome((player == null ? owner : player).blockPosition()));
    }

    /** What he says to a request that is nothing of the catalogue: up to three buildings of the kind it looks like, else all the kinds he builds, houses and the plain 5×5 house among them. */
    public static String refusal(String said) {
        String key = said == null ? "" : normalize(said);
        // The specific kinds first: «кузница для дома» is a smithy, a house is only the last guess.
        for (Kind kind : KINDS.reversed()) {
            if (kind.stem().isEmpty() || !key.contains(kind.stem())) continue;
            return "Такой постройки у меня нет. Похожие: " + String.join(", ", ALL.stream().filter(b -> b.kind().equals(kind.label())).limit(3).map(Building::name).toList()) + ".";
        }
        return "Такой постройки у меня нет. Умею строить: простой дом 5×5, " + String.join(", ", KINDS.stream().map(Kind::label).toList()) + " (по именам из набора).";
    }

    /** What the model is told of the catalogue: the names by kind, each with width x depth x height. */
    public static String modelList() {
        List<String> lines = new ArrayList<>();
        for (Kind kind : KINDS) {
            var of = ALL.stream().filter(b -> b.kind().equals(kind.label())).toList();
            if (!of.isEmpty()) lines.add(kind.label() + ": " + String.join(", ", of.stream().map(b -> b.name() + " " + b.width() + "×" + b.depth() + "×" + b.height()).toList()));
        }
        return String.join("; ", lines);
    }

    /** What the model is told of the catalogue must stay within this many characters (the catalogue test checks it). */
    public static final int MODEL_TEXT_LIMIT = 1500;

    /** The part of the model's system prompt about buildings: how to ask for one, and the catalogue by kind with width x depth x height. */
    public static String modelText() {
        return "Без item — дом 5×5 («дом», «домик» тоже он). item — точное имя здания деревни из списка (Ш×Г×В): " + modelList() + ". item=resume — достроить то, что осталось недостроенным («дострой», «продолжи стройку»). "
                + "Просят то, чего нет в списке, — не выдумывай имя: скажи, что такого нет, и назови похожее, build_house не запускай. ";
    }
}

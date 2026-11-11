package dev.aicompanion.entity;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * The closed catalogue of buildings he can build from the game's own structure templates. One-room houses without potted plants
 * only: every block is an item, and what is out of reach from the ground or from inside (a gabled roof) is placed from dirt
 * scaffolding. A name that is not here (a model's guess included) names no building.
 */
public final class CompanionVillageBuildings {
    public record Building(String name, ResourceLocation template) { }

    private static final List<Building> ALL = List.of(
            new Building("пустынный дом", ResourceLocation.withDefaultNamespace("village/desert/houses/desert_small_house_4")),
            new Building("песчаный дом", ResourceLocation.withDefaultNamespace("village/desert/houses/desert_small_house_8")),
            new Building("деревенский дом", ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1")),
            new Building("бревенчатый дом", ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_3")));

    private CompanionVillageBuildings() { }

    /** The building of that name (any case, «е» for «ё»), or null. */
    public static Building find(String name) {
        if (name == null) return null;
        String key = name.toLowerCase(Locale.ROOT).replace('ё', 'е').trim().replaceAll("\\s+", " ");
        for (Building building : ALL) if (building.name().equals(key)) return building;
        return null;
    }

    public static String names() {
        return String.join(", ", ALL.stream().map(Building::name).toList());
    }
}

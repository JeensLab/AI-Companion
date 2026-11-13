package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a building of the catalogue takes, as the bag stands: one row per material with what it needs, what the bag holds and who gets
 * what is missing. The window of buildings shows the rows; the short line he says is made of the same result, so the two cannot differ.
 * Reading it changes neither the bag nor the world.
 */
public final class CompanionBuildSummary {
    /** Who provides the missing part of a row: nobody (the bag covers it), he (gets or makes it) or the player (he cannot). */
    public enum Who { COVERED, COMPANION, PLAYER }

    public record Row(Item item, int need, int have, Who who) { }

    /** {@code width x depth x height} are the template's own size. */
    public record Summary(String name, int width, int depth, int height, List<Row> rows) {
        public boolean playerBrings() { return rows.stream().anyMatch(row -> row.who() == Who.PLAYER); }
        public boolean companionGets() { return rows.stream().anyMatch(row -> row.who() == Who.COMPANION); }

        /** The one line he says when the order is made: the building, its width by depth, and who is to provide what is missing. */
        public String line() {
            String title = name.isEmpty() ? name : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + name.substring(1);
            return title + ", " + width + " на " + depth + ". "
                    + (playerBrings() ? "Часть материалов принесёшь ты — глянь список." : companionGets() ? "Всё добуду сам." : "Материалы есть.");
        }
    }

    private CompanionBuildSummary() { }

    static Summary of(ServerLevel level, CompanionEntity owner, CompanionVillageBuildings.Building building, CompanionTemplates.Loaded read) {
        var materials = new CompanionHouseMaterials(owner);
        materials.chooseWoods(level, read.cells());
        Map<Item, Integer> required = required(materials, read);
        var lacking = materials.next(level, required).lacking();
        List<Row> rows = new ArrayList<>();
        required.forEach((item, need) -> {
            int have = carried(owner, item);
            rows.add(new Row(item, need, have, have >= need ? Who.COVERED : lacking.containsKey(item) ? Who.PLAYER : Who.COMPANION));
        });
        return new Summary(building.name(), read.sizeX(), read.sizeZ(), read.sizeY(), List.copyOf(rows));
    }

    /** The items the building takes, as it would be built with these materials, by the layer they are first needed in (planks last, as the building plans its materials). */
    static Map<Item, Integer> required(CompanionHouseMaterials materials, CompanionTemplates.Loaded read) {
        var cells = read.cells().stream().sorted(Comparator.comparingInt(CompanionTemplates.Cell::stage)).toList();
        Map<Item, Integer> required = new LinkedHashMap<>();
        for (boolean last : new boolean[]{false, true}) for (var cell : cells) {
            Item item = materials.adapt(cell.state()).getBlock().asItem();
            if (new ItemStack(item).is(ItemTags.PLANKS) == last) required.merge(item, 1, Integer::sum);
        }
        return required;
    }

    private static int carried(CompanionEntity owner, Item item) {
        int count = owner.getMainHandItem().is(item) ? owner.getMainHandItem().getCount() : 0;
        if (owner.getOffhandItem().is(item)) count += owner.getOffhandItem().getCount();
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(item)) count += owner.getItem(slot).getCount();
        return count;
    }
}

package dev.aicompanion.entity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/**
 * What a trade_items step asks for: the goods (an item, and for a book its enchantment and level), optionally what to pay with, the
 * trader's profession, and the player's word for a fresh villager. It travels in the step's {@code item} text as
 * {@code get|enchantment|level|pay|profession|consent}, so the step keeps its shape. IDs are complete here: the codec adds
 * {@code minecraft:} to what the model wrote bare (the trader line the model reads has none).
 */
public record CompanionTradeOrder(String get, String enchantment, int level, String pay, String profession, boolean consent) {
    private static final int MAX_LEVEL = 10;

    /** The step's fields checked against the registries; anything wrong spoils the whole plan. Level 0 is any level. */
    public static CompanionTradeOrder read(JsonObject step) {
        String get = id(text(step, "item"));
        if (get.isEmpty() || !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(get)) || BuiltInRegistries.ITEM.get(ResourceLocation.parse(get)) == Items.AIR) throw new IllegalStateException();
        String enchantment = id(text(step, "enchantment"));
        int level = 0;
        if (step.has("level")) {
            level = step.get("level").getAsBigDecimal().intValueExact();
            if (enchantment.isEmpty() || level < 1 || level > MAX_LEVEL) throw new IllegalStateException();
        }
        // A book is bought by its enchantment; the enchantment itself is looked up in the world's registry when there is one.
        if (get.equals("minecraft:enchanted_book") && enchantment.isEmpty()) throw new IllegalStateException();
        if (!enchantment.isEmpty()) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null && !server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).containsKey(ResourceLocation.parse(enchantment))) throw new IllegalStateException();
        }
        String pay = id(text(step, "pay"));
        if (!pay.isEmpty() && (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(pay)) || BuiltInRegistries.ITEM.get(ResourceLocation.parse(pay)) == Items.AIR)) throw new IllegalStateException();
        String profession = id(text(step, "profession"));
        if (!profession.isEmpty()) {
            if (!profession.startsWith("minecraft:")) throw new IllegalStateException();
            String key = profession.substring("minecraft:".length());
            boolean known = key.equals(CompanionTraders.WANDERING) || BuiltInRegistries.VILLAGER_PROFESSION.containsKey(ResourceLocation.parse(profession)) && !key.equals("none") && !key.equals("nitwit");
            if (!known) throw new IllegalStateException();
            profession = key;
        }
        boolean consent = false;
        if (step.has("consent")) {
            JsonElement flag = step.get("consent");
            if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) throw new IllegalStateException();
            consent = flag.getAsBoolean();
        }
        return new CompanionTradeOrder(get, enchantment, level, pay, profession, consent);
    }

    private static String text(JsonObject step, String field) {
        if (!step.has(field)) return "";
        JsonElement value = step.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalStateException();
        return value.getAsString().trim();
    }

    /** «arrow» is {@code minecraft:arrow}; anything that is not an ID at all is refused. */
    private static String id(String written) {
        if (written.isEmpty()) return "";
        String full = written.contains(":") ? written : "minecraft:" + written;
        if (ResourceLocation.tryParse(full) == null) throw new IllegalStateException();
        return full;
    }

    public String pack() { return get + "|" + enchantment + "|" + level + "|" + pay + "|" + profession + "|" + consent; }

    /** Null for text that is not a packed order. */
    public static CompanionTradeOrder parse(String packed) {
        String[] parts = packed.split("[|]", -1);
        if (parts.length != 6 || parts[0].isEmpty()) return null;
        try { return new CompanionTradeOrder(parts[0], parts[1], Integer.parseInt(parts[2]), parts[3], parts[4], Boolean.parseBoolean(parts[5])); }
        catch (NumberFormatException exception) { return null; }
    }
}

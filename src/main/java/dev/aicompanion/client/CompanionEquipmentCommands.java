package dev.aicompanion.client;

/** Small local grammar for explicit requests; compound requests are left to the model. */
public final class CompanionEquipmentCommands {
    private CompanionEquipmentCommands() { }

    public static String parse(String text) {
        String normalized = text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        if (normalized.contains(" и ")) return null;
        if (normalized.matches("(?sU).*\\bне\\s+(используй|возьми|экипируй|надень|одень|достань).*")) return null;
        boolean remove = normalized.matches("(?s).*(убери|сними|спрячь).*");
        boolean equip = normalized.matches("(?s).*(возьми|достань|экипируй|надень|одень|используй).*");
        if (!remove && !equip) return null;
        String category = normalized.contains("кирк") ? "pickaxe" : normalized.contains("топор") ? "axe"
                : normalized.contains("меч") ? "sword" : normalized.contains("лопат") ? "shovel"
                : normalized.contains("мотыг") ? "hoe" : normalized.contains("щит") ? "shield"
                : normalized.contains("шлем") ? "helmet" : normalized.contains("нагрудник") ? "chestplate"
                : normalized.contains("понож") ? "leggings" : normalized.contains("ботин") ? "boots" : null;
        if (category == null) return remove && normalized.contains("рук") ? "companion unequip mainhand" : null;
        String slot = switch (category) {
            case "helmet" -> "head";
            case "chestplate" -> "chest";
            case "leggings" -> "legs";
            case "boots" -> "feet";
            case "shield" -> "offhand";
            default -> normalized.contains("лев") || normalized.contains("вторую руку") ? "offhand" : "mainhand";
        };
        if (remove) return "companion unequip " + slot;
        String material = normalized.contains("деревян") ? "wooden" : normalized.contains("камен") ? "stone"
                : normalized.contains("желез") ? "iron" : normalized.contains("золот") ? "golden"
                : normalized.contains("алмаз") ? "diamond" : normalized.contains("незерит") ? "netherite"
                : normalized.contains("кожан") ? "leather" : normalized.contains("кольчуж") ? "chainmail" : null;
        String item = material == null || "shield".equals(category) ? category : "minecraft:" + material + "_" + category;
        return "companion equip " + item + " " + slot;
    }
}

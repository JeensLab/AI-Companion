package dev.aicompanion.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * Game animals for meat (the player 22.10: «чтобы он сам мог охотиться и убивать животных, жарить их мясо»). A player's rules:
 * grown animals only, never a named or leashed one (somebody's). A lone animal is game too (game log 22.10: one pig nearby was
 * spared as «the last pair», and the player asked for exactly that pig); the ones to keep get a name tag.
 */
public final class CompanionGame {
    private CompanionGame() { }

    public enum Kind {
        COW(EntityType.COW, Items.BEEF, "cow", "beef", "корову", "коровы", "коров"),
        PIG(EntityType.PIG, Items.PORKCHOP, "pig", "porkchop", "свинью", "свиньи", "свиней"),
        SHEEP(EntityType.SHEEP, Items.MUTTON, "sheep", "mutton", "овцу", "овцы", "овец"),
        CHICKEN(EntityType.CHICKEN, Items.CHICKEN, "chicken", "chicken", "курицу", "курицы", "куриц"),
        RABBIT(EntityType.RABBIT, Items.RABBIT, "rabbit", "rabbit", "кролик", "кролика", "кроликов");

        public final EntityType<?> type;
        public final Item meat;
        public final String key, one, few, many;
        private final String meatKey;
        Kind(EntityType<?> type, Item meat, String key, String meatKey, String one, String few, String many) {
            this.type = type; this.meat = meat; this.key = key; this.meatKey = meatKey; this.one = one; this.few = few; this.many = many;
        }

        /** The animal or its meat, raw or cooked, with or without «minecraft:»; null for anything else (and for «any»). */
        public static Kind of(String item) {
            String id = item == null ? "" : item.trim().toLowerCase(Locale.ROOT).replaceFirst("^food:", "").replaceFirst("^minecraft:", "").replaceFirst("^cooked_", "");
            for (Kind kind : values()) if (id.equals(kind.key) || id.equals(kind.meatKey)) return kind;
            return null;
        }
        static Kind of(Animal animal) {
            for (Kind kind : values()) if (animal.getType() == kind.type) return kind;
            return null;
        }
        /** «добыл 1 корову», «3 коровы», «5 коров». */
        public String count(int count) {
            int last = count % 10, tens = count % 100;
            return count + " " + (last == 1 && tens != 11 ? one : last >= 2 && last <= 4 && (tens < 12 || tens > 14) ? few : many);
        }
    }

    /** smelt_items item for «пожарь мясо» after hunting any game: the raw meat carried most of. */
    public static final String ANY_MEAT = "minecraft:meat";
    /** A food run searches for up to its batch size and can cook a smaller real catch when no more eligible game exists. */
    public static boolean foodRun(String item) { return "food".equals(item) || item != null && item.startsWith("food:") && Kind.of(item) != null; }

    /** «any» and the like: whatever game is nearest. */
    public static boolean anyKind(String item) {
        String id = item == null ? "" : item.trim().toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
        return id.isEmpty() || List.of("any", "meat", "animals", "animal", "food").contains(id);
    }

    /** Grown and nobody's. */
    static boolean huntable(Animal animal, Kind kind) {
        return animal.isAlive() && animal.getType() == kind.type && !animal.isBaby() && !animal.hasCustomName() && !animal.isLeashed()
                && !animal.isVehicle() && !animal.isPassenger();
    }
}

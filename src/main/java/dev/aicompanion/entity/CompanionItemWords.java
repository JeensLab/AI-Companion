package dev.aicompanion.entity;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Russian names of the things that lie on the ground. The server's own language is en_us, so {@code getHoverName()}
 * reads «Rotten Flesh», «Water Bucket» and «Diamond Pickaxe» to the player; the pickup's lines take their words from
 * here instead. A thing the table does not know keeps its own name in quotes — better than nothing, but every common
 * drop is listed.
 */
final class CompanionItemWords {
    private CompanionItemWords() { }

    /** The three forms the pickup's lines need: «курица лежит», «подобрал курицу», «нет курицы». */
    record Forms(String name, String accusative, String genitive) { }

    private static final Map<Item, Forms> WORDS = new HashMap<>();

    private static void put(Item item, String name, String accusative, String genitive) {
        WORDS.put(item, new Forms(name, accusative, genitive));
    }

    static {
        // Meat, raw and cooked: the meat meaning itself takes the raw word, the list names the stack it sees.
        put(Items.CHICKEN, "курица", "курицу", "курицы");
        put(Items.COOKED_CHICKEN, "жареная курица", "жареную курицу", "жареной курицы");
        put(Items.BEEF, "говядина", "говядину", "говядины");
        put(Items.COOKED_BEEF, "жареная говядина", "жареную говядину", "жареной говядины");
        put(Items.PORKCHOP, "свинина", "свинину", "свинины");
        put(Items.COOKED_PORKCHOP, "жареная свинина", "жареную свинину", "жареной свинины");
        put(Items.MUTTON, "баранина", "баранину", "баранины");
        put(Items.COOKED_MUTTON, "жареная баранина", "жареную баранину", "жареной баранины");
        put(Items.RABBIT, "крольчатина", "крольчатину", "крольчатины");
        put(Items.COOKED_RABBIT, "жареная крольчатина", "жареную крольчатину", "жареной крольчатины");
        put(Items.ROTTEN_FLESH, "гнилая плоть", "гнилую плоть", "гнилой плоти");
        put(Items.INK_SAC, "чернильный мешок", "чернильный мешок", "чернильного мешка");
        put(Items.GLOW_INK_SAC, "светящийся чернильный мешок", "светящийся чернильный мешок", "светящегося чернильного мешка");
        // Buckets: the pickup knows them by meaning, the list names what it sees.
        put(Items.BUCKET, "ведро", "ведро", "ведра");
        put(Items.WATER_BUCKET, "ведро воды", "ведро воды", "ведра воды");
        put(Items.LAVA_BUCKET, "ведро лавы", "ведро лавы", "ведра лавы");
        put(Items.MILK_BUCKET, "ведро молока", "ведро молока", "ведра молока");
        put(Items.POWDER_SNOW_BUCKET, "ведро рыхлого снега", "ведро рыхлого снега", "ведра рыхлого снега");
        put(Items.COD_BUCKET, "ведро с треской", "ведро с треской", "ведра с треской");
        put(Items.SALMON_BUCKET, "ведро с лососем", "ведро с лососем", "ведра с лососем");
        put(Items.PUFFERFISH_BUCKET, "ведро с иглобрюхом", "ведро с иглобрюхом", "ведра с иглобрюхом");
        put(Items.TROPICAL_FISH_BUCKET, "ведро с тропической рыбой", "ведро с тропической рыбой", "ведра с тропической рыбой");
        put(Items.AXOLOTL_BUCKET, "ведро с аксолотлем", "ведро с аксолотлем", "ведра с аксолотлем");
        put(Items.TADPOLE_BUCKET, "ведро с головастиком", "ведро с головастиком", "ведра с головастиком");
        // Tools and arms.
        put(Items.WOODEN_PICKAXE, "деревянная кирка", "деревянную кирку", "деревянной кирки");
        put(Items.STONE_PICKAXE, "каменная кирка", "каменную кирку", "каменной кирки");
        put(Items.IRON_PICKAXE, "железная кирка", "железную кирку", "железной кирки");
        put(Items.GOLDEN_PICKAXE, "золотая кирка", "золотую кирку", "золотой кирки");
        put(Items.DIAMOND_PICKAXE, "алмазная кирка", "алмазную кирку", "алмазной кирки");
        put(Items.NETHERITE_PICKAXE, "незеритовая кирка", "незеритовую кирку", "незеритовой кирки");
        put(Items.WOODEN_AXE, "деревянный топор", "деревянный топор", "деревянного топора");
        put(Items.STONE_AXE, "каменный топор", "каменный топор", "каменного топора");
        put(Items.IRON_AXE, "железный топор", "железный топор", "железного топора");
        put(Items.GOLDEN_AXE, "золотой топор", "золотой топор", "золотого топора");
        put(Items.DIAMOND_AXE, "алмазный топор", "алмазный топор", "алмазного топора");
        put(Items.NETHERITE_AXE, "незеритовый топор", "незеритовый топор", "незеритового топора");
        put(Items.WOODEN_SHOVEL, "деревянная лопата", "деревянную лопату", "деревянной лопаты");
        put(Items.STONE_SHOVEL, "каменная лопата", "каменную лопату", "каменной лопаты");
        put(Items.IRON_SHOVEL, "железная лопата", "железную лопату", "железной лопаты");
        put(Items.GOLDEN_SHOVEL, "золотая лопата", "золотую лопату", "золотой лопаты");
        put(Items.DIAMOND_SHOVEL, "алмазная лопата", "алмазную лопату", "алмазной лопаты");
        put(Items.NETHERITE_SHOVEL, "незеритовая лопата", "незеритовую лопату", "незеритовой лопаты");
        put(Items.WOODEN_SWORD, "деревянный меч", "деревянный меч", "деревянного меча");
        put(Items.STONE_SWORD, "каменный меч", "каменный меч", "каменного меча");
        put(Items.IRON_SWORD, "железный меч", "железный меч", "железного меча");
        put(Items.GOLDEN_SWORD, "золотой меч", "золотой меч", "золотого меча");
        put(Items.DIAMOND_SWORD, "алмазный меч", "алмазный меч", "алмазного меча");
        put(Items.NETHERITE_SWORD, "незеритовый меч", "незеритовый меч", "незеритового меча");
        put(Items.WOODEN_HOE, "деревянная тяпка", "деревянную тяпку", "деревянной тяпки");
        put(Items.STONE_HOE, "каменная тяпка", "каменную тяпку", "каменной тяпки");
        put(Items.IRON_HOE, "железная тяпка", "железную тяпку", "железной тяпки");
        put(Items.GOLDEN_HOE, "золотая тяпка", "золотую тяпку", "золотой тяпки");
        put(Items.DIAMOND_HOE, "алмазная тяпка", "алмазную тяпку", "алмазной тяпки");
        put(Items.NETHERITE_HOE, "незеритовая тяпка", "незеритовую тяпку", "незеритовой тяпки");
        put(Items.BOW, "лук", "лук", "лука");
        put(Items.CROSSBOW, "арбалет", "арбалет", "арбалета");
        put(Items.ARROW, "стрела", "стрелу", "стрелы");
        put(Items.SHIELD, "щит", "щит", "щита");
        put(Items.FLINT_AND_STEEL, "огниво", "огниво", "огнива");
        put(Items.SHEARS, "ножницы", "ножницы", "ножниц");
        put(Items.FISHING_ROD, "удочка", "удочку", "удочки");
        // Armour.
        put(Items.LEATHER_HELMET, "кожаный шлем", "кожаный шлем", "кожаного шлема");
        put(Items.IRON_HELMET, "железный шлем", "железный шлем", "железного шлема");
        put(Items.DIAMOND_HELMET, "алмазный шлем", "алмазный шлем", "алмазного шлема");
        put(Items.IRON_CHESTPLATE, "железный нагрудник", "железный нагрудник", "железного нагрудника");
        put(Items.DIAMOND_CHESTPLATE, "алмазный нагрудник", "алмазный нагрудник", "алмазного нагрудника");
        put(Items.IRON_LEGGINGS, "железные штаны", "железные штаны", "железных штанов");
        put(Items.DIAMOND_LEGGINGS, "алмазные штаны", "алмазные штаны", "алмазных штанов");
        put(Items.IRON_BOOTS, "железные ботинки", "железные ботинки", "железных ботинок");
        put(Items.DIAMOND_BOOTS, "алмазные ботинки", "алмазные ботинки", "алмазных ботинок");
        // Drops of the world.
        put(Items.BONE, "кость", "кость", "кости");
        put(Items.STRING, "нить", "нить", "нити");
        put(Items.GUNPOWDER, "порох", "порох", "пороха");
        put(Items.SPIDER_EYE, "паучий глаз", "паучий глаз", "паучьего глаза");
        put(Items.FERMENTED_SPIDER_EYE, "маринованный паучий глаз", "маринованный паучий глаз", "маринованного паучьего глаза");
        put(Items.SLIME_BALL, "слизь", "слизь", "слизи");
        put(Items.ENDER_PEARL, "жемчуг края", "жемчуг края", "жемчуга края");
        put(Items.BLAZE_ROD, "огненный стержень", "огненный стержень", "огненного стержня");
        put(Items.PRISMARINE_SHARD, "осколок призмарина", "осколок призмарина", "осколка призмарина");
        put(Items.PRISMARINE_CRYSTALS, "кристалл призмарина", "кристалл призмарина", "кристалла призмарина");
        put(Items.NAUTILUS_SHELL, "раковина наутилуса", "раковину наутилуса", "раковины наутилуса");
        put(Items.PHANTOM_MEMBRANE, "перепонка фантома", "перепонку фантома", "перепонки фантома");
        put(Items.RABBIT_HIDE, "кроличья шкурка", "кроличью шкурку", "кроличьей шкурки");
        put(Items.RABBIT_FOOT, "кроличья лапка", "кроличью лапку", "кроличьей лапки");
        put(Items.NAME_TAG, "бирка", "бирку", "бирки");
        put(Items.SADDLE, "седло", "седло", "седла");
        put(Items.ELYTRA, "элитры", "элитры", "элитр");
        put(Items.TOTEM_OF_UNDYING, "тотем бессмертия", "тотем бессмертия", "тотема бессмертия");
        // Food.
        put(Items.APPLE, "яблоко", "яблоко", "яблока");
        put(Items.BREAD, "хлеб", "хлеб", "хлеба");
        put(Items.WHEAT, "пшеница", "пшеницу", "пшеницы");
        put(Items.WHEAT_SEEDS, "семена пшеницы", "семена пшеницы", "семян пшеницы");
        put(Items.CARROT, "морковь", "морковь", "моркови");
        put(Items.POTATO, "картошка", "картошку", "картошки");
        put(Items.BAKED_POTATO, "печёная картошка", "печёную картошку", "печёной картошки");
        put(Items.BEETROOT, "свёкла", "свёклу", "свёклы");
        put(Items.SUGAR, "сахар", "сахар", "сахара");
        put(Items.COAL, "уголь", "уголь", "угля");
        put(Items.CHARCOAL, "древесный уголь", "древесный уголь", "древесного угля");
        put(Items.DIAMOND, "алмаз", "алмаз", "алмаза");
        put(Items.EMERALD, "изумруд", "изумруд", "изумруда");
        put(Items.LAPIS_LAZULI, "лазурит", "лазурит", "лазурита");
        put(Items.REDSTONE, "редстоун", "редстоун", "редстоуна");
        put(Items.RAW_IRON, "сырое железо", "сырое железо", "сырого железа");
        put(Items.RAW_GOLD, "сырое золото", "сырое золото", "сырого золота");
        put(Items.RAW_COPPER, "сырая медь", "сырую медь", "сырой меди");
        // Blocks the ground is made of and the loose things a player drops.
        put(Items.TORCH, "факел", "факел", "факела");
        put(Items.CRAFTING_TABLE, "верстак", "верстак", "верстака");
        put(Items.FURNACE, "печь", "печь", "печи");
        put(Items.CHEST, "сундук", "сундук", "сундука");
        put(Items.BARREL, "бочка", "бочку", "бочки");
        put(Items.HOPPER, "воронка", "воронку", "воронки");
        put(Items.LADDER, "лестница", "лестницу", "лестницы");
        put(Items.GLASS, "стекло", "стекло", "стекла");
        put(Items.OBSIDIAN, "обсидиан", "обсидиан", "обсидиана");
        put(Items.NETHERRACK, "незерак", "незерак", "незерака");
        put(Items.GLOWSTONE, "светокамень", "светокамень", "светокамня");
        put(Items.END_STONE, "камень края", "камень края", "камня края");
        put(Items.SNOWBALL, "снежок", "снежок", "снежка");
        put(Items.CLAY_BALL, "комок глины", "комок глины", "комка глины");
        put(Items.BRICK, "кирпич", "кирпич", "кирпича");
        put(Items.PAPER, "бумага", "бумагу", "бумаги");
        put(Items.BOOK, "книга", "книгу", "книги");
        put(Items.ENCHANTED_BOOK, "зачарованная книга", "зачарованную книгу", "зачарованной книги");
        put(Items.STICK, "палка", "палку", "палки");
        put(Items.BOWL, "миска", "миску", "миски");
    }

    /** The Russian forms of this thing, or null for one the table does not know. */
    static Forms of(Item item) { return item == null ? null : WORDS.get(item); }

    /** «ведро воды», «алмазная кирка»: what the thing is called in the honest list, or null for an unknown one. */
    static String label(ItemStack stack) {
        Forms forms = of(stack.getItem());
        return forms == null ? null : forms.name();
    }
}

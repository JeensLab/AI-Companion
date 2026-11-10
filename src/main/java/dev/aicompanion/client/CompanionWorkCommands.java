package dev.aicompanion.client;

import java.util.Locale;
import java.util.regex.Pattern;

/** Common unambiguous work requests also function when the language model is unavailable. */
public final class CompanionWorkCommands {
    private CompanionWorkCommands() { }

    /** Buckets of water fetched at most without the model: a stack of empty buckets. */
    private static final int MAX_WATER = 16;
    private static final Pattern WATER = Pattern.compile("^(?:(принеси|дай|набери)( мне)?(?: (?:ведро|ведра|ведер))? воды(?: в (?:ведро|ведра))?( и (?:принеси|дай|отдай)(?: мне)?)?"
            + "|(сходи) за водой|наполни (?:ведро|ведра)(?: водой)?)$");

    /**
     * «Принеси воды», «набери мне ведро воды», «набери воды» (FWR29, decided 07.11): "companion work obtain_item N minecraft:water_bucket",
     * with " give" when the water is for the player («принеси», «дай», «сходи за водой», «мне», «…и отдай»). Null for anything else,
     * which is the model's: «во все вёдра» (only the server knows his buckets), «сколько сможешь», more than {@link #MAX_WATER}.
     */
    public static String water(String text, String name) {
        var said = CompanionNumbers.parse(plain(text, name));
        String rest = said.rest().replaceAll("\\s+", " ").trim();
        // «Наполни вёдра» with no number (ё is е here) means all his buckets, as «во все вёдра»; «0 вёдер» is no order of one.
        if (said.count() == 0 && rest.matches("(?U).*\\b(?:ведра|ведер)\\b.*")) return null;
        int count = said.count() == 0 ? 1 : said.count();
        var water = WATER.matcher(rest);
        if (!water.matches() || count < 1 || count > MAX_WATER) return null;
        boolean give = "принеси".equals(water.group(1)) || "дай".equals(water.group(1)) || water.group(2) != null || water.group(3) != null || water.group(4) != null;
        return "companion work obtain_item " + count + " minecraft:water_bucket" + (give ? " give" : "");
    }

    /** Obsidian got without the model at most (a stack) and when no number is said (a portal's frame without its corners). */
    private static final int MAX_OBSIDIAN = 64, OBSIDIAN = 10;
    private static final Pattern OBSIDIAN_ORDER = Pattern.compile("^(?:добудь|накопай|выкопай|сделай|набери)(?: (?:блоков|блока|блок|штук|штуки))? обсидиана?$");

    /**
     * «Добудь обсидиан», «накопай 5 обсидиана» (decided 08.11): "companion work obtain_item N minecraft:obsidian", ten when no number is
     * said. Null for anything else, which is the model's: «принеси», «и …», «0», more than {@link #MAX_OBSIDIAN}.
     */
    public static String obsidian(String text, String name) {
        String value = plain(text, name).replaceAll("\s+", " ");
        var said = CompanionNumbers.parse(value);
        // Nothing taken out of what was said: no number at all, so ten («блок» alone is one); a «0» taken out is no order of one.
        int count = said.rest().equals(value) ? ((" " + value + " ").contains(" блок ") ? 1 : OBSIDIAN) : said.count();
        if (!OBSIDIAN_ORDER.matcher(said.rest()).matches() || count < 1 || count > MAX_OBSIDIAN) return null;
        return "companion work obtain_item " + count + " minecraft:obsidian";
    }

    /** Lower case, ё as е, without his name in front and the full stop or «!» at the end. */
    private static final Pattern REPAIR = Pattern.compile("^(?:почини|почини-ка|подлатай)(?: свою| свои| твою| твои)? (.+)$");
    private static final Pattern DISENCHANT = Pattern.compile("^(?:сними|снимай|убери|счисти)(?: все)? чары (?:с|со) (всех )?(.+)$");
    private static final String[][] KINDS = {{"кирк", "pickaxe"}, {"топор", "axe"}, {"меч", "sword"}, {"лопат", "shovel"}, {"мотыг", "hoe"},
            {"шлем", "helmet"}, {"нагрудник", "chestplate"}, {"понож", "leggings"}, {"ботин", "boots"}};
    private static final String[][] THINGS = {{"лук", "minecraft:bow"}, {"арбалет", "minecraft:crossbow"}, {"удочк", "minecraft:fishing_rod"},
            {"ножниц", "minecraft:shears"}, {"огнив", "minecraft:flint_and_steel"}, {"трезуб", "minecraft:trident"}, {"щит", "minecraft:shield"},
            {"элитр", "minecraft:elytra"}, {"книг", "minecraft:enchanted_book"}};
    private static final String[][] MATERIALS = {{"деревян", "wooden"}, {"камен", "stone"}, {"желез", "iron"}, {"золот", "golden"},
            {"алмаз", "diamond"}, {"незерит", "netherite"}, {"кожан", "leather"}, {"кольчуж", "chainmail"}};

    /**
     * «Почини кирку» folds two alike, «сними чары с меча» takes it to a grindstone, «со всех луков» every one (CompanionGrinding).
     * Only a thing's name is read here: a material and a word of the kind, nothing more. Anything else («мой», «мне», a number,
     * a comma, «а не …», «потом») is left to the model: a wrong local guess grinds off what the player wanted kept (review 10.11).
     */
    public static String grind(String text, String name) {
        String value = plain(text, name).replaceAll("\\s+", " ");
        var repair = REPAIR.matcher(value);
        var disenchant = DISENCHANT.matcher(value);
        boolean fold = repair.matches();
        if (!fold && !disenchant.matches()) return null;
        String words = fold ? repair.group(1) : disenchant.group(2);
        if (!words.matches("(?U)\\p{L}+(?: \\p{L}+)?")) return null;
        String[] parts = words.split(" ");
        String kind = word(KINDS, parts[parts.length - 1]), thing = word(THINGS, parts[parts.length - 1]);
        String material = parts.length == 2 ? word(MATERIALS, parts[0]) : null;
        if (parts.length == 2 && (material == null || kind == null)) return null;
        String item = kind == null ? thing : material == null ? kind : "minecraft:" + material + "_" + kind;
        // «Деревянный шлем», «кольчужный меч»: no such thing; the model says so better than «У меня нет …».
        if (item == null || item.contains(":") && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.parse(item))) return null;
        return fold ? "companion work repair_item 1 " + item : "companion work disenchant_item " + (disenchant.group(1) != null ? 64 : 1) + " " + item;
    }

    private static final Pattern COMPOST_MEAL = Pattern.compile("^(?:сделай|наделай|приготовь|добудь) (?:(\\d{1,2}) )?костн(?:ую|ой) мук[уи] (?:в|на|из) компостер[ае]$");
    private static final Pattern COMPOST_SCRAP = Pattern.compile("^(?:перегной|переработай|закинь|положи|кинь|засыпь) (?:весь )?(?:хлам|мусор|растительный хлам|растительный мусор) в компостер[е]?$");
    private static final Pattern COMPOST_PUT = Pattern.compile("^(?:закинь|положи|кинь|брось|засыпь|загрузи) (?:(\\d{1,2}) |все |всю )?(\\p{L}+) в компостер$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String[][] COMPOST_GROUPS = {{"саженц", "saplings"}, {"семен", "seeds"}, {"семян", "seeds"}, {"листв", "leaves"}, {"листь", "leaves"}};

    /**
     * «Сделай костную муку в компостере», «закинь саженцы в компостер» (CompanionComposting): scrap until it ends or so much bone meal,
     * or a named group, all of it or so many. Only these plain phrases; a thing by its own name, «и», «потом» go to the model.
     */
    public static String compost(String text, String name) {
        String value = plain(text, name).replaceAll("\\s+", " ");
        var meal = COMPOST_MEAL.matcher(value);
        if (meal.matches()) return "companion work compost_items " + (meal.group(1) == null ? 64 : Math.clamp(Integer.parseInt(meal.group(1)), 1, 63)) + " scrap";
        if (COMPOST_SCRAP.matcher(value).matches()) return "companion work compost_items 64 scrap";
        var put = COMPOST_PUT.matcher(value);
        String group = put.matches() ? word(COMPOST_GROUPS, put.group(2)) : null;
        if (group == null) return null;
        return "companion work compost_items " + (put.group(1) == null ? 64 : Math.clamp(Integer.parseInt(put.group(1)), 1, 63)) + " " + group;
    }

    /** The id of the word's stem in the table, or null. */
    private static String word(String[][] table, String word) {
        for (String[] entry : table) if (word.startsWith(entry[0])) return entry[1];
        return null;
    }

    private static String plain(String text, String name) {
        String value = text.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
        if (name != null && !name.isBlank()) value = value.replaceFirst("^" + Pattern.quote(name.toLowerCase(Locale.ROOT).replace('ё', 'е')) + "[,! ]+", "");
        return value.replaceAll("[.!]+$", "").trim();
    }

    public static PlannedAction parse(String text, String name) {
        String value = plain(text, name);
        if (value.matches("^(?:построй|сделай|дострой)(?: мне| нам| себе)? (?:небольшой |маленький )?(?:деревянный )?(?:дом|домик)(?: из дерева| из бревен| из досок)?(?: рядом)?$")) return new PlannedAction("build_house", 0, 1);
        // Said of the place the player stands on. A question («это наш дом?») keeps its mark and stays talk for the model.
        if (value.matches("^(?:(?:это|вот|здесь|тут)(?: и есть| теперь| будет)? (?:наш|мой) дом|наш дом (?:здесь|тут)|(?:здесь|тут) (?:будет|теперь)(?: наш)? дом|запомни(?:,)? (?:где наш дом|(?:здесь|тут) наш дом|(?:это|этот дом|это место) как (?:наш )?дом))$"))
            return new PlannedAction("set_home", 0, 1);
        // Sorting the chests by group: «разложи сундуки», not «сложи вещи в сундук» (that one is store_items).
        if (value.matches("^(?:разложи|отсортируй|рассортируй)(?: мне| нам)?(?: (?:все )?(?:вещи|предметы))?(?: (?:по|в) сундук(?:ам|ах)| сундуки)$|^наведи порядок в сундук(?:ах|е)$"))
            return new PlannedAction("sort_chests", 0, 1);
        if (value.matches("^продолжи (?:строить (?:дом|домик)|стройку|строительство(?: дома)?)$")) return new PlannedAction("build_house", 0, 1);
        // «Построй портал (в Незер)» (decided 08.11): the frame without its corners near the player, lit; more said is the model's.
        // «Новый», «не хочу идти» (decided 09.11): built though a portal stands near; «здесь», «возле меня»: built here, not led to one.
        if (value.matches("^(?:(?:не хочу (?:идти|туда)|не пойду|не надо)[, ]+)?(?:построй|сделай|поставь)(?: мне| нам| себе)?(?: (?:новый|другой|еще один))? портал"
                + "(?: в (?:незер|ад|нижний мир))?(?: из обсидиана)?(?: (?:рядом|здесь|тут|возле меня|рядом со мной))?$"))
            return new PlannedAction("build_portal", 0, 1,
                    value.matches(".*(?:новый|другой|еще один|не хочу|не пойду|не надо).*") ? "new"
                            : value.matches(".*(?:здесь|тут|возле меня|рядом со мной).*") ? "here" : "");
        // Numbers as people say them («пять», «двадцать», «сколько сможешь»), and the spoken «дабудь(-ка)» of the voice transcript.
        var said = CompanionNumbers.parse(value);
        if (said.count() == CompanionNumbers.UNCLEAR) return null;
        String bare = said.rest().replace("-ка", "ка").replaceAll("\\s+", " ").trim();
        String dig = "(?:(?:до|да)будь(?:ка)?|накопай|наковыряй|собери|найди|поищи|ищи)";
        if (bare.matches("^" + dig + "(?: мне| нам| себе)?\\s+(?:камень|камня|камней|булыжник|булыжника)(?:\\s+(?:блоков|штук))?(?:\\s+(?:рядом|поблизости))?$")) {
            int count = amount(said.count(), 8, 64, 32);
            return count >= 1 && count <= 64 ? new PlannedAction("gather_stone", 0, count) : null;
        }
        // Loose blocks: the amount comes from said.count(); the anchors leave negations and compound phrases to the model.
        var loose = Pattern.compile("^(?:(?:до|да)будь(?:ка)?|накопай|набери|выкопай)(?: мне| нам| себе)?\\s+(?:(?:блоков|блока|блок|штук|штуки)\\s+)?"
                + "(красный песок|красного песка|красному песку|песок|песка|песку|гравий|гравия|гравию)(?:\\s+(?:блоков|штук))?(?:\\s+(?:рядом|поблизости))?$").matcher(bare);
        if (loose.matches()) {
            String word = loose.group(1);
            String item = word.startsWith("красн") ? "minecraft:red_sand" : word.startsWith("грави") ? "minecraft:gravel" : "minecraft:sand";
            int count = amount(said.count(), 8, 64, 32);
            return count >= 1 && count <= 64 ? new PlannedAction("gather_loose", 0, count, item) : null;
        }
        var flint = Pattern.compile("^(?:(?:до|да)будь(?:ка)?|накопай|набери|выкопай)(?: мне| нам| себе)?\\s+(?:(?:штук|штуки)\\s+)?"
                + "(кремня|кремень|кремний|кремню)(?:\\s+(?:штук))?(?:\\s+(?:рядом|поблизости))?$").matcher(bare);
        if (flint.matches()) {
            int count = amount(said.count(), 2, CompanionPlanCodec.FLINT_STEP, 4);
            return count >= 1 && count <= CompanionPlanCodec.FLINT_STEP ? new PlannedAction("gather_loose", 0, count, CompanionPlanCodec.FLINT) : null;
        }
        var ore = Pattern.compile("^(?:(?:до|да)будь(?:ка)?|накопай|выкопай|наколи)(?: мне| нам| себе)?\\s+(угля|уголь|железа|железо|железной|меди|медь|медной|золота|золото|золотой"
                + "|алмазов|алмазы|алмаз|изумрудов|изумруды|изумруд|лазурита|лазурит|редстоуна|редстоун)(?:\\s+(?:руды|руда|руду))?(?:\\s+(?:рядом|поблизости))?$").matcher(bare);
        if (ore.matches()) {
            String word = ore.group(1);
            String kind = word.startsWith("угл") || word.equals("уголь") ? "coal" : word.startsWith("желез") ? "iron" : word.startsWith("мед") ? "copper"
                    : word.startsWith("золот") ? "gold" : word.startsWith("алмаз") ? "diamond" : word.startsWith("изумруд") ? "emerald"
                    : word.startsWith("лазурит") ? "lapis" : "redstone";
            int max = dev.aicompanion.entity.CompanionWork.MAX_ORE;
            int count = amount(said.count(), CompanionPlanRequirements.DEFAULT_ORE, max, 8);
            // More than one order holds goes to the model, which splits it into several steps.
            return count >= 1 && count <= max ? new PlannedAction("mine_ore", 0, count, "minecraft:" + kind + "_ore") : null;
        }
        // «Сделай загон для коров», «загони овец», «разведи свиней»: the kind in any case, none for any kind.
        String animals = "(?:\\s+(коров|корову|коровы|овец|овцу|овцы|свиней|свинью|свиньи|куриц|курицу|курицы|кур))?";
        var pen = Pattern.compile("^(?:(?:сделай|построй)(?: мне| нам| себе)? загон(?: для)?|загони)" + animals + "$").matcher(bare);
        var breed = Pattern.compile("^разведи" + animals + "$").matcher(bare);
        if (pen.matches() || breed.matches() && said.count() == 0) {
            String word = pen.matches() ? pen.group(1) : breed.group(1);
            String kind = word == null ? "any" : "minecraft:" + (word.startsWith("коров") ? "cow" : word.startsWith("ов") ? "sheep" : word.startsWith("свин") ? "pig" : "chicken");
            return pen.matches() ? new PlannedAction("build_pen", 0, amount(said.count(), 4, 8, 8), kind) : new PlannedAction("breed_animals", 0, 1, kind);
        }
        var smelt = Pattern.compile("^(?:переплавь|расплавь|переплави|пережги)(?: мне| нам| себе)?(?:\\s+(?:всю|весь))?\\s+(железо|железа|золото|золота|медь|меди|песок|песка|булыжник|булыжника)(?:\\s+(?:в печи|в печке))?$").matcher(bare);
        if (smelt.matches()) {
            String raw = switch (smelt.group(1)) {
                case "железо", "железа" -> "minecraft:raw_iron";
                case "золото", "золота" -> "minecraft:raw_gold";
                case "медь", "меди" -> "minecraft:raw_copper";
                case "песок", "песка" -> "minecraft:sand";
                default -> "minecraft:cobblestone";
            };
            if (said.count() <= 0) return new PlannedAction("smelt_items", 0, PlannedAction.ALL_CARRIED, raw);
            return said.count() <= 64 ? new PlannedAction("smelt_items", 0, said.count(), raw) : null;
        }
        if (value.matches("^(?:сделай|скрафти|создай)(?: мне| нам| себе)? каменную кирку$")) return new PlannedAction("craft_item", 0, 1, "minecraft:stone_pickaxe");
        var iron = Pattern.compile("^(?:сделай|скрафти|создай)(?: себе)? (железную кирку|железный топор|железный меч|железную лопату|железную мотыгу)$").matcher(value);
        if (iron.matches()) return new PlannedAction("craft_item", 0, 1, switch (iron.group(1)) {
            case "железную кирку" -> "minecraft:iron_pickaxe";
            case "железный топор" -> "minecraft:iron_axe";
            case "железный меч" -> "minecraft:iron_sword";
            case "железную лопату" -> "minecraft:iron_shovel";
            default -> "minecraft:iron_hoe";
        });
        if (value.matches("^(?:убей|перебей|уничтожь|поубивай|убивай)(?: всех)?(?: ближайших| враждебных)? (?:мобов|монстров)(?: рядом| поблизости| вокруг)?$"))
            return new PlannedAction("hunt_mobs", 0, 1);
        if (value.matches("^(?:поставь|установи)(?: мне| нам| себе)? сундук(?: рядом)?$")) return new PlannedAction("place_block", 0, 1, "minecraft:chest");
        var chests = Pattern.compile("^(?:поставь|установи)(?: мне| нам| себе)? (\\d+) сундук(?:а|ов)?(?: здесь| рядом| здесь рядом)?$").matcher(value);
        if (chests.matches()) {
            try { int count = Integer.parseInt(chests.group(1));
                return count >= 1 && count <= 64 ? new PlannedAction("place_block", 0, count, "minecraft:chest") : null;
            } catch (NumberFormatException ignored) { return null; }
        }
        // Store and take orders, with the container they name («в бочку», «из печи»; «в сундук» as it always was).
        PlannedAction container = CompanionContainerCommands.parse(value);
        if (container != null) return container;
        // The field and the harvest need no model either: «собери урожай», «сделай огород», «посади морковь».
        if (bare.matches("^(?:собери|сними|убери)(?: мне| нам)?(?: весь| созревший)? урожай(?: рядом| вокруг| с огорода| с грядок| с фермы)?$"))
            return new PlannedAction("harvest_crops", 0, dev.aicompanion.entity.CompanionCrops.MAX_HARVEST, "any");
        if (bare.matches("^(?:сделай|разбей|вскопай|создай)(?: мне| нам| себе)? (?:небольшой |маленький )?(?:огород|огородик|грядки|грядку|ферму(?: пшеницы)?)(?: у воды| рядом)?$"))
            return new PlannedAction("farm_crops", 0, dev.aicompanion.entity.CompanionCrops.DEFAULT_TILES, "minecraft:wheat_seeds");
        var sow = Pattern.compile("^(?:посади|засади|посей|высади)(?: мне| нам)?\\s+(пшениц\\p{L}*|морков\\p{L}*|картош\\p{L}*|картофел\\p{L}*|свекл\\p{L}*)(?: у воды| рядом)?$").matcher(bare);
        if (sow.matches()) {
            var crop = dev.aicompanion.entity.CompanionCrops.Kind.of(switch (sow.group(1).substring(0, 4)) {
                case "морк" -> "carrot";
                case "карт" -> "potato";
                case "свек" -> "beetroot";
                default -> "wheat";
            });
            int count = amount(said.count(), dev.aicompanion.entity.CompanionCrops.DEFAULT_TILES, dev.aicompanion.entity.CompanionCrops.MAX_TILES, dev.aicompanion.entity.CompanionCrops.MAX_TILES);
            return count >= 1 && count <= dev.aicompanion.entity.CompanionCrops.MAX_TILES ? new PlannedAction("farm_crops", 0, count, crop.seedId()) : null;
        }
        return null;
    }

    private static int amount(int said, int none, int asMany, int more) {
        return said > 0 ? said : said == CompanionNumbers.AS_MANY ? asMany : said == CompanionNumbers.MORE ? more : none;
    }
}

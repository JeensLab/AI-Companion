package dev.aicompanion.client;

import java.util.Locale;
import java.util.regex.Pattern;

/** Common unambiguous work requests also function when the language model is unavailable. */
public final class CompanionWorkCommands {
    private CompanionWorkCommands() { }

    public static PlannedAction parse(String text, String name) {
        String value = text.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
        if (name != null && !name.isBlank()) value = value.replaceFirst("^" + Pattern.quote(name.toLowerCase(Locale.ROOT).replace('ё', 'е')) + "[,! ]+", "");
        value = value.replaceAll("[.!]+$", "").trim();
        if (value.matches("^(?:построй|сделай|дострой)(?: мне| нам| себе)? (?:небольшой |маленький )?(?:деревянный )?(?:дом|домик)(?: из дерева| из бревен| из досок)?(?: рядом)?$")) return new PlannedAction("build_house", 0, 1);
        // Sorting the chests by group: «разложи сундуки», not «сложи вещи в сундук» (that one is store_items).
        if (value.matches("^(?:разложи|отсортируй|рассортируй)(?: мне| нам)?(?: (?:все )?(?:вещи|предметы))?(?: (?:по|в) сундук(?:ам|ах)| сундуки)$|^наведи порядок в сундук(?:ах|е)$"))
            return new PlannedAction("sort_chests", 0, 1);
        if (value.matches("^продолжи (?:строить (?:дом|домик)|стройку|строительство(?: дома)?)$")) return new PlannedAction("build_house", 0, 1);
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
        // Diamonds lie deep: unless the player says «рядом», the order is the long find_diamonds goal, not a search of the nearby ore.
        if (bare.matches("^(?:(?:(?:до|да)будь(?:ка)?|накопай|наковыряй|выкопай|наколи|найди|поищи|ищи)(?: мне| нам| себе)?\\s+алмаз(?:а|ов|ы)?(?:\\s+(?:руды|руда|руду))?"
                + "|сходи(?: в шахту)? за алмазами|(?:копай|выкопай|накопай|прокопай) шахту до алмазов)$")) {
            int count = amount(said.count(), dev.aicompanion.entity.CompanionPlaybooks.DIAMONDS_DEFAULT, dev.aicompanion.entity.CompanionPlaybooks.DIAMONDS_MAX, 8);
            return count >= 1 && count <= dev.aicompanion.entity.CompanionPlaybooks.DIAMONDS_MAX ? new PlannedAction("pursue_goal", 0, count, "find_diamonds") : null;
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
        var take = Pattern.compile("^(?:забери|возьми|достань)(?:\\s+(\\d+))?\\s+(все|все вещи|вещи|ресурсы|дерево|бревна|бревен|камень|камня|булыжник|булыжника|землю|земли)\\s+из сундука$").matcher(value);
        if (take.matches()) {
            String filter = switch (take.group(2)) {
                case "все", "все вещи", "вещи" -> "all";
                case "дерево", "бревна", "бревен" -> "logs";
                case "камень", "камня", "булыжник", "булыжника" -> "stone";
                case "землю", "земли" -> "minecraft:dirt";
                default -> "resources";
            };
            try { int count = take.group(1) == null ? 64 : Integer.parseInt(take.group(1));
                return count > 0 && count <= 64 ? new PlannedAction("take_items", 0, count, filter) : null;
            } catch (NumberFormatException ignored) { return null; }
        }
        var store = Pattern.compile("^(?:сложи|положи|выгрузи|убери) (ресурсы|вещи|все вещи|все|дерево|бревна|камень|булыжник) в сундук$").matcher(value);
        if (store.matches()) {
            String filter = switch (store.group(1)) {
                case "все", "все вещи" -> "all";
                case "дерево", "бревна" -> "logs";
                case "камень", "булыжник" -> "stone";
                default -> "resources";
            };
            return new PlannedAction("store_items", 0, 1, filter);
        }
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

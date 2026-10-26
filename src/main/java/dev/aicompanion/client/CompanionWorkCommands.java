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
        if (value.matches("^(?:построй|сделай)(?: мне| нам| себе)? (?:небольшой |маленький )?(?:деревянный )?(?:дом|домик)(?: из дерева| из бревен| из досок)?(?: по схеме)?(?: рядом)?$")) return new PlannedAction("build_house", 0, 1);
        if (value.matches("^дострой(?: мне| нам)? (?:дом|домик)$") || value.matches("^продолжи (?:строить (?:дом|домик)|стройку|строительство(?: дома)?)$"))
            return new PlannedAction("build_house", 0, 1, CompanionServerPlanPort.HOUSE_RESUME);
        // Numbers as people say them («пять», «двадцать», «сколько сможешь»), and the spoken «дабудь(-ка)» of the voice transcript.
        var said = CompanionNumbers.parse(value);
        if (said.count() == CompanionNumbers.UNCLEAR) return null;
        String bare = said.rest().replace("-ка", "ка").replaceAll("\\s+", " ").trim();
        String dig = "(?:(?:до|да)будь(?:ка)?|накопай|наковыряй|собери|найди|поищи|ищи)";
        if (bare.matches("^" + dig + "(?: мне| нам| себе)?\\s+(?:камень|камня|камней|булыжник|булыжника)(?:\\s+(?:блоков|штук))?(?:\\s+(?:рядом|поблизости))?$")) {
            int count = amount(said.count(), 8, 64, 32);
            return count >= 1 && count <= 64 ? new PlannedAction("gather_stone", 0, count) : null;
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
        return null;
    }

    private static int amount(int said, int none, int asMany, int more) {
        return said > 0 ? said : said == CompanionNumbers.AS_MANY ? asMany : said == CompanionNumbers.MORE ? more : none;
    }
}

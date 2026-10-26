package dev.aicompanion.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Preserve explicit placement constraints even when a provider omits fields or whole conjuncts.
 * Only a positive placement imperative enables supplementation; questions and negations never do. */
public final class CompanionPlanRequirements {
    private CompanionPlanRequirements() { }

    public static CompanionReply apply(String request, CompanionReply decoded) {
        String text = request.toLowerCase(Locale.ROOT).replace('ё', 'е');
        decoded = recipientsAndScope(text, decoded);
        decoded = smeltEverything(text, decoded);
        decoded = smeltAfterOre(text, decoded);
        decoded = cookAfterHunt(text, decoded);
        decoded = spokenQuantity(text, decoded);
        if (text.matches("(?sU).*\\b(не|почему|зачем|где|как|нельзя|или|либо)\\b.*")
                || !text.matches("(?sU).*\\b(поставь|размести|установи)\\b.*")
                && !(text.matches("(?sU).*\\b(можешь|пожалуйста|прошу|нужно|надо|давай)\\b.*")
                && text.matches("(?sU).*\\b(поставить|разместить|установить)\\b.*"))) return decoded;
        boolean inside = text.matches("(?sU).*(внутри.{0,20}дом|в\\s+(нашем\\s+|этом\\s+)?доме).*" );
        if (inside && text.contains("снаружи")) return decoded;
        String location = inside ? "inside_house" : "near_player";
        List<PlannedAction> result = new ArrayList<>();
        for (PlannedAction step : decoded.actions()) {
            if (inside && List.of("place_block", "store_items", "take_items").contains(step.type()))
                step = new PlannedAction(step.type(), step.seconds(), step.count(), step.item(), step.slot(), location);
            result.add(step);
        }
        String objects = placementObjects(text);
        for (String[] block : new String[][]{{"сундук", "minecraft:chest"}, {"печ", "minecraft:furnace"}, {"верстак", "minecraft:crafting_table"}}) {
            if (objects.contains(block[0]) && result.stream().noneMatch(step -> step.type().equals("place_block") && step.item().equals(block[1]))) {
                int beforeStorage = 0;
                while (beforeStorage < result.size() && !result.get(beforeStorage).type().equals("store_items")) beforeStorage++;
                result.add(beforeStorage, new PlannedAction("place_block", 0, 1, block[1], "auto", location));
            }
        }
        if (text.matches("(?sU).*\\b(сложи|сложить|убери|убрать)\\b.*(вещ|предмет|ресурс).*")) {
            String filter = text.matches("(?sU).*\\b(все|свои)\\b.*") ? "all" : "resources";
            result.removeIf(step -> step.type().equals("store_items"));
            result.add(new PlannedAction("store_items", 0, 1, filter, "auto", location));
        }
        if (result.size() > CompanionPlanCodec.MAX_STEPS) throw new IllegalStateException("Просьба содержит слишком много шагов. Раздели ее на части.");
        return result.isEmpty() ? decoded : new CompanionReply(CompanionDialogue.acknowledge(result), List.copyOf(result));
    }
    private static final String TOOL = ".*_(pickaxe|axe|shovel|sword|hoe|helmet|chestplate|leggings|boots)";

    /**
     * Game log 22.10: «сделай себе и мне каменное оружие и инструменты» became one set for Oleg plus an unasked
     * 64-stone dig and a chest. A plain "make" request keeps only what was asked; "for both of us" makes two and
     * hands one over; "for me" hands the made tool over. Crafting already gathers its own stone and wood.
     */
    static CompanionReply recipientsAndScope(String text, CompanionReply decoded) {
        // A reproach such as «почему ты не сделал себе и мне инструменты?» is still the same request (game log 22.10);
        // only an explicit "don't" or an either/or choice leaves the model's plan alone.
        if (decoded.actions().isEmpty() || text.matches("(?sU).*\\b(не делай|не надо|не нужно|не крафти|нельзя|или|либо)\\b.*")) return decoded;
        boolean make = text.matches("(?sU).*(сдела|скрафт|созда|изготов|смастер).*")
                && decoded.actions().stream().anyMatch(step -> step.type().equals("craft_item"));
        if (!make) return decoded;
        boolean gatherAsked = text.matches("(?sU).*(добуд|добыт|добыв|накопа|наруб|собер|собрат|выкопа|копа|натаска|принес).*")
                || text.matches("(?sU).*\\d+\\s*(камн|бревен|бревн|блок|дерев|земл).*");
        boolean storeAsked = text.matches("(?sU).*(сундук|сложи|сложить|убери|положи).*");
        boolean both = text.matches("(?sU).*(себе и мне|мне и себе|нам обоим|нам двоим|для нас обоих|и мне и себе|и себе и мне).*");
        boolean forMe = !both && !text.contains("себе") && text.matches("(?sU).*\\b(мне|для меня)\\b.*");
        List<PlannedAction> result = new ArrayList<>();
        List<String> handed = new ArrayList<>();
        for (PlannedAction step : decoded.actions()) {
            if (!gatherAsked && step.type().startsWith("gather_")) continue;
            if (!storeAsked && (step.type().equals("store_items") || step.type().equals("place_block") && step.item().equals("minecraft:chest"))) continue;
            if (step.type().equals("craft_item") && step.item().matches(TOOL) && step.count() == 1 && (both || forMe)) {
                if (both) step = new PlannedAction(step.type(), step.seconds(), 2, step.item(), step.slot(), step.location());
                if (!handed.contains(step.item())) handed.add(step.item());
            }
            result.add(step);
        }
        for (String item : handed) if (result.stream().noneMatch(step -> step.type().equals("give_items") && step.item().equals(item)))
            result.add(new PlannedAction("give_items", 0, 1, item, "auto", "near_player"));
        if (result.equals(decoded.actions())) return decoded;
        if (result.size() > CompanionPlanCodec.MAX_STEPS) throw new IllegalStateException("Просьба содержит слишком много шагов. Раздели ее на части.");
        return result.isEmpty() ? decoded : new CompanionReply(CompanionDialogue.acknowledge(result), List.copyOf(result));
    }

    private static final String NUMBER = "(?sU).*(\\d|\\b(одну|один|одного|одна|пару|пара|два|две|три|четыре|пять|шесть|семь|восемь|девять|десять"
            + "|несколько|половину|кусок|штук[уи]?)\\b).*";

    /**
     * Game log 22.10: «переплавь железо» with 64 raw iron in the bag smelt one piece, the model put count=1.
     * Like a friend, a request without a number («переплавь железо», «всё железо») smelts all of that raw material he carries.
     */
    static CompanionReply smeltEverything(String text, CompanionReply decoded) {
        // Any number in the request («5 железа», «и сложи 10 камня») leaves the model's counts alone.
        if (decoded.actions().stream().noneMatch(step -> step.type().equals("smelt_items")) || text.matches(NUMBER)) return decoded;
        List<PlannedAction> result = decoded.actions().stream().map(step -> step.type().equals("smelt_items") && step.count() != PlannedAction.ALL_CARRIED
                ? new PlannedAction(step.type(), step.seconds(), PlannedAction.ALL_CARRIED, step.item(), step.slot(), step.location()) : step).toList();
        return result.equals(decoded.actions()) ? decoded : new CompanionReply(decoded.text(), result);
    }

    /**
     * Game log 22.10: «Добудь нам больше железа и пойди его переплавь в печке» became one mine_ore step, the smelting was lost.
     * Asked to smelt and given ore to dig but no furnace step: smelt all of that raw ore after digging it.
     */
    static CompanionReply smeltAfterOre(String text, CompanionReply decoded) {
        if (!text.matches("(?sU).*(переплав|расплав|переплави|в печ|пережги).*") || text.matches("(?sU).*\\b(не\\s+(переплавляй|плавь|надо)|почему|зачем)\\b.*")
                || decoded.actions().stream().anyMatch(step -> step.type().equals("smelt_items"))) return decoded;
        int last = -1;
        for (int i = 0; i < decoded.actions().size(); i++) if (decoded.actions().get(i).type().equals("mine_ore")) last = i;
        if (last < 0) return decoded;
        var kind = dev.aicompanion.entity.CompanionOre.Kind.of(decoded.actions().get(last).item());
        String raw = kind == null ? null : switch (kind) { case IRON -> "minecraft:raw_iron"; case GOLD -> "minecraft:raw_gold"; case COPPER -> "minecraft:raw_copper"; default -> null; };
        if (raw == null || decoded.actions().size() >= CompanionPlanCodec.MAX_STEPS) return decoded;
        List<PlannedAction> result = new ArrayList<>(decoded.actions());
        result.add(last + 1, new PlannedAction("smelt_items", 0, PlannedAction.ALL_CARRIED, raw));
        return new CompanionReply(decoded.text(), List.copyOf(result));
    }

    /**
     * «Добудь мяса и пожарь» (Толик 22.10): the meat of the hunt goes into the furnace even when the model forgot that step.
     * A named kind cooks its meat; «any» cooks the raw meat he carries most of.
     */
    static CompanionReply cookAfterHunt(String text, CompanionReply decoded) {
        if (!text.matches("(?sU).*(пожар|зажар|жарь|жарен|поджар|приготов|запеки|запечь|в печ).*") || text.matches("(?sU).*\\b(не\\s+(жарь|надо)|почему|зачем)\\b.*")
                || decoded.actions().stream().anyMatch(step -> step.type().equals("smelt_items"))) return decoded;
        int last = -1;
        for (int i = 0; i < decoded.actions().size(); i++) if (decoded.actions().get(i).type().equals("hunt_animals")) last = i;
        if (last < 0 || decoded.actions().size() >= CompanionPlanCodec.MAX_STEPS) return decoded;
        var kind = dev.aicompanion.entity.CompanionGame.Kind.of(decoded.actions().get(last).item());
        String meat = kind == null ? dev.aicompanion.entity.CompanionGame.ANY_MEAT : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(kind.meat).toString();
        List<PlannedAction> result = new ArrayList<>(decoded.actions());
        result.add(last + 1, new PlannedAction("smelt_items", 0, PlannedAction.ALL_CARRIED, meat));
        return new CompanionReply(decoded.text(), List.copyOf(result));
    }

    /** Steps that carry no count of their own, so a number in the request can only mean the gathering. */
    private static final java.util.Set<String> COUNTLESS = java.util.Set.of("follow", "come", "wait", "store_items", "stop", "protect", "stop_protect");
    static final int DEFAULT_ORE = 4;
    static final int DEFAULT_GAME = 3;
    /** Unnumbered food/hunting requests make a larger, bounded food run. */
    static final int FOOD_RUN = 8;

    /**
     * Game log 22.10: «добудь пять руды», «добудь 20», «сколько сможешь», «добывай бесконечно» — every time the model sent a
     * single block, and Oleg said «готово». The amount the player said is the amount: digits or words, «сколько сможешь» is
     * the most one order allows, no number for ore is four. Only a plan with one kind of gathering is touched.
     */
    static CompanionReply spokenQuantity(String text, CompanionReply decoded) {
        var gathering = decoded.actions().stream().filter(step -> List.of("mine_ore", "gather_stone", "gather_wood", "hunt_animals").contains(step.type())).toList();
        if (gathering.isEmpty() || gathering.stream().map(step -> step.type() + "|" + step.item()).distinct().count() != 1) return decoded;
        boolean otherCounts = decoded.actions().stream().anyMatch(step -> !gathering.contains(step) && !COUNTLESS.contains(step.type())
                && !(step.type().equals("smelt_items") && step.count() == PlannedAction.ALL_CARRIED));
        if (otherCounts || text.matches("(?sU).*\\b(не|почему|зачем|сколько\\s+у\\s+тебя)\\b.*")) return decoded;
        PlannedAction first = gathering.getFirst();
        String type = first.type();
        int asked = gathering.stream().mapToInt(PlannedAction::count).sum();
        var said = CompanionNumbers.parse(text);
        boolean game = type.equals("hunt_animals");
        boolean foodRun = game && said.count() == 0
                && text.matches("(?sU).*(охот|мяс|ед[уы]|пожар|приготов).*");
        int most = type.equals("mine_ore") ? 3 * dev.aicompanion.entity.CompanionWork.MAX_ORE : game ? dev.aicompanion.entity.CompanionWork.MAX_GAME : 64;
        int total = said.count() > 0 ? said.count()
                : said.count() == CompanionNumbers.AS_MANY ? (type.equals("mine_ore") || game ? (game ? dev.aicompanion.entity.CompanionWork.MAX_GAME : dev.aicompanion.entity.CompanionWork.MAX_ORE) : type.equals("gather_stone") ? 64 : 32)
                : said.count() == CompanionNumbers.MORE ? (type.equals("mine_ore") ? 8 : game ? 6 : type.equals("gather_stone") ? 32 : 16)
                : said.count() == 0 && type.equals("mine_ore") ? DEFAULT_ORE
                : said.count() == 0 && game && text.matches("(?sU).*(охот|мяс|ед[уы]|пожар|приготов).*") ? FOOD_RUN : asked;
        total = Math.max(1, Math.min(most, total));
        if (total == asked && !foodRun && !(game && said.count() > 0 && dev.aicompanion.entity.CompanionGame.foodRun(first.item()))) return decoded;
        int chunk = type.equals("mine_ore") ? dev.aicompanion.entity.CompanionWork.MAX_ORE : game ? dev.aicompanion.entity.CompanionWork.MAX_GAME
                : type.equals("gather_wood") ? CompanionPlanCodec.WOOD_STEP : 64;
        List<PlannedAction> result = new ArrayList<>();
        boolean placed = false;
        for (PlannedAction step : decoded.actions()) {
            if (!gathering.contains(step)) { result.add(step); continue; }
            if (placed) continue;
            placed = true;
            for (int left = total; left > 0; left -= chunk)
                result.add(new PlannedAction(type, step.seconds(), Math.min(chunk, left), huntItem(step.item(), game, foodRun, said.count()), step.slot(), step.location()));
        }
        if (result.size() > CompanionPlanCodec.MAX_STEPS) return decoded;
        return new CompanionReply(decoded.text(), List.copyOf(result));
    }

    private static String huntItem(String item, boolean hunt, boolean foodRun, int count) {
        if (!hunt) return item;
        var kind = dev.aicompanion.entity.CompanionGame.Kind.of(item);
        if (foodRun) return kind == null ? "food" : "food:" + kind.key;
        if (count > 0 && dev.aicompanion.entity.CompanionGame.foodRun(item)) return kind == null ? "any" : "minecraft:" + kind.key;
        return item;
    }

    private static String placementObjects(String text) {
        var verbs = java.util.regex.Pattern.compile("(?U)\\b(поставь|поставить|размести|разместить|установи|установить)\\b").matcher(text);
        StringBuilder objects = new StringBuilder();
        while (verbs.find()) {
            String tail = text.substring(verbs.end()).stripLeading()
                    .replaceFirst("(?U)^(?:внутри|в)\\s+(?:нашем\\s+|нашего\\s+|этом\\s+)?дом[ае]\\s+", "");
            // References such as 'near the workbench' are destinations, not another
            // requested block. Stop the object list at the next action or location.
            objects.append(tail.split("(?U)\\b(рядом|возле|около|на|под|за|перед|к|внутри|в|с|сложи|сложить|забери|сделай|скажи|потом)\\b", 2)[0]).append(' ');
        }
        return objects.toString();
    }
}

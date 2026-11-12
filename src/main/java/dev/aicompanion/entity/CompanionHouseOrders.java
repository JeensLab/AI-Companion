package dev.aicompanion.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How an order to build begins: the plank house, a building of the catalogue by its name or kind, or «дострой», which goes on with what is
 * unfinished and never starts anything of its own. Also what a gathering stage that fell short is (a building goes on with what there is).
 */
final class CompanionHouseOrders {
    /** What the work is to build (the template building or the plank house), what the player is told first, or why nothing begins. */
    record Order(boolean template, String note, CompanionActionResult refusal) { }

    private CompanionHouseOrders() { }

    static Order start(CompanionEntity owner, CompanionHouse plank, CompanionHouse built, String item) {
        // The window's order carries the place the player set (CompanionBuildPlacement.Site).
        var site = CompanionBuildPlacement.Site.parse(item);
        item = CompanionBuildPlacement.Site.nameOf(item);
        String resume = CompanionVillageBuildings.resume(item);
        var named = resume != null ? null : CompanionVillageBuildings.resolve(item, owner);
        if (resume == null && named == null && item != null && !item.isBlank()) return refused(CompanionVillageBuildings.refusal(item));
        if (named != null) {
            String note = built.begin(named);
            CompanionBuildPlacement.ask(owner, built, note, site);
            // With a ghost to place it the player is told in the placement's own words; the unfinished building is not dropped before the place is set.
            if (built.awaiting()) note = "";
            else if (!note.isEmpty()) owner.workStatus(note.trim());
            return new Order(true, note, null);
        }
        if (resume == null) { plank.begin(); return new Order(false, "", null); }
        return resume(owner, plank, built, resume.substring("resume".length()).trim());
    }

    /**
     * «Дострой» with what was said after it: nothing (the house worked on last, when two are unfinished), «дом» (the plank house, else a house of
     * the catalogue) or the name or kind of a building. Nothing begins that is not unfinished here; the reason is said.
     */
    private static Order resume(CompanionEntity owner, CompanionHouse plank, CompanionHouse built, String said) {
        // A building ordered whose place was never chosen: the order asks for the place again.
        if (built.unplaced() && (said.isEmpty() || built.is(said))) {
            built.begin();
            CompanionBuildPlacement.ask(owner, built, "", null);
            return new Order(true, "", null);
        }
        boolean plankLeft = plank.unfinished(), builtLeft = built.unfinished();
        CompanionHouse pick = null;
        if (said.isEmpty()) pick = plankLeft && builtLeft ? (built.touched() > plank.touched() ? built : plank) : plankLeft ? plank : builtLeft ? built : null;
        else if (said.equals("дом")) pick = plankLeft ? plank : builtLeft && built.is(said) ? built : null;
        else if (builtLeft && built.is(said)) pick = built;
        if (pick == null) return refused(nothing(plank, built, said));
        pick.begin();
        boolean both = plankLeft && builtLeft && said.isEmpty();
        String note = "Продолжаю " + pick.what() + (both ? " — его я строил последним; недостроено и " + (pick == built ? plank : built).what() + ", назови его, если нужно другое" : "") + ". ";
        return new Order(pick == built, note, null);
    }

    private static String nothing(CompanionHouse plank, CompanionHouse built, String said) {
        String text = "Нечего достраивать: " + (said.isEmpty() ? "недостроенного здесь нет" : said.equals("дом") ? "недостроенного дома здесь нет" : "«" + said + "» здесь не начата или уже готова") + ".";
        List<String> left = new ArrayList<>(), away = new ArrayList<>();
        if (plank.unfinished()) left.add(plank.what());
        if (built.unfinished()) left.add(built.what());
        if (plank.elsewhereWhat() != null) away.add(plank.elsewhereWhat());
        if (built.elsewhereWhat() != null) away.add(built.elsewhereWhat());
        if (!left.isEmpty()) text += " Недостроено: " + String.join(", ", left) + ".";
        if (!away.isEmpty()) text += " В другом измерении осталось недостроенное: " + String.join(", ", away) + "; достроить это можно только там.";
        return text;
    }

    private static Order refused(String text) { return new Order(false, "", CompanionActionResult.failed(text)); }

    /**
     * A stage of getting a building's materials that found less than it went for (no tree, no stone, a shorter dig): the building goes on with
     * what there is, and the rest is told at the end. A full bag, whichever stage says it, and any other failure end the order as they are.
     */
    static boolean shortRound(CompanionWork.Phase phase, CompanionActionResult result) {
        boolean gathering = phase == CompanionWork.Phase.WOOD || phase == CompanionWork.Phase.PICKAXE || phase == CompanionWork.Phase.MINE
                || phase == CompanionWork.Phase.SHOVEL || phase == CompanionWork.Phase.DIGGING;
        String said = result.message().toLowerCase(Locale.ROOT);
        return gathering && result.status() == CompanionActionResult.Status.FAILED && !said.contains("нет места") && !said.contains("сумка полна");
    }
}

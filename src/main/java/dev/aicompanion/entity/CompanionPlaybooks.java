package dev.aicompanion.entity;

import dev.aicompanion.entity.CompanionMilestones.Milestone;
import dev.aicompanion.entity.CompanionMilestones.Piece;
import dev.aicompanion.entity.CompanionMilestones.Tier;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The closed catalog of long goals: the order of milestones a player learns from guides. Milestones without Oleg's skills
 * are listed too, so he says honestly how far he gets now; each new skill turns one of them on without touching the engine.
 */
public final class CompanionPlaybooks {
    public static final List<String> IDS = List.of("gear_up", "prepare_nether", "defeat_ender_dragon");

    public record Playbook(String id, int tier, String title, List<Milestone> milestones) {
        /** What he says when he takes the goal: what he will do and, past the first milestone he cannot do, what he cannot. */
        public String summary(CompanionFacts facts, boolean fallback) {
            List<String> todo = new ArrayList<>(), cannot = new ArrayList<>();
            for (Milestone milestone : milestones) {
                if (milestone.satisfied(facts, fallback, milestones)) continue;
                if (!cannot.isEmpty() || !milestone.supported()) cannot.add(milestone.label(fallback));
                else todo.add(milestone.label(fallback));
            }
            if (todo.isEmpty() && cannot.isEmpty()) return "Для цели «" + title + "» всё уже есть.";
            String can = todo.isEmpty() ? "Для цели «" + title + "» всё, что умею, уже сделано."
                    : "Дело долгое: " + title + ". Сделаю: " + String.join(", ", todo) + ".";
            return can + (cannot.isEmpty() ? "" : " Дальше пока не умею: " + String.join(", ", cannot) + ".") + (todo.isEmpty() ? "" : " Начинаю.");
        }

        public Milestone milestone(String key) {
            return milestones.stream().filter(milestone -> milestone.key().equals(key)).findFirst().orElse(null);
        }
    }

    private CompanionPlaybooks() { }

    /** The goal by its catalog id; {@code tier} 2 asks gear_up for diamond, anything else for iron. Null for an unknown id. */
    public static Playbook of(String id, int tier) {
        return switch (id == null ? "" : id) {
            case "gear_up" -> tier >= 2 ? new Playbook("gear_up", 2, "алмазное снаряжение", gear(Tier.DIAMOND))
                    : new Playbook("gear_up", 1, "железное снаряжение", gear(Tier.IRON));
            case "prepare_nether" -> new Playbook("prepare_nether", 0, "подготовка к Незеру", nether());
            case "defeat_ender_dragon" -> new Playbook("defeat_ender_dragon", 0, "победа над драконом Края", dragon());
            default -> null;
        };
    }

    /** Diamonds want an iron pickaxe first; then the material for every piece at once, so the ore is mined and smelted in one go. */
    private static List<Milestone> gear(Tier tier) {
        List<Milestone> list = new ArrayList<>();
        if (tier == Tier.DIAMOND) list.add(CompanionMilestones.piece(Piece.PICKAXE, Tier.IRON));
        list.add(CompanionMilestones.materials(tier));
        for (Piece piece : Piece.values()) list.add(CompanionMilestones.piece(piece, tier));
        return list;
    }

    private static List<Milestone> nether() {
        List<Milestone> list = gear(Tier.DIAMOND);
        list.add(CompanionMilestones.food(16));
        list.add(CompanionMilestones.stock(Items.COBBLESTONE, 64, "64 булыжника для мостов и столбов", true));
        list.add(CompanionMilestones.stock(Items.FLINT_AND_STEEL, 1, "огниво", false));
        return list;
    }

    private static List<Milestone> dragon() {
        List<Milestone> list = nether();
        list.add(CompanionMilestones.skill("obsidian", "10 обсидиана"));
        list.add(CompanionMilestones.skill("portal", "портал в Незер"));
        list.add(CompanionMilestones.skill("nether", "поход в Незер"));
        list.add(CompanionMilestones.skill("fortress", "найти крепость Незера"));
        list.add(CompanionMilestones.skill("blaze_rods", "7 огненных стержней"));
        list.add(CompanionMilestones.skill("ender_pearls", "12 жемчужин Края"));
        list.add(CompanionMilestones.skill("bow", "лук и 64 стрелы"));
        list.add(CompanionMilestones.skill("ender_eyes", "12 очей Края"));
        list.add(CompanionMilestones.skill("stronghold", "найти крепость Края и открыть портал"));
        list.add(CompanionMilestones.skill("end", "поход в Энд"));
        list.add(CompanionMilestones.dragon());
        return list;
    }
}

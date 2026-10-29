package dev.aicompanion.entity;

import dev.aicompanion.entity.CompanionAgenda.State;
import dev.aicompanion.entity.CompanionAgendaData.Goal;
import dev.aicompanion.entity.CompanionMilestones.Milestone;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the player (task screen) and the model (the context line) are told of the long goals: one reading of the milestones
 * against the facts, immutable, so neither side holds the live goal. Only a goal that is not over is shown.
 */
public final class CompanionAgendaView {
    private static final int OBSTACLE_LIMIT = 200;
    public enum Mark { DONE, CURRENT, LATER, CANNOT, OBSTACLE }

    public record Line(String label, Mark mark) { }

    /**
     * @param goal          the goal the lines tell about: the working one, or the deferred one when none works
     * @param deferredOnly  no goal works; {@code goal} waits in the deferred slot
     * @param now           what he does, or why he does not
     * @param obstacle      why the goal does not advance; empty when nothing stands in its way
     * @param deferredTitle the other goal in the deferred slot; empty when there is none
     * @param pickaxesFirst an outstanding pickaxe order takes «продолжай» first, so the long goal cannot be resumed yet
     */
    public record View(UUID goal, String title, State state, boolean deferredOnly, String now, String obstacle,
                       List<Line> lines, String deferredTitle, boolean pickaxesFirst) {
        /** «Продолжай» changes something: a paused, held or blocked goal goes on, a deferred one comes back. */
        public boolean canResume() {
            return !pickaxesFirst && (deferredOnly || state == State.HELD || state == State.INTERRUPTED || state == State.BLOCKED);
        }

        /** The deferred slot holds one goal; a working goal can step aside only into an empty one. */
        public boolean canDefer() { return !deferredOnly && deferredTitle.isEmpty(); }

        /** The line of the model's context: «Долгая цель: …; сейчас: …; сделано: …; дальше: …; не умею: …; помеха: …; отложена: …». */
        public String contextLine() {
            if (deferredOnly) return "Долгая цель: нет; отложена: «" + title + "»";
            StringBuilder text = new StringBuilder("Долгая цель: «").append(title).append("»; сейчас: ").append(now);
            section(text, "сделано", Mark.DONE);
            section(text, "дальше", Mark.LATER);
            section(text, "не умею", Mark.CANNOT);
            if (!obstacle.isEmpty()) text.append("; помеха: ").append(obstacle);
            if (!deferredTitle.isEmpty()) text.append("; отложена: «").append(deferredTitle).append('»');
            return text.toString();
        }

        private void section(StringBuilder text, String name, Mark mark) {
            List<String> labels = lines.stream().filter(line -> line.mark() == mark).map(Line::label).toList();
            if (!labels.isEmpty()) text.append("; ").append(name).append(": ").append(String.join(", ", labels));
        }
    }

    private CompanionAgendaView() { }

    /** The goal a button of the screen or a phrase acts on: the working one, else the deferred one. Null when there is none. */
    static UUID target(CompanionEntity c) {
        Goal goal = shown(c);
        return goal == null ? null : goal.id;
    }

    private static Goal shown(CompanionEntity c) {
        if (c.getServer() == null || c.getOwnerId() == null) return null;
        CompanionAgendaData data = CompanionAgendaData.get(c.getServer());
        Goal active = data.active(c.getOwnerId());
        return active != null && active.state.outstanding() ? active : data.deferred(c.getOwnerId());
    }

    /** Null when the player has no long goal that is not over. Server thread only. */
    public static View of(CompanionEntity c) {
        Goal goal = shown(c);
        if (goal == null) return null;
        CompanionAgendaData data = CompanionAgendaData.get(c.getServer());
        boolean deferredOnly = data.active(c.getOwnerId()) != goal;
        Goal other = deferredOnly ? null : data.deferred(c.getOwnerId());
        CompanionFacts facts = CompanionFacts.of(c);
        List<Milestone> all = goal.book().milestones();
        boolean hindered = goal.state == State.BLOCKED || goal.state == State.ACTIVE && !goal.reason.isBlank();
        List<Line> lines = new ArrayList<>();
        String stage = "";
        boolean cannot = false;
        for (Milestone milestone : all) {
            String label = milestone.label(goal.fallback);
            Mark mark;
            if (milestone.satisfied(facts, goal.fallback, all)) mark = Mark.DONE;
            else if (!milestone.supported() || cannot) { cannot = true; mark = Mark.CANNOT; }
            else if (stage.isEmpty()) { stage = label; mark = hindered ? Mark.OBSTACLE : Mark.CURRENT; }
            else mark = Mark.LATER;
            lines.add(new Line(label, mark));
        }
        // A goal that stops at what he cannot do yet is not hindered: there is no step to go wrong, and the list says so.
        boolean stuckOnSkill = stage.isEmpty() && goal.state == State.BLOCKED;
        String obstacle = hindered && !stage.isEmpty() ? goal.reason.strip() : "";
        // The reason is the tail of a mechanics report: kept short, it goes into every prompt and into synced entity data.
        if (obstacle.length() > OBSTACLE_LIMIT) obstacle = obstacle.substring(0, OBSTACLE_LIMIT - 1) + "…";
        return new View(goal.id, goal.book().title(), goal.state, deferredOnly, now(goal.state, deferredOnly, stuckOnSkill, stage),
                obstacle, List.copyOf(lines), other == null ? "" : other.book().title(), c.goals().outstanding());
    }

    /** The context line for the entity's synced data; empty when there is no long goal. */
    public static String line(CompanionEntity c) {
        View view = of(c);
        return view == null ? "" : view.contextLine();
    }

    private static String now(State state, boolean deferredOnly, boolean stuckOnSkill, String stage) {
        if (deferredOnly) return "отложена, вернусь к ней по слову «продолжай»";
        String at = stage.isEmpty() ? "" : " (этап: " + stage + ")";
        return switch (state) {
            case ACTIVE -> stage.isEmpty() ? "проверяю, что осталось" : stage;
            case INTERRUPTED -> "отвлёкся на просьбу игрока, потом сам вернусь к цели" + at;
            case HELD -> "на паузе, жду слова «продолжай»" + at;
            case PAUSED_DANGER -> "пауза, сначала разберусь с опасностью" + at;
            case BLOCKED -> stuckOnSkill ? "дошёл до того, что пока не умею" : "стою, нужна помощь игрока" + at;
            case COMPLETED, CANCELLED -> "";
        };
    }
}

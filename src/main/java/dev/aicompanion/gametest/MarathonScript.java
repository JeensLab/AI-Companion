package dev.aicompanion.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Requests one after another, the way the player plays: say something, wait till it is done (or for a moment in the game), look at
 * what happened, say the next thing. Each stage has a time limit in ticks; a stage over time fails the test with the observer's
 * trail. The checks of a stage run once, right after it.
 */
final class MarathonScript {
    /** soft: running over time is an issue noted for the end, not the end of the test. */
    private record Stage(String name, Runnable start, BooleanSupplier done, int limit, boolean soft) { }

    private final GameTestHelper h;
    private final MarathonWatch watch;
    private final MarathonChat chat;
    private final List<Stage> stages = new ArrayList<>();
    private int current;
    private long startedAt = -1;
    private long tick;
    private boolean finished;
    private String name = "marathon";

    MarathonScript(GameTestHelper h, MarathonWatch watch, MarathonChat chat) { this.h = h; this.watch = watch; this.chat = chat; }

    /** A request; the stage ends when its plan is done. */
    MarathonScript ask(String text, String modelSteps, int limitTicks) {
        MarathonChat.Said[] said = new MarathonChat.Said[1];
        stages.add(new Stage("«" + text + "»", () -> { watch.newSection(text); said[0] = modelSteps == null ? chat.say(text) : chat.say(text, modelSteps); }, () -> said[0].done(), limitTicks, false));
        return this;
    }

    /** A short order that only switches a mode («иди за мной», «стой»): done as soon as the server took it. */
    MarathonScript order(String text) { return ask(text, null, 200); }

    /** Something happens in the world (the player walks off, a mob comes): started once, done when the condition holds. */
    MarathonScript then(String name, Runnable start, BooleanSupplier done, int limitTicks) {
        stages.add(new Stage(name, start, done, limitTicks, false));
        return this;
    }

    /** Waits for something Oleg should do on his own; not done in time is an issue, and the chain goes on. */
    MarathonScript waitUntil(String name, BooleanSupplier done, int limitTicks) { stages.add(new Stage(name, () -> { }, done, limitTicks, true)); return this; }

    /** Lets the game run a while (a player looks around); never fails. */
    MarathonScript pause(int ticks) {
        long[] until = {0};
        return then("pause " + ticks, () -> until[0] = tick + ticks, () -> tick >= until[0], ticks + 20);
    }

    MarathonScript check(String name, Runnable check) { return then(name, check, () -> true, 20); }

    /** Installs the script: the observer ticks every tick, the test succeeds when the last stage is done. */
    void run(String name, Supplier<String> finalNote) {
        this.name = name;
        MarathonTicks.each(h, this::tick);
        h.succeedWhen(() -> h.assertTrue(finished && watch.issues.isEmpty(), (finished ? "Issues: " + String.join(" ;; ", watch.issues) + " || " : "") + "Stage " + (current + 1) + "/" + stages.size() + " " + (current < stages.size() ? stages.get(current).name() : "")
                + " not done || " + chat.history() + " || " + watch.summary() + " || " + finalNote.get() + " || " + watch.trail()));
    }

    private void tick() {
        tick++;
        watch.tick();
        if (finished) return;
        // Stages that end at once (checks, finished plans) follow each other in the same tick.
        for (int guard = 0; guard < 8 && current < stages.size(); guard++) {
            Stage stage = stages.get(current);
            try {
                if (startedAt < 0) { startedAt = tick; stage.start().run(); }
                if (stage.done().getAsBoolean()) { current++; startedAt = -1; continue; }
            } catch (RuntimeException failure) { watch.dump(name, "FAILED " + stage.name() + ": " + failure.getMessage() + "\n" + chat.history()); throw failure; }
            if (tick - startedAt > stage.limit() && stage.soft()) { watch.expect(false, "not in " + stage.limit() / 20 + "s: " + stage.name()); current++; startedAt = -1; continue; }
            if (tick - startedAt > stage.limit()) {
                watch.dump(name, "TIMEOUT " + stage.name() + "\n" + chat.history());
                h.fail("Stage " + (current + 1) + " " + stage.name() + " took over " + stage.limit() / 20 + "s || " + chat.history() + " || " + watch.summary() + " || " + watch.trail());
            }
            return;
        }
        if (current >= stages.size() && !finished) { finished = true; watch.dump(name, "DONE\n" + chat.history()); }
    }
}

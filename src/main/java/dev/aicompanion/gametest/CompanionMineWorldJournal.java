package dev.aicompanion.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Development only. What one scenario run saw: counters, every goal stop, and the refusals the mine wrote into its own diagnostics. */
final class CompanionMineWorldJournal {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FLOOR_REFUSAL = "неустойчивый пол", UNLOADED_REFUSAL = "незагруженн", STAIR_STOP = "Дальше лестницу не прокопать";
    /** The status he sets when he has no stairs of his own to the surface and begins to cut new ones; once per cutting, the steps say something else. */
    private static final String NEW_STAIRS = "прокапываю новую к поверхности";
    private static final String CAVE_MARK = "Наткнулся на пещеру", PATCH_MARK = "Пролом заделал";
    private static final long SLOW_ENTITY_TICK_NANOS = 50_000_000L;
    /** The last rows of each kind kept in the journal: how a death came about is in the end of the run. */
    private static final int KEPT = 40;

    record Blocked(long tick, String reason, String at, String lastTask) { }

    final String scenario, stage;
    final long startedNanos = System.nanoTime();
    final List<Blocked> blocked = new ArrayList<>();
    final List<String> caveNotes = new ArrayList<>();
    /** Rows of the entity's own diagnostics that explain a death: hits taken, creepers met, very dangerous caves, the deaths themselves. */
    final List<String> hurts = new ArrayList<>(), creeperEvents = new ArrayList<>(), veryDangerous = new ArrayList<>(), deathEvents = new ArrayList<>();
    final Map<String, Object> facts = new LinkedHashMap<>();
    /** Time of day (in ticks, 0..23999) the goal began at, -1 when the probe never got that far. */
    long startDayTime = -1;
    int resumes, climbs, deaths, maxDepth, diagnosticBlocked, floorRefusals, unloadedRefusals, stairStops, caveSentences, patchSentences, newStairs;
    long maxEntityTickNanos, slowEntityTicks;
    /** Whether the hazard of the scenario really met Oleg; null for scenarios that have none. */
    Boolean hazardMet;

    CompanionMineWorldJournal(String scenario, String stage) { this.scenario = scenario; this.stage = stage; }

    void entityTick(long nanos) {
        maxEntityTickNanos = Math.max(maxEntityTickNanos, nanos);
        if (nanos > SLOW_ENTITY_TICK_NANOS) slowEntityTicks++;
    }

    double wallSeconds() { return (System.nanoTime() - startedNanos) / 1e9; }

    /** Cross-checks the polled stops against the mine's own diagnostic rows (call after CompanionDiagnostics.flush). */
    void readDiagnostics(Path gameDir) {
        Path file = gameDir.resolve("logs").resolve("aicompanion").resolve("companion-latest.jsonl");
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.startsWith("{")) continue;
                JsonObject row = JsonParser.parseString(line).getAsJsonObject();
                String event = text(row, "event");
                if (event.equals("goal_blocked")) diagnosticBlocked++;
                switch (event) {
                    case "companion_hurt" -> keep(hurts, row, "tick", "source", "attacker", "damage", "health", "position", "sourcePosition", "distance", "stage", "cavePhase", "air");
                    case "creeper_seen", "creeper_swell" -> keep(creeperEvents, row, "event", "probeTick", "creeperPosition", "distance", "position", "health", "stage", "cavePhase", "air");
                    case "cave_very_dangerous" -> keep(veryDangerous, row, "where", "reasons", "breach", "position");
                    case "work_status" -> { if (text(row, "message").contains(NEW_STAIRS)) newStairs++; }
                    case "death" -> keep(deathEvents, row, "tick", "source", "attacker", "position", "stage", "cavePhase", "air");
                    default -> { }
                }
                if (!event.equals("goal_step_finished")) continue;
                String report = text(row, "report");
                if (report.contains(FLOOR_REFUSAL)) floorRefusals++;
                if (report.contains(UNLOADED_REFUSAL)) unloadedRefusals++;
                if (report.contains(STAIR_STOP)) stairStops++;
                if (report.contains(CAVE_MARK)) { caveSentences++; caveNotes.add(sentence(report, CAVE_MARK)); }
                if (report.contains(PATCH_MARK)) patchSentences++;
            }
        } catch (IOException | RuntimeException error) {
            facts.put("diagnosticsError", error.toString());
        }
    }

    void write(Path file, String verdict, String reason) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenario", scenario);
        out.put("stage", stage);
        out.put("verdict", verdict);
        if (hazardMet != null) out.put("hazardMet", hazardMet);
        out.put("reason", reason);
        out.put("wallSeconds", Math.round(wallSeconds()));
        out.put("goalBlocked", blocked.size());
        out.put("goalBlockedInDiagnostics", diagnosticBlocked);
        out.put("resumes", resumes);
        out.put("climbs", climbs);
        out.put("deaths", deaths);
        out.put("maxDepth", maxDepth);
        out.put("floorRefusals", floorRefusals);
        out.put("stairStops", stairStops);
        out.put("day", startDayTime);
        out.put("newStairs", newStairs);
        out.put("unloadedRefusals", unloadedRefusals);
        out.put("caveSentences", caveSentences);
        out.put("patchSentences", patchSentences);
        out.put("maxEntityTickMs", maxEntityTickNanos / 1_000_000L);
        out.put("slowEntityTicks", slowEntityTicks);
        out.put("caveNotes", caveNotes.stream().limit(8).toList());
        out.put("blocked", blocked);
        out.put("hurts", hurts);
        out.put("creeperEvents", creeperEvents);
        out.put("veryDangerous", veryDangerous);
        out.put("deathEvents", deathEvents);
        out.putAll(facts);
        Files.writeString(file, JSON.toJson(out), StandardCharsets.UTF_8);
    }

    /** One line of the named fields of the row, the oldest line dropped once there are too many. */
    private static void keep(List<String> into, JsonObject row, String... keys) {
        StringBuilder line = new StringBuilder();
        for (String key : keys) line.append(line.isEmpty() ? "" : " ").append(key).append('=').append(text(row, key));
        if (into.size() >= KEPT) into.remove(0);
        into.add(line.toString());
    }

    private static String text(JsonObject row, String key) { return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : ""; }

    private static String sentence(String report, String mark) {
        int from = report.indexOf(mark), to = report.indexOf('.', from);
        return report.substring(from, to < 0 ? Math.min(report.length(), from + 160) : Math.min(to + 1, from + 240));
    }
}

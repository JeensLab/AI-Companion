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
    private static final String CAVE_MARK = "Наткнулся на пещеру", PATCH_MARK = "Пролом заделал";
    private static final long SLOW_ENTITY_TICK_NANOS = 50_000_000L;

    record Blocked(long tick, String reason, String at, String lastTask) { }

    final String scenario, stage;
    final long startedNanos = System.nanoTime();
    final List<Blocked> blocked = new ArrayList<>();
    final List<String> caveNotes = new ArrayList<>();
    final Map<String, Object> facts = new LinkedHashMap<>();
    int resumes, climbs, deaths, maxDepth, diagnosticBlocked, floorRefusals, unloadedRefusals, stairStops, caveSentences, patchSentences;
    long maxEntityTickNanos, slowEntityTicks;

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
        out.put("unloadedRefusals", unloadedRefusals);
        out.put("caveSentences", caveSentences);
        out.put("patchSentences", patchSentences);
        out.put("maxEntityTickMs", maxEntityTickNanos / 1_000_000L);
        out.put("slowEntityTicks", slowEntityTicks);
        out.put("caveNotes", caveNotes.stream().limit(8).toList());
        out.put("blocked", blocked);
        out.putAll(facts);
        Files.writeString(file, JSON.toJson(out), StandardCharsets.UTF_8);
    }

    private static String text(JsonObject row, String key) { return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : ""; }

    private static String sentence(String report, String mark) {
        int from = report.indexOf(mark), to = report.indexOf('.', from);
        return report.substring(from, to < 0 ? Math.min(report.length(), from + 160) : Math.min(to + 1, from + 240));
    }
}

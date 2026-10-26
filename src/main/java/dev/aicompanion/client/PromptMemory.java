package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionMemory;
import dev.aicompanion.entity.CompanionMemoryDigest.Digest;

import java.util.ArrayList;
import java.util.List;

/** Memory, bond and happenings as text fields of the world snapshot. Pure: no game classes, so it is testable. */
public final class PromptMemory {
    /** A full memory plus a full happenings buffer is about 4000 characters; this is all of it that may reach one request. */
    public static final int SNAPSHOT_MEMORY_HARD_LIMIT_CHARS = 2500;
    private static final int HAPPENINGS_BUDGET = 800;
    private static final int ITEM_MAX = 160;
    private static final int SEPARATOR = 2;
    private static final int RECALLED = 5;
    private static final int AMBIENT_MEMORIES = 2;
    private static final String MEMORY_FIELD = "что ты помнишь о товарище=";

    private PromptMemory() { }

    /** Comma-joined snapshot fields; empty when the digest is empty. The bond comes first and the memories last, so only memories give way. */
    public static String section(Digest digest, String query) {
        List<String> fields = new ArrayList<>();
        String relation = relation(digest);
        if (!relation.isEmpty()) fields.add(relation);
        String happenings = happenings(digest.happenings());
        if (!happenings.isEmpty()) fields.add(happenings);

        int room = SNAPSHOT_MEMORY_HARD_LIMIT_CHARS - MEMORY_FIELD.length();
        for (String field : fields) room -= field.length() + SEPARATOR;
        List<String> kept = new ArrayList<>();
        for (String part : memoryParts(digest, query == null ? "" : query)) {
            if (part.length() + SEPARATOR > room) continue;
            kept.add(part);
            room -= part.length() + SEPARATOR;
        }
        if (!kept.isEmpty()) fields.add(MEMORY_FIELD + String.join("; ", kept));
        String joined = String.join(", ", fields);
        return joined.length() <= SNAPSHOT_MEMORY_HARD_LIMIT_CHARS ? joined : joined.substring(0, SNAPSHOT_MEMORY_HARD_LIMIT_CHARS - 1) + "…";
    }

    /** Ambient speech gets only the latest happening and up to two memories. */
    public static String ambient(Digest digest) {
        List<String> parts = new ArrayList<>();
        if (!digest.happenings().isEmpty()) parts.add("последнее событие=" + clip(digest.happenings().getLast()));
        List<String> memories = digest.relevant("", AMBIENT_MEMORIES).stream().map(entry -> clip(entry.text())).toList();
        if (!memories.isEmpty()) parts.add("помню=" + String.join(" | ", memories));
        return String.join("; ", parts);
    }

    private static String relation(Digest digest) {
        if (digest.tier().isEmpty()) return "";
        String reasons = digest.reasons().isEmpty() ? "" : " (причины: " + clipAll(digest.reasons()) + ")";
        return "ваши отношения=" + clip(digest.tier()) + reasons;
    }

    private static String happenings(List<String> lines) {
        List<String> newestFirst = new ArrayList<>();
        int used = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = clip(lines.get(i));
            if (used + line.length() + SEPARATOR > HAPPENINGS_BUDGET) break;
            newestFirst.add(0, line);
            used += line.length() + SEPARATOR;
        }
        return newestFirst.isEmpty() ? "" : "что недавно случилось=" + String.join("; ", newestFirst);
    }

    private static List<String> memoryParts(Digest digest, String query) {
        List<String> parts = new ArrayList<>();
        if (!digest.callMe().isEmpty()) parts.add("просил звать его " + clip(digest.callMe()));
        for (CompanionMemory.Entry entry : digest.relevant(query, RECALLED)) parts.add(clip(entry.text()));
        if (!digest.likes().isEmpty()) parts.add("ему нравится: " + clipAll(digest.likes()));
        if (!digest.dislikes().isEmpty()) parts.add("ему не нравится: " + clipAll(digest.dislikes()));
        return parts;
    }

    private static String clipAll(List<String> items) {
        return String.join(", ", items.stream().map(PromptMemory::clip).toList());
    }

    private static String clip(String text) {
        return text.length() <= ITEM_MAX ? text : text.substring(0, ITEM_MAX - 1) + "…";
    }
}

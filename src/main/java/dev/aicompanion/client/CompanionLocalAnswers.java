package dev.aicompanion.client;

import dev.aicompanion.CompanionDiagnostics;

import java.util.Random;

/**
 * The local answers of the chat/text path that need no client: «что ты умеешь» and «расскажи 10 умений» are built here
 * from the code's registry and recorded as his turn in the model's history, exactly as the other local answers are
 * recorded. The screen shows the returned line through its usual reply path. Kept out of the screen so a GameTest can
 * walk this path on a dedicated server, where the screen itself (a client class) cannot be loaded.
 */
public final class CompanionLocalAnswers {
    /**
     * The reply a routed action carries, or null when the action is not a local answer. The player's phrase and his
     * answer become the model's history, so the next request sees what the player was told.
     */
    public static String answer(String action, String text, String trace) {
        if (action == null || !action.startsWith("companion abilities")) return null;
        return record(text, trace, CompanionAbilities.answer(action));
    }

    /** The same reply from a given source, so a test can hold the draw still. */
    public static String answer(String action, String text, String trace, Random random) {
        if (action == null || !action.startsWith("companion abilities")) return null;
        return record(text, trace, CompanionAbilities.answer(action, random));
    }

    private static String record(String text, String trace, String answer) {
        CompanionDiagnostics.event(trace, "message_route", "route", "local_abilities");
        OpenRouterClient.recordLocalExchange(text, answer);
        return answer;
    }

    private CompanionLocalAnswers() { }
}

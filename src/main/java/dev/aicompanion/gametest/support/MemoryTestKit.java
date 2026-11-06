package dev.aicompanion.gametest.support;

import dev.aicompanion.client.CompanionMemoryFlow;
import dev.aicompanion.client.OpenRouterClient;

/** The replies and readers the memory GameTest classes share. */
public final class MemoryTestKit {
    private MemoryTestKit() { }

    public static final String VALID = "{\"memories\":[{\"kind\":\"player_fact\",\"text\":\"Игрок любит железо\"},"
            + "{\"kind\":\"promise\",\"text\":\"Обещал показать шахту\"}],\"call_me\":\"Роб\"}";

    public static void resetFlow() {
        CompanionMemoryFlow.useCompleter(null);
        CompanionMemoryFlow.unbind();
        OpenRouterClient.resetSession();
    }
}

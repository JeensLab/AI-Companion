package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.OpenRouterClient;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chat history belongs to one world session; the previous world's history and late replies must not leak into the next. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMemoryTests {
    @GameTest(template="empty")
    public static void historyIsClearedOnLogoutAndLateRepliesAreDropped(GameTestHelper h) {
        OpenRouterClient.resetSession();
        long begun = OpenRouterClient.generation();
        h.assertTrue(OpenRouterClient.recordReply(begun, "Первая реплика."), "Reply of the current session was refused");
        h.assertTrue(OpenRouterClient.historySize() == 1, "History did not take the reply: " + OpenRouterClient.historySize());

        OpenRouterClient.resetSession();
        h.assertTrue(OpenRouterClient.historySize() == 0, "History survived the reset: " + OpenRouterClient.historySize());
        h.assertTrue(OpenRouterClient.generation() != begun, "Reset did not start a new generation");

        h.assertTrue(!OpenRouterClient.recordReply(begun, "Поздний ответ."), "Late reply was accepted");
        h.assertTrue(OpenRouterClient.historySize() == 0, "Late reply leaked into the new session: " + OpenRouterClient.historySize());

        h.assertTrue(OpenRouterClient.recordReply(OpenRouterClient.generation(), "Новая реплика."), "New session refused its own reply");
        h.assertTrue(OpenRouterClient.historySize() == 1, "New session history is wrong: " + OpenRouterClient.historySize());
        OpenRouterClient.resetSession();
        h.succeed();
    }
}

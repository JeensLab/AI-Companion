package dev.aicompanion.gametest;

import com.google.gson.JsonObject;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.client.OpenRouterRetry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Review 22.10, К3/В10. The game log had single model answers of 64, 69 and 131 seconds despite a «60 s timeout», which only
 * covers the response headers; a body that never ends would hold the request queue for ever («перестал отвечать»).
 * And every request carried up to a dozen old world snapshots in its history. No network is used here.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionModelLinkTests {
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aHangingAnswerEndsWithAPlainMessage(GameTestHelper h) {
        CompletableFuture<String> never = new CompletableFuture<>();
        var bounded = OpenRouterRetry.bounded(never, Duration.ofMillis(20), "Модель не ответила вовремя.");
        var quick = OpenRouterRetry.bounded(CompletableFuture.completedFuture("ответ"), Duration.ofMillis(20), "x");
        h.succeedWhen(() -> {
            h.assertTrue(bounded.isCompletedExceptionally() && never.isCancelled(), "A hanging answer was not ended");
            try { bounded.join(); } catch (Exception error) {
                h.assertTrue(error.getCause() instanceof TimeoutException && "Модель не ответила вовремя.".equals(error.getCause().getMessage()), "Wrong failure: " + error);
            }
            h.assertTrue("ответ".equals(quick.join()), "A quick answer was changed");
        });
    }

    @GameTest(template = "empty")
    public static void onlyTheCurrentMessageCarriesTheWorld(GameTestHelper h) {
        JsonObject system = message("system", "правила"), old = message("user", "добудь камень"), reply = message("assistant", "Ага."),
                current = message("user", "а теперь железо");
        var sent = OpenRouterClient.withContext(List.of(system, old, reply, current), current, "\n\n[Контекст мира: снимок]");
        h.assertTrue(sent.get(1).getAsJsonObject().get("content").getAsString().equals("добудь камень"), "An old message carries a snapshot");
        h.assertTrue(sent.get(3).getAsJsonObject().get("content").getAsString().endsWith("[Контекст мира: снимок]"), "The current message lost the world");
        h.assertTrue(current.get("content").getAsString().equals("а теперь железо"), "The stored history was changed");
        h.succeed();
    }

    /** Issue #2: any OpenAI-compatible server instead of OpenRouter, the address completed as OpenAI clients do; a local one needs no key. */
    @GameTest(template = "empty")
    public static void anyCompatibleServerTakesTheModelRequests(GameTestHelper h) {
        var openRouter = OpenRouterClient.endpoint("");
        var ollama = OpenRouterClient.endpoint(" http://localhost:11434/v1/ ");
        h.assertTrue(openRouter.toString().equals("https://openrouter.ai/api/v1/chat/completions"), "Empty is not OpenRouter: " + openRouter);
        h.assertTrue(ollama.toString().equals("http://localhost:11434/v1/chat/completions"), "Base address not completed: " + ollama);
        h.assertTrue(OpenRouterClient.endpoint("http://127.0.0.1:1234/v1/chat/completions").toString()
                .equals("http://127.0.0.1:1234/v1/chat/completions"), "A full address was changed");
        h.assertTrue(OpenRouterClient.keyRequired(openRouter) && !OpenRouterClient.keyRequired(ollama), "Key required for the wrong server");
        h.assertTrue(OpenRouterClient.service(openRouter).equals("OpenRouter") && OpenRouterClient.service(ollama).equals("localhost:11434"),
                "Errors name the wrong server");
        h.assertTrue(OpenRouterClient.keyRequired(OpenRouterClient.endpoint("https://OpenRouter.ai/api/v1"))
                && OpenRouterClient.endpoint("HTTP://localhost:11434/v1").getHost().equals("localhost"), "The address case matters");
        // The key goes to OpenRouter, any https server and this computer; never over plain http across a network.
        h.assertTrue(OpenRouterClient.sendsKey(openRouter) && OpenRouterClient.sendsKey(ollama)
                && OpenRouterClient.sendsKey(OpenRouterClient.endpoint("https://api.groq.com/openai/v1"))
                && !OpenRouterClient.sendsKey(OpenRouterClient.endpoint("http://192.168.1.20:11434/v1")), "The key goes to the wrong servers");
        h.assertTrue(OpenRouterClient.service(OpenRouterClient.endpoint("http://user:secret@localhost:11434/v1")).equals("localhost:11434"),
                "Credentials from the address reach errors and logs");
        try {
            OpenRouterClient.endpoint("localhost:11434");
            h.fail("An address without http:// was taken");
        } catch (IllegalStateException expected) {
            h.assertTrue(expected.getMessage().startsWith("Адрес сервера модели"), "Unclear message: " + expected.getMessage());
        }
        h.succeed();
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role); message.addProperty("content", content);
        return message;
    }
}

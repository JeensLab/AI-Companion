package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.client.OpenRouterRetry;
import dev.aicompanion.client.PlannedAction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

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
        h.assertTrue(OpenRouterClient.sendsKey(openRouter, "sk-or-v1-test") && OpenRouterClient.sendsKey(ollama, "gsk_test")
                && OpenRouterClient.sendsKey(OpenRouterClient.endpoint("https://api.groq.com/openai/v1"), "gsk_test")
                && !OpenRouterClient.sendsKey(OpenRouterClient.endpoint("http://192.168.1.20:11434/v1"), "gsk_test"), "The key goes to the wrong servers");
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

    /** Review of PR #7: «127.example.com» is not this computer, and an OpenRouter key left in the settings goes to OpenRouter only. */
    @GameTest(template = "empty")
    public static void keyStaysWithItsServer(GameTestHelper h) {
        Object[][] cases = {
                {"http://127.example.com/v1", "gsk_test", false},
                {"http://127.0.0.1:8317/v1", "gsk_test", true},
                {"http://127.1.2.3:11434/v1", "gsk_test", true},
                {"http://[::1]:11434/v1", "gsk_test", true},
                {"https://api.groq.com/openai/v1", "gsk_test", true},
                {"https://api.groq.com/openai/v1", "sk-or-v1-test", false},
                {"http://localhost:11434/v1", "sk-or-v1-test", false},
                {"", "sk-or-v1-test", true},
                {"https://OpenRouter.ai/api/v1", "sk-or-v1-test", true},
                {"http://openrouter.ai/api/v1", "sk-or-v1-test", false},
                {"https://openrouter.ai@evil.example/v1", "sk-or-v1-test", false},
                {"https://api.groq.com/openai/v1", "Bearer sk-or-v1-test", false},
                {"https://api.groq.com/openai/v1", "SK-OR-V1-TEST", false},
        };
        List<String> wrong = new ArrayList<>();
        for (Object[] c : cases)
            if (OpenRouterClient.sendsKey(OpenRouterClient.endpoint((String) c[0]), (String) c[1]) != (boolean) c[2]) wrong.add(c[0] + " + " + c[1]);
        h.assertTrue(wrong.isEmpty(), "The key goes to the wrong servers: " + wrong);
        // A held-back key is explained, or a 401 from a server that wanted it says nothing to the player.
        String forgotten = OpenRouterClient.withheldKeyNote(OpenRouterClient.endpoint("https://api.groq.com/openai/v1"), "sk-or-v1-test");
        String network = OpenRouterClient.withheldKeyNote(OpenRouterClient.endpoint("http://192.168.1.20:1234/v1"), "lm-key");
        h.assertTrue(forgotten.startsWith("Ключ OpenRouter") && network.contains("https")
                && OpenRouterClient.withheldKeyNote(OpenRouterClient.endpoint("https://api.groq.com/openai/v1"), "gsk_test").isEmpty()
                && OpenRouterClient.withheldKeyNote(OpenRouterClient.endpoint(""), "").isEmpty(),
                "Withheld key not explained: «" + forgotten + "» / «" + network + "»");
        h.succeed();
    }

    /** The model field still says openrouter/auto by default: with an own server its «model not found» must say what to change. */
    @GameTest(template = "empty")
    public static void ownServerWithTheOpenRouterModelSaysWhatToChange(GameTestHelper h) {
        var ollama = OpenRouterClient.endpoint("http://localhost:11434/v1");
        h.assertTrue(OpenRouterClient.modelNote(ollama, "openrouter/auto").contains("«Модель»")
                && !OpenRouterClient.modelNote(ollama, " ").isEmpty() && !OpenRouterClient.modelNote(ollama, "OpenRouter/free").isEmpty(),
                "The OpenRouter model for an own server is not explained");
        h.assertTrue(OpenRouterClient.modelNote(ollama, "llama3.1:8b").isEmpty()
                && OpenRouterClient.modelNote(OpenRouterClient.endpoint(""), "openrouter/auto").isEmpty(), "A fitting model is questioned");
        h.succeed();
    }

    /**
     * In game 29.10: «Как думаешь, что лучше построить первым делом — дом или шахту?» made a plan obligatory («строить» in «построить»
     * and «дом»); the model answered in words and the player got «не передала команды» instead. A question gets the words, an order still needs a plan.
     */
    @GameTest(template = "empty")
    public static void aQuestionGetsTheWordsAnOrderStillNeedsAPlan(GameTestHelper h) {
        var words = new CompanionReply("Я бы сначала дерево, без него ни дома, ни кирки.", List.of());
        for (String question : new String[]{"Как думаешь, что лучше построить первым делом — дом или шахту?", "Как построить дом?",
                "Можно скрафтить алмазную кирку?", "Ты умеешь строить дом?", "Стоит ли строить дом из камня?"})
            h.assertTrue(!OpenRouterClient.wordsRefused(question, true, words), "A question lost its answer: " + question);
        for (String order : new String[]{"построй дом", "Олег, скрафти мне меч.", "Добудь железа!", "Можешь построить дом?", "Сможешь добыть железо?", "Пожалуйста, сделай кирку?"})
            h.assertTrue(OpenRouterClient.wordsRefused(order, true, words), "An order was answered without a plan: " + order);
        var plan = new CompanionReply("Строю.", List.of(new PlannedAction("build_house", 0, 1, "")));
        h.assertTrue(!OpenRouterClient.wordsRefused("построй дом", true, plan) && !OpenRouterClient.wordsRefused("привет", false, words),
                "A plan or a chat was refused");
        h.succeed();
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role); message.addProperty("content", content);
        return message;
    }

    /** A canned chat completion: the finish reason, the text (null for none) and whether a tool call came with it. */
    private static JsonObject response(String finish, String content, boolean toolCall) {
        JsonObject reply = new JsonObject();
        reply.addProperty("role", "assistant");
        if (content == null) reply.add("content", com.google.gson.JsonNull.INSTANCE); else reply.addProperty("content", content);
        if (toolCall) {
            JsonArray calls = new JsonArray();
            calls.add(new JsonObject());
            reply.add("tool_calls", calls);
        }
        JsonObject choice = new JsonObject();
        choice.addProperty("finish_reason", finish);
        choice.add("message", reply);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject response = new JsonObject();
        response.add("choices", choices);
        return response;
    }

    /**
     * Measured 11.2026: with thinking on, the 12 258-token prompt spent 2263 hidden tokens and 180 s; the same prompt with
     * the template switch off answered in 4–12 s. Sampling is absent until the owner sets it, so the server's default stands.
     */
    @GameTest(template = "empty")
    public static void thinkingIsOffByDefaultAndSamplingOnlyWhenSet(GameTestHelper h) {
        JsonArray messages = new JsonArray();
        messages.add(message("system", "правила"));
        JsonObject thinkingOff = OpenRouterClient.requestBody("Dingo", messages, OpenRouterClient.answerBudget(false), true, null, null);
        h.assertTrue(thinkingOff.has("chat_template_kwargs")
                && !thinkingOff.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean(), "Thinking is not switched off by default");
        h.assertTrue(!thinkingOff.has("temperature") && !thinkingOff.has("top_p"), "Sampling is sent although not set");
        JsonObject tuned = OpenRouterClient.requestBody("Dingo", messages, 1024, true, 0.7, 0.9);
        h.assertTrue(tuned.get("temperature").getAsDouble() == 0.7 && tuned.get("top_p").getAsDouble() == 0.9, "A set sampling value was not sent");
        JsonObject owners = OpenRouterClient.requestBody("Dingo", messages, OpenRouterClient.answerBudget(true), false, null, null);
        h.assertTrue(!owners.has("chat_template_kwargs"), "Thinking was forced off although the owner turned it on");
        // A server that does not know the field: the refusal is recognised and the retry drops only the switch.
        h.assertTrue(OpenRouterClient.refusedUnknownField("llama.cpp: unknown field chat_template_kwargs")
                && OpenRouterClient.refusedUnknownField("Unrecognized field \"chat_template_kwargs\"")
                && !OpenRouterClient.refusedUnknownField("model not found"), "A refusal of the switch is not recognised");
        JsonObject plain = OpenRouterClient.withoutThinkingField(tuned);
        h.assertTrue(!plain.has("chat_template_kwargs") && plain.get("temperature").getAsDouble() == 0.7 && tuned.has("chat_template_kwargs"),
                "The fallback changed more than the switch");
        h.succeed();
    }

    /** 2048 was below the measured 2263 reasoning tokens + plan; the budget now fits thinking on, and is small without it. */
    @GameTest(template = "empty")
    public static void theBudgetFitsTheReasoningItAsksFor(GameTestHelper h) {
        h.assertTrue(OpenRouterClient.answerBudget(true) == 4096 && OpenRouterClient.answerBudget(false) == 1024, "The budget does not fit thinking on/off");
        h.assertTrue(OpenRouterClient.retryBudget(1024) == 2048 && OpenRouterClient.retryBudget(4096) == 8192
                && OpenRouterClient.retryBudget(8192) == 8192, "The retry budget is wrong");
        h.succeed();
    }

    /** The measured failure: finish_reason «length», the whole budget in hidden reasoning, no text, the plan lost silently. */
    @GameTest(template = "empty")
    public static void anEmptyCutOffAnswerIsRetriedOnceWithABiggerBudget(GameTestHelper h) {
        JsonObject cut = response("length", null, false), plan = response("stop", "{\"steps\":[]}", false);
        List<Integer> budgets = new ArrayList<>();
        var first = OpenRouterClient.ensureAnswer(CompletableFuture.completedFuture(cut), 1024, budget -> {
            budgets.add(budget);
            return CompletableFuture.completedFuture(plan);
        });
        h.assertTrue(plan == first.join() && budgets.equals(List.of(2048)), "The empty cut-off answer was not retried once with a bigger budget");
        budgets.clear();
        var twice = OpenRouterClient.ensureAnswer(CompletableFuture.completedFuture(cut), 1024, budget -> {
            budgets.add(budget);
            return CompletableFuture.completedFuture(cut);
        });
        boolean honest = false;
        try { twice.join(); } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            honest = cause instanceof IllegalStateException && cause.getMessage().contains("оборвался");
        }
        h.assertTrue(honest && budgets.equals(List.of(2048)), "A still empty answer did not fail honestly once: " + budgets);
        List<Integer> untouched = new ArrayList<>();
        var good = OpenRouterClient.ensureAnswer(CompletableFuture.completedFuture(plan), 1024, budget -> {
            untouched.add(budget);
            return CompletableFuture.completedFuture(cut);
        });
        h.assertTrue(good.join() == plan && untouched.isEmpty(), "A good answer was retried");
        h.assertTrue(OpenRouterClient.truncatedEmpty(cut) && !OpenRouterClient.truncatedEmpty(plan)
                && !OpenRouterClient.truncatedEmpty(response("length", "короткий текст", false))
                && !OpenRouterClient.truncatedEmpty(response("length", null, true)), "A usable answer looks empty");
        h.succeed();
    }

    /** Measured: three 60 s timeouts with two pauses held the queue 183 s; the attempt limit is the 90 s answer cap. */
    @GameTest(template = "empty")
    public static void aTimeoutIsNotRetriedAndTheLimitIsTheAnswerCap(GameTestHelper h) {
        AtomicInteger attempts = new AtomicInteger();
        var timedOut = OpenRouterRetry.execute(() -> {
            attempts.incrementAndGet();
            return CompletableFuture.<Integer>failedFuture(new HttpTimeoutException("timed out"));
        }, value -> value, value -> "", delay -> CompletableFuture.completedFuture(null));
        boolean failed = false;
        try { timedOut.join(); } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            failed = cause instanceof HttpTimeoutException;
        }
        h.assertTrue(failed && attempts.get() == 1, "A timeout was retried: " + attempts.get());
        attempts.set(0);
        var retried = OpenRouterRetry.execute(() -> attempts.incrementAndGet() == 1
                ? CompletableFuture.<Integer>failedFuture(new IOException("offline")) : CompletableFuture.completedFuture(200),
                value -> value, value -> "", delay -> CompletableFuture.completedFuture(null));
        h.assertTrue(retried.join() == 200 && attempts.get() == 2, "A transport failure stopped being retried");
        h.assertTrue(OpenRouterClient.ANSWER_LIMIT.equals(Duration.ofSeconds(90)), "The attempt limit left the 90 s answer cap");
        h.succeed();
    }

    /** The owner sees «не задавать» until he chooses a value; a value outside the API's range is clamped, not sent as is. */
    @GameTest(template = "empty")
    public static void theSamplingSettingsKeepTheirDefaultsAndRanges(GameTestHelper h) {
        h.assertTrue(!OpenRouterConfig.THINKING.getDefault() && OpenRouterConfig.TEMPERATURE.getDefault().isEmpty()
                && OpenRouterConfig.TOP_P.getDefault().isEmpty(), "A model setting changed its default");
        h.assertTrue(OpenRouterClient.sampling("", 2.0) == null && OpenRouterClient.sampling(null, 1.0) == null
                && OpenRouterClient.sampling("не задавать", 2.0) == null && OpenRouterClient.sampling("abc", 2.0) == null,
                "An unset value became a number");
        h.assertTrue(OpenRouterClient.sampling("0,7", 2.0) == 0.7 && OpenRouterClient.sampling("3.5", 2.0) == 2.0
                && OpenRouterClient.sampling("-1", 2.0) == 0.0 && OpenRouterClient.sampling("1.4", 1.0) == 1.0,
                "Sampling is not parsed or clamped");
        h.succeed();
    }
}

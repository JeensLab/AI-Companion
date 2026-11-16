package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.OpenAiResponses;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.client.OpenRouterRetry;
import dev.aicompanion.client.PlannedAction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
     * the template switch off answered in 4–12 s. The switch carries the owner's own value, so turning thinking on is not
     * left to a server whose template default is off. Sampling is absent until the owner sets it, then sent as typed.
     */
    @GameTest(template = "empty")
    public static void thinkingIsSentAsConfiguredAndSamplingOnlyWhenSet(GameTestHelper h) {
        JsonArray messages = new JsonArray();
        messages.add(message("system", "правила"));
        JsonObject thinkingOff = OpenRouterClient.requestBody("Dingo", messages, OpenRouterClient.answerBudget(false), false, null, null);
        h.assertTrue(thinkingOff.has("chat_template_kwargs")
                && !thinkingOff.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean(), "Thinking is not switched off by default");
        JsonObject thinkingOn = OpenRouterClient.requestBody("Dingo", messages, OpenRouterClient.answerBudget(true), true, null, null);
        h.assertTrue(thinkingOn.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean(),
                "Thinking on is not sent: a server whose template default is off would ignore the switch");
        h.assertTrue(!thinkingOff.has("temperature") && !thinkingOff.has("top_p"), "Sampling is sent although not set");
        JsonObject tuned = OpenRouterClient.requestBody("Dingo", messages, 1024, false, 0.7, 1.5);
        h.assertTrue(tuned.get("temperature").getAsDouble() == 0.7 && tuned.get("top_p").getAsDouble() == 1.5,
                "A set sampling value was not sent as typed");
        // A refusal of the switch is recognised by name with tools too; a bare phrase is believed only without tools, where a
        // strict gateway's «unknown field max_tokens» cannot silently turn thinking off and restore 100–180 s answers.
        h.assertTrue(OpenRouterClient.refusedUnknownField("llama.cpp: unknown field chat_template_kwargs", true)
                && OpenRouterClient.refusedUnknownField("Unrecognized field \"chat_template_kwargs\"", true)
                && OpenRouterClient.refusedUnknownField("unknown field", false)
                && OpenRouterClient.refusedUnknownField("additional properties are not allowed", false)
                && !OpenRouterClient.refusedUnknownField("unknown field", true)
                && !OpenRouterClient.refusedUnknownField("extra field max_tokens", true)
                && !OpenRouterClient.refusedUnknownField("model not found", true)
                && !OpenRouterClient.refusedUnknownField("model not found", false), "A refusal of the switch is not recognised");
        // The fallback drops only the switch and raises the budget: without it the server thinks by default.
        JsonObject plain = OpenRouterClient.fallbackBody(tuned);
        h.assertTrue(!plain.has("chat_template_kwargs") && plain.get("temperature").getAsDouble() == 0.7
                && plain.get("max_tokens").getAsInt() == OpenRouterClient.answerBudget(true) && tuned.has("chat_template_kwargs"),
                "The fallback changed more than the switch and the budget");
        h.succeed();
    }

    /**
     * OpenRouter does not document the template switch, so the chat body's switch never reaches it: there the thinking
     * switch is reasoning.effort «none», and reasoning tokens count against max_tokens, so the budget follows the same
     * thinking state. A plain chat server keeps the template switch and is never sent a reasoning field.
     */
    @GameTest(template = "empty")
    public static void openRouterGetsItsOwnThinkingFieldAndNoTemplateSwitch(GameTestHelper h) {
        JsonArray messages = new JsonArray();
        messages.add(message("user", "привет"));
        JsonObject off = OpenRouterClient.requestBody("openrouter/auto", messages, OpenRouterClient.answerBudget(false), false, null, null);
        JsonObject sentOff = OpenRouterClient.wireBody(OpenRouterClient.endpoint(""), off);
        h.assertTrue(sentOff.has("reasoning") && sentOff.getAsJsonObject("reasoning").get("effort").getAsString().equals("none"),
                "Thinking off is not sent as reasoning.effort none on OpenRouter");
        h.assertTrue(!sentOff.has("chat_template_kwargs"), "OpenRouter was sent the template switch it does not document");
        h.assertTrue(sentOff.get("max_tokens").getAsInt() == OpenRouterClient.answerBudget(false),
                "The OpenRouter budget does not match the thinking state: " + sentOff.get("max_tokens"));
        JsonObject on = OpenRouterClient.requestBody("openrouter/auto", messages, OpenRouterClient.answerBudget(true), true, null, null);
        JsonObject sentOn = OpenRouterClient.wireBody(OpenRouterClient.endpoint(""), on);
        h.assertTrue(!sentOn.has("reasoning") && !sentOn.has("chat_template_kwargs"),
                "Thinking on imposed an effort of ours on OpenRouter");
        h.assertTrue(sentOn.get("max_tokens").getAsInt() == OpenRouterClient.answerBudget(true),
                "The thinking-on budget was lost on OpenRouter");
        JsonObject tuned = OpenRouterClient.requestBody("openrouter/auto", messages, 1024, false, 0.7, 1.5);
        JsonObject sentTuned = OpenRouterClient.wireBody(OpenRouterClient.endpoint(""), tuned);
        h.assertTrue(sentTuned.get("temperature").getAsDouble() == 0.7 && sentTuned.get("top_p").getAsDouble() == 1.5,
                "The owner's sampling was lost on OpenRouter");
        h.assertTrue(sentOff.get("messages").toString().equals(messages.toString()), "The OpenRouter body lost the prompt");
        JsonObject local = OpenRouterClient.wireBody(OpenRouterClient.endpoint("http://localhost:11434/v1"), off);
        h.assertTrue(local.has("chat_template_kwargs") && !local.has("reasoning")
                && !local.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean(),
                "A plain chat server lost its template switch");
        h.assertTrue(off.has("chat_template_kwargs") && !off.has("reasoning") && !tuned.has("reasoning"),
                "The chat body itself was changed");
        h.succeed();
    }

    /** 2048 was below the measured 2263 reasoning tokens + plan; the budget now fits thinking on, and is small without it. */
    @GameTest(template = "empty")
    public static void theBudgetFitsTheReasoningItAsksFor(GameTestHelper h) {
        h.assertTrue(OpenRouterClient.answerBudget(true) == 4096 && OpenRouterClient.answerBudget(false) == 1024, "The budget does not fit thinking on/off");
        h.assertTrue(OpenRouterClient.ambientBudget(false) == 600 && OpenRouterClient.ambientBudget(true) == 2048,
                "The ambient budget does not fit thinking on/off");
        h.assertTrue(OpenRouterClient.retryBudget(1024) == 2048 && OpenRouterClient.retryBudget(4096) == 8192
                && OpenRouterClient.retryBudget(8192) == 8192, "The retry budget is wrong");
        h.succeed();
    }

    /**
     * The retry doubles the budget of the request that was cut off, capped: a thinking-off plan retries at 2048, a
     * thinking-on one at 8192, never 1024 at 8192. A still empty answer fails honestly after that one retry.
     */
    @GameTest(template = "empty")
    public static void anEmptyCutOffAnswerIsRetriedOnceWithABiggerBudget(GameTestHelper h) {
        JsonObject cut = response("length", null, false), plan = response("stop", "{\"steps\":[]}", false);
        // The budget answer() hands to the retry: the request's own, doubled.
        h.assertTrue(OpenRouterClient.retryBudget(OpenRouterClient.answerBudget(false)) == 2048
                && OpenRouterClient.retryBudget(OpenRouterClient.answerBudget(true)) == 8192,
                "The retry does not double the budget of the request that was cut off");
        List<Integer> budgets = new ArrayList<>();
        var first = OpenRouterClient.ensureAnswer(CompletableFuture.completedFuture(cut), OpenRouterClient.answerBudget(false), budget -> {
            budgets.add(budget);
            return CompletableFuture.completedFuture(plan);
        });
        h.assertTrue(plan == first.join() && budgets.equals(List.of(2048)),
                "The empty cut-off answer was not retried once with 2048: " + budgets);
        budgets.clear();
        var twice = OpenRouterClient.ensureAnswer(CompletableFuture.completedFuture(cut), OpenRouterClient.answerBudget(true), budget -> {
            budgets.add(budget);
            return CompletableFuture.completedFuture(cut);
        });
        boolean honest = false;
        try { twice.join(); } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            honest = cause instanceof IllegalStateException && cause.getMessage().contains("оборвался");
        }
        h.assertTrue(honest && budgets.equals(List.of(8192)), "A still empty answer did not fail honestly once: " + budgets);
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

    /**
     * Two timers raced on one attempt — the request timeout and the bounded future — so one game run said «Модель не
     * ответила за 90 секунд…» and another a bare «request timed out». The bounded future is now the only limit, and a
     * timeout of any origin is told in the same words: the transport's English line never reaches the player.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aTimeoutGetsOneHonestMessageWhicheverTimerFires(GameTestHelper h) {
        String note = OpenRouterClient.timeoutNote();
        h.assertTrue(note.contains("90") && note.contains("Модель не ответила") && note.contains("модель побыстрее"),
                "The timeout message lost its own words: " + note);
        var bounded = OpenRouterRetry.bounded(new CompletableFuture<>(), Duration.ofMillis(20), note);
        String fromLimit = "";
        try { bounded.join(); } catch (Exception error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            fromLimit = cause instanceof TimeoutException ? cause.getMessage() : "";
        }
        h.assertTrue(note.equals(fromLimit), "The bounded limit said something else: " + fromLimit);
        var openRouter = OpenRouterClient.endpoint("");
        Throwable transport = OpenRouterClient.explained(openRouter, new HttpTimeoutException("request timed out"));
        Throwable wrapped = OpenRouterClient.explained(openRouter, new CompletionException(new HttpTimeoutException("request timed out")));
        h.assertTrue(transport instanceof IllegalStateException && note.equals(transport.getMessage()),
                "A transport timeout kept the English words: " + transport.getMessage());
        h.assertTrue(note.equals(wrapped.getMessage()), "A wrapped transport timeout was not given the same words");
        Throwable connect = OpenRouterClient.explained(openRouter, new HttpConnectTimeoutException("HTTP connect timed out"));
        h.assertTrue(connect.getMessage().contains("не отвечает") && !connect.getMessage().contains("request timed out"),
                "A connect timeout was read as a slow model: " + connect.getMessage());
        Throwable other = new IllegalStateException("Модель не нашлась");
        h.assertTrue(OpenRouterClient.explained(openRouter, other) == other, "An ordinary failure was rewritten");
        h.succeed();
    }

    /**
     * The whole 400-to-fallback chain against a local stub server: the first body carries the template switch and the
     * thinking-off budget, the server refuses the field by name, and the retry drops only that field, keeps the prompt and
     * the tools, and raises the budget to fit the thinking the server now does by default.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void theRefusedThinkingSwitchFallsBackToTheThinkingOnBudget(GameTestHelper h) {
        HttpServer stub;
        try {
            stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException noPort) {
            h.fail("No local stub server: " + noPort);
            return;
        }
        List<String> bodies = Collections.synchronizedList(new ArrayList<>());
        stub.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            byte[] reply = bodies.size() == 1
                    ? "{\"error\":{\"message\":\"llama.cpp: unknown field chat_template_kwargs\"}}".getBytes(StandardCharsets.UTF_8)
                    : "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"steps\\\":[]}\"}}]}"
                            .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(bodies.size() == 1 ? 400 : 200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        stub.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + stub.getAddress().getPort() + "/v1/chat/completions");
            JsonArray messages = new JsonArray();
            messages.add(message("user", "построй дом"));
            JsonObject body = OpenRouterClient.requestBody("Dingo", messages, OpenRouterClient.answerBudget(false), false, 0.7, 1.5);
            JsonArray tools = new JsonArray();
            tools.add(OpenRouterClient.planTool());
            body.add("tools", tools);
            body.addProperty("tool_choice", "required");
            JsonObject answer = OpenRouterClient.postAdapted(endpoint, body, true, "fallback-chain")
                    .get(15, TimeUnit.SECONDS);
            h.assertTrue(bodies.size() == 2, "The stub saw " + bodies.size() + " requests, not two");
            JsonObject first = JsonParser.parseString(bodies.get(0)).getAsJsonObject();
            JsonObject retry = JsonParser.parseString(bodies.get(1)).getAsJsonObject();
            h.assertTrue(first.get("max_tokens").getAsInt() == OpenRouterClient.answerBudget(false)
                    && !first.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean(),
                    "The first request did not carry the switch and the thinking-off budget");
            h.assertTrue(!retry.has("chat_template_kwargs"), "The retry kept the refused field");
            h.assertTrue(retry.get("max_tokens").getAsInt() == OpenRouterClient.answerBudget(true),
                    "The retry kept a budget too small for the thinking the server now does: " + retry.get("max_tokens"));
            h.assertTrue(retry.get("messages").equals(first.get("messages")), "The retry changed the prompt");
            h.assertTrue(retry.get("tools").equals(first.get("tools")), "The retry lost the tools");
            h.assertTrue(retry.get("temperature").getAsDouble() == 0.7, "The retry lost the owner's sampling");
            h.assertTrue(answer.has("choices"), "The fallback answer was not the server's completion");
        } catch (ExecutionException error) {
            h.fail("The fallback chain failed: " + error.getCause());
        } catch (TimeoutException slow) {
            h.fail("The fallback chain did not finish in 15 s");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            h.fail("Interrupted while waiting for the fallback chain");
        } finally {
            stub.stop(0);
        }
        h.succeed();
    }

    /**
     * «Unknown parameter: 'chat_template_kwargs'.» contains «parameter», which the tools check used to take as a refusal
     * of tools: the request fell to the JSON protocol without them, got a second 400 and only then retried without the
     * field — three requests per reply and no tool calls for good. The thinking field is tested before the tools now:
     * two requests, and the retry keeps the tools, losing only the field it refused.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aRefusedTemplateSwitchKeepsTheTools(GameTestHelper h) {
        HttpServer stub;
        try {
            stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException noPort) {
            h.fail("No local stub server: " + noPort);
            return;
        }
        List<String> bodies = Collections.synchronizedList(new ArrayList<>());
        stub.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            byte[] reply = bodies.size() == 1
                    ? "{\"error\":{\"message\":\"Unknown parameter: 'chat_template_kwargs'.\"}}".getBytes(StandardCharsets.UTF_8)
                    : "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"steps\\\":[]}\"}}]}"
                            .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(bodies.size() == 1 ? 400 : 200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        stub.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + stub.getAddress().getPort() + "/v1/chat/completions");
            JsonArray messages = new JsonArray();
            messages.add(message("user", "построй дом"));
            JsonObject body = OpenRouterClient.requestBody("Dingo", messages, OpenRouterClient.answerBudget(false), false, null, null);
            JsonArray tools = new JsonArray();
            tools.add(OpenRouterClient.planTool());
            body.add("tools", tools);
            body.addProperty("tool_choice", "required");
            JsonObject answer = OpenRouterClient.postAdapted(endpoint, body, true, "refusal-order").get(15, TimeUnit.SECONDS);
            h.assertTrue(bodies.size() == 2, "The stub saw " + bodies.size() + " requests, not two");
            JsonObject first = JsonParser.parseString(bodies.get(0)).getAsJsonObject();
            JsonObject retry = JsonParser.parseString(bodies.get(1)).getAsJsonObject();
            h.assertTrue(first.has("chat_template_kwargs") && first.has("tools"), "The first request lost the switch or the tools");
            h.assertTrue(!retry.has("chat_template_kwargs"), "The retry kept the refused field");
            h.assertTrue(retry.get("tools").equals(first.get("tools")), "The retry lost the tools: " + retry.get("tools"));
            h.assertTrue(retry.get("tool_choice").getAsString().equals("required"), "The retry lost the forced plan");
            h.assertTrue(retry.get("max_tokens").getAsInt() == OpenRouterClient.answerBudget(true),
                    "The retry kept a budget too small for a server that now thinks: " + retry.get("max_tokens"));
            h.assertTrue(retry.get("messages").equals(first.get("messages")), "The retry changed the prompt");
            h.assertTrue(answer.has("choices"), "The fallback answer was not the server's completion");
        } catch (ExecutionException error) {
            h.fail("The refusal chain failed: " + error.getCause());
        } catch (TimeoutException slow) {
            h.fail("The refusal chain did not finish in 15 s");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            h.fail("Interrupted while waiting for the refusal chain");
        } finally {
            stub.stop(0);
        }
        h.succeed();
    }

    /**
     * The owner types the number himself: dot or comma, negative too, and the mod imposes no range — models differ. A
     * non-number is not sent, stays as typed, and the screen says so. Empty means «не задавать».
     */
    @GameTest(template = "empty")
    public static void theSamplingSettingsAreFreeEntryWithoutARangeOfOurs(GameTestHelper h) {
        h.assertTrue(!OpenRouterConfig.THINKING.getDefault() && OpenRouterConfig.TEMPERATURE.getDefault().isEmpty()
                && OpenRouterConfig.TOP_P.getDefault().isEmpty(), "A model setting changed its default");
        h.assertTrue(OpenRouterClient.sampling("") == null && OpenRouterClient.sampling(null) == null
                && OpenRouterClient.sampling(" ") == null && OpenRouterClient.sampling("abc") == null,
                "An unset or non-numeric value became a number");
        h.assertTrue(OpenRouterClient.sampling("0.7") == 0.7 && OpenRouterClient.sampling("1,2") == 1.2
                && OpenRouterClient.sampling("1.5") == 1.5 && OpenRouterClient.sampling("-0.5") == -0.5
                && OpenRouterClient.sampling("3.5") == 3.5, "Sampling is parsed or clamped by the mod");
        h.assertTrue(OpenRouterClient.samplingHint("").isEmpty() && OpenRouterClient.samplingHint("1,2").isEmpty()
                && !OpenRouterClient.samplingHint("abc").isEmpty(), "A typed value is not explained");
        JsonArray messages = new JsonArray();
        messages.add(message("user", "привет"));
        JsonObject typed = OpenRouterClient.requestBody("Dingo", messages, 600, false,
                OpenRouterClient.sampling("-0.5"), OpenRouterClient.sampling("1.5"));
        h.assertTrue(typed.get("temperature").getAsDouble() == -0.5 && typed.get("top_p").getAsDouble() == 1.5,
                "A typed sampling value was not sent as typed");
        JsonObject junk = OpenRouterClient.requestBody("Dingo", messages, 600, false,
                OpenRouterClient.sampling("abc"), OpenRouterClient.sampling(""));
        h.assertTrue(!junk.has("temperature") && !junk.has("top_p"), "A junk value reached the request");
        h.succeed();
    }

    /**
     * Direct OpenAI goes through Responses, which has no chat_template_kwargs: the branch's rules must reach that wire
     * body too. Thinking off is reasoning.effort none, thinking on leaves the provider's own effort rather than one the
     * mod invents, the budget is the same one, sampling is sent only when the owner set it, and the field never goes to a
     * model that does not take it. The 400 fallback drops only the reasoning switch.
     */
    @GameTest(template = "empty")
    public static void theResponsesTransportKeepsTheThinkingSamplingAndBudgetRules(GameTestHelper h) {
        JsonArray messages = new JsonArray();
        messages.add(message("user", "построй дом"));
        JsonObject off = OpenRouterClient.requestBody("gpt-6-luna", messages, OpenRouterClient.answerBudget(false), false, null, null);
        JsonObject sentOff = OpenAiResponses.request(off);
        h.assertTrue(sentOff.getAsJsonObject("reasoning").get("effort").getAsString().equals("none"),
                "Thinking off is not sent on Responses");
        h.assertTrue(!sentOff.has("chat_template_kwargs") && !sentOff.has("messages")
                && sentOff.get("max_output_tokens").getAsInt() == OpenRouterClient.answerBudget(false),
                "A chat-only field or another budget reached Responses");
        JsonObject on = OpenRouterClient.requestBody("gpt-6-luna", messages, OpenRouterClient.answerBudget(true), true, null, null);
        JsonObject sentOn = OpenAiResponses.request(on);
        h.assertTrue(!sentOn.has("reasoning") && sentOn.get("max_output_tokens").getAsInt() == OpenRouterClient.answerBudget(true),
                "Thinking on imposed an effort of ours or kept the thinking-off budget");
        JsonObject tuned = OpenRouterClient.requestBody("gpt-6.1-terra", messages, 1024, false, 0.7, 1.5);
        JsonObject sentTuned = OpenAiResponses.request(tuned);
        h.assertTrue(sentTuned.get("temperature").getAsDouble() == 0.7 && sentTuned.get("top_p").getAsDouble() == 1.5,
                "The owner's sampling was lost on Responses");
        h.assertTrue(!OpenAiResponses.request(OpenRouterClient.requestBody("gpt-4o", messages, 600, false, null, null)).has("reasoning"),
                "A model that does not take effort was sent the field");
        JsonObject plain = OpenAiResponses.withoutReasoning(sentOff);
        h.assertTrue(!plain.has("reasoning") && plain.get("input").equals(sentOff.get("input"))
                && plain.get("max_output_tokens").equals(sentOff.get("max_output_tokens")) && sentOff.has("reasoning"),
                "The Responses fallback changed more than the reasoning switch");
        h.succeed();
    }

    /**
     * OpenRouter with require_parameters and a model without reasoning: «No endpoints found that can handle the requested
     * parameters». It is the reasoning switch that no endpoint takes, not the tools: the request is retried without it.
     */
    @GameTest(template = "empty")
    public static void noOpenRouterEndpointForTheReasoningSwitchDropsTheSwitchNotTheTools(GameTestHelper h) {
        String noEndpoint = "no endpoints found that can handle the requested parameters. to learn more about provider routing, visit: https://openrouter.ai/docs/provider-routing";
        h.assertTrue(OpenRouterClient.refusedReasoning(noEndpoint, true), "OpenRouter's «no endpoints» is not read as a refusal of the reasoning switch");
        h.assertTrue(!OpenRouterClient.refusedReasoning(noEndpoint, false), "Another server's words were taken for OpenRouter's");
        h.assertTrue(OpenRouterClient.refusedReasoning("unrecognized request argument supplied: reasoning", false), "A refusal naming the field is missed");
        h.assertTrue(!OpenRouterClient.refusedReasoning("invalid tool_choice parameter", true), "A refusal of tools was taken for the reasoning switch");
        h.succeed();
    }
}

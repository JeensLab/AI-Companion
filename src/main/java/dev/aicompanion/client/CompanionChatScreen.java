package dev.aicompanion.client;

import dev.aicompanion.CompanionIdentity;
import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.OpenRouterConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class CompanionChatScreen extends Screen {
    private static final int MAX_HISTORY_LINES = 200;
    private static final List<Component> conversation = new ArrayList<>();
    private EditBox input;
    private int scrollOffset;

    public CompanionChatScreen() {
        super(Component.literal(companionName()));
        if (conversation.isEmpty()) conversation.add(Component.literal(companionName() + ": Привет!").withStyle(ChatFormatting.AQUA));
    }

    static String companionName() { return CompanionIdentity.cleanName(OpenRouterConfig.COMPANION_NAME.get()); }

    @Override
    protected void init() {
        int panelWidth = Math.min(420, this.width - 32);
        int left = (this.width - panelWidth) / 2;
        int inputY = this.height - 42;
        CompanionNavigation.tabs(this, "Чат", left, 31, panelWidth, this::addRenderableWidget);
        input = addRenderableWidget(new EditBox(this.font, left, inputY, panelWidth - 86, 20,
                Component.literal("Напиши сообщение...")));
        input.setMaxLength(1000);
        addRenderableWidget(Button.builder(Component.literal("Отправить"), button -> sendMessage())
                .bounds(left + panelWidth - 80, inputY, 80, 20)
                .build());
        input.setFocused(true);
    }

    private void sendMessage() {
        String text = input.getValue().trim();
        if (text.isEmpty()) return;
        String trace = CompanionDiagnostics.newTrace();
        CompanionDiagnostics.event(trace, "player_message", "source", "text", "text", text);
        CompanionAmbientClient.playerMessage();
        // A message typed while the last answer is still coming used to vanish silently (review 22.10, М8). It is queued,
        // and «ещё отвечает на прошлую просьбу… сделает следом» says so, as for voice.
        input.setValue("");
        addLine(Component.literal("Ты: " + text).withStyle(ChatFormatting.WHITE));
        String action = companionAction(text);
        CompanionMemoryFlow.playerSaid(text, action);
        if (clarifyResources(text, trace)) return;
        if (CompanionIdentity.isNameQuestion(text)) { replyWithName(text, trace); return; }
        if (answerMemoryCommand(action, trace)) return;
        if (action != null && Minecraft.getInstance().getConnection() != null) {
            if (CompanionLocalCommands.cancelsPlans(action)) CompanionActionExecutor.cancelPendingPlans(trace, CompanionLocalCommands.cancelReason(text, action));
            runLocalCommand(action, trace);
            return;
        }
        if (hushInFight(text, trace)) return;
        addLine(Component.literal("...").withStyle(ChatFormatting.GRAY));
        announceThinking(OpenRouterClient.isBusy());
        long planGeneration = CompanionActionExecutor.currentPlanGeneration();

        CompanionDiagnostics.event(trace, "message_route", "route", "openrouter");
        long sessionGeneration = OpenRouterClient.generation();
        OpenRouterClient.send(text, GameContext.snapshot(text), trace).whenComplete((reply, error) -> Minecraft.getInstance().execute(() -> {
            CompanionThinking.answered();
            if (OpenRouterClient.sessionEnded(error, sessionGeneration)) {
                CompanionDiagnostics.event(trace, "reply_discarded", "reason", "session_changed");
                dropThinkingLine();
            } else if (error == null) {
                if (planGeneration != CompanionActionExecutor.currentPlanGeneration()) {
                    CompanionDiagnostics.event(trace, "reply_discarded", "reason", "generation_changed");
                    dropThinkingLine();
                    return;
                }
                replaceThinkingLine(Component.literal(companionName() + ": " + reply.text()).withStyle(ChatFormatting.AQUA));
                CompanionMemoryFlow.companionSaid("model", reply.text());
                CompanionDiagnostics.event(trace, "companion_reply", "source", "model", "text", reply.text());
                CompanionAmbientClient.replyDelivered(reply.text());
                if (!reply.actions().isEmpty()) {
                    long executionGeneration = actionGeneration(reply.actions(), planGeneration, trace);
                    CompanionActionExecutor.execute(reply.actions(), executionGeneration, trace).whenComplete((actionReport, actionError) ->
                            Minecraft.getInstance().execute(() -> {
                                if (executionGeneration == CompanionActionExecutor.currentPlanGeneration()) finishActions(reply.actions(), actionReport, actionError, false, trace);
                            }));
                }
                InworldTtsClient.speak(reply.text(), trace).exceptionally(speechError -> {
                    dev.aicompanion.AICompanionMod.LOGGER.warn("Inworld speech could not be played: {}", speechError.getMessage());
                    return null;
                });
            } else {
                CompanionDiagnostics.failure(trace, "chat_response", error);
                Throwable cause = error.getCause() == null ? error : error.getCause();
                replaceThinkingLine(Component.literal("Не удалось получить ответ: " + cause.getMessage())
                        .withStyle(ChatFormatting.RED));
            }
        }));
    }

    private static String companionAction(String text) { return CompanionLocalCommands.route(text, companionName()); }

    private static void runLocalCommand(String command, String trace) {
        CompanionDiagnostics.event(trace, "message_route", "route", "local_parser", "command", command);
        PlannedAction action = CompanionLocalCommands.action(command);
        long generation = CompanionActionExecutor.currentPlanGeneration();
        if ("build_house".equals(action.type())) addVoiceReply(CompanionDialogue.acknowledge(List.of(action)), trace, "local_acknowledgement");
        CompanionActionExecutor.execute(List.of(action), generation, trace).whenComplete((report, error) -> Minecraft.getInstance().execute(() -> {
            if (generation == CompanionActionExecutor.currentPlanGeneration()) finishActions(List.of(action), report, error, true, trace);
        }));
    }

    public static void processVoiceInBackground(java.util.concurrent.CompletableFuture<String> transcript) {
        String trace = CompanionDiagnostics.newTrace();
        CompanionDiagnostics.event(trace, "speech_transcription_pending");
        CompanionAmbientClient.transcriptionStarted();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setOverlayMessage(Component.literal("Распознаю речь..."), false);
        long heardGeneration = OpenRouterClient.generation();
        var heardWorld = minecraft.level;
        transcript.whenComplete((text, error) -> Minecraft.getInstance().execute(() -> {
            CompanionAmbientClient.transcriptionFinished();
            Minecraft current = Minecraft.getInstance();
            // Recordings overlap: a phrase heard before «Забудь всё» can be recognized after it and must not reach the cleared journal or the model.
            if (OpenRouterClient.sessionEnded(null, heardGeneration)) {
                CompanionDiagnostics.event(trace, "voice_discarded", "reason", "session_changed", "outcome", error != null ? "error" : "text");
                // A phrase said after «Забудь всё» shares the generation of one said before it and cannot be told apart, so the player is told; leaving the world stays silent.
                if (heardWorld != null && current.level == heardWorld) {
                    Component message = Component.literal("Фразу, сказанную вместе с просьбой забыть всё, я пропустил. Если она нужна — повтори.").withStyle(ChatFormatting.GRAY);
                    appendHistory(message);
                    current.gui.getChat().addMessage(message);
                }
                return;
            }
            if (error != null) {
                CompanionDiagnostics.failure(trace, "speech_transcription", error);
                Throwable cause = error.getCause() == null ? error : error.getCause();
                Component message = Component.literal("Не удалось распознать речь: " + cause.getMessage()).withStyle(ChatFormatting.RED);
                appendHistory(message);
                current.gui.getChat().addMessage(message);
            } else if (text == null || text.isBlank()) {
                CompanionDiagnostics.event(trace, "speech_transcription_empty");
                Component message = Component.literal("Не удалось распознать речь. Попробуй сказать ещё раз.").withStyle(ChatFormatting.RED);
                appendHistory(message);
                current.gui.getChat().addMessage(message);
            } else {
                CompanionDiagnostics.event(trace, "player_message", "source", "voice_transcript", "text", text);
                appendHistory(Component.literal("Ты: " + text).withStyle(ChatFormatting.WHITE));
                current.gui.getChat().addMessage(Component.literal("Ты: " + text).withStyle(ChatFormatting.WHITE));
                sendVoiceMessage(text, trace);
            }
        }));
    }

    private static void sendVoiceMessage(String text, String trace) {
        Minecraft minecraft = Minecraft.getInstance();
        CompanionAmbientClient.playerMessage();
        String action = companionAction(text);
        CompanionMemoryFlow.playerSaid(text, action);
        if (clarifyResources(text, trace)) return;
        if (CompanionIdentity.isNameQuestion(text)) { replyWithName(text, trace); return; }
        if (answerMemoryCommand(action, trace)) return;
        if (action != null && minecraft.getConnection() != null) {
            if (CompanionLocalCommands.cancelsPlans(action)) CompanionActionExecutor.cancelPendingPlans(trace, CompanionLocalCommands.cancelReason(text, action));
            runLocalCommand(action, trace);
            return;
        }
        if (hushInFight(text, trace)) return;

        long planGeneration = CompanionActionExecutor.currentPlanGeneration();
        CompanionDiagnostics.event(trace, "message_route", "route", "openrouter");
        // «Распознаю речь...» is over; «думает…» used to replace it here. Not that of another phrase or a new «Запись...».
        if (!CompanionAmbientClient.transcribing() && !AICompanionClient.recordingSpeech()) minecraft.gui.setOverlayMessage(Component.empty(), false);
        announceThinking(OpenRouterClient.isBusy());
        long sessionGeneration = OpenRouterClient.generation();
        OpenRouterClient.send(text, GameContext.snapshot(text), trace).whenComplete((reply, error) -> minecraft.execute(() -> {
            CompanionThinking.answered();
            if (OpenRouterClient.sessionEnded(error, sessionGeneration)) {
                CompanionDiagnostics.event(trace, "reply_discarded", "reason", "session_changed");
                return;
            }
            if (error != null) {
                CompanionDiagnostics.failure(trace, "voice_chat_response", error);
                Throwable cause = error.getCause() == null ? error : error.getCause();
                Component message = Component.literal("Не удалось получить ответ: " + cause.getMessage()).withStyle(ChatFormatting.RED);
                appendHistory(message);
                minecraft.gui.getChat().addMessage(message);
                return;
            }
            if (planGeneration != CompanionActionExecutor.currentPlanGeneration()) {
                CompanionDiagnostics.event(trace, "reply_discarded", "reason", "generation_changed");
                return;
            }
            addVoiceReply(reply.text(), trace, "model");
            if (!reply.actions().isEmpty()) {
                long executionGeneration = actionGeneration(reply.actions(), planGeneration, trace);
                CompanionActionExecutor.execute(reply.actions(), executionGeneration, trace).whenComplete((report, actionError) -> minecraft.execute(() -> {
                    if (executionGeneration == CompanionActionExecutor.currentPlanGeneration()) finishActions(reply.actions(), report, actionError, false, trace);
                }));
            }
        }));
    }

    /**
     * The model can take a minute or more to answer. {@link CompanionThinking} shows it until the answer comes, and when an
     * earlier one is still being answered say that this one comes next, so the player does not repeat it (game log 22.10).
     */
    private static void announceThinking(boolean earlierStillAnswering) {
        Minecraft minecraft = Minecraft.getInstance();
        CompanionThinking.asked();
        if (!earlierStillAnswering) return;
        Component line = Component.literal(companionName() + " ещё отвечает на прошлую просьбу. Эту услышал — сделает следом.").withStyle(ChatFormatting.GRAY);
        appendHistory(line);
        minecraft.gui.getChat().addMessage(line);
    }

    private static long actionGeneration(List<PlannedAction> actions, long captured, String trace) {
        if (CompanionLocalCommands.priorityOrder(actions)) {
            CompanionActionExecutor.cancelPendingPlans(trace, "priority_player_order");
            return CompanionActionExecutor.currentPlanGeneration();
        }
        return captured;
    }

    private static boolean clarifyResources(String text, String trace) {
        String value = text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        if (!value.matches("(?s).*(добудь|собери|накопай|добывай|добыть|собрать).*ресурс.*")
                || value.matches("(?s).*(дерев|бревн|камень|камня|камен|булыж|земл).*")) return false;
        String answer = "Что добыть и сколько — дерево, землю или камень?";
        if (value.matches("(?s).*(моб|монстр).*")) answer += " С монстрами рядом тоже могу разобраться.";
        CompanionDiagnostics.event(trace, "message_route", "route", "local_clarification", "reason", "unspecified_resource");
        OpenRouterClient.recordLocalExchange(text, answer); addVoiceReply(answer, trace, "clarification"); return true;
    }

    private static void addVoiceReply(String text) {
        addVoiceReply(text, CompanionDiagnostics.newTrace(), "autonomous");
    }

    private static void addVoiceReply(String text, String trace, String source) {
        CompanionMemoryFlow.companionSaid(source, text);
        CompanionDiagnostics.event(trace, "companion_reply", "source", source, "text", text);
        if ("autonomous".equals(source) || "topic".equals(source)) CompanionAmbientClient.initiativeDelivered(text);
        else CompanionAmbientClient.replyDelivered(text);
        Minecraft minecraft = Minecraft.getInstance();
        Component reply = Component.literal(companionName() + ": " + text).withStyle(ChatFormatting.AQUA);
        appendHistory(reply);
        minecraft.gui.getChat().addMessage(reply);
        InworldTtsClient.speak(text, trace).exceptionally(speechError -> {
            dev.aicompanion.AICompanionMod.LOGGER.warn("Inworld speech could not be played: {}", speechError.getMessage());
            return null;
        });
    }

    public static void autonomousReply(String text) {
        autonomousReply(text, CompanionDiagnostics.newTrace());
    }

    /** His own line before or after a deed of his own (CompanionAutonomy): his words as the server wrote them, no model request. */
    static void autonomyLine(String text) {
        String trace = CompanionDiagnostics.newTrace();
        OpenRouterClient.recordActionResult("Сам, без просьбы (режим самостоятельности): " + text);
        addVoiceReply(text, trace, "autonomy");
    }

    public static void autonomousReply(String text, String trace) {
        OpenRouterClient.recordSpokenLine(text);
        addVoiceReply(text, trace, "autonomous");
    }

    /** A topic of his own: unlike a remark on an event, it goes to the memory journal with the player's answers to it. */
    static void ownTopic(String text, String trace) {
        OpenRouterClient.recordSpokenLine(text);
        addVoiceReply(text, trace, "topic");
    }

    /** A fight is no time to chat: small talk gets a short brush-off without a model request; orders still go through. */
    private static boolean hushInFight(String text, String trace) {
        var speaker = CompanionVoiceAudio.speaker();
        if (!CompanionLocalCommands.hushed(speaker != null && speaker.fighting(), text)) return false;
        CompanionDiagnostics.event(trace, "message_route", "route", "local_fight_hush");
        OpenRouterClient.recordLocalExchange(text, CompanionLocalCommands.FIGHT_HUSH);
        addVoiceReply(CompanionLocalCommands.FIGHT_HUSH, trace, "fight_hush");
        return true;
    }

    private static boolean answerMemoryCommand(String action, String trace) {
        boolean forget = "companion memory forget".equals(action);
        if (!forget && !"companion memory show".equals(action)) return false;
        CompanionDiagnostics.event(trace, "message_route", "route", "local_memory", "command", action);
        boolean remembers = OpenRouterConfig.LONG_TERM_MEMORY.get();
        String answer;
        if (forget) {
            CompanionMemoryFlow.forget();
            answer = remembers ? "Всё, что ты мне рассказывал, я забыл. Наши общие дела и отношения остаются."
                    : "Всё, что ты мне рассказывал, я забыл. Долговременная память выключена, так что нового я не запоминаю.";
        } else if (remembers) answer = CompanionMemoryView.recall(CompanionMemoryView.current());
        else answer = "Долговременная память выключена в настройках: нового я не запоминаю и прежнее не вспоминаю.";
        addVoiceReply(answer, trace, "local_memory");
        return true;
    }

    private static void replyWithName(String userText, String trace) {
        CompanionDiagnostics.event(trace, "message_route", "route", "local_identity");
        String answer = "Я " + companionName() + ".";
        OpenRouterClient.recordLocalExchange(userText, answer);
        addVoiceReply(answer, trace, "local_identity");
    }

    private static void finishActions(List<PlannedAction> actions, String report, Throwable error, boolean local, String trace) {
        String raw = error == null ? (report == null ? "Без отчёта." : report) : "Не завершено: " + error.getMessage();
        OpenRouterClient.recordActionResult(raw);
        CompanionDiagnostics.event(trace, "execution_report", "report", raw);
        dev.aicompanion.AICompanionMod.LOGGER.debug("Companion action result: {}", raw);
        String spoken = error == null ? CompanionDialogue.summarize(actions, raw) : "Не вышло, бля. Поручение не завершено.";
        if (local && spoken.isBlank()) spoken = CompanionDialogue.acknowledge(actions);
        if (!spoken.isBlank()) addVoiceReply(spoken, trace, "engine_summary");
    }

    private void addLine(Component line) {
        appendHistory(line);
        scrollOffset = 0;
    }

    private void replaceThinkingLine(Component replacement) {
        dropThinkingLine();
        addLine(replacement);
    }

    /**
     * The oldest «...» belongs to this reply: requests are answered in order. Only the last line was checked before, so a
     * message typed while waiting («забудь всё», a second request) left a grey «...» for good (review of PR #19).
     */
    private static synchronized void dropThinkingLine() {
        for (var it = conversation.iterator(); it.hasNext(); ) {
            if (it.next().getString().equals("...")) { it.remove(); return; }
        }
    }

    private static synchronized void appendHistory(Component line) {
        conversation.add(line);
        while (conversation.size() > MAX_HISTORY_LINES) conversation.removeFirst();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This screen draws its own dim overlay and panel. Skipping Screen's default
        // background also prevents its blur pass from being drawn over the chat text.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x50000000);
        int panelWidth = Math.min(420, this.width - 32);
        int left = (this.width - panelWidth) / 2;
        int top = 62;
        int bottom = this.height - 52;
        graphics.fill(left - 8, top - 14, left + panelWidth + 8, bottom + 4, 0xEE10131A);
        graphics.drawCenteredString(font, title, this.width / 2, 14, 0xFFFFFF);

        int y = bottom - 8;
        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        for (Component line : conversation) {
            wrappedLines.addAll(font.split(line, panelWidth));
        }
        int visibleRows = Math.max(1, (bottom - top - 8) / font.lineHeight);
        int end = Math.max(0, wrappedLines.size() - scrollOffset);
        int start = Math.max(0, end - visibleRows);
        for (int i = end - 1; i >= start; i--) {
            y -= font.lineHeight;
            graphics.drawString(font, wrappedLines.get(i), left, y, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int wrappedRows = conversation.stream().mapToInt(line -> font.split(line, Math.min(420, width - 32)).size()).sum();
        int visibleRows = Math.max(1, (height - 122) / font.lineHeight);
        int maximum = Math.max(0, wrappedRows - visibleRows);
        scrollOffset = Math.max(0, Math.min(maximum, scrollOffset + (int) Math.signum(scrollY) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            sendMessage();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (input != null && input.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return input != null && input.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

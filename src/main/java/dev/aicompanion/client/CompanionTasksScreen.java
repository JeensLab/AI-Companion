package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionGoals;
import dev.aicompanion.entity.CompanionTaskBoard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** A live task journal, deliberately separate from the inventory that pauses work. */
public final class CompanionTasksScreen extends Screen {
    private record Row(String key, String title, String status, int color, List<String> details,
                       long queueId, int priority, Runnable cancel, Runnable resume) { }
    private CompanionTaskBoard.Snapshot snapshot;
    private List<Row> rows = List.of();
    private String selected = "";
    private int left, panelWidth, split, listTop, bottom, listOffset, detailOffset, ticks;
    private boolean reading;
    private boolean fullReport;
    private String notice = "";
    private Button cancel, resume, priority;

    public CompanionTasksScreen() { super(Component.literal("Задачи компаньона")); }
    @Override protected void init() {
        panelWidth = Math.min(740, width - 24); left = (width - panelWidth) / 2;
        split = left + panelWidth * 43 / 100; listTop = height < 290 ? 109 : 135; bottom = height - 38;
        rebuild();
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).key().equals(selected)) {
            if (i < listOffset || i >= listOffset + capacity()) { listOffset = i; rebuild(); }
            break;
        }
        refresh();
    }
    @Override public void tick() {
        if (++ticks % 10 == 0) { refresh(); rebuild(); }
    }
    private void refresh() {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            notice = "Задачи доступны в одиночном мире."; snapshot = null; return;
        }
        if (reading) return;
        reading = true;
        var world = minecraft.level;
        var playerId = minecraft.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            var value = player == null ? null : CompanionTaskBoard.read(player);
            minecraft.execute(() -> {
                reading = false;
                if (minecraft.screen != this || minecraft.level != world) return;
                snapshot = value;
                notice = value == null ? "Компаньон сейчас не загружен в мире." : "";
                rebuild();
            });
        });
    }
    private void control(String command) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null || snapshot == null) return;
        var shown = snapshot;
        var id = minecraft.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) CompanionTaskBoard.control(player, shown, command);
            minecraft.execute(this::refresh);
        });
    }
    private List<Row> collectRows() {
        List<Row> result = new ArrayList<>();
        for (var task : CompanionActionExecutor.tasks()) {
            List<String> details = new ArrayList<>();
            boolean running = task.status() == CompanionTaskQueue.Status.RUNNING;
            boolean waiting = task.status() == CompanionTaskQueue.Status.WAITING;
            boolean queued = task.status() == CompanionTaskQueue.Status.QUEUED;
            String state = switch (task.status()) {
                case QUEUED -> "В очереди"; case RUNNING -> "Выполняется"; case CANCELLING -> "Отменяется";
                case COMPLETED -> "Завершено"; case BLOCKED -> "Не выполнено"; case CANCELLED -> "Отменено"; case WAITING -> "Ждёт";
            };
            details.add(state + ((running || waiting) && task.step() >= 0 ? " · шаг " + (task.step() + 1) + " из " + task.steps().size() : ""));
            details.add("Приоритет: " + priorityName(task.priority()));
            details.add("");
            for (int i = 0; i < task.steps().size(); i++) details.add(((running || waiting) && i == task.step() ? "▶ " : "")
                    + (i + 1) + ". " + CompanionDialogue.describe(task.steps().get(i)));
            details.add("");
            details.add(queued ? "Повышенный приоритет выполняется раньше. Текущее поручение завершится первым."
                    : "Шаги одного поручения выполняются по порядку.");
            if (!task.report().isBlank()) {
                String summary = CompanionDialogue.summarize(task.steps(), task.report());
                if (summary.isBlank()) summary = CompanionDialogue.speakable(task.report());
                details.add(""); details.add("Результат: " + (fullReport && selected.equals("queue:" + task.id()) ? task.report() : summary));
            }
            int color = queued || waiting ? 0xE6C77C : running ? 0x75D9CB : task.status() == CompanionTaskQueue.Status.COMPLETED ? 0x91C998 : 0xB6A8A8;
            result.add(new Row("queue:" + task.id(), CompanionDialogue.describe(task.steps().getFirst()), state, color,
                    details, task.id(), queued || waiting ? task.priority() : -1,
                    running || queued || waiting ? () -> { CompanionActionExecutor.cancelTask(task.id()); rebuild(); } : null, null));
        }
        if (snapshot != null && snapshot.goalState() != null) {
            boolean outstanding = snapshot.goalState() != CompanionGoals.State.COMPLETED && snapshot.goalState() != CompanionGoals.State.CANCELLED;
            boolean paused = snapshot.goalState() == CompanionGoals.State.BLOCKED || snapshot.goalState() == CompanionGoals.State.PAUSED_PLAYER;
            Row goal = new Row("goal:" + snapshot.goal(), "Каменные кирки для нас обоих", goalState(snapshot.goalState()),
                    0x86BDE8, List.of("Сохранённая цель", snapshot.goalStatus(), "", "Далее: " + snapshot.goalNext()),
                    0, -1, outstanding ? () -> control("cancel_goal") : null, paused ? () -> control("resume_goal") : null);
            result.add(outstanding ? 0 : result.size(), goal);
        }
        if (snapshot != null && snapshot.autonomous()) result.add(new Row("autonomy", "Самостоятельные дела", snapshot.autonomyHeld() ? "На паузе" : "Порядок и запас",
                0xB7A2DE, List.of(snapshot.autonomyTask(), "", "Проверяет запасы, готовит инструменты, собирает материалы и складывает лишнее.",
                "Твои поручения выполняются первыми. Следующее самостоятельное дело выбирается по обстановке."),
                0, -1, () -> control("cancel_autonomy"), null));
        return result;
    }
    private static String goalState(CompanionGoals.State state) {
        return switch (state) {
            case ACTIVE -> "Выполняется"; case PAUSED_PLAYER -> "Отложено по команде";
            case PAUSED_COMBAT -> "Пауза: бой"; case BLOCKED -> "Нужна помощь";
            case COMPLETED -> "Завершено"; case CANCELLED -> "Отменено"; case UNSUPPORTED -> "Не удалось загрузить";
        };
    }
    private static String priorityName(int p) { return p == 2 ? "высокий" : p == 0 ? "низкий" : "обычный"; }
    private Row selection() { return rows.stream().filter(r -> r.key().equals(selected)).findFirst().orElse(null); }
    private boolean hasReport(Row row) {
        return row != null && row.queueId() > 0 && CompanionActionExecutor.tasks().stream()
                .anyMatch(task -> task.id() == row.queueId() && !task.report().isBlank());
    }
    private int capacity() { return Math.max(1, (bottom - listTop - 6) / 36); }
    private void rebuild() {
        if (font == null) return;
        rows = collectRows();
        if (selection() == null && !rows.isEmpty()) { selected = rows.getFirst().key(); detailOffset = 0; fullReport = false; }
        listOffset = Math.clamp(listOffset, 0, Math.max(0, rows.size() - capacity()));
        clearWidgets();
        CompanionNavigation.tabs(this, "Задачи", left, 31, panelWidth, this::addRenderableWidget);
        Button stop = addRenderableWidget(Button.builder(Component.literal("Прервать дело"), b -> {
            var active = CompanionActionExecutor.tasks().stream().filter(t -> t.status() == CompanionTaskQueue.Status.RUNNING).findFirst();
            if (active.isPresent()) CompanionActionExecutor.cancelTask(active.get().id());
            else if (snapshot != null && snapshot.work() > 0) control("cancel_work");
            else CompanionActionExecutor.tasks().stream().filter(t -> t.status() == CompanionTaskQueue.Status.WAITING).findFirst()
                    .ifPresent(t -> CompanionActionExecutor.cancelTask(t.id()));
            rebuild();
        }).bounds(left + panelWidth - 113, 65, 105, 20).build());
        stop.active = snapshot != null && snapshot.work() > 0 || CompanionActionExecutor.tasks().stream().anyMatch(t -> t.status() == CompanionTaskQueue.Status.RUNNING || t.status() == CompanionTaskQueue.Status.WAITING);
        stop.setTooltip(Tooltip.create(Component.literal("Прерывает текущее поручение и его оставшиеся шаги. Остальная очередь сохраняется.")));
        for (int i = listOffset; i < Math.min(rows.size(), listOffset + capacity()); i++) {
            Row row = rows.get(i); int y = listTop + (i - listOffset) * 36;
            String name = font.plainSubstrByWidth(row.title(), split - left - 22);
            Button item = addRenderableWidget(Button.builder(Component.literal(name), b -> {
                selected = row.key(); detailOffset = 0; fullReport = false; rebuild();
            }).bounds(left + 6, y, split - left - 12, 20).build());
            item.setTooltip(Tooltip.create(Component.literal(row.title())));
        }
        int detailX = split + 10, detailWidth = left + panelWidth - detailX - 8;
        int buttonWidth = (detailWidth - 6) / 2;
        Row row = selection();
        priority = addRenderableWidget(Button.builder(Component.literal(row != null && row.priority() >= 0 ? "Приоритет: " + priorityName(row.priority())
                : hasReport(row) ? fullReport ? "Краткий итог" : "Полный отчёт" : "Приоритет очереди"), b -> {
            Row current = selection();
            if (current != null && current.priority() >= 0) CompanionActionExecutor.setPriority(current.queueId(), (current.priority() + 1) % 3);
            else if (hasReport(current)) { fullReport = !fullReport; detailOffset = 0; }
            rebuild();
        }).bounds(detailX, bottom - 47, detailWidth, 20).build());
        priority.active = row != null && (row.priority() >= 0 || hasReport(row));
        priority.setTooltip(Tooltip.create(Component.literal(hasReport(row) ? "Показать или скрыть подробный журнал выполнения."
                : "Нажми для смены: обычный → высокий → низкий. Меняет порядок ожидающих поручений.")));
        cancel = addRenderableWidget(Button.builder(Component.literal("Отменить"), b -> {
            Row current = selection(); if (current != null && current.cancel() != null) current.cancel().run();
        }).bounds(detailX, bottom - 23, buttonWidth, 20).build());
        resume = addRenderableWidget(Button.builder(Component.literal("Продолжить"), b -> {
            Row current = selection(); if (current != null && current.resume() != null) current.resume().run();
        }).bounds(detailX + buttonWidth + 6, bottom - 23, buttonWidth, 20).build());
        cancel.active = row != null && row.cancel() != null; resume.active = row != null && row.resume() != null;
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose()).bounds(width / 2 - 45, height - 28, 90, 20).build());
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x90000000);
        g.fill(left - 5, 24, left + panelWidth + 5, bottom + 2, 0xF0151C27);
        g.drawCenteredString(font, snapshot == null ? title : Component.literal(snapshot.name() + " · задачи"), width / 2, 12, 0xF1F5FA);
        g.drawString(font, "СЕЙЧАС", left + 8, 57, 0x75D9CB, false);
        // Issue #32: on stream the player opened this screen to see why he was silent, and nothing here said so.
        if (CompanionThinking.waiting()) g.drawString(font, "· думает над твоей просьбой…" + CompanionThinking.seconds(), left + 12 + font.width("СЕЙЧАС"), 57, 0xE3C26A, false);
        int activityWidth = panelWidth - 138;
        g.enableScissor(left + 8, 72, left + 8 + activityWidth, listTop - 17);
        g.drawWordWrap(font, Component.literal(snapshot == null ? notice.isEmpty() ? "Ожидаю данные компаньона…" : notice : snapshot.activity()), left + 8, 72, activityWidth, 0xF1F5FA);
        g.disableScissor();
        String detail = snapshot == null ? notice : CompanionDialogue.names(snapshot.detail());
        if (height >= 290) g.drawString(font, font.plainSubstrByWidth(detail.replace('\n', ' '), panelWidth - 16), left + 8, 103, 0xA6B4C6, false);
        g.drawString(font, "ПОРУЧЕНИЯ · " + rows.size() + (rows.size() > capacity() ? " ↕" : ""), left + 8, listTop - 14, 0xC6CFDC, false);
        g.drawString(font, "ПЛАН И РЕЗУЛЬТАТ", split + 10, listTop - 14, 0xC6CFDC, false);
        g.fill(split, listTop - 16, split + 1, bottom, 0xFF39465B);
        for (int i = listOffset; i < Math.min(rows.size(), listOffset + capacity()); i++) {
            Row row = rows.get(i); int y = listTop + (i - listOffset) * 36;
            if (row.key().equals(selected)) g.fill(left + 3, y - 2, left + 5, y + 30, row.color() | 0xFF000000);
            String status = row.status() + (row.priority() >= 0 ? " · " + priorityName(row.priority()) : "");
            g.drawString(font, font.plainSubstrByWidth(status, split - left - 18), left + 9, y + 23, row.color(), false);
        }
        if (rows.isEmpty()) g.drawWordWrap(font, Component.literal("Поручений пока нет. Дай задание в чате или голосом."), left + 8, listTop + 6, split - left - 18, 0xA6B4C6);
        Row row = selection();
        if (row != null) {
            int detailWidth = left + panelWidth - split - 20;
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (String line : row.details()) {
                if (line.isEmpty()) lines.add(FormattedCharSequence.EMPTY);
                else lines.addAll(font.split(Component.literal(line), detailWidth));
            }
            int visible = Math.max(1, (bottom - 53 - listTop) / font.lineHeight);
            detailOffset = Math.clamp(detailOffset, 0, Math.max(0, lines.size() - visible));
            g.enableScissor(split + 6, listTop, left + panelWidth - 4, bottom - 51);
            for (int i = detailOffset; i < Math.min(lines.size(), detailOffset + visible); i++)
                g.drawString(font, lines.get(i), split + 10, listTop + (i - detailOffset) * font.lineHeight, 0xDCE4EE);
            g.disableScissor();
            if (lines.size() > visible) g.drawString(font, "↕ колёсико: подробности", split + 10, bottom - 59, 0x8595AC, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x < split) { listOffset += dy > 0 ? -1 : 1; rebuild(); }
        else detailOffset = Math.max(0, detailOffset + (dy > 0 ? -3 : 3));
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (AICompanionClient.interfaceKeyMatches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
}

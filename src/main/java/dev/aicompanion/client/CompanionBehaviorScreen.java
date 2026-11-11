package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CompanionBehaviorScreen extends Screen {
    private final Screen parent;
    CompanionBehaviorScreen(Screen parent) { super(Component.literal("Поведение компаньона")); this.parent = parent; }
    @Override protected void init() {
        int left = width / 2 - 150, top = Math.max(64, height / 2 - 90); // the sampling fields hold the first rows, the switches start below them
        // Sampling is free entry: models differ, so the mod sends the number as typed and lets the server judge its range.
        addSamplingField(left + 72, 30, 80, OpenRouterConfig.TEMPERATURE);
        addSamplingField(left + 200, 30, 100, OpenRouterConfig.TOP_P);
        addRenderableWidget(Button.builder(speechLabel(), b -> {
            OpenRouterConfig.AUTONOMOUS_SPEECH.set(!OpenRouterConfig.AUTONOMOUS_SPEECH.get()); OpenRouterConfig.AUTONOMOUS_SPEECH.save();
            b.setMessage(speechLabel()); CompanionPreferences.sync();
        }).bounds(left, top, 300, 20).build());
        addRenderableWidget(Button.builder(chunksLabel(), b -> {
            OpenRouterConfig.CHUNK_LOADING.set(!OpenRouterConfig.CHUNK_LOADING.get()); OpenRouterConfig.CHUNK_LOADING.save();
            b.setMessage(chunksLabel()); CompanionPreferences.sync();
        }).bounds(left, top + 24, 300, 20).build());
        addRenderableWidget(Button.builder(distanceLabel(), b -> {
            int next = (OpenRouterConfig.VOICE_DISTANCE.get() / 16 + 1) * 16;
            OpenRouterConfig.VOICE_DISTANCE.set(next > 64 ? 16 : next);
            OpenRouterConfig.VOICE_DISTANCE.save(); b.setMessage(distanceLabel());
        }).bounds(left, top + 48, 300, 20).build());
        addRenderableWidget(Button.builder(markerLabel(), b -> {
            OpenRouterConfig.COMPANION_MARKER.set(!OpenRouterConfig.COMPANION_MARKER.get()); OpenRouterConfig.COMPANION_MARKER.save();
            b.setMessage(markerLabel());
        }).bounds(left, top + 72, 300, 20).build());
        addRenderableWidget(Button.builder(autonomyLabel(CompanionVoiceAudio.speaker() != null && CompanionVoiceAudio.speaker().autonomyEnabled()), b -> {
            // Kept in the world with Oleg (off in a world that never had it), so the switch asks him, not the config file.
            var client = minecraft;
            var server = client.getSingleplayerServer();
            if (server == null || client.player == null) return;
            var playerId = client.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var companion = player == null ? null : dev.aicompanion.entity.CompanionEvents.getOwnedCompanion(player);
                if (companion == null) return;
                boolean on = !companion.autonomy().enabled();
                companion.autonomy().setEnabled(on);
                client.execute(() -> b.setMessage(autonomyLabel(on)));
            });
        }).bounds(left, top + 96, 300, 20).build());
        Button windowsVoice = addRenderableWidget(Button.builder(windowsVoiceLabel(), b -> {
            localVoiceSetting().set(!localVoiceSetting().get()); localVoiceSetting().save();
            b.setMessage(windowsVoiceLabel());
        }).bounds(left, top + 120, 300, 20).build());
        windowsVoice.setTooltip(Tooltip.create(Component.literal(
                MacOsVoice.macos() ? "Без ключа Inworld Олег говорит установленным русским голосом macOS. Если голос недоступен, появится подсказка."
                        : "Без ключа Inworld Олег говорит русским голосом Windows. Нет такого голоса — добавь русский язык с речью в параметрах Windows.")));
        addRenderableWidget(Button.builder(shortLabel(), b -> {
            OpenRouterConfig.SHORT_MURMURS.set(!OpenRouterConfig.SHORT_MURMURS.get()); OpenRouterConfig.SHORT_MURMURS.save();
            b.setMessage(shortLabel());
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(
                "Включено: свои реплики не длиннее 15 слов и без цифр, вдали от игрока он пишет их в чат без голоса. Выключено: как раньше, длинные реплики звучат только рядом."))).build());
        addRenderableWidget(Button.builder(intervalLabel(), b -> {
            int next = OpenRouterConfig.MURMUR_INTERVAL_PERCENT.get() + 50;
            OpenRouterConfig.MURMUR_INTERVAL_PERCENT.set(next > 400 ? 50 : next);
            OpenRouterConfig.MURMUR_INTERVAL_PERCENT.save(); b.setMessage(intervalLabel()); CompanionPreferences.sync();
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(
                "Насколько редко он заговаривает сам: 100% как задумано, 200% вдвое реже, 50% вдвое чаще."))).build());
        addRenderableWidget(Button.builder(guardLabel(), b -> {
            OpenRouterConfig.GUARD_NEAREST_TO_PLAYER.set(!OpenRouterConfig.GUARD_NEAREST_TO_PLAYER.get()); OpenRouterConfig.GUARD_NEAREST_TO_PLAYER.save();
            b.setMessage(guardLabel()); CompanionPreferences.sync();
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(containersLabel(), b -> {
            OpenRouterConfig.OTHER_CONTAINERS.set(!OpenRouterConfig.OTHER_CONTAINERS.get()); OpenRouterConfig.OTHER_CONTAINERS.save();
            b.setMessage(containersLabel()); CompanionPreferences.sync();
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(
                "Класть в бочки, шалкеры, печи, воронки и раздатчики и брать из них, когда названо; бочки входят в раскладку. Выключено: только сундуки."))).build());
        addRenderableWidget(Button.builder(urgentLabel(), b -> {
            OpenRouterConfig.URGENT_ORDER.set(!OpenRouterConfig.URGENT_ORDER.get()); OpenRouterConfig.URGENT_ORDER.save();
            b.setMessage(urgentLabel()); CompanionPreferences.sync();
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(
                "«Иди ко мне», «стой», «защищай» посреди добычи: он откладывает остаток и докапывает его, когда освободится. Выключено: приказ просто прерывает работу."))).build());
        addRenderableWidget(Button.builder(boatClutchLabel(), b -> {
            OpenRouterConfig.BOAT_CLUTCH.set(!OpenRouterConfig.BOAT_CLUTCH.get()); OpenRouterConfig.BOAT_CLUTCH.save();
            b.setMessage(boatClutchLabel()); CompanionPreferences.sync();
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(
                "Падение, которое ведро воды не ловит (Незер, нет ведра): он ставит под себя лодку из сумки, садится, потом ломает её и забирает. Выключено: падает как есть."))).build());
        addRenderableWidget(Button.builder(pickUpLabel(), b -> {
            OpenRouterConfig.PICK_UP_NEARBY.set(!OpenRouterConfig.PICK_UP_NEARBY.get()); OpenRouterConfig.PICK_UP_NEARBY.save();
            b.setMessage(pickUpLabel()); CompanionPreferences.sync();
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(
                "«Подбери курицу» — взять то, что уже лежит рядом, в том числе в воде и на другой высоте, а не начинать охоту или добычу. Выключено: сразу обычная охота или добыча."))).build());
        addRenderableWidget(Button.builder(pickUpRadiusLabel(), b -> {
            int next = OpenRouterConfig.PICK_UP_RADIUS.get() + 8;
            OpenRouterConfig.PICK_UP_RADIUS.set(next > 32 ? 4 : next);
            OpenRouterConfig.PICK_UP_RADIUS.save(); b.setMessage(pickUpRadiusLabel()); CompanionPreferences.sync();
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(
                "Насколько далеко он осматривает землю вокруг себя, блоков."))).build());
        addRenderableWidget(Button.builder(thinkingLabel(), b -> {
            OpenRouterConfig.THINKING.set(!OpenRouterConfig.THINKING.get()); OpenRouterConfig.THINKING.save();
            b.setMessage(thinkingLabel());
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(
                "Выключено: модель отвечает сразу — замерено 4–12 с вместо 1,5–3 мин. Включено: модель думает перед ответом, это медленнее, но иногда удачнее выбор материалов."))).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose()).bounds(width / 2 - 50, height - 35, 100, 20).build());
        addRenderableWidget(Button.builder(damageNumbersLabel(), b -> {
            OpenRouterConfig.DAMAGE_NUMBERS.set(!OpenRouterConfig.DAMAGE_NUMBERS.get()); OpenRouterConfig.DAMAGE_NUMBERS.save();
            b.setMessage(damageNumbersLabel());
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(jokesLabel(), b -> {
            OpenRouterConfig.JOKES.set(!OpenRouterConfig.JOKES.get()); OpenRouterConfig.JOKES.save();
            b.setMessage(jokesLabel());
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(
                "Короткие анекдоты без запроса к модели, только в спокойный момент. Один и тот же — не чаще раза в час."))).build());
        addRenderableWidget(Button.builder(jokeIntervalLabel(), b -> {
            int next = OpenRouterConfig.JOKE_INTERVAL_MINUTES.get() + 5;
            OpenRouterConfig.JOKE_INTERVAL_MINUTES.set(next > 60 ? 5 : next); OpenRouterConfig.JOKE_INTERVAL_MINUTES.save();
            b.setMessage(jokeIntervalLabel());
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).build());
        fitRows(top);
    }
    private Component jokesLabel() { return Component.literal("Анекдоты: " + (OpenRouterConfig.JOKES.get() ? "вкл." : "выкл.")); }
    private Component jokeIntervalLabel() {
        int minutes = OpenRouterConfig.JOKE_INTERVAL_MINUTES.get();
        return Component.literal("Пауза между анекдотами: " + minutes + "–" + (minutes * 2) + " мин.");
    }
    private Component shortLabel() { return Component.literal("Короткие реплики (до 15 слов): " + (OpenRouterConfig.SHORT_MURMURS.get() ? "вкл." : "выкл.")); }
    private Component intervalLabel() { return Component.literal("Пауза между своими репликами: " + OpenRouterConfig.MURMUR_INTERVAL_PERCENT.get() + "%"); }
    private Component guardLabel() { return Component.literal("Охрана: цель ближе к игроку: " + (OpenRouterConfig.GUARD_NEAREST_TO_PLAYER.get() ? "вкл." : "выкл.")); }
    private Component containersLabel() { return Component.literal("Бочки, шалкеры, печи и другие контейнеры: " + (OpenRouterConfig.OTHER_CONTAINERS.get() ? "вкл." : "выкл.")); }
    private static Component boatClutchLabel() { return Component.literal("Лодка при падении: " + (OpenRouterConfig.BOAT_CLUTCH.get() ? "вкл." : "выкл.")); }
    private static Component urgentLabel() { return Component.literal("Срочный приказ откладывает работу: " + (OpenRouterConfig.URGENT_ORDER.get() ? "вкл." : "выкл.")); }
    private static Component autonomyLabel(boolean on) {
        return Component.literal("Самостоятельность (порядок и запас): " + (on ? "вкл." : "выкл."));
    }
    private Component damageNumbersLabel() { return Component.literal("Цифры урона над Олегом: " + (OpenRouterConfig.DAMAGE_NUMBERS.get() ? "вкл." : "выкл.")); }
    private Component pickUpLabel() { return Component.literal("Подбирать лежащее рядом: " + (OpenRouterConfig.PICK_UP_NEARBY.get() ? "вкл." : "выкл.")); }
    private Component pickUpRadiusLabel() { return Component.literal("Радиус подбора: " + OpenRouterConfig.PICK_UP_RADIUS.get() + " блоков"); }
    private static Component thinkingLabel() { return Component.literal("Размышление модели: " + (OpenRouterConfig.THINKING.get() ? "вкл." : "выкл.")); }
    /**
     * A free-entry sampling field: any number the owner types is sent as is — dot or comma, negative too. The mod sets no
     * range of its own; a value the server rejects comes back as the server's own error. Empty means «не задавать».
     */
    private EditBox addSamplingField(int x, int y, int width, net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> setting) {
        EditBox field = addRenderableWidget(new EditBox(font, x, y, width, 20, Component.literal("")));
        field.setMaxLength(24);
        field.setHint(Component.literal("не задавать").withStyle(ChatFormatting.GRAY));
        field.setValue(setting.get() == null ? "" : setting.get());
        field.setResponder(text -> {
            setting.set(text.trim());
            setting.save();
            field.setTextColor(OpenRouterClient.samplingHint(text).isEmpty() ? 0xE0E0E0 : 0xFF5555);
        });
        field.setTextColor(OpenRouterClient.samplingHint(setting.get()).isEmpty() ? 0xE0E0E0 : 0xFF5555);
        field.setTooltip(Tooltip.create(Component.literal(
                "Значение отправляется как есть; допустимый диапазон зависит от вашей модели. Пусто — не задавать, остаётся настройка сервера модели.")));
        return field;
    }
    private Component speechLabel() { return Component.literal("Самостоятельные реплики: " + (OpenRouterConfig.AUTONOMOUS_SPEECH.get() ? "вкл." : "выкл.")); }
    private Component chunksLabel() { return Component.literal("Работа вдали от игрока: " + (OpenRouterConfig.CHUNK_LOADING.get() ? "вкл." : "выкл.")); }
    private Component markerLabel() { return Component.literal("Контур Олега и метка вдали (клавиша G): " + (OpenRouterConfig.COMPANION_MARKER.get() ? "вкл." : "выкл.")); }
    private static net.neoforged.neoforge.common.ModConfigSpec.BooleanValue localVoiceSetting() {
        return MacOsVoice.macos() ? OpenRouterConfig.MACOS_VOICE : OpenRouterConfig.WINDOWS_VOICE;
    }
    private Component windowsVoiceLabel() { return Component.literal("Голос " + (MacOsVoice.macos() ? "macOS" : "Windows") + " без ключа Inworld: " + (localVoiceSetting().get() ? "вкл." : "выкл.")); }
    private Component distanceLabel() { return Component.literal("Слышимость голоса: " + OpenRouterConfig.VOICE_DISTANCE.get() + " блоков"); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        int left = width / 2 - 150;
        graphics.drawString(font, "Температура", left, 35, 0xFFFFFF);
        graphics.drawString(font, "top_p", left + 160, 35, 0xFFFFFF);
        // A typed value that is not a number is not sent; say so visibly, the text itself stays as typed.
        String hint = OpenRouterClient.samplingHint(OpenRouterConfig.TEMPERATURE.get());
        if (!hint.isEmpty()) hint = "Температура — " + hint;
        else {
            hint = OpenRouterClient.samplingHint(OpenRouterConfig.TOP_P.get());
            if (!hint.isEmpty()) hint = "top_p — " + hint;
        }
        if (!hint.isEmpty()) graphics.drawCenteredString(font, hint, width / 2, 52, 0xFF5555);
        graphics.drawCenteredString(font, "Свои темы и редкие реплики об обстановке идут на сервер модели и в Inworld.", width / 2, height - 64, 0xB8C3D1);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }

    private static final int ROW_WIDTH = 300, ROW_HEIGHT = 20, ROW_STEP = 30, HIGHEST_ROW = 64, NOTE_FROM_BOTTOM = 64, PAGED_STEP = 24;
    /** The page of switches shown when they do not all fit above the note. */
    private int page;
    /**
     * The switches, every button as wide as a row, stand one under another in the order they were added: from the
     * usual top and 30 apart while there is room, higher up and closer (down to touching) when they would reach the
     * note. So a switch added anywhere in init needs no place of its own, and the sampling fields above keep their own
     * row, leaving about four switch rows at a GUI height of 240.
     * More than fit 24 apart go on pages, turned by a button beside «Назад».
     */
    private void fitRows(int top) {
        var all = children().stream().filter(child -> child instanceof Button button && button.getWidth() == ROW_WIDTH)
                .map(Button.class::cast).toList();
        int bottom = height - NOTE_FROM_BOTTOM - 4, perPage = Math.max(1, (bottom - HIGHEST_ROW - ROW_HEIGHT) / PAGED_STEP + 1);
        var rows = all;
        if (all.size() > perPage) {
            int pages = (all.size() + perPage - 1) / perPage, shown = page % pages, from = shown * perPage;
            rows = all.subList(from, Math.min(all.size(), from + perPage));
            for (Button row : all) row.visible = rows.contains(row);
            addRenderableWidget(Button.builder(Component.literal("Ещё (" + (shown + 1) + "/" + pages + ")"), b -> {
                page = shown + 1; rebuildWidgets();
            }).bounds(width / 2 + 54, height - 35, 100, 20).build());
        }
        int gaps = Math.max(1, rows.size() - 1);
        int step = Math.max(ROW_HEIGHT, Math.min(ROW_STEP, (bottom - HIGHEST_ROW - ROW_HEIGHT) / gaps));
        int y = Math.max(HIGHEST_ROW, Math.min(top, bottom - ROW_HEIGHT - step * gaps));
        for (Button row : rows) { row.setY(y); y += step; }
    }
}

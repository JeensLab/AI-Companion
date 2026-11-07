package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CompanionBehaviorScreen extends Screen {
    private final Screen parent;
    CompanionBehaviorScreen(Screen parent) { super(Component.literal("Поведение компаньона")); this.parent = parent; }
    @Override protected void init() {
        int left = width / 2 - 150, top = Math.max(30, height / 2 - 90); // six rows end above the note at height - 64, down to a GUI height of 240
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
            OpenRouterConfig.WINDOWS_VOICE.set(!OpenRouterConfig.WINDOWS_VOICE.get()); OpenRouterConfig.WINDOWS_VOICE.save();
            b.setMessage(windowsVoiceLabel());
        }).bounds(left, top + 120, 300, 20).build());
        windowsVoice.setTooltip(Tooltip.create(Component.literal(
                "Без ключа Inworld Олег говорит русским голосом Windows. Нет такого голоса — добавь русский язык с речью в параметрах Windows.")));
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
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose()).bounds(width / 2 - 50, height - 35, 100, 20).build());
        addRenderableWidget(Button.builder(damageNumbersLabel(), b -> {
            OpenRouterConfig.DAMAGE_NUMBERS.set(!OpenRouterConfig.DAMAGE_NUMBERS.get()); OpenRouterConfig.DAMAGE_NUMBERS.save();
            b.setMessage(damageNumbersLabel());
        }).bounds(left, top, ROW_WIDTH, ROW_HEIGHT).build());
        fitRows(top);
    }
    private Component shortLabel() { return Component.literal("Короткие реплики (до 15 слов): " + (OpenRouterConfig.SHORT_MURMURS.get() ? "вкл." : "выкл.")); }
    private Component intervalLabel() { return Component.literal("Пауза между своими репликами: " + OpenRouterConfig.MURMUR_INTERVAL_PERCENT.get() + "%"); }
    private Component guardLabel() { return Component.literal("Охрана: цель ближе к игроку: " + (OpenRouterConfig.GUARD_NEAREST_TO_PLAYER.get() ? "вкл." : "выкл.")); }
    private static Component autonomyLabel(boolean on) {
        return Component.literal("Самостоятельность (порядок и запас): " + (on ? "вкл." : "выкл."));
    }
    private Component damageNumbersLabel() { return Component.literal("Цифры урона над Олегом: " + (OpenRouterConfig.DAMAGE_NUMBERS.get() ? "вкл." : "выкл.")); }
    private Component speechLabel() { return Component.literal("Самостоятельные реплики: " + (OpenRouterConfig.AUTONOMOUS_SPEECH.get() ? "вкл." : "выкл.")); }
    private Component chunksLabel() { return Component.literal("Работа вдали от игрока: " + (OpenRouterConfig.CHUNK_LOADING.get() ? "вкл." : "выкл.")); }
    private Component markerLabel() { return Component.literal("Контур Олега и метка вдали (клавиша G): " + (OpenRouterConfig.COMPANION_MARKER.get() ? "вкл." : "выкл.")); }
    private Component windowsVoiceLabel() { return Component.literal("Голос Windows без ключа Inworld: " + (OpenRouterConfig.WINDOWS_VOICE.get() ? "вкл." : "выкл.")); }
    private Component distanceLabel() { return Component.literal("Слышимость голоса: " + OpenRouterConfig.VOICE_DISTANCE.get() + " блоков"); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, "Свои темы и редкие реплики об обстановке идут на сервер модели и в Inworld.", width / 2, height - 64, 0xB8C3D1);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }

    private static final int ROW_WIDTH = 300, ROW_HEIGHT = 20, ROW_STEP = 30, HIGHEST_ROW = 30, NOTE_FROM_BOTTOM = 64;
    /**
     * The switches, every button as wide as a row, stand one under another in the order they were added: from the
     * usual top and 30 apart while there is room, higher up and closer (down to touching) when they would reach the
     * note. So a switch added anywhere in init needs no place of its own, and seven rows fit at a GUI height of 240.
     */
    private void fitRows(int top) {
        var rows = children().stream().filter(child -> child instanceof Button button && button.getWidth() == ROW_WIDTH)
                .map(Button.class::cast).toList();
        int bottom = height - NOTE_FROM_BOTTOM - 4, gaps = Math.max(1, rows.size() - 1);
        int step = Math.max(ROW_HEIGHT, Math.min(ROW_STEP, (bottom - HIGHEST_ROW - ROW_HEIGHT) / gaps));
        int y = Math.max(HIGHEST_ROW, Math.min(top, bottom - ROW_HEIGHT - step * gaps));
        for (Button row : rows) { row.setY(y); y += step; }
    }
}

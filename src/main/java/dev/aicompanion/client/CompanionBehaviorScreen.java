package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CompanionBehaviorScreen extends Screen {
    private final Screen parent;
    CompanionBehaviorScreen(Screen parent) { super(Component.literal("Поведение компаньона")); this.parent = parent; }
    @Override protected void init() {
        int left = width / 2 - 150, top = Math.max(32, height / 2 - 85);
        addRenderableWidget(Button.builder(speechLabel(), b -> {
            OpenRouterConfig.AUTONOMOUS_SPEECH.set(!OpenRouterConfig.AUTONOMOUS_SPEECH.get()); OpenRouterConfig.AUTONOMOUS_SPEECH.save();
            b.setMessage(speechLabel()); CompanionPreferences.sync();
        }).bounds(left, top, 300, 20).build());
        addRenderableWidget(Button.builder(chunksLabel(), b -> {
            OpenRouterConfig.CHUNK_LOADING.set(!OpenRouterConfig.CHUNK_LOADING.get()); OpenRouterConfig.CHUNK_LOADING.save();
            b.setMessage(chunksLabel()); CompanionPreferences.sync();
        }).bounds(left, top + 30, 300, 20).build());
        addRenderableWidget(Button.builder(distanceLabel(), b -> {
            int next = (OpenRouterConfig.VOICE_DISTANCE.get() / 16 + 1) * 16;
            OpenRouterConfig.VOICE_DISTANCE.set(next > 64 ? 16 : next);
            OpenRouterConfig.VOICE_DISTANCE.save(); b.setMessage(distanceLabel());
        }).bounds(left, top + 60, 300, 20).build());
        addRenderableWidget(Button.builder(markerLabel(), b -> {
            OpenRouterConfig.COMPANION_MARKER.set(!OpenRouterConfig.COMPANION_MARKER.get()); OpenRouterConfig.COMPANION_MARKER.save();
            b.setMessage(markerLabel());
        }).bounds(left, top + 90, 300, 20).build());
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
        }).bounds(left, top + 120, 300, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose()).bounds(width / 2 - 50, height - 35, 100, 20).build());
    }
    private static Component autonomyLabel(boolean on) {
        return Component.literal("Самостоятельность (порядок и запас): " + (on ? "вкл." : "выкл."));
    }
    private Component speechLabel() { return Component.literal("Самостоятельные реплики: " + (OpenRouterConfig.AUTONOMOUS_SPEECH.get() ? "вкл." : "выкл.")); }
    private Component chunksLabel() { return Component.literal("Работа вдали от игрока: " + (OpenRouterConfig.CHUNK_LOADING.get() ? "вкл." : "выкл.")); }
    private Component markerLabel() { return Component.literal("Контур Олега сквозь стены (клавиша G): " + (OpenRouterConfig.COMPANION_MARKER.get() ? "вкл." : "выкл.")); }
    private Component distanceLabel() { return Component.literal("Слышимость голоса: " + OpenRouterConfig.VOICE_DISTANCE.get() + " блоков"); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(font, "Свои темы, реплики и разбор бесед используют OpenRouter и Inworld.", width / 2, height - 76, 0xB8C3D1);
        graphics.drawCenteredString(font, "Память Олега о тебе — в папке " + dev.aicompanion.CompanionSoulFiles.FOLDER + ".", width / 2, height - 64, 0xB8C3D1);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

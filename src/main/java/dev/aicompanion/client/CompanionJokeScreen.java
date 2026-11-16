package dev.aicompanion.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** File tools live on their own screen so the behavior switch pages stay readable. */
final class CompanionJokeScreen extends Screen {
    private final Screen parent;
    CompanionJokeScreen(Screen parent) { super(Component.literal("Свои анекдоты")); this.parent = parent; }
    @Override protected void init() {
        int left = width / 2 - 150;
        addRenderableWidget(Button.builder(Component.literal("Открыть файл"), b -> CompanionJokeFiles.reload(true))
                .bounds(left, 48, 148, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Перезагрузить"), b -> CompanionJokeFiles.reload(false))
                .bounds(left + 152, 48, 148, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20).build());
    }
    @Override public void tick() { CompanionAmbientClient.updateJokeFiles(); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        String description = "До 150 слов и 1500 знаков. UTF-8. В многострочном формате истории разделяются строкой ---. "
                + "Сохраните файл и нажмите «Перезагрузить»: выходить из мира не нужно. "
                + "Свои истории дополняют встроенные, интервал одинаков для обоих наборов.";
        int y = 82;
        for (var line : font.split(Component.literal(description), Math.max(100, width - 40))) {
            graphics.drawString(font, line, 20, y, 0xB8C3D1); y += 11;
        }
        y += 12;
        for (var line : font.split(Component.literal(CompanionJokeFiles.status()), Math.max(100, width - 40))) {
            graphics.drawString(font, line, 20, y, 0xFFFFFF); y += 11;
        }
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

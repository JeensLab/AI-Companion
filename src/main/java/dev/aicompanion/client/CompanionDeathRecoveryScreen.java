package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The switch and the budget of the walk back for his own things after a death. */
public final class CompanionDeathRecoveryScreen extends Screen {
    private final Screen parent;
    private EditBox attempts, deaths;

    public CompanionDeathRecoveryScreen(Screen parent) {
        super(Component.literal("Вещи после смерти"));
        this.parent = parent;
    }

    private static Component onOff() {
        return Component.literal("Возвращаться за вещами: " + (OpenRouterConfig.DEATH_RECOVERY.get() ? "вкл." : "выкл."));
    }

    @Override protected void init() {
        int left = this.width / 2 - 160, top = 44;
        addRenderableWidget(Button.builder(onOff(), b -> {
            OpenRouterConfig.DEATH_RECOVERY.set(!OpenRouterConfig.DEATH_RECOVERY.get());
            b.setMessage(onOff());
        }).bounds(left, top, 320, 20).tooltip(Tooltip.create(Component.literal(
                "Олег сам возвращается к месту смерти за своими вещами: сначала за самым дорогим, мусор оставляет лежать."))).build());
        attempts = new EditBox(this.font, left, top + 44, 120, 20, Component.literal("Попыток"));
        attempts.setValue(Integer.toString(OpenRouterConfig.DEATH_RECOVERY_ATTEMPTS.get()));
        attempts.setFilter(value -> value.matches("\\d{0,2}"));
        attempts.setTooltip(Tooltip.create(Component.literal("Сколько раз он дойдёт до одной точки, прежде чем бросить её.")));
        addRenderableWidget(attempts);
        deaths = new EditBox(this.font, left + 132, top + 44, 120, 20, Component.literal("Смертей"));
        deaths.setValue(Integer.toString(OpenRouterConfig.DEATH_RECOVERY_DEATHS.get()));
        deaths.setFilter(value -> value.matches("\\d{0,2}"));
        deaths.setTooltip(Tooltip.create(Component.literal("Сколько смертей за одну точку — по дороге к ней или на ней — и он бросает её.")));
        addRenderableWidget(deaths);
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> saveAndClose())
                .bounds(left + 200, top + 78, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose())
                .bounds(left, top + 78, 120, 20).build());
    }

    private void saveAndClose() {
        OpenRouterConfig.DEATH_RECOVERY_ATTEMPTS.set(clamp(attempts.getValue(), 5, 1, 20));
        OpenRouterConfig.DEATH_RECOVERY_DEATHS.set(clamp(deaths.getValue(), 10, 1, 50));
        OpenRouterConfig.DEATH_RECOVERY.save();
        OpenRouterConfig.DEATH_RECOVERY_ATTEMPTS.save();
        OpenRouterConfig.DEATH_RECOVERY_DEATHS.save();
        CompanionPreferences.sync();
        onClose();
    }

    private static int clamp(String text, int fallback, int low, int high) {
        try { return Math.max(low, Math.min(high, Integer.parseInt(text.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawString(this.font, "Попыток на точку", this.width / 2 - 160, 44 + 34, 0xA0A0A0);
        graphics.drawString(this.font, "Смертей на точку", this.width / 2 - 28, 44 + 34, 0xA0A0A0);
    }

    @Override public void onClose() { this.minecraft.setScreen(parent); }
}

package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * The model's own screen, opened from the first row of «Поведение»: whether it reasons before answering, the sampling
 * values, and what an answer costs in time and tokens. The config keys are the ones the behaviour screen used
 * (thinking, temperature, topP), so an existing config keeps working.
 */
final class CompanionModelScreen extends Screen {
    private final Screen parent;
    CompanionModelScreen(Screen parent) { super(Component.literal("Модель и ответы")); this.parent = parent; }

    @Override protected void init() {
        addRenderableWidget(Button.builder(thinkingLabel(), b -> {
            OpenRouterConfig.THINKING.set(!OpenRouterConfig.THINKING.get()); OpenRouterConfig.THINKING.save();
            b.setMessage(thinkingLabel());
        }).bounds(CompanionModelLayout.switchX(width), CompanionModelLayout.thinkingY(height),
                CompanionModelLayout.switchWidth(width), CompanionModelLayout.ROW_HEIGHT).tooltip(Tooltip.create(Component.literal(
                "Выключено: модель отвечает сразу — замерено 4–12 с вместо 1,5–3 мин. Включено: модель думает перед ответом, это умнее, но медленнее."))).build());
        int column = CompanionModelLayout.columnWidth(width), fieldsY = CompanionModelLayout.fieldsY(height);
        addSamplingField(CompanionModelLayout.left(width), fieldsY, column, OpenRouterConfig.TEMPERATURE);
        addSamplingField(CompanionModelLayout.left(width) + column + CompanionModelLayout.COLUMN_GAP, fieldsY, column, OpenRouterConfig.TOP_P);
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose())
                .bounds(width / 2 - 50, CompanionModelLayout.backY(height), 100, CompanionModelLayout.BACK_HEIGHT).build());
    }
    private static Component thinkingLabel() { return Component.literal("Размышление модели: " + (OpenRouterConfig.THINKING.get() ? "вкл." : "выкл.")); }
    /**
     * A free-entry sampling field: any number the owner types is sent as is — dot or comma, negative too. The mod sets no
     * range of its own; a value the server rejects comes back as the server's own error. Empty means «не задавать».
     * The rule is drawn on the screen's own line under the fields: a hover box there would cover the red «не число» line
     * and the reference block under it.
     */
    private EditBox addSamplingField(int x, int y, int width, ModConfigSpec.ConfigValue<String> setting) {
        EditBox field = addRenderableWidget(new EditBox(font, x, y, width, CompanionModelLayout.FIELD_HEIGHT, Component.literal("")));
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
                "Допустимый диапазон зависит от твоей модели: мод значение не ограничивает и отправляет как есть.")));
        return field;
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        int left = CompanionModelLayout.left(width), column = CompanionModelLayout.columnWidth(width);
        graphics.drawString(font, "Температура", left, CompanionModelLayout.labelsY(height), 0xFFFFFF);
        graphics.drawString(font, "top_p", left + column + CompanionModelLayout.COLUMN_GAP, CompanionModelLayout.labelsY(height), 0xFFFFFF);
        // A typed value that is not a number is not sent; say so visibly, the text itself stays as typed. The line wraps
        // into the two rows the layout keeps for it, so it fits a narrow GUI instead of running off its edge.
        String hint = OpenRouterClient.samplingHint(OpenRouterConfig.TEMPERATURE.get());
        boolean notANumber = !hint.isEmpty();
        if (notANumber) hint = "Температура — " + hint;
        else {
            hint = OpenRouterClient.samplingHint(OpenRouterConfig.TOP_P.get());
            notANumber = !hint.isEmpty();
            if (notANumber) hint = "top_p — " + hint;
        }
        if (!notANumber) hint = "Значение уходит как есть; допустимое решает сервер модели.";
        List<FormattedCharSequence> hintLines = font.split(Component.literal(hint), CompanionModelLayout.contentWidth(width));
        for (int i = 0; i < hintLines.size() && i < CompanionModelLayout.HINT_LINES; i++) {
            graphics.drawString(font, hintLines.get(i), left, CompanionModelLayout.hintY(height) + i * CompanionModelLayout.LINE,
                    notANumber ? 0xFF5555 : 0xB8C3D1);
        }
        int y = CompanionModelLayout.referenceTop(height);
        for (int i = 0; i < CompanionModelLayout.REFERENCE.length; i++) {
            graphics.drawString(font, CompanionModelLayout.REFERENCE[i], left, y, i == 0 ? 0xFFFFFF : 0xB8C3D1);
            y += CompanionModelLayout.LINE;
        }
    }
    /** The temperature field would take the keyboard on opening; Tab reaches the fields and the switch alike. */
    @Override protected void setInitialFocus() { }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.CompanionIdentity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Local settings page that masks both API credentials while they are edited. */
public final class CompanionSettingsScreen extends Screen {
    private static final int MAX_RADIUS = dev.aicompanion.entity.CompanionEntity.MAX_SEARCH_RADIUS;
    private final Screen parent;
    private final List<EditBox> fields = new ArrayList<>();
    private EditBox openRouterKey;
    private EditBox companionName;
    private EditBox searchRadius;
    private int firstY;
    private int rowGap;
    private EditBox model;
    private EditBox apiUrl;
    private EditBox inworldKey;
    private EditBox voiceId;
    private EditBox personality;
    private boolean showOpenRouterKey;
    private boolean showInworldKey;

    public CompanionSettingsScreen(Screen parent) {
        super(Component.literal("Настройки AI Companion"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        fields.clear();
        showOpenRouterKey = showInworldKey = false;
        addRenderableWidget(Button.builder(Component.literal("Поведение"), b -> {
            saveAndClose(); minecraft.setScreen(new CompanionBehaviorScreen(this));
        }).bounds(width - 94, 8, 88, 20).build());
        int fieldWidth = Math.min(380, this.width - 80);
        int left = (this.width - fieldWidth) / 2;
        firstY = 36;
        rowGap = Math.min(48, Math.max(28, (this.height - 110) / 6));

        companionName = addField(left, firstY, "Имя компаньона", OpenRouterConfig.COMPANION_NAME.get(), false);
        companionName.setWidth(fieldWidth / 2 - 8);
        companionName.setMaxLength(24);
        searchRadius = addField(left + fieldWidth / 2 + 8, firstY, "Дальность поиска", Integer.toString(OpenRouterConfig.SEARCH_RADIUS.get()), false);
        searchRadius.setWidth(fieldWidth / 2 - 8);
        searchRadius.setMaxLength(3);
        searchRadius.setFilter(value -> value.matches("\\d{0,3}"));
        openRouterKey = addField(left, firstY + rowGap, "Ключ API", OpenRouterConfig.API_KEY.get(), true);
        openRouterKey.setHint(Component.literal("для OpenRouter; локальному серверу не нужен").withStyle(ChatFormatting.GRAY));
        addVisibilityToggle(openRouterKey, left, firstY + rowGap, true);
        // The server shares the model's row: one more full row would push the last field under the buttons at small heights.
        model = addField(left, firstY + rowGap * 2, "openrouter/auto", OpenRouterConfig.MODEL.get(), false);
        model.setWidth(fieldWidth / 2 - 8);
        apiUrl = addField(left + fieldWidth / 2 + 8, firstY + rowGap * 2, "OpenRouter", OpenRouterConfig.API_URL.get(), false);
        apiUrl.setWidth(fieldWidth / 2 - 8);
        inworldKey = addField(left, firstY + rowGap * 3, "Inworld Basic credential", OpenRouterConfig.INWORLD_API_KEY.get(), true);
        addVisibilityToggle(inworldKey, left, firstY + rowGap * 3, false);
        voiceId = addField(left, firstY + rowGap * 4, "ID голоса Inworld", OpenRouterConfig.INWORLD_VOICE_ID.get(), false);
        personality = addField(left, firstY + rowGap * 5, "Характер компаньона", OpenRouterConfig.PERSONALITY.get(), false);
        personality.setMaxLength(2000);

        int buttonY = this.height - 42;
        this.addRenderableWidget(Button.builder(Component.literal("Назад"), button -> onClose())
                .bounds(this.width / 2 - 104, buttonY, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Сохранить"), button -> saveAndClose())
                .bounds(this.width / 2 + 4, buttonY, 100, 20).build());
    }

    private EditBox addField(int x, int y, String hint, String value, boolean secret) {
        int totalWidth = Math.min(380, this.width - 80);
        int fieldWidth = secret ? totalWidth - 70 : totalWidth;
        EditBox field = this.addRenderableWidget(new EditBox(this.font, x, y + 17,
                fieldWidth, 20, Component.literal(hint)));
        field.setHint(Component.literal(hint).withStyle(ChatFormatting.GRAY));
        field.setMaxLength(secret ? 512 : 2000);
        field.setValue(value == null ? "" : value);
        if (secret) mask(field);
        fields.add(field);
        return field;
    }

    private void addVisibilityToggle(EditBox field, int x, int y, boolean openRouter) {
        int totalWidth = Math.min(380, this.width - 80);
        this.addRenderableWidget(Button.builder(Component.literal("Показать"), button -> {
            boolean visible = openRouter ? (showOpenRouterKey = !showOpenRouterKey)
                    : (showInworldKey = !showInworldKey);
            if (visible) field.setFormatter((text, cursor) -> FormattedCharSequence.forward(text, Style.EMPTY));
            else mask(field);
            button.setMessage(Component.literal(visible ? "Скрыть" : "Показать"));
        }).bounds(x + totalWidth - 66, y + 17, 66, 20).build());
    }

    private static void mask(EditBox field) {
        field.setFormatter((text, cursor) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
    }

    private void saveAndClose() {
        OpenRouterConfig.COMPANION_NAME.set(CompanionIdentity.cleanName(companionName.getValue()));
        OpenRouterConfig.COMPANION_NAME.save();
        int radius;
        try { radius = Integer.parseInt(searchRadius.getValue()); } catch (NumberFormatException ignored) { radius = 64; }
        OpenRouterConfig.SEARCH_RADIUS.set(Math.max(16, Math.min(MAX_RADIUS, radius)));
        OpenRouterConfig.SEARCH_RADIUS.save();
        CompanionPreferences.sync();
        OpenRouterConfig.API_KEY.set(openRouterKey.getValue().trim());
        OpenRouterConfig.API_KEY.save();
        OpenRouterConfig.MODEL.set(model.getValue().trim());
        OpenRouterConfig.MODEL.save();
        OpenRouterConfig.API_URL.set(apiUrl.getValue().trim());
        OpenRouterConfig.API_URL.save();
        OpenRouterConfig.INWORLD_API_KEY.set(inworldKey.getValue().trim());
        OpenRouterConfig.INWORLD_API_KEY.save();
        OpenRouterConfig.INWORLD_VOICE_ID.set(voiceId.getValue().trim());
        OpenRouterConfig.INWORLD_VOICE_ID.save();
        OpenRouterConfig.PERSONALITY.set(personality.getValue().trim());
        OpenRouterConfig.PERSONALITY.save();
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, this.width / 2, 18, 0xFFFFFF);
        int fieldWidth = Math.min(380, this.width - 80);
        int left = (this.width - fieldWidth) / 2;
        firstY = 36;
        rowGap = Math.min(48, Math.max(28, (this.height - 110) / 6));
        String[] labels = {"Имя компаньона", "Ключ API", "Модель", "Inworld Basic credential", "ID голоса Inworld", "Характер компаньона"};
        for (int i = 0; i < labels.length; i++) {
            graphics.drawString(font, labels[i], left, firstY + i * rowGap, 0xFFFFFF);
        }
        graphics.drawString(font, "Дальность поиска (16–" + MAX_RADIUS + ")", left + fieldWidth / 2 + 8, firstY, 0xFFFFFF);
        graphics.drawString(font, "Адрес сервера модели", left + fieldWidth / 2 + 8, firstY + rowGap * 2, 0xFFFFFF);
        graphics.drawCenteredString(font, "Ключи скрыты по умолчанию. Нажми «Показать» рядом с полем; данные хранятся локально.",
                this.width / 2, this.height - 62, 0xB8C3D1);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}

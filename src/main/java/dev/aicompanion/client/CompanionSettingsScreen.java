package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.CompanionIdentity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Local settings page that masks both API credentials while they are edited.
 * <p>
 * The page is laid out from {@link CompanionSettingsLayout}: four header entries (two header rows below 418 px, so no
 * entry covers another at any auto scale), a field row every 37..48 px, and pages turned by «Ещё» when the rows do not
 * all fit above the note and the footer. A row on another page keeps its value and is saved with the rest — it is
 * hidden, never rebuilt.
 */
public final class CompanionSettingsScreen extends Screen {
    private static final int MAX_RADIUS = dev.aicompanion.entity.CompanionEntity.MAX_SEARCH_RADIUS;
    /** The labels of the field rows, by row; the second column of rows 0 and 2 has its own label in render. */
    private static final String[] LABELS = {"Имя компаньона", "Ключ API", "Модель", "Inworld Basic credential",
            "ID голоса Inworld", "Характер компаньона"};
    private final Screen parent;
    /** The widgets of every field row: the field, the second field of a split row, and its «Показать» button. */
    private final List<List<AbstractWidget>> rowWidgets = new ArrayList<>();
    private EditBox openRouterKey;
    private EditBox companionName;
    private EditBox searchRadius;
    private CompanionSettingsLayout.Header header;
    /** The page of field rows shown; kept across a resize, so a page turned by «Ещё» survives the window change. */
    private int page;
    private Button pager;
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

    private static Component memoryLabel() { return Component.literal("Память: " + (OpenRouterConfig.LONG_TERM_MEMORY.get() ? "вкл." : "выкл.")); }

    @Override
    protected void init() {
        rowWidgets.clear();
        for (int row = 0; row < CompanionSettingsLayout.ROW_COUNT; row++) rowWidgets.add(new ArrayList<>());
        showOpenRouterKey = showInworldKey = false;
        pager = null;
        header = CompanionSettingsLayout.header(this.width);
        page = Math.clamp(page, 0, CompanionSettingsLayout.pages(this.width, this.height) - 1);

        // The header entries share the top band: below 418 px they take two rows, so no entry sits inside another and none
        // runs past the right edge. The fourth place is the «Метки» page, whose own change adds its button there.
        addRenderableWidget(Button.builder(Component.literal("Поведение"), b -> {
            saveAndClose(); minecraft.setScreen(new CompanionBehaviorScreen(this));
        }).bounds(header.behavior().x(), header.behavior().y(), header.behavior().width(), header.behavior().height()).build());
        addRenderableWidget(Button.builder(Component.literal("Вещи после смерти"), b -> {
            saveAndClose(); minecraft.setScreen(new CompanionDeathRecoveryScreen(this));
        }).bounds(header.death().x(), header.death().y(), header.death().width(), header.death().height()).tooltip(Tooltip.create(Component.literal(
                "Возвращаться за своими вещами после смерти: выключатель и сколько попыток он делает."))).build());
        addRenderableWidget(Button.builder(memoryLabel(), b -> {
            OpenRouterConfig.LONG_TERM_MEMORY.set(!OpenRouterConfig.LONG_TERM_MEMORY.get()); OpenRouterConfig.LONG_TERM_MEMORY.save();
            b.setMessage(memoryLabel()); CompanionPreferences.sync();
        }).bounds(header.memory().x(), header.memory().y(), header.memory().width(), header.memory().height()).tooltip(Tooltip.create(Component.literal(
                "Помнить факты, обещания и недавний разговор между сессиями, подстраивать характер и заводить свои темы. Хранится в сохранении мира."))).build());

        int fieldWidth = Math.min(380, this.width - 80);
        int left = (this.width - fieldWidth) / 2;

        companionName = addRowField(0, left, fieldWidth / 2 - 8, "Имя компаньона", OpenRouterConfig.COMPANION_NAME.get(), false);
        companionName.setMaxLength(24);
        searchRadius = addRowField(0, left + fieldWidth / 2 + 8, fieldWidth / 2 - 8, "Дальность поиска",
                Integer.toString(OpenRouterConfig.SEARCH_RADIUS.get()), false);
        searchRadius.setMaxLength(3);
        searchRadius.setFilter(value -> value.matches("\\d{0,3}"));
        openRouterKey = addRowField(1, left, fieldWidth - 70, "Ключ API", OpenRouterConfig.API_KEY.get(), true);
        openRouterKey.setHint(Component.literal("OpenRouter или https-сервер; локальному не нужен").withStyle(ChatFormatting.GRAY));
        addRowToggle(1, left + fieldWidth - 66, true);
        // The server shares the model's row: one more full row would push the last field under the buttons at small heights.
        model = addRowField(2, left, fieldWidth / 2 - 8, "openrouter/auto", OpenRouterConfig.MODEL.get(), false);
        apiUrl = addRowField(2, left + fieldWidth / 2 + 8, fieldWidth / 2 - 8, "OpenRouter", OpenRouterConfig.API_URL.get(), false);
        inworldKey = addRowField(3, left, fieldWidth - 70, "Inworld Basic credential", OpenRouterConfig.INWORLD_API_KEY.get(), true);
        addRowToggle(3, left + fieldWidth - 66, false);
        voiceId = addRowField(4, left, fieldWidth, "ID голоса Inworld", OpenRouterConfig.INWORLD_VOICE_ID.get(), false);
        personality = addRowField(5, left, fieldWidth, "Характер компаньона", OpenRouterConfig.PERSONALITY.get(), false);
        personality.setMaxLength(2000);

        int pages = CompanionSettingsLayout.pages(this.width, this.height);
        CompanionSettingsLayout.Footer footer = CompanionSettingsLayout.footer(this.width, this.height, pages);
        addRenderableWidget(Button.builder(Component.literal("Назад"), button -> onClose())
                .bounds(footer.back().x(), footer.back().y(), footer.back().width(), footer.back().height()).build());
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), button -> saveAndClose())
                .bounds(footer.save().x(), footer.save().y(), footer.save().width(), footer.save().height()).build());
        if (footer.pager() != null) {
            pager = addRenderableWidget(Button.builder(Component.literal("Ещё"), b -> {
                setFocused(null);
                // «Ещё» turns the pages in a circle: the button never goes dead on the last one.
                showPage((page + 1) % CompanionSettingsLayout.pages(this.width, this.height));
            }).bounds(footer.pager().x(), footer.pager().y(), footer.pager().width(), footer.pager().height())
                    .tooltip(Tooltip.create(Component.literal("Остальные поля не помещаются в это окно: листай их здесь, значения сохранятся все."))).build());
        }
        showPage(page);
    }

    private EditBox addRowField(int row, int x, int width, String hint, String value, boolean secret) {
        EditBox field = new EditBox(this.font, x, 0, width, CompanionSettingsLayout.FIELD_HEIGHT, Component.literal(hint));
        field.setHint(Component.literal(hint).withStyle(ChatFormatting.GRAY));
        field.setMaxLength(secret ? 512 : 2000);
        field.setValue(value == null ? "" : value);
        if (secret) mask(field);
        addRenderableWidget(field);
        rowWidgets.get(row).add(field);
        return field;
    }

    private void addRowToggle(int row, int x, boolean openRouter) {
        Button toggle = addRenderableWidget(Button.builder(Component.literal("Показать"), button -> {
            boolean visible = openRouter ? (showOpenRouterKey = !showOpenRouterKey)
                    : (showInworldKey = !showInworldKey);
            EditBox field = openRouter ? openRouterKey : inworldKey;
            if (visible) field.setFormatter((text, cursor) -> FormattedCharSequence.forward(text, Style.EMPTY));
            else mask(field);
            button.setMessage(Component.literal(visible ? "Скрыть" : "Показать"));
        }).bounds(x, 0, 66, CompanionSettingsLayout.FIELD_HEIGHT).build());
        rowWidgets.get(row).add(toggle);
    }

    private static void mask(EditBox field) {
        field.setFormatter((text, cursor) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
    }

    /**
     * Places the rows of a page and hides the rest: a row that is not shown keeps its value in its widget, so every
     * field is saved whether it is on the page or not.
     */
    private void showPage(int page) {
        int pages = CompanionSettingsLayout.pages(this.width, this.height);
        this.page = Math.clamp(page, 0, pages - 1);
        List<CompanionSettingsLayout.Row> shown = CompanionSettingsLayout.page(this.width, this.height, this.page);
        for (int row = 0; row < rowWidgets.size(); row++) {
            int labelY = -1;
            for (CompanionSettingsLayout.Row visible : shown) if (visible.index() == row) labelY = visible.labelY();
            for (AbstractWidget widget : rowWidgets.get(row)) {
                widget.visible = labelY >= 0;
                if (labelY >= 0) widget.setY(labelY + CompanionSettingsLayout.FIELD_OFFSET);
            }
        }
        if (pager != null) pager.setMessage(Component.literal("Ещё (" + (this.page + 1) + "/" + pages + ")"));
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
        int titleWidth = font.width(title);
        int titleX = this.width / 2 - titleWidth / 2;
        // Drawn only where the header leaves it room: a narrow window would otherwise paint it over a button.
        if (titleX >= header.titleFrom() && titleX + titleWidth <= header.titleTo()) {
            graphics.drawCenteredString(font, title, this.width / 2, 18, 0xFFFFFF);
        }
        int fieldWidth = Math.min(380, this.width - 80);
        int left = (this.width - fieldWidth) / 2;
        for (CompanionSettingsLayout.Row row : CompanionSettingsLayout.page(this.width, this.height, this.page)) {
            graphics.drawString(font, LABELS[row.index()], left, row.labelY(), 0xFFFFFF);
            if (row.index() == 0)
                graphics.drawString(font, "Дальность поиска (16–" + MAX_RADIUS + ")", left + fieldWidth / 2 + 8, row.labelY(), 0xFFFFFF);
            if (row.index() == 2)
                graphics.drawString(font, "Адрес сервера модели", left + fieldWidth / 2 + 8, row.labelY(), 0xFFFFFF);
        }
        // The note has a line of its own under the fields; a narrow window cuts it instead of painting it off the edge.
        String note = "Ключи скрыты по умолчанию. Нажми «Показать» рядом с полем; данные хранятся локально.";
        graphics.drawCenteredString(font, font.plainSubstrByWidth(note, this.width - 8),
                this.width / 2, CompanionSettingsLayout.noteY(this.height), 0xB8C3D1);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}

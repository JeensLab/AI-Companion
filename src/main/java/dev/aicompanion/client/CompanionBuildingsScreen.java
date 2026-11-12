package dev.aicompanion.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aicompanion.CompanionIdentity;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionBuildPlacement;
import dev.aicompanion.entity.CompanionBuildSummary;
import dev.aicompanion.entity.CompanionBuildSummary.Who;
import dev.aicompanion.entity.CompanionVillageBuildings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The window of buildings, like the choice of a building in a colony builder: the catalogue by kind on the left, and for the one chosen its
 * size and a table of what it takes (needed / in the bag / who gets it: the companion himself, or the player who has to bring it) on the
 * right. The numbers are the server's own summary (CompanionBuildPlacement.summary), read again every second. «Строить» closes the window
 * and opens the ghost (CompanionGhostPlacement.show): looking is no order, the order is made by Enter on the ghost. «Отмена» and Esc
 * drop an order that waits for its place, if there is one. The whole window works from the keyboard: Tab between the list and the buttons, Up/Down in the list, Enter or Space on
 * the list or a focused button, PageUp/PageDown scroll a long table. Client only.
 */
public final class CompanionBuildingsScreen extends Screen {
    private static final int LIST_ROW = 14, TABLE_ROW = 18, ASK_TICKS = 20;
    /** The building chosen when the window was last left, for the next opening from the tasks. */
    private static String lastChosen;
    private static final Map<String, String> KIND_TITLES = Map.of("дом", "Дома", "кузница", "Кузницы", "храм", "Храмы");

    private String chosen;
    private int left, panelWidth, split, listTop, bottom, tableTop, tableOffset, ticks;
    private BuildingList list;
    private boolean asking, unavailable;
    private CompanionBuildSummary.Summary summary;
    private String summaryFor = "";

    /** The window opened on {@code name} (an order waits for it), or on the building chosen last when null. */
    public CompanionBuildingsScreen(String name) {
        super(Component.literal("Постройки"));
        String initial = name != null ? name : CompanionGhostPlacement.active() != null ? CompanionGhostPlacement.active() : lastChosen;
        var building = CompanionVillageBuildings.find(initial);
        chosen = (building != null ? building : CompanionVillageBuildings.all().getFirst()).name();
    }

    /** Opens the window over whatever is on the screen (the order has just been made by a phrase). */
    static void show(String name) { net.minecraft.client.Minecraft.getInstance().setScreen(new CompanionBuildingsScreen(name)); }

    @Override protected void init() {
        panelWidth = Math.min(760, width - 24); left = (width - panelWidth) / 2;
        split = left + panelWidth * 38 / 100; listTop = 40; bottom = height - 38;
        tableTop = 82;
        list = addRenderableWidget(new BuildingList(left + 6, listTop, split - left - 12, bottom - listTop - 6));
        int detailX = split + 10, detailWidth = left + panelWidth - detailX - 8, buttonWidth = (detailWidth - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("Строить"), b -> build()).bounds(detailX, bottom - 23, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> onClose()).bounds(detailX + buttonWidth + 6, bottom - 23, buttonWidth, 20).build());
        setInitialFocus(list);
        summaryFor = "";
        request();
    }

    /** «Строить»: the window closes without dropping anything, and the ghost of the chosen building opens. */
    private void build() {
        lastChosen = chosen;
        minecraft.setScreen(null);
        CompanionGhostPlacement.show(chosen);
    }

    /** «Отмена», Esc: the order is dropped on the server (world and bag were never touched), the window closes. */
    @Override public void onClose() {
        lastChosen = chosen;
        CompanionGhostPlacement.cancelOrder();
        super.onClose();
    }

    private void select(String name) {
        if (name.equals(chosen)) return;
        chosen = name; tableOffset = 0;
        request();
    }

    @Override public void tick() {
        if (++ticks % ASK_TICKS == 0 || !chosen.equals(summaryFor)) request();
    }

    /** The server's summary for the chosen building; the answer is dropped when another is chosen meanwhile. */
    private void request() {
        if (asking) return;
        String name = chosen;
        asking = true;
        boolean sent = CompanionGhostPlacement.onServer(player -> CompanionBuildPlacement.summary(player, name), answer -> {
            asking = false;
            if (minecraft.screen != this) return;
            unavailable = answer == null;
            if (name.equals(chosen)) { summary = answer; summaryFor = name; }
        });
        if (!sent) { asking = false; unavailable = true; summaryFor = name; summary = null; }
    }

    private int tableVisible() { return Math.max(1, (bottom - 52 - tableTop) / TABLE_ROW); }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x90000000);
        g.fill(left - 5, 24, left + panelWidth + 5, bottom + 2, 0xF0151C27);
        g.drawCenteredString(font, title, width / 2, 12, 0xF1F5FA);
        g.fill(split, 28, split + 1, bottom, 0xFF39465B);
        var building = CompanionVillageBuildings.find(chosen);
        int x = split + 10, right = left + panelWidth - 8, textWidth = right - x;
        g.drawString(font, font.plainSubstrByWidth(building.name(), textWidth), x, 32, 0xF1F5FA, false);
        g.drawString(font, font.plainSubstrByWidth("Размер: " + building.width() + "×" + building.depth() + "×" + building.height() + " (ширина × глубина × высота)", textWidth), x, 46, 0xA6B4C6, false);
        String who = CompanionIdentity.cleanName(OpenRouterConfig.COMPANION_NAME.get());
        // The columns from the right edge: who, in the bag, needed; the material takes the rest.
        int whoX = right - 46, haveX = whoX - 54, needX = haveX - 40, nameX = x + 20;
        g.drawString(font, "МАТЕРИАЛ", nameX, tableTop - 14, 0xC6CFDC, false);
        g.drawString(font, "надо", needX, tableTop - 14, 0xC6CFDC, false);
        g.drawString(font, "в сумке", haveX, tableTop - 14, 0xC6CFDC, false);
        g.drawString(font, "кто", whoX, tableTop - 14, 0xC6CFDC, false);
        g.fill(x, tableTop - 3, right, tableTop - 2, 0xFF39465B);
        boolean shown = summary != null && chosen.equals(summaryFor);
        if (!shown) {
            g.drawWordWrap(font, Component.literal(unavailable && chosen.equals(summaryFor) ? who + " сейчас не загружен в мир, список материалов недоступен." : "Считаю материалы…"), x, tableTop + 4, textWidth, 0xA6B4C6);
        } else {
            var rows = summary.rows();
            int visible = tableVisible();
            tableOffset = Math.clamp(tableOffset, 0, Math.max(0, rows.size() - visible));
            for (int i = tableOffset; i < Math.min(rows.size(), tableOffset + visible); i++) {
                var row = rows.get(i);
                int y = tableTop + (i - tableOffset) * TABLE_ROW;
                int color = switch (row.who()) { case COVERED -> 0xFF6F7B8C; case COMPANION -> 0xFFDCE4EE; case PLAYER -> 0xFFF2C46B; };
                // What the player has to bring stands out; what the bag covers is dimmed with a check.
                if (row.who() == Who.PLAYER) g.fill(x - 2, y - 1, right, y + TABLE_ROW - 2, 0x40F2C46B);
                g.renderItem(new ItemStack(row.item()), x, y - 1);
                g.drawString(font, font.plainSubstrByWidth(new ItemStack(row.item()).getHoverName().getString(), needX - nameX - 4), nameX, y + 4, color, false);
                g.drawString(font, String.valueOf(row.need()), needX, y + 4, color, false);
                g.drawString(font, String.valueOf(row.have()), haveX, y + 4, color, false);
                g.drawString(font, row.who() == Who.COVERED ? "✔" : row.who() == Who.PLAYER ? "ты" : font.plainSubstrByWidth(who, right - whoX), whoX, y + 4,
                        row.who() == Who.COVERED ? 0xFF7FBF86 : color, false);
            }
            if (rows.size() > visible) g.drawString(font, "↕ PgUp/PgDn или колёсико: ещё материалы", x, bottom - 46, 0x8595AC, false);
            else g.drawString(font, font.plainSubstrByWidth("✔ хватает · " + who + " — добудет сам · ты — принеси", textWidth), x, bottom - 46, 0x8595AC, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN) {
            tableOffset = Math.max(0, tableOffset + (key == InputConstants.KEY_PAGEUP ? -1 : 1) * Math.max(1, tableVisible() - 1));
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x >= split) { tableOffset = Math.max(0, tableOffset + (dy > 0 ? -1 : 1)); return true; }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override public boolean isPauseScreen() { return false; }

    /** The catalogue by kind: a heading, then its buildings with their sizes; the chosen one is highlighted. One focusable widget, so Tab skips over the rows. */
    private final class BuildingList extends AbstractWidget {
        private record Line(String text, CompanionVillageBuildings.Building building) { }
        private final List<Line> lines = new ArrayList<>();
        private int offset;

        BuildingList(int x, int y, int w, int h) {
            super(x, y, w, h, Component.literal("Список построек"));
            for (String kind : CompanionVillageBuildings.kinds()) {
                var of = CompanionVillageBuildings.all().stream().filter(b -> b.kind().equals(kind)).toList();
                if (of.isEmpty()) continue;
                lines.add(new Line(KIND_TITLES.getOrDefault(kind, kind.substring(0, 1).toUpperCase(Locale.ROOT) + kind.substring(1)), null));
                for (var building : of) lines.add(new Line(building.name(), building));
            }
            reveal(indexOf(chosen));
        }

        private int shown() { return Math.max(1, height / LIST_ROW); }
        private int indexOf(String name) {
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).building() != null && lines.get(i).building().name().equals(name)) return i;
            return 0;
        }
        /** Scrolls so that line {@code index} is seen, and its heading with it when it is the first of its kind. */
        private void reveal(int index) {
            int top = index > 0 && lines.get(index - 1).building() == null ? index - 1 : index;
            if (top < offset) offset = top;
            if (index >= offset + shown()) offset = index - shown() + 1;
            offset = Math.clamp(offset, 0, Math.max(0, lines.size() - shown()));
        }
        /** One building up or down (headings are skipped); a longer step goes to the first or the last. */
        private void move(int step) {
            int index = indexOf(chosen), target = index, direction = Integer.signum(step);
            for (int i = index + direction; i >= 0 && i < lines.size(); i += direction) {
                if (lines.get(i).building() == null) continue;
                target = i;
                if (Math.abs(step) == 1) break;
            }
            if (target == index) return;
            select(lines.get(target).building().name());
            reveal(target);
        }

        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(getX(), getY(), getX() + width, getY() + height, 0x40000000);
            int end = Math.min(lines.size(), offset + shown());
            for (int i = offset; i < end; i++) {
                var line = lines.get(i);
                int y = getY() + (i - offset) * LIST_ROW;
                if (line.building() == null) { g.drawString(font, line.text().toUpperCase(Locale.ROOT), getX() + 4, y + 3, 0x75D9CB, false); continue; }
                boolean selected = line.building().name().equals(chosen);
                if (selected) { g.fill(getX(), y, getX() + width, y + LIST_ROW, 0x80406080); g.fill(getX(), y, getX() + 2, y + LIST_ROW, 0xFF86BDE8); }
                String size = line.building().width() + "×" + line.building().depth() + "×" + line.building().height();
                int sizeWidth = font.width(size);
                g.drawString(font, font.plainSubstrByWidth(line.text(), width - sizeWidth - 18), getX() + 8, y + 3, selected ? 0xFFFFFFFF : 0xFFC6CFDC, false);
                g.drawString(font, size, getX() + width - sizeWidth - 4, y + 3, 0xFF8595AC, false);
            }
            if (lines.size() > shown()) {
                int barHeight = Math.max(8, height * shown() / lines.size());
                int barTop = getY() + (height - barHeight) * offset / Math.max(1, lines.size() - shown());
                g.fill(getX() + width - 2, barTop, getX() + width, barTop + barHeight, 0xFF8595AC);
            }
            if (isFocused()) g.renderOutline(getX() - 1, getY() - 1, width + 2, height + 2, 0xFFE6EDF7);
        }

        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (!active || !visible) return false;
            if (key == InputConstants.KEY_UP) { move(-1); return true; }
            if (key == InputConstants.KEY_DOWN) { move(1); return true; }
            if (key == InputConstants.KEY_HOME) { move(-lines.size()); return true; }
            if (key == InputConstants.KEY_END) { move(lines.size()); return true; }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) { build(); return true; }
            return false;
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            int index = offset + (int) ((mouseY - getY()) / LIST_ROW);
            if (index < lines.size() && lines.get(index).building() != null) select(lines.get(index).building().name());
            return true;
        }

        @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            offset = Math.clamp(offset + (scrollY > 0 ? -1 : 1), 0, Math.max(0, lines.size() - shown()));
            return true;
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { output.add(NarratedElementType.TITLE, getMessage()); }
    }
}

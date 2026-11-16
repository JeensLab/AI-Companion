package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionMarkerBoard;
import dev.aicompanion.entity.CompanionMarkerNames;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The named places the player asked the companion to remember: the switch, the list, renaming, deleting and coordinates. */
public final class CompanionMarkersScreen extends Screen {
    private static final int ROW = 20;
    /** The column every widget of this screen shares: the list, the fields and the footer sit inside it. */
    private static final int PANEL = 320;
    /** The first control row, under the title. */
    private static final int TOP = 32;
    /** The distance between control rows: a field and the gap under it, so nothing of one row reaches the next. */
    private static final int PITCH = 26;
    private final Screen parent;
    private EditBox name, x, y, z;
    private final List<Button> rowButtons = new ArrayList<>();
    private List<CompanionMarkerBoard.Place> places = List.of();
    private UUID selected;
    private String notice = "";
    private int offset, listTop, listBottom, ticks;
    private boolean reading;
    /** A refresh asked for while a read was in flight: it runs once that read is done, never lost. */
    private boolean refreshQueued;

    public CompanionMarkersScreen(Screen parent) {
        super(Component.literal("Метки на карте"));
        this.parent = parent;
    }

    private static Component onOff() {
        return Component.literal("Метки на карте: " + (OpenRouterConfig.MARKERS.get() ? "вкл." : "выкл."));
    }

    @Override protected void init() {
        int left = this.width / 2 - PANEL / 2, top = TOP;
        rowButtons.clear();
        addRenderableWidget(Button.builder(onOff(), b -> {
            OpenRouterConfig.MARKERS.set(!OpenRouterConfig.MARKERS.get());
            OpenRouterConfig.MARKERS.save();
            b.setMessage(onOff());
            CompanionPreferences.sync();
        }).bounds(left, top, PANEL, 20).tooltip(Tooltip.create(Component.literal(
                "Выключено: Олег не узнаёт названия меток и не ходит к ним; список здесь остаётся и включается обратно."))).build());
        name = new EditBox(this.font, left, top + PITCH, 200, 20, Component.literal("Название"));
        name.setHint(Component.literal("Название метки, до 24 символов").withStyle(net.minecraft.ChatFormatting.GRAY));
        name.setMaxLength(24);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("Добавить здесь"), b -> addHere())
                .bounds(left + 210, top + PITCH, 110, 20).tooltip(Tooltip.create(Component.literal(
                        "Запомнить место, где стоит игрок, под названием из поля слева."))).build());
        x = coordinate(left, top + PITCH * 2, "X", "Координата X метки.");
        y = coordinate(left + 92, top + PITCH * 2, "Y", "Высота метки.");
        z = coordinate(left + 184, top + PITCH * 2, "Z", "Координата Z метки.");
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> saveSelected())
                .bounds(left, top + PITCH * 3, 150, 20).tooltip(Tooltip.create(Component.literal(
                        "Применить новое название и координаты к выбранной метке. Координаты ограничены границей мира и высотой постройки."))).build());
        addRenderableWidget(Button.builder(Component.literal("Удалить выбранную"), b -> deleteSelected())
                .bounds(left + 170, top + PITCH * 3, 150, 20).build());
        // The heading, the notice line and the list follow the controls; the list stops above the footer button.
        listTop = top + 132;
        listBottom = Math.max(listTop + ROW, this.height - 34);
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
        refresh();
        // A resize clears every widget and the read may find the same list, so the rows are rebuilt from it here;
        // otherwise the markers stay without their «Изменить»/«Удалить» buttons.
        rebuildRows();
    }

    private EditBox coordinate(int x, int y, String hint, String description) {
        EditBox field = new EditBox(this.font, x, y, 80, 20, Component.literal(hint));
        field.setHint(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.GRAY));
        field.setFilter(value -> value.matches("-?\\d{0,9}"));
        field.setTooltip(Tooltip.create(Component.literal(description)));
        addRenderableWidget(field);
        return field;
    }

    @Override public void tick() {
        if (++ticks % 10 == 0) refresh();
    }

    /** The list comes from the server, which keeps the markers; the screen only draws it and sends the changes. */
    private void refresh() {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            notice = "Список меток доступен в одиночном мире.";
            setPlaces(List.of());
            return;
        }
        if (reading) { refreshQueued = true; return; }
        reading = true;
        var playerId = minecraft.player.getUUID();
        var world = minecraft.level;
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            var value = player == null ? List.<CompanionMarkerBoard.Place>of() : CompanionMarkerBoard.places(player);
            minecraft.execute(() -> {
                reading = false;
                if (minecraft.screen != this || minecraft.level != world) { refreshQueued = false; return; }
                if (refreshQueued) { refreshQueued = false; refresh(); return; }
                setPlaces(value);
            });
        });
    }

    private void setPlaces(List<CompanionMarkerBoard.Place> now) {
        if (now.equals(places)) return;
        places = now;
        if (selected != null && places.stream().noneMatch(place -> place.id().equals(selected))) selected = null;
        offset = Math.clamp(offset, 0, Math.max(0, places.size() - capacity()));
        rebuildRows();
    }

    private void rebuildRows() {
        for (Button button : rowButtons) removeWidget(button);
        rowButtons.clear();
        // The window may have grown since the last scroll: the offset follows the room the list has now.
        offset = Math.clamp(offset, 0, Math.max(0, places.size() - capacity()));
        int left = this.width / 2 - PANEL / 2;
        for (int index = offset; index < Math.min(places.size(), offset + capacity()); index++) {
            CompanionMarkerBoard.Place place = places.get(index);
            int row = listTop + (index - offset) * ROW;
            Button edit = addRenderableWidget(Button.builder(Component.literal("Изменить"), b -> select(place))
                    .bounds(left + 180, row + 2, 70, 16).build());
            edit.setTooltip(Tooltip.create(Component.literal("Взять название и координаты этой метки в поля выше.")));
            Button forget = addRenderableWidget(Button.builder(Component.literal("Удалить"), b -> forget(place.id()))
                    .bounds(left + 254, row + 2, 60, 16).build());
            forget.setTooltip(Tooltip.create(Component.literal("Забыть эту метку; соседние остаются.")));
            rowButtons.add(edit);
            rowButtons.add(forget);
        }
    }

    private void select(CompanionMarkerBoard.Place place) {
        selected = place.id();
        name.setValue(place.name());
        x.setValue(Integer.toString(place.x()));
        y.setValue(Integer.toString(place.y()));
        z.setValue(Integer.toString(place.z()));
        notice = "Выбрана метка «" + place.name() + "».";
    }

    private void addHere() {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) { notice = "Список меток доступен в одиночном мире."; return; }
        String wanted = name.getValue().trim();
        String refusal = CompanionMarkerNames.refusal(wanted);
        if (!refusal.isEmpty()) { notice = refusal; return; }
        var playerId = minecraft.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            // The board answers the cause itself: a name it refused is not «тебя нет на сервере».
            CompanionMarkerBoard.AddResult added = CompanionMarkerBoard.addHere(player, wanted);
            minecraft.execute(() -> {
                notice = added.message();
                refresh();
            });
        });
    }

    private void saveSelected() {
        if (selected == null) { notice = "Сначала выбери метку кнопкой «Изменить»."; return; }
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) { notice = "Список меток доступен в одиночном мире."; return; }
        var playerId = minecraft.player.getUUID();
        UUID id = selected;
        String wanted = name.getValue().trim();
        int px = number(x.getValue()), py = number(y.getValue()), pz = number(z.getValue());
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            // The board answers with the cause: a vanished marker, a name that did not fit or a point not saved.
            CompanionMarkerBoard.Saved saved = CompanionMarkerBoard.save(player, id, wanted, px, py, pz);
            minecraft.execute(() -> {
                notice = saved.message();
                refresh();
            });
        });
    }

    private void deleteSelected() {
        if (selected == null) { notice = "Сначала выбери метку кнопкой «Изменить»."; return; }
        forget(selected);
    }

    /** Deleting one marker on the server: the list he reads comes back without it. */
    private void forget(UUID markerId) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) return;
        var playerId = minecraft.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            boolean forgotten = player != null && CompanionMarkerBoard.forget(player, markerId);
            minecraft.execute(() -> {
                notice = forgotten ? "Метка забыта." : "Такой метки уже нет.";
                if (selected != null && selected.equals(markerId)) selected = null;
                refresh();
            });
        });
    }

    private static int number(String text) {
        try { return Integer.parseInt(text.trim()); } catch (NumberFormatException ignored) { return 0; }
    }

    private int capacity() { return Math.max(1, (listBottom - listTop) / ROW); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        int left = this.width / 2 - PANEL / 2;
        graphics.drawString(this.font, "Места, которые он помнит", left, listTop - 26, 0xC6CFDC);
        if (places.size() > capacity()) graphics.drawString(this.font, "↕ колёсико", left + 250, listTop - 26, 0x8595AC);
        // The last answer has a line of its own above the list, so it never covers a marker row.
        if (!notice.isEmpty()) graphics.drawString(this.font, this.font.plainSubstrByWidth(notice, PANEL - 8), left, listTop - 12, 0xA6B4C6);
        graphics.enableScissor(left, listTop, left + PANEL, listBottom);
        if (places.isEmpty() && notice.isEmpty()) graphics.drawString(this.font,
                this.font.plainSubstrByWidth("Пока меток нет: скажи «запомни это место, дача» или добавь здесь.", PANEL - 8),
                left + 4, listTop + 5, 0xA6B4C6);
        for (int index = offset; index < Math.min(places.size(), offset + capacity()); index++) {
            CompanionMarkerBoard.Place place = places.get(index);
            String label = (place.id().equals(selected) ? "▶ " : "") + place.name() + " — " + place.world() + " " + place.x() + ", " + place.y() + ", " + place.z();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label, 172), left + 4, listTop + (index - offset) * ROW + 6, 0xF1F5FA);
        }
        graphics.disableScissor();
    }

    /**
     * The wheel scrolls the list only under the panel and only while a marker is out of sight; everywhere else the
     * event goes on to the screen below instead of being swallowed.
     */
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int left = this.width / 2 - PANEL / 2;
        int maximum = Math.max(0, places.size() - capacity());
        if (x < left || x >= left + PANEL || y < listTop || y > listBottom || maximum == 0) return super.mouseScrolled(x, y, dx, dy);
        int next = Math.clamp(offset + (dy > 0 ? -1 : 1), 0, maximum);
        if (next == offset) return super.mouseScrolled(x, y, dx, dy);
        offset = next;
        rebuildRows();
        return true;
    }

    @Override public void onClose() { this.minecraft.setScreen(parent); }
}

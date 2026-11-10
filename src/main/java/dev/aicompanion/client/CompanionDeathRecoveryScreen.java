package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionDeathBoard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The switch, the budget of the walk back for his own things after a death, and the places he still remembers. */
public final class CompanionDeathRecoveryScreen extends Screen {
    private static final int ROW = 20;
    private final Screen parent;
    private EditBox attempts, deaths;
    private final List<Button> forgetButtons = new ArrayList<>();
    private List<CompanionDeathBoard.Place> places = List.of();
    private String notice = "";
    private int offset, listTop, listBottom, ticks;
    private boolean reading;
    /** A refresh asked for while a read was in flight: it runs once that read is done, never lost. */
    private boolean refreshQueued;

    public CompanionDeathRecoveryScreen(Screen parent) {
        super(Component.literal("Вещи после смерти"));
        this.parent = parent;
    }

    private static Component onOff() {
        return Component.literal("Возвращаться за вещами: " + (OpenRouterConfig.DEATH_RECOVERY.get() ? "вкл." : "выкл."));
    }

    @Override protected void init() {
        int left = this.width / 2 - 160, top = 44;
        forgetButtons.clear();
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
        // The list of what he still remembers: numbered as he says it, dearest first.
        listTop = top + 114;
        listBottom = Math.max(listTop + ROW, this.height - 24);
        refresh();
    }

    @Override public void tick() {
        if (++ticks % 10 == 0) refresh();
    }

    /** The list comes from the server, which keeps what he remembers; the screen only draws it. */
    private void refresh() {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            notice = "Список мест доступен в одиночном мире.";
            setPlaces(List.of());
            return;
        }
        // A read is already in flight: ask for another one rather than dropping this request on the floor.
        if (reading) { refreshQueued = true; return; }
        reading = true;
        var playerId = minecraft.player.getUUID();
        var world = minecraft.level;
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            var value = player == null ? List.<CompanionDeathBoard.Place>of() : CompanionDeathBoard.places(player);
            minecraft.execute(() -> {
                reading = false;
                if (minecraft.screen != this || minecraft.level != world) { refreshQueued = false; return; }
                // A refresh queued meanwhile is the newer truth: its own read is started instead of showing this one,
                // which may already be behind a delete.
                if (refreshQueued) { refreshQueued = false; refresh(); return; }
                notice = "";
                setPlaces(value);
            });
        });
    }

    private void setPlaces(List<CompanionDeathBoard.Place> now) {
        if (now.equals(places)) return;
        places = now;
        offset = Math.clamp(offset, 0, Math.max(0, places.size() - capacity()));
        rebuildRows();
    }

    private void rebuildRows() {
        for (Button button : forgetButtons) removeWidget(button);
        forgetButtons.clear();
        int left = this.width / 2 - 160;
        for (int index = offset; index < Math.min(places.size(), offset + capacity()); index++) {
            CompanionDeathBoard.Place place = places.get(index);
            int y = listTop + (index - offset) * ROW;
            Button forget = addRenderableWidget(Button.builder(Component.literal("Забыть"), b -> forget(place.id()))
                    .bounds(left + 262, y + 2, 58, 16).build());
            forget.setTooltip(Tooltip.create(Component.literal(
                    "Забыть это место. В списке только то, что он ещё помнит: забранное или забытое место из него исчезает.")));
            forgetButtons.add(forget);
        }
    }

    /** Forgetting one place on the server: the list he reads comes back without it. */
    private void forget(UUID spotId) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) return;
        var playerId = minecraft.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            if (player != null) CompanionDeathBoard.forget(player, spotId);
            minecraft.execute(this::refresh);
        });
    }

    private int capacity() { return Math.max(1, (listBottom - listTop) / ROW); }

    private void save() {
        OpenRouterConfig.DEATH_RECOVERY_ATTEMPTS.set(clamp(attempts.getValue(), 5, 1, 20));
        OpenRouterConfig.DEATH_RECOVERY_DEATHS.set(clamp(deaths.getValue(), 10, 1, 50));
        OpenRouterConfig.DEATH_RECOVERY.save();
        OpenRouterConfig.DEATH_RECOVERY_ATTEMPTS.save();
        OpenRouterConfig.DEATH_RECOVERY_DEATHS.save();
        CompanionPreferences.sync();
    }

    private void saveAndClose() { save(); onClose(); }

    private static int clamp(String text, int fallback, int low, int high) {
        try { return Math.max(low, Math.min(high, Integer.parseInt(text.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawString(this.font, "Попыток на точку", this.width / 2 - 160, 44 + 34, 0xA0A0A0);
        graphics.drawString(this.font, "Смертей на точку", this.width / 2 - 28, 44 + 34, 0xA0A0A0);
        int left = this.width / 2 - 160;
        graphics.drawString(this.font, "Места, которые он ещё помнит", left, listTop - 12, 0xC6CFDC);
        if (places.size() > capacity()) graphics.drawString(this.font, "↕ колёсико", left + 250, listTop - 12, 0x8595AC);
        graphics.enableScissor(left, listTop, left + 320, listBottom);
        if (!notice.isEmpty()) graphics.drawString(this.font, notice, left + 4, listTop + 5, 0xA6B4C6);
        else if (places.isEmpty()) graphics.drawString(this.font, "Пока он ничего не помнит: мест после смерти нет.", left + 4, listTop + 5, 0xA6B4C6);
        for (int index = offset; index < Math.min(places.size(), offset + capacity()); index++) {
            CompanionDeathBoard.Place place = places.get(index);
            String label = "№" + place.number() + " — " + place.world() + " " + place.x() + ", " + place.y() + ", " + place.z();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label, 252), left + 4, listTop + (index - offset) * ROW + 6, 0xF1F5FA);
        }
        graphics.disableScissor();
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (y >= listTop && y <= listBottom && places.size() > capacity()) {
            offset = Math.clamp(offset + (dy > 0 ? -1 : 1), 0, Math.max(0, places.size() - capacity()));
            rebuildRows();
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override public void onClose() { save(); this.minecraft.setScreen(parent); }
}

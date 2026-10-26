package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.inventory.CompanionMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import java.util.function.Consumer;

final class CompanionNavigation {
    private CompanionNavigation() { }
    static void tabs(Screen parent, String selected, int x, int y, int width, Consumer<Button> add) {
        String[] labels = selected.equals("Инвентарь") ? new String[]{"Задачи", "Чат"}
                : new String[]{"Задачи", "Чат", "Инвентарь", "Поведение"};
        int tabWidth = (width - (labels.length - 1) * 3) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            Button button = Button.builder(Component.literal(label), b -> {
                switch (label) {
                    case "Задачи" -> show(new CompanionTasksScreen());
                    case "Чат" -> show(new CompanionChatScreen());
                    case "Инвентарь" -> inventory();
                    case "Поведение" -> show(new CompanionBehaviorScreen(parent instanceof CompanionInventoryScreen ? new CompanionTasksScreen() : parent));
                }
            }).bounds(x + i * (tabWidth + 3), y, tabWidth, 20).build();
            button.active = !label.equals(selected);
            add.accept(button);
        }
    }
    static void show(Screen screen) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        mc.setScreen(screen);
    }
    private static void inventory() {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) { notice("Инвентарь доступен в одиночном мире."); return; }
        var id = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            var c = player == null ? null : CompanionEvents.getOwnedCompanion(player);
            if (c == null || c.level() != player.level() || !c.stillValid(player)) {
                mc.execute(() -> notice("Для обмена вещами подойди к компаньону ближе чем на 8 блоков."));
                return;
            }
            player.openMenu(new SimpleMenuProvider((menuId, bag, ignored) -> new CompanionMenu(menuId, bag, c),
                    c.getName()), buffer -> buffer.writeVarInt(c.getId()));
        });
    }
    private static void notice(String message) { Minecraft.getInstance().gui.setOverlayMessage(Component.literal(message), false); }
}

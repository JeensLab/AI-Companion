package dev.aicompanion.client;

import dev.aicompanion.CompanionHealth;
import dev.aicompanion.inventory.CompanionMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact gear, bag and player inventory, with a live preview of the actual equipped companion. */
public final class CompanionInventoryScreen extends AbstractContainerScreen<CompanionMenu> {
    public CompanionInventoryScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 248;
        inventoryLabelX = 12;
        inventoryLabelY = 152;
        titleLabelX = 12;
    }

    @Override protected void init() {
        super.init();
        CompanionNavigation.tabs(this, "Инвентарь", leftPos + 12, topPos + 56, 172, this::addRenderableWidget);
        var sort = new SortButton(leftPos + 110, topPos + 77, 12, button -> sortBag(descending));
        sort.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Отсортировать инвентарь Олега")));
        addRenderableWidget(sort);
    }

    /** Three shortening bars: a sort mark drawn by hand, so no font glyph is needed. */
    private final class SortButton extends Button {
        private SortButton(int x, int y, int size, OnPress onPress) {
            super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            int color = active ? 0xFFF1F5FA : 0xFF8595AC;
            int left = getX() + 3, top = getY() + 3;
            // Bars shorten towards the next order: down for the usual one, up for the reversed one.
            int[] widths = descending ? new int[]{3, 4, 6} : new int[]{6, 4, 3};
            for (int row = 0; row < widths.length; row++)
                graphics.fill(left, top + row * 3, left + widths[row], top + row * 3 + 1, color);
        }
    }

    /** The direction the next press uses: every press flips it, and the button shows which way it will go. */
    private boolean descending;

    /** Tidies his bag on the server thread: the screen holds the client copy, which the server would overwrite. */
    private void sortBag(boolean reverse) {
        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();
        var player = minecraft.player;
        if (server == null || player == null) return;
        server.execute(() -> {
            var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (serverPlayer == null || !(serverPlayer.containerMenu instanceof CompanionMenu open)) return;
            var companion = open.getCompanion();
            if (companion != null) companion.sortStoredItems(reverse);
        });
        descending = !descending;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, 0xFF555F70);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF161C28);
        graphics.fill(x + 193, y + 19, x + 289, y + 143, 0xFF10151E);
        for (var slot : menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF566174);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF252E3D);
        }
        if (menu.getCompanion() != null) {
            var companion = menu.getCompanion();
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 199, y + 48, x + 283, y + 137,
                    40, 0.0F, mouseX, mouseY, companion);
            // Health bar with the armor strip laid over it: wider, thicker and semi-transparent, so the health stays visible
            // behind it and the armor reads as lying on top.
            int barX = x + 199, barY = y + 22, barWidth = 84, barHeight = 8;
            graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF566174);
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF10151E);
            graphics.fill(barX, barY, barX + Math.round(barWidth * CompanionHealth.share(companion.getHealth(), companion.getMaxHealth())),
                    barY + barHeight, 0xFF3FBF3F);
            int armorMax = CompanionHealth.ARMOR_BAR_CEILING, armor = companion.getArmorValue();
            int armorX = barX - 2, armorY = barY - 2, armorWidth = barWidth + 4, armorHeight = barHeight + 4;
            graphics.fill(armorX, armorY, armorX + Math.round(armorWidth * CompanionHealth.share(armor, armorMax)),
                    armorY + armorHeight, 0x66C8CDD4);
            drawBarText(graphics, CompanionHealth.health(companion.getHealth(), companion.getMaxHealth()),
                    barX, barWidth, barY);
            // The name sits right above the preview, clear of the bars.
            var name = companion.getName();
            graphics.drawString(font, name, (int) (x + 241 - font.width(name) / 2.0F), y + 37, 0xFFFFFFFF, true);
        }
    }

    /** Numbers on a bar: drawn small and centred, so «XX/MAX» fits inside the strip. */
    private void drawBarText(GuiGraphics graphics, String text, int barX, int barWidth, int barY) {
        float scale = 0.7F;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, (int) ((barX + barWidth / 2.0F) / scale - font.width(text) / 2.0F),
                (int) ((barY + 1) / scale), 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, menu.getCompanion() == null ? "Компаньон" : menu.getCompanion().getName().getString(), 12, 8, 0xF1F5FA, false);
        graphics.drawString(font, "Броня", 12, 23, 0xB4C0D2, false);
        graphics.drawString(font, "Правая", 100, 23, 0xB4C0D2, false);
        graphics.drawString(font, "Левая", 144, 23, 0xB4C0D2, false);
        graphics.drawString(font, "Сумка компаньона", 12, 77, 0xF1F5FA, false);
        graphics.drawString(font, "Твой инвентарь", 12, 152, 0xF1F5FA, false);
        graphics.drawWordWrap(font, Component.literal("Перетащи предмет в руку или ячейку брони."), 196, 155, 90, 0xA7B4C7);
        graphics.drawWordWrap(font, Component.literal("Shift + клик — быстро перенести."), 196, 202, 90, 0x8595AC);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.index < 6 && !hoveredSlot.hasItem()) {
            String[] names = {"Шлем", "Нагрудник", "Поножи", "Ботинки", "Правая рука: оружие или инструмент", "Левая рука"};
            graphics.renderTooltip(font, Component.literal(names[hoveredSlot.index]), mouseX, mouseY);
        }
    }
}

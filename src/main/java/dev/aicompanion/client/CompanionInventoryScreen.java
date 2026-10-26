package dev.aicompanion.client;

import dev.aicompanion.inventory.CompanionMenu;
import net.minecraft.client.gui.GuiGraphics;
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
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 199, y + 22, x + 283, y + 137,
                    40, 0.0F, mouseX, mouseY, menu.getCompanion());
        }
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

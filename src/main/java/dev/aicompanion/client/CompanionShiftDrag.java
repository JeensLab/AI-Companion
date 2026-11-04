package dev.aicompanion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/**
 * Shift held and the mouse dragged across slots moves every stack the pointer passes, one quick move per slot,
 * as a player's shift-click moves one. Only with an empty hand: a stack on the cursor is the game's own drag-split.
 */
final class CompanionShiftDrag {
    private static Slot last;
    private static AbstractContainerScreen<?> lastScreen;

    static void dragged(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || !Screen.hasShiftDown() || !screen.getMenu().getCarried().isEmpty()) {
            reset();
            return;
        }
        // A screen the player has opened before keeps its slots, so the last one of an earlier drag must not count.
        if (screen != lastScreen) {
            lastScreen = screen;
            last = null;
        }
        Slot slot = at(screen, mouseX, mouseY);
        if (slot == null || slot == last) return;
        last = slot;
        if (slot.getItem().isEmpty() || !slot.mayPickup(player)) return;
        minecraft.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0, ClickType.QUICK_MOVE, player);
    }

    static void reset() {
        last = null;
        lastScreen = null;
    }

    /** The slot under the pointer, as the game's own hover test finds one: a pixel of margin round every slot. */
    private static Slot at(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;
            int x = left + slot.x, y = top + slot.y;
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) return slot;
        }
        return null;
    }
}

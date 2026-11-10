package dev.aicompanion.client;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Shift held and the mouse dragged across slots moves every stack the pointer passes, one quick move per slot,
 * as a player's shift-click moves one. Only with an empty hand: a stack on the cursor is the game's own drag-split.
 * <p>
 * The slot choice is kept apart from the screen, so a game test can drive a whole drag with plain slots and a mock
 * player: the screen only tells which menu is open, where it is drawn and whether shift is held.
 */
public final class CompanionShiftDrag {
    private static Slot last;
    private static AbstractContainerMenu lastMenu;

    private CompanionShiftDrag() {
    }

    /**
     * The slot the pointer has newly reached and whose stack has to be quick-moved now, or null when this frame moves
     * nothing. The caller sends the move with the menu's own container id, as the game does for a shift-click.
     */
    public static Slot reached(AbstractContainerMenu menu, int guiLeft, int guiTop, double mouseX, double mouseY,
            boolean shiftDown, Player player) {
        if (!shiftDown || !menu.getCarried().isEmpty()) {
            reset();
            return null;
        }
        // A menu the player has opened before keeps its slots, so the last slot of an earlier drag must not count.
        if (menu != lastMenu) {
            lastMenu = menu;
            last = null;
        }
        Slot slot = at(menu, guiLeft, guiTop, mouseX, mouseY);
        if (slot == null || slot == last) return null;
        last = slot;
        return slot.getItem().isEmpty() || !slot.mayPickup(player) ? null : slot;
    }

    public static void reset() {
        last = null;
        lastMenu = null;
    }

    /** The slot under the pointer, as the game's own hover test finds one: a pixel of margin round every slot. */
    private static Slot at(AbstractContainerMenu menu, int guiLeft, int guiTop, double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            int x = guiLeft + slot.x, y = guiTop + slot.y;
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) return slot;
        }
        return null;
    }
}

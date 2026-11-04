package dev.aicompanion.client;

import dev.aicompanion.AICompanionMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Every container screen the player has open, his own inventory too, not only Oleg's menu.
 * <p>
 * The drag is read while the screen draws, once a frame: the game sends a container screen no drag event of its own
 * (the screen's own drag handling never reaches the event), and a tick would step over slots on a quick pass.
 */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID, value = Dist.CLIENT)
final class CompanionShiftDragEvents {
    @SubscribeEvent
    static void rendered(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        // The creative screen keeps its own click handling. Its catalogue menu has the player's container id, but its
        // slot numbers are the catalogue's own, so a click sent with that id would be applied to another slot of the
        // player's real inventory menu and could, say, take off a worn chestplate. A drag there is left to the game.
        if (screen instanceof CreativeModeInventoryScreen) {
            CompanionShiftDrag.reset();
            return;
        }
        // The game's own isLeftPressed is only kept while no screen is open, so the button is asked of the window.
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            CompanionShiftDrag.reset();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) {
            CompanionShiftDrag.reset();
            return;
        }
        Slot slot = CompanionShiftDrag.reached(screen.getMenu(), screen.getGuiLeft(), screen.getGuiTop(),
                event.getMouseX(), event.getMouseY(), Screen.hasShiftDown(), player);
        if (slot == null) return;
        minecraft.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0, ClickType.QUICK_MOVE, player);
    }

    @SubscribeEvent
    static void released(ScreenEvent.MouseButtonReleased.Post event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) CompanionShiftDrag.reset();
    }
}

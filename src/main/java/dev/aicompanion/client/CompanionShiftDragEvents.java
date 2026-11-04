package dev.aicompanion.client;

import dev.aicompanion.AICompanionMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
        // The game's own isLeftPressed is only kept while no screen is open, so the button is asked of the window.
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            CompanionShiftDrag.reset();
            return;
        }
        CompanionShiftDrag.dragged(screen, event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    static void released(ScreenEvent.MouseButtonReleased.Post event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) CompanionShiftDrag.reset();
    }
}

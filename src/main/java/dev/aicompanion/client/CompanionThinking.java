package dev.aicompanion.client;

import dev.aicompanion.AICompanionMod;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Issue #32: an answer took 58 s on stream, and «думает…» over the hotbar faded in three seconds — the player asked again,
 * opened the tasks, found nothing there and said he had broken. While a request of the player waits for the model, a line
 * of its own above the hotbar says so with the seconds (other notices there stay readable); it goes with the answer or error.
 */
final class CompanionThinking {
    private CompanionThinking() { }

    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AICompanionMod.MOD_ID, "thinking");
    private static long since, generation = -1, plans = -1;

    /** The request now at the head of the queue has just gone to the model. */
    static void started() {
        since = Util.getMillis(); generation = OpenRouterClient.generation(); plans = CompanionActionExecutor.currentPlanGeneration();
    }

    /**
     * A request sent before leaving the world or «забудь всё» can still be on its way (up to 90 s), and the answer to one
     * sent before «стоп» or another order that drops pending plans is thrown away: neither is something he still thinks over.
     */
    static boolean waiting() {
        return OpenRouterClient.isBusy() && generation == OpenRouterClient.generation() && plans == CompanionActionExecutor.currentPlanGeneration();
    }

    /** « 12 с» once the wait is noticeable; nothing in the first seconds. */
    static String seconds() {
        long seconds = (Util.getMillis() - since) / 1000;
        return seconds >= 3 ? " " + seconds + " с" : "";
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.level == null || !waiting()) return;
        // Above the action bar (guiHeight - 72) and the held item's name (- 59), so neither covers it.
        graphics.drawCenteredString(minecraft.font, CompanionChatScreen.companionName() + " думает…" + seconds(),
                graphics.guiWidth() / 2, graphics.guiHeight() - 84, 0xE0E6EDF5);
    }
}

package dev.aicompanion.client;

import dev.aicompanion.AICompanionMod;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;

/**
 * Issue #32: an answer took 58 s on stream, and «думает…» over the hotbar faded in three seconds — the player asked again,
 * opened the tasks, found nothing there and said he had broken. While a request of the player waits for the model, a line
 * of its own above the hotbar says so with the seconds (other notices there stay readable); it goes with the answer or error.
 * Render thread only.
 */
final class CompanionThinking {
    private CompanionThinking() { }

    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AICompanionMod.MOD_ID, "thinking");

    /**
     * A request in the model queue, with what decides whether its answer will be heard: one asked before leaving the world
     * or «забудь всё», or before «стоп» or another order that drops pending plans, is thrown away when it comes.
     */
    private record Asked(long at, long session, long plans) {
        boolean heard() { return session == OpenRouterClient.generation() && plans == CompanionActionExecutor.currentPlanGeneration(); }
    }

    private static final ArrayDeque<Asked> ASKED = new ArrayDeque<>();
    private static long answeredAt;

    /** A request of the player joins the model queue. */
    static void asked() { ASKED.addLast(new Asked(Util.getMillis(), OpenRouterClient.generation(), CompanionActionExecutor.currentPlanGeneration())); }

    /** The oldest request got its answer or error (the queue answers in order); the next one goes to the model now. */
    static void answered() {
        Asked done = ASKED.pollFirst();
        // An answer thrown away was never heard: the count of the request behind it goes on.
        if (done != null && done.heard()) answeredAt = Util.getMillis();
    }

    private static Asked waitedFor() {
        for (Asked asked : ASKED) if (asked.heard()) return asked;
        return null;
    }

    static boolean waiting() { return waitedFor() != null; }

    /** « 12 с» once the wait is noticeable; nothing in the first seconds. */
    static String seconds() {
        Asked asked = waitedFor();
        long seconds = asked == null ? 0 : (Util.getMillis() - Math.max(asked.at(), answeredAt)) / 1000;
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

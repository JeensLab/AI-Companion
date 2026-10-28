package dev.aicompanion.client;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionMood;
import dev.aicompanion.CompanionPersonality;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionBond;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Oleg's mood at a glance, the player 28.10: «небольшой интерфейс настроения справа вертикальной полоской, чтобы она не сильно
 * выделялась, но её было видно, и можно отключить кнопкой» — his face now, the bond and the traits his talks have moved.
 * The key N (changeable in the controls). Read from what the client already has; nothing is asked of the server or a model.
 */
final class CompanionMoodPanel {
    private CompanionMoodPanel() { }

    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AICompanionMod.MOD_ID, "mood_panel");
    private static final float SCALE = .75F;
    private static final int WIDTH = 84, ROW = 10, MARGIN = 4;
    private static CompanionEntity companion;
    private static long sampledAt = Long.MIN_VALUE;

    static void toggle() {
        boolean on = !OpenRouterConfig.MOOD_PANEL.get();
        OpenRouterConfig.MOOD_PANEL.set(on); OpenRouterConfig.MOOD_PANEL.save();
        Minecraft.getInstance().gui.setOverlayMessage(Component.literal("Настроение Олега: " + (on ? "вкл." : "выкл.")), false);
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!OpenRouterConfig.MOOD_PANEL.get() || minecraft.options.hideGui || minecraft.level == null || minecraft.player == null
                || minecraft.getDebugOverlay().showDebugScreen()) return;
        // The search for him is once a tick, not once a frame.
        long now = minecraft.level.getGameTime();
        if (now != sampledAt) { companion = CompanionVoiceAudio.speaker(); sampledAt = now; }
        if (companion == null || !companion.isAlive()) return;
        var digest = CompanionMemoryView.current();
        var traits = CompanionPersonality.Trait.values();
        int height = ROW * (2 + traits.length) + 6;
        graphics.pose().pushPose();
        graphics.pose().scale(SCALE, SCALE, 1);
        int x = Math.round((graphics.guiWidth() - MARGIN) / SCALE) - WIDTH, y = Math.round((graphics.guiHeight() / SCALE - height) / 2);
        var mood = companion.expression();
        graphics.fill(x - 4, y - 4, x + WIDTH + 2, y + height, 0x40000000);
        graphics.fill(x - 4, y - 4, x - 3, y + height, CompanionMood.color(mood) & 0x90FFFFFF);
        var font = minecraft.font;
        graphics.drawString(font, CompanionMood.of(mood), x, y, CompanionMood.color(mood), true);
        y += ROW;
        graphics.drawString(font, CompanionMood.bond(digest.tier()), x, y, 0xD0B8C3D1, true);
        // The bond from −100 to 100 as a thin line under its tier.
        int filled = Math.round(WIDTH * (Math.max(CompanionBond.MIN, Math.min(CompanionBond.MAX, digest.score())) - CompanionBond.MIN)
                / (float) (CompanionBond.MAX - CompanionBond.MIN));
        graphics.fill(x, y + 10, x + WIDTH, y + 11, 0x40FFFFFF);
        graphics.fill(x, y + 10, x + filled, y + 11, digest.score() < 0 ? 0xC0E38A8A : 0xC09FD89A);
        y += ROW + 6;
        for (var trait : traits) {
            int level = Math.max(-2, Math.min(2, digest.character().getOrDefault(trait, 0)));
            graphics.drawString(font, CompanionMood.of(trait), x, y, 0xC0A8B0BC, true);
            // Five marks from −2 to 2; those between the middle and the level are lit.
            for (int step = -2; step <= 2; step++) {
                int left = x + WIDTH - 24 + (step + 2) * 5;
                boolean lit = step != 0 && Integer.signum(step) == Integer.signum(level) && Math.abs(step) <= Math.abs(level);
                int color = lit ? (step < 0 ? 0xD07FA8D8 : 0xD0E3B06A) : step == 0 ? 0x90FFFFFF : 0x40FFFFFF;
                graphics.fill(left, y + 2, left + 4, y + 6, color);
            }
            y += ROW;
        }
        graphics.pose().popPose();
    }
}

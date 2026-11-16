package dev.aicompanion.client;

import dev.aicompanion.AICompanionMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * While the ghost of a building is open: a small panel in the top left corner with the building's name and the keys as they are bound now.
 * It takes the place of the hint line above the hotbar and of the hint in the chat. Everything is placed from the scaled window size, so it
 * is the same few lines at every GUI scale: the corner is clear of the chat and the hotbar (both at the bottom), the mood panel (at the
 * right) and the action bar; it is not drawn over an open screen (the chat included) or the debug screen. Client only.
 */
final class CompanionGhostPanel {
    private CompanionGhostPanel() { }

    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AICompanionMod.MOD_ID, "ghost_panel");
    private static final int MARGIN = 6, PAD = 4, MAX_TEXT = 200, MIN_TEXT = 90;

    /** The lines of the panel: the building, then the keys. */
    static List<Component> lines(String building) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("«" + building + "»").withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal(CompanionGhostPlacement.keyName(CompanionGhostPlacement.CONFIRM) + " — строить здесь"));
        lines.add(Component.literal(CompanionGhostPlacement.keyName(CompanionGhostPlacement.CANCEL) + " — отмена"));
        lines.add(Component.literal(CompanionGhostPlacement.keyName(CompanionGhostPlacement.ROTATE) + " — повернуть"));
        lines.add(Component.literal(CompanionGhostPlacement.keyName(CompanionGhostPlacement.FOLLOW) + " — снова за прицелом"));
        lines.add(Component.literal("Сдвинуть: " + String.join(" / ", CompanionGhostPlacement.moveKeys())));
        lines.add(Component.literal("Выше / ниже: " + CompanionGhostPlacement.keyName(CompanionGhostPlacement.RAISE) + " / " + CompanionGhostPlacement.keyName(CompanionGhostPlacement.LOWER)));
        return lines;
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        String building = CompanionGhostPlacement.active();
        if (building == null || minecraft.options.hideGui || minecraft.screen != null || minecraft.level == null || minecraft.getDebugOverlay().showDebugScreen()) return;
        var font = minecraft.font;
        // A third of the width at most: at a big GUI scale the window is few units wide, and the lines wrap instead.
        int maxText = Math.clamp(graphics.guiWidth() / 3 - 2 * PAD - MARGIN, MIN_TEXT, MAX_TEXT);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines(building)) wrapped.addAll(font.split(line, maxText));
        int textWidth = 0;
        for (var line : wrapped) textWidth = Math.max(textWidth, font.width(line));
        int x = MARGIN, y = MARGIN, width = textWidth + 2 * PAD, height = wrapped.size() * font.lineHeight + 2 * PAD;
        graphics.fill(x, y, x + width, y + height, 0x90151C27);
        graphics.fill(x, y, x + 1, y + height, 0xFF75D9CB);
        for (int i = 0; i < wrapped.size(); i++) graphics.drawString(font, wrapped.get(i), x + PAD, y + PAD + i * font.lineHeight, 0xFFDCE4EE, false);
    }
}

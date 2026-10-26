package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Where Oleg is, at a glance: a golden outline through walls and ground once he is out of sight or farther than a few steps.
 * Only for his owner, only on this client. Толик 23.10: «стрелка где он не нужна, оставь только контур, и сделай возможность
 * включить и отключать его по кнопке» — the key G (changeable in the controls), also in «Поведение».
 */
final class CompanionMarker {
    private CompanionMarker() { }

    /** Nearer than this and in sight he needs no outline. */
    private static final double NEAR = 12;
    private static final Set<UUID> outlined = new HashSet<>();

    static void init() { CompanionEntity.clientOutline = companion -> outlined.contains(companion.getUUID()); }

    /** The key: on or off, said in the action bar. */
    static void toggle() {
        boolean on = !OpenRouterConfig.COMPANION_MARKER.get();
        OpenRouterConfig.COMPANION_MARKER.set(on); OpenRouterConfig.COMPANION_MARKER.save();
        Minecraft.getInstance().gui.setOverlayMessage(Component.literal("Контур Олега: " + (on ? "вкл." : "выкл.")), false);
    }

    /** Once a tick: who is outlined (a sight check per frame would be wasted work). */
    static void tick() {
        outlined.clear();
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null || !OpenRouterConfig.COMPANION_MARKER.get()) return;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof CompanionEntity companion) || !companion.isAlive() || !player.getUUID().equals(companion.getOwnerId())) continue;
            if (player.distanceTo(companion) > NEAR || !player.hasLineOfSight(companion)) outlined.add(companion.getUUID());
        }
    }
}

package dev.aicompanion.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;

import java.util.List;

/** What an expedition says of itself when it ends: the diamonds, the depth, the tunnels (moved out of {@link CompanionExpedition}). */
final class CompanionExpeditionReport {
    private CompanionExpeditionReport() { }

    static String of(CompanionWork w, int gained, int stairSteps) {
        StringBuilder text = new StringBuilder("Алмазов в сумке: " + w.items.countCarried(Items.DIAMOND) + " (добыто за вылазку: " + gained
                + ", нужно " + w.requestedCount + "). Ноги на высоте " + w.owner.blockPosition().getY() + ", ступеней вниз " + stairSteps + ".");
        List<CompanionMineSite.Tunnel> tunnels = w.mine.tunnels();
        if (tunnels.isEmpty()) return text.toString();
        text.append(tunnels.size() == 1 ? " Тоннель " : " Тоннели ");
        for (int i = 0; i < tunnels.size(); i++) {
            text.append(i == 0 ? "" : ", ").append("на ").append(name(tunnels.get(i).direction())).append(' ').append(tunnels.get(i).length());
        }
        return text.append('.').toString();
    }

    static String name(Direction direction) {
        return switch (direction) {
            case NORTH -> "север";
            case EAST -> "восток";
            case SOUTH -> "юг";
            case WEST -> "запад";
            default -> direction.getName();
        };
    }
}

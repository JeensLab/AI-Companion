package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A compact, read-only snapshot supplied to the model when the player sends a message. */
public final class GameContext {
    private static Object remoteWorld;
    private static String remoteState = "не найден рядом";
    private static long remoteAt;
    public static void setRemoteState(Object world, String state) { remoteWorld = world; remoteState = state; remoteAt = System.nanoTime(); }
    private GameContext() {
    }

    /** Ambient speech needs current nearby facts, not bags, coordinates, known places or a long journal. */
    public static String ambientSnapshot(dev.aicompanion.entity.CompanionEntity speaker) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return "";
        String activity = speaker.getActivityForAi().split(";", 2)[0];
        String remembered = PromptMemory.ambient(CompanionMemoryView.current());
        return "Время=" + timeOfDay(client.level.getDayTime()) + "; дождь=" + client.level.isRaining()
                + "; здоровье игрока=" + Math.round(client.player.getHealth()) + "/" + Math.round(client.player.getMaxHealth())
                + "; в его руке=" + client.player.getMainHandItem().getHoverName().getString()
                + "; я=" + activity + "; здоровье=" + Math.round(speaker.getHealth()) + "/" + Math.round(speaker.getMaxHealth())
                + (remembered.isEmpty() ? "" : "; " + remembered);
    }

    /** {@code message} is the player's current line: it picks which memories are the most relevant to show. */
    public static String snapshot(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return "Игровой мир сейчас недоступен.";

        var player = minecraft.player;
        String biome = minecraft.level.getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("неизвестный биом");
        List<String> inventory = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) inventory.add(stack.getCount() + " " + stack.getHoverName().getString());
            if (inventory.size() >= 8) break;
        }
        if (inventory.isEmpty()) inventory.add("пусто");

        List<String> threats = minecraft.level.getEntitiesOfClass(Monster.class,
                        player.getBoundingBox().inflate(18.0D), monster -> dev.aicompanion.entity.CompanionHostiles.observed(player, monster, 18)).stream()
                .limit(5)
                .map(entity -> entity.getName().getString() + " (" + Math.round(entity.distanceTo(player)) + " блоков)")
                .toList();
        List<CompanionEntity> companions = minecraft.level.getEntitiesOfClass(CompanionEntity.class,
                player.getBoundingBox().inflate(128.0D), entity -> player.getUUID().equals(entity.getOwnerId()));

        String remembered = PromptMemory.section(CompanionMemoryView.current(), message);
        return "Игровая обстановка (снимок на момент сообщения): измерение=" + minecraft.level.dimension().location()
                + ", биом=" + biome
                + ", позиция игрока=" + player.blockPosition().getX() + "," + player.blockPosition().getY() + "," + player.blockPosition().getZ()
                + ", здоровье=" + Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth())
                + ", сытость=" + player.getFoodData().getFoodLevel()
                + ", предмет в руке=" + player.getMainHandItem().getHoverName().getString()
                + ", инвентарь=" + String.join(", ", inventory)
                + ", время мира=" + Math.floorMod(minecraft.level.getDayTime(), 24000L)
                + ", время суток=" + (minecraft.level.dimensionType().hasSkyLight() ? timeOfDay(minecraft.level.getDayTime()) : "обычного цикла дня и ночи нет")
                + ", дождь=" + minecraft.level.isRaining()
                + ", ближайшие монстры=" + (threats.isEmpty() ? "не замечены" : String.join(", ", threats))
                + ", состояние компаньона=" + (companions.isEmpty() ? (remoteWorld == minecraft.level && System.nanoTime() - remoteAt < 10_000_000_000L ? remoteState : "не найден рядом") : companions.getFirst().getActivityForAi())
                + (companions.isEmpty() ? "" : ", его сумка=" + companions.getFirst().getStoredItemsForAi()
                        + ", его имя=" + companions.getFirst().getName().getString()
                        + ", дальность самостоятельного поиска=" + dev.aicompanion.OpenRouterConfig.SEARCH_RADIUS.get() + " блоков"
                        + ", его экипировка=" + companions.getFirst().getEquipmentForAi()
                        + ", последний использованный сундук=" + companions.getFirst().getStorageForAi()
                        + ", память о местах=" + companions.getFirst().getPlacesForAi()
                        + ", установленный верстак рядом=" + companions.getFirst().hasCraftingTableNearby()
                        + ", исторический отчёт (не описание текущей экипировки)=" + (companions.getFirst().getLastTaskResult().isBlank()
                        ? "пока нет" : companions.getFirst().getLastTaskResult())
                        + ", журнал его недавних дел (старые первыми, настоящие итоги)=" + companions.getFirst().getDeedsForAi())
                + (remembered.isEmpty() ? "" : ", " + remembered)
                + ". Это только видимые клиенту сведения; не додумывай неизвестное.";
    }

    public static String timeOfDay(long dayTime) {
        long tick = Math.floorMod(dayTime, 24000L);
        return tick < 12000 ? "день" : tick < 13000 ? "закат" : tick < 23000 ? "ночь" : "рассвет";
    }
}

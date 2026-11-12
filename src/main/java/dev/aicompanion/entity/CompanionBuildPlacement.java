package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a building of the catalogue goes is chosen by the player, with a ghost on his own screen: the order tells him the building,
 * its size and what it takes, and then waits (as the work itself, so a stop, another order or a world change end it like any work)
 * until he confirms a corner and a turn or cancels. Nothing is placed, dug or gathered before. The mod is singleplayer only, so the
 * client calls these methods on the integrated server's thread; everything it sends is treated as untrusted all the same.
 */
public final class CompanionBuildPlacement {
    /** How far from the player a building may be set down: the ghost follows the crosshair up to 64 blocks, and a building is up to a dozen wide. */
    public static final int MAX_DISTANCE = 96;
    /** A placement nobody confirms is dropped after two minutes. */
    public static final int TIMEOUT_TICKS = 20 * 120;
    /** The client says it is there (CompanionPreferences.sync, every 40 ticks); one that has not for this long is taken to be gone. */
    private static final int CLIENT_SEEN_TICKS = 200;
    private static final int MAX_LISTED = 8;
    private static final Map<UUID, Integer> CLIENTS = new ConcurrentHashMap<>();

    /** One block of the building relative to its lowest corner (both halves of a door or a bed), for the ghost. */
    public record Block(BlockPos rel, BlockState state) { }
    public record Preview(String name, int sizeX, int sizeY, int sizeZ, List<Block> blocks) { }

    private CompanionBuildPlacement() { }

    /** The player's client can show a ghost: a building he orders by name waits for the place he sets. Without it (a server order, a test) the companion chooses the site. */
    public static void clientReady(ServerPlayer player) { CLIENTS.put(player.getUUID(), player.server.getTickCount()); }
    private static boolean hasClient(ServerPlayer player) {
        Integer seen = CLIENTS.get(player.getUUID());
        // A tick count from before a restart of the server is no sign of a client.
        return seen != null && player.server.getTickCount() - seen >= 0 && player.server.getTickCount() - seen <= CLIENT_SEEN_TICKS;
    }

    /** A fresh order for a building: with a client to place it, tell the player what it is and what it takes, and wait. */
    static void ask(CompanionEntity owner, CompanionHouse house) {
        ServerPlayer player = owner.getOwnerPlayer();
        if (house.sited() || player == null || !hasClient(player) || !(owner.level() instanceof ServerLevel level)) return;
        var read = CompanionTemplates.load(level, house.building(), Rotation.NONE);
        if (read == null) return;
        String text = summary(level, owner, house.building(), read);
        house.awaitPlacement(level.getGameTime() + TIMEOUT_TICKS, text);
        player.sendSystemMessage(Component.literal(owner.getName().getString() + ": " + text));
    }

    /** The name, the size and what the bag lacks of the building: what he gets himself and what the player has to give. Changes neither the bag nor the world. */
    private static String summary(ServerLevel level, CompanionEntity owner, CompanionVillageBuildings.Building building, CompanionTemplates.Loaded read) {
        var materials = new CompanionHouseMaterials(owner);
        materials.chooseWoods(level, read.cells());
        var cells = read.cells().stream().sorted(Comparator.comparingInt(CompanionTemplates.Cell::stage)).toList();
        Map<Item, Integer> required = new LinkedHashMap<>();
        // Planks last, as the building plans its materials.
        for (boolean last : new boolean[]{false, true}) for (var cell : cells) {
            Item item = materials.adapt(cell.state()).getBlock().asItem();
            if (new ItemStack(item).is(ItemTags.PLANKS) == last) required.merge(item, 1, Integer::sum);
        }
        var lacking = materials.next(level, required).lacking();
        List<String> himself = new ArrayList<>(), given = new ArrayList<>();
        required.forEach((item, count) -> {
            int missing = count - carried(owner, item);
            if (missing > 0) (lacking.containsKey(item) ? given : himself).add(missing + " × " + new ItemStack(item).getHoverName().getString());
        });
        String needs = himself.isEmpty() && given.isEmpty() ? "Материалов в сумке хватает на всё здание. "
                : (himself.isEmpty() ? "" : "Сам добуду или сделаю: " + listed(himself) + ". ") + (given.isEmpty() ? "" : "Принести придётся тебе: " + listed(given) + ". ");
        return "Могу построить «" + building.name() + "»: " + read.sizeX() + "×" + read.sizeZ() + "×" + read.sizeY() + " блоков (ширина × глубина × высота). " + needs
                + "Выбери место призраком и подтверди; пока не подтвердишь, ничего не трогаю. Не выберешь за " + TIMEOUT_TICKS / 1200 + " минуты — отменю.";
    }
    private static String listed(List<String> items) {
        return String.join(", ", items.subList(0, Math.min(MAX_LISTED, items.size()))) + (items.size() > MAX_LISTED ? " и ещё " + (items.size() - MAX_LISTED) + " видов" : "");
    }
    private static int carried(CompanionEntity owner, Item item) {
        int count = owner.getMainHandItem().is(item) ? owner.getMainHandItem().getCount() : 0;
        if (owner.getOffhandItem().is(item)) count += owner.getOffhandItem().getCount();
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(item)) count += owner.getItem(slot).getCount();
        return count;
    }

    private static CompanionHouse pending(ServerPlayer player) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        return companion == null ? null : companion.work.awaitingHouse();
    }

    /** The building the player's order waits to have placed, or null (the client asks, to open and to close its ghost). */
    public static String pendingName(ServerPlayer player) {
        CompanionHouse house = pending(player);
        return house == null ? null : house.building().name();
    }

    /** The building of the catalogue turned {@code rotation} quarter-turns clockwise, for the ghost; null for a name not in the catalogue or a turn that is none. */
    public static Preview preview(ServerLevel level, String name, int rotation) {
        var building = CompanionVillageBuildings.find(name);
        if (building == null || rotation < 0 || rotation >= Rotation.values().length) return null;
        var read = CompanionTemplates.load(level, building, Rotation.values()[rotation]);
        if (read == null) return null;
        List<Block> blocks = new ArrayList<>();
        for (var cell : read.cells()) {
            blocks.add(new Block(cell.rel(), cell.state()));
            if (cell.state().getBlock() instanceof DoorBlock) blocks.add(new Block(cell.rel().above(), cell.state().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
            else if (cell.state().getBlock() instanceof BedBlock)
                blocks.add(new Block(cell.rel().relative(cell.state().getValue(BedBlock.FACING)), cell.state().setValue(BedBlock.PART, BedPart.HEAD)));
        }
        return new Preview(building.name(), read.sizeX(), read.sizeY(), read.sizeZ(), List.copyOf(blocks));
    }

    /**
     * The player confirms: the building at {@code corner} (its lowest corner), turned {@code rotation} quarter-turns. Everything here is
     * untrusted: only the owner of the companion, only for the order that waits, only the building ordered, one of four turns, a corner
     * within {@link #MAX_DISTANCE} of the player in a loaded chunk, and a site the building can stand on. Null when the work goes on from
     * there; otherwise the reason in words for the player, and the order keeps waiting so the ghost can be moved.
     */
    public static String confirm(ServerPlayer player, String name, int rotation, BlockPos corner) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        CompanionHouse house = companion == null ? null : companion.work.awaitingHouse();
        if (house == null) return "Я сейчас не жду места для постройки.";
        if (name == null || !house.building().equals(CompanionVillageBuildings.find(name))) return "Я жду место для «" + house.building().name() + "», а не для другой постройки.";
        if (rotation < 0 || rotation >= Rotation.values().length) return "Не понял, как повернуть постройку.";
        if (corner == null || companion.level() != player.level() || !(player.level() instanceof ServerLevel level)) return "Отсюда не получится построить.";
        if (corner.distSqr(player.blockPosition()) > (double) MAX_DISTANCE * MAX_DISTANCE) return "Это дальше " + MAX_DISTANCE + " блоков от тебя, так далеко не строю.";
        String why = house.place(level, corner, Rotation.values()[rotation]);
        if (why != null) return "Здесь строить нельзя: " + why + " Сдвинь призрак.";
        companion.workStatus("Строю «" + house.building().name() + "» на " + corner.toShortString() + ", поворот " + rotation * 90 + "°.");
        return null;
    }

    /** The player gives the placement up: the order is dropped, the world and the bag are as they were. */
    public static void cancel(ServerPlayer player) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        if (companion == null || companion.work.awaitingHouse() == null) return;
        companion.stopGatheringForPlayer();
        companion.workStatus("Ладно, не строю: место не выбрано, ничего не тронул.");
    }
}

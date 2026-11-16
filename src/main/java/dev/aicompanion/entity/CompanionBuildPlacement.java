package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
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
    /** The end of an order dropped while it waited for its place (given up, or another building chosen): nothing was done, the client does not say it. */
    public static final String DROPPED = "Место не выбрано: заказ снят, ничего не тронуто.";
    /** The end of an order nobody placed in time. */
    static final String TIMED_OUT = "строить не буду. Ничего не тронул.";
    /** A report of an order for a building that ended before its place was set: such an order is not kept among the tasks done. */
    public static boolean neverBegan(String report) { return report != null && (report.contains(DROPPED) || report.contains(TIMED_OUT)); }
    /** The client says it is there (CompanionPreferences.sync, every 40 ticks); one that has not for this long is taken to be gone. */
    private static final int CLIENT_SEEN_TICKS = 200;
    private static final Map<UUID, Integer> CLIENTS = new ConcurrentHashMap<>();

    /** One block of the building relative to its lowest corner (both halves of a door or a bed), for the ghost. */
    public record Block(BlockPos rel, BlockState state) { }
    public record Preview(String name, int sizeX, int sizeY, int sizeZ, List<Block> blocks) { }
    /**
     * A building with the place the player set for it in the window, as the order carries it: «name@x,y,z,turn». Only the client's own
     * order has it (the plan codec lets no such name through from the model), and the server checks the place again when the order begins.
     */
    public record Site(String name, BlockPos corner, int rotation) {
        public String item() { return name + "@" + corner.getX() + "," + corner.getY() + "," + corner.getZ() + "," + rotation; }
        /** The name alone, for what is said and shown. */
        public static String nameOf(String item) { return item == null || item.indexOf('@') < 0 ? item : item.substring(0, item.indexOf('@')); }
        static Site parse(String item) {
            if (item == null || item.indexOf('@') < 0) return null;
            String[] parts = item.substring(item.indexOf('@') + 1).split(",");
            try {
                int turn = parts.length == 4 ? Integer.parseInt(parts[3].trim()) : -1;
                if (turn < 0 || turn >= Rotation.values().length) return null;
                return new Site(nameOf(item), new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())), turn);
            } catch (NumberFormatException error) { return null; }
        }
    }
    /**
     * The answer to the player's Enter: the reason the building cannot go there, else whether the client is to make the order (false: the
     * order that waited took the place) and whether that order will wait for a work of his that is under way.
     */
    public record Settled(String why, boolean order, boolean queued) {
        Settled(String why) { this(why, false, false); }
    }

    private CompanionBuildPlacement() { }

    /** The player's client can show a ghost: a building he orders by name waits for the place he sets. Without it (a server order, a test) the companion chooses the site. */
    public static void clientReady(ServerPlayer player) { CLIENTS.put(player.getUUID(), player.server.getTickCount()); }
    private static boolean hasClient(ServerPlayer player) {
        Integer seen = CLIENTS.get(player.getUUID());
        // A tick count from before a restart of the server is no sign of a client.
        return seen != null && player.server.getTickCount() - seen >= 0 && player.server.getTickCount() - seen <= CLIENT_SEEN_TICKS;
    }

    /**
     * A fresh order for a building. With the place the player set in the window ({@code site}) it is set down there at once; without one,
     * or when it can stand there no longer, and with a client to place it, he says the one short line about it (after {@code note}) and waits.
     */
    static void ask(CompanionEntity owner, CompanionHouse house, String note, Site site) {
        ServerPlayer player = owner.getOwnerPlayer();
        if (house.sited() || !(owner.level() instanceof ServerLevel level)) return;
        var read = CompanionTemplates.load(level, house.building(), Rotation.NONE);
        if (read == null) return;
        if (site != null) {
            String why = house.place(level, site.corner(), Rotation.values()[site.rotation()]);
            if (why == null) {
                house.takeDropNote();
                if (player != null) player.sendSystemMessage(Component.literal(owner.getName().getString() + ": " + CompanionBuildSummary.of(level, owner, house.building(), read).line()));
                return;
            }
            note += "На выбранном месте строить уже нельзя: " + why + " Выбери место заново. ";
        }
        if (player == null || !hasClient(player)) return;
        // An unfinished building an old order left is not dropped yet: it is dropped when the place is set.
        String text = (house.waitingNote().isEmpty() ? note : house.waitingNote()) + CompanionBuildSummary.of(level, owner, house.building(), read).line();
        house.awaitPlacement(level.getGameTime() + TIMEOUT_TICKS, text);
        player.sendSystemMessage(Component.literal(owner.getName().getString() + ": " + text));
    }

    /** What the building {@code name} of the catalogue takes, for the window of buildings; null when there is no such building, no companion, or the template cannot be read. */
    public static CompanionBuildSummary.Summary summary(ServerPlayer player, String name) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        var building = CompanionVillageBuildings.find(name);
        if (companion == null || building == null || !(companion.level() instanceof ServerLevel level)) return null;
        var read = CompanionTemplates.load(level, building, Rotation.NONE);
        return read == null ? null : CompanionBuildSummary.of(level, companion, building, read);
    }

    /**
     * The player's Enter on a ghost: the building {@code name} at {@code corner}, turned. The ghost itself is no order (looking at buildings
     * touches nothing he does), so this is where one begins. An order that already waits for this very building takes the place (confirm).
     * Otherwise the place is checked as confirm checks it, without touching any work; if it is good, a different building's order that waits
     * is dropped quietly, and the client makes the order with the place in it ({@link Site#item()}), which takes its turn in the queue.
     */
    public static Settled settle(ServerPlayer player, String name, int rotation, BlockPos corner) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        var building = CompanionVillageBuildings.find(name);
        if (companion == null) return new Settled("Меня сейчас нет в мире.");
        if (building == null) return new Settled("Такой постройки я не знаю.");
        CompanionHouse waits = companion.work.awaitingHouse();
        if (waits != null && waits.building().equals(building)) return new Settled(confirm(player, name, rotation, corner));
        if (rotation < 0 || rotation >= Rotation.values().length) return new Settled("Не понял, как повернуть постройку.");
        if (corner == null || companion.level() != player.level() || !(player.level() instanceof ServerLevel level)) return new Settled("Отсюда не получится построить.");
        if (corner.distSqr(player.blockPosition()) > (double) MAX_DISTANCE * MAX_DISTANCE) return new Settled("Это дальше " + MAX_DISTANCE + " блоков от тебя, так далеко не строю.");
        String why = CompanionHouse.refusalAt(companion, level, building, corner, Rotation.values()[rotation]);
        if (why != null) return new Settled("Здесь строить нельзя: " + why + " Сдвинь призрак.");
        if (waits != null) drop(companion, waits);
        return new Settled(null, true, waits == null && companion.hasActiveWorkOrder());
    }

    /** An order that waits for its place ends without one: its end is {@link #DROPPED}, not «work interrupted». */
    private static void drop(CompanionEntity companion, CompanionHouse house) {
        house.replacing();
        companion.stopGatheringForPlayer();
        // Given up: «дострой» does not ask for the place again (an unfinished building the order was to replace is his to finish).
        house.forgetPlacement();
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
        companion.workStatus(house.takeDropNote() + "Строю «" + house.building().name() + "» на " + corner.toShortString() + ", поворот " + rotation * 90 + "°.");
        return null;
    }

    /** The player gives the placement up: the order is dropped, the world and the bag are as they were. */
    public static void cancel(ServerPlayer player) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        CompanionHouse house = companion == null ? null : companion.work.awaitingHouse();
        if (house == null) return;
        drop(companion, house);
        companion.workStatus("Ладно, не строю: место не выбрано, ничего не тронул.");
    }
}

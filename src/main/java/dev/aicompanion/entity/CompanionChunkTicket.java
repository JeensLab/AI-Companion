package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the chunk the companion stands in loaded and ticking while a mine expedition runs, so the player may walk away.
 * One ticket per companion, moved when he crosses a chunk border and removed on any end. Tickets are saved with the level,
 * so on load every ticket whose owner is not active in this session is dropped.
 */
public final class CompanionChunkTicket {
    private static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(AICompanionMod.MOD_ID, "expedition"), CompanionChunkTicket::validate);
    private record Held(ServerLevel level, ChunkPos chunk) { }
    private static final Map<UUID, Held> ACTIVE = new HashMap<>();

    private CompanionChunkTicket() { }

    public static void register(RegisterTicketControllersEvent event) { event.register(CONTROLLER); }

    /** Holds the chunk he stands in; moves an existing ticket. False when the level refuses it. */
    public static boolean hold(CompanionEntity companion) {
        if (!(companion.level() instanceof ServerLevel level)) return false;
        UUID id = companion.getUUID();
        ChunkPos chunk = companion.chunkPosition();
        Held old = ACTIVE.get(id);
        if (old != null && old.level == level && old.chunk.equals(chunk)) return true;
        if (!CONTROLLER.forceChunk(level, id, chunk.x, chunk.z, true, true)) return false;
        ACTIVE.put(id, new Held(level, chunk));
        if (old != null) CONTROLLER.forceChunk(old.level, id, old.chunk.x, old.chunk.z, false, true);
        return true;
    }

    /** Moves the ticket to his chunk when he has crossed a border; does nothing while he holds none. */
    public static void follow(CompanionEntity companion) {
        if (ACTIVE.containsKey(companion.getUUID())) hold(companion);
    }

    public static void release(CompanionEntity companion) { release(companion.getUUID()); }

    public static void release(UUID id) {
        Held held = ACTIVE.remove(id);
        if (held != null) CONTROLLER.forceChunk(held.level, id, held.chunk.x, held.chunk.z, false, true);
    }

    public static boolean held(UUID id) { return ACTIVE.containsKey(id); }

    /** The chunk held for the companion, or null. */
    public static ChunkPos heldChunk(UUID id) {
        Held held = ACTIVE.get(id);
        return held == null ? null : held.chunk;
    }

    /** The session is over: its tickets are saved with the levels and dropped by the next load. */
    public static void stop() { ACTIVE.clear(); }

    private static void validate(ServerLevel level, TicketHelper helper) {
        for (UUID owner : List.copyOf(helper.getEntityTickets().keySet())) {
            if (!ACTIVE.containsKey(owner)) helper.removeAllTickets(owner);
        }
    }
}

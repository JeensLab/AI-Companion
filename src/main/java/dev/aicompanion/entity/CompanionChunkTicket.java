package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.ArrayList;
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
    private record Held(ServerLevel level, ChunkPos chunk) {
        /** His chunk and its eight neighbours: a step over a border must not leave him in a chunk that does not tick. */
        List<ChunkPos> around() {
            List<ChunkPos> chunks = new ArrayList<>(9);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) chunks.add(new ChunkPos(chunk.x + dx, chunk.z + dz));
            return chunks;
        }
    }
    private static final Map<UUID, Held> ACTIVE = new HashMap<>();

    private CompanionChunkTicket() { }

    public static void register(RegisterTicketControllersEvent event) { event.register(CONTROLLER); }

    /** Holds the chunk he stands in and its neighbours; moves an existing ticket. False when the level refuses it (the old ticket stays). */
    public static boolean hold(CompanionEntity companion) {
        if (!(companion.level() instanceof ServerLevel level)) return false;
        UUID id = companion.getUUID();
        ChunkPos chunk = companion.chunkPosition();
        Held old = ACTIVE.get(id);
        if (old != null && old.level == level && old.chunk.equals(chunk)) return true;
        Held next = new Held(level, chunk);
        List<ChunkPos> had = old != null && old.level == level ? old.around() : List.of();
        List<ChunkPos> added = new ArrayList<>(9);
        for (ChunkPos wanted : next.around()) {
            if (had.contains(wanted)) continue;
            if (!CONTROLLER.forceChunk(level, id, wanted.x, wanted.z, true, true)) {
                for (ChunkPos undo : added) CONTROLLER.forceChunk(level, id, undo.x, undo.z, false, true);
                return false;
            }
            added.add(wanted);
        }
        ACTIVE.put(id, next);
        if (old != null) {
            List<ChunkPos> keep = next.around();
            for (ChunkPos gone : old.around()) {
                if (old.level != level || !keep.contains(gone)) CONTROLLER.forceChunk(old.level, id, gone.x, gone.z, false, true);
            }
        }
        return true;
    }

    /** Moves the ticket to his chunk when he has crossed a border; does nothing while he holds none. */
    public static void follow(CompanionEntity companion) {
        if (ACTIVE.containsKey(companion.getUUID())) hold(companion);
    }

    public static void release(CompanionEntity companion) { release(companion.getUUID()); }

    public static void release(UUID id) {
        Held held = ACTIVE.remove(id);
        if (held != null) for (ChunkPos chunk : held.around()) CONTROLLER.forceChunk(held.level, id, chunk.x, chunk.z, false, true);
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

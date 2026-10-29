package dev.aicompanion.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Session leases, not permanent /forceload entries. Expiry also cleans up crashes/disconnects. */
public final class CompanionChunks {
    private static final TicketType<UUID> TYPE = TicketType.create("aicompanion_active", Comparator.comparing(UUID::toString), 80);
    private record Lease(ServerLevel level, Set<Long> chunks) { }
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private CompanionChunks() { }
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        Set<UUID> live = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
            if (!(entity instanceof CompanionEntity companion) || companion.getOwnerId() == null) continue;
            var player = server.getPlayerList().getPlayer(companion.getOwnerId());
            if (player == null) continue;
            boolean enabled = !player.getPersistentData().contains("aicompanion:chunk_loading_enabled") || player.getPersistentData().getBoolean("aicompanion:chunk_loading_enabled");
            if (!companion.isAlive() || !companion.loadsChunks() || !enabled) { player.getPersistentData().remove("aicompanion:last_chunk"); continue; }
            live.add(companion.getUUID());
            // Close to a player, their normal simulation covers the surroundings. The central
            // lease bridges the transition as they leave. Far away, keep a 5x5 work area ticking.
            int radius = player.level() == level && companion.distanceToSqr(player) < 32 * 32 ? 0 : 2;
            Set<Long> chunks = new HashSet<>(); ChunkPos center = companion.chunkPosition();
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                ChunkPos pos = new ChunkPos(center.x + x, center.z + z);
                if (!level.getWorldBorder().isWithinBounds(pos.getWorldPosition())) continue;
                level.getChunkSource().addRegionTicket(TYPE, pos, 2, companion.getUUID(), true); chunks.add(pos.toLong());
            }
            for (long packed : companion.batches().chunks(level)) {
                ChunkPos pos = new ChunkPos(packed);
                if (!level.getWorldBorder().isWithinBounds(pos.getWorldPosition())) continue;
                if (chunks.add(packed)) level.getChunkSource().addRegionTicket(TYPE, pos, 2, companion.getUUID(), true);
            }
            Lease previous = LEASES.put(companion.getUUID(), new Lease(level, chunks));
            if (previous != null) for (long old : previous.chunks) if (previous.level != level || !chunks.contains(old))
                previous.level.getChunkSource().removeRegionTicket(TYPE, new ChunkPos(old), 2, companion.getUUID(), true);
            var data = player.getPersistentData();
            data.putString("aicompanion:last_dimension", level.dimension().location().toString());
            data.putLong("aicompanion:last_chunk", center.toLong());
        }
        for (UUID id : Set.copyOf(LEASES.keySet())) if (!live.contains(id)) release(id);
        // On world reopen the companion may be beyond player view distance. Reacquire its last
        // chunk until its saved entity joins, without spawning a replacement or ticking offline.
        for (var player : server.getPlayerList().getPlayers()) {
            var data = player.getPersistentData();
            if (data.contains("aicompanion:chunk_loading_enabled") && !data.getBoolean("aicompanion:chunk_loading_enabled")) continue;
            if (!data.hasUUID("aicompanion:canonical_companion") || !data.contains("aicompanion:last_chunk")) continue;
            UUID id = data.getUUID("aicompanion:canonical_companion"); if (live.contains(id)) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(data.getString("aicompanion:last_dimension"));
            if (dimension == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level != null) level.getChunkSource().addRegionTicket(TYPE, new ChunkPos(data.getLong("aicompanion:last_chunk")), 2, id, true);
        }
    }
    public static int ticketCount(UUID id) { Lease lease = LEASES.get(id); return lease == null ? 0 : lease.chunks.size(); }
    /** Whether the chunk (packed as ChunkPos.asLong) is in the companion's current lease. */
    public static boolean leases(UUID id, long chunk) { Lease lease = LEASES.get(id); return lease != null && lease.chunks.contains(chunk); }
    public static void release(UUID id) {
        Lease old = LEASES.remove(id);
        if (old != null) for (long pos : old.chunks) old.level.getChunkSource().removeRegionTicket(TYPE, new ChunkPos(pos), 2, id, true);
    }
    public static void stop() { for (UUID id : Set.copyOf(LEASES.keySet())) release(id); }
}

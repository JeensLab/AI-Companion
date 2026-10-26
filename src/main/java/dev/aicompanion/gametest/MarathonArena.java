package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * One marathon arena in a test: its size, Oleg and Толик on its ground, the observer, the chat and the script.
 * Positions are relative to the arena, like everything a GameTestHelper does.
 */
final class MarathonArena {
    final GameTestHelper h;
    final int sizeX, sizeY, sizeZ;
    CompanionEntity oleg;
    final ServerPlayer player;
    final MarathonWatch watch;
    final MarathonChat chat;
    final MarathonScript script;
    /** Where the player is being walked to, and how fast (blocks a tick); null when he stands. */
    private List<Vec3> route;
    private int routeIndex;
    private double speed;

    MarathonArena(GameTestHelper h, int sizeX, int sizeY, int sizeZ, int olegX, int olegZ, int playerX, int playerZ, int searchRadius) {
        this.h = h; this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
        h.assertTrue(ground(olegX, olegZ) != NO_GROUND && ground(playerX, playerZ) != NO_GROUND, "No ground to stand on at the start");
        oleg = h.spawn(AICompanionMod.COMPANION.get(), spot(olegX + .5, ground(olegX, olegZ), olegZ + .5));
        var mock = h.makeMockServerPlayerInLevel();
        Vec3 stand = h.absoluteVec(spot(playerX + .5, ground(playerX, playerZ), playerZ + .5));
        mock.teleportTo(stand.x, stand.y, stand.z);
        oleg.setOwnerId(mock.getUUID());
        mock.getPersistentData().putUUID("aicompanion:canonical_companion", oleg.getUUID());
        CompanionEvents.getOwnedCompanion(mock);
        oleg.setSearchRadius(searchRadius);
        player = h.getLevel().getServer().getPlayerList().getPlayer(oleg.getOwnerId());
        watch = new MarathonWatch(h, oleg, player, sizeX, sizeY, sizeZ);
        chat = new MarathonChat(h, oleg);
        script = new MarathonScript(h, watch, chat);
        MarathonTicks.each(h, this::walkPlayer);
    }

    /**
     * Empty air round every marathon arena in its template (32 blocks a side): the next test of the GameTest grid lies beyond
     * the reach of his searches. All coordinates of the tests are inside the arena, from its own corner.
     */
    static final int M = 32;
    BlockPos cell(int x, int y, int z) { return new BlockPos(x + M, y, z + M); }
    Vec3 spot(double x, double y, double z) { return new Vec3(x + M, y, z + M); }
    /** Where to put something standing on the ground of this column (relative to the helper, for h.spawn). */
    Vec3 at(int x, int z) { return spot(x + .5, ground(x, z), z + .5); }

    /** No place to stand in this column: never a step for the walking player. */
    static final int NO_GROUND = -10000;

    /** Feet height of the ground at a column: the highest spot with solid ground (not leaves) under it and room for a body. */
    int ground(int x, int z) {
        for (int y = sizeY - 2; y > 0; y--) {
            BlockState below = h.getBlockState(cell(x, y - 1, z));
            // On water the player swims at its surface.
            if (!below.getFluidState().isEmpty() && free(x, y, z) && free(x, y + 1, z) && h.getBlockState(cell(x, y, z)).getFluidState().isEmpty()) return y;
            if (below.isAir() || below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS)) continue;
            if (below.getCollisionShape(h.getLevel(), h.absolutePos(cell(x, y - 1, z))).isEmpty()) continue;
            if (free(x, y, z) && free(x, y + 1, z)) return y;
        }
        return NO_GROUND;
    }
    private boolean free(int x, int y, int z) {
        BlockPos pos = cell(x, y, z);
        // Leaves are no wall for the walking player: he breaks through a hanging crown.
        return h.getBlockState(pos).is(BlockTags.LEAVES) || h.getBlockState(pos).getCollisionShape(h.getLevel(), h.absolutePos(pos)).isEmpty();
    }

    /** Walks the player through these columns at about a player's walking pace, on the ground. */
    void walkPlayer(double blocksPerTick, int... xz) {
        route = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < xz.length; i += 2) route.add(new Vec3(xz[i] + .5, 0, xz[i + 1] + .5));
        routeIndex = 0; speed = blocksPerTick; routeBest = Double.MAX_VALUE; routeStill = 0;
    }
    boolean playerArrived() { return route == null; }

    /** After a death the companion who comes back is a new entity: the test follows him. */
    private void followRespawn() {
        if (oleg.isAlive() || h.getTick() % 10 != 0) return;
        CompanionEntity next = CompanionEvents.getOwnedCompanion(player);
        if (next != null && next != oleg && next.isAlive()) { oleg = next; watch.watch(next); chat.watch(next); }
    }

    private void walkPlayer() {
        followRespawn();
        if (route == null) return;
        Vec3 at = h.relativeVec(player.position()).subtract(M, 0, M);
        Vec3 next = route.get(routeIndex);
        double dx = next.x - at.x, dz = next.z - at.z, d = Math.sqrt(dx * dx + dz * dz);
        double x, z;
        // No nearer to the waypoint for three seconds (stepping round a tree back and forth): close enough if within four.
        if (d + .05 < routeBest) { routeBest = d; routeStill = 0; }
        else if (++routeStill > 60 && d < 4) { routeBest = Double.MAX_VALUE; routeStill = 0; if (++routeIndex >= route.size()) route = null; return; }
        // Near enough to a waypoint (a trunk may stand on it): on to the next one.
        if (d <= 1.5 && routeIndex + 1 < route.size()) { routeIndex++; routeBest = Double.MAX_VALUE; return; }
        if (d <= Math.max(speed, d < 2.5 && blocked > 0 ? 2.5 : 0)) { route = null; return; }
        if (d <= speed) { x = next.x; z = next.z; if (++routeIndex >= route.size()) route = null; }
        else { x = at.x + dx / d * speed; z = at.z + dz / d * speed; }
        // Like a player: a step up of one block or a drop of three; round a trunk or a boulder by a step across the way,
        // still going forward.
        double px = d < 1e-6 ? 0 : -dz / d, pz = d < 1e-6 ? 0 : dx / d;
        int fx = (int) Math.floor(x), fz = (int) Math.floor(z);
        for (double side : new double[]{0, 1, -1, 2, -2}) {
            double sx = x + px * side, sz = z + pz * side;
            int y = ground((int) Math.floor(sx), (int) Math.floor(sz));
            if (y - at.y > 1.25 || at.y - y > 2.5) continue;
            Vec3 to = h.absoluteVec(spot(sx, y, sz));
            player.teleportTo(to.x, to.y, to.z);
            blocked = 0;
            return;
        }
        // A wall higher than a step: a player jumps, climbs or puts a block; here he is set on top after a second.
        if (++blocked > 20) {
            double bx = at.x + dx / Math.max(d, 1e-6), bz = at.z + dz / Math.max(d, 1e-6);
            int gy = ground((int) Math.floor(bx), (int) Math.floor(bz));
            Vec3 to = h.absoluteVec(spot(bx, gy, bz));
            if (gy != NO_GROUND) player.teleportTo(to.x, to.y, to.z);
            blocked = 0;
        }
    }
    private int blocked, routeStill;
    private double routeBest = Double.MAX_VALUE;

    /** Teleports the player (a scene set up, not a walk). */
    void putPlayer(int x, int z) {
        route = null;
        Vec3 to = h.absoluteVec(spot(x + .5, ground(x, z), z + .5));
        player.teleportTo(to.x, to.y, to.z);
    }

    /**
     * Leaving the world and coming back, for Oleg: saved to NBT, removed, and a tick later loaded as a new entity with the same
     * UUID (as the game does). The plans of the old session are cancelled, as a closed world cancels them.
     */
    void reload(Runnable after) {
        var saved = new net.minecraft.nbt.CompoundTag();
        oleg.saveWithoutId(saved);
        oleg.discard();
        chat.worldClosed();
        h.runAfterDelay(1, () -> {
            CompanionEntity replacement = AICompanionMod.COMPANION.get().create(h.getLevel());
            replacement.load(saved);
            h.assertTrue(h.getLevel().addFreshEntity(replacement), "Reloaded companion rejected");
            oleg = replacement; watch.watch(replacement); chat.watch(replacement);
            after.run();
        });
    }

    int count(Item item) { return count(oleg, item); }

    static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : EquipmentSlot.values()) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }

    int countTag(net.minecraft.tags.TagKey<Item> tag) {
        int count = 0;
        for (int slot = 0; slot < oleg.getContainerSize(); slot++) if (oleg.getItem(slot).is(tag)) count += oleg.getItem(slot).getCount();
        for (EquipmentSlot slot : EquipmentSlot.values()) if (oleg.getItemBySlot(slot).is(tag)) count += oleg.getItemBySlot(slot).getCount();
        return count;
    }

    /** Items thrown to the player and lying at his feet or picked up: what he got. */
    int given(Item item) {
        int count = player.getInventory().countItem(item);
        for (var entity : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(6)))
            if (entity.getItem().is(item)) count += entity.getItem().getCount();
        return count;
    }

    double toPlayer() { return oleg.distanceTo(player); }

    /** A soft check: noted with the stage, the test fails at the end with every issue found. */
    void expect(boolean ok, String message) { watch.expect(ok, message); }
}

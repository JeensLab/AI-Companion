package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The marathon's observer: what Толик watching Oleg would notice, measured the same way on every arena. Positions, statuses,
 * self-rescues, the longest time stood still with nothing in hand, the longest walk round in circles, how far he went from the
 * player and from the work, and every item that fell in the arena and whether it was picked up.
 * Built in the test, ticked by {@code h.onEachTick(watch::tick)}; its trail goes into every failure message.
 */
final class MarathonWatch {
    private final GameTestHelper h;
    private CompanionEntity oleg;
    private final ServerPlayer player;
    private final AABB arena;
    private final List<String> trail = new ArrayList<>();
    private String lastLine = "";
    private long tick;
    /** Self-rescues begun, and when each one started (relative position and status). */
    final List<String> rescues = new ArrayList<>();
    private boolean rescuing;
    /** Stood within a block of one spot with nothing visibly in hand while he had something to do. */
    private Vec3 idleAnchor;
    private long idleSince = -1;
    long worstIdleTicks;
    String worstIdleAt = "";
    /** Walked a lot without getting anywhere: path length against net displacement over a sliding 15 seconds. */
    private final List<Vec3> recent = new ArrayList<>();
    double worstCircling;
    String worstCirclingAt = "";
    double farthestFromPlayer;
    /** Items that fell in the arena (not gifts thrown to the player): when first seen and what. */
    private final Map<UUID, String> fallen = new HashMap<>();
    private final Map<UUID, ItemEntity> fallenEntities = new HashMap<>();
    /** Oleg's deaths, with the game's own death message. */
    final List<String> deaths = new ArrayList<>();
    private boolean reportedDeath;
    void assertAlive() { expect(deaths.isEmpty(), "Died: " + deaths); }

    /** Jumps of more than three blocks in one tick: a teleport, not a walk. */
    final List<String> teleports = new ArrayList<>();
    private Vec3 lastPosition;
    /** Pause measurements while a scene is set up on purpose (the player walks away, a mob is spawned). */
    boolean paused;

    MarathonWatch(GameTestHelper h, CompanionEntity oleg, ServerPlayer player, int sizeX, int sizeY, int sizeZ) {
        this.h = h; this.oleg = oleg; this.player = player;
        BlockPos from = h.absolutePos(new BlockPos(MarathonArena.M, 0, MarathonArena.M)), to = h.absolutePos(new BlockPos(MarathonArena.M + sizeX, sizeY, MarathonArena.M + sizeZ));
        arena = new AABB(Vec3.atLowerCornerOf(from), Vec3.atLowerCornerOf(to)).inflate(2);
    }

    void tick() {
        tick++;
        if (!oleg.isAlive() && !reportedDeath) {
            reportedDeath = true;
            var cause = oleg.getLastDamageSource();
            deaths.add(tick + "@" + where(oleg.blockPosition()) + " " + (cause == null ? "?" : cause.getLocalizedDeathMessage(oleg).getString()));
            trail.add(tick + "@DIED " + deaths.getLast());
        }
        String status = oleg.getLastTaskResult();
        String line = where(oleg.blockPosition()) + " " + oleg.workPhase() + (oleg.isSelfRescuing() ? " RESCUE" : "") + ": " + status;
        if (!line.equals(lastLine)) { trail.add(tick + "@" + line); lastLine = line; if (trail.size() > 20000) trail.removeFirst(); }
        if (lastPosition != null && oleg.position().distanceTo(lastPosition) > 3) teleports.add(tick + "@" + where(BlockPos.containing(lastPosition)) + "->" + where(oleg.blockPosition()));
        lastPosition = oleg.position();
        if (tick % 100 == 0) trail.add(tick + "@PLAYER " + where(player.blockPosition()) + " oleg " + where(oleg.blockPosition()) + " d=" + Math.round(oleg.distanceTo(player)));
        // Where a self-rescue starts, the blocks round him go into the trail: what kind of hole it was.
        if (oleg.isSelfRescuing() && !rescuing) { rescues.add(tick + "@" + where(oleg.blockPosition()) + " " + oleg.workPhase() + ": " + status); if (rescueShots++ < 3) terrain(); }
        rescuing = oleg.isSelfRescuing();
        if (oleg.level() == player.level()) farthestFromPlayer = Math.max(farthestFromPlayer, oleg.distanceTo(player));
        idle();
        circling();
        if (tick % 10 == 0) for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, arena)) {
            if (item.getPersistentData().getBoolean("aicompanion:gift") || fallen.containsKey(item.getUUID())) continue;
            ItemStack stack = item.getItem();
            fallen.put(item.getUUID(), tick + "@" + where(item.blockPosition()) + " " + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + "x" + stack.getCount());
            fallenEntities.put(item.getUUID(), item);
        }
    }

    /** Something to do: a work order, or following a player out of reach. Then a still spot without a visible action is idling. */
    private boolean hasSomethingToDo() {
        if (paused || !oleg.isAlive()) return false;
        boolean far = oleg.level() == player.level() && oleg.distanceToSqr(player) > 8 * 8;
        return oleg.hasActiveWorkOrder() || (oleg.isFollowing() || oleg.isProtecting()) && far;
    }

    private void idle() {
        Vec3 at = oleg.position();
        boolean still = idleAnchor != null && at.distanceToSqr(idleAnchor) < 1;
        if (!still) idleAnchor = at;
        if (!still || !hasSomethingToDo() || oleg.isVisiblyBusy() || cooking()) { idleSince = -1; return; }
        if (idleSince < 0) idleSince = tick;
        long idle = tick - idleSince;
        // Why he stands: his inner state, written into the trail every 3 seconds of standing.
        if (idle > 0 && idle % 60 == 0) trail.add(tick + "@IDLE " + idle / 20 + "s " + oleg.watchDetail() + " items=" + nearItems());
        if (idle == 20 * 20 && terrainShots++ < 3) terrain();
        if (idle > worstIdleTicks) { worstIdleTicks = idle; worstIdleAt = (idleSince) + "@" + where(oleg.blockPosition()) + " " + oleg.workPhase() + ": " + oleg.getLastTaskResult(); }
    }

    private int terrainShots, rescueShots;
    /** The blocks around him, layer by layer (O is Oleg's feet, i an item, # stone, d soil, ~ water, * leaves, L log, . air). */
    private void terrain() { terrain(oleg.blockPosition(), trail); }
    /** The blocks around him now, into the trail: for a test that wants to see where something went wrong. */
    void snapshot() { terrain(); }
    private void terrain(BlockPos at, List<String> out) {
        java.util.Set<BlockPos> items = new java.util.HashSet<>();
        for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(8))) items.add(item.blockPosition());
        for (int dy = 4; dy >= -3; dy--) {
            StringBuilder b = new StringBuilder(tick + "@TERRAIN y=" + (at.getY() + dy - h.absolutePos(BlockPos.ZERO).getY()) + " x from " + where(at.offset(-6, dy, -6)) + ": ");
            for (int dz = -6; dz <= 6; dz++) {
                for (int dx = -6; dx <= 6; dx++) {
                    BlockPos pos = at.offset(dx, dy, dz);
                    var state = h.getLevel().getBlockState(pos);
                    char c = pos.equals(at) ? 'O' : items.contains(pos) ? 'i' : !state.getFluidState().isEmpty() ? '~' : state.isAir() ? '.'
                            : state.is(net.minecraft.tags.BlockTags.LEAVES) ? '*' : state.is(net.minecraft.tags.BlockTags.LOGS) ? 'L'
                            : state.is(net.minecraft.world.level.block.Blocks.STONE) || state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE) ? '#'
                            : state.is(net.minecraft.tags.BlockTags.DIRT) ? 'd' : state.getCollisionShape(h.getLevel(), pos).isEmpty() ? ',' : '+';
                    b.append(c);
                }
                b.append('/');
            }
            out.add(b.toString());
        }
    }

    private String nearItems() {
        StringBuilder b = new StringBuilder();
        for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, oleg.getBoundingBox().inflate(8)))
            b.append(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath()).append('@').append(String.format("%.1f,%.1f,%.1f",
                    item.getX() - h.absolutePos(BlockPos.ZERO).getX() - MarathonArena.M, item.getY() - h.absolutePos(BlockPos.ZERO).getY(), item.getZ() - h.absolutePos(BlockPos.ZERO).getZ() - MarathonArena.M))
                    .append(item.onGround() ? "g" : "a").append(item.hasPickUpDelay() ? "d" : "").append(' ');
        return b.toString();
    }

    /** Waiting at a loaded furnace is honest waiting, like a player watching the fire. */
    private boolean cooking() { return oleg.workPhase().endsWith("/COOK"); }

    private void circling() {
        if (tick % 20 != 0) return;
        if (!hasSomethingToDo()) { recent.clear(); return; }
        recent.add(oleg.position());
        if (recent.size() > 15) recent.removeFirst();
        if (recent.size() < 15) return;
        double walked = 0;
        for (int i = 1; i < recent.size(); i++) walked += recent.get(i).distanceTo(recent.get(i - 1));
        double net = recent.getFirst().distanceTo(recent.getLast());
        double circling = walked - 3 * net;
        if (circling > worstCircling) { worstCircling = circling; worstCirclingAt = tick + "@" + where(oleg.blockPosition()) + " walked " + Math.round(walked) + ", net " + Math.round(net) + " " + oleg.workPhase() + ": " + oleg.getLastTaskResult(); }
    }

    /** Fallen items still lying in the arena, not counting what nobody needs (saplings, sticks and apples from leaves). */
    List<String> leftOnGround() {
        List<String> left = new ArrayList<>();
        fallenEntities.forEach((id, item) -> {
            if (!item.isAlive()) return;
            String name = BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath();
            if (name.endsWith("sapling") || name.equals("stick") || name.equals("apple") || name.endsWith("_seeds") || name.equals("rotten_flesh")
                    || name.equals("bone") || name.equals("arrow") || name.equals("string") || name.equals("spider_eye") || name.equals("gunpowder")) return;
            left.add(fallen.get(id) + " now " + where(item.blockPosition()));
        });
        return left;
    }

    /** A line of the test's own into the trail. */
    void note(String line) { trail.add(tick + "@NOTE " + line); }

    /** After a reload the companion is a new entity with the same UUID. */
    void watch(CompanionEntity reloaded) { oleg = reloaded; lastPosition = null; reportedDeath = false; }

    /** Relative position (GameTestHelper.relativePos mirrors an unrotated test, so subtract the origin here). */
    String where(BlockPos pos) { return pos.subtract(h.absolutePos(new BlockPos(MarathonArena.M, 0, MarathonArena.M))).toShortString().replace(" ", ""); }

    String trail() { return String.join(" | ", trail.subList(Math.max(0, trail.size() - 25), trail.size())); }

    /** The whole trail and the item log into run/marathon/<name>.txt, for reading a failure step by step. */
    void dump(String name, String extra) {
        try {
            var dir = java.nio.file.Path.of("marathon");
            java.nio.file.Files.createDirectories(dir);
            List<String> lines = new ArrayList<>(trail);
            lines.add("---- fallen items");
            fallen.forEach((id, what) -> lines.add(what + (fallenEntities.get(id).isAlive() ? " LEFT at " + where(fallenEntities.get(id).blockPosition()) : " gone")));
            for (var entry : fallenEntities.entrySet()) if (entry.getValue().isAlive()) { lines.add("---- left item " + fallen.get(entry.getKey())); terrain(entry.getValue().blockPosition(), lines); }
            lines.add("---- issues");
            lines.addAll(issues);
            lines.add("---- " + summary());
            lines.add(extra);
            java.nio.file.Files.write(dir.resolve(name + ".txt"), lines);
        } catch (java.io.IOException ignored) { }
    }

    String summary() {
        return "idle " + worstIdleTicks / 20 + "s at " + worstIdleAt + "; circling " + Math.round(worstCircling) + " at " + worstCirclingAt
                + "; rescues " + rescues.size() + " " + rescues + "; farthest from player " + Math.round(farthestFromPlayer);
    }

    // ---- the checks «как у человека» ----

    /** Problems found so far, each with the stage it was seen in; the script fails the test with all of them at the end. */
    final List<String> issues = new ArrayList<>();
    String section = "start";
    private final List<String> allRescues = new ArrayList<>();

    void expect(boolean ok, String message) {
        if (ok) return;
        String bare = message.contains(" || ") ? message.substring(0, message.indexOf(" || ")) : message;
        issues.add("[" + section + "] " + bare);
        trail.add(tick + "@ISSUE [" + section + "] " + bare);
    }

    /** A new stretch of the chain: the idle, circling, distance and rescue measures start again (items on the ground stay). */
    void newSection(String name) {
        section = name; worstIdleTicks = 0; worstIdleAt = ""; worstCircling = 0; worstCirclingAt = ""; farthestFromPlayer = 0;
        allRescues.addAll(rescues); rescues.clear(); idleSince = -1; recent.clear();
    }

    /** Self-rescues over the whole test. */
    List<String> everyRescue() { List<String> all = new ArrayList<>(allRescues); all.addAll(rescues); return all; }

    void assertNoLongIdle(int seconds) {
        expect(worstIdleTicks <= seconds * 20L, "Stood still " + worstIdleTicks / 20 + "s (limit " + seconds + "s) at " + worstIdleAt + " || " + trail());
    }

    void assertNoCircling(double limit) {
        expect(worstCircling <= limit, "Walked in circles: " + worstCirclingAt + " || " + trail());
    }

    void assertNoTeleport() { expect(teleports.isEmpty(), "Teleported instead of walking: " + teleports); }

    void assertNoRescue() {
        expect(rescues.isEmpty(), "Self-rescue where a player just walks: " + rescues + " || " + trail());
    }

    void assertNothingLeft() {
        var left = leftOnGround();
        expect(left.isEmpty(), "Left on the ground: " + left + " || " + trail());
    }

    void assertWithPlayer(double distance) {
        expect(oleg.level() == player.level() && oleg.distanceTo(player) <= distance,
                "Not back with the player: " + Math.round(oleg.distanceTo(player)) + " blocks, at " + where(oleg.blockPosition()) + " || " + trail());
    }

    void assertStayedNear(double distance) {
        expect(farthestFromPlayer <= distance, "Went " + Math.round(farthestFromPlayer) + " blocks from the player (limit " + distance + ") || " + trail());
    }
}

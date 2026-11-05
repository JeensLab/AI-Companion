package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The walk back to where he fell. The place carries the manifest of the stacks that fell from him, and the walk is a walk
 * to those stacks, one by one: the pickup's own reach decides arrival, the game's own route (CompanionMiningNavigation)
 * decides the way — through water, up steps, down drops — and the manifest decides what is his.
 * <p>
 * Every stack is judged in a ledger: taken, part of it, gone (somebody else had it), or unreachable with a reason. The
 * report is written from that ledger alone, so «Забрал свои вещи» is said only when every stack of the walk is in his bag.
 * <p>
 * A place in a chunk nobody has loaded is walked to all the same; what lies there is judged only once the chunk is loaded,
 * because an unloaded chunk is not an empty one.
 */
public final class CompanionDeathTrip {
    /** What became of one stack of his: the ledger the report is written from. */
    public enum Fate { PENDING, TAKEN, PARTIAL, GONE, UNREACHABLE }

    /** One stack's outcome: how many of it reached the bag, what stayed, and why. */
    public record Verdict(Fate fate, int taken, int left, String reason) { }

    /** A walk longer than this, with a block's worth of time for every block of the way, is a walk that got stuck. */
    private static final int PATIENCE_TICKS = 20 * 120;
    private static final int TICKS_PER_BLOCK = 4;
    /** A walk that has been at it this long ends, whatever the game clock says. */
    private static final int MOST_TICKS = 20 * 90;
    /** Standing in the pickup's reach this long: it did not take the stack, name the reason and go on. */
    private static final int WAIT_IN_REACH = 60;
    /** Walking at one stack this long without getting into its reach: give it up with a reason and go on. */
    private static final int APPROACH_PATIENCE = 200;
    /** The stack drifts (water, a slope, a shove): the way to it is searched again this often. */
    private static final int REPATH = 5;
    /** A second order inside this window means the player insists, and his own walk gives way. */
    private static final long INSIST_WINDOW = 20 * 20;
    /** A place that keeps something behind is not tried again for this long (his bag may be full). */
    private static final int RETRY_TICKS = 20 * 30;
    /** A place in another dimension is worth a crossing only for things that are really dear: a portal costs more. */
    private static final double CROSS_WORTH = 20;
    /** Standing within this of the place itself is close enough to start judging it on foot. */
    private static final double NEAR = 1.2;

    private final CompanionEntity c;
    private CompanionDeathSpots.Spot spot;
    /** Every stack the walk set out for, as the manifest stood when it began: the report covers all of them. */
    private List<CompanionDeathSpots.Entry> original = List.of();
    /** The ledger: what became of each stack of this walk. */
    private final Map<UUID, Verdict> ledger = new LinkedHashMap<>();
    private UUID target;
    private BlockPos routedPos;
    private int repathAt;
    private int entryTicks;
    private int approach;
    private long giveUpAt;
    /** The trip's own ticks: the game's counter advances only for entities the level ticks, a trip is bounded by its own. */
    private int ticks;
    private boolean crossing;
    private boolean refused;
    private long refusedAt;
    /** When this place was last tried (the game clock), so a place that keeps refusing is not tried again at once. */
    private final Map<BlockPos, Long> triedAt = new HashMap<>();
    private String lastLine = "";

    CompanionDeathTrip(CompanionEntity c) { this.c = c; }

    public boolean active() { return spot != null; }

    /** Where he is walking to, for the death hook. */
    public BlockPos place() { return spot == null ? null : spot.pos(); }

    /** The id of the place he is walking to, so a death on the way is charged to this record and not to a neighbour. */
    public UUID spotId() { return spot == null ? null : spot.id(); }

    /** The dimension of the place he is walking to, for the death hook. */
    public net.minecraft.resources.ResourceKey<Level> dimension() {
        return spot == null ? null : spot.dimension();
    }

    /** The last thing he said about the walk, for the tests that check he does not claim what he did not take. */
    public String lastLine() { return lastLine; }

    /** The ledger of the walk, by stack: the truth the report is written from (for the task screen and the tests). */
    public Map<UUID, Verdict> ledger() { return Map.copyOf(ledger); }

    /** What became of one stack of this walk, or null when it is not part of it. */
    public Verdict verdict(UUID stack) { return ledger.get(stack); }

    /** What he is about, for the task screen. */
    public String describe() {
        if (spot == null) return "";
        int left = 0;
        for (CompanionDeathSpots.Entry entry : original) {
            Verdict verdict = ledger.get(entry.id());
            if (verdict == null || verdict.fate() == Fate.PENDING) left++;
        }
        return "Иду за своими вещами: " + spot.pos().toShortString() + (left > 0 ? ", осталось " + left + "." : ".");
    }

    /** Sets out for the dearest place with a stack still lying there; false when there is nothing worth or possible to walk to. */
    public boolean begin() {
        if (spot != null || !c.deathRecovery() || !(c.level() instanceof ServerLevel level) || c.getOwnerId() == null) return false;
        var server = level.getServer();
        if (server == null) return false;
        CompanionDeathSpots data = CompanionDeathSpots.get(server);
        List<CompanionDeathSpots.Spot> places = new ArrayList<>(data.all(c.getOwnerId()));
        places.sort(Comparator.comparingDouble(CompanionDeathSpots.Spot::worth).reversed());
        for (CompanionDeathSpots.Spot place : places) {
            if (place.attempts() >= c.deathRecoveryAttempts() || place.deaths() >= c.deathRecoveryDeaths()) {
                data.taken(c.getOwnerId(), place.id());
                say("Ну и подавитесь своим лутом.");
                continue;
            }
            Long tried = triedAt.get(place.pos());
            if (tried != null && level.getGameTime() - tried < RETRY_TICKS) continue;
            // Only the stacks of the manifest that still lie there are walked to: a stack somebody took is struck off
            // before the walk starts, so a looted place costs no walk at all.
            List<CompanionDeathSpots.Entry> live = liveEntries(level, place);
            if (live.isEmpty()) {
                data.taken(c.getOwnerId(), place.id());
                say("Вещей на месте уже нет.");
                continue;
            }
            if (!place.dimension().equals(level.dimension())) {
                // Only the Nether can be crossed on foot: the End needs its own portal, so a place there is left.
                if (place.worth() < CROSS_WORTH || !place.dimension().equals(Level.NETHER)) {
                    data.taken(c.getOwnerId(), place.id());
                    continue;
                }
                // No portal within reach right now is not a reason to forget a dear place: it waits for next time.
                var entered = c.portals.enter(level);
                if (!entered.success()) continue;
                data.attempted(c.getOwnerId(), place.id());
                start(place, live, level, true);
                say("Иду за своими вещами через портал.");
                return true;
            }
            data.attempted(c.getOwnerId(), place.id());
            start(place, live, level, false);
            say("Иду за своими вещами.");
            return true;
        }
        return false;
    }

    /** The stacks of a place that still lie there, judged only where the chunk is loaded: an unloaded chunk is not empty. */
    private List<CompanionDeathSpots.Entry> liveEntries(ServerLevel level, CompanionDeathSpots.Spot place) {
        ServerLevel there = place.dimension().equals(level.dimension()) ? level : level.getServer().getLevel(place.dimension());
        List<CompanionDeathSpots.Entry> live = new ArrayList<>();
        for (CompanionDeathSpots.Entry entry : place.manifest()) {
            if (there == null || !there.hasChunkAt(entry.blockPos()) || alive(there, entry) != null) live.add(entry);
            else CompanionDeathSpots.get(level.getServer()).pruneEntry(c.getOwnerId(), place.id(), entry.id());
        }
        return live;
    }

    private void start(CompanionDeathSpots.Spot place, List<CompanionDeathSpots.Entry> live, ServerLevel level, boolean cross) {
        spot = place;
        original = List.copyOf(live);
        ledger.clear();
        for (CompanionDeathSpots.Entry entry : live) ledger.put(entry.id(), new Verdict(Fate.PENDING, 0, entry.count(), ""));
        target = null;
        routedPos = null;
        repathAt = 0;
        entryTicks = 0;
        approach = 0;
        crossing = cross;
        refused = false;
        ticks = 0;
        giveUpAt = level.getGameTime() + PATIENCE_TICKS * 2
                + (long) (Math.sqrt(c.blockPosition().distSqr(place.pos())) * TICKS_PER_BLOCK);
        // Water is walked like a player walks it: he wades, and he swims where it is deep enough to float.
        c.getNavigation().setCanFloat(true);
    }

    /** One tick of the walk: true while it goes on. */
    public boolean tick() {
        if (spot == null) return false;
        if (!(c.level() instanceof ServerLevel level)) { stop(); return false; }
        // The player turned the feature off: the walk ends here, as he asked.
        if (!c.deathRecovery()) { stop(); say("Ладно, вещи подождут."); return false; }
        ticks++;
        if (crossing) {
            if (level.dimension().equals(spot.dimension())) { crossing = false; say("Перешёл, иду к вещам."); return true; }
            if (!c.portals.walking()) { stop(); say("Не смог перейти в другое измерение."); return false; }
            if (ticks > MOST_TICKS) { stop(); say("Портал не пустил."); return false; }
            return true;
        }
        // Moved out of the dimension by something else: back through a portal, or the place is left.
        if (!level.dimension().equals(spot.dimension())) {
            if (!c.portals.walking()) {
                var entered = c.portals.enter(level);
                if (!entered.success()) { stop(); say("Оказался в другом измерении, портала рядом нет."); return false; }
                crossing = true;
                giveUpAt = level.getGameTime() + PATIENCE_TICKS * 2;
                say("Возвращаюсь к своим вещам через портал.");
            }
            return true;
        }
        if (ticks > MOST_TICKS || level.getGameTime() > giveUpAt) {
            finish(level);
            return false;
        }
        // Not loaded yet: the walk goes to the coordinates and the chunk loads on the way; nothing can be judged before that.
        if (!level.hasChunkAt(spot.pos())) {
            c.movementIntent(spot.pos());
            c.travel().toward(spot.pos(), NEAR);
            return true;
        }
        refresh(level);
        CompanionDeathSpots.Entry entry = nextEntry(level);
        if (entry == null) {
            // Nothing left to walk at now, but a stack in a chunk nobody has loaded yet is not gone: walk to the place.
            if (unjudged(level)) { c.movementIntent(spot.pos()); c.travel().toward(spot.pos(), NEAR); return true; }
            finish(level);
            return false;
        }
        ItemEntity item = alive(level, entry);
        if (item == null) return true;
        // A new stack to fetch: the patience and the way belong to this stack, not to the walk.
        if (!entry.id().equals(target)) { target = entry.id(); entryTicks = 0; approach = 0; repathAt = 0; routedPos = null; }
        c.movementIntent(item.blockPosition());
        // Arrival is the pickup's own reach, not a guess of two blocks: from there the game's pickup takes the stack.
        if (c.loot.touches(c.position(), item)) {
            c.getNavigation().stop();
            if (entryTicks == 0 && !c.canStoreDrops(List.of(item.getItem().copyWithCount(1)))) makeRoom(level);
            entryTicks++;
            if (entryTicks > WAIT_IN_REACH) giveUpEntry(entry, item, inReachReason(item));
            return true;
        }
        approach++;
        if (approach > APPROACH_PATIENCE) { giveUpEntry(entry, item, "не дошёл"); return true; }
        boolean drifted = routedPos == null || !routedPos.equals(item.blockPosition());
        if (drifted || ticks >= repathAt || c.getNavigation().isDone() || c.getNavigation().isStuck()) {
            repathAt = ticks + REPATH;
            routedPos = item.blockPosition().immutable();
            Set<BlockPos> cells = standCells(level, item);
            if (cells.isEmpty()) { giveUpEntry(entry, item, standReason(level, item)); return true; }
            if (!(c.getNavigation() instanceof CompanionNavigation nav)) return true;
            // The way is searched whole, not in this tick's share: a walk to one stack must decide on that stack now.
            Path path = nav.searchNow(cells, 0);
            // No cell to stand in yet (he is in the air, on a ladder): not a verdict, ask again next tick.
            if (path == null) return true;
            if (!path.canReach()) {
                double range = c.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
                if (item.distanceToSqr(c) <= range * range) {
                    dev.aicompanion.AICompanionMod.LOGGER.info("[death] no route from {} to {} ({} cells)",
                            c.blockPosition(), item.blockPosition(), cells.size());
                    giveUpEntry(entry, item, "нет пути");
                    return true;
                }
                // A stack farther than one search sees: walk the leg it does see, and search again from there.
            }
            CompanionMiningNavigation.walk(c, path);
        }
        return true;
    }

    /** Judges every stack of the manifest that can be seen: gone ones leave it, taken ones are entered in the ledger. */
    private void refresh(ServerLevel level) {
        for (CompanionDeathSpots.Entry entry : original) {
            Verdict known = ledger.get(entry.id());
            if (known != null && known.fate() != Fate.PENDING) continue;
            if (!level.hasChunkAt(entry.blockPos())) continue;
            ItemEntity item = alive(level, entry);
            int mine = c.loot.takenByMe(entry.id());
            if (item != null) {
                // Still lying there: part of it in his bag is a part taken, the rest is still his errand.
                if (mine > 0) ledger.put(entry.id(), new Verdict(Fate.PENDING, mine, item.getItem().getCount(), ""));
                continue;
            }
            Verdict now;
            if (mine >= entry.count()) now = new Verdict(Fate.TAKEN, entry.count(), 0, "");
            else if (mine > 0) now = new Verdict(Fate.PARTIAL, mine, 0, "остальное пропало");
            else now = new Verdict(Fate.GONE, 0, 0, goneReason(level, entry));
            ledger.put(entry.id(), now);
            CompanionDeathSpots.get(level.getServer()).pruneEntry(c.getOwnerId(), spot.id(), entry.id());
        }
    }

    /** The nearest stack still to fetch, among the manifest entries that lie in a loaded chunk. */
    private CompanionDeathSpots.Entry nextEntry(ServerLevel level) {
        CompanionDeathSpots.Entry best = null;
        double bestDistance = Double.MAX_VALUE;
        for (CompanionDeathSpots.Entry entry : original) {
            Verdict verdict = ledger.get(entry.id());
            if (verdict == null || verdict.fate() != Fate.PENDING) continue;
            ItemEntity item = alive(level, entry);
            if (item == null) continue;
            double distance = item.distanceToSqr(c);
            if (distance < bestDistance) { bestDistance = distance; best = entry; }
        }
        return best;
    }

    /** A stack of the walk whose chunk is not loaded yet: not gone, just not seen. */
    private boolean unjudged(ServerLevel level) {
        for (CompanionDeathSpots.Entry entry : original) {
            Verdict verdict = ledger.get(entry.id());
            if (verdict != null && verdict.fate() != Fate.PENDING) continue;
            if (!level.hasChunkAt(entry.blockPos())) return true;
        }
        return false;
    }

    private ItemEntity alive(ServerLevel level, CompanionDeathSpots.Entry entry) {
        Entity entity = level.getEntity(entry.id());
        if (entity instanceof ItemEntity item && item.isAlive() && !item.getItem().isEmpty()) return item;
        return null;
    }

    /** Why a stack that is no longer there is gone, in the game's own terms. */
    private String goneReason(ServerLevel level, CompanionDeathSpots.Entry entry) {
        if (CompanionLava.at(level, entry.blockPos())) return "сгорело в лаве";
        var state = level.getBlockState(entry.blockPos());
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return "сгорело";
        // Vanilla merges a stack into a neighbour of the same item within half a block; the survivor is not this entity.
        for (ItemEntity other : level.getEntitiesOfClass(ItemEntity.class, new AABB(entry.pos().add(-.5, -.5, -.5), entry.pos().add(.5, .5, .5)),
                found -> found.isAlive() && !found.getItem().isEmpty() && found.getItem().getItem() == itemOf(entry)))
            return "слилось с соседней стопкой";
        return "пропало";
    }

    private static net.minecraft.world.item.Item itemOf(CompanionDeathSpots.Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.item());
        return id == null ? null : BuiltInRegistries.ITEM.get(id);
    }

    /** The cells he can be in and take the stack from: the pickup's own reach decides, the game's own standability the rest. */
    private Set<BlockPos> standCells(ServerLevel level, ItemEntity item) {
        Set<BlockPos> cells = new HashSet<>();
        BlockPos center = item.blockPosition();
        for (int dy = -2; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos cell = center.offset(dx, dy, dz);
            if (!canBeAt(level, cell)) continue;
            if (!c.loot.touches(Vec3.atBottomCenterOf(cell), item)) continue;
            cells.add(cell.immutable());
        }
        return cells;
    }

    /**
     * A cell he can be in: standing on a floor, holding a ladder or vine (nothing under his feet there), or swimming where
     * the water is deep enough to float him.
     */
    private boolean canBeAt(ServerLevel level, BlockPos cell) {
        if (CompanionMiningNavigation.standable(c, cell)) return true;
        if (CompanionLava.at(level, cell)) return false;
        if (level.getBlockState(cell).is(net.minecraft.tags.BlockTags.CLIMBABLE)) return level.noCollision(c, bodyAt(cell));
        if (!level.getFluidState(cell).is(FluidTags.WATER)) return false;
        return level.noCollision(c, bodyAt(cell));
    }

    private AABB bodyAt(BlockPos cell) {
        return new AABB(cell.getX() + .2, cell.getY(), cell.getZ() + .2,
                cell.getX() + .8, cell.getY() + c.getBbHeight(), cell.getZ() + .8);
    }

    /** Why no cell takes the stack: lava, fire, a cactus in the way, or simply nowhere to stand. */
    private String standReason(ServerLevel level, ItemEntity item) {
        BlockPos at = item.blockPosition();
        if (CompanionLava.at(level, at)) return "в лаве";
        var state = level.getBlockState(at);
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return "в огне";
        if (at.getY() < level.getMinBuildHeight()) return "в пустоте";
        return "негде встать";
    }

    /** Why a stack in reach was not taken: the game's own reasons, not a guess. */
    private String inReachReason(ItemEntity item) {
        if (item.hasPickUpDelay()) return "нельзя взять сразу";
        // The game lets only the stack's target take it, and that is never the companion.
        if (item.getTarget() != null) return "метит в другого";
        if (!c.canStoreDrops(List.of(item.getItem().copyWithCount(1)))) return "сумка полна";
        return "не берётся";
    }

    /** Records what became of one stack and moves on to the next: never a long stand at one place. */
    private void giveUpEntry(CompanionDeathSpots.Entry entry, ItemEntity item, String reason) {
        int mine = c.loot.takenByMe(entry.id());
        int left = item == null ? 0 : item.getItem().getCount();
        Fate fate;
        if (left > 0) fate = mine > 0 ? Fate.PARTIAL : Fate.UNREACHABLE;
        else if (mine >= entry.count()) fate = Fate.TAKEN;
        else fate = mine > 0 ? Fate.PARTIAL : Fate.GONE;
        ledger.put(entry.id(), new Verdict(fate, mine, left, reason));
        target = null;
        routedPos = null;
        repathAt = 0;
        entryTicks = 0;
        approach = 0;
        c.getNavigation().stop();
        c.work.route.stop();
        dev.aicompanion.AICompanionMod.LOGGER.info("[death] stack {}: {} ({})", entry.id(), fate, reason);
    }

    /**
     * Done for now: the report comes from the ledger alone. Everything of the walk in his bag is «Забрал свои вещи»; a
     * stack still lying there is named with the reason it stayed, and the place waits for the next walk; nothing left at
     * all forgets the place.
     */
    private void finish(ServerLevel level) {
        refresh(level);
        int taken = 0, gone = 0;
        List<String> left = new ArrayList<>();
        for (CompanionDeathSpots.Entry entry : original) {
            Verdict verdict = ledger.get(entry.id());
            if (verdict == null) verdict = new Verdict(Fate.PENDING, 0, entry.count(), "не дошёл");
            switch (verdict.fate()) {
                case TAKEN -> taken++;
                case GONE -> gone++;
                case PARTIAL -> left.add(name(entry) + " — осталось " + verdict.left() + " из " + entry.count() + ", " + verdict.reason());
                case UNREACHABLE -> left.add(name(entry) + " — " + verdict.reason());
                case PENDING -> left.add(name(entry) + " — не дошёл");
            }
        }
        boolean everything = !original.isEmpty() && taken == original.size();
        if (everything) {
            forget(level);
            say("Забрал свои вещи.");
            return;
        }
        if (!left.isEmpty()) {
            // Something of his is still lying there: the place waits, and is tried again in tens of seconds, not minutes.
            triedAt.put(spot.pos(), level.getGameTime());
            stop();
            say("Часть моих вещей осталась: " + String.join("; ", left) + ".");
            return;
        }
        forget(level);
        say(taken > 0 ? "Забрал свои вещи, остальное пропало." : "Вещей на месте уже нет.");
    }

    private void forget(ServerLevel level) {
        CompanionDeathSpots.get(level.getServer()).taken(c.getOwnerId(), spot.id());
        stop();
    }

    private String name(CompanionDeathSpots.Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.item());
        if (id == null) return entry.item();
        var item = BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item).getHoverName().getString();
    }

    /** A full bag is made room in by letting the cheapest junk go, never a diamond; what is dropped is marked as junk. */
    private void makeRoom(ServerLevel level) {
        while (room() == 0) {
            int worst = -1;
            double worstWorth = Double.MAX_VALUE;
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (stack.isEmpty()) continue;
                double worth = CompanionBelongings.worth(stack);
                if (worth < worstWorth) { worstWorth = worth; worst = slot; }
            }
            if (worst < 0 || worstWorth >= CompanionBelongings.WORTH_GOING) return;
            ItemStack stack = c.getItem(worst);
            c.setItem(worst, ItemStack.EMPTY);
            ItemEntity junk = c.spawnAtLocation(stack, .5F);
            // Junk he let go to make room: never part of a death place again, whoever picks it up.
            if (junk != null) junk.getPersistentData().putBoolean(CompanionBag.JUNK, true);
        }
    }

    /** An empty slot: a new stack needs one, a half stack of dirt will not take a diamond. */
    private int room() {
        int free = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).isEmpty()) free++;
        return free;
    }

    /**
     * An order arrived. True: he is busy with his own things and says so once. False: the player asked a second time
     * and the walk gives way to the order.
     */
    boolean refuseOrder() {
        if (spot == null) return false;
        long now = c.level().getGameTime();
        if (refused && now - refusedAt <= INSIST_WINDOW) {
            // The walk gives way, but the place stays: his things are still lying there.
            stop();
            say("Ладно, твоё дело важнее.");
            return false;
        }
        refused = true;
        refusedAt = now;
        return true;
    }

    public String refusalLine() { return "Сейчас только вещи заберу свои после смерти."; }

    /** A new fall at a place clears the wait that a previous walk there left behind. */
    public void freshPlace(net.minecraft.core.BlockPos pos) { triedAt.remove(pos); }

    /** Stops the walk but keeps the place: he died on the way, and his things are still there. */
    void stop() {
        spot = null;
        original = List.of();
        c.travel().reset();
        c.movementIntent(null);
        c.getNavigation().stop();
    }

    void cancel(String why) {
        if (spot != null && c.level().getServer() != null) CompanionDeathSpots.get(c.level().getServer()).taken(c.getOwnerId(), spot.id());
        stop();
        if (why != null && !why.isEmpty()) say(why);
    }

    private void say(String line) {
        lastLine = line;
        c.autonomySay(line);
        dev.aicompanion.AICompanionMod.LOGGER.info("[death] says: {}", line);
    }
}

package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.ItemTags;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Harvested items are normal world entities. Transfer to the bag requires physical proximity. */
final class CompanionLoot {
    private static final String HARVESTER = "aicompanion:harvester";
    /** How near a creeper in sight must be for the pickup to wait, whether or not he fights it: the drop may lie beside it. */
    static final double CREEPER_NEAR = 7;
    private final CompanionEntity owner;
    private final Set<UUID> pending = new HashSet<>();
    private final Set<UUID> unreachable = new HashSet<>();
    private final Map<UUID, Long> born = new HashMap<>();
    private ItemEntity target;
    private Path path;
    private Vec3 pickupPoint;
    private long lastProgress;
    private double bestDistance;
    private int collectedLogs;
    private int collectedStone;
    private int collectedSoil;
    /** Marked belongings of a death taken up: the proof a walk back actually fetched them. */
    private int belongingsTaken;
    int belongingsTaken() { return belongingsTaken; }
    /** How many of each stack this companion itself moved into the bag: the ledger of a death walk, by entity. */
    private final Map<UUID, Integer> takenByMe = new HashMap<>();
    int takenByMe(UUID id) { return takenByMe.getOrDefault(id, 0); }

    private final int[] collectedLoose = new int[CompanionGround.values().length];
    private long rescanUntil;
    private boolean collecting;
    private long lastScan = Long.MIN_VALUE;
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Map<UUID, Long> retryAt = new HashMap<>();
    private long nextRoute;
    private Vec3 routedItem;
    /** Where the drop lay when the chase for it began: a bob there is still the same try, a drift away is not. */
    private Vec3 chaseFrom;
    // The way to the drop is longer than one search: walked in legs, and where he was when last seen moving on.
    private boolean farRoute;
    private long farUntil;
    private Vec3 farMoved;
    private record Site(BlockPos pos, List<ItemStack> drops, long expires) { }
    private final java.util.Deque<Site> sites = new java.util.ArrayDeque<>();
    /** A leaf of a tree between him and a drop, being broken out of the way. */
    private BlockPos leaf;
    private double leafBest;
    private long leafProgress;
    private final CompanionBlockBreaking leafBreaking;
    private final Map<UUID, Integer> leavesCleared = new HashMap<>();
    CompanionLoot(CompanionEntity owner) { this.owner = owner; this.leafBreaking = new CompanionBlockBreaking(owner); }

    void drop(BlockPos pos, List<ItemStack> drops) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        collecting = true;
        sites.addLast(new Site(pos.immutable(), drops.stream().map(ItemStack::copy).toList(), level.getGameTime() + 20 * 300));
        while (sites.size() > 256) sites.removeFirst();
        rescanUntil = level.getGameTime() + 40;
        for (ItemStack stack : drops) if (!stack.isEmpty()) {
            ItemEntity item = new ItemEntity(level, pos.getX() + .5, pos.getY() + .35, pos.getZ() + .5, stack.copy());
            item.setDeltaMovement((level.random.nextDouble() - .5) * .08, .12, (level.random.nextDouble() - .5) * .08);
            item.setPickUpDelay(10);
            item.getPersistentData().putUUID(HARVESTER, owner.getUUID());
            if (level.addFreshEntity(item)) { pending.add(item.getUUID()); born.put(item.getUUID(), level.getGameTime()); }
        }
        owner.syncWorkState();
    }
    /** Drops of an animal he killed: tagged as his and picked up like his harvest (the hunt counts them in the bag). */
    void claim(java.util.Collection<ItemEntity> items) {
        if (!(owner.level() instanceof ServerLevel level) || items.isEmpty()) return;
        collecting = true;
        rescanUntil = level.getGameTime() + 40;
        for (ItemEntity item : items) {
            item.getPersistentData().putUUID(HARVESTER, owner.getUUID());
            pending.add(item.getUUID()); born.put(item.getUUID(), level.getGameTime());
            sites.addLast(new Site(item.blockPosition(), List.of(item.getItem().copy()), level.getGameTime() + 20 * 300));
        }
        while (sites.size() > 256) sites.removeFirst();
        owner.syncWorkState();
    }
    private boolean ours(ItemEntity item) {
        CompoundTag data = item.getPersistentData();
        if (data.getBoolean("aicompanion:gift")) return false; // handed to the player, never taken back
        if (data.hasUUID(HARVESTER)) return owner.getUUID().equals(data.getUUID(HARVESTER));
        if (item.getTarget() != null && !item.getTarget().equals(owner.getOwnerId())) return false;
        // Thrown by someone (the player dropping cobblestone by his pit): not his harvest (review 22.10, М3).
        if (item.getOwner() != null) return false;
        // Vanilla can merge our item INTO an older, untagged stack. Recover the survivor
        // from the real harvest location and type, instead of silently losing its UUID.
        for (Site site : sites) if (site.pos.getCenter().distanceToSqr(item.position()) < 9
                && site.drops.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, item.getItem()))) {
            data.putUUID(HARVESTER, owner.getUUID()); return true;
        }
        return false;
    }
    void tickNearby() {
        if (!(owner.level() instanceof ServerLevel level) || !owner.canCollectItems()) return;
        // The soil for the column is dug: the pickup waits for it, and the wood gather the dig interrupted goes on after it.
        if (diggingForWay && !owner.gatheringDirt) {
            diggingForWay = false;
            if (woodWasRunning) { owner.gatheringLogs = true; owner.requestedLogs = woodRequested; owner.logsRemaining = woodRemaining; woodWasRunning = false; }
        }
        // A way raised for a drop that is already in his bag (or gone) comes down at once, whichever goal runs.
        if (wayFor != null && (!pending.contains(wayFor)
                || level.getEntity(wayFor) instanceof ItemEntity found && !found.blockPosition().equals(wayCell))) finishWay();
        // gather_block and mine_obsidian take the drop of what they broke after stepping down their own column, dismantling it
        // behind them (PATH7): the chase walks to nothing while the column stands or comes down (canChase), so a drop below him
        // waits for the ground. What lies at his own level is taken from where he stands: a crown's leaf that fell on the trunk's
        // top is out of reach once he is down, and waiting for the descent would lose it (the owner, 19.11).
        while (!sites.isEmpty() && sites.peekFirst().expires < level.getGameTime()) sites.removeFirst();
        if (collecting && (owner.tickCount % 5 == 0 || level.getGameTime() <= rescanUntil)) discoverSurvivors(level);
        pending.removeIf(id -> gone(level, id));
        for (UUID id : Set.copyOf(pending)) {
            // The surviving entity of a vanilla item merge can have a different UUID. Keep
            // discovering our harvest for the whole task, including after walking back down.
            if (!(level.getEntity(id) instanceof ItemEntity item) || item.hasPickUpDelay()) continue;
            if (!touches(owner.position(), item)) continue;
            // A drop still falling falls to the ground and is taken from there; one resting on his own column falls with it.
            // A drop at rest on a block beside the column is no column's: a crown's leaf on the trunk's top is out of reach
            // once he is down, so it comes now (19.11).
            if (owner.work.holdsDropsForItsColumn()
                    && (!item.onGround() || owner.temporaryPillar.carries(item.getBoundingBox()) || !restsOnABlock(level, item))) continue;
            ItemStack offered = item.getItem().copy();
            int before = offered.getCount();
            // What fell out of the dead player is kept apart for him, as the loose pickup does.
            if (item.getPersistentData().getBoolean(CompanionPlayersThings.MARK)) owner.playersThings.keep(offered);
            else owner.storeLoot(offered);
            int moved = before - offered.getCount();
            if (moved == 0) continue;
            if (item.getItem().is(ItemTags.LOGS)) collectedLogs += moved;
            if (item.getItem().is(ItemTags.STONE_TOOL_MATERIALS)) collectedStone += moved;
            if (CompanionTools.soilItem(item.getItem())) collectedSoil += moved;
            for (CompanionGround ground : CompanionGround.values()) if (ground.isLoose() && ground == owner.gathering.ground && ground.counts(item.getItem())) collectedLoose[ground.ordinal()] += moved;
            takeFrom(item, offered, moved, false);
            if (offered.isEmpty()) pending.remove(id);
        }
        collectLoose(level);
        owner.syncWorkState();
    }

    /**
     * Anything lying within reach goes into the bag as he passes, like a player's pickup: loot of a kill, what a creeper
     * blew out of the ground, crops washed away, a stack the player threw to him. Three things stay where they are: an
     * item aimed at somebody (the game lets only that one take it), a stack he threw himself, and his own harvest.
     * What fell out of the dead player is taken apart from the bag and given back to the player (CompanionPlayersThings).
     */
    private void collectLoose(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(2.0D),
                found -> !found.hasPickUpDelay() && found.getTarget() == null)) {
            if (pending.contains(item.getUUID())) continue;
            // His own throw: kept out of the bag on purpose (the mine leaves excess stone this way, PR #48).
            if (item.getOwner() == owner) continue;
            if (item.getPersistentData().hasUUID(HARVESTER)) continue;
            // Junk he let go to make room for his own things: taking it back would fill the freed slot with it again.
            if (item.getPersistentData().getBoolean(CompanionBag.JUNK)) continue;
            if (!touches(owner.position(), item)) continue;
            ItemStack offered = item.getItem().copy();
            int before = offered.getCount();
            if (item.getPersistentData().getBoolean(CompanionPlayersThings.MARK)) owner.playersThings.keep(offered);
            else owner.storeLoot(offered);
            int moved = before - offered.getCount();
            if (moved == 0) continue;
            if (item.getPersistentData().hasUUID(CompanionBag.BELONGINGS)
                    || item.getPersistentData().hasUUID(CompanionBag.BELONGINGS_SPOT)
                    || item.getPersistentData().getBoolean(CompanionPlayersThings.MARK)) belongingsTaken++;
            // Not counted as harvest progress: this was lying there, not gathered for a task.
            takeFrom(item, offered, moved, true);
        }
    }

    /** Hands over what was stored: the pickup sound, the note in the log, and the rest of the stack left on the floor. */
    private void takeFrom(ItemEntity item, ItemStack offered, int moved, boolean loose) {
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "item_picked_up", "entity", owner.getUUID(),
                "dropEntity", item.getUUID(), "item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem().getItem()),
                "count", moved, "position", item.position(), "loose", loose);
        owner.take(item, moved);
        if (owner.level() instanceof ServerLevel level) {
            level.playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, .2F, 1.1F);
            noteBelonging(level, item, moved, offered.getCount());
        }
        if (offered.isEmpty()) item.discard(); else item.setItem(offered);
    }

    /**
     * A stack of a death place taken by him: the walk's ledger counts what he moved, and the place's manifest loses the
     * stack (or keeps what is left of it), so nobody ever walks to it again for what is already in the bag.
     */
    private void noteBelonging(ServerLevel level, ItemEntity item, int moved, int left) {
        CompoundTag data = item.getPersistentData();
        if (!data.hasUUID(CompanionBag.BELONGINGS_SPOT)) return;
        takenByMe.merge(item.getUUID(), moved, Integer::sum);
        UUID spotId = data.getUUID(CompanionBag.BELONGINGS_SPOT);
        UUID ownerId = data.hasUUID(CompanionBag.BELONGINGS) ? data.getUUID(CompanionBag.BELONGINGS) : owner.getOwnerId();
        var places = CompanionDeathSpots.get(level.getServer());
        if (left <= 0) places.pruneEntry(ownerId, spotId, item.getUUID());
        else places.setEntryCount(ownerId, spotId, item.getUUID(), left);
    }
    /** The drop the chase is going for now, or null: the swimmer sinks to a drop under him while the pickup is active. */
    ItemEntity chaseTarget() { return target; }
    boolean hasPending() {
        if (!(owner.level() instanceof ServerLevel level)) return false;
        // Never finish a task in the gap between a vanilla merge deleting our original
        // entity and the periodic discovery finding the surviving stack.
        if (collecting && level.getGameTime() <= rescanUntil) discoverSurvivors(level);
        pending.removeIf(id -> gone(level, id));
        // A drop the pickup will not go for is no reason for the work to wait (marathon 23.10: a stone floating in the water below
        // held the store order ninety seconds). Wait for what is chased now or still settling: falling, pickup delay, a retry.
        // chaseable also invalidates an old rejection after moving four blocks. Do not filter it out before that check:
        // the last rejected drop otherwise prevents its own reconsideration forever once the rest have been collected.
        // Nor is a drop let lie (shun): it is waited for neither chased nor settling (obsidian under his water, 08.11).
        return pending.stream().anyMatch(id -> !(level.getEntity(id) instanceof ItemEntity item)
                || owner.canStoreDrops(List.of(item.getItem().copyWithCount(1)))
                && (chaseable(level, id) || !unreachable.contains(id) && shunned.getOrDefault(id, 0L) <= level.getGameTime() && settling(level, id, item)));
    }
    private boolean settling(ServerLevel level, UUID id, ItemEntity item) {
        return item.hasPickUpDelay() || level.getGameTime() - born.getOrDefault(id, 0L) < SETTLE || retryAt.getOrDefault(id, 0L) > level.getGameTime();
    }
    /** Whether a drop is at rest on a block: its box may sit on a block's corner, not only in the cell it is over. */
    private static boolean restsOnABlock(ServerLevel level, ItemEntity item) {
        var box = item.getBoundingBox();
        int below = (int)Math.floor(box.minY) - 1;
        if (Math.abs(box.minY - (below + 1)) > 0.2D) return false;
        for (int x = (int)Math.floor(box.minX); x <= (int)Math.floor(box.maxX); x++)
            for (int z = (int)Math.floor(box.minZ); z <= (int)Math.floor(box.maxZ); z++) {
                BlockPos pos = new BlockPos(x, below, z);
                if (level.getBlockState(pos).isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)) return true;
            }
        return false;
    }
    /** A drop has this long to come to rest before «no way to it» is believed. */
    private static final int SETTLE = 40;
    /** Where he stood when a drop was found out of reach: from four blocks away it gets another try. */
    private final Map<UUID, BlockPos> unreachableFrom = new HashMap<>();
    /** The drop a way is being raised for and its cell, the shape of that way, and how many ways this drop has already been given. */
    private UUID wayFor;
    private BlockPos wayCell;
    private boolean wayBridge;
    private int wayTries;
    private long wayAt;
    /** Ticks a way to one drop may take before it is given up, and how many ways one drop may be given (a column that came short). */
    private static final int WAY_PATIENCE = 600, WAY_TRIES = 3;
    /** The soil for the column is being dug: the pickup waits for it, and what the dig interrupted comes back after it. */
    private boolean diggingForWay;
    private boolean woodWasRunning;
    private int woodRequested, woodRemaining;

    /** Whether the pickup itself is raising a column or a floor to a drop: the chase goes on while it stands (canChase). */
    boolean raisingWay() { return wayFor != null; }

    private void giveUpFromHere(ServerLevel level, UUID id) {
        unreachable.add(id); unreachableFrom.put(id, owner.blockPosition());
        // The drop is no destination any more: a self-rescue must not dig towards it.
        owner.movementIntent(null);
    }
    String diagnosticState() {
        StringBuilder items = new StringBuilder();
        if (owner.level() instanceof ServerLevel level) {
            int shown = 0;
            for (UUID id : pending) {
                if (unreachable.contains(id) || shown++ >= 6) continue;
                if (!(level.getEntity(id) instanceof ItemEntity item)) { items.append("[missing]"); continue; }
                // Where the drop is relative to him, and why it is or is not chased now.
                items.append(String.format(java.util.Locale.ROOT, "[%.1f,%.1f,%.1f%s%s%s%s]", item.getX() - owner.getX(), item.getY() - owner.getY(), item.getZ() - owner.getZ(),
                        item.onGround() ? "g" : item.isInWater() ? "w" : "a", item.hasPickUpDelay() ? "d" : "", retryAt.getOrDefault(id, 0L) > level.getGameTime() ? "r" : "",
                        chaseable(level, id) ? "C" : ""));
            }
        }
        return "pending=" + pending.size() + ",unreachable=" + unreachable.size() + ",collectedLogs=" + collectedLogs + ",collectedStone=" + collectedStone + ",collectedSoil=" + collectedSoil
                + (items.isEmpty() ? "" : ",items=" + items);
    }
    private void discoverSurvivors(ServerLevel level) {
        if (lastScan == level.getGameTime()) return;
        lastScan = level.getGameTime();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(32))) {
            if (ours(item)) {
                pending.add(item.getUUID());
                born.putIfAbsent(item.getUUID(), level.getGameTime());
            }
        }
    }
    boolean canChase() {
        // A floor across a gap stands under his feet: the drops on the far bank are walked to over it (PATH7, #105).
        // A column the pickup itself raised for a drop is no reason to stop the chase: it is the way to it (owner, 24.11).
        boolean ownWay = raisingWay();
        if (!hasPending() || !owner.canCollectItems() || owner.isRecoveringSupports() || owner.isRecoveringBridge()
                || diggingForWay
                // A floor laid from bank to bank is a walk to the drops on the far side: the chase goes over it. A column, or a
                // floor still being laid, is another work's way: the chase waits (the drop is waited for, not given up).
                || !ownWay && (owner.temporaryPillar.isActive() || owner.temporaryBridge.isActive() && !owner.temporaryBridge.isWalk())
                || owner.isSwingingAtBlock() || owner.creeperGuard.nearby(CREEPER_NEAR)) return false;
        if (owner.level() instanceof ServerLevel level) for (UUID id : pending) if (chaseable(level, id)) return true;
        return false;
    }
    void tickChase() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        if (target == null || !target.isAlive() || !pending.contains(target.getUUID()) || unreachable.contains(target.getUUID())) {
            target = pending.stream().filter(id -> chaseable(level, id)).map(level::getEntity).filter(e -> e instanceof ItemEntity).map(e -> (ItemEntity)e)
                    .min(java.util.Comparator.comparingDouble(owner::distanceToSqr)).orElse(null);
            chaseFrom = target == null ? null : target.position();
            path = null; pickupPoint = null; nextRoute = 0; routedItem = null; farRoute = false; farUntil = 0; farMoved = null; lastProgress = level.getGameTime(); bestDistance = Double.MAX_VALUE;
            dropLeaf();
        }
        if (target == null) return;
        // The drop the way was raised for is in his bag (or gone): the column comes down and the soil with it.
        if (wayFor != null && !pending.contains(wayFor)) finishWay();
        // The column of loose blocks above a dug cell is still coming down: whoever walks in for the drop is buried in it,
        // and so is whoever cuts across that cell on his way to a drop pushed out beside it. He waits where he stands.
        if (holdForFall(level)) { owner.work.route.stop(); lastProgress = level.getGameTime(); return; }
        owner.movementIntent(target.blockPosition());
        if (leaf != null) { clearLeaf(level); return; }
        owner.workStatus("Подбираю добытые вещи.");
        owner.getLookControl().setLookAt(target.getX(), target.getY(), target.getZ());
        double distance = owner.distanceToSqr(target);
        if (distance + .05 < bestDistance) { bestDistance = distance; lastProgress = level.getGameTime(); }
        // Along a route longer than one search the distance to the drop first grows: walking on is progress there.
        if (farRoute && path != null && level.getGameTime() < farUntil && (farMoved == null || owner.position().distanceToSqr(farMoved) > .7 * .7)) { farMoved = owner.position(); lastProgress = level.getGameTime(); }
        // A drop floating on the water rises to the surface and drifts with the current: while it is moving, the chase is
        // keeping up with it, not stuck, and it gets the patience of a routed drop instead of sixteen ticks.
        int patience = path == null && !target.isInWater() ? 16 : 45;
        if (level.getGameTime() - lastProgress > patience) {
            UUID id = target.getUUID();
            if (attempts.merge(id, 1, Integer::sum) >= 3) giveUpFromHere(level, id);
            retryAt.put(id, level.getGameTime() + 20);
            target = null; owner.work.route.stop(); return;
        }
        if (touches(owner.position(), target)) { owner.work.route.stop(); return; }
        // Swimming after a drop in the water is walking too; only in the air no route is made.
        if (!owner.onGround() && !owner.isInWater()) return;
        // A drop floating on the water drifts with the current (and one under it rises to the surface): the chase follows
        // the drop itself, so its movement only makes the route stale. That is no progress of the chase by itself: it
        // counts while the drop still lies near where the chase began and a way to it was found — the same try goes on.
        // A drop that has drifted off, or one with no stand at all, keeps its attempts and is given up honestly
        // (judge 2: a drop bobbing in water with no stand was swum after for the whole order, «Не успел подобрать»).
        if (routedItem != null && routedItem.distanceToSqr(target.position()) > (target.isInWater() ? .0025 : .16)) {
            boolean hadWay = path != null && path.canReach();
            path = null; pickupPoint = null; routedItem = null;
            if (hadWay && chaseFrom != null && target.position().distanceToSqr(chaseFrom) <= 1) {
                lastProgress = level.getGameTime(); attempts.remove(target.getUUID());
            }
        }
        if (pickupPoint != null && path != null && path.getEndNode() != null
                && owner.blockPosition().equals(path.getTarget()) && owner.position().distanceToSqr(pickupPoint) < 2.25) {
            owner.work.route.stop();
            owner.getMoveControl().setWantedPosition(pickupPoint.x, pickupPoint.y, pickupPoint.z, .65);
            return;
        }
        if ((path == null || owner.work.route.legOver()) && level.getGameTime() >= nextRoute) {
            nextRoute = level.getGameTime() + 5; routedItem = target.position();
            Set<BlockPos> feet = new HashSet<>(); BlockPos center = target.blockPosition();
            Map<BlockPos, Vec3> points = new HashMap<>();
            // From two below (a drop on a ledge at head height) to one above (a drop in a dip). A drop in the water is
            // taken from its own cell: the swimmer's stand there, and a route from the top of the water column can reach it.
            // A cell below it is no use to the swimmer: the search starts at the surface and does not go down the water.
            for (int dy = target.isInWater() ? 0 : -2; dy <= 1; dy++) for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
                BlockPos spot = center.offset(ox, dy, oz);
                // The drop pushed out of a landed block lies clear, while the cell it came from still has the rest of the
                // column in the air over it: no stand there.
                if (!CompanionMiningNavigation.pickupStand(owner, spot) || fallingInto(level, new AABB(spot), spot)) continue;
                var standing = Vec3.atBottomCenterOf(spot);
                var toward = target.position().subtract(standing).multiply(1, 0, 1).normalize().scale(.3);
                // A reachable adjacent cell can still be outside pickup range. Route to a point
                // from which the actual proximity check succeeds. At a ledge, lean toward the
                // item within the standing cell so the eye ray clears the edge above the drop.
                for (var point : new Vec3[]{standing, standing.add(toward)}) {
                    if (touches(point, target) && level.noCollision(owner, owner.getBoundingBox().move(point.subtract(owner.position())))) {
                        feet.add(spot); points.put(spot, point); break;
                    }
                }
            }
            var reach = feet.isEmpty() ? null : owner.work.route.cells(feet, 0);
            // The tick's cells went elsewhere: asked again next tick, the drop is not given up for it.
            if (reach != null && reach.busy()) { nextRoute = level.getGameTime() + 1; return; }
            path = reach == null ? null : reach.path(); farRoute = reach != null && reach.kind() == CompanionRoute.Kind.FAR;
            if (farRoute && farUntil == 0) farUntil = level.getGameTime() + CompanionRoute.farLimit(reach.length());
            pickupPoint = path == null ? null : points.get(path.getTarget());
            // Marathon 23.10: drops in crevices of a ravine got three tries of standing a second or two each, though from here
            // there was no way to them at all. A drop at rest with no cell to take it from, or no route to one, is given up
            // from this spot at once; walking four blocks away gives it another chance.
            boolean atRest = (target.onGround() || target.isInWater()) && !target.hasPickUpDelay()
                    && level.getGameTime() - born.getOrDefault(target.getUUID(), 0L) >= SETTLE;
            // A drop in the water may still be rising to the surface or drifting with the current: no cell to take it
            // from now is no verdict on it yet — the attempts above give it up only if it really stays out of reach.
            if (atRest && !target.isInWater() && (path == null || !path.canReach())) {
                // A drop in a pocket under the leaves of a bush or a crown: a player breaks a leaf or two and takes it (marathon
                // 23.10, «долина»: a log fell into a dip under a bush, the leaves shut every cell he could take it from).
                BlockPos blocking = leavesCleared.getOrDefault(target.getUUID(), 0) < LEAVES_PER_DROP ? leafInTheWay(level, target) : null;
                if (blocking != null) {
                    leaf = blocking; leavesCleared.merge(target.getUUID(), 1, Integer::sum);
                    leafBest = owner.distanceToSqr(leaf.getCenter()); leafProgress = level.getGameTime();
                    path = null; owner.work.route.stop(); return;
                }
                // A drop no walk reaches is come at by the same way a block is (PATH7, #105): a column of his soil up to it, or
                // a floor across the gap — never left lying where it fell (owner, 24.11: the lapis on his own pumpkin).
                // The way walks with the one route: stopping it here would stop the column's own approach every tick.
                if (raiseWay(level, target)) { path = null; pickupPoint = null; return; }
                giveUpFromHere(level, target.getUUID());
                target = null; path = null; owner.work.route.stop(); return;
            }
        }
        if (path != null && path.canReach()) owner.work.route.walk(path);
    }
    void pause() { target = null; path = null; pickupPoint = null; dropLeaf(); }

    /**
     * A drop no walk reaches is come at by the same way a block is (PATH7, #105): a column of his own soil whose top puts his
     * body level with the drop's cell, or a floor laid across the gap to the cell he touches it from — the way's own classes
     * walk, jump and lay it, this only asks which of them is there. False when neither is, and the drop is given up as before
     * with the reason in the log: no way is no silent drop. A drop resting on a block above, or on the far bank of a gap, is
     * no exception — that is what the owner's lapis on his pumpkin was.
     */
    private boolean raiseWay(ServerLevel level, ItemEntity item) {
        BlockPos cell = item.blockPosition();
        // The player's own switch («Строить себе путь»): off, no column and no floor is raised for a drop either, and the drop
        // is given up with that reason in the log once, not a line per tick.
        if (!owner.buildWay()) {
            if (wayTries++ == 0) sayWay(cell, "none", 0, 0, "switch off");
            return false;
        }
        if (wayFor != null) {
            if (wayCell.equals(cell)) return tickWay(level, cell);
            // The drop drifted to another cell: the way it stands is no way to it any more, and is taken back.
            finishWay();
        }
        // A column of another work's stands, or a floor still being laid: it is no way to this drop, and the drop is waited for,
        // not given up — the way is raised once that one is down (canChase keeps the chase off while it stands). A floor laid
        // from bank to bank is a walk, and the drop over it is walked to.
        if (owner.temporaryPillar.isActive() || owner.temporaryPillar.isRecovering()
                || owner.temporaryBridge.isActive() && !owner.temporaryBridge.isWalk() || owner.temporaryBridge.isRecovering()) return true;
        if (wayTries >= WAY_TRIES) return false;
        int steps = owner.temporaryPillar.leastStepsNearCell(cell, CompanionPillar.WAY_SUPPORTS);
        boolean soil = steps > 0 && steps <= owner.temporaryPillar.soilCarried();
        CompanionReach.BridgePlan bridge = soil ? null : CompanionReach.bridgeToCell(owner, level, cell);
        if (bridge != null) {
            wayFor = item.getUUID(); wayCell = cell.immutable(); wayBridge = true; wayAt = level.getGameTime(); wayTries++;
            sayWay(item, "bridge", 0, bridge.length(), "");
            return tickWay(level, cell);
        }
        if (steps > 0) {
            // The column asks more soil than he carries: he digs it about him, as the way digs it for a block, and the column is
            // raised once the soil is in his bag — never a refusal over ground he can dig (the owner, 24.11: «не хватает земли»
            // over the soil under his feet).
            if (!soil && !diggingForWay) {
                int lacks = steps - owner.temporaryPillar.soilCarried();
                if (digSoilForWay(level, lacks)) {
                    sayWay(cell, "column", steps, 0, "нет земли, копаю " + lacks);
                    return true;
                }
            }
            if (diggingForWay) return true;
            wayFor = item.getUUID(); wayCell = cell.immutable(); wayBridge = false; wayAt = level.getGameTime(); wayTries++;
            sayWay(item, "column", steps, 0, "");
            return tickWay(level, cell);
        }
        sayWay(item, "none", 0, 0, "ни столба, ни моста");
        return false;
    }

    /** One tick of the way to the drop: the column raised, or the floor laid and crossed, until his body touches the drop. */
    private boolean tickWay(ServerLevel level, BlockPos cell) {
        if (level.getGameTime() - wayAt > WAY_PATIENCE) { sayWay(cell, "none", 0, 0, "не успел за " + WAY_PATIENCE + " тактов"); finishWay(); return false; }
        if (wayBridge) {
            CompanionBridge.Outcome outcome = owner.temporaryBridge.tickTowardCell(cell);
            if (outcome == CompanionBridge.Outcome.FAILED) {
                sayWay(cell, "none", 0, 0, owner.temporaryBridge.failureReason());
                finishWay();
                return false;
            }
            owner.workStatus("Мостю провал к выпавшему.");
            return true;
        }
        CompanionPillar.Outcome outcome = owner.temporaryPillar.tickTowardCell(cell, CompanionPillar.WAY_SUPPORTS);
        if (outcome == CompanionPillar.Outcome.FAILED) {
            sayWay(cell, "none", 0, 0, owner.temporaryPillar.failureReason());
            finishWay();
            return false;
        }
        owner.workStatus("Поднимаюсь к выпавшему.");
        return true;
    }

    /**
     * The soil the column lacks, dug about him as the way digs it for a block (the dig's own rule, thick ground only): the pickup
     * waits for the dig, and the column is raised with the soil in his bag. False when there is no soil to dig about him, and the
     * column is tried and fails with the soil named, as before.
     */
    private boolean digSoilForWay(ServerLevel level, int lacks) {
        if (!CompanionReach.soilAbout(owner, level) || !CompanionDigGoal.thickSoilNear(owner)) return false;
        woodWasRunning = owner.gatheringLogs; woodRequested = owner.requestedLogs; woodRemaining = owner.logsRemaining;
        diggingForWay = true;
        owner.startGatheringDirtForWork(Math.max(1, lacks));
        return true;
    }

    /** The way to a drop is over: the column or the floor comes back, the soil with it, as a block's way does. */
    private void finishWay() {
        if (wayFor == null) return;
        wayFor = null; wayCell = null; wayBridge = false; wayAt = 0;
        if (owner.temporaryPillar.isActive()) owner.temporaryPillar.requestCleanup();
        if (owner.temporaryBridge.isActive()) owner.temporaryBridge.requestCleanup();
    }

    private void sayWay(ItemEntity item, String shape, int steps, int gap, String why) { sayWay(item.blockPosition(), shape, steps, gap, why); }

    /** One line per way to a drop, in the game log as well as the journal: the thing, where it lies, how far up and across, the shape of the way and what it asks. */
    private void sayWay(BlockPos cell, String shape, int steps, int gap, String why) {
        ItemEntity item = owner.level() instanceof ServerLevel level && level.getEntity(wayFor) instanceof ItemEntity found ? found : target;
        String id = item == null ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString();
        BlockPos feet = owner.blockPosition();
        int rise = cell.getY() - feet.getY();
        double across = Math.hypot(cell.getX() + .5D - owner.getX(), cell.getZ() + .5D - owner.getZ());
        String text = "«" + id + "»: выпавшее " + cell.toShortString() + " " + (rise >= 0 ? "+" + rise : String.valueOf(rise))
                + " вверх/" + Math.round(across * 10) / 10D + " вбок → " + shape
                + (steps > 0 ? ", " + steps + " шагов земли" : gap > 0 ? ", " + gap + " клеток пола" : "")
                + (why.isEmpty() ? "" : "; почему: " + why);
        dev.aicompanion.CompanionDiagnostics.say(owner.diagnosticTrace(), "pickup_way", text, "entity", owner.getUUID(),
                "item", id, "drop", cell.toShortString(), "rise", rise, "across", Math.round(across * 100) / 100D,
                "shape", shape, "steps", steps, "gap", gap, "why", why);
    }
    /** Only a leaf really under way is let go: the reset also clears the mining look the other work may be showing, and the hand the tool replaced comes back. */
    private void dropLeaf() { if (leaf != null) { leaf = null; leafBreaking.reset(); owner.putToolAway(); } }

    private static final int LEAVES_PER_DROP = 6;
    /** Where a leaf in the way is broken from: within reach and sight, off its own cell. */
    private static final CompanionRoute.Need LEAF_STAND = new CompanionRoute.Need(3.5, false, true, false);
    private static final double REACH = 4.5;
    /** Leaves that grew on a tree; those a player set (persistent) are his, not in the way. */
    private static boolean naturalLeaves(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LEAVES) && state.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                && !state.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT);
    }
    /**
     * The leaves between him and a drop: the spot from which it would be taken (feet and head cells) and the way into it from
     * one side — the side cell at the spot's level or a step above it, with room for the head (and over it, stepping down).
     * Of all such ways whose cells are free apart from natural leaves, the one with the fewest leaves and nearest to him; of its
     * leaves the nearest within reach, or the leaf that hides it from his eyes.
     */
    private BlockPos leafInTheWay(ServerLevel level, ItemEntity item) {
        BlockPos center = item.blockPosition();
        Vec3 eye = owner.getEyePosition();
        List<BlockPos> bestWay = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = -2; dy <= 1; dy++) for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
            BlockPos spot = center.offset(ox, dy, oz);
            if (!level.hasChunkAt(spot) || !sturdyBelow(level, spot) || !touches(Vec3.atBottomCenterOf(spot), item)) continue;
            for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) for (int step = 0; step <= 1; step++) {
                BlockPos from = spot.relative(side).above(step);
                if (!sturdyBelow(level, from)) continue;
                List<BlockPos> way = new java.util.ArrayList<>(List.of(spot, spot.above(), from, from.above()));
                if (step == 1) { way.add(spot.above(2)); way.add(from.above(2)); }
                if (!way.stream().allMatch(cell -> open(level, cell))) continue;
                long leaves = way.stream().filter(cell -> naturalLeaves(level.getBlockState(cell))).count();
                if (leaves == 0) continue;
                double score = leaves * 16 + eye.distanceTo(Vec3.atBottomCenterOf(from));
                if (score < bestScore) { bestScore = score; bestWay = way; }
            }
        }
        if (bestWay == null) return null;
        BlockPos best = bestWay.stream().filter(cell -> naturalLeaves(level.getBlockState(cell)))
                .min(java.util.Comparator.comparingDouble(cell -> eye.distanceToSqr(cell.getCenter()))).orElse(null);
        if (best == null) return null;
        if (CompanionMiningNavigation.visible(owner, eye, best)) return best;
        // Behind other leaves: the leaf that hides it comes first, as a player breaks his way into a bush.
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, best.getCenter(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, owner));
        BlockPos front = hit.getBlockPos().immutable();
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && naturalLeaves(level.getBlockState(front)) ? front : null;
    }
    private static boolean sturdyBelow(ServerLevel level, BlockPos cell) {
        return level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), net.minecraft.core.Direction.UP);
    }
    /** Free for the body, or only natural leaves in it. */
    private static boolean open(ServerLevel level, BlockPos cell) {
        var state = level.getBlockState(cell);
        return naturalLeaves(state) || state.getCollisionShape(level, cell).isEmpty() && level.getFluidState(cell).isEmpty();
    }
    /** The leaf is broken as any block he breaks: swings, cracks and sound; what falls from it is his harvest. */
    private void clearLeaf(ServerLevel level) {
        var state = level.getBlockState(leaf);
        if (!naturalLeaves(state)) {
            leafBreaking.reset(); owner.putToolAway(); leaf = null; path = null; return;
        }
        if (owner.getEyePosition().distanceToSqr(leaf.getCenter()) > REACH * REACH
                || !CompanionMiningNavigation.visible(owner, owner.getEyePosition(), leaf)) {
            leafBreaking.reset();
            // The swing is let go: the hand the tool replaced comes back while he walks to it.
            owner.putToolAway();
            double distance = owner.distanceToSqr(leaf.getCenter());
            if (distance + .05 < leafBest) { leafBest = distance; leafProgress = level.getGameTime(); }
            if (level.getGameTime() - leafProgress > 45) { giveUpFromHere(level, target.getUUID()); leaf = null; path = null; owner.work.route.stop(); return; }
            if (path == null || owner.work.route.legOver()) {
                var reach = owner.work.route.to(leaf, LEAF_STAND);
                if (reach.busy()) return;
                path = reach.path();
            }
            if (path == null || !path.canReach()) { giveUpFromHere(level, target.getUUID()); leaf = null; path = null; owner.work.route.stop(); return; }
            owner.workStatus("Подойду к листве, чтобы достать вещь.");
            owner.work.route.walk(path); return;
        }
        owner.work.route.stop();
        owner.getLookControl().setLookAt(leaf.getX() + .5, leaf.getY() + .5, leaf.getZ() + .5);
        owner.workStatus("Раздвигаю листву, чтобы достать вещь.");
        lastProgress = level.getGameTime();
        // The leaf is the shears' block: the tool goes into his hand before the first swing, so the cracks and the break's own time
        // are the tool's, and the leaf comes down as itself, not to a pickaxe in his hand that gives only a sapling. What it gives is
        // weighed against the bag first: the hand item the tool replaces takes a cell of its own, and a leaf with one cell free was
        // refused where it used to be broken (in game). With no room it stays, the drop under it with it, and the hand goes back.
        if (!leafBreaking.swinging()) {
            ItemStack forTheLeaf = CompanionTools.bestToolForBlock(owner, state);
            if (!owner.canStoreDrops(CompanionTools.drops(state, level, leaf, null, owner, forTheLeaf.isEmpty() ? owner.getMainHandItem() : forTheLeaf))) {
                owner.putToolAway();
                leafBreaking.reset(); leaf = null; path = null; nextRoute = 0;
                return;
            }
            owner.takeToolForBlock(state);
        }
        if (!leafBreaking.tick(level, leaf)) return;
        var tool = owner.getMainHandItem();
        var drops = CompanionTools.drops(state, level, leaf, null, owner, tool);
        if (level.destroyBlock(leaf, false, owner, 512)) {
            CompanionTools.wearOnBreak(tool, level, state, leaf, owner);
            owner.dropHarvest(leaf, drops);
        }
        owner.putToolAway();
        leafBreaking.reset(); leaf = null; path = null; nextRoute = 0;
    }
    private boolean gone(ServerLevel level, UUID id) {
        var item = level.getEntity(id);
        return item == null ? level.getGameTime() - born.getOrDefault(id, 0L) > 5 : !item.isAlive();
    }
    /**
     * Like a player's pickup (Player.aiStep): the item touches the body widened by one block sideways and half a block up and
     * down. So a drop in a wall pocket or on the ledge at head height is taken from below it (marathon 23.10: the stone mined
     * in a ravine wall lay two blocks over his feet and was «out of reach» for a rule of a block and a half). The box is that
     * of a body standing at {@code at}; a full block between them leaves no overlap, as for the player.
     */
    boolean touches(Vec3 at, ItemEntity item) {
        return owner.getBoundingBox().move(at.subtract(owner.position())).inflate(1, .5, 1).intersects(item.getBoundingBox());
    }
    int collectedLogs() { return collectedLogs; }
    int collectedStone() { return collectedStone; }
    int collectedSoil() { return collectedSoil; }
    int incomingLogs() { return incoming(stack -> stack.is(ItemTags.LOGS)); }
    int incomingSoil() { return incoming(CompanionTools::soilItem); }
    /** Picked up of one ground, loose or soil. */
    int collected(CompanionGround ground) { return ground == CompanionGround.SOIL ? collectedSoil : collectedLoose[ground.ordinal()]; }
    int collectedLoose() { int total = 0; for (CompanionGround ground : CompanionGround.values()) if (ground.isLoose()) total += collectedLoose[ground.ordinal()]; return total; }
    int incoming(CompanionGround ground) { return incoming(ground::counts); }
    int incoming(java.util.function.Predicate<ItemStack> matches) {
        if (!(owner.level() instanceof ServerLevel level)) return 0;
        int amount = 0;
        for (UUID id : pending) if (!unreachable.contains(id) && level.getEntity(id) instanceof ItemEntity item && item.isAlive() && matches.test(item.getItem())) amount += item.getItem().getCount();
        return amount;
    }
    private static final int FALL_SCAN = 8;

    /** A tall column comes down block by block, a few seconds for a desert one; past this the wait is a false alarm and the stuck detector takes over. */
    private static final int FALL_HOLD_LIMIT = 200;
    private UUID heldTarget;
    private int heldTicks;

    /** The wait for a falling column, bounded per drop: it never holds one forever. */
    private boolean holdForFall(ServerLevel level) {
        if (!fallingOver(level, target) && !fallingNearby(level)) return false;
        if (!target.getUUID().equals(heldTarget)) { heldTarget = target.getUUID(); heldTicks = 0; }
        return ++heldTicks <= FALL_HOLD_LIMIT;
    }

    private static boolean fallingOver(ServerLevel level, ItemEntity item) {
        return fallingInto(level, item.getBoundingBox().inflate(.5, 0, .5), item.blockPosition());
    }

    private boolean fallingNearby(ServerLevel level) {
        return !level.getEntitiesOfClass(FallingBlockEntity.class, owner.getBoundingBox().inflate(1.5, 0, 1.5).expandTowards(0, FALL_SCAN, 0)).isEmpty();
    }

    /**
     * A block in the air over the box, or one about to drop: the first block over the cell is loose and has its fall already
     * scheduled. Sand and gravel that merely hang over the cell (worldgen overhangs, a ceiling) have no scheduled fall and are not waited for.
     */
    private static boolean fallingInto(ServerLevel level, AABB box, BlockPos cell) {
        if (!level.getEntitiesOfClass(FallingBlockEntity.class, box.expandTowards(0, FALL_SCAN, 0)).isEmpty()) return true;
        for (BlockPos above = cell.above(); above.getY() <= cell.getY() + FALL_SCAN; above = above.above()) {
            BlockState state = level.getBlockState(above);
            if (!state.isAir()) return state.getBlock() instanceof FallingBlock && level.getBlockTicks().hasScheduledTick(above, state.getBlock());
        }
        return false;
    }

    private boolean chaseable(ServerLevel level, UUID id) {
        if (shunned.getOrDefault(id, 0L) > level.getGameTime()) return false;
        if (unreachable.contains(id)) {
            // Given up from one spot only: four blocks away the drop is tried again.
            BlockPos from = unreachableFrom.get(id);
            if (from == null || owner.blockPosition().distSqr(from) < 4 * 4) return false;
            unreachable.remove(id); unreachableFrom.remove(id); attempts.remove(id);
        }
        if (retryAt.getOrDefault(id, 0L) > level.getGameTime()) return false;
        // Floating in the water is taken like one on the ground: he swims to it, as a player does.
        return level.getEntity(id) instanceof ItemEntity item && item.isAlive() && !item.hasPickUpDelay()
                && (item.onGround() || item.isInWater() || Math.abs(item.getY() - owner.getY()) < 2.3)
                && owner.canStoreDrops(List.of(item.getItem().copyWithCount(1)));
    }
    /**
     * His own harvest lying near a spot (tagged as his) that is not on the pickup list: put back on it, each item at most once in
     * five minutes (one out of reach does not keep him busy forever). Returns how many were taken up again.
     */
    int reclaim(net.minecraft.core.BlockPos center, int radius) {
        if (!(owner.level() instanceof ServerLevel level)) return 0;
        long now = level.getGameTime();
        reclaimedAt.values().removeIf(at -> now - at > 20 * 300);
        int found = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(center).inflate(radius, 8, radius))) {
            CompoundTag data = item.getPersistentData();
            if (!item.isAlive() || pending.contains(item.getUUID()) || reclaimedAt.containsKey(item.getUUID()) || data.getBoolean("aicompanion:gift")
                    || !data.hasUUID(HARVESTER) || !owner.getUUID().equals(data.getUUID(HARVESTER))
                    || !owner.canStoreDrops(List.of(item.getItem().copyWithCount(1)))) continue;
            reclaimedAt.put(item.getUUID(), now);
            pending.add(item.getUUID()); born.putIfAbsent(item.getUUID(), now);
            found++;
        }
        if (found > 0) collecting = true;
        return found;
    }
    private final Map<UUID, Long> reclaimedAt = new HashMap<>();
    /** Drops left lying until the given game time (a creeper that will not leave them): not chased, and the work does not wait for them. */
    private final Map<UUID, Long> shunned = new HashMap<>();
    void shun(long until) { for (UUID id : pending) shunned.put(id, until); pause(); }
    void beginCollection() { collecting = true; shunned.clear(); leavesCleared.clear(); unreachable.clear(); unreachableFrom.clear(); attempts.clear(); retryAt.clear(); wayTries = 0; }
    void resumeCollection() { collecting = true; }
    void cancel() { collecting = false; shunned.clear(); leavesCleared.clear(); pending.clear(); unreachable.clear(); unreachableFrom.clear(); born.clear(); attempts.clear(); retryAt.clear(); sites.clear(); rescanUntil = 0; wayTries = 0; diggingForWay = false; finishWay(); pause(); }
}

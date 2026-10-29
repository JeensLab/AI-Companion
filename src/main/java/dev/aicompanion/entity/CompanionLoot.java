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
    private final int[] collectedLoose = new int[CompanionGround.values().length];
    private long rescanUntil;
    private boolean collecting;
    private long lastScan = Long.MIN_VALUE;
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Map<UUID, Long> retryAt = new HashMap<>();
    private long nextRoute;
    private Vec3 routedItem;
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
        while (!sites.isEmpty() && sites.peekFirst().expires < level.getGameTime()) sites.removeFirst();
        if (collecting && (owner.tickCount % 5 == 0 || level.getGameTime() <= rescanUntil)) discoverSurvivors(level);
        pending.removeIf(id -> gone(level, id));
        for (UUID id : Set.copyOf(pending)) {
            // The surviving entity of a vanilla item merge can have a different UUID. Keep
            // discovering our harvest for the whole task, including after walking back down.
            if (!(level.getEntity(id) instanceof ItemEntity item) || item.hasPickUpDelay()) continue;
            if (!touches(owner.position(), item)) continue;
            ItemStack offered = item.getItem().copy();
            int before = offered.getCount(); owner.storeLoot(offered);
            int moved = before - offered.getCount();
            if (moved == 0) continue;
            dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "item_picked_up", "entity", owner.getUUID(),
                    "dropEntity", item.getUUID(), "item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem().getItem()),
                    "count", moved, "position", item.position());
            if (item.getItem().is(ItemTags.LOGS)) collectedLogs += moved;
            if (item.getItem().is(ItemTags.STONE_TOOL_MATERIALS)) collectedStone += moved;
            if (CompanionTools.soilItem(item.getItem())) collectedSoil += moved;
            for (CompanionGround ground : CompanionGround.values()) if (ground.isLoose() && ground.counts(item.getItem())) collectedLoose[ground.ordinal()] += moved;
            owner.take(item, moved);
            level.playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, .2F, 1.1F);
            if (offered.isEmpty()) { item.discard(); pending.remove(id); } else item.setItem(offered);
        }
        owner.syncWorkState();
    }
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
        return pending.stream().anyMatch(id -> !(level.getEntity(id) instanceof ItemEntity item)
                || owner.canStoreDrops(List.of(item.getItem().copyWithCount(1)))
                && (chaseable(level, id) || !unreachable.contains(id) && settling(level, id, item)));
    }
    private boolean settling(ServerLevel level, UUID id, ItemEntity item) {
        return item.hasPickUpDelay() || level.getGameTime() - born.getOrDefault(id, 0L) < SETTLE || retryAt.getOrDefault(id, 0L) > level.getGameTime();
    }
    /** A drop has this long to come to rest before «no way to it» is believed. */
    private static final int SETTLE = 40;
    /** Where he stood when a drop was found out of reach: from four blocks away it gets another try. */
    private final Map<UUID, BlockPos> unreachableFrom = new HashMap<>();
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
        if (!hasPending() || !owner.canCollectItems() || owner.isRecoveringSupports() || owner.getTemporarySupportCount() > 0
                || owner.isSwingingAtBlock()) return false;
        if (owner.level() instanceof ServerLevel level) for (UUID id : pending) if (chaseable(level, id)) return true;
        return false;
    }
    void tickChase() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        if (target == null || !target.isAlive() || !pending.contains(target.getUUID()) || unreachable.contains(target.getUUID())) {
            target = pending.stream().filter(id -> chaseable(level, id)).map(level::getEntity).filter(e -> e instanceof ItemEntity).map(e -> (ItemEntity)e)
                    .min(java.util.Comparator.comparingDouble(owner::distanceToSqr)).orElse(null);
            path = null; pickupPoint = null; nextRoute = 0; routedItem = null; farRoute = false; farUntil = 0; farMoved = null; lastProgress = level.getGameTime(); bestDistance = Double.MAX_VALUE;
            dropLeaf();
        }
        if (target == null) return;
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
        if (level.getGameTime() - lastProgress > (path == null ? 16 : 45)) {
            UUID id = target.getUUID();
            if (attempts.merge(id, 1, Integer::sum) >= 3) giveUpFromHere(level, id);
            retryAt.put(id, level.getGameTime() + 20);
            target = null; owner.work.route.stop(); return;
        }
        if (touches(owner.position(), target)) { owner.work.route.stop(); return; }
        // Swimming after a drop in the water is walking too; only in the air no route is made.
        if (!owner.onGround() && !owner.isInWater()) return;
        if (routedItem != null && routedItem.distanceToSqr(target.position()) > .16) { path = null; pickupPoint = null; }
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
            // From two below (a drop on a ledge at head height) to one above (a drop in a dip).
            for (int dy = -2; dy <= 1; dy++) for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
                BlockPos spot = center.offset(ox, dy, oz);
                // The drop pushed out of a landed block lies clear, while the cell it came from still has the rest of the
                // column in the air over it: no stand there.
                if (!CompanionMiningNavigation.standable(owner, spot) || fallingInto(level, new AABB(spot), spot)) continue;
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
            if (atRest && (path == null || !path.canReach())) {
                // A drop in a pocket under the leaves of a bush or a crown: a player breaks a leaf or two and takes it (marathon
                // 23.10, «долина»: a log fell into a dip under a bush, the leaves shut every cell he could take it from).
                BlockPos blocking = leavesCleared.getOrDefault(target.getUUID(), 0) < LEAVES_PER_DROP ? leafInTheWay(level, target) : null;
                if (blocking != null) {
                    leaf = blocking; leavesCleared.merge(target.getUUID(), 1, Integer::sum);
                    leafBest = owner.distanceToSqr(leaf.getCenter()); leafProgress = level.getGameTime();
                    path = null; owner.work.route.stop(); return;
                }
                giveUpFromHere(level, target.getUUID());
                target = null; path = null; owner.work.route.stop(); return;
            }
        }
        if (path != null && path.canReach()) owner.work.route.walk(path);
    }
    void pause() { target = null; path = null; pickupPoint = null; dropLeaf(); }
    /** Only a leaf really under way is let go: the reset also clears the mining look the other work may be showing. */
    private void dropLeaf() { if (leaf != null) { leaf = null; leafBreaking.reset(); } }

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
            leafBreaking.reset(); leaf = null; path = null; return;
        }
        if (owner.getEyePosition().distanceToSqr(leaf.getCenter()) > REACH * REACH
                || !CompanionMiningNavigation.visible(owner, owner.getEyePosition(), leaf)) {
            leafBreaking.reset();
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
        if (!leafBreaking.tick(level, leaf)) return;
        var tool = owner.getMainHandItem();
        var drops = net.minecraft.world.level.block.Block.getDrops(state, level, leaf, null, owner, tool);
        if (owner.canStoreDrops(drops) && level.destroyBlock(leaf, false, owner, 512)) owner.dropHarvest(leaf, drops);
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
    private boolean touches(Vec3 at, ItemEntity item) {
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
    private int incoming(java.util.function.Predicate<ItemStack> matches) {
        if (!(owner.level() instanceof ServerLevel level)) return 0;
        int amount = 0;
        for (UUID id : pending) if (!unreachable.contains(id) && level.getEntity(id) instanceof ItemEntity item && item.isAlive() && matches.test(item.getItem())) amount += item.getItem().getCount();
        return amount;
    }
    private static final int FALL_SCAN = 8;

    /** A column that is really coming down is over in a second or two; past this the wait is a false alarm and the stuck detector takes over. */
    private static final int FALL_HOLD_LIMIT = 60;
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
    void beginCollection() { collecting = true; leavesCleared.clear(); unreachable.clear(); unreachableFrom.clear(); attempts.clear(); retryAt.clear(); }
    void resumeCollection() { collecting = true; }
    void cancel() { collecting = false; leavesCleared.clear(); pending.clear(); unreachable.clear(); unreachableFrom.clear(); born.clear(); attempts.clear(); retryAt.clear(); sites.clear(); rescanUntil = 0; pause(); }
}

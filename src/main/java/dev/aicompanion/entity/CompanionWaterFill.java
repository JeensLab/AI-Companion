package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Water for his bucket (decided 05.11), from living water only: a source the game fills again by itself (FlowingFluid.getNewLiquid:
 * two sources beside it and something solid or a source under it), with open air over it, so a lake, a river or the sea is whole
 * again behind him (a lily pad on it would drop), and a single source (the player's field water, a fountain, his own puddle) is never
 * taken. On an order (fill_bucket, the last step of obtain_item minecraft:water_bucket) he goes as far as he looks for anything. By
 * himself — no order, no fight, no water bucket in his bag — he steps aside sixteen blocks at most while he follows the player or
 * roams; standing, and after «стой» or «стоп» until the next order, he takes only what his hand reaches.
 */
final class CompanionWaterFill {
    static final String NOWHERE = "Здесь воды не набрать — наберу в обычном мире.";
    static final String STILL = "Вода здесь сама не восполняется — набирать не буду, останется яма.";
    static final String NOT_FOUND = "Не нашёл, где набрать воды: рядом ни озера, ни реки.";
    static final String NO_WAY = "Вижу воду, но не могу к ней подойти.";
    static final String FULL_BAG = "Сумка забита — некуда налить воду.";
    static final String GOING = "Наберу воды в ведро.";
    /** A player's reach for a block, as his bucket has it (CompanionWaterBucket); a cell to stand at keeps a margin, as a walk ends off its centre. */
    private static final double REACH = 4.5, STAND_REACH = REACH - .6;
    /** How far he steps aside by himself (blocks), the longest walk he takes for it, and how long it may take (ticks). */
    private static final int ASIDE = 16, ASIDE_WALK = 24, ASIDE_TICKS = 400;
    /** How often he looks for water by himself (ticks), and how long a step aside that came to nothing keeps him from that place. */
    private static final int LOOK = 40, RETRY = 3600;
    /** He says he goes for water at most this often (ticks). */
    private static final int TALK = 1200;
    /** Water above and below his feet that is looked at on an order and by himself (blocks). */
    private static final int DEPTH = 32, DEPTH_ASIDE = 8;
    /** The nearest cells of water walked to, the most of one body of water looked at (a sea), and the walks and looks one fill may take. */
    private static final int CELLS = 32, BODY = 4096, LEGS = 8;

    private final CompanionEntity c;
    /** The order: buckets to fill, buckets filled, whose it is for the journal, whether any living water was seen. */
    private int wanted, filled;
    private String why = "order";
    private boolean sawWater;
    /**
     * The way to water: the body of water aimed at and the cells to stand at for it (null: look again), the walk on it, the walks
     * and looks so far, and the water passed over because there was no way to it (a pond in a closed cave).
     */
    private Set<BlockPos> body = Set.of();
    private Set<BlockPos> stands;
    private Path path;
    private int legs;
    private final Set<BlockPos> passed = new HashSet<>();
    /** The step aside: running, from where and since when, said or not, given up; where and when the last one came to nothing. */
    private boolean aside, told, gaveUp;
    private BlockPos asideFrom, failedAt;
    private long nextLook, asideSince, failedTime, saidAt = Long.MIN_VALUE / 2;

    CompanionWaterFill(CompanionEntity c) { this.c = c; }

    /** The Nether boils water away and the End has none (decided 05.11): an order is refused there at once. */
    static boolean nowhere(Level level) { return level.dimensionType().ultraWarm() || level.dimension() == Level.END; }

    /** Why no water is to be had in this world at all, or null: the Nether or the End, or the game's rule that refills water off. */
    static String refusal(Level level) {
        if (nowhere(level)) return NOWHERE;
        return level.getGameRules().getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION) ? null : STILL;
    }

    /** fill_bucket: {@code count} empty buckets of his to fill, for an order or a goal. Null when the work may start, else why not. */
    CompanionActionResult begin(int count, boolean goal) {
        String refusal = refusal(c.level());
        if (refusal != null) return CompanionActionResult.failed(refusal);
        wanted = count; filled = 0; why = goal ? "goal" : "order"; sawWater = false;
        reset();
        return null;
    }

    /** One tick of the order: a bucket filled where his hand reaches, else on toward the nearest living water. */
    void orderTick(ServerLevel level) {
        if (filled >= wanted) { end(true, wanted == 1 ? "Набрал воды в ведро." : "Набрал воды в вёдра: " + wanted + " шт."); return; }
        // His own water from a fall is scooped back first: it wants an empty bucket of his.
        if (c.waterBucket.hasPools()) return;
        String done = filled == 0 ? "" : " Набрал воды в " + filled + " из " + wanted + ".";
        if (!carries(Items.BUCKET)) { end(false, filled == 0 ? "Нет пустого ведра." : "Пустые вёдра кончились: набрал воды в " + filled + " из " + wanted + "."); return; }
        if (!CompanionWaterBucket.roomToScoop(c)) { end(false, FULL_BAG + done); return; }
        BlockPos cell = inReach(level);
        if (cell != null) {
            if (scoop(level, cell, why)) { filled++; reset(); }
            return;
        }
        if (!walk(level, c.getSearchRadius(), DEPTH, false)) end(false, (sawWater ? NO_WAY : NOT_FOUND) + done);
    }

    private void end(boolean success, String message) { reset(); c.work.finish(success, message); }

    /** Every server tick: by himself, what his hand reaches he fills without a step aside (standing and after «стой» too). */
    void tick() {
        if (!(c.level() instanceof ServerLevel level) || c.tickCount % (aside ? 1 : 10) != 0 || !wantsWater(level)) return;
        BlockPos cell = inReach(level);
        if (cell != null) scoop(level, cell, "reflex");
    }

    /**
     * He fills a bucket by himself: an empty one of his that the water can go into, and no water bucket in the bag; no order, goal,
     * deed or request of the player under way (the plan may have made the bucket to give it away), no fight, nothing of his lying about.
     */
    private boolean wantsWater(ServerLevel level) {
        return !nowhere(level) && !c.isPassenger() && c.inventoryViewers == 0 && !c.hasActiveWorkOrder() && !c.goals().outstanding()
                && !c.autonomy().running() && !CompanionAgenda.suppressesDeeds(c) && !CompanionAgenda.requestRunning(c)
                && !c.fighting() && c.getTarget() == null && !c.isSelfRescuing() && !c.hasPendingLoot() && !c.waterBucket.hasPools()
                && !carries(Items.WATER_BUCKET) && CompanionWaterBucket.roomToScoop(c);
    }

    /**
     * A step aside is his to take while he follows the player (who then leads him back) or roams: not standing where he was left,
     * not after «стой» or «стоп» until the next order, not while he guards the player.
     */
    private boolean mayStepAside() {
        return (c.isFollowing() || c.isRoaming()) && !c.autonomy().playerStopped() && !c.isProtecting();
    }

    /** The way to living water within the radius, one tick of it: false when there is none he can get to. */
    private boolean walk(ServerLevel level, int radius, int depth, boolean aside) {
        CompanionRoute route = c.work.route;
        if (path != null && !route.legOver()) return true;
        if (stands == null) {
            if (++legs > LEGS) return false;
            // By himself the water is looked for about where he set off: a walk that ended short does not carry the sixteen blocks on.
            Water water = living(level, aside ? asideFrom : c.blockPosition(), radius, depth, passed);
            sawWater |= !water.body().isEmpty();
            if (water.body().isEmpty()) return false;
            body = water.body();
            stands = standsFor(c, level, water.aimed());
        }
        CompanionRoute.Reach reach = stands.isEmpty() ? null : route.cells(stands, 0);
        // No search ran (this tick's share is spent, or he is in a jump): asked again next tick.
        if (reach != null && (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason()))) return true;
        // By himself only a short walk there (decided 05.11: sixteen blocks), not round through a cave or over a far bridge.
        if (reach == null || !reach.found() || aside && (reach.kind() != CompanionRoute.Kind.WALK || reach.length() > ASIDE_WALK)) {
            // No way to this water (a pond in a closed cave, the sea over his mine): all of it is passed over, the next water looked at.
            passed.addAll(body);
            stands = null;
            return true;
        }
        path = reach.path();
        route.walk(path);
        // Where the walk ends the water is looked for again: in reach, it is taken; a far one is walked in legs.
        stands = null;
        return true;
    }

    private void reset() { stands = null; path = null; legs = 0; passed.clear(); }

    /** One body of water: its cells nearest him that are walked to, and all of it (passed over at once if there is no way to it). */
    record Water(List<BlockPos> aimed, Set<BlockPos> body) { }

    /**
     * The nearest body of living water with open air over it within the radius and {@code depth} above and below him, cells passed
     * over before left out: the loaded sections nearest him first, those without water skipped by their palette, for the nearest
     * cell; then its water joined to it side by side at its height, up to {@link #BODY} cells. One body at a time: a search to a cave
     * pond under his feet and a lake beside together spent itself on the cave, and the lake was passed over with it (AgentTest and
     * GameTest 05.11). None while the game's rule that refills water is off.
     */
    static Water living(ServerLevel level, BlockPos center, int radius, int depth, Set<BlockPos> passed) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION)) return new Water(List.of(), Set.of());
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - depth), maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + depth);
        double far = (double) radius * radius;
        List<int[]> sections = new ArrayList<>();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++)
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++)
                for (int index = level.getSectionIndex(minY); index <= level.getSectionIndex(maxY); index++) sections.add(new int[]{cx, cz, index});
        sections.sort(Comparator.comparingDouble(s -> new BlockPos((s[0] << 4) + 8, (level.getSectionYFromSectionIndex(s[2]) << 4) + 8, (s[1] << 4) + 8).distSqr(center)));
        BlockPos nearest = null;
        int found = 0;
        for (int[] s : sections) {
            if (found >= CELLS) break;
            var chunk = level.getChunkSource().getChunkNow(s[0], s[1]);
            if (chunk == null) continue;
            var section = chunk.getSection(s[2]);
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(Blocks.WATER))) continue;
            int bottom = level.getSectionYFromSectionIndex(s[2]) << 4;
            for (int y = Math.max(minY, bottom); y <= Math.min(maxY, bottom + 15); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                if (!section.getBlockState(x, y & 15, z).is(Blocks.WATER)) continue;
                BlockPos cell = new BlockPos((s[0] << 4) + x, y, (s[1] << 4) + z);
                if (cell.distSqr(center) > far || passed.contains(cell) || !open(level, cell)) continue;
                found++;
                if (nearest == null || cell.distSqr(center) < nearest.distSqr(center)) nearest = cell;
            }
        }
        if (nearest == null) return new Water(List.of(), Set.of());
        Set<BlockPos> body = new HashSet<>(List.of(nearest));
        ArrayDeque<BlockPos> next = new ArrayDeque<>(body);
        while (!next.isEmpty() && body.size() < BODY) {
            BlockPos cell = next.poll();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos beside = cell.relative(side);
                if (!body.contains(beside) && !passed.contains(beside) && beside.distSqr(center) <= far && open(level, beside)) { body.add(beside); next.add(beside); }
            }
        }
        List<BlockPos> aimed = new ArrayList<>(body);
        aimed.sort(Comparator.comparingDouble(cell -> cell.distSqr(center)));
        return new Water(aimed.size() > CELLS ? aimed.subList(0, CELLS) : aimed, body);
    }

    /** Living water with air over it: a bucket from above takes this cell, not one under it, and nothing on it (a lily pad) drops. */
    private static boolean open(ServerLevel level, BlockPos cell) {
        return level.getBlockState(cell.above()).isAir() && living(level, cell);
    }

    /**
     * A water source the game fills again once taken (FlowingFluid.getNewLiquid), with its rule for that on: else a bucket from it
     * leaves a hole in the pond for good.
     */
    static boolean living(ServerLevel level, BlockPos cell) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_WATER_SOURCE_CONVERSION) || !source(level, cell)) return false;
        int beside = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) if (source(level, cell.relative(side))) beside++;
        BlockPos under = cell.below();
        return beside >= 2 && (level.getBlockState(under).isSolid() || source(level, under));
    }

    /**
     * Water itself, a whole source, in a chunk that is loaded (a neighbour across a chunk edge is not loaded for this): not water in a
     * slab or stairs, not a bubble column, not a flow.
     */
    private static boolean source(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    /** The cells he can stand at with one of these within his reach and sight from anywhere in the cell. */
    static Set<BlockPos> standsFor(CompanionEntity c, ServerLevel level, List<BlockPos> water) {
        Set<BlockPos> stands = new HashSet<>();
        java.util.Map<BlockPos, Boolean> standable = new java.util.HashMap<>();
        for (BlockPos cell : water) for (int dy = -1; dy <= 2; dy++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos stand = cell.offset(dx, dy, dz);
            if (stands.contains(stand) || !standable.computeIfAbsent(stand, at -> CompanionMiningNavigation.standable(c, at))) continue;
            if (seen(c, level, Vec3.atBottomCenterOf(stand).add(0, c.getEyeHeight(), 0), cell, STAND_REACH)) stands.add(stand.immutable());
        }
        return stands;
    }

    /** The nearest living water his hand reaches from where he is, or null. */
    private BlockPos inReach(ServerLevel level) {
        Vec3 eyes = c.getEyePosition();
        BlockPos best = null;
        double bestDistance = REACH * REACH;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(eyes).offset(-5, -5, -5), BlockPos.containing(eyes).offset(5, 5, 5))) {
            double distance = eyes.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance <= bestDistance && level.getBlockState(pos).is(Blocks.WATER) && open(level, pos) && seen(c, level, eyes, pos, REACH)) {
                best = pos.immutable(); bestDistance = distance;
            }
        }
        return best;
    }

    /** Within this reach from these eyes and in their sight: not through a wall. */
    static boolean seen(CompanionEntity c, ServerLevel level, Vec3 eyes, BlockPos cell, double reach) {
        Vec3 water = Vec3.atCenterOf(cell);
        if (eyes.distanceToSqr(water) > reach * reach) return false;
        HitResult hit = level.clip(new ClipContext(eyes, water, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        return hit.getType() == HitResult.Type.MISS || hit instanceof net.minecraft.world.phys.BlockHitResult block && block.getBlockPos().equals(cell);
    }

    /**
     * Into an empty bucket of his. A bucket he carries for the player is one only while he sorts the chests (CompanionSorting.onlyCarried),
     * and then he does nothing else: the player's bucket stays as it was (decided 05.11).
     */
    private boolean scoop(ServerLevel level, BlockPos cell, String why) {
        if (!CompanionWaterBucket.scoopInto(c, level, cell)) return false;
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), "water_bucket_filled", "at", c.blockPosition().toShortString(),
                "water", cell.toShortString(), "why", why);
        return true;
    }

    private boolean carries(Item item) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) return true;
        return false;
    }

    /** A step aside to living water by himself, when his hand reaches none: sixteen blocks, a short walk, twenty seconds at most. */
    static final class RefillGoal extends Goal {
        private final CompanionEntity c;
        RefillGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override public boolean canUse() {
            CompanionWaterFill fill = c.waterFill;
            if (!(c.level() instanceof ServerLevel level) || level.getGameTime() < fill.nextLook || !fill.mayStepAside() || !fill.wantsWater(level)) return false;
            long now = level.getGameTime();
            fill.nextLook = now + LOOK;
            // A step aside from here came to nothing (a pond behind a fence): not again until he is elsewhere or some minutes have passed.
            if (fill.failedAt != null && now - fill.failedTime < RETRY && c.blockPosition().closerThan(fill.failedAt, ASIDE)) return false;
            if (fill.inReach(level) != null) return false;
            fill.reset();
            Water water = living(level, c.blockPosition(), ASIDE, DEPTH_ASIDE, fill.passed);
            if (water.body().isEmpty()) return false;
            fill.body = water.body();
            fill.stands = standsFor(c, level, water.aimed());
            return true;
        }
        @Override public boolean canContinueToUse() {
            CompanionWaterFill fill = c.waterFill;
            return !fill.gaveUp && c.level() instanceof ServerLevel level && fill.mayStepAside() && fill.wantsWater(level);
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() {
            CompanionWaterFill fill = c.waterFill;
            fill.aside = true; fill.gaveUp = fill.told = false;
            fill.asideSince = c.level().getGameTime(); fill.asideFrom = c.blockPosition();
        }
        @Override public void tick() {
            CompanionWaterFill fill = c.waterFill;
            long now = c.level().getGameTime();
            boolean on = c.level() instanceof ServerLevel level && fill.walk(level, ASIDE, DEPTH_ASIDE, true);
            // Said once a way is found, not when the water turns out to be out of his reach.
            if (on && fill.path != null && !fill.told) {
                fill.told = true;
                if (now - fill.saidAt >= TALK) { fill.saidAt = now; c.autonomySay(GOING, false); }
            }
            if (on && now - fill.asideSince <= ASIDE_TICKS) return;
            fill.gaveUp = true;
            fill.failedAt = fill.asideFrom; fill.failedTime = now;
            dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), "water_bucket_aside_failed", "at", c.blockPosition().toShortString(),
                    "from", fill.asideFrom.toShortString(), "ticks", now - fill.asideSince);
        }
        @Override public void stop() { c.getNavigation().stop(); c.waterFill.reset(); c.waterFill.aside = false; }
    }
}

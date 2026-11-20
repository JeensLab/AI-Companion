package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Obsidian like a player gets it (decided 08.11): lava he can walk to within his search radius, his bucket of water poured over it
 * so the lava under the water sets, the obsidian mined with a pickaxe that takes it and picked up, his water scooped back before
 * the next lava and at the end. With no lava about he makes it of his own bucket of lava (the player 19.11): a hole dug beside him
 * in the ground, his lava poured into it, his water over it, the block waited for, his water scooped back before the dig. Obsidian
 * already lying about is mined too, but never a portal's; one on a site where the player builds only when nothing free-standing is
 * about — his building is protected while other obsidian exists, and the blocks he set down himself are still minable when he asks
 * for them. A bucket of lava or water dropped within his hand is a source as much as one in his bag: it is taken up before the first
 * pour. The obsidian his own water set is his wherever it lies. Under the obsidian there is often more lava: a block with his water
 * over it is mined, as the water runs into the hole and sets the lava under it before the drop falls; a dry one over lava or beside it
 * is left (its drop would burn).
 */
final class CompanionObsidian {
    static final String NO_PICKAXE = "Обсидиан берёт только алмазная кирка — дай мне её.";
    static final String NETHER = "В Незере обсидиан не сделать: вода здесь испаряется.";
    static final String END = "В Энде обсидиан не сделать: лавы здесь нет.";
    static final String NO_LAVA = "Не нашёл лавы рядом — отведи меня к ней.";
    static final String NO_WATER = "Нет ведра воды — нечем залить лаву.";
    static final String NO_SITE = "Негде вылить лаву: рядом нет ямки, где она не растечётся.";
    static final String NOT_SET = "Лава не застыла — обсидиан не вышел.";
    static final String FULL_BAG = "Сумка забита — некуда сложить обсидиан.";
    static final String WATER_LEFT = "Не дотянулся до своей воды — осталась у лавы.";
    /** A player's reach for a block, as his bucket has it (CompanionWaterBucket); a cell to stand at keeps a margin. */
    private static final double REACH = 4.5;
    /** Lava and obsidian above and below his feet that are looked at (blocks), as water is (CompanionWaterFill). */
    private static final int DEPTH = 32;
    /** Pours one order takes at most, walks to one place before it is passed over, lava cells of one pond remembered as his. */
    private static final int POURS = 16, LEGS = 8, BODY = 512;
    /** How far his water runs from where it is poured (seven cells on the level) and how deep the lava under it is his. */
    private static final int SPREAD = 8, BELOW = 8;
    /** With none to mine, obsidian is looked for again this often (ticks): set lava shows at once, a search costs. */
    private static final int LOOK = 10;
    /** Buckets lying about him looked for at once when one is out of his way (each is a walk), and how many are tried. */
    private static final int FETCH_TRIES = 4;
    /** How far he may be pushed off the place he mines from before he walks to the block again (blocks). */
    private static final double HOLD = .5;
    /** Ticks his water takes to run off once scooped (seven cells, five ticks each) and the drops to come to rest. */
    private static final int SETTLE = 60;
    /** How long the drops floating in his water are left alone at most (ticks), as long as his harvest is remembered. */
    private static final int SHUN = 20 * 300;
    /** Stages of a block he makes of his own buckets: the hole dug beside him, his lava in it, his water over it, the block waited for. */
    private static final int MADE_DIG = 1, MADE_LAVA = 2, MADE_WATER = 3, MADE_SET = 4;
    /** How far beside him a hole is dug for his lava (blocks), the hardest ground dug for it, and how long the water may take to set it (ticks). */
    private static final int HOLE_NEAR = 3;
    private static final float HOLE_HARDEST = 3;
    private static final int SET_TICKS = 60;

    private final CompanionWork w;
    private final CompanionEntity c;
    /** Obsidian wanted, in his bag at the order, blocks mined for it (picked up or not), pours and walks so far. */
    private int wanted, before, mined, pours, legs;
    private long nextLook, settleAt;
    private boolean retried;
    /** The obsidian he mines; the cell over lava he goes to pour into; his water standing; the cells to stand at for either. */
    private BlockPos target, aim, water;
    /** The hole his own lava goes into, his water over it, the stage of it, when the water was poured, blocks made of buckets, and the lava of a block that did not set. */
    private BlockPos made, madeWater, madeLava;
    private int madeStep, fromBuckets;
    private long madeAt;
    private Set<BlockPos> stands = Set.of();
    private Path path;
    /** Where he stood when the block came within his reach and sight. */
    private Vec3 foothold;
    /** How the order ends, held while his water is scooped first. */
    private CompanionActionResult ending;
    /** The player's building sites here, taken once a tick (a block scan asks about many cells). */
    private java.util.function.Predicate<BlockPos> site = pos -> false;
    /** Cells of lava his water set or is setting: their obsidian is his even beside a portal. */
    private final Set<BlockPos> his = new HashSet<>();
    /** Obsidian and lava he found no way to. */
    private final Set<BlockPos> passed = new HashSet<>();
    /** The bucket lying about him he walks to and takes up, and the drops no way led to (named honestly, never «нет лавы»). */
    private ItemEntity fetch;
    private final Set<java.util.UUID> fetchless = new HashSet<>();

    CompanionObsidian(CompanionWork w) { this.w = w; this.c = w.owner; }

    /** Why no obsidian is to be had here with what he carries, or null: no pickaxe that takes it (he does not go for diamonds), the Nether, the End. */
    static String refusal(Level level, Iterable<ItemStack> carried) {
        if (level.dimensionType().ultraWarm()) return NETHER;
        if (level.dimension() == Level.END) return END;
        for (ItemStack stack : carried) if (takesObsidian(stack)) return null;
        return NO_PICKAXE;
    }

    static boolean takesObsidian(ItemStack stack) { return !stack.isEmpty() && stack.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()); }

    /**
     * Buckets of this kind lying on the ground about him, as far as they are his to take: how many his hand reaches from where he
     * stands, how many lie within his sight with a cell he can take them from — he walks to those and takes them up — and whether
     * any lie in his sight with no such cell at all. A bucket dropped beside him is a source for an order as much as one in his bag,
     * so the plan counts it (CompanionObtain) and the work walks to it and takes it up (fetchLoose).
     */
    record Loose(int reach, int fetch, boolean sight) { static final Loose NONE = new Loose(0, 0, false); }

    static Loose loose(CompanionEntity c, ServerLevel level, Item bucket) {
        int reach = 0, fetch = 0;
        boolean sight = false;
        int radius = c.getSearchRadius();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(c.blockPosition()).inflate(radius))) {
            if (!drop.isAlive() || !drop.getItem().is(bucket) || !CompanionPickUp.pickable(drop, c.getOwnerId())) continue;
            if (c.reachesItem(drop)) { reach += drop.getItem().getCount(); continue; }
            if (drop.distanceToSqr(c.position()) > (double) radius * radius) continue;
            // A cell to take it from is a bucket he walks to; seen with none at all, it is named when nothing else is to be had.
            if (!standsFor(c, drop).isEmpty()) { fetch += drop.getItem().getCount(); continue; }
            if (c.hasLineOfSight(drop)) sight = true;
        }
        return new Loose(reach, fetch, sight);
    }

    /**
     * The cells he can take this drop from, as his pickup does (CompanionLoot.tickChase): standing on the floor or swimming, the
     * drop within the reach of his hand. Empty when no cell near it is one.
     */
    private static Set<BlockPos> standsFor(CompanionEntity c, ItemEntity drop) {
        Set<BlockPos> stands = new HashSet<>();
        BlockPos center = drop.blockPosition();
        for (int dy = drop.isInWater() ? 0 : -2; dy <= 1; dy++) for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
            BlockPos cell = center.offset(ox, dy, oz);
            if (CompanionMiningNavigation.pickupStand(c, cell) && c.loot.touches(Vec3.atBottomCenterOf(cell), drop)) stands.add(cell.immutable());
        }
        return stands;
    }

    /** «Вижу рядом вёдра лавы и воды, но не дотянуться — подойди ближе, я подберу.»: the buckets are there, his hand is not. */
    static String unreached(boolean lava, boolean water) {
        String what = lava && water ? "вёдра лавы и воды" : lava ? "ведро лавы" : "ведро воды";
        return "Вижу рядом " + what + ", но не дотянуться — подойди ближе, я подберу.";
    }

    /**
     * The buckets of this kind lying within his hand are taken up before the work starts, as his pickup takes a drop: what the bag
     * cannot hold stays where it lies, so no bucket is lost and none is doubled.
     */
    static int takeLoose(CompanionEntity c, ServerLevel level, Item bucket) {
        int taken = 0;
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, c.getBoundingBox().inflate(2), found -> found.isAlive() && found.getItem().is(bucket))) {
            if (!c.reachesItem(drop) || !CompanionPickUp.pickable(drop, c.getOwnerId())) continue;
            ItemStack offered = drop.getItem().copy();
            int before = offered.getCount();
            c.storeLoot(offered);
            int moved = before - offered.getCount();
            if (moved <= 0) continue;
            // As his pickup does it: the packet, then what is left of the stack (the game's take only tells the clients).
            c.take(drop, moved);
            if (offered.isEmpty()) drop.discard(); else drop.setItem(offered);
            taken += moved;
            level.playSound(null, c.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, .2F, 1.1F);
        }
        return taken;
    }

    /**
     * Whether obsidian he may mine lies within his reach now: it is mined where it lies, so no lava is cooled and no bucket of
     * water is needed for it. What a portal's frame is and what lies beside lava is not, and what stands on a site of the player's
     * only when nothing free-standing is about — his building is protected while other obsidian exists, and the blocks he set down
     * himself are still minable when he asks for them.
     */
    static boolean liesAbout(CompanionEntity c, ServerLevel level, Predicate<BlockPos> laid) {
        Predicate<BlockPos> site = CompanionPicking.sites(c, level);
        // The block under his feet is one a player mines too: its drop comes to rest in the hole it leaves and he steps down to it.
        return nearest(c, level, DEPTH, state -> state.is(Blocks.OBSIDIAN),
                pos -> !laid.test(pos) && free(level, pos, site, false)) != null
                || nearest(c, level, DEPTH, state -> state.is(Blocks.OBSIDIAN),
                pos -> !laid.test(pos) && free(level, pos, site, true)) != null;
    }

    /** mine_obsidian: {@code count} more obsidian in his bag. Null when the work may start, else why not. */
    CompanionActionResult begin(int count) {
        String refusal = refusal(c.level(), w.items.carriedItems());
        if (refusal != null) return CompanionActionResult.failed(refusal);
        reset();
        wanted = count; before = w.items.countCarried(Items.OBSIDIAN); mined = pours = 0; water = null; ending = null; nextLook = settleAt = 0; retried = false;
        made = madeWater = madeLava = null; madeStep = fromBuckets = 0; madeAt = 0;
        his.clear(); passed.clear();
        fetch = null; fetchless.clear();
        // The buckets lying within his hand are his before the first pour: the plan counted them as the source (CompanionObtain).
        // The ones further off he walks to and takes up as the work goes (fetchLoose).
        if (c.level() instanceof ServerLevel level) {
            int taken = takeLoose(c, level, Items.LAVA_BUCKET) + takeLoose(c, level, Items.WATER_BUCKET);
            if (taken > 0) event("obsidian_buckets_taken", "buckets", taken);
        }
        return null;
    }

    /** Blocks mined for the order: progress for the watchdog while the drops wait in his water. */
    int mined() { return mined; }

    String jobName() { return "добычу обсидиана (" + got() + " из " + wanted + ")"; }

    private int got() { return Math.max(0, w.items.countCarried(Items.OBSIDIAN) - before); }

    /** One tick of the order: obsidian mined while there is some, else his water back, else on to the next lava and over it, else made of his own buckets. */
    void tick(ServerLevel level) {
        if (ending != null) { if (takeWaterBack(level)) end(ending); return; }
        site = c.getOwnerId() == null ? pos -> false : CompanionPlayerBuilds.get(level.getServer()).sites(c.getOwnerId(), level);
        int got = got();
        if (got >= wanted) { if (takeWaterBack(level)) end(true, "Накопал обсидиана: " + got + "."); return; }
        if (refusal(level, w.items.carriedItems()) != null) { end(false, NO_PICKAXE + short_(got)); return; }
        // A block of his own buckets is being made: the hole, his lava, his water, the block waited for.
        if (madeStep != 0) { make(level, got); return; }
        if (mined >= wanted) { collect(level, got); return; }
        if (target != null && !minable(level, target, true)) { target = null; w.breaking.reset(); w.resetApproach(); }
        if (target == null && level.getGameTime() >= nextLook) {
            target = nearest(c, level, DEPTH, state -> state.is(Blocks.OBSIDIAN), pos -> minable(level, pos, false));
            // Nothing free-standing about: the obsidian the player set down on his own site is minable too, never a portal's frame and
            // never over lava — the building is protected only while other obsidian exists.
            if (target == null) target = nearest(c, level, DEPTH, state -> state.is(Blocks.OBSIDIAN), pos -> minable(level, pos, true));
            if (target == null) nextLook = level.getGameTime() + LOOK;
        }
        if (target != null) { mine(level, got); return; }
        // Nothing more to mine here: his water back in the bucket before the next lava.
        if (water != null) { takeWaterBack(level); return; }
        // A bucket the plan counted lies on the ground within his sight: he walks to it and takes it up before the make.
        if (fetchLoose(level)) return;
        pour(level, got);
    }

    /**
     * A bucket the plan counted lies on the ground within his sight: he walks to it and takes it up, as his pickup takes a drop —
     * what the bag cannot hold stays lying, so no bucket is lost and none is doubled. True while that is what he does this tick.
     * A drop no way leads to is given up honestly, and the next one of a kind he needs is looked for; the pour step then says what
     * is missing, never «Не нашёл лавы рядом» over a bucket in his sight.
     */
    private boolean fetchLoose(ServerLevel level) {
        for (int tried = 0; tried < FETCH_TRIES; tried++) {
            ItemEntity drop = fetch != null && fetch.isAlive() && needed(fetch) && !fetchless.contains(fetch.getUUID()) ? fetch : nearestLoose(level);
            if (drop == null) { fetch = null; return false; }
            if (drop != fetch) { fetch = drop; stands = Set.of(); path = null; legs = 0; }
            if (c.reachesItem(drop)) {
                int taken = takeLoose(c, level, drop.getItem().getItem());
                if (taken <= 0) { end(false, FULL_BAG + short_(got())); return true; }
                event("obsidian_buckets_fetched", "buckets", taken);
                reset();
                return true;
            }
            if (stands.isEmpty()) { stands = standsFor(c, drop); legs = 0; path = null; }
            c.workStatus("Иду за ведром.");
            if (walk()) return true;
            // No way to it: given up honestly, the next bucket of a kind he needs is looked for.
            fetchless.add(drop.getUUID());
            reset();
        }
        return false;
    }

    /** Whether this drop is a bucket of a kind he still needs from the ground: lava or water that is not in his bag. */
    private boolean needed(ItemEntity drop) {
        if (!drop.isAlive()) return false;
        Item item = drop.getItem().getItem();
        return item == Items.LAVA_BUCKET && slot(Items.LAVA_BUCKET) < 0 || item == Items.WATER_BUCKET && slot(Items.WATER_BUCKET) < 0;
    }

    /** The nearest bucket lying about him that he still needs and has not given up, or null. */
    private ItemEntity nearestLoose(ServerLevel level) {
        ItemEntity best = null;
        double bestFar = Double.MAX_VALUE;
        int radius = c.getSearchRadius();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(c.blockPosition()).inflate(radius))) {
            if (!needed(drop) || fetchless.contains(drop.getUUID()) || !CompanionPickUp.pickable(drop, c.getOwnerId())) continue;
            double far = drop.distanceToSqr(c.position());
            if (far > (double) radius * radius || far >= bestFar) continue;
            bestFar = far; best = drop;
        }
        return best;
    }

    /** Whether a bucket of this kind he still needs lies about him and no way led to it: named plainly, never «Не нашёл лавы рядом». */
    private boolean leftBehind(ServerLevel level, Item item) {
        for (java.util.UUID id : fetchless) {
            if (level.getEntity(id) instanceof ItemEntity drop && drop.isAlive() && drop.getItem().is(item) && CompanionPickUp.pickable(drop, c.getOwnerId())) return true;
        }
        return false;
    }

    /** Why his water is not to be had: the bucket he needs lies there with no way to it, else none at all. */
    private String noWater(ServerLevel level, int got) {
        return leftBehind(level, Items.WATER_BUCKET) ? unreached(false, true) + short_(got) : NO_WATER + short_(got);
    }

    /**
     * Enough is mined: his water back first, the drops floating in it (the pickup takes no drop over a pit under water) settle in
     * the dry holes, then they are picked up (the work waits while the pickup runs). What is still not in his bag after that is
     * lost, burnt or washed away, and more is mined.
     */
    private void collect(ServerLevel level, int got) {
        if (!takeWaterBack(level)) return;
        long now = level.getGameTime();
        if (settleAt == 0) { settleAt = now + SETTLE; return; }
        if (now < settleAt) return;
        if (!retried) { retried = true; c.retryHarvestPickup(); return; }
        event("obsidian_drops_lost", "mined", mined, "loot", c.loot.diagnosticState());
        mined = got; settleAt = 0; retried = false;
    }

    private String short_(int got) { return got == 0 ? "" : " Накопал " + got + " из " + wanted + "."; }

    /**
     * A failure ends obtain_item at once with what he says (BLOCKED): planned again, the same lava was looked for twice more. With
     * his water out it is scooped first, and the drops let lie in it are given back to the pickup (left for the order's end, they
     * lay until they were gone).
     */
    private void end(boolean success, String message) { end(success ? CompanionActionResult.ok(message) : CompanionActionResult.blocked(message)); }

    private void end(CompanionActionResult result) {
        if (water != null) { ending = result; return; }
        // The lava of a block that did not set goes back into his empty bucket once his water is in its own.
        if (madeLava != null && c.level() instanceof ServerLevel level) {
            boolean back = takeLavaBack(c, level, madeLava);
            event(back ? "obsidian_lava_scooped" : "obsidian_lava_left", "lava", madeLava.toShortString(), "took", back);
        }
        madeLava = null;
        if (mined > got()) c.retryHarvestPickup();
        ending = null;
        reset();
        w.finish(result);
    }

    /** Stopped or given another order: his water is scooped if his hand reaches it, else it stays (stop means stop). */
    void cancel() {
        if (water != null && c.level() instanceof ServerLevel level && inReach(level, water)) CompanionWaterBucket.scoopInto(c, level, water);
        if (madeWater != null && c.level() instanceof ServerLevel level && inReach(level, madeWater)) CompanionWaterBucket.scoopInto(c, level, madeWater);
        water = madeWater = null; made = madeLava = null; madeStep = 0; ending = null;
        reset();
    }

    private void reset() { target = aim = null; stands = Set.of(); path = null; foothold = null; legs = 0; fetch = null; }

    /**
     * Obsidian he may mine now: his own or lying about but no portal's, its drop safe from lava, and on a site of the player's only
     * when {@code onSite} — nothing free-standing was found, so the blocks the player set down himself are his to take.
     */
    private boolean minable(ServerLevel level, BlockPos pos, boolean onSite) {
        // The block under his feet is mined too, as a player mines it: its drop rests in the hole and he steps down into it.
        if (passed.contains(pos)) return false;
        // While his water is out, only what it sets: a walk to obsidian far off would leave the water behind him.
        if (water != null && !pos.closerThan(water, SPREAD + 1)) return false;
        if (site.test(pos) && !onSite) return false;
        if (w.portal.laid(pos)) return false;
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(Blocks.OBSIDIAN)) return false;
        if (!his.contains(pos) && (portal(level, pos) || frame(level, pos))) return false;
        return safe(level, pos);
    }

    /** Whether the block at this cell is his to mine at all: no portal's frame, its drop safe from lava, and on a site only when {@code onSite}. */
    private static boolean free(ServerLevel level, BlockPos pos, Predicate<BlockPos> site, boolean onSite) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.OBSIDIAN) && (onSite || !site.test(pos))
                && !portal(level, pos) && !frame(level, pos) && safe(level, pos);
    }

    /** Whether the drop of the block here rests where he can pick it up: under his water, or nowhere near lava. */
    private static boolean safe(ServerLevel level, BlockPos pos) {
        if (wet(level, pos)) return true;
        for (Direction side : Direction.values()) if (CompanionLava.at(level, pos.relative(side))) return false;
        return true;
    }

    /** Water over it: it runs into the hole when the block is gone and sets the lava under it before the drop gets there. */
    private static boolean wet(ServerLevel level, BlockPos pos) { return level.getFluidState(pos.above()).is(FluidTags.WATER); }

    /** A Nether portal touches it, corners too: it is part of a portal's frame. */
    private static boolean portal(ServerLevel level, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) if (level.getBlockState(near).is(Blocks.NETHER_PORTAL)) return true;
        return false;
    }

    /**
     * A portal's frame not lit yet, corners or not: an empty frame stands beside it (PortalShape, as the flint and steel finds it).
     * The player's, built before the mod knew his buildings, or anyone's; the broken frame of ruined portals is no frame.
     */
    private static boolean frame(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos inside = pos.relative(side);
            if (!level.getBlockState(inside).isAir()) continue;
            if (net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(level, inside, Direction.Axis.X).isPresent()
                    || net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(level, inside, Direction.Axis.Z).isPresent()) return true;
        }
        return false;
    }

    private void mine(ServerLevel level, int got) {
        BlockState state = level.getBlockState(target);
        // Once he stands where he sees it he mines from there: in his running water he sways a few hundredths of a block, and the
        // shore's edge took a block level with it in and out of his sight every other tick (GameTest 08.11).
        // The next block within his reach and sight where he stands is mined from there, as a player does: walked to anew, he went
        // against his running water for seconds (in game 08.11). Dry, the drop must also rest where he picks it up (the approach).
        if (foothold == null && wet(level, target) && inReach(c.getEyePosition(), target) && CompanionMiningNavigation.visible(c, c.getEyePosition(), target)) {
            foothold = c.position();
            w.route.stop();
        }
        boolean holds = foothold != null && c.position().distanceToSqr(foothold) < HOLD * HOLD;
        // Dry, its drop must rest where he can pick it up; under his water it floats until the water is back in his bucket (collect).
        int reach = holds ? 1 : w.items.reachBlock(level, target, !wet(level, target));
        if (reach < 0) { passed.add(target); target = null; w.breaking.reset(); w.resetApproach(); return; }
        if (reach == 0) { foothold = null; return; }
        // In his running water he keeps his place, as a player walks against the stream.
        if (foothold == null) foothold = c.position();
        if (c.isInWater()) c.getMoveControl().setWantedPosition(foothold.x, foothold.y, foothold.z, 1.0);
        if (!pickaxe()) { end(false, NO_PICKAXE + short_(got)); return; }
        ItemStack tool = c.getMainHandItem();
        if (!w.items.canStore(List.of(new ItemStack(Items.OBSIDIAN)))) { end(false, FULL_BAG + short_(got)); return; }
        c.workStatus("Копаю обсидиан.");
        if (!w.breaking.tick(level, target)) return;
        var drops = CompanionTools.drops(state, level, target, null, c, tool);
        if (!level.destroyBlock(target, false, c, 512)) { passed.add(target); target = null; return; }
        CompanionTools.wearOnBreak(tool, level, state, target, c);
        c.dropHarvest(target, drops);
        mined++;
        // Floating in his running water the drop is no use chasing (in game 08.11 each was chased ten seconds in vain): it waits
        // until the water is back in the bucket (collect).
        if (water != null) c.loot.shun(level.getGameTime() + SHUN);
        event("obsidian_mined", "obsidian", target.toShortString(), "his", his.contains(target), "wet", wet(level, target));
        target = null; foothold = null;
        w.resetApproach();
    }

    /** To the nearest lava with air over it and his water poured there; the lava under the water is his from now on. With no lava about, his own bucket of it makes the block (see {@link #make}). */
    private void pour(ServerLevel level, int got) {
        if (aim == null) {
            BlockPos lava = pours >= POURS ? null
                    : nearest(c, level, DEPTH, state -> state.is(Blocks.LAVA), pos -> !passed.contains(pos) && level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).isAir() && !site.test(pos));
            if (lava == null) {
                // What he mined and has not picked up yet is picked up before he says how far he got.
                if (mined > got) { collect(level, got); return; }
                // No lava here to cool: with his own bucket of lava and his water he makes the obsidian where he stands.
                boolean lavaOfHis = slot(Items.LAVA_BUCKET) >= 0;
                if (lavaOfHis && slot(Items.WATER_BUCKET) >= 0) { madeStep = MADE_DIG; return; }
                // The one that is missing is named, never the lava he holds: with his own lava and no water, the water is what he lacks.
                // The order stops there (the next one plans the water first), as it does when nothing is left to pour.
                if (lavaOfHis) { end(false, noWater(level, got)); return; }
                // A bucket he needs lies there with no way to it: named plainly, never «Не нашёл лавы рядом» over it.
                boolean lavaLeft = leftBehind(level, Items.LAVA_BUCKET), waterLeft = leftBehind(level, Items.WATER_BUCKET);
                if (lavaLeft || waterLeft) { end(false, unreached(lavaLeft, waterLeft) + short_(got)); return; }
                end(false, pours == 0 && got == 0 && fromBuckets == 0 ? NO_LAVA
                        : "Накопал " + got + " из " + wanted + (fromBuckets > 0 ? ": вёдра лавы кончились." : ": лава рядом кончилась."));
                return;
            }
            aim = lava.above(); stands = CompanionWaterFill.standsFor(c, level, List.of(aim)); legs = 0; path = null;
        }
        int slot = slot(Items.WATER_BUCKET);
        // Planned again, obtain_item gets water first (his was left, or a fall took it): no BLOCKED here.
        if (slot < 0) { end(CompanionActionResult.failed(noWater(level, got))); return; }
        if (!inReach(level, aim)) {
            c.workStatus("Иду к лаве.");
            // No cell to pour it from (the middle of a lake): only this lava is passed over; no way there: all of it.
            if (!walk()) { if (stands.isEmpty()) passed.add(aim.below()); else passed.addAll(pond(level, aim.below())); aim = null; }
            return;
        }
        if (!level.getBlockState(aim).isAir() || !CompanionLava.at(level, aim.below())) { aim = null; return; }
        Set<BlockPos> under = lavaUnder(level, aim);
        // His water runs seven cells: lava of a building of the player's within its run is not poured over (second review 08.11).
        if (under.stream().anyMatch(site)) { passed.addAll(pond(level, aim.below())); aim = null; return; }
        ItemStack bucket = c.getItem(slot);
        c.getLookControl().setLookAt(Vec3.atCenterOf(aim));
        his.addAll(under);
        if (!((BucketItem) bucket.getItem()).emptyContents(null, level, aim, null, bucket)) { passed.addAll(pond(level, aim.below())); aim = null; return; }
        emptyBucket(slot);
        c.animateAction(1, aim, bucket);
        water = aim; aim = null; pours++; nextLook = 0; reset();
        c.workStatus("Заливаю лаву водой.");
        event("obsidian_water_poured", "water", water.toShortString(), "pours", pours);
    }

    /**
     * Obsidian made of his own buckets, as a player makes it (the player 19.11): a hole dug beside him in the ground, which walls
     * his lava in, the lava poured into it, his water poured over it, the block waited for until the game sets it, his water scooped
     * back before the dig (it would otherwise lie over the block and float its drop), and the block mined by the mining step that
     * follows. One pour takes one of his buckets of lava; the empty buckets stay in his bag, and his water comes back into its own.
     */
    private void make(ServerLevel level, int got) {
        switch (madeStep) {
            case MADE_DIG -> {
                if (made == null) made = siteFor(level);
                if (made == null) { end(false, NO_SITE + short_(got)); return; }
                // His own hole of the block before is air: the lava goes straight in.
                if (level.getBlockState(made).isAir()) { madeStep = MADE_LAVA; reset(); return; }
                // Something else stands there since: another hole is looked for.
                if (!holeFor(level, made)) { made = null; reset(); return; }
                if (dig(level, made, got)) { madeStep = MADE_LAVA; reset(); }
            }
            case MADE_LAVA -> {
                // No lava of his left: the pour step says how far he got.
                if (slot(Items.LAVA_BUCKET) < 0) { made = null; madeStep = 0; reset(); return; }
                if (!level.getBlockState(made).isAir()) { made = null; madeStep = MADE_DIG; reset(); return; }
                // The drop of the dig lies in the hole and the pickup walks him down into it: the lava goes in from beside it, never
                // under his own feet (the player 20.11: he climbed into the lava and lost health). No cell beside it at all: this
                // hole is passed over and another dug.
                if (c.blockPosition().equals(made)) { walkTo(level, made); return; }
                if (!inReach(level, made) && !walkTo(level, made)) return;
                int slot = slot(Items.LAVA_BUCKET);
                ItemStack bucket = c.getItem(slot);
                c.getLookControl().setLookAt(Vec3.atCenterOf(made));
                if (!((BucketItem) bucket.getItem()).emptyContents(null, level, made, null, bucket)) { made = null; madeStep = MADE_DIG; reset(); return; }
                emptyBucket(slot);
                c.animateAction(1, made, bucket);
                his.add(made);
                madeStep = MADE_WATER;
                c.workStatus("Лью лаву.");
                event("obsidian_lava_poured", "lava", made.toShortString());
            }
            case MADE_WATER -> {
                // The lava ran off or was taken: another hole, or the pour step's own words.
                if (!level.getFluidState(made).isSourceOfType(net.minecraft.world.level.material.Fluids.LAVA)) { made = null; madeStep = MADE_DIG; reset(); return; }
                // No water left for the lava he has already poured: the lava is his, so it goes back into the empty bucket of the pour (end).
                if (slot(Items.WATER_BUCKET) < 0) { abandonMade(CompanionActionResult.failed(NO_WATER + short_(got))); return; }
                if (!inReach(level, made.above()) && !walkTo(level, made.above())) return;
                int slot = slot(Items.WATER_BUCKET);
                if (slot < 0) { abandonMade(CompanionActionResult.failed(NO_WATER + short_(got))); return; }
                ItemStack bucket = c.getItem(slot);
                c.getLookControl().setLookAt(Vec3.atCenterOf(made.above()));
                // The pour found no cell: the lava of it goes back into his bucket and another hole is dug.
                if (!((BucketItem) bucket.getItem()).emptyContents(null, level, made.above(), null, bucket)) { madeLava = made; made = null; madeStep = MADE_DIG; reset(); return; }
                emptyBucket(slot);
                c.animateAction(1, made.above(), bucket);
                madeWater = made.above();
                madeAt = level.getGameTime();
                madeStep = MADE_SET;
                c.workStatus("Заливаю лаву водой.");
                event("obsidian_water_poured", "water", madeWater.toShortString(), "lava", made.toShortString());
            }
            case MADE_SET -> {
                if (level.getBlockState(made).is(Blocks.OBSIDIAN)) {
                    // His water back first: over the block it would float the drop away from the hole.
                    if (CompanionWaterBucket.scoopInto(c, level, madeWater)) event("obsidian_water_scooped", "water", madeWater.toShortString());
                    else water = madeWater;
                    BlockPos block = made;
                    madeWater = null; made = null; madeStep = 0; fromBuckets++; reset();
                    event("obsidian_made", "block", block.toShortString());
                    return;
                }
                // Not set in time: his water comes back first (over the block it would float the drop), the lava he poured with it after
                // — a source left standing in the hole is no use to him, and the empty bucket of the pour takes it back as his own does.
                if (level.getGameTime() - madeAt > SET_TICKS) {
                    water = madeWater; madeWater = null; madeLava = made; made = null; madeStep = 0; reset();
                    end(false, NOT_SET + short_(got));
                    return;
                }
                c.workStatus("Жду, пока лава застынет.");
            }
            default -> { }
        }
    }

    /**
     * The lava of the make is his once poured: with no water to pour over it, it goes back into the empty bucket the pour left him
     * (end) instead of standing in the hole, so no bucket is spent for a block that was not made.
     */
    private void abandonMade(CompanionActionResult result) { madeLava = made; made = null; madeStep = 0; reset(); end(result); }

    /**
     * The nearest hole for his own lava beside him: ground he can dig, or the hole his last block left, walled by the ground itself
     * on every side, with a firm dry floor, air over it and over that, dry about it, no one standing in it but him, and not where the
     * player builds. Null when there is none.
     */
    private BlockPos siteFor(ServerLevel level) {
        BlockPos feet = c.blockPosition();
        BlockPos best = null;
        double bestFar = Double.MAX_VALUE;
        for (int dx = -HOLE_NEAR; dx <= HOLE_NEAR; dx++) for (int dz = -HOLE_NEAR; dz <= HOLE_NEAR; dz++) for (int dy = 1; dy >= -2; dy--) {
            BlockPos cell = feet.offset(dx, dy, dz);
            // Never the cell he stands in, nor the block under his feet: the lava there would be under him. Nor one he found no way to.
            if (cell.equals(feet) || cell.equals(feet.below()) || passed.contains(cell)) continue;
            if (!level.hasChunkAt(cell) || site.test(cell) || !holeFor(level, cell)) continue;
            double far = cell.distSqr(feet);
            if (far < bestFar) { bestFar = far; best = cell.immutable(); }
        }
        return best;
    }

    /** Whether this cell is a hole his lava stays in: air or ground he can dig, the ground walling it on every side, a firm floor, air over it and dry about. */
    private boolean holeFor(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        if (!level.getFluidState(cell).isEmpty() || !level.hasChunkAt(cell.below())) return false;
        if (!state.isAir()) {
            float hardness = state.getDestroySpeed(level, cell);
            if (hardness < 0 || hardness > HOLE_HARDEST || state.hasBlockEntity() || state.requiresCorrectToolForDrops() && !anyToolTakes(state)) return false;
        }
        BlockPos above = cell.above();
        if (!level.getBlockState(above).isAir()) return false;
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(above),
                entity -> entity != c && entity.isAlive()).isEmpty()) return false;
        // Nothing runs in from above, and the ground itself holds the lava in.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!level.getFluidState(above.relative(side)).isEmpty()) return false;
            BlockState wall = level.getBlockState(cell.relative(side));
            if (wall.canBeReplaced() || !wall.getFluidState().isEmpty()) return false;
        }
        BlockState floor = level.getBlockState(cell.below());
        return floor.isFaceSturdy(level, cell.below(), Direction.UP) && floor.getFluidState().isEmpty();
    }

    /** A tool of his that gives this block its drop. */
    private boolean anyToolTakes(BlockState state) {
        for (ItemStack stack : w.items.carriedItems()) if (!stack.isEmpty() && stack.isCorrectToolForDrops(state)) return true;
        return false;
    }

    /** The hole dug out of the ground beside him, what it dropped picked up: false while he walks to it or swings. */
    private boolean dig(ServerLevel level, BlockPos cell, int got) {
        BlockState state = level.getBlockState(cell);
        int reach = w.items.reachBlock(level, cell, true);
        // No way to it: the cell is passed over, so it is not dug at again every tick until the watchdog (another hole is looked for).
        if (reach < 0) { passed.add(cell); made = null; reset(); return false; }
        if (reach == 0) return false;
        c.takeToolForBlock(state);
        c.workStatus("Копаю ямку для лавы.");
        if (!w.breaking.tick(level, cell)) return false;
        ItemStack tool = c.getMainHandItem();
        var drops = CompanionTools.drops(state, level, cell, null, c, tool);
        if (!w.items.canStore(drops)) { c.putToolAway(); end(false, FULL_BAG + short_(got)); return false; }
        if (!level.destroyBlock(cell, false, c, 512)) { c.putToolAway(); made = null; return false; }
        CompanionTools.wearOnBreak(tool, level, state, cell, c);
        c.dropHarvest(cell, drops);
        c.putToolAway();
        event("obsidian_hole_dug", "hole", cell.toShortString(), "block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        return true;
    }

    /** A walk to the cell he pours into: false when there is no way to it (another hole is looked for, this one passed over). */
    private boolean walkTo(ServerLevel level, BlockPos cell) {
        if (stands.isEmpty()) { stands = standsBeside(level, cell); legs = 0; path = null; }
        if (walk()) return true;
        passed.add(cell); made = null; madeStep = MADE_DIG; reset();
        return false;
    }

    /**
     * The cells he pours the make from: beside the cell he pours into, never in it. The empty hole is a cell he can stand in, and a
     * walk that ends there would put the lava under his own feet.
     */
    private Set<BlockPos> standsBeside(ServerLevel level, BlockPos cell) {
        Set<BlockPos> stands = CompanionWaterFill.standsFor(c, level, List.of(cell));
        stands.remove(cell);
        return stands;
    }

    /** One bucket of this slot emptied, as the game's own: the empty one takes its place in his bag and the rest of the stack stays his (one that finds no cell lies at his feet, never lost). */
    private void emptyBucket(int slot) {
        c.removeItem(slot, 1);
        ItemStack empty = new ItemStack(Items.BUCKET);
        c.storeLoot(empty);
        if (!empty.isEmpty() && c.level() instanceof ServerLevel level) net.minecraft.world.level.block.Block.popResource(level, c.blockPosition(), empty);
    }

    /**
     * The lava of his own pour back into an empty bucket of his, by the game's own bucket (the one the pour left empty): false when the
     * cell holds no lava source any more, or he has no empty bucket to take it — then it stays, a source in a walled hole.
     */
    static boolean takeLavaBack(CompanionEntity c, ServerLevel level, BlockPos cell) {
        return level.getFluidState(cell).isSourceOfType(net.minecraft.world.level.material.Fluids.LAVA)
                && CompanionWaterBucket.scoopInto(c, level, cell);
    }

    /** His water back into the bucket it came from: true once it is (or gone, or given up as out of his way). */
    private boolean takeWaterBack(ServerLevel level) {
        if (water == null) return true;
        if (!level.getFluidState(water).isSourceOfType(net.minecraft.world.level.material.Fluids.WATER)) { water = null; reset(); return true; }
        if (inReach(level, water)) {
            if (CompanionWaterBucket.scoopInto(c, level, water)) event("obsidian_water_scooped", "water", water.toShortString());
            else event("obsidian_water_left", "water", water.toShortString(), "why", "no_bucket");
            water = null; reset();
            return true;
        }
        if (stands.isEmpty()) { stands = CompanionWaterFill.standsFor(c, level, List.of(water)); legs = 0; path = null; }
        c.workStatus("Забираю свою воду.");
        if (walk()) return false;
        event("obsidian_water_left", "water", water.toShortString(), "why", "no_way");
        c.autonomySay(WATER_LEFT, false);
        water = null; reset();
        return true;
    }

    /**
     * A pickaxe that takes obsidian in his hand: the fastest of his that does (a golden one is faster on it and drops nothing, so the
     * game's choice of the fastest tool took it and he said he had no diamond pickaxe).
     */
    private boolean pickaxe() {
        if (takesObsidian(c.getMainHandItem())) return true;
        if (takesObsidian(c.getOffhandItem())) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.getOffhandItem().getItem()).toString();
            return c.unequipForSupport("offhand").success() && c.equipForWork(id).success() && takesObsidian(c.getMainHandItem());
        }
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        ItemStack best = ItemStack.EMPTY;
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            ItemStack stack = c.getItem(slot);
            if (takesObsidian(stack) && (best.isEmpty() || stack.getDestroySpeed(obsidian) > best.getDestroySpeed(obsidian))) best = stack;
        }
        return !best.isEmpty() && c.equipForWork(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(best.getItem()).toString()).success()
                && takesObsidian(c.getMainHandItem());
    }

    /**
     * As a player's hand reaches a block: to its nearest face, not its middle (in his running water he stood 4.8 from the middle of
     * his own water, walked at it eight times against the stream and left it, GameTest 08.11); and in sight.
     */
    private boolean inReach(ServerLevel level, BlockPos cell) {
        Vec3 eyes = c.getEyePosition();
        return inReach(eyes, cell) && CompanionWaterFill.seen(c, level, eyes, cell, REACH + 1);
    }

    private static boolean inReach(Vec3 eyes, BlockPos cell) { return new net.minecraft.world.phys.AABB(cell).distanceToSqr(eyes) <= REACH * REACH; }

    /** One tick of the walk to the stand cells: false when there is no way (or no progress after {@link #LEGS} walks). */
    private boolean walk() {
        CompanionRoute route = w.route;
        if (path != null && !route.legOver()) return true;
        if (stands.isEmpty() || ++legs > LEGS) return false;
        CompanionRoute.Reach reach = route.cells(stands, 0);
        if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; return true; }
        if (!reach.found()) return false;
        path = reach.path();
        route.walk(path);
        return true;
    }

    /** The lava his water poured into this cell sets: the pond under it as far as the water runs, and as deep. */
    private static Set<BlockPos> lavaUnder(ServerLevel level, BlockPos aim) {
        Set<BlockPos> cells = new HashSet<>();
        ArrayDeque<BlockPos> next = new ArrayDeque<>(List.of(aim.below()));
        while (!next.isEmpty() && cells.size() < BODY) {
            BlockPos cell = next.poll();
            if (cells.contains(cell) || !level.hasChunkAt(cell) || !CompanionLava.at(level, cell)) continue;
            if (Math.abs(cell.getX() - aim.getX()) > SPREAD || Math.abs(cell.getZ() - aim.getZ()) > SPREAD || cell.getY() < aim.getY() - 1 - BELOW) continue;
            cells.add(cell.immutable());
            for (Direction side : Direction.values()) if (side != Direction.UP) next.add(cell.relative(side));
        }
        return cells;
    }

    /** The lava sources level with this one and joined to it, open to the air: passed over together when there is no way to them. */
    private static Set<BlockPos> pond(ServerLevel level, BlockPos lava) {
        Set<BlockPos> cells = new HashSet<>();
        ArrayDeque<BlockPos> next = new ArrayDeque<>(List.of(lava));
        while (!next.isEmpty() && cells.size() < BODY) {
            BlockPos cell = next.poll();
            if (cells.contains(cell) || !level.hasChunkAt(cell) || !level.getFluidState(cell).isSourceOfType(net.minecraft.world.level.material.Fluids.LAVA)) continue;
            cells.add(cell.immutable());
            for (Direction side : Direction.Plane.HORIZONTAL) next.add(cell.relative(side));
        }
        return cells;
    }

    /**
     * The nearest block of this kind within his search radius and {@code depth} above and below him that passes the test: the
     * sections nearest him first, those without it skipped by their palette (CompanionWaterFill looks for water the same way).
     */
    static BlockPos nearest(CompanionEntity c, ServerLevel level, int depth, Predicate<BlockState> kind, Predicate<BlockPos> ok) {
        BlockPos center = c.blockPosition();
        int radius = c.getSearchRadius();
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - depth), maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + depth);
        double far = (double) radius * radius;
        List<int[]> sections = new ArrayList<>();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++)
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++)
                for (int index = level.getSectionIndex(minY); index <= level.getSectionIndex(maxY); index++) sections.add(new int[]{cx, cz, index});
        sections.sort(java.util.Comparator.comparingDouble(s -> new BlockPos((s[0] << 4) + 8, (level.getSectionYFromSectionIndex(s[2]) << 4) + 8, (s[1] << 4) + 8).distSqr(center)));
        BlockPos best = null;
        for (int[] s : sections) {
            // A nearer one cannot lie in a section whose middle is farther by more than its half diagonal.
            if (best != null && Math.sqrt(new BlockPos((s[0] << 4) + 8, (level.getSectionYFromSectionIndex(s[2]) << 4) + 8, (s[1] << 4) + 8).distSqr(center)) - 14 > Math.sqrt(best.distSqr(center))) break;
            var chunk = level.getChunkSource().getChunkNow(s[0], s[1]);
            if (chunk == null) continue;
            var section = chunk.getSection(s[2]);
            if (section.hasOnlyAir() || !section.maybeHas(kind)) continue;
            int bottom = level.getSectionYFromSectionIndex(s[2]) << 4;
            for (int y = Math.max(minY, bottom); y <= Math.min(maxY, bottom + 15); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                if (!kind.test(section.getBlockState(x, y & 15, z))) continue;
                BlockPos cell = new BlockPos((s[0] << 4) + x, y, (s[1] << 4) + z);
                if (cell.distSqr(center) > far || best != null && cell.distSqr(center) >= best.distSqr(center) || !ok.test(cell)) continue;
                best = cell;
            }
        }
        return best;
    }

    private int slot(net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) return slot;
        return -1;
    }

    private void event(String name, Object... fields) {
        Object[] head = {"at", c.blockPosition().toShortString(), "got", got(), "wanted", wanted};
        Object[] all = java.util.Arrays.copyOf(head, head.length + fields.length);
        System.arraycopy(fields, 0, all, head.length, fields.length);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, all);
    }
}

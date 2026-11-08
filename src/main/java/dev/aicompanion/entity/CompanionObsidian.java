package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
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
 * the next lava and at the end. Obsidian already lying about is mined too, but never a portal's or one on a site where the player
 * builds; the obsidian his own water set is his wherever it lies. Under the obsidian there is often more lava: a block with his
 * water over it is mined, as the water runs into the hole and sets the lava under it before the drop falls; a dry one over lava
 * or beside it is left (its drop would burn).
 */
final class CompanionObsidian {
    static final String NO_PICKAXE = "Обсидиан берёт только алмазная кирка — дай мне её.";
    static final String NETHER = "В Незере обсидиан не сделать: вода здесь испаряется.";
    static final String END = "В Энде обсидиан не сделать: лавы здесь нет.";
    static final String NO_LAVA = "Не нашёл лавы рядом — отведи меня к ней.";
    static final String NO_WATER = "Нет ведра воды — нечем залить лаву.";
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
    /** How far he may be pushed off the place he mines from before he walks to the block again (blocks). */
    private static final double HOLD = .5;
    /** Ticks his water takes to run off once scooped (seven cells, five ticks each) and the drops to come to rest. */
    private static final int SETTLE = 60;
    /** How long the drops floating in his water are left alone at most (ticks), as long as his harvest is remembered. */
    private static final int SHUN = 20 * 300;

    private final CompanionWork w;
    private final CompanionEntity c;
    /** Obsidian wanted, in his bag at the order, blocks mined for it (picked up or not), pours and walks so far. */
    private int wanted, before, mined, pours, legs;
    private long nextLook, settleAt;
    private boolean retried;
    /** The obsidian he mines; the cell over lava he goes to pour into; his water standing; the cells to stand at for either. */
    private BlockPos target, aim, water;
    private Set<BlockPos> stands = Set.of();
    private Path path;
    /** Where he stood when the block came within his reach and sight. */
    private Vec3 foothold;
    /** The player's building sites here, taken once a tick (a block scan asks about many cells). */
    private java.util.function.Predicate<BlockPos> site = pos -> false;
    /** Cells of lava his water set or is setting: their obsidian is his even beside a portal. */
    private final Set<BlockPos> his = new HashSet<>();
    /** Obsidian and lava he found no way to. */
    private final Set<BlockPos> passed = new HashSet<>();

    CompanionObsidian(CompanionWork w) { this.w = w; this.c = w.owner; }

    /** Why no obsidian is to be had here with what he carries, or null: no pickaxe that takes it (he does not go for diamonds), the Nether, the End. */
    static String refusal(Level level, Iterable<ItemStack> carried) {
        if (level.dimensionType().ultraWarm()) return NETHER;
        if (level.dimension() == Level.END) return END;
        for (ItemStack stack : carried) if (takesObsidian(stack)) return null;
        return NO_PICKAXE;
    }

    static boolean takesObsidian(ItemStack stack) { return !stack.isEmpty() && stack.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()); }

    /** mine_obsidian: {@code count} more obsidian in his bag. Null when the work may start, else why not. */
    CompanionActionResult begin(int count) {
        String refusal = refusal(c.level(), w.items.carriedItems());
        if (refusal != null) return CompanionActionResult.failed(refusal);
        reset();
        wanted = count; before = w.items.countCarried(Items.OBSIDIAN); mined = pours = 0; water = null; nextLook = settleAt = 0; retried = false;
        his.clear(); passed.clear();
        return null;
    }

    /** Blocks mined for the order: progress for the watchdog while the drops wait in his water. */
    int mined() { return mined; }

    String jobName() { return "добычу обсидиана (" + got() + " из " + wanted + ")"; }

    private int got() { return Math.max(0, w.items.countCarried(Items.OBSIDIAN) - before); }

    /** One tick of the order: obsidian mined while there is some, else his water back, else on to the next lava and over it. */
    void tick(ServerLevel level) {
        site = c.getOwnerId() == null ? pos -> false : CompanionPlayerBuilds.get(level.getServer()).sites(c.getOwnerId(), level);
        int got = got();
        if (got >= wanted) { if (takeWaterBack(level)) end(true, "Накопал обсидиана: " + got + "."); return; }
        if (refusal(level, w.items.carriedItems()) != null) { end(false, NO_PICKAXE + short_(got)); return; }
        if (mined >= wanted) { collect(level, got); return; }
        if (target != null && !minable(level, target)) { target = null; w.breaking.reset(); w.resetApproach(); }
        if (target == null && level.getGameTime() >= nextLook) {
            target = nearest(level, Blocks.OBSIDIAN, pos -> minable(level, pos));
            if (target == null) nextLook = level.getGameTime() + LOOK;
        }
        if (target != null) { mine(level, got); return; }
        // Nothing more to mine here: his water back in the bucket before the next lava.
        if (water != null) { takeWaterBack(level); return; }
        pour(level, got);
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

    /** A failure ends obtain_item at once with what he says (BLOCKED): planned again, the same lava was looked for twice more. */
    private void end(boolean success, String message) { reset(); w.finish(success ? CompanionActionResult.ok(message) : CompanionActionResult.blocked(message)); }

    /** Stopped or given another order: his water is scooped if his hand reaches it, else it stays (stop means stop). */
    void cancel() {
        if (water != null && c.level() instanceof ServerLevel level && inReach(level, water)) CompanionWaterBucket.scoopInto(c, level, water);
        water = null;
        reset();
    }

    private void reset() { target = aim = null; stands = Set.of(); path = null; foothold = null; legs = 0; }

    /** Obsidian he may mine now: his own or lying about but no portal's nor on a site of the player's, and its drop safe from lava. */
    private boolean minable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(Blocks.OBSIDIAN) || passed.contains(pos) || pos.equals(c.blockPosition().below())) return false;
        // While his water is out, only what it sets: a walk to obsidian far off would leave the water behind him.
        if (water != null && !pos.closerThan(water, SPREAD + 1)) return false;
        if (site.test(pos) || !his.contains(pos) && portal(level, pos)) return false;
        if (wet(level, pos)) return true;
        for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
            if (CompanionLava.at(level, pos.relative(side))) return false;
        return true;
    }

    /** Water over it: it runs into the hole when the block is gone and sets the lava under it before the drop gets there. */
    private static boolean wet(ServerLevel level, BlockPos pos) { return level.getFluidState(pos.above()).is(FluidTags.WATER); }

    /** A Nether portal touches it, corners too: it is part of a portal's frame. */
    private static boolean portal(ServerLevel level, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) if (level.getBlockState(near).is(Blocks.NETHER_PORTAL)) return true;
        return false;
    }

    private void mine(ServerLevel level, int got) {
        BlockState state = level.getBlockState(target);
        // Once he stands where he sees it he mines from there: in his running water he sways a few hundredths of a block, and the
        // shore's edge took a block level with it in and out of his sight every other tick (GameTest 08.11).
        // The next block within his reach and sight where he stands is mined from there, as a player does: walked to anew, he went
        // against his running water for seconds (in game 08.11). Dry, the drop must also rest where he picks it up (the approach).
        if (foothold == null && wet(level, target) && inReach(c.getEyePosition(), target) && CompanionMiningNavigation.visible(c, c.getEyePosition(), target))
            foothold = c.position();
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
        tool.getItem().mineBlock(tool, level, state, target, c);
        c.dropHarvest(target, drops);
        mined++;
        // Floating in his running water the drop is no use chasing (in game 08.11 each was chased ten seconds in vain): it waits
        // until the water is back in the bucket (collect).
        if (water != null) c.loot.shun(level.getGameTime() + SHUN);
        event("obsidian_mined", "obsidian", target.toShortString(), "his", his.contains(target), "wet", wet(level, target));
        target = null; foothold = null;
        w.resetApproach();
    }

    /** To the nearest lava with air over it and his water poured there; the lava under the water is his from now on. */
    private void pour(ServerLevel level, int got) {
        if (aim == null) {
            BlockPos lava = pours >= POURS ? null
                    : nearest(level, Blocks.LAVA, pos -> !passed.contains(pos) && level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).isAir() && !site.test(pos));
            if (lava == null) {
                // What he mined and has not picked up yet is picked up before he says how far he got.
                if (mined > got) { collect(level, got); return; }
                end(false, pours == 0 && got == 0 ? NO_LAVA : "Накопал " + got + " из " + wanted + ": лава рядом кончилась.");
                return;
            }
            aim = lava.above(); stands = CompanionWaterFill.standsFor(c, level, List.of(aim)); legs = 0; path = null;
        }
        int slot = slot(Items.WATER_BUCKET);
        if (slot < 0) { end(false, NO_WATER + short_(got)); return; }
        if (!inReach(level, aim)) {
            c.workStatus("Иду к лаве.");
            // No cell to pour it from (the middle of a lake): only this lava is passed over; no way there: all of it.
            if (!walk()) { if (stands.isEmpty()) passed.add(aim.below()); else passed.addAll(pond(level, aim.below())); aim = null; }
            return;
        }
        if (!level.getBlockState(aim).isAir() || !CompanionLava.at(level, aim.below())) { aim = null; return; }
        ItemStack bucket = c.getItem(slot);
        c.getLookControl().setLookAt(Vec3.atCenterOf(aim));
        his.addAll(lavaUnder(level, aim));
        if (!((BucketItem) bucket.getItem()).emptyContents(null, level, aim, null, bucket)) { passed.addAll(pond(level, aim.below())); aim = null; return; }
        c.setItem(slot, new ItemStack(Items.BUCKET));
        c.animateAction(1, aim, bucket);
        water = aim; aim = null; pours++; nextLook = 0; reset();
        c.workStatus("Заливаю лаву водой.");
        event("obsidian_water_poured", "water", water.toShortString(), "pours", pours);
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
     * The nearest block of this kind within his search radius and {@link #DEPTH} above and below him that passes the test: the
     * sections nearest him first, those without it skipped by their palette (CompanionWaterFill looks for water the same way).
     */
    private BlockPos nearest(ServerLevel level, Block block, Predicate<BlockPos> ok) {
        BlockPos center = c.blockPosition();
        int radius = c.getSearchRadius();
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - DEPTH), maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + DEPTH);
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
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(block))) continue;
            int bottom = level.getSectionYFromSectionIndex(s[2]) << 4;
            for (int y = Math.max(minY, bottom); y <= Math.min(maxY, bottom + 15); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                if (!section.getBlockState(x, y & 15, z).is(block)) continue;
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

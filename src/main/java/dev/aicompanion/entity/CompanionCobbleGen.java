package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * build_cobble_gen: a cobblestone generator like a player makes one (ticket 01 of cobble-gen-build). On flat natural ground a few
 * blocks from the player he digs a channel one block deep and sets his buckets in it, in a row: lava, the empty cell the stone grows
 * in, an empty cell with a hole one deep under it, water. The water falls into the hole and the lava that runs into the empty cell
 * meets it and sets, as probe 8 measured. What lets fluid out is walled in with blocks from his bag. Both buckets are his from the
 * bag (the lava one is never made or taken from the world), and they stay in it, empty. Without a water bucket his empty one is
 * filled at water about first, or a block of his ice is set and broken (ticket 02). The next step is read from the world
 * every tick: stopped half way, nothing is built twice and no bucket is lost.
 */
final class CompanionCobbleGen {
    static final String NO_LAVA = "Нужно ведро лавы.";
    static final String NO_WATER = "Нужна вода: ведро воды или лёд.";
    static final String NO_WALL = "Нечем сделать бортики — нужны блоки (булыжник или земля).";
    static final String NO_PLACE = "Не нашёл ровного твёрдого места рядом для генератора.";
    static final String NO_WAY = "Не могу подойти к месту генератора.";
    static final String TAKEN = "Место генератора кто-то занял — построю заново по новой просьбе.";
    static final String FULL_BAG = "Сумка забита — некуда сложить выкопанное.";
    static final String NETHER = "В Незере генератор не сделать: вода здесь испаряется.";
    static final String NOT_POURED = "Не получилось вылить ведро — ведро осталось при мне.";
    static final String BLOCKED = "В канале кто-то стоит или лежит выкопанное — не могу лить.";
    static final String NO_STONE = "Лава и вода стоят, а булыжник не появился.";
    /** A player's reach for a block, as his bucket has it (CompanionObsidian). */
    private static final double REACH = 4.5;
    /** How far from the player the stone cell may lie (blocks), and how many levels of ground above and below the player's are looked at. */
    private static final int SITE = 8, BELOW = 2, ABOVE = 1;
    /** The hardest ground he digs (deepslate); walks to one place before it is passed over; ticks the dug blocks' drops are waited for and the stone is. */
    private static final float HARDEST = 3;
    private static final int LEGS = 8, PICK_TICKS = 200, GROW_TICKS = 200;
    /** How far lava sets fire, around and above (vanilla LavaFluid.randomTick); the shape is {@link #fireReaches}. */
    private static final int FIRE = 3;

    private static final net.minecraft.world.level.BlockGetter EMPTY = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;

    /** ICE: a block of ice set in the water's cell; MELT: that ice broken, which leaves a source as it does for a player. */
    private enum Kind { DIG, CLEAR, PICK, WALL, WATER, ICE, MELT, LAVA }
    private record Step(Kind kind, BlockPos cell) { }

    /**
     * The row: lava at {@code lava}, the stone grows at {@code lava + along}, the hole's cell at +2, water at +3, all on the ground's
     * top layer (dug out); the hole is one deeper under the third cell.
     */
    record Plan(BlockPos lava, Direction along) {
        BlockPos stone() { return lava.relative(along); }
        BlockPos pit() { return lava.relative(along, 2); }
        BlockPos hole() { return pit().below(); }
        BlockPos water() { return lava.relative(along, 3); }
        List<BlockPos> row() { return List.of(lava, stone(), pit(), water()); }
        /** In the order he digs: the row, then the hole (he stands over it, as a player digs down). */
        List<BlockPos> dig() { return List.of(lava, stone(), pit(), water(), hole()); }
        /** Cells whose column he does not stand in once the fluids are in. */
        boolean over(BlockPos at) { return row().stream().anyMatch(cell -> cell.getX() == at.getX() && cell.getZ() == at.getZ()); }
        AABB box() { return new AABB(lava).minmax(new AABB(water())).minmax(new AABB(hole())); }
        /** What holds the fluid in: beside the row, beyond its two ends, and beside the hole; the floors under the row are checked apart. */
        List<BlockPos> walls() {
            Set<BlockPos> cells = new java.util.LinkedHashSet<>();
            for (BlockPos cell : row()) for (Direction side : Direction.Plane.HORIZONTAL) cells.add(cell.relative(side));
            for (Direction side : Direction.Plane.HORIZONTAL) cells.add(hole().relative(side));
            cells.removeAll(row());
            cells.remove(hole());
            // Under the stone and the water cells and the lava: the ground itself (floors), not a wall to set.
            cells.removeAll(List.of(lava.below(), stone().below(), water().below()));
            return List.copyOf(cells);
        }
        List<BlockPos> floors() { return List.of(lava.below(), stone().below(), water().below(), hole().below()); }
    }

    private final CompanionWork w;
    private final CompanionEntity c;
    private Plan plan;
    /** The cell he walks to a place to reach, the cells he can reach it from, the walk's path and how many legs of it were walked. */
    private BlockPos aimed;
    private Set<BlockPos> stands = Set.of();
    private Path path;
    private int legs;
    private long pickingSince, grownFrom, blockedSince;
    private boolean retried;
    /** Why the last walk found no way, for the journal. */
    private String why;

    CompanionCobbleGen(CompanionWork w) { this.w = w; this.c = w.owner; }

    /** Null when the work may start, else why not: the buckets, the world, a place, blocks for the walls. {@code item} "ice": the water fetch gave up, no second one. */
    CompanionActionResult begin(String item) {
        reset();
        if (!(c.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Для работы нужен загруженный мир.");
        if (level.dimensionType().ultraWarm()) return CompanionActionResult.failed(NETHER);
        if (w.items.countCarried(Items.LAVA_BUCKET) < 1) return CompanionActionResult.failed(NO_LAVA);
        // Water (spec Г5): his bucket of water; else his empty bucket filled at living water about (the plan gets it first, as a
        // portal its obsidian); else ice of his bag, set and broken. An empty bucket with no water about is still sent for it: the
        // fill says honestly that there is none, unless he has ice.
        boolean fetch = false;
        if (w.items.countCarried(Items.WATER_BUCKET) < 1) {
            boolean ice = w.items.countCarried(Items.ICE) > 0;
            fetch = !"ice".equals(item) && w.items.countCarried(Items.BUCKET) > 0 && (!ice || waterAbout(level));
            if (!fetch && !ice) return CompanionActionResult.failed(NO_WATER);
        }
        ServerPlayer player = c.getOwnerPlayer();
        Predicate<BlockPos> site = c.getOwnerId() == null ? pos -> false : CompanionPlayerBuilds.get(level.getServer()).sites(c.getOwnerId(), level);
        plan = choose(level, CompanionPortalBuild.center(c), site, player == null || player.level() != level ? null : player.getBoundingBox());
        if (plan == null) return CompanionActionResult.failed(NO_PLACE);
        if (needed(level, plan) > blocks()) { plan = null; return CompanionActionResult.failed(NO_WALL); }
        if (fetch) {
            plan = null;
            return CompanionActionResult.shortage(NO_WATER).withEvidence(new CompanionActionResult.Evidence(0, "build_cobble_gen", "minecraft:water_bucket", 1, null, null, null));
        }
        event("cobble_gen_site", "lava", plan.lava().toShortString(), "along", plan.along(), "walls", needed(level, plan));
        return null;
    }

    String jobName() { return "генератор булыжника"; }

    void cancel() { reset(); }

    private void reset() { plan = null; aimed = null; stands = Set.of(); path = null; legs = 0; pickingSince = grownFrom = blockedSince = 0; retried = false; }

    /** One tick: the first thing the generator lacks, read from the world, done from where he reaches it. */
    void tick(ServerLevel level) {
        if (plan == null) { w.finish(false, NO_PLACE); return; }
        if (!level.hasChunkAt(plan.lava()) || !level.hasChunkAt(plan.water())) { w.finish(false, TAKEN); return; }
        if (level.getBlockState(plan.stone()).is(Blocks.COBBLESTONE) && lava(level) && water(level)) { done(level); return; }
        Step step = next(level);
        if (step == null) return;
        if (step.kind() == Kind.DIG || step.kind() == Kind.CLEAR) { pickingSince = 0; retried = false; }
        if (step.kind() == Kind.PICK) { pick(level, step.cell()); return; }
        // Pouring from a cell over the row would be standing over the lava: out of its columns, with the cell in his reach and sight.
        boolean pouring = step.kind() == Kind.WATER || step.kind() == Kind.ICE || step.kind() == Kind.MELT || step.kind() == Kind.LAVA;
        // A wall is not set in the cell he stands in: he steps out of it first.
        boolean inWall = step.kind() == Kind.WALL && c.getBoundingBox().intersects(new AABB(step.cell()));
        if (pouring && plan.over(c.blockPosition()) || inWall || !inReach(level, step.cell())) {
            c.workStatus("Иду строить генератор.");
            if (!step.cell().equals(aimed)) { aimed = step.cell(); stands = standsFor(level, step.cell(), pouring); legs = 0; path = null; }
            // Come to a place it is still not seen from (a block set since hides it): that place is no good any more.
            if (path != null && w.route.legOver() && stands.remove(c.blockPosition())) path = null;
            if (!walk()) { event("cobble_gen_no_way", "step", step, "stands", stands.size(), "legs", legs, "why", why); w.finish(false, NO_WAY); }
            return;
        }
        w.route.stop();
        if (pouring && !clear(level, step.kind())) return;
        blockedSince = 0;
        w.progressed();
        switch (step.kind()) {
            case DIG -> dig(level, step.cell(), "Копаю канал для генератора.");
            case CLEAR -> dig(level, step.cell(), "Срезаю траву у генератора.");
            case WALL -> wall(level, step.cell());
            case WATER -> pour(level, Items.WATER_BUCKET, plan.water());
            case ICE -> {
                ItemStack ice = ItemStack.EMPTY;
                for (ItemStack stack : w.items.carriedItems()) if (stack.is(Items.ICE)) ice = stack;
                if (ice.isEmpty()) { w.finish(false, NO_WATER); return; }
                place(level, step.cell(), ice, NO_WATER);
            }
            case MELT -> melt(level, step.cell());
            case LAVA -> pour(level, Items.LAVA_BUCKET, plan.lava());
            case PICK -> { }
        }
    }

    /**
     * The row dug (nothing is dug once a fluid stands in it), what it dropped picked up, the walls set, the water in, the lava in; then
     * the stone is waited for. Null when the order ended.
     */
    private Step next(ServerLevel level) {
        boolean poured = !level.getFluidState(plan.lava()).isEmpty() || !level.getFluidState(plan.water()).isEmpty();
        if (!poured) {
            for (BlockPos cell : plan.dig()) {
                BlockState state = level.getBlockState(cell);
                if (!level.getFluidState(cell).isEmpty()) { w.finish(false, TAKEN); return null; }
                // His ice set for the water stays to be broken (MELT), not dug out as ground.
                if (state.isAir() || cell.equals(plan.water()) && state.is(Blocks.ICE)) continue;
                if (!diggable(state, level, cell)) { w.finish(false, TAKEN); return null; }
                return new Step(Kind.DIG, cell);
            }
            if (!lying(level).isEmpty() && (pickingSince == 0 || level.getGameTime() - pickingSince <= PICK_TICKS)) return new Step(Kind.PICK, plan.stone());
            for (BlockPos wall : plan.walls()) if (!level.getBlockState(wall).isCollisionShapeFullBlock(level, wall)) {
                if (!CompanionPortalBuild.free(level, wall)) { w.finish(false, TAKEN); return null; }
                return new Step(Kind.WALL, wall);
            }
        }
        if (!water(level)) {
            if (level.getBlockState(plan.water()).is(Blocks.ICE)) return new Step(Kind.MELT, plan.water());
            return new Step(w.items.countCarried(Items.WATER_BUCKET) > 0 ? Kind.WATER : Kind.ICE, plan.water());
        }
        if (!lava(level)) {
            // Wild plants within the lava's fire range are cut, as a player clears the grass before he sets lava.
            for (BlockPos cell : flammable(level, plan.lava())) {
                if (!level.getFluidState(cell).isEmpty()) continue;
                return new Step(Kind.CLEAR, cell);
            }
            // What lies where the lava goes would burn: picked up first (a short while, then the work ends).
            if (!lyingIn(level, plan.lava(), plan.stone()).isEmpty()) {
                if (pickingSince == 0) pickingSince = level.getGameTime();
                if (level.getGameTime() - pickingSince > PICK_TICKS) { w.finish(false, BLOCKED); return null; }
                return new Step(Kind.PICK, plan.stone());
            }
            // What holds the lava in, again: a wall or a floor broken since the water went in (a creeper, the player) would let it out.
            for (BlockPos wall : plan.walls()) {
                if (wall.distManhattan(plan.lava()) != 1 && wall.distManhattan(plan.stone()) != 1 || level.getBlockState(wall).isCollisionShapeFullBlock(level, wall)) continue;
                if (!CompanionPortalBuild.free(level, wall)) { w.finish(false, TAKEN); return null; }
                return new Step(Kind.WALL, wall);
            }
            for (BlockPos floor : List.of(plan.lava().below(), plan.stone().below()))
                if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) { w.finish(false, TAKEN); return null; }
            return new Step(Kind.LAVA, plan.lava());
        }
        // Both stand: the lava runs into the stone's cell, meets the water that fell into the hole next to it, and sets.
        if (grownFrom == 0) grownFrom = level.getGameTime();
        if (level.getGameTime() - grownFrom > GROW_TICKS) { event("cobble_gen_no_stone", "stone", level.getBlockState(plan.stone())); w.finish(false, NO_STONE); return null; }
        c.workStatus("Жду, пока образуется булыжник.");
        return null;
    }

    /** Living water his bucket may be filled at, as an order to fill it looks for it (CompanionWaterFill). */
    private boolean waterAbout(ServerLevel level) {
        return CompanionWaterFill.refusal(level) == null && !CompanionWaterFill.living(level, c.blockPosition(), c.getSearchRadius(), 32, Set.of()).body().isEmpty();
    }

    private boolean water(ServerLevel level) { return level.getFluidState(plan.water()).isSourceOfType(Fluids.WATER); }

    private boolean lava(ServerLevel level) { return level.getFluidState(plan.lava()).isSourceOfType(Fluids.LAVA); }

    private void done(ServerLevel level) {
        BlockPos stone = plan.stone();
        event("cobble_gen_done", "stone", stone.toShortString());
        reset();
        w.finish(true, "Готово, генератор у " + stone.getX() + ", " + stone.getY() + ", " + stone.getZ() + ".");
    }

    /** What he dug lies in the row or in its hole: picked up before the fluids burn it (the pickup runs between his ticks). */
    private List<ItemEntity> lying(ServerLevel level) { return level.getEntitiesOfClass(ItemEntity.class, plan.box().inflate(1), ItemEntity::isAlive); }

    private List<ItemEntity> lyingIn(ServerLevel level, BlockPos... cells) {
        List<ItemEntity> items = new java.util.ArrayList<>();
        for (BlockPos cell : cells) items.addAll(level.getEntitiesOfClass(ItemEntity.class, new AABB(cell), ItemEntity::isAlive));
        return items;
    }

    /**
     * No one stands where the fluid goes (lava: its cell and the stone's; water: the pit, its own cell and the hole): a player or a mob
     * in the channel would be burnt or drowned. Waits a while, then the work ends.
     */
    private boolean clear(ServerLevel level, Kind kind) {
        BlockPos[] cells = kind == Kind.LAVA ? new BlockPos[]{plan.lava(), plan.stone()} : new BlockPos[]{plan.pit(), plan.water(), plan.hole()};
        boolean occupied = false;
        for (BlockPos cell : cells) occupied |= !level.getEntitiesOfClass(LivingEntity.class, new AABB(cell), LivingEntity::isAlive).isEmpty();
        if (!occupied) return true;
        if (blockedSince == 0) blockedSince = level.getGameTime();
        if (level.getGameTime() - blockedSince > PICK_TICKS * 3) { w.finish(false, BLOCKED); return false; }
        c.workStatus("Жду, пока освободится канал.");
        return false;
    }

    private void pick(ServerLevel level, BlockPos cell) {
        if (pickingSince == 0) pickingSince = level.getGameTime();
        c.workStatus("Подбираю выкопанное.");
        if (lying(level).stream().anyMatch(item -> !item.onGround())) return;
        if (!retried) { retried = true; c.retryHarvestPickup(); }
    }

    /** A block of the row or the hole, broken as he breaks blocks, what it drops picked up. */
    private void dig(ServerLevel level, BlockPos cell, String status) {
        BlockState state = level.getBlockState(cell);
        // Taken once: a tool put in his hand again every tick would start the swing over.
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && !c.getMainHandItem().is(ItemTags.SHOVELS)) CompanionTools.equip(c, ItemTags.SHOVELS, state);
        else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !c.getMainHandItem().is(ItemTags.PICKAXES)) CompanionTools.equip(c, ItemTags.PICKAXES, state);
        c.workStatus(status);
        if (!w.breaking.tick(level, cell)) return;
        ItemStack tool = c.getMainHandItem();
        var drops = CompanionTools.drops(state, level, cell, null, c, tool);
        if (!w.items.canStore(drops)) { w.finish(false, FULL_BAG); return; }
        if (!level.destroyBlock(cell, false, c, 512)) { w.finish(false, TAKEN); return; }
        tool.getItem().mineBlock(tool, level, state, cell, c);
        c.dropHarvest(cell, drops);
        event("cobble_gen_dug", "cell", cell.toShortString(), "block", BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /** A block of his own set where fluid would run out, as a player sets one. */
    private void wall(ServerLevel level, BlockPos cell) {
        ItemStack best = ItemStack.EMPTY;
        // Earth and cobblestone first (what he digs out), then the softest: not glass or a lamp before them.
        for (ItemStack stack : w.items.carriedItems()) if (wallBlock(stack) && (best.isEmpty() || plain(stack) && !plain(best) || plain(stack) == plain(best) && hardness(stack) < hardness(best))) best = stack;
        if (best.isEmpty()) { w.finish(false, NO_WALL); return; }
        place(level, cell, best, NO_WALL);
    }

    /** A block of this stack of his set in the cell from his hand. */
    private void place(ServerLevel level, BlockPos cell, ItemStack stack, String none) {
        // Not over a block set there since (the player's): only into a free cell.
        if (!CompanionPortalBuild.free(level, cell)) { w.finish(false, TAKEN); return; }
        BlockState state = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
        if (!level.isUnobstructed(state, cell, CollisionContext.empty())) { c.workStatus("Жду, пока освободится место для блока."); return; }
        Item item = stack.getItem();
        if (!c.getMainHandItem().is(item) && !c.equipForWork(BuiltInRegistries.ITEM.getKey(item).toString()).success()) { w.finish(false, none); return; }
        ItemStack held = c.getMainHandItem();
        if (!held.is(item) || !level.setBlock(cell, state, 3)) { w.finish(false, TAKEN); return; }
        c.animateAction(1, cell, held);
        held.shrink(1);
        c.setChanged();
        c.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, cell, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, .65F, 1F);
        level.gameEvent(c, GameEvent.BLOCK_PLACE, cell);
        w.cooldown = 4;
    }

    /**
     * His ice in the water's cell broken as a player breaks it: over the firm floor it leaves a water source, and nothing drops
     * (vanilla IceBlock.playerDestroy). A tool that keeps ice whole (silk touch) is put away first, as it would not melt.
     */
    private void melt(ServerLevel level, BlockPos cell) {
        ItemStack held = c.getMainHandItem();
        if (net.minecraft.world.item.enchantment.EnchantmentHelper.hasTag(held, net.minecraft.tags.EnchantmentTags.PREVENTS_ICE_MELTING)
                && !c.unequipForSupport("mainhand").success()) { w.finish(false, NO_WATER); return; }
        c.workStatus("Разбиваю лёд — будет вода.");
        if (!w.breaking.tick(level, cell)) return;
        if (!level.destroyBlock(cell, false, c, 512) || !level.setBlockAndUpdate(cell, Blocks.WATER.defaultBlockState())) { w.finish(false, TAKEN); return; }
        held = c.getMainHandItem();
        if (!held.isEmpty()) held.getItem().mineBlock(held, level, Blocks.ICE.defaultBlockState(), cell, c);
        w.cooldown = 8;
        event("cobble_gen_melted", "cell", cell.toShortString());
    }

    /** His bucket emptied into the cell as a player's is; the empty one stays in the bag (or the hand) the full one came from. */
    private void pour(ServerLevel level, Item full, BlockPos cell) {
        ItemStack bucket = ItemStack.EMPTY;
        int slot = -1;
        EquipmentSlot hand = null;
        for (int i = 0; i < c.getContainerSize() && bucket.isEmpty(); i++) if (c.getItem(i).is(full)) { bucket = c.getItem(i); slot = i; }
        for (EquipmentSlot at : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (bucket.isEmpty() && c.getItemBySlot(at).is(full)) { bucket = c.getItemBySlot(at); hand = at; }
        if (bucket.isEmpty()) { w.finish(false, full == Items.LAVA_BUCKET ? NO_LAVA : NO_WATER); return; }
        c.getLookControl().setLookAt(Vec3.atCenterOf(cell));
        if (!((BucketItem) bucket.getItem()).emptyContents(null, level, cell, null, bucket)) { w.finish(false, NOT_POURED); return; }
        if (hand != null) c.setItemSlot(hand, new ItemStack(Items.BUCKET)); else c.setItem(slot, new ItemStack(Items.BUCKET));
        c.animateAction(1, cell, bucket);
        c.swing(InteractionHand.MAIN_HAND);
        c.setChanged();
        w.cooldown = 8;
        c.workStatus(full == Items.LAVA_BUCKET ? "Лью лаву." : "Лью воду.");
        event("cobble_gen_poured", "cell", cell.toShortString(), "bucket", BuiltInRegistries.ITEM.getKey(full));
    }

    // ---- the place ----

    /**
     * The nearest row that fits, the one needing fewest walls first: all on natural ground he can dig, free and dry above, the floors
     * under firm and dry, what must be walled free to set a block in, and no cliff beside it (a wall's cell with nothing under it).
     * Not where the player stands, nor on his buildings.
     */
    private Plan choose(ServerLevel level, BlockPos center, Predicate<BlockPos> site, AABB player) {
        Plan best = null;
        int bestWalls = 0;
        double bestFar = 0;
        for (int dy = -BELOW; dy <= ABOVE; dy++) for (int dx = -SITE; dx <= SITE; dx++) for (int dz = -SITE; dz <= SITE; dz++) {
            BlockPos lava = center.offset(dx, dy - 1, dz);
            for (Direction along : Direction.Plane.HORIZONTAL) {
                Plan p = new Plan(lava, along);
                double far = Vec3.atCenterOf(p.stone()).distanceToSqr(Vec3.atBottomCenterOf(center));
                if (far > SITE * SITE) continue;
                // Down to the hole too: the row lies in the ground he stands on (game 15.11: the lava cell was the block under him).
                if (player != null && p.box().intersects(player.inflate(1.5, 0, 1.5).expandTowards(0, -3, 0))) continue;
                int walls = fits(level, p, site);
                if (walls < 0) continue;
                if (best == null || walls < bestWalls || walls == bestWalls && far < bestFar) { best = p; bestWalls = walls; bestFar = far; }
            }
        }
        return best;
    }

    /** How many walls the row needs, or -1 when it does not fit. */
    private int fits(ServerLevel level, Plan p, Predicate<BlockPos> site) {
        for (BlockPos cell : p.dig()) {
            if (!level.hasChunkAt(cell)) return -1;
            BlockState state = level.getBlockState(cell);
            if (site.test(cell) || !CompanionPortalBuild.natural(state, false) || !diggable(state, level, cell)) return -1;
        }
        // Only the lava's own cell sets fire: what runs into the stone's cell sets into cobblestone at once (beside the water), never ticks as lava.
        if (ignites(level, site, p.lava())) return -1;
        for (BlockPos cell : p.row()) if (!(CompanionPortalBuild.free(level, cell.above()) || clearable(level.getBlockState(cell.above()))) || !level.getFluidState(cell.above()).isEmpty()) return -1;
        for (BlockPos floor : p.floors()) {
            if (!level.hasChunkAt(floor)) return -1;
            BlockState state = level.getBlockState(floor);
            if (!state.isFaceSturdy(level, floor, Direction.UP) || !state.getFluidState().isEmpty() || site.test(floor) || !holds(state)) return -1;
        }
        int walls = 0;
        for (BlockPos wall : p.walls()) {
            if (!level.hasChunkAt(wall)) return -1;
            BlockState state = level.getBlockState(wall);
            if (!state.getFluidState().isEmpty()) return -1;
            if (state.isCollisionShapeFullBlock(level, wall)) {
                if (site.test(wall) || !holds(state)) return -1;
                continue;
            }
            // A gap in the ground's edge: walled if something firm lies under it, else it is a cliff or a hole.
            if (!CompanionPortalBuild.free(level, wall) || site.test(wall) || !level.getBlockState(wall.below()).isFaceSturdy(level, wall.below(), Direction.UP)) return -1;
            walls++;
        }
        return walls;
    }

    /**
     * Lava's random tick sets fire in air up to three above it and its neighbours that touches something flammable, and on a flammable
     * block beside it (vanilla LavaFluid.randomTick: fire up to two steps up and out, then a flammable neighbour of that cell): anything
     * flammable within three blocks around and three above is too near. Wild plants
     * there are cut before the lava is poured ({@link #clearable}); anything else flammable, or a plant of the player's, rules the place out.
     */
    private static boolean ignites(ServerLevel level, Predicate<BlockPos> site, BlockPos lava) {
        for (BlockPos at : BlockPos.betweenClosed(lava.offset(-FIRE, 0, -FIRE), lava.offset(FIRE, FIRE, FIRE))) {
            if (!fireReaches(lava, at)) continue;
            if (!level.hasChunkAt(at)) return true;
            BlockState state = level.getBlockState(at);
            if (state.ignitedByLava() && !(clearable(state) && !site.test(at))) return true;
        }
        return false;
    }

    /**
     * A block lava may set fire to: fire is lit up to two steps of (±1, +1, ±1) above it, then on the flammable neighbours of that
     * cell. So two blocks around up to one above, three around only at two above, two around at three above (a tree's lowest leaves
     * three above and three aside are safe: game 16.11, an island with an oak was ruled out by a plain box).
     */
    private static boolean fireReaches(BlockPos lava, BlockPos at) {
        int dy = at.getY() - lava.getY(), far = Math.max(Math.abs(at.getX() - lava.getX()), Math.abs(at.getZ() - lava.getZ()));
        return dy >= 0 && dy <= FIRE && far <= (dy == 2 ? FIRE : FIRE - 1);
    }

    /** A wild plant he cuts with one blow: flammable or a flower (a poppy does not burn, but stands where the lava goes), nothing to stand on or bump into, breaks at once (grass, ferns, flowers, dead bush); not a sapling or a crop. */
    private static boolean clearable(BlockState state) {
        return (state.ignitedByLava() || state.is(BlockTags.FLOWERS)) && state.getCollisionShape(EMPTY, BlockPos.ZERO).isEmpty() && state.getDestroySpeed(EMPTY, BlockPos.ZERO) == 0
                && !state.hasBlockEntity() && state.getFluidState().isEmpty() && !state.is(BlockTags.SAPLINGS) && !state.is(BlockTags.CROPS);
    }

    /** The plants within the fire range of the given lava cells that are left (what he cuts). */
    private static List<BlockPos> flammable(ServerLevel level, BlockPos... lava) {
        Set<BlockPos> cells = new java.util.LinkedHashSet<>();
        for (BlockPos cell : lava)
            for (BlockPos at : BlockPos.betweenClosed(cell.offset(-FIRE, 0, -FIRE), cell.offset(FIRE, FIRE, FIRE))) if (fireReaches(cell, at) && clearable(level.getBlockState(at))) cells.add(at.immutable());
        return List.copyOf(cells);
    }

    /** A block that stays whole next to lava and water: it does not burn, fall or melt (ice, packed ice, frosted ice). */
    private static boolean holds(BlockState state) {
        return !(state.getBlock() instanceof FallingBlock) && !state.ignitedByLava() && !state.is(BlockTags.ICE) && !state.is(BlockTags.LEAVES);
    }

    private int needed(ServerLevel level, Plan p) {
        int walls = 0;
        for (BlockPos wall : p.walls()) if (!level.getBlockState(wall).isCollisionShapeFullBlock(level, wall)) walls++;
        return walls;
    }

    /** What he carries that would do for a wall: all of it, counted. */
    private int blocks() {
        int count = 0;
        for (ItemStack stack : w.items.carriedItems()) if (wallBlock(stack)) count += stack.getCount();
        return count;
    }

    /** A plain solid block that neither falls, burns nor melts and has no inside: cobblestone, dirt, stone and the like (not sand, planks, ice, ores, a chest). */
    private static boolean wallBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        BlockState state = item.getBlock().defaultBlockState();
        // Not magma: it burns whoever walks on the wall, level with the ground.
        return holds(state) && !state.hasBlockEntity() && !state.is(Blocks.MAGMA_BLOCK) && !state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)
                && !state.is(net.neoforged.neoforge.common.Tags.Blocks.STORAGE_BLOCKS) && state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(EMPTY, BlockPos.ZERO) && state.getDestroySpeed(EMPTY, BlockPos.ZERO) >= 0;
    }

    private static boolean plain(ItemStack stack) { return CompanionTools.soilItem(stack) || stack.is(Items.COBBLESTONE); }

    private static float hardness(ItemStack stack) { return ((BlockItem) stack.getItem()).getBlock().defaultDestroyTime(); }

    /** A plain block he breaks in a few seconds with what he carries: no chest or furnace, no fluid. */
    private boolean diggable(BlockState state, ServerLevel level, BlockPos cell) {
        float hardness = state.getDestroySpeed(level, cell);
        if (hardness < 0 || hardness > HARDEST || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        if (!state.requiresCorrectToolForDrops()) return true;
        for (ItemStack stack : w.items.carriedItems()) if (stack.isCorrectToolForDrops(state)) return true;
        return false;
    }

    // ---- the walk ----

    /** Cells he can reach this one from; to pour, not over the row (he would stand over the lava). */
    private Set<BlockPos> standsFor(ServerLevel level, BlockPos cell, boolean outside) {
        Set<BlockPos> cells = new HashSet<>(CompanionWaterFill.standsFor(c, level, List.of(cell)));
        // Nor in a wall's cell: he would stand where his own block is to go (review: he waited there 90 s for himself to move).
        Set<BlockPos> walls = Set.copyOf(plan.walls());
        cells.removeIf(stand -> outside && plan.over(stand) || stand.equals(cell) || walls.contains(stand));
        return cells;
    }

    /** As a player's hand reaches a block: to its nearest face, and in sight (CompanionObsidian). */
    private boolean inReach(ServerLevel level, BlockPos cell) {
        Vec3 eyes = c.getEyePosition();
        return new AABB(cell).distanceToSqr(eyes) <= REACH * REACH && CompanionWaterFill.seen(c, level, eyes, cell, REACH + 1);
    }

    /** One tick of the walk to the stand cells: false when there is no way (or no progress after {@link #LEGS} walks). */
    private boolean walk() {
        CompanionRoute route = w.route;
        if (path != null && !route.legOver()) return true;
        if (stands.isEmpty() || ++legs > LEGS) { why = stands.isEmpty() ? "no stand" : "legs"; return false; }
        CompanionRoute.Reach reach = route.cells(stands, 0);
        why = reach.reason();
        if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; return true; }
        if (!reach.found()) return false;
        path = reach.path();
        route.walk(path);
        return true;
    }

    private void event(String name, Object... fields) {
        Object[] head = {"at", c.blockPosition().toShortString()};
        Object[] all = java.util.Arrays.copyOf(head, head.length + fields.length);
        System.arraycopy(fields, 0, all, head.length, fields.length);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, all);
    }
}

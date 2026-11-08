package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * «Построй портал» (decided 08.11): a Nether portal's frame without its corners, ten obsidian, on the nearest level free place within
 * {@link #SITE} blocks of the player, its two bottom blocks set into the ground in place of what was there (he picks that up), so the
 * way in has no step; lit at once with his flint and steel, which wears as a player's does. A lit portal within {@link #NEAR} blocks of
 * the player is his way to the Nether already: none is built. Short of obsidian or of a flint and steel the order is refused at once
 * as a shortage, and the plan gets them first (CompanionPlanRunner). What stands of the frame is read from the world, so an order
 * stopped half way goes on with the same frame. The top of a frame without corners hangs in the air: as a player does, he props it
 * with a block of his under it and takes the prop away after. He never steps into the portal himself.
 */
final class CompanionPortalBuild {
    static final String THERE = "Рядом уже есть портал.";
    static final String DONE = "Портал готов.";
    static final String END = "В Энде портал не зажечь.";
    static final String NO_PLACE = "Не нашёл ровного свободного места под портал рядом с тобой.";
    static final String NO_WAY = "Не могу подойти туда, где строю портал.";
    static final String TAKEN = "Место под портал заняли — дострою по новой просьбе.";
    static final String NO_FLINT = "Нечем зажечь портал — нужно огниво.";
    static final String NO_PROP = "Нечем подпереть верх рамки — дай мне любой блок.";
    static final String NOT_LIT = "Рамка не зажглась.";
    /** A frame without its corners. */
    static final int OBSIDIAN = 10;
    /** A lit portal this near the player is his way to the Nether already; the frame goes up this near him (blocks). */
    private static final int NEAR = 16, SITE = 10;
    /** Ground levels looked at below and above the ground under the player. */
    private static final int BELOW = 3, ABOVE = 3;
    /** The hardest ground he digs out for the bottom of the frame (deepslate); harder is no place for it. */
    private static final float HARDEST = 3;
    /** No chest this near the frame: the portal would stand in the way to it (blocks). */
    private static final int CHESTS = 2;
    /** A player's reach for a block; walks to the place before he gives it up; ticks a block he set takes; how long he waits for someone in the way. */
    private static final double REACH = 4.5;
    private static final int LEGS = 8, PLACE_TICKS = 4, WAIT_TICKS = 100;

    private enum Kind { DIG, PROP, OBSIDIAN, CLEAR, LIGHT }
    private record Step(Kind kind, BlockPos cell) { }

    /** The frame: its column 0 stands on {@code base}, it runs {@code along}, the way in is on the {@code front} side. */
    record Frame(BlockPos base, Direction along, Direction front) {
        BlockPos cell(int i, int j) { return base.relative(along, i).above(j); }
        /** In the order he sets them: the bottom in the ground, the columns from below, the top. */
        List<BlockPos> obsidian() {
            return List.of(cell(1, 0), cell(2, 0), cell(0, 1), cell(0, 2), cell(0, 3), cell(3, 1), cell(3, 2), cell(3, 3), cell(1, 4), cell(2, 4));
        }
        List<BlockPos> inside() { return List.of(cell(1, 1), cell(2, 1), cell(1, 2), cell(2, 2), cell(1, 3), cell(2, 3)); }
        BlockPos fire() { return cell(1, 1); }
        AABB box() { return new AABB(cell(0, 0)).minmax(new AABB(cell(3, 4))); }
    }

    private final CompanionWork w;
    private final CompanionEntity c;
    private Frame frame;
    private Set<BlockPos> stands = Set.of();
    private Path path;
    private int legs;
    private long waitingSince;

    CompanionPortalBuild(CompanionWork w) { this.w = w; this.c = w.owner; }

    /** Where the portal is for: the player when he is in this world, else where he himself stands. */
    static BlockPos center(CompanionEntity c) {
        ServerPlayer player = c.getOwnerPlayer();
        return player != null && player.level() == c.level() ? player.blockPosition() : c.blockPosition();
    }

    /** A lit Nether portal within {@link #NEAR} blocks of the cell, whoever lit it. */
    static boolean litNear(ServerLevel level, BlockPos center) {
        return level.getPoiManager().findClosest(type -> type.is(PoiTypes.NETHER_PORTAL),
                pos -> level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.NETHER_PORTAL), center, NEAR, PoiManager.Occupancy.ANY).isPresent();
    }

    /**
     * build_portal: null when the work may start, else the answer: a portal there already (a success), the End, no place, or a
     * shortage of obsidian or of a flint and steel, its evidence naming what to get and how many he must carry.
     */
    CompanionActionResult begin() {
        if (!(c.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Для работы нужен загруженный мир.");
        reset();
        if (level.dimension() == Level.END) return CompanionActionResult.failed(END);
        BlockPos center = center(c);
        if (litNear(level, center)) return CompanionActionResult.ok(THERE);
        ServerPlayer player = c.getOwnerPlayer();
        Predicate<BlockPos> site = c.getOwnerId() == null ? pos -> false : CompanionPlayerBuilds.get(level.getServer()).sites(c.getOwnerId(), level);
        long started = System.nanoTime();
        List<BlockPos> chests = w.chests.blockEntitiesNear(level, center, SITE + 4 + CHESTS).stream().filter(pos -> level.getBlockEntity(pos) instanceof net.minecraft.world.Container).toList();
        frame = choose(level, center, site, chests, player == null || player.level() != level ? null : player.getBoundingBox());
        if (frame == null) return CompanionActionResult.failed(NO_PLACE);
        int need = (int) frame.obsidian().stream().filter(cell -> !level.getBlockState(cell).is(Blocks.OBSIDIAN)).count();
        int have = w.items.countCarried(Items.OBSIDIAN);
        if (have < need) return shortage("Для портала нужно " + need + " обсидиана, у меня " + have + ".", "minecraft:obsidian", need);
        if (!lighter()) return shortage(NO_FLINT, "minecraft:flint_and_steel", 1);
        stands = standsFor(level, frame);
        if (stands.isEmpty()) return CompanionActionResult.failed(NO_WAY);
        event("portal_site", "frame", frame.base().toShortString(), "along", frame.along(), "front", frame.front(), "need", need, "searchMs", (System.nanoTime() - started) / 1_000_000);
        return null;
    }

    private static CompanionActionResult shortage(String message, String item, int count) {
        return CompanionActionResult.shortage(message).withEvidence(new CompanionActionResult.Evidence(0, "build_portal", item, count, null, null, null));
    }

    String jobName() {
        if (frame == null || !(c.level() instanceof ServerLevel level)) return "постройку портала";
        return "постройку портала (" + frame.obsidian().stream().filter(cell -> level.getBlockState(cell).is(Blocks.OBSIDIAN)).count() + " из " + OBSIDIAN + ")";
    }

    void cancel() { reset(); }

    private void reset() { frame = null; stands = Set.of(); path = null; legs = 0; waitingSince = 0; }

    /** One tick: the next thing the frame lacks, from where he can reach it; lit once whole. */
    void tick(ServerLevel level) {
        if (frame == null) { w.finish(false, NO_PLACE); return; }
        if (level.getBlockState(frame.fire()).is(Blocks.NETHER_PORTAL)) { done(level); return; }
        Step step = next(level);
        if (step == null) return;
        // Out of the frame, with the cell in his reach and sight: from inside it the fire would take him to the Nether.
        Vec3 eyes = c.getEyePosition();
        if (!c.onGround() || frame.box().intersects(c.getBoundingBox()) || new AABB(step.cell()).distanceToSqr(eyes) > REACH * REACH || !visible(level, eyes, step.cell())) {
            c.workStatus("Иду строить портал.");
            // Come to a place it is still not seen from (a column set since hides it): that place is no good any more.
            if (path != null && w.route.legOver() && stands.remove(c.blockPosition())) path = null;
            if (!walk()) { event("portal_no_way", "step", step); w.finish(false, NO_WAY); }
            return;
        }
        w.route.stop();
        switch (step.kind()) {
            case DIG, CLEAR -> dig(level, step.cell());
            case PROP -> prop(level, step.cell());
            case OBSIDIAN -> set(level, step.cell(), Items.OBSIDIAN);
            case LIGHT -> light(level);
        }
    }

    /**
     * What the frame lacks first, read from the world: the ground where its bottom goes, then the obsidian in order (a prop under a
     * top block that has nothing to rest on), then whatever is inside it (the prop too), then the fire. Null when he cannot go on.
     */
    private Step next(ServerLevel level) {
        for (BlockPos ground : List.of(frame.cell(1, 0), frame.cell(2, 0)))
            if (!level.getBlockState(ground).isAir() && !level.getBlockState(ground).is(Blocks.OBSIDIAN)) return new Step(Kind.DIG, ground);
        for (BlockPos cell : frame.obsidian()) {
            if (level.getBlockState(cell).is(Blocks.OBSIDIAN)) continue;
            if (!free(level, cell)) { w.finish(false, TAKEN); return null; }
            if (supported(level, cell)) return new Step(Kind.OBSIDIAN, cell);
            BlockPos under = cell.below();
            if (frame.inside().contains(under) && level.getBlockState(under).isAir()) return new Step(Kind.PROP, under);
            w.finish(false, TAKEN);
            return null;
        }
        for (BlockPos cell : frame.inside()) {
            BlockState state = level.getBlockState(cell);
            if (state.isAir()) continue;
            if (!free(level, cell) && !diggable(state, level, cell)) { w.finish(false, TAKEN); return null; }
            return new Step(Kind.CLEAR, cell);
        }
        return new Step(Kind.LIGHT, frame.fire());
    }

    /** The ground under the bottom, a prop or a plant inside the frame: broken as he breaks blocks, what it drops picked up. */
    private void dig(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        // Taken once: a tool put in his hand again every tick would start the swing over.
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && !c.getMainHandItem().is(ItemTags.SHOVELS)) CompanionTools.equip(c, ItemTags.SHOVELS, state);
        else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !c.getMainHandItem().is(ItemTags.PICKAXES)) CompanionTools.equip(c, ItemTags.PICKAXES, state);
        c.workStatus("Расчищаю место под портал.");
        if (!w.breaking.tick(level, cell)) return;
        ItemStack tool = c.getMainHandItem();
        var drops = CompanionTools.drops(state, level, cell, level.getBlockEntity(cell), c, tool);
        if (!level.destroyBlock(cell, false, c, 512)) { w.finish(false, TAKEN); return; }
        tool.getItem().mineBlock(tool, level, state, cell, c);
        c.dropHarvest(cell, drops);
        event("portal_dug", "cell", cell.toShortString(), "block", BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /** A block of his own under a top block, so it has a face to be set against; taken away with the rest of what is inside. */
    private void prop(ServerLevel level, BlockPos cell) {
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack stack : w.items.carriedItems()) if (propping(stack, level, cell) && (best.isEmpty() || hardness(stack) < hardness(best))) best = stack;
        if (best.isEmpty()) { w.finish(false, NO_PROP); return; }
        set(level, cell, best.getItem());
    }

    /** A whole block that stays where it is set and comes away with what he carries: no obsidian, nothing that falls or holds things. */
    private boolean propping(ItemStack stack, ServerLevel level, BlockPos cell) {
        if (!(stack.getItem() instanceof BlockItem item) || stack.is(Items.OBSIDIAN) || item.getBlock() instanceof FallingBlock || item.getBlock() instanceof EntityBlock) return false;
        BlockState state = item.getBlock().defaultBlockState();
        return state.isCollisionShapeFullBlock(level, cell) && diggable(state, level, cell);
    }

    private static float hardness(ItemStack stack) { return ((BlockItem) stack.getItem()).getBlock().defaultDestroyTime(); }

    /** The block in his hand set in the cell as a player sets it: against a face, with no one in it, one from the stack. */
    private void set(ServerLevel level, BlockPos cell, net.minecraft.world.item.Item item) {
        BlockState state = ((BlockItem) item).getBlock().defaultBlockState();
        if (!level.isUnobstructed(state, cell, CollisionContext.empty())) {
            if (waitingSince == 0) waitingSince = level.getGameTime();
            if (level.getGameTime() - waitingSince > WAIT_TICKS) { w.finish(false, TAKEN); return; }
            c.workStatus("Жду, пока отойдут от рамки портала.");
            return;
        }
        waitingSince = 0;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (!c.getMainHandItem().is(item) && !c.equipForWork(id).success()) { w.finish(false, "Не могу взять в руку «" + CompanionWork.itemName(item) + "»."); return; }
        ItemStack held = c.getMainHandItem();
        if (!held.is(item) || !level.setBlock(cell, state, 3)) { w.finish(false, TAKEN); return; }
        c.animateAction(1, cell, held);
        held.shrink(1);
        c.setChanged();
        c.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, cell, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, .65F, 1F);
        level.gameEvent(c, GameEvent.BLOCK_PLACE, cell);
        w.cooldown = PLACE_TICKS;
        c.workStatus("Строю портал: " + frame.obsidian().stream().filter(at -> level.getBlockState(at).is(Blocks.OBSIDIAN)).count() + " из " + OBSIDIAN + ".");
    }

    /** His flint and steel on the bottom of the frame, as a player strikes it: the fire there lights the frame, the steel wears by one. */
    private void light(ServerLevel level) {
        if (!lighter() || !c.getMainHandItem().is(Items.FLINT_AND_STEEL) && !c.equipForWork("minecraft:flint_and_steel").success()
                || !CompanionFacts.usable(c.getMainHandItem())) { w.finish(false, NO_FLINT); return; }
        ItemStack steel = c.getMainHandItem();
        BlockPos fire = frame.fire();
        if (!BaseFireBlock.canBePlacedAt(level, fire, frame.front().getOpposite())) { w.finish(false, NOT_LIT); return; }
        level.playSound(null, fire, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1F, level.getRandom().nextFloat() * .4F + .8F);
        level.setBlock(fire, BaseFireBlock.getState(level, fire), 11);
        level.gameEvent(c, GameEvent.BLOCK_PLACE, fire.below());
        c.animateAction(1, fire, steel);
        c.swing(InteractionHand.MAIN_HAND);
        steel.hurtAndBreak(1, c, EquipmentSlot.MAINHAND);
        if (!level.getBlockState(fire).is(Blocks.NETHER_PORTAL)) { event("portal_not_lit", "fire", fire.toShortString()); w.finish(false, NOT_LIT); return; }
        done(level);
    }

    private void done(ServerLevel level) {
        c.rememberPlaced(frame.fire());
        event("portal_lit", "fire", frame.fire().toShortString());
        reset();
        w.finish(true, DONE);
    }

    /** He carries a flint and steel he can still strike, in the bag or a hand. */
    private boolean lighter() {
        for (ItemStack stack : w.items.carriedItems()) if (stack.is(Items.FLINT_AND_STEEL) && CompanionFacts.usable(stack)) return true;
        return false;
    }

    /**
     * The nearest frame that fits, the one most of whose obsidian already stands first (his own, stopped half way): both ways round,
     * the way in on either side, its ground a few levels above or below the player's.
     */
    private Frame choose(ServerLevel level, BlockPos center, Predicate<BlockPos> site, List<BlockPos> chests, AABB player) {
        Frame best = null;
        int bestPlaced = -1;
        double bestFar = 0;
        for (int dy = -BELOW; dy <= ABOVE; dy++) for (int dx = -SITE; dx <= SITE; dx++) for (int dz = -SITE; dz <= SITE; dz++) {
            BlockPos base = center.offset(dx, dy - 1, dz);
            for (Direction along : new Direction[]{Direction.EAST, Direction.SOUTH}) for (Direction front : new Direction[]{along.getClockWise(), along.getCounterClockWise()}) {
                Frame f = new Frame(base, along, front);
                double far = Vec3.atCenterOf(f.cell(1, 2)).add(Vec3.atLowerCornerOf(along.getNormal()).scale(.5)).distanceToSqr(Vec3.atBottomCenterOf(center));
                if (far > SITE * SITE) continue;
                int placed = fits(level, f, site, chests, player);
                if (placed > bestPlaced || placed >= 0 && placed == bestPlaced && far < bestFar) { best = f; bestPlaced = placed; bestFar = far; }
            }
        }
        return best;
    }

    /**
     * How many of the frame's obsidian stand already, or -1 when it does not fit: its columns on firm ground, the ground for its bottom
     * his to dig, the rest free and dry (a prop of his may still stand inside), a level way in on its front, no building of the
     * player's, no chest near, not where the player stands (null: he is not in this world).
     */
    private int fits(ServerLevel level, Frame f, Predicate<BlockPos> site, List<BlockPos> chests, AABB player) {
        if (player != null && f.box().intersects(player)) return -1;
        AABB near = f.box().inflate(CHESTS);
        if (chests.stream().anyMatch(chest -> near.contains(Vec3.atCenterOf(chest)))) return -1;
        int placed = 0;
        for (int i = 0; i < 4; i++) {
            BlockPos ground = f.cell(i, 0);
            if (!level.hasChunkAt(ground) || site.test(ground)) return -1;
            BlockState state = level.getBlockState(ground);
            boolean column = i == 0 || i == 3;
            if (column) { if (!state.isFaceSturdy(level, ground, Direction.UP) || !state.getFluidState().isEmpty()) return -1; }
            else if (state.is(Blocks.OBSIDIAN)) placed++;
            else if (!free(level, ground) && !diggable(state, level, ground)) return -1;
            if (!column) {
                BlockPos before = ground.relative(f.front());
                if (!level.getBlockState(before).isFaceSturdy(level, before, Direction.UP) || !free(level, before.above()) || !free(level, before.above(2)) || site.test(before)) return -1;
            }
            for (int j = 1; j <= 4; j++) {
                if (column && j == 4) continue;
                BlockPos cell = f.cell(i, j);
                if (site.test(cell)) return -1;
                BlockState here = level.getBlockState(cell);
                if (!column && j <= 3) { if (!free(level, cell) && !(j == 3 && diggable(here, level, cell) && !here.is(Blocks.OBSIDIAN))) return -1; }
                else if (here.is(Blocks.OBSIDIAN)) placed++;
                else if (!free(level, cell)) return -1;
            }
        }
        return placed;
    }

    /** Air or a plant a block is set over, without water or lava. */
    private static boolean free(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** A plain block he breaks in a few seconds and gets back with what he carries: no chest or furnace, no fluid. */
    private boolean diggable(BlockState state, ServerLevel level, BlockPos cell) {
        float hardness = state.getDestroySpeed(level, cell);
        if (hardness < 0 || hardness > HARDEST || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
        if (!state.requiresCorrectToolForDrops()) return true;
        for (ItemStack stack : w.items.carriedItems()) if (stack.isCorrectToolForDrops(state)) return true;
        return false;
    }

    /** A face of a block beside it to set a block against, as the house builder asks. */
    private static boolean supported(ServerLevel level, BlockPos cell) {
        for (Direction side : Direction.values()) if (!level.getBlockState(cell.relative(side)).getCollisionShape(level, cell.relative(side)).isEmpty()) return true;
        return false;
    }

    /** Cells before the frame from which his hand reaches all of it, out of the frame. */
    private Set<BlockPos> standsFor(ServerLevel level, Frame f) {
        List<BlockPos> all = new ArrayList<>(f.obsidian());
        all.addAll(f.inside());
        Set<BlockPos> cells = new HashSet<>();
        for (int i = -1; i <= 4; i++) for (int d = 1; d <= 3; d++) for (int dy = 0; dy <= 2; dy++) {
            BlockPos stand = f.cell(i, dy).relative(f.front(), d).immutable();
            if (!CompanionMiningNavigation.standable(c, stand)) continue;
            Vec3 eyes = Vec3.atBottomCenterOf(stand).add(0, c.getEyeHeight(), 0);
            if (all.stream().allMatch(cell -> new AABB(cell).distanceToSqr(eyes) <= REACH * REACH)) cells.add(stand);
        }
        return cells;
    }

    /** One tick of the walk to the place before the frame: false when there is no way (or no progress after {@link #LEGS} walks). */
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

    /**
     * As a player aims at a block: its nearest point or the middle of a face turned to him, with nothing between. The ray to the nearest
     * edge grazes what is beside it: the ground of the way in for the ground he digs, the prop under a top block of the frame.
     */
    private boolean visible(ServerLevel level, Vec3 eyes, BlockPos cell) {
        List<Vec3> aims = new ArrayList<>(List.of(new Vec3(Math.clamp(eyes.x, cell.getX() + .01, cell.getX() + .99),
                Math.clamp(eyes.y, cell.getY() + .01, cell.getY() + .99), Math.clamp(eyes.z, cell.getZ() + .01, cell.getZ() + .99))));
        for (Direction side : Direction.values()) {
            Vec3 face = Vec3.atCenterOf(cell).add(Vec3.atLowerCornerOf(side.getNormal()).scale(.49));
            if (eyes.subtract(face).dot(Vec3.atLowerCornerOf(side.getNormal())) > 0) aims.add(face);
        }
        for (Vec3 aim : aims) {
            HitResult hit = level.clip(new ClipContext(eyes, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
            if (hit.getType() == HitResult.Type.MISS || hit instanceof BlockHitResult block && block.getBlockPos().equals(cell)) return true;
        }
        return false;
    }

    private void event(String name, Object... fields) {
        Object[] head = {"at", c.blockPosition().toShortString()};
        Object[] all = java.util.Arrays.copyOf(head, head.length + fields.length);
        System.arraycopy(fields, 0, all, head.length, fields.length);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, all);
    }
}

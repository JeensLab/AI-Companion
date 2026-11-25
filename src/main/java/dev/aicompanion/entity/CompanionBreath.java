package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Air like a player's: with his eyes in water and less than a third of his air left, he drops what he is doing (the work is only
 * paused), swims to the nearest cell where his head is out of the water, waits there for a full breath and goes back to it.
 * A flooded tunnel with a ceiling does not let him float up on his own (FloatGoal), and nothing else counts his air.
 * <p>
 * The way is his own plain search over the cells he fits in, not the route search: that one starts from the top of the water
 * column, and under a ceiling a two-high flooded tunnel has no cell to start from. With no way at all he breaks the block over the top
 * of his water, as a player in a pocket would ({@link #airCut}).
 */
final class CompanionBreath {
    /** How far around him air is looked for, in blocks across and above/below, and how many cells the look may visit. */
    private static final int RADIUS = 12, DOWN = 2, UP = 10, VISITED = 12000;
    /** The way is looked for again this often (ticks), or at once when he is off it. */
    private static final int AGAIN = 10;
    /** The block over his water is broken only up to deepslate's hardness, and from no farther than a player's reach of it. */
    private static final float CUT_HARDNESS = 3;
    private static final double CUT_REACH = 4.5;
    /** A cut not broken in this many ticks (out of reach after all, a block that does not give) is given up for the rest of the breath. */
    private static final int CUT_PATIENCE = 200;
    private final CompanionEntity c;
    private final CompanionBlockBreaking breaking;
    /** The block over the top of his water that he breaks for air; null while there is a way to air or no such block. */
    private BlockPos cut;
    /** When the cut was chosen, and whether one has been given up in this breath. */
    private long cutSince;
    private boolean cutGivenUp;
    private boolean active;
    private int unreachable;
    private long nextSearch;
    /** The cells to swim through, his own first; empty with no way. */
    private List<BlockPos> way = List.of();
    private BlockPos nearest;

    CompanionBreath(CompanionEntity c) { this.c = c; breaking = new CompanionBlockBreaking(c); }

    private boolean eyesInWater() { return c.isEyeInFluid(FluidTags.WATER); }

    // No order cancels this: «стоп» or a new order stops the work, and he still swims up for his breath like a player would.
    boolean canUse() { return c.isAlive() && (active || eyesInWater() && c.getAirSupply() < c.getMaxAirSupply() / 3); }
    boolean canContinue() { return active && c.isAlive() && (eyesInWater() || c.getAirSupply() < c.getMaxAirSupply()); }

    void begin() {
        active = true; unreachable = 0; nextSearch = 0; way = List.of(); nearest = null; cut = null; cutGivenUp = false;
        c.getNavigation().stop();
        if (c.hasActiveWorkOrder()) c.workStatus("Выныриваю подышать, потом вернусь к делу.");
    }
    void end() { active = false; way = List.of(); nearest = null; dropCut(); c.getNavigation().stop(); }

    /** Going up for air and breathing: no dive holds him under meanwhile (CompanionSwimming, CompanionMobDrops.watch). */
    boolean active() { return active; }

    void tick() {
        // In the air: the stroke is over, the rest is waiting for the lungs to fill.
        if (!eyesInWater()) { c.getNavigation().stop(); return; }
        c.getNavigation().stop();
        long now = c.level().getGameTime();
        int at = way.indexOf(c.blockPosition());
        if (now >= nextSearch || !way.isEmpty() && at < 0) { search(now); at = way.indexOf(c.blockPosition()); }
        if (way.size() > at + 1 && at >= 0) {
            BlockPos next = way.get(at + 1);
            c.getMoveControl().setWantedPosition(next.getX() + .5, next.getY(), next.getZ() + .5, 1.0);
            if (next.getY() > c.getBlockY()) c.getJumpControl().jump();
        } else if (way.isEmpty() && cut != null) cutAir();
        // No way to any air and nothing over him to break: still towards the nearest through the walls, not standing still.
        else if (way.isEmpty() && nearest != null) c.getMoveControl().setWantedPosition(nearest.getX() + .5, nearest.getY(), nearest.getZ() + .5, 1.0);
    }

    private void search(long now) {
        nextSearch = now + AGAIN;
        way = wayToAir();
        if (!way.isEmpty()) { unreachable = 0; dropCut(); return; }
        nearest = nearestAir();
        // The cut once chosen is kept while it holds (a drift of his does not move it and throw away the cracks), until its patience runs out.
        if (cut != null && (now - cutSince > CUT_PATIENCE || !cuttable(cut))) { dropCut(); cutGivenUp = true; }
        if (cut == null && !cutGivenUp && (cut = airCut()) != null) cutSince = now;
        if (cut != null) { unreachable = 0; return; }
        // No way for three looks: the work stops and says why, and he keeps swimming to the air he sees.
        if (++unreachable == 3 && c.work.isActive()) {
            c.work.finish(false, "Не могу выбраться к воздуху: путь наверх закрыт, а воздух на исходе. Остановил работу.");
        }
    }

    /**
     * The block over the top of the water he is in, when breaking it lets his head out (runs m20261031 void-4 and m7 descent: a pocket of
     * water with a ceiling, no way to air, drowned where they stood); null when there is none. Only what {@link #cuttable} allows.
     */
    private BlockPos airCut() {
        var level = c.level();
        BlockPos top = c.blockPosition().above();
        // Plain water blocks only: a waterlogged slab, fence or trapdoor is no cell he swims up through.
        if (!level.getBlockState(top).is(Blocks.WATER)) return null;
        while (level.getBlockState(top.above()).is(Blocks.WATER) && top.getY() < c.getBlockY() + UP) top = top.above();
        BlockPos block = top.above();
        return cuttable(block) && breakTicks(block) <= CUT_PATIENCE ? block : null;
    }

    /** How long the blow takes with the tool he would take for it (a block of deepslate by hand is not worth the air). */
    private int breakTicks(BlockPos block) {
        var level = c.level();
        BlockState state = level.getBlockState(block);
        c.takeToolForBlock(state);
        ItemStack hand = c.getMainHandItem();
        float speed = Math.max(1, CompanionTools.breakSpeed(c, hand, state, block));
        float divisor = !state.requiresCorrectToolForDrops() || hand.isCorrectToolForDrops(state) ? 30 : 100;
        c.putToolAway();
        return Math.max(1, (int) Math.ceil(state.getDestroySpeed(level, block) * divisor / speed));
    }

    /**
     * A block he may break for air, as the way out of a pit is cut (CompanionRecovery): soil, rock or leaves no harder than deepslate,
     * not one that falls, not a plug of his mine or a cell it forbids, nothing anyone stands on, no chest or bench beside it, and no water or
     * lava over it or beside it to fill the cell. A player's planks, glass or ladder stay.
     */
    private boolean cuttable(BlockPos block) {
        var level = c.level();
        BlockState state = level.getBlockState(block);
        float hardness = state.getDestroySpeed(level, block);
        if (!(CompanionQuarry.soil(state) || CompanionRecovery.rock(state) || state.is(net.minecraft.tags.BlockTags.LEAVES))
                || hardness < 0 || hardness > CUT_HARDNESS || state.getBlock() instanceof FallingBlock) return false;
        if (c.mineSite().seals().contains(block) || c.mineSite().forbidden(block) || CompanionMiningNavigation.supportsLivingEntity(c, block)) return false;
        // A player's own: leaves he set (persistent), his buildings and the stone he placed.
        if (state.getBlock() instanceof LeavesBlock && state.getValue(LeavesBlock.PERSISTENT)) return false;
        if (level instanceof ServerLevel server && c.getOwnerId() != null
                && (CompanionPicking.sites(c, server).test(block) || CompanionPlayerBuilds.get(server.getServer()).placedStone(c.getOwnerId(), server).test(block))) return false;
        for (Direction side : Direction.values()) {
            BlockPos near = block.relative(side);
            if (side != Direction.DOWN) {
                // Something that rests on it or hangs from it (torch, door, rail, carpet, sign) would pop off.
                BlockState other = level.getBlockState(near);
                if (!other.isAir() && !other.canBeReplaced() && !other.isCollisionShapeFullBlock(level, near)) return false;
            }
            var fluid = level.getFluidState(near);
            if (fluid.is(FluidTags.LAVA) || side != Direction.DOWN && fluid.is(FluidTags.WATER) || level.getBlockEntity(near) != null
                    || level.getBlockState(near).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
                    || side == Direction.UP && level.getBlockState(near).getBlock() instanceof FallingBlock) return false;
        }
        return true;
    }

    private void dropCut() {
        if (cut != null) { breaking.reset(); c.putToolAway(); }
        cut = null;
    }

    /** Up the water to within reach of the block over it, then the swing with the tool for it, and the block broken and dropped like any harvest. */
    private void cutAir() {
        if (!(c.level() instanceof ServerLevel level)) return;
        if (c.getEyePosition().distanceTo(cut.getCenter()) > CUT_REACH) {
            c.getMoveControl().setWantedPosition(cut.getX() + .5, cut.getY() - 1, cut.getZ() + .5, 1.0);
            c.getJumpControl().jump();
            return;
        }
        if (!cuttable(cut)) { dropCut(); cutGivenUp = true; return; }
        BlockState state = level.getBlockState(cut);
        c.takeToolForBlock(state);
        if (!breaking.tick(level, cut)) return;
        // A player may have stepped on it since the last look.
        if (!cuttable(cut)) { dropCut(); cutGivenUp = true; return; }
        ItemStack tool = c.getMainHandItem();
        List<ItemStack> drops = CompanionTools.drops(state, level, cut, level.getBlockEntity(cut), c, tool);
        if (level.destroyBlock(cut, false, c, 512)) {
            CompanionTools.wearOnBreak(tool, level, state, cut, c);
            c.dropHarvest(cut, drops);
        }
        dropCut();
        nextSearch = 0;
    }

    /** From his cell to the nearest cell (by steps) where his head is out of the water, through the water and air he fits in; empty if none. */
    private List<BlockPos> wayToAir() {
        BlockPos at = c.blockPosition();
        Map<BlockPos, BlockPos> from = new HashMap<>();
        from.put(at, at);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(at));
        while (!queue.isEmpty() && from.size() < VISITED) {
            BlockPos p = queue.poll();
            if (headOut(p)) {
                List<BlockPos> way = new ArrayList<>();
                for (BlockPos step = p; !step.equals(at); step = from.get(step)) way.add(step);
                way.add(at);
                Collections.reverse(way);
                return way;
            }
            for (Direction d : Direction.values()) {
                BlockPos next = p.relative(d);
                if (Math.abs(next.getX() - at.getX()) > RADIUS || Math.abs(next.getZ() - at.getZ()) > RADIUS || next.getY() < at.getY() - DOWN || next.getY() > at.getY() + UP
                        || !c.level().hasChunkAt(next) || !fits(next) || from.containsKey(next)) continue;
                from.put(next.immutable(), p);
                queue.add(next.immutable());
            }
        }
        return List.of();
    }

    /** The nearest cell, walls or not, where his head would be out of the water; null if none within the look. */
    private BlockPos nearestAir() {
        BlockPos at = c.blockPosition();
        BlockPos best = null;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-RADIUS, -DOWN, -RADIUS), at.offset(RADIUS, UP, RADIUS)))
            if (c.level().hasChunkAt(p) && headOut(p) && (best == null || p.distSqr(at) < best.distSqr(at))) best = p.immutable();
        return best;
    }

    /** He fits in a cell if it and the cell over it are open (no block, no lava). */
    private boolean fits(BlockPos p) { return passable(p) && c.level().hasChunkAt(p.above()) && passable(p.above()); }
    private boolean passable(BlockPos p) { return c.level().getBlockState(p).getCollisionShape(c.level(), p).isEmpty() && !c.level().getFluidState(p).is(FluidTags.LAVA); }

    /** A cell he fits in with his eyes over the water: they are 1.62 above his feet, in the cell over them. */
    private boolean headOut(BlockPos p) {
        if (!fits(p)) return false;
        BlockPos eyes = p.above();
        var fluid = c.level().getFluidState(eyes);
        return !fluid.is(FluidTags.WATER) || p.getY() + 1.62 >= eyes.getY() + fluid.getHeight(c.level(), eyes);
    }
}

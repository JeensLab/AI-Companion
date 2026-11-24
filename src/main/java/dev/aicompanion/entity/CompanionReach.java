package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The one answer to «how does he get to this block and work it», asked by every place that chooses a target. The answer is a
 * way, not a yes: WALK a cell he can stand and work it from, ACROSS a walk farther than his own search (legs, CompanionTravel),
 * UP a column of his own soil with the steps it takes, BRIDGE a floor across a gap with the cells it takes, DOWN the block under
 * his feet (its drop rests in the hole he steps into), or NONE.
 *
 * The answer is geometry only — how far up, how far across, whether a stand cell exists, whether there is a gap — never what the
 * block is: a pumpkin, an obsidian block and a lapis block in the air are the same question, and so is every block of the game
 * (idea PATH7, #105: «для любой цели в мире, а не для отдельных предметов»). The material comes in order: what he carries, then
 * what he can dig about him. A way that cannot be made names its distance, never the first thing that comes to mind.
 */
public final class CompanionReach {
    public enum Way { WALK, ACROSS, UP, BRIDGE, DOWN, NONE }

    /** The way to the block and what it asks: for UP the least steps of soil, for BRIDGE the cells of floor, else why there is none. */
    public record How(Way way, int steps, int gap, String why) {
        public How(Way way, int steps) { this(way, steps, 0, ""); }
        public boolean yes() { return way != Way.NONE; }
        public boolean up() { return way == Way.UP; }
        public boolean bridge() { return way == Way.BRIDGE; }
        /** The shape of the way, as the decision line names it. */
        public String shape() {
            return switch (way) {
                case WALK, ACROSS -> "walk";
                case UP -> "column";
                case BRIDGE -> "bridge";
                case DOWN -> "step down";
                case NONE -> "none";
            };
        }
    }

    /** What the world about him really gives of an item: how many it yields by these ways, the blocks a way still asks, and
     * whether the promise counts on water of his (obsidian cooled from lava or made of his own bucket). */
    public record Available(int count, int soil, boolean water) {
        public Available(int count, int soil) { this(count, soil, false); }
        public boolean some() { return count > 0; }
        /** The two answers together: the blocks themselves and the drops of other blocks. */
        public static Available sum(Available a, Available b) {
            if (a == null) return b;
            if (b == null) return a;
            return new Available(a.count() + b.count(), Math.max(a.soil(), b.soil()), a.water() || b.water());
        }
    }

    /** A floor across a gap: the bank he walks to, the cell he works the block from, and the cells of floor to lay between. */
    public record BridgePlan(BlockPos start, BlockPos end, List<BlockPos> cells) {
        public int length() { return cells.size(); }
    }

    private CompanionReach() { }

    /** The last decision line said for a companion: a way asked again every tick is one decision, not sixty lines. */
    private static final java.util.Map<UUID, String> SAID = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<UUID, String>(64, .75F, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<UUID, String> eldest) { return size() > 64; }
            });

    /**
     * How he gets to this block and works it. {@code reach}: how close a stand cell must be; {@code forTheDrop}: the drop must
     * rest where he can pick it up from that cell; {@code allowDugSoil}: blocks he can dig about him count for a column or a
     * bridge; {@code maxSteps}: the most steps a column may take (a leaf crown keeps its short one).
     */
    public static How how(CompanionEntity c, ServerLevel level, BlockPos block, double reach, boolean forTheDrop, boolean allowDugSoil, int maxSteps) {
        if (!level.hasChunkAt(block) || level.getBlockState(block).isAir()) return new How(Way.NONE, 0, 0, "no block");
        BlockPos feet = c.blockPosition();
        // The block under his feet is worked from where he stands: its drop rests in the hole and he steps down into it.
        if (block.equals(feet.below()) && CompanionMiningNavigation.visible(c, c.getEyePosition(), block)
                && (!forTheDrop || CompanionMiningNavigation.dropCollectable(level, block, feet))) return new How(Way.DOWN, 0);
        // On the bed of the water he works it from the water over it, as the swimmer stands at the surface (CompanionSwimming):
        // the dry stand cells of standCells are no use there, and a block below him is no way up.
        boolean wet = !level.getFluidState(block).isEmpty() || !level.getFluidState(block.above()).isEmpty();
        Set<BlockPos> stands = new HashSet<>();
        if (wet) {
            for (int dy = 1; dy <= 11; dy++) {
                BlockPos cell = block.above(dy);
                if (!level.getFluidState(cell).is(FluidTags.WATER)) break;
                if (CompanionMiningNavigation.pickupStand(c, cell)) { stands.add(cell); break; }
            }
        } else {
            // A block with a body is worked from a cell beside it, not from on top of it; a flower, a cane or a vine has no body
            // for the eye to rest on: it is worked from a cell the ray reaches it from (the picking's own rule, CompanionPicking).
            boolean solid = !level.getBlockState(block).getCollisionShape(level, block).isEmpty();
            if (solid) {
                stands.addAll(CompanionMiningNavigation.standCells(c, block, reach, false, true));
                if (forTheDrop) stands.removeIf(stand -> !CompanionMiningNavigation.dropCollectable(level, block, stand));
            } else {
                for (int dy = -2; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    BlockPos cell = block.offset(dx, dy, dz);
                    if (!CompanionMiningNavigation.standable(c, cell) || level.getBlockState(cell).is(net.minecraft.world.level.block.Blocks.COBWEB)) continue;
                    Vec3 eye = Vec3.atBottomCenterOf(cell).add(0, c.getEyeHeight(), 0);
                    if (level.clip(new ClipContext(eye, block.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c)).getType()
                            == HitResult.Type.MISS) stands.add(cell);
                }
            }
        }
        if (!stands.isEmpty()) {
            // A block farther than his own search is a walk in legs, not a lost one (CompanionTravel).
            return new How(block.distSqr(feet) > (double) c.getSearchRadius() * c.getSearchRadius() ? Way.ACROSS : Way.WALK, 0);
        }
        // No stand cell reaches it: a column of his own soil, or a floor laid across the gap between him and it.
        How up = up(c, level, block, allowDugSoil, maxSteps);
        if (up.yes()) return up;
        BridgePlan bridge = bridge(c, level, block, reach, forTheDrop, maxSteps, allowDugSoil);
        if (bridge != null && bridge.length() > 0) return new How(Way.BRIDGE, 0, bridge.length(), "");
        // No way: the distance is the honest reason, never the first thing that comes to mind.
        int rise = block.getY() - feet.getY();
        double across = Math.hypot(block.getX() + .5D - c.getX(), block.getZ() + .5D - c.getZ());
        return new How(Way.NONE, 0, 0, "up " + rise + ", across " + Math.round(across * 10) / 10D + (up.why().isEmpty() ? "" : ", " + up.why()));
    }

    /**
     * A column of his own soil to a block no stand cell reaches, or why there is none. Geometry only: the column stands at its
     * own foot, which he walks to — the block's distance from where he stands now is no reason to refuse it (a pumpkin eight to
     * the side is reached from the foot under it, as a player builds up; the pillar's own walk budget bounds it).
     */
    private static How up(CompanionEntity c, ServerLevel level, BlockPos block, boolean allowDugSoil, int maxSteps) {
        // The way up is the player's switch («Строить себе путь»): off, no column is raised.
        if (!c.buildWay()) return new How(Way.NONE, 0, 0, "switch off");
        int rise = block.getY() - c.blockPosition().getY();
        if (rise < 2 || rise > CompanionPillar.WAY_HEIGHT) return new How(Way.NONE, 0, 0, "rise " + rise);
        int limit = Math.clamp(maxSteps, 1, CompanionPillar.WAY_SUPPORTS);
        int carried = c.temporaryPillar.soilCarried();
        int least = c.temporaryPillar.leastStepsNear(block, limit);
        if (least <= 0) return new How(Way.NONE, 0, 0, "no column he can walk to");
        if (least <= carried) return new How(Way.UP, least);
        if (allowDugSoil && soilAbout(c, level)) return new How(Way.UP, least);
        return new How(Way.NONE, 0, 0, "short " + (least - carried) + " blocks");
    }

    /**
     * A floor across the gap between a bank he can walk to and a cell he can work this block from: the cells of air under the
     * walking line, and the bank behind them. Geometry only: how far across, whether the cells are free, whether the material
     * is his. Null when no line is there.
     */
    public static BridgePlan bridge(CompanionEntity c, ServerLevel level, BlockPos block, double reach, boolean forTheDrop, int maxGap, boolean allowDugSoil) {
        return bridge(c, level, block, reach, end -> {
            if (!airForBody(c, level, end)) return false;
            Vec3 eye = Vec3.atBottomCenterOf(end).add(0, c.getEyeHeight(), 0);
            if (!CompanionMiningNavigation.visible(c, eye, block)) return false;
            return !forTheDrop || CompanionMiningNavigation.dropCollectable(level, block, end);
        }, maxGap, allowDugSoil);
    }

    /**
     * A floor across the gap to a cell a drop is taken from (PATH7, #105: the same way, for a drop beyond a gap): the cells
     * whose body touches the drop, the bank he walks to, and the cells of floor to lay between them. Null when no line is there.
     */
    public static BridgePlan bridgeToCell(CompanionEntity c, ServerLevel level, BlockPos cell) {
        return bridge(c, level, cell, 3.5D, end -> airForBody(c, level, end)
                && CompanionMiningNavigation.pickupStand(c, end)
                && CompanionMiningNavigation.touchesCell(c, Vec3.atBottomCenterOf(end), cell), CompanionPillar.WAY_SUPPORTS, false);
    }

    private static BridgePlan bridge(CompanionEntity c, ServerLevel level, BlockPos block, double reach, java.util.function.Predicate<BlockPos> endOk,
                                     int maxGap, boolean allowDugSoil) {
        if (!c.buildWay()) return null;
        int limit = Math.clamp(maxGap, 1, CompanionPillar.WAY_SUPPORTS);
        int material = bridgeBlocks(c);
        boolean dig = allowDugSoil && soilAbout(c, level);
        // The cells he can take it from, nearest him first: air for a body, in reach, and the drop collectable there.
        List<BlockPos> ends = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz > 9) continue;
            BlockPos end = block.offset(dx, dy, dz);
            if (Vec3.atBottomCenterOf(end).distanceToSqr(block.getCenter()) > reach * reach) continue;
            if (!endOk.test(end)) continue;
            ends.add(end.immutable());
        }
        ends.sort(java.util.Comparator.comparingDouble(end -> end.distSqr(c.blockPosition())));
        for (BlockPos end : ends) {
            BridgePlan best = null;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int back = 1; back <= limit + 1; back++) {
                    BlockPos start = end.relative(dir, back);
                    // The bank: a cell he stands in with the ground under it already.
                    if (!CompanionMiningNavigation.standable(c, start)) continue;
                    int gap = back - 1;
                    if (gap <= 0) break;
                    if (gap > limit) break;
                    List<BlockPos> cells = new ArrayList<>();
                    boolean free = true;
                    // The cell he works the block from stands on a floor of its own too: the last cell of the floor is under his
                    // feet at the far end, never only up to it (a bridge that ends over the gap drops him into it).
                    for (int step = 0; step <= gap; step++) {
                        BlockPos cell = end.relative(dir, step);
                        if (!airForBody(c, level, cell)) { free = false; break; }
                        BlockPos floor = cell.below();
                        BlockState floorState = level.getBlockState(floor);
                        if (!floorState.getCollisionShape(level, floor).isEmpty()) continue;   // a ledge or his own floor: nothing to lay
                        if (!level.getFluidState(floor).isEmpty()) { free = false; break; }
                        cells.add(floor.immutable());
                    }
                    if (!free) continue;
                    // A floor already there (a ledge, his own bridge from before) asks no material: only what he still lacks does.
                    if (cells.size() > material && !dig) continue;
                    if (best == null || cells.size() < best.length()) best = new BridgePlan(start.immutable(), end, List.copyOf(cells));
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** How many cells of floor he carries: any solid block item in the bag or hands is a cell of a bridge. */
    public static int bridgeBlocks(CompanionEntity c) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            ItemStack stack = c.getItem(slot);
            if (placeable(stack)) count += stack.getCount();
        }
        if (placeable(c.getMainHandItem())) count += c.getMainHandItem().getCount();
        if (placeable(c.getOffhandItem())) count += c.getOffhandItem().getCount();
        return count;
    }

    /** Whether this stack is a block that can be set down as a cell of floor. */
    public static boolean placeable(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                && !item.getBlock().defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    /** The material for a bridge in his hand: the first solid block of the hands or the bag. */
    static ItemStack bridgeMaterial(CompanionEntity c) {
        if (placeable(c.getMainHandItem())) return c.getMainHandItem();
        if (placeable(c.getOffhandItem())) return c.getOffhandItem();
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (placeable(c.getItem(slot))) return c.getItem(slot);
        return ItemStack.EMPTY;
    }

    /** Air for a body at this cell: nothing solid there or over it, no fluid, inside the border and the loaded world. */
    private static boolean airForBody(CompanionEntity c, ServerLevel level, BlockPos cell) {
        return level.hasChunkAt(cell) && level.hasChunkAt(cell.above()) && level.getWorldBorder().isWithinBounds(cell)
                && level.getFluidState(cell).isEmpty() && level.getFluidState(cell.above()).isEmpty()
                && level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                && level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
                && level.noCollision(c, new AABB(cell.getX() + .2, cell.getY(), cell.getZ() + .2,
                        cell.getX() + .8, cell.getY() + c.getBbHeight(), cell.getZ() + .8));
    }

    /** «5 блоков вверх и 3 вбок»: the geometry of a block, as a refusal names it. */
    public static String distance(CompanionEntity c, BlockPos block) {
        BlockPos feet = c.blockPosition();
        int rise = block.getY() - feet.getY();
        double across = Math.hypot(block.getX() + .5D - c.getX(), block.getZ() + .5D - c.getZ());
        String vertical = rise > 0 ? CompanionWorkItems.blocks(rise) + " вверх" : rise < 0 ? CompanionWorkItems.blocks(-rise) + " вниз" : "на моей высоте";
        return vertical + (across < .6D ? "" : " и " + Math.round(across) + " вбок");
    }

    /** One line per decision, in the game log as well as the journal: the block, where it is, how far up and across, the answer, the shape, and why not. */
    public static void decision(CompanionEntity c, BlockPos block, How how) {
        if (!(c.level() instanceof ServerLevel level) || !level.hasChunkAt(block)) return;
        BlockPos feet = c.blockPosition();
        double across = Math.hypot(block.getX() + .5D - c.getX(), block.getZ() + .5D - c.getZ());
        String answer = how.way().name().toLowerCase(Locale.ROOT);
        // A way asked again every tick is one decision, not sixty lines: said again only when it changes.
        String line = block.toShortString() + "|" + answer + "|" + how.steps() + "|" + how.gap() + "|" + how.why();
        if (line.equals(SAID.get(c.getUUID()))) return;
        SAID.put(c.getUUID(), line);
        int rise = block.getY() - feet.getY();
        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(block).getBlock()).toString();
        String text = block.toShortString() + " «" + id + "»: " + (rise >= 0 ? "+" + rise : String.valueOf(rise)) + " вверх/"
                + Math.round(across * 10) / 10D + " вбок → " + answer + " (" + how.shape() + ")"
                + (how.steps() > 0 ? ", " + how.steps() + " шагов земли" : how.gap() > 0 ? ", " + how.gap() + " клеток пола" : "")
                + (how.why().isEmpty() ? "" : "; почему: " + how.why());
        dev.aicompanion.CompanionDiagnostics.say(c.diagnosticTrace(), "way_decision", text, "entity", c.getUUID(),
                "block", block.toShortString(), "id", id,
                "rise", rise, "across", Math.round(across * 100) / 100D,
                "answer", answer, "shape", how.shape(),
                "steps", how.steps(), "gap", how.gap(), "why", how.why());
    }

    /**
     * Whether a block of soil stands within the dig's reach, where he may take it: the material for a column or a bridge (the
     * dig's own rule, the site of a building and all, CompanionDigGoal).
     */
    static boolean soilAbout(CompanionEntity c, ServerLevel level) {
        BlockPos origin = c.blockPosition();
        for (BlockPos pos : CompanionLogSearch.find(level, origin, 12, BlockTags.DIRT)) {
            if (!c.leashAllows(pos) || !CompanionGround.SOIL.matches(level.getBlockState(pos))) continue;
            if (pos.getY() < origin.getY() - 1 || pos.getY() > origin.getY() + 5 || pos.distSqr(origin) > 12 * 12) continue;
            if (!CompanionGround.SOIL.safeCell(level, pos, c) || !exposed(level, pos)) continue;
            return true;
        }
        return false;
    }

    /** A face of the block open to the air: the dig takes only a block he can come at from a stand. */
    private static boolean exposed(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos beside = pos.relative(side);
            if (level.hasChunkAt(beside) && level.getBlockState(beside).getCollisionShape(level, beside).isEmpty()) return true;
        }
        return false;
    }
}

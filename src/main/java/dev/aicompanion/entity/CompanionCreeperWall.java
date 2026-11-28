package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What {@link CompanionCreeperGuard} does against a lit fuse it cannot run from.
 * <p>
 * Under rock a lit fuse he cannot run from is walled out: two blocks from the bag on the next step towards the creeper, which stands while
 * its fuse burns. A wall cuts the creeper's sight of him, and a hiss goes out then, so it may stand with no blast: he remembers its cells (in
 * memory only) and, once no fuse has burned near for a while, takes it down with the visible swing and picks the blocks up
 * ({@link #takeDown}). It is set only where it does no harm (see {@link #raise}). In water, which has no floor for a way out, he swims
 * straight off the creeper.
 */
final class CompanionCreeperWall {
    /** How far ahead of him the point lies that he swims for, straight off a lit creeper in water. */
    private static final double SWIM_OFF = 4;
    /** A wall is set no nearer the player than this (it would shut him off from the way out), and no nearer a block of his making than this (it would stay there for good). */
    private static final double PLAYER_NEAR = 8;
    private static final int BUILT_NEAR = 4;
    /** A player this far off, at about his level and not on the creeper's side of him, would be shut off from the way out: no wall. */
    private static final double SIDE_RANGE = 32, SIDE_HEIGHT = 6;
    /** No axis leads (a creeper on the diagonal): the one pillar would shield nothing. */
    private static final double DIAGONAL = 1.5;

    final CompanionEntity c;
    private final CompanionBlockBreaking breaking;
    /** The cells of the wall he has set, and the cells of it still to set (a creeper in the cell, a block not in reach yet). */
    private final List<BlockPos> set = new ArrayList<>(), pending = new ArrayList<>();

    CompanionCreeperWall(CompanionEntity c) {
        this.c = c;
        breaking = new CompanionBlockBreaking(c);
    }

    /** Some block of the wall he set stands: the guard holds behind it, then takes it down ({@link #takeDown}). A cell that lost its block (a blast) is forgotten. */
    boolean standing() {
        if (set.isEmpty() && pending.isEmpty()) return false;
        if (c.level() instanceof ServerLevel level) set.removeIf(cell -> !level.hasChunkAt(cell) || !CompanionSeal.kind(level.getBlockState(cell)));
        if (set.isEmpty()) pending.clear();
        return !set.isEmpty();
    }

    /** One tick behind the wall while a fuse burns: the cells not yet set are tried again. */
    void complete(ServerLevel level) {
        pending.removeIf(cell -> {
            CompanionSeal.Result result = CompanionSeal.place(c, cell);
            if (result == CompanionSeal.Result.PLACED) set.add(cell);
            return result == CompanionSeal.Result.PLACED || result == CompanionSeal.Result.NO_BLOCK || !free(level, cell);
        });
    }

    /** A swing stopped half way leaves no cracks. */
    void rest() { breaking.reset(); }

    /**
     * One tick of taking his wall down, a block at a time with the swing of a player and its drop picked up as any harvest is. True when none
     * of it stands. A block out of his reach is let be.
     */
    boolean takeDown(ServerLevel level) {
        pending.clear();
        standing();
        if (set.isEmpty()) { breaking.reset(); return true; }
        BlockPos cell = set.get(0);
        BlockState state = level.getBlockState(cell);
        if (c.distanceToSqr(Vec3.atCenterOf(cell)) > CompanionSeal.REACH * CompanionSeal.REACH) { set.remove(0); return set.isEmpty(); }
        c.getNavigation().stop();
        c.takeToolForBlock(state);
        if (!breaking.tick(level, cell)) return false;
        ItemStack tool = c.getMainHandItem();
        var drops = CompanionTools.drops(state, level, cell, level.getBlockEntity(cell), c, tool);
        if (level.destroyBlock(cell, false, c, 512)) {
            CompanionTools.wearOnBreak(tool, level, state, cell, c);
            c.swing(InteractionHand.MAIN_HAND);
            c.dropHarvest(cell, drops);
        }
        breaking.reset();
        c.putToolAway();
        set.remove(0);
        return set.isEmpty();
    }

    /**
     * Sets the wall: blocks from the bag at his feet and head on the next step towards the creeper, which stands while its fuse burns. True when
     * a block of it was set just now; false where there is no block, no free cell, or the creeper or the reach stand in the way of it. Nobody
     * takes such a wall out but him, so it is set only under a roof of rock (leaves and water are none), away from the buildings of the
     * player, not in water, not with the player by or behind him (it would cut him off from the way out), and not with the creeper on the
     * diagonal (one pillar shields nothing); a cell that holds water or light is no free cell anywhere.
     */
    boolean raise(ServerLevel level, Creeper creeper) {
        if (c.isInWater() || CompanionSeal.available(c) <= 0 || playerNear(level, creeper) || !roofed(level) || byABuilding(level)) return false;
        boolean shut = false;
        for (BlockPos cell : wallCells(level, creeper)) {
            CompanionSeal.Result result = CompanionSeal.place(c, cell);
            if (result == CompanionSeal.Result.PLACED) { set.add(cell.immutable()); shut = true; }
            else if (result == CompanionSeal.Result.NO_BLOCK) return shut;
            else if (!free(level, cell)) shut = true;
            else { pending.add(cell.immutable()); shut = true; }
        }
        return shut;
    }

    /** A cell a wall may take: air or a plant, with no water in it (a chest, a stair or a plate of the player's that holds water is his) and no light block. */
    static boolean free(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty() && !state.is(Blocks.LIGHT);
    }

    /** A block that stops light and a step above his head, below the surface of the column (the leaves of a tree and the water of a pond are no roof). */
    private boolean roofed(ServerLevel level) {
        BlockPos head = c.blockPosition().above(2);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, head.getX(), head.getZ());
        BlockPos.MutableBlockPos cell = head.mutable();
        for (int y = head.getY(); y < top; y++) if (level.getBlockState(cell.setY(y)).canOcclude()) return true;
        return false;
    }

    /** Any player within a few blocks, or farther at about his level and not on the creeper's side of him (deeper in the same tunnel). */
    private boolean playerNear(ServerLevel level, Creeper creeper) {
        Vec3 towards = creeper.position().subtract(c.position()).multiply(1, 0, 1);
        for (var player : level.players()) {
            if (player.isSpectator()) continue;
            double distance = c.distanceTo(player);
            if (distance <= PLAYER_NEAR) return true;
            Vec3 to = player.position().subtract(c.position());
            if (distance <= SIDE_RANGE && Math.abs(to.y) <= SIDE_HEIGHT && to.multiply(1, 0, 1).dot(towards) <= 0) return true;
        }
        return false;
    }

    /** A block of a player's making within a few blocks of him: a crafted block or a chest ({@link CompanionShaftPlan#built}), a site the game saw him build, stone he set down. */
    private boolean byABuilding(ServerLevel level) {
        BlockPos at = c.blockPosition();
        Predicate<BlockPos> site = CompanionPicking.sites(c, level);
        Predicate<BlockPos> stone = c.getOwnerId() == null ? pos -> false : CompanionPlayerBuilds.get(level.getServer()).placedStone(c.getOwnerId(), level);
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-BUILT_NEAR, -BUILT_NEAR, -BUILT_NEAR), at.offset(BUILT_NEAR, BUILT_NEAR, BUILT_NEAR))) {
            if (level.hasChunkAt(pos) && (CompanionShaftPlan.built(level, pos) || stone.test(pos) || site.test(pos))) return true;
        }
        return false;
    }

    private List<BlockPos> wallCells(ServerLevel level, Creeper creeper) {
        Vec3 towards = creeper.position().subtract(c.position());
        double x = Math.abs(towards.x), z = Math.abs(towards.z);
        if (x < DIAGONAL * z && z < DIAGONAL * x) return List.of();
        Direction side = x > z ? (towards.x > 0 ? Direction.EAST : Direction.WEST) : (towards.z > 0 ? Direction.SOUTH : Direction.NORTH);
        for (int rise : new int[]{0, 1, -1}) {
            BlockPos feet = c.blockPosition().relative(side).above(rise);
            if (free(level, feet) && free(level, feet.above())) return List.of(feet, feet.above());
        }
        return List.of();
    }

    /**
     * Where he swims or wades to, straight off the creeper, or null out of water or with rock or lava ahead: a push into those is worse than
     * a blow. A few blocks halve the blast; a blow throws the creeper back by less than one (run r314159-d3/retreat: in a lake, three blows,
     * the fourth too late, dead at sixteen).
     */
    Vec3 swimOff(ServerLevel level, Creeper creeper) {
        Vec3 away = c.position().subtract(creeper.position()).multiply(1, 0, 1);
        if (!c.isInWater() || away.lengthSqr() <= 1.0E-4) return null;
        Vec3 way = away.normalize();
        BlockPos ahead = BlockPos.containing(c.position().add(way));
        if (!level.noCollision(c, c.getBoundingBox().move(way)) || CompanionLava.at(level, ahead) || CompanionLava.at(level, ahead.above())) return null;
        return c.position().add(way.scale(SWIM_OFF));
    }
}

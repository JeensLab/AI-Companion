package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.function.Predicate;

/** The search for the site of a house: a free flat one for the plank house, one clearable at a block of height difference for a template building. */
final class CompanionHouseSite {
    private final CompanionHouse house;
    private final CompanionEntity owner;
    private final boolean template;
    private final CompanionSiteClearing clearing;

    CompanionHouseSite(CompanionHouse house, CompanionEntity owner, boolean template, CompanionSiteClearing clearing) {
        this.house = house; this.owner = owner; this.template = template; this.clearing = clearing;
    }

    /** The corner of a site for a building of that size, or null (also when this tick's route cells went elsewhere: asked again). */
    BlockPos find(ServerLevel level, int width, int depth, int height) {
        if (!template) return search(level, width, depth, height, false, pos -> false);
        // A site that needs no clearing is preferred; failing that, one with only clearable blocks and at most a block of height difference.
        var players = clearing.playerSites(level);
        for (boolean digging : new boolean[]{false, true}) {
            int busy = owner.work.route.busyAnswers();
            BlockPos found = search(level, width, depth, height, digging, players);
            if (found != null || owner.work.route.busyAnswers() != busy) return found;
        }
        return null;
    }

    /** The first living thing or decoration (not the player, not a drop) in the building's box and a block around it; null when none. */
    Entity standingIn(ServerLevel level, BlockPos corner, int width, int height, int depth) {
        return level.getEntities(owner, new AABB(Vec3.atLowerCornerOf(corner.offset(-1, 0, -1)), Vec3.atLowerCornerOf(corner.offset(width + 1, height, depth + 1))),
                entity -> entity.isAlive() && !(entity instanceof Player) && !(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)).stream().findFirst().orElse(null);
    }

    /** Whoever stands in the cells (a block goes into neither the player nor a mob), or null. */
    Entity occupant(ServerLevel level, BlockPos pos, BlockPos second) {
        return level.getEntities(owner, new AABB(pos).minmax(new AABB(second)), entity -> entity.isAlive() && entity.blocksBuilding).stream().findFirst().orElse(null);
    }

    /** A cell near him for a bench, off the building's own ground and its margin of a block. */
    BlockPos tableSite(ServerLevel level, BlockPos base, int width, int depth) {
        for (int radius = 1; radius <= 4; radius++) for (int dy : new int[]{0, -1, 1}) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            BlockPos pos = owner.blockPosition().offset(dx, dy, dz);
            boolean reserved = pos.getX() >= base.getX() - 1 && pos.getX() <= base.getX() + width && pos.getZ() >= base.getZ() - 1 && pos.getZ() <= base.getZ() + depth;
            if (reserved || !house.replaceable(level, pos) || !level.hasChunkAt(pos.below())) continue;
            if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) && level.noCollision(new AABB(pos))) return pos;
        }
        return null;
    }

    private BlockPos search(ServerLevel level, int width, int depth, int height, boolean digging, Predicate<BlockPos> players) {
        BlockPos center = owner.blockPosition();
        for (int radius = 6; radius <= Math.min(24, owner.getSearchRadius()); radius += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) for (int dz = -radius; dz <= radius; dz += 2) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                for (int dy : new int[]{0, -1, 1, -2, 2, -3}) {
                    BlockPos corner = center.offset(dx - width / 2, dy, dz - depth / 2);
                    if (clear(level, corner, width, depth, height, digging, players)) return corner;
                }
            }
        }
        return null;
    }

    private boolean clear(ServerLevel level, BlockPos corner, int width, int depth, int height, boolean digging, Predicate<BlockPos> players) {
        // Nobody placed it (no ghost): only plants, trees and the ground are broken for it, nothing that might be a player's.
        if (template) { if (clearing.assess(level, corner, digging, players, true) < 0) return false; }
        else for (int x = -1; x <= width; x++) for (int z = -1; z <= depth; z++) {
            BlockPos floor = corner.offset(x, -1, z);
            if (!level.hasChunkAt(floor) || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) || !level.getFluidState(floor).isEmpty()) return false;
            for (int y = 0; y < height; y++) if (!house.replaceable(level, corner.offset(x, y, z))) return false;
        }
        if (!level.getEntities(owner, new AABB(Vec3.atLowerCornerOf(corner.offset(-1, 0, -1)), Vec3.atLowerCornerOf(corner.offset(width + 1, height, depth + 1))), entity -> entity.isAlive()).isEmpty()) return false;
        // The floor of a template building is sunk, so the ground to walk on beside it is one block above its corner.
        int stand = template ? 1 : 0;
        return owner.work.route.toCells(Set.of(corner.offset(width / 2, stand, -1), corner.offset(-1, stand, depth / 2), corner.offset(width, stand, depth / 2))) != null;
    }
}

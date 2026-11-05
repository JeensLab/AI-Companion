package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionDeathSpots;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.UUID;

/** The fixtures the death-recovery tests share: a companion of the test's own, stacks, a fall, and a yard of known ground. */
final class DeathTestKit {
    private DeathTestKit() { }

    static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 2, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() - 4);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        dev.aicompanion.entity.CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        // The world and its store outlive a test: this owner starts with no places of his own.
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        for (var spot : store.all(owner.getUUID())) store.taken(owner.getUUID(), spot.id());
        return companion;
    }

    /** An item entity of the test's own, not yet part of a fall. */
    static ItemEntity stack(GameTestHelper h, BlockPos pos, ItemStack stack) {
        var item = new ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY() + .2, pos.getZ() + .5, stack);
        h.getLevel().addFreshEntity(item);
        return item;
    }

    /** A fall at a place, with the manifest of these stacks; returns the place's id. */
    static UUID place(GameTestHelper h, CompanionEntity c, BlockPos pos, double worth, ItemEntity... items) {
        UUID spotId = UUID.randomUUID();
        for (ItemEntity item : items) {
            item.getPersistentData().putUUID("aicompanion:belongings", c.getOwnerId());
            item.getPersistentData().putUUID("aicompanion:belongings_spot", spotId);
        }
        CompanionDeathSpots.get(h.getLevel().getServer()).died(c.getOwnerId(), spotId, h.getLevel().dimension(), pos,
                worth, h.getLevel().getGameTime(), CompanionDeathSpots.manifestOf(List.of(items)));
        return spotId;
    }

    /** Lets him fall to the ground before the test builds its geometry round him. */
    static void settle(CompanionEntity c) {
        for (int tick = 0; tick < 40 && !c.onGround(); tick++) c.tick();
    }

    /** A flat stone yard round him, air above: the test's ground of its own, whatever another test left nearby. */
    static BlockPos yard(GameTestHelper h, CompanionEntity c, int radius) {
        BlockPos feet = c.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            put(h, feet.offset(dx, -1, dz), Blocks.STONE);
            for (int dy = 0; dy <= 2; dy++) put(h, feet.offset(dx, dy, dz), Blocks.AIR);
        }
        return feet;
    }

    /** A block at an absolute position (the helper's own setBlock counts from the structure's corner). */
    static void put(GameTestHelper h, BlockPos pos, Block block) {
        h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
    }

    static void put(GameTestHelper h, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        h.getLevel().setBlock(pos, state, 3);
    }

    /**
     * Sets him out for the dearest place, asking again a moment later: a stack that fell a moment ago can be missing from
     * the first look, and the walk only sets out once it has been seen. Returns whether the walk is under way.
     */
    static boolean setOut(GameTestHelper h, CompanionEntity c) {
        for (int attempt = 0; attempt < 3 && !c.deathTrip.active(); attempt++) {
            if (c.deathTrip.begin()) return true;
            c.tick();
        }
        return c.deathTrip.active();
    }

    /** Sets him out and ticks until the walk ends; returns how many ticks it took. */
    static int walk(GameTestHelper h, CompanionEntity c, int limit) {
        if (!c.deathTrip.active()) setOut(h, c);
        int tick = 0;
        for (; tick < limit && c.deathTrip.active(); tick++) c.tick();
        return tick;
    }

}

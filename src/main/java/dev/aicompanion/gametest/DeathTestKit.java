package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionDeathSpots;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.UUID;

/** The fixtures the death-recovery tests share: a companion of the test's own, stacks, a fall, and a yard of known ground. */
final class DeathTestKit {
    private DeathTestKit() { }

    /**
     * The game's own bookkeeping, run as a tick would run it. A test body holds the server thread from its first
     * statement to its last: the level never ticks while it runs, and the chunk-status changes its own edits cause stay
     * queued on the server's main-thread chunk queue. Until that queue is drained the entity manager still counts those
     * chunks hidden, so a stack placed in one is in a section nobody hands out: {@code getEntity} does not find it,
     * {@code getEntitiesOfClass} does not see it, and it does not tick — a look at it says «gone», and the walk never
     * sets out («He did not set out» in a filtered run, where the arena lands on a chunk that very run promoted).
     * Draining the queue is what the server itself does between ticks: no sleep, and nothing loaded that had no ticket.
     */
    static void settleWorld(GameTestHelper h) {
        for (int guard = 0; guard < 4096 && h.getLevel().getChunkSource().pollTask(); guard++) { }
    }

    /**
     * Waits, the way the game itself would, until a thing the test put into the world is handed out by it. A chunk the
     * test's own edits brought into being is loaded and promoted by the server on its worker threads, and only the
     * server's queued chunk work publishes that status to the entity manager; a test body never lets it run, so the
     * entity stays in a section nobody hands out until it is drained here. A chunk that no ticket covers (a place far
     * off, which a walk goes to and judges only once it is there) is left alone: it is not loaded for the test's sake.
     */
    static void handOut(GameTestHelper h, Entity entity) {
        var level = h.getLevel();
        for (int round = 0; round < 8 && level.getEntity(entity.getUUID()) == null; round++) {
            if (!level.hasChunkAt(entity.blockPosition())) return;
            settleWorld(h);
            // The load blocks until the chunk is full and runs the queued work while it waits, as the server does.
            level.getChunk(entity.blockPosition().getX() >> 4, entity.blockPosition().getZ() >> 4);
        }
    }

    static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 2, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() - 4);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        dev.aicompanion.entity.CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        handOut(h, companion);
        // The world and its store outlive a test: this owner starts with no places of his own.
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        for (var spot : store.all(owner.getUUID())) store.taken(owner.getUUID(), spot.id());
        return companion;
    }

    /** An item entity of the test's own, not yet part of a fall. */
    static ItemEntity stack(GameTestHelper h, BlockPos pos, ItemStack stack) {
        var item = new ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY() + .2, pos.getZ() + .5, stack);
        h.getLevel().addFreshEntity(item);
        // The chunk may be one this test itself brought into being: let the game hand the stack out before it is looked for.
        handOut(h, item);
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

    /**
     * A flat stone yard round him, air above: the test's ground of its own, whatever another test left nearby. It ends at
     * the edge of the test's own structure: past it lies the arena of another test, whose walls and floor are not this
     * test's to cut. A test that needs more ground than "empty" has takes "large_search".
     */
    static BlockPos yard(GameTestHelper h, CompanionEntity c, int radius) {
        BlockPos feet = c.blockPosition();
        var own = h.getBounds();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (feet.getX() + dx < own.minX || feet.getX() + dx >= own.maxX || feet.getZ() + dz < own.minZ || feet.getZ() + dz >= own.maxZ) continue;
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
            // The world's own bookkeeping first: a chunk the ticks below brought into being is only handed out once
            // the server's queued chunk work has run, and a look before that says «gone» of a stack that is lying there.
            settleWorld(h);
            if (c.deathTrip.begin()) return true;
            // The game looks for a place once a second: the test asks again the same way, not tick after tick.
            for (int wait = 0; wait < 20; wait++) c.tick();
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

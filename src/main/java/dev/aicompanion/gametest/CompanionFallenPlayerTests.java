package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Skyblock probe 15.11: the player stepped off the island and fell a hundred blocks; the self-rescue dug the island up toward
 * him (a route to the player below does not exist). A player who fell is waited for, not dug to.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFallenPlayerTests {
    private static final int ISLAND_TOP = 22; // top layer y; the island is three layers of soil in the air

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aPlayerWhoFellOffTheIslandIsNotDugTo(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 30; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : Blocks.AIR);
        int islandBlocks = 0;
        for (int x = 12; x <= 18; x++) for (int z = 12; z <= 18; z++) for (int y = ISLAND_TOP - 2; y <= ISLAND_TOP; y++) {
            h.setBlock(new BlockPos(x, y, z), y == ISLAND_TOP ? Blocks.GRASS_BLOCK : Blocks.DIRT); islandBlocks++;
        }
        final int total = islandBlocks;
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 14, ISLAND_TOP + 1, 15);
        companion.setItem(0, new ItemStack(Items.STONE_SHOVEL));
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(15, ISLAND_TOP + 1, 15));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setFollowing(true);
        BlockPos floor = h.absolutePos(new BlockPos(24, 2, 15));
        // Off the edge and down 20 blocks in a second (a mock player has no physics of its own).
        h.runAtTickTime(20, () -> owner.teleportTo(floor.getX() + .5, owner.getY(), floor.getZ() + .5));
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t > 20 && t <= 40) owner.teleportTo(owner.getX(), Math.max(floor.getY(), owner.getY() - 1), owner.getZ());
        });
        h.runAtTickTime(300, () -> {
            int left = 0;
            for (int x = 12; x <= 18; x++) for (int z = 12; z <= 18; z++) for (int y = ISLAND_TOP - 2; y <= ISLAND_TOP; y++)
                if (!h.getBlockState(new BlockPos(x, y, z)).is(y == ISLAND_TOP ? Blocks.GRASS_BLOCK : Blocks.DIRT)) left++;
            h.assertTrue(left == 0, "Island blocks broken: " + left + " of " + total + " (" + companion.getLastTaskResult() + ")");
            h.assertTrue(companion.getY() > h.absolutePos(new BlockPos(0, ISLAND_TOP, 0)).getY(), "Off the island at " + h.relativePos(companion.blockPosition()));
            // The wait survives a save: a world reloaded with the player still far below must not dig the island either.
            CompoundTag saved = new CompoundTag();
            companion.saveWithoutId(saved);
            CompanionEntity reloaded = AICompanionMod.COMPANION.get().create(h.getLevel());
            reloaded.load(saved);
            CompoundTag again = new CompoundTag();
            reloaded.saveWithoutId(again);
            h.assertTrue(again.getBoolean("PlayerFell"), "The fall was forgotten by a save and reload");
            h.succeed();
        });
    }

    /**
     * A player who goes down a shaft by himself is followed (the self-rescue digs to him): 10 blocks at a ladder's 0.15 a tick take
     * ~67 ticks, and over 3 s that is 8+ blocks: only a window of a second tells it from a fall.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aPlayerWhoClimbsDownAShaftIsFollowed(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 30; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : y < 15 ? Blocks.STONE : Blocks.AIR);
        for (int y = 5; y <= 14; y++) h.setBlock(new BlockPos(20, y, 16), Blocks.AIR); // a shaft one block wide, ten deep
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 14, 15, 16);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos rim = h.absolutePos(new BlockPos(20, 15, 16));
        BlockPos bottom = h.absolutePos(new BlockPos(20, 5, 16));
        owner.teleportTo(rim.getX() + .5, rim.getY(), rim.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setFollowing(true);
        h.onEachTick(() -> {
            if (h.getTick() > 20 && owner.getY() > bottom.getY()) owner.teleportTo(owner.getX(), Math.max(bottom.getY(), owner.getY() - .15), owner.getZ());
        });
        h.succeedWhen(() -> h.assertTrue(companion.isSelfRescuing(), "Not following into the shaft: " + companion.getLastTaskResult()));
    }
}

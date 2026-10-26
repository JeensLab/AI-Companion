package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Review 22.10, К2: «наруби дерева» took any logs, a log cabin too; «добудь камня» took any cobblestone, a wall too. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBuildingsTests {
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void logCabinNextToATreeStaysStanding(GameTestHelper h) {
        floor(h, Blocks.GRASS_BLOCK);
        // A cabin of logs with a plank roof, four blocks from him, nearer than the tree.
        for (int x = 8; x <= 12; x++) for (int z = 8; z <= 12; z++) {
            boolean wall = x == 8 || x == 12 || z == 8 || z == 12;
            for (int y = 1; y <= 3; y++) if (wall && !(x == 10 && z == 12 && y <= 2)) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
            h.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);
        }
        int cabin = count(h, Blocks.OAK_LOG);
        // A real tree with its crown farther away.
        for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(26, y, 26), Blocks.BIRCH_LOG);
        for (int x = 24; x <= 28; x++) for (int z = 24; z <= 28; z++) for (int y = 4; y <= 6; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).isAir()) h.setBlock(new BlockPos(x, y, z), Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        CompanionEntity companion = spawn(h);
        companion.startGatheringLogs(3);
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isGatheringTask(), "Still gathering: " + companion.getLastTaskResult());
            h.assertTrue(count(h, Blocks.OAK_LOG) == cabin, "The log cabin was chopped: " + (cabin - count(h, Blocks.OAK_LOG)) + " logs gone");
            // Some of the tree may go into a shovel for climbing soil: what counts is that the tree was chopped, not the cabin.
            h.assertTrue(count(h, Blocks.BIRCH_LOG) <= 2, "Did not get the tree: " + companion.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void cobblestoneWallIsNotQuarried(GameTestHelper h) {
        floor(h, Blocks.GRASS_BLOCK);
        for (int x = 10; x <= 15; x++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, 20), Blocks.COBBLESTONE);
        int wall = count(h, Blocks.COBBLESTONE);
        for (int x = 24; x <= 27; x++) for (int z = 24; z <= 27; z++) for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        CompanionEntity companion = spawn(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.startWork("gather_stone", "", 3);
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still working: " + companion.getLastTaskResult());
            h.assertTrue(count(h, Blocks.COBBLESTONE) == wall, "The cobblestone wall was taken apart");
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
        });
    }

    private static void floor(GameTestHelper h, Block top) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), x == 0 || z == 0 || x == 31 || z == 31 ? Blocks.BEDROCK : top);
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 2, companion.getY(), companion.getZ() - 2);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    private static int count(GameTestHelper h, Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(30, 8, 30))) if (h.getBlockState(pos).is(block)) count++;
        return count;
    }

    private static int carried(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

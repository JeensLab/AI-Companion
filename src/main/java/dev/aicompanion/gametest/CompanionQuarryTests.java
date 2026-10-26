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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Stone like a player: stairs instead of a shaft, mining on the own level, back up the stairs before crafting. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionQuarryTests {
    private static final int SURFACE = 6; // feet level above the top layer

    /**
     * Game log 22.10: one bare stone in the ground next to Oleg, more stone under the grass. He mined that floor
     * block, then the one under it, and so on: a 13-deep shaft he kept falling into. Stone is reached by stairs.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void bareStoneInTheGroundIsReachedByStairsNotAShaft(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y < SURFACE; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? Blocks.BEDROCK
                    : y == SURFACE - 1 ? Blocks.GRASS_BLOCK : Blocks.STONE);
        h.setBlock(new BlockPos(17, SURFACE - 1, 16), Blocks.STONE); // the bare patch beside his feet
        CompanionEntity companion = spawn(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.startWork("gather_stone", "", 8);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 8, companion.getLastTaskResult());
            int deepestColumn = 0, mined = 0;
            for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) {
                int column = 0;
                for (int y = 1; y < SURFACE; y++) if (h.getBlockState(new BlockPos(x, y, z)).isAir()) column++;
                deepestColumn = Math.max(deepestColumn, column); mined += column;
            }
            h.assertTrue(mined >= 8 && deepestColumn <= 3, "Dug a shaft instead of stairs: deepest column " + deepestColumn + ", mined " + mined);
            h.assertTrue(companion.getY() >= h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() - .01, "Did not climb back out: " + companion.position());
        });
    }

    /** Game log 22.10: crafting right after mining happened at the bottom of the pit, with a new bench there. */
    @GameTest(template = "tall_tree", timeoutTicks = 2600)
    public static void climbsOutAndCraftsAtTheSurfaceBench(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y < SURFACE; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? Blocks.BEDROCK
                    : y <= 2 ? Blocks.STONE : y == SURFACE - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        BlockPos bench = new BlockPos(12, SURFACE, 16); h.setBlock(bench, Blocks.CRAFTING_TABLE);
        CompanionEntity companion = spawn(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        companion.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        double surface = h.absolutePos(new BlockPos(0, SURFACE, 0)).getY();
        double[] craftedAtY = {Double.NaN};
        h.onEachTick(() -> { if (Double.isNaN(craftedAtY[0]) && count(companion, Items.STONE_PICKAXE) > 0) craftedAtY[0] = companion.getY(); });
        h.assertTrue(CompanionEvents.executeAction(owner, "craft_item", "minecraft:stone_pickaxe", 1).success(), "Craft order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.STONE_PICKAXE) == 1, companion.getLastTaskResult());
            int benches = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(30, SURFACE + 2, 30))) if (h.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) benches++;
            h.assertTrue(benches == 1 && h.getBlockState(bench).is(Blocks.CRAFTING_TABLE), "Placed another bench instead of using the surface one: " + benches);
            h.assertTrue(craftedAtY[0] >= surface - .01, "Crafted at the bottom of the stairs, y=" + (craftedAtY[0] - surface + SURFACE));
        });
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, SURFACE, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
    }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

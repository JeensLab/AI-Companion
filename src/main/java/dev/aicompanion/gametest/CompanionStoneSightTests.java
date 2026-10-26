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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game log 22.10 (build f21ae3e): «рядом куча камня, а он копает землю», «добывает 3-4 камня и в упор не видит камень рядом»,
 * «зачем-то добывает всю землю вокруг камня». Stone a player would simply mine is mined, and the soil around it stays.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionStoneSightTests {
    private static final int GROUND = 5; // top layer of the arena floor

    /** Bare stone ground: the stone one step below his feet is the stone next to him, not «nothing on my level». */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void stoneGroundBesideHimIsMinedWithoutStairs(GameTestHelper h) {
        arena(h, (x, y, z) -> y == GROUND ? Blocks.STONE : Blocks.STONE);
        CompanionEntity companion = spawn(h, GROUND + 1);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 4, companion.getLastTaskResult());
            h.assertTrue(deepestHole(h, GROUND) <= 1, "Dug stairs or a pit into a stone floor: " + deepestHole(h, GROUND) + " deep");
        });
    }

    /**
     * A stone cliff with grass on top: the faces at his height have stone above them and the top row has soil above.
     * He used to reject the first ones and strip the soil off the others.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void cliffStoneIsMinedAndTheSoilOnTopStays(GameTestHelper h) {
        arena(h, (x, y, z) -> y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        for (int x = 10; x <= 22; x++) for (int z = 21; z <= 24; z++) {
            for (int y = GROUND + 1; y <= GROUND + 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            h.setBlock(new BlockPos(x, GROUND + 4, z), Blocks.DIRT);
            h.setBlock(new BlockPos(x, GROUND + 5, z), Blocks.GRASS_BLOCK);
        }
        int soilBefore = soil(h);
        CompanionEntity companion = spawn(h, GROUND + 1);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 4, companion.getLastTaskResult());
            h.assertTrue(soil(h) == soilBefore, "Dug soil to get at stone: " + (soilBefore - soil(h)) + " soil blocks gone");
        });
    }

    /** He stands on a soil hill; plenty of bare stone lies in the hollow below it. He walks down instead of digging the hill. */
    @GameTest(template = "tall_tree", timeoutTicks = 2000)
    public static void stoneDownTheSlopeIsNotIgnored(GameTestHelper h) {
        arena(h, (x, y, z) -> y == GROUND ? Blocks.STONE : Blocks.STONE);
        // A soil hill four blocks high with a walkable slope down to the stone hollow.
        for (int x = 8; x <= 24; x++) for (int z = 8; z <= 18; z++) {
            int top = z <= 14 ? GROUND + 4 : GROUND + 4 - (z - 14);
            for (int y = GROUND + 1; y <= top; y++) h.setBlock(new BlockPos(x, y, z), y == top ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        }
        int soilBefore = soil(h);
        CompanionEntity companion = spawn(h, GROUND + 5, 16, 11);
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 4, companion.getLastTaskResult());
            h.assertTrue(soil(h) == soilBefore, "Dug into the hill instead of walking down: " + (soilBefore - soil(h)) + " soil blocks gone");
        });
    }

    interface Floor { Block at(int x, int y, int z); }

    private static void arena(GameTestHelper h, Floor floor) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? Blocks.BEDROCK : floor.at(x, y, z));
    }

    private static CompanionEntity spawn(GameTestHelper h, int y) { return spawn(h, y, 16, 16); }

    private static CompanionEntity spawn(GameTestHelper h, int y, int x, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.startWork("gather_stone", "", 4);
        return companion;
    }

    private static int deepestHole(GameTestHelper h, int top) {
        int deepest = 0;
        for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) {
            int depth = 0;
            for (int y = top; y >= 1 && h.getBlockState(new BlockPos(x, y, z)).isAir(); y--) depth++;
            deepest = Math.max(deepest, depth);
        }
        return deepest;
    }

    private static int soil(GameTestHelper h) {
        int soil = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(30, GROUND + 6, 30))) {
            var state = h.getBlockState(pos);
            if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)) soil++;
        }
        return soil;
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

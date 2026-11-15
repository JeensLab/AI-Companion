package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A cobblestone generator is worked like a player works one: from a dry cell, again and again as the stone grows back. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCobbleGeneratorTests {
    private static final int SETTLE = 60;

    private CompanionCobbleGeneratorTests() { }

    /**
     * The layout that worked in game (probe 8): lava at x 20, the stone at 21, a pit at 22 that the water of 23 falls into, so the
     * lava that runs into 21 meets water and hardens. Walls are cobblestone (not natural stone: no other stone to take here).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aCobbleGeneratorIsMinedFiveTimesAsItGrowsBack(GameTestHelper h) {
        BlockPos stone = generator(h), lava = stone.west(), water = stone.east(2);
        CompanionEntity c = companion(h);
        boolean[] burnt = {false};
        h.onEachTick(() -> burnt[0] |= c.isOnFire() || c.isInLava());
        h.runAfterDelay(SETTLE, () -> h.assertTrue(c.startWork("gather_stone", "", 5).success(), "Stone order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE && !c.isGatheringTask() && !c.isWorking(), "Work still active: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.COBBLESTONE) == 5, c.getLastTaskResult() + " | " + c.getStoredItemsSummary());
            h.assertTrue(!burnt[0] && c.getHealth() >= c.getMaxHealth(), "Burnt or hurt: " + c.getHealth());
            h.assertTrue(h.getBlockState(lava).is(Blocks.LAVA) && h.getBlockState(water).is(Blocks.WATER), "The generator was changed");
        });
    }

    /**
     * Water only under the stone hardens nothing (vanilla: lava turns to stone by water beside or over it), so the lava that runs
     * into the cell stays lava. It is no generator: he leaves the stone and takes none from it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void waterOnlyUnderTheStoneIsNoGenerator(GameTestHelper h) {
        room(h);
        BlockPos lava = new BlockPos(20, 1, 16), stone = new BlockPos(21, 1, 16);
        for (BlockPos wall : new BlockPos[]{new BlockPos(19, 1, 16), new BlockPos(20, 1, 15), new BlockPos(20, 1, 17)}) h.setBlock(wall, Blocks.COBBLESTONE);
        h.setBlock(stone.below(), Blocks.WATER); h.setBlock(lava, Blocks.LAVA); h.setBlock(stone, Blocks.COBBLESTONE);
        CompanionEntity c = companion(h);
        boolean[] mined = {false};
        h.onEachTick(() -> mined[0] |= !h.getBlockState(stone).is(Blocks.COBBLESTONE));
        h.runAfterDelay(SETTLE, () -> h.assertTrue(c.startWork("gather_stone", "", 5).success(), "Stone order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE && !c.isGatheringTask() && !c.isWorking(), "Work still active: " + c.getLastTaskResult());
            h.assertTrue(!mined[0] && count(c, Items.COBBLESTONE) == 0 && !c.didLastTaskSucceed(), "The stone was taken: " + c.getLastTaskResult() + " | " + c.getStoredItemsSummary());
        });
    }

    /** His own deed (autonomy) is no order from the player: a cobblestone wall between lava and water may be his lava trap, he does not take it apart. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aDeedOfHisOwnLeavesTheGeneratorAlone(GameTestHelper h) {
        BlockPos stone = generator(h);
        CompanionEntity c = companion(h);
        c.setItem(0, new ItemStack(Items.OAK_LOG, 16));
        boolean[] deed = {false}, mined = {false};
        h.onEachTick(() -> {
            deed[0] |= c.autonomy().running();
            mined[0] |= !h.getBlockState(stone).is(Blocks.COBBLESTONE);
        });
        c.autonomy().setEnabled(true);
        h.succeedWhen(() -> {
            h.assertTrue(deed[0] && h.getTick() > 400 && !c.autonomy().running(), "His stone deed did not come and go: " + c.autonomy().deedName() + " | " + c.getLastTaskResult());
            h.assertTrue(!mined[0] && count(c, Items.COBBLESTONE) == 0, "The wall was taken apart: " + c.getStoredItemsSummary());
            c.autonomy().setEnabled(false);
        });
    }

    /** The room with its walls, ceiling and bedrock floor, and the lava, stone and water-over-a-pit of probe 8; returns the stone. */
    private static BlockPos generator(GameTestHelper h) {
        room(h);
        BlockPos lava = new BlockPos(20, 1, 16), stone = new BlockPos(21, 1, 16), water = new BlockPos(23, 1, 16);
        h.setBlock(new BlockPos(22, 0, 16), Blocks.AIR);
        for (BlockPos wall : new BlockPos[]{new BlockPos(19, 1, 16), new BlockPos(20, 1, 15), new BlockPos(20, 1, 17),
                new BlockPos(24, 1, 16), new BlockPos(23, 1, 15), new BlockPos(23, 1, 17)}) h.setBlock(wall, Blocks.COBBLESTONE);
        h.setBlock(lava, Blocks.LAVA); h.setBlock(water, Blocks.WATER); h.setBlock(stone, Blocks.COBBLESTONE);
        return stone;
    }

    private static void room(GameTestHelper h) {
        box(h, 0, -1, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 7, 0, 31, 7, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 6, 0, Blocks.BEDROCK); box(h, 0, 1, 31, 31, 6, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 0, 6, 31, Blocks.BEDROCK); box(h, 31, 1, 0, 31, 6, 31, Blocks.BEDROCK);
        box(h, 1, 1, 1, 30, 6, 30, Blocks.AIR);
    }

    /** Oleg with a stone pickaxe, his owner a few blocks away; the stone order or his own deed comes after the caller's setup. */
    private static CompanionEntity companion(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(new BlockPos(13, 1, 13));
        owner.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setLifestyle(false, false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        return c;
    }

    private static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(new BlockPos(x, y, z), block);
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.support.WaterMiningKit.*;

import java.util.List;

/** Ponds and lakes behind the ore: they are never let out, and the dry ore is taken. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWaterMiningPondTests {
    private CompanionWaterMiningPondTests() { }

    /** Review 3: an iron washed by a pond he stands in: a pond runs in again, so nothing is plugged, the bag keeps its cobblestone. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void anOreInAPondIsTakenWithoutPluggingThePond(GameTestHelper h) {
        clear(h);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 1, 31, Blocks.STONE);
        box(h, 9, 2, 9, 23, 2, 23, Blocks.STONE);
        box(h, 10, 2, 10, 22, 2, 22, Blocks.WATER);
        h.setBlock(new BlockPos(16, 2, 16), Blocks.IRON_ORE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 13, 2, 13);
        bind(h, c, 5, 2, 5);
        c.setLifestyle(false, false);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        h.runAfterDelay(20, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 22 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            int inPond = 0;
            for (int x = 10; x <= 22; x++) for (int z = 10; z <= 22; z++) if (h.getBlockState(new BlockPos(x, 2, z)).is(Blocks.COBBLESTONE)) inPond++;
            h.assertTrue(inPond == 0 && count(c, Items.COBBLESTONE) == 5, inPond + " cobblestones in the pond, bag " + count(c, Items.COBBLESTONE));
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c) + " " + c.getLastTaskResult());
        });
    }

    /**
     * Review 3: an iron in the floor of a pond one block deep, the pond over it: nothing is plugged over it (a plug would seal the drop
     * in the pit), and he never says «Готово, накопал» for a block whose drop he does not have: either the raw iron is in the bag, or the
     * order says it was not got.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void anOreUnderAPondIsDoneOnlyWithItsDrop(GameTestHelper h) {
        clear(h);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 1, 31, Blocks.STONE);
        box(h, 9, 2, 9, 23, 2, 23, Blocks.STONE);
        box(h, 10, 2, 10, 22, 2, 22, Blocks.WATER);
        BlockPos ore = new BlockPos(16, 1, 16);
        h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 13, 2, 13);
        bind(h, c, 5, 2, 5);
        c.setLifestyle(false, false);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        h.runAfterDelay(20, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 22 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(!h.getBlockState(ore.above()).is(Blocks.COBBLESTONE), "The pond over the ore was plugged");
            String said = CompanionDialogue.summarize(List.of(new PlannedAction("mine_ore", 0, 1, "minecraft:iron_ore")),
                    (c.didLastTaskSucceed() ? "" : "План остановлен: ") + c.getLastTaskResult());
            h.assertTrue(count(c, Items.RAW_IRON) == 1 ? c.didLastTaskSucceed() : !c.didLastTaskSucceed() && !said.startsWith("Готово"),
                    "Said done without the drop: " + c.getLastTaskResult() + " | " + said);
            // Its drop would lie in the hole under water, where he cannot stand: the ore is left as it was (game 30.10).
            h.assertTrue(count(c, Items.RAW_IRON) == 1 || h.getBlockState(ore).is(Blocks.IRON_ORE), "Broken and its drop left: " + c.getLastTaskResult());
        });
    }

    /**
     * Game 30.10: iron in the stone at the edge of an underground lake (the lake is shut in, no way into it on foot). The one way to it
     * (the cell beside it on the dry side is bedrock) runs along the water: a cell dug there would let the lake in. The way is not dug,
     * the lake stays, the iron stands, the dry iron far from the lake is taken and the reason is «не нашёл безопасного хода».
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void anOreAtALakeWithTheWayAlongTheWaterIsLeftAndTheDryOreIsTaken(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y < SURFACE; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? Blocks.BEDROCK
                    : y <= 3 ? Blocks.STONE : y == SURFACE - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        box(h, 20, 2, 10, 27, 3, 22, Blocks.WATER);
        BlockPos wet = new BlockPos(19, 2, 16), dry = new BlockPos(6, 3, 16);
        h.setBlock(wet, Blocks.IRON_ORE); h.setBlock(dry, Blocks.IRON_ORE); h.setBlock(new BlockPos(18, 2, 16), Blocks.BEDROCK);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 12, SURFACE, 16);
        bind(h, c, 12, SURFACE, 12);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        boolean[] wetWay = {false};
        h.onEachTick(() -> {
            for (int x = 17; x <= 19; x++) for (int y = 2; y <= 5; y++) for (int z = 10; z <= 22; z++) wetWay[0] |= !h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, y, z))).isEmpty();
        });
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 2).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(!wetWay[0], "Water ran into his way: " + report(c));
            h.assertTrue(h.getBlockState(wet).is(Blocks.IRON_ORE) && !h.getBlockState(dry).is(Blocks.IRON_ORE) && count(c, Items.RAW_IRON) == 1, "Not the dry ore alone: " + report(c));
            h.assertTrue(!c.didLastTaskSucceed() && c.getLastTaskResult().contains("не нашёл безопасного хода"), "Not the no-safe-way reason: " + report(c));
            h.assertTrue(c.getHealth() >= c.getMaxHealth(), "Hurt: " + c.getHealth());
        });
    }

    /** Iron in a wall on dry land with a lake of several cells behind it (no single spring): left alone as on main, the lake is not let out. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void anOreInAWallWithALakeBehindItIsLeftAndTheLakeStays(GameTestHelper h) {
        CompanionEntity c = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16);
        h.setBlock(ore, Blocks.IRON_ORE);
        box(h, 21, 1, 15, 22, 2, 17, Blocks.WATER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            h.assertTrue(!c.didLastTaskSucceed() && c.getLastTaskResult().contains("не нашёл безопасного хода") && !c.getLastTaskResult().contains("родник"), report(c));
            h.assertTrue(h.getBlockState(ore).is(Blocks.IRON_ORE) && h.getBlockState(ore.above()).is(Blocks.STONE), "The wall was opened: " + report(c));
            h.assertTrue(h.getBlockState(new BlockPos(21, 1, 16)).is(Blocks.WATER) && count(c, Items.COBBLESTONE) == 16, "The lake was touched: " + report(c));
        });
    }

    /**
     * Review 6, finding 1: iron buried beside a pond one deep in the grass. The shortest stairs start from a stand in the pond, which
     * would run down them; he digs from the dry bank instead, as main does, and no water gets into his way.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void anOreBesideAShallowPondIsDugFromTheDryBank(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y < SURFACE; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? Blocks.BEDROCK
                    : y <= 3 ? Blocks.STONE : y == SURFACE - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        box(h, 6, SURFACE - 1, 10, 14, SURFACE - 1, 22, Blocks.WATER);
        BlockPos ore = new BlockPos(18, 2, 16);
        h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 3, SURFACE, 16);
        bind(h, c, 2, SURFACE, 14);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        boolean[] wetWay = {false};
        h.onEachTick(() -> {
            for (int x = 5; x <= 20; x++) for (int y = 1; y <= SURFACE - 1; y++) for (int z = 9; z <= 23; z++)
                if (!(x >= 6 && x <= 14 && y == SURFACE - 1 && z >= 10 && z <= 22)) wetWay[0] |= !h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, y, z))).isEmpty();
        });
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(!wetWay[0], "The pond ran into his way: " + report(c));
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, "No iron: " + report(c) + " " + c.getLastTaskResult());
        });
    }

    /** Review 6, finding 2: the cell of the pond behind the ore is seagrass, water all the same: the ore is left and the pond stays, as on main. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void aPondHeldBySeagrassBehindTheOreIsNotLetOut(GameTestHelper h) { pondHeldBehind(h, Blocks.SEAGRASS.defaultBlockState()); }

    /** The same with waterlogged glow lichen, as in a cave with an aquifer. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void aPondHeldByWaterloggedLichenBehindTheOreIsNotLetOut(GameTestHelper h) {
        pondHeldBehind(h, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true).setValue(BlockStateProperties.WATERLOGGED, true));
    }

    /** Review 6, finding 7: the nearest stones hold a lake back; «добудь камня» takes the dry ones farther off and the lake stays, as on main. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void stoneThatHoldsALakeIsLeftAndTheDryStonesAreTaken(GameTestHelper h) {
        CompanionEntity c = flat(h);
        box(h, 20, 1, 10, 25, 3, 22, Blocks.BEDROCK);
        box(h, 20, 1, 13, 20, 1, 19, Blocks.STONE);
        box(h, 21, 1, 12, 24, 2, 20, Blocks.WATER);
        for (BlockPos dry : new BlockPos[]{new BlockPos(6, 1, 6), new BlockPos(6, 1, 26), new BlockPos(4, 1, 16)}) h.setBlock(dry, Blocks.STONE);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        boolean[] wet = {false};
        h.onEachTick(() -> {
            for (int x = 8; x <= 20; x++) for (int z = 8; z <= 24; z++) wet[0] |= !h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, 1, z))).isEmpty();
        });
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("gather_stone", "", 3).success(), "Stone order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(!wet[0], "The lake was let out: " + report(c));
            for (int z = 13; z <= 19; z++) h.assertTrue(h.getBlockState(new BlockPos(20, 1, z)).is(Blocks.STONE), "The lake's wall was dug at z " + z);
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.COBBLESTONE) >= 3, "No stone: " + report(c) + " " + c.getLastTaskResult());
        });
    }

    private static void pondHeldBehind(GameTestHelper h, BlockState touching) {
        CompanionEntity c = flat(h);
        box(h, 20, 1, 10, 25, 4, 22, Blocks.STONE);
        box(h, 21, 1, 14, 24, 3, 18, Blocks.WATER);
        BlockPos ore = new BlockPos(20, 1, 16);
        h.setBlock(ore, Blocks.IRON_ORE);
        h.setBlock(new BlockPos(21, 1, 16), touching);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        boolean[] wet = {false};
        h.onEachTick(() -> {
            for (int x = 10; x <= 20; x++) for (int y = 1; y <= 3; y++) for (int z = 8; z <= 24; z++) wet[0] |= !h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, y, z))).isEmpty();
        });
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(!wet[0], "The pond was let out: " + report(c));
            h.assertTrue(!c.didLastTaskSucceed() && h.getBlockState(ore).is(Blocks.IRON_ORE) && c.getLastTaskResult().contains("не нашёл безопасного хода"), report(c) + " " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.COBBLESTONE) == 16, "A plug was spent: " + count(c, Items.COBBLESTONE));
        });
    }
}

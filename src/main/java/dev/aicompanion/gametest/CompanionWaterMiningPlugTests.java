package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.support.WaterMiningKit.*;

/** A spring behind the ore: when it is plugged, with what, and when the ore is left instead. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWaterMiningPlugTests {
    private CompanionWaterMiningPlugTests() { }

    /**
     * Game 30.10: iron at his feet in a wall, a spring behind the block over it and nothing to plug with. That block is only the
     * player's 1x2 for comfort: it is not opened (the spring stays), and the ore goes as on main, from the one-high pocket.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void aSpringBehindTheBlockOverAnOreAtTheFeetDoesNotCostTheOre(GameTestHelper h) {
        CompanionEntity c = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16), spring = new BlockPos(21, 2, 16);
        h.setBlock(ore, Blocks.IRON_ORE); h.setBlock(spring, Blocks.WATER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c));
            h.assertTrue(h.getBlockState(spring).is(Blocks.WATER) && h.getBlockState(ore.above()).is(Blocks.STONE), "The spring was opened: " + report(c));
        });
    }

    /**
     * Game 30.10: a vein of two irons in a wall, a water source behind it and one over the second ore. Each cell he opens lets water in,
     * and he plugs it at once with a block from the bag like a player (lava first, then water): both ores are mined, the vein stays dry,
     * every plug is a real block that was a water cell, the dirt is not spent on climbing out of a flood.
     */
    @GameTest(template = "large_search", timeoutTicks = 1800)
    public static void aVeinWithWaterBehindItIsPluggedAndMinedWithTheBagKept(GameTestHelper h) {
        BlockPos[] water = veinWithWater(h);
        CompanionEntity c = veinMiner(h);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 16)); c.setItem(1, new ItemStack(Items.DIRT, 1));
        boolean[] wet = {false};
        h.onEachTick(() -> { if (h.getTick() > 400) for (int x = 17; x <= 18; x++) wet[0] |= !h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, 2, 15))).isEmpty(); });
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 2).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 10 && !c.isGatheringTask() && !c.isWorking(), "Work still active: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 2, report(c));
            int plugged = 0;
            for (BlockPos cell : water) plugged += h.getBlockState(cell).is(Blocks.COBBLESTONE) ? 1 : 0;
            h.assertTrue(plugged == water.length && count(c, Items.COBBLESTONE) >= 16 - plugged, plugged + " of " + water.length + " water cells plugged, cobblestone " + count(c, Items.COBBLESTONE) + ": " + report(c));
            h.assertTrue(!wet[0], "Water ran into the opened vein: " + report(c));
            h.assertTrue(count(c, Items.DIRT) == 1, "Dirt spent: " + report(c));
            h.assertTrue(c.getHealth() >= c.getMaxHealth(), "Hurt: " + c.getHealth());
        });
    }

    /**
     * The same vein and nothing to plug with: a spring is handled like lava. The first iron has no spring of its own and goes from
     * the pocket (the block over it, beside a spring, is left); the second, with the springs in its wall, is not opened, the reason is
     * the spring, nothing of the bag is put into the water, the water is not let out.
     */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void aVeinWithWaterBehindItAndNoPlugsEndsHonestlyAndSpendsNothing(GameTestHelper h) {
        BlockPos[] water = veinWithWater(h);
        CompanionEntity c = veinMiner(h);
        c.setItem(0, new ItemStack(Items.SAND, 5));
        // The stone he digs gives cobblestone: taken out of the bag as it comes, so that he really has nothing to plug with.
        h.onEachTick(() -> { for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.COBBLESTONE)) c.setItem(slot, ItemStack.EMPTY); });
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 2).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 10 && !c.isGatheringTask() && !c.isWorking(), "Work still active after " + h.getTick() + ": " + c.getLastTaskResult());
            h.assertTrue(!c.didLastTaskSucceed() && c.getLastTaskResult().contains("рядом родник, заткнуть нечем") && !c.getLastTaskResult().contains("Залило"), "Not the spring as the reason: " + report(c));
            h.assertTrue(!h.getBlockState(new BlockPos(17, 2, 15)).is(Blocks.IRON_ORE) && count(c, Items.RAW_IRON) == 1, "The dry first iron was not taken: " + report(c));
            h.assertTrue(h.getBlockState(new BlockPos(18, 2, 15)).is(Blocks.IRON_ORE), "The iron of the springs was opened: " + report(c));
            for (BlockPos cell : water) h.assertTrue(h.getBlockState(cell).is(Blocks.WATER), "The spring was let out: " + report(c));
            h.assertTrue(count(c, Items.SAND) == 5 && count(c, Items.DIRT) == 0, "The bag was spent: " + report(c));
        });
    }

    /**
     * No plugs at all, the first iron is a wall spring's, five dry irons stand free on the floor far away. The spring's ore is left and
     * named as the reason, the water is not let out, the five dry ones are mined and nothing but their drops is in the bag.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aSpringAtTheFirstOreWithNoPlugsIsLeftAndTheDryFiveAreTaken(GameTestHelper h) {
        clear(h);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 20, 1, 10, 23, 3, 22, Blocks.STONE);
        BlockPos spring = new BlockPos(20, 1, 16), water = new BlockPos(21, 1, 16);
        h.setBlock(spring, Blocks.IRON_ORE); h.setBlock(spring.above(), Blocks.AIR); h.setBlock(water, Blocks.WATER);
        for (BlockPos dry : new BlockPos[]{new BlockPos(6, 1, 10), new BlockPos(6, 1, 22), new BlockPos(9, 1, 6), new BlockPos(9, 1, 26), new BlockPos(4, 1, 16)})
            h.setBlock(dry, Blocks.IRON_ORE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        bind(h, c, 13, 1, 13);
        c.setLifestyle(false, false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 6).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(!c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 5 && c.getLastTaskResult().contains("рядом родник, заткнуть нечем") && !c.getLastTaskResult().contains("Залило"),
                    "Not five of six with the spring as the reason: " + c.getLastTaskResult());
            h.assertTrue(h.getBlockState(spring).is(Blocks.IRON_ORE) && h.getBlockState(water).is(Blocks.WATER), "The spring's ore was opened: " + h.getBlockState(spring));
            h.assertTrue(count(c, Items.COBBLESTONE) == 0 && count(c, Items.DIRT) == 0, "The bag holds more than the drops: " + c.getStoredItemsSummary());
        });
    }

    /**
     * Review 3: a chest and a slab of the player, waterlogged, beside the ore: only a block that is water itself is a spring; they stay,
     * the diamonds in the chest too. Review 6: their water runs into the opened ore like any pond's, so the ore is left, as on main.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void waterloggedBlocksBesideTheOreAreNeverReplacedByAPlug(GameTestHelper h) {
        clear(h);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 20, 1, 10, 23, 3, 22, Blocks.STONE);
        BlockPos ore = new BlockPos(20, 1, 16), chest = new BlockPos(20, 1, 17), slab = new BlockPos(20, 1, 15);
        h.setBlock(ore, Blocks.IRON_ORE); h.setBlock(ore.above(), Blocks.AIR);
        h.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST).setValue(ChestBlock.WATERLOGGED, true));
        ((ChestBlockEntity) h.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 5));
        h.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true));
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        bind(h, c, 13, 1, 13);
        c.setLifestyle(false, false);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        h.runAfterDelay(20, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 22 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(!c.didLastTaskSucceed() && c.getLastTaskResult().contains("не нашёл безопасного хода") && h.getBlockState(ore).is(Blocks.IRON_ORE), report(c) + " " + c.getLastTaskResult());
            h.assertTrue(h.getBlockState(chest).is(Blocks.CHEST) && h.getBlockState(slab).is(Blocks.OAK_SLAB), "Replaced: " + h.getBlockState(chest) + " " + h.getBlockState(slab));
            h.assertTrue(h.getBlockEntity(chest) instanceof ChestBlockEntity box && box.getItem(0).is(Items.DIAMOND) && box.getItem(0).getCount() == 5, "The chest lost its diamonds");
            h.assertTrue(count(c, Items.COBBLESTONE) == 3, "A plug was spent: " + count(c, Items.COBBLESTONE));
        });
    }

    /**
     * Review 4: the spring's ore and a cobblestone of his that lies in a crack he cannot reach (sealed in bedrock), no plugs in the bag: five
     * of the six irons are his, and the reason is the spring, never «залило».
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aSpringAndADropOutOfReachEndWithTheSpringNamed(GameTestHelper h) {
        clear(h);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 20, 1, 10, 23, 3, 22, Blocks.STONE);
        BlockPos first = new BlockPos(20, 1, 16);
        h.setBlock(first, Blocks.IRON_ORE); h.setBlock(first.above(), Blocks.AIR); h.setBlock(new BlockPos(21, 1, 16), Blocks.WATER);
        for (BlockPos dry : new BlockPos[]{new BlockPos(6, 1, 10), new BlockPos(6, 1, 22), new BlockPos(9, 1, 6), new BlockPos(9, 1, 26), new BlockPos(4, 1, 16)})
            h.setBlock(dry, Blocks.IRON_ORE);
        box(h, 12, 1, 18, 14, 3, 20, Blocks.BEDROCK);
        h.setBlock(new BlockPos(13, 2, 19), Blocks.AIR);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        bind(h, c, 13, 1, 13);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        Vec3 in = h.absoluteVec(new Vec3(13.5, 2.0, 19.5));
        ItemEntity stuck = new ItemEntity(h.getLevel(), in.x, in.y, in.z, new ItemStack(Items.COBBLESTONE));
        stuck.getPersistentData().putUUID("aicompanion:harvester", c.getUUID());
        stuck.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(stuck);
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 6).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            String said = c.getLastTaskResult();
            h.assertTrue(!said.contains("Залило") && (c.didLastTaskSucceed() ? count(c, Items.RAW_IRON) == 6
                    : count(c, Items.RAW_IRON) == 5 && said.contains("рядом родник, заткнуть нечем")), "Neither six of six nor five with the spring: " + said);
            h.assertTrue(h.getBlockState(new BlockPos(21, 1, 16)).is(Blocks.WATER), "The spring was let out");
        });
    }

    /**
     * Review 5, finding 1b: nothing to plug with, one iron behind eight cells of stone (the digging gives cobblestone), the other at a
     * closed spring. The spring is judged when its ore is opened, not when the ore is picked: the cobblestone of the way plugs it, both are taken.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aSpringAtOneOreIsPluggedWithTheCobblestoneOfTheWayToTheOther(GameTestHelper h) {
        clear(h);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 20, 1, 8, 27, 4, 24, Blocks.STONE);
        BlockPos buried = new BlockPos(24, 1, 12), byWater = new BlockPos(20, 1, 20), spring = new BlockPos(21, 1, 20);
        h.setBlock(buried, Blocks.IRON_ORE); h.setBlock(byWater, Blocks.IRON_ORE); h.setBlock(spring, Blocks.WATER);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 14, 1, 16);
        bind(h, c, 10, 1, 16);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 2).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 2, report(c));
            h.assertTrue(h.getBlockState(spring).is(Blocks.COBBLESTONE), "The spring was not plugged: " + report(c));
        });
    }

    /**
     * Review 5, finding 3: iron at his feet in a wall, two sources in a row behind the block over it (open water: no plug holds it) and
     * sixteen cobblestones in the bag. The block over the ore is only the player's 1x2: it is not opened, the ore goes from the pocket.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void openWaterBehindTheBlockOverAnOreAtTheFeetIsNotLetIn(GameTestHelper h) {
        CompanionEntity c = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16), near = new BlockPos(21, 2, 16), far = new BlockPos(22, 2, 16);
        h.setBlock(ore, Blocks.IRON_ORE); h.setBlock(near, Blocks.WATER); h.setBlock(far, Blocks.WATER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c));
            h.assertTrue(h.getBlockState(near).is(Blocks.WATER) && h.getBlockState(far).is(Blocks.WATER) && h.getBlockState(ore.above()).is(Blocks.STONE), "The water was let in: " + report(c));
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(ore)).isEmpty() && count(c, Items.COBBLESTONE) >= 16, "The pocket is wet or plugs went: " + report(c));
        });
    }

    /** Stone wall x 17..21 with two irons at z 15, a source behind them (x 19) and a source over the second one. */
    private static BlockPos[] veinWithWater(GameTestHelper h) {
        box(h, 0, 1, 0, 30, 1, 30, Blocks.STONE);
        box(h, 17, 2, 10, 21, 6, 20, Blocks.STONE);
        h.setBlock(new BlockPos(17, 2, 15), Blocks.IRON_ORE); h.setBlock(new BlockPos(18, 2, 15), Blocks.IRON_ORE);
        BlockPos[] water = {new BlockPos(19, 2, 15), new BlockPos(18, 3, 15)};
        for (BlockPos cell : water) h.setBlock(cell, Blocks.WATER);
        return water;
    }

    private static CompanionEntity veinMiner(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 13.5f, 2, 10.5f);
        bind(h, c, 10, 2, 10);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        return c;
    }
}

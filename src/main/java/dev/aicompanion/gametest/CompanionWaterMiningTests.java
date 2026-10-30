package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Water does not scare him at work, as it does not scare a player: ore and stone at the water, a tunnel through it, drops that float. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWaterMiningTests {
    private static final int SURFACE = 6; // feet level above the top layer of the buried arena

    private CompanionWaterMiningTests() { }

    /** Iron with a water source right behind it in the wall and a plug in the bag: the spring is plugged as the ore goes, the raw iron is in his bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void oreBesideWaterIsMinedAndPickedUp(GameTestHelper h) {
        CompanionEntity c = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.IRON_ORE);
        h.setBlock(new BlockPos(21, 1, 16), Blocks.WATER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c));
            h.assertTrue(!h.getBlockState(ore).is(Blocks.IRON_ORE), "Ore still there");
            h.assertTrue(c.isAlive() && c.getHealth() >= c.getMaxHealth(), "Hurt: " + c.getHealth());
        });
    }

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
     * Iron buried in the ground, a player's stairs down to it; the last cell of the stairs, beside the ore, is a pocket of water
     * (his tunnel goes through it, a cell flooded at the floor is open) and more water lies along it (springs in the wall: he has
     * plugs for them). He digs the stairs, stands in the water to the knee, mines the ore, and climbs back up the stairs he made.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void tunnelThroughAndAlongWaterReachesTheOreAndBack(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y < SURFACE; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? Blocks.BEDROCK
                    : y <= 3 ? Blocks.STONE : y == SURFACE - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, SURFACE, 16);
        bind(h, c, 13, SURFACE, 13);
        BlockPos ore = new BlockPos(16, 3, 23); h.setBlock(ore, Blocks.IRON_ORE);
        h.setBlock(new BlockPos(16, 3, 22), Blocks.WATER);
        h.setBlock(new BlockPos(17, 3, 21), Blocks.WATER);
        h.setBlock(new BlockPos(15, 3, 21), Blocks.WATER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c));
            h.assertTrue(!h.getBlockState(ore).is(Blocks.IRON_ORE), "Ore still there");
            h.assertTrue(c.isAlive() && c.getHealth() >= c.getMaxHealth(), "Hurt: " + c.getHealth());
            h.assertTrue(c.getY() >= h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() - .01, "Did not climb back out: " + h.relativePos(c.blockPosition()).toShortString());
        });
    }

    /**
     * A stone island, two high, in a pool one block deep: the top layer is dry, the lower one is washed by the water, and what he
     * mines from it floats. All eight stones are mined and picked up, those that lay in the water too.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void stoneAtTheWaterIsMinedAndPickedUp(GameTestHelper h) {
        CompanionEntity c = flat(h);
        box(h, 13, 1, 13, 23, 1, 21, Blocks.STONE_BRICKS);
        box(h, 14, 1, 14, 22, 1, 20, Blocks.WATER);
        box(h, 18, 1, 16, 19, 2, 17, Blocks.STONE);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("gather_stone", "", 8).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.COBBLESTONE) >= 8, report(c) + " " + c.getLastTaskResult());
            h.assertTrue(c.isAlive() && c.getHealth() >= c.getMaxHealth(), "Hurt: " + c.getHealth());
        });
    }

    /** Lava beside the ore and no plug in the bag: refused; the reason names lava only. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void lavaBesideTheOreIsStillRefusedAndTheReasonIsLavaOnly(GameTestHelper h) {
        CompanionEntity c = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.IRON_ORE);
        h.setBlock(new BlockPos(21, 1, 16), Blocks.LAVA);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            String said = c.getLastTaskResult();
            h.assertTrue(!c.didLastTaskSucceed() && said.contains("рядом лава") && !said.contains("вода"), said);
            h.assertTrue(h.getBlockState(ore).is(Blocks.IRON_ORE), "Ore dug");
        });
    }

    /**
     * Fourteen blocks of a flooded tunnel with a ceiling between him and the player, his lungs full: the search for the way
     * cannot start from a cell with no room for the head, so the way to the player is the swim, not a refusal.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void aRouteStartsInsideAFloodedTunnelUnderACeiling(GameTestHelper h) {
        box(h, 0, 0, 14, 63, 0, 18, Blocks.BEDROCK);
        box(h, 0, 1, 14, 63, 7, 18, Blocks.STONE);
        box(h, 1, 2, 16, 6, 5, 16, Blocks.AIR);
        box(h, 7, 2, 16, 20, 3, 16, Blocks.WATER);
        box(h, 21, 2, 16, 40, 3, 16, Blocks.AIR);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 14, 2, 16);
        bind(h, c, 2, 2, 16);
        c.setLifestyle(false, false);
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 10 && !c.isWorking() && c.didLastTaskSucceed() && c.getX() < h.absoluteVec(new Vec3(6, 0, 0)).x,
                    "Did not come: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(c.getHealth() >= c.getMaxHealth(), "Drowned: " + c.getHealth());
        });
    }

    /**
     * He stands on a grass island in a pond one block deep, with no open stone anywhere: the stone is under the soil, so he digs his
     * own stairs down, and every way down goes under or through the pond. A player digs them on the dry bank: water would flood them.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void stoneStairsAreDugOnTheDryBankNotUnderAPond(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 12; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 || x == 0 || x == 31 || z == 0 || z == 31 ? (y <= 6 ? Blocks.BEDROCK : Blocks.AIR)
                    : y <= 3 ? Blocks.STONE : y == 4 ? Blocks.DIRT : y == 5 ? (x >= 10 && x <= 22 && z >= 10 && z <= 22 && !(x == 16 && z == 16) ? Blocks.WATER : Blocks.GRASS_BLOCK) : Blocks.AIR);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, 6, 16);
        bind(h, c, 16, 6, 4);
        c.setLifestyle(false, false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("gather_stone", "", 4).success(), "Stone order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 10 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.COBBLESTONE) >= 4, "No stone: " + c.getLastTaskResult() + " | " + c.getStoredItemsSummary());
            h.assertTrue(c.getHealth() >= c.getMaxHealth(), "Hurt: " + c.getHealth());
        });
    }

    /**
     * The branch's own aRouteStartsInsideAFloodedTunnelUnderACeiling with his air kept full, so that no trip for a breath carries him:
     * the float of the water must not take the jump from the work goal, or the order stands and nothing ticks.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void aRouteThroughAFloodedTunnelIsWalkedWithAFullBreathToo(GameTestHelper h) {
        box(h, 0, 0, 14, 63, 0, 18, Blocks.BEDROCK);
        box(h, 0, 1, 14, 63, 7, 18, Blocks.STONE);
        box(h, 1, 2, 16, 6, 5, 16, Blocks.AIR);
        box(h, 7, 2, 16, 20, 3, 16, Blocks.WATER);
        box(h, 21, 2, 16, 40, 3, 16, Blocks.AIR);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 14, 2, 16);
        bind(h, c, 2, 2, 16);
        c.setLifestyle(false, false);
        h.onEachTick(() -> { if (c.getAirSupply() < c.getMaxAirSupply()) c.setAirSupply(c.getMaxAirSupply()); });
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted"));
        h.succeedWhen(() -> h.assertTrue(h.getTick() > 10 && !c.isWorking() && c.didLastTaskSucceed() && c.getX() < h.absoluteVec(new Vec3(6, 0, 0)).x,
                "Did not come with a full breath: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString()));
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
     * Review 3: a pocket of water lies on the line of his tunnel (open for him to swim, a cell of his way, not a spring): never plugged.
     * A plug across the line shuts the way he is digging along; the work ends either way, and whether the iron behind the flowing water
     * is reached is the tunnel's own business (it was not on 04cbab6 either).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aFloodedCellOnTheLineOfHisTunnelIsNeverPlugged(GameTestHelper h) {
        clear(h);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 1, 31, Blocks.STONE);
        box(h, 11, 2, 0, 31, 7, 31, Blocks.STONE);
        BlockPos ore = new BlockPos(16, 2, 16), pocket = new BlockPos(13, 2, 16);
        h.setBlock(ore, Blocks.IRON_ORE); h.setBlock(pocket, Blocks.WATER);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 7, 2, 16);
        bind(h, c, 4, 2, 16);
        c.setLifestyle(false, false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        boolean[] plugged = {false};
        h.onEachTick(() -> plugged[0] |= h.getBlockState(pocket).is(Blocks.COBBLESTONE));
        h.runAfterDelay(20, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 22 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(!plugged[0], "The water on his way was plugged");
            h.assertTrue(count(c, Items.RAW_IRON) == 1 ? c.didLastTaskSucceed() && !h.getBlockState(ore).is(Blocks.IRON_ORE) : h.getBlockState(ore).is(Blocks.IRON_ORE) && !c.didLastTaskSucceed(),
                    "Said otherwise than the ore and the bag say: " + c.getLastTaskResult());
        });
    }

    /** Review 3: iron in the side wall of a flooded corridor one wide: the corridor cell in front of it is the way, not a spring: it stays water. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void anOreInTheWallOfAFloodedCorridorLeavesTheCorridorWater(GameTestHelper h) {
        clear(h);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 12, 31, 4, 20, Blocks.STONE);
        box(h, 2, 1, 16, 29, 2, 16, Blocks.AIR);
        box(h, 2, 1, 16, 29, 1, 16, Blocks.WATER);
        BlockPos ore = new BlockPos(14, 1, 17);
        h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 16);
        bind(h, c, 27, 1, 16);
        c.setLifestyle(false, false);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        int[] plugs = {0};
        h.onEachTick(() -> { plugs[0] = 0; for (int x = 2; x <= 29; x++) for (int y = 1; y <= 2; y++) if (h.getBlockState(new BlockPos(x, y, 16)).is(Blocks.COBBLESTONE)) plugs[0]++; });
        h.runAfterDelay(20, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 22 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c) + " " + c.getLastTaskResult());
            h.assertTrue(plugs[0] == 0 && h.getBlockState(new BlockPos(14, 1, 16)).is(Blocks.WATER), "The corridor was plugged: " + plugs[0]);
        });
    }

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

    /** Review 3: a chest and a slab of the player, waterlogged, beside the ore: only a block that is water itself is a spring; they stay, the diamonds in the chest too. */
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
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c) + " " + c.getLastTaskResult());
            h.assertTrue(h.getBlockState(chest).is(Blocks.CHEST) && h.getBlockState(slab).is(Blocks.OAK_SLAB), "Replaced: " + h.getBlockState(chest) + " " + h.getBlockState(slab));
            h.assertTrue(h.getBlockEntity(chest) instanceof ChestBlockEntity box && box.getItem(0).is(Items.DIAMOND) && box.getItem(0).getCount() == 5, "The chest lost its diamonds");
            h.assertTrue(count(c, Items.COBBLESTONE) == 3, "A plug was spent: " + count(c, Items.COBBLESTONE));
        });
    }

    /**
     * Review 4: a closed spring over the next step of his stairs touches two cells of his own way. It is a spring all the same: plugged when
     * the cell beside it is opened, the ore taken, the water kept out of the stairs.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aSpringInTheCeilingOfHisStairsIsPluggedAndTheOreTaken(GameTestHelper h) {
        clear(h);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 12, 31, Blocks.STONE);
        BlockPos ore = new BlockPos(16, 7, 26), spring = new BlockPos(16, 11, 24);
        h.setBlock(ore, Blocks.IRON_ORE); h.setBlock(spring, Blocks.WATER);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, 13, 13);
        bind(h, c, 13, 13, 11);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        h.runAfterDelay(20, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 22 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c) + " " + c.getLastTaskResult());
            h.assertTrue(h.getBlockState(spring).is(Blocks.COBBLESTONE), "The spring in the ceiling was not plugged: " + h.getBlockState(spring));
            int wet = 0;
            for (int z = 19; z <= 26; z++) for (int y = 7; y <= 12; y++) if (!h.getLevel().getFluidState(h.absolutePos(new BlockPos(16, y, z))).isEmpty()) wet++;
            h.assertTrue(wet == 0, wet + " wet cells on the stairs");
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

    /** Review 4: a Silk Touch pickaxe brings the ore block itself: that is a mined ore, not «выпавшее не достал». */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void anOreTakenWithSilkTouchIsDone(GameTestHelper h) {
        CompanionEntity c = flat(h);
        BlockPos ore = new BlockPos(20, 1, 16);
        h.setBlock(ore, Blocks.IRON_ORE);
        c.setFollowing(false);
        ItemStack pick = new ItemStack(Items.STONE_PICKAXE);
        pick.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        c.setItemSlot(EquipmentSlot.MAINHAND, pick);
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.IRON_ORE) == 1 && !c.getLastTaskResult().contains("Выпавшее не достал"), report(c) + " " + c.getLastTaskResult());
        });
    }

    /** Review 4: Nether gold ore (in the gold ore tag) drops nuggets, not raw gold: the nuggets in the bag are the ore mined. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void aNetherGoldOreGivingNuggetsIsDone(GameTestHelper h) {
        CompanionEntity c = flat(h);
        BlockPos ore = new BlockPos(20, 1, 16);
        h.setBlock(ore, Blocks.NETHER_GOLD_ORE);
        c.setFollowing(false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:gold_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 12 && !c.isGatheringTask() && !c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.GOLD_NUGGET) > 0 && !c.getLastTaskResult().contains("Выпавшее не достал"), report(c) + " " + c.getLastTaskResult());
        });
    }

    private static void clear(GameTestHelper h) { box(h, 0, 1, 0, 31, 23, 31, Blocks.AIR); }

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

    private static CompanionEntity flat(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        bind(h, c, 13, 1, 13);
        return c;
    }
    /** A stone wall three high across the arena at x=20..23, facing Oleg. */
    private static void wall(GameTestHelper h) { box(h, 20, 1, 10, 23, 3, 22, Blocks.STONE); }
    private static void bind(GameTestHelper h, CompanionEntity c, int x, int y, int z) {
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(new BlockPos(x, y, z));
        owner.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
    }
    private static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(new BlockPos(x, y, z), block);
    }
    private static String report(CompanionEntity c) { return c.getDeedsForAi() + " | bag: " + c.getStoredItemsSummary(); }
    private static void idle(GameTestHelper h, CompanionEntity c) {
        h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "Work still active: " + c.getLastTaskResult());
    }
    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }
}

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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Water does not scare him at work, as it does not scare a player: ore and stone at the water, a tunnel through it, drops that float. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWaterMiningTests {
    private static final int SURFACE = 6; // feet level above the top layer of the buried arena

    private CompanionWaterMiningTests() { }

    /** Iron with a water source right behind it in the wall: mined, the raw iron (floating in the water that flowed out) is in his bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void oreBesideWaterIsMinedAndPickedUp(GameTestHelper h) {
        CompanionEntity c = flat(h);
        wall(h);
        BlockPos ore = new BlockPos(20, 1, 16); h.setBlock(ore, Blocks.IRON_ORE);
        h.setBlock(new BlockPos(21, 1, 16), Blocks.WATER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            idle(h, c);
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c));
            h.assertTrue(!h.getBlockState(ore).is(Blocks.IRON_ORE), "Ore still there");
            h.assertTrue(c.isAlive() && c.getHealth() >= c.getMaxHealth(), "Hurt: " + c.getHealth());
        });
    }

    /**
     * Iron buried in the ground, a player's stairs down to it; the last cell of the stairs, beside the ore, is a pocket of water
     * (his tunnel goes through it, a cell flooded at the floor is open) and more water lies along it. He digs the stairs, stands in
     * the water to the knee, mines the ore, and climbs back up the stairs he made.
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
     * The same vein and nothing to plug with: he mines what the water lets him, and the work ends, with the ore or with the reason
     * «залило водой», not hanging in a pickup that never ends; nothing of the bag is put into the water.
     */
    @GameTest(template = "large_search", timeoutTicks = 2400)
    public static void aVeinWithWaterBehindItAndNoPlugsEndsHonestlyAndSpendsNothing(GameTestHelper h) {
        veinWithWater(h);
        CompanionEntity c = veinMiner(h);
        c.setItem(0, new ItemStack(Items.SAND, 5));
        // The stone he digs gives cobblestone: taken out of the bag as it comes, so that he really has nothing to plug with.
        h.onEachTick(() -> { for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.COBBLESTONE)) c.setItem(slot, ItemStack.EMPTY); });
        h.runAfterDelay(10, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 2).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 10 && !c.isGatheringTask() && !c.isWorking(), "Work still active after " + h.getTick() + ": " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() || c.getLastTaskResult().contains("Ход залило водой, заткнуть нечем"), "Neither the ore nor the reason: " + report(c));
            h.assertTrue(count(c, Items.SAND) == 5 && count(c, Items.DIRT) == 0, "The bag was spent: " + report(c));
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

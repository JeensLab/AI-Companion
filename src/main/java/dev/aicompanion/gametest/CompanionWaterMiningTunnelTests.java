package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.aicompanion.gametest.support.WaterMiningKit.*;

/** Flooded tunnels, corridors and stairs: the way through water and which cells are never plugged. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWaterMiningTunnelTests {
    private CompanionWaterMiningTunnelTests() { }

    /**
     * Iron buried in the ground, a player's stairs down to it; the last cell of the stairs, beside the ore, is a pocket of water
     * (his tunnel goes through it, a cell flooded at the floor is open) and more water lies along it (springs in the wall: the cobblestone of the
     * stairs he digs plugs them). He digs the stairs, stands in the water to the knee, mines the ore, and climbs back up the stairs he made.
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
     * Fourteen blocks of a flooded tunnel with a ceiling between him and the player, his lungs full: the search for the way
     * cannot start from a cell with no room for the head, so the way to the player is the swim, not a refusal.
     */
    @GameTest(template = "large_search", timeoutTicks = 1500)
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
    @GameTest(template = "large_search", timeoutTicks = 1500)
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
}

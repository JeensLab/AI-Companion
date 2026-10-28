package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionDiagnostics;
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
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.List;

/**
 * The single route (ticket 04): ore mining walks to its ore and out of the mine by it. An ore ten blocks off by the straight
 * line whose only way is a detour longer than one search is not «no safe way»: it is far, and he goes there in legs.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSingleRouteTests {
    private static final int SURFACE = 19;

    /**
     * Bedrock all through with a one-wide corridor winding back and forth (nothing to dig, no short cut): about 60 cells to an
     * iron ore in the end wall that is thirteen blocks from the start by the straight line, inside the scan of the order.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void oreBeyondOneSearchIsWalkedToInLegs(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 7, 31, Blocks.BEDROCK);
        box(h, 4, 2, 4, 24, 3, 4, Blocks.AIR);
        box(h, 24, 2, 5, 24, 3, 8, Blocks.AIR);
        box(h, 4, 2, 8, 24, 3, 8, Blocks.AIR);
        box(h, 4, 2, 9, 4, 3, 12, Blocks.AIR);
        box(h, 4, 2, 12, 13, 3, 12, Blocks.AIR);
        BlockPos ore = new BlockPos(14, 2, 12); h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = spawn(h, 4, 2, 4);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1 && h.getBlockState(ore).isAir(), c.getLastTaskResult());
            h.assertTrue(routeEvents(c).stream().anyMatch(line -> line.contains("\"activity\":\"ORE\"")), "No route_outcome of the ore walk in the journal");
        });
    }

    /**
     * A mine of 59 cells, 17 blocks deep, winding over four rows (the way out is longer than one search). Iron in the wall at its
     * bottom; the entrance is his exit. After the ore he walks up his own corridor to the entrance, in legs, and says nothing of
     * being below; no stone is dug on the way.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void climbsOutOfALongMineToItsEntranceInLegs(GameTestHelper h) throws Exception {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, SURFACE - 1, 31, Blocks.STONE);
        box(h, 0, SURFACE, 0, 31, SURFACE, 31, Blocks.GRASS_BLOCK);
        List<int[]> cells = new java.util.ArrayList<>();
        for (int row = 0; row < 4; row++) {
            int z = 6 + 2 * row;
            for (int k = 0; k < 14; k++) cells.add(new int[]{row % 2 == 0 ? 4 + k : 17 - k, z});
            if (row < 3) cells.add(new int[]{row % 2 == 0 ? 17 : 4, z + 1});
        }
        for (int i = 0; i < cells.size(); i++) {
            int floor = Math.max(2, SURFACE - i / 3), x = cells.get(i)[0], z = cells.get(i)[1];
            box(h, x, floor, z, x, floor, z, floor == SURFACE ? Blocks.GRASS_BLOCK : Blocks.STONE);
            box(h, x, floor + 1, z, x, floor + 3, z, Blocks.AIR);
        }
        int[] bottom = cells.get(cells.size() - 1);
        BlockPos ore = new BlockPos(bottom[0] - 1, 3, bottom[1]); h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = spawn(h, bottom[0], 3, bottom[1]);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted");
        BlockPos entrance = h.absolutePos(new BlockPos(cells.get(0)[0], SURFACE + 1, cells.get(0)[1]));
        Object work = field(c, "work");
        var exit = work.getClass().getDeclaredField("oreExit"); exit.setAccessible(true); exit.set(work, entrance);
        int[] solid = {solidCount(h)};
        // After the work he follows the player, who stands at the bottom: what counts is how high he got.
        double[] highest = {c.getY()};
        h.onEachTick(() -> highest[0] = Math.max(highest[0], c.getY()));
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, c.getLastTaskResult());
            h.assertTrue(highest[0] >= entrance.getY() - .5 && !c.getLastTaskResult().contains("Наверх пока не выбрался"), "Still below: " + c.position() + " vs " + entrance + " " + c.getLastTaskResult());
            h.assertTrue(solidCount(h) >= solid[0] - 1, "Dug on the way up: " + (solid[0] - solidCount(h)) + " blocks");
            h.assertTrue(routeEvents(c).stream().anyMatch(line -> line.contains("\"activity\":\"ORE\"") && line.contains("\"outcome\":\"NEAR\"")), "No route_outcome of the climb");
        });
    }

    /**
     * Ticket 05: stone, a trunk, dirt and a drop that lie thirteen blocks off by the straight line, inside the scan, behind a
     * corridor of about 60 cells (bedrock all round, nothing to dig): the way is longer than one search, so they are far, not lost.
     */
    private static BlockPos labyrinth(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 23, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 7, 31, Blocks.BEDROCK);
        box(h, 4, 2, 4, 24, 3, 4, Blocks.AIR);
        box(h, 24, 2, 5, 24, 3, 8, Blocks.AIR);
        box(h, 4, 2, 8, 24, 3, 8, Blocks.AIR);
        box(h, 4, 2, 9, 4, 3, 12, Blocks.AIR);
        box(h, 4, 2, 12, 13, 3, 12, Blocks.AIR);
        return new BlockPos(14, 2, 12);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void stoneBeyondOneSearchIsWalkedTo(GameTestHelper h) {
        BlockPos stone = labyrinth(h); h.setBlock(stone, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 2, 4);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        h.assertTrue(c.startWork("gather_stone", "", 1).success(), "Stone order not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.COBBLESTONE) == 1 && h.getBlockState(stone).isAir(), c.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void trunkBeyondOneSearchIsWalkedTo(GameTestHelper h) {
        BlockPos trunk = labyrinth(h); h.setBlock(trunk, Blocks.OAK_LOG);
        CompanionEntity c = spawn(h, 4, 2, 4);
        c.startGatheringLogs(1);
        h.succeedWhen(() -> {
            h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "Still gathering: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.OAK_LOG) == 1 && h.getBlockState(trunk).isAir(), c.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void dirtBeyondOneSearchIsWalkedTo(GameTestHelper h) throws Exception {
        BlockPos dirt = labyrinth(h); h.setBlock(dirt, Blocks.DIRT);
        box(h, 14, 3, 12, 14, 7, 12, Blocks.AIR); // soil is dug only with a free cover above it (CompanionQuarry.safeCell)
        CompanionEntity c = spawn(h, 4, 2, 4);
        var gather = CompanionEntity.class.getDeclaredMethod("startGatheringDirtForWork", int.class); gather.setAccessible(true); gather.invoke(c, 1);
        h.succeedWhen(() -> {
            h.assertTrue(!c.isGatheringTask(), "Still gathering: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.DIRT) == 1 && h.getBlockState(dirt).isAir(), c.getLastTaskResult());
        });
    }

    /** A drop of his harvest that came to rest at the far end of the corridor is picked up: the walk to it is in legs. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void dropBeyondOneSearchIsPickedUp(GameTestHelper h) throws Exception {
        BlockPos spot = labyrinth(h).west();
        CompanionEntity c = spawn(h, 4, 2, 4);
        Object loot = field(c, "loot");
        var drop = loot.getClass().getDeclaredMethod("drop", BlockPos.class, List.class); drop.setAccessible(true);
        drop.invoke(loot, h.absolutePos(spot), List.of(new ItemStack(Items.COBBLESTONE)));
        h.succeedWhen(() -> h.assertTrue(count(c, Items.COBBLESTONE) == 1, "Drop not picked up: " + c.getLastTaskResult()));
    }

    private static int solidCount(GameTestHelper h) {
        int n = 0;
        for (int x = 0; x < 32; x++) for (int y = 1; y < SURFACE; y++) for (int z = 0; z < 32; z++) if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE)) n++;
        return n;
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int y, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x, y, z));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        return c;
    }

    /** The journal lines of route_outcome written by this companion. */
    static List<String> routeEvents(CompanionEntity c) {
        CompanionDiagnostics.flush();
        try {
            return Files.readAllLines(FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion").resolve("companion-latest.jsonl")).stream()
                    .filter(line -> line.contains("\"route_outcome\"") && line.contains(c.getUUID().toString())).toList();
        } catch (java.io.IOException error) { return List.of(); }
    }

    private static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(new BlockPos(x, y, z), block);
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        return count;
    }
}

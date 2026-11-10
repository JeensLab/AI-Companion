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

/**
 * A diamond mine like a player's: a straight staircase down to diamond depth, a 2×2 tunnel, torches, caves met on the way.
 * A GameTest arena starts at Y -60, so diamond depth (feet at Y -58) is the real one: relative height 2.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDiamondMineTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8;
    private static final int DIAMOND_FEET = 2; // Y -58
    private static final int RING_FROM = 21; // diamonds under the tunnel floor, past the foot of a 19-step staircase

    /** Stone down to bedrock, diamonds only at depth: the staircase, the tunnel and the ore are all his own work. */
    @GameTest(template = "marathon_cave", timeoutTicks = 9000)
    public static void descendsByStairsAndMinesVisibleDiamond(GameTestHelper h) {
        mountain(h);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        bind(h, companion);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        companion.setItem(2, new ItemStack(Items.TORCH, 16));
        companion.setItem(3, new ItemStack(Items.BREAD, 8));
        h.assertTrue(companion.startWork("dig_for_diamonds", "", 1).success(), "Diamond mine not accepted: " + companion.getLastTaskResult());
        long id = companion.activeWorkId();
        int[] deepest = {GROUND + 1};
        h.onEachTick(() -> deepest[0] = Math.min(deepest[0], h.relativePos(companion.blockPosition()).getY()));
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
            var result = companion.workResult(id);
            h.assertTrue(result != null && count(companion, Items.DIAMOND) >= 1, "No diamond: " + report(companion));
            h.assertTrue(deepest[0] <= DIAMOND_FEET + 1, "Never reached diamond depth, deepest " + deepest[0]);
            h.assertTrue(tallestAirColumn(h) <= 4, "A vertical shaft instead of a staircase: " + tallestAirColumn(h));
            h.assertTrue(torches(h) >= 1, "No torch on the dark way down");
            h.assertTrue(count(companion, Items.IRON_PICKAXE) == 1 && damage(companion, Items.IRON_PICKAXE) <= 2,
                    "Iron pickaxe used on plain stone: " + damage(companion, Items.IRON_PICKAXE));
        });
    }

    /** Bedrock floor and walls, deepslate up to Y -52, stone, dirt, grass; deepslate diamond ore in a ring under the tunnel floor. */
    private static void mountain(GameTestHelper h) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            int ring = Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER));
            var block = y == 0 || edge ? Blocks.BEDROCK
                    : y == DIAMOND_FEET - 1 && ring >= RING_FROM ? Blocks.DEEPSLATE_DIAMOND_ORE
                    : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    private static int tallestAirColumn(GameTestHelper h) {
        int tallest = 0;
        for (int x = 1; x < SIZE - 1; x++) for (int z = 1; z < SIZE - 1; z++) {
            int run = 0;
            for (int y = 1; y < GROUND; y++) {
                run = h.getBlockState(new BlockPos(x, y, z)).isAir() ? run + 1 : 0;
                tallest = Math.max(tallest, run);
            }
        }
        return tallest;
    }

    private static int torches(GameTestHelper h) {
        int torches = 0;
        for (int x = 1; x < SIZE - 1; x++) for (int z = 1; z < SIZE - 1; z++) for (int y = 1; y <= GROUND; y++) {
            var state = h.getBlockState(new BlockPos(x, y, z));
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) torches++;
        }
        return torches;
    }

    private static void bind(GameTestHelper h, CompanionEntity companion) {
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
    }

    private static String report(CompanionEntity companion) { return companion.getDeedsForAi() + " | bag: " + companion.getStoredItemsSummary(); }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }

    private static int damage(CompanionEntity companion, Item item) {
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) return companion.getItem(slot).getDamageValue();
        return companion.getMainHandItem().is(item) ? companion.getMainHandItem().getDamageValue() : -1;
    }
}

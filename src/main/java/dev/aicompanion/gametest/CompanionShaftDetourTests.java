package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Where the staircase meets a hollow, a liquid pocket or gravel, it goes on instead of refusing: the floor is laid under the next
 * step, another turn is taken, and a relaunch after a refusal does not repeat the move that failed. Arena as in the other mine tests:
 * origin at Y -60, Oleg picks NORTH at (24,22,24), stand k is (24,22-k,24-k), diamond ore under the tunnel floor far from the middle.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionShaftDetourTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2, RING_FROM = 12, TIMEOUT = 12000;
    private static final String FLOOR_REFUSAL = "неустойчив";

    /** A 7x7x5 empty dungeon under the stairs: the steps that fall into it have no floor, he lays one block after another down to the dungeon's bottom. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void goesDownThroughAnEmptyDungeonUnderTheStairsLayingTheFloor(GameTestHelper h) {
        mountain(h);
        carve(h, 21, 27, 7, 11, 9, 15);
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(count(c, Items.DIAMOND) >= 1, "No diamond after the dungeon: " + report(c));
            h.assertTrue(!result.message().contains(FLOOR_REFUSAL), "Refused for the floor: " + report(c));
            for (int k = 12; k <= 14; k++) h.assertTrue(!h.getBlockState(under(k)).isAir(), "No floor under stand " + k + " at " + under(k).toShortString() + " | " + report(c));
        });
    }

    /** A cave 29 wide and ten high: the way down is nothing but air under every step for nine steps, and no turn offers any ground. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void crossesAWideCaveLayingTheFloorUnderEachStep(GameTestHelper h) {
        mountain(h);
        carve(h, 10, 38, 4, 13, 4, 22);
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(count(c, Items.DIAMOND) >= 1, "No diamond after the cave: " + report(c));
            h.assertTrue(!result.message().contains(FLOOR_REFUSAL), "Refused for the floor: " + report(c));
            h.assertTrue(!h.getBlockState(under(15)).isAir(), "No floor under stand 15 | " + report(c));
        });
    }

    /** Lava sits where the next step's floor and both turns' floors would be, hidden in the rock: he opens the step, lays a block over the lava and walks on without burning. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void laysABlockOverLavaUnderTheStairInsteadOfRefusing(GameTestHelper h) {
        mountain(h);
        BlockPos straight = under(12), west = new BlockPos(22, 9, 13), east = new BlockPos(26, 9, 13);
        for (BlockPos lava : new BlockPos[]{straight, west, east}) h.setBlock(lava, Blocks.LAVA);
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.onEachTick(() -> h.assertTrue(!c.isOnFire() && c.getRemainingFireTicks() <= 0 && !c.isInLava(), "Burning at " + c.blockPosition().toShortString() + " | " + report(c)));
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(count(c, Items.DIAMOND) >= 1, "No diamond past the lava: " + report(c));
            var state = h.getBlockState(straight);
            h.assertTrue(!state.isAir() && state.getFluidState().isEmpty(), "Lava under the stair not covered: " + state + " | " + report(c));
            h.assertTrue(result.success(), "Ended without success: " + report(c));
        });
    }

    /** Gravel over the head cell of the next step and over the first step along either turn: the turn is taken anyway and the next one after it. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void goesAroundGravelOverTheNextCell(GameTestHelper h) {
        mountain(h);
        for (BlockPos gravel : new BlockPos[]{new BlockPos(24, 13, 12), new BlockPos(22, 13, 13), new BlockPos(26, 13, 13)}) h.setBlock(gravel, Blocks.GRAVEL);
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(count(c, Items.DIAMOND) >= 1, "No diamond past the gravel: " + report(c));
            h.assertTrue(result.success(), "Ended without success: " + report(c));
        });
    }

    /**
     * Straight on is closed by gravel, the first turn (west, the left on a tie) leads into a pocket with two zombies and is plugged, so the
     * first launch ends. The relaunch must not take west again: east is free.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void takesAnotherMoveWhenRelaunchedAfterARefusal(GameTestHelper h) {
        mountain(h);
        h.setBlock(new BlockPos(24, 13, 12), Blocks.GRAVEL);
        carve(h, 20, 22, 10, 12, 12, 14);
        for (int x : new int[]{21, 22}) {
            Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(x, 10, 13));
            zombie.setNoAi(true);
            zombie.setPersistenceRequired();
        }
        CompanionEntity c = rig(h);
        long first = start(h, c, 1);
        boolean[] relaunched = {false};
        long[] second = {0};
        h.succeedWhen(() -> {
            if (!relaunched[0]) {
                CompanionActionResult refused = ended(h, c, first);
                h.assertTrue(!refused.success() && count(c, Items.DIAMOND) == 0, "SETUP: the first launch should end in a refusal at the pocket: " + report(c));
                relaunched[0] = true;
                second[0] = start(h, c, 1);
                h.fail("Relaunched, waiting for the second end");
            }
            CompanionActionResult result = ended(h, c, second[0]);
            h.assertTrue(count(c, Items.DIAMOND) >= 1 && result.success(), "The relaunch repeated the move that failed: " + report(c));
        });
    }

    private static BlockPos under(int step) { return new BlockPos(CENTER, GROUND - step, CENTER - step); }

    private static void carve(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    /** Bedrock floor and walls, deepslate up to Y -52, stone, dirt, grass; deepslate diamond ore in a ring under the tunnel floor. */
    private static void mountain(GameTestHelper h) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            int ring = Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER));
            Block block = y == 0 || edge ? Blocks.BEDROCK
                    : y == DIAMOND_FEET - 1 && ring >= RING_FROM ? Blocks.DEEPSLATE_DIAMOND_ORE
                    : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    private static CompanionEntity rig(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 16),
                new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64)};
        for (int slot = 0; slot < bag.length; slot++) companion.setItem(slot, bag[slot]);
        return companion;
    }

    private static long start(GameTestHelper h, CompanionEntity c, int count) {
        h.assertTrue(c.startWork("dig_for_diamonds", "", count).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
        return c.activeWorkId();
    }

    private static CompanionActionResult ended(GameTestHelper h, CompanionEntity c, long id) {
        h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "Work still active: " + c.getLastTaskResult());
        CompanionActionResult result = c.workResult(id);
        h.assertTrue(result != null, "No result for the order: " + report(c));
        return result;
    }

    private static String report(CompanionEntity companion) { return companion.getDeedsForAi() + " | " + companion.getLastTaskResult() + " | bag: " + companion.getStoredItemsSummary(); }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

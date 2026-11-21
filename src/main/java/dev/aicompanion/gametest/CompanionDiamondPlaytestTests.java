package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The diamond mine's tools and stone as a playtest found them wrong: a diamond pickaxe for rock, diamonds kept for the player, stone kept for pickaxes. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDiamondPlaytestTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DIAMOND_FEET = 2, TIMEOUT = 9000, CLIMB_TIMEOUT = 12000, PAST_GAP_BY = 220, WATCH = 300, STONE_KEPT = 9;
    private static final String NO_TOOL = "Копать нечем";

    /** No stone pickaxe and nothing to make one of: the diamond pickaxe digs the rock, the iron one stays for ore, and he does not go up for supplies. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void diamondPickaxeDigsTheRockInsteadOfAClimb(GameTestHelper h) {
        var r = CompanionDiamondGoalTests.rig(h, true,
                new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH, 16), new ItemStack(Items.BREAD, 8));
        CompanionEntity c = r.c();
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        h.assertTrue(c.startWork("dig_for_diamonds", "", 1).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        int[] deepest = {GROUND + 1};
        h.onEachTick(() -> {
            deepest[0] = Math.min(deepest[0], h.relativePos(c.blockPosition()).getY());
            h.assertTrue(!c.getLastTaskResult().contains(NO_TOOL), "Climbed for supplies with a diamond pickaxe in the bag: " + c.getLastTaskResult());
        });
        h.succeedWhen(() -> {
            h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "Work still active: " + c.getLastTaskResult());
            h.assertTrue(c.workResult(id) != null && CompanionDiamondGoalTests.count(c, Items.DIAMOND) >= 1, "No diamond: " + c.getLastTaskResult());
            h.assertTrue(deepest[0] <= DIAMOND_FEET + 1, "Never reached diamond depth, deepest " + deepest[0]);
            h.assertTrue(wear(c, Items.DIAMOND_PICKAXE) > 0, "The rock was not dug with the diamond pickaxe");
            h.assertTrue(wear(c, Items.IRON_PICKAXE) <= 3, "Iron pickaxe worn on rock: " + wear(c, Items.IRON_PICKAXE));
        });
    }

    /** The diamonds are the player's: with a find_diamonds goal outstanding, three diamonds and iron in the bag do not become a pickaxe at a bench. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void diamondsOfAnActiveGoalAreNotMadeIntoAPickaxe(GameTestHelper h) {
        var r = CompanionDiamondGoalTests.rig(h, true, new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.STICK, 8));
        CompanionEntity c = r.c();
        h.setBlock(new BlockPos(CENTER + 1, GROUND + 1, CENTER), Blocks.CRAFTING_TABLE);
        h.setBlock(new BlockPos(CENTER + 3, GROUND + 1, CENTER), Blocks.COAL_ORE);
        CompanionDiamondGoalTests.start(h, r, 5);
        var order = dev.aicompanion.entity.CompanionEvents.executeAction(r.p(), "mine_ore", "coal", 1);
        h.assertTrue(order.status() == CompanionActionResult.Status.ACCEPTED, "Coal order not accepted: " + order.message());
        var goal = CompanionAgenda.goal(h.getLevel().getServer(), r.p().getUUID());
        h.assertTrue(goal != null && goal.state().outstanding(), "The goal is not outstanding");
        h.onEachTick(() -> h.assertTrue(CompanionDiamondGoalTests.count(c, Items.DIAMOND_PICKAXE) == 0, "Diamonds went into a pickaxe: " + c.getStoredItemsSummary()));
        h.runAfterDelay(WATCH, () -> {
            h.assertTrue(CompanionDiamondGoalTests.count(c, Items.DIAMOND) >= 3, "Diamonds were spent: " + c.getStoredItemsSummary());
            h.succeed();
        });
    }

    /** The bag is full of seals (dirt), yet the deepslate he digs is stone-pickaxe material and stays in the bag. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void cobbledDeepslateIsKeptForPickaxes(GameTestHelper h) {
        deepslateMountain(h);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8),
                new ItemStack(Items.TORCH, 16), new ItemStack(Items.DIRT, 64)};
        for (int slot = 0; slot < bag.length; slot++) c.setItem(slot, bag[slot]);
        CompanionDiamondGoalTests.bind(h, c, 3);
        h.assertTrue(c.startWork("dig_for_diamonds", "", 1).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
        h.succeedWhen(() -> {
            h.assertTrue(h.relativePos(c.blockPosition()).getY() <= GROUND - 6, "Not deep enough yet");
            int kept = CompanionDiamondGoalTests.count(c, Items.COBBLED_DEEPSLATE);
            h.assertTrue(kept >= STONE_KEPT, "Cobbled deepslate was thrown out: " + kept + " | " + c.getStoredItemsSummary());
        });
    }

    /** A natural cobweb at head height of the next step is open to the shaft plan yet holds him: he tears it out and goes on down. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void cobwebAtTheHeadOfTheNextStepIsTornOut(GameTestHelper h) {
        deepslateMountain(h);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 16)};
        for (int slot = 0; slot < bag.length; slot++) c.setItem(slot, bag[slot]);
        CompanionDiamondGoalTests.bind(h, c, 3);
        h.assertTrue(c.startWork("dig_for_diamonds", "", 1).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
        int[] webs = {0};
        h.onEachTick(() -> {
            var stands = c.mineSite().stands();
            if (webs[0] >= 3 || stands.size() < 2) return;
            BlockPos last = stands.get(stands.size() - 1), prev = stands.get(stands.size() - 2);
            BlockPos next = last.offset(last.getX() - prev.getX(), -1, last.getZ() - prev.getZ()).above(2);
            if (h.getLevel().getBlockState(next).isAir()) { h.getLevel().setBlockAndUpdate(next, Blocks.COBWEB.defaultBlockState()); webs[0]++; }
        });
        h.succeedWhen(() -> {
            h.assertTrue(webs[0] >= 1, "No cobweb was set");
            h.assertTrue(h.relativePos(c.blockPosition()).getY() <= GROUND - 8, "Held back by a cobweb at " + h.relativePos(c.blockPosition()).toShortString() + " | " + c.getLastTaskResult());
        });
    }

    /**
     * The floors under two stands in a row are gone while he stands on the stand just below them on his way up to the surface: the next floored
     * stand is three steps off, out of the walk's reach for a push, and he must bridge it or go round, not stand there for good.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = CLIMB_TIMEOUT)
    public static void climbsPastTwoStandsWithoutAFloor(GameTestHelper h) {
        CompanionMineBacktrackTests.mountain(h);
        CompanionEntity c = CompanionMineBacktrackTests.rig(h);
        var stairs = CompanionMineBacktrackTests.carveMine(h, c, 0);
        int gone = stairs.size() / 2;
        BlockPos below = stairs.get(gone + 2);
        for (int i = gone; i <= gone + 1; i++) h.getLevel().setBlock(stairs.get(i).below(), Blocks.AIR.defaultBlockState(), 2 | 16);
        c.teleportTo(below.getX() + .5, below.getY(), below.getZ() + .5);
        h.assertTrue(c.startWork("dig_for_diamonds", "surface", 1).success(), "Climb not accepted: " + c.getLastTaskResult());
        // A push at the far stand that the walk undoes every tick holds him for a hundred ticks more than the bridge does.
        h.runAfterDelay(PAST_GAP_BY, () -> {
            int y = CompanionMineBacktrackTests.rel(h, c.blockPosition()).getY(), limit = CompanionMineBacktrackTests.rel(h, stairs.get(gone)).getY() + 2;
            h.assertTrue(y >= limit, "Still at the gap after " + PAST_GAP_BY + " ticks, at height " + y + " | " + c.getLastTaskResult());
        });
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking() && !c.isGatheringTask(), "Still climbing at " + CompanionMineBacktrackTests.rel(h, c.blockPosition()).toShortString() + " | " + c.getLastTaskResult());
            h.assertTrue(CompanionMineBacktrackTests.rel(h, c.blockPosition()).getY() >= GROUND, "Did not reach the surface, at " + CompanionMineBacktrackTests.rel(h, c.blockPosition()).toShortString());
        });
    }

    private static void deepslateMountain(GameTestHelper h) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            var block = y == 0 || edge ? Blocks.BEDROCK : y < GROUND - 2 ? Blocks.DEEPSLATE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    private static int wear(CompanionEntity c, net.minecraft.world.item.Item item) {
        if (c.getMainHandItem().is(item)) return c.getMainHandItem().getDamageValue();
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) return c.getItem(slot).getDamageValue();
        return -1;
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

import static dev.aicompanion.gametest.CompanionSortingInterruptionTests.outOfReach;
import static dev.aicompanion.gametest.CompanionSortingTests.bagOf;
import static dev.aicompanion.gametest.CompanionSortingTests.count;
import static dev.aicompanion.gametest.CompanionSortingTests.mixedThree;
import static dev.aicompanion.gametest.CompanionSortingTests.setup;
import static dev.aicompanion.gametest.CompanionSortingTests.totals;

/** What he carries for the player in the middle of sorting is not his: not eaten, not wielded; at a stop it goes back into a chest, and what could not is his (issue 32, tickets 06b, 09). */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSortingCarriedTests {
    /** Hurt (above half health) and with a zombie as the target, he has no own food and weapon: the carried beef stays, the carried sword stays out of his hand. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void carriedFoodAndSwordAreNotSpentInAFight(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        chests[0].setItem(4, new ItemStack(Items.DIAMOND_SWORD)); chests[0].setItem(5, new ItemStack(Items.COOKED_BEEF, 8)); chests[0].setItem(6, new ItemStack(Items.TORCH, 16));
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        ItemStack handBefore = companion.getMainHandItem().copy();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2); zombie.setNoAi(true); zombie.setInvulnerable(true);
        boolean[] carried = {false}; int[] stress = {0};
        helper.onEachTick(() -> {
            if (!companion.hasActiveWorkOrder()) return;
            if (!totals(companion).equals(bagBefore)) carried[0] = true;
            if (!carried[0]) return;
            if (stress[0]++ < 60) { companion.setHealth(12); companion.setTarget(zombie); } else companion.setTarget(null);
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(carried[0] && !companion.hasActiveWorkOrder(), "Not done: " + companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items eaten, lost or doubled: " + totals(companion, chests) + " vs " + before);
            helper.assertTrue(ItemStack.matches(companion.getMainHandItem(), handBefore), "The hand changed: " + companion.getMainHandItem());
        });
    }

    /** In the game (29.10): «иди ко мне» ended the sorting, and he, hurt, ate two breads he carried for the player. Stopped at a chest, they are back in it, so there is nothing to eat. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void carriedFoodIsInTheChestAfterTheStopAndNotEaten(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        long[] stoppedAt = {-1};
        helper.onEachTick(() -> {
            Map<Item, Integer> bag = totals(companion);
            if (stoppedAt[0] < 0 && companion.hasActiveWorkOrder() && (bag.containsKey(Items.BREAD) || bag.containsKey(Items.APPLE) || bag.containsKey(Items.COOKED_BEEF))) {
                companion.stopGathering(); stoppedAt[0] = helper.getTick();
            }
            if (stoppedAt[0] >= 0 && helper.getTick() < stoppedAt[0] + 100) companion.setHealth(12);
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(stoppedAt[0] >= 0 && helper.getTick() > stoppedAt[0] + 140, "Waiting: he never carried food, or he is still hurt");
            helper.assertTrue(totals(companion).equals(bagBefore), "The player's food is still in his bag: " + totals(companion) + " vs " + bagBefore);
            helper.assertTrue(totals(companion, chests).equals(before), "The player's food eaten or lost after the stop: " + totals(companion, chests) + " vs " + before);
        });
    }

    /** Oleg (29.10): at half health or less, with no food of his own, he eats the player's food he carries rather than die (while sorting: the mark is only for it). */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void carriedFoodIsEatenAtHalfHealthWithNoOwnFood(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        int[] food = {-1}; long[] hurtAt = {-1};
        helper.onEachTick(() -> {
            if (hurtAt[0] >= 0) return;
            int carried = count(companion, Items.BREAD) + count(companion, Items.APPLE) + count(companion, Items.COOKED_BEEF);
            if (!companion.hasActiveWorkOrder() || carried == 0) return;
            companion.setHealth(8); food[0] = foodIn(companion, chests); hurtAt[0] = helper.getTick();
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(hurtAt[0] >= 0, "He never carried food");
            helper.assertTrue(foodIn(companion, chests) < food[0] && companion.getHealth() > 8, "At four hearts he did not eat the player's food: " + foodIn(companion, chests) + " of " + food[0] + ", health " + companion.getHealth());
        });
    }

    /** Pieces of food the player's chests and his bag hold together. */
    private static int foodIn(CompanionEntity companion, Container[] chests) {
        Map<Item, Integer> all = totals(companion, chests);
        return all.getOrDefault(Items.BREAD, 0) + all.getOrDefault(Items.APPLE, 0) + all.getOrDefault(Items.COOKED_BEEF, 0);
    }

    /** The player's pickaxe, better than his own, carried at a stop far from the chests: it is his now, «накопай камня» is mined with it. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void playersPickaxeLeftInTheBagIsHisForTheNextStoneJob(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        chests[0].setItem(4, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos stone = new BlockPos(28, 1, 30);
        boolean[] stopped = {false};
        helper.onEachTick(() -> {
            if (stopped[0] || !companion.hasActiveWorkOrder() || count(companion, Items.DIAMOND_PICKAXE) == 0) return;
            outOfReach(helper, companion);
            companion.stopGathering();
            helper.assertTrue(count(companion, Items.DIAMOND_PICKAXE) == 1, "The pickaxe is not in his bag after a stop out of reach");
            helper.setBlock(stone, Blocks.STONE);
            companion.startWork("gather_stone", "", 1); stopped[0] = true;
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(stopped[0], "He never carried the pickaxe");
            helper.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed() && helper.getBlockState(stone).isAir(), "The stone job: " + companion.getLastTaskResult());
        });
    }

    /** In the game (29.10): stopped with the player's logs in his bag, he made planks of one to climb to the player on a ledge. Stopped at a chest, the logs are back in it, none are made into planks. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void carriedLogsAreInTheChestAfterTheStopNotMadeIntoSupports(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        int[] logs = {-1}; long[] at = {-1};
        helper.onEachTick(() -> {
            if (at[0] >= 0 || !companion.hasActiveWorkOrder() || count(companion, Items.OAK_LOG) == 0) return;
            companion.stopGathering(); logs[0] = count(companion, Items.OAK_LOG); at[0] = helper.getTick();
            for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(4, y, 4), Blocks.STONE);
            var top = helper.absoluteVec(new Vec3(4.5, 4, 4.5));
            helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId()).teleportTo(top.x, top.y, top.z);
            companion.startWork("come", "", 1);
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(at[0] >= 0 && helper.getTick() > at[0] + 400, "Waiting: he never carried logs, or the climb is not over");
            helper.assertTrue(count(companion, Items.OAK_LOG) == 0 && count(companion, Items.OAK_PLANKS) == 0 && totals(companion, chests).get(Items.OAK_LOG) == 62,
                    "The player's logs are not back in the chests or went into supports: bag logs " + count(companion, Items.OAK_LOG) + " (carried " + logs[0] + "), planks " + count(companion, Items.OAK_PLANKS) + ", all " + totals(companion, chests));
        });
    }
}

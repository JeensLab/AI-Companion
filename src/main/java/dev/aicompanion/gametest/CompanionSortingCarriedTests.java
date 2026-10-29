package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

import static dev.aicompanion.gametest.CompanionSortingTests.bagOf;
import static dev.aicompanion.gametest.CompanionSortingTests.mixedThree;
import static dev.aicompanion.gametest.CompanionSortingTests.setup;
import static dev.aicompanion.gametest.CompanionSortingTests.totals;

/** What he carries for the player in the middle of sorting is not his: not eaten, not wielded (issue 32, ticket 06b). */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSortingCarriedTests {
    /** Hurt and with a zombie as the target, he has no own food and weapon: the carried beef stays, the carried sword stays out of his hand. */
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
            if (stress[0]++ < 60) { companion.setHealth(6); companion.setTarget(zombie); } else companion.setTarget(null);
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(carried[0] && !companion.hasActiveWorkOrder(), "Not done: " + companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items eaten, lost or doubled: " + totals(companion, chests) + " vs " + before);
            helper.assertTrue(ItemStack.matches(companion.getMainHandItem(), handBefore), "The hand changed: " + companion.getMainHandItem());
        });
    }

    /** In the game (29.10): «иди ко мне» ended the sorting, and he, hurt, ate two breads he carried for the player. Stopped, they are still the player's. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void carriedFoodIsNotEatenAfterTheStop(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests);
        long[] stoppedAt = {-1};
        helper.onEachTick(() -> {
            Map<Item, Integer> bag = totals(companion);
            if (stoppedAt[0] < 0 && companion.hasActiveWorkOrder() && (bag.containsKey(Items.BREAD) || bag.containsKey(Items.APPLE) || bag.containsKey(Items.COOKED_BEEF))) {
                companion.stopGathering(); stoppedAt[0] = helper.getTick();
            }
            if (stoppedAt[0] >= 0 && helper.getTick() < stoppedAt[0] + 100) companion.setHealth(6);
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(stoppedAt[0] >= 0 && helper.getTick() > stoppedAt[0] + 140, "Waiting: he never carried food, or he is still hurt");
            helper.assertTrue(totals(companion, chests).equals(before), "The player's food eaten after the stop: " + totals(companion, chests) + " vs " + before);
        });
    }
}

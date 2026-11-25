package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionSingleRouteTests.box;
import static dev.aicompanion.gametest.CompanionSingleRouteTests.count;
import static dev.aicompanion.gametest.CompanionSingleRouteTests.spawn;

/** A meal is five health in 32 ticks with the sword hand free: a wound on a long order is not left open because a foe stands near. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMealTests {
    private static final int BREAD = 8, MEAL_WINDOW = 40, ZOMBIE_HEALTH = 500;
    private static final float WOUNDED = 8, WEAK_ZOMBIE_BLOW = 1;

    /** Eight health, a zombie on him, bread in the bag, a soil order going on: the loaf is eaten under the zombie's blows, not after it (the zombie here does not die in the window). */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void woundedOnALongOrderHeEatsWithTheZombieOnHim(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 6, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 2, 31, Blocks.DIRT);
        CompanionEntity c = spawn(h, 16, 3, 16);
        c.setItem(0, new ItemStack(Items.BREAD, BREAD));
        c.setHealth(WOUNDED);
        Zombie zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(17, 3, 16));
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(WEAK_ZOMBIE_BLOW);
        // It outlives the window: the foe is still there when the meal is due.
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ZOMBIE_HEALTH); zombie.setHealth(ZOMBIE_HEALTH);
        h.assertTrue(c.startWork("gather_dirt", "", 8).success(), "Order not accepted");
        long begun = h.getTick();
        h.onEachTick(() -> {
            if (zombie.isAlive()) { zombie.setTarget(c); c.setTarget(zombie); }
            String trace = " | bread " + count(c, Items.BREAD) + ", zombie " + (zombie.isAlive() ? "health " + zombie.getHealth() : "dead") + ", health " + c.getHealth();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (h.getTick() - begun < MEAL_WINDOW) return;
            h.assertTrue(count(c, Items.BREAD) < BREAD, "No bread was eaten" + trace);
            h.assertTrue(c.getHealth() > WOUNDED, "The meal did not bring him above where he began" + trace);
            h.succeed();
        });
    }

    /** With no order and no goal the meal waits for the foe at his side as before: a wound of eight is not the line (the zombie here does no harm). */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void withNoOrderTheMealStillWaitsForTheZombieAtHisSide(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 6, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 2, 31, Blocks.DIRT);
        CompanionEntity c = spawn(h, 16, 3, 16);
        c.setItem(0, new ItemStack(Items.BREAD, BREAD));
        c.setHealth(WOUNDED);
        Zombie zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(17, 3, 16));
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ZOMBIE_HEALTH); zombie.setHealth(ZOMBIE_HEALTH);
        long begun = h.getTick();
        h.onEachTick(() -> {
            if (zombie.isAlive()) { zombie.setTarget(c); c.setTarget(zombie); }
            String trace = " | bread " + count(c, Items.BREAD) + ", health " + c.getHealth();
            h.assertTrue(count(c, Items.BREAD) == BREAD, "He ate with a foe at his side and no order" + trace);
            if (h.getTick() - begun >= MEAL_WINDOW) h.succeed();
        });
    }
}

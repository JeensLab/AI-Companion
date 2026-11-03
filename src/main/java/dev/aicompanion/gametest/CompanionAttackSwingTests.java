package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A hit has to show in his arm: the renderer draws the swing from the entity, so the hit itself must start one. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAttackSwingTests {
    /** A hit dealt outside the attack goal still lands and starts the stroke. */
    @GameTest(template = "empty")
    public static void aHitStartsTheStroke(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, 18, 2, 16);
        zombie.setNoAi(true);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        h.startSequence().thenExecute(() -> {
            float before = zombie.getHealth();
            h.assertTrue(companion.doHurtTarget(zombie), "The hit did not land");
            h.assertTrue(zombie.getHealth() < before, "The zombie kept its health: " + zombie.getHealth());
            h.assertTrue(companion.swinging, "He hit with his arm standing still");
            h.assertTrue(companion.swingTime <= 0, "The stroke did not start: swingTime " + companion.swingTime);
        }).thenSucceed();
    }

    /** The stroke the attack goal starts before its hit is not thrown away by that hit. */
    @GameTest(template = "empty")
    public static void theGoalsOwnStrokeIsNotRestarted(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, 18, 2, 16);
        zombie.setNoAi(true);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        h.startSequence().thenExecute(() -> {
            companion.swing(InteractionHand.MAIN_HAND);
            h.assertTrue(companion.swinging, "The goal's swing did not start");
            // The test area does not tick him, so his own ticks are run here: the stroke must advance, or the renderer draws a still arm.
            companion.tick();
            int started = companion.swingTime;
            h.assertTrue(started == 0, "The stroke did not start: swingTime " + started);
            h.assertTrue(companion.doHurtTarget(zombie), "The hit did not land");
            h.assertTrue(companion.swinging, "The hit dropped the stroke");
            companion.tick();
            companion.tick();
            h.assertTrue(companion.swingTime == started + 2, "The hit restarted the stroke instead of continuing it: " + companion.swingTime + " after " + started);
            h.assertTrue(companion.attackAnim > 0.0F, "The renderer would draw a still arm: attackAnim " + companion.attackAnim);
        }).thenSucceed();
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 2, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() - 4);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
}

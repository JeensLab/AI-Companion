package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * the player 24.11: a monster he finished gave no experience and nothing of what only a player's kill brings (a blaze rod, a
 * spider's eye), so fighting beside him cost the player both. His blow counts for his player the way the game counts a tamed
 * wolf's bite.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionKillCreditTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aMonsterHeKillsIsHisPlayersKill(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 1, 2, 1);
        var owner = h.makeMockServerPlayerInLevel();
        c.setOwnerId(owner.getUUID());
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 1);
        // His sword is in his blow a tick on. The blow leaves the zombie alive; what kills it a second later is nobody's.
        h.runAfterDelay(5, () -> h.assertTrue(c.doHurtTarget(zombie) && zombie.isAlive(), "His blow: health " + zombie.getHealth()));
        h.runAfterDelay(25, () -> {
            zombie.hurt(h.getLevel().damageSources().magic(), 100);
            h.assertTrue(zombie.isDeadOrDying() && zombie.getKillCredit() == owner, "The kill is not his player's: " + zombie.getKillCredit());
            h.assertTrue(!h.getLevel().getEntitiesOfClass(ExperienceOrb.class, zombie.getBoundingBox().inflate(3)).isEmpty(), "No experience fell");
            h.succeed();
        });
    }
}

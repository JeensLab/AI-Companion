package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * What he struck in its last five seconds is his player's kill, the way the game counts a tamed animal's (LivingEntity.hurt: a
 * wolf's bite): experience falls, and so does what only a player's kill brings (a blaze rod, a spider's eye, the monster's own
 * gear), and the kill is the player's (mobs killed, the kill advancements). Without it a monster he finished gave none of these
 * (the player 24.11), and fighting beside him cost the player both. As for a wolf, with his player in another dimension only the
 * experience falls.
 */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID)
public final class CompanionKillCredit {
    private CompanionKillCredit() { }

    @SubscribeEvent
    public static void struck(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof CompanionEntity c) || c.getOwnerId() == null) return;
        LivingEntity struck = event.getEntity();
        Player owner = c.level().getPlayerByUUID(c.getOwnerId());
        // His stray arrow in his own player, or his own turned back into him, is no kill of the player's.
        if (struck == owner || struck == c) return;
        struck.lastHurtByPlayerTime = 100;
        struck.lastHurtByPlayer = owner;
    }
}

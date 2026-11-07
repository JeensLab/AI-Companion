package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionDamageNumbers;
import dev.aicompanion.CompanionDamageNumbers.Kind;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Idea 7ES2V (#105): what he lost or regained rises above him for a second, «блок» when a shield stopped a whole hit. The
 * client counts it from his health as it arrives; here the same count is fed his health after real hits on the server.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDamageNumbersTests {
    @GameTest(template = "empty")
    public static void eachChangeOfHisHealthIsOneNumber(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2);
        companion.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        var zombie = h.spawn(EntityType.ZOMBIE, 4, 1, 2);
        zombie.setNoAi(true);
        // The chestplate counts once his equipment has been looked at in a tick.
        h.runAfterDelay(2, () -> hits(h, companion, zombie));
    }

    private static void hits(GameTestHelper h, CompanionEntity companion, net.minecraft.world.entity.monster.Zombie zombie) {
        h.assertTrue(companion.getArmorValue() == 6, "Armour " + companion.getArmorValue());
        var sources = h.getLevel().damageSources();
        var numbers = new CompanionDamageNumbers();
        seen(numbers, companion, 0);
        h.assertTrue(numbers.live(0).isEmpty(), "The health he came with is no change: " + numbers.live(0));

        companion.hurt(sources.mobAttack(zombie), 6);
        seen(numbers, companion, 1);
        seen(numbers, companion, 2);
        expect(h, numbers, 2, "5,3", Kind.HIT);

        again(companion);
        companion.hurt(sources.onFire(), 1);
        seen(numbers, companion, 3);
        expect(h, numbers, 3, "1", Kind.FIRE); // burning goes past armour

        again(companion);
        companion.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        companion.hurt(sources.lava(), 4);
        seen(numbers, companion, 4);
        h.assertTrue(numbers.live(4).size() == 2, "Lava he does not feel is no number: " + numbers.live(4));

        again(companion);
        companion.hurt(sources.magic(), 1);
        seen(numbers, companion, 5);
        expect(h, numbers, 5, "1", Kind.MAGIC);

        again(companion);
        companion.hurt(sources.explosion(null, null), 2);
        seen(numbers, companion, 6);
        h.assertTrue(numbers.live(6).getLast().kind() == Kind.EXPLOSION, "Blast: " + numbers.live(6));

        companion.heal(2);
        seen(numbers, companion, 7);
        expect(h, numbers, 7, "+2", Kind.HEAL);

        numbers.blocked(8);
        expect(h, numbers, 8, "блок", Kind.BLOCK);
        h.assertTrue(numbers.live(8).size() == 6, "One number for each change: " + numbers.live(8));
        h.assertTrue(numbers.live(8 + CompanionDamageNumbers.LIFE_TICKS).isEmpty(), "Still up after a second: " + numbers.live(28));
        h.succeed();
    }

    private static void seen(CompanionDamageNumbers numbers, CompanionEntity companion, int tick) {
        numbers.health(companion.getHealth(), companion.getLastDamageSource(), tick);
    }

    /** The game lets a second hit through only once the first one's cooldown is over. */
    private static void again(CompanionEntity companion) { companion.invulnerableTime = 0; }

    private static void expect(GameTestHelper h, CompanionDamageNumbers numbers, int tick, String text, Kind kind) {
        var last = numbers.live(tick).getLast();
        h.assertTrue(last.text().equals(text) && last.kind() == kind, "Expected " + text + " " + kind + ", got " + numbers.live(tick));
    }
}

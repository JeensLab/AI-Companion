package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Supplier;

/**
 * The shield against blows and shots, not only a creeper's blast: a zombie set on him from behind, a pillager's bolt and an arrow from no
 * one meet the raised shield. The player is away and Oleg stays, so nothing is his target before it strikes. A test passes once the shield has
 * taken wear — the blow did reach him and was blocked — with no health lost on the way; a single blow that gets through fails it.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionShieldDefenseTests {
    /** The companion stands at x 16.5, z 16.5 of the test. */
    private static final double HOME = 16.5, ZOMBIE_AT = 6, PILLAGER_AT = 9, ARROW_FROM = 15, ARROW_HEIGHT = 1.5;
    private static final float ARROW_SPEED = 1.6F;
    private static final int ARROW_EVERY = 30, TIMEOUT = 400, GIVE_UP = TIMEOUT - 10;

    private CompanionShieldDefenseTests() { }

    /** A zombie set on him from behind, away from any fight of his: he turns, the shield is up before it reaches him and takes the blow. */
    @GameTest(template = "tall_tree", timeoutTicks = TIMEOUT)
    public static void aZombieSetOnHimFromBehindHitsTheRaisedShield(GameTestHelper h) {
        CompanionEntity c = CompanionShieldGuardTests.setup(h, true, true);
        Mob zombie = h.spawn(EntityType.ZOMBIE, new Vec3(HOME + ZOMBIE_AT, 1, HOME));
        // The helmet keeps the daylight from burning it before it strikes.
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        set(zombie, c);
        c.setYRot(90); c.setYHeadRot(90); c.setYBodyRot(90);
        blockedWithoutHarm(h, c, () -> "zombie at " + String.format("%.1f", c.distanceTo(zombie)));
    }

    /** A baby zombie runs at him about twice as fast as a grown one: the shield is still up and turned before its first blow. */
    @GameTest(template = "tall_tree", timeoutTicks = TIMEOUT)
    public static void aBabyZombieRunningAtHimHitsTheRaisedShield(GameTestHelper h) {
        CompanionEntity c = CompanionShieldGuardTests.setup(h, true, true);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new Vec3(HOME + ZOMBIE_AT, 1, HOME));
        zombie.setBaby(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        set(zombie, c);
        c.setYRot(90); c.setYHeadRot(90); c.setYBodyRot(90);
        blockedWithoutHarm(h, c, () -> "baby zombie at " + String.format("%.1f", c.distanceTo(zombie)));
    }

    /** A pillager's crossbow at nine blocks, set on him: the shield is up and turned to it before the first bolt arrives. */
    @GameTest(template = "tall_tree", timeoutTicks = TIMEOUT)
    public static void aPillagersBoltHitsTheRaisedShield(GameTestHelper h) {
        CompanionEntity c = CompanionShieldGuardTests.setup(h, true, true);
        Mob pillager = h.spawn(EntityType.PILLAGER, new Vec3(HOME + PILLAGER_AT, 1, HOME));
        pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        set(pillager, c);
        blockedWithoutHarm(h, c, () -> "pillager at " + String.format("%.1f", c.distanceTo(pillager)) + (pillager.isAlive() ? "" : ", dead"));
    }

    /** An arrow of no one, from his side: he sees it come, turns to it, and it hits the shield. A miss is followed by another one. */
    @GameTest(template = "tall_tree", timeoutTicks = TIMEOUT)
    public static void anArrowFromHisSideHitsTheRaisedShield(GameTestHelper h) {
        CompanionEntity c = CompanionShieldGuardTests.setup(h, true, false);
        Vec3 from = h.absoluteVec(new Vec3(HOME, 1 + ARROW_HEIGHT, HOME - ARROW_FROM));
        int[] shots = {0};
        h.onEachTick(() -> {
            if (h.getTick() % ARROW_EVERY != 1) return;
            Arrow arrow = new Arrow(h.getLevel(), from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            // A skeleton's aim: the lower third of the body, as high again over it as the arrow sinks on the way.
            double dx = c.getX() - from.x, dz = c.getZ() - from.z;
            arrow.shoot(dx, c.getY(1.0 / 3) - from.y + Math.sqrt(dx * dx + dz * dz) * .2, dz, ARROW_SPEED, 0);
            h.getLevel().addFreshEntity(arrow);
            shots[0]++;
        });
        blockedWithoutHarm(h, c, () -> shots[0] + " arrows shot");
    }

    private static void set(Mob mob, CompanionEntity c) {
        mob.setPersistenceRequired();
        mob.setTarget(c);
    }

    /**
     * The shield takes wear only from a blow of 3 or more (CompanionEquipment.wearShield, as a player's): a zombie strikes for 3, the arrows
     * and bolts here for 4 or so, and a mob's blow on a mob does not scale with the difficulty.
     */
    private static void blockedWithoutHarm(GameTestHelper h, CompanionEntity c, Supplier<String> what) {
        float full = c.getMaxHealth();
        h.onEachTick(() -> {
            String trace = " | " + what.get() + ", health " + c.getHealth() + ", blocking " + c.isBlocking() + ", shield wear " + c.getOffhandItem().getDamageValue();
            h.assertTrue(c.getHealth() >= full, "A blow got past the shield" + trace);
            h.assertTrue(c.getOffhandItem().is(Items.SHIELD), "The shield is gone from the hand" + trace);
            if (c.getOffhandItem().getDamageValue() > 0) h.succeed();
            else if (h.getTick() >= GIVE_UP) h.fail("Nothing ever reached the shield" + trace);
        });
    }
}

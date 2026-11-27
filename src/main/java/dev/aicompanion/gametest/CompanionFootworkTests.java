package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The melee on open ground (CompanionFootwork): what is on him is struck before what is not, a lone zombie lands at most one blow on him while
 * he is after a far foe or a drop, and the pickup of a drop waits while a foe he would fight is within three blocks.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFootworkTests {
    private static final int SIZE = 32, MIDDLE = 16, FAR_FOE = 12, DROP_REACH = 10, PICKUP_UNDER_WAY = 8, KILL_TICKS = 600, FIGHT_TICKS = 100;
    private static final float HALF = 10;
    private static final double REACHED = 3;

    private CompanionFootworkTests() { }

    /**
     * He is after a skeleton far across the arena (it cannot shoot or come: no bow, no legs) and a zombie on his back strikes him: the zombie is
     * struck down before he has reached the skeleton, and he is not brought below half his health. A foe that is on him is not left for one that is not.
     */
    @GameTest(template = "empty", timeoutTicks = KILL_TICKS + 40)
    public static void aZombieOnHimIsKilledBeforeTheFarFoeHeIsAfterIsReached(GameTestHelper h) {
        CompanionEntity c = armed(h);
        Zombie zombie = zombie(h, MIDDLE - 1);
        Skeleton skeleton = farSkeleton(h);
        long begun = h.getTick();
        float[] lowest = {c.getMaxHealth()};
        h.onEachTick(() -> {
            lowest[0] = Math.min(lowest[0], c.getHealth());
            if (h.getTick() == begun + 1) c.setTarget(skeleton);
            if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            String trace = " | zombie " + (zombie.isAlive() ? "health " + zombie.getHealth() : "dead") + ", skeleton at " + String.format("%.1f", Math.sqrt(c.distanceToSqr(skeleton)))
                    + ", lowest health " + lowest[0] + ", health " + c.getHealth() + ", target " + c.getTarget();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (!zombie.isAlive()) {
                h.assertTrue(lowest[0] > HALF, "He was brought to half his health or below" + trace);
                h.succeed();
                return;
            }
            h.assertTrue(c.distanceToSqr(skeleton) > REACHED * REACHED, "He reached the far foe with the zombie on him alive" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The zombie on him was not killed in time" + trace);
        });
    }

    /**
     * The same: the zombie has the cooldown of his sword against its own, so it may land one blow on him while it is killed, not the string of
     * them that comes of standing in its reach.
     */
    @GameTest(template = "empty", timeoutTicks = KILL_TICKS + 40)
    public static void aLoneZombieOnHimLandsAtMostOneBlowWhileHeIsAfterAFarFoe(GameTestHelper h) {
        CompanionEntity c = armed(h);
        Zombie zombie = zombie(h, MIDDLE - 1);
        Skeleton skeleton = farSkeleton(h);
        long begun = h.getTick();
        float[] last = {c.getHealth()};
        int[] blows = {0};
        h.onEachTick(() -> {
            if (c.getHealth() < last[0]) blows[0]++;
            last[0] = c.getHealth();
            if (h.getTick() == begun + 1) c.setTarget(skeleton);
            if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            String trace = " | blows on him " + blows[0] + ", zombie " + (zombie.isAlive() ? "health " + zombie.getHealth() : "dead") + ", health " + c.getHealth() + ", target " + c.getTarget();
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(blows[0] <= 1, "The zombie landed more than one blow on him" + trace);
            if (!zombie.isAlive()) { h.succeed(); return; }
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The zombie on him was not killed in time" + trace);
        });
    }

    /** The pickup of a drop ten blocks off is under way when a zombie wakes on him: the zombie is struck first, the pickup waits (it shares the melee goal's priority and would hold him walking while the zombie strikes). */
    @GameTest(template = "empty", timeoutTicks = FIGHT_TICKS + 40)
    public static void aZombieOnHimIsStruckBeforeADropIsPickedUp(GameTestHelper h) {
        CompanionEntity c = armed(h);
        Zombie zombie = zombie(h, MIDDLE - 1);
        zombie.setCanPickUpLoot(false);
        zombie.setNoAi(true);
        ItemEntity drop = drop(h, c);
        long begun = h.getTick();
        h.onEachTick(() -> {
            if (h.getTick() == begun + PICKUP_UNDER_WAY) { zombie.setNoAi(false); zombie.setTarget(c); c.setTarget(zombie); }
            String trace = " | zombie health " + zombie.getHealth() + ", drop " + (drop.isAlive() ? "lies" : "taken") + ", health " + c.getHealth() + ", target " + c.getTarget();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (zombie.getHealth() < zombie.getMaxHealth()) {
                h.assertTrue(drop.isAlive(), "The drop was picked up before the zombie on him was struck" + trace);
                h.succeed();
                return;
            }
            h.assertTrue(h.getTick() - begun < FIGHT_TICKS, "The zombie on him was not struck" + trace);
        });
    }

    /** A skeleton with a bow two blocks off is a mob the footwork does not count (it is the bow's, not the blade's): the pickup of his own drop still waits while it is that near. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aBowSkeletonWithinReachHoldsThePickupOfADrop(GameTestHelper h) throws Exception {
        CompanionEntity c = armed(h);
        Skeleton skeleton = h.spawn(EntityType.SKELETON, MIDDLE + 2, 1, MIDDLE);
        skeleton.setPersistenceRequired();
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        drop(h, c);
        Object loot = CompanionSingleRouteTests.field(c, "loot");
        var canChase = loot.getClass().getDeclaredMethod("canChase");
        canChase.setAccessible(true);
        long begun = h.getTick();
        h.onEachTick(() -> {
            if (h.getTick() < begun + 2) return;
            try {
                if (skeleton.isAlive()) skeleton.setTarget(c);
                String trace = " | skeleton at " + String.format("%.1f", Math.sqrt(c.distanceToSqr(skeleton)));
                if (skeleton.isAlive()) {
                    h.assertTrue(c.distanceToSqr(skeleton) < 3 * 3, "The skeleton is not within three blocks" + trace);
                    h.assertTrue(!(boolean) canChase.invoke(loot), "The pickup would run with the bow skeleton within reach" + trace);
                    skeleton.discard();
                    return;
                }
                h.assertTrue((boolean) canChase.invoke(loot), "The pickup does not run with no foe near" + trace);
                h.succeed();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** A zombified piglin two blocks off that nobody has angered goes for nobody: it does not hold the pickup of his drop, which would else wait on it for good. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aCalmZombifiedPiglinWithinReachDoesNotHoldThePickupOfADrop(GameTestHelper h) throws Exception {
        CompanionEntity c = armed(h);
        ZombifiedPiglin piglin = h.spawn(EntityType.ZOMBIFIED_PIGLIN, MIDDLE + 2, 1, MIDDLE);
        piglin.setPersistenceRequired();
        piglin.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        drop(h, c);
        Object loot = CompanionSingleRouteTests.field(c, "loot");
        var canChase = loot.getClass().getDeclaredMethod("canChase");
        canChase.setAccessible(true);
        long begun = h.getTick();
        h.onEachTick(() -> {
            if (h.getTick() < begun + 2) return;
            try {
                String trace = " | piglin at " + String.format("%.1f", Math.sqrt(c.distanceToSqr(piglin))) + ", its target " + piglin.getTarget();
                h.assertTrue(piglin.isAlive() && piglin.getTarget() == null && c.distanceToSqr(piglin) < 3 * 3, "The piglin is not a calm one within three blocks" + trace);
                h.assertTrue((boolean) canChase.invoke(loot), "The pickup waits on a calm zombified piglin" + trace);
                h.succeed();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** An idle hostile three blocks off that goes for nobody (the owner is far away) does not hold the pickup: an order for bones next to an idle skeleton would wait on it for good. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anIdleHostileWithinReachThatGoesForNobodyDoesNotHoldThePickupOfADrop(GameTestHelper h) throws Exception {
        CompanionEntity c = armed(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        owner.teleportTo(c.getX() + 60, c.getY(), c.getZ());
        Zombie zombie = zombie(h, MIDDLE + 2);
        zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        drop(h, c);
        Object loot = CompanionSingleRouteTests.field(c, "loot");
        var canChase = loot.getClass().getDeclaredMethod("canChase");
        canChase.setAccessible(true);
        h.runAfterDelay(3, () -> {
            try {
                String trace = " | zombie at " + String.format("%.1f", Math.sqrt(c.distanceToSqr(zombie))) + ", its target " + zombie.getTarget() + ", his target " + c.getTarget();
                h.assertTrue(zombie.getTarget() == null && c.distanceToSqr(zombie) < 3 * 3, "The zombie is not an idle one within three blocks" + trace);
                h.assertTrue((boolean) canChase.invoke(loot), "The pickup waits on a hostile that goes for nobody" + trace);
                h.succeed();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** The step back is not made onto a magma block: with magma on the floor behind him (and no knockback to push him there) he stays off it while the zombie strikes. */
    @GameTest(template = "empty", timeoutTicks = 140)
    public static void theStepBackDoesNotPutHimOnMagma(GameTestHelper h) {
        CompanionEntity c = armed(h);
        for (int x = MIDDLE - 5; x <= MIDDLE - 1; x++) for (int z = MIDDLE - 4; z <= MIDDLE + 4; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.MAGMA_BLOCK);
        Zombie zombie = zombie(h, MIDDLE + 1);
        c.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            if (h.getTick() == begun + 1) c.setTarget(zombie);
            h.assertTrue(!c.getBlockStateOn().is(Blocks.MAGMA_BLOCK), "He stands on magma, at " + c.position() + ", zombie health " + zombie.getHealth());
            if (!zombie.isAlive() || h.getTick() - begun > 100) h.succeed();
        });
    }

    /** A stone floor and open air round him, the sword in his hand, an owner beside him. */
    private static CompanionEntity armed(GameTestHelper h) {
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), MIDDLE, 1, MIDDLE);
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(c.getX() - 3, c.getY(), c.getZ() - 3);
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        return c;
    }

    /** A zombie that does not burn in the daylight of the test world. */
    private static Zombie zombie(GameTestHelper h, int x) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, x, 1, MIDDLE);
        zombie.setPersistenceRequired();
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        return zombie;
    }

    /** A skeleton across the arena with no bow and no legs: a foe to walk to that does nothing. */
    private static Skeleton farSkeleton(GameTestHelper h) {
        Skeleton skeleton = h.spawn(EntityType.SKELETON, MIDDLE + FAR_FOE, 1, MIDDLE);
        skeleton.setPersistenceRequired();
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        skeleton.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        return skeleton;
    }

    /** A cobblestone ten blocks off, claimed as his drop to pick up. */
    private static ItemEntity drop(GameTestHelper h, CompanionEntity c) {
        BlockPos at = h.absolutePos(new BlockPos(MIDDLE + DROP_REACH, 1, MIDDLE));
        ItemEntity drop = new ItemEntity(h.getLevel(), at.getX() + .5, at.getY(), at.getZ() + .5, new ItemStack(Items.COBBLESTONE));
        drop.setDeltaMovement(Vec3.ZERO);
        drop.setNoPickUpDelay();
        h.getLevel().addFreshEntity(drop);
        c.claimDrops(List.of(drop));
        return drop;
    }
}

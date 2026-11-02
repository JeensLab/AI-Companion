package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Issue 59: «Олег не бьёт водных враждебных мобов». The game's float held him on the surface, and a drowned or a guardian on the
 * bottom under the swimming player was out of his reach: he swam to the spot above it and stayed there.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWaterMobTests {
    private static final int DEPTH = 7;

    /** In water two deep he reached it before too: it stays so. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void drownedInAShallowPoolBesideThePlayerIsFought(GameTestHelper h) {
        CompanionEntity companion = setup(h, 2, false);
        var drowned = h.spawn(EntityType.DROWNED, 12, 1, 18); drowned.setNoAi(true);
        h.succeedWhen(() -> h.assertTrue(drowned.getLastHurtByMob() == companion, report(companion, drowned)));
    }

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void drownedOnTheBottomThatHitTheSwimmingPlayerIsFought(GameTestHelper h) {
        CompanionEntity companion = setup(h, DEPTH, true);
        var drowned = h.spawn(EntityType.DROWNED, 12, 1, 14); drowned.setNoAi(true);
        hitsThePlayer(h, companion, drowned);
        h.succeedWhen(() -> h.assertTrue(drowned.getLastHurtByMob() == companion, report(companion, drowned)));
    }

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void guardianDeepUnderTheSwimmingPlayerIsFought(GameTestHelper h) {
        CompanionEntity companion = setup(h, DEPTH, true);
        var guardian = h.spawn(EntityType.GUARDIAN, 12, 2, 14); guardian.setNoAi(true);
        hitsThePlayer(h, companion, guardian);
        h.succeedWhen(() -> h.assertTrue(guardian.getLastHurtByMob() == companion, report(companion, guardian)));
    }

    /** A foe he cannot finish under water: he comes up for air instead of drowning beside it. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void underWaterHeComesUpForAirFromAFoeHeCannotFinish(GameTestHelper h) {
        comesUpForAir(h, DEPTH, 760);
    }

    /**
     * Thirty blocks down: with only «half his breath» to turn back at he came up from 19 blocks with 15 ticks of it left; now
     * he dives only as deep as he comes back from with breath to spare.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1300)
    public static void inDeepWaterHeDivesOnlyAsFarAsHisBreathBringsHimBack(GameTestHelper h) {
        comesUpForAir(h, 31, 1240);
    }

    /** Three seconds of breath, at least, when he is up again. */
    private static final int SPARE_AIR = 60;

    private static void comesUpForAir(GameTestHelper h, int depth, int ticks) {
        CompanionEntity companion = setup(h, depth, true);
        var drowned = h.spawn(EntityType.DROWNED, 12, 1, 14); drowned.setNoAi(true); drowned.setInvulnerable(true);
        double[] lowest = {Double.MAX_VALUE};
        int[] leastAir = {Integer.MAX_VALUE};
        h.onEachTick(() -> {
            if (companion.getTarget() == null) companion.setTarget(drowned);
            lowest[0] = Math.min(lowest[0], companion.getY());
            leastAir[0] = Math.min(leastAir[0], companion.getAirSupply());
        });
        h.runAtTickTime(ticks, () -> {
            h.assertTrue(lowest[0] < surface(h, depth) - 3, "He never went under: " + report(companion, drowned));
            h.assertTrue(leastAir[0] >= SPARE_AIR && companion.getHealth() == companion.getMaxHealth(), "He ran out of air: least " + leastAir[0] + ", hp " + companion.getHealth());
            h.succeed();
        });
    }

    /** Bare-handed he stays up (a drowned with a trident killed him over and over down there). */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void withoutABladeHeDoesNotDive(GameTestHelper h) {
        CompanionEntity companion = setup(h, DEPTH, true);
        companion.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        staysUp(h, companion, bottomDrowned(h), null);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void hurtToHalfHeDoesNotDive(GameTestHelper h) {
        CompanionEntity companion = setup(h, DEPTH, true);
        companion.setHealth(companion.getMaxHealth() / 2);
        staysUp(h, companion, bottomDrowned(h), null);
    }

    /** The owner asked (03.11): with a bow he shoots a foe under water from above instead of diving to it, near as well as far. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void withABowHeShootsTheDrownedUnderHimFromAbove(GameTestHelper h) {
        CompanionEntity companion = setup(h, DEPTH, true);
        archer(companion);
        // On a pillar three blocks of water under the surface, beside him: within an arrow's way through water, inside the blade's reach.
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(15, y, 16), Blocks.STONE);
        var drowned = h.spawn(EntityType.DROWNED, 15, 4, 16); drowned.setNoAi(true);
        drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        staysUp(h, companion, drowned, () -> h.assertTrue(drowned.getLastHurtByMob() == companion && countArrows(companion) < 32,
                "No arrow reached the drowned: " + report(companion, drowned) + " hand=" + companion.getMainHandItem() + " arrows=" + countArrows(companion)));
    }

    /** Six blocks of water stop an arrow: with a bow he neither dives nor wastes arrows on it. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void withABowHeLeavesAFoeTooDeepForArrows(GameTestHelper h) {
        CompanionEntity companion = setup(h, DEPTH, true);
        archer(companion);
        staysUp(h, companion, bottomDrowned(h), () -> h.assertTrue(countArrows(companion) == 32, "He shot arrows into deep water: " + countArrows(companion)));
    }

    private static void archer(CompanionEntity companion) {
        companion.setItem(0, new ItemStack(Items.BOW));
        companion.setItem(1, new ItemStack(Items.ARROW, 32));
    }

    private static int countArrows(CompanionEntity companion) {
        int arrows = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(Items.ARROW)) arrows += companion.getItem(slot).getCount();
        return arrows;
    }

    /** The foe has hit the player; for 240 ticks he keeps within a block and a half of the surface. */
    private static void staysUp(GameTestHelper h, CompanionEntity companion, Mob foe, Runnable more) {
        hitsThePlayer(h, companion, foe);
        double[] lowest = {Double.MAX_VALUE};
        h.onEachTick(() -> { if (companion.getTarget() == null && h.getTick() > 5) companion.setTarget(foe); lowest[0] = Math.min(lowest[0], companion.getY()); });
        h.runAtTickTime(240, () -> {
            h.assertTrue(lowest[0] > surface(h, DEPTH) - 1.5, "He dived: lowest " + String.format("%.1f", lowest[0] - surface(h, DEPTH)) + " under the surface; " + report(companion, foe));
            if (more != null) more.run();
            h.succeed();
        });
    }

    private static Mob bottomDrowned(GameTestHelper h) {
        var drowned = h.spawn(EntityType.DROWNED, 12, 1, 14); drowned.setNoAi(true);
        return drowned;
    }

    /** The top of the water (its highest block is y = depth). */
    private static double surface(GameTestHelper h, int depth) { return h.absolutePos(BlockPos.ZERO).getY() + depth + 1; }

    private static void hitsThePlayer(GameTestHelper h, CompanionEntity companion, Mob mob) {
        h.runAtTickTime(5, () -> owner(h, companion).setLastHurtByMob(mob));
    }

    private static String report(CompanionEntity companion, LivingEntity mob) {
        return "He left the " + mob.getType().toShortString() + " alone; target=" + companion.getTarget() + " at " + at(companion)
                + " mob at " + at(mob) + " air=" + companion.getAirSupply();
    }

    private static String at(LivingEntity entity) { return String.format("%.1f/%.1f/%.1f", entity.getX(), entity.getY(), entity.getZ()); }

    /**
     * Stone up to y = depth with water that deep in it: a pool by the player at x 9..14, z 17..22, or, out at sea, all of x and z
     * 4..27 with the player and Oleg swimming on it.
     */
    private static CompanionEntity setup(GameTestHelper h, int depth, boolean atSea) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            boolean water = atSea ? x >= 4 && x <= 27 && z >= 4 && z <= 27 : x >= 9 && x <= 14 && z >= 17 && z <= 22;
            for (int y = 0; y <= depth; y++) h.setBlock(new BlockPos(x, y, z), y > 0 && water ? Blocks.WATER : Blocks.STONE);
        }
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, depth + (atSea ? 0 : 1), 16);
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = h.absolutePos(atSea ? new BlockPos(13, depth, 15) : new BlockPos(14, depth + 1, 15));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        return companion;
    }

    private static ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

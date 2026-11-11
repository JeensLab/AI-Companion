package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A creeper that sees him on open ground, with no mine: in any mode, with a sword or with his fists, he strikes a lit one first and then
 * runs, and by the house he walks it off before the first blow; too hurt to fight, he steps away from one that has not lit its fuse and
 * dashes from one that has.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCreeperFleeTests {
    private static final double SEES_WITHIN = 9, RUN_GAP = 4, HOUSE_NEAR = 10;
    private static final int RUN_TICKS = 20, WATCH_TICKS = 60, STRIKE_TICKS = 400, BLOW_TICKS = 10, KILL_TICKS = 1000, FIST_KILL_TICKS = 3600;
    /** Below the health at which he fights a creeper. */
    private static final float HURT = 10;
    /** Half a minute of a hunt (mnimar's 69 blocks took 30 s), and how far from the player he may be: the guard's leash plus a dash. */
    private static final int CHASE_TICKS = 600;
    private static final double CHASE_LEASH = 18;

    private CompanionCreeperFleeTests() { }

    @AfterBatch(batch = "creeper_flee_goal") public static void afterGoal(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Five blocks off and unlit, too hurt to fight it and no order to keep him: he goes out of the range at which the guard sees a creeper. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void anUnlitCreeperInSightMakesHimStepAwayWhenTooHurtToFight(GameTestHelper h) {
        CompanionEntity c = setup(h);
        c.setHealth(HURT);
        Creeper creeper = creeper(h, new Vec3(21.5, 1, 16.5));
        h.succeedWhen(() -> {
            h.assertTrue(c.isAlive(), "Died");
            h.assertTrue(creeper.getSwellDir() <= 0, "The creeper lit its fuse, the test is no longer about an unlit one");
            h.assertTrue(c.distanceTo(creeper) > SEES_WITHIN, "Still " + String.format("%.1f", c.distanceTo(creeper)) + " blocks from the creeper");
        });
    }

    /**
     * Review of PR #95 (mnimar, 04.11): a creeper after him while he is too hurt to fight led him 70-100 blocks off the player, a step away
     * at a time. Now every step away stays by the player: never more than the leash and a few steps of slack from him while it hunts him.
     */
    @GameTest(template = "tall_tree", timeoutTicks = CHASE_TICKS + 100)
    public static void aCreeperAfterHimWhenTooHurtToFightDoesNotLeadHimOffThePlayer(GameTestHelper h) {
        CompanionEntity c = setup(h);
        c.setHealth(HURT);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        // In creative the creeper hunts him, not the player.
        player.setGameMode(GameType.CREATIVE);
        Creeper creeper = h.spawn(EntityType.CREEPER, new Vec3(16.5, 1, 24.5));
        creeper.setPersistenceRequired();
        creeper.setTarget(c);
        long begun = h.getTick();
        double[] farthest = {0};
        h.onEachTick(() -> {
            farthest[0] = Math.max(farthest[0], c.distanceTo(player));
            String trace = " | tick " + (h.getTick() - begun) + ", from the player " + String.format("%.1f", c.distanceTo(player)) + " (at most "
                    + String.format("%.1f", farthest[0]) + "), creeper " + (creeper.isAlive() ? "alive, gap " + String.format("%.1f", c.distanceTo(creeper)) : "gone")
                    + ", health " + c.getHealth();
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(c.distanceTo(player) <= CHASE_LEASH, "Led off the player" + trace);
            if (h.getTick() - begun >= CHASE_TICKS) h.succeed();
        });
    }

    /** On a long goal with a sword in hand an unlit creeper is struck, not run from. */
    @GameTest(template = "tall_tree", batch = "creeper_flee_goal", timeoutTicks = 600)
    public static void anArmedGoalWorkerStrikesAnUnlitCreeper(GameTestHelper h) {
        CompanionEntity c = setup(h);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        player.setInvulnerable(true);
        Creeper creeper = h.spawn(EntityType.CREEPER, new Vec3(21.5, 1, 16.5));
        creeper.setPersistenceRequired();
        CompanionActionResult started = CompanionEvents.executeAction(player, "pursue_goal", "gear_up", 1);
        h.assertTrue(started.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + started.message());
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "alive, fuse " + creeper.getSwellDir() : "gone") + ", health " + c.getHealth() + ", gap " + String.format("%.1f", c.distanceTo(creeper));
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.isDeadOrDying()) { h.succeed(); return; }
            h.assertTrue(creeper.isAlive(), "The creeper blew up, it was not struck" + trace);
            h.assertTrue(h.getTick() - begun < STRIKE_TICKS, "The creeper was not struck in time" + trace);
        });
    }

    /**
     * The fuse lit two and a half blocks off, a sword in hand and no order: a blow lands within half a second, he is four blocks off
     * in twenty ticks (or the creeper is gone), and the blast does not reach him.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void aLitFuseIsStruckBeforeHeRuns(GameTestHelper h) { strikeBeforeTheRun(h, setup(h), true); }

    /** The same with no weapon at all: the blow is a punch. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void aLitFuseIsPunchedBeforeHeRunsWithNoWeapon(GameTestHelper h) {
        CompanionEntity c = setup(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        strikeBeforeTheRun(h, c, false);
    }

    private static void strikeBeforeTheRun(GameTestHelper h, CompanionEntity c, boolean gapAtRun) {
        Creeper creeper = creeper(h, new Vec3(19, 1, 16.5));
        creeper.ignite();
        long begun = h.getTick();
        h.onEachTick(() -> {
            long elapsed = h.getTick() - begun;
            String trace = " | tick " + elapsed + ", gap " + String.format("%.1f", c.distanceTo(creeper)) + (creeper.isAlive() ? ", creeper health " + creeper.getHealth() : " (gone)")
                    + ", health " + c.getHealth();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (elapsed == BLOW_TICKS) h.assertTrue(creeper.isDeadOrDying() || creeper.getHealth() < creeper.getMaxHealth(), "No blow before the run" + trace);
            if (gapAtRun && elapsed == RUN_TICKS) h.assertTrue(!creeper.isAlive() || c.distanceTo(creeper) >= RUN_GAP, "Less than 4 blocks off in 20 ticks" + trace);
            if (elapsed >= WATCH_TICKS) {
                h.assertTrue(c.getHealth() >= c.getMaxHealth(), "The blast reached him" + trace);
                h.succeed();
            }
        });
    }

    /** Guarding the player with a sword, a creeper that walks up to him is struck and run from until it is dead, and he is never hurt. */
    @GameTest(template = "tall_tree", batch = "creeper_flee_goal", timeoutTicks = 1200)
    public static void aGuardWithASwordKillsAWalkingCreeperUnhurt(GameTestHelper h) { killUnhurt(h, setup(h), KILL_TICKS); }

    /** The same with his fists: a punch a round, so it takes longer, and still no blast reaches him. */
    @GameTest(template = "tall_tree", batch = "creeper_flee_goal", timeoutTicks = FIST_KILL_TICKS + 200)
    public static void aGuardWithNoWeaponPunchesAWalkingCreeperDeadUnhurt(GameTestHelper h) {
        CompanionEntity c = setup(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        killUnhurt(h, c, FIST_KILL_TICKS);
    }

    private static void killUnhurt(GameTestHelper h, CompanionEntity c, int within) {
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        player.setInvulnerable(true);
        CompanionActionResult guard = CompanionEvents.executeAction(player, "protect", "", 1);
        h.assertTrue(guard.status() != CompanionActionResult.Status.FAILED, "Guard not taken: " + guard.message());
        Creeper creeper = h.spawn(EntityType.CREEPER, new Vec3(24.5, 1, 16.5));
        creeper.setPersistenceRequired();
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "alive, health " + creeper.getHealth() + ", fuse " + creeper.getSwellDir() : "gone")
                    + ", health " + c.getHealth() + ", gap " + String.format("%.1f", c.distanceTo(creeper));
            h.assertTrue(c.getHealth() >= c.getMaxHealth(), "Hurt" + trace);
            if (creeper.isDeadOrDying()) { h.succeed(); return; }
            h.assertTrue(creeper.isAlive(), "The creeper blew up" + trace);
            h.assertTrue(h.getTick() - begun < within, "The creeper was not killed in time" + trace);
        });
    }

    /**
     * A creeper by the house, the player close enough for the melee goal to go for it too: no blow lands on it until it is walked off the
     * house (it starts three blocks from it), then he kills it, and it never blows up.
     */
    @GameTest(template = "tall_tree", batch = "creeper_flee_goal", timeoutTicks = 1400)
    public static void byTheHouseHeWalksTheCreeperOffBeforeTheFirstBlow(GameTestHelper h) {
        CompanionEntity c = setup(h);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        player.setInvulnerable(true);
        BlockPos home = h.absolutePos(new BlockPos(4, 1, 16));
        player.teleportTo(home.getX() + .5, home.getY(), home.getZ() + .5);
        CompanionActionResult named = CompanionEvents.executeAction(player, "set_home", "", 1);
        h.assertTrue(named.status() != CompanionActionResult.Status.FAILED, "Home not named: " + named.message());
        BlockPos away = h.absolutePos(new BlockPos(2, 1, 2));
        player.teleportTo(away.getX() + .5, away.getY(), away.getZ() + .5);
        c.teleportTo(h.absolutePos(new BlockPos(8, 1, 16)).getX() + .5, home.getY(), home.getZ() + .5);
        Creeper creeper = h.spawn(EntityType.CREEPER, new Vec3(3.5, 1, 13.5));
        creeper.setPersistenceRequired();
        Vec3 house = Vec3.atBottomCenterOf(home);
        float[] health = {creeper.getHealth()};
        long begun = h.getTick();
        h.onEachTick(() -> {
            double fromHouse = creeper.position().distanceTo(house);
            String trace = " | creeper " + (creeper.isAlive() ? "alive, health " + creeper.getHealth() + ", fuse " + creeper.getSwellDir() : "gone")
                    + ", " + String.format("%.1f", fromHouse) + " from the house, health " + c.getHealth();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.getHealth() < health[0]) h.assertTrue(fromHouse >= HOUSE_NEAR, "Struck by the house" + trace);
            health[0] = creeper.getHealth();
            if (creeper.isDeadOrDying()) { h.succeed(); return; }
            h.assertTrue(creeper.isAlive(), "The creeper blew up" + trace);
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The creeper was not killed in time" + trace);
        });
    }

    /** A creeper that does not walk: it stays where it was put, in sight, whatever he does. */
    private static Creeper creeper(GameTestHelper h, Vec3 at) {
        Creeper creeper = h.spawn(EntityType.CREEPER, at);
        creeper.setPersistenceRequired();
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        return creeper;
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = h.absolutePos(new BlockPos(14, 1, 15));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        return companion;
    }
}

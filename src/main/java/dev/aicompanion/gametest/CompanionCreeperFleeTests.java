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
 * A creeper that sees him on open ground, with no mine: unarmed, he steps away from one that has not lit its fuse and dashes from one that
 * has; with a sword, in any mode, he strikes a lit one first and then runs, and by the house he walks it off before the first blow.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCreeperFleeTests {
    private static final double SEES_WITHIN = 9, RUN_GAP = 4, HOUSE_NEAR = 10;
    private static final int RUN_TICKS = 20, WATCH_TICKS = 60, STRIKE_TICKS = 400, BLOW_TICKS = 10, KILL_TICKS = 1000;

    private CompanionCreeperFleeTests() { }

    @AfterBatch(batch = "creeper_flee_goal") public static void afterGoal(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Five blocks off and unlit, with no weapon to meet it and no order to keep him: he goes out of the range at which the guard sees a creeper. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void anUnlitCreeperInSightMakesHimStepAwayWithNoOrder(GameTestHelper h) {
        CompanionEntity c = setup(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        Creeper creeper = creeper(h, new Vec3(21.5, 1, 16.5));
        h.succeedWhen(() -> {
            h.assertTrue(c.isAlive(), "Died");
            h.assertTrue(creeper.getSwellDir() <= 0, "The creeper lit its fuse, the test is no longer about an unlit one");
            h.assertTrue(c.distanceTo(creeper) > SEES_WITHIN, "Still " + String.format("%.1f", c.distanceTo(creeper)) + " blocks from the creeper");
        });
    }

    /** The fuse lit two and a half blocks off: four blocks off in twenty ticks, and alive when it blows or goes out. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void aLitFuseGetsHimFourBlocksOffInTwentyTicks(GameTestHelper h) {
        CompanionEntity c = setup(h);
        Creeper creeper = creeper(h, new Vec3(19, 1, 16.5));
        creeper.ignite();
        long begun = h.getTick();
        h.onEachTick(() -> {
            long elapsed = h.getTick() - begun;
            double gap = c.distanceTo(creeper);
            String trace = " | tick " + elapsed + ", gap " + String.format("%.1f", gap) + (creeper.isAlive() ? "" : " (gone)") + ", health " + c.getHealth();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (elapsed == RUN_TICKS) h.assertTrue(!creeper.isAlive() || gap >= RUN_GAP, "Less than 4 blocks off in 20 ticks" + trace);
            if (elapsed >= WATCH_TICKS) h.succeed();
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

    /** The fuse lit two and a half blocks off, a sword in hand and no order: a blow lands within half a second, and the blast does not reach him. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void aLitFuseIsStruckBeforeHeRuns(GameTestHelper h) {
        CompanionEntity c = setup(h);
        Creeper creeper = creeper(h, new Vec3(19, 1, 16.5));
        creeper.ignite();
        long begun = h.getTick();
        h.onEachTick(() -> {
            long elapsed = h.getTick() - begun;
            String trace = " | tick " + elapsed + ", gap " + String.format("%.1f", c.distanceTo(creeper)) + (creeper.isAlive() ? ", creeper health " + creeper.getHealth() : " (gone)")
                    + ", health " + c.getHealth();
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (elapsed == BLOW_TICKS) h.assertTrue(creeper.isDeadOrDying() || creeper.getHealth() < creeper.getMaxHealth(), "No blow before the run" + trace);
            if (elapsed >= WATCH_TICKS) {
                h.assertTrue(c.getHealth() >= c.getMaxHealth(), "The blast reached him" + trace);
                h.succeed();
            }
        });
    }

    /** Guarding the player with a sword, a creeper that walks up to him is struck and run from until it is dead, and he is never hurt. */
    @GameTest(template = "tall_tree", batch = "creeper_flee_goal", timeoutTicks = 1200)
    public static void aGuardWithASwordKillsAWalkingCreeperUnhurt(GameTestHelper h) {
        CompanionEntity c = setup(h);
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
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The creeper was not killed in time" + trace);
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

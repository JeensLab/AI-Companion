package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Air like a player's: in a flooded tunnel with a ceiling the game never floats him up, and with less than a third of his air
 * left he must leave what he is doing, swim to air, breathe and go back. Work here is «come»: mining at water is still
 * refused, the tunnel is where the water is.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBreathTests {
    private CompanionBreathTests() { }

    /** A one-block-wide tunnel along x at z 16, flooded from x 7 to 55; an air chamber at each end (on the tunnel's floor, higher up). */
    private static void tunnel(GameTestHelper h, int waterTo) {
        box(h, 0, 0, 14, 63, 0, 18, Blocks.BEDROCK);
        box(h, 0, 1, 14, 63, 7, 18, Blocks.STONE);
        box(h, 1, 2, 16, 6, 5, 16, Blocks.AIR);
        box(h, 57, 2, 16, 62, 5, 16, Blocks.AIR);
        box(h, 7, 2, 16, waterTo, 3, 16, Blocks.WATER);
        box(h, waterTo + 1, 2, 16, 56, 3, 16, Blocks.AIR);
    }

    private static CompanionEntity oleg(GameTestHelper h, int x, int playerX) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, 2, 16);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(playerX, 2, 16));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16); c.setLifestyle(false, false);
        return c;
    }

    /** What the tick-by-tick watch saw: the lowest health, the lowest air, and whether the air was filled again after being low. */
    private static final class Watch {
        float health = 100; int air = 300; boolean refilled;
        Watch(GameTestHelper h, CompanionEntity c) {
            h.onEachTick(() -> {
                health = Math.min(health, c.getHealth()); air = Math.min(air, c.getAirSupply());
                if (air < 100 && c.getAirSupply() >= c.getMaxAirSupply()) refilled = true;
            });
        }
    }

    /**
     * He is sent to the player, who stands in the air chamber at the end of the flooded tunnel, and his air is low a few blocks in.
     * The search for a route cannot start from under the tunnel's ceiling, so it is the trip to the air that gets him out: then
     * he breathes, and the order, only paused, is finished without a point of drowning damage.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void heSurfacesForAirInTheMiddleOfWorkAndFinishesIt(GameTestHelper h) {
        tunnel(h, 55);
        CompanionEntity c = oleg(h, 14, 2);
        var watch = new Watch(h, c);
        h.runAfterDelay(10, () -> {
            h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
            c.setAirSupply(80);
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 10 && !c.isWorking() && c.didLastTaskSucceed() && c.distanceToSqr(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 16.5))) <= 25,
                    "Not at the player yet: " + c.getLastTaskResult() + " alive " + c.isAlive() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(watch.health >= c.getMaxHealth(), "Drowned: health down to " + watch.health + ", air down to " + watch.air);
            h.assertTrue(watch.refilled, "Never filled his lungs again, lowest air " + watch.air);
        });
    }

    /** No work at all, standing in the flooded tunnel with a ceiling over him: he does not stand there and drown. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void idleUnderWaterHeSwimsToAirAndBreathes(GameTestHelper h) {
        tunnel(h, 55);
        CompanionEntity c = oleg(h, 14, 60);
        c.setFollowing(false);
        var watch = new Watch(h, c);
        h.runAfterDelay(10, () -> c.setAirSupply(80));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 10 && !c.isEyeInFluid(FluidTags.WATER) && c.getAirSupply() >= c.getMaxAirSupply(),
                    "Not breathing in the air: alive " + c.isAlive() + " air " + c.getAirSupply() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(watch.health >= c.getMaxHealth(), "Drowned: health down to " + watch.health + ", air down to " + watch.air);
        });
    }

    /** «Стоп» on the way to the air stops the work, not his breath: he still swims to the air and does not drown. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void stopOnTheWayToAirStopsTheWorkNotTheBreath(GameTestHelper h) {
        tunnel(h, 55);
        CompanionEntity c = oleg(h, 14, 60);
        var watch = new Watch(h, c);
        h.runAfterDelay(10, () -> {
            h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
            c.setAirSupply(80);
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(CompanionEvents.executeAction(h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId()), "stop", "", 1).success(), "Stop not accepted");
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 20 && !c.isWorking() && !c.isEyeInFluid(FluidTags.WATER) && c.getAirSupply() >= c.getMaxAirSupply(),
                    "Not breathing in the air after the stop: working " + c.isWorking() + " alive " + c.isAlive() + " air " + c.getAirSupply());
            h.assertTrue(watch.health >= c.getMaxHealth(), "Drowned after the stop: health down to " + watch.health + ", air down to " + watch.air);
        });
    }

    /**
     * Sealed in a flooded box, the air a stone block away behind a wall: no way to it. The work stops and tells why, he does not
     * stand still but swims for the air he can see (to the wall).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void withNoWayToAirTheWorkStopsAndTellsWhy(GameTestHelper h) {
        // Stone all round, so that the nearest air he can see is the chamber behind the wall, not the open air outside the arena.
        box(h, 0, 1, 3, 30, 13, 29, Blocks.STONE);
        tunnel(h, 25);
        box(h, 6, 2, 16, 6, 3, 16, Blocks.STONE);
        box(h, 26, 2, 16, 26, 3, 16, Blocks.STONE);
        CompanionEntity c = oleg(h, 14, 24);
        h.runAfterDelay(5, () -> {
            h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
            c.setAirSupply(80);
        });
        double[] nearest = {1e9};
        h.onEachTick(() -> nearest[0] = Math.min(nearest[0], c.position().distanceTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(7.5, 2, 16.5)))));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 5 && !c.isWorking() && !c.didLastTaskSucceed() && c.getLastTaskResult().contains("воздух"),
                    "The work did not stop with the reason: " + c.getLastTaskResult() + " working " + c.isWorking() + " alive " + c.isAlive());
            h.assertTrue(nearest[0] <= 3, "Did not swim for the air: nearest to the wall " + nearest[0] + ", at " + h.relativePos(c.blockPosition()).toShortString());
        });
    }

    private static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(new BlockPos(x, y, z), block);
    }
}

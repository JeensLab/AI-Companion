package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Riding with the player (idea 5949G «Лодки», #105): the player gets into a boat, he comes and takes the other seat; the player
 * steps ashore, he steps out onto land too. A boat without the player is not his to sit in.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBoatRideTests {
    private CompanionBoatRideTests() { }

    /**
     * The player sits in a boat two blocks off the shore, he stands on the shore six off it: following, he comes and gets in behind
     * the player, who steers; told to stay, or with the switch off, he stays where he is.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void followingHeGetsIntoThePlayersBoatButNotToldToStayOrSwitchedOff(GameTestHelper h) {
        Lane go = new Lane(h, 2, 9.5), stay = new Lane(h, 10, 9.5), off = new Lane(h, 18, 9.5);
        go.player.startRiding(go.boat);
        stay.player.startRiding(stay.boat);
        off.player.startRiding(off.boat);
        stay.oleg.setFollowing(false);
        off.oleg.setBoatRide(false);
        h.runAtTickTime(200, () -> {
            h.assertTrue(go.oleg.getVehicle() == go.boat, "Following, he did not get in: riding " + go.oleg.getVehicle() + " at " + go.oleg.position());
            h.assertTrue(go.boat.getFirstPassenger() == go.player, "The player does not steer: " + go.boat.getPassengers());
            h.assertTrue(!stay.oleg.isPassenger(), "Told to stay, he got in");
            h.assertTrue(!off.oleg.isPassenger(), "Switched off, he got in");
            h.succeed();
        });
    }

    /**
     * Both in the boat beside the shore, he looking along it over the water (the shore on his left): the player steps ashore, and he
     * steps out onto the shore too, not into the water nor onto the boat.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void thePlayerSteppedAshoreAndHeStepsOutOntoTheShoreNotIntoTheWater(GameTestHelper h) {
        Lane lane = new Lane(h, 2, 10.8);
        lane.player.startRiding(lane.boat);
        lane.oleg.startRiding(lane.boat);
        Vec3[] out = {null};
        h.onEachTick(() -> { if (out[0] == null && h.getTick() > 10 && !lane.oleg.isPassenger()) out[0] = lane.oleg.position().subtract(h.absoluteVec(Vec3.ZERO)); });
        h.runAtTickTime(10, () -> {
            h.assertTrue(lane.oleg.getVehicle() == lane.boat, "He is not in the boat to begin with");
            lane.player.stopRiding();
            var shore = h.absoluteVec(new Vec3(15.5, 3, 4.5));
            lane.player.teleportTo(shore.x, shore.y, shore.z);
        });
        h.succeedWhen(() -> {
            h.assertTrue(out[0] != null, "He stayed in the boat");
            h.assertTrue(out[0].x >= 12 && Math.abs(out[0].y - 3) < .01, "He stepped out into the water or onto the boat: at " + out[0]);
        });
    }

    /** The player in a chest boat, its one seat theirs: he does not get in, and says so once, not over and over. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void inAChestBoatThereIsNoSeatForHimAndHeSaysSoOnce(GameTestHelper h) {
        Lane lane = new Lane(h, 2, 9.5, EntityType.CHEST_BOAT);
        lane.player.startRiding(lane.boat);
        int[] said = {0}, serial = {lane.oleg.autonomyLineSerial()};
        h.onEachTick(() -> {
            if (lane.oleg.autonomyLineSerial() == serial[0]) return;
            serial[0] = lane.oleg.autonomyLineSerial();
            if ("Места в лодке нет — поплыву рядом.".equals(lane.oleg.autonomyLine())) said[0]++;
        });
        h.runAtTickTime(150, () -> {
            h.assertTrue(!lane.oleg.isPassenger(), "He got into a chest boat");
            h.assertTrue(said[0] == 1, "Said there is no seat " + said[0] + " times");
            h.succeed();
        });
    }

    /** The game seated him in an empty boat by the shore (a boat seats a mob it bumps into), the player ashore: he steps out onto the shore. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void inABoatWithoutThePlayerHeStepsOutOntoTheShore(GameTestHelper h) {
        Lane lane = new Lane(h, 2, 10.8);
        var shore = h.absoluteVec(new Vec3(18.5, 3, 4.5));
        lane.player.teleportTo(shore.x, shore.y, shore.z);
        lane.oleg.startRiding(lane.boat);
        Vec3[] out = {null};
        h.onEachTick(() -> { if (out[0] == null && h.getTick() > 1 && !lane.oleg.isPassenger()) out[0] = lane.oleg.position().subtract(h.absoluteVec(Vec3.ZERO)); });
        h.runAtTickTime(100, () -> {
            h.assertTrue(out[0] != null && !lane.oleg.isPassenger(), "He sits in the boat: first out at " + out[0]);
            h.assertTrue(out[0].x >= 12 && Math.abs(out[0].y - 3) < .01, "He stepped out into the water or onto the boat: at " + out[0]);
            h.succeed();
        });
    }

    /** The switch lives in the save with him; an old save has it on. */
    @GameTest(template = "empty")
    public static void theBoatRideSwitchIsSavedAndAnOldSaveHasItOn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 4);
        h.assertTrue(c.boatRide(), "The switch is not on by default");
        c.setBoatRide(false);
        CompanionEntity other = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        other.load(c.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(!other.boatRide(), "The switch was not saved");
        CompanionEntity third = h.spawn(AICompanionMod.COMPANION.get(), 12, 1, 12);
        third.setBoatRide(false);
        third.load(new net.minecraft.nbt.CompoundTag());
        h.assertTrue(third.boatRide(), "An old save loaded with the switch off");
        h.succeed();
    }

    /**
     * A strip of shore and water five blocks wide along x from z0, walled off from the next: water two deep (x 0..11, its top at
     * y 3), stone shore level with it beyond (x 12..23). His player stands beside a boat on the water at boatX (the shore's edge
     * at 12), not in it yet; he stands on the shore at x 16.5.
     */
    private static final class Lane {
        final CompanionEntity oleg;
        final ServerPlayer player;
        final Boat boat;

        Lane(GameTestHelper h, int z0, double boatX) { this(h, z0, boatX, EntityType.BOAT); }

        Lane(GameTestHelper h, int z0, double boatX, EntityType<? extends Boat> type) {
            for (int x = 0; x < 24; x++) for (int z = z0 - 1; z <= z0 + 5; z++) for (int y = 0; y <= 8; y++) {
                boolean wall = z == z0 - 1 || z == z0 + 5;
                var block = y == 0 ? Blocks.STONE : wall ? (y <= 4 ? Blocks.STONE : Blocks.AIR)
                        : y <= 2 ? (x < 12 ? Blocks.WATER : Blocks.STONE) : Blocks.AIR;
                h.setBlock(new BlockPos(x, y, z), block);
            }
            oleg = h.spawn(AICompanionMod.COMPANION.get(), new Vec3(16.5, 3, z0 + 2.5));
            boat = h.spawn(type, new Vec3(boatX, 3, z0 + 2.5));
            player = h.makeMockServerPlayerInLevel();
            var at = h.absoluteVec(new Vec3(boatX, 3, z0 + 1.2));
            player.teleportTo(at.x, at.y, at.z);
            oleg.setOwnerId(player.getUUID());
            player.getPersistentData().putUUID("aicompanion:canonical_companion", oleg.getUUID());
            CompanionEvents.getOwnedCompanion(player);
        }
    }
}

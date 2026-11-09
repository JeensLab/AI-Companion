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
     * The player sits in a boat two blocks off a bank a block high, he stands on the bank six off it: following, he comes and gets in
     * behind the player, who steers; told to stay, or with the switch off, he stays where he is.
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
     * Both in the boat, he looking along it: the player steps onto the bank a block high beside it, and he steps out onto the bank
     * too, not onto the boat's top nor into the water, also with the switch turned off on the way; the player drops into open
     * water, and he steps out into the water beside the boat.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void thePlayerOutHeStepsOutOntoTheBankOrIntoTheWaterNotOntoTheBoat(GameTestHelper h) {
        Lane bank = new Lane(h, 2, 10.8), off = new Lane(h, 10, 10.8), open = new Lane(h, 18, 5.5);
        Lane[] lanes = {bank, off, open};
        Vec3[] out = new Vec3[3];
        for (Lane lane : lanes) lane.player.startRiding(lane.boat);
        // By the bank he gets in himself; out on open water he is put in (he would swim there first).
        open.oleg.startRiding(open.boat);
        boolean[] left = {false};
        h.onEachTick(() -> {
            if (!left[0]) {
                if (!java.util.Arrays.stream(lanes).allMatch(lane -> lane.oleg.getVehicle() == lane.boat)) return;
                left[0] = true;
                off.oleg.setBoatRide(false);
                bank.ashore(h, 15.5);
                off.ashore(h, 15.5);
                open.player.stopRiding();
                var water = h.absoluteVec(new Vec3(2.5, 2, 20.5));
                open.player.teleportTo(water.x, water.y, water.z);
                return;
            }
            for (int i = 0; i < 3; i++) {
                if (out[i] == null && !lanes[i].oleg.isPassenger()) out[i] = lanes[i].oleg.position().subtract(h.absoluteVec(Vec3.ZERO));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(left[0], "He did not get in by the bank: " + bank.oleg.getVehicle() + ", " + off.oleg.getVehicle());
            for (int i = 0; i < 2; i++) {
                h.assertTrue(out[i] != null, "Lane " + i + ": he stayed in the boat");
                h.assertTrue(out[i].x >= 12 && Math.abs(out[i].y - 4) < .01, "Lane " + i + ": he stepped out into the water or onto the boat: at " + out[i]);
            }
            h.assertTrue(out[2] != null, "Open water: he stayed in the boat");
            h.assertTrue(Math.abs(out[2].y - 2) < .01, "Open water: he stepped out onto the boat, not into the water: at " + out[2]);
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

    /**
     * A boat without the player does not take him: the game seats a mob an empty boat bumps into by this call (Boat.tick), and he
     * would be out of it again in the same tick, set down beside it. Switched off, it takes him, as before.
     */
    @GameTest(template = "tall_tree")
    public static void anEmptyBoatDoesNotTakeHim(GameTestHelper h) {
        Lane lane = new Lane(h, 2, 9.5);
        h.assertTrue(!lane.oleg.startRiding(lane.boat), "The empty boat took him");
        lane.oleg.setBoatRide(false);
        h.assertTrue(lane.oleg.startRiding(lane.boat), "Switched off, the empty boat did not take him");
        h.succeed();
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
     * A strip of water and bank five blocks wide along x from z0, walled off from the next: water two deep (x 0..11, its top at
     * y 3), a stone bank a block over it beyond (x 12..23, its top at 4), the most common shore. A boat floats at boatX (as high
     * as a boat floats), the bank's edge at 12; his player stands beside it in the water, not in it yet; he stands on the bank at
     * x 16.5.
     */
    private static final class Lane {
        final CompanionEntity oleg;
        final ServerPlayer player;
        final Boat boat;
        final int z0;

        Lane(GameTestHelper h, int z0, double boatX) { this(h, z0, boatX, EntityType.BOAT); }

        Lane(GameTestHelper h, int z0, double boatX, EntityType<? extends Boat> type) {
            this.z0 = z0;
            for (int x = 0; x < 24; x++) for (int z = z0 - 1; z <= z0 + 5; z++) for (int y = 0; y <= 8; y++) {
                boolean wall = z == z0 - 1 || z == z0 + 5;
                var block = y == 0 ? Blocks.STONE : wall ? (y <= 4 ? Blocks.STONE : Blocks.AIR)
                        : x < 12 ? (y <= 2 ? Blocks.WATER : Blocks.AIR) : y <= 3 ? Blocks.STONE : Blocks.AIR;
                h.setBlock(new BlockPos(x, y, z), block);
            }
            oleg = h.spawn(AICompanionMod.COMPANION.get(), new Vec3(16.5, 4, z0 + 2.5));
            boat = h.spawn(type, new Vec3(boatX, 2.52, z0 + 2.5));
            player = h.makeMockServerPlayerInLevel();
            var at = h.absoluteVec(new Vec3(boatX, 2, z0 + 1.2));
            player.teleportTo(at.x, at.y, at.z);
            oleg.setOwnerId(player.getUUID());
            player.getPersistentData().putUUID("aicompanion:canonical_companion", oleg.getUUID());
            CompanionEvents.getOwnedCompanion(player);
        }

        /** The player out of the boat onto the bank at x. */
        void ashore(GameTestHelper h, double x) {
            player.stopRiding();
            var at = h.absoluteVec(new Vec3(x, 4, z0 + 2.5));
            player.teleportTo(at.x, at.y, at.z);
        }
    }
}

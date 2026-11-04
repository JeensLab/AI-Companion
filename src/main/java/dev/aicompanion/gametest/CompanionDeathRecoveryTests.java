package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBelongings;
import dev.aicompanion.entity.CompanionDeathSpots;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionTaskBoard;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** His own things after a death: what is worth a walk, where it lies, the walk back, and every way it can be gone. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDeathRecoveryTests {
    /** Junk is not worth a walk; one diamond is. */
    @GameTest(template = "empty")
    public static void junkIsNothingAndADiamondIsWorthGoing(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.DIRT, 32));
        companion.setItem(1, new ItemStack(Items.COBBLESTONE, 32));
        companion.setItem(2, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(3, new ItemStack(Items.WHEAT_SEEDS, 8));
        h.assertTrue(CompanionBelongings.worth(companion) < CompanionBelongings.WORTH_GOING,
                "Junk was worth a walk: " + CompanionBelongings.worth(companion));
        companion.setItem(4, new ItemStack(Items.DIAMOND, 1));
        h.assertTrue(CompanionBelongings.worth(companion) >= CompanionBelongings.WORTH_GOING,
                "A diamond was not worth a walk: " + CompanionBelongings.worth(companion));
        companion.setItem(5, new ItemStack(Items.BREAD, 32));
        h.assertTrue(CompanionBelongings.worth(companion) >= CompanionBelongings.WORTH_GOING + 6,
                "A bag of food added nothing: " + CompanionBelongings.worth(companion));
        h.succeed();
    }

    /** A fall with things worth going back for is remembered with its price. */
    @GameTest(template = "empty")
    public static void aValuableDeathIsRemembered(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.DIAMOND, 2));
        BlockPos where = companion.blockPosition();
        companion.hurt(h.getLevel().damageSources().generic(), 1000);
        var spots = CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId());
        h.assertTrue(spots.size() == 1, "The fall was not remembered: " + spots.size());
        h.assertTrue(spots.get(0).pos().equals(where), "Remembered the wrong place: " + spots.get(0).pos());
        h.assertTrue(spots.get(0).worth() >= CompanionBelongings.WORTH_GOING, "Remembered it as worthless: " + spots.get(0).worth());
        h.succeed();
    }

    /** A bag of dirt and stone is left where it fell: no walk, no memory. */
    @GameTest(template = "empty")
    public static void aJunkDeathIsForgotten(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.DIRT, 32));
        companion.setItem(1, new ItemStack(Items.GRAVEL, 16));
        companion.hurt(h.getLevel().damageSources().generic(), 1000);
        var spots = CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId());
        h.assertTrue(spots.isEmpty(), "Junk was remembered: " + spots.size());
        h.succeed();
    }

    /** He walks to the place, the game's own pickup takes what lies there, and he says so honestly. */
    @GameTest(template = "empty")
    public static void theWalkTakesTheThingsAndForgetsThePlace(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(4, 0, 0);
        var item = belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            // The test area does not tick him: his own ticks run the walk and the goals that drive it.
            for (int tick = 0; tick < 400 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(!companion.deathTrip.active(), "The walk never ended");
            h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The diamond was not taken: " + item.getItem());
            h.assertTrue(companion.deathTrip.lastLine().equals("Забрал свои вещи."),
                    "He did not say he had them: " + companion.deathTrip.lastLine());
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "The place is still remembered");
        }).thenSucceed();
    }

    /** Things that slid away from the place are walked to one by one, not left because the spot itself was empty. */
    @GameTest(template = "empty")
    public static void thingsThatSlidAwayAreStillFetched(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition();
        var slid = belonging(h, place.offset(6, 0, 0), new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            for (int tick = 0; tick < 600 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(!companion.deathTrip.active(), "The walk never ended");
            h.assertTrue(slid.isRemoved() || slid.getItem().isEmpty(), "The diamond that slid away was not taken: " + slid.getItem());
        }).thenSucceed();
    }

    /** Water carries a stack far from the place: he still walks to it and takes it. */
    @GameTest(template = "empty")
    public static void thingsWashedAwayAreStillFetched(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition();
        var drifted = belonging(h, place.offset(20, 0, 0), new ItemStack(Items.IRON_INGOT, 4), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            for (int tick = 0; tick < 900 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(!companion.deathTrip.active(), "The walk never ended");
            h.assertTrue(drifted.isRemoved() || drifted.getItem().isEmpty(), "The ingots that drifted were not taken: " + drifted.getItem());
            h.assertTrue(companion.deathTrip.lastLine().equals("Забрал свои вещи."),
                    "He did not say he had them: " + companion.deathTrip.lastLine());
        }).thenSucceed();
    }

    /** Burned, blown up or otherwise destroyed: nothing of his is left, so the walk is not made at all. */
    @GameTest(template = "empty")
    public static void thingsDestroyedAreNotWalkedTo(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(10, 0, 0);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!companion.deathTrip.begin(), "He set out for things that are gone");
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "A place with nothing left is still remembered");
        }).thenSucceed();
    }

    /** A zombie picked the stack up and wears it: the ground is empty, so the walk is not made. */
    @GameTest(template = "empty")
    public static void thingsTakenByAMobAreNotWalkedTo(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        Zombie thief = h.spawn(EntityType.ZOMBIE, 22, 2, 16);
        thief.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        thief.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        BlockPos place = companion.blockPosition().offset(10, 0, 0);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!companion.deathTrip.begin(), "He set out after a zombie already took the things");
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "A place picked clean is still remembered");
        }).thenSucceed();
    }

    /** A thing the pickup cannot take is given up on and the walk ends honestly, not with a claim of having it. */
    @GameTest(template = "empty")
    public static void aThingHeCannotTakeIsLeftAndSaid(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var other = h.makeMockServerPlayerInLevel();
        BlockPos place = companion.blockPosition();
        var stuck = belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        stuck.setTarget(other.getUUID());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            for (int tick = 0; tick < 400 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(!companion.deathTrip.active(), "The walk never ended");
            h.assertTrue(!stuck.isRemoved() && !stuck.getItem().isEmpty(), "The thing aimed at another was taken anyway");
            h.assertTrue(companion.deathTrip.lastLine().contains("осталась"),
                    "He claimed more than he took: " + companion.deathTrip.lastLine());
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "The place is still remembered after the walk");
        }).thenSucceed();
    }

    /** Of two falls the dearer one is walked to first, as the owner asked. */
    @GameTest(template = "empty")
    public static void theDearerPlaceGoesFirst(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos cheap = companion.blockPosition().offset(6, 0, 0);
        BlockPos dear = companion.blockPosition().offset(-6, 0, 0);
        belonging(h, cheap, new ItemStack(Items.IRON_INGOT, 4), companion.getOwnerId());
        belonging(h, dear, new ItemStack(Items.DIAMOND, 8), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), cheap, 12, 0);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), dear, 80, 1);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            h.assertTrue(companion.deathTrip.describe().contains(dear.toShortString()),
                    "He set out for the cheaper place: " + companion.deathTrip.describe());
        }).thenSucceed();
    }

    /** Two falls with things worth having: the dearer first, then the next one without going back to the player. */
    @GameTest(template = "empty")
    public static void bothPlacesAreFetchedOneAfterAnother(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos dear = companion.blockPosition().offset(-2, 0, 0);
        BlockPos cheap = companion.blockPosition().offset(2, 0, 0);
        var dearItems = belonging(h, dear, new ItemStack(Items.DIAMOND, 6), companion.getOwnerId());
        var cheapItems = belonging(h, cheap, new ItemStack(Items.IRON_INGOT, 6), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), dear, 60, 0);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), cheap, 18, 1);
        h.startSequence().thenExecute(() -> {
            StringBuilder trace = new StringBuilder();
            for (int tick = 0; tick < 1200 && !CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(); tick++) {
                if (!companion.deathTrip.active() && companion.deathTrip.begin())
                    trace.append("set out: ").append(companion.deathTrip.describe()).append("; ");
                companion.tick();
            }
            h.assertTrue(dearItems.isRemoved() || dearItems.getItem().isEmpty(),
                    "The dearer place was not fetched: " + dearItems.getItem() + " | " + trace + " | at " + companion.blockPosition());
            h.assertTrue(cheapItems.isRemoved() || cheapItems.getItem().isEmpty(), "The cheaper place was left behind: " + cheapItems.getItem());
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "A place is still remembered after both walks");
        }).thenSucceed();
    }

    /** A new fall while he walks to an old one is taken up after the walk he is on, not forgotten. */
    @GameTest(template = "empty")
    public static void aNewFallDuringTheWalkIsTakenUpAfterwards(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos first = companion.blockPosition().offset(3, 0, 0);
        BlockPos second = companion.blockPosition().offset(-3, 0, 0);
        var firstItems = belonging(h, first, new ItemStack(Items.DIAMOND, 4), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), first, 40, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            var secondItems = belonging(h, second, new ItemStack(Items.IRON_INGOT, 8), companion.getOwnerId());
            CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), second, 24, 1);
            for (int tick = 0; tick < 1200 && !CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(); tick++) {
                if (!companion.deathTrip.active()) companion.deathTrip.begin();
                companion.tick();
            }
            h.assertTrue(firstItems.isRemoved() || firstItems.getItem().isEmpty(), "The first place was not fetched");
            h.assertTrue(secondItems.isRemoved() || secondItems.getItem().isEmpty(), "The new fall was forgotten: " + secondItems.getItem());
        }).thenSucceed();
    }

    /** Dying on the way back with things on him: the walk stops and the new place is remembered for later. */
    @GameTest(template = "empty")
    public static void dyingOnTheWalkRemembersTheNewPlace(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        companion.setItem(0, new ItemStack(Items.DIAMOND, 3));
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            companion.tick();
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).size() == 1,
                    "The old place was lost before the death: " + CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()));
            companion.hurt(h.getLevel().damageSources().generic(), 1000);
            h.assertTrue(!companion.deathTrip.active(), "The walk survived his own death");
            var spots = CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId());
            h.assertTrue(spots.size() >= 2, "The new fall was not remembered: " + spots.size() + " " + spots);
        }).thenSucceed();
    }

    /** Only what survived counts: a pile that burned down to dirt is not worth a walk, however dear it was. */
    @GameTest(template = "empty")
    public static void onlyWhatSurvivedCounts(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(8, 0, 0);
        belonging(h, place, new ItemStack(Items.DIRT, 12), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 40, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!companion.deathTrip.begin(), "He walked for what burned down to dirt");
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "A place worth nothing now is still remembered");
        }).thenSucceed();
    }

    /** A little of it left is still not a walk: the price is what lies there now, not what fell. */
    @GameTest(template = "empty")
    public static void aLittleSurvivedIsNotWalkedTo(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(8, 0, 0);
        belonging(h, place, new ItemStack(Items.IRON_INGOT, 1), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 40, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!companion.deathTrip.begin(), "He walked for a single ingot out of a dear pile");
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "A place worth too little now is still remembered");
        }).thenSucceed();
    }

    /** Another dimension is crossed only for a place that is really dear, and only when there is a portal to cross by. */
    @GameTest(template = "empty")
    public static void anotherDimensionNeedsAPortalAndAWorthwhilePlace(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition();
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), Level.NETHER, place, 40, 0);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), Level.NETHER, place.offset(0, 0, 2), 8, 1);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!companion.deathTrip.begin(), "He set out across dimensions with no portal");
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "A crossing that could not be made is still remembered");
        }).thenSucceed();
    }

    /** A step of a goal of his is not the player insisting: the walk goes on and the refusal stays for the player. */
    @GameTest(template = "empty")
    public static void aGoalStepDoesNotTakeTheRefusal(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos place = companion.blockPosition().offset(40, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            var step = CompanionEvents.executeGoal(owner, "gather_stone", "", 1, "auto", "test-trace", "near_player");
            h.assertTrue(step.status() != dev.aicompanion.entity.CompanionActionResult.Status.BLOCKED,
                    "A goal step was refused: " + step.status());
            h.assertTrue(companion.deathTrip.active(), "A goal step cancelled the walk");
            var order = CompanionEvents.executeAction(owner, "gather_stone", "", 1);
            h.assertTrue(order.status() == dev.aicompanion.entity.CompanionActionResult.Status.BLOCKED,
                    "The player's first order was not refused after a goal step: " + order.status());
        }).thenSucceed();
    }

    /** Naming the home is a word, not an order: the walk goes on. */
    @GameTest(template = "empty")
    public static void namingTheHomeDoesNotRefuse(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos place = companion.blockPosition().offset(40, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            var home = CompanionEvents.executeAction(owner, "set_home", "", 1);
            h.assertTrue(home.status() != dev.aicompanion.entity.CompanionActionResult.Status.BLOCKED,
                    "Naming the home was refused: " + home.status());
            h.assertTrue(companion.deathTrip.active(), "Naming the home cancelled the walk");
        }).thenSucceed();
    }

    /** Things that vanish on the way (burned, taken by a mob) are not claimed: he says the place is empty. */
    @GameTest(template = "empty")
    public static void thingsThatVanishAreNotClaimed(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(20, 0, 0);
        var item = belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            for (int tick = 0; tick < 10; tick++) companion.tick();
            item.discard();
            for (int tick = 0; tick < 400 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(!companion.deathTrip.active(), "The walk never ended");
            h.assertTrue(companion.deathTrip.lastLine().equals("Вещей на месте уже нет."),
                    "He claimed what he never had: " + companion.deathTrip.lastLine());
        }).thenSucceed();
    }

    /** A place in a chunk nobody has loaded is walked to all the same: the record is trusted until it can be seen. */
    @GameTest(template = "empty")
    public static void aPlaceInAnUnloadedChunkIsStillWalkedTo(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos far = companion.blockPosition().offset(100000, 0, 0);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), far, 40, 0);
        h.startSequence().thenExecute(() -> {
            // The chunk cannot be looked at from here, so the record is trusted and the walk is made; the chunk loads on the way.
            h.assertTrue(companion.deathTrip.begin(), "He gave up on a place he could not look at");
            h.assertTrue(companion.deathTrip.active(), "The walk did not start");
        }).thenSucceed();
    }

    /** The places outlive a reload: what is written comes back the same. */
    @GameTest(template = "empty")
    public static void spotsSurviveASaveAndLoad(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var server = h.getLevel().getServer();
        BlockPos place = companion.blockPosition().offset(5, 0, 0);
        CompanionDeathSpots.get(server).died(companion.getOwnerId(), h.getLevel().dimension(), place, 42, 7);
        var saved = CompanionDeathSpots.get(server).save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
        var loaded = CompanionDeathSpots.load(saved, server.registryAccess());
        var spots = loaded.all(companion.getOwnerId());
        h.assertTrue(spots.size() == 1, "The place did not survive the save: " + spots.size());
        h.assertTrue(spots.get(0).pos().equals(place), "The place came back wrong: " + spots.get(0).pos());
        h.assertTrue(spots.get(0).worth() == 42, "The price came back wrong: " + spots.get(0).worth());
        h.assertTrue(spots.get(0).dimension().equals(h.getLevel().dimension()), "The dimension came back wrong");
        h.succeed();
    }

    /** Cancelling from the task screen clears the walk and its place. */
    @GameTest(template = "empty")
    public static void cancellingClearsThePlace(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos place = companion.blockPosition().offset(40, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            var shown = CompanionTaskBoard.read(owner);
            CompanionTaskBoard.control(owner, shown, "cancel_recovery");
            h.assertTrue(!companion.deathTrip.active(), "The walk survived the cancel");
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "The cancelled place is still remembered");
        }).thenSucceed();
    }

    /** A place he cannot walk to at all — another dimension, the void — is dropped without a walk. */
    @GameTest(template = "empty")
    public static void unreachablePlacesAreDropped(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition();
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), Level.NETHER, place, 20, 0);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(),
                new BlockPos(place.getX(), h.getLevel().getMinBuildHeight(), place.getZ()), 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!companion.deathTrip.begin(), "He set out for a place he cannot reach");
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "An unreachable place is still remembered");
        }).thenSucceed();
    }

    /** An order waits once: the first ask is refused with a word, the second one takes the walk away. */
    @GameTest(template = "empty")
    public static void anOrderIsRefusedOnceThenObeys(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos place = companion.blockPosition().offset(40, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            var first = CompanionEvents.executeAction(owner, "gather_stone", "", 1);
            h.assertTrue(first.status() == dev.aicompanion.entity.CompanionActionResult.Status.BLOCKED,
                    "The first order was not refused: " + first.status());
            h.assertTrue(first.message().contains("вещи"), "The refusal does not say why: " + first.message());
            h.assertTrue(companion.deathTrip.active(), "The walk was dropped on the first ask");
            var second = CompanionEvents.executeAction(owner, "gather_stone", "", 1);
            h.assertTrue(second.status() != dev.aicompanion.entity.CompanionActionResult.Status.BLOCKED,
                    "The second order was refused too: " + second.status());
            h.assertTrue(!companion.deathTrip.active(), "The walk survived the second ask");
        }).thenSucceed();
    }

    /** The walk is a task the player can see in the task screen, and cancel from it. */
    @GameTest(template = "empty")
    public static void theWalkIsShownAndCanBeCancelled(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos place = companion.blockPosition().offset(40, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            var shown = CompanionTaskBoard.read(owner);
            h.assertTrue(shown != null && !shown.recovery().isBlank(), "The task screen does not show the walk: " + shown);
            h.assertTrue(shown.recovery().contains("вещами"), "The task does not say what it is: " + shown.recovery());
            CompanionTaskBoard.control(owner, shown, "cancel_recovery");
            h.assertTrue(!companion.deathTrip.active(), "The walk survived the cancel");
        }).thenSucceed();
    }

    /** A stack that fell off him carries his mark; what somebody else dropped is not part of the walk. */
    private static ItemEntity belonging(GameTestHelper h, BlockPos pos, ItemStack stack, UUID owner) {
        var item = new ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY() + .2, pos.getZ() + .5, stack);
        item.getPersistentData().putUUID("aicompanion:belongings", owner);
        h.getLevel().addFreshEntity(item);
        return item;
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 2, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() - 4);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
}

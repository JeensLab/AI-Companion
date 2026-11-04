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
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).size() == 1,
                    "A place with something left was forgotten");
            h.assertTrue(!companion.deathTrip.begin(), "He set out again at once for the same place");
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

    /** Two falls close together: the dearer one first, and the other on the way — one walk takes both. */
    @GameTest(template = "empty")
    public static void bothPlacesCloseByAreCollectedInOneWalk(GameTestHelper h) {
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
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).size() == 1,
                    "A dear place across dimensions was forgotten without a portal");
        }).thenSucceed();
    }

    /** A step of a goal of his is not the player insisting: the walk goes on and the refusal stays for the player. */
    @GameTest(template = "empty")
    public static void aGoalStepDoesNotTakeTheRefusal(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
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
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
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

    /** A bag of food alone is worth a walk, as the owner asked: more than twenty of it. */
    @GameTest(template = "empty")
    public static void aBagOfFoodAloneIsWorthGoing(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.BREAD, 21));
        h.assertTrue(CompanionBelongings.worth(companion) >= CompanionBelongings.WORTH_GOING,
                "Twenty-one bread was not worth a walk: " + CompanionBelongings.worth(companion));
        companion.setItem(0, new ItemStack(Items.BREAD, 20));
        h.assertTrue(CompanionBelongings.worth(companion) < CompanionBelongings.WORTH_GOING,
                "Twenty bread was worth a walk: " + CompanionBelongings.worth(companion));
        h.succeed();
    }

    /** A stack burned beside him is not claimed: only what reached the bag counts. */
    @GameTest(template = "empty")
    public static void thingsBurnedBesideHimAreNotClaimed(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition();
        var item = belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        // Out of the pickup's reach for a while: it can only vanish, and vanishing beside him is not taking.
        item.setPickUpDelay(200);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            // Beside him and out of the pickup's reach, then gone: burned, not taken.
            item.discard();
            for (int tick = 0; tick < 400 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(!companion.deathTrip.active(), "The walk never ended");
            h.assertTrue(companion.deathTrip.lastLine().equals("Вещей на месте уже нет."),
                    "He claimed what burned: " + companion.deathTrip.lastLine() + " | discarded=" + item.isRemoved()
                            + " | alive=" + item.isAlive());
        }).thenSucceed();
    }

    /** One stack taken and one left behind: he says part of it stayed and the place waits. */
    @GameTest(template = "empty")
    public static void aPartlyTakenPileIsSaidHonestly(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var other = h.makeMockServerPlayerInLevel();
        BlockPos place = companion.blockPosition();
        var taken = belonging(h, place, new ItemStack(Items.DIAMOND, 4), companion.getOwnerId());
        var left = belonging(h, place.offset(1, 0, 0), new ItemStack(Items.DIAMOND, 4), companion.getOwnerId());
        left.setTarget(other.getUUID());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            for (int tick = 0; tick < 400 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(taken.isRemoved() || taken.getItem().isEmpty(), "The reachable stack was not taken");
            h.assertTrue(!left.isRemoved() && !left.getItem().isEmpty(), "The stack aimed at another was taken");
            h.assertTrue(companion.deathTrip.lastLine().contains("осталась"),
                    "He did not say part of it stayed: " + companion.deathTrip.lastLine());
        }).thenSucceed();
    }

    /** With nothing ordered he sets out by himself: the walk starts on his own ticks, not only when asked. */
    @GameTest(template = "empty")
    public static void heSetsOutByHimself(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            for (int tick = 0; tick < 40 && !companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(companion.deathTrip.active(), "He never set out on his own");
        }).thenSucceed();
    }

    /** A zombie that picks the stack up leaves nothing on the ground: the walk is not made. */
    @GameTest(template = "empty")
    public static void aZombieThatPicksItUpLeavesNothing(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        Zombie thief = h.spawn(EntityType.ZOMBIE, 20, 2, 16);
        thief.setCanPickUpLoot(true);
        BlockPos place = thief.blockPosition();
        var sword = belonging(h, place, new ItemStack(Items.IRON_SWORD), companion.getOwnerId());
        h.startSequence().thenExecute(() -> {
            for (int tick = 0; tick < 60 && !sword.isRemoved(); tick++) thief.tick();
            h.assertTrue(sword.isRemoved(), "The zombie did not pick the sword up");
            CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
            h.assertTrue(!companion.deathTrip.begin(), "He set out for a pile a zombie already took");
        }).thenSucceed();
    }

    /** The player can turn the feature off: no walk starts, and one under way ends. */
    @GameTest(template = "empty")
    public static void theFeatureCanBeTurnedOff(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            companion.setDeathRecovery(false);
            h.assertTrue(!companion.deathTrip.begin(), "He set out with the feature turned off");
            companion.setDeathRecovery(true);
            h.assertTrue(companion.deathTrip.begin(), "He did not set out with the feature on");
            companion.setDeathRecovery(false);
            companion.tick();
            h.assertTrue(!companion.deathTrip.active(), "The walk went on after the feature was turned off");
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).size() == 1,
                    "Turning the feature off forgot the place");
        }).thenSucceed();
    }

    /** Five tries at one place and he lets it lie, with a word about it. */
    @GameTest(template = "empty")
    public static void fiveAttemptsAndThePlaceIsLeft(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        store.died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        for (int i = 0; i < 5; i++) store.attempted(companion.getOwnerId(), h.getLevel().dimension(), place);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!companion.deathTrip.begin(), "He set out a sixth time");
            h.assertTrue(companion.deathTrip.lastLine().contains("подавитесь"),
                    "He did not say why he let it lie: " + companion.deathTrip.lastLine());
            h.assertTrue(store.all(companion.getOwnerId()).isEmpty(), "The given-up place is still remembered");
        }).thenSucceed();
    }

    /** Ten deaths on the way to one place: it is not worth his life any more. */
    @GameTest(template = "empty")
    public static void tenDeathsOnTheWayAndThePlaceIsLeft(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        store.died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        for (int i = 0; i < 10; i++) store.deathOnTheWay(companion.getOwnerId(), h.getLevel().dimension(), place);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!companion.deathTrip.begin(), "He set out an eleventh time");
            h.assertTrue(store.all(companion.getOwnerId()).isEmpty(), "The place that cost ten lives is still remembered");
        }).thenSucceed();
    }

    /** A full bag is made room in by letting junk go, not by walking away from a diamond. */
    @GameTest(template = "empty")
    public static void aFullBagMakesRoom(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        for (int slot = 0; slot < companion.getContainerSize(); slot++)
            companion.setItem(slot, new ItemStack(Items.DIRT, 64));
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out with a full bag");
            int free = 0;
            for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).isEmpty()) free++;
            h.assertTrue(free > 0, "No room was made in a bag of dirt");
        }).thenSucceed();
    }

    /** The budget is the one in the settings, not a number in the code. */
    @GameTest(template = "empty")
    public static void theBudgetIsWhatTheSettingsSay(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        companion.setDeathRecoveryLimits(2, 3);
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        store.died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        store.attempted(companion.getOwnerId(), h.getLevel().dimension(), place);
        store.attempted(companion.getOwnerId(), h.getLevel().dimension(), place);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!companion.deathTrip.begin(), "He set out past the attempt budget");
            h.assertTrue(store.all(companion.getOwnerId()).isEmpty(), "The place past its budget is still remembered");
        }).thenSucceed();
    }

    /** The player's things he was carrying are priced and fetched too: they fell with the rest. */
    @GameTest(template = "empty")
    public static void thePlayersThingsHeCarriedCountToo(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        companion.playersThings.keep(new ItemStack(Items.DIAMOND, 4));
        h.assertTrue(CompanionBelongings.worth(companion) >= CompanionBelongings.WORTH_GOING,
                "The player's diamonds were worth nothing: " + CompanionBelongings.worth(companion));
        BlockPos place = companion.blockPosition().offset(4, 0, 0);
        var kept = new ItemEntity(h.getLevel(), place.getX() + .5, place.getY() + .2, place.getZ() + .5,
                new ItemStack(Items.DIAMOND, 4));
        kept.getPersistentData().putBoolean("aicompanion:players", true);
        h.getLevel().addFreshEntity(kept);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 40, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out for the player's things");
            for (int tick = 0; tick < 400 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(kept.isRemoved() || kept.getItem().isEmpty(), "The player's things were left lying");
        }).thenSucceed();
    }

    /** A foreign drop beside him does not make him claim his own: only his stacks count. */
    @GameTest(template = "empty")
    public static void aForeignDropIsNotHis(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition();
        var mine = belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        mine.setPickUpDelay(200);
        var foreign = new ItemEntity(h.getLevel(), place.getX() + .5, place.getY() + .2, place.getZ() + .5,
                new ItemStack(Items.IRON_INGOT, 8));
        h.getLevel().addFreshEntity(foreign);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            for (int tick = 0; tick < 400 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(companion.deathTrip.lastLine().contains("осталась"),
                    "A foreign drop made him claim his own: " + companion.deathTrip.lastLine());
        }).thenSucceed();
    }

    /** A monster on him: he grabs his things and never swings at it. */
    @GameTest(template = "empty")
    public static void aMonsterOnHimIsNotFought(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition();
        var item = belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        Zombie foe = h.spawn(EntityType.ZOMBIE, 17, 2, 16);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            companion.setTarget(foe);
            for (int tick = 0; tick < 60 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The pile was not taken with a monster on him");
            h.assertTrue(!companion.swinging, "He stopped to swing at the monster");
        }).thenSucceed();
    }

    /** The tries and the deaths are counted against the place, as the budget needs. */
    @GameTest(template = "empty")
    public static void triesAndDeathsAreCounted(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(4, 0, 0);
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        store.died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        store.attempted(companion.getOwnerId(), h.getLevel().dimension(), place);
        store.attempted(companion.getOwnerId(), h.getLevel().dimension(), place);
        store.deathOnTheWay(companion.getOwnerId(), h.getLevel().dimension(), place);
        h.startSequence().thenExecute(() -> {
            var spots = store.all(companion.getOwnerId());
            h.assertTrue(spots.size() == 1, "The place went missing: " + spots);
            h.assertTrue(spots.get(0).attempts() == 2, "The tries were not counted: " + spots.get(0).attempts());
            h.assertTrue(spots.get(0).deaths() == 1, "The deaths were not counted: " + spots.get(0).deaths());
        }).thenSucceed();
    }

    /** The switch and the budget live in the save with him, and an old save has the walk on. */
    @GameTest(template = "empty")
    public static void theSwitchAndBudgetAreSaved(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        companion.setDeathRecovery(false);
        companion.setDeathRecoveryLimits(3, 7);
        var tag = companion.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        CompanionEntity other = h.spawn(AICompanionMod.COMPANION.get(), 20, 2, 20);
        other.load(tag);
        h.assertTrue(!other.deathRecovery(), "The switch was not saved");
        h.assertTrue(other.deathRecoveryAttempts() == 3, "The tries were not saved: " + other.deathRecoveryAttempts());
        h.assertTrue(other.deathRecoveryDeaths() == 7, "The deaths were not saved: " + other.deathRecoveryDeaths());
        var old = new net.minecraft.nbt.CompoundTag();
        CompanionEntity third = h.spawn(AICompanionMod.COMPANION.get(), 24, 2, 24);
        third.setDeathRecovery(false);
        third.load(old);
        h.assertTrue(third.deathRecovery(), "An old save loaded with the walk off");
        h.assertTrue(third.deathRecoveryAttempts() == 5 && third.deathRecoveryDeaths() == 10,
                "An old save loaded with a strange budget: " + third.deathRecoveryAttempts() + "/" + third.deathRecoveryDeaths());
        h.succeed();
    }

    /** Setting out spends a try: a pile he never reaches cannot be retried for ever. */
    @GameTest(template = "empty")
    public static void settingOutSpendsATry(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
        belonging(h, place, new ItemStack(Items.DIAMOND, 2), companion.getOwnerId());
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        store.died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            var spots = store.all(companion.getOwnerId());
            h.assertTrue(spots.size() == 1 && spots.get(0).attempts() == 1,
                    "Setting out did not spend a try: " + spots);
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
            // The void place is dropped at once; the dear Nether place waits for a portal.
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).size() == 1,
                    "The unreachable places were not sorted out: " + CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()));
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
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).size() == 1,
                    "The place was forgotten when the player insisted");
        }).thenSucceed();
    }

    /** The walk is a task the player can see in the task screen, and cancel from it. */
    @GameTest(template = "empty")
    public static void theWalkIsShownAndCanBeCancelled(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos place = companion.blockPosition().offset(6, 0, 0);
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

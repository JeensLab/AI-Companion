package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBelongings;
import dev.aicompanion.entity.CompanionDeathSpots;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** His own things after a death: what is worth a walk, where it lies, and the walk back. */
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

    /** He walks to the place and the game's own pickup takes what lies there; then the place is forgotten. */
    @GameTest(template = "empty")
    public static void theWalkTakesTheThingsAndForgetsThePlace(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition().offset(4, 0, 0);
        var item = new ItemEntity(h.getLevel(), place.getX() + .5, place.getY() + .2, place.getZ() + .5,
                new ItemStack(Items.DIAMOND, 2));
        h.getLevel().addFreshEntity(item);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            // The test area does not tick him: his own ticks run the walk and the goals that drive it.
            for (int tick = 0; tick < 400 && companion.deathTrip.active(); tick++) companion.tick();
        }).thenExecute(() -> {
                    h.assertTrue(!companion.deathTrip.active(), "The walk never ended");
                    h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The diamond was not taken: " + item.getItem());
                    h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                            "The place is still remembered");
                }).thenSucceed();
    }

    /** Things that slid away from the place are walked to one by one, not left because the spot itself was empty. */
    @GameTest(template = "empty")
    public static void thingsThatSlidAwayAreStillFetched(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition();
        var slid = new ItemEntity(h.getLevel(), place.getX() + 6.5, place.getY() + .2, place.getZ() + .5,
                new ItemStack(Items.DIAMOND, 2));
        h.getLevel().addFreshEntity(slid);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            for (int tick = 0; tick < 400 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(!companion.deathTrip.active(), "The walk never ended");
            h.assertTrue(slid.isRemoved() || slid.getItem().isEmpty(), "The diamond that slid away was not taken: " + slid.getItem());
        }).thenSucceed();
    }

    /** A thing the pickup cannot take is given up on and the walk ends honestly, not with a claim of having it. */
    @GameTest(template = "empty")
    public static void aThingHeCannotTakeIsLeftAndSaid(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var other = h.makeMockServerPlayerInLevel();
        BlockPos place = companion.blockPosition();
        var stuck = new ItemEntity(h.getLevel(), place.getX() + .5, place.getY() + .2, place.getZ() + .5,
                new ItemStack(Items.DIAMOND, 2));
        stuck.setTarget(other.getUUID());
        h.getLevel().addFreshEntity(stuck);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            for (int tick = 0; tick < 400 && companion.deathTrip.active(); tick++) companion.tick();
            h.assertTrue(!companion.deathTrip.active(), "The walk never ended");
            h.assertTrue(!stuck.isRemoved() && !stuck.getItem().isEmpty(), "The thing aimed at another was taken anyway");
            h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(companion.getOwnerId()).isEmpty(),
                    "The place is still remembered after the walk");
        }).thenSucceed();
    }

    /** The walk is a task the player can see in the task screen, and cancel from it. */
    @GameTest(template = "empty")
    public static void theWalkIsShownAndCanBeCancelled(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos place = companion.blockPosition().offset(40, 0, 0);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), h.getLevel().dimension(), place, 20, 0);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(companion.deathTrip.begin(), "He did not set out");
            var shown = dev.aicompanion.entity.CompanionTaskBoard.read(owner);
            h.assertTrue(shown != null && !shown.recovery().isBlank(), "The task screen does not show the walk: " + shown);
            h.assertTrue(shown.recovery().contains("вещами"), "The task does not say what it is: " + shown.recovery());
            dev.aicompanion.entity.CompanionTaskBoard.control(owner, shown, "cancel_recovery");
            h.assertTrue(!companion.deathTrip.active(), "The walk survived the cancel");
        }).thenSucceed();
    }

    /** A place he cannot walk to at all — another dimension, the void — is dropped without a walk. */
    @GameTest(template = "empty")
    public static void unreachablePlacesAreDropped(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        BlockPos place = companion.blockPosition();
        CompanionDeathSpots.get(h.getLevel().getServer()).died(companion.getOwnerId(), net.minecraft.world.level.Level.NETHER, place, 20, 0);
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

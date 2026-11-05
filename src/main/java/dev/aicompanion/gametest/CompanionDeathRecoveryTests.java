package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBelongings;
import dev.aicompanion.entity.CompanionDeathSpots;
import dev.aicompanion.entity.CompanionDeathTrip;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionTaskBoard;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static dev.aicompanion.gametest.DeathTestKit.place;
import static dev.aicompanion.gametest.DeathTestKit.setOut;
import static dev.aicompanion.gametest.DeathTestKit.settle;
import static dev.aicompanion.gametest.DeathTestKit.spawn;
import static dev.aicompanion.gametest.DeathTestKit.stack;
import static dev.aicompanion.gametest.DeathTestKit.walk;
import static dev.aicompanion.gametest.DeathTestKit.yard;

/** His own things after a death: the manifest of what fell, the ledger of the walk, and every way a stack can be gone. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDeathRecoveryTests {

    // ---- the manifest: what fell, exactly ----

    /** Every stack that fell is recorded with its entity, its exact point and its count. */
    @GameTest(template = "empty")
    public static void theFallRecordsEveryStackWithItsPoint(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND, 3));
        c.setItem(1, new ItemStack(Items.IRON_INGOT, 7));
        c.setItem(2, new ItemStack(Items.GOLDEN_APPLE, 2));
        BlockPos where = c.blockPosition();
        UUID owner = c.getOwnerId();
        c.hurt(h.getLevel().damageSources().generic(), 1000);
        var spots = CompanionDeathSpots.get(h.getLevel().getServer()).all(owner);
        h.assertTrue(spots.size() == 1, "The fall was not remembered: " + spots.size());
        var manifest = spots.get(0).manifest();
        h.assertTrue(manifest.size() == 3, "The manifest does not hold every stack: " + manifest.size());
        int diamonds = 0, ingots = 0, apples = 0;
        for (var entry : manifest) {
            h.assertTrue(entry.id() != null && entry.count() > 0, "A manifest stack is empty: " + entry);
            h.assertTrue(entry.pos().distanceToSqr(where.getCenter()) < 9, "A stack is recorded somewhere else: " + entry.pos());
            if (entry.item().equals("minecraft:diamond")) diamonds += entry.count();
            if (entry.item().equals("minecraft:iron_ingot")) ingots += entry.count();
            if (entry.item().equals("minecraft:golden_apple")) apples += entry.count();
        }
        h.assertTrue(diamonds == 3 && ingots == 7 && apples == 2,
                "The manifest lost counts: " + diamonds + "/" + ingots + "/" + apples + " " + manifest);
        h.succeed();
    }

    /** The manifest names the entities that really lie there: they are the existence oracle of the walk. */
    @GameTest(template = "empty")
    public static void theManifestNamesTheDroppedEntities(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND, 2));
        c.hurt(h.getLevel().damageSources().generic(), 1000);
        var spot = CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).get(0);
        for (var entry : spot.manifest()) {
            h.assertTrue(h.getLevel().getEntity(entry.id()) instanceof ItemEntity,
                    "A manifest stack names an entity that is not there: " + entry.id());
        }
        h.succeed();
    }

    /** The player's things he was carrying fell with his own and are in the manifest too. */
    @GameTest(template = "empty")
    public static void thePlayersThingsAreInTheManifestToo(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND, 2));
        c.playersThings.keep(new ItemStack(Items.IRON_INGOT, 4));
        c.hurt(h.getLevel().damageSources().generic(), 1000);
        var spot = CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).get(0);
        h.assertTrue(spot.manifest().size() == 2, "The player's things are missing from the manifest: " + spot.manifest());
        boolean ingots = spot.manifest().stream().anyMatch(entry -> entry.item().equals("minecraft:iron_ingot") && entry.count() == 4);
        h.assertTrue(ingots, "The player's ingots are not in the manifest: " + spot.manifest());
        h.succeed();
    }

    /** The manifest outlives a reload: ids, points, items and counts come back the same. */
    @GameTest(template = "empty")
    public static void theManifestSurvivesASaveAndLoad(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var server = h.getLevel().getServer();
        BlockPos place = c.blockPosition().offset(5, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 4));
        UUID spotId = place(h, c, place, 40, item);
        var saved = CompanionDeathSpots.get(server).save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
        var loaded = CompanionDeathSpots.load(saved, server.registryAccess());
        var spots = loaded.all(c.getOwnerId());
        h.assertTrue(spots.size() == 1, "The place did not survive the save: " + spots.size());
        h.assertTrue(spots.get(0).id().equals(spotId), "The place came back with another id");
        h.assertTrue(spots.get(0).manifest().size() == 1, "The manifest did not survive: " + spots.get(0).manifest());
        var entry = spots.get(0).manifest().get(0);
        h.assertTrue(entry.id().equals(item.getUUID()), "The stack's entity came back wrong");
        h.assertTrue(entry.item().equals("minecraft:diamond") && entry.count() == 4, "The stack came back wrong: " + entry);
        h.assertTrue(entry.pos().distanceToSqr(item.position()) < .01, "The stack's point came back wrong: " + entry.pos());
        h.succeed();
    }

    /** An old save has no manifest: its place is forgotten without a walk, not trusted for ever. */
    @GameTest(template = "empty")
    public static void anOldSaveWithoutAManifestIsForgotten(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var server = h.getLevel().getServer();
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        CompanionDeathSpots.get(server).died(c.getOwnerId(), h.getLevel().dimension(), place, 40, 0);
        h.assertTrue(!c.deathTrip.begin(), "He set out for a place with no manifest");
        h.assertTrue(CompanionDeathSpots.get(server).all(c.getOwnerId()).isEmpty(), "An old place without a manifest stayed");
        h.succeed();
    }

    /** Two falls at one place are two records with their own manifests, and one is not the other's. */
    @GameTest(template = "empty")
    public static void twoFallsAtOnePlaceAreTwoRecords(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var first = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var second = stack(h, place, new ItemStack(Items.IRON_INGOT, 6));
        UUID one = place(h, c, place, 20, first);
        UUID two = place(h, c, place, 18, second);
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        h.assertTrue(store.all(c.getOwnerId()).size() == 2, "The two falls became one record");
        h.assertTrue(store.manifestOf(c.getOwnerId(), one).size() == 1
                        && store.manifestOf(c.getOwnerId(), one).get(0).id().equals(first.getUUID()),
                "The first fall's manifest holds the other's stack");
        h.assertTrue(store.manifestOf(c.getOwnerId(), two).size() == 1
                        && store.manifestOf(c.getOwnerId(), two).get(0).id().equals(second.getUUID()),
                "The second fall's manifest holds the other's stack");
        h.succeed();
    }

    /** A junk fall is not remembered at all: no manifest, no walk. */
    @GameTest(template = "empty")
    public static void aJunkDeathIsForgotten(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIRT, 32));
        c.setItem(1, new ItemStack(Items.GRAVEL, 16));
        c.hurt(h.getLevel().damageSources().generic(), 1000);
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).isEmpty(), "Junk was remembered");
        h.succeed();
    }

    /** A dear fall is remembered with its price and its manifest. */
    @GameTest(template = "empty")
    public static void aDearFallIsRememberedWithItsPrice(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND, 2));
        BlockPos where = c.blockPosition();
        c.hurt(h.getLevel().damageSources().generic(), 1000);
        var spots = CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId());
        h.assertTrue(spots.size() == 1, "The fall was not remembered: " + spots.size());
        h.assertTrue(spots.get(0).pos().equals(where), "Remembered the wrong place: " + spots.get(0).pos());
        h.assertTrue(spots.get(0).worth() >= CompanionBelongings.WORTH_GOING, "Remembered it as worthless");
        h.assertTrue(!spots.get(0).manifest().isEmpty(), "A dear fall has no manifest");
        h.succeed();
    }

    // ---- the ledger: taken, part, gone, unreachable ----

    /** The walk fetches the stack, says so honestly and forgets the place. */
    @GameTest(template = "empty")
    public static void theWalkTakesTheStackAndForgetsThePlace(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(!c.deathTrip.active(), "The walk never ended: " + c.deathTrip.describe());
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The diamond was not taken: " + item.getItem());
        h.assertTrue(c.deathTrip.verdict(item.getUUID()) != null
                        && c.deathTrip.verdict(item.getUUID()).fate() == CompanionDeathTrip.Fate.TAKEN,
                "The ledger does not say taken: " + c.deathTrip.verdict(item.getUUID()));
        h.assertTrue(c.deathTrip.lastLine().equals("Забрал свои вещи."), "He did not say he had them: " + c.deathTrip.lastLine());
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) == null,
                "The place is still remembered");
        h.succeed();
    }

    /** The ledger has one entry for every stack of the fall, and each says what became of it. */
    @GameTest(template = "empty")
    public static void theLedgerHoldsEveryStackOfTheFall(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition();
        var one = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var two = stack(h, place.offset(1, 0, 0), new ItemStack(Items.IRON_INGOT, 4));
        var three = stack(h, place.offset(-1, 0, 0), new ItemStack(Items.GOLDEN_APPLE, 3));
        place(h, c, place, 60, one, two, three);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        var ledger = c.deathTrip.ledger();
        h.assertTrue(ledger.size() == 3, "The ledger does not hold every stack: " + ledger);
        for (ItemEntity item : List.of(one, two, three))
            h.assertTrue(ledger.containsKey(item.getUUID()) && ledger.get(item.getUUID()).fate() == CompanionDeathTrip.Fate.TAKEN,
                    "A stack is not in the ledger as taken: " + item.getItem() + " " + ledger.get(item.getUUID()));
        h.succeed();
    }

    /** A place whose stacks are all gone is forgotten without a walk, with a word about it. */
    @GameTest(template = "empty")
    public static void anEmptyPlaceIsForgottenWithoutAWalk(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 12);
        BlockPos place = c.blockPosition().offset(10, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        item.discard();
        // One look that finds nothing does not strike the stack off: the walk asks again a moment later.
        h.assertTrue(!c.deathTrip.begin(), "He set out for a place with nothing left");
        for (int tick = 0; tick < 5; tick++) c.tick();
        h.assertTrue(!c.deathTrip.begin(), "He set out for a place with nothing left");
        h.assertTrue(c.deathTrip.lastLine().equals("Вещей на месте уже нет."),
                "He did not say the place was empty: " + c.deathTrip.lastLine());
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) == null,
                "An empty place is still remembered");
        h.succeed();
    }

    /** A stack the player already took is pruned before any walk: the place costs no walk at all. */
    @GameTest(template = "empty")
    public static void aStackThePlayerTookIsPrunedBeforeTheWalk(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 12);
        BlockPos place = c.blockPosition().offset(10, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        Player player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        ItemStack taken = item.getItem().copy();
        // What the game does on a player's pickup: the stack leaves the entity, the event is fired, the entity is discarded.
        item.setItem(ItemStack.EMPTY);
        CompanionDeathSpots.onPlayerPickup(new ItemEntityPickupEvent.Post(player, item, taken));
        item.discard();
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).manifestOf(c.getOwnerId(), spotId).isEmpty(),
                "The player's take did not prune the manifest");
        h.assertTrue(!c.deathTrip.begin(), "He set out for what the player already carried off");
        h.succeed();
    }

    /** A part taken by the player keeps the rest in the manifest, and the walk fetches only that. */
    @GameTest(template = "empty")
    public static void aPartTakenByThePlayerKeepsTheRest(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 5));
        UUID spotId = place(h, c, place, 50, item);
        Player player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        ItemStack taken = new ItemStack(Items.DIAMOND, 3);
        item.setItem(new ItemStack(Items.DIAMOND, 2));
        CompanionDeathSpots.onPlayerPickup(new ItemEntityPickupEvent.Post(player, item, taken));
        var manifest = CompanionDeathSpots.get(h.getLevel().getServer()).manifestOf(c.getOwnerId(), spotId);
        h.assertTrue(manifest.size() == 1 && manifest.get(0).count() == 2,
                "The manifest does not hold what the player left: " + manifest);
        h.assertTrue(setOut(h, c), "He did not set out for the rest");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The rest was not taken");
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) == null,
                "The place is still remembered after the rest was taken");
        h.succeed();
    }

    /** His own pickup of a part of a stack keeps the rest in the manifest, with what is left. */
    @GameTest(template = "empty")
    public static void hisOwnPartPickupKeepsTheRestInTheManifest(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        for (int slot = 1; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIRT, 64));
        c.setItem(0, new ItemStack(Items.DIAMOND, 62));
        BlockPos place = c.blockPosition();
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 4));
        UUID spotId = place(h, c, place, 40, item);
        // No walk: he simply stands on it and the pickup takes what fits (2 of the 4).
        c.setDeathRecovery(false);
        for (int tick = 0; tick < 4; tick++) c.tick();
        var manifest = CompanionDeathSpots.get(h.getLevel().getServer()).manifestOf(c.getOwnerId(), spotId);
        h.assertTrue(manifest.size() == 1, "The partly taken stack left the manifest: " + manifest);
        h.assertTrue(manifest.get(0).count() == 2, "The manifest does not hold what is left: " + manifest);
        h.assertTrue(item.isAlive() && item.getItem().getCount() == 2, "The stack was not taken in part: " + item.getItem());
        h.succeed();
    }

    /** A stack that vanished is gone in the ledger and is not claimed as taken. */
    @GameTest(template = "empty")
    public static void aStackThatVanishedIsGoneNotTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 12);
        BlockPos place = c.blockPosition().offset(8, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        for (int tick = 0; tick < 4; tick++) c.tick();
        item.discard();
        walk(h, c, 600);
        h.assertTrue(!c.deathTrip.active(), "The walk never ended");
        var verdict = c.deathTrip.verdict(item.getUUID());
        h.assertTrue(verdict != null && verdict.fate() == CompanionDeathTrip.Fate.GONE,
                "A stack that vanished was not called gone: " + verdict);
        h.assertTrue(c.deathTrip.lastLine().equals("Вещей на месте уже нет."),
                "He claimed what he never had: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** A stack merged into a neighbour is gone with the game's own reason, and is not claimed. */
    @GameTest(template = "empty")
    public static void aStackMergedIntoANeighbourIsGoneWithItsReason(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(5, 0, 0);
        var mine = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var neighbour = stack(h, place, new ItemStack(Items.DIAMOND, 3));
        place(h, c, place, 20, mine);
        h.assertTrue(setOut(h, c), "He did not set out");
        // Vanilla's merge: the neighbour grows and the other entity is removed, the same item within half a block.
        neighbour.setItem(new ItemStack(Items.DIAMOND, 5));
        mine.discard();
        walk(h, c, 600);
        var verdict = c.deathTrip.verdict(mine.getUUID());
        h.assertTrue(verdict != null && verdict.fate() == CompanionDeathTrip.Fate.GONE,
                "A merged stack was not called gone: " + verdict);
        h.assertTrue(verdict.reason().equals("слилось с соседней стопкой"), "The reason is not the merge: " + verdict.reason());
        h.assertTrue(c.deathTrip.lastLine().equals("Вещей на месте уже нет."), "He claimed the merged stack: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** A stack the player emptied by throwing it back is gone, and the place is forgotten without a chase. */
    @GameTest(template = "empty")
    public static void aStackThrownBackIsNotChased(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        Player player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        ItemStack taken = item.getItem().copy();
        item.setItem(ItemStack.EMPTY);
        CompanionDeathSpots.onPlayerPickup(new ItemEntityPickupEvent.Post(player, item, taken));
        item.discard();
        // Thrown back: a new entity, no manifest entry of its own.
        var thrown = player.drop(taken, false);
        h.assertTrue(thrown != null, "The player did not throw the stack back");
        h.assertTrue(!c.deathTrip.begin(), "He set out after a stack the player had already handled");
        h.assertTrue(thrown.isAlive() && !thrown.getItem().isEmpty(), "The thrown-back stack vanished");
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) == null,
                "The place of a thrown-back stack is still remembered");
        h.succeed();
    }

    /** A stack aimed at somebody else is left with a real reason, and the place keeps it for the next walk. */
    @GameTest(template = "empty")
    public static void aStackAimedAtAnotherIsLeftWithAReason(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition();
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        item.setTarget(c.getOwnerId());
        UUID spotId = place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(!c.deathTrip.active(), "The walk never ended");
        h.assertTrue(!item.isRemoved() && !item.getItem().isEmpty(), "The stack aimed at another was taken");
        var verdict = c.deathTrip.verdict(item.getUUID());
        h.assertTrue(verdict != null && verdict.fate() == CompanionDeathTrip.Fate.UNREACHABLE,
                "The stack he could not take was not called unreachable: " + verdict);
        h.assertTrue(verdict.reason().equals("метит в другого"), "The reason is not the real one: " + verdict.reason());
        h.assertTrue(c.deathTrip.lastLine().startsWith("Часть моих вещей осталась"), "He claimed more than he took: " + c.deathTrip.lastLine());
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) != null,
                "A place with something left was forgotten");
        h.succeed();
    }

    /** A full bag of things worth having is not made room in: the stack stays with the reason named. */
    @GameTest(template = "empty")
    public static void aBagFullOfGoodsLeavesTheStackWithTheReason(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIAMOND, 64));
        BlockPos place = c.blockPosition();
        var item = stack(h, place, new ItemStack(Items.IRON_INGOT, 8));
        UUID spotId = place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        var verdict = c.deathTrip.verdict(item.getUUID());
        h.assertTrue(verdict != null && verdict.fate() == CompanionDeathTrip.Fate.UNREACHABLE,
                "A stack that did not fit was not called unreachable: " + verdict);
        h.assertTrue(verdict.reason().equals("сумка полна"), "The reason is not the bag: " + verdict.reason());
        h.assertTrue(item.isAlive() && !item.getItem().isEmpty(), "The stack that did not fit was taken anyway");
        h.assertTrue(c.deathTrip.lastLine().contains("сумка полна"), "The report does not say why: " + c.deathTrip.lastLine());
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) != null,
                "The place with a stack left is forgotten");
        h.succeed();
    }

    /** A full bag is made room in only for a stack in reach, and the junk let go is marked as junk. */
    @GameTest(template = "empty")
    public static void aFullBagMakesRoomForAStackInReach(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIRT, 64));
        BlockPos place = c.blockPosition();
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out with a full bag");
        walk(h, c, 300);
        int diamonds = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.DIAMOND)) diamonds += c.getItem(slot).getCount();
        h.assertTrue(diamonds == 2, "No room was made in a bag of dirt: " + diamonds);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The diamond was not taken from under his feet");
        boolean junkMarked = false;
        for (var entity : h.getLevel().getAllEntities()) {
            if (!(entity instanceof ItemEntity dropped)) continue;
            if (dropped.getItem().is(Items.DIRT) && dropped.getPersistentData().getBoolean("aicompanion:junk")) junkMarked = true;
        }
        h.assertTrue(junkMarked, "The junk he let go was not marked as junk");
        h.succeed();
    }

    /** Nothing is let go for a stack that is still far off: the bag is made room in only in reach. */
    @GameTest(template = "empty")
    public static void aFullBagKeepsItsLoadWhileTheStackIsFar(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 12);
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIRT, 64));
        BlockPos place = c.blockPosition().offset(8, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        for (int tick = 0; tick < 4; tick++) c.tick();
        int dirt = 0;
        for (var entity : h.getLevel().getEntitiesOfClass(ItemEntity.class, c.getBoundingBox().inflate(8)))
            if (entity.getItem().is(Items.DIRT)) dirt += entity.getItem().getCount();
        h.assertTrue(dirt == 0, "Junk was let go before he reached the stack: " + dirt);
        h.succeed();
    }

    /** A diamond in a bag of dirt is never the junk that makes room. */
    @GameTest(template = "empty")
    public static void theDiamondIsNeverTheJunkThatMakesRoom(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIRT, 64));
        c.setItem(0, new ItemStack(Items.DIAMOND, 3));
        BlockPos place = c.blockPosition();
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 300);
        int diamonds = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.DIAMOND)) diamonds += c.getItem(slot).getCount();
        h.assertTrue(diamonds == 5, "A diamond was let go to make room: " + diamonds);
        h.succeed();
    }

    /** The report never claims a stack the walk did not take: a partly fetched pile says what stayed and why. */
    @GameTest(template = "empty")
    public static void aPartlyFetchedPileSaysWhatStayed(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition();
        var taken = stack(h, place, new ItemStack(Items.DIAMOND, 4));
        var left = stack(h, place.offset(1, 0, 0), new ItemStack(Items.DIAMOND, 4));
        left.setTarget(c.getOwnerId());
        place(h, c, place, 40, taken, left);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(taken.isRemoved() || taken.getItem().isEmpty(), "The reachable stack was not taken");
        h.assertTrue(!left.isRemoved() && !left.getItem().isEmpty(), "The stack aimed at another was taken");
        h.assertTrue(c.deathTrip.lastLine().startsWith("Часть моих вещей осталась"), "He did not say part of it stayed: " + c.deathTrip.lastLine());
        h.assertTrue(c.deathTrip.lastLine().contains("Diamond"), "The report does not name what stayed: " + c.deathTrip.lastLine());
        h.assertTrue(c.deathTrip.lastLine().contains("метит в другого"), "The report does not say why: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** Nothing of a walk in his bag but all of it taken: the place goes and the report is exact. */
    @GameTest(template = "empty")
    public static void everyStackTakenIsTheOnlyClaimOfEverything(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(3, 0, 0);
        var one = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var two = stack(h, place.offset(1, 0, 0), new ItemStack(Items.IRON_INGOT, 5));
        UUID spotId = place(h, c, place, 30, one, two);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(one.isRemoved() || one.getItem().isEmpty(), "The diamonds were left");
        h.assertTrue(two.isRemoved() || two.getItem().isEmpty(), "The ingots were left");
        h.assertTrue(c.deathTrip.lastLine().equals("Забрал свои вещи."), "He did not claim them: " + c.deathTrip.lastLine());
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) == null, "The place is still remembered");
        h.succeed();
    }

    /** One stack taken and another lost: the report says both, never that everything was taken. */
    @GameTest(template = "empty")
    public static void aTakenAndALostStackAreBothSaid(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 12);
        BlockPos place = c.blockPosition().offset(8, 0, 0);
        var taken = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var lost = stack(h, place.offset(3, 0, 0), new ItemStack(Items.IRON_INGOT, 4));
        place(h, c, place, 30, taken, lost);
        h.assertTrue(setOut(h, c), "He did not set out");
        for (int tick = 0; tick < 4; tick++) c.tick();
        lost.discard();
        walk(h, c, 600);
        h.assertTrue(taken.isRemoved() || taken.getItem().isEmpty(), "The diamond was not taken");
        h.assertTrue(c.deathTrip.lastLine().equals("Забрал свои вещи, остальное пропало."),
                "The report does not say the rest was lost: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** A stack somebody else carried off in the middle of the walk is not claimed: the report names the loss. */
    @GameTest(template = "empty")
    public static void aStackTakenMidWalkIsNotClaimed(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 12);
        BlockPos place = c.blockPosition().offset(8, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        for (int tick = 0; tick < 4; tick++) c.tick();
        item.discard();
        walk(h, c, 400);
        h.assertTrue(!c.deathTrip.lastLine().equals("Забрал свои вещи."), "He claimed a stack somebody else took: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** A stack burned in lava is gone with a reason of its own, not taken. */
    @GameTest(template = "empty")
    public static void aStackInLavaIsGoneWithItsReason(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        DeathTestKit.put(h, place.below(), Blocks.LAVA);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        item.discard();
        // One look that finds nothing does not strike the stack off: the walk asks again a moment later.
        h.assertTrue(!c.deathTrip.begin(), "He set out for a stack that burned");
        for (int tick = 0; tick < 5; tick++) c.tick();
        h.assertTrue(!c.deathTrip.begin(), "He set out for a stack that burned");
        h.assertTrue(c.deathTrip.lastLine().equals("Вещей на месте уже нет."), "He did not say the place was empty: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** A stack a mob picked up leaves nothing on the ground: no walk, and no claim. */
    @GameTest(template = "empty")
    public static void aStackAMobTookIsNotWalkedTo(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var zombie = new Zombie(h.getLevel());
        zombie.moveTo(place.getX() + .5, place.getY(), place.getZ() + .5);
        zombie.setCanPickUpLoot(true);
        h.getLevel().addFreshEntity(zombie);
        var sword = stack(h, place, new ItemStack(Items.IRON_SWORD));
        place(h, c, place, 20, sword);
        for (int tick = 0; tick < 200 && !sword.isRemoved(); tick++) zombie.tick();
        h.assertTrue(sword.isRemoved(), "The zombie did not pick the sword up: " + sword.getItem() + " at " + sword.blockPosition() + ", zombie at " + zombie.blockPosition());
        h.assertTrue(!c.deathTrip.begin(), "He set out after a pile a zombie already took");
        zombie.discard();
        h.succeed();
    }

    /** A stack the companion itself picked up outside the walk leaves the manifest, so nobody walks to it again. */
    @GameTest(template = "empty")
    public static void hisOwnPickupPrunesTheManifest(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition();
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        for (int tick = 0; tick < 4; tick++) c.tick();
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The pickup did not take the stack at his feet");
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).manifestOf(c.getOwnerId(), spotId).isEmpty(),
                "His own pickup did not prune the manifest");
        h.succeed();
    }

    /** Two falls close by: the dearer first, then the other, and one walk takes both. */
    @GameTest(template = "empty")
    public static void theDearerFallGoesFirstAndTheOtherAfter(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 10);
        BlockPos dear = c.blockPosition().offset(-3, 0, 0);
        BlockPos cheap = c.blockPosition().offset(3, 0, 0);
        var dearItems = stack(h, dear, new ItemStack(Items.DIAMOND, 6));
        var cheapItems = stack(h, cheap, new ItemStack(Items.IRON_INGOT, 6));
        place(h, c, dear, 60, dearItems);
        place(h, c, cheap, 18, cheapItems);
        h.assertTrue(setOut(h, c), "He did not set out");
        h.assertTrue(c.deathTrip.describe().contains(dear.toShortString()), "He set out for the cheaper place: " + c.deathTrip.describe());
        for (int tick = 0; tick < 1200 && !CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).isEmpty(); tick++) {
            if (!c.deathTrip.active()) setOut(h, c);
            c.tick();
        }
        h.assertTrue(dearItems.isRemoved() || dearItems.getItem().isEmpty(), "The dearer place was not fetched");
        h.assertTrue(cheapItems.isRemoved() || cheapItems.getItem().isEmpty(), "The cheaper place was left behind");
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).isEmpty(),
                "A place is still remembered after both walks");
        h.succeed();
    }

    /** A place whose stacks are all gone is skipped and the next one is walked to. */
    @GameTest(template = "empty")
    public static void aPlaceWhoseStacksAreGoneIsSkippedForTheNext(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 10);
        BlockPos dear = c.blockPosition().offset(-3, 0, 0);
        BlockPos cheap = c.blockPosition().offset(3, 0, 0);
        var dearItems = stack(h, dear, new ItemStack(Items.DIAMOND, 6));
        var cheapItems = stack(h, cheap, new ItemStack(Items.IRON_INGOT, 6));
        place(h, c, dear, 60, dearItems);
        place(h, c, cheap, 18, cheapItems);
        dearItems.discard();
        h.assertTrue(setOut(h, c), "He did not set out for the place that still had something");
        h.assertTrue(c.deathTrip.describe().contains(cheap.toShortString()), "He set out for the emptied place: " + c.deathTrip.describe());
        walk(h, c, 600);
        h.assertTrue(cheapItems.isRemoved() || cheapItems.getItem().isEmpty(), "The cheap place was not fetched");
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).isEmpty(), "A place is still remembered");
        h.succeed();
    }

    /** A place in a chunk nobody has loaded is walked to all the same; it is not judged empty before it can be seen. */
    @GameTest(template = "empty")
    public static void anUnloadedPlaceIsWalkedToNotJudgedEmpty(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
        BlockPos far = c.blockPosition().offset(100000, 0, 0);
        var item = stack(h, far, new ItemStack(Items.DIAMOND, 2));
        place(h, c, far, 40, item);
        h.assertTrue(setOut(h, c), "He gave up on a place he could not look at");
        h.assertTrue(c.deathTrip.active(), "The walk did not start");
        h.assertTrue(c.deathTrip.verdict(item.getUUID()) == null
                        || c.deathTrip.verdict(item.getUUID()).fate() == CompanionDeathTrip.Fate.PENDING,
                "An unloaded stack was judged: " + c.deathTrip.verdict(item.getUUID()));
        h.succeed();
    }

    /** Another dimension is crossed only for a place that is really dear, and only when there is a portal to cross by. */
    @GameTest(template = "empty")
    public static void anotherDimensionNeedsAPortalAndAWorthwhilePlace(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition();
        var dear = stack(h, place, new ItemStack(Items.DIAMOND, 4));
        var cheap = stack(h, place.offset(2, 0, 0), new ItemStack(Items.IRON_INGOT, 2));
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        store.died(c.getOwnerId(), UUID.randomUUID(), Level.NETHER, place, 40, 0, CompanionDeathSpots.manifestOf(List.of(dear)));
        store.died(c.getOwnerId(), UUID.randomUUID(), Level.NETHER, place.offset(0, 0, 2), 8, 1, CompanionDeathSpots.manifestOf(List.of(cheap)));
        h.assertTrue(!c.deathTrip.begin(), "He set out across dimensions with no portal");
        h.assertTrue(store.all(c.getOwnerId()).size() == 1,
                "A dear place across dimensions was forgotten without a portal: " + store.all(c.getOwnerId()));
        h.succeed();
    }

    // ---- the walk is a task and a decision the player can make ----

    /** The player can turn the feature off: no walk starts, and one under way ends. */
    @GameTest(template = "empty")
    public static void theFeatureCanBeTurnedOff(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        c.setDeathRecovery(false);
        h.assertTrue(!c.deathTrip.begin(), "He set out with the feature turned off");
        c.setDeathRecovery(true);
        h.assertTrue(setOut(h, c), "He did not set out with the feature on");
        c.setDeathRecovery(false);
        c.tick();
        h.assertTrue(!c.deathTrip.active(), "The walk went on after the feature was turned off");
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).size() == 1,
                "Turning the feature off forgot the place");
        h.succeed();
    }

    /** Five tries at one place and he lets it lie, with a word about it. */
    @GameTest(template = "empty")
    public static void fiveAttemptsAndThePlaceIsLeft(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        UUID spotId = place(h, c, place, 20, item);
        for (int i = 0; i < 5; i++) store.attempted(c.getOwnerId(), spotId);
        h.assertTrue(!c.deathTrip.begin(), "He set out a sixth time");
        h.assertTrue(c.deathTrip.lastLine().contains("подавитесь"), "He did not say why he let it lie: " + c.deathTrip.lastLine());
        h.assertTrue(store.all(c.getOwnerId()).isEmpty(), "The given-up place is still remembered");
        h.succeed();
    }

    /** Ten deaths on the way to one place: it is not worth his life any more. */
    @GameTest(template = "empty")
    public static void tenDeathsOnTheWayAndThePlaceIsLeft(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        UUID spotId = place(h, c, place, 20, item);
        for (int i = 0; i < 10; i++) store.deathOnTheWay(c.getOwnerId(), spotId);
        h.assertTrue(!c.deathTrip.begin(), "He set out an eleventh time");
        h.assertTrue(store.all(c.getOwnerId()).isEmpty(), "The place that cost ten lives is still remembered");
        h.succeed();
    }

    /** The budget is the one in the settings, not a number in the code. */
    @GameTest(template = "empty")
    public static void theBudgetIsWhatTheSettingsSay(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        c.setDeathRecoveryLimits(2, 3);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        UUID spotId = place(h, c, place, 20, item);
        store.attempted(c.getOwnerId(), spotId);
        store.attempted(c.getOwnerId(), spotId);
        h.assertTrue(!c.deathTrip.begin(), "He set out past the attempt budget");
        h.assertTrue(store.all(c.getOwnerId()).isEmpty(), "The place past its budget is still remembered");
        h.succeed();
    }

    /** Setting out spends a try: a pile he never reaches cannot be retried for ever. */
    @GameTest(template = "empty")
    public static void settingOutSpendsATry(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        var spots = CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId());
        h.assertTrue(spots.size() == 1 && spots.get(0).attempts() == 1, "Setting out did not spend a try: " + spots);
        h.succeed();
    }

    /** The tries and the deaths are counted against the place, as the budget needs. */
    @GameTest(template = "empty")
    public static void triesAndDeathsAreCounted(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        UUID spotId = place(h, c, place, 20, item);
        store.attempted(c.getOwnerId(), spotId);
        store.attempted(c.getOwnerId(), spotId);
        store.deathOnTheWay(c.getOwnerId(), spotId);
        var spots = store.all(c.getOwnerId());
        h.assertTrue(spots.size() == 1, "The place went missing: " + spots);
        h.assertTrue(spots.get(0).attempts() == 2, "The tries were not counted: " + spots.get(0).attempts());
        h.assertTrue(spots.get(0).deaths() == 1, "The deaths were not counted: " + spots.get(0).deaths());
        h.succeed();
    }

    /** The switch and the budget live in the save with him, and an old save has the walk on. */
    @GameTest(template = "empty")
    public static void theSwitchAndBudgetAreSaved(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setDeathRecovery(false);
        c.setDeathRecoveryLimits(3, 7);
        var tag = c.saveWithoutId(new net.minecraft.nbt.CompoundTag());
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

    /** Cancelling from the task screen clears the walk and its place. */
    @GameTest(template = "empty")
    public static void cancellingClearsThePlace(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        var shown = CompanionTaskBoard.read(owner);
        CompanionTaskBoard.control(owner, shown, "cancel_recovery");
        h.assertTrue(!c.deathTrip.active(), "The walk survived the cancel");
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) == null,
                "The cancelled place is still remembered");
        h.succeed();
    }

    /** The walk is a task the player can see in the task screen, and cancel from it. */
    @GameTest(template = "empty")
    public static void theWalkIsShownAndCanBeCancelled(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        var shown = CompanionTaskBoard.read(owner);
        h.assertTrue(shown != null && !shown.recovery().isBlank(), "The task screen does not show the walk: " + shown);
        h.assertTrue(shown.recovery().contains("вещами"), "The task does not say what it is: " + shown.recovery());
        CompanionTaskBoard.control(owner, shown, "cancel_recovery");
        h.assertTrue(!c.deathTrip.active(), "The walk survived the cancel");
        h.succeed();
    }

    /** An order waits once: the first ask is refused with a word, the second one takes the walk away. */
    @GameTest(template = "empty")
    public static void anOrderIsRefusedOnceThenObeys(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        settle(c);
        yard(h, c, 12);
        BlockPos place = c.blockPosition().offset(8, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        var first = CompanionEvents.executeAction(owner, "gather_stone", "", 1);
        h.assertTrue(first.status() == dev.aicompanion.entity.CompanionActionResult.Status.BLOCKED,
                "The first order was not refused: " + first.status());
        h.assertTrue(first.message().contains("вещи"), "The refusal does not say why: " + first.message());
        h.assertTrue(c.deathTrip.active(), "The walk was dropped on the first ask");
        var second = CompanionEvents.executeAction(owner, "gather_stone", "", 1);
        h.assertTrue(second.status() != dev.aicompanion.entity.CompanionActionResult.Status.BLOCKED,
                "The second order was refused too: " + second.status());
        h.assertTrue(!c.deathTrip.active(), "The walk survived the second ask");
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).size() == 1,
                "The place was forgotten when the player insisted");
        h.succeed();
    }

    /** A step of a goal of his is not the player insisting: the walk goes on and the refusal stays for the player. */
    @GameTest(template = "empty")
    public static void aGoalStepDoesNotTakeTheRefusal(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        var step = CompanionEvents.executeGoal(owner, "gather_stone", "", 1, "auto", "test-trace", "near_player");
        h.assertTrue(step.status() != dev.aicompanion.entity.CompanionActionResult.Status.BLOCKED,
                "A goal step was refused: " + step.status());
        h.assertTrue(c.deathTrip.active(), "A goal step cancelled the walk");
        var order = CompanionEvents.executeAction(owner, "gather_stone", "", 1);
        h.assertTrue(order.status() == dev.aicompanion.entity.CompanionActionResult.Status.BLOCKED,
                "The player's first order was not refused after a goal step: " + order.status());
        h.succeed();
    }

    /** Naming the home is a word, not an order: the walk goes on. */
    @GameTest(template = "empty")
    public static void namingTheHomeDoesNotRefuse(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        settle(c);
        yard(h, c, 12);
        BlockPos place = c.blockPosition().offset(8, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        var home = CompanionEvents.executeAction(owner, "set_home", "", 1);
        h.assertTrue(home.status() != dev.aicompanion.entity.CompanionActionResult.Status.BLOCKED,
                "Naming the home was refused: " + home.status());
        h.assertTrue(c.deathTrip.active(), "Naming the home cancelled the walk");
        h.succeed();
    }

    // ---- multi-death and regressions ----

    /** A new fall while he walks to an old one is taken up after the walk he is on, not forgotten. */
    @GameTest(template = "empty")
    public static void aNewFallDuringTheWalkIsTakenUpAfterwards(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 10);
        BlockPos first = c.blockPosition().offset(3, 0, 0);
        BlockPos second = c.blockPosition().offset(-3, 0, 0);
        var firstItems = stack(h, first, new ItemStack(Items.DIAMOND, 4));
        place(h, c, first, 40, firstItems);
        h.assertTrue(setOut(h, c), "He did not set out");
        var secondItems = stack(h, second, new ItemStack(Items.IRON_INGOT, 8));
        place(h, c, second, 24, secondItems);
        for (int tick = 0; tick < 1200 && !CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).isEmpty(); tick++) {
            if (!c.deathTrip.active()) setOut(h, c);
            c.tick();
        }
        h.assertTrue(firstItems.isRemoved() || firstItems.getItem().isEmpty(), "The first place was not fetched");
        h.assertTrue(secondItems.isRemoved() || secondItems.getItem().isEmpty(), "The new fall was forgotten: " + secondItems.getItem());
        h.succeed();
    }

    /** Dying on the way back with things on him: the walk stops and the new place is remembered for later. */
    @GameTest(template = "empty")
    public static void dyingOnTheWalkRemembersTheNewPlace(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        c.setItem(0, new ItemStack(Items.DIAMOND, 3));
        h.assertTrue(setOut(h, c), "He did not set out");
        c.tick();
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) != null,
                "The old place was lost before the death");
        c.hurt(h.getLevel().damageSources().generic(), 1000);
        h.assertTrue(!c.deathTrip.active(), "The walk survived his own death");
        var spots = CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId());
        h.assertTrue(spots.size() >= 2, "The new fall was not remembered: " + spots.size() + " " + spots);
        h.succeed();
    }

    /** A death on the way is charged to the record he was walking to, not to another fall at the same place. */
    @GameTest(template = "empty")
    public static void aDeathOnTheWayIsChargedToItsOwnRecord(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var one = stack(h, place, new ItemStack(Items.DIAMOND, 4));
        var two = stack(h, place.offset(3, 0, 0), new ItemStack(Items.IRON_INGOT, 8));
        UUID dear = place(h, c, place, 40, one);
        UUID cheap = place(h, c, place, 24, two);
        h.assertTrue(setOut(h, c), "He did not set out");
        c.tick();
        c.hurt(h.getLevel().damageSources().generic(), 1000);
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        h.assertTrue(store.byId(c.getOwnerId(), dear).deaths() == 1, "The death was not charged to the place walked to");
        h.assertTrue(store.byId(c.getOwnerId(), cheap).deaths() == 0, "The death was charged to another fall at the same place");
        h.succeed();
    }

    /** Two falls at one place are both walked, and taking one does not forget the other. */
    @GameTest(template = "empty")
    public static void twoFallsAtOnePlaceAreBothWalked(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var first = stack(h, place, new ItemStack(Items.DIAMOND, 4));
        var second = stack(h, place.offset(3, 0, 0), new ItemStack(Items.IRON_INGOT, 6));
        UUID dear = place(h, c, place, 40, first);
        UUID cheap = place(h, c, place, 24, second);
        h.assertTrue(setOut(h, c), "He did not set out");
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        for (int tick = 0; tick < 400 && store.byId(c.getOwnerId(), dear) != null; tick++) c.tick();
        h.assertTrue(first.isRemoved() || first.getItem().isEmpty(), "The dearer fall was not fetched");
        h.assertTrue(store.byId(c.getOwnerId(), dear) == null, "The fetched place is still remembered");
        h.assertTrue(store.byId(c.getOwnerId(), cheap) != null, "The other fall at the same place was forgotten");
        // The companion sets out for the second fall by himself: only the places are driven here.
        for (int tick = 0; tick < 800 && store.byId(c.getOwnerId(), cheap) != null; tick++) c.tick();
        h.assertTrue(second.isRemoved() || second.getItem().isEmpty(), "The second fall was not fetched");
        h.assertTrue(store.all(c.getOwnerId()).isEmpty(), "A place is still remembered");
        h.succeed();
    }

    /** Another fall's stack at the same place is not fetched by this walk. */
    @GameTest(template = "empty")
    public static void anotherFallsStackIsNotFetchedByThisWalk(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var mine = stack(h, place, new ItemStack(Items.DIAMOND, 4));
        var other = stack(h, place.offset(6, 0, 0), new ItemStack(Items.IRON_INGOT, 6));
        place(h, c, place, 40, mine);
        place(h, c, place, 24, other);
        h.assertTrue(setOut(h, c), "He did not set out");
        // Only this walk: the companion sets out for the next place on his own afterwards, and that is another test.
        for (int tick = 0; tick < 300 && mine.isAlive() && !mine.getItem().isEmpty(); tick++) c.tick();
        h.assertTrue(mine.isRemoved() || mine.getItem().isEmpty(), "His own stack was not fetched");
        h.assertTrue(!other.isRemoved() && !other.getItem().isEmpty(),
                "Another fall's stack was taken by this walk: removed=" + other.isRemoved() + " " + other.getItem()
                        + " at " + other.blockPosition() + ", he at " + c.blockPosition());
        h.succeed();
    }

    /** A stranger's junk far off is not a reason for a trip: only the manifest is walked to. */
    @GameTest(template = "empty")
    public static void aStrangersJunkFarOffIsNotFetched(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 20);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var mine = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var junk = new ItemEntity(h.getLevel(), c.getX() + 18.5, c.getY() + .2, c.getZ() + .5, new ItemStack(Items.DIRT, 64));
        h.getLevel().addFreshEntity(junk);
        place(h, c, place, 20, mine);
        h.assertTrue(setOut(h, c), "He did not set out");
        int ticks = walk(h, c, 600);
        h.assertTrue(mine.isRemoved() || mine.getItem().isEmpty(), "His diamond was not fetched");
        h.assertTrue(junk.isAlive() && junk.getItem().getCount() == 64, "He walked to a stranger's junk");
        h.assertTrue(ticks < 200, "The walk to one stack took too long: " + ticks);
        h.succeed();
    }

    /** A walk to a stack with no way to it ends in bounded time, with the reason and the place kept. */
    @GameTest(template = "empty")
    public static void aWalkWithNoWayEndsInBoundedTime(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        BlockPos pocket = feet.offset(3, 0, 0);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dz != 0) DeathTestKit.put(h, pocket.offset(dx, 0, dz), Blocks.STONE);
        DeathTestKit.put(h, pocket.above(2), Blocks.STONE);
        var item = stack(h, pocket, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, pocket, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        int ticks = walk(h, c, 600);
        h.assertTrue(!c.deathTrip.active(), "The walk never ended: " + c.deathTrip.describe());
        h.assertTrue(ticks < 300, "He stood at the pocket too long: " + ticks);
        var verdict = c.deathTrip.verdict(item.getUUID());
        h.assertTrue(verdict != null && verdict.fate() == CompanionDeathTrip.Fate.UNREACHABLE, "The stack was not called unreachable: " + verdict);
        h.assertTrue(verdict.reason().equals("нет пути"), "The reason is not the way: " + verdict.reason());
        h.assertTrue(c.deathTrip.lastLine().contains("нет пути"), "The report does not say why: " + c.deathTrip.lastLine());
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) != null,
                "A place he could not reach is forgotten");
        h.succeed();
    }

    /** A stack of another fall taken on the way is not a claim of this walk. */
    @GameTest(template = "empty")
    public static void aStackOfAnotherFallTakenOnTheWayIsNoClaim(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 12);
        BlockPos walked = c.blockPosition().offset(8, 0, 0);
        var target = stack(h, walked, new ItemStack(Items.DIAMOND, 2));
        place(h, c, walked, 20, target);
        BlockPos onTheWay = c.blockPosition().offset(2, 0, 0);
        var other = stack(h, onTheWay, new ItemStack(Items.IRON_INGOT, 4));
        place(h, c, onTheWay, 12, other);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(other.isRemoved() || other.getItem().isEmpty(), "The stack on the way was not picked up");
        h.assertTrue(c.deathTrip.verdict(other.getUUID()) == null, "Another fall's stack is in this walk's ledger");
        h.succeed();
    }

    // ---- the value model: what a fall is worth ----

    /** Junk is not worth a walk; one diamond is. */
    @GameTest(template = "empty")
    public static void junkIsNothingAndADiamondIsWorthGoing(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIRT, 32));
        c.setItem(1, new ItemStack(Items.COBBLESTONE, 32));
        c.setItem(2, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(3, new ItemStack(Items.WHEAT_SEEDS, 8));
        h.assertTrue(CompanionBelongings.worth(c) < CompanionBelongings.WORTH_GOING, "Junk was worth a walk: " + CompanionBelongings.worth(c));
        c.setItem(4, new ItemStack(Items.DIAMOND, 1));
        h.assertTrue(CompanionBelongings.worth(c) >= CompanionBelongings.WORTH_GOING, "A diamond was not worth a walk: " + CompanionBelongings.worth(c));
        c.setItem(5, new ItemStack(Items.BREAD, 32));
        h.assertTrue(CompanionBelongings.worth(c) >= CompanionBelongings.WORTH_GOING + 6, "A bag of food added nothing: " + CompanionBelongings.worth(c));
        h.succeed();
    }

    /** A bag of food alone is worth a walk, as the owner asked: more than twenty of it. */
    @GameTest(template = "empty")
    public static void aBagOfFoodAloneIsWorthGoing(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BREAD, 21));
        h.assertTrue(CompanionBelongings.worth(c) >= CompanionBelongings.WORTH_GOING, "Twenty-one bread was not worth a walk");
        c.setItem(0, new ItemStack(Items.BREAD, 20));
        h.assertTrue(CompanionBelongings.worth(c) < CompanionBelongings.WORTH_GOING, "Twenty bread was worth a walk");
        h.succeed();
    }

    /** The player's things he was carrying are priced and fetched too: they fell with the rest. */
    @GameTest(template = "empty")
    public static void thePlayersThingsHeCarriedCountToo(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        c.playersThings.keep(new ItemStack(Items.DIAMOND, 4));
        h.assertTrue(CompanionBelongings.worth(c) >= CompanionBelongings.WORTH_GOING, "The player's diamonds were worth nothing");
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var kept = stack(h, place, new ItemStack(Items.DIAMOND, 4));
        kept.getPersistentData().putBoolean("aicompanion:players", true);
        place(h, c, place, 40, kept);
        h.assertTrue(setOut(h, c), "He did not set out for the player's things");
        walk(h, c, 600);
        h.assertTrue(kept.isRemoved() || kept.getItem().isEmpty(), "The player's things were left lying");
        h.succeed();
    }

    /** One ingot out of a dear pile is still his: he fetches it. */
    @GameTest(template = "empty")
    public static void oneLeftIngotIsFetched(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var ingot = stack(h, place, new ItemStack(Items.IRON_INGOT, 1));
        place(h, c, place, 40, ingot);
        h.assertTrue(setOut(h, c), "He did not set out for the ingot");
        walk(h, c, 400);
        h.assertTrue(ingot.isRemoved() || ingot.getItem().isEmpty(), "The ingot was left lying");
        h.succeed();
    }

    /** A monster on him does not stop the walk: he grabs his things and never swings at it. */
    @GameTest(template = "empty")
    public static void aMonsterOnHimIsNotFought(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition();
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var foe = new Zombie(h.getLevel());
        foe.moveTo(c.getX() + 3.5, c.getY(), c.getZ() + .5);
        h.getLevel().addFreshEntity(foe);
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        c.setTarget(foe);
        for (int tick = 0; tick < 60 && c.deathTrip.active(); tick++) c.tick();
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The pile was not taken with a monster on him");
        h.assertTrue(!c.swinging, "He stopped to swing at the monster");
        foe.discard();
        h.succeed();
    }

    /** A foreign drop beside him does not make him claim his own: only his stacks count. */
    @GameTest(template = "empty")
    public static void aForeignDropIsNotHis(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition();
        var mine = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        mine.setPickUpDelay(32767);
        var foreign = stack(h, place, new ItemStack(Items.IRON_INGOT, 8));
        place(h, c, place, 20, mine);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(c.deathTrip.lastLine().startsWith("Часть моих вещей осталась"),
                "A foreign drop made him claim his own: " + c.deathTrip.lastLine());
        h.assertTrue(c.deathTrip.verdict(foreign.getUUID()) == null, "A foreign drop is in the walk's ledger");
        h.succeed();
    }

    /** A manifest with only one stack still lying there is walked for that one, and the claim is exact. */
    @GameTest(template = "empty")
    public static void aManifestWithOneLiveStackIsWalkedForIt(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var kept = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var goneOne = stack(h, place.offset(1, 0, 0), new ItemStack(Items.IRON_INGOT, 4));
        var goneTwo = stack(h, place.offset(2, 0, 0), new ItemStack(Items.GOLDEN_APPLE, 3));
        place(h, c, place, 40, kept, goneOne, goneTwo);
        goneOne.discard();
        goneTwo.discard();
        h.assertTrue(setOut(h, c), "He did not set out for the stack still lying there");
        walk(h, c, 600);
        h.assertTrue(kept.isRemoved() || kept.getItem().isEmpty(), "The live stack was not taken");
        h.assertTrue(c.deathTrip.lastLine().equals("Забрал свои вещи."), "He did not claim what he took: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** The last stack taken by the player in the middle of the walk is not claimed, and the place goes. */
    @GameTest(template = "empty")
    public static void theLastStackTakenByAPlayerMidWalkIsNotClaimed(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 12);
        BlockPos place = c.blockPosition().offset(8, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        for (int tick = 0; tick < 4; tick++) c.tick();
        Player player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        ItemStack taken = item.getItem().copy();
        item.setItem(ItemStack.EMPTY);
        CompanionDeathSpots.onPlayerPickup(new ItemEntityPickupEvent.Post(player, item, taken));
        item.discard();
        walk(h, c, 400);
        h.assertTrue(c.deathTrip.lastLine().equals("Вещей на месте уже нет."),
                "He claimed a stack the player took: " + c.deathTrip.lastLine());
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) == null,
                "A place emptied by the player is still remembered");
        h.succeed();
    }

    /** Setting out twice does not spend two tries on one walk. */
    @GameTest(template = "empty")
    public static void beginTwiceDoesNotSpendAnotherTry(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        h.assertTrue(!c.deathTrip.begin(), "A second begin started another walk");
        var spots = CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId());
        h.assertTrue(spots.size() == 1 && spots.get(0).attempts() == 1, "The second begin spent another try: " + spots);
        h.succeed();
    }

    /** A place across dimensions with a manifest is kept for a portal, not judged empty from here. */
    @GameTest(template = "empty")
    public static void aPlaceAcrossDimensionsWithAManifestIsKept(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition();
        var dear = stack(h, place, new ItemStack(Items.DIAMOND, 4));
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        store.died(c.getOwnerId(), UUID.randomUUID(), Level.NETHER, place, 40, 0, CompanionDeathSpots.manifestOf(List.of(dear)));
        h.assertTrue(!c.deathTrip.begin(), "He set out across dimensions with no portal");
        h.assertTrue(store.all(c.getOwnerId()).size() == 1, "The dear place across dimensions was forgotten");
        h.succeed();
    }

    /** Every stack of a fall taken by the player empties the place before any walk. */
    @GameTest(template = "empty")
    public static void everyStackTakenByThePlayerEmptiesThePlace(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 12);
        BlockPos place = c.blockPosition().offset(8, 0, 0);
        var one = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var two = stack(h, place.offset(1, 0, 0), new ItemStack(Items.IRON_INGOT, 4));
        UUID spotId = place(h, c, place, 30, one, two);
        Player player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        for (ItemEntity item : List.of(one, two)) {
            ItemStack taken = item.getItem().copy();
            item.setItem(ItemStack.EMPTY);
            CompanionDeathSpots.onPlayerPickup(new ItemEntityPickupEvent.Post(player, item, taken));
            item.discard();
        }
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).manifestOf(c.getOwnerId(), spotId).isEmpty(),
                "The player's takes did not empty the manifest");
        h.assertTrue(!c.deathTrip.begin(), "He set out for a place the player had already emptied");
        h.succeed();
    }

    /** A place walked to once and emptied is not walked to again. */
    @GameTest(template = "empty")
    public static void thePlaceIsNotWalkedToAgainAfterTheWalk(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(!c.deathTrip.begin(), "He set out again for the place he had already emptied");
        h.succeed();
    }

    /** Two stacks that stayed are both named, with the reason each stayed. */
    @GameTest(template = "empty")
    public static void theReportNamesEveryStackThatStayed(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition();
        var one = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var two = stack(h, place.offset(1, 0, 0), new ItemStack(Items.IRON_INGOT, 4));
        one.setTarget(c.getOwnerId());
        two.setTarget(c.getOwnerId());
        place(h, c, place, 40, one, two);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        String line = c.deathTrip.lastLine();
        h.assertTrue(line.contains("Diamond") && line.contains("Iron Ingot"), "The report does not name both stacks: " + line);
        h.assertTrue(line.indexOf("Diamond") < line.indexOf("Iron Ingot") || line.indexOf("Iron Ingot") < line.indexOf("Diamond"),
                "The report names only one: " + line);
        h.succeed();
    }

    /** Junk that fell with the rest is part of the manifest and is fetched: the mark is the truth, not the price. */
    @GameTest(template = "empty")
    public static void junkInTheManifestIsFetchedWithTheRest(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        BlockPos place = c.blockPosition();
        var diamonds = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var dirt = stack(h, place.offset(1, 0, 0), new ItemStack(Items.DIRT, 12));
        place(h, c, place, 40, diamonds, dirt);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(diamonds.isRemoved() || diamonds.getItem().isEmpty(), "The diamonds were not taken");
        h.assertTrue(dirt.isRemoved() || dirt.getItem().isEmpty(), "The junk of the same fall was left");
        h.succeed();
    }

    // ---- the look at a stack: one look is not proof, a full bag is not a failed walk, the junk stays ----

    /** A stack looked for once and not found is not struck off the manifest: only a second look proves it gone. */
    @GameTest(template = "empty")
    public static void aStackMissedOnceIsNotPrunedAtOnce(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        BlockPos place = feet.offset(4, 0, 0);
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        UUID spotId = UUID.randomUUID();
        UUID phantom = UUID.randomUUID();
        store.died(c.getOwnerId(), spotId, h.getLevel().dimension(), place, 20, h.getLevel().getGameTime(),
                List.of(new CompanionDeathSpots.Entry(phantom, new Vec3(place.getX() + .5, place.getY() + .2, place.getZ() + .5),
                        "minecraft:diamond", 2)));
        h.assertTrue(!c.deathTrip.begin(), "He set out for a stack the first look did not find");
        h.assertTrue(store.manifestOf(c.getOwnerId(), spotId).size() == 1,
                "One look that found nothing struck the stack off: " + store.manifestOf(c.getOwnerId(), spotId));
        // The second look, a few ticks later, is the proof: the place is empty and is forgotten with a word about it.
        for (int tick = 0; tick < 5; tick++) c.tick();
        h.assertTrue(!c.deathTrip.begin(), "He set out for a stack the second look proved gone");
        h.assertTrue(store.byId(c.getOwnerId(), spotId) == null, "A place whose stack was missed twice is still remembered");
        h.assertTrue(c.deathTrip.lastLine().equals("Вещей на месте уже нет."), "He did not say the place was empty: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** A walk that ended only because the bag was full spends no try: the place waits for room, not for the budget. */
    @GameTest(template = "empty")
    public static void aFullBagDoesNotSpendATry(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 8);
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIAMOND, 64));
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var item = stack(h, place, new ItemStack(Items.IRON_INGOT, 8));
        UUID spotId = place(h, c, place, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        var spot = CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId);
        h.assertTrue(spot != null, "The place with a stack left was forgotten");
        h.assertTrue(spot.attempts() == 0, "A walk that ended for a full bag spent a try: " + spot.attempts());
        h.assertTrue(c.deathTrip.lastLine().contains("сумка полна"), "The report does not say why: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** Junk he let go to make room stays on the ground: taken back, it would fill the slot it was dropped for. */
    @GameTest(template = "empty")
    public static void junkHeLetGoIsNotPickedUpAgain(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 4);
        var junk = stack(h, c.blockPosition(), new ItemStack(Items.DIRT, 64));
        junk.getPersistentData().putBoolean("aicompanion:junk", true);
        for (int tick = 0; tick < 20; tick++) c.tick();
        h.assertTrue(junk.isAlive() && junk.getItem().getCount() == 64, "The junk he let go was taken up again: " + junk.getItem());
        int dirt = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.DIRT)) dirt += c.getItem(slot).getCount();
        h.assertTrue(dirt == 0, "The junk went back into the bag: " + dirt);
        h.succeed();
    }

    /** A part taken from an old drop without the owner's mark keeps the rest: the spot's own id finds the place. */
    @GameTest(template = "empty")
    public static void aPartTakenWithoutTheOwnersMarkKeepsTheRest(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        BlockPos place = feet.offset(4, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 5));
        UUID spotId = UUID.randomUUID();
        // An old drop: the death is marked, the owner's mark is not.
        item.getPersistentData().putUUID("aicompanion:belongings_spot", spotId);
        CompanionDeathSpots.get(h.getLevel().getServer()).died(c.getOwnerId(), spotId, h.getLevel().dimension(), place, 50,
                h.getLevel().getGameTime(), CompanionDeathSpots.manifestOf(List.of(item)));
        Player player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        ItemStack taken = new ItemStack(Items.DIAMOND, 3);
        item.setItem(new ItemStack(Items.DIAMOND, 2));
        CompanionDeathSpots.onPlayerPickup(new ItemEntityPickupEvent.Post(player, item, taken));
        var manifest = CompanionDeathSpots.get(h.getLevel().getServer()).manifestOf(c.getOwnerId(), spotId);
        h.assertTrue(manifest.size() == 1 && manifest.get(0).count() == 2,
                "A part taken without the owner's mark left a stale count: " + manifest);
        h.succeed();
    }

    /** A place saved without its own id gets one, so a walk can charge it and forget it by that id. */
    @GameTest(template = "empty")
    public static void aPlaceSavedWithoutAnIdGetsOne(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        CompoundTag one = new CompoundTag();
        one.putString("dimension", "minecraft:overworld");
        one.putInt("x", 1);
        one.putInt("y", 2);
        one.putInt("z", 3);
        one.putDouble("worth", 40);
        one.putLong("tick", 0);
        one.putInt("attempts", 1);
        one.putInt("deaths", 0);
        one.put("manifest", new ListTag());
        ListTag list = new ListTag();
        list.add(one);
        CompoundTag owners = new CompoundTag();
        owners.put(owner.toString(), list);
        CompoundTag tag = new CompoundTag();
        tag.put("owners", owners);
        CompanionDeathSpots data = CompanionDeathSpots.load(tag, h.getLevel().registryAccess());
        var spots = data.all(owner);
        h.assertTrue(spots.size() == 1 && spots.get(0).id() != null, "A place saved without an id has none: " + spots);
        data.attempted(owner, spots.get(0).id());
        data.taken(owner, spots.get(0).id());
        h.assertTrue(data.all(owner).isEmpty(), "A place saved without an id could not be forgotten by its id");
        h.succeed();
    }
}

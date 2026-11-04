package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBelongings;
import dev.aicompanion.entity.CompanionDeathSpots;
import dev.aicompanion.entity.CompanionDeathTrip;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

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

    /** Two falls at one place are two records with their own manifests, and one is not the other's. */
    @GameTest(template = "empty")
    public static void twoFallsAtOnePlaceAreTwoRecords(GameTestHelper h) {
        CompanionEntity c = spawn(h);
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
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).isEmpty(),
                "Junk was remembered");
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
        yard(h, c, 16);
        BlockPos place = c.blockPosition().offset(4, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        h.assertTrue(c.deathTrip.begin(), "He did not set out");
        for (int tick = 0; tick < 600 && c.deathTrip.active(); tick++) c.tick();
        h.assertTrue(!c.deathTrip.active(), "The walk never ended: " + c.deathTrip.describe());
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The diamond was not taken: " + item.getItem());
        h.assertTrue(c.deathTrip.verdict(item.getUUID()) != null
                        && c.deathTrip.verdict(item.getUUID()).fate() == CompanionDeathTrip.Fate.TAKEN,
                "The ledger does not say taken: " + c.deathTrip.verdict(item.getUUID()));
        h.assertTrue(c.deathTrip.lastLine().equals("Забрал свои вещи."),
                "He did not say he had them: " + c.deathTrip.lastLine());
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) == null,
                "The place is still remembered");
        h.succeed();
    }

    /** A place whose stacks are all gone is forgotten without a walk, with a word about it. */
    @GameTest(template = "empty")
    public static void anEmptyPlaceIsForgottenWithoutAWalk(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
        BlockPos place = c.blockPosition().offset(10, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        UUID spotId = place(h, c, place, 20, item);
        item.discard();
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
        yard(h, c, 16);
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
        yard(h, c, 16);
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
        h.assertTrue(c.deathTrip.begin(), "He did not set out for the rest");
        for (int tick = 0; tick < 600 && c.deathTrip.active(); tick++) c.tick();
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The rest was not taken");
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) == null,
                "The place is still remembered after the rest was taken");
        h.succeed();
    }

    /** A stack that vanished is gone in the ledger and is not claimed as taken. */
    @GameTest(template = "empty")
    public static void aStackThatVanishedIsGoneNotTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
        BlockPos place = c.blockPosition().offset(14, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(c.deathTrip.begin(), "He did not set out");
        for (int tick = 0; tick < 6; tick++) c.tick();
        item.discard();
        for (int tick = 0; tick < 600 && c.deathTrip.active(); tick++) c.tick();
        h.assertTrue(!c.deathTrip.active(), "The walk never ended");
        var verdict = c.deathTrip.verdict(item.getUUID());
        h.assertTrue(verdict != null && verdict.fate() == CompanionDeathTrip.Fate.GONE,
                "A stack that vanished was not called gone: " + verdict);
        h.assertTrue(c.deathTrip.lastLine().equals("Вещей на месте уже нет."),
                "He claimed what he never had: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** A stack the player emptied by throwing it back is gone, and the place is forgotten without a chase. */
    @GameTest(template = "empty")
    public static void aStackThrownBackIsNotChased(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
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
        yard(h, c, 16);
        BlockPos place = c.blockPosition();
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        item.setTarget(c.getOwnerId());
        UUID spotId = place(h, c, place, 20, item);
        h.assertTrue(c.deathTrip.begin(), "He did not set out");
        for (int tick = 0; tick < 400 && c.deathTrip.active(); tick++) c.tick();
        h.assertTrue(!c.deathTrip.active(), "The walk never ended");
        h.assertTrue(!item.isRemoved() && !item.getItem().isEmpty(), "The stack aimed at another was taken");
        var verdict = c.deathTrip.verdict(item.getUUID());
        h.assertTrue(verdict != null && verdict.fate() == CompanionDeathTrip.Fate.UNREACHABLE,
                "The stack he could not take was not called unreachable: " + verdict);
        h.assertTrue(verdict.reason().equals("метит в другого"), "The reason is not the real one: " + verdict.reason());
        h.assertTrue(c.deathTrip.lastLine().startsWith("Часть моих вещей осталась"),
                "He claimed more than he took: " + c.deathTrip.lastLine());
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) != null,
                "A place with something left was forgotten");
        h.succeed();
    }

    /** A full bag is made room in only for a stack in reach, and the junk let go is marked as junk. */
    @GameTest(template = "empty")
    public static void aFullBagMakesRoomForAStackInReach(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIRT, 64));
        BlockPos place = c.blockPosition();
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(c.deathTrip.begin(), "He did not set out with a full bag");
        for (int tick = 0; tick < 300 && c.deathTrip.active(); tick++) c.tick();
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

    /** A diamond in a bag of dirt is never the junk that makes room. */
    @GameTest(template = "empty")
    public static void theDiamondIsNeverTheJunkThatMakesRoom(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIRT, 64));
        c.setItem(0, new ItemStack(Items.DIAMOND, 3));
        BlockPos place = c.blockPosition();
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(c.deathTrip.begin(), "He did not set out");
        for (int tick = 0; tick < 300 && c.deathTrip.active(); tick++) c.tick();
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
        yard(h, c, 16);
        BlockPos place = c.blockPosition();
        var taken = stack(h, place, new ItemStack(Items.DIAMOND, 4));
        var left = stack(h, place.offset(1, 0, 0), new ItemStack(Items.DIAMOND, 4));
        left.setTarget(c.getOwnerId());
        place(h, c, place, 40, taken, left);
        h.assertTrue(c.deathTrip.begin(), "He did not set out");
        for (int tick = 0; tick < 400 && c.deathTrip.active(); tick++) c.tick();
        h.assertTrue(taken.isRemoved() || taken.getItem().isEmpty(), "The reachable stack was not taken");
        h.assertTrue(!left.isRemoved() && !left.getItem().isEmpty(), "The stack aimed at another was taken");
        h.assertTrue(c.deathTrip.lastLine().startsWith("Часть моих вещей осталась"),
                "He did not say part of it stayed: " + c.deathTrip.lastLine());
        h.assertTrue(c.deathTrip.lastLine().contains("Diamond"), "The report does not name what stayed: " + c.deathTrip.lastLine());
        h.assertTrue(c.deathTrip.lastLine().contains("метит в другого"), "The report does not say why: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** Nothing of a walk in his bag but all of it taken: the place goes and the report is exact. */
    @GameTest(template = "empty")
    public static void everyStackTakenIsTheOnlyClaimOfEverything(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
        BlockPos place = c.blockPosition().offset(3, 0, 0);
        var one = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        var two = stack(h, place.offset(1, 0, 0), new ItemStack(Items.IRON_INGOT, 5));
        UUID spotId = place(h, c, place, 30, one, two);
        h.assertTrue(c.deathTrip.begin(), "He did not set out");
        for (int tick = 0; tick < 600 && c.deathTrip.active(); tick++) c.tick();
        h.assertTrue(one.isRemoved() || one.getItem().isEmpty(), "The diamonds were left");
        h.assertTrue(two.isRemoved() || two.getItem().isEmpty(), "The ingots were left");
        h.assertTrue(c.deathTrip.lastLine().equals("Забрал свои вещи."), "He did not claim them: " + c.deathTrip.lastLine());
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).byId(c.getOwnerId(), spotId) == null,
                "The place is still remembered");
        h.succeed();
    }

    /** A stack somebody else carried off in the middle of the walk is not claimed: the report names the loss. */
    @GameTest(template = "empty")
    public static void aStackTakenMidWalkIsNotClaimed(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
        BlockPos place = c.blockPosition().offset(12, 0, 0);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        h.assertTrue(c.deathTrip.begin(), "He did not set out");
        for (int tick = 0; tick < 4; tick++) c.tick();
        item.discard();
        for (int tick = 0; tick < 400 && c.deathTrip.active(); tick++) c.tick();
        h.assertTrue(!c.deathTrip.lastLine().equals("Забрал свои вещи."),
                "He claimed a stack somebody else took: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** A stack burned in lava is gone with a reason of its own, not taken. */
    @GameTest(template = "empty")
    public static void aStackInLavaIsGoneWithItsReason(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        put(h, place.below(), Blocks.LAVA);
        var item = stack(h, place, new ItemStack(Items.DIAMOND, 2));
        place(h, c, place, 20, item);
        item.discard();
        h.assertTrue(!c.deathTrip.begin(), "He set out for a stack that burned");
        h.assertTrue(c.deathTrip.lastLine().equals("Вещей на месте уже нет."),
                "He did not say the place was empty: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** A stack a mob picked up leaves nothing on the ground: no walk, and no claim. */
    @GameTest(template = "empty")
    public static void aStackAMobTookIsNotWalkedTo(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
        BlockPos place = c.blockPosition().offset(6, 0, 0);
        var zombie = new net.minecraft.world.entity.monster.Zombie(h.getLevel());
        zombie.moveTo(place.getX() + .5, place.getY(), place.getZ() + .5);
        zombie.setCanPickUpLoot(true);
        h.getLevel().addFreshEntity(zombie);
        var sword = stack(h, place, new ItemStack(Items.IRON_SWORD));
        place(h, c, place, 20, sword);
        for (int tick = 0; tick < 80 && !sword.isRemoved(); tick++) zombie.tick();
        h.assertTrue(sword.isRemoved(), "The zombie did not pick the sword up");
        h.assertTrue(!c.deathTrip.begin(), "He set out after a pile a zombie already took");
        h.succeed();
    }

    /** Two falls close by: the dearer first, then the other, and one walk takes both. */
    @GameTest(template = "empty")
    public static void theDearerFallGoesFirstAndTheOtherAfter(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        yard(h, c, 16);
        BlockPos dear = c.blockPosition().offset(-3, 0, 0);
        BlockPos cheap = c.blockPosition().offset(3, 0, 0);
        var dearItems = stack(h, dear, new ItemStack(Items.DIAMOND, 6));
        var cheapItems = stack(h, cheap, new ItemStack(Items.IRON_INGOT, 6));
        place(h, c, dear, 60, dearItems);
        place(h, c, cheap, 18, cheapItems);
        h.assertTrue(c.deathTrip.begin(), "He did not set out");
        h.assertTrue(c.deathTrip.describe().contains(dear.toShortString()),
                "He set out for the cheaper place: " + c.deathTrip.describe());
        for (int tick = 0; tick < 1200 && !CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).isEmpty(); tick++) {
            if (!c.deathTrip.active()) c.deathTrip.begin();
            c.tick();
        }
        h.assertTrue(dearItems.isRemoved() || dearItems.getItem().isEmpty(), "The dearer place was not fetched");
        h.assertTrue(cheapItems.isRemoved() || cheapItems.getItem().isEmpty(), "The cheaper place was left behind");
        h.assertTrue(CompanionDeathSpots.get(h.getLevel().getServer()).all(c.getOwnerId()).isEmpty(),
                "A place is still remembered after both walks");
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
        h.assertTrue(c.deathTrip.begin(), "He gave up on a place he could not look at");
        h.assertTrue(c.deathTrip.active(), "The walk did not start");
        h.assertTrue(c.deathTrip.verdict(item.getUUID()) == null
                        || c.deathTrip.verdict(item.getUUID()).fate() == CompanionDeathTrip.Fate.PENDING,
                "An unloaded stack was judged: " + c.deathTrip.verdict(item.getUUID()));
        h.succeed();
    }


    // ---- helpers ----

    /** An item entity of the test's own, not yet part of a fall. */
    private static ItemEntity stack(GameTestHelper h, BlockPos pos, ItemStack stack) {
        var item = new ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY() + .2, pos.getZ() + .5, stack);
        h.getLevel().addFreshEntity(item);
        return item;
    }

    /** A fall at a place, with the manifest of these stacks; returns the place's id. */
    private static UUID place(GameTestHelper h, CompanionEntity c, BlockPos pos, double worth, ItemEntity... items) {
        UUID spotId = UUID.randomUUID();
        for (ItemEntity item : items) {
            item.getPersistentData().putUUID("aicompanion:belongings", c.getOwnerId());
            item.getPersistentData().putUUID("aicompanion:belongings_spot", spotId);
        }
        CompanionDeathSpots.get(h.getLevel().getServer()).died(c.getOwnerId(), spotId, h.getLevel().dimension(), pos,
                worth, h.getLevel().getGameTime(), CompanionDeathSpots.manifestOf(List.of(items)));
        return spotId;
    }

    /** A flat stone yard round him, air above: the test's ground of its own, whatever another test left nearby. */
    private static BlockPos yard(GameTestHelper h, CompanionEntity c, int radius) {
        BlockPos feet = c.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            put(h, feet.offset(dx, -1, dz), Blocks.STONE);
            for (int dy = 0; dy <= 2; dy++) put(h, feet.offset(dx, dy, dz), Blocks.AIR);
        }
        return feet;
    }

    /** A block at an absolute position (the helper's own setBlock counts from the structure's corner). */
    private static void put(GameTestHelper h, BlockPos pos, net.minecraft.world.level.block.Block block) {
        h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
    }

    /** Lets him fall to the ground before the test builds its geometry round him. */
    private static void settle(CompanionEntity c) {
        for (int tick = 0; tick < 40 && !c.onGround(); tick++) c.tick();
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 2, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() - 4);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        dev.aicompanion.entity.CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        // The world and its store outlive a test: this owner starts with no places of his own.
        var store = CompanionDeathSpots.get(h.getLevel().getServer());
        for (var spot : store.all(owner.getUUID())) store.taken(owner.getUUID(), spot.id());
        return companion;
    }
}

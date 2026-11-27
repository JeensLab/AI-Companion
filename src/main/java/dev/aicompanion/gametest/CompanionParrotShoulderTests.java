package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionPet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionParrotShoulderTests {
    @GameTest(template = "large_search", timeoutTicks = 1000, batch = "parrot_shoulder_music")
    public static void parrotFliesOntoShoulderRidesAndDances(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var bird = tame(h, companion, player);
        var record = new BlockPos(16, 3, 8);
        h.setBlock(record, Blocks.JUKEBOX);
        var jukebox = (JukeboxBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(record));
        jukebox.setTheItem(new ItemStack(Items.MUSIC_DISC_CAT));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(bird.isTame(), companion.getLastTaskResult()))
                .thenExecute(() -> {
                    h.assertTrue(companion.getUUID().equals(bird.getOwnerUUID()) && !bird.isPassenger(), "Taming skipped the flight");
                    bird.setNoAi(false);
                }).thenWaitUntil(() -> h.assertTrue(bird.getVehicle() == companion, "The bird never flew onto Oleg's shoulder"))
                .thenIdle(2)
                .thenExecute(() -> {
                    h.assertTrue(companion.autonomyLine().contains("на плече"), "The first landing went unnoticed");
                    h.assertTrue(companion.getControllingPassenger() == null, "The parrot took control of Oleg");
                    h.assertTrue(bird.isInSittingPose() && bird.position().distanceTo(companion.getPassengerRidingPosition(bird)) < 0.1
                            && bird.getY() > companion.getY() + 1, "The parrot did not land at shoulder height");
                    h.assertTrue(!bird.isPartyParrot(), "Danced outside the jukebox's range");
                    companion.setFollowing(false);
                    var nearMusic = h.absolutePos(new BlockPos(15, 3, 8));
                    companion.teleportTo(nearMusic.getX() + 0.5, nearMusic.getY(), nearMusic.getZ() + 0.5);
                }).thenWaitUntil(() -> h.assertTrue(bird.isPartyParrot() && bird.getVehicle() == companion,
                        "The perched parrot did not dance; record playing=" + jukebox.getSongPlayer().isPlaying()
                                + ", perched=" + (bird.getVehicle() == companion) + ", bird=" + h.relativePos(bird.blockPosition())
                                + ", owner=" + h.relativePos(companion.blockPosition()) + ", jukebox=" + record))
                .thenExecute(() -> jukebox.removeTheItem())
                .thenWaitUntil(() -> h.assertTrue(!bird.isPartyParrot(), "Danced after the record stopped"))
                .thenExecute(() -> order(h, player, "sit_parrot"))
                .thenWaitUntil(() -> h.assertTrue(!bird.isPassenger() && bird.isInSittingPose(), "Did not leave the shoulder to sit"))
                .thenExecute(() -> order(h, player, "follow_parrot"))
                .thenWaitUntil(() -> h.assertTrue(bird.getVehicle() == companion, "The same parrot did not return to the shoulder"))
                .thenExecute(() -> {
                    h.setBlock(new BlockPos(11, 3, 8), Blocks.CAMPFIRE);
                    order(h, player, "campfire_song");
                }).thenWaitUntil(() -> h.assertTrue(!companion.campfireTune().isEmpty() && bird.getVehicle() == companion
                                && bird.isPartyParrot(), "The perched parrot did not dance to Oleg's guitar"))
                .thenExecute(() -> order(h, player, "stop"))
                .thenWaitUntil(() -> h.assertTrue(companion.campfireTune().isEmpty() && !bird.isPartyParrot(),
                        "The dance continued after Oleg put the guitar away"))
                .thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void perchedParrotSurvivesReloadAndOwnerDeath(GameTestHelper h) {
        var level = h.getLevel();
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        var player = level.getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var bird = tame(h, companion, player);
        bird.setVariant(Parrot.Variant.BLUE);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(bird.isTame(), companion.getLastTaskResult()))
                .thenExecute(() -> bird.setNoAi(false))
                .thenWaitUntil(() -> h.assertTrue(bird.getVehicle() == companion, "Parrot did not perch before save"))
                .thenExecute(() -> {
                    var destination = h.absolutePos(new BlockPos(19, 3, 16));
                    companion.teleportTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
                    var expected = bird.blockPosition();
                    var saved = new CompoundTag();
                    h.assertTrue(companion.save(saved), "Could not save Oleg and his passenger");
                    bird.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    var where = CompanionEvents.executeAction(player, "pet_location", "parrot", 1);
                    h.assertTrue(where.success() && where.message().contains(expected.toShortString())
                                    && where.message().contains(level.dimension().location().toString()),
                            "Wrong last location after riding and unloading: " + where.message());
                    companion.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    var reloaded = EntityType.loadEntityRecursive(saved, level, entity -> entity);
                    h.assertTrue(reloaded instanceof CompanionEntity, "Could not reload Oleg");
                    level.addFreshEntityWithPassengers(reloaded);
                }).thenWaitUntil(() -> {
                    var reloaded = CompanionEvents.getOwnedCompanion(player);
                    h.assertTrue(level.getEntity(bird.getUUID()) instanceof Parrot passenger && passenger.getVehicle() == reloaded
                                    && passenger.getVariant() == Parrot.Variant.BLUE && reloaded.hasPet(CompanionPet.PARROT),
                            "Reload lost the original bird, its variant or its perch");
                }).thenExecute(() -> CompanionEvents.getOwnedCompanion(player).kill())
                .thenWaitUntil(() -> {
                    var back = CompanionEvents.getOwnedCompanion(player);
                    h.assertTrue(back != null && !back.getUUID().equals(companion.getUUID()) && back.hasPet(CompanionPet.PARROT)
                                    && level.getEntity(bird.getUUID()) instanceof Parrot survivor
                                    && back.getUUID().equals(survivor.getOwnerUUID()) && survivor.getVehicle() == back,
                            "The original parrot did not rejoin the respawned Oleg");
                }).thenSucceed();
    }

    private static Parrot tame(GameTestHelper h, CompanionEntity companion, ServerPlayer player) {
        var bird = h.spawn(EntityType.PARROT, 9, 3, 8);
        bird.setNoAi(true);
        bird.getRandom().setSeed(0);
        companion.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 12));
        order(h, player, "tame_parrot");
        return bird;
    }

    private static void order(GameTestHelper h, ServerPlayer player, String action) {
        var result = CompanionEvents.executeAction(player, action, "", 1);
        h.assertTrue(result.success(), result.message());
    }
}

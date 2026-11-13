package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionMusicPack;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.entity.CompanionCampfire;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMusicLibrary;
import dev.aicompanion.entity.CompanionTunes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;

/**
 * The evening by the fire: he sits down by a lit campfire and plays his own tunes, by request or on his own, and stops for any order.
 * Every test that lays its ground has a batch of its own: the evening watches for monsters twelve blocks round, which reaches into
 * the next arenas of a shared batch, where other tests keep zombies.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCampfireTests {
    private static final int GROUND = 5;
    private static final BlockPos FIRE = new BlockPos(16, GROUND + 1, 16);

    /**
     * These tests play the fallback (standard Minecraft music, CompanionTunes): the folder is pointed at an empty one, so whatever
     * the player left in the run's config folder does not change what an evening by the fire plays.
     */
    static {
        try {
            CompanionMusicLibrary.readFrom(java.nio.file.Files.createTempDirectory("aicompanion-empty"));
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    /**
     * «Сыграй» by a lit fire: he sits down across the fire from the player and the guitar sounds; his eyes stay on the fire though the
     * player looks at him, and his face follows the tune. «Стоп» puts the guitar away, the fire stays.
     */
    @GameTest(template = "empty", batch = "campfire_request", timeoutTicks = 600)
    public static void asksToPlayByALitFire(GameTestHelper h) {
        ground(h);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        var result = CompanionEvents.executeAction(player, "campfire_song", "", 1);
        h.assertTrue(result.success() && c.campfire().active(), "Not taken: " + result.message());
        String[] reply = {null};
        h.onEachTick(() -> {
            // The player stares at him the whole time: that alone used to turn his eyes to the player.
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, c.getEyePosition());
            if (reply[0] != null || c.campfirePose() != CompanionCampfire.PLAYING || c.campfire().playedTicks() < 30) return;
            var fire = h.absoluteVec(FIRE.getCenter());
            float toFire = (float) (net.minecraft.util.Mth.atan2(fire.z - c.getZ(), fire.x - c.getX()) * net.minecraft.util.Mth.RAD_TO_DEG) - 90F;
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(c.getYHeadRot() - toFire)) < 25 && Math.abs(net.minecraft.util.Mth.wrapDegrees(c.yBodyRot - toFire)) < 10,
                    "Not facing the fire: head " + c.getYHeadRot() + ", body " + c.yBodyRot + ", fire at " + toFire);
            var tune = tune(c);
            h.assertTrue(c.expression() == (tune.mood() == CompanionMusicLibrary.Mood.CHEERFUL ? dev.aicompanion.CompanionExpression.PLEASED : dev.aicompanion.CompanionExpression.CALM),
                    "Face " + c.expression() + " for «" + tune.title() + "»");
            h.assertTrue(c.distanceToSqr(fire) <= 3.2 * 3.2, "He plays away from the fire: " + c.position());
            // Round the fire to the seat and the first bars: seconds, not the walk's twenty-second limit.
            h.assertTrue(h.getTick() < 20 * 14, "Took " + h.getTick() + " ticks to sit down and play");
            h.assertTrue(c.distanceToSqr(player) >= 2.5 * 2.5, "He sat on the player's side, not across the fire: " + c.position());
            reply[0] = CompanionEvents.executeAction(player, "stop", "", 1).message();
        });
        h.succeedWhen(() -> {
            h.assertTrue(reply[0] != null, "Never sat down and played: pose " + c.campfirePose() + ", played " + c.campfire().playedTicks() + ", ended " + c.campfire().ended());
            h.assertTrue(!c.campfire().active() && c.campfirePose() == CompanionCampfire.STANDING, "The guitar is still out after «стоп»");
            h.assertTrue("Ладно, убираю гитару.".equals(reply[0]), "Stop reply: " + reply[0]);
            h.assertTrue(CampfireBlock.isLitCampfire(h.getBlockState(FIRE)), "The fire is gone");
        });
    }

    /**
     * From one tune to the next he stays where he sat down: no standing up, no other seat (owner 09.11: he moved between tunes). The
     * player stands up on a mound six blocks off, out of a walk's reach: the following's «too far, no way» must not take him off his seat.
     */
    @GameTest(template = "empty", batch = "campfire_seat", timeoutTicks = 20 * 280)
    public static void staysOnHisSeatFromTuneToTune(GameTestHelper h) {
        ground(h);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        for (int y = GROUND + 1; y <= GROUND + 2; y++) h.setBlock(new BlockPos(12, y, 16), Blocks.BEDROCK);
        CompanionEntity c = spawn(h, 9, 18, 12, 16);
        ServerPlayer player = owner(h, c);
        var mound = h.absoluteVec(new net.minecraft.world.phys.Vec3(12.5, GROUND + 3, 16.5));
        player.teleportTo(mound.x, mound.y, mound.z);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "", 1).success(), "Not taken");
        net.minecraft.world.phys.Vec3[] seat = {null};
        String[] first = {null}, trace = {""};
        boolean[] next = {false};
        double[] moved = {0};
        int[] lastPose = {-1};
        h.onEachTick(() -> {
            if (next[0]) return;
            int pose = c.campfirePose();
            if (pose != lastPose[0]) { trace[0] += h.getTick() + ":" + pose + "@" + c.blockPosition().toShortString() + " "; lastPose[0] = pose; }
            if (seat[0] == null) {
                if (pose == CompanionCampfire.PLAYING) { seat[0] = c.position(); first[0] = c.campfire().tuneTitle(); }
                return;
            }
            moved[0] = Math.max(moved[0], c.position().distanceTo(seat[0]));
            h.assertTrue(c.campfire().active() && pose != CompanionCampfire.STANDING, "Stood up between tunes: " + trace[0] + " ended " + c.campfire().ended());
            if (pose == CompanionCampfire.PLAYING && !c.campfire().tuneTitle().equals(first[0])) next[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(next[0], "The second tune never began: " + trace[0]);
            h.assertTrue(moved[0] < .15, "Moved " + moved[0] + " from his seat between tunes: " + trace[0]);
        });
    }

    /** Asked again while he plays («сыграй грустное», «повеселее»): the tune changes to that kind at once, and he stays on his seat. */
    @GameTest(template = "empty", batch = "campfire_again", timeoutTicks = 900)
    public static void askingAgainChangesTheTuneNotTheSeat(GameTestHelper h) {
        ground(h);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "cheerful", 1).success(), "Not taken");
        net.minecraft.world.phys.Vec3[] seat = {null};
        int[] stage = {0};
        long[] askedAt = {0};
        h.onEachTick(() -> {
            if (stage[0] == 0 && c.campfirePose() == CompanionCampfire.PLAYING) {
                h.assertTrue(tune(c).mood() == CompanionMusicLibrary.Mood.CHEERFUL, "Asked for a cheerful one, got «" + c.campfire().tuneTitle() + "»");
                seat[0] = c.position();
                String before = c.campfire().tuneTitle();
                var again = CompanionEvents.executeAction(player, "campfire_song", "calm", 1);
                h.assertTrue(again.success() && c.campfire().active(), "Asking again: " + again.message());
                h.assertTrue(!c.campfire().tuneTitle().equals(before) && tune(c).mood() != CompanionMusicLibrary.Mood.CHEERFUL,
                        "Not a calm tune at once: «" + c.campfire().tuneTitle() + "»");
                askedAt[0] = h.getTick();
                stage[0] = 1;
            }
            if (stage[0] == 1) h.assertTrue(c.position().distanceTo(seat[0]) < .15 && c.campfirePose() == CompanionCampfire.PLAYING,
                    "Left his seat or stopped after being asked again: " + c.position() + " pose " + c.campfirePose());
        });
        h.succeedWhen(() -> h.assertTrue(stage[0] == 1 && h.getTick() - askedAt[0] > 60, "Not played on after being asked again"));
    }

    /** /sit: the player sits on the block he stands on; /sit again stands him up, and the seat is gone. Nothing of it is saved. */
    @GameTest(template = "empty", batch = "campfire_sit")
    public static void sitCommandSeatsThePlayerAndLetsHimUp(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h, 20, 20, 10, 10);
        ServerPlayer player = owner(h, c);
        var commands = h.getLevel().getServer().getCommands();
        var feet = player.position();
        // A mock player does not run the physics that would set it after the teleport.
        player.setOnGround(true);
        commands.performPrefixedCommand(player.createCommandSourceStack(), "sit");
        h.assertTrue(player.getVehicle() instanceof dev.aicompanion.entity.CompanionSeat, "Not seated: " + player.getVehicle());
        var seat = player.getVehicle();
        h.assertTrue(seat.position().distanceTo(feet) < .01 && !seat.shouldBeSaved(), "The seat is not at his feet or would be saved");
        h.runAtTickTime(h.getTick() + 10, () -> {
            h.assertTrue(player.getVehicle() == seat && seat.isAlive(), "Seat lost while he sits");
            commands.performPrefixedCommand(player.createCommandSourceStack(), "sit");
            h.assertTrue(!player.isPassenger(), "Still seated after /sit again");
        });
        h.runAtTickTime(h.getTick() + 15, () -> h.succeedIf(() -> h.assertTrue(seat.isRemoved(), "The empty seat stayed in the world")));
    }

    /** No fire near, a campfire in his bag: he sets that one down (a real block from the bag) and plays by it. */
    @GameTest(template = "empty", batch = "campfire_bag", timeoutTicks = 600)
    public static void setsDownHisOwnCampfireFromTheBag(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h, 16, 16, 13, 16);
        c.setItem(0, new ItemStack(Items.CAMPFIRE, 2));
        var result = CompanionEvents.executeAction(owner(h, c), "campfire_song", "", 1);
        h.assertTrue(result.success(), "Refused: " + result.message());
        h.assertTrue(campfires(h) == 1 && count(c, Items.CAMPFIRE) == 1, "Not one campfire set down from the bag: " + campfires(h) + " placed, " + count(c, Items.CAMPFIRE) + " left");
        h.assertTrue(c.campfire().fire() != null && CampfireBlock.isLitCampfire(h.getLevel().getBlockState(c.campfire().fire())), "The evening is not by the new fire");
        h.succeedWhen(() -> h.assertTrue(c.campfirePose() == CompanionCampfire.PLAYING && c.campfire().tunesStarted() > 0,
                "Not playing by his fire: pose " + c.campfirePose() + ", ended " + c.campfire().ended()));
    }

    /**
     * Neither a fire near nor one in the bag: a fire is not needed for the music (owner 09.11). He sits down beside the player, facing
     * him, and plays; nothing is placed and nothing taken from the bag. The player walking off ends it.
     */
    @GameTest(template = "empty", batch = "campfire_none", timeoutTicks = 600)
    public static void withoutAFireHeSitsDownBesideThePlayer(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h, 20, 16, 13, 16);
        ServerPlayer player = owner(h, c);
        c.setItem(0, new ItemStack(Items.OAK_LOG, 3));
        var result = CompanionEvents.executeAction(player, "campfire_song", "", 1);
        h.assertTrue(result.success() && c.campfire().active() && c.campfire().fire() == null, "Not taken without a fire: " + result.message());
        boolean[] left = {false};
        h.onEachTick(() -> {
            if (left[0] || c.campfirePose() != CompanionCampfire.PLAYING || c.campfire().playedTicks() < 20) return;
            h.assertTrue(c.distanceToSqr(player) <= 3 * 3 && c.distanceToSqr(player) >= 1, "Not sitting beside the player: " + Math.sqrt(c.distanceToSqr(player)));
            h.assertTrue(h.getTick() < 20 * 12, "Took " + h.getTick() + " ticks to sit down and play");
            float toPlayer = (float) (net.minecraft.util.Mth.atan2(player.getZ() - c.getZ(), player.getX() - c.getX()) * net.minecraft.util.Mth.RAD_TO_DEG) - 90F;
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(c.yBodyRot - toPlayer)) < 15, "Not facing the player: body " + c.yBodyRot + ", player at " + toPlayer);
            h.assertTrue(campfires(h) == 0 && count(c, Items.OAK_LOG) == 3, "Something was placed or spent");
            var away = h.absoluteVec(new net.minecraft.world.phys.Vec3(28.5, GROUND + 1, 28.5));
            player.teleportTo(away.x, away.y, away.z);
            left[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(left[0], "Never sat down and played: pose " + c.campfirePose() + ", ended " + c.campfire().ended());
            h.assertTrue(!c.campfire().active() && c.campfirePose() == CompanionCampfire.STANDING && "player_left".equals(c.campfire().ended()),
                    "Still playing after the player left: " + c.campfire().ended());
        });
    }

    /**
     * The player stays by a lit fire for half a minute: he sits down and plays on his own; the player walks off and the evening ends.
     * Not again by itself while the player stays, after he was stopped.
     */
    @GameTest(template = "empty", batch = "campfire_linger", timeoutTicks = 1400)
    public static void playsOnHisOwnWhenThePlayerLingersByTheFire(GameTestHelper h) {
        ground(h);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 11, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        c.setLifestyle(c.loadsChunks(), false);
        long[] startedAt = {-1};
        boolean[] left = {false};
        h.onEachTick(() -> {
            if (startedAt[0] < 0 && c.campfire().active()) startedAt[0] = h.getTick();
            if (!left[0] && c.campfirePose() == CompanionCampfire.PLAYING && c.campfire().playedTicks() > 20) {
                var away = h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, GROUND + 1, 2.5));
                player.teleportTo(away.x, away.y, away.z);
                left[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(startedAt[0] >= 20L * 25, "Began before the player had lingered: tick " + startedAt[0]);
            h.assertTrue(left[0], "Did not sit down and play by himself: pose " + c.campfirePose());
            h.assertTrue(!c.campfire().active() && c.campfirePose() == CompanionCampfire.STANDING, "Still playing after the player left");
        });
    }

    /** A monster by the fire: «сыграй» waits for a calm place, and no guitar comes out. */
    @GameTest(template = "empty", batch = "campfire_monster")
    public static void aMonsterNearKeepsTheGuitarAway(GameTestHelper h) {
        ground(h);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 12, 16, 14, 16);
        var zombie = h.spawn(EntityType.ZOMBIE, 20, GROUND + 1, 16);
        zombie.setNoAi(true);
        var result = CompanionEvents.executeAction(owner(h, c), "campfire_song", "", 1);
        h.assertTrue(!result.success() && !c.campfire().active(), "Played with a monster by the fire: " + result.message());
        zombie.discard();
        h.succeed();
    }

    /**
     * The fallback while the folder is empty: standard Minecraft music, not a recording of the mod's — no music ships in the JAR at
     * all any more. Every disc is a registered vanilla sound (so the client resolves it from the game's own sounds.json) and is timed
     * like a tune, so the evening goes on to the next one.
     */
    @GameTest(template = "empty")
    public static void theFallbackIsVanillaMusicAndNoMusicShipsInTheJar(GameTestHelper h) {
        var titles = new HashSet<String>();
        var ids = new HashSet<String>();
        h.assertTrue(CompanionTunes.ALL.size() >= 3, "Too few standard tunes: " + CompanionTunes.ALL.size());
        h.assertTrue(CompanionTunes.ALL.stream().anyMatch(CompanionTunes.Tune::cheerful) && CompanionTunes.ALL.stream().anyMatch(t -> !t.cheerful()),
                "Both cheerful and calm tunes are needed");
        for (var tune : CompanionTunes.ALL) {
            h.assertTrue(titles.add(tune.title()) && ids.add(tune.soundId()), "Two tunes alike: " + tune);
            var id = net.minecraft.resources.ResourceLocation.parse(tune.soundId());
            h.assertTrue("minecraft".equals(id.getNamespace()) && id.getPath().startsWith("music_disc."),
                    "Not vanilla music: " + tune.soundId());
            h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(id), "Not a registered sound: " + id);
            h.assertTrue(tune.ticks() >= 20 * 60 && tune.ticks() <= 20 * 330, "Not a tune's length: " + tune.ticks() + " ticks");
        }
        var fallback = CompanionMusicLibrary.available();
        h.assertTrue(fallback.size() == CompanionTunes.ALL.size(), "The fallback set is not the catalog: " + fallback.size());
        for (var track : fallback) {
            h.assertTrue(track.file().isEmpty() && track.soundId().startsWith("minecraft:"), "Not the vanilla fallback: " + track);
            h.assertTrue(CompanionTunes.ALL.stream().anyMatch(t -> t.soundId().equals(track.soundId()) && t.ticks() == track.ticks()
                            && track.mood() == (t.cheerful() ? CompanionMusicLibrary.Mood.CHEERFUL : CompanionMusicLibrary.Mood.CALM)),
                    "The fallback disagrees with the catalog: " + track);
        }
        h.assertTrue(CompanionCampfireTests.class.getResource("/assets/aicompanion/sounds/guitar/romance.ogg") == null,
                "A recording still ships in the JAR");
        h.assertTrue(CompanionCampfireTests.class.getResource("/assets/aicompanion/sounds.json") == null,
                "The JAR still declares guitar sounds");
        h.succeed();
    }

    /**
     * The player's folder is read as it is: every .ogg in it is a playable recording with its own sound id, its length and its title;
     * a mood comes from the digit the name starts with, 1 (sad) to 9 (happy), while a plain name or a longer number (a year) carries
     * none; a broken file is skipped, and a missing folder is simply empty.
     */
    @GameTest(template = "empty")
    public static void theFolderIsReadForEveryRecordingInIt(GameTestHelper h) {
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-guitar");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        try {
            writeOgg(dir, "1_first.ogg", 100);
            writeOgg(dir, "8_second.ogg", 30);
            writeOgg(dir, "quiet_one.ogg", 50);
            writeOgg(dir, "1999_live.ogg", 20);
            write(dir, "broken.ogg", "not an ogg file at all");
            var tracks = CompanionMusicLibrary.scan(dir);
            h.assertTrue(tracks.size() == 4, "Found " + tracks.size() + " of four recordings: " + tracks);
            var byTitle = new java.util.HashMap<String, CompanionMusicLibrary.Track>();
            for (var track : tracks) byTitle.put(track.title(), track);
            h.assertTrue(byTitle.containsKey("First") && byTitle.containsKey("Second") && byTitle.containsKey("Quiet one")
                            && byTitle.containsKey("1999 live"),
                    "Titles: " + byTitle.keySet());
            var first = byTitle.get("First");
            var second = byTitle.get("Second");
            var quiet = byTitle.get("Quiet one");
            var year = byTitle.get("1999 live");
            h.assertTrue(first.mood() == CompanionMusicLibrary.Mood.CALM && second.mood() == CompanionMusicLibrary.Mood.CHEERFUL
                            && quiet.mood() == null && year.mood() == null,
                    "Moods: " + first.mood() + ", " + second.mood() + ", " + quiet.mood() + ", " + year.mood());
            h.assertTrue(first.ticks() == 2000 && second.ticks() == 600 && quiet.ticks() == 1000 && year.ticks() == 400,
                    "Lengths: " + first.ticks() + ", " + second.ticks() + ", " + quiet.ticks() + ", " + year.ticks());
            var ids = new HashSet<String>();
            for (var track : tracks) {
                h.assertTrue(track.soundId().startsWith("aicompanion:guitar_folder/") && ids.add(track.soundId()),
                        "Sound id: " + track.soundId());
                // The sound id goes into a ResourceLocation on the client: it must be one the game accepts.
                var id = net.minecraft.resources.ResourceLocation.parse(track.soundId());
                h.assertTrue(id.getPath().equals(track.soundId().substring("aicompanion:".length())), "Parsed: " + id);
            }
            h.assertTrue(CompanionMusicLibrary.scan(dir.resolve("no-such-folder")).isEmpty(), "A missing folder is not empty");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } finally {
            deleteTree(dir);
        }
        h.succeed();
    }

    /**
     * A tiny file is skipped, not thrown on: a recording cut short after the identification header (191 bytes here, the marker at its
     * very end) has no sample rate inside the file, and the scan used to fail on it with an IndexOutOfBoundsException, which broke the
     * whole folder and the resource pack built from it. The good recording next to it is still found.
     */
    @GameTest(template = "empty")
    public static void aTinyFileIsSkippedAndTheScanDoesNotThrow(GameTestHelper h) {
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-tiny");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        try {
            // The identification header starts at 180: only eleven bytes are left for the marker and nothing for the sample rate.
            byte[] cut = new byte[191];
            System.arraycopy(new byte[]{1, 'v', 'o', 'r', 'b', 'i', 's'}, 0, cut, 180, 7);
            writeBytes(dir, "tiny.ogg", cut);
            writeBytes(dir, "empty.ogg", new byte[0]);
            writeBytes(dir, "short.ogg", new byte[63]);
            writeOgg(dir, "good.ogg", 16);
            var tracks = CompanionMusicLibrary.scan(dir);
            h.assertTrue(tracks.size() == 1 && tracks.getFirst().ticks() == 320,
                    "The good recording was lost or a tiny one was taken: " + tracks);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } finally {
            deleteTree(dir);
        }
        h.succeed();
    }

    /**
     * The kind of tune asked for: a calm-tagged recording for «сыграй грустное», a cheerful one for «повеселее», and the honest answer
     * — no such recording, play any — when nothing is tagged so; «другую» never plays the same one twice running.
     */
    @GameTest(template = "empty")
    public static void aTaggedRecordingIsPickedForTheMoodOrHeSaysThereIsNone(GameTestHelper h) {
        var random = net.minecraft.util.RandomSource.create(7);
        var calm = new CompanionMusicLibrary.Track("a.ogg", "aicompanion:guitar_folder/a", "A", 400, CompanionMusicLibrary.Mood.CALM);
        var cheerful = new CompanionMusicLibrary.Track("b.ogg", "aicompanion:guitar_folder/b", "B", 400, CompanionMusicLibrary.Mood.CHEERFUL);
        var plain = new CompanionMusicLibrary.Track("c.ogg", "aicompanion:guitar_folder/c", "C", 400, null);
        var all = java.util.List.of(calm, cheerful, plain);
        for (int i = 0; i < 20; i++) {
            var pick = CompanionMusicLibrary.pick(all, "calm", "", random);
            h.assertTrue(pick.track() == calm && pick.moodMatched(), "A calm tune was asked for, got " + pick.track().title());
            pick = CompanionMusicLibrary.pick(all, "cheerful", "", random);
            h.assertTrue(pick.track() == cheerful && pick.moodMatched(), "A cheerful tune was asked for, got " + pick.track().title());
            pick = CompanionMusicLibrary.pick(all, "next", cheerful.soundId(), random);
            h.assertTrue(pick.track() != cheerful && pick.moodMatched(), "«Другую» played the same tune: " + pick.track().title());
            pick = CompanionMusicLibrary.pick(all, "", plain.soundId(), random);
            h.assertTrue(pick.track() != plain && pick.moodMatched(), "No request repeated the tune: " + pick.track().title());
        }
        var untagged = java.util.List.of(plain);
        var honest = CompanionMusicLibrary.pick(untagged, "calm", "", random);
        h.assertTrue(honest.track() == plain && !honest.moodMatched(), "An untagged calm pick was not honest");
        h.assertTrue(!CompanionMusicLibrary.hasMood(untagged, "calm") && !CompanionMusicLibrary.hasMood(untagged, "cheerful"),
                "An untagged set claims a mood");
        h.assertTrue(CompanionMusicLibrary.hasMood(all, "calm") && CompanionMusicLibrary.hasMood(all, "cheerful"), "Tagged moods not seen");
        // The fallback while the folder is empty: standard Minecraft music, one calm and one cheerful.
        var bundled = CompanionMusicLibrary.fallback();
        var calmFallback = CompanionMusicLibrary.pick(bundled, "calm", "", random);
        var cheerfulFallback = CompanionMusicLibrary.pick(bundled, "cheerful", "", random);
        h.assertTrue(calmFallback.track().mood() == CompanionMusicLibrary.Mood.CALM && calmFallback.moodMatched()
                        && cheerfulFallback.track().mood() == CompanionMusicLibrary.Mood.CHEERFUL && cheerfulFallback.moodMatched(),
                "The standard fallback has no tune of a kind: " + calmFallback + ", " + cheerfulFallback);
        h.assertTrue(CompanionMusicLibrary.available().size() >= 2, "Nothing to play at all");
        h.succeed();
    }

    /**
     * The dynamic resource pack the client builds from the folder: the game's sound engine knows a sound only from a pack, so the pack
     * has to declare every recording and serve its file. This checks what the game reads from it — the metadata of a required pack, the
     * generated sounds.json with the recording's sound id, and the file behind that id — and that the game accepts the pack at all.
     */
    @GameTest(template = "empty")
    public static void theFolderPackDeclaresAndServesItsRecordings(GameTestHelper h) {
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-pack");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        try {
            writeOgg(dir, "3_evening.ogg", 40);
            // An upgraded player keeps the folder made by an older build, with its older note in it.
            write(dir, "README.txt", "Музыка: положите сюда записи.\n");
            var location = new net.minecraft.server.packs.PackLocationInfo("aicompanion_music_test",
                    net.minecraft.network.chat.Component.literal("test"), net.minecraft.server.packs.repository.PackSource.BUILT_IN, java.util.Optional.empty());
            var pack = new CompanionMusicPack(location, dir);
            h.assertTrue(java.nio.file.Files.readString(dir.resolve("README.txt")).equals(CompanionMusicLibrary.FOLDER_README),
                    "The note in the folder is not this build's: " + java.nio.file.Files.readString(dir.resolve("README.txt")));
            h.assertTrue(pack.getMetadataSection(net.minecraft.server.packs.metadata.pack.PackMetadataSection.TYPE) != null, "No pack metadata");
            h.assertTrue(pack.getRootResource("pack.mcmeta") != null, "No pack.mcmeta");
            var soundsId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aicompanion", "sounds.json");
            var sounds = pack.getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, soundsId);
            h.assertTrue(sounds != null, "No sounds.json served");
            String json;
            try (var in = sounds.get()) { json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
            var root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            var track = CompanionMusicLibrary.scan(dir).getFirst();
            var key = track.soundId().substring("aicompanion:".length());
            var entry = root.getAsJsonObject(key);
            h.assertTrue(root.size() == 1 && entry != null, "sounds.json does not declare the recording: " + json);
            var sound = entry.getAsJsonArray("sounds").get(0).getAsJsonObject();
            h.assertTrue(track.soundId().equals(sound.get("name").getAsString()) && sound.get("stream").getAsBoolean(),
                    "The declaration does not point at the recording: " + entry);
            var listed = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
            pack.listResources(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, "aicompanion", "sounds", (id, supplier) -> listed.add(id));
            var fileId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aicompanion", "sounds/" + key + ".ogg");
            h.assertTrue(listed.contains(fileId), "The recording is not listed: " + listed);
            var file = pack.getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, fileId);
            h.assertTrue(file != null, "The recording is not served");
            h.assertTrue(file.get().readAllBytes().length == java.nio.file.Files.size(dir.resolve("3_evening.ogg")), "The served file is not the recording");
            var built = net.minecraft.server.packs.repository.Pack.readMetaAndCreate(location, new net.minecraft.server.packs.repository.Pack.ResourcesSupplier() {
                @Override public net.minecraft.server.packs.PackResources openPrimary(net.minecraft.server.packs.PackLocationInfo info) { return new CompanionMusicPack(info, dir); }
                @Override public net.minecraft.server.packs.PackResources openFull(net.minecraft.server.packs.PackLocationInfo info,
                                                                               net.minecraft.server.packs.repository.Pack.Metadata metadata) { return new CompanionMusicPack(info, dir); }
            }, net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                    new net.minecraft.server.packs.PackSelectionConfig(true, net.minecraft.server.packs.repository.Pack.Position.TOP, true));
            h.assertTrue(built != null, "The game refused the pack: no metadata");
            h.assertTrue(built.open().getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, soundsId) != null, "The opened pack serves no sounds.json");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } finally {
            deleteTree(dir);
        }
        h.succeed();
    }

    /**
     * «Олег играет у костра» off (the player's switch in «Поведение»): «сыграй» says so and no guitar comes out; the player lingers by
     * a lit fire and no evening begins by itself.
     */
    @GameTest(template = "empty", batch = "campfire_off", timeoutTicks = 900)
    public static void switchedOffThereIsNoGuitarByTheFire(GameTestHelper h) {
        ground(h);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 11, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        c.setLifestyle(c.loadsChunks(), false);
        c.setPlaysByTheFire(false);
        var result = CompanionEvents.executeAction(player, "campfire_song", "", 1);
        h.assertTrue(!result.success() && !c.campfire().active(), "The guitar came out with the switch off: " + result.message());
        h.assertTrue(result.message().contains("выключено"), "Not said plainly: " + result.message());
        boolean[] began = {false};
        h.onEachTick(() -> { if (c.campfire().active()) began[0] = true; });
        h.runAtTickTime(h.getTick() + 700, () -> {
            h.assertTrue(!began[0] && c.campfirePose() == CompanionCampfire.STANDING,
                    "An evening began by itself with the switch off: pose " + c.campfirePose());
            h.succeed();
        });
    }

    /** The switch lives in the save with him; an old save has it on, as the guitar had before the switch existed. */
    @GameTest(template = "empty")
    public static void theFireSwitchIsSavedAndAnOldSaveHasItOn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 4, 1, 4);
        h.assertTrue(c.playsByTheFire(), "The switch is not on by default");
        c.setPlaysByTheFire(false);
        CompanionEntity other = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        other.load(c.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(!other.playsByTheFire(), "The switch was not saved");
        CompanionEntity third = h.spawn(AICompanionMod.COMPANION.get(), 12, 1, 12);
        third.setPlaysByTheFire(false);
        third.load(new net.minecraft.nbt.CompoundTag());
        h.assertTrue(third.playsByTheFire(), "An old save loaded with the switch off");
        h.succeed();
    }

    /** The words: «сыграй» in its forms reaches him without the model, «хватит играть» stops, a game of hide-and-seek is not music. */
    @GameTest(template = "empty")
    public static void phrasesAndThePlanStep(GameTestHelper h) {
        for (String phrase : new String[]{"Олег, сыграй на гитаре", "поставь костёр и сыграй", "Сыграй что-нибудь", "сыграй", "Олег, сыграй нам!",
                "давай посидим у костра", "можешь сыграть песню?"})
            h.assertTrue("companion campfire".equals(CompanionLocalCommands.route(phrase, "Олег")), "Not the music: " + phrase + " -> " + CompanionLocalCommands.route(phrase, "Олег"));
        h.assertTrue("companion campfire calm".equals(CompanionLocalCommands.route("сыграй что нибудь грустное", "Олег")), "Sad tune");
        h.assertTrue("companion campfire cheerful".equals(CompanionLocalCommands.route("Олег, сыграй повеселее", "Олег")), "Cheerful tune");
        h.assertTrue("companion campfire next".equals(CompanionLocalCommands.route("давай другую мелодию", "Олег")), "Another tune: " + CompanionLocalCommands.route("давай другую мелодию", "Олег"));
        h.assertTrue("calm".equals(CompanionLocalCommands.actions("companion campfire calm").getFirst().item()), "Mood lost on the way to the step");
        h.assertTrue(CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString("[{\"action\":\"campfire_song\",\"item\":\"calm\"}]")).getFirst().item().equals("calm"), "Codec mood");
        for (String phrase : new String[]{"хватит играть", "Олег, перестань играть", "убери гитару"})
            h.assertTrue("companion stop".equals(CompanionLocalCommands.route(phrase, "Олег")), "Not a stop: " + phrase);
        for (String phrase : new String[]{"давай сыграем в прятки", "не играй сейчас на гитаре", "поставь костёр"})
            h.assertTrue(!"companion campfire".equals(CompanionLocalCommands.route(phrase, "Олег")), "Taken for music: " + phrase);
        var step = CompanionLocalCommands.actions("companion campfire").getFirst();
        h.assertTrue("campfire_song".equals(step.type()) && step.count() == 1 && step.item().isEmpty(), "Local step: " + step);
        var plan = CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString("[{\"action\":\"campfire_song\"}]"));
        h.assertTrue(plan.size() == 1 && "campfire_song".equals(plan.getFirst().type()) && plan.getFirst().count() == 1, "Plan: " + plan);
        for (String bad : new String[]{"[{\"action\":\"campfire_song\",\"count\":2}]", "[{\"action\":\"campfire_song\",\"item\":\"minecraft:campfire\"}]"}) {
            boolean refused = false;
            try { CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString(bad)); } catch (IllegalStateException expected) { refused = true; }
            h.assertTrue(refused, "Accepted " + bad);
        }
        h.succeed();
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            h.setBlock(new BlockPos(x, y, z), y <= GROUND || border ? Blocks.BEDROCK : Blocks.AIR);
        }
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z, int playerX, int playerZ) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(playerX, GROUND + 1, playerZ));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    /** The recording he plays: one of the vanilla fallback's, or a file from the player's folder while that has recordings. */
    private static CompanionMusicLibrary.Track tune(CompanionEntity c) {
        for (var track : CompanionMusicLibrary.fallback()) if (track.title().equals(c.campfire().tuneTitle())) return track;
        return CompanionMusicLibrary.scan(CompanionMusicLibrary.folder()).stream()
                .filter(t -> t.title().equals(c.campfire().tuneTitle())).findFirst().orElseThrow();
    }

    /**
     * A small Ogg Vorbis-like file of the given length in seconds: the identification header with its channels and sample rate, and a
     * last page carrying the granule position. The library reads only those, so a fixture needs no real audio and no file of the
     * repository's music folder.
     */
    private static byte[] ogg(int channels, int rate, int seconds) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.writeBytes(new byte[]{'O', 'g', 'g', 'S', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}); // a page header, granule 0
        out.writeBytes(new byte[]{1, 'v', 'o', 'r', 'b', 'i', 's'});                    // the identification header's marker
        out.writeBytes(new byte[]{0, 0, 0, 0});                                         // version
        out.write(channels);
        out.writeBytes(little(rate, 4));                                                // sample rate
        out.writeBytes(new byte[12]);                                                   // bitrates
        out.writeBytes(new byte[]{0, 1});                                               // block sizes, framing
        while (out.size() < 64) out.write(0);                                           // the scan reads a file from 64 bytes up
        out.writeBytes(new byte[]{'O', 'g', 'g', 'S', 0, 0});                           // the last page: granule at +6
        out.writeBytes(little((long) rate * seconds, 8));                               // samples played
        return out.toByteArray();
    }

    private static byte[] little(long value, int bytes) {
        byte[] out = new byte[bytes];
        for (int i = 0; i < bytes; i++) out[i] = (byte) (value >>> (8 * i));
        return out;
    }

    /** A mono 32 kHz recording of the given length, as the folder would hold it. */
    private static void writeOgg(java.nio.file.Path dir, String name, int seconds) throws java.io.IOException {
        writeBytes(dir, name, ogg(1, 32000, seconds));
    }

    private static void writeBytes(java.nio.file.Path dir, String name, byte[] bytes) throws java.io.IOException {
        java.nio.file.Files.write(dir.resolve(name), bytes);
    }

    private static void write(java.nio.file.Path dir, String name, String text) throws java.io.IOException {
        java.nio.file.Files.writeString(dir.resolve(name), text);
    }

    private static void deleteTree(java.nio.file.Path dir) {
        try (var paths = java.nio.file.Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { java.nio.file.Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
            });
        } catch (java.io.IOException ignored) { }
    }

    private static ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static int campfires(GameTestHelper h) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, GROUND + 1, 1), new BlockPos(30, GROUND + 3, 30)))
            if (h.getBlockState(pos).is(Blocks.CAMPFIRE)) found++;
        return found;
    }

    private static int count(CompanionEntity companion, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        return count;
    }
}

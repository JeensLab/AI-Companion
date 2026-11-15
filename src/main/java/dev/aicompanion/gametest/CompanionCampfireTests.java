package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionCampfirePlayback;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionMusicPack;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.entity.CompanionCampfire;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMusicLibrary;
import dev.aicompanion.entity.CompanionTunes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
 *
 * <p>The folder these tests read is an empty one, so whatever the player left in the run's config folder does not change what an
 * evening plays. Every batch that reads it points the library at it before its tests and gives the player's own folder back after
 * them: no batch leaves the library pointed elsewhere, and a batch that forgot says so in {@link #ground}.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCampfireTests {
    private static final int GROUND = 5;
    private static final BlockPos FIRE = new BlockPos(16, GROUND + 1, 16);

    /** The empty folder the batches below read, made once: an empty one, so whatever the player left in the run's config folder
     *  does not change what an evening by the fire plays. */
    private static java.nio.file.Path emptyFolder() {
        if (empty == null) {
            try {
                empty = java.nio.file.Files.createTempDirectory("aicompanion-empty");
            } catch (java.io.IOException error) {
                throw new IllegalStateException(error);
            }
        }
        return empty;
    }

    private static java.nio.file.Path empty;

    /** Before a batch: the library reads the empty folder. */
    private static void readTheEmptyFolder() { CompanionMusicLibrary.readFrom(emptyFolder()); }

    /** After a batch: the player's own folder again, so a live client that ran these tests plays it. */
    private static void readThePlayersFolder() { CompanionMusicLibrary.readFrom(null); }

    // A set and a give-back per batch, one method each: the game itself always reads the player's folder, and a batch must not
    // leave the library pointed at the tests' empty one. A batch missing its pair fails at once in ground(), not silently later.
    @BeforeBatch(batch = "campfire_request") public static void requestReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_request") public static void requestReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_seat") public static void seatReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_seat") public static void seatReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_again") public static void againReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_again") public static void againReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_sit") public static void sitReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_sit") public static void sitReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_bag") public static void bagReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_bag") public static void bagReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_none") public static void noneReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_none") public static void noneReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_linger") public static void lingerReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_linger") public static void lingerReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_monster") public static void monsterReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_monster") public static void monsterReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_off") public static void offReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_off") public static void offReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_folder") public static void folderReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_folder") public static void folderReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_alone") public static void aloneReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_alone") public static void aloneReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_switch") public static void switchReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_switch") public static void switchReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_added") public static void addedReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_added") public static void addedReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_library") public static void libraryReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_library") public static void libraryReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_nokind") public static void noKindReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_nokind") public static void noKindReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_mood") public static void moodReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_mood") public static void moodReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_restored") public static void restoredReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_restored") public static void restoredReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }
    @BeforeBatch(batch = "campfire_names") public static void namesReadsTheEmptyFolder(ServerLevel level) { readTheEmptyFolder(); }
    @AfterBatch(batch = "campfire_names") public static void namesReadsThePlayersFolder(ServerLevel level) { readThePlayersFolder(); }

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
            h.assertTrue(c.expression() == (tune.mood() >= 500 ? dev.aicompanion.CompanionExpression.PLEASED : dev.aicompanion.CompanionExpression.CALM),
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

    /**
     * Asked again while he plays («сыграй грустное», «повеселее»): the tune changes to that kind at once, starts over, and he stays on
     * his seat. The vanilla fallback changes its disc for both kinds, and each answer names the kind that starts.
     */
    @GameTest(template = "empty", batch = "campfire_again", timeoutTicks = 900)
    public static void askingAgainChangesTheTuneNotTheSeat(GameTestHelper h) {
        ground(h);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "happiest", 1).success(), "Not taken");
        net.minecraft.world.phys.Vec3[] seat = {null};
        int[] stage = {0};
        long[] askedAt = {0};
        String[] sadTitle = {null};
        h.onEachTick(() -> {
            if (stage[0] == 0 && c.campfirePose() == CompanionCampfire.PLAYING) {
                h.assertTrue(tune(c).mood() >= 500, "Asked for the happiest one, got «" + c.campfire().tuneTitle() + "»");
                seat[0] = c.position();
                String before = c.campfire().tuneTitle();
                int starts = c.campfireTuneStart();
                var again = CompanionEvents.executeAction(player, "campfire_song", "saddest", 1);
                h.assertTrue(again.success() && c.campfire().active(), "Asking again: " + again.message());
                h.assertTrue("Сыграю грустную.".equals(again.message()), "Not the kind asked for: " + again.message());
                h.assertTrue(!c.campfire().tuneTitle().equals(before) && tune(c).mood() < 500,
                        "Not a sad tune at once: «" + c.campfire().tuneTitle() + "»");
                h.assertTrue(c.campfireTuneStart() == starts + 1 && c.campfire().playedTicks() <= 2,
                        "Not started over: start " + c.campfireTuneStart() + ", played " + c.campfire().playedTicks());
                sadTitle[0] = c.campfire().tuneTitle();
                askedAt[0] = h.getTick();
                stage[0] = 1;
            } else if (stage[0] == 1 && c.campfire().playedTicks() > 20) {
                int starts = c.campfireTuneStart();
                var again = CompanionEvents.executeAction(player, "campfire_song", "happiest", 1);
                h.assertTrue(again.success() && "Сыграю повеселее.".equals(again.message()), "Not the kind asked for: " + again.message());
                h.assertTrue(tune(c).mood() >= 500 && !c.campfire().tuneTitle().equals(sadTitle[0]),
                        "Not a cheerful tune at once: «" + c.campfire().tuneTitle() + "»");
                h.assertTrue(c.campfireTuneStart() == starts + 1 && c.campfire().playedTicks() <= 2,
                        "Not started over: start " + c.campfireTuneStart() + ", played " + c.campfire().playedTicks());
                askedAt[0] = h.getTick();
                stage[0] = 2;
            }
            if (stage[0] >= 1) h.assertTrue(c.position().distanceTo(seat[0]) < .15 && c.campfirePose() == CompanionCampfire.PLAYING,
                    "Left his seat or stopped after being asked again: " + c.position() + " pose " + c.campfirePose());
        });
        h.succeedWhen(() -> h.assertTrue(stage[0] == 2 && h.getTick() - askedAt[0] > 60, "Not played on after being asked again"));
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
    @GameTest(template = "empty", batch = "campfire_library")
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
                            && track.mood() == (t.cheerful() ? 900 : 100)),
                    "The fallback disagrees with the catalog: " + track);
        }
        h.assertTrue(CompanionCampfireTests.class.getResource("/assets/aicompanion/sounds/guitar/romance.ogg") == null,
                "A recording still ships in the JAR");
        h.assertTrue(CompanionCampfireTests.class.getResource("/assets/aicompanion/sounds.json") == null,
                "The JAR still declares guitar sounds");
        h.succeed();
    }

    /**
     * The client's restart, driven without a client: the start counter the server syncs decides what this client does with the
     * tune it names — asked for again, the recording is started over rather than left sounding; one that has played out by its
     * length is not started again while the server still names it; another tune starts, and no tune stops the sound.
     */
    @GameTest(template = "empty", batch = "campfire_defaults")
    public static void theClientStartsTheRecordingOverWhenTheStartCounterMoves(GameTestHelper h) {
        String tune = "aicompanion:guitar_folder/quiet";
        var over = CompanionCampfirePlayback.decide(tune, 4, 3, true, true, 40, 2000, "");
        h.assertTrue(over == CompanionCampfirePlayback.Action.START_OVER, "Asked for again it was not started over: " + over);
        var on = CompanionCampfirePlayback.decide(tune, 3, 3, true, true, 40, 2000, "");
        h.assertTrue(on == CompanionCampfirePlayback.Action.KEEP_SOUNDING, "The same start did not let it sound on: " + on);
        var out = CompanionCampfirePlayback.decide(tune, 3, 3, true, false, 2000, 2000, "");
        h.assertTrue(out == CompanionCampfirePlayback.Action.PLAYED_OUT, "A played-out recording was started again: " + out);
        var gone = CompanionCampfirePlayback.decide(tune, 3, 3, false, false, 0, -1, tune);
        h.assertTrue(gone == CompanionCampfirePlayback.Action.FINISHED, "A played-out recording came back: " + gone);
        var other = CompanionCampfirePlayback.decide("minecraft:music_disc.cat", 3, 3, false, false, 0, 2000, "");
        h.assertTrue(other == CompanionCampfirePlayback.Action.START, "Another tune did not start: " + other);
        var none = CompanionCampfirePlayback.decide("", -1, 3, false, false, 0, -1, "");
        h.assertTrue(none == CompanionCampfirePlayback.Action.STOP, "The sound stayed after the tune was gone: " + none);
        // A tune this client's sound engine does not know — on a server, a recording in the server's folder and not in this
        // player's — cannot start: the tune sounding goes on and nothing is claimed changed, while a known one does start.
        var unknown = CompanionCampfirePlayback.decide("aicompanion:guitar_folder/server_only", 4, 3, false, false, 0, 2000, "", false);
        h.assertTrue(unknown == CompanionCampfirePlayback.Action.KEEP_OLD, "An unknown tune stopped the old sound: " + unknown);
        var unknownFirst = CompanionCampfirePlayback.decide("aicompanion:guitar_folder/server_only", -1, -1, false, false, 0, -1, "", false);
        h.assertTrue(unknownFirst == CompanionCampfirePlayback.Action.KEEP_OLD, "An unknown tune was claimed to start: " + unknownFirst);
        var known = CompanionCampfirePlayback.decide("aicompanion:guitar_folder/server_only", 4, 3, false, false, 0, 2000, "", true);
        h.assertTrue(known == CompanionCampfirePlayback.Action.START_OVER, "A tune the engine knows was not started: " + known);
        // An id unknown here never stops the recording that is sounding on its own tune.
        var sounding = CompanionCampfirePlayback.decide(tune, 3, 3, true, true, 40, 2000, "", false);
        h.assertTrue(sounding == CompanionCampfirePlayback.Action.KEEP_SOUNDING, "The sounding recording was stopped: " + sounding);
        h.succeed();
    }

    /**
     * The player's folder is read as it is: every .ogg in it and in its subfolders is a playable recording with its own sound id, its
     * length and its title; a mood is the integer the name starts with, 0 (the saddest) to 999 (the happiest), while a plain name or a
     * longer number (a year) carries none; a broken file is skipped, and a missing folder is simply empty.
     */
    @GameTest(template = "empty")
    public static void theFolderIsReadForEveryRecordingInIt(GameTestHelper h) {
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-guitar");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        try {
            writeOgg(dir, "0_first.ogg", 100);
            writeOgg(dir, "150_second.ogg", 30);
            writeOgg(dir, "quiet_one.ogg", 50);
            writeOgg(dir, "1999_live.ogg", 20);
            java.nio.file.Files.createDirectories(dir.resolve("1"));
            writeOgg(dir.resolve("1"), "999_last.ogg", 40);
            write(dir, "broken.ogg", "not an ogg file at all");
            var tracks = CompanionMusicLibrary.scan(dir);
            h.assertTrue(tracks.size() == 5, "Found " + tracks.size() + " of five recordings: " + tracks);
            var byTitle = new java.util.HashMap<String, CompanionMusicLibrary.Track>();
            for (var track : tracks) byTitle.put(track.title(), track);
            h.assertTrue(byTitle.containsKey("First") && byTitle.containsKey("Second") && byTitle.containsKey("Quiet one")
                            && byTitle.containsKey("1999 live") && byTitle.containsKey("Last"),
                    "Titles: " + byTitle.keySet());
            var first = byTitle.get("First");
            var second = byTitle.get("Second");
            var quiet = byTitle.get("Quiet one");
            var year = byTitle.get("1999 live");
            var last = byTitle.get("Last");
            h.assertTrue(first.mood() == 0 && second.mood() == 150 && quiet.mood() == CompanionMusicLibrary.UNTAGGED
                            && year.mood() == CompanionMusicLibrary.UNTAGGED && last.mood() == 999,
                    "Moods: " + first.mood() + ", " + second.mood() + ", " + quiet.mood() + ", " + year.mood() + ", " + last.mood());
            h.assertTrue(first.ticks() == 2000 && second.ticks() == 600 && quiet.ticks() == 1000 && year.ticks() == 400 && last.ticks() == 800,
                    "Lengths: " + first.ticks() + ", " + second.ticks() + ", " + quiet.ticks() + ", " + year.ticks() + ", " + last.ticks());
            // A recording in a subfolder is still read: the file keeps its path inside the folder, so the pack can serve it.
            h.assertTrue("1/999_last.ogg".equals(last.file()), "The subfolder recording lost its path: " + last.file());
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
     * The player's folder is the game's own config folder unless a test points the library elsewhere: merely loading this class
     * used to point it at an empty temporary folder, and an evening by the fire then played vanilla music while the folder was
     * full. Nothing is reset here first — a reset would produce the very answer it checks — and the folder is compared with the
     * one the library read when this class was loaded, before any test body or batch hook could have pointed it elsewhere.
     */
    @GameTest(template = "empty", batch = "campfire_defaults")
    public static void thePlayerFolderIsTheConfigFolderUntilATestPointsElsewhere(GameTestHelper h) {
        var expected = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(CompanionMusicLibrary.FOLDER);
        h.assertTrue(FOLDER_WHEN_LOADED.equals(expected), "Loading this test class pointed the library at " + FOLDER_WHEN_LOADED);
        h.assertTrue(expected.equals(CompanionMusicLibrary.folder()), "The folder is not the player's: " + CompanionMusicLibrary.folder());
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
     * The mood scale the owner set (0 the saddest, 999 the happiest) and the requests along it: «самую грустную» takes the lowest,
     * «самую весёлую» the highest, «повеселее» the nearest strictly higher, «погрустнее» the nearest strictly lower, two «повеселее»
     * requests move strictly up, and a change request never returns the recording that is playing. One recording in the folder means
     * no change at all; an untagged folder is answered honestly, still with another recording when there is one.
     */
    @GameTest(template = "empty", batch = "campfire_library")
    public static void aTaggedRecordingIsPickedForTheMoodOrHeSaysThereIsNone(GameTestHelper h) {
        var random = net.minecraft.util.RandomSource.create(7);
        var zero = new CompanionMusicLibrary.Track("0_aerie.ogg", "aicompanion:guitar_folder/0_aerie", "Aerie", 400, 0);
        var low = new CompanionMusicLibrary.Track("150_blue-blood.ogg", "aicompanion:guitar_folder/150_blue-blood", "Blue blood", 400, 150);
        var middle = new CompanionMusicLibrary.Track("400_dolphin.ogg", "aicompanion:guitar_folder/400_dolphin", "Dolphin", 400, 400);
        var high = new CompanionMusicLibrary.Track("850_gavotte.ogg", "aicompanion:guitar_folder/850_gavotte", "Gavotte", 400, 850);
        var top = new CompanionMusicLibrary.Track("999_water-dripping.ogg", "aicompanion:guitar_folder/999_water-dripping", "Water dripping", 400, 999);
        var all = java.util.List.of(zero, low, middle, high, top);
        for (int i = 0; i < 20; i++) {
            var pick = CompanionMusicLibrary.pick(all, "saddest", "", random);
            h.assertTrue(pick.track() == zero && pick.moodMatched(), "«Самую грустную» was not the 0 one: " + pick.track().title());
            pick = CompanionMusicLibrary.pick(all, "happiest", "", random);
            h.assertTrue(pick.track() == top && pick.moodMatched(), "«Самую весёлую» was not the 999 one: " + pick.track().title());
            pick = CompanionMusicLibrary.pick(all, "happier", middle.soundId(), random);
            h.assertTrue(pick.track() == high, "«Повеселее» from 400 was not 850: " + pick.track().title());
            pick = CompanionMusicLibrary.pick(all, "sadder", middle.soundId(), random);
            h.assertTrue(pick.track() == low, "«Погрустнее» from 400 was not 150: " + pick.track().title());
            pick = CompanionMusicLibrary.pick(all, "next", middle.soundId(), random);
            h.assertTrue(pick.track() != middle, "«Другую» played the same tune: " + pick.track().title());
            pick = CompanionMusicLibrary.pick(all, "", low.soundId(), random);
            h.assertTrue(pick.track() != low, "No request repeated the tune: " + pick.track().title());
        }
        // A request while he plays moves along the scale and never returns the recording playing.
        for (int i = 0; i < 20; i++) {
            var changed = CompanionMusicLibrary.change(all, "saddest", middle.soundId(), random);
            h.assertTrue(changed != null && changed.track() == zero, "«Самую грустную» did not take the 0 one: " + changed);
            changed = CompanionMusicLibrary.change(all, "happiest", middle.soundId(), random);
            h.assertTrue(changed != null && changed.track() == top, "«Самую весёлую» did not take the 999 one: " + changed);
            changed = CompanionMusicLibrary.change(all, "happier", middle.soundId(), random);
            h.assertTrue(changed != null && changed.track() == high, "«Повеселее» did not take 850: " + changed);
            changed = CompanionMusicLibrary.change(all, "sadder", middle.soundId(), random);
            h.assertTrue(changed != null && changed.track() == low, "«Погрустнее» did not take 150: " + changed);
            changed = CompanionMusicLibrary.change(all, "next", middle.soundId(), random);
            h.assertTrue(changed != null && changed.track() != middle, "«Другую» played the same tune: " + changed);
            // At the end of the scale the request still changes the recording: the highest (or lowest) other one.
            changed = CompanionMusicLibrary.change(all, "happier", top.soundId(), random);
            h.assertTrue(changed != null && changed.track() == high, "«Повеселее» at the top did not change: " + changed);
            changed = CompanionMusicLibrary.change(all, "sadder", zero.soundId(), random);
            h.assertTrue(changed != null && changed.track() == low, "«Погрустнее» at the bottom did not change: " + changed);
        }
        // Two consecutive «повеселее» requests move strictly up each time.
        var first = CompanionMusicLibrary.change(all, "happier", middle.soundId(), random);
        h.assertTrue(first != null && first.track() == high, "The first «повеселее» did not move up: " + first);
        var second = CompanionMusicLibrary.change(all, "happier", first.track().soundId(), random);
        h.assertTrue(second != null && second.track() == top && second.track().mood() > first.track().mood(),
                "The second «повеселее» did not move strictly up: " + second);
        // One recording in the folder: no change request has anything else to play.
        var one = java.util.List.of(middle);
        h.assertTrue(CompanionMusicLibrary.change(one, "next", middle.soundId(), random) == null, "One recording was promised as another");
        h.assertTrue(CompanionMusicLibrary.change(one, "saddest", middle.soundId(), random) == null, "One recording was promised the saddest");
        h.assertTrue(CompanionMusicLibrary.change(one, "happier", middle.soundId(), random) == null, "One recording was promised as happier");
        var plain = new CompanionMusicLibrary.Track("plain.ogg", "aicompanion:guitar_folder/plain", "Plain", 400, CompanionMusicLibrary.UNTAGGED);
        var other = new CompanionMusicLibrary.Track("other.ogg", "aicompanion:guitar_folder/other", "Other", 400, CompanionMusicLibrary.UNTAGGED);
        var untagged = java.util.List.of(plain, other);
        var honest = CompanionMusicLibrary.pick(untagged, "saddest", "", random);
        h.assertTrue(!honest.moodMatched(), "An untagged saddest pick was not honest");
        h.assertTrue(!CompanionMusicLibrary.hasMood(untagged, "saddest") && !CompanionMusicLibrary.hasMood(untagged, "happiest"),
                "An untagged set claims a mood");
        h.assertTrue(CompanionMusicLibrary.hasMood(all, "saddest") && CompanionMusicLibrary.hasMood(all, "happiest")
                        && CompanionMusicLibrary.hasMood(all, "sadder") && CompanionMusicLibrary.hasMood(all, "happier"),
                "Tagged moods not seen");
        // The fallback while the folder is empty: standard Minecraft music at the ends of the same scale.
        var bundled = CompanionMusicLibrary.fallback();
        var saddestFallback = CompanionMusicLibrary.pick(bundled, "saddest", "", random);
        var happiestFallback = CompanionMusicLibrary.pick(bundled, "happiest", "", random);
        h.assertTrue(saddestFallback.track().mood() == 100 && saddestFallback.moodMatched()
                        && happiestFallback.track().mood() == 900 && happiestFallback.moodMatched(),
                "The standard fallback has no tune of a kind: " + saddestFallback + ", " + happiestFallback);
        h.assertTrue(CompanionMusicLibrary.available().size() >= 2, "Nothing to play at all");
        h.succeed();
    }

    /**
     * The dynamic resource pack the client builds from the folder: the game's sound engine knows a sound only from a pack, so the pack
     * has to declare every recording the server can name and serve its file at the path that id resolves to. Two recordings are built
     * and every id the server would send is checked in the generated sounds.json, in what the pack lists and in the file it serves, so
     * a pack that drops one, or serves it under another path, cannot pass. The game accepts the pack as a required one.
     */
    @GameTest(template = "empty")
    public static void theFolderPackDeclaresAndServesEveryRecording(GameTestHelper h) {
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-pack");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        try {
            writeOgg(dir, "3_evening.ogg", 40);
            writeOgg(dir, "8_morning.ogg", 30);
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
            // Every id the server names for this folder, as CompanionMusicLibrary.scan makes it.
            var tracks = CompanionMusicLibrary.scan(dir);
            h.assertTrue(tracks.size() == 2 && root.size() == tracks.size(),
                    "sounds.json declares " + root.size() + " of " + tracks.size() + " recordings: " + json);
            var listed = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
            pack.listResources(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, "aicompanion", "sounds", (id, supplier) -> listed.add(id));
            for (var track : tracks) {
                var key = track.soundId().substring("aicompanion:".length());
                var entry = root.getAsJsonObject(key);
                h.assertTrue(entry != null, "sounds.json does not declare " + track.soundId() + ": " + json);
                var sound = entry.getAsJsonArray("sounds").get(0).getAsJsonObject();
                h.assertTrue(track.soundId().equals(sound.get("name").getAsString()) && sound.get("stream").getAsBoolean(),
                        "The declaration does not point at " + track.soundId() + ": " + entry);
                // The path the sound engine reads for that id: sounds/<id path>.ogg, listed and served as the recording itself.
                var id = net.minecraft.resources.ResourceLocation.parse(track.soundId());
                var fileId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "sounds/" + id.getPath() + ".ogg");
                h.assertTrue(listed.contains(fileId), fileId + " is not listed: " + listed);
                var file = pack.getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, fileId);
                h.assertTrue(file != null, fileId + " is not served");
                try (var in = file.get()) {
                    h.assertTrue(java.util.Arrays.equals(in.readAllBytes(), java.nio.file.Files.readAllBytes(dir.resolve(track.file()))),
                            "The served file is not " + track.file());
                }
            }
            h.assertTrue(listed.size() == tracks.size(), "The pack lists more than its recordings: " + listed);
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
     * The vanilla fallback while the folder is empty answers the mood scale too: a disc at the cheerful end for «повеселее», one at
     * the sad end for «погрустнее», always a different one than the disc playing — and the one disc of that end that is playing now is
     * started over rather than replaced by a disc of the wrong end. With nothing else to play the change says so instead.
     */
    @GameTest(template = "empty", batch = "campfire_library")
    public static void theVanillaFallbackChangesItsDiscWithTheMood(GameTestHelper h) {
        var random = net.minecraft.util.RandomSource.create(11);
        var discs = CompanionMusicLibrary.available();
        var playing = "minecraft:music_disc.cat";
        for (int i = 0; i < 20; i++) {
            var cheerful = CompanionMusicLibrary.pick(discs, "happier", playing, random);
            h.assertTrue(cheerful.moodMatched() && cheerful.track().mood() >= 500
                            && !cheerful.track().soundId().equals(playing),
                    "«Повеселее» did not change the disc: " + cheerful.track());
            var calm = CompanionMusicLibrary.pick(discs, "sadder", cheerful.track().soundId(), random);
            h.assertTrue(calm.moodMatched() && calm.track().mood() < 500
                            && !calm.track().soundId().equals(cheerful.track().soundId()),
                    "«Погрустнее» did not change the disc: " + calm.track());
        }
        var onlyCheerful = java.util.List.of(discs.stream().filter(t -> t.mood() >= 500).findFirst().orElseThrow());
        var again = CompanionMusicLibrary.pick(onlyCheerful, "happiest", onlyCheerful.getFirst().soundId(), random);
        h.assertTrue(again.moodMatched() && again.track().soundId().equals(onlyCheerful.getFirst().soundId()),
                "The only cheerful disc was not started over: " + again.track());
        var onlyCalm = java.util.List.of(discs.stream().filter(t -> t.mood() < 500).findFirst().orElseThrow());
        var honest = CompanionMusicLibrary.pick(onlyCalm, "happiest", "", random);
        h.assertTrue(honest.moodMatched() && honest.track().mood() == onlyCalm.getFirst().mood(),
                "The only disc was not the happiest of its folder: " + honest.track());
        // A request while he plays changes the disc only to a really different one; with nothing else it says so instead.
        for (int i = 0; i < 20; i++) {
            var changed = CompanionMusicLibrary.change(discs, "saddest", playing, random);
            h.assertTrue(changed != null && changed.track().mood() < 500
                            && !changed.track().soundId().equals(playing),
                    "«Погрустнее» did not change the disc: " + changed);
            var vanilla = CompanionMusicLibrary.change(discs, "vanilla", "minecraft:music_disc.cat", random);
            h.assertTrue(vanilla != null && vanilla.track().soundId().startsWith("minecraft:music_disc.")
                            && !vanilla.track().soundId().equals("minecraft:music_disc.cat"),
                    "«Из майнкрафта» did not change the disc: " + vanilla);
        }
        h.assertTrue(CompanionMusicLibrary.change(onlyCheerful, "happiest", onlyCheerful.getFirst().soundId(), random) == null,
                "The only cheerful disc was promised as another one");
        h.assertTrue(CompanionMusicLibrary.change(onlyCalm, "happiest", onlyCalm.getFirst().soundId(), random) == null,
                "The only calm disc was promised as another one");
        h.succeed();
    }

    /**
     * The player's folder is what the evening plays: two recordings there and «сыграй» plays one of them, not a vanilla disc; asked
     * «повеселее» and then «погрустнее» while he plays, the tune moves along the mood scale to the nearest recording that way and
     * starts over, and the answer names the recording that really started, by its number and its file name. The recording asked for
     * again is the one the client restarts (the start counter moves).
     */
    @GameTest(template = "empty", batch = "campfire_folder", timeoutTicks = 900)
    public static void aMoodRequestPlaysAFolderRecordingOfThatKind(GameTestHelper h) {
        ground(h);
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-folder-evening");
            writeOgg(dir, "0_quiet.ogg", 100);
            writeOgg(dir, "800_lively.ogg", 100);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        CompanionMusicLibrary.readFrom(dir);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "saddest", 1).success(), "Not taken");
        int[] stage = {0};
        String[] quietTitle = {null};
        h.onEachTick(() -> {
            if (stage[0] == 0 && c.campfirePose() == CompanionCampfire.PLAYING && c.campfire().playedTicks() > 20) {
                var quiet = tune(c);
                h.assertTrue(quiet.soundId().startsWith("aicompanion:guitar_folder/"), "Not the folder's recording: " + quiet);
                h.assertTrue(quiet.mood() == 0, "Asked for the saddest one, got «" + quiet.title() + "» (" + quiet.mood() + ")");
                quietTitle[0] = quiet.title();
                int starts = c.campfireTuneStart();
                var again = CompanionEvents.executeAction(player, "campfire_song", "happier", 1);
                h.assertTrue(again.success() && "Играю 800 Lively.".equals(again.message()), "Answer: " + again.message());
                var lively = tune(c);
                h.assertTrue(lively.mood() == 800 && !lively.title().equals(quietTitle[0]),
                        "Not the nearest higher recording at once: «" + lively.title() + "» (" + lively.mood() + ")");
                h.assertTrue(c.campfireTuneStart() == starts + 1 && c.campfire().playedTicks() <= 2,
                        "Not started over: start " + c.campfireTuneStart() + ", played " + c.campfire().playedTicks());
                stage[0] = 1;
            } else if (stage[0] == 1 && c.campfire().playedTicks() > 20) {
                int starts = c.campfireTuneStart();
                var again = CompanionEvents.executeAction(player, "campfire_song", "sadder", 1);
                h.assertTrue(again.success() && "Играю 0 Quiet.".equals(again.message()), "Answer: " + again.message());
                var quiet = tune(c);
                h.assertTrue(quiet.mood() == 0 && quiet.title().equals(quietTitle[0]),
                        "Not back to the saddest recording at once: «" + quiet.title() + "»");
                h.assertTrue(c.campfireTuneStart() == starts + 1 && c.campfire().playedTicks() <= 2,
                        "Not started over: start " + c.campfireTuneStart() + ", played " + c.campfire().playedTicks());
                h.assertTrue(c.campfirePose() == CompanionCampfire.PLAYING, "Stopped instead of playing on");
                stage[0] = 2;
            }
        });
        h.succeedWhen(() -> h.assertTrue(stage[0] == 2,
                "Never asked again: pose " + c.campfirePose() + ", tune «" + c.campfire().tuneTitle() + "»"));
    }

    /**
     * One recording in the folder, and he plays it: asked to move up, down, or simply for another one, there is nothing else to
     * change to — the recording goes on playing (it is not started over), and each answer says plainly that there are no other songs.
     */
    @GameTest(template = "empty", batch = "campfire_alone", timeoutTicks = 1200)
    public static void theOnlyRecordingGoesOnAndTheAnswerSaysThereAreNoOthers(GameTestHelper h) {
        ground(h);
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-alone");
            writeOgg(dir, "800_lively.ogg", 100);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        CompanionMusicLibrary.readFrom(dir);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "happiest", 1).success(), "Not taken");
        int[] stage = {0};
        String[] title = {null};
        int[] start = {0};
        h.onEachTick(() -> {
            if (stage[0] == 0 && c.campfirePose() == CompanionCampfire.PLAYING && c.campfire().playedTicks() > 20) {
                title[0] = c.campfire().tuneTitle();
                start[0] = c.campfireTuneStart();
                int played = c.campfire().playedTicks();
                var again = CompanionEvents.executeAction(player, "campfire_song", "happier", 1);
                h.assertTrue(again.success() && "Других песен не знаю.".equals(again.message()), "Answer: " + again.message());
                h.assertTrue(c.campfire().tuneTitle().equals(title[0]) && c.campfireTuneStart() == start[0]
                                && c.campfire().playedTicks() >= played && c.campfirePose() == CompanionCampfire.PLAYING,
                        "The only recording was restarted or stopped: «" + c.campfire().tuneTitle() + "», start "
                                + c.campfireTuneStart() + ", played " + c.campfire().playedTicks());
                stage[0] = 1;
            } else if (stage[0] == 1 && c.campfire().playedTicks() > 40) {
                int played = c.campfire().playedTicks();
                var again = CompanionEvents.executeAction(player, "campfire_song", "sadder", 1);
                h.assertTrue(again.success() && "Других песен не знаю.".equals(again.message()), "Answer: " + again.message());
                h.assertTrue(c.campfire().tuneTitle().equals(title[0]) && c.campfireTuneStart() == start[0]
                                && c.campfire().playedTicks() >= played,
                        "Not the only recording still playing: «" + c.campfire().tuneTitle() + "»");
                stage[0] = 2;
            } else if (stage[0] == 2 && c.campfire().playedTicks() > 60) {
                var again = CompanionEvents.executeAction(player, "campfire_song", "next", 1);
                h.assertTrue(again.success() && "Других песен не знаю.".equals(again.message()), "Answer: " + again.message());
                h.assertTrue(c.campfire().tuneTitle().equals(title[0]) && c.campfireTuneStart() == start[0],
                        "Not the only recording still playing: «" + c.campfire().tuneTitle() + "»");
                stage[0] = 3;
            }
        });
        h.succeedWhen(() -> h.assertTrue(stage[0] == 3,
                "Never asked again: pose " + c.campfirePose() + ", tune «" + c.campfire().tuneTitle() + "»"));
    }

    /**
     * A request while he plays takes the nearest recording in the asked direction, never the one playing, and the answer names it:
     * «погрустнее» from the 800 one takes the 700 one, and «другую» then takes the 800 one back. Nothing is restarted and called a
     * change; the id and the start counter move on the first request.
     */
    @GameTest(template = "empty", batch = "campfire_nokind", timeoutTicks = 900)
    public static void theAnswerTellsNoSuchKindFromNoOtherSong(GameTestHelper h) {
        ground(h);
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-nokind");
            writeOgg(dir, "700_happy.ogg", 100);
            writeOgg(dir, "800_lively.ogg", 100);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        CompanionMusicLibrary.readFrom(dir);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "happiest", 1).success(), "Not taken");
        int[] stage = {0};
        String[] playing = {null};
        int[] start = {0};
        h.onEachTick(() -> {
            if (stage[0] == 0 && c.campfirePose() == CompanionCampfire.PLAYING && c.campfire().playedTicks() > 20) {
                playing[0] = c.campfire().tuneTitle();
                start[0] = c.campfireTuneStart();
                // The 700 recording is the nearest one lower than the 800 playing: «погрустнее» takes it, and says so by name.
                var again = CompanionEvents.executeAction(player, "campfire_song", "sadder", 1);
                h.assertTrue(again.success() && "Играю 700 Happy.".equals(again.message()), "Answer: " + again.message());
                h.assertTrue(!c.campfire().tuneTitle().equals(playing[0]) && tune(c).mood() == 700
                                && c.campfireTuneStart() == start[0] + 1,
                        "«Погрустнее» did not change to 700: «" + c.campfire().tuneTitle() + "», start " + c.campfireTuneStart());
                playing[0] = c.campfire().tuneTitle();
                start[0] = c.campfireTuneStart();
                stage[0] = 1;
            } else if (stage[0] == 1 && c.campfire().playedTicks() > 30) {
                // «Другую» takes the other recording back.
                var again = CompanionEvents.executeAction(player, "campfire_song", "next", 1);
                h.assertTrue(again.success() && "Играю 800 Lively.".equals(again.message()), "Answer: " + again.message());
                h.assertTrue(!c.campfire().tuneTitle().equals(playing[0]) && tune(c).mood() == 800
                                && c.campfireTuneStart() == start[0] + 1,
                        "«Другую» did not change the recording: «" + c.campfire().tuneTitle() + "»");
                stage[0] = 2;
            }
        });
        h.succeedWhen(() -> h.assertTrue(stage[0] == 2,
                "Never asked twice: pose " + c.campfirePose() + ", tune «" + c.campfire().tuneTitle() + "»"));
    }

    /**
     * Asked for another tune while he plays, with another recording in the folder, he really switches: the sound id the server
     * syncs changes (not only the start counter), the new recording starts from its beginning, and the answer names the recording
     * that started. A mood request moves along the scale; «из майнкрафта» plays standard Minecraft music even while the folder is full.
     */
    @GameTest(template = "empty", batch = "campfire_switch", timeoutTicks = 1200)
    public static void aRequestReallyChangesTheRecordingAndVanillaIgnoresTheFolder(GameTestHelper h) {
        ground(h);
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-switch");
            writeOgg(dir, "0_quiet.ogg", 100);
            writeOgg(dir, "150_sad.ogg", 100);
            writeOgg(dir, "800_lively.ogg", 100);
            writeOgg(dir, "700_happy.ogg", 100);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        CompanionMusicLibrary.readFrom(dir);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "saddest", 1).success(), "Not taken");
        int[] stage = {0};
        String[] playing = {null};
        h.onEachTick(() -> {
            if (stage[0] == 0 && c.campfirePose() == CompanionCampfire.PLAYING && c.campfire().playedTicks() > 20) {
                playing[0] = tune(c).soundId();
                int mood = tune(c).mood();
                int starts = c.campfireTuneStart();
                var again = CompanionEvents.executeAction(player, "campfire_song", "happier", 1);
                h.assertTrue(again.success() && again.message().startsWith("Играю "), "Answer: " + again.message());
                h.assertTrue(tune(c).mood() > mood && !tune(c).soundId().equals(playing[0]),
                        "Not a recording strictly higher at once: «" + c.campfire().tuneTitle() + "» (" + tune(c).mood() + ")");
                h.assertTrue(again.message().contains(CompanionMusicLibrary.label(tune(c))),
                        "The answer does not name the recording that started: " + again.message());
                h.assertTrue(c.campfireTuneStart() == starts + 1 && c.campfire().playedTicks() <= 2,
                        "The new recording was not started from its beginning: start " + c.campfireTuneStart() + ", played " + c.campfire().playedTicks());
                stage[0] = 1;
            } else if (stage[0] == 1 && c.campfire().playedTicks() > 20) {
                playing[0] = tune(c).soundId();
                var again = CompanionEvents.executeAction(player, "campfire_song", "next", 1);
                h.assertTrue(again.success() && again.message().startsWith("Играю "), "Answer: " + again.message());
                h.assertTrue(!tune(c).soundId().equals(playing[0]), "The recording did not change: " + tune(c).soundId());
                h.assertTrue(again.message().contains(CompanionMusicLibrary.label(tune(c))),
                        "The answer does not name the recording that started: " + again.message());
                stage[0] = 2;
            } else if (stage[0] == 2 && c.campfire().playedTicks() > 20) {
                playing[0] = tune(c).soundId();
                int starts = c.campfireTuneStart();
                var again = CompanionEvents.executeAction(player, "campfire_song", "vanilla", 1);
                h.assertTrue(again.success() && "Сыграю из майнкрафта.".equals(again.message()), "Answer: " + again.message());
                h.assertTrue(tune(c).soundId().startsWith("minecraft:music_disc.") && !tune(c).soundId().equals(playing[0])
                                && c.campfireTuneStart() == starts + 1,
                        "«Из майнкрафта» did not play vanilla music: " + tune(c).soundId());
                stage[0] = 3;
            } else if (stage[0] == 3 && c.campfire().playedTicks() > 20) {
                var again = CompanionEvents.executeAction(player, "campfire_song", "saddest", 1);
                h.assertTrue(again.success() && "Самая грустная — 0 Quiet.".equals(again.message()), "Answer: " + again.message());
                h.assertTrue(tune(c).mood() == 0 && tune(c).soundId().startsWith("aicompanion:guitar_folder/"),
                        "Not back to the folder's saddest recording: «" + c.campfire().tuneTitle() + "»");
                stage[0] = 4;
            }
        });
        h.succeedWhen(() -> h.assertTrue(stage[0] == 4,
                "Never asked again: pose " + c.campfirePose() + ", tune «" + c.campfire().tuneTitle() + "»"));
    }

    /**
     * A recording put into the folder while the evening goes on is picked up by the next request: the folder is read afresh, so
     * «другую» switches to the file just added, without a reload and without the player pressing F3+T.
     */
    @GameTest(template = "empty", batch = "campfire_added", timeoutTicks = 1200)
    public static void aRequestPicksUpARecordingAddedToTheFolder(GameTestHelper h) {
        ground(h);
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-added");
            writeOgg(dir, "0_quiet.ogg", 100);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        CompanionMusicLibrary.readFrom(dir);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "saddest", 1).success(), "Not taken");
        int[] stage = {0};
        String[] playing = {null};
        h.onEachTick(() -> {
            if (stage[0] == 0 && c.campfirePose() == CompanionCampfire.PLAYING && c.campfire().playedTicks() > 20) {
                playing[0] = tune(c).soundId();
                try {
                    writeOgg(dir, "800_added.ogg", 100);
                } catch (java.io.IOException e) { throw new IllegalStateException(e); }
                int starts = c.campfireTuneStart();
                var again = CompanionEvents.executeAction(player, "campfire_song", "next", 1);
                h.assertTrue(again.success() && "Играю 800 Added.".equals(again.message()), "Answer: " + again.message());
                h.assertTrue("Added".equals(c.campfire().tuneTitle()) && !tune(c).soundId().equals(playing[0])
                                && c.campfireTuneStart() == starts + 1,
                        "The recording added to the folder was not picked up: «" + c.campfire().tuneTitle() + "», start " + c.campfireTuneStart());
                stage[0] = 1;
            }
        });
        h.succeedWhen(() -> h.assertTrue(stage[0] == 1,
                "Never asked again: pose " + c.campfirePose() + ", tune «" + c.campfire().tuneTitle() + "»"));
    }

    /**
     * The first «повеселее» request is authoritative: the synced tune and its start counter move to the picked recording at once, and
     * the automatic rotation does not replace that pick on the following ticks — it only takes over when the recording has played
     * out. The client decision for the new id is START_OVER, so the sound restarts with the picked file. Further requests move
     * strictly up the scale each time.
     */
    @GameTest(template = "empty", batch = "campfire_mood", timeoutTicks = 1200)
    public static void aRequestMovesTheTuneAtOnceAndTheRotationDoesNotReplaceIt(GameTestHelper h) {
        ground(h);
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-mood");
            writeOgg(dir, "0_quiet.ogg", 100);
            writeOgg(dir, "150_low.ogg", 100);
            writeOgg(dir, "400_mid.ogg", 100);
            writeOgg(dir, "850_high.ogg", 100);
            writeOgg(dir, "999_top.ogg", 100);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        CompanionMusicLibrary.readFrom(dir);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "saddest", 1).success(), "Not taken");
        int[] stage = {0};
        String[] picked = {null};
        int[] pickedStart = {0};
        long[] guardUntil = {0};
        h.onEachTick(() -> {
            if (stage[0] == 0 && c.campfirePose() == CompanionCampfire.PLAYING && c.campfire().playedTicks() > 20) {
                var zero = tune(c);
                h.assertTrue(zero.mood() == 0, "The evening did not start at 0: " + zero);
                int starts = c.campfireTuneStart();
                var again = CompanionEvents.executeAction(player, "campfire_song", "happier", 1);
                h.assertTrue(again.success() && "Играю 150 Low.".equals(again.message()), "Answer: " + again.message());
                var low = tune(c);
                h.assertTrue(low.mood() == 150 && !low.soundId().equals(zero.soundId()) && c.campfireTuneStart() == starts + 1
                                && c.campfire().playedTicks() <= 2,
                        "The first «повеселее» did not switch at once: «" + low.title() + "», start " + c.campfireTuneStart());
                // The client sees the new id and a moved counter: it starts the picked recording over.
                h.assertTrue(CompanionCampfirePlayback.decide(low.soundId(), c.campfireTuneStart(), starts, false, false, 0,
                                low.ticks(), "", true) == CompanionCampfirePlayback.Action.START_OVER,
                        "The client did not start the picked id over");
                h.assertTrue(CompanionCampfirePlayback.decide(low.soundId(), c.campfireTuneStart(), c.campfireTuneStart(), true, true, 0,
                                low.ticks(), "", true) == CompanionCampfirePlayback.Action.KEEP_SOUNDING,
                        "The client did not keep the picked id sounding");
                picked[0] = low.soundId();
                pickedStart[0] = c.campfireTuneStart();
                guardUntil[0] = h.getTick() + 2;
                stage[0] = 1;
            } else if (stage[0] == 1) {
                // The automatic rotation must not replace the request's pick on the next ticks.
                h.assertTrue(c.campfireTune().equals(picked[0]) && c.campfireTuneStart() == pickedStart[0],
                        "The rotation replaced the request's pick: «" + c.campfireTune() + "», start " + c.campfireTuneStart());
                if (h.getTick() < guardUntil[0]) return;
                var again = CompanionEvents.executeAction(player, "campfire_song", "happier", 1);
                h.assertTrue(again.success() && "Играю 400 Mid.".equals(again.message()), "Answer: " + again.message());
                var mid = tune(c);
                h.assertTrue(mid.mood() == 400 && !mid.soundId().equals(picked[0]) && c.campfireTuneStart() == pickedStart[0] + 1,
                        "The second «повеселее» did not move up: «" + mid.title() + "»");
                picked[0] = mid.soundId();
                pickedStart[0] = c.campfireTuneStart();
                stage[0] = 2;
            } else if (stage[0] == 2 && c.campfire().playedTicks() > 20) {
                var again = CompanionEvents.executeAction(player, "campfire_song", "happier", 1);
                h.assertTrue(again.success() && "Играю 850 High.".equals(again.message()), "Answer: " + again.message());
                var high = tune(c);
                h.assertTrue(high.mood() == 850 && high.mood() > 400 && c.campfireTuneStart() == pickedStart[0] + 1,
                        "The third «повеселее» did not move strictly up: «" + high.title() + "»");
                pickedStart[0] = c.campfireTuneStart();
                stage[0] = 3;
            } else if (stage[0] == 3 && c.campfire().playedTicks() > 20) {
                var again = CompanionEvents.executeAction(player, "campfire_song", "happier", 1);
                h.assertTrue(again.success() && "Играю 999 Top.".equals(again.message()), "Answer: " + again.message());
                var top = tune(c);
                h.assertTrue(top.mood() == 999 && top.mood() > 850 && c.campfireTuneStart() == pickedStart[0] + 1,
                        "The fourth «повеселее» did not move strictly up: «" + top.title() + "»");
                stage[0] = 4;
            }
        });
        h.succeedWhen(() -> h.assertTrue(stage[0] == 4,
                "Never asked again: pose " + c.campfirePose() + ", tune «" + c.campfire().tuneTitle() + "»"));
    }

    /**
     * A folder whose files were taken away and then put back is read as it is on the FIRST request: with one recording «другую»
     * answers that there are no others, and after three recordings are added the very next «другую» takes one of them at once, not
     * after a restart. The server scan is fresh, and the client's pack is rebuilt for the request by its folder fingerprint.
     */
    @GameTest(template = "empty", batch = "campfire_restored", timeoutTicks = 1200)
    public static void aRestoredFolderIsSeenOnTheFirstRequest(GameTestHelper h) {
        ground(h);
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-restored");
            writeOgg(dir, "0_quiet.ogg", 100);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        CompanionMusicLibrary.readFrom(dir);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "saddest", 1).success(), "Not taken");
        int[] stage = {0};
        String[] title = {null};
        int[] start = {0};
        h.onEachTick(() -> {
            if (stage[0] == 0 && c.campfirePose() == CompanionCampfire.PLAYING && c.campfire().playedTicks() > 20) {
                title[0] = c.campfire().tuneTitle();
                start[0] = c.campfireTuneStart();
                var again = CompanionEvents.executeAction(player, "campfire_song", "next", 1);
                h.assertTrue(again.success() && "Других песен не знаю.".equals(again.message()), "Answer: " + again.message());
                h.assertTrue(c.campfire().tuneTitle().equals(title[0]) && c.campfireTuneStart() == start[0],
                        "The only recording was restarted or stopped: «" + c.campfire().tuneTitle() + "»");
                // The files come back while the evening goes on: three new recordings, no reload and no restart of the game.
                try {
                    writeOgg(dir, "150_blue.ogg", 100);
                    writeOgg(dir, "400_dolphin.ogg", 100);
                    writeOgg(dir, "850_gavotte.ogg", 100);
                } catch (java.io.IOException e) { throw new IllegalStateException(e); }
                h.assertTrue(CompanionMusicLibrary.rescan().size() == 4, "The server scan did not see the restored files");
                int starts = c.campfireTuneStart();
                var next = CompanionEvents.executeAction(player, "campfire_song", "next", 1);
                h.assertTrue(next.success() && next.message().startsWith("Играю ") && !next.message().contains("Других песен"),
                        "The first request after the restore did not see the files: " + next.message());
                var picked = tune(c);
                h.assertTrue(!"0 Quiet".equals(c.campfire().tuneTitle()) && picked.mood() > 0
                                && c.campfireTuneStart() == starts + 1 && c.campfire().playedTicks() <= 2,
                        "The restored recording was not picked on the first request: «" + c.campfire().tuneTitle() + "», start "
                                + c.campfireTuneStart());
                h.assertTrue(next.message().contains(CompanionMusicLibrary.label(picked)),
                        "The answer does not name the restored recording: " + next.message());
                stage[0] = 1;
            }
        });
        h.succeedWhen(() -> h.assertTrue(stage[0] == 1,
                "Never asked again: pose " + c.campfirePose() + ", tune «" + c.campfire().tuneTitle() + "»"));
    }

    /**
     * Every name he says for a guitar request comes from the recording that really started, never from the model's imagination:
     * «самую грустную» answers with the folder's 0 recording, «повеселее» with the picked file, and each name is one the scan made
     * from the file. A reply that names a recording the folder does not hold («Lullaby», «Sunrise») is not kept.
     */
    @GameTest(template = "empty", batch = "campfire_names", timeoutTicks = 1200)
    public static void theGuitarAnswerNamesTheRealRecordingAndNeverAnInventedOne(GameTestHelper h) {
        ground(h);
        java.nio.file.Path dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("aicompanion-names");
            writeOgg(dir, "0_Aerie.ogg", 100);
            writeOgg(dir, "150_blue-blood.ogg", 100);
            writeOgg(dir, "400_dolphin.ogg", 100);
            writeOgg(dir, "550_guitar-improvisation.ogg", 100);
            writeOgg(dir, "800_hypnotic-heartbeat-atmosphere.ogg", 100);
            writeOgg(dir, "999_water-dripping.ogg", 100);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        CompanionMusicLibrary.readFrom(dir);
        h.setBlock(FIRE, Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 9, 16, 14, 16);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "campfire_song", "happiest", 1).success(), "Not taken");
        int[] stage = {0};
        h.onEachTick(() -> {
            if (stage[0] == 0 && c.campfirePose() == CompanionCampfire.PLAYING && c.campfire().playedTicks() > 20) {
                var top = tune(c);
                h.assertTrue(top.mood() == 999, "The evening did not start at 999: " + top);
                var again = CompanionEvents.executeAction(player, "campfire_song", "saddest", 1);
                h.assertTrue(again.success() && "Самая грустная — 0 Aerie.".equals(again.message()), "Answer: " + again.message());
                var zero = tune(c);
                h.assertTrue(zero.mood() == 0 && zero.title().equals("Aerie"), "Not the folder's 0 recording: " + zero);
                h.assertTrue(again.message().contains(CompanionMusicLibrary.label(zero)),
                        "The answer does not name the scanned recording: " + again.message());
                h.assertTrue(!CompanionMusicLibrary.namesUnknownRecording(again.message()),
                        "The answer names something the folder does not hold: " + again.message());
                stage[0] = 1;
            } else if (stage[0] == 1 && c.campfire().playedTicks() > 20) {
                var again = CompanionEvents.executeAction(player, "campfire_song", "happier", 1);
                h.assertTrue(again.success() && again.message().startsWith("Играю "), "Answer: " + again.message());
                var low = tune(c);
                h.assertTrue(low.mood() == 150, "Not the nearest higher recording: " + low);
                h.assertTrue(again.message().contains(CompanionMusicLibrary.label(low)),
                        "The answer does not name the picked file: " + again.message());
                h.assertTrue(!CompanionMusicLibrary.namesUnknownRecording(again.message()),
                        "The answer names something the folder does not hold: " + again.message());
                // A model reply that invents a name is recognised, and a real name is not.
                h.assertTrue(CompanionMusicLibrary.namesUnknownRecording("Играю «Lullaby» — вот тебе другая."),
                        "An invented name was taken for a recording");
                h.assertTrue(CompanionMusicLibrary.namesUnknownRecording("Играю «Sunrise», слушай."),
                        "An invented name was taken for a recording");
                h.assertTrue(!CompanionMusicLibrary.namesUnknownRecording("Играю 800 Hypnotic heartbeat atmosphere."),
                        "A real recording was taken for an invented name");
                h.assertTrue(!CompanionMusicLibrary.namesUnknownRecording("Сыграю повеселее, без названий."),
                        "A reply without a recording name was taken for an invented one");
                stage[0] = 2;
            }
        });
        h.succeedWhen(() -> h.assertTrue(stage[0] == 2,
                "Never asked again: pose " + c.campfirePose() + ", tune «" + c.campfire().tuneTitle() + "»"));
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
        h.assertTrue("companion campfire saddest".equals(CompanionLocalCommands.route("сыграй что нибудь грустное", "Олег")), "Sad tune");
        h.assertTrue("companion campfire happier".equals(CompanionLocalCommands.route("Олег, сыграй повеселее", "Олег")), "Cheerful tune");
        // The change requests are local too: a bare «другую», «повеселее», «погрустнее», «самую грустную» or «самую весёлую» must not
        // wait for the model, which used to answer some of them with a sentence and no step at all.
        h.assertTrue("companion campfire next".equals(CompanionLocalCommands.route("давай другую мелодию", "Олег")), "Another tune: " + CompanionLocalCommands.route("давай другую мелодию", "Олег"));
        h.assertTrue("companion campfire next".equals(CompanionLocalCommands.route("другую", "Олег")), "A bare «другую»: " + CompanionLocalCommands.route("другую", "Олег"));
        h.assertTrue("companion campfire sadder".equals(CompanionLocalCommands.route("погрустнее", "Олег")), "«Погрустнее»: " + CompanionLocalCommands.route("погрустнее", "Олег"));
        h.assertTrue("companion campfire sadder".equals(CompanionLocalCommands.route("нет есть грустнее", "Олег")), "«Грустнее»: " + CompanionLocalCommands.route("нет есть грустнее", "Олег"));
        h.assertTrue("companion campfire saddest".equals(CompanionLocalCommands.route("самую грустную", "Олег")), "«Самую грустную»: " + CompanionLocalCommands.route("самую грустную", "Олег"));
        h.assertTrue("companion campfire saddest".equals(CompanionLocalCommands.route("самую грустную сгырай", "Олег")), "«Самую грустную сгырай»: " + CompanionLocalCommands.route("самую грустную сгырай", "Олег"));
        h.assertTrue("companion campfire happiest".equals(CompanionLocalCommands.route("самую весёлую", "Олег")), "«Самую весёлую»: " + CompanionLocalCommands.route("самую весёлую", "Олег"));
        h.assertTrue("companion campfire".equals(CompanionLocalCommands.route("давай ещё сыграй", "Олег")), "«Давай ещё сыграй»: " + CompanionLocalCommands.route("давай ещё сыграй", "Олег"));
        for (String phrase : new String[]{"сыграй из майнкрафта", "сыграй что-нибудь из майнкрафта", "включи майнкрафт", "Олег, вруби майнкрафт"})
            h.assertTrue("companion campfire vanilla".equals(CompanionLocalCommands.route(phrase, "Олег")), "Not vanilla music: " + phrase + " -> " + CompanionLocalCommands.route(phrase, "Олег"));
        h.assertTrue("vanilla".equals(CompanionLocalCommands.actions("companion campfire vanilla").getFirst().item()), "Vanilla lost on the way to the step");
        h.assertTrue(CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString("[{\"action\":\"campfire_song\",\"item\":\"vanilla\"}]")).getFirst().item().equals("vanilla"), "Codec vanilla");
        h.assertTrue("saddest".equals(CompanionLocalCommands.actions("companion campfire saddest").getFirst().item()), "Mood lost on the way to the step");
        h.assertTrue(CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString("[{\"action\":\"campfire_song\",\"item\":\"saddest\"}]")).getFirst().item().equals("saddest"), "Codec mood");
        h.assertTrue(CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString("[{\"action\":\"campfire_song\",\"item\":\"happier\"}]")).getFirst().item().equals("happier"), "Codec relative mood");
        for (String phrase : new String[]{"хватит играть", "Олег, перестань играть", "убери гитару"})
            h.assertTrue("companion stop".equals(CompanionLocalCommands.route(phrase, "Олег")), "Not a stop: " + phrase);
        for (String phrase : new String[]{"давай сыграем в прятки", "не играй сейчас на гитаре", "поставь костёр", "расскажи про майнкрафт"})
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
        // Every batch that lays a ground points the library at the empty folder before its tests: a batch that forgot says so
        // here, instead of playing whatever the player left in the run's config folder.
        h.assertTrue(emptyFolder().equals(CompanionMusicLibrary.folder()),
                "This batch did not point the guitar library at the empty folder: " + CompanionMusicLibrary.folder());
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

    /**
     * The folder the library read when this class was loaded, taken in the last static initializer of the class so that every
     * static field above it has already run: a test class must not change what the game plays merely by being loaded. The guard
     * test compares this instead of resetting first, because a reset would produce the very answer it checks.
     */
    private static final java.nio.file.Path FOLDER_WHEN_LOADED;
    static { FOLDER_WHEN_LOADED = CompanionMusicLibrary.folder(); }
}

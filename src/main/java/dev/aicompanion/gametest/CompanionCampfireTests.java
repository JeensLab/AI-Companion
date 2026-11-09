package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.entity.CompanionCampfire;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
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
            var tune = CompanionTunes.ALL.stream().filter(t -> t.title().equals(c.campfire().tuneTitle())).findFirst().orElseThrow();
            h.assertTrue(c.expression() == (tune.cheerful() ? dev.aicompanion.CompanionExpression.PLEASED : dev.aicompanion.CompanionExpression.CALM),
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
                h.assertTrue(tune(c).cheerful(), "Asked for a cheerful one, got «" + c.campfire().tuneTitle() + "»");
                seat[0] = c.position();
                String before = c.campfire().tuneTitle();
                var again = CompanionEvents.executeAction(player, "campfire_song", "calm", 1);
                h.assertTrue(again.success() && c.campfire().active(), "Asking again: " + again.message());
                h.assertTrue(!c.campfire().tuneTitle().equals(before) && !tune(c).cheerful(), "Not a calm tune at once: «" + c.campfire().tuneTitle() + "»");
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
     * The recordings: each tune's file is in the mod, mono (so it sounds from where he sits) and as long as the catalog says (the evening
     * goes on to the next tune by that length); titles and ids differ, and there are cheerful and calm ones.
     */
    @GameTest(template = "empty")
    public static void theRecordingsAreThereAndAsLongAsTheCatalogSays(GameTestHelper h) {
        var titles = new HashSet<String>();
        var ids = new HashSet<String>();
        h.assertTrue(CompanionTunes.ALL.size() >= 6, "Too few tunes: " + CompanionTunes.ALL.size());
        h.assertTrue(CompanionTunes.ALL.stream().anyMatch(CompanionTunes.Tune::cheerful) && CompanionTunes.ALL.stream().anyMatch(t -> !t.cheerful()),
                "Both cheerful and calm tunes are needed");
        h.assertTrue(CompanionCampfireTests.class.getResource("/assets/aicompanion/sounds/guitar/CREDITS.md") != null, "The credits are missing");
        for (var tune : CompanionTunes.ALL) {
            h.assertTrue(titles.add(tune.title()) && ids.add(tune.id()), "Two tunes alike: " + tune);
            h.assertTrue(AICompanionMod.GUITAR_TUNES.get(tune.id()) != null && AICompanionMod.GUITAR_TUNES.get(tune.id()).get() != null, "No sound event for " + tune.id());
            byte[] ogg;
            try (var in = CompanionCampfireTests.class.getResourceAsStream("/assets/aicompanion/sounds/guitar/" + tune.id() + ".ogg")) {
                h.assertTrue(in != null, "No file for " + tune.id());
                ogg = in.readAllBytes();
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
            int header = indexOf(ogg, new byte[]{1, 'v', 'o', 'r', 'b', 'i', 's'}, 0);
            h.assertTrue(header >= 0, tune.id() + " is not Ogg Vorbis");
            int channels = ogg[header + 11] & 0xFF;
            long rate = java.nio.ByteBuffer.wrap(ogg, header + 12, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
            int last = -1;
            for (int at = indexOf(ogg, new byte[]{'O', 'g', 'g', 'S'}, 0); at >= 0; at = indexOf(ogg, new byte[]{'O', 'g', 'g', 'S'}, at + 4)) last = at;
            long granule = java.nio.ByteBuffer.wrap(ogg, last + 6, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong();
            double seconds = (double) granule / rate;
            h.assertTrue(channels == 1, tune.id() + " has " + channels + " channels: only mono sounds from a place");
            h.assertTrue(Math.abs(seconds * 20 - tune.ticks()) <= 10, tune.id() + " lasts " + seconds + " s, the catalog says " + tune.ticks() + " ticks");
            h.assertTrue(seconds >= 30 && seconds <= 300, tune.id() + " lasts " + seconds + " s");
        }
        h.succeed();
    }

    private static int indexOf(byte[] data, byte[] what, int from) {
        outer:
        for (int i = from; i <= data.length - what.length; i++) {
            for (int j = 0; j < what.length; j++) if (data[i + j] != what[j]) continue outer;
            return i;
        }
        return -1;
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

    private static CompanionTunes.Tune tune(CompanionEntity c) {
        return CompanionTunes.ALL.stream().filter(t -> t.title().equals(c.campfire().tuneTitle())).findFirst().orElseThrow();
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

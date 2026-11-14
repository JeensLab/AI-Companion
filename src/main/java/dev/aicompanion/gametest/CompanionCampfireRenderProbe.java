package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionVoiceAudio;
import dev.aicompanion.entity.CompanionCampfire;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMusicLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Opt-in offline render QA of the evening by the fire (-Daicompanion.campfireProbe=true on runClient; the gametest package is
 * excluded from the release JAR): a fresh flat world, a lit campfire, «сыграй», then screenshots of him sitting with the guitar from
 * the front, the side and at night into the client's screenshots folder, then «повеселее» and «спокойнее» — each must make the client
 * stream a different recording, unless that mood holds a single recording, which is then restarted — and a check that the client
 * streamed his guitar recordings at all and that the game's own music does not sound over them (keep the master volume above zero,
 * the game skips silent sounds). No provider key is needed.
 */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID, value = Dist.CLIENT)
public final class CompanionCampfireRenderProbe {
    private static boolean started, asked, placed, passed;
    // The client began streaming one of his guitar recordings (the sound itself, not only the server's word for it).
    private static volatile String streamed;
    /** The recording the client streamed first, and the one it streams after the mood requests. */
    private static String first, second;
    private static int ticks, shot;
    /** 0: the screenshots; 1: «повеселее» asked; 2: «спокойнее» asked; 3: passed. */
    private static int stage, stageAt, changeAt;
    private static String before;
    /** The tune start counter the client had when a mood was asked for, and since when it has been seen playing. */
    private static int askedStart, playingSince;
    /** Whether the game's own music was checked (and found silent) while the guitar sounded. */
    private static boolean musicChecked;
    private static final String[] SHOTS = {"campfire-front", "campfire-side", "campfire-three-quarter", "campfire-night"};
    private static final double[][] VIEWS = {{0, 4.2}, {3.2, 0}, {2.4, 2.4}, {-1.8, 3.6}};

    private CompanionCampfireRenderProbe() { }

    @SubscribeEvent
    public static void streamed(net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent event) {
        var id = event.getSound().getLocation();
        if (!Boolean.getBoolean("aicompanion.campfireProbe") || !id.getNamespace().equals(AICompanionMod.MOD_ID)
                || !id.getPath().startsWith("guitar_folder/")) return;
        streamed = id.toString();
        if (first == null) first = streamed;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("aicompanion.campfireProbe")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            mc.createWorldOpenFlows().createFreshLevel("OlegCampfire-" + System.currentTimeMillis(),
                    new LevelSettings("Offline campfire QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            return;
        }
        CompanionEntity oleg = CompanionVoiceAudio.speaker();
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || oleg == null || server == null) return;
        ticks++;
        // Unattended: a pause screen left by the world creation (or a lost-focus pause) would freeze the integrated server, and the
        // companion would never sit down while the probe ticks on. Nothing is waited for here, so any pause screen is closed.
        if (mc.screen != null && mc.screen.isPauseScreen()) mc.setScreen(null);
        if (mc.getOverlay() != null && mc.getOverlay().isPauseScreen()) mc.setOverlay(null);
        if (ticks > 20 * 180) throw new IllegalStateException("Campfire probe timed out at stage " + stage + ", pose " + oleg.campfirePose()
                + ", streamed " + streamed + ", tune " + oleg.campfireTune() + " (start " + oleg.campfireTuneStart() + "), paused "
                + mc.isPaused() + ", screen " + mc.screen + ", overlay " + mc.getOverlay() + ", server ticks " + server.getTickCount());
        // The guitar is the evening's music: while it sounds, the game's own track must not be heard over it. The client refuses
        // every music selection then and stops the track sounding as the guitar starts, so the check begins a few ticks into the
        // tune, past the tick the guitar itself started on, and only once a recording has really streamed.
        if (streamed != null && oleg.campfirePose() == CompanionCampfire.PLAYING) {
            if (playingSince == 0) playingSince = ticks;
            var situational = mc.getSituationalMusic();
            if (ticks - playingSince > 5) {
                musicChecked = true;
                if (situational != null && mc.getMusicManager().isPlayingMusic(situational))
                    throw new IllegalStateException("The game's own music sounds over the guitar: "
                            + situational.getEvent().value().getLocation() + " (tune " + oleg.campfireTune() + ")");
            }
        } else {
            playingSince = 0;
        }
        if (!asked && ticks > 40) {
            asked = true;
            mc.options.hideGui = true;
            AICompanionMod.LOGGER.info("Campfire probe: paused {}, screen {}, overlay {}, server ticks {}, his ticks {}",
                    mc.isPaused(), mc.screen, mc.getOverlay(), server.getTickCount(), oleg.tickCount);
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                var level = player.serverLevel();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                BlockPos fire = player.blockPosition().relative(player.getDirection(), 3);
                level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), 3);
                var result = CompanionEvents.executeAction(player, "campfire_song", "", 1);
                AICompanionMod.LOGGER.info("Campfire probe: {} (fire {})", result.message(), fire);
            });
            return;
        }
        if (!asked) return;
        if (stage == 0) { shots(mc, server, oleg); return; }
        // The mood requests: each must make the client stream a different recording of the folder.
        if (streamed == null) throw new IllegalStateException("No guitar recording was streamed by the client");
        String mood = stage == 1 ? "cheerful" : "calm";
        if (changeAt == 0) {
            if (ticks - stageAt < 20) return;
            before = streamed;
            askedStart = oleg.campfireTuneStart();
            changeAt = ticks;
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                var result = CompanionEvents.executeAction(player, "campfire_song", mood, 1);
                AICompanionMod.LOGGER.info("Campfire probe: «{}» — {} (tune {} now, start {})", mood, result.message(),
                        oleg.campfireTune(), oleg.campfireTuneStart());
            });
            return;
        }
        if (!streamed.equals(before)) {
            if (passed) return;
            AICompanionMod.LOGGER.info("Campfire probe: after «{}» the client streams {} (was {})", mood, streamed, before);
            second = streamed;
            if (stage == 1) { stage = 2; stageAt = ticks; changeAt = 0; return; }
            passed = true;
            AICompanionMod.LOGGER.info("CAMPFIRE_RENDER_PROBE_PASSED: {} screenshots, recordings streamed {} → {}, the game's own music silent over the guitar: {}",
                    SHOTS.length, first, second, musicChecked);
            mc.stop();
            return;
        }
        // Asking for a mood whose pool holds one recording restarts the recording that is playing instead of switching: the same
        // id streams again, so the restart is accepted when the server's own counter moved and that recording is the only one the
        // mood can offer. Without this, a folder with a single cheerful or calm recording would fail as if nothing had happened.
        if (ticks - changeAt > 20 && oleg.campfireTuneStart() != askedStart && before.equals(oleg.campfireTune())
                && noOtherRecordingFor(mood)) {
            AICompanionMod.LOGGER.info("Campfire probe: «{}» restarted the only recording of that mood: {} (start {} → {})",
                    mood, streamed, askedStart, oleg.campfireTuneStart());
            second = streamed;
            if (stage == 1) { stage = 2; stageAt = ticks; changeAt = 0; return; }
            passed = true;
            AICompanionMod.LOGGER.info("CAMPFIRE_RENDER_PROBE_PASSED: {} screenshots, recordings streamed {} → {} (a mood with one recording restarted it), the game's own music silent over the guitar: {}",
                    SHOTS.length, first, second, musicChecked);
            mc.stop();
            return;
        }
        if (ticks - changeAt > 20 * 10) throw new IllegalStateException("The tune did not change after «" + mood + "»: still " + streamed
                + ", tune " + oleg.campfireTune() + " (start " + oleg.campfireTuneStart() + "), pose " + oleg.campfirePose());
    }

    /**
     * Whether the mood asked for leaves no other recording to switch to: its pool holds a single recording, or the whole folder
     * holds one — then the tune that is playing is restarted, and the streamed id legitimately stays the same.
     */
    private static boolean noOtherRecordingFor(String mood) {
        var tracks = CompanionMusicLibrary.available();
        if (tracks.size() == 1) return true;
        CompanionMusicLibrary.Mood wanted = "cheerful".equals(mood) ? CompanionMusicLibrary.Mood.CHEERFUL
                : CompanionMusicLibrary.Mood.CALM;
        int tagged = 0;
        for (CompanionMusicLibrary.Track track : tracks) if (track.mood() == wanted) tagged++;
        return tagged == 1;
    }

    /** The four views of him playing, one every two seconds, into the client's screenshots folder. */
    private static void shots(Minecraft mc, net.minecraft.server.MinecraftServer server, CompanionEntity oleg) {
        if (oleg.campfirePose() != CompanionCampfire.PLAYING) return;
        int phase = ticks % 40;
        if (phase == 0) {
            placed = true;
            if (shot >= SHOTS.length) {
                if (streamed == null) throw new IllegalStateException("No guitar recording was streamed by the client");
                stage = 1;
                stageAt = ticks;
                changeAt = 0;
                AICompanionMod.LOGGER.info("Campfire probe: the client streamed {} for the evening", streamed);
                return;
            }
            double[] view = VIEWS[shot];
            Vec3 at = oleg.position();
            float yaw = oleg.yBodyRot;
            // The views are in his own frame: forward is where he faces, right his right hand.
            Vec3 forward = Vec3.directionFromRotation(0, yaw), right = new Vec3(-forward.z, 0, forward.x);
            Vec3 eye = at.add(forward.scale(view[1])).add(right.scale(view[0])).add(0, 1.8, 0);
            Vec3 look = at.add(0, .6, 0).subtract(eye);
            float viewYaw = (float) (Math.toDegrees(Math.atan2(-look.x, look.z)));
            float viewPitch = (float) (-Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z))));
            boolean night = SHOTS[shot].endsWith("night");
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (night) player.serverLevel().setDayTime(18000);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.connection.teleport(eye.x, eye.y - player.getEyeHeight(), eye.z, viewYaw, viewPitch);
            });
        } else if (phase == 30 && placed) {
            placed = false;
            Screenshot.grab(mc.gameDirectory, SHOTS[shot] + ".png", mc.getMainRenderTarget(),
                    message -> AICompanionMod.LOGGER.info("Campfire probe screenshot: {}", message.getString()));
            shot++;
        }
    }
}

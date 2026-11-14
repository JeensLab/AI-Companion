package dev.aicompanion.client;

import com.mojang.logging.LogUtils;
import dev.aicompanion.entity.CompanionCampfire;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionMusicLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * The tune by the fire on this client (the owner's ruling, 13.11): the recordings come from the player's folder, which the game's
 * sound engine knows only through the dynamic resource pack ({@link CompanionMusicPack}), not as registered sound events; while the
 * folder is empty the evening plays standard Minecraft music ({@link dev.aicompanion.entity.CompanionTunes}). So the server sends no
 * sound packet: it syncs the sound id and a start counter with his pose (CompanionEntity.campfireTune, campfireTuneStart) and this
 * plays that id from him while he sits and plays, starting it over when the counter moves — a recording asked for again is restarted,
 * not left sounding — and stopping when the tune changes, he stands up or the world goes. The «Jukebox/Note Blocks» volume and the
 * distance work as for any entity-bound sound, and the game's own background music is silenced for as long as the guitar sounds
 * (every music selection is refused while the tune does, not only the track sounding at its start), so the guitar is what is heard;
 * when the guitar stops, the game's music gets its normal countdown back instead of the twenty-minute cap a stop leaves behind.
 * An id this client cannot resolve is never started in place of the tune sounding: the tune that plays goes on and the chat says
 * why, so a change is not claimed when none can be heard.
 *
 * <p>Every step is said once in the log, never on every tick: what the client sees synced, what it decides, which id it hands the
 * sound engine, whether that engine knows the id and its volume, whether it began streaming the recording, and why a tune is skipped.
 * That is what tells a tune that never starts from one that never changes.
 */
final class CompanionCampfireAudio {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** He is looked for only this close: the sound itself fades out at sixteen blocks. */
    private static final double LOOK_RANGE = 48;
    private static SoundInstance playing;
    private static String tune = "";
    /** The tune start the client has begun (-1 for none), so the same recording asked for again is started over. */
    private static int started = -1;
    /** Ticks the recording has sounded, and its length in ticks (-1 when it cannot be told). */
    private static int sounded, length;
    /** An id whose recording has just played out: not started again while the server still names it. */
    private static String finished = "";
    private static CompanionEntity companion;
    private static int lookAgain;
    /** The sound id already reported as not loaded, so one line is said once and not on every tick. */
    private static String reported = "";
    /** The last line said for each reason (the part of a key before ':'): a decision that repeats every tick is said once, and a
     *  situation that alternates between two reasons — an unknown id: START, then unknown — is said once for each, not every tick. */
    private static final Map<String, String> noted = new HashMap<>();
    /** What the client last saw synced (pose, tune, start counter), so a change is said once. */
    private static int seenPose = -1, seenStart = -2;
    private static String seenTune = "";
    /** The last folder recording the sound engine began streaming, so a start that never streamed is told apart from one that did.
     *  Only the folder's recordings are kept: the game streams its own music too, and that would hide a recording never opened. */
    private static volatile String streaming = "";

    private CompanionCampfireAudio() { }

    /** Every client tick: the sound follows the pose, the tune and the start counter the server synced. */
    static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) { forget(); return; }
        if (companion != null && (companion.isRemoved() || companion.level() != client.level)) companion = null;
        if (companion == null) {
            // No companion in sight yet: look again now and then, not on every tick.
            if (lookAgain-- > 0) return;
            lookAgain = 10;
            companion = client.level.getEntitiesOfClass(CompanionEntity.class, client.player.getBoundingBox().inflate(LOOK_RANGE),
                    entity -> client.player.getUUID().equals(entity.getOwnerId())).stream().findFirst().orElse(null);
            if (companion == null) return;
        }
        int pose = companion.campfirePose();
        boolean byTheFire = pose == CompanionCampfire.PLAYING;
        String wanted = byTheFire ? companion.campfireTune() : "";
        int start = byTheFire ? companion.campfireTuneStart() : -1;
        seen(pose, wanted, start);
        boolean sameTune = playing != null && wanted.equals(tune);
        // The sound engine knows an id only from a resource pack: on a server the recording the server picked may not be in this
        // client's folder at all. That is decided before anything is stopped, so a tune that cannot start does not silence the one
        // that is sounding.
        ResourceLocation id = wanted.isEmpty() ? null : ResourceLocation.tryParse(wanted);
        boolean known = id != null && client.getSoundManager().getSoundEvent(id) != null;
        switch (CompanionCampfirePlayback.decide(wanted, start, started, sameTune,
                sameTune && client.getSoundManager().isActive(playing), sounded, length, finished, known)) {
            case STOP -> {
                if (playing != null || !tune.isEmpty()) {
                    note("stop:" + tune, "the tune «" + tune + "» stops: pose " + poseName(pose) + ", no tune is named");
                    stop();
                }
                started = -1;
                return;
            }
            // The tune that is wanted sounds on: nothing to do while it does.
            case KEEP_SOUNDING -> {
                // The engine said the sound is active; that alone does not prove it opened the recording, so the stream is checked
                // once: a recording the engine could not open sounds nothing at all.
                if (sounded == 20 && isFolderTune(tune) && !tune.equals(streaming))
                    note("nostream:" + tune, "the sound engine reports «" + tune + "» active but never began streaming it — "
                            + "the recording could not be opened");
                sounded++;
                return;
            }
            // The tune the server names is not here: the one sounding goes on, and the line says so rather than claiming a change.
            case KEEP_OLD -> { keepOld(wanted); return; }
            // The counter moved: the same recording asked for again starts over, it is not left sounding.
            case START_OVER -> {
                note("over:" + wanted + ":" + start, "the tune «" + wanted + "» is started over: the start counter moved to "
                        + start + " from " + started);
                stop(); started = start; finished = "";
            }
            // It has played out by its length, not stopped by a reload: not started again while it is still named.
            case PLAYED_OUT -> {
                note("out:" + wanted, "the tune «" + wanted + "» played out: " + sounded + " of " + length + " ticks");
                stop(); finished = wanted; return;
            }
            case FINISHED -> { return; }
            case START -> {
                note("start:" + wanted + ":" + start, "the tune «" + wanted + "» starts: start counter " + start);
                stop();
            }
        }
        reported = "";
        length = lengthOf(wanted);
        sounded = 0;
        // The guitar is the music of the evening: the game's own background music does not sound over it, or a tune that changed
        // would be heard under an unchanged track of the game and seem not to have changed at all. The track sounding now is
        // stopped here, and selectMusic() refuses every new one while the tune sounds: one stop is not enough, the game's own
        // countdown goes on and would start a track minutes into the guitar.
        client.getMusicManager().stopPlaying();
        playing = new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(id), SoundSource.RECORDS, 1F, 1F, companion,
                companion.getRandom().nextLong());
        client.getSoundManager().play(playing);
        tune = wanted;
        note("play:" + wanted + ":" + start, "playing «" + wanted + "» from him: start counter " + start + ", length " + length
                + " ticks, the sound engine knows the id, «Jukebox/Note Blocks» volume "
                + client.options.getSoundSourceVolume(SoundSource.RECORDS)
                + ", the game's own music stopped and kept stopped while the guitar sounds");
    }

    /**
     * The tune the server names is not one this client's sound engine knows — on a server, a recording that is in the server's
     * folder and not in this player's. The tune that is sounding goes on rather than stop into silence, and the line says so: the
     * answer already named the tune it picked, and this is the part of it this client can keep.
     */
    private static void keepOld(String wanted) {
        Minecraft client = Minecraft.getInstance();
        boolean loading = client.getOverlay() instanceof LoadingOverlay;
        // Not while a reload is queued or running: the sound engine is about to read the folder, so the line would be premature.
        if (!wanted.equals(reported) && !loading) {
            reported = wanted;
            boolean here = inClientFolder(wanted);
            String said = here ? "Запись ещё не загружена: перечитайте ресурсы (F3+T)."
                    : "Этой песни нет на клиенте: на сервере она есть, в вашей папке config/aicompanion/music — нет.";
            client.gui.getChat().addMessage(Component.literal(said + (tune.isEmpty() ? "" : " Пока звучит прежняя песня.")));
        }
        note("unknown:" + wanted, "the sound engine does not know «" + wanted + "» (" + client.getSoundManager().getAvailableSounds().size()
                + " ids known): " + (inClientFolder(wanted) ? "the recording is in this client's folder, the resources need a reload"
                : "the recording is not in this client's folder either")
                + " — the tune «" + tune + "» goes on, nothing is claimed changed"
                + (loading ? " (a resource reload is running)" : ""));
    }

    /**
     * The game's own music is silenced while the guitar sounds: the music manager asks what to play every tick, and while a tune
     * is held, nothing is chosen — so a track the game would have started minutes into the evening never sounds over the guitar.
     * When the guitar stops, the selection is let through again and {@link #stop()} gives the music its normal countdown back.
     */
    static void selectMusic(SelectMusicEvent event) {
        if (playing != null) event.setMusic(null);
    }

    /**
     * The sound engine began streaming a sound: for a folder recording this is the ground truth that this client plays the file
     * itself, not a disc and not silence. Only the folder's recordings are remembered — the game streams its own music too, and a
     * track of the game streaming would hide a recording that never opened — and a line is said for them alone.
     */
    static void streamed(net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent event) {
        ResourceLocation id = event.getSound().getLocation();
        if (!isFolderId(id)) return;
        streaming = id.toString();
        LOGGER.info("AI Companion: the sound engine is streaming {} for the evening by the fire", id);
    }

    /** What the client sees synced, said when it changes: the tune, the counter and the pose the server put on him. */
    private static void seen(int pose, String wanted, int start) {
        if (pose == seenPose && start == seenStart && wanted.equals(seenTune)) return;
        seenPose = pose; seenStart = start; seenTune = wanted;
        LOGGER.info("AI Companion: the client sees him {} with the tune «{}» and start counter {}",
                poseName(pose), wanted.isEmpty() ? "none" : wanted, start);
    }

    private static String poseName(int pose) {
        return pose == CompanionCampfire.PLAYING ? "playing" : pose == CompanionCampfire.SITTING ? "sitting" : "standing";
    }

    /** Says a line once per reason and key: a decision that repeats every tick is said once, and each reason keeps its own last
     *  key, so a tune that alternates START and unknown does not say either line on every tick. */
    private static void note(String key, String line) {
        int colon = key.indexOf(':');
        String reason = colon < 0 ? key : key.substring(0, colon);
        if (key.equals(noted.put(reason, key))) return;
        LOGGER.info("AI Companion: {}", line);
    }

    /** Whether a sound id is one of the player's folder recordings (aicompanion:guitar_folder/…), not the game's own music. */
    private static boolean isFolderId(ResourceLocation id) {
        return dev.aicompanion.AICompanionMod.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith("guitar_folder/");
    }

    /** Whether a tune's sound id is one of the player's folder recordings, whose stream proves the file was opened. */
    private static boolean isFolderTune(String soundId) {
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        return id != null && isFolderId(id);
    }

    /** Whether this client's own folder holds the recording: then only a resource reload is missing, not the file itself. */
    private static boolean inClientFolder(String soundId) {
        for (CompanionMusicLibrary.Track track : CompanionMusicLibrary.scan(CompanionMusicLibrary.folder()))
            if (track.soundId().equals(soundId)) return true;
        return false;
    }

    /** The length of a recording in ticks, as the evening reads it — a folder's recording or the standard music; -1 when it cannot be told. */
    private static int lengthOf(String soundId) {
        for (CompanionMusicLibrary.Track track : CompanionMusicLibrary.available())
            if (track.soundId().equals(soundId)) return track.ticks();
        for (CompanionMusicLibrary.Track track : CompanionMusicLibrary.fallback())
            if (track.soundId().equals(soundId)) return track.ticks();
        return -1;
    }

    /** The evening is over, the tune changed or the world went: the sound stops here and the game's own music gets its countdown
     *  back, so a tune that ends does not leave the player without background music for the twenty-minute cap. */
    static void stop() {
        if (playing != null) Minecraft.getInstance().getSoundManager().stop(playing);
        playing = null;
        tune = "";
        sounded = 0;
        length = -1;
        resumeGameMusic();
    }

    /**
     * Gives the game's music manager a normal countdown again: stopPlaying() only adds a hundred ticks to the countdown it keeps,
     * and after a track it sits at the twenty-minute cap — left there, the player would lose background music for up to twenty
     * minutes after every evening and again on every tune change. The delay is the one the game itself uses after a track ends:
     * a random wait within the situational music's own range.
     */
    private static void resumeGameMusic() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Music music = client.getSituationalMusic();
        if (music == null) return;
        client.getMusicManager().nextSongDelay = Mth.nextInt(RandomSource.create(), music.getMinDelay(), music.getMaxDelay());
    }

    /** Leaving the world: nothing of the old one is left sounding. */
    static void forget() {
        stop();
        companion = null; lookAgain = 0; reported = ""; finished = ""; started = -1;
        noted.clear(); seenPose = -1; seenStart = -2; seenTune = ""; streaming = "";
    }
}

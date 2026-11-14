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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;

/**
 * The tune by the fire on this client (the owner's ruling, 13.11): the recordings come from the player's folder, which the game's
 * sound engine knows only through the dynamic resource pack ({@link CompanionMusicPack}), not as registered sound events; while the
 * folder is empty the evening plays standard Minecraft music ({@link dev.aicompanion.entity.CompanionTunes}). So the server sends no
 * sound packet: it syncs the sound id and a start counter with his pose (CompanionEntity.campfireTune, campfireTuneStart) and this
 * plays that id from him while he sits and plays, starting it over when the counter moves — a recording asked for again is restarted,
 * not left sounding — and stopping when the tune changes, he stands up or the world goes. The «Jukebox/Note Blocks» volume and the
 * distance work as for any entity-bound sound, and the game's own background music is stopped while a tune plays, so the guitar is
 * what is heard. An id this client cannot resolve is reported in the chat, never replaced by a disc.
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
    /** The last line said about a decision, so the same situation is not said on every tick. */
    private static String noted = "";
    /** What the client last saw synced (pose, tune, start counter), so a change is said once. */
    private static int seenPose = -1, seenStart = -2;
    private static String seenTune = "";
    /** The last id the sound engine began streaming, so a start that never streamed is told apart from one that did. */
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
        switch (CompanionCampfirePlayback.decide(wanted, start, started, sameTune,
                sameTune && client.getSoundManager().isActive(playing), sounded, length, finished)) {
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
                if (sounded == 20 && !tune.equals(streaming))
                    note("nostream:" + tune, "the sound engine reports «" + tune + "» active but never began streaming it — "
                            + "the recording could not be opened");
                sounded++;
                return;
            }
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
        ResourceLocation id = ResourceLocation.tryParse(wanted);
        if (id == null) {
            note("bad:" + wanted, "«" + wanted + "» is not a sound id: nothing is played");
            return;
        }
        if (client.getSoundManager().getSoundEvent(id) == null) {
            // Not read yet — a recording added after the last resource reload, or one this client does not have at all. Said once, and
            // not while a reload is queued or running: the sound engine is about to read the folder, so the line would be premature.
            boolean loading = client.getOverlay() instanceof LoadingOverlay;
            if (!wanted.equals(reported) && !loading) {
                reported = wanted;
                client.gui.getChat().addMessage(Component.literal(inClientFolder(wanted)
                        ? "Запись ещё не загружена: перечитайте ресурсы (F3+T)."
                        : "Этой записи нет на клиенте: положите её в папку config/aicompanion/music — на сервере она нужна и серверу, и клиенту."));
            }
            note("unknown:" + wanted, "the sound engine does not know «" + wanted + "» (" + client.getSoundManager().getAvailableSounds().size()
                    + " ids known): " + (inClientFolder(wanted) ? "the recording is in this client's folder, the resources need a reload"
                    : "the recording is not in this client's folder either")
                    + (loading ? " (a resource reload is running)" : ""));
            return;
        }
        reported = "";
        length = lengthOf(wanted);
        sounded = 0;
        // The guitar is the music of the evening: the game's own background music does not sound over it, or a tune that changed
        // would be heard under an unchanged track of the game and seem not to have changed at all.
        client.getMusicManager().stopPlaying();
        playing = new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(id), SoundSource.RECORDS, 1F, 1F, companion,
                companion.getRandom().nextLong());
        client.getSoundManager().play(playing);
        tune = wanted;
        note("play:" + wanted + ":" + start, "playing «" + wanted + "» from him: start counter " + start + ", length " + length
                + " ticks, the sound engine knows the id, active " + client.getSoundManager().isActive(playing)
                + ", «Jukebox/Note Blocks» volume " + client.options.getSoundSourceVolume(SoundSource.RECORDS)
                + ", the game's own music stopped");
    }

    /**
     * The sound engine began streaming a sound: for a folder recording this is the ground truth that this client plays the file
     * itself, not a disc and not silence. Every streamed id is remembered, so a tune whose stream never began is told apart; a line is
     * said for the folder's recordings only.
     */
    static void streamed(net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent event) {
        ResourceLocation id = event.getSound().getLocation();
        streaming = id.toString();
        if (!dev.aicompanion.AICompanionMod.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith("guitar_folder/")) return;
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

    /** Says a line once per change: a decision that repeats every tick is said once. */
    private static void note(String key, String line) {
        if (key.equals(noted)) return;
        noted = key;
        LOGGER.info("AI Companion: {}", line);
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

    /** The evening is over, the tune changed or the world went: the sound stops here. */
    static void stop() {
        if (playing != null) Minecraft.getInstance().getSoundManager().stop(playing);
        playing = null;
        tune = "";
        sounded = 0;
        length = -1;
    }

    /** Leaving the world: nothing of the old one is left sounding. */
    static void forget() {
        stop();
        companion = null; lookAgain = 0; reported = ""; finished = ""; started = -1;
        noted = ""; seenPose = -1; seenStart = -2; seenTune = ""; streaming = "";
    }
}

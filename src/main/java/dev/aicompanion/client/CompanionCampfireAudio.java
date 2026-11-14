package dev.aicompanion.client;

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

/**
 * The tune by the fire on this client (the owner's ruling, 13.11): the recordings come from the player's folder, which the game's
 * sound engine knows only through the dynamic resource pack ({@link CompanionMusicPack}), not as registered sound events; while the
 * folder is empty the evening plays standard Minecraft music ({@link dev.aicompanion.entity.CompanionTunes}). So the server sends no
 * sound packet: it syncs the sound id and a start counter with his pose (CompanionEntity.campfireTune, campfireTuneStart) and this
 * plays that id from him while he sits and plays, starting it over when the counter moves — a recording asked for again is restarted,
 * not left sounding — and stopping when the tune changes, he stands up or the world goes. The «Jukebox/Note Blocks» volume and the
 * distance work as for any entity-bound sound. An id this client cannot resolve is reported in the chat, never replaced by a disc.
 */
final class CompanionCampfireAudio {
    /** He is looked for only this close: the sound itself fades out at sixteen blocks. */
    private static final double LOOK_RANGE = 48;
    /** A recording is taken as played out this close to its length: the sound engine ends it within a tick or two of it. */
    private static final int END_SLACK = 2;
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
        boolean byTheFire = companion.campfirePose() == CompanionCampfire.PLAYING;
        String wanted = byTheFire ? companion.campfireTune() : "";
        int start = byTheFire ? companion.campfireTuneStart() : -1;
        if (wanted.isEmpty()) {
            if (playing != null || !tune.isEmpty()) stop();
            started = -1;
            return;
        }
        boolean restarted = start != started;
        // The tune that is wanted sounds on: nothing to do while it does.
        if (!restarted && playing != null && wanted.equals(tune) && client.getSoundManager().isActive(playing)) { sounded++; return; }
        // It is gone: it has played out, or a reload stopped it. The length tells the two apart.
        boolean playedOut = !restarted && playing != null && wanted.equals(tune) && length > 0 && sounded + END_SLACK >= length;
        if (restarted) { stop(); started = start; finished = ""; }
        else if (playedOut) { stop(); finished = wanted; return; }
        else if (wanted.equals(finished)) return;
        else stop();
        ResourceLocation id = ResourceLocation.tryParse(wanted);
        if (id == null) return;
        if (client.getSoundManager().getSoundEvent(id) == null) {
            // Not read yet — a recording added after the last resource reload, or one this client does not have at all. Said once, and
            // not while a reload is queued or running: the sound engine is about to read the folder, so the line would be premature.
            if (!wanted.equals(reported) && !(client.getOverlay() instanceof LoadingOverlay)) {
                reported = wanted;
                client.gui.getChat().addMessage(Component.literal(inClientFolder(wanted)
                        ? "Запись ещё не загружена: перечитайте ресурсы (F3+T)."
                        : "Этой записи нет на клиенте: положите её в папку config/aicompanion/music — на сервере она нужна и серверу, и клиенту."));
            }
            return;
        }
        reported = "";
        length = lengthOf(wanted);
        sounded = 0;
        playing = new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(id), SoundSource.RECORDS, 1F, 1F, companion,
                companion.getRandom().nextLong());
        client.getSoundManager().play(playing);
        tune = wanted;
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
    static void forget() { stop(); companion = null; lookAgain = 0; reported = ""; finished = ""; started = -1; }
}

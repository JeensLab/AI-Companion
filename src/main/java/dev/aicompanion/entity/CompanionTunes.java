package dev.aicompanion.entity;

import java.util.List;

/**
 * What he plays while the player's folder has no recordings (the owner's ruling, 13.11): standard Minecraft music, not a guitar
 * recording of the mod's own — no music ships in the JAR any more, only the player's folder does ({@link CompanionMusicLibrary}).
 * The evening picks one of these discs at random, the client plays it from where he sits as it plays a folder recording, and its
 * length is the music's, so the evening goes on to the next one by itself. Nothing is said about it in the chat: the evening simply
 * sounds, and «Jukebox/Note Blocks» controls the volume.
 */
public final class CompanionTunes {
    /**
     * One vanilla recording: the sound event the client resolves, the title he gives it, its length in ticks and whether it is a
     * cheerful one (he smiles to it). The length is a little shorter than the music, so a tune is not cut off in the middle.
     */
    public record Tune(String soundId, String title, int ticks, boolean cheerful) { }

    /** Music discs, the vanilla music that sounds from a place: mono, positional, and on the «Jukebox/Note Blocks» volume. */
    public static final List<Tune> ALL = List.of(
            new Tune("minecraft:music_disc.cat", "Cat", 3620, true),
            new Tune("minecraft:music_disc.mellohi", "Mellohi", 1880, true),
            new Tune("minecraft:music_disc.stal", "Stal", 2940, false),
            new Tune("minecraft:music_disc.mall", "Mall", 3860, false),
            new Tune("minecraft:music_disc.chirp", "Chirp", 3620, false),
            new Tune("minecraft:music_disc.far", "Far", 3400, false));

    private CompanionTunes() { }
}

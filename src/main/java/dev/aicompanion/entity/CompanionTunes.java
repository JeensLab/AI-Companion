package dev.aicompanion.entity;

import java.util.List;

/**
 * The two recordings that ship in the mod (the owner's ruling, 13.11: only a couple of standard ones stay inside the JAR, everything
 * else is the player's own folder — {@link CompanionMusicLibrary}). They are the fallback while that folder is empty, so an evening by
 * the fire still sounds. Both are free to share — CC0 and CC BY — credited in assets/aicompanion/sounds/guitar/CREDITS.md, mono Ogg
 * Vorbis at assets/aicompanion/sounds/guitar/{id}.ogg, re-encoded to about a third less than before; «Jukebox/Note Blocks» controls
 * the volume.
 */
public final class CompanionTunes {
    /** One recording: its id, the title he gives it, its length in ticks, and whether it is a cheerful one (he smiles to it). */
    public record Tune(String id, String title, int ticks, boolean cheerful) { }

    public static final List<Tune> ALL = List.of(
            new Tune("romance", "Испанский романс", 3336, false),
            new Tune("gavotte", "Гавот Пахельбеля", 974, true));

    private CompanionTunes() { }
}

package dev.aicompanion.entity;

import java.util.List;

/**
 * What he plays by the fire: real recordings of solo acoustic (classical) guitar, instrumental only, free to share — public domain,
 * CC0, CC BY or CC BY-SA, every one credited in assets/aicompanion/sounds/guitar/CREDITS.md (the owner asked, 09.11: the note-block
 * guitar did not sound like an acoustic one, and the tunes sounded alike). Each is a mono Ogg Vorbis file at
 * assets/aicompanion/sounds/guitar/{id}.ogg, so the client plays it from where he sits; the «Jukebox/Note Blocks» volume controls it.
 */
public final class CompanionTunes {
    /** One recording: its sound id, the title he gives it, its length in ticks, and whether it is a cheerful one (he smiles to it). */
    public record Tune(String id, String title, int ticks, boolean cheerful) { }

    public static final List<Tune> ALL = List.of(
            new Tune("romance", "Испанский романс", 3336, false),
            new Tune("noi", "Эль Ной де ла Маре", 2645, false),
            new Tune("recuerdos", "Воспоминание об Альгамбре", 4779, false),
            new Tune("gavotte", "Гавот Пахельбеля", 974, true),
            new Tune("fugue143", "Фуга Пахельбеля, четвёртая", 1647, true),
            new Tune("fugue144", "Фуга Пахельбеля, пятая", 1817, true),
            new Tune("canon", "Канон Пахельбеля", 3821, true));

    private CompanionTunes() { }
}

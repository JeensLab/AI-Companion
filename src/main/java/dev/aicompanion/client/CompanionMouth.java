package dev.aicompanion.client;

import java.util.UUID;

/**
 * Whether Oleg's mouth is open while his voice plays: open on the loud ticks, closed in the pauses and when the voice ends.
 * Pure (no game classes), so the rules are tested on the GameTest server. Client thread only.
 */
public final class CompanionMouth {
    /** The open-mouth patch on the 1254 px face atlas; oleg_mouth.png holds one patch of this size per expression, in enum order. */
    public static final int BOX_LEFT = 232, BOX_TOP = 314, BOX_RIGHT = 340, BOX_BOTTOM = 358;
    // Quieter than this share of the loudest tick of the clip is a pause, not a syllable.
    private static final float LOUD = .25F;
    // A syllable is louder than the ticks around it: in a phrase without pauses the level dips only a little between them.
    // Tuned on Windows' Russian voice: about 3.4 openings a second, 2-3 ticks each.
    private static final int AROUND = 2;
    // A clip whose loudest tick is below this (about -40 dB) is silence, not quiet speech.
    private static final float SILENCE = .01F;

    private static UUID speaker;
    private static float[] voice;
    private static int ticks;

    private CompanionMouth() { }

    /** Level of every 1/20 s of 16-bit little-endian mono PCM, as a share of the loudest one. */
    public static float[] loudness(byte[] pcm, float sampleRate) {
        int perTick = Math.max(1, Math.round(sampleRate / 20)), samples = pcm.length / 2;
        float[] levels = new float[(samples + perTick - 1) / perTick];
        float loudest = 0;
        for (int tick = 0; tick < levels.length; tick++) {
            int from = tick * perTick, to = Math.min(samples, from + perTick);
            double sum = 0;
            for (int i = from; i < to; i++) {
                int sample = (short) ((pcm[2 * i] & 255) | pcm[2 * i + 1] << 8);
                sum += (double) sample * sample;
            }
            levels[tick] = (float) (Math.sqrt(sum / (to - from)) / 32768);
            loudest = Math.max(loudest, levels[tick]);
        }
        for (int tick = 0; tick < levels.length; tick++) levels[tick] = loudest < SILENCE ? 0 : levels[tick] / loudest;
        return levels;
    }

    public static boolean voiceOpen(float[] loudness, float tick) {
        int at = (int) tick;
        if (tick < 0 || at >= loudness.length || loudness[at] < LOUD) return false;
        int from = Math.max(0, at - AROUND), to = Math.min(loudness.length, at + AROUND + 1);
        float sum = 0;
        for (int i = from; i < to; i++) sum += loudness[i];
        return loudness[at] * (to - from) >= sum;
    }

    /** The voice that has just started playing replaces the one before. */
    public static void voice(UUID who, float[] loudness) {
        speaker = who; voice = loudness; ticks = 0;
    }

    public static void stop() {
        speaker = null; voice = null;
    }

    /** One tick of the voice; not called while the game is paused, as the voice does not play then either. */
    public static void tick() {
        if (speaker != null && ++ticks >= voice.length) stop();
    }

    public static boolean open(UUID who, float partialTick) {
        return speaker != null && speaker.equals(who) && voiceOpen(voice, ticks + partialTick);
    }
}

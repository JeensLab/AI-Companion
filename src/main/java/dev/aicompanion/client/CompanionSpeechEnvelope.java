package dev.aicompanion.client;

/** Local PCM energy in ten-millisecond windows, indexed by the audio device's sample cursor. */
public final class CompanionSpeechEnvelope {
    private final float[] levels;
    private final int window, frames;

    public CompanionSpeechEnvelope(VoicePcm.Pcm pcm) {
        window = Math.max(1, Math.round(pcm.format().getSampleRate() / 100));
        frames = pcm.bytes().length / 2;
        levels = new float[(frames + window - 1) / window];
        for (int i = 0; i < levels.length; i++) {
            double energy = 0;
            int end = Math.min(frames, (i + 1) * window);
            for (int frame = i * window; frame < end; frame++) {
                int offset = frame * 2;
                short sample = (short)((pcm.bytes()[offset] & 255) | (pcm.bytes()[offset + 1] << 8));
                double amplitude = sample / 32768.0;
                energy += amplitude * amplitude;
            }
            double rms = Math.sqrt(energy / (end - i * window));
            levels[i] = (float)Math.min(1, Math.max(0, (rms - .012) / .18));
        }
    }

    public float atSample(long sample) {
        if (sample < 0 || sample >= frames) return 0;
        int index = (int)(sample / window);
        float next = index + 1 < levels.length ? levels[index + 1] : 0;
        float fraction = (sample % window) / (float)window;
        return levels[index] + (next - levels[index]) * fraction;
    }

    public static float smooth(float previous, float target, double seconds) {
        double time = target > previous ? .025 : .045;
        float value = previous + (target - previous) * (float)(1 - Math.exp(-Math.max(0, seconds) / time));
        return value < .005F ? 0 : value;
    }
}

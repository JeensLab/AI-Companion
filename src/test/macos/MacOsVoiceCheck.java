import dev.aicompanion.client.MacOsVoice;
import dev.aicompanion.client.MacOsVoiceHint;
import dev.aicompanion.client.SpeechEngine;
import dev.aicompanion.client.VoicePcm;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Pure regression checks, with an explicit optional offline synthesis probe on a real Mac. */
public class MacOsVoiceCheck {
    public static void main(String[] args) throws Exception {
        check(SpeechEngine.choose("key", true, false, true) == SpeechEngine.INWORLD, "Inworld priority");
        check(SpeechEngine.choose(" ", true, false, true) == SpeechEngine.MACOS, "macOS without key");
        check(SpeechEngine.choose("", false, false, true) == SpeechEngine.NONE, "macOS switch");
        check(SpeechEngine.choose("", true, true) == SpeechEngine.WINDOWS, "Windows compatibility");
        check(SpeechEngine.choose("", true, false, false) == SpeechEngine.NONE, "Other platforms");
        String english = "Alex en_US # Hello\n";
        check(MacOsVoice.chooseVoice(english).isEmpty(), "English substitute");
        check(MacOsVoice.chooseVoice(english + "Milena ru_RU # Привет\n").orElseThrow().equals("Milena"), "Russian selection");
        check(MacOsVoice.chooseVoice("Milena ru_RU # Привет\nYuri ru_RU # Привет\n").orElseThrow().equals("Yuri"), "Yuri preference");
        check(MacOsVoice.chooseVoice("Milena (Enhanced) ru_RU # Привет\n").orElseThrow().equals("Milena (Enhanced)"), "Multiword name");
        var hint = new MacOsVoiceHint();
        check(hint.take().contains("Inworld") && hint.take() == null, "One-time explanation");
        check("no_russian_voice".equals(MacOsVoice.unavailableReason(new CompletionException(new MacOsVoice.Unavailable("no_russian_voice")))), "Wrapped failure");
        check(MacOsVoice.unavailableReason(new IllegalStateException("Unexpected")) == null, "Unexpected errors hidden");
        System.out.println("All 12 local voice checks passed");
        if (args.length == 0 || !args[0].equals("--live")) return;
        byte[] wav = MacOsVoice.synthesize("Привет, я Олег. Сейчас нарублю дров и вернусь.").get(55, TimeUnit.SECONDS);
        var pcm = VoicePcm.decode(wav);
        check(pcm.format().getChannels() == 1 && pcm.format().getSampleRate() == 22050 && pcm.format().getSampleSizeInBits() == 16, "Audio format");
        double seconds = pcm.bytes().length / (22050.0 * 2);
        check(seconds > 1.5 && seconds < 15, "Speech duration");
        if (args.length > 1) java.nio.file.Files.write(java.nio.file.Path.of(args[1]), wav);
        System.out.println("Offline macOS synthesis passed: " + wav.length + " bytes, " + seconds + " seconds, PCM 22050 Hz mono 16-bit");
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.MacOsVoice;
import dev.aicompanion.client.MacOsVoiceHint;
import dev.aicompanion.client.SpeechEngine;
import dev.aicompanion.client.VoicePcm;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.concurrent.CompletionException;

/** No cloud calls: installed macOS speech and platform-independent routing rules. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMacOsVoiceTests {
    @GameTest(template = "empty")
    public static void macosVoiceNeedsNoKeyButRespectsTheSwitch(GameTestHelper h) {
        h.assertTrue(SpeechEngine.choose("", true, false, true) == SpeechEngine.MACOS, "No local voice on macOS");
        h.assertTrue(SpeechEngine.choose(" ", true, false, true) == SpeechEngine.MACOS, "Blank key selected Inworld");
        h.assertTrue(SpeechEngine.choose("", false, false, true) == SpeechEngine.NONE, "Disabled local voice spoke");
        h.assertTrue(SpeechEngine.choose("key", true, false, true) == SpeechEngine.INWORLD, "Local voice replaced Inworld");
        h.assertTrue(SpeechEngine.choose("key", false, false, true) == SpeechEngine.INWORLD, "Local switch disabled Inworld");
        h.assertTrue(SpeechEngine.choose("", true, false, false) == SpeechEngine.NONE, "Local voice selected on Linux");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void installedRussianVoicesAreSelectedWithoutEnglishFallback(GameTestHelper h) {
        h.assertTrue(MacOsVoice.chooseVoice("Alex en_US # Hello\n").isEmpty(), "English substitute selected");
        h.assertTrue(MacOsVoice.chooseVoice("Milena ru_RU # Привет\n").orElseThrow().equals("Milena"), "Russian voice missed");
        h.assertTrue(MacOsVoice.chooseVoice("Milena ru_RU # Привет\nYuri ru_RU # Привет\n").orElseThrow().equals("Yuri"), "Yuri preference lost");
        h.assertTrue(MacOsVoice.chooseVoice("Milena (Enhanced) ru_RU # Привет\n").orElseThrow().equals("Milena (Enhanced)"), "Voice name truncated");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void missingVoiceExplainsItselfOnlyOnce(GameTestHelper h) {
        var hint = new MacOsVoiceHint();
        String first = hint.take();
        h.assertTrue(first != null && first.contains("русск") && first.contains("Inworld"), "No useful missing voice hint");
        h.assertTrue(hint.take() == null && hint.take() == null, "Hint repeated");
        h.assertTrue("no_russian_voice".equals(MacOsVoice.unavailableReason(new CompletionException(
                new MacOsVoice.Unavailable("no_russian_voice")))), "Wrapped unavailable voice not recognized");
        h.assertTrue(MacOsVoice.unavailableReason(new IllegalStateException("unexpected")) == null, "Unexpected failure hidden");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20000)
    public static void installedMacosVoiceProducesAudioOrExplainsUnavailability(GameTestHelper h) {
        var speech = MacOsVoice.synthesize("Привет, я Олег. Сейчас нарублю дров и вернусь.");
        h.succeedWhen(() -> {
            h.assertTrue(speech.isDone(), "Speech has not completed");
            String unavailable = speech.handle((audio, error) -> error == null ? null : MacOsVoice.unavailableReason(error)).join();
            if (!MacOsVoice.macos()) {
                h.assertTrue("not_macos".equals(unavailable), "Wrong platform not recognized");
                return;
            }
            if (unavailable != null) {
                h.assertTrue(unavailable.equals("no_russian_voice"), "Installed speech failed: " + unavailable);
                return;
            }
            try {
                var pcm = VoicePcm.decode(speech.join());
                h.assertTrue(pcm.format().getChannels() == 1 && pcm.format().getSampleSizeInBits() == 16
                        && pcm.format().getSampleRate() == 22050, "Wrong speech format");
                double seconds = pcm.bytes().length / (22050.0 * 2);
                h.assertTrue(seconds > 1.5 && seconds < 15, "Wrong speech duration: " + seconds);
            } catch (Exception error) { throw new AssertionError("Audio could not be played", error); }
        });
    }
}

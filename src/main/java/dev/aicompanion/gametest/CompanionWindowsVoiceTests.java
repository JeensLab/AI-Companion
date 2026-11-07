package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.SpeechEngine;
import dev.aicompanion.client.VoicePcm;
import dev.aicompanion.client.WindowsVoice;
import dev.aicompanion.client.WindowsVoiceChoice;
import dev.aicompanion.client.WindowsVoiceChoice.Candidate;
import dev.aicompanion.client.WindowsVoiceHint;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.concurrent.CompletionException;

/** Oleg's voice without an Inworld key: the system voice of Windows. Never calls cloud APIs. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWindowsVoiceTests {
    private static final Candidate ZIRA = new Candidate("TTS_MS_EN-US_ZIRA_11.0", "409", "Female", false);
    private static final Candidate IRINA_DESKTOP = new Candidate("TTS_MS_RU-RU_IRINA_11.0", "419", "Female", false);
    private static final Candidate IRINA = new Candidate("MSTTS_V110_ruRU_IrinaM", "419", "Female", true);
    private static final Candidate PAVEL = new Candidate("MSTTS_V110_ruRU_PavelM", "419", "Male", true);

    @GameTest(template = "empty")
    public static void heSpeaksWithARussianMaleVoiceFirstAndNeverWithAnEnglishOne(GameTestHelper h) {
        h.assertTrue(WindowsVoiceChoice.pick(List.of(ZIRA, IRINA_DESKTOP, IRINA, PAVEL)).orElseThrow() == PAVEL,
                "The Russian male voice was not taken first");
        Candidate russian = WindowsVoiceChoice.pick(List.of(ZIRA, IRINA_DESKTOP, IRINA)).orElse(null);
        h.assertTrue(russian == IRINA_DESKTOP || russian == IRINA, "Without a male voice a Russian one was not taken: " + russian);
        h.assertTrue(WindowsVoiceChoice.pick(List.of(ZIRA)).isEmpty(), "An English voice was taken to read Russian");
        h.assertTrue(WindowsVoiceChoice.pick(List.of()).isEmpty(), "A voice was taken out of none");
        // Language is a list of hex LCIDs; Gender comes in any case.
        Candidate bilingual = new Candidate("bilingual", "409;419", "Female", false), padded = new Candidate("padded", "0419", "Female", false);
        h.assertTrue(WindowsVoiceChoice.pick(List.of(ZIRA, bilingual)).orElse(null) == bilingual, "A voice listing Russian among its languages was not taken");
        h.assertTrue(WindowsVoiceChoice.pick(List.of(ZIRA, padded)).orElse(null) == padded, "Russian written as 0419 was not taken");
        Candidate lowerMale = new Candidate("male", "419", "male", true);
        h.assertTrue(WindowsVoiceChoice.pick(List.of(IRINA, lowerMale)).orElse(null) == lowerMale, "A male voice written in lower case was not preferred");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyAMissingWindowsVoiceCountsAsSilenceNotAnyOtherError(GameTestHelper h) {
        var silent = new WindowsVoice.Silent("no_russian_voice");
        h.assertTrue("no_russian_voice".equals(WindowsVoice.silentReason(silent)), "A bare silence was not read");
        h.assertTrue("no_russian_voice".equals(WindowsVoice.silentReason(new CompletionException(silent))), "A wrapped silence was not read");
        h.assertTrue(WindowsVoice.silentReason(new CompletionException(new IllegalStateException("COM failed"))) == null, "A real error was taken for silence");
        h.assertTrue(WindowsVoice.silentReason(new CompletionException("no cause", null)) == null, "An error without a cause was taken for silence");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theWindowsVoiceSpeaksOnlyWithoutAnInworldKeyAndOnlyOnWindows(GameTestHelper h) {
        h.assertTrue(SpeechEngine.choose("key", true, true) == SpeechEngine.INWORLD, "A set Inworld key did not keep Inworld");
        h.assertTrue(SpeechEngine.choose("key", false, false) == SpeechEngine.INWORLD, "Inworld depended on the Windows voice setting");
        h.assertTrue(SpeechEngine.choose("", true, true) == SpeechEngine.WINDOWS, "No key on Windows: the Windows voice was not used");
        h.assertTrue(SpeechEngine.choose("   ", true, true) == SpeechEngine.WINDOWS, "A blank key was taken for a key");
        h.assertTrue(SpeechEngine.choose("", false, true) == SpeechEngine.NONE, "The Windows voice spoke while turned off");
        h.assertTrue(SpeechEngine.choose("", true, false) == SpeechEngine.NONE, "The Windows voice was used off Windows");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void withoutARussianVoiceHeSaysHowToGetOneOnlyOnce(GameTestHelper h) {
        var hint = new WindowsVoiceHint();
        String first = hint.take();
        h.assertTrue(first != null && first.contains("русск") && !first.contains("\n"), "No one-line hint about a Russian voice: " + first);
        h.assertTrue(hint.take() == null && hint.take() == null, "The hint came again in the same game");
        h.succeed();
    }

    /**
     * The real Windows voice: a line becomes a 22 kHz mono 16-bit WAV for the same player as Inworld's; off Windows, it says why
     * not. What Windows has installed is read independently, from the registry, not through the code under test.
     */
    // The test server ticks without pause: the budget is in ticks, the synthesis takes wall time (about a second).
    @GameTest(template = "empty", timeoutTicks = 20000)
    public static void theWindowsVoiceTurnsALineIntoSpeechOrSaysWhyNot(GameTestHelper h) {
        var voices = WindowsVoice.voices();
        var speech = WindowsVoice.synthesize("Привет, я Олег. Сейчас нарублю дров и вернусь.");
        h.succeedWhen(() -> {
            h.assertTrue(voices.isDone() && speech.isDone(), "The Windows voice has not answered yet");
            String silent = speech.handle((wav, error) -> error == null ? null
                    : WindowsVoice.silentReason(error) != null ? WindowsVoice.silentReason(error) : error.toString()).join();
            if (!WindowsVoice.windows()) {
                h.assertTrue("not_windows".equals(silent), "Off Windows it did not say so: " + silent);
                return;
            }
            List<String> installed = registryVoiceLanguages();
            h.assertTrue(voices.join().size() >= installed.size(), "Voices were lost: SAPI listed " + voices.join().size() + " of " + installed.size());
            // Russian is the LCID 0x419, written as 419 or 0419, alone or among other languages.
            boolean russian = installed.stream().flatMap(language -> java.util.Arrays.stream(language.split(";")))
                    .anyMatch(code -> code.trim().matches("0*419"));
            if (!russian) {
                h.assertTrue("no_russian_voice".equals(silent), "Without a Russian voice it did not say so: " + silent);
                return;
            }
            h.assertTrue(silent == null, "A Russian voice is installed, yet no speech: " + silent);
            javax.sound.sampled.AudioFormat format;
            long frames;
            try {
                var file = javax.sound.sampled.AudioSystem.getAudioFileFormat(new java.io.ByteArrayInputStream(speech.join()));
                format = file.getFormat(); frames = file.getFrameLength();
            } catch (Exception error) { throw new GameTestAssertException("The speech is not a WAV: " + error); }
            h.assertTrue(format.getChannels() == 1 && format.getSampleSizeInBits() == 16 && format.getSampleRate() == 22050,
                    "Not the 22 kHz mono 16-bit asked for: " + format);
            float seconds = frames / format.getSampleRate();
            h.assertTrue(seconds > 1.5f && seconds < 15f, "A short line gave " + seconds + " s of speech");
            // The player's own decoder gets every frame the header promises: the stream was written to its end.
            long decoded;
            try { decoded = VoicePcm.decode(speech.join()).bytes().length / 2; }
            catch (Exception error) { throw new GameTestAssertException("The player cannot decode the speech: " + error); }
            h.assertTrue(decoded == frames, "The header promises " + frames + " frames, the player decodes " + decoded);
        });
    }

    /** The Language attribute of every voice token in the registry, OneCore and desktop. */
    private static List<String> registryVoiceLanguages() {
        var machine = com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE;
        List<String> languages = new java.util.ArrayList<>();
        for (String root : List.of("SOFTWARE\\Microsoft\\Speech_OneCore\\Voices\\Tokens", "SOFTWARE\\Microsoft\\Speech\\Voices\\Tokens")) {
            if (!com.sun.jna.platform.win32.Advapi32Util.registryKeyExists(machine, root)) continue;
            for (String id : com.sun.jna.platform.win32.Advapi32Util.registryGetKeys(machine, root)) {
                String attributes = root + "\\" + id + "\\Attributes";
                languages.add(com.sun.jna.platform.win32.Advapi32Util.registryValueExists(machine, attributes, "Language")
                        ? com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(machine, attributes, "Language") : "");
            }
        }
        return languages;
    }
}

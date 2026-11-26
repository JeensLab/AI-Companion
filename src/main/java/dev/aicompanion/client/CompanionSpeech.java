package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.CompanionDiagnostics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Routes cloud speech and plays replies through the shared positional audio queue. */
public final class CompanionSpeech {
    private static final WindowsVoiceHint WINDOWS_VOICE_HINT = new WindowsVoiceHint();
    private static final MacOsVoiceHint MACOS_VOICE_HINT = new MacOsVoiceHint();
    private static CompletableFuture<Void> playbackQueue = CompletableFuture.completedFuture(null);

    private CompanionSpeech() {
    }
    static synchronized boolean isSpeaking() { return !playbackQueue.isDone(); }
    public static synchronized void resetPlaybackQueue() { playbackQueue = CompletableFuture.completedFuture(null); }

    static CompletableFuture<String> transcribe(byte[] audio, SpeechProvider provider, String key) {
        if (key.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Добавь ключ " + provider.displayName() + " в настройках мода."));
        }
        if (audio.length < 1600) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Запись слишком короткая. Удерживай клавишу микрофона, пока говоришь."));
        }
        return switch (provider) {
            case INWORLD -> InworldSttClient.transcribe(audio, key);
            case ELEVENLABS -> ElevenLabsVoiceClient.transcribe(audio, key);
        };
    }

    public static synchronized CompletableFuture<Void> speak(String text, String trace) {
        return speak(text, trace, false);
    }

    static synchronized CompletableFuture<Void> speakJoke(String text, String trace) {
        return speak(text, trace, true);
    }

    private static CompletableFuture<Void> speak(String text, String trace, boolean joke) {
        if (text == null || text.isBlank()) return CompletableFuture.completedFuture(null);
        SpeechProvider provider = OpenRouterConfig.SPEECH_PROVIDER.get();
        String key = OpenRouterConfig.speechKey(provider).get().trim();
        boolean macos = MacOsVoice.macos();
        boolean localVoiceOn = macos ? OpenRouterConfig.MACOS_VOICE.get() : OpenRouterConfig.WINDOWS_VOICE.get();
        SpeechEngine engine = SpeechEngine.choose(provider, key, localVoiceOn, WindowsVoice.windows(), macos);
        if (engine == SpeechEngine.NONE) {
            CompanionDiagnostics.event(trace, "speech_skipped", "reason", "no_speech_provider_key", "provider", provider.displayName());
            return CompletableFuture.completedFuture(null);
        }
        var speaker = CompanionVoiceAudio.speaker();
        if (!CompanionVoiceAudio.audible(speaker)) {
            CompanionDiagnostics.event(trace, "speech_skipped", "reason", "speaker_missing_or_outside_hearing_range");
            return CompletableFuture.completedFuture(null);
        }
        var speakerId = speaker.getUUID(); Object world = speaker.level();
        String line = text.length() > 1800 ? text.substring(0, 1800) : text;
        CompanionDiagnostics.event(trace, "speech_requested", "characters", text.length(), "speaker", speakerId,
                "engine", engine.name().toLowerCase(Locale.ROOT));
        String voiceId = OpenRouterConfig.speechVoiceId(provider).get().trim();
        CompletableFuture<byte[]> synthesis;
        if (joke) {
            String intro = "Внимание, анекдот. ";
            var announcement = synthesize(engine, intro.strip(), key, voiceId, trace);
            var story = synthesize(engine, line.substring(intro.length()), key, voiceId, trace);
            synthesis = announcement.thenCombine(story, (first, second) -> {
                try { return VoicePcm.joinWithPause(first, second); }
                catch (Exception error) { throw new CompletionException(error); }
            });
        } else synthesis = synthesize(engine, line, key, voiceId, trace);
        // Reserve the playback position now, so a faster later synthesis cannot overtake this reply.
        playbackQueue = playbackQueue.handle((ignored, error) -> null)
                .thenCompose(ignored -> synthesis.thenCompose(wav -> {
                    CompanionDiagnostics.event(trace, "speech_playback_queued", "audioBytes", wav.length);
                    return CompanionVoiceAudio.play(wav, speakerId, world);
                })).handle((unused, error) -> {
                    String silent = null;
                    if (error != null) {
                        silent = engine == SpeechEngine.MACOS
                                ? MacOsVoice.unavailableReason(error) : WindowsVoice.silentReason(error);
                    }
                    if (error == null) CompanionDiagnostics.event(trace, "speech_playback_finished");
                    else if (silent == null) {
                        CompanionDiagnostics.failure(trace, "speech_synthesis_or_playback", error);
                        throw error instanceof CompletionException completion ? completion : new CompletionException(error);
                    } else {
                        // A missing local voice must explain itself once rather than fail silently.
                        CompanionDiagnostics.event(trace, "speech_skipped", "reason", silent);
                        String hint = engine == SpeechEngine.MACOS ? MACOS_VOICE_HINT.take() : WINDOWS_VOICE_HINT.take();
                        if (hint != null) Minecraft.getInstance().execute(() ->
                                Minecraft.getInstance().gui.getChat().addMessage(Component.literal(hint).withStyle(ChatFormatting.GRAY)));
                    }
                    return null;
                });
        return playbackQueue;
    }

    private static CompletableFuture<byte[]> synthesize(SpeechEngine engine, String line, String key, String voiceId, String trace) {
        // A local voice that hangs must not hold the queue: use the cloud request timeout.
        return switch (engine) {
            case WINDOWS -> WindowsVoice.synthesize(line).orTimeout(60, TimeUnit.SECONDS);
            case MACOS -> MacOsVoice.synthesize(line).orTimeout(60, TimeUnit.SECONDS);
            case INWORLD -> InworldTtsClient.synthesize(line, key, voiceId, trace);
            case ELEVENLABS -> ElevenLabsVoiceClient.synthesize(line, key, voiceId, trace);
            case NONE -> throw new IllegalStateException("Speech is disabled");
        };
    }
}

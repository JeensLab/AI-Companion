package dev.aicompanion.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.CompanionDiagnostics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Synthesizes companion replies with Inworld (without its key, with the Windows voice) and plays the WAV audio. */
public final class InworldTtsClient {
    private static final URI SYNTHESIZE = URI.create("https://api.inworld.ai/tts/v1/voice");
    private static final WindowsVoiceHint WINDOWS_VOICE_HINT = new WindowsVoiceHint();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static CompletableFuture<Void> playbackQueue = CompletableFuture.completedFuture(null);

    private InworldTtsClient() {
    }
    static synchronized boolean isSpeaking() { return !playbackQueue.isDone(); }
    public static synchronized void resetPlaybackQueue() { playbackQueue = CompletableFuture.completedFuture(null); }

    public static synchronized CompletableFuture<Void> speak(String text) {
        return speak(text, CompanionDiagnostics.newTrace());
    }

    public static synchronized CompletableFuture<Void> speak(String text, String trace) {
        String credential = OpenRouterConfig.INWORLD_API_KEY.get().trim();
        SpeechEngine engine = SpeechEngine.choose(credential, OpenRouterConfig.WINDOWS_VOICE.get(), WindowsVoice.windows());
        if (engine == SpeechEngine.NONE) {
            CompanionDiagnostics.event(trace, "speech_skipped", "reason", "no_inworld_credential");
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
        // A Windows voice that hangs must not hold the queue: the same 60 s as an Inworld request.
        CompletableFuture<byte[]> synthesis = engine == SpeechEngine.WINDOWS
                ? WindowsVoice.synthesize(line).orTimeout(60, TimeUnit.SECONDS) : inworld(line, credential, trace);
        // Reserve the playback position now, so a faster later synthesis cannot overtake this reply.
        playbackQueue = playbackQueue.handle((ignored, error) -> null)
                .thenCompose(ignored -> synthesis.thenCompose(wav -> {
                    CompanionDiagnostics.event(trace, "speech_playback_queued", "audioBytes", wav.length);
                    return CompanionVoiceAudio.play(wav, speakerId, world);
                })).handle((unused, error) -> {
                    String silent = error == null ? null : WindowsVoice.silentReason(error);
                    if (error == null) CompanionDiagnostics.event(trace, "speech_playback_finished");
                    else if (silent == null) {
                        CompanionDiagnostics.failure(trace, "speech_synthesis_or_playback", error);
                        throw error instanceof CompletionException completion ? completion : new CompletionException(error);
                    } else {
                        // Windows has no Russian voice: silence as without a key, and one line on how to get one.
                        CompanionDiagnostics.event(trace, "speech_skipped", "reason", silent);
                        String hint = WINDOWS_VOICE_HINT.take();
                        if (hint != null) Minecraft.getInstance().execute(() ->
                                Minecraft.getInstance().gui.getChat().addMessage(Component.literal(hint).withStyle(ChatFormatting.GRAY)));
                    }
                    return null;
                });
        return playbackQueue;
    }

    private static CompletableFuture<byte[]> inworld(String line, String credential, String trace) {
        JsonObject body = new JsonObject();
        body.addProperty("text", line);
        body.addProperty("voiceId", OpenRouterConfig.INWORLD_VOICE_ID.get().trim().isEmpty()
                ? "Ashley" : OpenRouterConfig.INWORLD_VOICE_ID.get().trim());
        body.addProperty("modelId", "inworld-tts-2");
        body.addProperty("language", "ru-RU");
        JsonObject audioConfig = new JsonObject();
        audioConfig.addProperty("audioEncoding", "LINEAR16");
        body.add("audioConfig", audioConfig);

        HttpRequest request = HttpRequest.newBuilder(SYNTHESIZE)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Basic " + credential)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    CompanionDiagnostics.event(trace, "http_result", "service", "inworld_tts", "status", response.statusCode());
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Inworld TTS returned HTTP " + response.statusCode());
                    }
                    JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                    return Base64.getDecoder().decode(result.get("audioContent").getAsString());
                });
    }

}

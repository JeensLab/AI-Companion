package dev.aicompanion.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.CompanionDiagnostics;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/** Synthesizes companion replies with Inworld and plays the returned WAV audio. */
public final class InworldTtsClient {
    private static final URI SYNTHESIZE = URI.create("https://api.inworld.ai/tts/v1/voice");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static CompletableFuture<Void> playbackQueue = CompletableFuture.completedFuture(null);

    private InworldTtsClient() {
    }
    public static synchronized void resetPlaybackQueue() { playbackQueue = CompletableFuture.completedFuture(null); }

    public static synchronized CompletableFuture<Void> speak(String text) {
        return speak(text, CompanionDiagnostics.newTrace());
    }

    public static synchronized CompletableFuture<Void> speak(String text, String trace) {
        String credential = OpenRouterConfig.INWORLD_API_KEY.get().trim();
        if (credential.isEmpty()) {
            CompanionDiagnostics.event(trace, "speech_skipped", "reason", "no_inworld_credential");
            return CompletableFuture.completedFuture(null);
        }
        var speaker = CompanionVoiceAudio.speaker();
        if (!CompanionVoiceAudio.audible(speaker)) {
            CompanionDiagnostics.event(trace, "speech_skipped", "reason", "speaker_missing_or_outside_hearing_range");
            return CompletableFuture.completedFuture(null);
        }
        var speakerId = speaker.getUUID(); Object world = speaker.level();

        JsonObject body = new JsonObject();
        body.addProperty("text", text.length() > 1800 ? text.substring(0, 1800) : text);
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

        CompanionDiagnostics.event(trace, "speech_requested", "characters", text.length(), "speaker", speakerId);
        CompletableFuture<byte[]> synthesis = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    CompanionDiagnostics.event(trace, "http_result", "service", "inworld_tts", "status", response.statusCode());
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Inworld TTS returned HTTP " + response.statusCode());
                    }
                    JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                    return Base64.getDecoder().decode(result.get("audioContent").getAsString());
                });
        // Reserve the playback position now, so a faster later HTTP response cannot overtake this reply.
        playbackQueue = playbackQueue.handle((ignored, error) -> null)
                .thenCompose(ignored -> synthesis.thenCompose(wav -> {
                    CompanionDiagnostics.event(trace, "speech_playback_queued", "audioBytes", wav.length);
                    return CompanionVoiceAudio.play(wav, speakerId, world);
                })).whenComplete((unused, error) -> {
                    if (error != null) CompanionDiagnostics.failure(trace, "speech_synthesis_or_playback", error);
                    else CompanionDiagnostics.event(trace, "speech_playback_finished");
                });
        return playbackQueue;
    }

}

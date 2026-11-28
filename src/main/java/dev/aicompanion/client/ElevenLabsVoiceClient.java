package dev.aicompanion.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.aicompanion.CompanionDiagnostics;

import javax.sound.sampled.AudioFormat;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** ElevenLabs REST adapter for captured PCM and Russian companion replies. */
final class ElevenLabsVoiceClient {
    private static final String API = "https://api.elevenlabs.io/v1/";
    private static final AudioFormat VOICE_FORMAT = new AudioFormat(24_000, 16, 1, true, false);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private ElevenLabsVoiceClient() { }

    static CompletableFuture<String> transcribe(byte[] audio, String key) {
        if (!validKey(key)) return CompletableFuture.failedFuture(invalidKey());
        return HTTP.sendAsync(transcriptionRequest(audio, key), HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            requireSuccess(response, "распознавание речи");
            return transcript(response.body());
        });
    }

    static HttpRequest transcriptionRequest(byte[] audio, String key) {
        String boundary = UUID.randomUUID().toString();
        String headers = field(boundary, "model_id", "scribe_v3")
                + field(boundary, "language_code", "rus")
                + field(boundary, "numbers_format", "digits")
                + field(boundary, "file_format", "pcm_s16le_16")
                + field(boundary, "timestamps_granularity", "none")
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"recording.pcm\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        int frameSize = MicrophoneRecorder.FORMAT.getFrameSize();
        return request("speech-to-text", key)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.concat(BodyPublishers.ofString(headers),
                        BodyPublishers.ofByteArray(audio, 0, audio.length - audio.length % frameSize),
                        BodyPublishers.ofString("\r\n--" + boundary + "--\r\n")))
                .build();
    }

    static CompletableFuture<byte[]> synthesize(String text, String key, String voiceId, String trace) {
        if (!validKey(key)) return CompletableFuture.failedFuture(invalidKey());
        if (!voiceId.matches("[A-Za-z0-9_-]+")) {
            return CompletableFuture.failedFuture(new IllegalStateException("Укажи ID голоса ElevenLabs в настройках мода."));
        }
        return HTTP.sendAsync(synthesisRequest(text, key, voiceId), HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            CompanionDiagnostics.event(trace, "http_result", "service", "elevenlabs_tts", "status", response.statusCode());
            requireSuccess(response, "озвучка");
            return voiceWav(response.body());
        });
    }

    static HttpRequest synthesisRequest(String text, String key, String voiceId) {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("model_id", "eleven_flash_v2_5");
        body.addProperty("language_code", "ru");
        return request("text-to-speech/" + voiceId + "?output_format=pcm_24000", key)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body.toString()))
                .build();
    }

    static String transcript(String json) {
        try {
            var root = JsonParser.parseString(json);
            if (root.isJsonObject()) {
                var text = root.getAsJsonObject().get("text");
                if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                    return text.getAsString().trim();
                }
            }
        } catch (JsonParseException ignored) {
            // Report the response shape without logging the response body.
        }
        throw new IllegalStateException("ElevenLabs вернул некорректный результат распознавания.");
    }

    static byte[] voiceWav(byte[] audio) {
        if (audio.length == 0 || audio.length % VOICE_FORMAT.getFrameSize() != 0) {
            throw new IllegalStateException("ElevenLabs вернул некорректное аудио.");
        }
        return VoicePcm.wrapAsWav(audio, VOICE_FORMAT);
    }

    private static HttpRequest.Builder request(String path, String key) {
        return HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(60))
                .header("xi-api-key", key);
    }

    private static String field(String boundary, String name, String value) {
        return "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n";
    }

    private static boolean validKey(String key) {
        return !key.isEmpty() && key.chars().allMatch(character -> character >= 33 && character <= 126);
    }

    private static IllegalStateException invalidKey() {
        return new IllegalStateException("Проверь ключ ElevenLabs в настройках мода.");
    }

    private static void requireSuccess(HttpResponse<?> response, String operation) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("ElevenLabs, " + operation + " (HTTP " + status + ").");
        }
    }
}

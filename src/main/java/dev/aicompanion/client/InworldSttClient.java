package dev.aicompanion.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aicompanion.OpenRouterConfig;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/** Sends captured LINEAR16 audio to Inworld speech-to-text. */
public final class InworldSttClient {
    private static final URI TRANSCRIBE = URI.create("https://api.inworld.ai/stt/v1/transcribe");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private InworldSttClient() {
    }

    public static CompletableFuture<String> transcribe(byte[] audio) {
        if (audio.length < 1600) {
            return CompletableFuture.failedFuture(new IllegalStateException("Запись слишком короткая. Удерживай V и говори, затем отпусти клавишу."));
        }
        String credential = OpenRouterConfig.INWORLD_API_KEY.get().trim();
        if (credential.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Добавь ключ Inworld в настройках мода."));
        }

        JsonObject config = new JsonObject();
        config.addProperty("modelId", "inworld/inworld-stt-1");
        config.addProperty("audioEncoding", "AUTO_DETECT");
        config.addProperty("language", "ru");
        config.addProperty("sampleRateHertz", 16_000);
        config.addProperty("numberOfChannels", 1);

        JsonObject audioData = new JsonObject();
        audioData.addProperty("content", Base64.getEncoder().encodeToString(wrapAsWav(audio)));
        JsonObject body = new JsonObject();
        body.add("transcribeConfig", config);
        body.add("audioData", audioData);

        HttpRequest request = HttpRequest.newBuilder(TRANSCRIBE)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Basic " + credential)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException(readError(response.body(), response.statusCode()));
                    }
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    return root.getAsJsonObject("transcription").get("transcript").getAsString().trim();
                });
    }

    private static byte[] wrapAsWav(byte[] pcm) {
        int frameSize = MicrophoneRecorder.FORMAT.getFrameSize();
        int completeFramesLength = pcm.length - (pcm.length % frameSize);
        try (AudioInputStream audioStream = new AudioInputStream(
                new ByteArrayInputStream(pcm, 0, completeFramesLength),
                MicrophoneRecorder.FORMAT,
                completeFramesLength / frameSize);
             ByteArrayOutputStream wav = new ByteArrayOutputStream()) {
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, wav);
            return wav.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось подготовить WAV-запись для Inworld.", exception);
        }
    }

    private static String readError(String body, int statusCode) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error")) {
                var error = root.get("error");
                if (error.isJsonObject() && error.getAsJsonObject().has("message")) {
                    return "Inworld STT (HTTP " + statusCode + "): "
                            + error.getAsJsonObject().get("message").getAsString();
                }
                if (error.isJsonPrimitive()) {
                    return "Inworld STT (HTTP " + statusCode + "): " + error.getAsString();
                }
            }
            if (root.has("message")) {
                return "Inworld STT (HTTP " + statusCode + "): " + root.get("message").getAsString();
            }
        } catch (RuntimeException ignored) {
            // The service can return a plain-text error rather than a JSON object.
        }
        String safeBody = body == null ? "" : body.replaceAll("[\\r\\n]+", " ").trim();
        if (safeBody.length() > 240) safeBody = safeBody.substring(0, 240) + "…";
        return safeBody.isEmpty()
                ? "Inworld STT вернул HTTP " + statusCode
                : "Inworld STT (HTTP " + statusCode + "): " + safeBody;
    }
}

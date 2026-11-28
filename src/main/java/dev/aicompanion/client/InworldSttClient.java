package dev.aicompanion.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/** Sends captured LINEAR16 audio to Inworld speech-to-text. */
final class InworldSttClient {
    private static final URI TRANSCRIBE = URI.create("https://api.inworld.ai/stt/v1/transcribe");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private InworldSttClient() {
    }

    static CompletableFuture<String> transcribe(byte[] audio, String credential) {
        JsonObject config = new JsonObject();
        config.addProperty("modelId", "inworld/inworld-stt-1");
        config.addProperty("audioEncoding", "AUTO_DETECT");
        config.addProperty("language", "ru");
        config.addProperty("sampleRateHertz", 16_000);
        config.addProperty("numberOfChannels", 1);

        JsonObject audioData = new JsonObject();
        audioData.addProperty("content", Base64.getEncoder().encodeToString(VoicePcm.wrapAsWav(audio, MicrophoneRecorder.FORMAT)));
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

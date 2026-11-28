package dev.aicompanion.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aicompanion.CompanionDiagnostics;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

final class InworldTtsClient {
    private static final URI SYNTHESIZE = URI.create("https://api.inworld.ai/tts/v1/voice");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private InworldTtsClient() { }

    static CompletableFuture<byte[]> synthesize(String line, String credential, String voiceId, String trace) {
        JsonObject body = new JsonObject();
        body.addProperty("text", line);
        body.addProperty("voiceId", voiceId.isEmpty() ? "Ashley" : voiceId);
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

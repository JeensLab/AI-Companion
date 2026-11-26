package dev.aicompanion.client;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/** Tests the ElevenLabs payloads and audio conversion without contacting either provider. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionElevenLabsVoiceTests {
    @GameTest(template = "empty")
    public static void elevenLabsTranscribesRussianPcm(GameTestHelper h) {
        var request = ElevenLabsVoiceClient.transcriptionRequest(new byte[]{'A', 'B', 'C'}, "test-key");
        h.assertTrue(request.uri().toString().equals("https://api.elevenlabs.io/v1/speech-to-text"),
                "Unexpected transcription endpoint");
        h.assertTrue(request.headers().firstValue("xi-api-key").orElseThrow().equals("test-key"),
                "ElevenLabs key header is missing");
        String contentType = request.headers().firstValue("Content-Type").orElseThrow();
        h.assertTrue(contentType.startsWith("multipart/form-data; boundary="), "No multipart boundary");
        String boundary = contentType.substring(contentType.indexOf('=') + 1);
        String upload = body(request);
        h.assertTrue(upload.contains("name=\"model_id\"\r\n\r\nscribe_v3\r\n")
                        && upload.contains("name=\"language_code\"\r\n\r\nrus\r\n")
                        && upload.contains("name=\"file_format\"\r\n\r\npcm_s16le_16\r\n")
                        && upload.endsWith("\r\n\r\nAB\r\n--" + boundary + "--\r\n"),
                "Multipart fields or PCM framing changed");
        h.assertTrue(ElevenLabsVoiceClient.transcript("{\"text\":\"  Добудь 5 брёвен  \"}").equals("Добудь 5 брёвен"),
                "Transcript was not returned to the dialogue as text");
        // A malformed header value keeps this client-path check offline even if the duration guard regresses.
        var shortRecording = CompanionSpeech.transcribe(new byte[3199], SpeechProvider.ELEVENLABS, "\ninvalid");
        var error = shortRecording.handle((text, failure) -> failure).join();
        h.assertTrue(error != null && error.getMessage().contains("Запись слишком короткая"),
                "A recording under the ElevenLabs minimum reached credential validation");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void elevenLabsVoiceUsesSelectedIdAndPositionalAudioFormat(GameTestHelper h) throws Exception {
        h.assertTrue(SpeechEngine.choose(SpeechProvider.ELEVENLABS, "test-key", true, true, false) == SpeechEngine.ELEVENLABS,
                "The selected cloud voice was replaced by a local voice or Inworld");
        h.assertTrue(SpeechEngine.choose(SpeechProvider.ELEVENLABS, "", true, true, false) == SpeechEngine.WINDOWS,
                "An empty key disabled the local Windows voice");
        var request = ElevenLabsVoiceClient.synthesisRequest("Привет, я Олег!", "test-key", "voice123");
        h.assertTrue(request.uri().toString().equals("https://api.elevenlabs.io/v1/text-to-speech/voice123?output_format=pcm_24000"),
                "Voice ID or output format is missing");
        var json = JsonParser.parseString(body(request)).getAsJsonObject();
        h.assertTrue(json.get("language_code").getAsString().equals("ru")
                        && json.get("text").getAsString().equals("Привет, я Олег!"),
                "Russian speech request was not encoded correctly");
        byte[] samples = {1, -2, 3, -4};
        var sound = VoicePcm.decode(ElevenLabsVoiceClient.voiceWav(samples));
        h.assertTrue(sound.format().getSampleRate() == 24_000 && sound.format().getChannels() == 1
                        && sound.format().getSampleSizeInBits() == 16 && Arrays.equals(sound.bytes(), samples),
                "Minecraft's WAV decoder lost the PCM format or samples");
        h.succeed();
    }

    private static String body(HttpRequest request) {
        var result = new CompletableFuture<String>();
        var bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return result.orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).join();
    }
}

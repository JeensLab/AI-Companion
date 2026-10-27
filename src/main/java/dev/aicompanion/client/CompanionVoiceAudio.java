package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import dev.aicompanion.client.VoicePcm.Pcm;
import dev.aicompanion.AICompanionMod;
import com.mojang.blaze3d.audio.Channel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import org.lwjgl.openal.AL10;

/** Dynamic mono PCM in Minecraft's sound engine: native panning, attenuation, volume and pause. */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID, value = Dist.CLIENT)
public final class CompanionVoiceAudio {
    private static final List<Voice> ACTIVE = new ArrayList<>();
    private CompanionVoiceAudio() { }
    public static CompanionEntity speaker() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return null;
        return client.level.getEntitiesOfClass(CompanionEntity.class, client.player.getBoundingBox().inflate(160),
                entity -> client.player.getUUID().equals(entity.getOwnerId()) && entity.isAlive()).stream().findFirst().orElse(null);
    }
    public static boolean audible(CompanionEntity speaker) {
        var client = Minecraft.getInstance();
        return speaker != null && client.player != null && client.level == speaker.level()
                && client.player.distanceToSqr(speaker) < Math.pow(OpenRouterConfig.VOICE_DISTANCE.get(), 2);
    }
    public static CompletableFuture<Void> play(byte[] wav, UUID speakerId, Object world) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        // Decoding and envelope analysis must not stall a client tick or a rendered frame.
        CompletableFuture.supplyAsync(() -> {
            try { Pcm pcm = VoicePcm.decode(wav); return new Prepared(pcm, new CompanionSpeechEnvelope(pcm)); }
            catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        }).whenComplete((prepared, error) -> {
            if (error != null) { done.completeExceptionally(error); return; }
            Minecraft.getInstance().execute(() -> {
                var client = Minecraft.getInstance(); var speaker = speaker();
                if (done.isDone() || client.level != world || !audible(speaker) || !speaker.getUUID().equals(speakerId)
                        || client.options.getSoundSourceVolume(SoundSource.VOICE) == 0 || client.options.getSoundSourceVolume(SoundSource.MASTER) == 0) { done.complete(null); return; }
                try {
                    Voice voice = new Voice(speaker, prepared, done);
                    ACTIVE.add(voice); client.getSoundManager().play(voice);
                } catch (Exception playbackError) { done.completeExceptionally(playbackError); }
            });
        });
        return done;
    }
    private record Prepared(Pcm pcm, CompanionSpeechEnvelope envelope) { }

    @SubscribeEvent
    public static void started(PlayStreamingSourceEvent event) {
        if (event.getSound() instanceof Voice voice) {
            voice.channel = event.getChannel();
            voice.executor = event.getEngine().executor;
            voice.sampleMouth();
        }
    }

    public static float mouthOpen(CompanionEntity speaker) {
        var client = Minecraft.getInstance();
        if (client.isPaused() || client.level != speaker.level()
                || client.options.getSoundSourceVolume(SoundSource.VOICE) == 0
                || client.options.getSoundSourceVolume(SoundSource.MASTER) == 0) return 0;
        float amount = 0;
        for (Voice voice : ACTIVE) if (voice.speaker == speaker && !voice.done.isDone()) {
            voice.requestMouth();
            amount = Math.max(amount, voice.mouth);
        }
        return amount;
    }
    public static void tick() {
        var client = Minecraft.getInstance();
        for (Voice voice : List.copyOf(ACTIVE)) {
            if (voice.speaker.level() != client.level || !voice.speaker.isAlive() || voice.speaker.isRemoved()
                    || voice.done.isDone() || (!client.isPaused() && ++voice.elapsed > voice.maximumTicks)) { client.getSoundManager().stop(voice); voice.finish(); }
            else voice.requestMouth();
            if (voice.done.isDone()) ACTIVE.remove(voice);
        }
    }
    private static final class Voice extends AbstractTickableSoundInstance {
        private final CompanionEntity speaker;
        private final Pcm pcm;
        private final CompanionSpeechEnvelope envelope;
        private final CompletableFuture<Void> done;
        private volatile Channel channel;
        private volatile SoundEngineExecutor executor;
        private volatile float mouth;
        private long retiredFrames, lastSampleNanos, lastRequestNanos;
        private int elapsed;
        private final int maximumTicks;
        Voice(CompanionEntity speaker, Prepared prepared, CompletableFuture<Void> done) {
            super(SoundEvents.AMBIENT_CAVE.value(), SoundSource.VOICE, RandomSource.create());
            this.speaker = speaker; this.pcm = prepared.pcm(); this.envelope = prepared.envelope(); this.done = done;
            maximumTicks = (int)((pcm.bytes().length / pcm.format().getFrameRate() / 2 + 5) * 20);
            volume = 1; relative = false; attenuation = SoundInstance.Attenuation.LINEAR; updatePosition();
        }
        @Override public WeighedSoundEvents resolve(SoundManager manager) {
            sound = new Sound(ResourceLocation.fromNamespaceAndPath("aicompanion", "voice"), ConstantFloat.of(1), ConstantFloat.of(1), 1,
                    Sound.Type.FILE, true, false, OpenRouterConfig.VOICE_DISTANCE.get());
            WeighedSoundEvents event = new WeighedSoundEvents(getLocation(), null); event.addSound(sound); return event;
        }
        @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(new AudioStream() {
                int offset;
                final ArrayDeque<Integer> queuedFrames = new ArrayDeque<>();
                @Override public AudioFormat getFormat() { return pcm.format(); }
                @Override public ByteBuffer read(int size) {
                    // Initial prebuffering precedes the started event. Later reads replace unqueued buffers.
                    if (channel != null && !queuedFrames.isEmpty()) retiredFrames += queuedFrames.removeFirst();
                    int amount = Math.min(size, pcm.bytes().length - offset); amount -= amount % 2;
                    if (amount == 0) return null;
                    queuedFrames.addLast(amount / 2);
                    ByteBuffer buffer = ByteBuffer.allocateDirect(amount); buffer.put(pcm.bytes(), offset, amount); offset += amount; buffer.flip(); return buffer;
                }
                @Override public void close() { mouth = 0; done.complete(null); }
            });
        }
        private void requestMouth() {
            long now = System.nanoTime();
            SoundEngineExecutor soundExecutor = executor;
            if (soundExecutor != null && now - lastRequestNanos >= 10_000_000) {
                lastRequestNanos = now;
                soundExecutor.execute(this::sampleMouth);
            }
        }
        private void sampleMouth() {
            // This method runs only on the sound executor, including the start event.
            if (done.isDone() || channel == null || !channel.playing()) {
                mouth = 0; lastSampleNanos = 0; return;
            }
            long now = System.nanoTime();
            long cursor = retiredFrames + AL10.alGetSourcei(channel.source, org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET);
            mouth = CompanionSpeechEnvelope.smooth(mouth, envelope.atSample(cursor),
                    lastSampleNanos == 0 ? .01 : (now - lastSampleNanos) / 1_000_000_000.0);
            lastSampleNanos = now;
        }
        private void updatePosition() { x = speaker.getX(); y = speaker.getEyeY() - .2; z = speaker.getZ(); }
        @Override public void tick() { if (done.isDone() || speaker.isRemoved()) finish(); else updatePosition(); }
        void finish() { mouth = 0; stop(); done.complete(null); }
    }
}

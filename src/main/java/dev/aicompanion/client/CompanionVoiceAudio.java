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
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import dev.aicompanion.client.VoicePcm.Pcm;

/** Dynamic mono PCM in Minecraft's sound engine: native panning, attenuation, volume and pause. */
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
        Minecraft.getInstance().execute(() -> {
            var client = Minecraft.getInstance(); var speaker = speaker();
            if (client.level != world || !audible(speaker) || !speaker.getUUID().equals(speakerId)
                    || client.options.getSoundSourceVolume(SoundSource.VOICE) == 0 || client.options.getSoundSourceVolume(SoundSource.MASTER) == 0) { done.complete(null); return; }
            try {
                Pcm pcm = VoicePcm.decode(wav); Voice voice = new Voice(speaker, pcm, done);
                ACTIVE.add(voice); client.getSoundManager().play(voice);
            } catch (Exception error) { done.completeExceptionally(error); }
        });
        return done;
    }
    public static void tick() {
        var client = Minecraft.getInstance();
        for (Voice voice : List.copyOf(ACTIVE)) {
            if (voice.speaker.level() != client.level || !voice.speaker.isAlive() || voice.speaker.isRemoved()
                    || (!client.isPaused() && ++voice.elapsed > voice.maximumTicks)) { client.getSoundManager().stop(voice); voice.finish(); }
            if (voice.done.isDone()) ACTIVE.remove(voice);
        }
    }
    private static final class Voice extends AbstractTickableSoundInstance {
        private final CompanionEntity speaker;
        private final Pcm pcm;
        private final CompletableFuture<Void> done;
        private int elapsed;
        private final int maximumTicks;
        Voice(CompanionEntity speaker, Pcm pcm, CompletableFuture<Void> done) {
            super(SoundEvents.AMBIENT_CAVE.value(), SoundSource.VOICE, RandomSource.create());
            this.speaker = speaker; this.pcm = pcm; this.done = done;
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
                @Override public AudioFormat getFormat() { return pcm.format(); }
                @Override public ByteBuffer read(int size) {
                    int amount = Math.min(size, pcm.bytes().length - offset); amount -= amount % 2;
                    ByteBuffer buffer = ByteBuffer.allocateDirect(amount); buffer.put(pcm.bytes(), offset, amount); offset += amount; buffer.flip(); return buffer;
                }
                @Override public void close() { done.complete(null); }
            });
        }
        private void updatePosition() { x = speaker.getX(); y = speaker.getEyeY() - .2; z = speaker.getZ(); }
        @Override public void tick() { if (done.isDone() || speaker.isRemoved()) finish(); else updatePosition(); }
        void finish() { stop(); done.complete(null); }
    }
}

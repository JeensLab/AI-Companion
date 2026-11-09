package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionConversation;
import dev.aicompanion.CompanionJokes;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.client.CompanionVoiceAudio;
import dev.aicompanion.client.InworldTtsClient;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;

/** Opt-in real-client integration probe in its own offline world. Excluded from the release JAR with gametest/. */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID, value = Dist.CLIENT)
public final class CompanionJokeClientProbe {
    private static boolean started, finished, soundStarted;
    private static int phase, checks;
    private static long deadline;
    private static long beforeAudio;
    private static CompletableFuture<Void> audio;

    @SubscribeEvent public static void sound(PlayStreamingSourceEvent event) {
        if (Boolean.getBoolean("aicompanion.jokeProbe")
                && event.getSound().getClass().getName().endsWith("CompanionVoiceAudio$Voice")) soundStarted = true;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("aicompanion.jokeProbe") || finished) return;
        var mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true; deadline = System.nanoTime() + 120_000_000_000L;
            // Fail before any config mutation: the UI checks call save() on the entire config specification.
            if (!java.nio.file.Files.isRegularFile(mc.gameDirectory.toPath().resolve(".jokes-probe-disposable"))
                    || !OpenRouterConfig.API_KEY.get().isBlank() || !OpenRouterConfig.API_URL.get().isBlank()
                    || !OpenRouterConfig.INWORLD_API_KEY.get().isBlank()) {
                fail(mc, "Dedicated disposable directory with empty credentials is required"); return;
            }
            OpenRouterConfig.LONG_TERM_MEMORY.set(false);
            OpenRouterConfig.WINDOWS_VOICE.set(false);
            mc.createWorldOpenFlows().createFreshLevel("JokesQA-" + System.currentTimeMillis(),
                    new LevelSettings("Offline jokes QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(0L, false, false),
                    registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), mc.screen);
        }
        if (!started) return;
        if (System.nanoTime() > deadline) { fail(mc, "Probe timed out at phase " + phase); return; }
        if (mc.level == null || mc.player == null || mc.level.getGameTime() % 20 != 0) return;
        var speaker = CompanionVoiceAudio.speaker();
        if (speaker == null) return;
        try {
            if (phase == 0) {
                prepare(mc, speaker);
                blocked(mc, speaker, "feature switch", () -> OpenRouterConfig.JOKES.set(false));
                blocked(mc, speaker, "global speech switch", () -> OpenRouterConfig.AUTONOMOUS_SPEECH.set(false));
                blocked(mc, speaker, "work", () -> flag(speaker, "DATA_GATHERING_LOGS", true));
                blocked(mc, speaker, "combat", () -> flag(speaker, "DATA_FIGHTING", true));
                blocked(mc, speaker, "unsafe scene", () -> flag(speaker, "DATA_SOCIAL_SAFE", false));
                blocked(mc, speaker, "transcription", () -> field(ambient(), "transcriptions").setInt(null, 1));
                blocked(mc, speaker, "pending response", () -> field(ambient(), "pending").setBoolean(null, true));
                blocked(mc, speaker, "player conversation", () -> conversation().playerMessage(mc.level.getGameTime(), 0));
                blocked(mc, speaker, "open menu", () -> mc.setScreen(new TitleScreen()));
                prepare(mc, speaker);
                invokeTick();
                check(lastJoke() == mc.level.getGameTime(), "Quiet scene did not deliver a joke");
                check(historyContainsJoke(), "Joke did not reach the actual conversation history");
                check(!conversation().ready(mc.level.getGameTime()), "Joke did not reserve the conversation pause");
                check(conversation().nextOpportunity() >= mc.level.getGameTime() + CompanionConversation.MIN_GAP,
                        "Joke did not reserve the full initiative interval");
                checkSettings(mc);
                prepare(mc, speaker);
                var wav = CompanionFaceRenderProbe.class.getDeclaredMethod("wav", int.class, boolean.class);
                wav.setAccessible(true);
                audio = CompanionVoiceAudio.play((byte[]) wav.invoke(null, 6, false), speaker.getUUID(), mc.level);
                field(InworldTtsClient.class, "playbackQueue").set(null, audio);
                beforeAudio = lastJoke();
                phase = 1;
            } else if (phase == 1 && soundStarted) {
                check(!audio.isDone(), "Playback ended before the speaking guard was tested");
                check(lastJoke() == beforeAudio, "Joke interrupted playback before the probe observed it");
                invokeTick();
                check(lastJoke() == beforeAudio, "Joke interrupted actual local audio playback");
                audio.cancel(false); InworldTtsClient.resetPlaybackQueue();
                finished = true;
                AICompanionMod.LOGGER.info("JOKE_CLIENT_PROBE_PASSED: {} checks; real client tick, UI buttons, local WAV playback; no provider requests", checks);
                mc.stop();
            }
        } catch (Exception | AssertionError error) { fail(mc, error.toString()); }
    }

    private static void prepare(Minecraft mc, CompanionEntity speaker) throws Exception {
        mc.setScreen(null); OpenRouterConfig.JOKES.set(true); OpenRouterConfig.AUTONOMOUS_SPEECH.set(true);
        OpenRouterConfig.SHORT_MURMURS.set(true); OpenRouterConfig.JOKE_INTERVAL_MINUTES.set(5);
        field(ambient(), "jokeBank").set(null, null);
        field(ambient(), "pending").setBoolean(null, false); field(ambient(), "transcriptions").setInt(null, 0);
        flag(speaker, "DATA_SOCIAL_SAFE", true); flag(speaker, "DATA_GATHERING_LOGS", false);
        flag(speaker, "DATA_FIGHTING", false);
        long now = mc.level.getGameTime();
        conversation().reset(now - 100_000, 0); conversation().trackObservation(speaker.socialSequence());
        field(ambient(), "world").set(null, mc.level); field(ambient(), "companion").set(null, speaker.getUUID());
        ((CompanionJokes) field(ambient(), "jokes").get(null)).reset(now - 100_000, 0);
    }
    private interface Change { void run() throws Exception; }
    private static void blocked(Minecraft mc, CompanionEntity speaker, String label, Change change) throws Exception {
        prepare(mc, speaker); long before = lastJoke(); change.run(); invokeTick();
        check(lastJoke() == before, "Joke was not blocked by " + label);
    }
    private static void checkSettings(Minecraft mc) throws Exception {
        var constructor = Class.forName("dev.aicompanion.client.CompanionBehaviorScreen").getDeclaredConstructor(Screen.class);
        constructor.setAccessible(true); var screen = (Screen) constructor.newInstance((Screen) null); mc.setScreen(screen);
        // Buttons are built for all pages; test the actual callbacks, including persistence, without coordinate assumptions.
        Button toggle = screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().startsWith("Анекдоты:"))
                .map(Button.class::cast).findFirst().orElseThrow();
        toggle.onPress(); check(!OpenRouterConfig.JOKES.get(), "UI did not disable jokes");
        toggle.onPress(); check(OpenRouterConfig.JOKES.get(), "UI did not enable jokes");
        Button interval = screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().startsWith("Пауза между анекдотами:"))
                .map(Button.class::cast).findFirst().orElseThrow();
        interval.onPress(); check(OpenRouterConfig.JOKE_INTERVAL_MINUTES.get() == 10, "UI did not change interval");
        OpenRouterConfig.JOKE_INTERVAL_MINUTES.set(60); interval.onPress();
        check(OpenRouterConfig.JOKE_INTERVAL_MINUTES.get() == 5, "UI interval did not wrap at maximum");
        mc.setScreen(null);
    }
    private static Class<?> ambient() throws ClassNotFoundException { return Class.forName("dev.aicompanion.client.CompanionAmbientClient"); }
    private static Field field(Class<?> type, String name) throws Exception { var f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static CompanionConversation conversation() throws Exception { return (CompanionConversation) field(ambient(), "conversation").get(null); }
    private static long lastJoke() throws Exception { return field(CompanionJokes.class, "lastJoke").getLong(field(ambient(), "jokes").get(null)); }
    private static void invokeTick() throws Exception { var method = ambient().getDeclaredMethod("tick"); method.setAccessible(true); method.invoke(null); }
    private static boolean historyContainsJoke() throws Exception {
        String history = field(OpenRouterClient.class, "HISTORY").get(null).toString();
        return CompanionJokes.ALL.stream().anyMatch(history::contains);
    }
    @SuppressWarnings("unchecked") private static void flag(CompanionEntity speaker, String name, boolean value) throws Exception {
        speaker.getEntityData().set((EntityDataAccessor<Boolean>) field(CompanionEntity.class, name).get(null), value);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    private static void fail(Minecraft mc, String message) {
        finished = true; if (audio != null) audio.cancel(false);
        AICompanionMod.LOGGER.error("JOKE_CLIENT_PROBE_FAILED: {}", message); mc.stop();
    }
}

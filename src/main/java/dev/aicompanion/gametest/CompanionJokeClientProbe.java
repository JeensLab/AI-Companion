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
    private static int phase, checks, loggedPhase = -1;
    private static long deadline;
    private static long beforeAudio;
    private static final String LONG_STORY = "Проверка длинной байки. " + "Олег вспоминал старую шахту и дорогу домой. ".repeat(12) + "Конец истории номер 145.";
    private static long deliveryStarted;
    private static CompletableFuture<Void> audio;

    @SubscribeEvent public static void sound(PlayStreamingSourceEvent event) {
        if (Boolean.getBoolean("aicompanion.jokeProbe")
                && event.getSound().getClass().getName().endsWith("CompanionVoiceAudio$Voice")) soundStarted = true;
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("aicompanion.jokeProbe") || finished) return;
        var mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true; deadline = System.nanoTime() + 240_000_000_000L;
            // Fail before any config mutation: the UI checks call save() on the entire config specification.
            if (!java.nio.file.Files.isRegularFile(mc.gameDirectory.toPath().resolve(".jokes-probe-disposable"))
                    || !OpenRouterConfig.API_KEY.get().isBlank() || !OpenRouterConfig.API_URL.get().isBlank()
                    || !OpenRouterConfig.INWORLD_API_KEY.get().isBlank()) {
                fail(mc, "Dedicated disposable directory with empty credentials is required"); return;
            }
            OpenRouterConfig.LONG_TERM_MEMORY.set(false);
            OpenRouterConfig.WINDOWS_VOICE.set(false); OpenRouterConfig.MACOS_VOICE.set(false);
            mc.createWorldOpenFlows().createFreshLevel("JokesQA-" + System.currentTimeMillis(),
                    new LevelSettings("Offline jokes QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(0L, false, false),
                    registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), mc.screen);
        }
        if (!started) return;
        if (phase != loggedPhase) { AICompanionMod.LOGGER.info("JOKE_PROBE_PHASE: {}", phase); loggedPhase = phase; }
        if (System.nanoTime() > deadline) { fail(mc, "Probe timed out at phase " + phase); return; }
        if (mc.level == null || mc.player == null) return;
        // Menus pause world time: asynchronous file phases must still advance on client ticks.
        if ((phase == 0 || phase == 1) && mc.level.getGameTime() % 20 != 0) return;
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
                deliveryStarted = mc.level.getGameTime(); phase = 2;
            } else if (phase == 2 && historyContainsJoke()) {
                check(lastJoke() >= deliveryStarted, "Quiet scene did not deliver a joke: " + lastJoke() + " < " + deliveryStarted);
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
                java.util.Locale.setDefault(java.util.Locale.ENGLISH);
                mc.getLanguageManager().setSelected("en_us");
                var files = Class.forName("dev.aicompanion.client.CompanionJokeFiles");
                field(files, "russianPlayer").setBoolean(null, false);
                java.nio.file.Files.writeString(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("aicompanion-jokes.txt"),
                        "# format: blocks\n" + LONG_STORY + "\n");
                openFileScreen(mc); press(mc, "Перезагрузить"); phase = 3;
            } else if (phase == 3 && !filesLoading()) {
                check(fileStatus().contains("Из файла: 1"), "File was not loaded through UI");
                prepare(mc, speaker); OpenRouterConfig.MACOS_VOICE.set(true); OpenRouterConfig.MACOS_VOICE.save();
                deliveryStarted = mc.level.getGameTime(); soundStarted = false; invokeTick(); phase = 4;
            } else if (phase == 4 && lastJoke() >= deliveryStarted) {
                check(field(OpenRouterClient.class, "HISTORY").get(null).toString().contains(LONG_STORY), "Long story was truncated or missing from history");
                audio = (CompletableFuture<Void>) field(InworldTtsClient.class, "playbackQueue").get(null);
                phase = 5;
            } else if (phase == 5 && audio.isDone()) {
                audio.join(); check(soundStarted, "Long story did not reach actual local speech playback");
                java.nio.file.Files.write(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("aicompanion-jokes.txt"), new byte[]{(byte)0xc3,0x28});
                openFileScreen(mc); press(mc, "Перезагрузить"); phase = 6;
            } else if (phase == 6 && !filesLoading()) {
                check(fileStatus().contains("Прежний набор сохранён"), "Read error was not reported");
                var bank = (java.util.List<?>) field(CompanionJokes.class, "bank").get(field(ambient(), "jokes").get(null));
                check(bank.size() == 1 && bank.toString().contains(LONG_STORY), "Read error destroyed last working bank");
                // An abandoned future must not be installed after leaving a world.
                var files = Class.forName("dev.aicompanion.client.CompanionJokeFiles");
                var abandoned = new CompletableFuture<dev.aicompanion.CompanionJokeBank.Bank>();
                field(files,"loading").set(null,abandoned);
                var reset = files.getDeclaredMethod("reset", boolean.class); reset.setAccessible(true); reset.invoke(null,false);
                abandoned.complete(new dev.aicompanion.CompanionJokeBank.Bank(java.util.List.of(), dev.aicompanion.CompanionJokeBank.parse("")));
                check(!filesLoading(), "Old world load survived reset");
                mc.getLanguageManager().setSelected("ru_ru");
                ((CompanionJokes) field(ambient(), "jokes").get(null)).reloadBank(java.util.List.of());
                reset.invoke(null,true); phase = 7;
            } else if (phase == 7 && !filesLoading()) {
                var bank = (java.util.List<?>) field(CompanionJokes.class, "bank").get(field(ambient(), "jokes").get(null));
                check(bank.size() == 40, "Invalid file on world entry lost Russian bundled fallback");
                finished = true;
                AICompanionMod.LOGGER.info("JOKE_CLIENT_PROBE_PASSED: {} checks; async file/UI reload, long local speech, error preservation, world reset; no provider requests", checks);
                mc.stop();
            }
        } catch (Exception | AssertionError error) { fail(mc, error.toString()); }
    }

    private static void prepare(Minecraft mc, CompanionEntity speaker) throws Exception {
        mc.setScreen(null); OpenRouterConfig.JOKES.set(true); OpenRouterConfig.AUTONOMOUS_SPEECH.set(true);
        OpenRouterConfig.SHORT_MURMURS.set(true); OpenRouterConfig.JOKE_INTERVAL_MINUTES.set(5);
        field(ambient(), "pending").setBoolean(null, false); field(ambient(), "transcriptions").setInt(null, 0);
        flag(speaker, "DATA_SOCIAL_SAFE", true); flag(speaker, "DATA_GATHERING_LOGS", false);
        flag(speaker, "DATA_FIGHTING", false);
        long now = mc.level.getGameTime();
        conversation().reset(now - 100_000, 0); conversation().trackObservation(speaker.socialSequence());
        field(ambient(), "world").set(null, mc.level); field(ambient(), "companion").set(null, speaker.getUUID());
        ((CompanionJokes) field(ambient(), "jokes").get(null)).reset(now - 100_000, 0);
    }
    private static boolean filesLoading() throws Exception { return field(Class.forName("dev.aicompanion.client.CompanionJokeFiles"), "loading").get(null) != null; }
    private static String fileStatus() throws Exception { return (String) field(Class.forName("dev.aicompanion.client.CompanionJokeFiles"), "status").get(null); }
    private static void openFileScreen(Minecraft mc) throws Exception {
        var constructor = Class.forName("dev.aicompanion.client.CompanionJokeScreen").getDeclaredConstructor(Screen.class);
        constructor.setAccessible(true); mc.setScreen((Screen) constructor.newInstance((Screen)null));
    }
    private static void press(Minecraft mc, String text) {
        mc.screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(text))
                .map(Button.class::cast).findFirst().orElseThrow().onPress();
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

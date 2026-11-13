package dev.aicompanion.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.OpenRouterConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.minecraft.commands.Commands;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import java.util.Optional;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.lwjgl.glfw.GLFW;

@Mod(value = AICompanionMod.MOD_ID, dist = Dist.CLIENT)
public final class AICompanionClient {
    private static final KeyMapping OPEN_INTERFACE = new KeyMapping(
            "key.aicompanion.open_interface", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.aicompanion");
    static boolean interfaceKeyMatches(int key, int scan) { return OPEN_INTERFACE.matches(key, scan); }
    private static final KeyMapping OPEN_CHAT = new KeyMapping(
            "key.aicompanion.open_chat",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.aicompanion"
    );
    private static final KeyMapping PUSH_TO_TALK = new KeyMapping(
            "key.aicompanion.push_to_talk",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.aicompanion"
    );
    private static final KeyMapping TOGGLE_MARKER = new KeyMapping(
            "key.aicompanion.toggle_marker",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.aicompanion"
    );
    private static final KeyMapping TOGGLE_MOOD = new KeyMapping(
            "key.aicompanion.toggle_mood",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.aicompanion"
    );
    private boolean wasPushToTalkDown;
    private int preferenceSyncTicks;
    private Object previousWorld;
    static boolean recordingSpeech() { return PUSH_TO_TALK.isDown(); }

    public AICompanionClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (minecraft, parent) -> new CompanionSettingsScreen(parent));
        modEventBus.addListener(this::registerKeyMappings);
        modEventBus.addListener(this::registerEntityRenderers);
        modEventBus.addListener(this::registerMenuScreens);
        modEventBus.addListener(this::registerGuiLayers);
        modEventBus.addListener(this::addPackFinders);
        CompanionMarker.init();
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::registerClientCommands);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(CompanionGhostPlacement::render);
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        OpenRouterClient.resetSession();
        CompanionMemoryFlow.unbind();
        CompanionAmbientClient.forgetWorld();
        CompanionCampfireAudio.forget();
        GameContext.forgetWorld();
        CompanionMoodPanel.forgetWorld();
        CompanionGhostPlacement.forgetWorld();
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CHAT);
        event.register(OPEN_INTERFACE);
        event.register(PUSH_TO_TALK);
        event.register(TOGGLE_MARKER);
        event.register(TOGGLE_MOOD);
        CompanionGhostPlacement.KEYS.forEach(event::register);
    }

    /**
     * The guitar folder as a resource pack (CompanionMusicPack): the sound engine reads sounds.json only from packs, and this one is
     * rebuilt from the folder on every resource reload. Always active, above the mod's own resources.
     */
    private void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(packs -> {
            PackLocationInfo location = new PackLocationInfo(CompanionMusicPack.ID,
                    Component.literal("AI Companion: музыка из папки"), PackSource.BUILT_IN, Optional.empty());
            Pack pack = Pack.readMetaAndCreate(location, new Pack.ResourcesSupplier() {
                @Override public PackResources openPrimary(PackLocationInfo info) { return new CompanionMusicPack(info); }
                @Override public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) { return new CompanionMusicPack(info); }
            }, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, true));
            if (pack != null) packs.accept(pack);
        });
    }

    /** «/companion music»: read the folder again and let the sound engine see what was added to it. */
    private void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("companion").then(Commands.literal("music").executes(context -> {
            var client = Minecraft.getInstance();
            int found = dev.aicompanion.entity.CompanionMusicLibrary.scan(dev.aicompanion.entity.CompanionMusicLibrary.folder()).size();
            client.reloadResourcePacks().thenRun(() -> client.execute(() -> client.gui.getChat().addMessage(Component.literal(found == 0
                    ? "Папка с музыкой пуста: играют две стандартные записи."
                    : "Записей в папке: " + found + ". Игра перечитала папку."))));
            return found;
        })));
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(CompanionMoodPanel.ID, CompanionMoodPanel::render);
        event.registerAboveAll(CompanionThinking.ID, CompanionThinking::render);
        event.registerBelow(VanillaGuiLayers.CHAT, CompanionMarker.ID, CompanionMarker::render);
        event.registerBelow(VanillaGuiLayers.CHAT, CompanionGhostPanel.ID, CompanionGhostPanel::render);
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(dev.aicompanion.AICompanionMod.COMPANION.get(), CompanionRenderer::new);
        event.registerEntityRenderer(dev.aicompanion.AICompanionMod.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(AICompanionMod.COMPANION_MENU.get(), CompanionInventoryScreen::new);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (preferenceSyncTicks <= 0) dev.aicompanion.CompanionDiagnostics.configure(OpenRouterConfig.DIAGNOSTICS.get(),
                OpenRouterConfig.API_KEY.get(), OpenRouterConfig.INWORLD_API_KEY.get());
        if (previousWorld != minecraft.level) {
            previousWorld = minecraft.level; preferenceSyncTicks = 0;
            dev.aicompanion.CompanionDiagnostics.event("", "world_changed", "dimension",
                    minecraft.level == null ? "disconnected" : minecraft.level.dimension().location());
            CompanionActionExecutor.cancelPendingPlans("", "world_changed"); InworldTtsClient.resetPlaybackQueue();
            CompanionCampfireAudio.forget();
        }
        CompanionVoiceAudio.tick();
        CompanionCampfireAudio.tick();
        CompanionAmbientClient.tick();
        CompanionAmbientClient.autonomyLines();
        CompanionMarker.tick();
        CompanionGhostPlacement.tick();
        if (minecraft.level != null && preferenceSyncTicks-- <= 0) { preferenceSyncTicks = 40; CompanionPreferences.sync(); }
        boolean gameplay = minecraft.level != null && minecraft.screen == null;
        boolean pushToTalkDown = gameplay && PUSH_TO_TALK.isDown();
        if (pushToTalkDown && !wasPushToTalkDown) {
            CompanionAmbientClient.conversationActivity();
            if (OpenRouterConfig.INWORLD_API_KEY.get().trim().isEmpty()) {
                minecraft.gui.setOverlayMessage(Component.literal("Добавь ключ Inworld в настройках мода для голосового чата"), false);
            } else {
                MicrophoneRecorder.start();
                minecraft.gui.setOverlayMessage(Component.literal("Запись... отпусти V, чтобы отправить"), false);
            }
        } else if (!pushToTalkDown && wasPushToTalkDown) {
            var recording = MicrophoneRecorder.stop();
            if (gameplay) CompanionChatScreen.processVoiceInBackground(recording.thenCompose(InworldSttClient::transcribe));
        }
        wasPushToTalkDown = pushToTalkDown;

        while (TOGGLE_MARKER.consumeClick()) if (minecraft.level != null && minecraft.screen == null) CompanionMarker.toggle();
        while (TOGGLE_MOOD.consumeClick()) if (minecraft.level != null && minecraft.screen == null) CompanionMoodPanel.toggle();
        while (OPEN_INTERFACE.consumeClick()) if (minecraft.level != null && minecraft.screen == null)
            minecraft.setScreen(new CompanionTasksScreen());
        while (OPEN_CHAT.consumeClick()) {
            if (minecraft.level != null && minecraft.screen == null) {
                minecraft.setScreen(new CompanionChatScreen());
            }
        }
    }
}

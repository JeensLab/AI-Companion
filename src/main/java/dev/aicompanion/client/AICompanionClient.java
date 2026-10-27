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
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
        CompanionMarker.init();
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        OpenRouterClient.resetSession();
        CompanionMemoryFlow.unbind();
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CHAT);
        event.register(OPEN_INTERFACE);
        event.register(PUSH_TO_TALK);
        event.register(TOGGLE_MARKER);
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(dev.aicompanion.AICompanionMod.COMPANION.get(), CompanionRenderer::new);
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
        }
        CompanionVoiceAudio.tick();
        CompanionAmbientClient.tick();
        CompanionAmbientClient.autonomyLines();
        CompanionMarker.tick();
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
        while (OPEN_INTERFACE.consumeClick()) if (minecraft.level != null && minecraft.screen == null)
            minecraft.setScreen(new CompanionTasksScreen());
        while (OPEN_CHAT.consumeClick()) {
            if (minecraft.level != null && minecraft.screen == null) {
                minecraft.setScreen(new CompanionChatScreen());
            }
        }
    }
}

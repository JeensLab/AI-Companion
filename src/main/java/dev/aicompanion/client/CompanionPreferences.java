package dev.aicompanion.client;

import dev.aicompanion.CompanionIdentity;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.client.Minecraft;

public final class CompanionPreferences {
    private CompanionPreferences() { }
    /** Same integrated-server bridge as preferences/actions, no extra network/model request. */
    static void speechExpression(String text) {
        var client = Minecraft.getInstance(); var server = client.getSingleplayerServer();
        var speaker = CompanionVoiceAudio.speaker();
        if (server == null || client.player == null || speaker == null) return;
        var playerId = client.player.getUUID(); var speakerId = speaker.getUUID();
        var dimension = speaker.level().dimension();
        server.execute(() -> {
            var level = server.getLevel(dimension);
            if (level != null && level.getEntity(speakerId) instanceof dev.aicompanion.entity.CompanionEntity companion
                    && playerId.equals(companion.getOwnerId())) companion.reactToSpeech(text);
        });
    }
    public static void sync() {
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (server == null || client.player == null) return;
        var playerId = client.player.getUUID();
        var clientWorld = client.level;
        String name = CompanionIdentity.cleanName(OpenRouterConfig.COMPANION_NAME.get());
        int radius = OpenRouterConfig.SEARCH_RADIUS.get();
        boolean chunks = OpenRouterConfig.CHUNK_LOADING.get(), speech = OpenRouterConfig.AUTONOMOUS_SPEECH.get();
        boolean conversationAvailable = CompanionAmbientClient.availableForObservation();
        boolean longTerm = OpenRouterConfig.LONG_TERM_MEMORY.get();
        CompanionMemoryFlow.bind(server, playerId, longTerm);
        long session = OpenRouterClient.generation();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;
            CompanionMemoryView.pull(player, longTerm, digest -> client.execute(() -> {
                if (client.level == clientWorld) CompanionMemoryView.deliver(session, digest, longTerm);
            }));
            var companion = CompanionEvents.getOwnedCompanion(player);
            player.getPersistentData().putBoolean("aicompanion:chunk_loading_enabled", chunks);
            if (companion != null) {
                companion.setCompanionName(name); companion.setSearchRadius(radius); companion.setLifestyle(chunks, speech);
                companion.setConversationAvailable(conversationAvailable);
                String remote = companion.getName().getString() + " находится на " + companion.blockPosition().toShortString()
                        + ", измерение=" + companion.level().dimension().location() + ", занят=" + companion.isGatheringTask()
                        + ", охрана=" + companion.isProtecting() + ", сумка=" + companion.getStoredItemsForAi()
                        + ", экипировка=" + companion.getEquipmentForAi() + ", текущая активность=" + companion.getActivityForAi()
                        + ", память о местах=" + companion.getPlacesForAi() + ", исторический отчёт=" + companion.getLastTaskResult();
                client.execute(() -> { if (client.level == clientWorld) GameContext.setRemoteState(clientWorld, remote); });
            }
        });
    }
}

package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import net.minecraft.client.Minecraft;
import java.util.UUID;
import dev.aicompanion.CompanionConversation;

/** Nearby comments. Background requests cannot execute actions or interrupt a conversation. */
final class CompanionAmbientClient {
    private static Object world;
    private static UUID companion;
    private static final CompanionConversation conversation = new CompanionConversation();
    private static boolean pending;
    private static int transcriptions;
    private CompanionAmbientClient() { }
    private static long now() { var level = Minecraft.getInstance().level; return level == null ? 0 : level.getGameTime(); }
    private static int jitter() { return java.util.concurrent.ThreadLocalRandom.current().nextInt(CompanionConversation.MAX_GAP - CompanionConversation.MIN_GAP + 1); }
    static void conversationActivity() { conversation.activity(now(), jitter()); }
    static void transcriptionStarted() { transcriptions++; conversationActivity(); }
    static void transcriptionFinished() { transcriptions = Math.max(0, transcriptions - 1); conversationActivity(); }
    private static boolean busy() { return pending || transcriptions > 0 || AICompanionClient.recordingSpeech() || OpenRouterClient.isBusy(); }
    static void playerMessage() { conversation.playerMessage(now(), jitter()); }
    static void replyDelivered(String text) {
        conversation.delivered(text, now(), jitter()); CompanionPreferences.speechExpression(text);
    }
    static void initiativeDelivered(String text) {
        conversation.initiativeDelivered(text, now(), jitter()); CompanionPreferences.speechExpression(text);
    }
    static boolean availableForObservation() {
        var client = Minecraft.getInstance();
        return client.level == world && companion != null && conversation.ready(now()) && !busy()
                && !client.isPaused() && client.screen == null;
    }
    private static boolean safe(dev.aicompanion.entity.CompanionEntity speaker) {
        var client = Minecraft.getInstance(); var player = client.player;
        return speaker.sociallySafe() && player != null && client.level != null && player.getHealth() > 8
                && player.hurtTime == 0 && !player.isOnFire() && !player.isInLava()
                && client.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, player.getBoundingBox().inflate(12),
                        monster -> dev.aicompanion.entity.CompanionHostiles.observed(player, monster, 12)).isEmpty();
    }
    private static Object lineWorld;
    private static UUID lineCompanion;
    private static int lineSerial;
    /** His lines about deeds of his own, shown and voiced once each; old ones are not replayed after entering the world. */
    static void autonomyLines() {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        var speaker = CompanionVoiceAudio.speaker();
        if (speaker == null) return;
        int serial = speaker.autonomyLineSerial();
        if (client.level != lineWorld || !speaker.getUUID().equals(lineCompanion)) { lineWorld = client.level; lineCompanion = speaker.getUUID(); lineSerial = serial; return; }
        if (serial == lineSerial) return;
        lineSerial = serial;
        String text = speaker.autonomyLine();
        if (text.isBlank() || client.level.getGameTime() - speaker.autonomyLineTime() > 200) return;
        CompanionChatScreen.autonomyLine(text);
    }
    static void tick() {
        var client = Minecraft.getInstance();
        if (client.level != world) {
            world = client.level; companion = null; pending = false;
            conversation.reset(now(), jitter());
        }
        if (client.level == null || client.player == null || client.isPaused() || client.screen != null || !OpenRouterConfig.AUTONOMOUS_SPEECH.get()) return;
        var speaker = CompanionVoiceAudio.speaker();
        if (speaker == null || !CompanionVoiceAudio.audible(speaker)) return;
        // Initial tracking data is a snapshot, not a new event. Never replay a pre-load observation.
        if (!speaker.getUUID().equals(companion)) { companion = speaker.getUUID(); conversation.trackObservation(speaker.socialSequence()); return; }
        int current = speaker.socialSequence();
        if (!conversation.newObservation(current)) return;
        if (!conversation.takeObservation(current, now() - speaker.socialTime(), now(), safe(speaker),
                busy(), jitter())) return;
        pending = true;
        Object capturedWorld = world; UUID capturedCompanion = companion;
        long activity = conversation.revision(); String observation = speaker.socialObservation();
        String trace = dev.aicompanion.CompanionDiagnostics.newTrace();
        OpenRouterClient.react(observation, GameContext.ambientSnapshot(speaker), trace, conversation.canAskQuestion()).whenComplete((text, error) -> client.execute(() -> {
            if (world != capturedWorld || client.level != capturedWorld) {
                dev.aicompanion.CompanionDiagnostics.event(trace, "ambient_discarded", "reason", "world_changed"); return;
            }
            pending = false;
            if (error != null) { dev.aicompanion.CompanionDiagnostics.failure(trace, "ambient_request", error); conversation.activity(now(), jitter()); return; }
            if (!conversation.acceptReply(activity, text, OpenRouterConfig.AUTONOMOUS_SPEECH.get(),
                    client.screen != null || client.isPaused() || busy())) {
                dev.aicompanion.CompanionDiagnostics.event(trace, "ambient_discarded", "reason", "conversation_changed_disabled_or_empty"); return;
            }
            var currentSpeaker = CompanionVoiceAudio.speaker();
            if (currentSpeaker == null || !capturedCompanion.equals(currentSpeaker.getUUID())
                    || !safe(currentSpeaker) || !CompanionVoiceAudio.audible(currentSpeaker)) return;
            CompanionChatScreen.autonomousReply(text, trace);
        }));
    }
}

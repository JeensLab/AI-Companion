package dev.aicompanion.client;

import dev.aicompanion.OpenRouterConfig;
import net.minecraft.client.Minecraft;
import java.util.UUID;
import dev.aicompanion.CompanionConversation;

/** Rare nearby comments. Background requests cannot execute actions or interrupt a conversation. */
final class CompanionAmbientClient {
    private static Object world;
    private static UUID companion;
    private static final CompanionConversation conversation = new CompanionConversation();
    private static boolean pending;
    private static int transcriptions;
    private static String urgentText = "";
    private CompanionAmbientClient() { }
    private static long now() { var level = Minecraft.getInstance().level; return level == null ? 0 : level.getGameTime(); }
    private static int jitter() { return java.util.concurrent.ThreadLocalRandom.current().nextInt(2401); }
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
        // Initial tracking data is a snapshot, not a new event, except an urgent line still fresh.
        if (!speaker.getUUID().equals(companion)) {
            companion = speaker.getUUID();
            String first = speaker.socialObservation();
            boolean firstUrgent = CompanionConversation.isUrgent(first);
            conversation.firstSight(speaker.socialSequence(), now() - speaker.socialTime(), now(), firstUrgent);
            urgentText = firstUrgent ? CompanionConversation.stripUrgent(first) : "";
            return;
        }
        int current = speaker.socialSequence();
        boolean fresh = conversation.newObservation(current);
        if (!fresh && conversation.urgentPending() == 0) return;
        boolean safe = safe(speaker);
        String latest = speaker.socialObservation(), observation = CompanionConversation.stripUrgent(latest);
        boolean urgent = fresh && CompanionConversation.isUrgent(latest);
        if (urgent) {
            boolean taken = conversation.takeObservation(current, now() - speaker.socialTime(), now(), safe, busy(), jitter(), true);
            // Kept once taken as well: a lost reply is retried with the text of this same event.
            if (taken || conversation.urgentPending() == current) urgentText = observation;
            if (!taken) return;
        } else if (conversation.takePendingUrgent(now(), safe, busy(), jitter())) {
            urgent = true; observation = urgentText;
        } else if (!fresh || !conversation.takeObservation(current, now() - speaker.socialTime(), now(), safe, busy(), jitter())) {
            return;
        }
        pending = true;
        Object capturedWorld = world; UUID capturedCompanion = companion;
        long activity = conversation.revision(); boolean wasUrgent = urgent;
        String trace = dev.aicompanion.CompanionDiagnostics.newTrace();
        OpenRouterClient.react(observation, GameContext.ambientSnapshot(speaker), trace, wasUrgent).whenComplete((text, error) -> client.execute(() -> {
            if (world != capturedWorld || client.level != capturedWorld) {
                dev.aicompanion.CompanionDiagnostics.event(trace, "ambient_discarded", "reason", "world_changed"); return;
            }
            pending = false;
            if (error != null) {
                dev.aicompanion.CompanionDiagnostics.failure(trace, "ambient_request", error); conversation.activity(now(), jitter());
                if (wasUrgent) conversation.retryUrgent(now());
                return;
            }
            String rejection = conversation.rejection(activity, text, OpenRouterConfig.AUTONOMOUS_SPEECH.get(),
                    client.screen != null || client.isPaused() || busy(), wasUrgent);
            if (!rejection.isEmpty()) { discarded(trace, rejection, text, wasUrgent); return; }
            var currentSpeaker = CompanionVoiceAudio.speaker();
            if (currentSpeaker == null || !capturedCompanion.equals(currentSpeaker.getUUID())
                    || !safe(currentSpeaker) || !CompanionVoiceAudio.audible(currentSpeaker)) { discarded(trace, "not_heard", text, wasUrgent); return; }
            CompanionChatScreen.autonomousReply(text, trace);
        }));
    }
    /** A dropped urgent remark waits for its one retry; an ordinary one is simply lost. */
    private static void discarded(String trace, String reason, String text, boolean urgent) {
        dev.aicompanion.CompanionDiagnostics.event(trace, "ambient_discarded", "reason", reason, "text", text);
        if (urgent) conversation.retryUrgent(now());
    }
}

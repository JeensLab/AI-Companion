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
    private static String urgentText = "";
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
    private static net.minecraft.world.phys.Vec3 lastSpot;
    private static long sampledAt, travelUntil, idleSince;
    /** What is going on, as the client sees it: a fight, a mine, the road together, a long idle spell or work. */
    private static CompanionConversation.Scene scene(dev.aicompanion.entity.CompanionEntity speaker) {
        var client = Minecraft.getInstance(); var player = client.player; long now = now();
        if (lastSpot == null || now - sampledAt >= 100 || now < sampledAt) {
            // Eight blocks in five seconds: walking or riding somewhere, not pottering about one spot.
            if (lastSpot != null && player.position().subtract(lastSpot).horizontalDistanceSqr() > 8 * 8) travelUntil = now + 300;
            lastSpot = player.position(); sampledAt = now;
        }
        boolean travelling = now < travelUntil, working = speaker.isGatheringTask();
        if (travelling || working || idleSince > now) idleSince = now;
        boolean underground = player.getY() < client.level.getSeaLevel() - 4 && !client.level.canSeeSky(player.blockPosition());
        return CompanionConversation.scene(speaker.fighting(), underground, travelling, working, now - idleSince);
    }
    /**
     * His own topic, word for word, no model request to start talking. The topics are his memory of the player, kept in the world:
     * the server picks one and marks it raised, and it is said if nothing has happened in the meantime.
     */
    private static void raiseTopic(CompanionConversation.Scene scene) {
        var client = Minecraft.getInstance(); var server = client.getSingleplayerServer();
        if (server == null) return;
        pending = true;
        Object capturedWorld = world; UUID capturedCompanion = companion, owner = client.player.getUUID(); long activity = conversation.revision();
        server.execute(() -> {
            if (!server.isRunning()) return;
            var topic = dev.aicompanion.entity.CompanionBonds.get(server).raiseTopic(owner, java.util.concurrent.ThreadLocalRandom.current());
            client.execute(() -> {
                if (world != capturedWorld || client.level != capturedWorld) return;
                pending = false;
                String trace = dev.aicompanion.CompanionDiagnostics.newTrace();
                var speaker = CompanionVoiceAudio.speaker();
                if (conversation.revision() != activity || speaker == null || !capturedCompanion.equals(speaker.getUUID()) || !safe(speaker)
                        || !CompanionVoiceAudio.audible(speaker) || client.screen != null || client.isPaused()) {
                    dev.aicompanion.CompanionDiagnostics.event(trace, "soul_topic_dropped", "topic", topic.key()); return;
                }
                conversation.talked(now(), jitter());
                dev.aicompanion.CompanionDiagnostics.event(trace, "soul_topic", "scene", scene.name(), "topic", topic.key());
                CompanionChatScreen.ownTopic(topic.text(), trace);
            });
        });
    }
    static void tick() {
        var client = Minecraft.getInstance();
        if (client.level != world) {
            world = client.level; companion = null; pending = false; lastSpot = null; travelUntil = 0; idleSince = now();
            conversation.reset(now(), jitter());
        }
        if (client.level == null || client.player == null || client.isPaused() || client.screen != null || !OpenRouterConfig.AUTONOMOUS_SPEECH.get()) return;
        var speaker = CompanionVoiceAudio.speaker();
        if (speaker == null || !CompanionVoiceAudio.audible(speaker)) return;
        var scene = scene(speaker);
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
        if (!fresh && conversation.urgentPending() == 0) {
            // A topic needs the model to go on talking, and the memory of the player to know which topics he has raised.
            if (conversation.topicReady(now(), scene) && !busy() && safe(speaker) && OpenRouterConfig.LONG_TERM_MEMORY.get()
                    && OpenRouterClient.modelConfigured()) raiseTopic(scene);
            return;
        }
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
        OpenRouterClient.react(observation, GameContext.ambientSnapshot(speaker), trace, wasUrgent, conversation.canAskQuestion()).whenComplete((text, error) -> client.execute(() -> {
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

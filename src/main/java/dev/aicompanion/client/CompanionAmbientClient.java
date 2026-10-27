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
                && client.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(12),
                        monster -> dev.aicompanion.entity.CompanionHostiles.hostile(monster) && dev.aicompanion.entity.CompanionHostiles.observed(player, monster, 12)).isEmpty();
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
    /** His own topic, word for word from his memory: no model request to start talking. */
    private static void raiseTopic(CompanionConversation.Scene scene) {
        var soul = dev.aicompanion.CompanionSoulFiles.soul();
        var topic = soul.nextTopic(java.util.concurrent.ThreadLocalRandom.current());
        if (topic == null) return;
        soul.raised(topic, System.currentTimeMillis());
        String trace = dev.aicompanion.CompanionDiagnostics.newTrace();
        dev.aicompanion.CompanionDiagnostics.event(trace, "soul_topic", "scene", scene.name(), "topic", topic.key());
        CompanionChatScreen.autonomousReply(topic.text(), trace);
    }
    private static boolean reflecting;
    private static long reflectCheck;
    /** A finished talk goes to analysis in the background; he adapts and gets new topics. Off with his own speech. */
    private static void reflect() {
        long now = System.currentTimeMillis();
        if (reflecting || now - reflectCheck < 5000) return;
        reflectCheck = now;
        boolean possible = OpenRouterConfig.AUTONOMOUS_SPEECH.get() && !OpenRouterConfig.API_KEY.get().isBlank();
        if (possible && !OpenRouterClient.backgroundFree()) return;
        var soul = dev.aicompanion.CompanionSoulFiles.soul();
        String talk = soul.takeTalk(now, possible);
        if (talk == null) return;
        reflecting = true;
        String trace = dev.aicompanion.CompanionDiagnostics.newTrace();
        OpenRouterClient.reflect(soul.memory(), talk, trace).whenComplete((analysis, error) -> Minecraft.getInstance().execute(() -> {
            reflecting = false;
            if (error != null) { dev.aicompanion.CompanionDiagnostics.failure(trace, "soul_reflection", error); soul.failed(System.currentTimeMillis()); }
            else dev.aicompanion.CompanionDiagnostics.event(trace, "soul_reflection_result", "kept", soul.absorb(analysis, System.currentTimeMillis()));
        }));
    }
    static void tick() {
        var client = Minecraft.getInstance();
        if (client.level != world) {
            world = client.level; companion = null; pending = false; lastSpot = null; idleSince = now();
            conversation.reset(now(), jitter());
        }
        reflect();
        dev.aicompanion.CompanionSoulFiles.saveIfChanged();
        if (client.level == null || client.player == null || client.isPaused() || client.screen != null || !OpenRouterConfig.AUTONOMOUS_SPEECH.get()) return;
        var speaker = CompanionVoiceAudio.speaker();
        if (speaker == null || !CompanionVoiceAudio.audible(speaker)) return;
        var scene = scene(speaker);
        // Initial tracking data is a snapshot, not a new event. Never replay a pre-load observation.
        if (!speaker.getUUID().equals(companion)) { companion = speaker.getUUID(); conversation.trackObservation(speaker.socialSequence()); return; }
        int current = speaker.socialSequence();
        if (!conversation.newObservation(current)) {
            // A topic needs the model to go on talking; without its key he would only be heard asking.
            if (conversation.topicReady(now(), scene) && !busy() && safe(speaker) && !OpenRouterConfig.API_KEY.get().isBlank()) raiseTopic(scene);
            return;
        }
        if (!conversation.takeObservation(current, now() - speaker.socialTime(), now(), safe(speaker),
                busy(), jitter())) return;
        pending = true;
        Object capturedWorld = world; UUID capturedCompanion = companion;
        long activity = conversation.revision(); String observation = speaker.socialObservation();
        String trace = dev.aicompanion.CompanionDiagnostics.newTrace();
        OpenRouterClient.react(observation, GameContext.ambientSnapshot(speaker), trace).whenComplete((text, error) -> client.execute(() -> {
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

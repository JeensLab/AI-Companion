package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.client.CompanionActionExecutor;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionTaskQueue;
import dev.aicompanion.client.GameContext;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/** Opt-in paid integration in a disposable client world. Excluded from the published JAR. */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID, value = Dist.CLIENT)
public final class CompanionResponsesClientProbe {
    private static final String[] MODELS = {"gpt-6-luna", "gpt-6.1-terra"};
    private static boolean started, finished;
    private static long deadline;
    private static long taskBefore;
    private static int stage, model, turn;
    private static CompletableFuture<Boolean> serverCheck;
    private static CompletableFuture<CompanionReply> answer;
    private static CompletableFuture<String> execution;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("aicompanion.responsesProbe") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!started && mc.screen instanceof TitleScreen) {
                if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("allow-paid-responses-probe")))
                    throw new AssertionError("Explicit paid probe marker missing");
                if (OpenRouterConfig.API_KEY.get().isBlank()
                        || !OpenRouterClient.endpoint(OpenRouterConfig.API_URL.get()).toString().equals("https://api.openai.com/v1/responses"))
                    throw new AssertionError("Direct OpenAI config required");
                started = true; deadline = System.nanoTime() + java.time.Duration.ofMinutes(12).toNanos();
                OpenRouterConfig.INWORLD_API_KEY.set("");
                OpenRouterConfig.WINDOWS_VOICE.set(false); OpenRouterConfig.MACOS_VOICE.set(false);
                OpenRouterConfig.LONG_TERM_MEMORY.set(false); OpenRouterConfig.AUTONOMOUS_SPEECH.set(false); OpenRouterConfig.JOKES.set(false);
                mc.options.renderDistance().set(6); mc.options.simulationDistance().set(5); mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel("ResponsesQA-" + System.currentTimeMillis(),
                        new LevelSettings("OpenAI Responses QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(0L, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            if (!started) return;
            if (System.nanoTime() > deadline) throw new AssertionError("Paid client probe timed out");
            if (mc.level == null || mc.player == null) return;
            if (stage == 0) {
                if (serverCheck == null) serverCheck = fixture(mc);
                if (!serverCheck.isDone()) return;
                boolean ready = serverCheck.join(); serverCheck = null;
                if (!ready) return;
                OpenRouterConfig.MODEL.set(MODELS[model]); OpenRouterClient.resetSession();
                turn = 0; ask(mc, "Наше кодовое слово — лещ. Просто поздоровайся, без игровых действий."); stage = 1;
            } else if (stage == 1) {
                if (!answer.isDone()) return;
                CompanionReply reply = answer.join();
                mc.gui.getChat().addMessage(Component.literal(MODELS[model] + ": " + reply.text()));
                if (turn == 0) {
                    check(reply.actions().isEmpty() && !reply.text().isBlank(), "Greeting did not remain dialogue");
                    turn++; ask(mc, "Какое кодовое слово я назвал в предыдущей реплике? Ответь коротко, без действий.");
                } else if (turn == 1) {
                    check(reply.actions().isEmpty() && reply.text().toLowerCase(java.util.Locale.ROOT).contains("лещ"), "Text history lost");
                    turn++; ask(mc, "Подойди ко мне, пожалуйста.");
                } else {
                    check(reply.actions().size() == 1 && reply.actions().getFirst().type().equals("come"), "No native come plan");
                    taskBefore = CompanionActionExecutor.tasks().stream().mapToLong(CompanionTaskQueue.View::id).max().orElse(0);
                    execution = CompanionActionExecutor.execute(reply.actions()); stage = 2;
                }
            } else if (stage == 2) {
                if (!execution.isDone()) return;
                execution.join();
                check(CompanionActionExecutor.tasks().stream().anyMatch(task -> task.id() > taskBefore && task.status() == CompanionTaskQueue.Status.COMPLETED
                        && task.steps().getFirst().type().equals("come")), "Queue did not complete the plan");
                serverCheck = new CompletableFuture<>(); var result = serverCheck; var id = mc.player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                        var companion = CompanionEvents.getOwnedCompanion(player);
                        result.complete(companion != null && !companion.hasActiveWorkOrder() && companion.distanceToSqr(player) <= 3 * 3);
                    } catch (Throwable error) { result.completeExceptionally(error); }
                });
                stage = 3;
            } else if (stage == 3 && serverCheck.isDone()) {
                check(serverCheck.join(), "Companion did not physically approach the player"); serverCheck = null;
                AICompanionMod.LOGGER.info("RESPONSES_CLIENT_MODEL_PASSED: {} dialogue, history, native plan, completed queue, physical approach", MODELS[model]);
                if (++model == MODELS.length) {
                    finished = true; AICompanionMod.LOGGER.info("RESPONSES_CLIENT_PROBE_PASSED: 2 models, 6 requests"); mc.stop();
                } else stage = 0;
            }
        } catch (Throwable error) {
            finished = true;
            // Provider errors may contain user data. Details stay in the existing private diagnostics.
            AICompanionMod.LOGGER.error("RESPONSES_CLIENT_PROBE_FAILED: model={}, stage={}, turn={}, error={}",
                    MODELS[Math.min(model, MODELS.length - 1)], stage, turn, error.getClass().getSimpleName());
            mc.stop();
        }
    }

    private static void ask(Minecraft mc, String text) {
        mc.gui.getChat().addMessage(Component.literal("Тест: " + text));
        answer = OpenRouterClient.send(text, GameContext.snapshot(text));
    }

    private static CompletableFuture<Boolean> fixture(Minecraft mc) {
        CompletableFuture<Boolean> result = new CompletableFuture<>(); var id = mc.player.getUUID(); var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(id); var companion = CompanionEvents.getOwnedCompanion(player);
                if (companion == null) { result.complete(false); return; }
                var level = player.serverLevel(); var base = player.blockPosition();
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                for (int x = -4; x <= 14; x++) for (int z = -4; z <= 4; z++) for (int y = -1; y <= 3; y++)
                    level.setBlockAndUpdate(base.offset(x, y, z), (y < 0 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                companion.setFollowing(false); companion.setPos(base.getX() + 10.5, base.getY(), base.getZ() + .5);
                player.connection.teleport(base.getX() + .5, base.getY(), base.getZ() + .5, -90, 0);
                result.complete(companion.distanceToSqr(player) >= 9 * 9);
            } catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}

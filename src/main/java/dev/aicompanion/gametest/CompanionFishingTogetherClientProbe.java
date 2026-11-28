package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionConversation;
import dev.aicompanion.CompanionFishingInvitation;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.client.CompanionChatScreen;
import dev.aicompanion.client.CompanionVoiceAudio;
import dev.aicompanion.entity.CompanionBobber;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

import java.util.List;

/** Offline client check of a real ambient invite, typed consent and a real cast that is removed by «стоп». */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID, value = Dist.CLIENT)
public final class CompanionFishingTogetherClientProbe {
    private static boolean started;
    private static int ticks, phase;
    private static volatile boolean pondReady;

    private CompanionFishingTogetherClientProbe() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws ReflectiveOperationException {
        if (!Boolean.getBoolean("aicompanion.fishingTogetherProbe")) return;
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 3600) throw new IllegalStateException("Fishing together client probe timed out at phase " + phase);
        if (!started && mc.screen instanceof TitleScreen) {
            if (!OpenRouterConfig.API_KEY.get().isBlank() || !OpenRouterConfig.INWORLD_API_KEY.get().isBlank()
                    || !OpenRouterConfig.API_URL.get().isBlank())
                throw new IllegalStateException("Fishing probe requires the unconfigured disposable profile");
            started = true;
            OpenRouterConfig.JOKES.set(false);
            OpenRouterConfig.LONG_TERM_MEMORY.set(false);
            OpenRouterConfig.WINDOWS_VOICE.set(false);
            OpenRouterConfig.MACOS_VOICE.set(false);
            OpenRouterConfig.AUTONOMOUS_SPEECH.set(true);
            OpenRouterConfig.FISHING_INVITATIONS.set(true);
            OpenRouterConfig.FISHING_INVITE_MINUTES.set(20);
            mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("FishingTogether-" + System.currentTimeMillis(),
                    new LevelSettings("Offline fishing together QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            return;
        }
        var server = mc.getSingleplayerServer();
        var oleg = CompanionVoiceAudio.speaker();
        if (!started || mc.level == null || mc.player == null || server == null || oleg == null) return;
        if (mc.screen != null && mc.screen.isPauseScreen()) mc.setScreen(null);
        if (phase == 0) {
            phase = 1;
            var owner = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(owner);
                var companion = CompanionEvents.getOwnedCompanion(player);
                var level = player.serverLevel();
                var bank = player.blockPosition().immutable();
                for (int x = 3; x <= 11; x++) for (int z = -4; z <= 4; z++)
                    level.setBlockAndUpdate(bank.offset(x, -1, z), Blocks.WATER.defaultBlockState());
                companion.moveTo(bank.getX() + .5, bank.getY(), bank.getZ() + .5, 0, 0);
                companion.setItem(0, new ItemStack(Items.FISHING_ROD));
                pondReady = true;
            });
        } else if (phase == 1 && pondReady && mc.screen == null
                && dev.aicompanion.entity.CompanionFishing.waterNearby(mc.level, oleg.blockPosition())) {
            // Advance only the local invitation timer; actual ambient checks and the route through chat still run.
            Class<?> ambient = Class.forName("dev.aicompanion.client.CompanionAmbientClient");
            var timer = (CompanionFishingInvitation) field(ambient, "fishing");
            var conversation = (CompanionConversation) field(ambient, "conversation");
            long now = mc.level.getGameTime();
            timer.offered(now - 24000, 20, 0);
            timer.cancel();
            conversation.reset(now - 100000, 0);
            conversation.trackObservation(oleg.socialSequence());
            phase = 2;
        } else if (phase == 2 && historyContains("Составишь компанию?")) {
            if (hookVisible(mc)) throw new IllegalStateException("An invitation started fishing without consent");
            phase = 3;
            say(mc, "Поехали, коллега.");
        } else if (phase == 3 && hookVisible(mc)) {
            phase = 4;
            say(mc, "стоп");
        } else if (phase == 4 && !hookVisible(mc)) {
            phase = 5;
            say(mc, "Порыбачь");
        } else if (phase == 5 && hookVisible(mc)) {
            phase = 6;
            say(mc, "стоп");
        } else if (phase == 6 && !hookVisible(mc)) {
            phase = 7;
            AICompanionMod.LOGGER.info("FISHING_TOGETHER_CLIENT_PASS: ambient invitation, typed casual consent, direct fishing, both casts stopped");
            mc.stop();
        }
    }

    private static boolean hookVisible(Minecraft mc) {
        return !mc.level.getEntitiesOfClass(CompanionBobber.class, mc.player.getBoundingBox().inflate(32)).isEmpty();
    }

    private static Object field(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static boolean historyContains(String text) throws ReflectiveOperationException {
        return ((List<?>) field(CompanionChatScreen.class, "conversation")).stream().map(Component.class::cast)
                .anyMatch(line -> line.getString().contains(text));
    }

    private static void say(Minecraft mc, String text) {
        var screen = new CompanionChatScreen();
        mc.setScreen(screen);
        screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow().setValue(text);
        screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Отправить")).findFirst().orElseThrow().onPress();
        mc.setScreen(null);
    }
}

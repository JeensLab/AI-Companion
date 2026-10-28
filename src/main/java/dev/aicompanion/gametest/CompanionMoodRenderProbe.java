package dev.aicompanion.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionPersonality.Trait;
import dev.aicompanion.OpenRouterConfig;
import dev.aicompanion.client.CompanionVoiceAudio;
import dev.aicompanion.entity.CompanionBonds;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
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
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

/** Opt-in check of the mood panel in a disposable offline world: shown, toggled by N, screenshots. Never shipped in the mod JAR. */
@EventBusSubscriber(modid=AICompanionMod.MOD_ID, value=Dist.CLIENT)
public final class CompanionMoodRenderProbe {
    private static boolean started;
    private static int phase, ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("aicompanion.moodProbe")) return;
        var mc=Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started=true;
            mc.createWorldOpenFlows().createFreshLevel("OlegMood-"+System.currentTimeMillis(),
                    new LevelSettings("Offline mood QA",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0L,false,false),registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        }
        if (mc.level==null || mc.player==null) return;
        if (++ticks<60) return;
        ticks=0;
        switch (phase++) {
            case 0 -> {
                mc.options.guiScale().set(3);mc.resizeDisplay();mc.setScreen(null);
                OpenRouterConfig.MOOD_PANEL.set(true);
                var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
                // A character shifted by talks, so the marks on both sides of the middle are lit.
                server.execute(() -> {
                    var player=server.getPlayerList().getPlayer(id);
                    CompanionEvents.getOwnedCompanion(player);
                    CompanionBonds.get(server).adapt(id,Map.of(Trait.TEASE,1,Trait.SWEARING,-1,Trait.HUMOR,1,Trait.WARMTH,1,Trait.CURIOSITY,-1,Trait.ENERGY,1),List.of());
                    CompanionBonds.get(server).adapt(id,Map.of(Trait.HUMOR,2,Trait.SWEARING,-2),List.of());
                });
            }
            case 1 -> check(CompanionVoiceAudio.speaker()!=null,"No companion for the panel");
            case 2 -> shot(mc,"oleg-mood-panel.png");
            case 3 -> { mc.options.guiScale().set(4);mc.resizeDisplay(); }
            case 4 -> shot(mc,"oleg-mood-panel-compact.png");
            case 5 -> {
                mc.options.guiScale().set(3);mc.resizeDisplay();
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_N));
            }
            case 6 -> { check(!OpenRouterConfig.MOOD_PANEL.get(),"N did not hide the mood panel"); shot(mc,"oleg-mood-panel-off.png"); }
            case 7 -> KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_N));
            case 8 -> {
                check(OpenRouterConfig.MOOD_PANEL.get(),"N did not bring the mood panel back");
                AICompanionMod.LOGGER.info("MOOD_PANEL_PROBE_PASSED: shown with the companion, N hides and shows it, screenshots at GUI scale 3 and 4");
                mc.stop();
            }
        }
    }
    private static void check(boolean condition,String error) { if(!condition)throw new IllegalStateException(error); }
    private static void shot(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->AICompanionMod.LOGGER.info("Mood panel screenshot: {}",message.getString()));
    }
}

package dev.aicompanion.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionActionExecutor;
import dev.aicompanion.client.CompanionSchematicPlacement;
import dev.aicompanion.client.PlannedAction;
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
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in check of the schematic placement in a real client: ghost, rotation, Enter, the building ghost. Never shipped in the mod JAR. */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID, value = Dist.CLIENT)
public final class CompanionSchematicRenderProbe {
    private static final String FILE = "probe_house.litematic";
    private static boolean started;
    private static int phase, ticks;
    private static final AtomicReference<String> placed = new AtomicReference<>();

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("aicompanion.schematicProbe")) return;
        var mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            mc.createWorldOpenFlows().createFreshLevel("OlegSchematic-" + System.currentTimeMillis(),
                    new LevelSettings("Schematic QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (mc.level == null || mc.player == null) return;
        if (++ticks < 40) return;
        ticks = 0;
        switch (phase++) {
            case 0 -> {
                CompanionHouseTests.write(FILE, CompanionHouseTests.litematic(5, 4, 5, CompanionHouseTests::house));
                mc.player.setXRot(35); mc.player.setYRot(0);
                CompanionActionExecutor.execute(List.of(new PlannedAction("follow", 0, 1)));
            }
            case 1 -> CompanionActionExecutor.execute(List.of(new PlannedAction("build_house", 0, 1)));
            case 2 -> { check(CompanionSchematicPlacement.placing(), "Placement did not open"); shot(mc, "oleg-schematic-ghost.png"); }
            case 3 -> KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
            case 4 -> { check(CompanionSchematicPlacement.placing(), "Rotation closed the placement"); shot(mc, "oleg-schematic-rotated.png"); }
            case 5 -> KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_ENTER));
            case 6 -> {
                check(!CompanionSchematicPlacement.placing(), "Enter did not set the schematic down");
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.execute(() -> {
                    var companion = CompanionEvents.getOwnedCompanion(server.getPlayerList().getPlayer(id));
                    placed.set(companion == null || companion.housePlacement() == null ? "none" : companion.housePlacement().toString());
                });
            }
            case 7 -> {
                check(placed.get() != null && !"none".equals(placed.get()), "The companion did not take the placement: " + placed.get());
                check(CompanionSchematicPlacement.showingBuild(), "The building ghost is not shown");
                shot(mc, "oleg-schematic-building.png");
            }
            case 8 -> {
                mc.setScreen(new dev.aicompanion.client.CompanionSettingsScreen(null));
                press(mc, "Клавиши схем");
                press(mc, "Повернуть");
                mc.screen.keyPressed(GLFW.GLFW_KEY_K, 0, 0);
                check(rotate(mc).getKey().getValue() == GLFW.GLFW_KEY_K, "Rotation was not rebound in the settings");
            }
            case 9 -> shot(mc, "oleg-schematic-keys.png");
            case 10 -> {
                press(mc, "Сброс");
                check(rotate(mc).getKey().getValue() == GLFW.GLFW_KEY_R, "Reset did not restore R");
                mc.setScreen(null);
                CompanionActionExecutor.cancelPendingPlans();
                // The game's own chat: a direct command, no model request.
                mc.player.connection.sendChat("стой здесь");
            }
            case 11 -> {
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.execute(() -> {
                    var companion = CompanionEvents.getOwnedCompanion(server.getPlayerList().getPlayer(id));
                    placed.set(companion == null ? "none" : companion.isFollowing() ? "following" : "stays");
                });
            }
            case 12 -> {
                check("stays".equals(placed.get()), "The game chat did not reach the companion: " + placed.get());
                CompanionHouseTests.delete(FILE);
                AICompanionMod.LOGGER.info("SCHEMATIC_PROBE_PASSED: ghost shown, rotated by R, set down by Enter, taken by the companion, keys rebound in settings, game chat heard");
                mc.stop();
            }
            default -> { }
        }
    }
    private static KeyMapping rotate(Minecraft mc) {
        for (KeyMapping key : mc.options.keyMappings) if (key.getName().equals("key.aicompanion.schematic_rotate")) return key;
        throw new IllegalStateException("No rotation key");
    }
    private static void press(Minecraft mc, String prefix) {
        mc.screen.children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.Button b && b.active && b.getMessage().getString().startsWith(prefix))
                .map(w -> (net.minecraft.client.gui.components.Button) w).findFirst().orElseThrow(() -> new IllegalStateException("No enabled button: " + prefix)).onPress();
    }
    private static void check(boolean condition, String error) { if (!condition) throw new IllegalStateException(error); }
    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> AICompanionMod.LOGGER.info("Schematic screenshot: {}", message.getString()));
    }
}

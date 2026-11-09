package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionVoiceAudio;
import dev.aicompanion.entity.CompanionCampfire;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Opt-in offline render QA of the evening by the fire (-Daicompanion.campfireProbe=true on runClient; the gametest package is
 * excluded from the release JAR): a fresh flat world, a lit campfire, «сыграй», then screenshots of him sitting with the guitar from
 * the front, the side and at night into the client's screenshots folder, and a check that the client streamed his guitar recording
 * (keep the master volume above zero, the game skips silent sounds). No provider key is needed.
 */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID, value = Dist.CLIENT)
public final class CompanionCampfireRenderProbe {
    private static boolean started, asked, placed;
    // The client began streaming one of his guitar recordings (the sound itself, not only the server's word for it).
    private static volatile String streamed;
    private static int ticks, shot;
    private static final String[] SHOTS = {"campfire-front", "campfire-side", "campfire-three-quarter", "campfire-night"};
    private static final double[][] VIEWS = {{0, 4.2}, {3.2, 0}, {2.4, 2.4}, {-1.8, 3.6}};

    private CompanionCampfireRenderProbe() { }

    @SubscribeEvent
    public static void streamed(net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent event) {
        var id = event.getSound().getLocation();
        if (Boolean.getBoolean("aicompanion.campfireProbe") && id.getNamespace().equals(AICompanionMod.MOD_ID) && id.getPath().startsWith("guitar.")) streamed = id.toString();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("aicompanion.campfireProbe")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started = true;
            mc.createWorldOpenFlows().createFreshLevel("OlegCampfire-" + System.currentTimeMillis(),
                    new LevelSettings("Offline campfire QA", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            return;
        }
        CompanionEntity oleg = CompanionVoiceAudio.speaker();
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || oleg == null || server == null) return;
        ticks++;
        if (ticks > 20 * 120) throw new IllegalStateException("Campfire probe timed out at shot " + shot + ", pose " + oleg.campfirePose());
        if (!asked && ticks > 40) {
            asked = true;
            mc.options.hideGui = true;
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                var level = player.serverLevel();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                BlockPos fire = player.blockPosition().relative(player.getDirection(), 3);
                level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), 3);
                var result = CompanionEvents.executeAction(player, "campfire_song", "", 1);
                AICompanionMod.LOGGER.info("Campfire probe: {} (fire {})", result.message(), fire);
            });
            return;
        }
        if (!asked || oleg.campfirePose() != CompanionCampfire.PLAYING) return;
        // One view every two seconds once he plays: the camera moves, the frame after settles, then the picture.
        int phase = ticks % 40;
        if (phase == 0) {
            placed = true;
            if (shot >= SHOTS.length) {
                if (streamed == null) throw new IllegalStateException("No guitar recording was streamed by the client");
                AICompanionMod.LOGGER.info("CAMPFIRE_RENDER_PROBE_PASSED: {} screenshots, streamed {}", SHOTS.length, streamed);
                mc.stop();
                return;
            }
            double[] view = VIEWS[shot];
            Vec3 at = oleg.position();
            float yaw = oleg.yBodyRot;
            // The views are in his own frame: forward is where he faces, right his right hand.
            Vec3 forward = Vec3.directionFromRotation(0, yaw), right = new Vec3(-forward.z, 0, forward.x);
            Vec3 eye = at.add(forward.scale(view[1])).add(right.scale(view[0])).add(0, 1.8, 0);
            Vec3 look = at.add(0, .6, 0).subtract(eye);
            float viewYaw = (float) (Math.toDegrees(Math.atan2(-look.x, look.z)));
            float viewPitch = (float) (-Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z))));
            boolean night = SHOTS[shot].endsWith("night");
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (night) player.serverLevel().setDayTime(18000);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.connection.teleport(eye.x, eye.y - player.getEyeHeight(), eye.z, viewYaw, viewPitch);
            });
        } else if (phase == 30 && placed) {
            placed = false;
            Screenshot.grab(mc.gameDirectory, SHOTS[shot] + ".png", mc.getMainRenderTarget(),
                    message -> AICompanionMod.LOGGER.info("Campfire probe screenshot: {}", message.getString()));
            shot++;
        }
    }
}

package dev.aicompanion.gametest;

import com.mojang.authlib.GameProfile;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/**
 * Development only (excluded from release JAR). A long goal across real process restarts: each stage is a fresh JVM and a
 * normal dedicated world save. prepare: the goal starts and the world stops after the first piece; resume: the goal is read
 * from disk and finishes from the facts without a second piece; verify: the completed goal stays completed.
 */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID)
public final class CompanionAgendaRestartProbe {
    private static final String STAGE = System.getProperty("aicompanion.agendaRestartStage", "disabled");
    private static final UUID PLAYER = UUID.fromString("5d1e7a40-3c39-4f0b-9a51-7b0c2f1e8a17");
    private static final Path MANIFEST = Path.of("agenda-probe.properties");
    private static final Properties manifest = new Properties();
    private static ServerPlayer player;
    private static CompanionEntity companion;
    private static int ticks;
    private static boolean finished, initialized;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (STAGE.equals("disabled")) return;
        try {
            if (Files.exists(MANIFEST)) try (var in = Files.newInputStream(MANIFEST)) { manifest.load(in); }
            check(STAGE.equals("prepare") ? manifest.isEmpty() : !manifest.isEmpty(), "Fresh/previous world manifest mismatch");
            MinecraftServer server = event.getServer(); var level = server.overworld();
            level.setChunkForced(0, 0, true); level.setChunkForced(1, 0, true); level.setChunkForced(0, 1, true); level.setChunkForced(1, 1, true);
            var cookie = CommonListenerCookie.createInitial(new GameProfile(PLAYER, "agenda-restart-probe"), false);
            player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            server.tickRateManager().setTickRate(200);
        } catch (Exception ex) { fail(event.getServer(), ex); }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (STAGE.equals("disabled") || finished || player == null) return;
        var server = event.getServer();
        try {
            ticks++;
            if (!initialized) {
                companion = CompanionEvents.getOwnedCompanion(player);
                if (companion == null) { check(ticks < 200, "Canonical companion did not load from entity region"); return; }
                if (STAGE.equals("prepare")) setup(server);
                else {
                    var goal = goal(server);
                    check(companion.getUUID().toString().equals(manifest.getProperty("companion")), "Companion UUID changed across process");
                    check(goal != null && goal.id().toString().equals(manifest.getProperty("goal")), "Goal did not survive the restart");
                    if (STAGE.equals("resume")) check(manifest.getProperty("stage").equals("prepare") && goal.state().outstanding(), "Goal not outstanding after restart: " + goal.state());
                    if (STAGE.equals("verify")) check(manifest.getProperty("stage").equals("resume") && goal.state() == CompanionAgenda.State.COMPLETED, "Completed goal was not saved");
                }
                initialized = true;
            }
            var goal = goal(server);
            if (STAGE.equals("prepare") && count(Items.IRON_PICKAXE) == 1 && goal.state() == CompanionAgenda.State.ACTIVE) pass(server);
            if (STAGE.equals("resume") && goal.state() == CompanionAgenda.State.COMPLETED) { ironSet(); pass(server); }
            if (STAGE.equals("verify") && ticks > 160) {
                check(goal.state() == CompanionAgenda.State.COMPLETED, "Completed goal restarted: " + goal.state());
                ironSet(); pass(server);
            }
            check(ticks < 6000, "Stage timeout: " + (goal == null ? "no goal" : goal.state() + " " + goal.reason()) + " / " + companion.getLastTaskResult());
        } catch (Exception ex) { fail(server, ex); }
    }

    private static void setup(MinecraftServer server) {
        var level = server.overworld();
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.BEDROCK.defaultBlockState());
            for (int y = 81; y <= 88; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(new BlockPos(17, 81, 16), Blocks.CRAFTING_TABLE.defaultBlockState());
        player.teleportTo(11.5, 81, 11.5); companion.teleportTo(16.5, 81, 16.5); companion.setLifestyle(false, false); companion.setSearchRadius(16);
        companion.setItem(0, new ItemStack(Items.IRON_INGOT, 29)); companion.setItem(1, new ItemStack(Items.STICK, 3));
        check(CompanionEvents.executeAction(player, "pursue_goal", "gear_up", 1).status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted");
    }

    private static CompanionAgendaData.Goal goal(MinecraftServer server) { return CompanionAgenda.goal(server, PLAYER); }

    private static void ironSet() {
        check(companion.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && companion.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                && companion.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS) && companion.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "Armour not worn");
        check(count(Items.IRON_PICKAXE) == 1 && count(Items.IRON_SWORD) == 1 && count(Items.IRON_INGOT) == 0, "A piece made twice or ingots left: " + companion.getStoredItemsSummary());
    }

    private static int count(net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < companion.getContainerSize(); i++) if (companion.getItem(i).is(item)) n += companion.getItem(i).getCount();
        for (var slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) n += companion.getItemBySlot(slot).getCount();
        return n;
    }

    private static void pass(MinecraftServer server) throws Exception {
        var goal = goal(server);
        manifest.setProperty("stage", STAGE); manifest.setProperty("companion", companion.getUUID().toString());
        manifest.setProperty("goal", goal.id().toString()); manifest.setProperty("state", goal.state().name());
        manifest.setProperty("pickaxes", Integer.toString(count(Items.IRON_PICKAXE)));
        try (var out = Files.newOutputStream(MANIFEST)) { manifest.store(out, "Dedicated process persistence probe of a long goal"); }
        System.out.println("AGENDA_RESTART_PASS stage=" + STAGE + " companion=" + companion.getUUID() + " goal=" + goal.id() + " state=" + goal.state());
        finished = true; server.halt(false);
    }

    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    private static void fail(MinecraftServer server, Exception ex) { finished = true; System.out.println("AGENDA_RESTART_FAIL stage=" + STAGE + " " + ex); ex.printStackTrace(); server.halt(false); }
}

package dev.aicompanion.gametest;

import com.mojang.authlib.GameProfile;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Development only (excluded from release JAR). A find_diamonds goal across real process restarts: each stage is a fresh JVM
 * and a normal dedicated world save. prepare: a mountain with a diamond pocket far along the tunnel, the goal starts and the
 * world stops mid-step in the tunnel; resume: the same mine is read from disk, he goes on in it under his own chunk tickets
 * while the player is far away, and gets the diamond; verify: the completed goal and the mine stay as saved.
 */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID)
public final class CompanionMineRestartProbe {
    private static final String STAGE = System.getProperty("aicompanion.mineRestartStage", "disabled");
    private static final UUID PLAYER = UUID.fromString("7c2b9d10-5e48-4a63-8b0f-3d9a6e1c4f52");
    private static final Path MANIFEST = Path.of("mine-probe.properties");
    private static final Properties manifest = new Properties();
    // The arena of CompanionDiamondGoalTests moved into the flat world and stretched north, so the pocket lies several advances past the stair foot.
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2;
    private static final int NORTH_EXTRA = 24, ORIGIN_X = 0, ORIGIN_Y = -60, ORIGIN_Z = NORTH_EXTRA, POCKET_Z = -12, POCKET_X0 = 22, POCKET_X1 = 25;
    private static final int FAR_PLAYER_X = 400, PREPARE_ADVANCES = 2, UNDERGROUND_DEPTH = 3;
    private static final int PREPARE_TIMEOUT = 30000, RESUME_TIMEOUT = 30000, VERIFY_TICKS = 160, FROZEN_TICKS = 1200, NO_TICKET_TICKS = 200;
    private static ServerPlayer player;
    private static CompanionEntity companion;
    private static int ticks, frozenFor, noTicketFor;
    private static boolean finished, initialized, farAway;
    private static String progress = "";

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (STAGE.equals("disabled")) return;
        try {
            if (Files.exists(MANIFEST)) try (var in = Files.newInputStream(MANIFEST)) { manifest.load(in); }
            check(STAGE.equals("prepare") ? manifest.isEmpty() : !manifest.isEmpty(), "Fresh/previous world manifest mismatch");
            MinecraftServer server = event.getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(PLAYER, "mine-restart-probe"), false);
            player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setInvulnerable(true);
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
                if (companion == null) { check(ticks < 400, "Canonical companion did not load from entity region"); return; }
                if (STAGE.equals("prepare")) setup(server);
                else restored(server);
                initialized = true;
                return;
            }
            var goal = goal(server);
            check(goal != null, "No goal");
            check(companion.isAlive(), "Companion died at " + companion.blockPosition().toShortString() + " | " + report(goal));
            switch (STAGE) {
                case "prepare" -> prepareTick(server, goal);
                case "resume" -> resumeTick(server, goal);
                default -> verifyTick(server, goal);
            }
        } catch (Exception ex) { fail(server, ex); }
    }

    private static BlockPos world(int x, int y, int z) { return new BlockPos(ORIGIN_X + x, ORIGIN_Y + y, ORIGIN_Z + z); }

    private static void setup(MinecraftServer server) {
        var level = server.overworld();
        for (int x = 0; x < SIZE; x++) for (int z = -NORTH_EXTRA; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == -NORTH_EXTRA || x == SIZE - 1 || z == SIZE - 1;
            boolean ore = y == DIAMOND_FEET - 1 && z == POCKET_Z && x >= POCKET_X0 && x <= POCKET_X1;
            var block = y == 0 || edge ? Blocks.BEDROCK
                    : ore ? Blocks.DEEPSLATE_DIAMOND_ORE
                    : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(world(x, y, z), block.defaultBlockState(), 2 | 16);
        }
        BlockPos at = world(CENTER, GROUND + 1, CENTER), near = world(CENTER - 3, GROUND + 1, CENTER - 3);
        player.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        companion.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        companion.setOwnerId(PLAYER); companion.setSearchRadius(16); companion.setLifestyle(false, false);
        ItemStack[] bag = {new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 32), new ItemStack(Items.COBBLESTONE, 8),
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE)};
        for (int slot = 0; slot < bag.length; slot++) companion.setItem(slot, bag[slot]);
        check(CompanionEvents.executeAction(player, "pursue_goal", "find_diamonds", 1).status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted");
    }

    private static void prepareTick(MinecraftServer server, CompanionAgendaData.Goal goal) throws Exception {
        check(goal.state() != CompanionAgenda.State.BLOCKED && goal.state() != CompanionAgenda.State.COMPLETED, "The goal ended before the stop: " + report(goal));
        check(ticks < PREPARE_TIMEOUT, "Stage timeout: " + report(goal));
        var site = companion.mineSite();
        boolean underground = companion.blockPosition().getY() <= ORIGIN_Y + GROUND - UNDERGROUND_DEPTH;
        if (!site.exists() || advanced() < PREPARE_ADVANCES || !underground || companion.activeWorkId() == 0 || count(Items.DIAMOND) > 0) return;
        check(pocketLeft(server) == pocketSize(), "The pocket was touched before the stop");
        record(goal);
        manifest.setProperty("opened", Integer.toString(site.opened()));
        manifest.setProperty("pocket", Integer.toString(pocketLeft(server)));
        pass(server, goal);
    }

    private static void restored(MinecraftServer server) {
        var goal = goal(server);
        var site = companion.mineSite();
        check(companion.getUUID().toString().equals(manifest.getProperty("companion")), "Companion UUID changed across process");
        check(goal != null && goal.id().toString().equals(manifest.getProperty("goal")), "Goal did not survive the restart");
        check(site.exists() && Long.toString(site.entrance().asLong()).equals(manifest.getProperty("entrance")), "The mine entrance changed or was lost: " + site.entrance());
        check(manifest.getProperty("stage").equals(STAGE.equals("resume") ? "prepare" : "resume"), "Manifest is from stage " + manifest.getProperty("stage"));
        check(standsText().equals(manifest.getProperty("stands")) && tunnelsText().equals(manifest.getProperty("tunnels")),
                "The mine record changed over the restart: stands " + site.stands().size() + "/" + manifest.getProperty("standCount") + ", tunnels " + tunnelsText() + "/" + manifest.getProperty("tunnels"));
        if (STAGE.equals("resume")) {
            check(goal.state().outstanding(), "Goal not outstanding after restart: " + goal.state());
            check(Integer.toString(site.opened()).equals(manifest.getProperty("opened")), "Opened count changed: " + site.opened() + "/" + manifest.getProperty("opened"));
            manifest.setProperty("openCellsChecked", Integer.toString(checkRecordedCellsOpen(server)));
        } else {
            check(goal.state() == CompanionAgenda.State.COMPLETED, "Completed goal was not saved: " + goal.state());
            check(count(Items.DIAMOND) == Integer.parseInt(manifest.getProperty("diamond")), "Diamonds in the bag changed: " + count(Items.DIAMOND) + "/" + manifest.getProperty("diamond"));
        }
    }

    private static void resumeTick(MinecraftServer server, CompanionAgendaData.Goal goal) throws Exception {
        check(goal.state() != CompanionAgenda.State.BLOCKED, "The goal stopped: " + report(goal));
        check(ticks < RESUME_TIMEOUT, "Stage timeout: " + report(goal));
        sameMine();
        String now = companion.blockPosition().toShortString() + "|" + companion.mineSite().opened() + "|" + count(Items.DIAMOND) + "|" + goal.state();
        frozenFor = now.equals(progress) ? frozenFor + 1 : 0;
        progress = now;
        check(frozenFor < FROZEN_TICKS, "No progress for " + FROZEN_TICKS + " ticks, frozen at " + now + (farAway ? " with the player far away" : "") + " | " + report(goal));
        boolean underground = companion.blockPosition().getY() <= ORIGIN_Y + GROUND - UNDERGROUND_DEPTH;
        if (underground && companion.activeWorkId() != 0) {
            noTicketFor = CompanionChunkTicket.held(companion.getUUID()) ? 0 : noTicketFor + 1;
            check(noTicketFor < NO_TICKET_TICKS, "Underground for " + NO_TICKET_TICKS + " ticks without his chunk ticket | " + report(goal));
            if (!farAway && noTicketFor == 0) {
                farAway = true;
                manifest.setProperty("farAtOpened", Integer.toString(companion.mineSite().opened()));
                player.teleportTo(FAR_PLAYER_X + 0.5, ORIGIN_Y, ORIGIN_Z + CENTER + 0.5);
            }
        }
        if (goal.state() != CompanionAgenda.State.COMPLETED) return;
        check(farAway, "The goal completed without ever being underground under a ticket | " + report(goal));
        var site = companion.mineSite();
        int diamonds = count(Items.DIAMOND), left = pocketLeft(server), broken = pocketSize() - left, onGround = groundDiamonds(server);
        check(diamonds >= 1, "Completed without a diamond in the bag | " + report(goal));
        check(diamonds + onGround <= broken, "Diamonds duplicated: bag " + diamonds + " + ground " + onGround + " from " + broken + " broken ore | " + report(goal));
        check(count(Items.TORCH) <= Integer.parseInt(manifest.getProperty("torch")) && count(Items.STONE_PICKAXE) <= Integer.parseInt(manifest.getProperty("stone_pickaxe"))
                && count(Items.IRON_PICKAXE) <= 1, "Torches or pickaxes above what the stop had: " + companion.getStoredItemsSummary());
        check(site.opened() > Integer.parseInt(manifest.getProperty("opened")), "No new cells after the restart | " + report(goal));
        var floor = world(CENTER, GROUND + 1, CENTER);
        player.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
        record(goal);
        manifest.setProperty("opened", Integer.toString(site.opened()));
        manifest.setProperty("pocket", Integer.toString(left));
        manifest.setProperty("groundDiamonds", Integer.toString(onGround));
        pass(server, goal);
    }

    private static void verifyTick(MinecraftServer server, CompanionAgendaData.Goal goal) throws Exception {
        check(goal.state() == CompanionAgenda.State.COMPLETED, "Completed goal restarted: " + goal.state());
        check(!CompanionChunkTicket.held(companion.getUUID()), "A chunk ticket outlived the goal");
        if (ticks <= VERIFY_TICKS) return;
        check(standsText().equals(manifest.getProperty("stands")) && tunnelsText().equals(manifest.getProperty("tunnels")), "The mine changed after the goal ended: " + tunnelsText());
        record(goal);
        pass(server, goal);
    }

    /** Stands stay the prefix of the staircase and the recorded tunnels only grow: nothing is dug beside the old mine. */
    private static void sameMine() {
        var site = companion.mineSite();
        List<BlockPos> recorded = parseStands(manifest.getProperty("stands")), stands = site.stands();
        check(site.exists() && Long.toString(site.entrance().asLong()).equals(manifest.getProperty("entrance")), "The mine moved: entrance " + site.entrance());
        check(stands.size() >= recorded.size() && stands.subList(0, recorded.size()).equals(recorded), "A new staircase instead of the old one: " + stands.size() + " stands, " + recorded.size() + " recorded");
        String[] old = manifest.getProperty("tunnels").isEmpty() ? new String[0] : manifest.getProperty("tunnels").split(";");
        var tunnels = site.tunnels();
        check(tunnels.size() >= old.length, "Recorded tunnels were lost: " + tunnelsText() + "/" + manifest.getProperty("tunnels"));
        for (int i = 0; i < old.length; i++) {
            String[] parts = old[i].split(":");
            int length = Integer.parseInt(parts[1]);
            boolean last = i == old.length - 1;
            check(tunnels.get(i).direction().name().equals(parts[0]) && (last ? tunnels.get(i).length() >= length : tunnels.get(i).length() == length),
                    "Tunnel " + i + " is not the recorded one: " + tunnelsText() + "/" + manifest.getProperty("tunnels"));
        }
    }

    /** The cells the record says are open are air in the loaded world; chunks not loaded yet are skipped and not counted. */
    private static int checkRecordedCellsOpen(MinecraftServer server) {
        var level = server.overworld();
        var site = companion.mineSite();
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos stand : site.stands()) cells.addAll(CompanionShaftPlan.column(stand));
        BlockPos foot = site.foot();
        for (var tunnel : site.tunnels()) for (int k = 0; k < tunnel.length(); k++) cells.addAll(CompanionShaftPlan.tunnelCells(foot.relative(tunnel.direction(), k), tunnel.direction()));
        int checked = 0;
        for (BlockPos cell : cells) {
            if (!level.hasChunkAt(cell)) continue;
            checked++;
            check(CompanionShaftPlan.open(level, cell), "A recorded open cell is solid after the restart: " + cell.toShortString() + " " + level.getBlockState(cell));
        }
        check(checked > 0, "No recorded cell is in a loaded chunk");
        return checked;
    }

    private static void record(CompanionAgendaData.Goal goal) {
        var site = companion.mineSite();
        manifest.setProperty("companion", companion.getUUID().toString());
        manifest.setProperty("goal", goal.id().toString()); manifest.setProperty("state", goal.state().name());
        manifest.setProperty("entrance", Long.toString(site.entrance().asLong()));
        manifest.setProperty("standCount", Integer.toString(site.stands().size()));
        manifest.setProperty("stands", standsText()); manifest.setProperty("tunnels", tunnelsText());
        manifest.setProperty("tunnelCells", Integer.toString(advanced()));
        manifest.setProperty("ticket", Boolean.toString(CompanionChunkTicket.held(companion.getUUID())));
        manifest.setProperty("at", companion.blockPosition().toShortString());
        for (Item item : new Item[]{Items.DIAMOND, Items.TORCH, Items.STONE_PICKAXE, Items.COBBLESTONE, Items.DEEPSLATE})
            manifest.setProperty(BuiltInRegistries.ITEM.getKey(item).getPath(), Integer.toString(count(item)));
    }

    private static String standsText() {
        StringBuilder text = new StringBuilder();
        for (BlockPos stand : companion.mineSite().stands()) text.append(text.isEmpty() ? "" : ",").append(stand.asLong());
        return text.toString();
    }

    private static List<BlockPos> parseStands(String text) {
        List<BlockPos> stands = new ArrayList<>();
        if (!text.isEmpty()) for (String packed : text.split(",")) stands.add(BlockPos.of(Long.parseLong(packed)));
        return stands;
    }

    private static String tunnelsText() {
        StringBuilder text = new StringBuilder();
        for (var tunnel : companion.mineSite().tunnels()) text.append(text.isEmpty() ? "" : ";").append(tunnel.direction().name()).append(':').append(tunnel.length());
        return text.toString();
    }

    private static int advanced() { return companion.mineSite().tunnels().stream().mapToInt(CompanionMineSite.Tunnel::length).sum(); }

    private static int pocketSize() { return POCKET_X1 - POCKET_X0 + 1; }

    private static int pocketLeft(MinecraftServer server) {
        int left = 0;
        for (int x = POCKET_X0; x <= POCKET_X1; x++) if (server.overworld().getBlockState(world(x, DIAMOND_FEET - 1, POCKET_Z)).is(Blocks.DEEPSLATE_DIAMOND_ORE)) left++;
        return left;
    }

    private static int groundDiamonds(MinecraftServer server) {
        int n = 0;
        var box = new AABB(world(0, 0, -NORTH_EXTRA).getCenter(), world(SIZE, GROUND, SIZE).getCenter());
        for (ItemEntity item : server.overworld().getEntitiesOfClass(ItemEntity.class, box)) if (item.getItem().is(Items.DIAMOND)) n += item.getItem().getCount();
        return n;
    }

    private static CompanionAgendaData.Goal goal(MinecraftServer server) { return CompanionAgenda.goal(server, PLAYER); }

    private static String report(CompanionAgendaData.Goal goal) {
        return "goal " + goal.state() + " '" + goal.reason() + "', at " + companion.blockPosition().toShortString() + ", mine " + companion.mineSite().opened() + " cells, ticket "
                + CompanionChunkTicket.held(companion.getUUID()) + " | " + companion.getLastTaskResult() + " | bag: " + companion.getStoredItemsSummary();
    }

    private static int count(Item item) {
        int n = 0;
        for (int i = 0; i < companion.getContainerSize(); i++) if (companion.getItem(i).is(item)) n += companion.getItem(i).getCount();
        for (var slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) n += companion.getItemBySlot(slot).getCount();
        return n;
    }

    private static void pass(MinecraftServer server, CompanionAgendaData.Goal goal) throws Exception {
        manifest.setProperty("stage", STAGE);
        try (var out = Files.newOutputStream(MANIFEST)) { manifest.store(out, "Dedicated process persistence probe of the diamond mine"); }
        System.out.println("MINE_RESTART_PASS stage=" + STAGE + " goal=" + goal.id() + " state=" + goal.state() + " stands=" + companion.mineSite().stands().size()
                + " tunnels=" + tunnelsText() + " diamonds=" + count(Items.DIAMOND) + " ticket=" + CompanionChunkTicket.held(companion.getUUID()));
        finished = true; server.halt(false);
    }

    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    private static void fail(MinecraftServer server, Exception ex) { finished = true; System.out.println("MINE_RESTART_FAIL stage=" + STAGE + " " + ex); ex.printStackTrace(); server.halt(false); }
}

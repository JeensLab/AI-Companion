package dev.aicompanion.gametest;

import com.mojang.authlib.GameProfile;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.entity.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Development only (excluded from release JAR). Acceptance harness of the find_diamonds goal in a real generated overworld.
 * Stage scan: generates the area around spawn and writes the entrances worth testing (sites.properties). Stages run, prepare
 * and resume: one scenario on one such entrance through CompanionAgenda, judged by the facts the world and the goal leave.
 */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID)
public final class CompanionMineWorldProbe {
    private static final String STAGE = System.getProperty("aicompanion.mineWorldStage", "disabled");
    private static final String SCENARIO = System.getProperty("aicompanion.mineWorldScenario", "descent");
    private static final UUID PLAYER = UUID.fromString("5a1e7c40-91d3-4b6e-8f27-c0d4a3b96e18");
    private static final Path SITES = Path.of("sites.properties"), MANIFEST = Path.of("mine-world-probe.properties");
    private static final int SCAN_CHUNK_RADIUS = 12, SCAN_SPAN = 160, SCAN_WALL_SECONDS = 900;
    private static final int REQUESTED_DIAMONDS = 1, SPRINT_TICKS = 1_000_000;
    private static final int LOGIN_TICKS = 400, SETTLE_TICKS = 80, FOLLOW_EVERY = 10, FOLLOW_ABOVE = 10, BELOW_SLACK = 2;
    private static final int SCENARIO_TICKS = 72_000, STALL_TICKS = 6_000, WALL_SECONDS = 420, MAX_RESUMES = 4, MAX_CLIMBS = 2, LOST_TICKS = 1_200;
    private static final int DEEP_ENOUGH = 25, RETREAT_HEALTH = 6, RETREAT_RISE = 8, COME_TICKS = 9_000, COME_NEAR = 8, COME_LINGER = 100;
    private static final int RESTART_DEPTH = 12, MOB_RANGE = 24, PROGRESS_TICKS = 6_000;

    private enum Phase { LOGIN, SETTLE, RUN }

    private static ServerPlayer player;
    private static CompanionEntity companion;
    private static CompanionMineWorldJournal journal;
    private static BlockPos entrance, cave;
    private static Phase phase = Phase.LOGIN;
    private static ServerLevel scanLevel;
    private static List<ChunkPos> scanChunks;
    private static long scanStarted;
    private static int ticks, settleUntil, runStart, stallFor, lostFor, comeAt, lingerFor;
    private static boolean finished, wasBelow, parked, mobsSpawned, comeIssued, comeArrived, resumedAfterCome, retreatTriggered, retreatRose;
    private static int retreatY;
    private static CompanionAgenda.State lastState;
    private static String signature = "";
    private static long entityTickStart;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (STAGE.equals("disabled")) return;
        var server = event.getServer();
        try {
            if (STAGE.equals("scan")) { startScan(server); return; }
            journal = new CompanionMineWorldJournal(SCENARIO, STAGE);
            var sites = new Properties();
            try (var in = Files.newInputStream(SITES)) { sites.load(in); }
            String status = sites.getProperty(SCENARIO + ".status", "NONE");
            if (!status.equals("OK")) {
                finish(server, status.equals("SKIPPED") ? "SKIPPED" : "SETUP_FAILED", "No site in the generated world for this scenario (" + status + ")");
                return;
            }
            entrance = CompanionMineWorldSites.parse(sites.getProperty(SCENARIO + ".entrance"));
            if (sites.containsKey(SCENARIO + ".cave")) cave = CompanionMineWorldSites.parse(sites.getProperty(SCENARIO + ".cave"));
            journal.facts.put("site", entrance.toShortString() + " " + sites.getProperty(SCENARIO + ".dir") + " " + sites.getProperty(SCENARIO + ".facts"));
            var cookie = CommonListenerCookie.createInitial(new GameProfile(PLAYER, "mine-world-probe"), false);
            player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setInvulnerable(true); player.setNoGravity(true);
        } catch (Exception ex) { crash(server, ex); }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (STAGE.equals("disabled") || finished) return;
        var server = event.getServer();
        try {
            if (STAGE.equals("scan")) { scanTick(server); return; }
            if (player == null) return;
            ticks++;
            if (!server.tickRateManager().isSprinting()) server.tickRateManager().requestGameToSprint(SPRINT_TICKS);
            switch (phase) {
                case LOGIN -> login(server);
                case SETTLE -> settle(server);
                default -> run(server);
            }
        } catch (Exception ex) { crash(server, ex); }
    }

    @SubscribeEvent public static void entityBefore(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof CompanionEntity) entityTickStart = System.nanoTime();
    }

    @SubscribeEvent public static void entityAfter(EntityTickEvent.Post event) {
        if (journal != null && event.getEntity() instanceof CompanionEntity) journal.entityTick(System.nanoTime() - entityTickStart);
    }

    // ---- scan ----

    private static void startScan(MinecraftServer server) {
        scanLevel = server.overworld();
        BlockPos spawn = scanLevel.getSharedSpawnPos();
        scanChunks = new ArrayList<>();
        for (int x = -SCAN_CHUNK_RADIUS; x <= SCAN_CHUNK_RADIUS; x++) for (int z = -SCAN_CHUNK_RADIUS; z <= SCAN_CHUNK_RADIUS; z++) {
            var pos = new ChunkPos((spawn.getX() >> 4) + x, (spawn.getZ() >> 4) + z);
            scanChunks.add(pos);
            // FORCED tickets are not saved with the level, unlike setChunkForced, and the loaded chunks are still written on stop.
            scanLevel.getChunkSource().addRegionTicket(TicketType.FORCED, pos, 0, pos);
        }
        scanStarted = System.nanoTime();
        server.tickRateManager().requestGameToSprint(SPRINT_TICKS);
    }

    private static void scanTick(MinecraftServer server) throws IOException {
        if ((System.nanoTime() - scanStarted) / 1e9 > SCAN_WALL_SECONDS) throw new IllegalStateException("Chunk generation did not finish in " + SCAN_WALL_SECONDS + " s");
        for (var pos : scanChunks) if (scanLevel.getChunkSource().getChunkNow(pos.x, pos.z) == null) return;
        BlockPos spawn = scanLevel.getSharedSpawnPos();
        var sites = CompanionMineWorldSites.scan(scanLevel, spawn, SCAN_SPAN);
        var out = new Properties();
        for (String scenario : CompanionMineWorldSites.SCENARIOS) {
            var site = CompanionMineWorldSites.pick(scenario, sites);
            CompanionMineWorldSites.store(out, scenario, site);
            System.out.println("MINE_WORLD_SITE scenario=" + scenario + " " + (site == null ? "none" : site.feet().toShortString() + " " + site.dir() + " " + site.facts()));
        }
        try (var stream = Files.newOutputStream(SITES)) { out.store(stream, "Mine acceptance sites"); }
        long floorOnly = sites.stream().filter(s -> s.cause() == CompanionMineWorldSites.Cause.FLOOR).count(), clean = sites.stream().filter(CompanionMineWorldSites.Site::clean).count();
        System.out.println("MINE_WORLD_SCAN_DONE spawn=" + spawn.toShortString() + " chunks=" + scanChunks.size() + " sites=" + sites.size() + " floorOnly=" + floorOnly + " clean=" + clean
                + " seconds=" + Math.round((System.nanoTime() - scanStarted) / 1e9));
        finished = true; server.halt(false);
    }

    // ---- run ----

    private static void login(MinecraftServer server) throws IOException {
        companion = CompanionEvents.getOwnedCompanion(player);
        if (companion == null) { setup(ticks < LOGIN_TICKS, "Canonical companion did not load"); return; }
        if (STAGE.equals("resume")) { restored(server); phase = Phase.RUN; runStart = ticks; return; }
        // Chunks are loaded from the saved world before anybody is placed there, so that the goal does not start in unloaded land.
        ServerLevel level = server.overworld();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk((entrance.getX() >> 4) + dx, (entrance.getZ() >> 4) + dz, ChunkStatus.FULL, true);
        player.teleportTo(entrance.getX() + 0.5, entrance.getY() + FOLLOW_ABOVE, entrance.getZ() + 0.5);
        settleUntil = ticks + SETTLE_TICKS;
        phase = Phase.SETTLE;
    }

    private static void settle(MinecraftServer server) {
        if (ticks < settleUntil) return;
        companion.teleportTo(entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5);
        companion.setOwnerId(PLAYER); companion.setSearchRadius(16); companion.setLifestyle(false, false);
        ItemStack[] bag = {new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE_PICKAXE, 4), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.TORCH, 32),
                new ItemStack(Items.BREAD, 16), new ItemStack(Items.COBBLESTONE, 32)};
        for (int slot = 0; slot < bag.length; slot++) companion.setItem(slot, bag[slot]);
        setup(CompanionEvents.executeAction(player, "pursue_goal", "find_diamonds", REQUESTED_DIAMONDS).status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted");
        phase = Phase.RUN; runStart = ticks;
    }

    private static void restored(MinecraftServer server) {
        var goal = goal(server);
        var props = new Properties();
        try (var in = Files.newInputStream(MANIFEST)) { props.load(in); } catch (IOException ex) { throw new IllegalStateException("No manifest of the prepare stage", ex); }
        var site = companion.mineSite();
        check(goal != null && goal.id().toString().equals(props.getProperty("goal")) && goal.state().outstanding(), "Goal did not survive the restart: " + (goal == null ? "none" : goal.state()));
        check(site.exists() && Long.toString(site.entrance().asLong()).equals(props.getProperty("entrance")), "The mine entrance changed or was lost: " + site.entrance());
        check(standsText().equals(props.getProperty("stands")), "The recorded stairs changed over the restart");
        journal.facts.put("restoredStands", site.stands().size());
    }

    private static void run(MinecraftServer server) throws IOException {
        if (companion == null || !companion.isAlive()) {
            if (companion != null) { journal.deaths++; wasBelow = false; System.out.println("MINE_WORLD_DEATH scenario=" + SCENARIO + " tick=" + ticks); }
            companion = CompanionEvents.getOwnedCompanion(player);
            lostFor = companion == null ? lostFor + 1 : 0;
            check(lostFor < LOST_TICKS, "Companion is gone");
            if (companion == null) return;
        }
        var goal = goal(server);
        check(goal != null, "No goal");
        int depth = entrance.getY() - companion.blockPosition().getY();
        journal.maxDepth = Math.max(journal.maxDepth, depth);
        observe(server, goal, depth);
        if (finished) return;
        if (!parked && ticks % FOLLOW_EVERY == 0) player.teleportTo(companion.getX(), companion.getY() + FOLLOW_ABOVE, companion.getZ());
        scenarioHooks(server, goal, depth);
        if (finished) return;
        if (STAGE.equals("prepare") && prepared(server, goal, depth)) return;
        if (goal.state() == CompanionAgenda.State.COMPLETED) { conclude(server, goal); return; }
        if ((ticks - runStart) % PROGRESS_TICKS == 0) System.out.println("MINE_WORLD_PROGRESS scenario=" + SCENARIO + " " + report(goal) + " depth=" + depth);
        if (ticks - runStart >= SCENARIO_TICKS) fail(server, "game time limit " + SCENARIO_TICKS + " ticks | " + report(goal));
        else if (journal.wallSeconds() > WALL_SECONDS) fail(server, "wall time limit " + WALL_SECONDS + " s | " + report(goal));
    }

    /** The tester's part: every stop of the goal is recorded, and answered with «продолжай» a bounded number of times. */
    private static void observe(MinecraftServer server, CompanionAgendaData.Goal goal, int depth) {
        var state = goal.state();
        boolean below = depth > BELOW_SLACK;
        if (wasBelow && !below && lastState != null && lastState.suppressesDeeds() && state.suppressesDeeds()) {
            journal.climbs++;
            System.out.println("MINE_WORLD_CLIMB scenario=" + SCENARIO + " n=" + journal.climbs + " tick=" + ticks);
        }
        wasBelow = below;
        if (state == CompanionAgenda.State.BLOCKED && lastState != CompanionAgenda.State.BLOCKED) {
            journal.blocked.add(new CompanionMineWorldJournal.Blocked(ticks, goal.reason(), companion.blockPosition().toShortString(), String.valueOf(companion.getLastTaskResult())));
            System.out.println("MINE_WORLD_BLOCKED scenario=" + SCENARIO + " n=" + journal.blocked.size() + " reason=\"" + goal.reason() + "\" at=" + companion.blockPosition().toShortString()
                    + " task=\"" + companion.getLastTaskResult() + "\"");
            if (journal.resumes >= MAX_RESUMES) { fail(server, "the goal stopped " + journal.blocked.size() + " times, last: " + goal.reason()); return; }
            journal.resumes++;
            CompanionEvents.executeAction(player, "goal_resume", "", 1);
        }
        lastState = state;
        if (journal.climbs > MAX_CLIMBS) { fail(server, "left the mine for the surface " + journal.climbs + " times (limit " + MAX_CLIMBS + ")"); return; }
        String now = companion.blockPosition().toShortString() + "|" + companion.mineSite().opened() + "|" + count(Items.DIAMOND) + "|" + state;
        stallFor = now.equals(signature) ? stallFor + 1 : 0;
        signature = now;
        if (stallFor >= STALL_TICKS) fail(server, "stalled for " + STALL_TICKS + " ticks at " + now + " | " + report(goal));
    }

    private static void scenarioHooks(MinecraftServer server, CompanionAgendaData.Goal goal, int depth) {
        switch (SCENARIO) {
            case "mobs" -> spawnMobs(server);
            case "retreat" -> {
                if (!retreatTriggered && depth >= DEEP_ENOUGH) { companion.setHealth(RETREAT_HEALTH); retreatTriggered = true; retreatY = companion.blockPosition().getY(); }
                if (retreatTriggered && companion.blockPosition().getY() >= retreatY + RETREAT_RISE) retreatRose = true;
            }
            case "come" -> come(goal, depth);
            default -> { }
        }
    }

    private static void spawnMobs(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (mobsSpawned || cave == null || companion.distanceToSqr(cave.getCenter()) > MOB_RANGE * MOB_RANGE || !level.hasChunkAt(cave)) return;
        for (var type : List.<EntityType<? extends Mob>>of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON)) {
            Mob mob = type.spawn(level, cave, MobSpawnType.COMMAND);
            if (mob != null) mob.setPersistenceRequired();
        }
        mobsSpawned = true;
    }

    /** «Ко мне» from depth: the player waits at the entrance, Oleg must come up and stand near, then the goal is resumed. */
    private static void come(CompanionAgendaData.Goal goal, int depth) {
        if (!comeIssued && depth >= DEEP_ENOUGH) {
            parked = true;
            player.teleportTo(entrance.getX() + 0.5, entrance.getY() + 1, entrance.getZ() + 0.5);
            CompanionEvents.executeAction(player, "come", "", 1);
            comeIssued = true; comeAt = ticks;
            return;
        }
        if (!comeIssued || resumedAfterCome) return;
        if (!comeArrived) {
            check(ticks - comeAt < COME_TICKS, "Did not come to the player from depth within " + COME_TICKS + " ticks | " + report(goal));
            comeArrived = companion.distanceToSqr(player) < COME_NEAR * COME_NEAR;
            return;
        }
        if (++lingerFor < COME_LINGER) return;
        parked = false;
        resumedAfterCome = true;
        CompanionEvents.executeAction(player, "goal_resume", "", 1);
    }

    private static boolean prepared(MinecraftServer server, CompanionAgendaData.Goal goal, int depth) throws IOException {
        var site = companion.mineSite();
        if (depth < RESTART_DEPTH || site.stands().size() < RESTART_DEPTH || companion.activeWorkId() == 0 || goal.state() != CompanionAgenda.State.ACTIVE) return false;
        var manifest = new Properties();
        manifest.setProperty("goal", goal.id().toString());
        manifest.setProperty("entrance", Long.toString(site.entrance().asLong()));
        manifest.setProperty("stands", standsText());
        try (var out = Files.newOutputStream(MANIFEST)) { manifest.store(out, "Mine world restart probe"); }
        journal.facts.put("stoppedAt", companion.blockPosition().toShortString() + " stands=" + site.stands().size());
        finish(server, "PREPARED", "stopped mid-mine for the restart | " + report(goal));
        return true;
    }

    private static void conclude(MinecraftServer server, CompanionAgendaData.Goal goal) {
        int diamonds = count(Items.DIAMOND);
        journal.facts.put("diamonds", diamonds);
        journal.facts.put("gameTicks", ticks - runStart);
        List<String> why = new ArrayList<>();
        if (!journal.blocked.isEmpty()) why.add("the goal stopped " + journal.blocked.size() + " times");
        if (journal.climbs > MAX_CLIMBS) why.add(journal.climbs + " climbs to the surface");
        if (diamonds < REQUESTED_DIAMONDS) why.add("completed with " + diamonds + " diamonds, wanted " + REQUESTED_DIAMONDS);
        if (journal.deaths > 0) why.add(journal.deaths + " deaths");
        if (!why.isEmpty()) { finish(server, "FAIL", String.join("; ", why) + " | " + report(goal)); return; }
        String unexercised = SCENARIO.equals("retreat") && !retreatTriggered ? "never went deep enough to retreat"
                : SCENARIO.equals("come") && !resumedAfterCome ? "never came up on the order"
                : SCENARIO.equals("mobs") && !mobsSpawned ? "the cave was never reached, no mobs were placed" : "";
        finish(server, unexercised.isEmpty() ? "PASS" : "SETUP_FAILED", unexercised.isEmpty() ? "diamonds " + diamonds + " | " + report(goal) : unexercised);
    }

    private static void fail(MinecraftServer server, String reason) { finish(server, "FAIL", reason); }

    private static void finish(MinecraftServer server, String verdict, String reason) {
        if (finished) return;
        finished = true;
        try {
            CompanionDiagnostics.flush();
            if (journal == null) journal = new CompanionMineWorldJournal(SCENARIO, STAGE);
            if (companion != null) {
                journal.facts.putIfAbsent("diamonds", count(Items.DIAMOND));
                journal.facts.putIfAbsent("gameTicks", ticks - runStart);
                journal.facts.put("retreatTriggered", retreatTriggered); journal.facts.put("retreatRose", retreatRose);
                journal.facts.put("comeIssued", comeIssued); journal.facts.put("comeArrived", comeArrived); journal.facts.put("mobsSpawned", mobsSpawned);
                journal.facts.put("finalState", lastState == null ? "" : lastState.name());
                journal.facts.put("opened", companion.mineSite().opened());
                journal.facts.put("lastTask", String.valueOf(companion.getLastTaskResult()));
                journal.facts.put("bag", companion.getStoredItemsSummary());
            }
            journal.readDiagnostics(Path.of(""));
            journal.write(Path.of("mine-world-" + STAGE + ".json"), verdict, reason);
        } catch (IOException | RuntimeException error) {
            System.out.println("MINE_WORLD_JOURNAL_ERROR " + error);
        }
        System.out.println("MINE_WORLD_" + verdict + " scenario=" + SCENARIO + " stage=" + STAGE + " climbs=" + (journal == null ? 0 : journal.climbs) + " blocked=" + (journal == null ? 0 : journal.blocked.size())
                + " floorRefusals=" + (journal == null ? 0 : journal.floorRefusals) + " reason=\"" + reason + "\"");
        server.halt(false);
    }

    // ---- helpers ----

    private static CompanionAgendaData.Goal goal(MinecraftServer server) { return CompanionAgenda.goal(server, PLAYER); }

    private static String report(CompanionAgendaData.Goal goal) {
        return "goal " + goal.state() + " '" + goal.reason() + "', at " + companion.blockPosition().toShortString() + ", mine " + companion.mineSite().opened() + " cells, ticket "
                + CompanionChunkTicket.held(companion.getUUID()) + " | " + companion.getLastTaskResult() + " | bag: " + companion.getStoredItemsSummary();
    }

    private static String standsText() {
        StringBuilder text = new StringBuilder();
        for (BlockPos stand : companion.mineSite().stands()) text.append(text.isEmpty() ? "" : ",").append(stand.asLong());
        return text.toString();
    }

    private static int count(Item item) {
        int n = 0;
        for (int i = 0; i < companion.getContainerSize(); i++) if (companion.getItem(i).is(item)) n += companion.getItem(i).getCount();
        for (var slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) n += companion.getItemBySlot(slot).getCount();
        return n;
    }

    /** A scenario outcome found deep in a helper: FAIL when the goal misbehaved, SETUP_FAILED when the scenario could not be put up. */
    private static final class Stop extends RuntimeException {
        final String verdict;
        Stop(String verdict, String message) { super(message); this.verdict = verdict; }
    }

    private static void check(boolean ok, String message) { if (!ok) throw new Stop("FAIL", message); }

    private static void setup(boolean ok, String message) { if (!ok) throw new Stop("SETUP_FAILED", message); }

    private static void crash(MinecraftServer server, Exception ex) {
        if (ex instanceof Stop stop) { finish(server, stop.verdict, stop.getMessage()); return; }
        ex.printStackTrace();
        finish(server, "ERROR", "harness error: " + ex);
    }
}

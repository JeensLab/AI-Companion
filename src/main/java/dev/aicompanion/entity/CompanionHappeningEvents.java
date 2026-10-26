package dev.aicompanion.entity;

import dev.aicompanion.OpenRouterConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.aicompanion.entity.CompanionHappenings.Importance;

/**
 * Turns what happens to the player into happenings (kept per owner) and hands the important ones to the companion to say.
 * A HIGH event goes straight to the owner's loaded companion, or waits here until one asks for it. Server thread only.
 */
public final class CompanionHappeningEvents {
    record Waiting(String key, String fact, long since) { }

    private static final long ABSENCE_MILLIS = 30L * 60_000;
    private static final int STAMP_TICKS = 1200, DANGER_TICKS = 20, RESCUE_WINDOW_TICKS = 100, FIGHT_RADIUS = 8, DEED_RADIUS = 32;
    private static final float DANGER_BELOW = 6, SAFE_ABOVE = 12;
    private static final Set<String> BIG_ADVANCEMENTS = Set.of("minecraft:story/mine_diamond", "minecraft:story/enter_the_nether",
            "minecraft:story/enter_the_end", "minecraft:adventure/totem_of_undying", "minecraft:nether/obtain_ancient_debris");

    private static final Map<UUID, String> DEATHS = new HashMap<>();
    private static final Map<UUID, Waiting> WAITING = new HashMap<>();
    private static final Set<UUID> PRESENT = new HashSet<>();
    private static final Set<UUID> ENDANGERED = new HashSet<>();

    private CompanionHappeningEvents() { }

    public static void clear() {
        DEATHS.clear();
        WAITING.clear();
        PRESENT.clear();
        ENDANGERED.clear();
    }

    public static void onServerStopped(ServerStoppedEvent event) { clear(); }

    /** The event a companion that just appeared should say, if its owner has one waiting. */
    static Waiting claim(UUID owner) { return owner == null ? null : WAITING.remove(owner); }

    static double voiceBlocks() {
        try {
            return OpenRouterConfig.VOICE_DISTANCE.get();
        } catch (IllegalStateException notLoaded) {
            return OpenRouterConfig.VOICE_DISTANCE.getDefault();
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer player) playerDied(player, event.getSource());
        else if (event.getEntity() instanceof CompanionEntity companion) companionDied(companion, event.getSource());
        else rescue(event);
    }

    private static void playerDied(ServerPlayer player, DamageSource source) {
        ENDANGERED.remove(player.getUUID());
        String text = "Товарищ погиб: " + source.getLocalizedDeathMessage(player).getString() + ".";
        bonds(player.getServer()).happened(player.getUUID(), "player_death", text, now(player), Importance.HIGH);
        DEATHS.put(player.getUUID(), text);
    }

    private static void companionDied(CompanionEntity companion, DamageSource source) {
        UUID owner = companion.getOwnerId();
        MinecraftServer server = companion.level().getServer();
        if (owner == null || server == null) return;
        String cause = source.getLocalizedDeathMessage(companion).getString();
        long now = server.overworld().getGameTime();
        bonds(server).happened(owner, "companion_died", companion.getName().getString() + " погиб: " + cause + ".", now, Importance.HIGH);
        // Never handed to the dying entity: the one who comes back tells it.
        WAITING.put(owner, new Waiting("companion_died", clip("Я погиб: " + cause + ", и вернулся к товарищу."), now));
    }

    private static void rescue(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof CompanionEntity killer) || killer.getOwnerId() == null) return;
        MinecraftServer server = killer.level().getServer();
        ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(killer.getOwnerId());
        var victim = event.getEntity();
        if (owner == null || owner.getLastHurtByMob() != victim || owner.tickCount - owner.getLastHurtByMobTimestamp() > RESCUE_WINDOW_TICKS) return;
        long now = now(owner);
        bonds(server).rescued(owner.getUUID(), CompanionBond.dayOf(now));
        tell(owner, "rescue", "Я убил " + victim.getName().getString() + ", который бил товарища.", null, Importance.NORMAL);
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ENDANGERED.remove(player.getUUID());
        String text = DEATHS.remove(player.getUUID());
        if (text != null) handOver(player, "player_death", text);
    }

    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AdvancementHolder holder = event.getAdvancement();
        var display = holder.value().display();
        if (display.isEmpty() || !display.get().shouldAnnounceChat()) return;
        String id = holder.id().toString();
        boolean big = display.get().getType() == AdvancementType.CHALLENGE || BIG_ADVANCEMENTS.contains(id);
        String text = "Товарищ получил достижение «" + display.get().getTitle().getString() + "».";
        tell(player, "advancement:" + id, text, text, big ? Importance.HIGH : Importance.NORMAL);
    }

    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var to = event.getTo();
        String name = to == Level.NETHER ? "Незер" : to == Level.END ? "Энд" : to == Level.OVERWORLD ? "Верхний мир" : to.location().getPath();
        tell(player, "dimension", "Товарищ перешёл в " + name + ".", null, Importance.LOW);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) welcome(player, System.currentTimeMillis());
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        bonds(player.getServer()).markSeen(id, System.currentTimeMillis());
        PRESENT.remove(id);
        WAITING.remove(id);
        ENDANGERED.remove(id);
        DEATHS.remove(id);
    }

    /** Tells of a long absence (judged by the stamp left before), then stamps the player as present. */
    public static void welcome(ServerPlayer player, long nowMillis) {
        CompanionBonds bonds = bonds(player.getServer());
        var lastSeen = bonds.lastSeen(player.getUUID());
        if (lastSeen.isPresent() && nowMillis - lastSeen.getAsLong() > ABSENCE_MILLIS) {
            String text = "Товарищ вернулся в мир после отсутствия около " + absence(nowMillis - lastSeen.getAsLong()) + ".";
            tell(player, "return", text, text, Importance.HIGH);
        }
        bonds.markSeen(player.getUUID(), nowMillis);
        PRESENT.add(player.getUUID());
    }

    /** Stamps only players whose login was processed: a pass before that must not overwrite the stamp the absence is judged by. */
    public static void stampPresent(MinecraftServer server, long nowMillis) {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (PRESENT.contains(player.getUUID())) bonds(server).markSeen(player.getUUID(), nowMillis);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % STAMP_TICKS == 0) stampPresent(server, System.currentTimeMillis());
        if (tick % DANGER_TICKS == 0) for (ServerPlayer player : server.getPlayerList().getPlayers()) checkDanger(player);
    }

    public static void checkDanger(ServerPlayer player) {
        if (!player.isAlive()) { ENDANGERED.remove(player.getUUID()); return; }
        if (player.getHealth() < DANGER_BELOW) ENDANGERED.add(player.getUUID());
        else if (player.getHealth() > SAFE_ABOVE && ENDANGERED.remove(player.getUUID()))
            tell(player, "survived", "Товарищ был на волоске от гибели, но выжил.", null, Importance.NORMAL);
    }

    static void playerHit(CompanionEntity companion, DamageSource source) {
        if (companion.level().isClientSide || !(source.getEntity() instanceof ServerPlayer player) || !player.getUUID().equals(companion.getOwnerId())) return;
        boolean fighting = companion.getTarget() instanceof Monster || !companion.level().getEntitiesOfClass(Monster.class,
                companion.getBoundingBox().inflate(FIGHT_RADIUS), monster -> CompanionHostiles.observed(companion, monster, FIGHT_RADIUS)).isEmpty();
        long now = now(player);
        int moved = bonds(player.getServer()).hitByPlayer(player.getUUID(), CompanionBond.dayOf(now), now, fighting);
        if (moved != 0) tell(player, "hit", "Товарищ ударил меня.", null, Importance.NORMAL);
    }

    static void sharedDeed(CompanionEntity companion) {
        ServerPlayer owner = companion.getOwnerPlayer();
        if (owner == null || companion.distanceToSqr(owner) > DEED_RADIUS * DEED_RADIUS) return;
        bonds(owner.getServer()).sharedDeed(owner.getUUID(), CompanionBond.dayOf(now(owner)));
    }

    /** What the owner left in the companion's bag while the inventory was open. */
    public static void gift(ServerPlayer giver, List<CompanionBond.ItemChange> changes, String what) {
        if (changes.isEmpty()) return;
        int moved = bonds(giver.getServer()).gift(giver.getUUID(), CompanionBond.dayOf(now(giver)), changes);
        if (moved > 0) tell(giver, "gift", "Товарищ положил мне в сумку: " + what + ".", null, Importance.NORMAL);
    }

    private static void tell(ServerPlayer player, String key, String text, String fact, Importance importance) {
        bonds(player.getServer()).happened(player.getUUID(), key, text, now(player), importance);
        if (importance == Importance.HIGH) handOver(player, key, fact == null ? text : fact);
    }

    private static void handOver(ServerPlayer player, String key, String fact) {
        CompanionEntity companion = CompanionEvents.getOwnedCompanion(player);
        if (companion != null) {
            WAITING.remove(player.getUUID());
            companion.social.urgent(key, clip(fact));
        } else WAITING.put(player.getUUID(), new Waiting(key, clip(fact), now(player)));
    }

    private static String absence(long millis) {
        long minutes = millis / 60_000;
        if (minutes < 60) return minutes + " мин";
        return minutes < 24 * 60 ? minutes / 60 + " ч" : minutes / (24 * 60) + " дн";
    }

    private static String clip(String text) { return text.length() <= CompanionHappenings.TEXT ? text : text.substring(0, CompanionHappenings.TEXT - 1) + "…"; }

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    private static CompanionBonds bonds(MinecraftServer server) { return CompanionBonds.get(server); }
}

package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionJokeBank.Theme;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/** Confirmed server observations; only theme sets cross back to the client. */
@EventBusSubscriber(modid = AICompanionMod.MOD_ID)
public final class CompanionJokeContext {
    public static final long RECENT_TICKS = 20L * 60 * 10;
    public record Snapshot(Set<Theme> surroundings, Set<Theme> recent) {
        public Snapshot { surroundings = Set.copyOf(surroundings); recent = Set.copyOf(recent); }
    }
    // Three timestamps per active owner keep the ten-minute window independent of journal eviction.
    private static final Map<UUID, EnumMap<Theme, Long>> observed = new HashMap<>();
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { observed.clear(); }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        observed.remove(event.getEntity().getUUID());
    }
    private CompanionJokeContext() { }
    private static Theme eventTheme(String key) {
        return switch (key) {
            case "joke_mine" -> Theme.MINE;
            case "joke_trade" -> Theme.VILLAGE;
            case "joke_creeper" -> Theme.CREEPER;
            default -> null;
        };
    }
    private static void record(ServerPlayer player, String key, String fact) {
        var bonds = CompanionBonds.get(player.server);
        if (!player.getPersistentData().hasUUID("aicompanion:canonical_companion") && !bonds.knows(player.getUUID())) return;
        long now = player.server.overworld().getGameTime();
        observed.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(Theme.class)).put(eventTheme(key), now);
        bonds.happened(player.getUUID(), key, fact, now, CompanionHappenings.Importance.LOW);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void mined(BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer player && event.getState().is(Tags.Blocks.ORES))
            record(player, "joke_mine", "Товарищ добыл руду.");
    }
    @SubscribeEvent public static void traded(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) record(player, "joke_trade", "Товарищ торговал с жителем.");
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().getEntity() instanceof Creeper)
            record(player, "joke_creeper", "Товарищ погиб от крипера.");
    }
    public static Set<Theme> recent(CompanionHappenings happenings, long now) {
        var themes = EnumSet.noneOf(Theme.class);
        for (var event : happenings.all()) {
            long age = now - event.gameTime();
            if (age < 0 || age > RECENT_TICKS) continue;
            Theme theme = eventTheme(event.key());
            if (theme != null) themes.add(theme);
        }
        return Set.copyOf(themes);
    }
    public static Snapshot capture(ServerPlayer player) { return capture(player, false); }
    public static Snapshot capture(ServerPlayer player, boolean travelling) {
        var level = player.serverLevel();
        var themes = EnumSet.noneOf(Theme.class);
        if (travelling) themes.add(Theme.TRAVEL);
        if (player.getFoodData().getFoodLevel() < 14 || player.getHealth() < player.getMaxHealth() * .75F
                || level.dimension() == Level.OVERWORLD && (level.isNight() || level.isRaining())) themes.add(Theme.SURVIVAL);
        if (level.dimension() == Level.NETHER) themes.add(Theme.NETHER);
        else if (level.dimension() == Level.END) themes.add(Theme.END);
        else {
            if (level.getBiome(player.blockPosition()).is(BiomeTags.IS_FOREST)) themes.add(Theme.FOREST);
            if (player.getY() < level.getSeaLevel() - 4 && !level.canSeeSky(player.blockPosition())) themes.add(Theme.MINE);
            if (!level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(24)).isEmpty()) themes.add(Theme.VILLAGE);
        }
        long now = player.server.overworld().getGameTime();
        var recent = EnumSet.noneOf(Theme.class);
        recent.addAll(recent(CompanionBonds.get(player.server).happenings(player.getUUID()), now));
        var stamps = observed.get(player.getUUID());
        if (stamps != null) stamps.forEach((theme, time) -> {
            if (now >= time && now - time <= RECENT_TICKS) recent.add(theme);
        });
        return new Snapshot(themes, recent);
    }
}

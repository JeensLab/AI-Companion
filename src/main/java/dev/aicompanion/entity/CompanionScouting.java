package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * scout_traders: a walk to every trader within {@link #RADIUS} blocks, nearest first. At each he stops within a hand's reach
 * with a clear line, looks at the trader and is looked at for a second or two, reads the real offers on the server and keeps
 * them in {@link CompanionTraders}. A sleeper is counted, not asked. At most {@link #LIMIT} ticks; the report says whom he got to.
 */
final class CompanionScouting {
    static final int RADIUS = 64;
    /** Three minutes of the round; what is not reached by then is left. */
    static final int LIMIT = 20 * 60 * 3;
    private static final double REACH = 3.0;
    private static final int LOOK_TICKS = 30;
    /** Without getting closer for this long the trader is given up (a wall, a fence all round, no way). */
    private static final int STUCK_TICKS = 200;
    private static final int SHOWN_DEALS = 3;
    private static final Map<String, String> PROFESSIONS = new LinkedHashMap<>();
    static {
        PROFESSIONS.put("armorer", "Бронник"); PROFESSIONS.put("butcher", "Мясник"); PROFESSIONS.put("cartographer", "Картограф");
        PROFESSIONS.put("cleric", "Священник"); PROFESSIONS.put("farmer", "Фермер"); PROFESSIONS.put("fisherman", "Рыбак");
        PROFESSIONS.put("fletcher", "Лучник"); PROFESSIONS.put("leatherworker", "Кожевник"); PROFESSIONS.put("librarian", "Библиотекарь");
        PROFESSIONS.put("mason", "Каменщик"); PROFESSIONS.put("shepherd", "Пастух"); PROFESSIONS.put("toolsmith", "Инструментальщик");
        PROFESSIONS.put("weaponsmith", "Оружейник"); PROFESSIONS.put(CompanionTraders.WANDERING, "Странствующий торговец");
    }

    final CompanionWork w;
    private final List<UUID> queue = new ArrayList<>();
    private final List<CompanionTraders.Entry> asked = new ArrayList<>();
    private UUID current;
    private int asleep, unreached;
    private long startedAt, lookUntil, progressAt, nextPath;
    private double best;
    private Path path;

    CompanionScouting(CompanionWork w) { this.w = w; }

    /** Null if the order is taken; no trader round him is a refusal that says so. */
    CompanionActionResult begin() {
        cancel();
        if (!(w.owner.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Не могу осмотреться.");
        List<AbstractVillager> traders = level.getEntitiesOfClass(AbstractVillager.class, new AABB(w.owner.blockPosition()).inflate(RADIUS, RADIUS, RADIUS),
                trader -> isTrader(trader) && trader.distanceToSqr(w.owner) <= RADIUS * RADIUS);
        if (traders.isEmpty()) return CompanionActionResult.failed("Деревни рядом не вижу. Могу поискать: скажи, в какую сторону света идти.");
        traders.sort(Comparator.comparingDouble(w.owner::distanceToSqr));
        for (AbstractVillager trader : traders) queue.add(trader.getUUID());
        return null;
    }

    /** A villager with a profession (not jobless, not a nitwit, not a child) or a wandering trader. */
    private static boolean isTrader(AbstractVillager trader) {
        if (!trader.isAlive() || trader.isBaby()) return false;
        if (!(trader instanceof Villager villager)) return true;
        var profession = villager.getVillagerData().getProfession();
        return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT;
    }

    void cancel() {
        if (current != null || !queue.isEmpty()) w.route.stop();
        queue.clear(); asked.clear(); current = null; path = null;
        asleep = unreached = 0; startedAt = lookUntil = progressAt = nextPath = 0;
    }

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (startedAt == 0) startedAt = now;
        if (now - startedAt > LIMIT) { finish(level, true); return; }
        if (current == null) {
            if (queue.isEmpty()) { finish(level, false); return; }
            current = queue.removeFirst(); best = Double.MAX_VALUE; progressAt = now; nextPath = 0; lookUntil = 0; path = null;
        }
        if (!(level.getEntity(current) instanceof AbstractVillager trader) || !trader.isAlive()) { next(); return; }
        if (trader.isSleeping()) { asleep++; next(); return; }
        double distance = trader.getBoundingBox().distanceToSqr(w.owner.getEyePosition());
        if (distance > REACH * REACH || !w.owner.hasLineOfSight(trader)) { approach(level, trader, distance, now); return; }
        w.route.stop();
        w.owner.getLookControl().setLookAt(trader, 30, 30);
        trader.getLookControl().setLookAt(w.owner, 30, 30);
        w.owner.workStatus("Смотрю, что он продаёт.");
        if (lookUntil == 0) lookUntil = now + LOOK_TICKS;
        if (now < lookUntil) return;
        asked.add(w.owner.traders().remember(trader, level));
        next();
    }

    private void next() { current = null; path = null; w.route.legsEnd(false); w.route.stop(); }

    private void approach(ServerLevel level, AbstractVillager trader, double distance, long now) {
        if (distance + .05 < best) { best = distance; progressAt = now; }
        if (now - progressAt > STUCK_TICKS) { unreached++; next(); return; }
        // Far off: the single route walks him in legs to a few blocks from the trader; the last steps are chosen for the hand's reach.
        if (trader.distanceToSqr(w.owner) > 6 * 6) {
            w.owner.workStatus("Иду к торговцу.");
            switch (w.route.legs(trader.blockPosition(), 3)) {
                case WALKING -> { }
                case NEAR -> w.route.legsEnd(true);
                case FAILED -> { unreached++; next(); }
            }
            return;
        }
        if (now >= nextPath && (path == null || w.route.legOver())) {
            path = standPath(level, trader);
            nextPath = now + 10;
            if (path == null || !path.canReach()) { path = null; w.owner.workStatus("Не могу подойти к торговцу."); return; }
        }
        if (path == null) return;
        w.owner.workStatus("Подхожу к торговцу.");
        w.route.walk(path);
    }

    /** Cells within a hand's reach of the trader with a clear line to him, over a fence too. */
    private Path standPath(ServerLevel level, AbstractVillager trader) {
        Set<BlockPos> feet = new HashSet<>();
        BlockPos at = trader.blockPosition();
        for (int dy = -2; dy <= 1; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos pos = at.offset(dx, dy, dz);
            if (!CompanionMiningNavigation.standable(w.owner, pos)) continue;
            Vec3 eye = Vec3.atBottomCenterOf(pos).add(0, w.owner.getEyeHeight(), 0);
            if (trader.getBoundingBox().distanceToSqr(eye) > (REACH - .2) * (REACH - .2)) continue;
            if (level.clip(new ClipContext(eye, trader.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w.owner)).getType() != HitResult.Type.MISS) continue;
            feet.add(pos);
        }
        return feet.isEmpty() ? null : w.route.toCells(feet);
    }

    private void finish(ServerLevel level, boolean timeUp) {
        int left = queue.size() + (current != null ? 1 : 0);
        w.route.stop();
        String report = report(level, timeUp, left);
        boolean any = !asked.isEmpty();
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "scouting_finished", "entity", w.owner.getUUID(), "asked", asked.size(), "asleep", asleep, "unreached", unreached);
        w.finish(any, report);
    }

    // ---- the report ----

    private String report(ServerLevel level, boolean timeUp, int left) {
        StringBuilder text = new StringBuilder();
        if (asked.isEmpty()) text.append(asleep > 0 ? "Никого не расспросил." : "Ни до кого не дошёл.");
        else {
            Map<String, Integer> kinds = new LinkedHashMap<>();
            for (var entry : asked) kinds.merge(profession(entry.kind()), 1, Integer::sum);
            text.append("Торговцев расспросил: ").append(asked.size()).append(" (")
                    .append(kinds.entrySet().stream().map(kind -> kind.getValue() > 1 ? kind.getKey() + " × " + kind.getValue() : kind.getKey()).collect(Collectors.joining(", "))).append(").");
        }
        if (asleep > 0) text.append(" Ещё ").append(asleep).append(asleep == 1 ? " спит" : " спят").append(", расспрошу утром.");
        if (unreached > 0) text.append(" До ").append(unreached).append(" не добрался.");
        if (timeUp && left > 0) text.append(" Три минуты вышли, остальных не успел.");
        for (String deal : bestDeals(level)) text.append(' ').append(deal);
        return text.toString();
    }

    /** Up to three worth saying: enchanted books first, then diamond things, then emeralds for what is in his bag. */
    private List<String> bestDeals(ServerLevel level) {
        record Scored(int score, String text) { }
        List<Scored> found = new ArrayList<>();
        for (var entry : asked) for (var deal : entry.deals()) {
            if (deal.left() <= 0) continue;
            String got = deal.get().item();
            int score = !deal.get().enchantment().isEmpty() && got.equals("minecraft:enchanted_book") ? 3
                    : got.contains("diamond") || got.contains("netherite") ? 2
                    : got.equals("minecraft:emerald") && inBag(deal.give().item()) ? 1 : 0;
            if (score == 0) continue;
            String pay = text(level, deal.give()) + (deal.give2() == null ? "" : " и " + text(level, deal.give2()));
            found.add(new Scored(score, profession(entry.kind()) + ": " + text(level, deal.get()) + " за " + pay + "."));
        }
        return found.stream().sorted(Comparator.comparingInt(Scored::score).reversed()).limit(SHOWN_DEALS).map(Scored::text).toList();
    }

    private boolean inBag(String item) {
        for (ItemStack stack : w.items.carriedItems()) if (!stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item)) return true;
        return false;
    }

    /** «Изумруд × 15», «книга «Починка»»: the player's words, not ids. */
    private static String text(ServerLevel level, CompanionTraders.Stack stack) {
        ItemStack item = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(stack.item())));
        String name = item.getHoverName().getString();
        // Mid-sentence, as the breeding report says «пшеница × 2»: «за изумруд × 15», not «за Изумруд × 15».
        if (!name.isEmpty()) name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        if (!stack.enchantment().isEmpty()) {
            String enchantment = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(ResourceLocation.parse(stack.enchantment()))
                    .map(holder -> Enchantment.getFullname(holder, stack.level()).getString()).orElse(stack.enchantment());
            name = stack.item().equals("minecraft:enchanted_book") ? "книга «" + enchantment + "»" : name + " (" + enchantment + ")";
        }
        return stack.count() > 1 ? name + " × " + stack.count() : name;
    }

    private static String profession(String kind) { return PROFESSIONS.getOrDefault(kind, "Торговец"); }
}

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
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    /** A remembered village is walked to from at most this far (same dimension); farther he asks to be led. */
    static final int FAR_RADIUS = 500;
    static final String TOO_FAR = "Далеко, отведи меня: деревня, которую помню, дальше " + FAR_RADIUS + " блоков или в другом мире.";
    /** The work's own deadline (CompanionWork): the walk there, the round and a minute over, or the far scout is cut before its report. */
    static final int WORK_SECONDS = (CompanionFarWalk.LIMIT + LIMIT) / 20 + 60;
    private static final int LOOK_TICKS = 30;
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
    private long startedAt, lookUntil;
    private final CompanionTraderWalk walk;
    /** The walk to a remembered spot (idle in the round itself), where the round is centred, and who was forgotten at the end. */
    private final CompanionFarWalk far;
    private BlockPos centre;
    private boolean walked;
    private final List<String> forgotten = new ArrayList<>();

    CompanionScouting(CompanionWork w) { this.w = w; walk = new CompanionTraderWalk(w); far = new CompanionFarWalk(w); }

    /** Null if the order is taken; no trader round him is a refusal that says so. */
    CompanionActionResult begin() {
        cancel();
        if (!(w.owner.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Не могу осмотреться.");
        centre = w.owner.blockPosition();
        queue.addAll(scan(level));
        if (!queue.isEmpty()) return null;
        // Nobody trades round him: the nearest remembered trader of this dimension, if not too far; the round is made there.
        String dimension = level.dimension().location().toString();
        var remembered = w.owner.traders().entries();
        if (remembered.isEmpty()) return CompanionActionResult.failed("Деревни рядом не вижу. Могу поискать: скажи, в какую сторону света идти.");
        var nearest = remembered.stream().filter(e -> e.dimension().equals(dimension)).min(Comparator.comparingDouble(e -> e.pos().distSqr(centre)));
        if (nearest.isEmpty() || nearest.get().pos().distSqr(centre) > (double) FAR_RADIUS * FAR_RADIUS)
            return CompanionActionResult.failed(TOO_FAR);
        // Without chunk loading he would stop where the world stops around him, half way, until the order times out.
        if (!CompanionErrand.holdsChunks(w.owner, level))
            return CompanionActionResult.failed("Один далеко не пойду: у меня выключена подгрузка чанков. Включи «Работа вдали от игрока» в «Поведении» или отведи меня.");
        far.begin(CompanionTraders.village(remembered.stream().filter(e -> e.dimension().equals(dimension)).map(CompanionTraders.Entry::pos).toList(), nearest.get().pos()));
        return null;
    }

    private List<UUID> scan(ServerLevel level) { return nearby(level, w.owner).stream().map(AbstractVillager::getUUID).toList(); }

    /** Traders within {@link #RADIUS} of him, nearest first. */
    static List<AbstractVillager> nearby(ServerLevel level, CompanionEntity owner) {
        List<AbstractVillager> traders = level.getEntitiesOfClass(AbstractVillager.class, new AABB(owner.blockPosition()).inflate(RADIUS, RADIUS, RADIUS),
                trader -> isTrader(trader) && trader.distanceToSqr(owner) <= RADIUS * RADIUS);
        traders.sort(Comparator.comparingDouble(owner::distanceToSqr));
        return traders;
    }

    /** A villager with a profession (not jobless, not a nitwit, not a child) or a wandering trader. */
    static boolean isTrader(AbstractVillager trader) {
        if (!trader.isAlive() || trader.isBaby()) return false;
        if (!(trader instanceof Villager villager)) return true;
        var profession = villager.getVillagerData().getProfession();
        return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT;
    }

    void cancel() {
        if (current != null || !queue.isEmpty() || far.active()) w.route.stop();
        far.cancel();
        queue.clear(); asked.clear(); forgotten.clear(); current = null; centre = null; walked = false;
        asleep = unreached = 0; startedAt = lookUntil = 0;
    }

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (startedAt == 0) startedAt = now;
        if (far.active()) { travel(level, now); return; }
        if (now - startedAt > LIMIT) { finish(level, true); return; }
        if (current == null) {
            if (queue.isEmpty()) { finish(level, false); return; }
            current = queue.removeFirst(); walk.begin(now); lookUntil = 0;
        }
        if (!(level.getEntity(current) instanceof AbstractVillager trader) || !trader.isAlive()) { next(); return; }
        if (trader.isSleeping()) { asleep++; next(); return; }
        if (!walk.inReach(trader)) {
            if (walk.approach(level, trader, now) == CompanionTraderWalk.Step.GAVE_UP) { unreached++; next(); }
            return;
        }
        w.route.stop();
        w.owner.getLookControl().setLookAt(trader, 30, 30);
        trader.getLookControl().setLookAt(w.owner, 30, 30);
        w.owner.workStatus("Смотрю, что он продаёт.");
        if (lookUntil == 0) lookUntil = now + LOOK_TICKS;
        if (now < lookUntil) return;
        asked.add(w.owner.traders().remember(trader, level));
        w.owner.refreshTraders();
        next();
    }

    /** The way to the remembered village, in legs like any long walk and never a teleport; its own time. Arrived, the round begins from here. */
    private void travel(ServerLevel level, long now) {
        switch (far.step(now, "Иду к деревне, которую помню.")) {
            case WALKING -> { }
            case ARRIVED -> {
                startedAt = 0; walked = true; centre = w.owner.blockPosition();
                queue.addAll(scan(level));
            }
            case TIMEOUT -> giveUp("Шёл к деревне слишком долго, не дошёл.");
            case FAILED -> giveUp("До деревни, которую помню, дойти не смог: нет пути.");
        }
    }

    private void giveUp(String message) {
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "scouting_far_failed", "entity", w.owner.getUUID(), "to", far.target().toShortString());
        far.cancel();
        w.route.stop();
        w.finish(false, message);
    }

    private void next() { current = null; walk.end(); }

    private void finish(ServerLevel level, boolean timeUp) {
        int left = queue.size() + (current != null ? 1 : 0);
        w.route.stop();
        forgetMissing(level);
        String report = report(level, timeUp, left);
        boolean any = !asked.isEmpty();
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "scouting_finished", "entity", w.owner.getUUID(), "asked", asked.size(), "asleep", asleep, "unreached", unreached);
        w.finish(any, report);
    }

    /** A remembered trader whose spot is within the round's reach, in a loaded chunk, and who is not found alive, is not there any more. */
    private void forgetMissing(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        for (var entry : w.owner.traders().entries()) {
            if (!entry.dimension().equals(dimension) || entry.pos().distSqr(centre) > (double) RADIUS * RADIUS || !level.isLoaded(entry.pos())) continue;
            if (level.getEntity(entry.id()) instanceof AbstractVillager trader && trader.isAlive()) continue;
            w.owner.traders().forget(entry.id());
            forgotten.add(profession(entry.kind()));
        }
        if (!forgotten.isEmpty()) w.owner.refreshTraders();
    }

    // ---- the report ----

    private String report(ServerLevel level, boolean timeUp, int left) {
        StringBuilder text = new StringBuilder(walked ? "Дошёл до деревни, которую помнил. " : "");
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
        if (!forgotten.isEmpty()) text.append(" Забыл тех, кого на месте нет: ").append(String.join(", ", forgotten)).append('.');
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
    static String text(ServerLevel level, CompanionTraders.Stack stack) {
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

    static String profession(String kind) { return PROFESSIONS.getOrDefault(kind, "Торговец"); }
}

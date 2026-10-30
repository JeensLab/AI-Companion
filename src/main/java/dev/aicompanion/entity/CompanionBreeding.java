package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * breed_animals, like a player: two grown animals of a kind that are ready (no calf, no pause after the last breeding, not in love
 * already) are walked up to and each is fed its food from the bag with the offering gesture and the item really taken; the game
 * itself does the rest (they walk to each other and a calf is born). Nothing is made up: no food, no pair or no calf is said so.
 */
final class CompanionBreeding {
    /** From his eye to the animal: a hand's reach, over a fence too. */
    private static final double REACH = 3.0;
    /** After the last feeding the calves are waited for this long. */
    private static final int WAIT_TICKS = 20 * 30;
    /** How far apart the two may stand: the game's partner search reaches eight blocks and the animals drift while he walks, the nearest pair is taken. */
    private static final double MATE = 7;
    private static final Map<Item, String> FOODS = new LinkedHashMap<>();
    static {
        FOODS.put(Items.WHEAT, "пшеница"); FOODS.put(Items.CARROT, "морковь"); FOODS.put(Items.POTATO, "картофель"); FOODS.put(Items.BEETROOT, "свёкла");
        FOODS.put(Items.WHEAT_SEEDS, "семена пшеницы"); FOODS.put(Items.MELON_SEEDS, "семена арбуза"); FOODS.put(Items.PUMPKIN_SEEDS, "семена тыквы");
        FOODS.put(Items.BEETROOT_SEEDS, "семена свёклы"); FOODS.put(Items.GOLDEN_CARROT, "золотая морковь"); FOODS.put(Items.DANDELION, "одуванчик");
    }
    private record Pair(Animal first, Animal second) { }

    final CompanionWork w;
    private CompanionGame.Kind kind, chosen;
    private int wanted, pairs, calves, rechosen;
    private boolean started, firstFed, farApart;
    private UUID first, second;
    private long lastFed, progressTime, noPairSince, nextChoice, nextPath, strayedSince;
    private double bestDistance;
    private Path path;
    private Vec3 pathAt = Vec3.ZERO, marked = Vec3.ZERO;
    private final Set<UUID> babiesBefore = new HashSet<>(), parents = new HashSet<>();
    private final Map<UUID, Long> skipped = new HashMap<>();
    private final Map<Item, Integer> used = new LinkedHashMap<>();

    CompanionBreeding(CompanionWork w) { this.w = w; }

    /** Null if the order is taken. */
    CompanionActionResult begin(String item, int count) {
        CompanionGame.Kind requested = CompanionGame.Kind.of(item);
        if (requested == null && !CompanionGame.anyKind(item)) return CompanionActionResult.failed("Не понял, каких животных разводить: коров, овец, свиней, кур или кроликов.");
        if (count < 1 || count > CompanionGame.MAX_PEN) return CompanionActionResult.failed("За раз развожу от 1 до " + CompanionGame.MAX_PEN + " пар.");
        kind = chosen = requested; wanted = count; pairs = calves = rechosen = 0; started = firstFed = false; noPairSince = -1; nextChoice = nextPath = strayedSince = 0;
        parents.clear(); babiesBefore.clear(); skipped.clear(); used.clear();
        cancel();
        return null;
    }

    /** Fed animals stay in love for their half minute, the food is gone: nothing else is left to undo. */
    void cancel() { first = second = null; firstFed = false; path = null; }

    void tick(ServerLevel level) {
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        long now = level.getGameTime();
        if (!started) { started = true; babiesBefore.addAll(babyIds(level)); lastFed = now; }
        skipped.values().removeIf(until -> until < now);
        if (first == null && pairs < wanted && now >= nextChoice) {
            Pair pair = choosePair(level);
            if (pair != null) {
                noPairSince = -1; first = pair.first.getUUID(); second = pair.second.getUUID(); firstFed = false; path = null;
                mark(now);
            } else {
                // No pair for two seconds on end (one look may fall in the moment an animal is in the air): then it is said. Ready
                // animals that only stand too far apart are waited for longer: grazing animals wander together as often as apart.
                if (noPairSince < 0) noPairSince = now;
                nextChoice = now + 10;
                if (pairs == 0 && now - noPairSince > (farApart ? 20 * 20 : 40)) { w.finish(false, nobody() + shortfall(level)); return; }
                if (farApart && pairs == 0) w.owner.workStatus("Жду, пока готовые животные подойдут друг к другу.");
            }
        }
        if (first != null) { feed(level, now); return; }
        // Every pair asked for is fed, or no more can be: the calves are waited for.
        if (pairs == 0) return;
        calves = newBabies(level);
        if (calves >= pairs || now - lastFed > WAIT_TICKS) { finish(level, now); return; }
        w.owner.getNavigation().stop();
        w.owner.workStatus("Жду, пока родятся детёныши: " + calves + " из " + pairs + ".");
    }

    private void finish(ServerLevel level, long now) {
        boolean all = pairs >= wanted && calves >= pairs;
        String fed = "Накормил пар: " + pairs + " из " + wanted + " (ушло: " + usedText() + ").";
        String born = calves >= pairs ? " Родилось: " + young(chosen, calves) + "."
                : " Родилось: " + young(chosen, calves) + " (ждал " + (now - lastFed) / 20 + " с" + apart(level) + ").";
        w.finish(all, fed + born + (pairs < wanted ? " " + shortfall(level) : ""));
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "breeding_finished", "entity", w.owner.getUUID(), "pairs", pairs, "calves", calves);
    }

    // ---- the pair ----

    private boolean ready(Animal animal) { return animal.isAlive() && !animal.isBaby() && animal.getAge() == 0 && animal.canFallInLove(); }

    private List<Animal> adults(ServerLevel level, CompanionGame.Kind of) {
        int radius = Math.clamp(w.owner.getSearchRadius(), 16, 32);
        return level.getEntitiesOfClass(Animal.class, new AABB(w.owner.blockPosition()).inflate(radius, 8, radius),
                animal -> animal.isAlive() && !animal.isBaby() && CompanionGame.Kind.of(animal) == of);
    }

    /** The two ready animals standing nearest to each other (the game's own search for a partner reaches 8 blocks) and able to walk to each other. */
    private Pair choosePair(ServerLevel level) {
        farApart = false;
        for (CompanionGame.Kind candidate : kind != null ? new CompanionGame.Kind[]{kind} : CompanionGame.Kind.values()) {
            List<Animal> ready = adults(level, candidate).stream().filter(this::ready).filter(animal -> !skipped.containsKey(animal.getUUID()))
                    .sorted(Comparator.comparingDouble(w.owner::distanceToSqr)).limit(10).toList();
            if (ready.size() < 2 || foodCount(ready.getFirst()) < 2) continue;
            List<Pair> pairs = new ArrayList<>();
            for (int i = 0; i < ready.size(); i++) for (int j = i + 1; j < ready.size(); j++)
                if (ready.get(i).distanceToSqr(ready.get(j)) <= MATE * MATE) pairs.add(new Pair(ready.get(i), ready.get(j)));
            pairs.sort(Comparator.comparingDouble(pair -> pair.first.distanceToSqr(pair.second)));
            for (Pair pair : pairs) if (together(pair.first, pair.second)) { chosen = candidate; farApart = false; return pair; }
            farApart = true;
        }
        return null;
    }

    private static boolean together(Animal a, Animal b) {
        // The game builds no route for an animal in the air (just spawned, falling): that says nothing against the pair.
        if (!a.onGround() || !b.onGround()) return true;
        Path route = a.getNavigation().createPath(b, 0);
        return route != null && route.canReach();
    }

    // ---- the feeding ----

    private void feed(ServerLevel level, long now) {
        UUID id = firstFed ? second : first;
        Animal animal = level.getEntity(id) instanceof Animal found && ready(found) ? found : null;
        if (animal == null) { drop(level, now, id, 20 * 20); return; }
        // The cows drift while he walks up: if the two have wandered apart on the way, they are chosen again from where he stands.
        if (!firstFed && rechosen < 8 && level.getEntity(second) instanceof Animal mate && animal.distanceToSqr(mate) > (MATE + 1) * (MATE + 1)) {
            rechosen++; first = second = null; path = null;
            return;
        }
        // The second one has wandered off from the first, which is in love: it is given ten seconds to come back (cows turn as often as they go).
        if (firstFed && level.getEntity(first) instanceof Animal fed && fed.distanceToSqr(animal) > 8.5 * 8.5 && (strayedSince == 0 || now - strayedSince < 20 * 10)) {
            if (strayedSince == 0) strayedSince = now;
            w.owner.getNavigation().stop();
            w.owner.workStatus("Жду, пока вторая " + CompanionGame.Kind.of(animal).one + " подойдёт к первой.");
            return;
        }
        ItemStack food = foodFor(animal);
        if (food.isEmpty()) { w.finish(false, "Корм кончился на середине. " + (pairs > 0 ? "Накормил пар: " + pairs + ". " : nobody()) + shortfall(level)); return; }
        if (animal.getBoundingBox().distanceToSqr(w.owner.getEyePosition()) > REACH * REACH) { approach(level, animal, now); return; }
        w.owner.getNavigation().stop();
        w.owner.getLookControl().setLookAt(animal, 30, 30);
        ItemStack held = hold(food);
        if (held.isEmpty() || !animal.isFood(held)) { w.finish(false, "Не могу взять корм в руку: в сумке нет места. Освободи ячейку."); return; }
        animal.setInLove(null);
        w.owner.animateAction(3, animal.blockPosition(), held);
        used.merge(held.getItem(), 1, Integer::sum);
        held.shrink(1);
        w.owner.setChanged();
        w.cooldown = 8;
        mark(now); path = null;
        if (!firstFed) { firstFed = true; return; }
        pairs++; parents.add(first); parents.add(second); first = second = null; lastFed = now;
        w.owner.workStatus("Накормил пару: " + pairs + " из " + wanted + ".");
    }

    /** A new animal to go to: the clock of progress starts again. */
    private void mark(long now) { progressTime = now; bestDistance = Double.MAX_VALUE; marked = w.owner.position(); nextPath = 0; }

    private void approach(ServerLevel level, Animal animal, long now) {
        double distance = animal.getBoundingBox().distanceToSqr(w.owner.getEyePosition());
        // Closer, or on the way (round a fence the way is not a straight line): either is progress.
        if (distance + .05 < bestDistance) { bestDistance = distance; progressTime = now; }
        if (w.owner.position().distanceToSqr(marked) > .7 * .7) { marked = w.owner.position(); progressTime = now; }
        if (now - progressTime > 300) { drop(level, now, animal.getUUID(), 20 * 20); return; }
        if (now >= nextPath && (path == null || w.owner.getNavigation().isDone() || w.owner.getNavigation().isStuck() || pathAt.distanceToSqr(animal.position()) > 4)) {
            // Both within reach from one spot, if the two stand near each other: no walk between the two feedings, no time to drift apart.
            Animal mate = firstFed || !(level.getEntity(second) instanceof Animal found) ? null : found;
            path = mate == null ? standPath(animal, null) : standPath(animal, mate);
            if (path == null || !path.canReach()) path = standPath(animal, null);
            pathAt = animal.position();
            nextPath = now + 5;
        }
        if (path == null || !path.canReach()) {
            // In the middle of a pen no hand reaches it over the fence: it is waited for, cows do not stand still.
            w.owner.getNavigation().stop();
            w.owner.workStatus("Жду, пока " + CompanionGame.Kind.of(animal).one + " окажется в пределах руки.");
            return;
        }
        w.owner.workStatus("Подхожу к " + CompanionGame.Kind.of(animal).one + ", чтобы покормить.");
        CompanionMiningNavigation.walk(w.owner, path);
    }

    /** Cells from which the animal is within reach, over a fence or not. */
    private Path standPath(Animal animal, Animal also) {
        Set<BlockPos> feet = new HashSet<>();
        BlockPos at = animal.blockPosition();
        for (int dy = -2; dy <= 1; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos pos = at.offset(dx, dy, dz);
            if (!CompanionMiningNavigation.standable(w.owner, pos)) continue;
            Vec3 eye = Vec3.atBottomCenterOf(pos).add(0, w.owner.getEyeHeight(), 0);
            if (animal.getBoundingBox().distanceToSqr(eye) <= (REACH - .2) * (REACH - .2) && (also == null || also.getBoundingBox().distanceToSqr(eye) <= (REACH - .2) * (REACH - .2))) feet.add(pos);
        }
        return feet.isEmpty() ? null : w.owner.getNavigation().createPath(feet, 0);
    }

    /**
     * This one cannot be got at, ran off or was fed by somebody: it is left for a while. An animal already fed is given another
     * partner if one is ready; otherwise the pair is chosen again.
     */
    private void drop(ServerLevel level, long now, UUID id, int skipTicks) {
        skipped.put(id, now + skipTicks);
        w.owner.getNavigation().stop();
        path = null;
        if (firstFed && id.equals(second) && level.getEntity(first) instanceof Animal fed && fed.isAlive()) {
            Animal other = adults(level, chosen).stream().filter(animal -> ready(animal) && !skipped.containsKey(animal.getUUID()) && animal.distanceToSqr(fed) <= 7 * 7 && together(fed, animal))
                    .min(Comparator.comparingDouble(fed::distanceToSqr)).orElse(null);
            if (other != null) { second = other.getUUID(); mark(now); return; }
        }
        first = second = null; firstFed = false;
    }

    private ItemStack hold(ItemStack food) {
        if (!w.owner.getMainHandItem().is(food.getItem()) && !w.owner.equipForWork(BuiltInRegistries.ITEM.getKey(food.getItem()).toString()).success()) return ItemStack.EMPTY;
        return w.owner.getMainHandItem();
    }

    private ItemStack foodFor(Animal animal) {
        for (ItemStack stack : w.items.carriedItems()) if (!stack.isEmpty() && animal.isFood(stack)) return stack;
        return ItemStack.EMPTY;
    }

    private int foodCount(Animal animal) {
        int count = 0;
        for (ItemStack stack : w.items.carriedItems()) if (!stack.isEmpty() && animal.isFood(stack)) count += stack.getCount();
        return count;
    }

    // ---- the calves ----

    private Set<UUID> babyIds(ServerLevel level) {
        return level.getEntitiesOfClass(Animal.class, new AABB(w.owner.blockPosition()).inflate(32, 8, 32), Animal::isBaby).stream().map(Animal::getUUID).collect(Collectors.toSet());
    }

    /** Calves that were not there before, born among the animals he fed. */
    private int newBabies(ServerLevel level) {
        List<Animal> fed = parents.stream().map(level::getEntity).filter(entity -> entity instanceof Animal).map(entity -> (Animal) entity).toList();
        return level.getEntitiesOfClass(Animal.class, new AABB(w.owner.blockPosition()).inflate(32, 8, 32),
                animal -> animal.isAlive() && animal.isBaby() && CompanionGame.Kind.of(animal) == chosen && !babiesBefore.contains(animal.getUUID())
                        && fed.stream().anyMatch(parent -> parent.distanceToSqr(animal) <= 12 * 12)).size();
    }

    /** Why a fed pair gave no calf, when it can be seen: they wandered off from each other. */
    private String apart(ServerLevel level) {
        List<Animal> fed = parents.stream().map(level::getEntity).filter(entity -> entity instanceof Animal).map(entity -> (Animal) entity).toList();
        if (fed.size() == 2 && fed.get(0).distanceTo(fed.get(1)) > 8) return ", сейчас между ними " + Math.round(fed.get(0).distanceTo(fed.get(1))) + " блоков: разошлись, не найдя друг друга";
        return ", пара не сошлась";
    }

    /** The beginning of a report with no pair fed: nobody, or one animal that got no partner. */
    private String nobody() {
        return used.isEmpty() ? "Не накормил никого. " : "Накормил только одно животное (ушло: " + usedText() + "), пары для него не вышло. ";
    }

    private String usedText() { return used.entrySet().stream().map(e -> foodName(e.getKey()) + " × " + e.getValue()).collect(Collectors.joining(", ")); }

    // ---- what is missing ----

    /** Why the next pair is not fed, in facts: the food in the bag and the grown animals round him. */
    private String shortfall(ServerLevel level) {
        CompanionGame.Kind of = chosen;
        if (kind == null && pairs == 0) {
            // Whichever kind there are most grown ones of is the one that was nearest to a pair.
            int most = -1;
            for (CompanionGame.Kind candidate : CompanionGame.Kind.values()) {
                int count = adults(level, candidate).size();
                if (count > most) { most = count; of = candidate; }
            }
            if (most <= 0) return "Рядом нет подходящих взрослых животных.";
        }
        if (of == null) return "";
        List<Animal> grown = adults(level, of);
        long readyCount = grown.stream().filter(this::ready).count(), pause = grown.stream().filter(animal -> animal.getAge() > 0).count();
        long inLove = grown.stream().filter(Animal::isInLove).count();
        CompanionGame.Kind asked = of;
        long young = level.getEntitiesOfClass(Animal.class, new AABB(w.owner.blockPosition()).inflate(32, 8, 32), animal -> animal.isAlive() && animal.isBaby() && CompanionGame.Kind.of(animal) == asked).size();
        List<String> why = new ArrayList<>();
        if (grown.isEmpty()) return "Рядом нет взрослых " + of.many + (young > 0 ? ": только детёныши, " + young : "") + ".";
        int food = foodCount(grown.getFirst());
        if (food < 2) why.add("Нечем кормить: нужен корм (" + foodList(grown.getFirst()) + "), " + (food == 0 ? "в сумке его нет." : "в сумке только " + food + ", а на пару нужно 2."));
        if (readyCount < 2) why.add("Пары не нашёл: взрослых " + of.many + " рядом " + grown.size() + ", готовы к размножению " + readyCount
                + (pause > 0 ? "; " + pause + " на паузе после недавнего размножения (она длится 5 минут)" : "") + (inLove > 0 ? "; " + inLove + " уже влюблены" : "")
                + (young > 0 ? "; детёнышей " + young : "") + ". Нужны две готовые.");
        else if (food >= 2 && pairs < wanted) {
            double gap = Double.MAX_VALUE;
            List<Animal> ready = grown.stream().filter(this::ready).toList();
            for (int i = 0; i < ready.size(); i++) for (int j = i + 1; j < ready.size(); j++) gap = Math.min(gap, ready.get(i).distanceTo(ready.get(j)));
            why.add("Готовые животные есть, но стоят далеко друг от друга или за преградой: ближайшие в " + Math.round(gap) + " блоках, а находят друг друга они не дальше чем за 8.");
        }
        return String.join(" ", why);
    }

    private static String foodList(Animal sample) {
        List<String> names = new ArrayList<>();
        for (var entry : FOODS.entrySet()) if (sample.isFood(new ItemStack(entry.getKey()))) names.add(entry.getValue());
        return names.isEmpty() ? "подходящий корм" : String.join(" или ", names);
    }

    private static String foodName(Item item) { return FOODS.getOrDefault(item, new ItemStack(item).getHoverName().getString()); }

    /** «1 телёнок», «2 телёнка», «5 телят». */
    private static String young(CompanionGame.Kind of, int count) {
        String[] forms = switch (of == null ? CompanionGame.Kind.COW : of) {
            case COW -> new String[]{"телёнок", "телёнка", "телят"};
            case SHEEP -> new String[]{"ягнёнок", "ягнёнка", "ягнят"};
            case PIG -> new String[]{"поросёнок", "поросёнка", "поросят"};
            case CHICKEN -> new String[]{"цыплёнок", "цыплёнка", "цыплят"};
            default -> new String[]{"крольчонок", "крольчонка", "крольчат"};
        };
        int last = count % 10, tens = count % 100;
        return count + " " + (last == 1 && tens != 11 ? forms[0] : last >= 2 && last <= 4 && (tens < 12 || tens > 14) ? forms[1] : forms[2]);
    }
}

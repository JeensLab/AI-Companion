package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.aicompanion.entity.CompanionItemClass.*;

/**
 * sort_items: the bag, or (tidy) the mixed chests nearby too, laid out by item class (CompanionItemClass), a chest for each class.
 * A class goes to the chest that holds most of it, else to an empty chest, else to a new chest made from his own wood next to the
 * others. Nothing is invented: every move is one tick between two real containers, so a stop at any moment loses nothing. A tidy
 * order keeps what it took out of a chest in the bag until it is put away, or put back mixed when no chest fits (and says so).
 */
final class CompanionSorting {
    /** Most chest openings one order makes; a bigger mess is a second order. */
    private static final int MAX_VISITS = 80;
    /** Bag slots kept free while chests are emptied: planks, bench and chest each want one on the way to a new chest. */
    private static final int KEEP_FREE = CompanionAutonomy.BAG_FREE_SLOTS;
    /** Wood goes last: it is also what a new chest is made of. */
    private static final List<CompanionItemClass> ORDER = List.of(STONE, ORES, FOOD, TOOLS, DROPS, OTHER, WOOD);

    private enum Visit { TAKE, PUT, RETURN }
    private record Unit(BlockPos pos, Container box) { }
    private record Part(int slot, ItemStack stack, int count) { }

    private final CompanionWork w;
    private boolean tidy, started, concluded, putting, walkingToPlace;
    private int soilKept, chestsSeen, visits, taken, sorted, returned;
    private Visit visit;
    /** Tidy: mixed chests still to empty. The others are left as they are and serve as destinations. */
    private final Set<BlockPos> pending = new HashSet<>();
    /** Chests that were to be emptied but were not (no way there, nothing could be taken, or things went back into them). */
    private final Set<BlockPos> skipped = new HashSet<>();
    private final Set<BlockPos> tookFrom = new HashSet<>(), unusable = new HashSet<>();
    /** The class and the chest it is put into on the next visit. */
    private final Map<CompanionItemClass, BlockPos> planned = new EnumMap<>(CompanionItemClass.class);
    /** Non-resources (tools, food…) that came out of a chest in this order: only these may go back in. */
    private final List<ItemStack> transit = new ArrayList<>();
    private final Map<CompanionItemClass, Map<BlockPos, Integer>> put = new EnumMap<>(CompanionItemClass.class);
    private final Map<BlockPos, Integer> mixedAt = new LinkedHashMap<>();
    /** Classes that got no chest, with the reason; they stay in the bag (tidy: they go back into a chest, mixed). */
    private final Map<CompanionItemClass, String> stuck = new EnumMap<>(CompanionItemClass.class);
    /** Stuck on purpose, not a failure: the few logs left over after the chests were made are the wood he keeps. */
    private final Set<CompanionItemClass> spare = EnumSet.noneOf(CompanionItemClass.class);
    private final List<BlockPos> made = new ArrayList<>();

    CompanionSorting(CompanionWork w) { this.w = w; }

    /** The item of a sort_items order: «bag» (or none) lays out the bag, «chests» tidies the mixed chests nearby too. */
    static boolean validMode(String item) { return item == null || item.isEmpty() || item.equals("bag") || item.equals("chests"); }

    void begin(boolean tidy) {
        this.tidy = tidy;
        started = concluded = putting = walkingToPlace = false;
        chestsSeen = visits = taken = sorted = returned = 0;
        visit = null;
        int soil = 0;
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) if (CompanionTools.soilItem(w.owner.getItem(slot))) soil += w.owner.getItem(slot).getCount();
        soilKept = Math.min(8, soil);
        pending.clear(); skipped.clear(); tookFrom.clear(); unusable.clear(); planned.clear(); transit.clear();
        put.clear(); mixedAt.clear(); stuck.clear(); spare.clear(); made.clear();
    }

    /** One work tick of the STORE phase: decide where to go, walk there, open the chest, move the items. */
    void step(ServerLevel level) {
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        if (w.target == null && !decide(level)) return;
        BlockPos at = w.target.immutable();
        Container chest = w.chests.chestAt(level, at);
        if (chest == null) { giveUpChest(at); return; }
        w.owner.movementIntent(at);
        if (w.owner.distanceToSqr(at.getCenter()) > 3.2D * 3.2D || !CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), at)) {
            if (!w.approach(level, at, 2.8D)) giveUpChest(at);
            return;
        }
        w.owner.getNavigation().stop();
        w.owner.getLookControl().setLookAt(at.getX() + .5, at.getY() + .5, at.getZ() + .5);
        w.openedChest = at;
        w.animateChest(level, at, 1);
        level.playSound(null, at, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        w.owner.animateAction(3, at, ItemStack.EMPTY);
        int moved = switch (visit) { case TAKE -> takeAll(chest); case PUT -> putAll(chest, at); case RETURN -> returnAll(chest, at); };
        chest.setChanged();
        w.owner.setChanged();
        visits++;
        if (visit != Visit.TAKE) w.transferred += moved;
        if (visit == Visit.TAKE) {
            tookFrom.add(at);
            if (chest.isEmpty()) pending.remove(at);
            else if (moved == 0) skipPending(at);
        } else {
            if (visit == Visit.RETURN) skipPending(at);
            if (moved == 0) unusable.add(at);
        }
        w.owner.rememberObserved(at);
        w.owner.storageStatus(w.storageForAi());
        w.phase = CompanionWork.Phase.CLOSE;
        w.cooldown = 10;
        w.owner.workStatus(visit == Visit.TAKE ? "Вынимаю вещи из сундука." : "Раскладываю вещи по сундукам.");
    }

    /** A chest that vanished, is blocked or cannot be walked to is left out of this order. */
    private void giveUpChest(BlockPos at) {
        unusable.add(at);
        skipPending(at);
        w.resetTarget();
    }

    private void skipPending(BlockPos at) { if (pending.remove(at)) skipped.add(at); }

    // ----- Deciding ------------------------------------------------------------------------------------------------------------------

    /** Picks the next chest to visit (target and visit set: true), or does something else this tick (craft, place, finish: false). */
    private boolean decide(ServerLevel level) {
        if (visits >= MAX_VISITS) { conclude("Слишком много заходов к сундукам за один раз, остановился."); return false; }
        List<Unit> units = units(level);
        if (!started) {
            started = true;
            chestsSeen = units.size();
            if (tidy) for (Unit unit : units) if (classes(unit.box()) > 1) pending.add(unit.pos());
        }
        // Tidy: empty the mixed chests first; when the bag is full, put it away before taking the next ones.
        if (tidy && !pending.isEmpty()) {
            if (freeSlots() <= KEEP_FREE) putting = true;
            if (!putting) {
                Unit from = nextToEmpty(units);
                if (from != null) return go(from.pos(), Visit.TAKE);
            }
        }
        List<Part> parts = parts();
        Set<CompanionItemClass> present = EnumSet.noneOf(CompanionItemClass.class);
        for (Part part : parts) present.add(CompanionItemClass.of(part.stack()));
        planned.clear();
        Set<BlockPos> claimed = new HashSet<>();
        List<CompanionItemClass> lacking = new ArrayList<>();
        for (CompanionItemClass cls : ORDER) {
            if (!present.contains(cls) || stuck.containsKey(cls)) continue;
            Unit home = holder(units, cls, parts);
            if (home == null) home = emptyOne(units, cls, parts, claimed);
            if (home == null) { lacking.add(cls); continue; }
            planned.put(cls, home.pos());
            claimed.add(home.pos());
        }
        // Wood waits while another class still needs a new chest: the wood is what that chest is made of.
        if (lacking.stream().anyMatch(cls -> cls != WOOD)) planned.remove(WOOD);
        BlockPos nearest = null;
        for (BlockPos pos : planned.values()) if (nearest == null || nearer(pos, nearest)) nearest = pos;
        if (nearest != null) return go(nearest, Visit.PUT);
        if (!lacking.isEmpty()) { makeChest(level, units, lacking); return false; }
        if (tidy) {
            Unit back = anywhere(units, parts);
            if (back != null) return go(back.pos(), Visit.RETURN);
            if (putting && !pending.isEmpty() && freeSlots() > KEEP_FREE) { putting = false; return false; }
        }
        conclude(null);
        return false;
    }

    private boolean go(BlockPos pos, Visit next) {
        w.target = pos.immutable();
        visit = next;
        w.resetApproach();
        return true;
    }

    /** The mixed chest to empty next: the nearest one that fits into the free bag slots whole, else the nearest. */
    private Unit nextToEmpty(List<Unit> units) {
        Set<BlockPos> here = new HashSet<>();
        for (Unit unit : units) here.add(unit.pos());
        for (BlockPos gone : new ArrayList<>(pending)) if (!here.contains(gone)) skipPending(gone);
        Unit best = null;
        boolean bestFits = false;
        for (Unit unit : units) {
            if (!pending.contains(unit.pos())) continue;
            if (unit.box().isEmpty()) { pending.remove(unit.pos()); continue; }
            boolean fits = stacks(unit.box()) <= freeSlots() - KEEP_FREE;
            if (best == null || fits && !bestFits) { best = unit; bestFits = fits; }
        }
        return best;
    }

    /** A chest a class may go into: not given up, and in a tidy order not one that is still to be emptied. */
    private boolean usable(Unit unit) { return !unusable.contains(unit.pos()) && !pending.contains(unit.pos()); }

    /** The chest that already holds most of the class (the nearer one on a tie), if it has room for some of it. */
    private Unit holder(List<Unit> units, CompanionItemClass cls, List<Part> parts) {
        Unit best = null;
        int most = 0;
        for (Unit unit : units) {
            if (!usable(unit) || !fits(unit, cls, parts)) continue;
            int held = held(unit.box(), cls);
            if (held > most) { best = unit; most = held; }
        }
        return best;
    }

    private Unit emptyOne(List<Unit> units, CompanionItemClass cls, List<Part> parts, Set<BlockPos> claimed) {
        for (Unit unit : units) if (usable(unit) && !claimed.contains(unit.pos()) && unit.box().isEmpty() && fits(unit, cls, parts)) return unit;
        return null;
    }

    /** Tidy: where what found no chest of its kind goes back, a chest with room: a settled one first, then one emptied in part. */
    private Unit anywhere(List<Unit> units, List<Part> parts) {
        for (Unit unit : units) if (!unusable.contains(unit.pos()) && !pending.contains(unit.pos()) && takesStuck(unit, parts)) return unit;
        for (Unit unit : units) if (!unusable.contains(unit.pos()) && pending.contains(unit.pos()) && tookFrom.contains(unit.pos()) && takesStuck(unit, parts)) return unit;
        return null;
    }

    private boolean takesStuck(Unit unit, List<Part> parts) {
        for (Part part : parts) if (stuck.containsKey(CompanionItemClass.of(part.stack())) && room(unit.box(), part.stack()) > 0) return true;
        return false;
    }

    private boolean nearer(BlockPos a, BlockPos b) {
        BlockPos here = w.owner.blockPosition();
        return a.distSqr(here) < b.distSqr(here);
    }

    // ----- A new chest ---------------------------------------------------------------------------------------------------------------

    /** Like store_items: a chest he carries, else one made from his own wood (a tree is felled only in a bag order), set down beside the others. */
    private void makeChest(ServerLevel level, List<Unit> units, List<CompanionItemClass> lacking) {
        if (!w.chests.hasChestItem()) {
            if (w.crafting.approachCraftingTable()) return;
            if (!w.items.moveHandMaterialsToBag()) return;
            CompanionActionResult crafted = w.owner.craftFromBag("minecraft:chest", 1);
            if (crafted.success()) return; // the crafting gesture ends before the chest is set down
            String message = crafted.message();
            if (message.contains("безопасное место")) giveUp(lacking, "негде поставить верстак для нового сундука");
            else if (message.contains("места")) giveUp(lacking, "в сумке нет места, чтобы сделать сундук");
            else if (lacking.getFirst() == WOOD && !tidy) { stuck.put(WOOD, "дерева мало для отдельного сундука, оставил как запас"); spare.add(WOOD); }
            else if (tidy || w.woodAttempts >= 2) giveUp(lacking, "нет сундука для этого, а на новый не хватает дерева");
            else w.crafting.requestWood(CompanionWork.Phase.STORE);
            return;
        }
        BlockPos origin = w.insideHouse ? w.placementAnchor : w.owner.blockPosition();
        if (!units.isEmpty()) {
            BlockPos beside = units.getFirst().pos();
            if (w.owner.distanceToSqr(beside.getCenter()) <= 3.2D * 3.2D) origin = beside;
            else {
                if (!walkingToPlace) { w.resetApproach(); walkingToPlace = true; }
                if (w.approach(level, beside, 2.8D)) { w.owner.workStatus("Иду к сундукам, поставлю новый рядом."); return; }
            }
        }
        walkingToPlace = false;
        CompanionActionResult placed = w.owner.placeBlockNearResult("minecraft:chest", origin, pos -> !w.insideHouse || w.house.inside(pos));
        if (!placed.success()) { giveUp(lacking, "негде поставить новый сундук"); return; }
        w.newChests++;
        if (w.owner.lastPlacedPosition() != null) made.add(w.owner.lastPlacedPosition());
        w.resetApproach();
    }

    private void giveUp(List<CompanionItemClass> lacking, String reason) {
        for (CompanionItemClass cls : lacking) stuck.put(cls, reason);
        walkingToPlace = false;
    }

    // ----- Moving items --------------------------------------------------------------------------------------------------------------

    /** Tidy: everything this chest holds that the bag takes, down to KEEP_FREE free slots. */
    private int takeAll(Container chest) {
        int moved = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty() || !bagTakes(stack)) continue;
            ItemStack proto = stack.copy(), offered = stack.copy();
            w.owner.storeLoot(offered);
            int got = stack.getCount() - offered.getCount();
            if (got <= 0) continue;
            stack.shrink(got);
            chest.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            if (!CompanionChests.resource(proto)) transit(proto, got);
            moved += got;
        }
        taken += moved;
        return moved;
    }

    /** The classes planned for this chest, in order. */
    private int putAll(Container chest, BlockPos at) {
        int moved = 0;
        for (CompanionItemClass cls : ORDER) {
            if (!at.equals(planned.get(cls))) continue;
            for (Part part : parts()) {
                if (CompanionItemClass.of(part.stack()) != cls) continue;
                int got = deposit(chest, part);
                if (got > 0) put.computeIfAbsent(cls, c -> new LinkedHashMap<>()).merge(at, got, Integer::sum);
                moved += got;
            }
        }
        sorted += moved;
        return moved;
    }

    private int returnAll(Container chest, BlockPos at) {
        int moved = 0;
        for (Part part : parts()) if (stuck.containsKey(CompanionItemClass.of(part.stack()))) moved += deposit(chest, part);
        if (moved > 0) mixedAt.merge(at, moved, Integer::sum);
        returned += moved;
        return moved;
    }

    /** Moves the part of a bag stack into the chest: into stacks of the same kind first, then into empty slots. */
    private int deposit(Container chest, Part part) {
        ItemStack stack = part.stack(), proto = stack.copyWithCount(1);
        int max = Math.min(chest.getMaxStackSize(), stack.getMaxStackSize()), moved = 0;
        for (int pass = 0; pass < 2 && moved < part.count(); pass++) for (int slot = 0; slot < chest.getContainerSize() && moved < part.count(); slot++) {
            ItemStack have = chest.getItem(slot);
            if ((pass == 0) == have.isEmpty() || !chest.canPlaceItem(slot, stack)) continue;
            if (!have.isEmpty() && !ItemStack.isSameItemSameComponents(have, stack)) continue;
            int amount = Math.min(part.count() - moved, max - have.getCount());
            if (amount <= 0) continue;
            chest.setItem(slot, have.isEmpty() ? stack.copyWithCount(amount) : have.copyWithCount(have.getCount() + amount));
            moved += amount;
        }
        if (moved == 0) return 0;
        stack.shrink(moved);
        w.owner.setItem(part.slot(), stack.isEmpty() ? ItemStack.EMPTY : stack);
        if (!CompanionChests.resource(proto)) untransit(proto, moved);
        return moved;
    }

    /** What of the bag may go into chests now: resources but the soil for supports, and what a tidy order took out of a chest. */
    private List<Part> parts() {
        List<Part> parts = new ArrayList<>();
        int soil = soilKept;
        List<ItemStack> budget = new ArrayList<>();
        for (ItemStack stack : transit) budget.add(stack.copy());
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) {
            ItemStack stack = w.owner.getItem(slot);
            if (stack.isEmpty()) continue;
            int count = 0;
            if (CompanionChests.resource(stack)) {
                int keep = CompanionTools.soilItem(stack) ? Math.min(stack.getCount(), soil) : 0;
                soil -= keep;
                count = stack.getCount() - keep;
            } else for (ItemStack owed : budget) if (ItemStack.isSameItemSameComponents(owed, stack)) {
                count = Math.min(owed.getCount(), stack.getCount());
                owed.shrink(count);
                break;
            }
            if (count > 0) parts.add(new Part(slot, stack, count));
        }
        return parts;
    }

    private void transit(ItemStack proto, int count) {
        for (ItemStack owed : transit) if (ItemStack.isSameItemSameComponents(owed, proto)) { owed.grow(count); return; }
        transit.add(proto.copyWithCount(count));
    }

    private void untransit(ItemStack proto, int count) {
        for (ItemStack owed : transit) if (ItemStack.isSameItemSameComponents(owed, proto)) { owed.shrink(count); break; }
        transit.removeIf(ItemStack::isEmpty);
    }

    private boolean bagTakes(ItemStack stack) {
        int merge = 0;
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) {
            ItemStack have = w.owner.getItem(slot);
            if (!have.isEmpty() && ItemStack.isSameItemSameComponents(have, stack)) merge += have.getMaxStackSize() - have.getCount();
        }
        return merge >= stack.getCount() || freeSlots() > KEEP_FREE;
    }

    private int freeSlots() {
        int free = 0;
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) if (w.owner.getItem(slot).isEmpty()) free++;
        return free;
    }

    // ----- Chests ---------------------------------------------------------------------------------------------------------------------

    /** The chests nearby (a double chest once, under its lower position), nearest first. */
    private List<Unit> units(ServerLevel level) {
        List<Unit> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        BlockPos here = w.owner.blockPosition();
        for (BlockPos pos : w.chests.blockEntitiesNear(level)) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChestBlock) || w.insideHouse && !w.house.inside(pos) || !seen.add(pos.immutable())) continue;
            BlockPos key = pos.immutable();
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state)).immutable();
                seen.add(other);
                if (other.asLong() < key.asLong()) key = other;
            }
            Container box = w.chests.chestAt(level, key);
            if (box != null) found.add(new Unit(key, box));
        }
        found.sort(Comparator.comparingDouble((Unit unit) -> unit.pos().distSqr(here)).thenComparingLong(unit -> unit.pos().asLong()));
        return found;
    }

    /** How much of the stack the chest takes: room in stacks of the same kind and in empty slots. */
    private static int room(Container chest, ItemStack stack) {
        int max = Math.min(chest.getMaxStackSize(), stack.getMaxStackSize()), room = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack have = chest.getItem(slot);
            if (!chest.canPlaceItem(slot, stack)) continue;
            if (have.isEmpty()) room += max;
            else if (ItemStack.isSameItemSameComponents(have, stack)) room += Math.max(0, max - have.getCount());
        }
        return room;
    }

    private static boolean fits(Unit unit, CompanionItemClass cls, List<Part> parts) {
        for (Part part : parts) if (CompanionItemClass.of(part.stack()) == cls && room(unit.box(), part.stack()) > 0) return true;
        return false;
    }

    private static int held(Container chest, CompanionItemClass cls) {
        int held = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && CompanionItemClass.of(stack) == cls) held += stack.getCount();
        }
        return held;
    }

    private static int classes(Container chest) {
        Set<CompanionItemClass> kinds = EnumSet.noneOf(CompanionItemClass.class);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (!chest.getItem(slot).isEmpty()) kinds.add(CompanionItemClass.of(chest.getItem(slot)));
        return kinds.size();
    }

    private static int stacks(Container chest) {
        int stacks = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (!chest.getItem(slot).isEmpty()) stacks++;
        return stacks;
    }

    // ----- The report -----------------------------------------------------------------------------------------------------------------

    /** The order is over: nothing more can be done, or he gave up with a reason. Success means nothing is left that should have moved. */
    private void conclude(String why) {
        // A few logs kept as a spare are no failure once something was laid out; nothing laid out at all is.
        boolean ok = why == null && spare.containsAll(stuck.keySet()) && pending.isEmpty() && skipped.isEmpty() && (spare.isEmpty() || sorted + returned > 0)
                && parts().stream().allMatch(part -> spare.contains(CompanionItemClass.of(part.stack())));
        concluded = true;
        String text = facts();
        if (text.isEmpty()) {
            if (!tidy) text = kept().isEmpty() ? "Раскладывать нечего: сумка пуста." : "Раскладывать нечего: в сумке только то, что нужно мне самому (" + kept() + ").";
            else if (chestsSeen == 0) { text = "Сундуков рядом не нашёл, наводить порядок не в чем."; ok = false; }
            else text = "В сундуках уже порядок: смешанных сундуков нет, раскладывать нечего.";
        }
        if (why != null) text += " " + why;
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "sort_finished", "entity", w.owner.getUUID(), "tidy", tidy, "taken", taken,
                "sorted", sorted, "returned", returned, "newChests", made.size(), "stuck", stuck.keySet(), "success", ok);
        w.finish(new CompanionActionResult(ok, text, ok ? CompanionActionResult.Status.SUCCESS : CompanionActionResult.Status.FAILED,
                new CompanionActionResult.Evidence(w.taskId, "sort_items", tidy ? "chests" : "bag", sorted + returned, w.owner.getUUID(), null, null)));
    }

    /** An order that ends from outside (time, a stall, no trees) still tells what was done and what is left in the bag. */
    String withFacts(String message) {
        if (concluded) return message;
        String facts = facts();
        return facts.isEmpty() ? message : facts + " " + message;
    }

    private String facts() {
        if (!started) return "";
        StringBuilder text = new StringBuilder();
        if (tidy && taken > 0) text.append("Вынул из сундуков ").append(items(taken)).append(". ");
        if (sorted > 0) {
            text.append("Разложил по сундукам ").append(items(sorted));
            if (!made.isEmpty()) text.append(", поставил новых сундуков: ").append(made.size());
            text.append(". ");
            for (var entry : put.entrySet()) {
                String label = entry.getKey().label;
                text.append(Character.toUpperCase(label.charAt(0))).append(label.substring(1)).append(": ")
                        .append(entry.getValue().values().stream().mapToInt(Integer::intValue).sum()).append(" — ").append(where(entry.getValue())).append(". ");
            }
        }
        if (returned > 0) text.append("Без разбора вернул ").append(items(returned)).append(" ").append(where(mixedAt)).append(" (для их вида не нашлось сундука). ");
        Map<CompanionItemClass, Integer> left = new EnumMap<>(CompanionItemClass.class);
        for (Part part : parts()) left.merge(CompanionItemClass.of(part.stack()), part.count(), Integer::sum);
        if (!left.isEmpty()) {
            if (sorted == 0 && returned == 0) text.append("Ничего не разложил. ");
            List<String> rows = new ArrayList<>();
            String otherwise = chestsSeen == 0 ? "сундуков рядом нет, а нового не из чего сделать" : tidy ? "в сундуках не нашлось места" : "не нашлось подходящего сундука";
            for (var entry : left.entrySet()) rows.add(entry.getKey().label + " — " + entry.getValue() + " (" + stuck.getOrDefault(entry.getKey(), otherwise) + ")");
            text.append("Остались у меня: ").append(String.join("; ", rows)).append(". ");
        }
        if (!pending.isEmpty() || !skipped.isEmpty()) text.append("Не разобрал сундуков: ").append(pending.size() + skipped.size())
                .append(freeSlots() <= KEEP_FREE ? " (в сумке нет места, чтобы вынуть из них вещи)" : "").append(". ");
        if (!unusable.isEmpty()) text.append("Не смог подойти к сундукам: ").append(String.join("; ", unusable.stream().map(BlockPos::toShortString).toList())).append(". ");
        String kept = kept();
        if (!kept.isEmpty() && text.length() > 0) text.append("У себя оставил: ").append(kept).append(". ");
        return text.toString().strip();
    }

    /** What the order leaves with him by its own rule: tools, armour, food, bench and chest, and the soil for supports. */
    private String kept() {
        Map<Integer, Integer> movable = new HashMap<>();
        for (Part part : parts()) movable.put(part.slot(), part.count());
        Map<String, Integer> kept = new LinkedHashMap<>();
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) {
            ItemStack stack = w.owner.getItem(slot);
            int stays = stack.getCount() - movable.getOrDefault(slot, 0);
            if (stack.isEmpty() || stays <= 0) continue;
            String name = CompanionTools.soilItem(stack) ? "земля для опор" : CompanionItemClass.of(stack) == TOOLS ? TOOLS.label
                    : CompanionItemClass.of(stack) == FOOD ? FOOD.label : !CompanionChests.resource(stack) ? "верстак и сундуки" : null;
            if (name != null) kept.merge(name, stays, Integer::sum);
        }
        return String.join(", ", kept.entrySet().stream().map(entry -> entry.getKey() + " — " + entry.getValue()).toList());
    }

    private String where(Map<BlockPos, Integer> at) {
        List<String> rows = new ArrayList<>();
        for (var entry : at.entrySet())
            rows.add(entry.getKey().toShortString() + (made.contains(entry.getKey()) ? " (новый)" : "") + (at.size() > 1 ? " — " + entry.getValue() : ""));
        return (at.size() > 1 ? "в сундуки на " : "в сундук на ") + String.join("; ", rows);
    }

    private static String items(int count) {
        int last = count % 10, tens = count % 100;
        return count + (last == 1 && tens != 11 ? " предмет" : last >= 2 && last <= 4 && (tens < 12 || tens > 14) ? " предмета" : " предметов");
    }
}

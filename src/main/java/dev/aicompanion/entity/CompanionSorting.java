package dev.aicompanion.entity;

import dev.aicompanion.entity.CompanionSortGroups.Group;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * «Разложи сундуки» (issue 32): in trips through his own bag he moves the things of a fixed set of chests until each chest holds what
 * the layout gives it ({@link CompanionSortGroups#layout}: pieces of each kind per chest, in real cells). Each trip is one chest opened:
 * what he carries that it lacks goes in, what it holds above its share comes out into the bag, in turns while anything moves; at a full
 * chest and a full bag he trades a stack for a stack. The next chest is the one where a trip, tried on copies, moves the most.
 * The layout is made once and again only when the set changes from outside (the player, a hopper, a broken chest): trips keep the totals.
 * The set is kept here for one work, what he carries until a sorting puts it away (a stopped one leaves it to the next); his own things
 * in the bag are never touched: only what he carries is put back.
 */
final class CompanionSorting {
    private static final double RADIUS = 6;
    private static final int MAX_CHESTS = 9;
    /** Openings in a row that moved nothing before the work gives up (every other chest is full, or the bag is). */
    private static final int MAX_IDLE = 3;
    /** Free bag cells needed to start: a trip carries out more than one stack. */
    private static final int MIN_FREE = 3;
    /** Openings allowed per stack of the set, and a few more: past that something keeps undoing the work (a hopper, a player), and he gives up. */
    private static final int TRIPS_PER_STACK = 3, TRIPS_SPARE = 6;
    private static final String LEFT_NOTE = " Часть вещей у меня в сумке.";
    private static final String[] NEAR = {"в ближнем", "во втором", "в третьем", "в четвёртом", "в пятом", "в шестом", "в седьмом", "в восьмом", "в девятом"};

    private final CompanionWork w;
    private final List<BlockPos> chests = new ArrayList<>();
    /** What he carries for the set, one stack per kind (its count may pass a full stack); the bag holds it among his own things. */
    private final List<ItemStack> carried = new ArrayList<>();
    private final Set<BlockPos> opened = new HashSet<>();
    private boolean closing, started, tidyOnly, reordered, left;
    private int idle, trips, tripLimit;
    private long leftAt;
    private ItemStack shown = ItemStack.EMPTY;
    /** The layout and what it was made for: the kinds of the set with the load, and which chests of the set were there. */
    private List<ItemStack> kinds = List.of();
    private int[][] keep;
    private int liveMask;

    CompanionSorting(CompanionWork w) { this.w = w; }

    /** The set is fixed at the request: ordinary chests (not trapped, not barrels, not under a block) within six blocks of the player, nearest first, a double one once. */
    void begin(ServerLevel level) {
        // What a stopped sorting left in his bag is still the player's: this one puts it away too, as much of it as the bag still holds (review round 4, finding 3).
        for (ItemStack carry : carried) carry.setCount(Math.min(carry.getCount(), count(w.owner, carry)));
        carried.removeIf(ItemStack::isEmpty);
        chests.clear(); opened.clear(); closing = false; started = false; tidyOnly = false; reordered = false; idle = 0; trips = 0; tripLimit = 0; shown = ItemStack.EMPTY; left = false;
        kinds = List.of(); keep = null; liveMask = 0;
        var player = w.owner.getOwnerPlayer();
        Vec3 from = player == null ? w.owner.position() : player.position();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : w.chests.blockEntitiesNear(level, BlockPos.containing(from), (int) RADIUS + 1)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() != Blocks.CHEST || state.getValue(ChestBlock.TYPE) == ChestType.RIGHT || pos.getCenter().distanceToSqr(from) > RADIUS * RADIUS
                    || w.chests.chestAt(level, pos) == null) continue;
            found.add(pos.immutable());
        }
        found.sort(Comparator.comparingDouble(pos -> pos.getCenter().distanceToSqr(from)));
        chests.addAll(found.subList(0, Math.min(MAX_CHESTS, found.size())));
    }

    void step(ServerLevel level) {
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        if (closing) { w.closeChest(); w.resetTarget(); closing = false; return; }
        if (chests.isEmpty()) { w.finish(false, "Рядом с тобой сундуков не вижу."); return; }
        if (!started) {
            started = true;
            if (carried.isEmpty() && freeBagCells() < MIN_FREE) { w.finish(false, "Сумка забита, сначала сложить мои вещи?"); return; }
            tripLimit = TRIPS_PER_STACK * stacksInSet(level) + TRIPS_SPARE;
        }
        if (w.target == null) {
            BlockPos next = nextChest(level);
            if (next == null) return;
            w.target = next; w.resetApproach();
        }
        Container chest = w.chests.chestAt(level, w.target);
        // A broken chest stays in the set (its place keeps the numbers of the report) and is skipped: the plan is made from the live ones.
        if (chest == null) { w.resetTarget(); return; }
        if (w.owner.distanceToSqr(w.target.getCenter()) > 3.2D * 3.2D || !w.items.visibleFrom(level, w.owner.getEyePosition(), w.target)) {
            if (!w.approach(level, w.target, 2.8D)) w.finish(false, "Не смог дойти до одного из сундуков." + (carried.isEmpty() ? "" : " Что нёс — у меня в сумке."));
            return;
        }
        w.owner.getNavigation().stop();
        w.owner.getLookControl().setLookAt(w.target.getX() + 0.5, w.target.getY() + 0.5, w.target.getZ() + 0.5);
        w.openedChest = w.target.immutable(); opened.add(w.openedChest);
        w.animateChest(level, w.openedChest, 1);
        level.playSound(null, w.openedChest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        layout(live(level));
        // Unfinished stacks merged first: their cells are the room a new kind of the chest's group needs (review round 4, finding 2).
        reordered |= tidy(chest);
        int moved = moves(chest, w.owner, carried, chests.indexOf(w.target));
        reordered |= tidy(chest);
        w.transferred += moved;
        // The closing tidy of the chests the trips never opened is no trip: with many empty chests it would run the limit out.
        if (!tidyOnly) { trips++; idle = moved == 0 ? idle + 1 : 0; }
        w.owner.animateAction(3, w.target, shown);
        chest.setChanged(); w.owner.setChanged();
        w.owner.workStatus("Раскладываю вещи по сундукам.");
        closing = true; w.cooldown = 10;
    }

    /**
     * One trip at a chest, on the real chest and bag or on copies: what he carries that the chest lacks goes in, what it holds above its
     * share comes out into the bag, in turns while anything moves (each frees room for the other); when neither can, a trade. Only what
     * the chest lacks goes in, so no chest gets more than its share: every trip that moves something brings the end nearer.
     * Returns how many pieces moved.
     */
    private int moves(Container chest, Container bag, List<ItemStack> load, int index) {
        int moved = 0;
        while (true) {
            int step = putIn(chest, bag, load, index) + takeOut(chest, bag, load, index);
            if (step == 0) step = trade(chest, bag, load, index);
            if (step == 0) return moved;
            moved += step;
        }
    }

    private int putIn(Container chest, Container bag, List<ItemStack> load, int index) {
        int moved = 0;
        for (ItemStack carry : load) {
            // The player may have taken it out of the bag meanwhile (his inventory screen): only what the bag still holds goes in.
            carry.setCount(Math.min(carry.getCount(), count(bag, carry)));
            int amount = Math.min(-excess(chest, index, carry), carry.getCount());
            if (amount <= 0) continue;
            int put = insert(chest, carry.copyWithCount(amount));
            if (put > 0) { remove(bag, carry, put); shown = carry.copyWithCount(1); carry.shrink(put); moved += put; }
        }
        load.removeIf(ItemStack::isEmpty);
        return moved;
    }

    /** What the chest holds above its share goes into the bag: onto his unfinished stacks of the kind first, then into empty cells, as a player's inventory takes it. */
    private int takeOut(Container chest, Container bag, List<ItemStack> load, int index) {
        int moved = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            int over = stack.isEmpty() ? 0 : Math.min(excess(chest, index, stack), stack.getCount());
            if (over <= 0) continue;
            int took = insert(bag, stack.copyWithCount(over));
            if (took == 0) continue;
            carry(load, stack.copyWithCount(took)); shown = stack.copyWithCount(1); moved += took;
            chest.setItem(slot, stack.getCount() == took ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - took));
        }
        return moved;
    }

    /**
     * A full chest and a bag that takes nothing: some of a carried kind the chest lacks (his smallest stack of it, which frees its cell)
     * takes the cell of a stack that is all above the chest's share, and that stack goes into the bag. Tried on copies, done only when
     * both sides have the room.
     */
    private int trade(Container chest, Container bag, List<ItemStack> load, int index) {
        for (ItemStack carry : List.copyOf(load)) {
            int lack = -excess(chest, index, carry), smallest = Integer.MAX_VALUE;
            for (int slot = 0; slot < bag.getContainerSize(); slot++) if (ItemStack.isSameItemSameComponents(bag.getItem(slot), carry)) smallest = Math.min(smallest, bag.getItem(slot).getCount());
            int amount = Math.min(Math.min(lack, smallest), carry.getCount());
            if (amount <= 0) continue;
            // A full chest is mostly full stacks of a few kinds: a stack like one tried before would fail the same way.
            List<ItemStack> tried = new ArrayList<>();
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack out = chest.getItem(slot);
                if (out.isEmpty() || ItemStack.isSameItemSameComponents(out, carry) || tried.stream().anyMatch(was -> ItemStack.matches(was, out)) || excess(chest, index, out) < out.getCount()) continue;
                tried.add(out);
                Container chestTry = copy(chest), bagTry = copy(bag);
                chestTry.setItem(slot, ItemStack.EMPTY);
                remove(bagTry, carry, amount);
                int put = insert(chestTry, carry.copyWithCount(amount));
                if (put < amount) insert(bagTry, carry.copyWithCount(amount - put));
                if (put == 0 || insert(bagTry, out.copy()) < out.getCount()) continue;
                copyBack(chestTry, chest); copyBack(bagTry, bag);
                shown = carry.copyWithCount(1); carry.shrink(put); load.removeIf(ItemStack::isEmpty); carry(load, out.copy());
                return put + out.getCount();
            }
        }
        return 0;
    }

    /** Where to go next: the chest where a trip, tried on copies, moves the most (the nearer one on a tie). Ends the work when no trip can move anything. */
    private BlockPos nextChest(ServerLevel level) {
        tidyOnly = false;
        List<Container> live = live(level);
        layout(live);
        if (trips > tripLimit) {
            w.finish(false, "Не получилось разложить всё: вещи в сундуках всё время меняются, остановился." + (carried.isEmpty() ? "" : " Часть вещей у меня в сумке.") + goneNote(live));
            return null;
        }
        if (idle >= MAX_IDLE) {
            w.finish(false, "Не получилось разложить всё: часть сундуков забита." + (carried.isEmpty() ? "" : " Часть вещей у меня в сумке.") + goneNote(live));
            return null;
        }
        BlockPos best = null; int bestMoved = 0; double bestDistance = Double.MAX_VALUE;
        boolean over = false;
        for (int i = 0; i < chests.size(); i++) {
            Container chest = live.get(i);
            if (chest == null) continue;
            boolean above = aboveShare(chest, i);
            over |= above;
            if (carried.isEmpty() && !above) continue;
            List<ItemStack> load = new ArrayList<>();
            for (ItemStack carry : carried) load.add(carry.copy());
            ItemStack was = shown;
            Container chestTry = copy(chest);
            tidy(chestTry);
            int moved = moves(chestTry, copy(w.owner), load, i);
            shown = was;
            double distance = w.owner.distanceToSqr(chests.get(i).getCenter());
            if (moved > bestMoved || moved > 0 && moved == bestMoved && distance < bestDistance) { best = chests.get(i); bestMoved = moved; bestDistance = distance; }
        }
        if (best != null) return best;
        if (!carried.isEmpty()) { w.finish(false, "Не получилось разложить всё: не поместилось " + carried.stream().mapToInt(ItemStack::getCount).sum() + ", они у меня в сумке." + goneNote(live)); return null; }
        if (over) { w.finish(false, "Не получилось разложить всё: часть сундуков забита." + goneNote(live)); return null; }
        // The last step: the order inside the chests the trips never opened (all of them, when there is one chest or all is in place).
        for (int i = 0; i < chests.size(); i++) {
            if (live.get(i) == null || opened.contains(chests.get(i))) continue;
            double distance = w.owner.distanceToSqr(chests.get(i).getCenter());
            if (distance < bestDistance) { best = chests.get(i); bestDistance = distance; tidyOnly = true; }
        }
        if (best != null) return best;
        w.finish(true, report(live));
        return null;
    }

    /** What the player hears: which group lies in which chest (counted from the one nearest to him at the request). */
    private String report(List<Container> live) {
        Map<Group, TreeSet<Integer>> where = new EnumMap<>(Group.class);
        int used = 0;
        for (int i = 0; i < live.size(); i++) {
            if (live.get(i) == null) continue;
            boolean any = false;
            for (int slot = 0; slot < live.get(i).getContainerSize(); slot++) {
                ItemStack stack = live.get(i).getItem(slot);
                if (stack.isEmpty()) continue;
                where.computeIfAbsent(CompanionSortGroups.of(stack), group -> new TreeSet<>()).add(i); any = true;
            }
            if (any) used++;
        }
        if (where.isEmpty()) return "В сундуках пусто, раскладывать нечего." + goneNote(live);
        // The groups of one chest together: «руда и металлы, еда — в ближнем», not the same chest named for each.
        Map<String, List<String>> byChest = new LinkedHashMap<>();
        where.forEach((group, at) -> byChest.computeIfAbsent(String.join(" и ", at.stream().map(i -> NEAR[i]).toList()), chest -> new ArrayList<>()).add(group.label));
        List<String> parts = new ArrayList<>();
        byChest.forEach((chest, groups) -> parts.add(String.join(", ", groups) + " — " + chest));
        return (w.transferred == 0 && !reordered ? "Всё уже лежит по группам: " : "Разложил по " + used + (used == 1 ? " сундуку: " : " сундукам: ")) + String.join("; ", parts) + "." + goneNote(live);
    }

    /** For the answer to the player: the chests of the set that were broken or built over meanwhile. */
    private static String goneNote(List<Container> live) {
        long gone = live.stream().filter(chest -> chest == null).count();
        return gone == 0 ? "" : gone == 1 ? " Пропустил сундук, которого больше нет." : " Пропустил сундуки, которых больше нет: " + gone + ".";
    }

    /** What the answer to «stop» adds: what he carries stays in his bag. */
    String stopNote(boolean sorting) {
        if (!sorting || carried.isEmpty()) return "";
        left = true; leftAt = w.owner.level().getGameTime();
        return LEFT_NOTE;
    }

    /** The same note for the order that ended the work, once and only just after: the work's own cancel answer is read by nobody («стоп» says its own). */
    String takeLeftNote() {
        boolean say = left && w.owner.level().getGameTime() - leftAt <= 100;
        left = false;
        return say ? LEFT_NOTE : "";
    }

    /** The chests of the set as they are now, by their place in the set; null for one that is gone. */
    private List<Container> live(ServerLevel level) {
        List<Container> live = new ArrayList<>();
        for (BlockPos pos : chests) live.add(w.chests.chestAt(level, pos));
        return live;
    }

    /**
     * The layout for the set as it is and what he carries. Trips only move things between the chests and the bag, so it is made again only
     * when the totals or the chests of the set change: made at every opening from what the chests hold, it would follow the half-done
     * work and send things back and forth.
     */
    private void layout(List<Container> live) {
        List<ItemStack> all = new ArrayList<>(carried);
        int mask = 0;
        for (int i = 0; i < live.size(); i++) {
            if (live.get(i) == null) continue;
            mask |= 1 << i;
            for (int slot = 0; slot < live.get(i).getContainerSize(); slot++) all.add(live.get(i).getItem(slot));
        }
        List<ItemStack> now = CompanionSortGroups.kinds(all);
        if (keep != null && mask == liveMask && now.size() == kinds.size() && now.stream().allMatch(kind -> kinds.stream().anyMatch(was -> ItemStack.matches(was, kind)))) return;
        List<CompanionSortGroups.Chest> infos = new ArrayList<>();
        for (int i = 0; i < live.size(); i++) {
            if (live.get(i) == null) continue;
            Map<Group, Double> cells = new EnumMap<>(Group.class);
            for (int slot = 0; slot < live.get(i).getContainerSize(); slot++) {
                ItemStack stack = live.get(i).getItem(slot);
                if (!stack.isEmpty()) cells.merge(CompanionSortGroups.of(stack), (double) stack.getCount() / stack.getMaxStackSize(), Double::sum);
            }
            infos.add(new CompanionSortGroups.Chest(i, i, live.get(i).getContainerSize(), cells));
        }
        kinds = now; liveMask = mask; keep = CompanionSortGroups.layout(infos, now);
    }

    /** How much of the stack's kind the chest holds above its share; below it, how much it lacks (negative). */
    private int excess(Container chest, int index, ItemStack like) {
        int share = 0;
        for (int k = 0; k < kinds.size(); k++) if (index < keep.length && ItemStack.isSameItemSameComponents(kinds.get(k), like)) share = keep[index][k];
        return count(chest, like) - share;
    }

    private boolean aboveShare(Container chest, int index) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (!chest.getItem(slot).isEmpty() && excess(chest, index, chest.getItem(slot)) > 0) return true;
        return false;
    }

    private static void carry(List<ItemStack> load, ItemStack stack) {
        for (ItemStack carry : load) if (ItemStack.isSameItemSameComponents(carry, stack)) { carry.grow(stack.getCount()); return; }
        load.add(stack);
    }

    private static Container copy(Container container) {
        SimpleContainer copy = new SimpleContainer(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) copy.setItem(slot, container.getItem(slot).copy());
        return copy;
    }

    private static void copyBack(Container from, Container to) {
        for (int slot = 0; slot < to.getContainerSize(); slot++) if (!ItemStack.matches(from.getItem(slot), to.getItem(slot))) to.setItem(slot, from.getItem(slot).copy());
    }

    private int stacksInSet(ServerLevel level) {
        int stacks = 0;
        for (Container chest : live(level)) if (chest != null) for (int slot = 0; slot < chest.getContainerSize(); slot++) if (!chest.getItem(slot).isEmpty()) stacks++;
        return stacks;
    }

    /** Fills the unfinished stacks first, then empty cells; returns how many pieces went in. */
    private static int insert(Container chest, ItemStack stack) {
        int left = stack.getCount();
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int slot = 0; slot < chest.getContainerSize() && left > 0; slot++) {
                ItemStack existing = chest.getItem(slot);
                if (pass == 0 ? existing.isEmpty() : !existing.isEmpty()) continue;
                if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) continue;
                int amount = Math.min(left, Math.min(chest.getMaxStackSize(), stack.getMaxStackSize()) - existing.getCount());
                if (amount <= 0) continue;
                chest.setItem(slot, existing.isEmpty() ? stack.copyWithCount(amount) : existing.copyWithCount(existing.getCount() + amount));
                left -= amount;
            }
        }
        return stack.getCount() - left;
    }

    /** Order inside the chest: unfinished stacks merged, then by group and item. */
    private static boolean tidy(Container chest) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot).copy();
            if (stack.isEmpty()) continue;
            for (ItemStack kept : stacks) {
                if (!ItemStack.isSameItemSameComponents(kept, stack)) continue;
                int move = Math.min(stack.getCount(), kept.getMaxStackSize() - kept.getCount());
                kept.grow(move); stack.shrink(move);
            }
            if (!stack.isEmpty()) stacks.add(stack);
        }
        stacks.sort(Comparator.comparingInt((ItemStack stack) -> CompanionSortGroups.of(stack).ordinal())
                .thenComparingInt(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem())));
        boolean changed = false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack now = slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY;
            changed |= !ItemStack.matches(chest.getItem(slot), now);
            chest.setItem(slot, now);
        }
        return changed;
    }

    private int freeBagCells() {
        int free = 0;
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) if (w.owner.getItem(slot).isEmpty()) free++;
        return free;
    }

    /**
     * True while the bag holds no more of this kind than he carries for the player: nothing of it is his own, so nothing of it is spent.
     * A stopped sorting leaves it the player's too, until a sorting puts it away (in the game 29.10 he ate the player's bread after «иди ко мне»).
     */
    boolean onlyCarried(ItemStack like) {
        if (like.isEmpty()) return false;
        int count = 0;
        for (ItemStack load : carried) if (ItemStack.isSameItemSameComponents(load, like)) count += load.getCount();
        return count > 0 && count >= count(w.owner, like);
    }

    private static int count(Container container, ItemStack like) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) if (ItemStack.isSameItemSameComponents(container.getItem(slot), like)) count += container.getItem(slot).getCount();
        return count;
    }

    /** Takes from his smallest stacks of the kind first: the cells they free are the soonest. */
    private static void remove(Container bag, ItemStack like, int count) {
        while (count > 0) {
            int smallest = -1;
            for (int slot = 0; slot < bag.getContainerSize(); slot++) {
                ItemStack stack = bag.getItem(slot);
                if (ItemStack.isSameItemSameComponents(stack, like) && (smallest < 0 || stack.getCount() < bag.getItem(smallest).getCount())) smallest = slot;
            }
            if (smallest < 0) return;
            int take = Math.min(count, bag.getItem(smallest).getCount());
            count -= take;
            bag.setItem(smallest, bag.getItem(smallest).getCount() == take ? ItemStack.EMPTY : bag.getItem(smallest).copyWithCount(bag.getItem(smallest).getCount() - take));
        }
    }
}

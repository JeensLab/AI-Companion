package dev.aicompanion.entity;

import dev.aicompanion.entity.CompanionSortGroups.Group;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * «Разложи сундуки» (issue 32): in trips through his own bag he moves what lies in the wrong chest of a fixed set to the chest of its
 * group. Each trip is one chest opened: what he carries for it is put in, what lies there out of place is taken into free bag slots.
 * The plan is made again from the chests as they are at every opening (the player may move things meanwhile). The set and what he
 * carries are kept here, for one work; his own things in the bag are never touched: only the carried list is put back.
 */
final class CompanionSorting {
    private static final double RADIUS = 6;
    private static final int MAX_CHESTS = 9;
    /** Openings in a row that moved nothing before the work gives up (every other chest is full, or the bag is). */
    private static final int MAX_IDLE = 3;
    private static final String[] NEAR = {"в ближнем", "во втором", "в третьем", "в четвёртом", "в пятом", "в шестом", "в седьмом", "в восьмом", "в девятом"};

    private final CompanionWork w;
    private final List<BlockPos> chests = new ArrayList<>();
    private final List<ItemStack> carried = new ArrayList<>();
    private boolean closing;
    private int idle;
    private ItemStack shown = ItemStack.EMPTY;

    CompanionSorting(CompanionWork w) { this.w = w; }

    /** The set is fixed at the request: ordinary chests (not trapped, not barrels) within six blocks of the player, nearest first, a double one once. */
    void begin(ServerLevel level) {
        chests.clear(); carried.clear(); closing = false; idle = 0; shown = ItemStack.EMPTY;
        var player = w.owner.getOwnerPlayer();
        Vec3 from = player == null ? w.owner.position() : player.position();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : w.chests.blockEntitiesNear(level)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() != Blocks.CHEST || state.getValue(ChestBlock.TYPE) == ChestType.RIGHT || pos.getCenter().distanceToSqr(from) > RADIUS * RADIUS) continue;
            found.add(pos.immutable());
        }
        found.sort(Comparator.comparingDouble(pos -> pos.getCenter().distanceToSqr(from)));
        chests.addAll(found.subList(0, Math.min(MAX_CHESTS, found.size())));
    }

    void step(ServerLevel level) {
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        if (closing) { w.closeChest(); w.resetTarget(); closing = false; return; }
        if (chests.isEmpty()) { w.finish(false, "Рядом с тобой сундуков не вижу."); return; }
        if (w.target == null) {
            BlockPos next = nextChest(level);
            if (next == null) return;
            w.target = next; w.resetApproach();
        }
        Container chest = w.chests.chestAt(level, w.target);
        if (chest == null) { chests.remove(w.target); w.resetTarget(); return; }
        if (w.owner.distanceToSqr(w.target.getCenter()) > 3.2D * 3.2D || !w.items.visibleFrom(level, w.owner.getEyePosition(), w.target)) {
            if (!w.approach(level, w.target, 2.8D)) w.finish(false, "Не смог дойти до одного из сундуков. Что нёс — у меня в сумке.");
            return;
        }
        w.owner.getNavigation().stop();
        w.owner.getLookControl().setLookAt(w.target.getX() + 0.5, w.target.getY() + 0.5, w.target.getZ() + 0.5);
        w.openedChest = w.target.immutable();
        w.animateChest(level, w.openedChest, 1);
        level.playSound(null, w.openedChest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        List<Container> live = live(level);
        int moved = visit(chest, chests.indexOf(w.target), CompanionSortGroups.plan(infos(live), carriedVolume()));
        w.transferred += moved;
        idle = moved == 0 ? idle + 1 : 0;
        w.owner.animateAction(3, w.target, shown);
        chest.setChanged(); w.owner.setChanged();
        w.owner.workStatus("Раскладываю вещи по сундукам.");
        closing = true; w.cooldown = 10;
    }

    /** Puts in what he carries for this chest, takes out what lies here out of place; returns how many pieces moved. */
    private int visit(Container chest, int index, Map<Group, List<Integer>> plan) {
        int moved = 0; shown = ItemStack.EMPTY;
        for (var it = carried.iterator(); it.hasNext(); ) {
            ItemStack load = it.next();
            if (!plan.getOrDefault(CompanionSortGroups.of(load), List.of()).contains(index)) continue;
            // The player may have taken it out of his bag meanwhile (his inventory screen): only what the bag still holds goes in.
            load.setCount(Math.min(load.getCount(), inBag(load)));
            int put = insert(chest, load);
            if (put > 0) { removeFromBag(load, put); load.shrink(put); moved += put; shown = load.copyWithCount(1); }
            if (load.isEmpty()) it.remove();
        }
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty() || plan.getOrDefault(CompanionSortGroups.of(stack), List.of()).contains(index)) continue;
            int free = freeBagSlot();
            if (free < 0) break;
            w.owner.setItem(free, stack.copy()); carried.add(stack.copy()); chest.setItem(slot, ItemStack.EMPTY);
            moved += stack.getCount(); shown = stack.copyWithCount(1);
        }
        tidy(chest);
        return moved;
    }

    /** Where to go next: with a load, the nearest chest that takes some of it; without, the nearest with something out of place. Ends the work when there is nothing left. */
    private BlockPos nextChest(ServerLevel level) {
        List<Container> live = live(level);
        Map<Group, List<Integer>> plan = CompanionSortGroups.plan(infos(live), carriedVolume());
        if (idle >= MAX_IDLE) {
            w.finish(false, "Не получилось разложить всё: часть сундуков забита." + (carried.isEmpty() ? "" : " Часть вещей у меня в сумке."));
            return null;
        }
        // With a load, the chests that take some of it first; else (or when none has room) the nearest with something out of place, to free room.
        BlockPos best = null; double bestDistance = Double.MAX_VALUE;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            if (pass == 1 && !carried.isEmpty() && freeBagSlot() < 0) break;
            for (int i = 0; i < chests.size(); i++) {
                Container chest = live.get(i);
                if (chest == null) continue;
                boolean fit = false;
                if (pass == 0) for (ItemStack load : carried) fit |= plan.getOrDefault(CompanionSortGroups.of(load), List.of()).contains(i) && room(chest, load);
                if (!(pass == 0 ? fit : misplaced(chest, i, plan))) continue;
                double distance = w.owner.distanceToSqr(chests.get(i).getCenter());
                if (distance < bestDistance) { best = chests.get(i); bestDistance = distance; }
            }
        }
        if (best != null) return best;
        if (!carried.isEmpty()) { w.finish(false, "Не получилось разложить всё: некуда донести вещи. Они у меня в сумке."); return null; }
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
        if (where.isEmpty()) return "В сундуках пусто, раскладывать нечего.";
        // The groups of one chest together: «руда и металлы, еда — в ближнем», not the same chest named for each.
        Map<String, List<String>> byChest = new LinkedHashMap<>();
        where.forEach((group, at) -> byChest.computeIfAbsent(String.join(" и ", at.stream().map(i -> NEAR[i]).toList()), chest -> new ArrayList<>()).add(group.label));
        List<String> parts = new ArrayList<>();
        byChest.forEach((chest, groups) -> parts.add(String.join(", ", groups) + " — " + chest));
        return (w.transferred == 0 ? "Всё уже лежит по группам: " : "Разложил по " + used + (used == 1 ? " сундуку: " : " сундукам: ")) + String.join("; ", parts) + ".";
    }

    /** The chests of the set as they are now, by their place in the set; null for one that is gone. */
    private List<Container> live(ServerLevel level) {
        List<Container> live = new ArrayList<>();
        for (BlockPos pos : chests) live.add(w.chests.chestAt(level, pos));
        return live;
    }

    private List<CompanionSortGroups.Chest> infos(List<Container> live) {
        List<CompanionSortGroups.Chest> infos = new ArrayList<>();
        for (int i = 0; i < live.size(); i++) {
            if (live.get(i) == null) continue;
            Map<Group, Double> cells = new EnumMap<>(Group.class);
            for (int slot = 0; slot < live.get(i).getContainerSize(); slot++) {
                ItemStack stack = live.get(i).getItem(slot);
                if (!stack.isEmpty()) cells.merge(CompanionSortGroups.of(stack), CompanionSortGroups.cells(stack), Double::sum);
            }
            infos.add(new CompanionSortGroups.Chest(i, i, live.get(i).getContainerSize(), cells));
        }
        return infos;
    }

    private Map<Group, Double> carriedVolume() {
        Map<Group, Double> volume = new EnumMap<>(Group.class);
        for (ItemStack load : carried) volume.merge(CompanionSortGroups.of(load), CompanionSortGroups.cells(load), Double::sum);
        return volume;
    }

    private static boolean misplaced(Container chest, int index, Map<Group, List<Integer>> plan) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && !plan.getOrDefault(CompanionSortGroups.of(stack), List.of()).contains(index)) return true;
        }
        return false;
    }

    private static boolean room(Container chest, ItemStack stack) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack existing = chest.getItem(slot);
            if (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < Math.min(chest.getMaxStackSize(), existing.getMaxStackSize())) return true;
        }
        return false;
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
    private static void tidy(Container chest) {
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
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY);
    }

    private int freeBagSlot() {
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) if (w.owner.getItem(slot).isEmpty()) return slot;
        return -1;
    }

    private int inBag(ItemStack like) {
        int count = 0;
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) if (ItemStack.isSameItemSameComponents(w.owner.getItem(slot), like)) count += w.owner.getItem(slot).getCount();
        return count;
    }

    private void removeFromBag(ItemStack like, int count) {
        for (int slot = 0; slot < w.owner.getContainerSize() && count > 0; slot++) {
            ItemStack stack = w.owner.getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, like)) continue;
            int take = Math.min(count, stack.getCount());
            stack.shrink(take); count -= take;
            w.owner.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
    }
}

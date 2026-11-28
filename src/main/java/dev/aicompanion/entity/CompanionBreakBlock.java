package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * break_block: the standing block of this kind nearest to the player, within {@link #RADIUS} blocks of him, walked to and broken
 * as a player breaks it (the same swings, the tool the block wants, a real drop). What a container held spills out as the game
 * spills it, and the block and all of it are picked up by his own pickup; with «отдай» everything he got from the block is then
 * tossed to the player (CompanionHandOver, bundle). What cannot be broken, gives nothing, or needs a tool he has not is refused
 * before the first swing, with the world untouched. What someone else picked up first (the player beside the block) is said so.
 */
final class CompanionBreakBlock {
    /** Blocks around the player the block is looked for in. */
    static final int RADIUS = 16;
    /** Ticks the drops are given to be picked up before another try, and then before they are counted left lying. */
    private static final int SETTLE = 40;

    private final CompanionWork w;
    private CompanionEntity c;
    private Block kind;
    private BlockPos target;
    private boolean give, broken, retried, silk;
    private long settleAt;
    /** What the broken block and its contents gave (stacks as templates), his bag's count of each right before the break, and the item entities left lying. */
    private final List<ItemStack> expected = new ArrayList<>();
    private final List<Integer> before = new ArrayList<>();
    private final Set<UUID> dropped = new HashSet<>();

    CompanionBreakBlock(CompanionWork w) { this.w = w; }

    /** break_block: the block of this id nearest to the player. Null when the work may start, else why not. */
    CompanionActionResult begin(String id, boolean toPlayer) {
        c = w.owner;
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        Block block = key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
        if (block == null || block.defaultBlockState().isAir()) return CompanionActionResult.failed("Не понял, какой блок ломать: " + id + ".");
        var player = c.getOwnerPlayer();
        if (!(c.level() instanceof ServerLevel level) || player == null) return CompanionActionResult.failed("Не вижу тебя рядом, ломать не буду.");
        String name = block.getName().getString();
        // The block under the player's feet is never the one: it would take the floor out from under him.
        BlockPos found = nearest(level, player.blockPosition(), block, player.blockPosition().below());
        if (found == null) return CompanionActionResult.failed("Рядом с тобой нет «" + name + "» (ищу в " + RADIUS + " блоках от тебя).");
        BlockState state = level.getBlockState(found);
        if (state.getDestroySpeed(level, found) < 0 || block instanceof LiquidBlock)
            return CompanionActionResult.failed("«" + name + "» не сломать: он неразрушим.");
        if (state.requiresCorrectToolForDrops() && CompanionTools.bestToolForBlock(c, state).isEmpty())
            return CompanionActionResult.failed("Чтобы сломать «" + name + "», нужен подходящий инструмент, а у меня его нет.");
        // A block that gives nothing (glass, ice, a spawner) is not destroyed for nothing: only a tool with Silk Touch brings it.
        BlockEntity entity = level.getBlockEntity(found);
        boolean held = entity instanceof Container box && !box.isEmpty();
        silk = false;
        if (!held && CompanionTools.drops(state, level, found, entity, c, CompanionTools.bestToolForBlock(c, state)).isEmpty()) {
            ItemStack silked = CompanionTools.bestSilkToolForBlock(c, state);
            if (silked.isEmpty() || CompanionTools.drops(state, level, found, entity, c, silked).isEmpty())
                return CompanionActionResult.failed("«" + name + "» при поломке ничего не даёт, ломать его без толку.");
            silk = true;
        }
        kind = block; target = found; give = toPlayer; broken = retried = false; settleAt = 0;
        expected.clear(); before.clear(); dropped.clear();
        w.breaking.reset();
        return null;
    }

    String jobName() { return "слом блока «" + kind.getName().getString() + "»" + (give ? " и передачу тебе" : ""); }

    /** One tick of the order: the walk to the block, the swings, then the pickup of all it gave. */
    void tick(ServerLevel level) {
        if (broken) { collect(level); return; }
        String name = kind.getName().getString();
        BlockState state = level.getBlockState(target);
        if (!state.is(kind)) { end(CompanionActionResult.blocked("«" + name + "» на " + target.toShortString() + " уже не стоит, ломать нечего.")); return; }
        int reach = w.items.reachBlock(level, target, true);
        if (reach < 0) { end(CompanionActionResult.blocked("Не могу подойти к «" + name + "» на " + target.toShortString() + ".")); return; }
        if (reach == 0) { c.workStatus("Иду к «" + name + "»."); return; }
        c.getNavigation().stop();
        if (silk) c.takeSilkToolForBlock(state); else c.takeToolForBlock(state);
        ItemStack tool = c.getMainHandItem();
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
            end(CompanionActionResult.blocked("Чтобы сломать «" + name + "», нужен подходящий инструмент, а в руке у меня его нет."));
            return;
        }
        c.workStatus("Ломаю «" + name + "».");
        if (!w.breaking.tick(level, target)) return;
        BlockEntity entity = level.getBlockEntity(target);
        List<ItemStack> drops = CompanionTools.drops(state, level, target, entity, c, tool);
        // What the block gave and what it held must both fit: nothing is broken that would leave things lying.
        List<ItemStack> all = new ArrayList<>(drops);
        if (entity instanceof Container box) for (int slot = 0; slot < box.getContainerSize(); slot++) if (!box.getItem(slot).isEmpty()) all.add(box.getItem(slot).copy());
        if (all.isEmpty()) { end(CompanionActionResult.blocked("«" + name + "» при поломке ничего не даёт, ломать его без толку.")); return; }
        if (!w.items.canStore(all)) { end(CompanionActionResult.blocked(CompanionPicking.FULL_BAG + " «" + name + "» не ломаю: не поместится всё, что в нём.")); return; }
        // The bag as it is right before the break is what the pickup is measured against.
        expected.clear(); before.clear(); dropped.clear();
        for (ItemStack stack : all) if (!stack.isEmpty()) add(expected, stack);
        for (ItemStack template : expected) before.add(countLike(template));
        AABB around = new AABB(target).inflate(3);
        Set<UUID> lying = new HashSet<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around)) lying.add(item.getUUID());
        if (!level.destroyBlock(target, false, c, 512)) { end(CompanionActionResult.blocked("«" + name + "» не поддался.")); return; }
        CompanionTools.wearOnBreak(tool, level, state, target, c);
        // What the game spilled out of the block (a container's things) is his to pick up, as the drop of the block is.
        c.claimDrops(level.getEntitiesOfClass(ItemEntity.class, around, item -> !lying.contains(item.getUUID())));
        c.dropHarvest(target, drops);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around, item -> !lying.contains(item.getUUID()))) dropped.add(item.getUUID());
        w.breaking.reset();
        c.putToolAway();
        broken = true; settleAt = 0; retried = false;
    }

    /** The drops are picked up (the work does not tick while his pickup still has some to go): what is in the bag now is the answer. */
    private void collect(ServerLevel level) {
        List<ItemStack> got = new ArrayList<>();
        int want = 0, have = 0;
        for (int i = 0; i < expected.size(); i++) {
            ItemStack template = expected.get(i);
            int took = Math.max(0, Math.min(template.getCount(), countLike(template) - before.get(i)));
            want += template.getCount(); have += took;
            if (took > 0) got.add(template.copyWithCount(took));
        }
        String name = kind.getName().getString();
        String note = "";
        if (have < want) {
            // Some of it still lies there: given time and one more try. None of it left: someone else took it (the player beside the block).
            boolean lying = dropped.stream().anyMatch(id -> level.getEntity(id) instanceof ItemEntity item && item.isAlive());
            if (lying) {
                long now = level.getGameTime();
                if (settleAt == 0) { settleAt = now + SETTLE; return; }
                if (now < settleAt) return;
                if (!retried) { retried = true; settleAt = 0; c.retryHarvestPickup(); return; }
                end(CompanionActionResult.blocked("Сломал «" + name + "», но подобрал только " + have + " из " + want + " вещей; остальное лежит у "
                        + target.toShortString() + (give ? ". Отдавать не стал." : ".")));
                return;
            }
            if (have == 0) { end(CompanionActionResult.blocked("Сломал «" + name + "», но всё, что выпало, забрал кто-то другой: мне ничего не досталось.")); return; }
            note = " Ещё " + (want - have) + " из " + want + " вещей забрал кто-то другой, не я.";
        }
        if (give) {
            // The pickup is done: the same hand-over that gives an item gives these, each stack as many as he got from this block.
            w.handOver.bundle = got; w.handOver.note = note;
            w.requestedItem = CompanionHandOver.BUNDLE; w.requestedCount = have;
            w.job = CompanionWork.Job.GIVE; w.phase = CompanionWork.Phase.HAND;
            w.orderProgressPosition = null;
            return;
        }
        end(CompanionActionResult.ok("Сломал «" + name + "» и подобрал: " + CompanionHandOver.list(got) + "." + note)
                .withEvidence(new CompanionActionResult.Evidence(w.taskId, "break_block", BuiltInRegistries.BLOCK.getKey(kind).toString(), have, null, null, null)));
    }

    private void end(CompanionActionResult result) {
        w.breaking.reset();
        c.putToolAway();
        w.finish(result);
    }

    /** What his bag and hands hold of this very stack (same item and components). */
    private int countLike(ItemStack template) {
        int total = 0;
        for (ItemStack stack : w.items.carriedItems()) if (ItemStack.isSameItemSameComponents(stack, template)) total += stack.getCount();
        return total;
    }

    static void add(List<ItemStack> into, ItemStack stack) {
        for (ItemStack have : into) if (ItemStack.isSameItemSameComponents(have, stack)) { have.grow(stack.getCount()); return; }
        into.add(stack.copy());
    }

    /**
     * The nearest block of this kind to {@code center} within {@link #RADIUS}, or null; {@code except} is never it.
     * ponytail: a plain scan of the 33-block cube, once per order; a palette skip by section (CompanionObsidian.nearest) when it shows in a profile.
     */
    static BlockPos nearest(ServerLevel level, BlockPos center, Block kind, BlockPos except) {
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int dy = -RADIUS; dy <= RADIUS; dy++) {
            int y = center.getY() + dy;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
            for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int distance = dx * dx + dy * dy + dz * dz;
                if (distance > RADIUS * RADIUS || distance >= bestDistance) continue;
                cell.set(center.getX() + dx, y, center.getZ() + dz);
                if (cell.equals(except) || !level.hasChunkAt(cell) || !level.getBlockState(cell).is(kind)) continue;
                best = cell.immutable(); bestDistance = distance;
            }
        }
        return best;
    }
}

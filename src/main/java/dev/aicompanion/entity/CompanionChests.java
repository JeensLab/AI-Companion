package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CompanionChests {
    final CompanionWork w;

    CompanionChests(CompanionWork w) { this.w = w; }

    void store(ServerLevel level) {
        if (w.job == CompanionWork.Job.TAKE) { retrieve(level); return; }
        if (w.job == CompanionWork.Job.PLACE_CHEST) { placeRequestedChests(); return; }
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        if (w.job == CompanionWork.Job.STORE && (eligibleCount() == 0 || storeQuota() <= 0)) { w.finish(true, w.transferred > 0 ? "Сложил " + w.transferred + " предметов в сундук." : "Лишних вещей для сундука сейчас нет."); return; }
        if (w.target == null) {
            w.target = findChest(level);
            w.resetApproach();
            if (w.target == null) {
                if (w.newChests >= 1) { w.finish(false, "Сложил " + w.transferred + " предметов, но оставшиеся вещи не помещаются. Они остались в сумке."); return; }
                if (!hasChestItem()) {
                    if (w.crafting.approachCraftingTable()) return;
                    if (!w.items.moveHandMaterialsToBag()) return;
                    CompanionActionResult crafted = w.owner.craftFromBag("minecraft:chest", 1);
                    if (!crafted.success()) {
                        if (crafted.message().contains("места") || crafted.message().contains("безопасное место")) w.finish(false, crafted.message());
                        else w.crafting.requestWood(CompanionWork.Phase.STORE);
                        return;
                    }
                    return; // Let the crafting/table gesture finish before placing the chest.
                }
                CompanionActionResult placed = w.owner.placeBlockNearResult("minecraft:chest", w.insideHouse ? w.placementAnchor : w.owner.blockPosition(),
                        pos -> !w.insideHouse || w.house.inside(pos));
                if (!placed.success()) { w.finish(false, placed.message()); return; }
                w.newChests++;
                w.target = findChest(level);
                w.resetApproach();
                if (w.target == null) { w.finish(false, "Сундук поставил, но не могу его открыть. Вещи остались в сумке."); return; }
                return; // Open on the next work stage, after the placement gesture.
            }
        }
        Container chest = chestAt(level, w.target);
        if (chest == null) { w.triedChests.add(w.target); w.resetTarget(); return; }
        if (w.owner.distanceToSqr(w.target.getCenter()) > 3.2D * 3.2D || !CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), w.target)) {
            if (!w.approach(level, w.target, 2.8D)) { w.triedChests.add(w.target); w.resetTarget(); }
            return;
        }
        w.owner.getNavigation().stop();
        w.owner.getLookControl().setLookAt(w.target.getX() + 0.5, w.target.getY() + 0.5, w.target.getZ() + 0.5);
        w.openedChest = w.target.immutable();
        w.animateChest(level, w.openedChest, 1);
        level.playSound(null, w.openedChest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        w.owner.animateAction(3, w.target, ItemStack.EMPTY);
        w.transferred += transfer(chest);
        chest.setChanged();
        w.rememberedChest = w.target.immutable();
        w.owner.rememberObserved(w.target);
        w.owner.storageStatus(w.storageForAi());
        w.triedChests.add(w.target);
        w.phase = CompanionWork.Phase.CLOSE;
        w.cooldown = 10;
        w.owner.workStatus("Складываю вещи в сундук.");
    }

    /** An explicit placement order always places new blocks, even if storage already exists. */
    private void placeRequestedChests() {
        if (!w.owner.onGround()) return;
        if (w.newChests >= w.requestedCount) { finishPlacedChests(); return; }
        boolean hasItem = w.items.carriedItems().stream().anyMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(w.requestedItem) && !stack.isEmpty());
        if (!hasItem) {
            if (!CompanionWork.supportsCraft(w.requestedItem, 1)) { w.finish(false, "Нет блока " + w.requestedItem + " в сумке, его подготовку пока не умею."); return; }
            if (w.crafting.needsWorkbench(w.requestedItem) && w.crafting.approachCraftingTable()) return;
            if (!w.items.moveHandMaterialsToBag()) return;
            CompanionActionResult crafted = w.owner.craftFromBag(w.requestedItem, 1);
            if (!crafted.success()) {
                if (crafted.message().contains("места") || crafted.message().contains("безопасное место"))
                    w.finish(false, "Поставил " + w.newChests + " из " + w.requestedCount + ". " + crafted.message());
                else if (CompanionWork.stoneCost(w.requestedItem) > w.items.countStoneMaterials()) {
                    w.wantedStone = CompanionWork.stoneCost(w.requestedItem) - w.items.countStoneMaterials();
                    w.stoneCollectedBefore = w.owner.collectedStone(); w.gathered = 0; w.phase = CompanionWork.Phase.PICKAXE;
                    w.stoneSearch = null; w.stoneCandidates = null; w.rejected.clear(); w.dryAnchor = null; w.resetTarget();
                } else w.crafting.requestWood(CompanionWork.Phase.STORE);
            }
            return;
        }
        if (w.owner.distanceToSqr(w.placementAnchor.getCenter()) > 3 * 3 || w.insideHouse && !w.house.inside(w.owner.blockPosition())) {
            if (!w.placementMoving) { w.resetApproach(); w.placementMoving = true; }
            w.owner.movementIntent(w.placementAnchor);
            double distance = w.owner.distanceToSqr(w.placementAnchor.getCenter());
            if (distance + .05 < w.bestDistance) { w.bestDistance = distance; w.lastProgressTick = w.owner.level().getGameTime(); }
            if (w.owner.level().getGameTime() - w.lastProgressTick > 160) {
                w.finish(false, "Блок есть в сумке, но не смог вернуться к месту установки на " + w.placementAnchor.toShortString() + "."); return;
            }
            Path route = w.owner.getNavigation().createPath(w.placementAnchor, w.insideHouse ? 0 : 1);
            if (route == null || !route.canReach()) { w.owner.getNavigation().stop(); return; }
            CompanionMiningNavigation.walk(w.owner, route); return;
        }
        if (w.insideHouse && !w.house.interiorAvailable((ServerLevel)w.owner.level())) { w.finish(false, "Дом изменился, место внутри больше не подтверждено."); return; }
        CompanionActionResult placed = w.owner.placeBlockNearResult(w.requestedItem, w.placementAnchor, pos -> !w.insideHouse || w.house.inside(pos));
        if (!placed.success()) { w.finish(false, "Поставил " + w.newChests + " из " + w.requestedCount + ". " + placed.message()); return; }
        if (w.owner.lastPlacedPosition() != null) w.placedChests.add(w.owner.lastPlacedPosition());
        w.newChests++;
        if (w.newChests >= w.requestedCount) finishPlacedChests();
        else w.owner.workStatus("Поставил " + w.newChests + " из " + w.requestedCount + " блоков " + w.requestedItem + ".");
    }

    private void finishPlacedChests() {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(w.requestedItem));
        long present = w.placedChests.stream().filter(pos -> w.owner.level().hasChunkAt(pos) && w.owner.level().getBlockState(pos).is(block)
                && (!w.insideHouse || w.house.inside(pos))).count();
        if (present != w.requestedCount) {
            w.finish(false, "На месте установки сейчас " + present + " из " + w.requestedCount + " блоков " + w.requestedItem + ". Часть блоков исчезла или недоступна для проверки."); return;
        }
        w.finish(true, "Поставил " + w.requestedItem + ": " + w.newChests + (w.insideHouse ? " внутри дома" : "") + ". Координаты: " + w.placedChests.stream().map(BlockPos::toShortString).toList() + ".");
    }

    void afterClose() {
        w.closeChest();
        w.resetTarget();
        if (w.job == CompanionWork.Job.TAKE) {
            if (w.transferred >= w.requestedCount) w.finish(true, "Забрал " + w.transferred + " предметов из сундука. В сумке: " + w.owner.getStoredItemsSummary() + ".");
            else w.phase = CompanionWork.Phase.STORE;
        }
        else if (eligibleCount() == 0) w.finish(true, "Сложил " + w.transferred + " предметов в сундук.");
        else w.phase = CompanionWork.Phase.STORE;
    }

    private void retrieve(ServerLevel level) {
        if (!w.owner.onGround()) return;
        if (w.target == null) { w.target = findChest(level); w.resetApproach(); }
        if (w.target == null) {
            w.finish(w.transferred > 0 && (w.requestedItem.equals("all") || w.transferred >= w.requestedCount),
                    "Забрал " + w.transferred + (w.requestedItem.equals("all") ? " предметов" : " из " + w.requestedCount) + ". В доступных сундуках больше нужных вещей нет. В сумке: " + w.owner.getStoredItemsSummary() + "."); return;
        }
        Container chest = chestAt(level, w.target);
        if (chest == null) { w.triedChests.add(w.target); w.resetTarget(); return; }
        w.owner.movementIntent(w.target);
        if (w.owner.distanceToSqr(w.target.getCenter()) > 3.2 * 3.2 || !CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), w.target)) {
            if (!w.approach(level, w.target, 2.8)) { w.triedChests.add(w.target); w.resetTarget(); }
            return;
        }
        w.owner.getNavigation().stop();
        w.openedChest = w.target.immutable(); w.animateChest(level, w.target, 1);
        level.playSound(null, w.target, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, .5F, 1);
        w.owner.observeEvent("chest", "Открыл сундук, чтобы взять нужные вещи.");
        ItemStack shown = ItemStack.EMPTY; int movedHere = 0;
        for (int slot = 0; slot < chest.getContainerSize() && w.transferred < w.requestedCount; slot++) {
            ItemStack stack = chest.getItem(slot); if (!eligible(stack)) continue;
            ItemStack offered = stack.copyWithCount(Math.min(stack.getCount(), w.requestedCount - w.transferred));
            int offeredCount = offered.getCount(); w.owner.storeLoot(offered);
            int moved = offeredCount - offered.getCount();
            if (moved > 0) { shown = stack.copyWithCount(1); stack.shrink(moved); chest.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack); w.transferred += moved; movedHere += moved; }
        }
        w.owner.animateAction(3, w.target, shown); chest.setChanged(); w.owner.setChanged();
        w.rememberedChest = w.target.immutable(); w.owner.rememberObserved(w.target); w.owner.storageStatus(w.storageForAi()); w.triedChests.add(w.target);
        if (movedHere == 0) { w.closeChest(); w.finish(false, "В сумке нет места для вещей из сундука. Предметы оставил на месте."); return; }
        w.phase = CompanionWork.Phase.CLOSE; w.cooldown = 10; w.owner.workStatus("Забираю вещи из сундука.");
    }

    private BlockPos findChest(ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos center = w.owner.blockPosition();
        for (BlockPos pos : blockEntitiesNear(level)) {
            if (w.triedChests.contains(pos) || w.insideHouse && !w.house.inside(pos)) continue;
            // A deed of his own: only a chest he set down himself, near the player (the player: «не трогает мои сундуки»).
            if (w.owner.ownPlacesOnly() && (!w.owner.placedByMe(pos) || !w.owner.leashAllows(pos))) continue;
            Container chest = chestAt(level, pos);
            if (chest != null && (w.job == CompanionWork.Job.TAKE ? containsRequested(chest) : w.job == CompanionWork.Job.PLACE_CHEST || hasCapacity(chest))) found.add(pos.immutable());
        }
        // The chest Oleg already uses is "ours": fill it before any other one nearby.
        found.sort(Comparator.comparing((BlockPos pos) -> !pos.equals(w.rememberedChest)).thenComparingDouble(pos -> pos.distSqr(center)));
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Block entities of loaded chunks within the container search radius (at most 32 blocks). */
    List<BlockPos> blockEntitiesNear(ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        int radius = Math.min(32, w.owner.getSearchRadius());
        BlockPos center = w.owner.blockPosition();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntities().keySet()) if (pos.distSqr(center) <= radius * radius) found.add(pos);
            }
        }
        return found;
    }

    private Container chestAt(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock chest ? ChestBlock.getContainer(chest, state, level, pos, false) : null;
    }

    private int transfer(Container destination) {
        int moved = 0;
        int[] reserves = reserves(w.requestedItem);
        for (int source = 0; source < w.owner.getContainerSize(); source++) {
            ItemStack stack = w.owner.getItem(source);
            if (!eligible(stack)) continue;
            int keep = kept(stack, reserves, CompanionWork.SURPLUS.equals(w.requestedItem));
            int available = Math.min(stack.getCount() - keep, storeQuota() - moved);
            for (int pass = 0; pass < 2 && available > 0; pass++) {
                for (int slot = 0; slot < destination.getContainerSize() && available > 0; slot++) {
                    if (!destination.canPlaceItem(slot, stack)) continue;
                    ItemStack existing = destination.getItem(slot);
                    if (pass == 0 && existing.isEmpty() || pass == 1 && !existing.isEmpty()) continue;
                    if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) continue;
                    int maximum = Math.min(destination.getMaxStackSize(), stack.getMaxStackSize());
                    int amount = Math.min(available, maximum - existing.getCount());
                    if (amount <= 0) continue;
                    ItemStack inserted = existing.isEmpty() ? stack.copyWithCount(amount) : existing.copyWithCount(existing.getCount() + amount);
                    destination.setItem(slot, inserted);
                    stack.shrink(amount); available -= amount; moved += amount;
                }
            }
            w.owner.setItem(source, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        w.owner.setChanged(); destination.setChanged();
        return moved;
    }

    private boolean hasCapacity(Container chest) {
        for (int source = 0; source < w.owner.getContainerSize(); source++) {
            ItemStack stack = w.owner.getItem(source);
            if (!eligible(stack)) continue;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack existing = chest.getItem(slot);
                if (chest.canPlaceItem(slot, stack) && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, stack)
                        && existing.getCount() < Math.min(stack.getMaxStackSize(), chest.getMaxStackSize()))) return true;
            }
        }
        return false;
    }

    private int eligibleCount() {
        int count = 0;
        int[] reserves = reserves(w.requestedItem);
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) {
            ItemStack stack = w.owner.getItem(slot);
            if (eligible(stack)) count += stack.getCount() - kept(stack, reserves, CompanionWork.SURPLUS.equals(w.requestedItem));
        }
        return count;
    }
    /** What stays in the bag on a store order: soil for supports (8) and, for SURPLUS, his stock of logs and stone. */
    private static int[] reserves(String requested) {
        boolean surplus = CompanionWork.SURPLUS.equals(requested);
        return new int[]{requested.isBlank() || requested.equals("resources") || surplus ? 8 : 0,
                surplus ? CompanionAutonomy.WOOD_STOCK : 0, surplus ? CompanionAutonomy.STONE_STOCK : 0,
                surplus ? CompanionAutonomy.COAL_STOCK : 0, surplus ? 16 : 0, surplus ? 8 : 0, surplus ? 8 : 0};
    }
    /** How much of this stack stays, counting the reserves down. */
    private static int kept(ItemStack stack, int[] reserves, boolean surplus) {
        int kind = CompanionTools.soilItem(stack) ? 0 : !surplus ? -1 : stack.is(ItemTags.LOGS) ? 1 : stack.is(ItemTags.STONE_TOOL_MATERIALS) ? 2
                : stack.is(ItemTags.COALS) ? 3 : stack.is(Items.TORCH) ? 4 : stack.is(Items.IRON_INGOT) ? 5 : stack.is(Items.RAW_IRON) ? 6 : -1;
        if (kind < 0) return 0;
        int keep = Math.min(stack.getCount(), reserves[kind]);
        reserves[kind] -= keep;
        return keep;
    }
    /** Not a tool, armour, food, bench or chest: a resource a store order of «resources» puts away. */
    private static boolean resource(ItemStack stack) {
        return stack.getMaxDamage() == 0 && !(stack.getItem() instanceof ArmorItem) && !stack.has(DataComponents.FOOD)
                && !stack.is(Items.CRAFTING_TABLE) && !stack.is(Items.CHEST);
    }
    /** What a SURPLUS store order would put away from his bag now (CompanionAutonomy: is the order worth giving). */
    static int surplusCount(CompanionEntity owner) {
        int count = 0;
        int[] reserves = reserves(CompanionWork.SURPLUS);
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack stack = owner.getItem(slot);
            if (!stack.isEmpty() && resource(stack)) count += stack.getCount() - kept(stack, reserves, true);
        }
        return count;
    }
    private boolean containsRequested(Container chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (eligible(chest.getItem(slot))) return true;
        return false;
    }

    /**
     * "Put the stone sword in the chest" means one sword, not every sword he carries: for a named tool, weapon or
     * armour piece only the requested count goes in (game log 22.10 put away his own sword too). Resources move whole.
     */
    private int storeQuota() {
        if (w.job != CompanionWork.Job.STORE || w.requestedItem.isBlank() || List.of("all", "logs", "stone", "resources", CompanionWork.SURPLUS).contains(w.requestedItem)) return Integer.MAX_VALUE;
        var id = ResourceLocation.tryParse(w.requestedItem);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || new ItemStack(BuiltInRegistries.ITEM.get(id)).getMaxStackSize() != 1) return Integer.MAX_VALUE;
        return Math.max(1, w.requestedCount) - w.transferred;
    }

    private boolean eligible(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (w.requestedItem.equals("all")) return true;
        if (w.requestedItem.equals("logs")) return stack.is(ItemTags.LOGS);
        if (w.requestedItem.equals("stone")) return stack.is(ItemTags.STONE_TOOL_MATERIALS) || stack.is(Items.STONE);
        if (!w.requestedItem.isBlank() && !w.requestedItem.equals("resources") && !w.requestedItem.equals(CompanionWork.SURPLUS)) return w.requestedItem.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        return resource(stack);
    }

    private boolean hasChestItem() { return w.items.carriedItems().stream().anyMatch(stack -> stack.is(Items.CHEST)); }
}

package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;

final class CompanionWorkCrafting {
    final CompanionWork w;

    CompanionWorkCrafting(CompanionWork w) { this.w = w; }

    void prepareShovel() {
        if (CompanionTools.equip(w.owner, ItemTags.SHOVELS, Blocks.DIRT.defaultBlockState())) {
            w.phase = CompanionWork.Phase.DIGGING; w.owner.startGatheringDirtForWork(w.requestedCount); return;
        }
        if (approachCraftingTable()) return;
        if (!w.items.moveHandMaterialsToBag()) return;
        if (CompanionTools.craft(w.owner, "shovel", Blocks.DIRT.defaultBlockState(), ItemTags.SHOVELS)) {
            w.phase = CompanionWork.Phase.DIGGING; w.owner.startGatheringDirtForWork(w.requestedCount);
        } else requestWood(CompanionWork.Phase.SHOVEL, 2);
    }

    void craftRequested() {
        if (needsWorkbench(w.requestedItem) && approachCraftingTable()) return;
        if (!w.items.moveHandMaterialsToBag()) return;
        int remainder = w.owner.goals().craftRemainder(w.taskId);
        if (remainder == 0) { w.finish(true, "Нужные пригодные кирки уже есть."); return; }
        if (remainder > 0) w.requestedCount = remainder;
        if (++w.craftAttempts > 6) { w.finish(false, "Для этой вещи пока не хватает доступных материалов."); return; }
        CompanionActionResult result = w.owner.craftFromBag(w.requestedItem, w.requestedCount);
        if (result.success()) {
            String report = result.message().split(" В сумке:")[0];
            // The pickaxe was only preparation for ore: take it and go on to the ore.
            if (w.job == CompanionWork.Job.ORE || w.ironPrep) { w.owner.equipForWork(w.requestedItem); w.phase = CompanionWork.Phase.ORE_PICK; w.resetTarget(); return; }
            if (w.requestedItem.matches(".*_(pickaxe|axe|shovel|hoe|sword)")) report += " " + w.owner.equipForWork(w.requestedItem).message();
            w.finish(CompanionActionResult.ok(report + " В сумке: " + w.owner.getStoredItemsSummary() + ".")
                    .withEvidence(new CompanionActionResult.Evidence(w.taskId, "craft_item", w.requestedItem, w.requestedCount, w.owner.getUUID(), null, null)));
            return;
        }
        if (w.craftOnly || result.message().contains("места") || result.message().contains("безопасное место")) { w.finish(false, result.message()); return; }
        int required = CompanionWork.stoneCost(w.requestedItem) * w.requestedCount;
        if (required > 0 && w.items.countStoneMaterials() < required) {
            w.wantedStone = Math.min(64, required - w.items.countStoneMaterials());
            if (required - w.items.countStoneMaterials() > 64) { w.finish(false, "Слишком большой заказ, давай сделаем меньше предметов за раз."); return; }
            w.gathered = 0; w.afterStone = CompanionWork.Phase.CRAFT;
            w.phase = CompanionWork.Phase.PICKAXE;
            return;
        }
        requestWood(CompanionWork.Phase.CRAFT);
    }

    void preparePickaxe() {
        if ((w.job == CompanionWork.Job.STONE || w.job == CompanionWork.Job.ORE) && w.items.keepingOrderStone(() -> w.pickaxeSense.tick(w.items.reservedStone()))) return;
        ItemStack best = ItemStack.EMPTY;
        double score = -1;
        for (ItemStack stack : w.items.carriedItems()) {
            if (!w.items.usablePickaxe(stack)) continue;
            double speed = stack.getDestroySpeed(Blocks.STONE.defaultBlockState());
            if (speed > score) { score = speed; best = stack; }
        }
        if (best.isEmpty()) {
            if (approachCraftingTable()) return;
            if (!w.items.moveHandMaterialsToBag()) return;
            // A worn-out pickaxe with mined cobblestone in the bag is replaced by the better stone one.
            String replacement = w.items.countStoneMaterials() >= 3 && w.items.keepingOrderStone(() -> w.owner.craftFromBag("minecraft:stone_pickaxe", 1).success())
                    ? "minecraft:stone_pickaxe" : "minecraft:wooden_pickaxe";
            CompanionActionResult crafted = replacement.equals("minecraft:stone_pickaxe")
                    ? CompanionActionResult.ok("") : w.owner.craftFromBag(replacement, 1);
            if (!crafted.success()) {
                if (crafted.message().contains("места") || crafted.message().contains("безопасное место")) w.finish(false, crafted.message());
                else requestWood(CompanionWork.Phase.PICKAXE);
                return;
            }
            w.owner.equipForWork(replacement);
        } else {
            String id = BuiltInRegistries.ITEM.getKey(best.getItem()).toString();
            if (w.owner.getOffhandItem() == best) w.owner.unequipForSupport("offhand");
            CompanionActionResult equipped = w.owner.equipForWork(id);
            if (!equipped.success()) { w.finish(false, equipped.message()); return; }
        }
        if (!w.items.usablePickaxe(w.owner.getMainHandItem())) {
            w.finish(false, "Не получилось взять подходящую кирку."); return;
        }
        w.owner.workStatus("Кирка готова, ищу доступный камень.");
        if (w.stoneSearch == null && w.owner.level() instanceof ServerLevel level) {
            w.stoneSearch = new CompanionStoneSearch(level, w.owner.blockPosition(), w.owner.getSearchRadius());
            // Game log 22.10 (efc17045): the place of the order, 54 blocks back up the hill before the wood trip, stood for
            // «where mining started»; no route back there ended the work without a stone. The way back is from where mining starts.
            w.workOrigin = w.stoneMining.miningOrigin(); w.returnRouteBlocked = false; w.nextReturnCheck = w.returnBlockedSince = 0; w.returnMisses = 0;
        }
        w.phase = CompanionWork.Phase.MINE;
        w.resetTarget();
    }

    void requestWood(CompanionWork.Phase resume) {
        requestWood(resume, 4);
    }

    void requestWood(CompanionWork.Phase resume, int count) {
        if (++w.woodAttempts > (w.job == CompanionWork.Job.HOUSE ? 4 : 2)) {
            w.finish(false, "Не хватает доступного дерева для подготовки. Вещи сохранил." + (w.job == CompanionWork.Job.HOUSE ? " Начатый дом можно достроить позже." : "")); return;
        }
        w.afterWood = resume;
        w.phase = CompanionWork.Phase.WOOD;
        w.woodBeforeRequest = w.owner.collectedLogs();
        w.owner.startGatheringLogsForWork(count);
    }

    /** Reuse a reachable workbench before spending more wood on another one. */
    boolean needsWorkbench(String item) {
        if (!(w.owner.level() instanceof ServerLevel level)) return true;
        var id = ResourceLocation.tryParse(item);
        if (id == null) return true;
        var targetItem = BuiltInRegistries.ITEM.get(id);
        return level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream()
                .filter(holder -> holder.value().getResultItem(level.registryAccess()).is(targetItem))
                .noneMatch(holder -> holder.value().canCraftInDimensions(2, 2));
    }

    boolean approachCraftingTable() {
        if (!w.owner.onGround()) return true;
        if (!(w.owner.level() instanceof ServerLevel level) || w.owner.hasCraftingTableNearby() || w.benchHere) return false;
        if (w.craftingStation == null || !level.hasChunkAt(w.craftingStation) || !level.getBlockState(w.craftingStation).is(Blocks.CRAFTING_TABLE)) {
            w.craftingStation = null;
            for (BlockPos pos : w.owner.knownCraftingTables()) {
                // Farther than a short trip, the travelling bench is set down here instead of a long walk back.
                if (pos.distSqr(w.owner.blockPosition()) > CompanionWork.BENCH_TRIP * CompanionWork.BENCH_TRIP) continue;
                Path route = CompanionMiningNavigation.pathTo(w.owner, pos, 2.8, false);
                if (route != null && route.canReach()) { w.craftingStation = pos; break; }
            }
            w.resetApproach();
        }
        if (w.craftingStation == null) {
            if (w.owner.hasKnownCraftingTableWithin(CompanionWork.BENCH_TRIP)) {
                if (canMakeBench()) return useOwnBench("к известному верстаку сейчас нет пути");
                w.finish(false, "Помню верстак неподалёку, но пройти к нему сейчас не могу, а дерева на новый у меня нет."); return true;
            }
            return false;
        }
        w.owner.movementIntent(w.craftingStation);
        if (!w.approach(level, w.craftingStation, 2.8D)) {
            // Down in a cut below the bench with no walk up: first out, the way a player cuts steps up the wall, then the bench
            // (marathon 23.10, «долина»: back down the quarry for more stone, the order ended «не смог добраться до верстака»
            // seven blocks from it, before the self-rescue had a chance to begin).
            if (w.owner.isSelfRescuing()) { w.owner.workStatus("Выбираюсь наверх, к верстаку."); return true; }
            if (w.craftingStation.getY() > w.owner.getY() + 1.5 && w.benchClimbs++ < 2) {
                w.resetApproach(); w.owner.workStatus("Выбираюсь наверх, к верстаку."); return true;
            }
            if (canMakeBench()) return useOwnBench("до верстака на " + w.craftingStation.toShortString() + " не дошёл");
            w.finish(false, "Не смог добраться до верстака на " + w.craftingStation.toShortString() + ". Вещи сохранил."); return true;
        }
        w.owner.workStatus("Возвращаюсь к верстаку на " + w.craftingStation.toShortString() + ".");
        return true;
    }

    /** Like a player far down a slope from his bench: one log buys a new bench here instead of giving up the order. */
    boolean canMakeBench() {
        // A bench a few steps away is never replaced: it is walked to, or the order honestly stops.
        if (w.owner.hasKnownCraftingTableWithin(CompanionWork.NEAR_BENCH)) return false;
        return CompanionTools.count(w.owner, ItemTags.LOGS) * 4 + CompanionTools.count(w.owner, ItemTags.PLANKS) >= 4;
    }

    boolean useOwnBench(String why) {
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "bench_replaced", "entity", w.owner.getUUID(),
                "remembered", w.craftingStation, "reason", why);
        w.benchHere = true; w.craftingStation = null; w.resetApproach();
        w.owner.allowNewBench(true);
        w.owner.workStatus("Ладно, " + why + " — поставлю новый верстак здесь.");
        return false;
    }
}

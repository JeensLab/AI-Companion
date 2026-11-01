package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

import java.util.Comparator;
import java.util.List;

final class CompanionFurnaces {
    private static final int FAR_LOT = 24;
    /** Time on top of the cooking itself for walking to the furnace, loading it and taking the product out. */
    static final int SERVICE_ALLOWANCE_TICKS = 20 * 120;
    final CompanionWork w;

    CompanionFurnaces(CompanionWork w) { this.w = w; }

    String furnaceNote() {
        // Smelting as its own order or as the iron step of a tool: either way the furnaces are ours.
        return CompanionFurnaceReader.note(w.lots);
    }
    private int loadedTotal() { return w.lots.stream().filter(lot -> lot.loaded).mapToInt(lot -> lot.count).sum(); }
    int smeltReady() { return w.lots.stream().mapToInt(lot -> lot.ready).sum(); }

    /** A big batch goes into several furnaces, about eight items each, as a player with spare furnaces would do. */
    private int wantedFurnaces() {
        int remaining = w.smeltCount - loadedTotal();
        if (remaining <= 0) return w.lots.size();
        int loaded = (int) w.lots.stream().filter(lot -> lot.loaded).count();
        return Math.min(CompanionWork.MAX_FURNACES, loaded + Math.max(1, (remaining + CompanionWork.ITEMS_PER_FURNACE - 1) / CompanionWork.ITEMS_PER_FURNACE));
    }

    /**
     * Free furnaces nearby are used like a player would; furnaces from the bag are set down beside them for a big batch.
     * With no furnace at all, one is made from real stone and set down.
     */
    void prepareFurnace(ServerLevel level) {
        if (w.trip.walk()) return;
        if (!w.owner.onGround()) return;
        int wanted = wantedFurnaces();
        while (w.lots.size() < wanted) {
            BlockPos free = findFurnace(level);
            if (free == null) break;
            w.lots.add(new CompanionWork.Lot(free, false));
        }
        if (w.lots.isEmpty() && w.trip.toStation("minecraft:furnace")) return;
        boolean carriesFurnace = w.items.carriedItems().stream().anyMatch(stack -> stack.is(Items.FURNACE));
        if (w.lots.size() < wanted && carriesFurnace && w.furnacesPlaced < CompanionWork.MAX_FURNACES + 2) {
            BlockPos group = w.lots.isEmpty() ? null : w.lots.getFirst().pos;
            if (group != null && w.owner.distanceToSqr(group.getCenter()) > 4.5 * 4.5) {
                // Set the new furnaces down next to the free one, not where he happens to stand.
                if (!group.equals(w.heading)) { w.heading = group; w.resetApproach(); }
                w.owner.movementIntent(group);
                if (w.approach(level, group, 2.8D)) { w.owner.workStatus("Иду к печи на " + group.toShortString() + "."); return; }
            } else {
                if (w.furnaceRoom != null && w.owner.distanceToSqr(w.furnaceRoom.getCenter()) > 2.5 * 2.5) {
                    w.owner.movementIntent(w.furnaceRoom);
                    if (w.approach(level, w.furnaceRoom, 2.5D)) { w.owner.workStatus("Здесь тесно для печи, выхожу туда, где есть место."); return; }
                }
                if (w.lots.isEmpty() && w.furnaceRoom == null && w.trip.toHome()) return;
                CompanionActionResult placed = w.trip.place("minecraft:furnace");
                if (placed.success() && w.owner.lastPlacedPosition() != null) {
                    w.furnacesPlaced++;
                    w.lots.add(new CompanionWork.Lot(w.owner.lastPlacedPosition(), true));
                    w.owner.workStatus(w.lots.size() > 1 ? "Ставлю ещё печь, чтобы плавить быстрее: печей " + w.lots.size() + "." : "Ставлю печь.");
                    w.cooldown = 10; return;
                }
                // Like a player in a narrow cut: out to where the order came, and the furnace is set down there (marathon 23.10,
                // «долина»: down the slope for the stone, the bench took the one free cell of the cut, and the meat stayed raw).
                if (w.lots.isEmpty() && w.furnaceRoom == null && w.placementAnchor != null && w.owner.distanceToSqr(w.placementAnchor.getCenter()) > 3 * 3) {
                    w.furnaceRoom = w.placementAnchor; w.resetApproach(); return;
                }
                if (w.lots.isEmpty()) { w.finish(false, placed.message()); return; }
            }
        }
        if (w.lots.isEmpty()) {
            if (w.ownFurnaceFailed) { w.finish(false, "Свою печь поставил, но подойти к ней и заложить сырьё не получилось. Сырьё у меня."); return; }
            if (!w.crafting.approachCraftingTable() && w.items.moveHandMaterialsToBag()) craftFurnace();
            return;
        }
        // Every furnace taken is loaded and still more raw material is left: no room for more furnaces, say so at the end.
        if (w.lots.stream().allMatch(lot -> lot.loaded)) { w.smeltCount = loadedTotal(); w.furnacesShort = true; }
        w.phase = CompanionWork.Phase.COOK; w.heading = null; w.resetApproach();
    }

    private void craftFurnace() {
        // Smelting cobblestone itself: the new furnace must not eat the stone that was to go inside it.
        int reserved = new ItemStack(w.smelt.input()).is(ItemTags.STONE_CRAFTING_MATERIALS) ? w.smeltCount : 0;
        int missing = CompanionCraftCosts.stoneCost("minecraft:furnace") + reserved - w.items.countStoneMaterials();
        if (missing > 0) {
            if (++w.craftAttempts > 3) { w.finish(false, "Для печи не хватает камня, а добыть его не вышло. Сырьё у меня."); return; }
            w.owner.workStatus("Свободной печи рядом нет. Добуду камня и сделаю свою.");
            w.wantedStone = missing; w.stoneCollectedBefore = w.owner.collectedStone(); w.gathered = 0; w.phase = CompanionWork.Phase.PICKAXE; w.afterStone = CompanionWork.Phase.FURNACE;
            w.stoneSearch = null; w.stoneCandidates = null; w.rejected.clear(); w.dryAnchor = null; w.resetTarget();
            return;
        }
        CompanionActionResult crafted = w.owner.craftFromBag("minecraft:furnace", 1);
        if (!crafted.success()) {
            if (crafted.message().contains("места") || crafted.message().contains("безопасное место")) w.finish(false, crafted.message());
            else w.crafting.requestWood(CompanionWork.Phase.FURNACE);
        }
        // On success the crafting gesture finishes first; the furnace is set down on the next stage.
    }

    private BlockPos findFurnace(ServerLevel level) {
        BlockPos center = w.owner.blockPosition();
        BlockPos group = w.lots.isEmpty() ? null : w.lots.getFirst().pos;
        return w.chests.blockEntitiesNear(level).stream().filter(pos -> !w.triedFurnaces.contains(pos)
                        && (!w.owner.ownPlacesOnly() || w.owner.placedByMe(pos) && w.owner.leashAllows(pos))
                        && w.lots.stream().noneMatch(lot -> lot.pos.equals(pos)) && !w.owner.batches().reserved(level, pos)
                        && (group == null || pos.distSqr(group) <= CompanionWork.FURNACE_GROUP * CompanionWork.FURNACE_GROUP)
                        && level.getBlockEntity(pos) instanceof FurnaceBlockEntity oven && freeFor(oven))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(center))).map(BlockPos::immutable).orElse(null);
    }

    /** Someone else's furnace at work stays theirs: only an empty input slot, and an output our product can join. */
    private boolean freeFor(AbstractFurnaceBlockEntity oven) { return oven.getItem(0).isEmpty() && outputRoom(oven) > 0; }

    private int outputRoom(AbstractFurnaceBlockEntity oven) {
        ItemStack out = oven.getItem(2);
        if (out.isEmpty()) return 64;
        return ItemStack.isSameItemSameComponents(out, w.smelt.result()) ? (out.getMaxStackSize() - out.getCount()) / w.smelt.result().getCount() : 0;
    }

    private void dropLot(CompanionWork.Lot lot) {
        w.triedFurnaces.add(lot.pos);
        if (lot.placed) w.ownFurnaceFailed = true;
        w.lots.remove(lot);
        w.heading = null; w.resetApproach();
    }

    private AbstractFurnaceBlockEntity oven(ServerLevel level, CompanionWork.Lot lot) { return CompanionFurnaceReader.oven(level, lot); }

    void cook(ServerLevel level) {
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        for (CompanionWork.Lot lot : List.copyOf(w.lots)) {
            if (oven(level, lot) != null) continue;
            if (!lot.loaded) dropLot(lot);
            else if (lot.open() && CompanionFurnaceReader.gone(level, lot)) { lot.gaveUp = true; w.owner.workStatus("Печь на " + lot.pos.toShortString() + " пропала или выгрузилась, пока плавилось."); }
        }
        int remaining = w.smeltCount - loadedTotal();
        CompanionWork.Lot next;
        if (remaining > 0) {
            next = w.lots.stream().filter(lot -> !lot.loaded).findFirst().orElse(null);
            if (next == null) { w.phase = CompanionWork.Phase.FURNACE; w.heading = null; return; }
        } else {
            w.lots.removeIf(lot -> !lot.loaded); // spare furnaces are not needed any more
            for (CompanionWork.Lot lot : w.lots) if (lot.open()) watch(level, lot);
            if (w.lots.stream().noneMatch(CompanionWork.Lot::open)) { finishSmelt(level); return; }
            next = w.lots.stream().filter(lot -> lot.open() && needsHands(level, lot))
                    .min(Comparator.comparingDouble(lot -> w.owner.distanceToSqr(lot.pos.getCenter()))).orElse(null);
            if (next == null) { if (!w.owner.batches().parkIfLong(w, level)) waitAtFurnaces(level); return; }
        }
        if (!next.pos.equals(w.heading)) { w.heading = next.pos; w.resetApproach(); }
        if (travelTo(level, next)) return;
        w.owner.movementIntent(next.pos);
        w.owner.getLookControl().setLookAt(next.pos.getX() + .5D, next.pos.getY() + .5D, next.pos.getZ() + .5D);
        if (w.owner.distanceToSqr(next.pos.getCenter()) > 3.2D * 3.2D || !w.items.visibleFrom(level, w.owner.getEyePosition(), next.pos)) {
            if (w.approach(level, next.pos, 2.8D)) { w.owner.workStatus(next.loaded ? "Иду к печи на " + next.pos.toShortString() + "." : "Иду к печи на " + next.pos.toShortString() + " закладывать."); return; }
            // A cow at the furnace or a crowd on the way blocks for a moment, not for good: once more, then another furnace.
            if (!next.loaded && ++next.misses < 2) { w.resetApproach(); w.owner.workStatus("К печи на " + next.pos.toShortString() + " не подойти, попробую ещё раз."); return; }
            if (!next.loaded) dropLot(next);
            else { next.gaveUp = true; w.heading = null; w.owner.workStatus("Не могу снова подойти к печи на " + next.pos.toShortString() + "."); }
            return;
        }
        w.owner.getNavigation().stop();
        AbstractFurnaceBlockEntity oven = oven(level, next);
        if (!next.loaded) loadLot(level, next, oven, remaining); else serviceLot(level, next, oven);
    }

    /** A furnace far off, or in a chunk nobody has loaded yet, is walked to leg by leg: the plain approach only knows the loaded neighbourhood. */
    private boolean travelTo(ServerLevel level, CompanionWork.Lot lot) {
        boolean far = !level.hasChunkAt(lot.pos) || w.owner.distanceToSqr(lot.pos.getCenter()) > FAR_LOT * FAR_LOT;
        if (!far) { if (w.owner.travel().goal() != null) w.owner.travel().reset(); return false; }
        var step = w.owner.travel().toward(lot.pos, FAR_LOT - 8);
        if (step == CompanionTravel.Step.WALKING) { w.owner.workStatus("Иду к печи на " + lot.pos.toShortString() + "."); return true; }
        w.owner.travel().reset();
        return !level.hasChunkAt(lot.pos); // no chunk to approach into: keep trying until the work's own deadline
    }

    /** Raw material into the top slot, fuel under it: the same two slots a player fills. */
    private void loadLot(ServerLevel level, CompanionWork.Lot lot, AbstractFurnaceBlockEntity oven, int remaining) {
        if (!freeFor(oven)) { dropLot(lot); return; }
        int carried = CompanionSmelting.count(w.items.carriedItems(), w.smelt.input());
        if (carried <= 0) {
            w.smeltCount = loadedTotal();
            if (w.smeltCount == 0) w.finish(false, "Сырья для печи у меня не осталось.");
            return;
        }
        int unloaded = (int) w.lots.stream().filter(other -> !other.loaded).count();
        int amount = Math.min(Math.min((remaining + unloaded - 1) / unloaded, 64), Math.min(outputRoom(oven), carried));
        ItemStack fuelSlot = oven.getItem(1);
        int need = amount * w.smelt.cookTicks() - fuelSlot.getCount() * CompanionSmelting.burnTicks(fuelSlot);
        planksForFuel(need);
        var fuel = CompanionSmelting.chooseFuel(w.items.carriedItems(), w.smelt.input(), Math.min(remaining, carried), need, fuelSlot);
        if (need > 0 && fuel == null && fuelSlot.isEmpty()) {
            // Nothing to burn: chop wood first, the raw material stays in the bag meanwhile.
            w.owner.workStatus("Нечем топить печь. Схожу за дровами.");
            w.crafting.requestWood(CompanionWork.Phase.COOK, CompanionSmelting.logsFor(need)); return;
        }
        ItemStack input = w.items.takeCarried(w.smelt.input(), amount);
        if (input.isEmpty()) return;
        lot.count = input.getCount();
        lot.outputBefore = oven.getItem(2).getCount();
        oven.setItem(0, input);
        if (fuel != null) addFuel(lot, oven, fuel);
        oven.setChanged();
        lot.loaded = true; lot.progressTick = level.getGameTime();
        // The recipe's own cooking time: ten seconds an item for ordinary smelting.
        w.deadline = Math.max(w.deadline, level.getGameTime() + (long) lot.count * w.smelt.cookTicks() + SERVICE_ALLOWANCE_TICKS);
        if (!lot.placed) w.owner.rememberObserved(lot.pos);
        w.owner.animateAction(3, lot.pos, new ItemStack(w.smelt.input()));
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "furnace_loaded", "entity", w.owner.getUUID(), "position", lot.pos,
                "input", BuiltInRegistries.ITEM.getKey(w.smelt.input()), "count", lot.count, "fuel", fuel == null ? "none" : BuiltInRegistries.ITEM.getKey(fuel.item()) + " x" + fuel.count(),
                "fuelSlot", BuiltInRegistries.ITEM.getKey(oven.getItem(1).getItem()) + " x" + oven.getItem(1).getCount(), "placedByCompanion", lot.placed,
                "furnace", w.lots.indexOf(lot) + 1, "furnaces", w.lots.size());
        w.owner.workStatus("Заложил в печь " + lot.count + " «" + CompanionWork.itemName(w.smelt.input()) + "»"
                + (fuel == null ? "" : " и " + fuel.count() + " «" + CompanionWork.itemName(fuel.item()) + "» на растопку")
                + (w.lots.size() > 1 ? " (печь " + (w.lots.indexOf(lot) + 1) + " из " + w.lots.size() + ")" : "") + ".");
        w.cooldown = 10;
    }

    /**
     * Like a player short of fuel: logs into planks first. A log burns for one and a half items, its four planks for six
     * (marathon 23.10: three logs went under eight pieces of meat, and he walked off for more wood halfway).
     */
    private void planksForFuel(int needTicks) {
        if (needTicks <= 0) return;
        long have = 0;
        for (ItemStack stack : w.items.carriedItems()) if (!stack.isEmpty() && !stack.is(w.smelt.input()) && CompanionSmelting.fuelRank(stack) >= 0) have += (long) stack.getCount() * CompanionSmelting.burnTicks(stack);
        for (ItemStack stack : w.items.carriedItems()) {
            if (have >= needTicks) return;
            if (stack.isEmpty() || !stack.is(ItemTags.LOGS) || stack.is(w.smelt.input())) continue;
            String type = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replaceFirst("^stripped_", "").replaceFirst("_(log|wood|stem|hyphae)$", "");
            int burn = CompanionSmelting.burnTicks(stack);
            int logs = (int) Math.min(stack.getCount(), (needTicks - have + 4L * 300 - 1) / (4L * 300));
            if (logs <= 0 || !w.owner.craftFromBag("minecraft:" + type + "_planks", logs * 4).success()) continue;
            have += (long) logs * (4 * 300 - burn);
        }
    }

    private void addFuel(CompanionWork.Lot lot, AbstractFurnaceBlockEntity oven, CompanionSmelting.Fuel fuel) {
        ItemStack slot = oven.getItem(1);
        int theirs = slot.getCount();
        ItemStack added = w.items.takeCarried(fuel.item(), fuel.count());
        if (added.isEmpty()) return;
        if (slot.isEmpty()) oven.setItem(1, added);
        else if (ItemStack.isSameItemSameComponents(slot, added)) slot.grow(added.getCount());
        else { w.owner.storeLoot(added); return; }
        // Fuel is added only to an empty slot or once at loading, so what lay there before is someone else's.
        lot.fuelPut = fuel.item(); lot.fuelOurs = added.getCount(); lot.fuelTheirs = theirs;
    }

    private void watch(ServerLevel level, CompanionWork.Lot lot) { CompanionFurnaceReader.watch(level, w.smelt, lot); }
    private int left(CompanionWork.Lot lot, AbstractFurnaceBlockEntity oven) { return CompanionFurnaceReader.left(w.smelt, oven); }
    private boolean lit(ServerLevel level, CompanionWork.Lot lot) { return CompanionFurnaceReader.lit(level, lot); }
    private boolean stalled(ServerLevel level, CompanionWork.Lot lot) { return CompanionFurnaceReader.stalled(level, w.smelt, lot); }
    private boolean needsHands(ServerLevel level, CompanionWork.Lot lot) { return CompanionFurnaceReader.needsHands(level, w.smelt, lot); }

    private void serviceLot(ServerLevel level, CompanionWork.Lot lot, AbstractFurnaceBlockEntity oven) {
        int left = left(lot, oven);
        if (left == 0 || lot.ready >= lot.count) { emptyLot(level, lot, oven, "done"); return; }
        // Out of fire and fuel is no stuck furnace (marathon 23.10: back from the wood with fuel, the minute of his walk counted as
        // «печь перестала плавить», and half the meat came back raw). It gets fuel; stuck is only a furnace that has fuel and makes nothing.
        boolean outOfFuel = !lit(level, lot) && oven.getItem(1).isEmpty();
        if (!outOfFuel && stalled(level, lot)) { emptyLot(level, lot, oven, "stalled"); return; }
        if (!outOfFuel) return;
        planksForFuel(left * w.smelt.cookTicks());
        var fuel = CompanionSmelting.chooseFuel(w.items.carriedItems(), null, 0, left * w.smelt.cookTicks(), ItemStack.EMPTY);
        if (fuel == null) {
            w.owner.workStatus("Топливо кончилось, схожу за дровами. Сырьё пока в печи.");
            w.crafting.requestWood(CompanionWork.Phase.COOK, CompanionSmelting.logsFor(left * w.smelt.cookTicks())); return;
        }
        addFuel(lot, oven, fuel); oven.setChanged();
        w.owner.animateAction(3, lot.pos, new ItemStack(fuel.item()));
        w.owner.workStatus("Подбросил в печь " + fuel.count() + " «" + CompanionWork.itemName(fuel.item()) + "».");
        lot.progressTick = level.getGameTime(); w.cooldown = 10;
    }

    /** Nothing to do right now: stand by the furnaces and look at the one with the most left. */
    private void waitAtFurnaces(ServerLevel level) {
        CompanionWork.Lot nearest = w.lots.stream().filter(CompanionWork.Lot::open).min(Comparator.comparingDouble(lot -> w.owner.distanceToSqr(lot.pos.getCenter()))).orElseThrow();
        if (w.owner.distanceToSqr(nearest.pos.getCenter()) > 4.5 * 4.5) {
            if (!nearest.pos.equals(w.heading)) { w.heading = nearest.pos; w.resetApproach(); }
            w.owner.movementIntent(nearest.pos);
            if (w.approach(level, nearest.pos, 2.8D)) return;
        }
        // Waiting is no failed approach: the walk to take the product starts its own progress clock (test 23.10: after the
        // ten seconds of cooking the old clock had run out, and «не могу снова подойти» left the meat in the furnace).
        w.resetApproach();
        w.owner.getNavigation().stop();
        CompanionWork.Lot busiest = w.lots.stream().filter(CompanionWork.Lot::open).max(Comparator.comparingInt(lot -> lot.count - lot.ready)).orElse(nearest);
        w.owner.getLookControl().setLookAt(busiest.pos.getX() + .5D, busiest.pos.getY() + .5D, busiest.pos.getZ() + .5D);
        int furnaces = (int) w.lots.stream().filter(lot -> lot.loaded).count();
        w.owner.workStatus("Плавлю «" + CompanionWork.itemName(w.smelt.input()) + "»" + (furnaces > 1 ? " в " + furnaces + " печах" : "") + ": готово " + smeltReady() + " из " + w.smeltCount + ".");
    }

    private int product(CompanionWork.Lot lot, AbstractFurnaceBlockEntity oven) { return CompanionFurnaceReader.product(w.smelt, lot, oven); }

    /** Like a player at the furnace: the product, raw material still left and his own unburnt fuel go back into the bag. */
    private void emptyLot(ServerLevel level, CompanionWork.Lot lot, AbstractFurnaceBlockEntity oven, String reason) {
        int product = product(lot, oven);
        lot.taken = w.items.takeFromSlot(oven, 2, product);
        int raw = left(lot, oven);
        lot.rawBack = w.items.takeFromSlot(oven, 0, raw);
        int fuelBack = lot.fuelPut != null && oven.getItem(1).is(lot.fuelPut)
                ? w.items.takeFromSlot(oven, 1, Math.min(lot.fuelOurs, oven.getItem(1).getCount() - lot.fuelTheirs)) : 0;
        oven.setChanged();
        lot.emptied = true; lot.ready = lot.taken / w.smelt.result().getCount();
        lot.stuck = lot.taken < product || lot.rawBack < raw; lot.stalled = "stalled".equals(reason);
        w.owner.animateAction(3, lot.pos, w.smelt.result());
        level.playSound(null, lot.pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, .4F, 1F);
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "furnace_emptied", "entity", w.owner.getUUID(), "position", lot.pos,
                "reason", reason, "product", product, "taken", lot.taken, "rawBack", lot.rawBack, "rawLeft", raw - lot.rawBack, "fuelBack", fuelBack);
        w.owner.workStatus("Забрал из печи " + lot.taken + " «" + CompanionWork.itemName(w.smelt.result().getItem()) + "».");
        w.cooldown = 5;
    }

    private void finishSmelt(ServerLevel level) {
        int per = w.smelt.result().getCount();
        int taken = w.lots.stream().mapToInt(lot -> lot.taken).sum(), rawBack = w.lots.stream().mapToInt(lot -> lot.rawBack).sum();
        List<CompanionWork.Lot> used = w.lots.stream().filter(lot -> lot.loaded).toList();
        String inName = CompanionWork.itemName(w.smelt.input()), outName = CompanionWork.itemName(w.smelt.result().getItem());
        String where = used.size() == 1 ? " Печь на " + used.getFirst().pos.toShortString() + "."
                : " Печи: " + String.join("; ", used.stream().map(lot -> lot.pos.toShortString()).toList()) + ".";
        String stuck = used.stream().anyMatch(lot -> lot.stuck) ? " Часть не влезла в сумку и осталась в печи." : "";
        int more = CompanionSmelting.count(w.items.carriedItems(), w.smelt.input()) - rawBack;
        String rest = w.smeltAll && more > 0 ? " В сумке ещё " + more + " «" + inName + "» — больше стопки за раз не беру." : "";
        if (w.ironPrep && taken > 0) {
            // Iron for a tool: the ingots are counted from the bag in the next step; furnaces stay where they are.
            w.owner.noteDeed("Для «" + CompanionWork.itemName(w.ironTool) + "» переплавил " + taken / per + " «" + inName + "» и забрал " + taken + " «" + outName + "»." + where + stuck, true);
            w.oreMining.ironStageDone(); return;
        }
        if (stuck.isEmpty() && used.stream().noneMatch(lot -> lot.gaveUp) && w.smeltCount == w.requestedCount && taken >= w.smeltCount * per) {
            if (w.mixedMeat) {
                w.mixedCooked += taken; w.mixedKinds++;
                w.mixedInputs.add(w.smelt.input());
                var next = CompanionSmelting.resolve(level, CompanionGame.ANY_MEAT,
                        w.items.carriedItems().stream().filter(stack -> !w.mixedInputs.contains(stack.getItem())).toList());
                if (next != null && w.mixedKinds < CompanionGame.Kind.values().length) {
                    // One verified load at a time, one workId for the whole request. Never burn someone else's input.
                    w.smelt = next; w.smeltCount = w.requestedCount = Math.min(64, CompanionSmelting.count(w.items.carriedItems(), next.input()));
                    w.lots.clear(); w.triedFurnaces.clear(); w.heading = w.furnaceRoom = null;
                    w.ownFurnaceFailed = w.furnacesShort = false; w.furnacesPlaced = 0; w.benchClimbs = 0;
                    w.phase = CompanionWork.Phase.FURNACE; w.resetTarget();
                    w.owner.workStatus("Пожарил одну часть мяса, готовлю остальное.");
                    return;
                }
                String excess = CompanionSmelting.resolve(level, CompanionGame.ANY_MEAT, w.items.carriedItems()) == null ? ""
                        : " Сырьё сверх стопки каждого вида осталось в сумке.";
                w.finish(true, "Пожарил и забрал " + w.mixedCooked + " порций мяса (видов: " + w.mixedKinds + ")." + excess + " В сумке: " + w.owner.getStoredItemsSummary() + ".");
                return;
            }
            w.finish(true, "Переплавил " + w.smeltCount + " «" + inName + "»" + (used.size() > 1 ? " в " + used.size() + " печах" : "") + " и забрал " + taken
                    + " «" + outName + "»." + where + rest + " В сумке: " + w.owner.getStoredItemsSummary() + "."); return;
        }
        String why = used.stream().anyMatch(lot -> lot.stalled) ? " Печь перестала плавить."
                : used.stream().anyMatch(lot -> lot.gaveUp) ? " Печь пропала или недоступна: что в ней было, не забрать."
                : w.furnacesShort ? " Свободных печей на всё не хватило, остальное сырьё у меня."
                : w.smeltCount < w.requestedCount ? " Сырья было только " + w.smeltCount + " — на остальное не хватает." : "";
        w.finish(false, "Переплавил " + taken / per + " из " + w.requestedCount + " «" + inName + "», забрал из печи " + taken + " «" + outName + "»." + why
                + (rawBack > 0 ? " Непереплавленное сырьё (" + rawBack + ") забрал обратно." : "") + stuck);
    }
}

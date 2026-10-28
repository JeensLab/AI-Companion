package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CompanionOreMining {
    /** Where ore is worked from: within reach, in sight, not on the ore's own block, where its drop can be picked up. */
    private static final CompanionRoute.Need APPROACH = new CompanionRoute.Need(3.5, false, true, true);
    final CompanionWork w;

    CompanionOreMining(CompanionWork w) { this.w = w; }

    /** The pickaxe this ore needs: the best carried one that really drops it, otherwise made the usual way. */
    void prepareOrePickaxe() {
        if (w.pickaxeSense.tick()) return;
        BlockState sample = w.ore.sampleIn(w.owner.level()).defaultBlockState();
        ItemStack best = ItemStack.EMPTY;
        float speed = -1;
        for (ItemStack stack : w.items.carriedItems())
            if (stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(sample) && stack.getDestroySpeed(sample) > speed) { best = stack; speed = stack.getDestroySpeed(sample); }
        if (!best.isEmpty()) {
            if (w.owner.getOffhandItem() == best) w.owner.unequipForSupport("offhand");
            CompanionActionResult equipped = w.owner.equipForWork(BuiltInRegistries.ITEM.getKey(best.getItem()).toString());
            if (!equipped.success()) { w.finish(false, equipped.message()); return; }
            w.owner.workStatus("Кирка готова, ищу «" + w.ore.oreName + "».");
            w.phase = CompanionWork.Phase.ORE; w.resetTarget();
            return;
        }
        if (++w.orePickaxeAttempts > 3) { w.finish(false, "Не получилось подготовить кирку для «" + w.ore.oreName + "». Вещи у меня."); return; }
        if (w.ore.pickaxeIn(w.owner.level()) == Items.IRON_PICKAXE && w.items.countCarried(Items.IRON_INGOT) < CompanionWork.ingotCost("minecraft:iron_pickaxe")) {
            // Like a player: iron first, smelted, then the pickaxe, then back to this ore.
            if (w.ironPrep || w.ironMines > 0) { w.finish(false, "Не получилось подготовить железную кирку для «" + w.ore.oreName + "». Вещи у меня."); return; }
            w.owner.workStatus("Для «" + w.ore.oreName + "» нужна железная кирка. Сначала добуду и переплавлю железо.");
            w.ironTool = "minecraft:iron_pickaxe"; w.ironToolCount = 1; w.ironNeeded = CompanionWork.ingotCost(w.ironTool); w.phase = CompanionWork.Phase.IRON;
            return;
        }
        w.owner.workStatus("Для «" + w.ore.oreName + "» нужна " + CompanionWork.itemName(w.ore.pickaxeIn(w.owner.level())).toLowerCase(java.util.Locale.ROOT) + ", сначала сделаю её.");
        w.requestedItem = BuiltInRegistries.ITEM.getKey(w.ore.pickaxeIn(w.owner.level())).toString(); w.requestedCount = 1; w.craftAttempts = 0;
        w.phase = CompanionWork.Phase.CRAFT;
    }

    /**
     * Iron for a tool, recounted from the bag at every step: enough ingots — the tool; raw iron — the furnace;
     * nothing — iron ore first. Each stage reports back here instead of ending the work.
     */
    void ironStep(ServerLevel level) {
        // Each stage of the chain gets its own five minutes; one ten-minute budget for ore, furnace, bench and goal was too tight.
        w.deadline = Math.max(w.deadline, level.getGameTime() + 20 * 300);
        int ingots = w.items.countCarried(Items.IRON_INGOT), missing = w.ironNeeded - ingots;
        String tool = "«" + CompanionWork.itemName(w.ironTool) + "»";
        if (missing <= 0) {
            w.requestedItem = w.ironTool; w.requestedCount = w.ironToolCount; w.craftAttempts = 0;
            w.owner.workStatus("Железа хватает, делаю " + tool + ".");
            w.phase = CompanionWork.Phase.CRAFT; w.resetTarget();
            return;
        }
        int raw = w.items.countCarried(Items.RAW_IRON);
        if (raw > 0) {
            if (++w.ironSmelts > 3) { w.finish(false, "Для " + tool + " не хватает железных слитков: печь не отдала нужное. Вещи у меня."); return; }
            w.smelt = CompanionSmelting.resolve(level, "minecraft:raw_iron", w.items.carriedItems());
            if (w.smelt == null) { w.finish(false, "В этом мире нет рецепта плавки сырого железа."); return; }
            w.smeltCount = Math.min(raw, missing); w.requestedCount = w.smeltCount; w.smeltAll = false;
            w.lots.clear(); w.triedFurnaces.clear(); w.heading = w.furnaceRoom = null; w.ownFurnaceFailed = w.furnacesShort = false; w.furnacesPlaced = 0; w.craftAttempts = 0;
            w.ironPrep = true; w.phase = CompanionWork.Phase.FURNACE; w.resetTarget();
            w.owner.workStatus("Для " + tool + " переплавлю " + w.smeltCount + " сырого железа.");
            return;
        }
        if (++w.ironMines > 2) { w.finish(false, "Для " + tool + " нужно ещё " + missing + " железных слитков, а железной руды больше не набрал. Вещи у меня."); return; }
        if (w.job == CompanionWork.Job.ORE && w.goalOre == null) {
            w.goalOre = w.ore; w.goalOreWanted = w.oreWanted; w.goalOreMined = w.oreMined; w.goalOreDropsBefore = w.oreDropsBefore; w.goalOreExit = w.oreExit;
        }
        w.ore = CompanionOre.Kind.IRON; w.oreWanted = missing; w.oreMined = w.oreFound = w.oreLiquid = w.oreUnreachable = w.orePickaxeAttempts = 0;
        w.oreDone = w.oreChoosing = w.oreLootRetried = false; w.oreSearch = null; w.oreCandidates = null; w.oreRejected.clear(); w.oreOffFoot.clear(); w.oreNoTunnel.clear(); w.oreTunnel.clear(); w.oreExit = null;
        w.oreDropsBefore = w.items.countCarried(Items.RAW_IRON);
        w.ironPrep = true; w.phase = CompanionWork.Phase.ORE_PICK; w.resetTarget();
        w.owner.workStatus("Для " + tool + " нужно " + w.ironNeeded + " железных слитков. Иду за железной рудой.");
    }

    /** The preparation stage is over: the ore the order is about comes back, and the iron is recounted. */
    void ironStageDone() {
        w.ironPrep = false;
        if (w.goalOre != null) {
            w.ore = w.goalOre; w.oreWanted = w.goalOreWanted; w.oreMined = w.goalOreMined; w.oreDropsBefore = w.goalOreDropsBefore;
            w.oreFound = w.oreLiquid = w.oreUnreachable = w.orePickaxeAttempts = 0; w.oreDone = w.oreChoosing = w.oreLootRetried = false;
            w.oreSearch = null; w.oreCandidates = null; w.oreRejected.clear(); w.oreOffFoot.clear(); w.oreNoTunnel.clear(); w.oreTunnel.clear(); w.oreExit = w.goalOreExit; w.goalOre = null;
        }
        w.phase = CompanionWork.Phase.IRON; w.resetTarget();
    }

    void mineOre(ServerLevel level) {
        if (w.oreDone || w.oreMined >= w.oreWanted) {
            w.oreDone = true;
            // A drop that fell into a pocket while the rest of the vein still closed it is reachable now: one more round.
            if (!w.oreLootRetried) { w.oreLootRetried = true; w.owner.retryHarvestPickup(); return; }
            climbOutOfOre(level); return;
        }
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        if (w.oreCandidates == null) {
            if (w.oreSearch == null) w.oreSearch = new CompanionOre.Search(level, w.owner.blockPosition(), w.owner.getSearchRadius(), w.ore);
            if (!w.oreSearch.scan(level)) { w.owner.workStatus("Ищу «" + w.ore.oreName + "» в пределах " + w.oreSearch.radius() + " блоков."); return; }
            w.oreCandidates = new ArrayList<>(w.oreSearch.results(level)); w.oreFound = w.oreCandidates.size();
        }
        if (!w.oreTunnel.isEmpty()) { digOreTunnel(level); return; }
        if (w.target == null) {
            w.target = nextOre(level);
            if (w.target == null) {
                if (w.oreChoosing) { w.owner.workStatus("Ищу подход к «" + w.ore.oreName + "»."); return; }
                w.oreDone = true; climbOutOfOre(level); return;
            }
            if (!w.oreTunnel.isEmpty()) return;
        }
        BlockState state = level.getBlockState(w.target);
        if (!w.ore.is(state)) { w.resetTarget(); return; }
        if (CompanionOre.liquidNear(level, w.target)) { w.oreLiquid++; rejectOre(); return; }
        if (!safeOreSwing(level, w.target)) return;
        if (w.items.prepareMiningTool(state)) return;
        if (!w.owner.getMainHandItem().isCorrectToolForDrops(state)) {
            w.owner.selectToolForBlock(state);
            if (!w.owner.getMainHandItem().isCorrectToolForDrops(state)) { w.resetTarget(); w.phase = CompanionWork.Phase.ORE_PICK; return; }
        }
        // Ore at the feet in a wall leaves a one-high pocket: its drop and the rest of the vein would be out of sight and reach.
        // Like a player's 1x2, the block above goes first.
        BlockPos head = w.target.above();
        BlockState above = level.getBlockState(head);
        if (w.target.getY() <= w.owner.getBlockY() && head.getY() >= w.owner.getBlockY() && level.getBlockState(w.target.below()).isFaceSturdy(level, w.target.below(), Direction.UP)
                && !above.isAir() && !w.ore.is(above) && CompanionOre.tunnelable(above) && CompanionOre.safeTunnelCell(level, head)) {
            w.oreTunnel.addFirst(head.immutable()); return;
        }
        int reach = w.items.reachBlock(level, w.target, true);
        if (reach < 0) { w.oreUnreachable++; rejectOre(); return; }
        if (reach == 0) return;
        w.owner.workStatus("Добываю «" + w.ore.oreName + "»: " + w.oreMined + " из " + w.oreWanted + ".");
        if (!w.breaking.tick(level, w.target)) return;
        if (harvestOreCell(level, w.target)) w.resetTarget();
    }

    /** The next ore that can really be reached: in sight from a standing place, or behind a short safe tunnel. A few per tick. */
    /**
     * Like stone: ore he can hit from here first, then the ore nearest on foot (one route search for the nearest ones in
     * sight), and a short tunnel only when walking plus digging it is less than the walk to the nearest ore in sight.
     */
    private BlockPos nextOre(ServerLevel level) {
        w.oreChoosing = false;
        for (var iterator = w.oreCandidates.iterator(); iterator.hasNext(); ) {
            BlockPos candidate = iterator.next();
            if (w.oreRejected.contains(candidate) || !w.owner.leashAllows(candidate)
                    || w.owner.autonomy().running() && candidate.getY() < w.owner.autonomy().center().getY() - CompanionOre.MAX_DROP
                    || !level.hasChunkAt(candidate) || !w.ore.is(level.getBlockState(candidate))) { iterator.remove(); continue; }
            if (CompanionOre.liquidNear(level, candidate)) { w.oreLiquid++; w.oreRejected.add(candidate); iterator.remove(); continue; }
            // Neither on foot nor by a tunnel from here.
            if (w.oreOffFoot.contains(candidate) && w.oreNoTunnel.contains(candidate)) { w.oreUnreachable++; w.oreRejected.add(candidate); iterator.remove(); }
        }
        List<BlockPos> sorted = w.oreCandidates.stream().sorted(Comparator.comparingDouble(pos -> w.owner.distanceToSqr(pos.getCenter()))).toList();
        for (BlockPos candidate : sorted) {
            if (w.owner.distanceToSqr(candidate.getCenter()) > 3.8 * 3.8) break;
            if (CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), candidate)
                    && CompanionMiningNavigation.dropCollectable(level, candidate, w.owner.blockPosition())) return chosenOre(candidate, null);
        }
        List<BlockPos> group = sorted.stream().filter(pos -> !w.oreOffFoot.contains(pos)).limit(CompanionWork.STONE_GROUP).toList();
        CompanionRoute.Reach walk = group.isEmpty() ? null : w.route.nearest(group, APPROACH);
        if (walk != null && !walk.found()) walk = null;
        if (walk != null && !safeOreApproach(walk.path())) { w.oreOffFoot.add(walk.block()); walk = null; }
        int budget = 2;
        java.util.Map<BlockPos, Double> keys = new java.util.HashMap<>();
        for (BlockPos candidate : sorted) keys.put(candidate, oreDigKey(candidate));
        for (BlockPos candidate : sorted.stream().sorted(Comparator.comparingDouble(keys::get)).toList()) {
            if (walk != null && keys.get(candidate) >= walk.length()) break;
            if (w.oreNoTunnel.contains(candidate)) continue;
            if (budget-- <= 0) { w.oreChoosing = true; return null; }
            if (!w.oreOffFoot.contains(candidate) && (walk == null || !group.contains(candidate))) {
                // Its own walk: beyond the compared group, or the group search spent its steps on an ore it cannot reach.
                CompanionRoute.Reach own = w.route.to(candidate, APPROACH);
                boolean safe = safeOreApproach(own.path());
                if (safe && (walk == null || own.length() < walk.length())) return chosenOre(candidate, own.path());
                if (!safe) w.oreOffFoot.add(candidate);
            }
            int tries = 0;
            for (BlockPos stand : CompanionOre.stands(w.owner, candidate)) {
                if (tries >= 6) break;
                List<BlockPos> cells = CompanionOre.tunnel(level, stand, candidate);
                if (cells == null || cells.stream().anyMatch(cell -> blockedOreCell(level, cell))) continue;
                tries++;
                if (w.owner.autonomy().running() && w.oreExit != null && !CompanionMiningNavigation.canWalkBackUp(w.owner, stand, w.oreExit.getY())) continue;
                Path way = w.owner.blockPosition().equals(stand) ? null : w.route.toCells(Set.of(stand));
                if (way == null && !w.owner.blockPosition().equals(stand)) continue;
                // Each cell is a few swings; a walk to the ore in sight that is shorter wins.
                if (walk != null && (way == null ? 0 : CompanionMiningNavigation.length(w.owner, way)) + cells.size() * 2 >= walk.length()) continue;
                w.oreTunnel.addAll(cells);
                if (w.oreExit == null && stand.getY() > candidate.getY()) w.oreExit = stand;
                dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "ore_tunnel_planned", "entity", w.owner.getUUID(),
                        "ore", candidate, "stand", stand, "cells", cells.size());
                return chosenOre(candidate, null);
            }
            w.oreNoTunnel.add(candidate);
            if (w.oreOffFoot.contains(candidate)) { w.oreUnreachable++; w.oreRejected.add(candidate); w.oreCandidates.remove(candidate); }
        }
        return walk == null ? null : chosenOre(walk.block(), walk.path());
    }
    private boolean safeOreApproach(Path route) {
        if (route == null || !route.canReach() || route.getEndNode() == null) return false;
        return !w.owner.autonomy().running() || w.oreExit == null
                || CompanionMiningNavigation.canWalkBackUp(w.owner, route.getEndNode().asBlockPos(), w.oreExit.getY());
    }
    /** Straight distance, and a hidden ore costs the digging to it: the order in which tunnels are worth a look. */
    private double oreDigKey(BlockPos pos) {
        return Math.sqrt(w.owner.distanceToSqr(pos.getCenter())) + (exposedOre(pos) ? 0 : 6);
    }
    private boolean exposedOre(BlockPos pos) {
        for (Direction side : Direction.values())
            if (w.owner.level().getBlockState(pos.relative(side)).getCollisionShape(w.owner.level(), pos.relative(side)).isEmpty()) return true;
        return false;
    }
    private BlockPos chosenOre(BlockPos candidate, Path route) {
        w.oreCandidates.remove(candidate); w.oreOffFoot.clear(); w.oreNoTunnel.clear();
        w.resetApproach(); w.path = route;
        return candidate;
    }

    private void digOreTunnel(ServerLevel level) {
        BlockPos cell = w.oreTunnel.peekFirst();
        if (!w.owner.leashAllows(cell)) { rejectOre(); return; }
        BlockState state = level.getBlockState(cell);
        if (state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()) { w.oreTunnel.removeFirst(); w.resetApproach(); return; }
        // The world may have changed since planning: never dig into liquid, under sand or into something built.
        if (!CompanionOre.tunnelable(state) || !CompanionOre.safeTunnelCell(level, cell)) { w.oreUnreachable++; rejectOre(); return; }
        if (!safeOreSwing(level, cell)) return;
        if (w.items.prepareMiningTool(state)) return;
        w.owner.selectToolForBlock(state);
        // Another ore on the way that this pickaxe would only destroy is not dug through.
        if (state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES) && !w.owner.getMainHandItem().isCorrectToolForDrops(state)) { w.oreUnreachable++; rejectOre(); return; }
        int reach = w.items.reachBlock(level, cell, false);
        if (reach < 0) { w.oreUnreachable++; rejectOre(); return; }
        if (reach == 0) return;
        w.owner.workStatus("Прокладываю ход к «" + w.ore.oreName + "».");
        if (!w.breaking.tick(level, cell)) return;
        if (harvestOreCell(level, cell)) { w.oreTunnel.removeFirst(); w.resetApproach(); w.cooldown = 2; }
    }

    /** Breaks a block like a player: real drops for the tool in hand, wear, the drops fall into the world to be picked up. */
    private boolean harvestOreCell(ServerLevel level, BlockPos pos) {
        // Leave the step used to get back up, as in stone mining. A vein can contain that very step.
        if (!safeOreSwing(level, pos)) return false;
        BlockState state = level.getBlockState(pos);
        ItemStack tool = w.owner.getMainHandItem();
        var drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), w.owner, tool);
        if (!drops.isEmpty() && !w.items.canStore(drops)) { w.finish(false, "В сумке нет места для добычи. Освободи место или попроси сложить ресурсы в сундук."); return false; }
        if (!level.destroyBlock(pos, false, w.owner, 512)) { blockOreCell(level, pos, "destroy_rejected"); return false; }
        tool.getItem().mineBlock(tool, level, state, pos, w.owner);
        w.owner.dropHarvest(pos, drops);
        if (w.ore.is(state)) {
            w.oreMined++;
            w.owner.observeEvent("ore", "Добыл блок руды «" + w.ore.oreName + "», подбираю выпавшее. Работа ещё может продолжаться.");
            // The rest of the vein first: same kind touching the mined block.
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (level.hasChunkAt(next) && w.ore.is(level.getBlockState(next)) && !w.oreRejected.contains(next)) { w.oreCandidates.remove(next); w.oreCandidates.addFirst(next.immutable()); }
            }
        }
        return true;
    }

    private void rejectOre() {
        if (w.target != null) w.oreRejected.add(w.target);
        w.oreTunnel.clear(); w.resetTarget();
    }

    /** No cracks until a block is safe. A shared stair must not be swung at again for every ore behind it. */
    private boolean safeOreSwing(ServerLevel level, BlockPos pos) {
        if (blockedOreCell(level, pos)) { w.oreUnreachable++; rejectOre(); return false; }
        if (CompanionLighting.supportsLight(level, pos)) { blockOreCell(level, pos, "path_light"); return false; }
        if (!w.owner.autonomy().running() && !w.owner.autonomy().protectsReturn()) return true;
        if (CompanionMiningNavigation.supportsLivingEntity(w.owner, pos)) {
            w.breaking.reset();
            if (level.getGameTime() - w.lastProgressTick > 160) { blockOreCell(level, pos, "occupied_support"); return false; }
            if (w.path == null || w.route.legOver()) w.path = w.route.to(pos, new CompanionRoute.Need(3.5, false, true, false)).path();
            if (w.path == null || !safeOreApproach(w.path)) { blockOreCell(level, pos, "occupied_support"); return false; }
            w.owner.movementIntent(pos); w.route.walk(w.path); return false;
        }
        if (!CompanionMiningNavigation.preservesAutonomousReturn(w.owner, pos) || w.oreExit != null && w.owner.getY() < w.oreExit.getY() - .5
                && !CompanionMiningNavigation.canWalkBackUp(w.owner, w.owner.blockPosition(), w.oreExit.getY(), pos)) {
            blockOreCell(level, pos, "return_stair"); return false;
        }
        return true;
    }
    private boolean blockedOreCell(ServerLevel level, BlockPos pos) {
        BlockState blocked = w.oreBlockedCells.get(pos);
        if (blocked == null) return false;
        if (blocked.equals(level.getBlockState(pos))) return true;
        w.oreBlockedCells.remove(pos); return false;
    }
    private void blockOreCell(ServerLevel level, BlockPos pos, String reason) {
        w.oreBlockedCells.put(pos.immutable(), level.getBlockState(pos));
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "ore_cell_skipped", "entity", w.owner.getUUID(), "pos", pos, "reason", reason);
        w.oreUnreachable++; rejectOre();
    }

    /** Back up the own stairs before reporting, like after stone: in legs when the way is longer than one search. */
    private void climbOutOfOre(ServerLevel level) {
        if (w.oreExit == null || w.owner.getY() >= w.oreExit.getY() - .5) { finishOre(); return; }
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        if (w.orderHasStalled(level, 300)) { finishOre(); return; }
        w.owner.movementIntent(w.oreExit);
        w.owner.workStatus("Поднимаюсь по своему ходу наверх.");
        // The exact start cell may have been a temporary support (autonomous work): near it, any safe cell at its height is an exit.
        boolean exact = !w.owner.autonomy().running();
        var step = w.route.legs(w.oreExit, exact ? 2.5 : 3.5);
        if (step == CompanionTravel.Step.WALKING) return;
        if (step == CompanionTravel.Step.FAILED) { finishOre(); return; }
        // Near it but not up on its level: the last steps by one search.
        if (w.path == null || w.route.legOver()) {
            Set<BlockPos> exits = new HashSet<>();
            if (exact) exits.add(w.oreExit);
            else for (BlockPos cell : BlockPos.betweenClosed(w.oreExit.offset(-3,0,-3), w.oreExit.offset(3,1,3)))
                if (CompanionMiningNavigation.standable(w.owner, cell)) exits.add(cell.immutable());
            w.path = exits.isEmpty() ? null : w.route.toCells(exits);
        }
        if (w.path != null) w.route.walk(w.path);
    }

    private void finishOre() {
        w.route.legsEnd(w.oreExit == null || w.owner.getY() >= w.oreExit.getY() - .5); w.route.abandon();
        int gained = Math.max(0, w.items.countCarried(w.ore.drop) - w.oreDropsBefore);
        String got = "«" + w.ore.oreName + "»: в сумке прибавилось " + gained + " «" + CompanionWork.itemName(w.ore.drop) + "».";
        String below = w.oreExit != null && w.owner.getY() < w.oreExit.getY() - .5
                ? " Наверх пока не выбрался: стою на " + w.owner.blockPosition().toShortString() + "." : "";
        String bag = " В сумке: " + w.owner.getStoredItemsSummary() + ".";
        if (w.ironPrep && gained > 0) {
            w.owner.noteDeed("Для «" + CompanionWork.itemName(w.ironTool) + "» добыл " + CompanionWorkItems.blocks(w.oreMined) + " " + got, true);
            ironStageDone(); return;
        }
        if (w.oreMined >= w.oreWanted) { w.finish(true, "Добыл " + CompanionWorkItems.blocks(w.oreMined) + " " + got + below + bag); return; }
        int radius = w.oreSearch == null ? w.owner.getSearchRadius() : w.oreSearch.radius(), depth = w.oreSearch == null ? CompanionOre.MAX_DROP : w.oreSearch.depth();
        String why = w.oreFound == 0 ? " Руды «" + w.ore.oreName + "» не нашёл в пределах " + radius + " блоков и до " + depth + " блоков вниз."
                : " Больше доступной руды «" + w.ore.oreName + "» рядом нет.";
        if (w.oreLiquid > 0) why += " У " + CompanionWorkItems.blocks(w.oreLiquid) + " рядом вода или лава — их не трогал.";
        if (w.oreUnreachable > 0) why += " К " + w.oreUnreachable + " не нашёл безопасного хода: не глубже " + CompanionOre.MAX_DROP
                + " блоков, без гравия, песка и жидкостей рядом.";
        if (w.ironPrep) {
            String goal = w.goalOre != null ? "Для «" + w.goalOre.oreName + "» нужна железная кирка, а на неё нужно " : "Для «" + CompanionWork.itemName(w.ironTool) + "» нужно ";
            w.finish(CompanionActionResult.blocked(goal + w.ironNeeded + " железных слитков, и железа я не набрал." + why + below + bag)); return;
        }
        w.finish(false, "Добыл " + w.oreMined + " из " + CompanionWorkItems.blocks(w.oreWanted) + " " + got + why + below + bag);
    }
}

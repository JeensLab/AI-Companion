package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CompanionOreMining {
    /** Where ore is worked from: within reach, in sight, not on the ore's own block, where its drop can be picked up. */
    private static final CompanionRoute.Need APPROACH = new CompanionRoute.Need(3.5, false, true, true);
    final CompanionWork w;
    /** Ores left alone this order, for the report: lava runs in and he has nothing to plug it with / a spring runs in and nothing is left to plug it with / the ore lies on lava / a lava fall stands beside it. */
    private final Set<BlockPos> lackedPlugs = new HashSet<>(), lackedSpringPlugs = new HashSet<>(), overLava = new HashSet<>(), lavaFalls = new HashSet<>();
    /** What each item the ore blocks of this order dropped was worth in his bag before the first of them broke: «lost» is when none of it came. */
    private final Map<Item, Integer> oreDrops = new HashMap<>();
    /** The flooded cells of his tunnel he has passed: his own way, not a pond, whatever is dug beside them. */
    private final Set<BlockPos> wayWater = new HashSet<>();

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
        if (w.ore.pickaxeIn(w.owner.level()) == Items.IRON_PICKAXE && w.items.countCarried(Items.IRON_INGOT) < CompanionCraftCosts.ingotCost("minecraft:iron_pickaxe")) {
            // Like a player: iron first, smelted, then the pickaxe, then back to this ore.
            if (w.ironPrep || w.ironMines > 0) { w.finish(false, "Не получилось подготовить железную кирку для «" + w.ore.oreName + "». Вещи у меня."); return; }
            w.owner.workStatus("Для «" + w.ore.oreName + "» нужна железная кирка. Сначала добуду и переплавлю железо.");
            w.ironTool = "minecraft:iron_pickaxe"; w.ironToolCount = 1; w.ironNeeded = CompanionCraftCosts.ingotCost(w.ironTool); w.phase = CompanionWork.Phase.IRON;
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
        w.ore = CompanionOre.Kind.IRON; w.oreWanted = missing; w.oreMined = w.oreFound = w.oreUnreachable = w.orePickaxeAttempts = 0;
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
            w.oreFound = w.oreUnreachable = w.orePickaxeAttempts = 0; w.oreDone = w.oreChoosing = w.oreLootRetried = false;
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
            if (w.oreSearch == null) {
                w.oreSearch = new CompanionOre.Search(level, w.owner.blockPosition(), w.owner.getSearchRadius(), w.ore);
                lackedPlugs.clear(); lackedSpringPlugs.clear(); wayWater.clear(); overLava.clear(); lavaFalls.clear();
            }
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
        if (!opensSafely(level, w.target, false)) { rejectOre(); return; }
        if (!safeOreSwing(level, w.target)) return;
        if (w.items.prepareMiningTool(state)) return;
        if (!w.owner.getMainHandItem().isCorrectToolForDrops(state)) {
            w.owner.selectToolForBlock(state);
            if (!w.owner.getMainHandItem().isCorrectToolForDrops(state)) { w.resetTarget(); w.phase = CompanionWork.Phase.ORE_PICK; return; }
        }
        // Ore at the feet in a wall leaves a one-high pocket: its drop and the rest of the vein would be out of sight and reach.
        // Like a player's 1x2, the block above goes first, unless it lets in lava or a spring he has nothing to plug: then the ore
        // goes from the pocket, as before (game 30.10: a spring behind that block cost him the ore).
        BlockPos head = w.target.above();
        BlockState above = level.getBlockState(head);
        if (w.target.getY() <= w.owner.getBlockY() && head.getY() >= w.owner.getBlockY() && level.getBlockState(w.target.below()).isFaceSturdy(level, w.target.below(), Direction.UP)
                && !above.isAir() && !w.ore.is(above) && CompanionOre.tunnelable(above) && CompanionOre.safeTunnelCell(level, head)
                && lacksPlugs(level, List.of(head), null, ownWater(), true) == CompanionLava.Opening.SAFE) {
            w.oreTunnel.addFirst(head.immutable()); return;
        }
        // After the block over the ore: the cobblestone it gives may be the plug for a spring behind the ore.
        if (!opensSafely(level, w.target, true)) { rejectOre(); return; }
        int reach = w.items.reachBlock(level, w.target, true);
        if (reach < 0) { unreachable(w.target); rejectOre(); return; }
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
            if (!opensSafely(level, candidate, false)) { w.oreRejected.add(candidate); iterator.remove(); continue; }
            // Neither on foot nor by a tunnel from here (an ore already reported for lack of plugs is not counted again).
            if (w.oreOffFoot.contains(candidate) && w.oreNoTunnel.contains(candidate)) { unreachable(candidate); w.oreRejected.add(candidate); iterator.remove(); }
        }
        List<BlockPos> sorted = w.oreCandidates.stream().sorted(Comparator.comparingDouble(pos -> w.owner.distanceToSqr(pos.getCenter()))).toList();
        for (BlockPos candidate : sorted) {
            if (w.owner.distanceToSqr(candidate.getCenter()) > 3.8 * 3.8) break;
            if (CompanionMiningNavigation.visible(w.owner, w.owner.getEyePosition(), candidate)
                    && CompanionMiningNavigation.dropCollectable(level, candidate, w.owner.blockPosition())) return chosenOre(candidate, null);
        }
        List<BlockPos> group = sorted.stream().filter(pos -> !w.oreOffFoot.contains(pos)).limit(CompanionWork.STONE_GROUP).toList();
        CompanionRoute.Reach walk = group.isEmpty() ? null : w.route.nearest(group, APPROACH);
        if (walk != null && walk.busy()) { w.oreChoosing = true; return null; }
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
                // The tick's cells are spent: the choice already found (the group's walk) stands, it is not cancelled.
                if (own.busy()) { if (walk != null) break; w.oreChoosing = true; return null; }
                boolean safe = safeOreApproach(own.path());
                if (safe && (walk == null || own.length() < walk.length())) return chosenOre(candidate, own.path());
                if (!safe) w.oreOffFoot.add(candidate);
            }
            int tries = 0;
            boolean lacked = false, fall = false;
            for (BlockPos stand : CompanionOre.stands(w.owner, candidate)) {
                if (tries >= 6) break;
                List<BlockPos> cells = CompanionOre.tunnel(level, stand, candidate);
                if (cells == null || cells.stream().anyMatch(cell -> blockedOreCell(level, cell))) continue;
                if (CompanionLava.besideFall(level, cells)) { fall = true; continue; }
                // Open water along the way is judged here, springs only when a cell is opened: the cobblestone of the way is not in the bag yet.
                // His own water is what digOreTunnel will count as his: the stand is his only once he stands there (a stand in a pond is the pond),
                // and water on the line of the tunnel only as a pocket in the rock: the edge of a pond on it would run down his stairs (review 6).
                Set<BlockPos> own = ownWater();
                for (BlockPos cell : cells) if (!level.getFluidState(cell).isEmpty() && CompanionLava.pocket(level, cell, cells)) own.add(cell);
                var lack = lacksPlugs(level, cells, candidate, own, false);
                if (lack != CompanionLava.Opening.SAFE) { if (lack == CompanionLava.Opening.NO_PLUG) lacked = true; continue; }
                tries++;
                if (w.owner.autonomy().running() && w.oreExit != null && !CompanionMiningNavigation.canWalkBackUp(w.owner, stand, w.oreExit.getY())) continue;
                int busy = w.route.busyAnswers();
                Path way = w.owner.blockPosition().equals(stand) ? null : w.route.toCells(Set.of(stand));
                // This tick's cells went elsewhere: the tunnel is not ruled out, the choice is made again next tick.
                if (way == null && w.route.busyAnswers() != busy) { w.oreChoosing = true; return null; }
                if (way == null && !w.owner.blockPosition().equals(stand)) continue;
                // Each cell is a few swings; a walk to the ore in sight that is shorter wins.
                if (walk != null && (way == null ? 0 : CompanionMiningNavigation.length(w.owner, way)) + CompanionOre.toDig(level, cells).size() * 2 >= walk.length()) continue;
                w.oreTunnel.addAll(cells);
                if (w.oreExit == null && stand.getY() > candidate.getY()) w.oreExit = stand;
                dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "ore_tunnel_planned", "entity", w.owner.getUUID(),
                        "ore", candidate, "stand", stand, "cells", cells.size());
                return chosenOre(candidate, null);
            }
            w.oreNoTunnel.add(candidate);
            if (lacked) lackedPlugs.add(candidate.immutable());
            if (fall) lavaFalls.add(candidate.immutable());
            if (w.oreOffFoot.contains(candidate)) { unreachable(candidate); w.oreRejected.add(candidate); w.oreCandidates.remove(candidate); }
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
        if (state.isAir() || state.canBeReplaced() && !state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
            if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) wayWater.add(cell.immutable());
            w.oreTunnel.removeFirst(); w.resetApproach(); return;
        }
        // The world may have changed since planning: never dig into lava, under sand or into something built.
        if (!CompanionOre.tunnelable(state) || !CompanionOre.safeTunnelCell(level, cell)) { unreachable(w.target); rejectOre(); return; }
        if (CompanionLava.besideFall(level, List.of(cell))) { if (w.target != null) lavaFalls.add(w.target.immutable()); rejectOre(); return; }
        var lack = lacksPlugs(level, List.of(cell), null, ownWater(), true);
        if (lack == CompanionLava.Opening.FLOOD) { unreachable(w.target); rejectOre(); return; }
        if (lack != CompanionLava.Opening.SAFE) { if (w.target != null) (lack == CompanionLava.Opening.NO_PLUG ? lackedPlugs : lackedSpringPlugs).add(w.target.immutable()); rejectOre(); return; }
        if (!safeOreSwing(level, cell)) return;
        if (w.items.prepareMiningTool(state)) return;
        w.owner.selectToolForBlock(state);
        // Another ore on the way that this pickaxe would only destroy is not dug through.
        if (state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES) && !w.owner.getMainHandItem().isCorrectToolForDrops(state)) { unreachable(w.target); rejectOre(); return; }
        int reach = w.items.reachBlock(level, cell, false);
        if (reach < 0) { unreachable(w.target); rejectOre(); return; }
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
        var drops = CompanionTools.drops(state, level, pos, level.getBlockEntity(pos), w.owner, tool);
        if (!drops.isEmpty() && !w.items.canStore(drops)) { w.finish(false, "В сумке нет места для добычи. Освободи место или попроси сложить ресурсы в сундук."); return false; }
        if (w.ore.is(state)) {
            // The first ore of the order (or of a stage of it): what its drops are worth in the bag now is the base for «lost».
            if (w.oreMined == 0) oreDrops.clear();
            for (ItemStack drop : drops) oreDrops.putIfAbsent(drop.getItem(), w.items.countCarried(drop.getItem()));
        }
        // Lava that runs in when this cell is open: plugged in this very tick, before it moves.
        List<BlockPos> lava = CompanionLava.inflow(level, pos);
        // Water too, with what is left of the plugs, but only a spring in the wall: the cells of his own way (a flooded one too) and any pond are open, not plugged.
        List<BlockPos> water = CompanionLava.waterInflow(level, pos, ownWater());
        if (!level.destroyBlock(pos, false, w.owner, 512)) { blockOreCell(level, pos, "destroy_rejected"); return false; }
        tool.getItem().mineBlock(tool, level, state, pos, w.owner);
        w.owner.dropHarvest(pos, drops);
        CompanionLava.plug(w.owner, level, lava);
        CompanionLava.plug(w.owner, level, water);
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

    /** The water that is no spring of the wall but his own way: the cells of his tunnel (those passed too), his feet and head. */
    private Set<BlockPos> ownWater() {
        Set<BlockPos> own = new HashSet<>(w.oreTunnel);
        own.addAll(wayWater);
        own.add(w.owner.blockPosition()); own.add(w.owner.blockPosition().above());
        return own;
    }

    /**
     * The ore may be broken now: not on lava, and with plugs enough for the lava that runs in; at the break also no open water runs in
     * and the plugs cover the springs ({@link CompanionLava#opening}). If not, the reason is kept for the report.
     */
    private boolean opensSafely(ServerLevel level, BlockPos ore, boolean atBreak) {
        switch (CompanionLava.opening(w.owner, level, ore, atBreak ? ownWater() : null)) {
            case FLOOD -> unreachable(ore);
            case OVER_LAVA -> overLava.add(ore.immutable());
            case NO_PLUG -> lackedPlugs.add(ore.immutable());
            case NO_SPRING -> lackedSpringPlugs.add(ore.immutable());
            case FALL -> lavaFalls.add(ore.immutable());
            case SAFE -> { return true; }
        }
        return false;
    }

    /**
     * Open water runs into one of the solid cells among these (FLOOD), or more lava, then more lava and (if {@code springs}) springs
     * together, run into them (and into the ore at their end, if any) than he has plugs for. {@code own} is his way: the water in it is
     * no spring and no flood.
     */
    private CompanionLava.Opening lacksPlugs(ServerLevel level, List<BlockPos> cells, BlockPos ore, Collection<BlockPos> own, boolean springs) {
        List<BlockPos> dig = CompanionOre.toDig(level, cells), opened = new ArrayList<>(dig);
        for (BlockPos cell : dig) if (CompanionLava.floods(level, cell, own)) return CompanionLava.Opening.FLOOD;
        if (ore != null) opened.add(ore);
        return CompanionLava.plugsFor(w.owner, CompanionLava.inflow(level, opened).size(), springs ? CompanionLava.springs(level, opened, own).size() : 0);
    }

    /** One ore, one reason: an ore already left for lava or a spring (no plugs, over lava, a fall beside it) is not counted again as «no safe way». */
    private void unreachable(BlockPos ore) {
        if (ore == null || !lackedPlugs.contains(ore) && !lackedSpringPlugs.contains(ore) && !overLava.contains(ore) && !lavaFalls.contains(ore)) w.oreUnreachable++;
    }

    /** How many of these ores still stand: the ones he took after all are no refusal. */
    private int standing(Set<BlockPos> ores) {
        return (int) ores.stream().filter(pos -> w.ore.is(w.owner.level().getBlockState(pos))).count();
    }

    private void rejectOre() {
        if (w.target != null) w.oreRejected.add(w.target);
        w.oreTunnel.clear(); w.resetTarget();
    }

    /** No cracks until a block is safe. A shared stair must not be swung at again for every ore behind it. */
    private boolean safeOreSwing(ServerLevel level, BlockPos pos) {
        if (blockedOreCell(level, pos)) { unreachable(w.target); rejectOre(); return false; }
        if (CompanionLighting.supportsLight(level, pos)) { blockOreCell(level, pos, "path_light"); return false; }
        if (!w.owner.autonomy().running() && !w.owner.autonomy().protectsReturn()) return true;
        if (CompanionMiningNavigation.supportsLivingEntity(w.owner, pos)) {
            w.breaking.reset();
            if (level.getGameTime() - w.lastProgressTick > 160) { blockOreCell(level, pos, "occupied_support"); return false; }
            if (w.path == null || w.route.legOver()) {
                var reach = w.route.to(pos, new CompanionRoute.Need(3.5, false, true, false));
                if (reach.busy()) return false;
                w.path = reach.path();
            }
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
        unreachable(w.target); rejectOre();
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
        // The block is broken but its drop is not in the bag (it floated off, it lies where he cannot get at it): not «done».
        // Any drop of the ore counts (nuggets, the ore block under Silk Touch, more under Fortune), not only the usual one; iron for a tool counts only as raw iron.
        boolean anyDrop = gained > 0 || oreDrops.entrySet().stream().anyMatch(drop -> w.items.countCarried(drop.getKey()) > drop.getValue());
        boolean lost = w.oreMined >= w.oreWanted && !anyDrop;
        if (w.oreMined >= w.oreWanted && !lost && !w.ironPrep) { w.finish(true, "Добыл " + CompanionWorkItems.blocks(w.oreMined) + " " + got + below + bag); return; }
        int radius = w.oreSearch == null ? w.owner.getSearchRadius() : w.oreSearch.radius(), depth = w.oreSearch == null ? CompanionOre.MAX_DROP : w.oreSearch.depth();
        String why = lost ? " Выпавшее не достал: оно осталось там, где мне его не взять."
                : w.ironPrep && w.oreMined >= w.oreWanted ? " Руда выпала целыми блоками: кирка с «Шёлковым касанием» не даёт сырого железа."
                : w.oreFound == 0 ? " Руды «" + w.ore.oreName + "» не нашёл в пределах " + radius + " блоков и до " + depth + " блоков вниз."
                : " Больше доступной руды «" + w.ore.oreName + "» рядом нет.";
        if (standing(overLava) > 0) why += " " + CompanionWorkItems.blocks(standing(overLava)) + " руды над лавой — не трогал, добыча сгорит.";
        if (standing(lavaFalls) > 0) why += " У " + CompanionWorkItems.blocks(standing(lavaFalls)) + " рядом лавопад — пробкой его не остановить, не трогал.";
        if (standing(lackedPlugs) > 0) why += " У " + CompanionWorkItems.blocks(standing(lackedPlugs)) + " рядом лава, заткнуть нечем — нужен булыжник или земля.";
        if (standing(lackedSpringPlugs) > 0) why += " У " + CompanionWorkItems.blocks(standing(lackedSpringPlugs)) + " рядом родник, заткнуть нечем — нужен булыжник или земля.";
        if (w.oreUnreachable > 0) why += " К " + w.oreUnreachable + " не нашёл безопасного хода: не глубже " + CompanionOre.MAX_DROP
                + " блоков, без гравия, песка, лавы и открытой воды рядом.";
        if (w.ironPrep) {
            String goal = w.goalOre != null ? "Для «" + w.goalOre.oreName + "» нужна железная кирка, а на неё нужно " : "Для «" + CompanionWork.itemName(w.ironTool) + "» нужно ";
            w.finish(CompanionActionResult.blocked(goal + w.ironNeeded + " железных слитков, и железа я не набрал." + why + below + bag)); return;
        }
        w.finish(false, "Добыл " + (lost ? CompanionWorkItems.blocks(w.oreMined) : w.oreMined + " из " + CompanionWorkItems.blocks(w.oreWanted)) + " " + got + why + below + bag);
    }
}

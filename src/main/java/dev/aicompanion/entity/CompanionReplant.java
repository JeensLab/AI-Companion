package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saplings back on the stumps of a tree he felled (idea RYVTB, #105), as a player replants: a sapling of the tree's wood from
 * his bag, on dirt or grass with two blocks of air over it, never by the player's buildings. A dark oak grows only from a
 * square of four, so it gets four or none; a giant spruce or jungle tree gets its square back from four, else one sapling.
 * No sapling of that wood in the bag: the stump stays bare. Mangroves and nether stems are not replanted.
 */
final class CompanionReplant {
    private static final Map<Block, Item> SAPLINGS = Map.of(
            Blocks.OAK_LOG, Items.OAK_SAPLING, Blocks.SPRUCE_LOG, Items.SPRUCE_SAPLING, Blocks.BIRCH_LOG, Items.BIRCH_SAPLING,
            Blocks.JUNGLE_LOG, Items.JUNGLE_SAPLING, Blocks.ACACIA_LOG, Items.ACACIA_SAPLING,
            Blocks.DARK_OAK_LOG, Items.DARK_OAK_SAPLING, Blocks.CHERRY_LOG, Items.CHERRY_SAPLING);
    /** How long the walk to one plot may take before its stumps are left bare; a fight or an open bag in between does not count. */
    private static final int PATIENCE = 20 * 15;
    /** The ground under the stump is worked from a cell beside it, not from the stump cell itself; a square from nearer, to reach all four. */
    private static final CompanionRoute.Need GROUND = new CompanionRoute.Need(3.5, false, true, false), SQUARE = new CompanionRoute.Need(2.2, false, true, false);

    /** The stumps of one tree planted together: one, or the four of a square trunk. */
    private record Plot(List<BlockPos> cells, Item sapling) { }

    private final CompanionEntity c;
    private final Deque<Plot> plots = new ArrayDeque<>();
    /** The cells of the first plot that get a sapling. */
    private final List<BlockPos> planting = new ArrayList<>();
    private Path path;
    private int walked, planted;

    CompanionReplant(CompanionEntity c) { this.c = c; }

    /** A new request: nothing to plant yet, nothing planted. */
    void reset() { plots.clear(); planting.clear(); path = null; planted = 0; }

    boolean pending() { return !plots.isEmpty(); }

    /** A stump cell waiting for its sapling: nothing of his goes there in the meantime. */
    boolean awaits(BlockPos pos) { return plots.stream().anyMatch(plot -> plot.cells().contains(pos)); }

    /** A tree about to be felled: its stumps, the logs standing on dirt or grass, are kept for a sapling once it is down. */
    void felling(Collection<BlockPos> logs) {
        if (!c.replantSaplings()) return;
        Map<BlockPos, Item> stumps = new HashMap<>();
        for (BlockPos log : logs) {
            Item sapling = SAPLINGS.get(c.level().getBlockState(log).getBlock());
            if (sapling != null && c.level().getBlockState(log.below()).is(BlockTags.DIRT)) stumps.put(log, sapling);
        }
        Set<BlockPos> taken = new HashSet<>();
        // Sorted by x, then z: a square trunk is met at its north-west corner first.
        for (BlockPos stump : stumps.keySet().stream().sorted(Comparator.<BlockPos>comparingInt(pos -> pos.getX()).thenComparingInt(pos -> pos.getZ())).toList()) {
            if (taken.contains(stump)) continue;
            Item sapling = stumps.get(stump);
            List<BlockPos> square = List.of(stump, stump.east(), stump.south(), stump.south().east());
            List<BlockPos> cells = square;
            if (!square.stream().allMatch(cell -> stumps.get(cell) == sapling && !taken.contains(cell))) cells = List.of(stump);
            taken.addAll(cells);
            plots.add(new Plot(cells, sapling));
        }
    }

    /** One tick of planting: true while he is at it, false once nothing is left to plant. */
    boolean tick() {
        if (!(c.level() instanceof ServerLevel level)) { reset(); return false; }
        // Switched off on the way to a stump: that stump stays bare too.
        if (!c.replantSaplings()) { plots.clear(); planting.clear(); path = null; return false; }
        while (!plots.isEmpty()) {
            if (planting.isEmpty() && !choose(level, plots.peekFirst())) { plots.removeFirst(); continue; }
            plant(level);
            return true;
        }
        return false;
    }

    /** What the felled tree gets now: the whole square from four saplings, else one (never one for a dark oak), else nothing. */
    private boolean choose(ServerLevel level, Plot plot) {
        List<BlockPos> free = plot.cells().stream().filter(cell -> fits(level, cell, plot.sapling())).toList();
        int have = c.work.items.countCarried(plot.sapling());
        String skipped = null;
        if (plot.cells().size() == 4 && free.size() == 4 && have >= 4) planting.addAll(free);
        // A trunk on a slope stands on ground of two heights: four saplings there are no square and grow nothing.
        else if (plot.sapling() == Items.DARK_OAK_SAPLING) skipped = plot.cells().size() == 4 ? "dark oak needs four" : "no level square";
        else if (free.isEmpty()) skipped = "no room";
        else if (have == 0) skipped = "no sapling";
        else planting.add(free.get(0));
        if (skipped != null) dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "replant_skipped", "entity", c.getUUID(),
                "stump", plot.cells().get(0), "sapling", BuiltInRegistries.ITEM.getKey(plot.sapling()), "have", have, "reason", skipped);
        walked = 0; path = null;
        return skipped == null;
    }

    private void plant(ServerLevel level) {
        Plot plot = plots.peekFirst();
        BlockPos first = planting.get(0);
        c.getLookControl().setLookAt(first.getX() + .5D, first.getY() + .2D, first.getZ() + .5D);
        if (planting.stream().anyMatch(cell -> c.distanceToSqr(cell.getCenter()) > CompanionEntity.LOG_INTERACTION_RANGE_SQR)
                || !CompanionMiningNavigation.visible(c, c.getEyePosition(), first.below())) {
            if (++walked > PATIENCE) { leave(plot, "no way"); return; }
            if (path == null || c.work.route.done() || c.work.route.stuck()) {
                var reach = c.work.route.to(first.below(), planting.size() == 1 ? GROUND : SQUARE);
                if (reach.busy()) return;
                if (!reach.found() || reach.path() == null) { leave(plot, "no way"); return; }
                path = reach.path();
            }
            c.work.route.walk(path);
            c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, "Иду посадить саженец на место дерева.");
            return;
        }
        c.work.route.stop();
        // A square goes down whole in one go or not at all (review 09.11: one by one, a fight or a cancel between left a dark oak
        // of one to three saplings that never grows). All cells and all saplings are checked before the first goes in.
        if (!planting.stream().allMatch(cell -> fits(level, cell, plot.sapling()))) { leave(plot, "no room"); return; }
        if (c.work.items.countCarried(plot.sapling()) < planting.size()) { leave(plot, "no sapling"); return; }
        BlockState sapling = Block.byItem(plot.sapling()).defaultBlockState();
        var sound = sapling.getSoundType();
        ItemStack shown = ItemStack.EMPTY;
        for (BlockPos cell : planting) {
            shown = c.work.items.takeCarried(plot.sapling(), 1);
            level.setBlock(cell, sapling, 3);
            level.playSound(null, cell, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, cell, GameEvent.Context.of(c, sapling));
            dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "sapling_planted", "entity", c.getUUID(),
                    "pos", cell, "sapling", BuiltInRegistries.ITEM.getKey(plot.sapling()));
            planted++;
        }
        c.animateAction(1, first, shown);
        c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, planting.size() == 1 ? "Посадил саженец на место дерева." : "Посадил саженцы на место дерева.");
        planting.clear(); plots.removeFirst(); path = null;
    }

    /** The rest of this plot stays bare: told in the journal, and the next plot is looked at. */
    private void leave(Plot plot, String reason) {
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "replant_skipped", "entity", c.getUUID(),
                "stump", planting.get(0), "sapling", BuiltInRegistries.ITEM.getKey(plot.sapling()), "reason", reason);
        c.work.route.stop();
        planting.clear(); plots.removeFirst(); path = null;
    }

    /** A stump cell a sapling can go on: air with air over it, ground it lives on, and no building of the player near. */
    private boolean fits(ServerLevel level, BlockPos cell, Item sapling) {
        if (!level.getBlockState(cell).isAir() || !level.getBlockState(cell.above()).isAir()
                || !Block.byItem(sapling).defaultBlockState().canSurvive(level, cell)) return false;
        // A tree by a building would grow into it, a house he built himself included. Only his own bench, set by the trunk for an
        // axe, is let be (review 09.11).
        for (BlockPos near : BlockPos.betweenClosed(cell.offset(-2, -1, -2), cell.offset(2, 2, 2))) {
            if (!level.hasChunkAt(near)) continue;
            BlockState state = level.getBlockState(near);
            if (CompanionBuildings.crafted(state) && !(state.is(Blocks.CRAFTING_TABLE) && c.placedByMe(near))) return false;
        }
        return true;
    }

    /** The end of the request's report: how many stumps got a sapling, or nothing. */
    String report() {
        if (planted == 0) return "";
        int last = planted % 10, tens = planted % 100;
        String word = last == 1 && tens != 11 ? "саженец" : last >= 2 && last <= 4 && (tens < 12 || tens > 14) ? "саженца" : "саженцев";
        return " На месте срубленных деревьев посадил " + planted + " " + word + ".";
    }
}

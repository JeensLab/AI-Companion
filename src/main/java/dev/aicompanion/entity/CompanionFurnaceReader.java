package dev.aicompanion.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

import java.util.List;

/** Reading the furnaces of loaded lots, shared by the work standing at them and by a parked batch (CompanionFurnaceBatches). */
final class CompanionFurnaceReader {
    private CompanionFurnaceReader() { }

    static AbstractFurnaceBlockEntity oven(ServerLevel level, CompanionWork.Lot lot) {
        return level.hasChunkAt(lot.pos) && level.getBlockEntity(lot.pos) instanceof FurnaceBlockEntity oven ? oven : null;
    }

    /** No oven because nobody has loaded the chunk is no oven that is gone: only a loaded chunk without the furnace says that. */
    static boolean gone(ServerLevel level, CompanionWork.Lot lot) { return level.hasChunkAt(lot.pos) && oven(level, lot) == null; }

    static boolean ticking(ServerLevel level, CompanionWork.Lot lot) {
        long chunk = ChunkPos.asLong(lot.pos);
        return level.shouldTickBlocksAt(chunk) && level.areEntitiesLoaded(chunk);
    }

    /** Reads a loaded furnace: new product counts as progress. Ticks in which the furnace could not run don't count towards a stall. */
    static void watch(ServerLevel level, CompanionSmelting.Recipe recipe, CompanionWork.Lot lot) {
        if (!ticking(level, lot)) { lot.progressTick = level.getGameTime(); return; }
        int ready = product(recipe, lot, oven(level, lot)) / recipe.result().getCount();
        if (ready > lot.ready) { lot.ready = ready; lot.progressTick = level.getGameTime(); }
    }

    static int left(CompanionSmelting.Recipe recipe, AbstractFurnaceBlockEntity oven) { return oven.getItem(0).is(recipe.input()) ? oven.getItem(0).getCount() : 0; }
    static boolean lit(ServerLevel level, CompanionWork.Lot lot) { return level.getBlockState(lot.pos).getValue(AbstractFurnaceBlock.LIT); }
    static boolean stalled(ServerLevel level, CompanionSmelting.Recipe recipe, CompanionWork.Lot lot) { return level.getGameTime() - lot.progressTick > recipe.cookTicks() * 2L + 20 * 30; }

    /** Done, out of fuel or stuck: the furnace needs him standing at it. */
    static boolean needsHands(ServerLevel level, CompanionSmelting.Recipe recipe, CompanionWork.Lot lot) {
        AbstractFurnaceBlockEntity oven = oven(level, lot);
        if (oven == null) return true; // not loaded: only walking there tells
        int left = left(recipe, oven);
        return left == 0 || lot.ready >= lot.count || stalled(level, recipe, lot) || !lit(level, lot) && oven.getItem(1).isEmpty();
    }

    /** Our product: what the output slot gained since loading. */
    static int product(CompanionSmelting.Recipe recipe, CompanionWork.Lot lot, AbstractFurnaceBlockEntity oven) {
        ItemStack out = oven.getItem(2);
        return ItemStack.isSameItemSameComponents(out, recipe.result()) ? Math.max(0, out.getCount() - lot.outputBefore) : 0;
    }

    /** Where the raw material and fuel of the lots still stand, for a result that leaves them in the furnaces. */
    static String note(List<CompanionWork.Lot> lots) {
        List<String> open = lots.stream().filter(lot -> lot.loaded && !lot.emptied).map(lot -> lot.pos.toShortString()).toList();
        return open.isEmpty() ? "" : " Сырьё и топливо остались в " + (open.size() == 1 ? "печи на " + open.getFirst() : "печах на " + String.join("; ", open))
                + " — переплавленное можно забрать оттуда.";
    }
}

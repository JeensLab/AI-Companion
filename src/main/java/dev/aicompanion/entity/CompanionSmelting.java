package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Furnace facts from the world's own smelting recipes and fuel values: what goes in, what comes out, what is worth burning. */
final class CompanionSmelting {
    /** One input item gives result (usually one item) after cookTicks of burning. */
    record Recipe(Item input, ItemStack result, int cookTicks) { }
    record Fuel(Item item, int count) { }

    private CompanionSmelting() { }

    /**
     * The model may name the raw material ("raw_iron") or the product ("iron_ingot"). A carried raw material wins;
     * then a carried item that smelts into the named product; then the named item as raw material that is not carried.
     */
    static Recipe resolve(ServerLevel level, String requested, List<ItemStack> carried) {
        // «Пожарь мясо» after a hunt of any game: the raw meat he carries most of (one kind per furnace load).
        if (CompanionGame.ANY_MEAT.equals(requested)) {
            Recipe most = null;
            int mostCount = 0;
            for (ItemStack stack : carried) {
                if (stack.isEmpty() || !stack.is(net.minecraft.tags.ItemTags.MEAT)) continue;
                Recipe recipe = recipeFor(level, stack);
                int available = count(carried, stack.getItem());
                if (recipe != null && available > mostCount) { most = recipe; mostCount = available; }
            }
            return most;
        }
        var id = ResourceLocation.tryParse(requested == null ? "" : requested.trim());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        Item named = BuiltInRegistries.ITEM.get(id);
        Recipe direct = recipeFor(level, new ItemStack(named));
        if (direct != null && count(carried, named) > 0) return direct;
        Recipe best = null;
        int bestCount = 0;
        for (ItemStack stack : carried) {
            if (stack.isEmpty() || stack.is(named)) continue;
            Recipe recipe = recipeFor(level, stack);
            if (recipe == null || !recipe.result().is(named)) continue;
            int available = count(carried, stack.getItem());
            if (available > bestCount) { best = recipe; bestCount = available; }
        }
        return best != null ? best : direct;
    }

    static Recipe recipeFor(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) return null;
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack.copyWithCount(1)), level)
                .map(holder -> new Recipe(stack.getItem(), holder.value().getResultItem(level.registryAccess()).copy(), holder.value().getCookingTime()))
                .filter(recipe -> !recipe.result().isEmpty() && recipe.cookTicks() > 0).orElse(null);
    }

    static int burnTicks(ItemStack stack) { return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING); }

    /** What a player would put under the furnace, best first. Tools, benches, chests and saplings are never burnt. */
    static int fuelRank(ItemStack stack) {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 0;
        if (stack.is(ItemTags.PLANKS)) return 1;
        if (stack.is(ItemTags.LOGS)) return 2;
        if (stack.is(Items.STICK)) return 3;
        if (stack.is(Items.COAL_BLOCK)) return 4;
        return -1;
    }

    /**
     * One fuel kind for the fuel slot: the best kind that alone covers needTicks, otherwise the one that burns longest
     * (the rest is added once the slot is empty). reserved items are the raw material itself, e.g. logs for charcoal.
     * A slot holding someone's other fuel only takes more of the same kind.
     */
    static Fuel chooseFuel(List<ItemStack> carried, Item reserved, int reservedCount, int needTicks, ItemStack slot) {
        if (needTicks <= 0) return null;
        Map<Item, Integer> available = new LinkedHashMap<>();
        for (ItemStack stack : carried) if (!stack.isEmpty() && fuelRank(stack) >= 0 && burnTicks(stack) > 0) available.merge(stack.getItem(), stack.getCount(), Integer::sum);
        if (reserved != null) available.computeIfPresent(reserved, (item, count) -> count > reservedCount ? count - reservedCount : null);
        Fuel best = null;
        long bestTicks = 0;
        int bestRank = Integer.MAX_VALUE;
        for (var entry : available.entrySet()) {
            ItemStack sample = new ItemStack(entry.getKey());
            if (!slot.isEmpty() && !slot.is(entry.getKey())) continue;
            int burn = burnTicks(sample);
            int room = slot.isEmpty() ? sample.getMaxStackSize() : slot.getMaxStackSize() - slot.getCount();
            int count = Math.min(Math.min(entry.getValue(), room), (needTicks + burn - 1) / burn);
            if (count <= 0) continue;
            long ticks = (long) count * burn;
            int rank = fuelRank(sample);
            boolean enough = ticks >= needTicks, bestEnough = bestTicks >= needTicks;
            if (best == null || enough && !bestEnough || enough == bestEnough && (enough ? rank < bestRank : ticks > bestTicks)) {
                best = new Fuel(entry.getKey(), count); bestTicks = ticks; bestRank = rank;
            }
        }
        return best;
    }

    /** Logs to chop when nothing burnable is carried: a log burns for one and a half items. */
    static int logsFor(int needTicks) { return Math.max(2, Math.min(16, (needTicks + 299) / 300)); }

    static int count(List<ItemStack> carried, Item item) {
        int result = 0;
        for (ItemStack stack : carried) if (stack.is(item)) result += stack.getCount();
        return result;
    }
}

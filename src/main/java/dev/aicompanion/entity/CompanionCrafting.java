package dev.aicompanion.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Plans against a detached bag. No ingredients or world blocks change until the entire craft is possible. */
public final class CompanionCrafting {
    private static final int MAX_SEARCH = 8000;
    private final ServerLevel level;
    private final Set<Item> active = new HashSet<>();
    private final List<String> steps = new ArrayList<>();
    private int searches;
    private boolean needsTable;
    private boolean inventoryFull;
    /** The stack the bag had no cell for: what the player has to make room for. */
    private Item blockedItem;
    /** The thing the chain ran out of at its deepest step: what the player has to bring. */
    private Item missingItem;
    /** He stands at a stonecutter: its recipes are tried first (stairs one for one, not six for four). */
    private final boolean cutterNearby;
    private boolean usedCutter;

    private CompanionCrafting(ServerLevel level, boolean cutterNearby) { this.level = level; this.cutterNearby = cutterNearby; }

    /** cutter: some step was cut at the stonecutter, so the gesture and the sound are made there. */
    public record Plan(boolean success, String message, NonNullList<ItemStack> inventory, boolean placeTable, boolean shortage, boolean cutter) {
        public Plan(boolean success, String message, NonNullList<ItemStack> inventory, boolean placeTable) { this(success, message, inventory, placeTable, false, false); }
        public Plan(boolean success, String message, NonNullList<ItemStack> inventory, boolean placeTable, boolean shortage) { this(success, message, inventory, placeTable, shortage, false); }
    }

    public static Plan plan(ServerLevel level, List<ItemStack> inventory, String requestedId, int count, boolean tableNearby) {
        return plan(level, inventory, requestedId, count, tableNearby, false);
    }

    public static Plan plan(ServerLevel level, List<ItemStack> inventory, String requestedId, int count, boolean tableNearby, boolean cutterNearby) {
        ResourceLocation id = ResourceLocation.tryParse(requestedId);
        Item target = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        NonNullList<ItemStack> bag = copy(inventory);
        if (target == null || target == Items.AIR) return new Plan(false, "Неизвестный предмет: " + requestedId + ".", bag, false);
        if (count < 1 || count > 64) return new Plan(false, "За раз можно сделать от 1 до 64 предметов.", bag, false);
        CompanionCrafting planner = new CompanionCrafting(level, cutterNearby);
        int before = count(bag, target);
        // Two half stacks of one thing take two cells: merging them is what a player does before crafting, and it gives the
        // chain the one cell it needs when the bag is otherwise full (the owner's five-step plan, 163 things carried).
        mergeStacks(bag);
        // A bench made and set down before the chain: its cell is free again for the planks and the product, while a full bag
        // has no second cell to spare for a bench made after them. Nothing to make, nothing to set down.
        boolean benchFirst = count(bag, target) < before + count && !tableNearby && needsBench(level, target)
                && !(cutterNearby && cuttable(level, target));
        if (benchFirst && !planner.craftBench(bag))
            return new Plan(false, planner.failureMessage(target, true), copy(inventory), false, planner.shortage());
        if (!planner.ensure(bag, target, before + count))
            return new Plan(false, planner.failureMessage(target, false), copy(inventory), false, planner.shortage());
        int made = count(bag, target) - before;
        boolean placeTable = benchFirst || !tableNearby && planner.needsTable;
        if (!benchFirst && placeTable && (!planner.ensure(bag, Items.CRAFTING_TABLE, 1) || !take(bag, Items.CRAFTING_TABLE)))
            return new Plan(false, planner.failureMessage(target, true), copy(inventory), false, planner.shortage());
        // The same step in a row (four stairs cut one by one) is said once with the counts added; a cut step says where it was cut.
        String chain = String.join("; ", merged(planner.steps));
        return new Plan(true, (planner.usedCutter ? "Нарезал на камнерезе " : "Скрафтил ") + made + " «" + target.getDescription().getString() + "»."
                + (chain.isEmpty() ? "" : " Цепочка: " + chain + "."), bag, placeTable, false, planner.usedCutter);
    }

    /** A bench made and taken out of the bag before the chain: the cell it was made in is free again for what follows. */
    private boolean craftBench(NonNullList<ItemStack> bag) {
        return ensure(bag, Items.CRAFTING_TABLE, 1) && take(bag, Items.CRAFTING_TABLE);
    }

    /** The requested item's own recipes all need the 3×3 grid, so a bench has to be made before its chain, not after it. */
    public static boolean needsBench(ServerLevel level, Item target) {
        boolean any = false;
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            if (recipe.getResultItem(level.registryAccess()).getItem() != target) continue;
            any = true;
            if (recipe.canCraftInDimensions(2, 2)) return false;
        }
        return any;
    }

    /** Whether the stonecutter has a recipe for the item: it is cut, not crafted, so no bench is made before its chain. */
    private static boolean cuttable(ServerLevel level, Item target) {
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING))
            if (holder.value().getResultItem(level.registryAccess()).getItem() == target) return true;
        return false;
    }

    /**
     * Everything the chain for an item is made of, the item itself too — what a hand must not take out of the bag while
     * the plan reads the bag (the torch chain's coal, the chest chain's logs and planks). The scan is bounded: a chain
     * that loops over more than a bagful of kinds has no single stack to spare anyway.
     */
    public static Set<Item> chainMaterials(ServerLevel level, Item target) {
        Set<Item> materials = new HashSet<>();
        java.util.Deque<Item> queue = new java.util.ArrayDeque<>();
        materials.add(target); queue.add(target);
        while (!queue.isEmpty() && materials.size() < 64) {
            Item item = queue.poll();
            for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe recipe = holder.value();
                if (recipe.getResultItem(level.registryAccess()).getItem() != item) continue;
                for (Ingredient ingredient : recipe.getIngredients())
                    for (ItemStack option : ingredient.getItems())
                        if (materials.add(option.getItem())) queue.add(option.getItem());
            }
        }
        return materials;
    }

    /**
     * What the player hears when the craft did not happen: the thing that was in the way, in plain words — the cell the bag
     * had none of, or the material the chain ran out of — never a «chain» that says nothing about what to bring or free.
     */
    private String failureMessage(Item target, boolean bench) {
        String name = "«" + target.getDescription().getString() + "»";
        String need = bench ? ": для него нужен верстак, а " : ": ";
        if (searches > MAX_SEARCH)
            return "Не могу сделать " + name + ": цепочка рецептов слишком сложная. Попроси сначала сделать промежуточные материалы; сумка не изменена.";
        if (inventoryFull && blockedItem != null)
            return "Не могу сделать " + name + need + "в сумке нет места, чтобы положить «" + blockedItem.getDescription().getString()
                    + "» — нужна одна свободная ячейка. Освободи ячейку или сложи вещи в сундук; материалы сохранены.";
        if (missingItem != null)
            return "Не могу сделать " + name + need + "не хватает «" + missingItem.getDescription().getString() + "». Материалы сохранены.";
        return "Не могу сделать " + name + need + "в сумке не хватает подходящих материалов. Материалы сохранены.";
    }

    /** False when the bag itself was too full; true when materials (or a chain too long) were missing. */
    private boolean shortage() { return !inventoryFull && searches <= MAX_SEARCH; }

    private boolean ensure(NonNullList<ItemStack> bag, Item target, int wanted) {
        if (count(bag, target) >= wanted) return true;
        if (++searches > MAX_SEARCH || active.size() >= 12 || !active.add(target)) return false;
        try {
            while (count(bag, target) < wanted) {
                int before = count(bag, target);
                boolean crafted = cutterNearby && cut(bag, target);
                if (!crafted) for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                    CraftingRecipe recipe = holder.value();
                    if (recipe.getResultItem(level.registryAccess()).getItem() != target) continue;
                    NonNullList<ItemStack> trial = copy(bag);
                    int checkpoint = steps.size();
                    boolean previousTable = needsTable, previousCutter = usedCutter;
                    List<Ingredient> ingredients = recipe.getIngredients();
                    if (ingredients.isEmpty() || ingredients.size() > 9) continue;
                    ItemStack[] grid = new ItemStack[ingredients.size()];
                    java.util.Arrays.fill(grid, ItemStack.EMPTY);
                    if (allocate(recipe, ingredients, 0, grid, trial) && count(trial, target) > before) {
                        replace(bag, trial);
                        needsTable |= !recipe.canCraftInDimensions(2, 2);
                        crafted = true;
                        break;
                    }
                    rollback(checkpoint, previousTable, previousCutter);
                    if (searches > MAX_SEARCH) break;
                }
                if (!crafted) {
                    // The deepest thing the chain could not get: what the player has to bring.
                    if (missingItem == null) missingItem = target;
                    return false;
                }
            }
            return true;
        } finally {
            active.remove(target);
        }
    }

    private boolean allocate(CraftingRecipe recipe, List<Ingredient> ingredients, int index,
                             ItemStack[] grid, NonNullList<ItemStack> bag) {
        if (++searches > MAX_SEARCH) return false;
        if (index == ingredients.size()) {
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : ingredients.size();
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 1;
            if (width > 3 && !(recipe instanceof ShapedRecipe)) { width = 3; height = (ingredients.size() + 2) / 3; }
            List<ItemStack> items = new ArrayList<>(List.of(grid));
            while (items.size() < width * height) items.add(ItemStack.EMPTY);
            CraftingInput input = CraftingInput.of(width, height, items);
            if (!recipe.matches(input, level)) return false;
            ItemStack output = recipe.assemble(input, level.registryAccess());
            if (output.isEmpty()) return false;
            if (!store(bag, output.copy())) { inventoryFull = true; if (blockedItem == null) blockedItem = output.getItem(); return false; }
            for (ItemStack remainder : recipe.getRemainingItems(input)) {
                if (!remainder.isEmpty() && !store(bag, remainder.copy())) { inventoryFull = true; if (blockedItem == null) blockedItem = remainder.getItem(); return false; }
            }
            steps.add(output.getCount() + " " + output.getHoverName().getString());
            return true;
        }
        Ingredient ingredient = ingredients.get(index);
        if (ingredient.isEmpty()) return allocate(recipe, ingredients, index + 1, grid, bag);

        // Preserve components of actual inventory stacks; prefer existing materials over crafting replacements.
        List<ItemStack> choices = new ArrayList<>();
        for (ItemStack stack : bag) if (!stack.isEmpty() && ingredient.test(stack)
                && choices.stream().noneMatch(choice -> ItemStack.isSameItemSameComponents(choice, stack))) {
            choices.add(stack.copyWithCount(1));
        }
        for (ItemStack option : ingredient.getItems()) if (choices.stream().noneMatch(choice -> choice.is(option.getItem()))) {
            choices.add(option.copyWithCount(1));
        }
        choices.sort(Comparator.comparingInt(choice -> count(bag, choice.getItem()) > 0 ? 0 : 1));
        for (ItemStack choice : choices) {
            NonNullList<ItemStack> trial = copy(bag);
            int checkpoint = steps.size();
            boolean previousTable = needsTable, previousCutter = usedCutter;
            if (ensure(trial, choice.getItem(), 1)) {
                ItemStack actual = takeMatching(trial, ingredient, choice.getItem());
                if (!actual.isEmpty()) {
                    grid[index] = actual;
                    if (allocate(recipe, ingredients, index + 1, grid, trial)) {
                        replace(bag, trial);
                        return true;
                    }
                }
            }
            grid[index] = ItemStack.EMPTY;
            rollback(checkpoint, previousTable, previousCutter);
            if (searches > MAX_SEARCH) break;
        }
        return false;
    }

    /** «1 X на камнерезе» four times over reads as «4 X на камнерезе»: the same step in a row is said once with the counts added. */
    private static List<String> merged(List<String> steps) {
        List<String> result = new ArrayList<>();
        for (String step : steps) {
            int last = result.size() - 1, space = step.indexOf(' ');
            String what = step.substring(space + 1);
            if (last >= 0 && result.get(last).substring(result.get(last).indexOf(' ') + 1).equals(what) && space > 0) {
                String previous = result.get(last);
                result.set(last, (Integer.parseInt(previous.substring(0, previous.indexOf(' '))) + Integer.parseInt(step.substring(0, space))) + " " + what);
            } else result.add(step);
        }
        return result;
    }

    /** At the stonecutter: one block in, the recipe's count out. Never costs more than the bench, often less (stairs). */
    private boolean cut(NonNullList<ItemStack> bag, Item target) {
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.STONECUTTING)) {
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.getItem() != target) continue;
            Ingredient ingredient = holder.value().getIngredients().getFirst();
            for (ItemStack option : ingredient.getItems()) {
                NonNullList<ItemStack> trial = copy(bag);
                int checkpoint = steps.size();
                boolean previousTable = needsTable, previousCutter = usedCutter;
                if (ensure(trial, option.getItem(), 1) && !takeMatching(trial, ingredient, option.getItem()).isEmpty()) {
                    if (store(trial, output.copy())) {
                        replace(bag, trial);
                        usedCutter = true;
                        steps.add(output.getCount() + " " + output.getHoverName().getString() + " на камнерезе");
                        return true;
                    }
                    inventoryFull = true; if (blockedItem == null) blockedItem = output.getItem();
                }
                rollback(checkpoint, previousTable, previousCutter);
                if (searches > MAX_SEARCH) return false;
            }
        }
        return false;
    }

    private void rollback(int checkpoint, boolean previousTable, boolean previousCutter) {
        while (steps.size() > checkpoint) steps.removeLast();
        needsTable = previousTable; usedCutter = previousCutter;
    }

    private static ItemStack takeMatching(List<ItemStack> bag, Ingredient ingredient, Item item) {
        for (ItemStack stack : bag) if (!stack.isEmpty() && stack.is(item) && ingredient.test(stack)) return stack.split(1);
        return ItemStack.EMPTY;
    }

    private static boolean take(List<ItemStack> bag, Item item) {
        for (ItemStack stack : bag) if (stack.is(item) && !stack.isEmpty()) { stack.shrink(1); return true; }
        return false;
    }

    private static int count(List<ItemStack> bag, Item item) {
        return bag.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean store(List<ItemStack> bag, ItemStack offered) {
        for (ItemStack stack : bag) if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, offered)) {
            int moved = Math.min(offered.getCount(), stack.getMaxStackSize() - stack.getCount());
            if (moved > 0) { stack.grow(moved); offered.shrink(moved); }
            if (offered.isEmpty()) return true;
        }
        for (int slot = 0; slot < bag.size(); slot++) if (bag.get(slot).isEmpty()) {
            int moved = Math.min(offered.getCount(), offered.getMaxStackSize());
            bag.set(slot, offered.copyWithCount(moved));
            offered.shrink(moved);
            if (offered.isEmpty()) return true;
        }
        return offered.isEmpty();
    }

    /** Half stacks of one thing into one cell: the same items and components, no item lost or made. */
    private static void mergeStacks(NonNullList<ItemStack> bag) {
        for (int slot = 0; slot < bag.size(); slot++) {
            ItemStack stack = bag.get(slot);
            if (stack.isEmpty() || stack.getCount() >= stack.getMaxStackSize()) continue;
            for (int other = slot + 1; other < bag.size() && stack.getCount() < stack.getMaxStackSize(); other++) {
                ItemStack candidate = bag.get(other);
                if (candidate.isEmpty() || !ItemStack.isSameItemSameComponents(stack, candidate)) continue;
                int moved = Math.min(candidate.getCount(), stack.getMaxStackSize() - stack.getCount());
                if (moved <= 0) continue;
                stack.grow(moved);
                candidate.shrink(moved);
                if (candidate.isEmpty()) bag.set(other, ItemStack.EMPTY);
            }
        }
    }

    private static NonNullList<ItemStack> copy(List<ItemStack> inventory) {
        NonNullList<ItemStack> result = NonNullList.withSize(inventory.size(), ItemStack.EMPTY);
        replace(result, inventory);
        return result;
    }

    private static void replace(List<ItemStack> destination, List<ItemStack> source) {
        for (int slot = 0; slot < source.size(); slot++) destination.set(slot, source.get(slot).copy());
    }
}

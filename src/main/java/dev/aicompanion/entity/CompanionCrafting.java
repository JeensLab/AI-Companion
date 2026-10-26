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

    private CompanionCrafting(ServerLevel level) { this.level = level; }

    public record Plan(boolean success, String message, NonNullList<ItemStack> inventory, boolean placeTable) { }

    public static Plan plan(ServerLevel level, List<ItemStack> inventory, String requestedId, int count, boolean tableNearby) {
        ResourceLocation id = ResourceLocation.tryParse(requestedId);
        Item target = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        NonNullList<ItemStack> bag = copy(inventory);
        if (target == null || target == Items.AIR) return new Plan(false, "Неизвестный предмет: " + requestedId + ".", bag, false);
        if (count < 1 || count > 64) return new Plan(false, "За раз можно сделать от 1 до 64 предметов.", bag, false);
        CompanionCrafting planner = new CompanionCrafting(level);
        int before = count(bag, target);
        if (!planner.ensure(bag, target, before + count)) {
            if (planner.inventoryFull) return new Plan(false, "В моей сумке нет места для всей цепочки крафта. Освободи несколько ячеек; материалы сохранены.", copy(inventory), false);
            if (planner.searches > MAX_SEARCH) return new Plan(false, "Цепочка рецептов слишком сложная. Попроси сначала сделать промежуточные материалы; сумка не изменена.", copy(inventory), false);
            return new Plan(false, "Не могу сделать " + count + " «" + target.getDescription().getString()
                    + "»: в моей сумке недостаточно подходящих материалов для полной цепочки крафта. Материалы сохранены.", copy(inventory), false);
        }
        int made = count(bag, target) - before;
        boolean placeTable = planner.needsTable && !tableNearby;
        if (placeTable && (!planner.ensure(bag, Items.CRAFTING_TABLE, 1) || !take(bag, Items.CRAFTING_TABLE))) {
            return new Plan(false, "Для этого нужен верстак. Не хватает материалов, чтобы сделать и верстак, и запрошенный предмет. Материалы сохранены.", copy(inventory), false);
        }
        String chain = String.join("; ", planner.steps);
        return new Plan(true, "Скрафтил " + made + " «" + target.getDescription().getString() + "»."
                + (chain.isEmpty() ? "" : " Цепочка: " + chain + "."), bag, placeTable);
    }

    private boolean ensure(NonNullList<ItemStack> bag, Item target, int wanted) {
        if (count(bag, target) >= wanted) return true;
        if (++searches > MAX_SEARCH || active.size() >= 12 || !active.add(target)) return false;
        try {
            while (count(bag, target) < wanted) {
                int before = count(bag, target);
                boolean crafted = false;
                for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                    CraftingRecipe recipe = holder.value();
                    if (recipe.getResultItem(level.registryAccess()).getItem() != target) continue;
                    NonNullList<ItemStack> trial = copy(bag);
                    int checkpoint = steps.size();
                    boolean previousTable = needsTable;
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
                    rollback(checkpoint, previousTable);
                    if (searches > MAX_SEARCH) break;
                }
                if (!crafted) return false;
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
            if (!store(bag, output.copy())) { inventoryFull = true; return false; }
            for (ItemStack remainder : recipe.getRemainingItems(input)) {
                if (!remainder.isEmpty() && !store(bag, remainder.copy())) { inventoryFull = true; return false; }
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
            boolean previousTable = needsTable;
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
            rollback(checkpoint, previousTable);
            if (searches > MAX_SEARCH) break;
        }
        return false;
    }

    private void rollback(int checkpoint, boolean previousTable) {
        while (steps.size() > checkpoint) steps.removeLast();
        needsTable = previousTable;
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

    private static NonNullList<ItemStack> copy(List<ItemStack> inventory) {
        NonNullList<ItemStack> result = NonNullList.withSize(inventory.size(), ItemStack.EMPTY);
        replace(result, inventory);
        return result;
    }

    private static void replace(List<ItemStack> destination, List<ItemStack> source) {
        for (int slot = 0; slot < source.size(); slot++) destination.set(slot, source.get(slot).copy());
    }
}

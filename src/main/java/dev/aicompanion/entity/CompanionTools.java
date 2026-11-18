package dev.aicompanion.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CompanionTools {
    private CompanionTools() { }
    static int count(CompanionEntity owner, TagKey<Item> tag) {
        int total = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(tag)) total += owner.getItem(slot).getCount();
        if (owner.getMainHandItem().is(tag)) total += owner.getMainHandItem().getCount();
        if (owner.getOffhandItem().is(tag)) total += owner.getOffhandItem().getCount();
        return total;
    }
    static int soil(CompanionEntity owner) {
        int count = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (soilItem(owner.getItem(slot))) count += owner.getItem(slot).getCount();
        if (soilItem(owner.getMainHandItem())) count += owner.getMainHandItem().getCount();
        if (soilItem(owner.getOffhandItem())) count += owner.getOffhandItem().getCount();
        return count;
    }
    static boolean soilItem(ItemStack stack) { return stack.is(net.minecraft.world.item.Items.DIRT) || stack.is(net.minecraft.world.item.Items.COARSE_DIRT) || stack.is(net.minecraft.world.item.Items.ROOTED_DIRT); }
    /**
     * What a block he breaks gives, as for a player (issue 65): a block that needs the right tool gives nothing without it, stone by
     * hand no cobblestone. The loot table does not check that; for a player the game checks it before the drops.
     */
    static List<ItemStack> drops(BlockState state, ServerLevel level, BlockPos pos, BlockEntity entity, Entity breaker, ItemStack tool) {
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) return List.of();
        return Block.getDrops(state, level, pos, entity, breaker, tool);
    }
    /**
     * The tool of his that breaks this block fastest — a pickaxe for stone, an axe for wood, a shovel for earth, shears for leaves
     * (issue #105, «ломает блок правильным инструментом») — from the bag or either hand; empty when a bare hand is as fast.
     * The class comes from the game's own destroy-speed rule (a tool speeds up only the blocks of its tag), not from a list of blocks;
     * a block that needs the right tool for its drop is left to a tool that gives it. What he carries for the player stays his.
     */
    static ItemStack bestToolForBlock(CompanionEntity owner, BlockState state) {
        // What only shears give (a leaf, a vine, grass, a cobweb) is cut with them: a faster axe on grass gives seeds where the
        // plant was wanted. The rule is the tool that gives the block, not only the quickest one.
        if (owner.level() instanceof ServerLevel level && CompanionPickable.shorn(level, state)) {
            ItemStack shears = shears(owner);
            if (!shears.isEmpty()) return shears;
        }
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = 1;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack stack = owner.getItem(slot);
            if (owner.carriedForPlayer(stack) || !takesTheDrop(stack, state)) continue;
            float speed = breakSpeed(owner, stack, state);
            if (better(stack, speed, best, bestSpeed)) { best = stack; bestSpeed = speed; }
        }
        for (ItemStack held : new ItemStack[]{owner.getMainHandItem(), owner.getOffhandItem()}) {
            if (!takesTheDrop(held, state)) continue;
            float speed = breakSpeed(owner, held, state);
            if (better(held, speed, best, bestSpeed)) { best = held; bestSpeed = speed; }
        }
        return best;
    }

    /**
     * The tool of his with Silk Touch that gives the block itself (a grass block, a coral block, a coral plant) — the fastest of
     * them; empty when he has none. Silk is not a speed but the drop: only a stack that carries it takes the block as it stands,
     * and a silk tool no faster than a bare hand on the block (shears or a wooden pickaxe on a coral plant) still gives it.
     */
    static ItemStack bestSilkToolForBlock(CompanionEntity owner, BlockState state) {
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = 1;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack stack = owner.getItem(slot);
            if (owner.carriedForPlayer(stack) || !silkTouch(owner, stack) || !takesTheDrop(stack, state)) continue;
            float speed = breakSpeed(owner, stack, state);
            if (betterSilk(stack, speed, best, bestSpeed)) { best = stack; bestSpeed = speed; }
        }
        for (ItemStack held : new ItemStack[]{owner.getMainHandItem(), owner.getOffhandItem()}) {
            if (owner.carriedForPlayer(held) || !silkTouch(owner, held) || !takesTheDrop(held, state)) continue;
            float speed = breakSpeed(owner, held, state);
            if (betterSilk(held, speed, best, bestSpeed)) { best = held; bestSpeed = speed; }
        }
        return best;
    }

    /** Whether the stack carries Silk Touch. */
    private static boolean silkTouch(CompanionEntity owner, ItemStack stack) {
        return !stack.isEmpty() && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                owner.level().registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), stack) > 0;
    }

    /**
     * The better of two stacks of tool for a block: the faster one, and at the same speed the one with more use left, so a fresh
     * tool in the bag wears instead of a nearly broken one.
     */
    private static boolean better(ItemStack stack, float speed, ItemStack best, float bestSpeed) {
        if (speed <= 1) return false;
        if (best.isEmpty()) return true;
        if (speed != bestSpeed) return speed > bestSpeed;
        return left(stack) > left(best);
    }

    /**
     * The better of two stacks carrying Silk Touch for a block: the faster one, and at the same speed the one with more use left.
     * A speed of one is no reason to pass a stack over here, as {@link #better} does for a tool: Silk Touch gives the block
     * itself whatever the speed, so a silk tool as slow as a hand on it is still the only one that gives it.
     */
    private static boolean betterSilk(ItemStack stack, float speed, ItemStack best, float bestSpeed) {
        if (best.isEmpty()) return true;
        if (speed != bestSpeed) return speed > bestSpeed;
        return left(stack) > left(best);
    }

    /** What is left of the tool before it breaks; a tool that does not wear is as good as a fresh one. */
    private static int left(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
    }

    /** The shears he carries, from the hand first; empty when he has none. */
    private static ItemStack shears(CompanionEntity owner) {
        if (owner.getMainHandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR)) return owner.getMainHandItem();
        for (int slot = 0; slot < owner.getContainerSize(); slot++)
            if (owner.getItem(slot).is(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR)) return owner.getItem(slot);
        if (owner.getOffhandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR)) return owner.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /**
     * The wear a tool takes on a block it broke, as a player's: none on a block that comes down at once — hardness zero, a crop,
     * a flower, a sapling, grass — which the game takes no tick off, so shears on grass stay as they were. The block is already
     * broken and its drops given; this is only the wear.
     */
    public static void wearOnBreak(ItemStack tool, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (state.getDestroySpeed(level, pos) == 0) return;
        tool.getItem().mineBlock(tool, level, state, pos, owner);
    }

    /**
     * Vanilla destroy speed with the tool's own Efficiency, as a player has it: the bonus only on the tool's own blocks. A block
     * that comes down on the first tick — hardness zero: a crop, a flower, a sapling — gains nothing from any speed, the game
     * takes no tick off it, so it is a bare hand's one to a tool as to a hand.
     */
    static float breakSpeed(CompanionEntity owner, ItemStack stack, BlockState state) {
        return breakSpeed(owner, stack, state, BlockPos.ZERO);
    }

    /** The same at the block's own position, for a hardness that depends on where it stands. */
    static float breakSpeed(CompanionEntity owner, ItemStack stack, BlockState state, BlockPos pos) {
        if (stack.isEmpty()) return 1;
        if (state.getDestroySpeed(owner.level(), pos) == 0) return 1;
        float speed = stack.getDestroySpeed(state);
        if (speed <= 1) return speed;
        int efficiency = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                owner.level().registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), stack);
        return efficiency > 0 ? speed + efficiency * efficiency + 1 : speed;
    }

    /**
     * A tool of another class may be faster and still give nothing: on obsidian an Efficiency V wooden pickaxe (speed 28) beats a
     * diamond one (8) and the block drops nothing. The game's own check keeps the tool that takes the drop.
     */
    private static boolean takesTheDrop(ItemStack stack, BlockState state) {
        return !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
    }

    /**
     * A tool in his hand that is no faster than a bare hand on the block he is about to break: a pickaxe held over earth, an axe
     * or a sword over a crop. A player puts it away rather than swings it in the way; what is not a tool (a block, food) stays in
     * the hand. The rule is the speed itself, not a list of the tags a tool speeds: whatever the block, a tool that does not speed
     * it up gives nothing and only swings.
     */
    static boolean wrongToolForBlock(CompanionEntity owner, ItemStack hand, BlockState state) {
        if (hand.isEmpty() || !isTool(hand)) return false;
        return breakSpeed(owner, hand, state) <= 1;
    }

    /** What a player swings at blocks and what wears on them: a tool of the four classes, shears, a sword. */
    private static boolean isTool(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.SWORDS) || stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR);
    }
    /** The better of two stacks of one tool class: the faster on this block, the one with more use left at the same speed. */
    private static boolean fresher(CompanionEntity owner, ItemStack candidate, ItemStack best, BlockState state) {
        if (best.isEmpty()) return true;
        // The effective speed, as the break itself uses it: a worn Efficiency tool is still faster than a fresh plain one.
        float mine = breakSpeed(owner, candidate, state), theirs = breakSpeed(owner, best, state);
        return mine != theirs ? mine > theirs : left(candidate) > left(best);
    }
    static boolean equip(CompanionEntity owner, TagKey<Item> tag, BlockState state) {
        ItemStack best = owner.getMainHandItem().is(tag) ? owner.getMainHandItem() : ItemStack.EMPTY;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack candidate = owner.getItem(slot);
            if (candidate.is(tag) && fresher(owner, candidate, best, state)) best = candidate;
        }
        if (owner.getOffhandItem().is(tag) && fresher(owner, owner.getOffhandItem(), best, state)) {
            String id = BuiltInRegistries.ITEM.getKey(owner.getOffhandItem().getItem()).toString();
            if (!owner.unequipForSupport("offhand").success()) return false;
            return owner.equipForWork(id).success();
        }
        if (best.isEmpty()) return false;
        return owner.equipForWork(BuiltInRegistries.ITEM.getKey(best.getItem()).toString()).success();
    }
    static boolean craft(CompanionEntity owner, String kind, BlockState state, TagKey<Item> tag) {
        if (count(owner, ItemTags.LOGS) == 0 && count(owner, ItemTags.PLANKS) == 0) return false;
        for (String material : new String[]{"stone", "wooden"}) {
            if (material.equals("stone") && count(owner, ItemTags.STONE_TOOL_MATERIALS) < (kind.equals("shovel") ? 1 : 3)) continue;
            String id = "minecraft:" + material + "_" + kind;
            if (owner.craftFromBag(id, 1).success()) return equip(owner, tag, state);
        }
        return false;
    }
}

package dev.aicompanion.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class CompanionTools {
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
     * The class comes from the game's own destroy-speed rule (a tool speeds up only the blocks of its tag), not from a list of blocks.
     */
    static ItemStack bestToolForBlock(CompanionEntity owner, BlockState state) {
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = 1;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack stack = owner.getItem(slot);
            float speed = breakSpeed(owner, stack, state);
            if (speed > bestSpeed) { best = stack; bestSpeed = speed; }
        }
        for (ItemStack held : new ItemStack[]{owner.getMainHandItem(), owner.getOffhandItem()}) {
            float speed = breakSpeed(owner, held, state);
            if (speed > bestSpeed) { best = held; bestSpeed = speed; }
        }
        // What a hand takes by chance only (grass, a fern, a dead bush) gives itself to shears: no faster, but the only way to it.
        if (best.isEmpty() && owner.level() instanceof ServerLevel level && CompanionPickable.shorn(level, state)) {
            ItemStack shears = shears(owner);
            if (!shears.isEmpty()) best = shears;
        }
        return best;
    }

    /** The shears he carries, from the hand first; empty when he has none. */
    private static ItemStack shears(CompanionEntity owner) {
        if (owner.getMainHandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR)) return owner.getMainHandItem();
        for (int slot = 0; slot < owner.getContainerSize(); slot++)
            if (owner.getItem(slot).is(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR)) return owner.getItem(slot);
        if (owner.getOffhandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR)) return owner.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** Vanilla destroy speed with the tool's own Efficiency, as a player has it: the bonus only on the tool's own blocks. */
    static float breakSpeed(CompanionEntity owner, ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return 1;
        float speed = stack.getDestroySpeed(state);
        if (speed <= 1) return speed;
        int efficiency = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                owner.level().registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), stack);
        return efficiency > 0 ? speed + efficiency * efficiency + 1 : speed;
    }
    static boolean equip(CompanionEntity owner, TagKey<Item> tag, BlockState state) {
        ItemStack best = owner.getMainHandItem().is(tag) ? owner.getMainHandItem() : ItemStack.EMPTY;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack candidate = owner.getItem(slot);
            if (candidate.is(tag) && (best.isEmpty() || candidate.getDestroySpeed(state) > best.getDestroySpeed(state))) best = candidate;
        }
        if (owner.getOffhandItem().is(tag) && (best.isEmpty() || owner.getOffhandItem().getDestroySpeed(state) > best.getDestroySpeed(state))) {
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

package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** The small 5×5 plank house: the species of its wood, its blueprint, and the interior he can be taken into once it stands. */
final class CompanionPlankHouse {
    static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped");

    private CompanionPlankHouse() { }

    static Block block(String wood, String suffix) { return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(wood + "_" + suffix)); }

    /** The species the bag holds most of (as planks, logs, slabs, stairs and doors); {@code current} when it holds none. */
    static String chooseWood(List<ItemStack> carried, String current) {
        String best = current;
        int most = -1;
        for (String type : WOODS) {
            int amount = 0;
            for (ItemStack stack : carried) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                if (id.equals(type + "_planks")) amount += stack.getCount();
                if (stack.is(ItemTags.LOGS) && (id.startsWith(type + "_") || id.startsWith("stripped_" + type + "_"))) amount += stack.getCount() * 4;
                if (id.equals(type + "_slab")) amount += stack.getCount() / 2;
                if (id.equals(type + "_stairs")) amount += stack.getCount();
                if (id.equals(type + "_door")) amount += stack.getCount() * 2;
            }
            if (amount > most) { most = amount; best = type; }
        }
        return best;
    }

    static List<CompanionHouse.Step> blueprint(BlockPos base, String wood) {
        List<CompanionHouse.Step> result = new ArrayList<>();
        BlockState planks = block(wood, "planks").defaultBlockState();
        for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++) result.add(new CompanionHouse.Step(base.offset(x, 0, z), planks, 0));
        result.add(new CompanionHouse.Step(base.offset(2, 0, -1), block(wood, "stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH), 0));
        for (int y = 1; y <= 3; y++) for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++) {
            if (x != 0 && x != 4 && z != 0 && z != 4) continue;
            if (z == 0 && x == 2 && y < 3) continue;
            if (y == 2 && (z == 4 && x == 2 || z == 2 && (x == 0 || x == 4))) continue;
            result.add(new CompanionHouse.Step(base.offset(x, y, z), planks, y));
        }
        // A shallow roof with a one-block overhang. Low enough to build from the ground/interior.
        for (int z = -1; z <= 5; z++) for (int x = -1; x <= 5; x++) {
            // Cap the walls before enclosing their tops with the overhang. Otherwise a final
            // tile can become invisible from every ground-level standing position.
            boolean overhang = x < 0 || x > 4 || z < 0 || z > 4;
            int stage = overhang ? 6 : x == 0 || x == 4 || z == 0 || z == 4 ? 4 : 5;
            result.add(new CompanionHouse.Step(base.offset(x, 4, z), block(wood, "slab").defaultBlockState(), stage));
        }
        result.add(new CompanionHouse.Step(base.offset(2, 1, 0), block(wood, "door").defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH), 7));
        return List.copyOf(result);
    }

    /** The floor stands, a roof is over it and the walls are up (a saved blueprint alone does not prove there is still a house in the world). */
    static boolean interiorAvailable(ServerLevel level, BlockPos base) {
        if (base == null) return false;
        BlockPos center = base.offset(2, 1, 2);
        return level.hasChunkAt(center) && level.hasChunkAt(center.above(3))
                && level.getBlockState(center.below()).isFaceSturdy(level, center.below(), Direction.UP)
                && !level.getBlockState(center.above(3)).isAir()
                && !level.getBlockState(base.offset(0, 1, 2)).isAir()
                && !level.getBlockState(base.offset(4, 1, 2)).isAir()
                && !level.getBlockState(base.offset(2, 1, 4)).isAir();
    }

    static boolean inside(BlockPos base, BlockPos pos) {
        return base != null && pos.getY() == base.getY() + 1
                && pos.getX() > base.getX() && pos.getX() < base.getX() + 4
                && pos.getZ() > base.getZ() && pos.getZ() < base.getZ() + 4;
    }
}

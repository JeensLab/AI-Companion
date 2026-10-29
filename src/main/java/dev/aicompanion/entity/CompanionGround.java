package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** What the digging goal gathers: natural soil, or one of the loose blocks that fall when nothing holds them. */
public enum CompanionGround {
    SOIL("minecraft:dirt", null, Items.DIRT, "землю", "земли"),
    SAND("minecraft:sand", Blocks.SAND, Items.SAND, "песок", "песка"),
    RED_SAND("minecraft:red_sand", Blocks.RED_SAND, Items.RED_SAND, "красный песок", "красного песка"),
    GRAVEL("minecraft:gravel", Blocks.GRAVEL, Items.GRAVEL, "гравий", "гравия"),
    /** Flint falls out of gravel blocks at random: counted by the flint in the bag; gravel is dug, put down again and re-dug. */
    FLINT("minecraft:flint", Blocks.GRAVEL, Items.FLINT, "кремень", "кремня");

    /** How far down a column is followed to the first solid block: past it the world has nothing to fall into. */
    private static final int COLUMN_DEPTH = 48;

    private final String itemId;
    private final Block block;
    private final Item item;
    private final String accusative;
    private final String genitive;

    CompanionGround(String itemId, Block block, Item item, String accusative, String genitive) {
        this.itemId = itemId; this.block = block; this.item = item; this.accusative = accusative; this.genitive = genitive;
    }

    public String id() { return itemId; }
    /** «Ищу песок», «собирает песок». */
    public String accusative() { return accusative; }
    /** «блоков песка», «добыча песка». */
    public String genitive() { return genitive; }
    public boolean isLoose() { return this != SOIL; }

    /** The ground of an ordered item id, or null for an item that is none. */
    public static CompanionGround of(String itemId) {
        for (CompanionGround ground : values()) if (ground.itemId.equals(itemId)) return ground;
        return null;
    }

    /** Exact blocks only: no suspicious sand or gravel (brushable, no drop), concrete powder or anvils. */
    boolean matches(BlockState state) {
        return this == SOIL ? CompanionQuarry.soil(state) : state.is(block);
    }

    boolean counts(ItemStack stack) {
        return this == SOIL ? CompanionTools.soilItem(stack) : stack.is(item);
    }

    /** What the bag and the hands hold of the ordered item. */
    int count(CompanionEntity owner) {
        if (this == SOIL) return CompanionTools.soil(owner);
        int total = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(item)) total += owner.getItem(slot).getCount();
        if (owner.getMainHandItem().is(item)) total += owner.getMainHandItem().getCount();
        if (owner.getOffhandItem().is(item)) total += owner.getOffhandItem().getCount();
        return total;
    }

    /** Any of the loose blocks; a column of them falls together. */
    static boolean isLooseBlock(BlockState state) {
        return state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL);
    }

    boolean safeCell(ServerLevel level, BlockPos pos, CompanionEntity companion) {
        return this == SOIL ? !CompanionMiningNavigation.supportsLivingEntity(companion, pos) && CompanionQuarry.safeCell(level, pos)
                : safeLooseCell(level, pos);
    }

    /**
     * The soil check rejects any falling block nearby; digging one asks other things instead: no water to flood the hole, nothing
     * built on top, and no living thing under the column that comes down once the block is gone.
     */
    static boolean safeLooseCell(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return false;
        if (CompanionLighting.supportsLight(level, pos) || carriesSomeone(level, pos)) return false;
        if (level.getBlockEntity(pos) != null || !level.getFluidState(pos).isEmpty()) return false;
        for (Direction direction : Direction.values()) {
            BlockPos nearby = pos.relative(direction);
            if (!level.hasChunkAt(nearby) || !level.getFluidState(nearby).isEmpty() || level.getBlockEntity(nearby) != null
                    || level.getBlockState(nearby).is(Blocks.CRAFTING_TABLE)) return false;
        }
        return nothingBuiltOnTop(level, pos) && nothingAliveUnder(level, pos);
    }

    /**
     * Walks up through the loose blocks of the column; what sits on its top (a cactus, sugar cane, a torch, a build) comes down or
     * breaks with it, so the first other block there must be air or something replaceable. A canopy or a cave roof with a gap
     * under it is no cover; one resting right on the column is.
     */
    private static boolean nothingBuiltOnTop(ServerLevel level, BlockPos pos) {
        for (int up = 1; up <= COLUMN_DEPTH; up++) {
            BlockPos above = pos.above(up);
            if (level.isOutsideBuildHeight(above) || !level.hasChunkAt(above) || !level.getFluidState(above).isEmpty()) return false;
            BlockState cover = level.getBlockState(above);
            if (isLooseBlock(cover)) continue;
            return cover.isAir() || cover.canBeReplaced();
        }
        return false;
    }

    /**
     * Whoever stands on the block comes down with it. The common check takes any body in the cell over the block, and a body
     * pressed against its side reaches into that cell too: Oleg hugging the foot of a column kept the last block undiggable.
     * On the block means the feet at its top face or over it.
     */
    private static boolean carriesSomeone(ServerLevel level, BlockPos pos) {
        return !level.getEntities((Entity)null, new AABB(pos.above()).inflate(.05, .1, .05),
                entity -> entity instanceof LivingEntity living && living.isAlive() && living.getY() >= pos.getY() + 1 - .05).isEmpty();
    }

    private static boolean nothingAliveUnder(ServerLevel level, BlockPos pos) {
        int bottom = pos.getY();
        while (bottom > pos.getY() - COLUMN_DEPTH && !level.isOutsideBuildHeight(bottom - 1)) {
            BlockPos below = new BlockPos(pos.getX(), bottom - 1, pos.getZ());
            if (!level.hasChunkAt(below)) return false;
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) break;
            bottom--;
        }
        AABB column = new AABB(pos.getX(), bottom, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        return level.getEntitiesOfClass(LivingEntity.class, column, LivingEntity::isAlive).isEmpty();
    }
}

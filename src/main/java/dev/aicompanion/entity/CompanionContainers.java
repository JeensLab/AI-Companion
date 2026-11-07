package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a store or take order may put things in and take things from. Chests, barrels and shulker boxes are storage for every order;
 * furnaces, hoppers and dispensers only when the order names that block (the {@code location} of the step carries its id).
 */
public final class CompanionContainers {
    public static final String SHULKER = "minecraft:shulker_box";
    /** The blocks an order may name; a shulker box of any colour counts as {@link #SHULKER}. */
    public static final Set<String> KINDS = Set.of("minecraft:barrel", SHULKER, "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
            "minecraft:hopper", "minecraft:dispenser", "minecraft:dropper", "minecraft:chest", "minecraft:trapped_chest");
    private static final Set<String> CHESTS = Set.of("minecraft:chest", "minecraft:trapped_chest");
    /** The ids an order or the model may name for a place other than a chest: a chest, named or not, is near_player. */
    public static final Set<String> NAMEABLE = KINDS.stream().filter(kind -> !CHESTS.contains(kind)).collect(Collectors.toUnmodifiableSet());
    /** Blocks `storageAt` decides for (a closed lid or a block above means shut); every other kind is opened as it stands. */
    private static final Set<String> STORAGE_KINDS = Set.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", SHULKER);
    private static final int FURNACE_RESULT_SLOT = 2;
    private static final float SOUND_VOLUME = 0.5F;
    private static final float SOUND_PITCH = 1.0F;

    /** How the player's lines name a container: where to, where from, and the plain name for «нет …». */
    private record Words(String into, String from, String name) { }

    private static final Words CHEST_WORDS = new Words("в сундук", "из сундука", "сундука");
    private static final Map<String, Words> WORDS = Map.of(
            "minecraft:barrel", new Words("в бочку", "из бочки", "бочки"),
            SHULKER, new Words("в шалкер", "из шалкера", "шалкера"),
            "minecraft:furnace", new Words("в печь", "из печи", "печи"),
            "minecraft:blast_furnace", new Words("в плавильную печь", "из плавильной печи", "плавильной печи"),
            "minecraft:smoker", new Words("в коптильню", "из коптильни", "коптильни"),
            "minecraft:hopper", new Words("в воронку", "из воронки", "воронки"),
            "minecraft:dispenser", new Words("в раздатчик", "из раздатчика", "раздатчика"),
            "minecraft:dropper", new Words("в выбрасыватель", "из выбрасывателя", "выбрасывателя"),
            "minecraft:chest", CHEST_WORDS,
            "minecraft:trapped_chest", new Words("в сундук-ловушку", "из сундука-ловушки", "сундука-ловушки"));

    private CompanionContainers() { }

    public static String into(String kind) { return words(kind).into(); }

    public static String from(String kind) { return words(kind).from(); }

    public static String nameOf(String kind) { return words(kind).name(); }

    private static Words words(String kind) { return kind == null ? CHEST_WORDS : WORDS.getOrDefault(kind, CHEST_WORDS); }

    /** A chest (a double one as a whole), a barrel or a shulker box that can be opened now; null for any other block. */
    static Container storageAt(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) return ChestBlock.getContainer(chest, state, level, pos, false);
        boolean shulker = state.getBlock() instanceof ShulkerBoxBlock;
        if (shulker && !lidFree(level, pos, state) || !shulker && !state.is(Blocks.BARREL)) return null;
        return level.getBlockEntity(pos) instanceof Container box ? box : null;
    }

    /** The container at pos when the block is of the kind the order named (a furnace, a hopper...), else null. */
    static Container containerAt(ServerLevel level, BlockPos pos, String kind) {
        if (!level.hasChunkAt(pos) || !kind.equals(kindOf(level.getBlockState(pos)))) return null;
        if (STORAGE_KINDS.contains(kind)) return storageAt(level, pos);
        return level.getBlockEntity(pos) instanceof Container container ? container : null;
    }

    private static String kindOf(BlockState state) {
        return state.getBlock() instanceof ShulkerBoxBlock ? SHULKER : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** A shulker box with a block against its lid does not open for a player either. */
    private static boolean lidFree(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos lid = pos.relative(state.getValue(ShulkerBoxBlock.FACING));
        return level.getBlockState(lid).getCollisionShape(level, lid).isEmpty();
    }

    /** The chests and barrels the sorting works on: not trapped chests, and no shulker boxes (a box inside a box cannot be planned). */
    static boolean sortable(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.BARREL);
    }

    /** Whether a take order may empty this slot: of a furnace only the result, never the input or the fuel. */
    static boolean takeable(Container container, int slot) {
        return !(container instanceof AbstractFurnaceBlockEntity) || slot == FURNACE_RESULT_SLOT;
    }

    /** The container's own rule for a slot (the furnace result takes nothing, its fuel slot only fuel), and no shulker box inside a shulker box. */
    static boolean accepts(Container container, int slot, ItemStack stack) {
        return container.canPlaceItem(slot, stack) && (!(container instanceof ShulkerBoxBlockEntity box) || box.canPlaceItemThroughFace(slot, stack, null));
    }

    /** Whether the open and close of this block is shown with a lid or a sound. */
    static boolean opens(BlockState state) {
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof ShulkerBoxBlock || state.is(Blocks.BARREL);
    }

    /** A barrel shows it is open by its block state; a chest or a shulker box by the block event. */
    static void showBarrel(ServerLevel level, BlockPos pos, BlockState state, boolean open) {
        if (state.is(Blocks.BARREL)) level.setBlock(pos, state.setValue(BarrelBlock.OPEN, open), Block.UPDATE_ALL);
    }

    static void playOpen(ServerLevel level, BlockPos pos) {
        play(level, pos, SoundEvents.CHEST_OPEN, SoundEvents.BARREL_OPEN, SoundEvents.SHULKER_BOX_OPEN);
    }

    static void playClose(ServerLevel level, BlockPos pos) {
        play(level, pos, SoundEvents.CHEST_CLOSE, SoundEvents.BARREL_CLOSE, SoundEvents.SHULKER_BOX_CLOSE);
    }

    private static void play(ServerLevel level, BlockPos pos, SoundEvent chest, SoundEvent barrel, SoundEvent shulker) {
        BlockState state = level.getBlockState(pos);
        if (!opens(state)) return;
        SoundEvent sound = state.is(Blocks.BARREL) ? barrel : state.getBlock() instanceof ShulkerBoxBlock ? shulker : chest;
        level.playSound(null, pos, sound, SoundSource.BLOCKS, SOUND_VOLUME, SOUND_PITCH);
    }
}

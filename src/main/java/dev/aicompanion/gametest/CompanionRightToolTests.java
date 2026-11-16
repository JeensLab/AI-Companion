package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Issue #105, «ломает блок правильным инструментом»: a block he breaks takes the tool from his bag that speeds it up most, and a
 * bare hand when he has none. The block is broken here as the paths that break one break it — the tool in his hand, the game's
 * own drops to that tool, the wear on that tool, then his hand put back — so the bag, the drops and the wear are real.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionRightToolTests {
    private static final int GROUND = 1;
    private static final BlockPos STAND = new BlockPos(16, GROUND, 16);

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void stoneIsBrokenWithThePickaxeFromTheBag(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        BlockPos stone = new BlockPos(16, GROUND, 18);
        h.setBlock(stone, Blocks.STONE);
        List<ItemStack> drops = breakWithTool(h, c, stone);
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.COBBLESTONE)), "Stone did not give its cobblestone: " + drops);
        h.assertTrue(damage(c, Items.STONE_PICKAXE) == 1, "The pickaxe was not the tool used: damage " + damage(c, Items.STONE_PICKAXE));
        h.assertBlockNotPresent(Blocks.STONE, stone);
        // The hand was empty: the tool is put back into the bag, not left in the hand.
        h.assertTrue(c.getMainHandItem().isEmpty(), "The pickaxe stayed in his hand: " + c.getMainHandItem());
        h.assertTrue(speedOf(Items.STONE_PICKAXE, Blocks.STONE.defaultBlockState()) > ItemStack.EMPTY.getDestroySpeed(Blocks.STONE.defaultBlockState()),
                "A pickaxe no longer speeds stone up by the game's own rule");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void logIsBrokenWithTheAxeFromTheBag(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.IRON_AXE));
        BlockPos log = new BlockPos(16, GROUND, 18);
        h.setBlock(log, Blocks.OAK_LOG);
        List<ItemStack> drops = breakWithTool(h, c, log);
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.OAK_LOG)), "The log did not give itself: " + drops);
        h.assertTrue(damage(c, Items.IRON_AXE) == 1, "The axe was not the tool used: damage " + damage(c, Items.IRON_AXE));
        h.assertBlockNotPresent(Blocks.OAK_LOG, log);
        h.assertTrue(c.getMainHandItem().isEmpty(), "The axe stayed in his hand: " + c.getMainHandItem());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void earthAndSnowAreBrokenWithTheShovelFromTheBag(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.IRON_SHOVEL));
        BlockPos dirt = new BlockPos(16, GROUND, 18), snow = new BlockPos(16, GROUND, 19);
        h.setBlock(dirt, Blocks.DIRT);
        h.setBlock(snow, Blocks.SNOW_BLOCK);
        List<ItemStack> dirtDrops = breakWithTool(h, c, dirt);
        List<ItemStack> snowDrops = breakWithTool(h, c, snow);
        h.assertTrue(dirtDrops.stream().anyMatch(drop -> drop.is(Items.DIRT)), "The dirt did not give itself: " + dirtDrops);
        // A snow block gives nothing to a hand: its snowballs prove the shovel was used.
        h.assertTrue(snowDrops.stream().anyMatch(drop -> drop.is(Items.SNOWBALL)), "The snow did not give its snowballs: " + snowDrops);
        h.assertTrue(damage(c, Items.IRON_SHOVEL) == 2, "The shovel did not wear on both blocks: damage " + damage(c, Items.IRON_SHOVEL));
        h.assertBlockNotPresent(Blocks.DIRT, dirt);
        h.assertBlockNotPresent(Blocks.SNOW_BLOCK, snow);
        h.assertTrue(c.getMainHandItem().isEmpty(), "The shovel stayed in his hand: " + c.getMainHandItem());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void leavesAreCutWithTheShearsFromTheBag(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.SHEARS));
        BlockPos leaves = new BlockPos(16, GROUND, 18);
        h.setBlock(leaves, Blocks.OAK_LEAVES);
        List<ItemStack> drops = breakWithTool(h, c, leaves);
        // A hand gives a sapling now and then; shears give the leaf block itself, as #173 has it.
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.OAK_LEAVES)), "The leaves did not give themselves: " + drops);
        h.assertTrue(damage(c, Items.SHEARS) == 1, "The shears were not the tool used: damage " + damage(c, Items.SHEARS));
        h.assertBlockNotPresent(Blocks.OAK_LEAVES, leaves);
        h.assertTrue(c.getMainHandItem().isEmpty(), "The shears stayed in his hand: " + c.getMainHandItem());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void grassIsCutWithTheShearsFromTheBag(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.SHEARS));
        BlockPos grass = new BlockPos(16, GROUND, 18);
        h.setBlock(grass, Blocks.SHORT_GRASS);
        List<ItemStack> drops = breakWithTool(h, c, grass);
        // No tool is faster than a hand on grass: shears are taken because they are the only way to the plant itself.
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.SHORT_GRASS)), "The grass did not give itself: " + drops);
        h.assertTrue(damage(c, Items.SHEARS) == 1, "The shears were not the tool used: damage " + damage(c, Items.SHEARS));
        h.assertBlockNotPresent(Blocks.SHORT_GRASS, grass);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void withNoToolOfThatKindHeBreaksByHand(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.BREAD, 3));
        BlockPos stone = new BlockPos(16, GROUND, 18);
        h.setBlock(stone, Blocks.STONE);
        List<ItemStack> drops = breakWithTool(h, c, stone);
        // Stone needs the right tool for its drop: broken by hand it gives nothing, and it is still broken.
        h.assertTrue(drops.isEmpty(), "A hand gave stone a drop: " + drops);
        h.assertBlockNotPresent(Blocks.STONE, stone);
        h.assertTrue(c.getMainHandItem().isEmpty(), "Something was put in his hand: " + c.getMainHandItem());
        h.assertTrue(count(c, Items.BREAD) == 3, "His bread was lost: " + count(c, Items.BREAD));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void theFasterOfTwoToolsIsTheOneTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.WOODEN_AXE));
        c.setItem(1, new ItemStack(Items.STONE_AXE));
        c.setItem(2, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos planks = new BlockPos(16, GROUND, 18);
        h.setBlock(planks, Blocks.OAK_PLANKS);
        List<ItemStack> drops = breakWithTool(h, c, planks);
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.OAK_PLANKS)), "The planks did not give themselves: " + drops);
        h.assertTrue(damage(c, Items.STONE_AXE) == 1, "The faster axe was not used: stone axe damage " + damage(c, Items.STONE_AXE));
        h.assertTrue(damage(c, Items.WOODEN_AXE) == 0, "The slower axe was used: damage " + damage(c, Items.WOODEN_AXE));
        h.assertTrue(damage(c, Items.DIAMOND_PICKAXE) == 0, "A pickaxe took a wooden block: damage " + damage(c, Items.DIAMOND_PICKAXE));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void withTheSwitchOffHeBreaksWithWhatHeHolds(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setRightToolForBlock(false);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        BlockPos stone = new BlockPos(16, GROUND, 18);
        h.setBlock(stone, Blocks.STONE);
        List<ItemStack> drops = breakWithTool(h, c, stone);
        h.assertTrue(c.getMainHandItem().is(Items.IRON_SWORD), "The sword was swapped for a tool: " + c.getMainHandItem());
        // A sword wears on any block it breaks, as for a player: it is the tool he broke with.
        h.assertTrue(damage(c, Items.IRON_SWORD) == 2, "The sword did not wear on stone: damage " + damage(c, Items.IRON_SWORD));
        h.assertTrue(damage(c, Items.STONE_PICKAXE) == 0, "The pickaxe in the bag was used: damage " + damage(c, Items.STONE_PICKAXE));
        // A sword is no pickaxe: stone gives nothing, but it is broken all the same.
        h.assertTrue(drops.isEmpty(), "A sword gave stone a drop: " + drops);
        h.assertBlockNotPresent(Blocks.STONE, stone);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void whatWasInHisHandIsPutBackAfterTheBreak(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIRT, 3));
        BlockPos stone = new BlockPos(16, GROUND, 18);
        h.setBlock(stone, Blocks.STONE);
        breakWithTool(h, c, stone);
        h.assertTrue(damage(c, Items.STONE_PICKAXE) == 1, "The pickaxe was not used: damage " + damage(c, Items.STONE_PICKAXE));
        // The equip helper takes one item of the stack, as CompanionRecovery restores a hand: the rest stays in the bag.
        h.assertTrue(c.getMainHandItem().is(Items.DIRT), "The dirt from his hand was not put back: " + c.getMainHandItem());
        h.assertTrue(count(c, Items.DIRT) == 3, "His dirt was lost or duplicated: " + count(c, Items.DIRT));
        h.assertTrue(count(c, Items.STONE_PICKAXE) == 1, "The pickaxe was lost or duplicated: " + count(c, Items.STONE_PICKAXE));
        h.assertBlockNotPresent(Blocks.STONE, stone);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aToolInTheOtherHandIsTakenToo(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STONE_PICKAXE));
        BlockPos stone = new BlockPos(16, GROUND, 18);
        h.setBlock(stone, Blocks.STONE);
        List<ItemStack> drops = breakWithTool(h, c, stone);
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.COBBLESTONE)), "Stone did not give its cobblestone: " + drops);
        h.assertTrue(damage(c, Items.STONE_PICKAXE) == 1, "The pickaxe from the other hand was not used: damage " + damage(c, Items.STONE_PICKAXE));
        h.assertTrue(c.getMainHandItem().isEmpty() && c.getOffhandItem().isEmpty(), "The pickaxe was left in a hand");
        h.succeed();
    }

    /** The switch lives in the save with him; an old save has it on. */
    @GameTest(template = "empty")
    public static void theSwitchIsSavedAndAnOldSaveHasItOn(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        h.assertTrue(c.rightToolForBlock(), "The switch is not on by default");
        c.setRightToolForBlock(false);
        CompanionEntity other = h.spawn(AICompanionMod.COMPANION.get(), 8, GROUND, 8);
        other.load(c.saveWithoutId(new CompoundTag()));
        h.assertTrue(!other.rightToolForBlock(), "The switch was not saved");
        CompanionEntity third = h.spawn(AICompanionMod.COMPANION.get(), 12, GROUND, 12);
        third.setRightToolForBlock(false);
        third.load(new CompoundTag());
        h.assertTrue(third.rightToolForBlock(), "An old save loaded with the switch off");
        h.succeed();
    }

    /** A plain pickaxe in the hand is no reason to leave the faster one in the bag: the stack itself is taken, and the hand comes back. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void anEnchantedPickaxeInTheBagBeatsThePlainOneHeHolds(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, enchanted(h, Items.STONE_PICKAXE, 2));
        BlockPos stone = new BlockPos(16, GROUND, 18);
        h.setBlock(stone, Blocks.STONE);
        List<ItemStack> drops = breakWithTool(h, c, stone);
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.COBBLESTONE)), "Stone did not give its cobblestone: " + drops);
        h.assertTrue(find(h, c, Items.STONE_PICKAXE, 2).getDamageValue() == 1,
                "The enchanted pickaxe from the bag was not the one used: " + c.getStoredItemsSummary());
        h.assertTrue(c.getMainHandItem().is(Items.STONE_PICKAXE) && efficiency(h, c.getMainHandItem()) == 0
                && c.getMainHandItem().getDamageValue() == 0, "The plain pickaxe from his hand was not put back: " + c.getMainHandItem());
        h.assertTrue(count(c, Items.STONE_PICKAXE) == 2, "A pickaxe was lost or duplicated: " + count(c, Items.STONE_PICKAXE));
        h.succeed();
    }

    /** Two stacks of one tool: the faster one is the one that wears, not the first one of its item the bag happens to hold. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void theFastestStackOfTwoOfTheSameToolIsTheOneThatWears(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(1, enchanted(h, Items.STONE_PICKAXE, 1));
        BlockPos stone = new BlockPos(16, GROUND, 18);
        h.setBlock(stone, Blocks.STONE);
        List<ItemStack> drops = breakWithTool(h, c, stone);
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.COBBLESTONE)), "Stone did not give its cobblestone: " + drops);
        h.assertTrue(find(h, c, Items.STONE_PICKAXE, 1).getDamageValue() == 1,
                "The enchanted pickaxe was not the one used: " + c.getStoredItemsSummary());
        h.assertTrue(find(h, c, Items.STONE_PICKAXE, 0).getDamageValue() == 0, "The plain pickaxe was used instead");
        h.succeed();
    }

    /** The tool breaks on the block it was taken for: what was in his hand comes back, and nothing of his is lost. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aToolThatBreaksMidBreakLeavesHisHandAndTheDrop(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack worn = enchanted(h, Items.STONE_PICKAXE, 1);
        worn.setDamageValue(worn.getMaxDamage() - 1);
        c.setItem(0, worn);
        BlockPos stone = new BlockPos(16, GROUND, 18);
        h.setBlock(stone, Blocks.STONE);
        List<ItemStack> drops = breakWithTool(h, c, stone);
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.COBBLESTONE)), "Stone did not give its cobblestone: " + drops);
        h.assertTrue(find(h, c, Items.STONE_PICKAXE, 1).isEmpty(), "The worn pickaxe did not break on the block it was taken for");
        h.assertTrue(c.getMainHandItem().is(Items.STONE_PICKAXE) && efficiency(h, c.getMainHandItem()) == 0,
                "The pickaxe from his hand was not put back after the tool broke: " + c.getMainHandItem());
        h.assertTrue(count(c, Items.STONE_PICKAXE) == 1, "A pickaxe was lost or duplicated: " + count(c, Items.STONE_PICKAXE));
        h.succeed();
    }

    /** A full bag has no cell for the tool once the swing is over: it stays in his hand rather than vanish. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aFullBagKeepsTheToolWhenItIsPutAway(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE, 2));
        BlockPos stone = new BlockPos(16, GROUND, 18);
        h.setBlock(stone, Blocks.STONE);
        List<ItemStack> drops = breakWithTool(h, c, stone);
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.COBBLESTONE)), "Stone did not give its cobblestone: " + drops);
        // The worn tool matches no cell and no stack of the full bag: it stays in his hand, and every pickaxe is still his.
        h.assertTrue(count(c, Items.STONE_PICKAXE) == 2, "A pickaxe was lost in a full bag: " + count(c, Items.STONE_PICKAXE));
        h.assertTrue(c.getMainHandItem().is(Items.STONE_PICKAXE) && c.getMainHandItem().getDamageValue() == 1,
                "The worn tool left his hand with no cell for it: " + c.getMainHandItem());
        h.assertTrue(count(c, Items.COBBLESTONE) == (c.getContainerSize() - 1) * 64, "His cobblestone was lost");
        h.succeed();
    }

    /** An order stopped with the tool in his hand mid-swing: the hand it replaced comes back, the tool goes into the bag. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aCancelledBreakPutsTheToolAwayAndRestoresHisHand(GameTestHelper h) throws Exception {
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIRT, 3));
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        c.takeToolForBlock(Blocks.STONE.defaultBlockState());
        h.assertTrue(c.getMainHandItem().is(Items.STONE_PICKAXE), "The tool was not taken: " + c.getMainHandItem());
        Object work = field(c, "work");
        var cancel = work.getClass().getDeclaredMethod("cancel");
        cancel.setAccessible(true);
        cancel.invoke(work);
        h.assertTrue(c.getMainHandItem().is(Items.DIRT), "His dirt was not put back after the cancel: " + c.getMainHandItem());
        h.assertTrue(count(c, Items.DIRT) == 3 && count(c, Items.STONE_PICKAXE) == 1, "Items were lost or duplicated on the cancel");
        h.succeed();
    }

    /** The path that keeps the tool in his hand for many blocks: a tool of another class is put away, the block is dug by hand. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void theWrongToolIsPutAwayForHandDigging(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        c.selectToolForBlock(Blocks.DIRT.defaultBlockState());
        h.assertTrue(c.getMainHandItem().isEmpty(), "The pickaxe stayed in his hand over earth: " + c.getMainHandItem());
        h.assertTrue(count(c, Items.WOODEN_PICKAXE) == 1, "The pickaxe was lost or duplicated");
        h.succeed();
    }

    /** A faster tool of the wrong class gives no drop: the pickaxe that takes the drop is the one taken and worn. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void obsidianIsTakenWithThePickaxeThatGivesItsDrop(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        c.setItem(0, enchanted(h, Items.WOODEN_PICKAXE, 5));
        c.setItem(1, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos obsidian = new BlockPos(16, GROUND, 18);
        h.setBlock(obsidian, Blocks.OBSIDIAN);
        List<ItemStack> drops = breakWithTool(h, c, obsidian);
        h.assertTrue(drops.stream().anyMatch(drop -> drop.is(Items.OBSIDIAN)), "Obsidian gave no drop: " + drops);
        h.assertTrue(find(h, c, Items.DIAMOND_PICKAXE, 0).getDamageValue() == 1, "The diamond pickaxe was not the one used");
        h.assertTrue(find(h, c, Items.WOODEN_PICKAXE, 5).getDamageValue() == 0,
                "The faster wooden pickaxe was used and the drop went with it");
        h.succeed();
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), STAND.getX(), GROUND, STAND.getZ());
        c.setNoAi(true);
        c.setOwnerId(h.makeMockServerPlayerInLevel().getUUID());
        return c;
    }

    /** A block broken as the paths break it: the tool for it in his hand, the drops to that tool, its wear, then his hand put back. */
    private static List<ItemStack> breakWithTool(GameTestHelper h, CompanionEntity c, BlockPos at) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(at);
        BlockState state = level.getBlockState(pos);
        c.takeToolForBlock(state);
        ItemStack tool = c.getMainHandItem();
        // The game checks the right tool before the loot table, as CompanionTools.drops does for the paths.
        List<ItemStack> drops = state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)
                ? List.of() : Block.getDrops(state, level, pos, level.getBlockEntity(pos), c, tool);
        h.assertTrue(level.destroyBlock(pos, false, c, 512), "The block at " + at + " was not broken");
        tool.getItem().mineBlock(tool, level, state, pos, c);
        c.putToolAway();
        return drops;
    }

    private static float speedOf(Item item, BlockState state) { return new ItemStack(item).getDestroySpeed(state); }

    private static ItemStack enchanted(GameTestHelper h, Item item, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(h.getLevel().registryAccess().holderOrThrow(Enchantments.EFFICIENCY), level);
        return stack;
    }

    private static int efficiency(GameTestHelper h, ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(h.getLevel().registryAccess().holderOrThrow(Enchantments.EFFICIENCY), stack);
    }

    /** The stack of this item with this Efficiency level, in the bag or in a hand: empty when he has none. */
    private static ItemStack find(GameTestHelper h, CompanionEntity c, Item item, int efficiency) {
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            ItemStack stack = c.getItem(slot);
            if (stack.is(item) && efficiency(h, stack) == efficiency) return stack;
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack stack = c.getItemBySlot(slot);
            if (stack.is(item) && efficiency(h, stack) == efficiency) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        var found = target.getClass().getDeclaredField(name);
        found.setAccessible(true);
        return found.get(target);
    }

    private static int damage(CompanionEntity c, Item item) {
        int found = -1;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) found = Math.max(found, c.getItem(slot).getDamageValue());
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (c.getItemBySlot(slot).is(item)) found = Math.max(found, c.getItemBySlot(slot).getDamageValue());
        return found;
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }
}

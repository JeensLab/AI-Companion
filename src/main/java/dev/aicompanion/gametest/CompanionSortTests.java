package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionSortGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner's request of 03.11: a button tidies the companion's bag. The bag comes out ordered by kind and by item, and
 * nothing is lost or merged on the way.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSortTests {
    /** The order the button promises: what he fights with, what he eats, then what he builds and carries. */
    private static final java.util.List<CompanionSortGroups.Group> ORDER = java.util.List.of(
            CompanionSortGroups.Group.GEAR, CompanionSortGroups.Group.FOOD, CompanionSortGroups.Group.ORE,
            CompanionSortGroups.Group.STONE, CompanionSortGroups.Group.WOOD, CompanionSortGroups.Group.MOB,
            CompanionSortGroups.Group.OTHER);
    @GameTest(template = "empty")
    public static void hisBagIsTidiedByKind(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.BREAD, 3));
        companion.setItem(1, new ItemStack(Items.OAK_LOG, 5));
        companion.setItem(2, new ItemStack(Items.IRON_PICKAXE));
        companion.setItem(3, new ItemStack(Items.COBBLESTONE, 12));
        companion.setItem(4, new ItemStack(Items.RAW_IRON, 2));
        companion.setItem(7, new ItemStack(Items.APPLE, 4));

        companion.sortStoredItems(false);

        h.startSequence().thenExecuteAfter(1, () -> {
            int previousGroup = -1;
            int total = 0;
            for (int slot = 0; slot < companion.getContainerSize(); slot++) {
                ItemStack stack = companion.getItem(slot);
                if (stack.isEmpty()) continue;
                total += stack.getCount();
                int group = ORDER.indexOf(CompanionSortGroups.of(stack));
                h.assertTrue(group >= previousGroup, "The bag is not ordered by kind at slot " + slot + ": " + stack);
                previousGroup = group;
            }
            h.assertTrue(total == 27, "Sorting changed what the bag holds: " + total + " instead of 27");
            h.assertTrue(stored(companion, Items.BREAD) == 3 && stored(companion, Items.OAK_LOG) == 5
                    && stored(companion, Items.IRON_PICKAXE) == 1 && stored(companion, Items.COBBLESTONE) == 12
                    && stored(companion, Items.RAW_IRON) == 2 && stored(companion, Items.APPLE) == 4,
                    "Sorting lost or merged a stack: " + companion.getStoredItemsSummary());

            // The second press puts the same order the other way round and keeps every stack.
            companion.sortStoredItems(true);
            int previousRank = Integer.MAX_VALUE;
            int reversed = 0;
            for (int slot = 0; slot < companion.getContainerSize(); slot++) {
                ItemStack stack = companion.getItem(slot);
                if (stack.isEmpty()) continue;
                reversed += stack.getCount();
                int rank = ORDER.indexOf(CompanionSortGroups.of(stack));
                h.assertTrue(rank <= previousRank, "The reversed bag is not ordered the other way at slot " + slot + ": " + stack);
                previousRank = rank;
            }
            h.assertTrue(reversed == 27, "Reversed sorting changed what the bag holds: " + reversed + " instead of 27");
        }).thenSucceed();
    }

    /** The button's path, not just the sort: the menu it looks up must point at this companion and see the tidied bag. */
    @GameTest(template = "empty")
    public static void theMenuPathSortsTheSameBag(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.BREAD, 2));
        companion.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        // Built the way the server builds it when the player opens the companion (a mock player cannot take a packet).
        var menu = new dev.aicompanion.inventory.CompanionMenu(1, owner.getInventory(), companion);
        h.startSequence().thenExecuteAfter(1, () -> {
            h.assertTrue(menu.getCompanion() == companion, "The menu points at another companion");
            menu.getCompanion().sortStoredItems(false);
            h.assertTrue(menu.getSlot(dev.aicompanion.inventory.CompanionMenu.BAG_START).getItem().is(Items.IRON_PICKAXE),
                    "The bag in the menu was not sorted: " + menu.getSlot(dev.aicompanion.inventory.CompanionMenu.BAG_START).getItem());
        }).thenSucceed();
    }

    private static int stored(CompanionEntity companion, Item item) {
        int total = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) {
            ItemStack stack = companion.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(5, 1, 8));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        companion.setFollowing(false);
        return companion;
    }
}

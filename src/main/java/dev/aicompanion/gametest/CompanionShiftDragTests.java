package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionShiftDrag;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.inventory.CompanionMenu;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A shift-drag is a run of quick moves, one per slot the pointer passes: the screen sends each of them. The drag's own
 * choice of slots is driven here frame by frame, with the game's own click for each slot it reaches.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionShiftDragTests {
    /** A drag up a column of slots moves every stack the pointer passes, and an empty starting slot moves nothing. */
    @GameTest(template = "empty")
    public static void aDragUpAColumnMovesEveryStackItPasses(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = owner(h, companion);
        // Menu slots 51, 42 and 33 are the player's first column; his hotbar slot 60 below them stays empty.
        owner.getInventory().setItem(27, new ItemStack(Items.COBBLESTONE, 16));
        owner.getInventory().setItem(18, new ItemStack(Items.BREAD, 5));
        owner.getInventory().setItem(9, new ItemStack(Items.DIRT, 12));
        var menu = new CompanionMenu(1, owner.getInventory(), companion);
        CompanionShiftDrag.reset();
        h.startSequence().thenExecute(() -> {
            // The pointer starts on the empty hotbar slot and is dragged up the column, a pixel at a time.
            int x = menu.getSlot(CompanionMenu.PLAYER_END - 9).x + 8;
            for (int y = menu.getSlot(CompanionMenu.PLAYER_END - 9).y + 8; y >= menu.getSlot(CompanionMenu.PLAYER_START).y + 8; y--) {
                dragFrame(menu, owner, x, y, true);
            }
            h.assertTrue(countInBag(companion, Items.COBBLESTONE) == 16, "The cobblestone did not reach the bag: " + countInBag(companion, Items.COBBLESTONE));
            h.assertTrue(countInBag(companion, Items.BREAD) == 5, "The bread did not reach the bag: " + countInBag(companion, Items.BREAD));
            h.assertTrue(countInBag(companion, Items.DIRT) == 12, "The dirt did not reach the bag: " + countInBag(companion, Items.DIRT));
            h.assertTrue(count(owner, Items.COBBLESTONE) == 0, "The cobblestone stayed with the player: " + count(owner, Items.COBBLESTONE));
            h.assertTrue(count(owner, Items.BREAD) == 0, "The bread stayed with the player: " + count(owner, Items.BREAD));
            h.assertTrue(count(owner, Items.DIRT) == 0, "The dirt stayed with the player: " + count(owner, Items.DIRT));
            h.assertTrue(owner.getInventory().getItem(0).isEmpty(), "The empty hotbar slot the drag started on filled up: " + owner.getInventory().getItem(0));
        }).thenSucceed();
    }

    /** The pointer resting on a slot it has already emptied is not a second move. */
    @GameTest(template = "empty")
    public static void aPointerThatStaysMovesOneStackOnce(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = owner(h, companion);
        companion.setItem(0, new ItemStack(Items.DIRT, 4));
        var menu = new CompanionMenu(1, owner.getInventory(), companion);
        CompanionShiftDrag.reset();
        h.startSequence().thenExecute(() -> {
            Slot bagSlot = menu.getSlot(CompanionMenu.BAG_START);
            for (int frame = 0; frame < 30; frame++) dragFrame(menu, owner, bagSlot.x + 8, bagSlot.y + 8, true);
            h.assertTrue(bagSlot.getItem().isEmpty(), "The bag slot still holds " + bagSlot.getItem());
            h.assertTrue(count(owner, Items.DIRT) == 4, "The dirt was moved twice: " + count(owner, Items.DIRT));
        }).thenSucceed();
    }

    /** A stack on the cursor is the game's own drag-split: the drag leaves the slots alone. */
    @GameTest(template = "empty")
    public static void aCarriedStackLeavesTheDragToTheGame(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = owner(h, companion);
        companion.setItem(0, new ItemStack(Items.DIRT, 4));
        var menu = new CompanionMenu(1, owner.getInventory(), companion);
        CompanionShiftDrag.reset();
        h.startSequence().thenExecute(() -> {
            menu.setCarried(new ItemStack(Items.STICK, 2));
            Slot bagSlot = menu.getSlot(CompanionMenu.BAG_START);
            for (int frame = 0; frame < 30; frame++) dragFrame(menu, owner, bagSlot.x + 8, bagSlot.y + 8, true);
            h.assertTrue(countInBag(companion, Items.DIRT) == 4, "The dirt moved with a stack on the cursor: " + countInBag(companion, Items.DIRT));
            h.assertTrue(menu.getCarried().is(Items.STICK) && menu.getCarried().getCount() == 2, "The carried stack changed: " + menu.getCarried());
        }).thenSucceed();
    }

    /** Without shift the same movement is no drag at all. */
    @GameTest(template = "empty")
    public static void aDragWithoutShiftMovesNothing(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = owner(h, companion);
        companion.setItem(0, new ItemStack(Items.DIRT, 4));
        var menu = new CompanionMenu(1, owner.getInventory(), companion);
        CompanionShiftDrag.reset();
        h.startSequence().thenExecute(() -> {
            Slot bagSlot = menu.getSlot(CompanionMenu.BAG_START);
            for (int frame = 0; frame < 30; frame++) dragFrame(menu, owner, bagSlot.x + 8, bagSlot.y + 8, false);
            h.assertTrue(countInBag(companion, Items.DIRT) == 4, "The dirt moved without shift: " + countInBag(companion, Items.DIRT));
            h.assertTrue(count(owner, Items.DIRT) == 0, "The dirt reached the player without shift: " + count(owner, Items.DIRT));
        }).thenSucceed();
    }

    /** A pointer between the menu's panels is over no slot and moves nothing. */
    @GameTest(template = "empty")
    public static void aPointerBetweenSlotsMovesNothing(GameTestHelper h) {
        CompanionEntity companion = spawn(h);
        var owner = owner(h, companion);
        companion.setItem(0, new ItemStack(Items.DIRT, 4));
        var menu = new CompanionMenu(1, owner.getInventory(), companion);
        CompanionShiftDrag.reset();
        h.startSequence().thenExecute(() -> {
            int x = menu.getSlot(CompanionMenu.BAG_START).x + 8;
            int y = (menu.getSlot(CompanionMenu.BAG_START + 18).y + menu.getSlot(CompanionMenu.PLAYER_START).y) / 2;
            for (int frame = 0; frame < 30; frame++) dragFrame(menu, owner, x, y, true);
            h.assertTrue(countInBag(companion, Items.DIRT) == 4, "The dirt moved from under a pointer that was between slots: " + countInBag(companion, Items.DIRT));
            h.assertTrue(count(owner, Items.DIRT) == 0, "The dirt reached the player from between slots: " + count(owner, Items.DIRT));
        }).thenSucceed();
    }

    /** One frame of a drag, as the screen sends it: the slot the pointer reached now, moved with the game's own click. */
    private static void dragFrame(AbstractContainerMenu menu, Player player, double mouseX, double mouseY, boolean shiftDown) {
        Slot slot = CompanionShiftDrag.reached(menu, 0, 0, mouseX, mouseY, shiftDown, player);
        if (slot != null) menu.clicked(slot.index, 0, ClickType.QUICK_MOVE, player);
    }

    private static int count(Player player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static int countInBag(CompanionEntity companion, Item item) {
        int total = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) {
            ItemStack stack = companion.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(owner != null, "The companion has no owner in the level");
        return owner;
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 2, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() - 4);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
}

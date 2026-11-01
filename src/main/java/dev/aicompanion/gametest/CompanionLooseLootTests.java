package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The owner's request of 31.10: items thrown at his feet or lying near him go into his bag, as a player takes them —
 * whoever dropped them. A gift handed to the player and a drop of another companion stay where they are.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLooseLootTests {
    /** Long enough for the pickup to run a few ticks after the drop. */
    private static final int PICKUP_TICKS = 5;

    @GameTest(template = "empty")
    public static void anItemThrownAtHisFeetGoesIntoTheBag(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity item = drop(h, companion, Items.COBBLESTONE, 5);
        h.startSequence().thenExecuteAfter(PICKUP_TICKS, () -> {
            h.assertTrue(item.isRemoved(), "The item is still lying on the floor");
            h.assertTrue(stored(companion, Items.COBBLESTONE) >= 5,
                    "Only " + stored(companion, Items.COBBLESTONE) + " of 5 reached his bag");
        }).thenSucceed();
    }

    /** A gift he threw to the player is aimed at the player and flagged: it must not come back. */
    @GameTest(template = "empty")
    public static void aGiftHandedToThePlayerStaysOnTheFloor(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity gift = drop(h, companion, Items.IRON_INGOT, 2);
        gift.getPersistentData().putBoolean("aicompanion:gift", true);
        gift.setTarget(companion.getOwnerId());
        // A plain drop lies next to it, so the pickup really runs and only the gift is refused.
        ItemEntity plain = drop(h, companion, Items.COBBLESTONE, 3);
        h.startSequence().thenExecuteAfter(PICKUP_TICKS, () -> {
            h.assertTrue(plain.isRemoved(), "The plain drop was not taken");
            h.assertTrue(!gift.isRemoved(), "He took back a gift he had handed to the player");
            h.assertTrue(stored(companion, Items.IRON_INGOT) == 0, "The gift reached his bag");
        }).thenSucceed();
    }

    /** A drop aimed at somebody else (another player's share) is theirs, not his. */
    @GameTest(template = "empty")
    public static void anItemAimedAtSomebodyElseStaysOnTheFloor(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity aimed = drop(h, companion, Items.IRON_INGOT, 2);
        aimed.setTarget(UUID.randomUUID());
        ItemEntity plain = drop(h, companion, Items.COBBLESTONE, 3);
        h.startSequence().thenExecuteAfter(PICKUP_TICKS, () -> {
            h.assertTrue(plain.isRemoved(), "The plain drop was not taken");
            h.assertTrue(!aimed.isRemoved(), "He took an item aimed at somebody else");
        }).thenSucceed();
    }

    /** A drop another companion harvested is that companion's: its gathering counters would lose it here. */
    @GameTest(template = "empty")
    public static void anotherCompanionsHarvestStaysOnTheFloor(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity foreign = drop(h, companion, Items.OAK_LOG, 3);
        foreign.getPersistentData().putUUID("aicompanion:harvester", UUID.randomUUID());
        ItemEntity plain = drop(h, companion, Items.COBBLESTONE, 3);
        h.startSequence().thenExecuteAfter(PICKUP_TICKS, () -> {
            h.assertTrue(plain.isRemoved(), "The plain drop was not taken");
            h.assertTrue(!foreign.isRemoved(), "He took another companion's harvest");
        }).thenSucceed();
    }

    /** A drop that is still in its pickup delay is left alone until the delay is over. */
    @GameTest(template = "empty")
    public static void aDelayedDropIsTakenOnlyAfterTheDelay(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity delayed = drop(h, companion, Items.OAK_LOG, 4);
        delayed.setPickUpDelay(10);
        h.startSequence().thenExecuteAfter(PICKUP_TICKS, () ->
                h.assertTrue(!delayed.isRemoved(), "A drop in its pickup delay was taken too early")
        ).thenExecuteAfter(20, () -> {
            h.assertTrue(delayed.isRemoved(), "The drop was never taken after its delay");
            h.assertTrue(stored(companion, Items.OAK_LOG) >= 4, "The delayed drop did not reach his bag");
        }).thenSucceed();
    }

    /** A stack on the ground next to him, thrown by the player — the very case the old rule refused. */
    private static ItemEntity drop(GameTestHelper h, CompanionEntity companion, Item item, int count) {
        ItemEntity drop = new ItemEntity(h.getLevel(), companion.getX() + 0.3D, companion.getY() + 0.1D, companion.getZ(),
                new ItemStack(item, count));
        var thrower = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        if (thrower != null) drop.setThrower(thrower);
        drop.setPickUpDelay(0);
        h.getLevel().addFreshEntity(drop);
        return drop;
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
        // Standing still keeps him on the drop for the few ticks the pickup needs; loose pickup does not need a task.
        companion.setFollowing(false);
        return companion;
    }
}

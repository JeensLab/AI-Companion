package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Review 22.10, М1 and М3. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSmallFixesTests {
    /** Cobblestone the player drops by Oleg's dig is the player's, not Oleg's harvest. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void cobblestoneThePlayerDroppedIsNotTaken(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 1; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : Blocks.STONE);
        CompanionEntity companion = spawn(h, 2);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var dropped = new ItemEntity(h.getLevel(), companion.getX() + .6, companion.getY() + .2, companion.getZ() + .6, new ItemStack(Items.COBBLESTONE, 5));
        dropped.setThrower(owner);
        h.getLevel().addFreshEntity(dropped);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.startWork("gather_stone", "", 1);
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking() && !companion.isGatheringTask(), "Still working: " + companion.getLastTaskResult());
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(dropped.isAlive() && dropped.getItem().getCount() == 5 && carried(companion, Items.COBBLESTONE) < 5, "He took the player's cobblestone");
        });
    }

    /** Reloaded in the middle of «наруби 4 бревна» with two already in the bag: two more, not four more. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void afterReloadOnlyTheRestIsChopped(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        for (int x = 18; x <= 28; x += 2) h.setBlock(new BlockPos(x, 1, 16), Blocks.OAK_LOG);
        CompanionEntity first = spawn(h, 1);
        first.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        first.startGatheringLogs(4);
        CompoundTag saved = new CompoundTag();
        first.saveWithoutId(saved);
        saved.putInt("LogsRemaining", 2);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(first.getOwnerId());
        first.discard();
        CompanionEntity loaded = AICompanionMod.COMPANION.get().create(h.getLevel());
        loaded.load(saved);
        h.getLevel().addFreshEntity(loaded);
        CompanionEvents.getOwnedCompanion(owner);
        h.succeedWhen(() -> {
            h.assertTrue(!loaded.isGatheringTask(), "Still gathering: " + loaded.getLastTaskResult());
            h.assertTrue(carried(loaded, Items.OAK_LOG) == 4, "Wanted 4 logs in all, has " + carried(loaded, Items.OAK_LOG));
        });
    }

    private static CompanionEntity spawn(GameTestHelper h, int y) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, y, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() - 4);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    private static int carried(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

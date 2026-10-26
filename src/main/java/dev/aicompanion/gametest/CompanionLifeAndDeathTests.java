package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Review 22.10, К1: a dead Oleg took his whole bag with him, and the player's record of him made a new one impossible forever.
 * He also never healed, so every scratch stayed until the end.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLifeAndDeathTests {
    @GameTest(template = "tall_tree", timeoutTicks = 20 * 40)
    public static void deathDropsEverythingAndHeComesBack(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 5));
        companion.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        companion.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        h.setBlock(new BlockPos(20, 1, 20), Blocks.CRAFTING_TABLE);
        companion.rememberPlaced(h.absolutePos(new BlockPos(20, 1, 20)));
        var oldId = companion.getUUID();
        companion.kill();
        h.runAtTickTime(5, () -> {
            h.assertTrue(dropped(h, Items.RAW_IRON) == 5 && dropped(h, Items.IRON_PICKAXE) == 1 && dropped(h, Items.IRON_HELMET) == 1
                    && dropped(h, Items.STONE_SWORD) == 1, "Belongings did not fall where he died");
            var still = CompanionEvents.getOwnedCompanion(owner);
            h.assertTrue(still == null, "A dead companion still counts as the living one: " + (still == null ? "" : still.getUUID().equals(oldId) + " alive=" + still.isAlive() + " removed=" + still.isRemoved() + " age=" + still.tickCount + " deeds=" + still.getDeedsForAi() + " pos=" + h.relativePos(still.blockPosition())));
        });
        h.succeedWhen(() -> {
            CompanionEntity back = CompanionEvents.getOwnedCompanion(owner);
            h.assertTrue(back != null && back.isAlive() && !back.getUUID().equals(oldId), "He did not come back");
            h.assertTrue(back.isEmpty() && back.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Came back with copies of the dropped things");
            h.assertTrue(back.getDeedsForAi().contains("Погиб"), "Forgot his own death: " + back.getDeedsForAi());
            h.assertTrue(back.getPlacesForAi().contains("crafting_table"), "Places forgotten: " + back.getPlacesForAi());
            h.assertTrue(dropped(h, Items.RAW_IRON) == 5, "Dropped belongings vanished or doubled");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void hurtHeEatsHisOwnFood(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.COOKED_MUTTON, 3));
        companion.setItem(1, new ItemStack(Items.ROTTEN_FLESH, 5));
        companion.setHealth(8);
        h.succeedWhen(() -> {
            h.assertTrue(companion.getHealth() >= 14, "Did not heal: " + companion.getHealth());
            h.assertTrue(count(companion, Items.COOKED_MUTTON) < 3 && count(companion, Items.ROTTEN_FLESH) == 5, "Ate the wrong food or none");
        });
    }

    private static int dropped(GameTestHelper h, Item item) {
        int count = 0;
        for (ItemEntity entity : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(new BlockPos(0, 0, 0))).inflate(40)))
            if (entity.isAlive() && entity.getItem().is(item)) count += entity.getItem().getCount();
        return count;
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 6, companion.getY(), companion.getZ() - 6);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setFollowing(false);
        return companion;
    }


    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        return count;
    }
}

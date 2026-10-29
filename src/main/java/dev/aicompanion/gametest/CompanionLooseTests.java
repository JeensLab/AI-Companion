package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Falling blocks (sand, red sand, gravel): dug with a real shovel, picked up from the ground, counted by the bag. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLooseTests {
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void digsSandWithAShovelAndPicksItUp(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        BlockPos[] patch = new BlockPos[25]; int index = 0;
        for (int x = 18; x <= 22; x++) for (int z = 19; z <= 23; z++) { patch[index] = new BlockPos(x, 1, z); helper.setBlock(patch[index++], Blocks.SAND); }
        var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var started = CompanionEvents.executeAction(player, "gather_loose", "minecraft:sand", 8);
        helper.assertTrue(started.success(), "Sand order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask(), diagnostic(companion));
            helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.SAND) >= 8, diagnostic(companion));
            helper.assertTrue(hasShovel(companion), "No shovel was prepared or carried");
            int left = 0; for (BlockPos at : patch) if (helper.getBlockState(at).is(Blocks.SAND)) left++;
            helper.assertTrue(left <= patch.length - 8, "Sand blocks are still in the world: " + left);
        });
    }

    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16); var player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5); companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID()); CompanionEvents.getOwnedCompanion(player); companion.setSearchRadius(16); return companion;
    }
    private static boolean hasShovel(CompanionEntity companion) {
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(ItemTags.SHOVELS)) return true;
        return companion.getMainHandItem().is(ItemTags.SHOVELS) || companion.getOffhandItem().is(ItemTags.SHOVELS);
    }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0; for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount(); return count;
    }
    private static String diagnostic(CompanionEntity companion) { return companion.getLastTaskResult() + " Bag=" + companion.getStoredItemsSummary() + " At=" + companion.position(); }
}

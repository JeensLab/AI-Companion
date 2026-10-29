package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** What counts toward a loose order (only the ordered item) and which stand cells a walk prefers (the free ones). */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLooseProgressTests {
    /** Fortune III turns every gravel block into flint: the flint is a bonus of a gravel order and is not counted for it. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void gravelOrderDoesNotCountTheFlintThatDropped(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        companion.setItem(1, enchantedShovel(helper));
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:gravel", 3);
        helper.assertTrue(started.success(), "Gravel order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(count(companion, Items.FLINT) >= 2, "No flint yet: " + companion.getStoredItemsSummary());
            helper.assertTrue(companion.planProgress("gather_loose") == 0, "Flint raised the gravel progress to " + companion.planProgress("gather_loose"));
        });
    }

    /** Gravel dug and picked up again on the way to the flint is not flint: the progress follows the flint in the bag exactly. */
    @GameTest(template = "tall_tree", timeoutTicks = 6500)
    public static void flintOrderCountsOnlyTheFlint(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:flint", 2);
        helper.assertTrue(started.success(), "Flint order rejected: " + started.message());
        String[] mismatch = {null};
        helper.onEachTick(() -> {
            int progress = companion.planProgress("gather_loose"), flint = count(companion, Items.FLINT);
            if (mismatch[0] == null && progress != flint) mismatch[0] = "Progress " + progress + " for " + flint + " flint";
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(mismatch[0] == null, String.valueOf(mismatch[0]));
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still working: " + companion.getLastTaskResult());
        });
    }

    @GameTest(template = "empty")
    public static void routePrefersFreeStandCellsAndKeepsThemWhenAllAreTaken(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 1, 1, 1);
        BlockPos taken = helper.absolutePos(new BlockPos(4, 1, 4)), free = helper.absolutePos(new BlockPos(6, 1, 4)), block = helper.absolutePos(new BlockPos(5, 1, 3));
        Map<BlockPos, BlockPos> stands = Map.of(taken, block, free, block);
        Mob first = helper.spawn(EntityType.PIG, 4, 1, 4); first.setNoAi(true);
        helper.assertTrue(CompanionRoute.unoccupied(companion, stands).keySet().equals(java.util.Set.of(free)), "The cell with an animal was kept beside a free one");
        Mob second = helper.spawn(EntityType.PIG, 6, 1, 4); second.setNoAi(true);
        helper.assertTrue(CompanionRoute.unoccupied(companion, stands).keySet().equals(stands.keySet()), "Taken cells were dropped although no free one is left");
        helper.succeed();
    }

    private static ItemStack enchantedShovel(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STONE_SHOVEL);
        stack.enchant(helper.getLevel().registryAccess().holderOrThrow(Enchantments.FORTUNE), 3);
        return stack;
    }

    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper helper, CompanionEntity companion) {
        return helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    /** The plot is cleared above the floor, a gravel patch in it: the template's tree must not cover or support any of it. */
    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int y = 1; y <= 12; y++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        for (int x = 18; x <= 22; x++) for (int z = 19; z <= 23; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.GRAVEL);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16); var player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5); companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID()); CompanionEvents.getOwnedCompanion(player); companion.setSearchRadius(16); return companion;
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0; for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount(); return count;
    }
}

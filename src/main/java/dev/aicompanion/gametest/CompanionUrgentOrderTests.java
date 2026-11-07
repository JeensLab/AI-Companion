package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * the player, issue #105: «стой»/«ко мне»/«защищай» in the middle of a dig is an urgent order, not the end of the dig. He puts the
 * rest of the work aside, does the order, and digs exactly what was left once he is free; a second work order meanwhile is refused
 * by name of the deed.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionUrgentOrderTests {
    private static final int WANTED = 6;
    private static final double ARRIVED = 3.0D;

    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void comeInterruptsStoneAndTheRestIsDugOnce(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        vein(helper);
        ServerPlayer player = owner(helper, companion);
        boolean[] called = {false};
        double[] minDist = {Double.MAX_VALUE};
        int[] left = {WANTED};
        helper.onEachTick(() -> {
            int now = veinLeft(helper);
            helper.assertTrue(now >= left[0] || !companion.isFollowing() || companion.isWorking(), "A block was dug while he was only following");
            left[0] = now;
            if (!called[0] && count(companion, Items.COBBLESTONE) >= 2) {
                called[0] = true;
                CompanionEvents.executeAction(player, "come", "", 1);
            }
            if (called[0]) minDist[0] = Math.min(minDist[0], companion.distanceTo(player));
        });
        helper.assertTrue(companion.startWork("gather_stone", "", WANTED).success(), "Stone work was not accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(called[0], "The dig has not gone far enough for the order yet");
            helper.assertTrue(minDist[0] <= ARRIVED, "He did not come to the player: closest " + minDist[0]);
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            helper.assertTrue(count(companion, Items.COBBLESTONE) == WANTED, "The rest of the dig was not done once: " + count(companion, Items.COBBLESTONE));
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void reloadMidParkResumesTheRest(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        vein(helper);
        ServerPlayer player = owner(helper, companion);
        CompanionEntity[] current = {companion};
        int[] stage = {0};
        helper.onEachTick(() -> {
            if (stage[0] != 0 || count(companion, Items.COBBLESTONE) < 2) return;
            stage[0] = 1;
            CompanionEvents.executeAction(player, "come", "", 1);
            int dug = count(companion, Items.COBBLESTONE);
            CompoundTag data = new CompoundTag();
            companion.saveWithoutId(data);
            helper.assertTrue("gather_stone".equals(data.getString("ParkedAction")), "The rest of the dig was not saved: " + data.getString("ParkedAction"));
            helper.assertTrue(data.getInt("ParkedCount") == WANTED - dug, "Saved remainder " + data.getInt("ParkedCount") + " after " + dug);
            companion.discard();
            helper.runAfterDelay(1, () -> {
                CompanionEntity replacement = AICompanionMod.COMPANION.get().create(helper.getLevel());
                replacement.load(data);
                helper.getLevel().addFreshEntity(replacement);
                current[0] = replacement;
                stage[0] = 2;
            });
        });
        helper.assertTrue(companion.startWork("gather_stone", "", WANTED).success(), "Stone work was not accepted");
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] == 2, "Not reloaded yet");
            CompanionEntity loaded = current[0];
            done(helper, loaded);
            helper.assertTrue(count(loaded, Items.COBBLESTONE) == WANTED, "The rest was not dug after the reload: " + count(loaded, Items.COBBLESTONE));
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void secondWorkOrderIsRefusedNamingTheDeed(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        vein(helper);
        helper.assertTrue(companion.startWork("gather_stone", "", 5).success(), "Stone work was not accepted");
        var refused = companion.startWork("gather_wood", "", 2);
        helper.assertTrue(!refused.success() && refused.message().startsWith("Погоди, доделаю добычу камня"),
                "Refusal does not name the deed: " + refused.message());
        helper.succeed();
    }

    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var owner = helper.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    private static void vein(GameTestHelper helper) {
        for (int z = 16; z < 22; z++) helper.setBlock(new BlockPos(20, 1, z), Blocks.STONE);
    }

    private static int veinLeft(GameTestHelper helper) {
        int left = 0;
        for (int z = 16; z < 22; z++) if (helper.getBlockState(new BlockPos(20, 1, z)).is(Blocks.STONE)) left++;
        return left;
    }

    private static int count(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(item)) count += container.getItem(slot).getCount();
        if (container instanceof CompanionEntity companion) for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }

    private static void done(GameTestHelper helper, CompanionEntity companion) {
        helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult() + " at " + companion.position());
    }

    private static ServerPlayer owner(GameTestHelper helper, CompanionEntity companion) {
        return helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

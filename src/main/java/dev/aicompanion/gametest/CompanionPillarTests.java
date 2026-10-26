package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionPillar;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real movement, finite materials, interruption and ownership checks for temporary tree supports. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPillarTests {
    private static final int TREE_HEIGHT = 9;
    private static final int SOIL_SUPPLY = 8;

    @GameTest(template = "tall_tree", timeoutTicks = 1000)
    public static void climbsTallTreeAndRecoversOwnSoil(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, true);
        double initialY = companion.getY();
        boolean[] climbed = {false};
        boolean[] placedSupports = {false};
        helper.onEachTick(() -> {
            climbed[0] |= companion.getY() > initialY + 1.0;
            placedSupports[0] |= companion.getTemporarySupportCount() > 0;
        });
        companion.startGatheringLogs(TREE_HEIGHT);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isRecoveringSupports(), diagnostic(companion));
            helper.assertTrue(remainingLogs(helper) == 0, "Tall trunk was not fully harvested: " + diagnostic(companion));
            helper.assertTrue(count(companion, Items.OAK_LOG) == TREE_HEIGHT, "Real harvested logs were lost or invented");
            helper.assertTrue(climbed[0] && placedSupports[0], "High trunk was harvested without a real visible climb");
            assertCleanGround(helper, companion, SOIL_SUPPLY);
            helper.assertTrue(companion.didLastTaskSucceed(), "Completed tall-tree harvest did not report success");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void stopDuringClimbStopsMiningAndRemovesSupports(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, true);
        long[] cancelledAt = {-1};
        int[] logsAfterStop = {-1};
        helper.onEachTick(() -> {
            if (cancelledAt[0] < 0 && companion.getTemporarySupportCount() >= 2) {
                companion.stopGathering();
                logsAfterStop[0] = remainingLogs(helper);
                cancelledAt[0] = helper.getTick();
            }
            if (cancelledAt[0] >= 0) {
                helper.assertTrue(remainingLogs(helper) == logsAfterStop[0], "Stop allowed another tree block to be mined");
            }
        });
        companion.startGatheringLogs(TREE_HEIGHT);
        helper.succeedWhen(() -> {
            helper.assertTrue(cancelledAt[0] >= 0, "Companion never started the climb: " + diagnostic(companion));
            helper.assertTrue(helper.getTick() - cancelledAt[0] >= 40, "Wait for the cancelled miner to settle");
            helper.assertTrue(!companion.isGatheringTask() && !companion.isRecoveringSupports(), diagnostic(companion));
            helper.assertTrue(logsAfterStop[0] > 0, "Cancellation did not happen before the whole trunk was mined");
            assertCleanGround(helper, companion, SOIL_SUPPLY);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 700)
    public static void noSoilSkipsUnreachableTreeWithoutInventingBlocks(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, false);
        boolean[] placedSupport = {false};
        helper.onEachTick(() -> placedSupport[0] |= companion.getTemporarySupportCount() != 0 || remainingDirt(helper) != 0);
        companion.startGatheringLogs(TREE_HEIGHT);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isRecoveringSupports(), "No-soil target stalled: " + diagnostic(companion));
            helper.assertTrue(helper.getTick() < 600, "Unreachable high logs took too long to skip");
            helper.assertTrue(remainingLogs(helper) > 0, "Tall tree was harvested without enough reach or soil");
            helper.assertTrue(!placedSupport[0] && count(companion, Items.DIRT) == 0, "Temporary soil was created for free");
            helper.assertTrue(!companion.didLastTaskSucceed(), "Partial unreachable gathering claimed full success");
            assertCleanGround(helper, companion, 0);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1000)
    public static void giantTreeStopsAtSixSupportsAndReturnsToGround(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, true);
        for (int y = 10; y <= 18; y++) helper.setBlock(new BlockPos(16, y, 16), Blocks.OAK_LOG);
        int[] maximumSupports = {0};
        helper.onEachTick(() -> {
            maximumSupports[0] = Math.max(maximumSupports[0], companion.getTemporarySupportCount());
            helper.assertTrue(maximumSupports[0] <= 6, "Pillar exceeded its bounded height");
        });
        companion.startGatheringLogs(18);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isRecoveringSupports(), diagnostic(companion));
            helper.assertTrue(maximumSupports[0] == 6, "Giant tree did not exercise the maximum climb: " + diagnostic(companion));
            helper.assertTrue(!companion.didLastTaskSucceed(), "An inaccessible crown was reported as fully harvested");
            helper.assertTrue(helper.getBlockState(new BlockPos(16, 18, 16)).is(Blocks.OAK_LOG), "Out-of-reach giant crown was destroyed");
            assertCleanGround(helper, companion, SOIL_SUPPLY);
        });
    }

    @GameTest(template = "empty")
    public static void supportOwnershipIsNotTransferredToAnotherDimension(GameTestHelper helper) {
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2);
        companion.setNoAi(true);
        BlockPos support = new BlockPos(3, 1, 3);
        helper.setBlock(support, Blocks.DIRT);
        CompoundTag saved = new CompoundTag();
        saved.putLong("PillarBase", helper.absolutePos(support).asLong());
        saved.putString("PillarDimension", "minecraft:the_nether");
        CompoundTag block = new CompoundTag();
        block.putLong("Pos", helper.absolutePos(support).asLong());
        block.putString("Block", "minecraft:dirt");
        ListTag supports = new ListTag();
        supports.add(block);
        saved.put("PillarSupports", supports);
        CompanionPillar controller = new CompanionPillar(companion);
        controller.load(saved);
        helper.assertTrue(!controller.hasSupports() && !controller.isRecovering() && controller.tickCleanup(), "Wrong-dimension ownership was accepted");
        helper.assertTrue(helper.getBlockState(support).is(Blocks.DIRT) && count(companion, Items.DIRT) == 0, "Foreign dirt was removed or refunded");
        helper.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void savedClimberRecoversSupportsAfterReload(GameTestHelper helper) {
        CompanionEntity original = setup(helper, true);
        CompanionEntity[] active = {original};
        boolean[] reloaded = {false};
        boolean[] restoring = {false};
        int[] logsAtReload = {-1};
        helper.onEachTick(() -> {
            if (!restoring[0] && active[0].getTemporarySupportCount() >= 2) {
                CompanionEntity old = active[0];
                CompoundTag saved = new CompoundTag();
                old.saveWithoutId(saved);
                restoring[0] = true;
                old.discard();
                logsAtReload[0] = remainingLogs(helper);
                // Real world loading preserves UUID. Wait for removal before reusing that identity.
                helper.runAfterDelay(1, () -> {
                    CompanionEntity replacement = AICompanionMod.COMPANION.get().create(helper.getLevel());
                    helper.assertTrue(replacement != null, "Could not restore companion");
                    replacement.load(saved);
                    helper.assertTrue(helper.getLevel().addFreshEntity(replacement) && !replacement.isRemoved(), "Reloaded companion was rejected");
                    active[0] = replacement;
                    reloaded[0] = true;
                });
            }
            if (reloaded[0]) {
                helper.assertTrue(remainingLogs(helper) == logsAtReload[0], "Reload resumed abandoned mining before cleanup");
            }
        });
        original.startGatheringLogs(TREE_HEIGHT);
        helper.succeedWhen(() -> {
            helper.assertTrue(reloaded[0], "Companion never reached a reloadable climb: " + diagnostic(active[0]));
            helper.assertTrue(!active[0].isGatheringTask() && !active[0].isRecoveringSupports(), diagnostic(active[0]));
            assertCleanGround(helper, active[0], SOIL_SUPPLY);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void replacedSupportIsNotDestroyedOrRefunded(GameTestHelper helper) {
        CompanionEntity companion = setup(helper, true);
        BlockPos[] foreignBlock = {null};
        helper.onEachTick(() -> {
            if (foreignBlock[0] == null && companion.getTemporarySupportCount() >= 2) {
                BlockPos support = lowestDirt(helper);
                helper.assertTrue(support != null, "Support count did not correspond to a real dirt block");
                helper.setBlock(support, Blocks.OBSIDIAN);
                foreignBlock[0] = support;
                companion.stopGathering();
            }
            if (foreignBlock[0] != null) {
                helper.assertTrue(helper.getBlockState(foreignBlock[0]).is(Blocks.OBSIDIAN), "Cleanup destroyed a player-replaced block");
            }
        });
        companion.startGatheringLogs(TREE_HEIGHT);
        helper.succeedWhen(() -> {
            helper.assertTrue(foreignBlock[0] != null, "Companion never placed two supports: " + diagnostic(companion));
            helper.assertTrue(!companion.isGatheringTask() && !companion.isRecoveringSupports(), diagnostic(companion));
            assertCleanGround(helper, companion, SOIL_SUPPLY - 1);
            helper.assertTrue(helper.getBlockState(foreignBlock[0]).is(Blocks.OBSIDIAN), "Player's replacement must remain");
        });
    }

    private static CompanionEntity setup(GameTestHelper helper, boolean giveSoil) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        for (int y = 1; y <= TREE_HEIGHT; y++) helper.setBlock(new BlockPos(16, y, 16), Blocks.OAK_LOG);
        // The crown is real collision-bearing foliage, with enough room below it for a short pillar.
        for (int y = 9; y <= 11; y++) {
            int radius = y == 11 ? 1 : 2;
            for (int x = 16 - radius; x <= 16 + radius; x++) for (int z = 16 - radius; z <= 16 + radius; z++) {
                if (x == 16 && z == 16 && y <= TREE_HEIGHT) continue;
                helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            }
        }
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 14, 1, 16);
        var owner = helper.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner); // Merge the login-created companion into this test's canonical entity.
        companion.setSearchRadius(16);
        companion.setItem(0, new ItemStack(Items.WOODEN_AXE));
        if (giveSoil) companion.setItem(1, new ItemStack(Items.DIRT, SOIL_SUPPLY));
        return companion;
    }

    private static int count(CompanionEntity companion, Item item) {
        int result = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) {
            ItemStack stack = companion.getItem(slot);
            if (stack.is(item)) result += stack.getCount();
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack stack = companion.getItemBySlot(slot);
            if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }

    private static int remainingLogs(GameTestHelper helper) {
        int count = 0;
        for (int y = 1; y <= TREE_HEIGHT; y++) if (helper.getBlockState(new BlockPos(16, y, 16)).is(Blocks.OAK_LOG)) count++;
        return count;
    }

    private static int remainingDirt(GameTestHelper helper) {
        int count = 0;
        for (int x = 10; x <= 22; x++) for (int z = 10; z <= 22; z++) for (int y = 1; y <= 13; y++) {
            if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) count++;
        }
        return count;
    }

    private static BlockPos lowestDirt(GameTestHelper helper) {
        for (int y = 1; y <= 13; y++) for (int x = 10; x <= 22; x++) for (int z = 10; z <= 22; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (helper.getBlockState(pos).is(Blocks.DIRT)) return pos;
        }
        return null;
    }

    private static void assertCleanGround(GameTestHelper helper, CompanionEntity companion, int expectedSoil) {
        helper.assertTrue(companion.getTemporarySupportCount() == 0, "Support ownership was not cleared");
        helper.assertTrue(remainingDirt(helper) == 0, "Temporary dirt was left in the world");
        helper.assertTrue(count(companion, Items.DIRT) == expectedSoil, "Soil was lost or duplicated: expected " + expectedSoil + ", found " + count(companion, Items.DIRT));
        helper.assertTrue(companion.onGround(), "Work completed while companion was still falling");
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            helper.assertTrue(helper.getBlockState(new BlockPos(x, 0, z)).is(Blocks.STONE), "Cleanup changed the original floor");
        }
    }

    private static String diagnostic(CompanionEntity companion) {
        return "gathering=" + companion.isGatheringTask() + ", recovering=" + companion.isRecoveringSupports()
                + ", removed=" + companion.isRemoved() + ", grounded=" + companion.onGround()
                + ", supports=" + companion.getTemporarySupportCount() + ", y=" + companion.getY()
                + ", report=" + companion.getLastTaskResult();
    }
}

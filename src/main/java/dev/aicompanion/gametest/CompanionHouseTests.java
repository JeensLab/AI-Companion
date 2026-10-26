package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionHouseTests {
    @GameTest(template = "tall_tree", timeoutTicks = 3200)
    public static void buildsWalkableHouseFromRealLogs(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 32));
        long[] firstFinished = {-1};
        helper.onEachTick(() -> {
            if (firstFinished[0] < 0 && !companion.isGatheringTask() && companion.didLastTaskSucceed()) {
                complete(helper, companion, "oak"); firstFinished[0] = helper.getTick(); start(helper, companion);
            }
        });
        start(helper, companion);
        helper.succeedWhen(() -> {
            helper.assertTrue(firstFinished[0] >= 0 && helper.getTick() - firstFinished[0] > 10, "Wait for repeated request");
            done(helper, companion);
            complete(helper, companion, "oak");
            helper.assertTrue(count(companion, Items.OAK_LOG) == 4, "House did not spend exactly 28 real logs");
            helper.assertTrue(count(companion, Items.OAK_PLANKS) == 1 && count(companion, Items.OAK_SLAB) == 5
                    && count(companion, Items.OAK_STAIRS) == 3 && count(companion, Items.OAK_DOOR) == 2, "Recipe yields were lost or duplicated");
            helper.assertTrue(countBlocks(helper, Blocks.CRAFTING_TABLE) == 1, "House workbench missing or duplicated");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3200)
    public static void usesJungleWoodAndReusesExistingWorkbench(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItem(0, new ItemStack(Items.JUNGLE_LOG, 32));
        helper.setBlock(new BlockPos(17, 1, 16), Blocks.CRAFTING_TABLE);
        start(helper, companion);
        helper.succeedWhen(() -> {
            done(helper, companion);
            complete(helper, companion, "jungle");
            helper.assertTrue(count(companion, Items.JUNGLE_LOG) == 5 && countBlocks(helper, Blocks.CRAFTING_TABLE) == 1, "Existing table not reused or wrong wood cost");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6500)
    public static void gathersMissingWoodBeforeBuilding(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        for (int x : new int[]{21, 24, 27}) for (int z : new int[]{7, 11, 15, 19, 23}) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        start(helper, companion);
        helper.succeedWhen(() -> {
            done(helper, companion);
            complete(helper, companion, "oak");
            helper.assertTrue(countBlocks(helper, Blocks.OAK_LOG) < 17, "House resources did not come from real trees");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 4200)
    public static void stopAndReloadResumeSameHouseWithoutExtraMaterials(GameTestHelper helper) {
        CompanionEntity original = setup(helper);
        original.setItem(0, new ItemStack(Items.OAK_LOG, 32));
        CompanionEntity[] current = {original};
        long[] stopped = {-1};
        int[] blocksAtStop = {0};
        BlockPos[] firstBase = {null};
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && saved(current[0]).getCompound("SmallHouse").getLongArray("Placed").length > 0
                    && countBlocks(helper, Blocks.OAK_PLANKS) >= 12) {
                current[0].stopGathering();
                stopped[0] = helper.getTick(); blocksAtStop[0] = countBlocks(helper, Blocks.OAK_PLANKS); firstBase[0] = base(current[0]);
                CompoundTag data = new CompoundTag(); original.saveWithoutId(data); original.discard();
                helper.runAfterDelay(1, () -> {
                    CompanionEntity replacement = AICompanionMod.COMPANION.get().create(helper.getLevel());
                    replacement.load(data); helper.getLevel().addFreshEntity(replacement); current[0] = replacement;
                });
            }
            if (stopped[0] >= 0 && helper.getTick() - stopped[0] == 30) {
                helper.assertTrue(countBlocks(helper, Blocks.OAK_PLANKS) == blocksAtStop[0], "Blocks placed after cancellation/reload");
                start(helper, current[0]);
            }
        });
        start(helper, original);
        helper.succeedWhen(() -> {
            helper.assertTrue(stopped[0] >= 0 && helper.getTick() - stopped[0] > 40, "Not resumed yet");
            done(helper, current[0]); complete(helper, current[0], "oak");
            helper.assertTrue(base(current[0]).equals(firstBase[0]), "Resuming selected another building site");
            helper.assertTrue(count(current[0], Items.OAK_LOG) == 4, "Resume consumed extra wood");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3200)
    public static void playerObstructionIsNotDestroyed(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 32));
        BlockPos[] obstruction = {null};
        helper.onEachTick(() -> {
            if (obstruction[0] == null && countBlocks(helper, Blocks.OAK_PLANKS) >= 5) {
                obstruction[0] = base(companion).offset(2, 3, 0);
                helper.getLevel().setBlock(obstruction[0], Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            }
        });
        start(helper, companion);
        helper.succeedWhen(() -> {
            helper.assertTrue(obstruction[0] != null, "Obstruction not added yet"); done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed() && helper.getLevel().getBlockState(obstruction[0]).is(Blocks.DIAMOND_BLOCK), "Building destroyed player block or falsely succeeded");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void missingMaterialsDoNotProduceFreeBuildingOrWorkbench(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 1));
        start(helper, companion);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) == 1, "Failed preparation consumed materials or claimed success");
            helper.assertTrue(countBlocks(helper, Blocks.OAK_PLANKS) == 0 && countBlocks(helper, Blocks.CRAFTING_TABLE) == 0, "Unfunded building created blocks");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void occupiedGroundIsPreservedAndHouseProtocolIsValidated(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        // Every possible footprint intersects an existing block.
        for (int x = 1; x < 32; x += 4) for (int z = 1; z < 32; z += 4) helper.setBlock(new BlockPos(x, 1, z), Blocks.GOLD_BLOCK);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 32));
        helper.assertTrue("build_house".equals(CompanionWorkCommands.parse("Олег, построй нам небольшой деревянный дом", "Олег").type()), "House command not recognized");
        helper.assertTrue("build_house".equals(CompanionWorkCommands.parse("дострой дом", "Олег").type()), "Resume not recognized");
        helper.assertTrue(CompanionWorkCommands.parse("не построй дом", "Олег") == null && CompanionWorkCommands.parse("построй дом и вернись", "Олег") == null, "Partial/negative command accepted");
        helper.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\"}]")).size() == 1, "House model protocol rejected");
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"count\":2}]")); } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Unsupported multiple houses accepted");
        start(helper, companion);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) == 32, "Occupied site used or inventory changed");
            helper.assertTrue(countBlocks(helper, Blocks.GOLD_BLOCK) == 64 && countBlocks(helper, Blocks.OAK_PLANKS) == 0, "Existing blocks were removed");
        });
    }

    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = helper.makeMockServerPlayerInLevel(); player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player); companion.setSearchRadius(16);
        return companion;
    }
    private static void start(GameTestHelper helper, CompanionEntity companion) { helper.assertTrue(companion.startWork("build_house", "", 1).success(), "House work rejected"); }
    private static void done(GameTestHelper helper, CompanionEntity companion) { helper.assertTrue(!companion.isGatheringTask(), companion.getLastTaskResult()); }
    private static CompoundTag saved(CompanionEntity companion) { CompoundTag tag = new CompoundTag(); companion.addAdditionalSaveData(tag); return tag; }
    private static BlockPos base(CompanionEntity companion) { return BlockPos.of(saved(companion).getCompound("SmallHouse").getLong("Base")); }
    private static int count(Container bag, Item item) {
        int count = 0; for (int slot = 0; slot < bag.getContainerSize(); slot++) if (bag.getItem(slot).is(item)) count += bag.getItem(slot).getCount();
        if (bag instanceof CompanionEntity companion) for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
    private static int countBlocks(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
        int count = 0; for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 8, 31))) if (helper.getBlockState(pos).is(block)) count++; return count;
    }
    private static void complete(GameTestHelper helper, CompanionEntity companion, String wood) {
        helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult() + " Bag: " + companion.getStoredItemsSummary() + " Position: " + helper.relativePos(companion.blockPosition()));
        BlockPos base = base(companion);
        var level = helper.getLevel();
        var planks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(wood + "_planks"));
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) helper.assertTrue(level.getBlockState(base.offset(x, 0, z)).is(planks), "Floor is incomplete");
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) for (int y = 1; y <= 3; y++) helper.assertTrue(level.getBlockState(base.offset(x, y, z)).isAir(), "Interior was filled in");
        helper.assertTrue(level.getBlockState(base.offset(0, 2, 2)).isAir() && level.getBlockState(base.offset(4, 2, 2)).isAir() && level.getBlockState(base.offset(2, 2, 4)).isAir(), "Windows blocked");
        var door = level.getBlockState(base.offset(2, 1, 0)); var upper = level.getBlockState(base.offset(2, 2, 0));
        helper.assertTrue(door.getBlock() instanceof DoorBlock && door.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && upper.is(door.getBlock()) && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "Door is incomplete");
        helper.assertTrue(countBlocks(helper, planks) == 68, "Walls/floor not complete or extra buildings appeared");
        helper.assertTrue(saved(companion).getCompound("SmallHouse").getLongArray("Placed").length > 0, "Build progress not saved");
    }
}

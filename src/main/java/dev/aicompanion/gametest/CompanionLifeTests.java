package dev.aicompanion.gametest;

import com.google.gson.JsonObject;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionIdentity;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionLogSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLifeTests {
    @GameTest(template = "empty")
    public static void nameAndRoamingPreferencesPersist(GameTestHelper helper) {
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2);
        companion.setOwnerId(UUID.randomUUID());
        companion.setCompanionName("Рыжик");
        companion.setSearchRadius(80);
        companion.startRoaming();
        helper.assertTrue("Рыжик".equals(companion.getName().getString()) && companion.isRoaming() && !companion.isFollowing(), "Identity or independent mode missing");
        CompoundTag tag = new CompoundTag();
        companion.addAdditionalSaveData(tag);
        CompanionEntity restored = AICompanionMod.COMPANION.get().create(helper.getLevel());
        restored.readAdditionalSaveData(tag);
        helper.assertTrue("Рыжик".equals(restored.getName().getString()) && restored.getSearchRadius() == 80 && restored.isRoaming(), "Name or roaming lost after reload");
        restored.startGatheringLogs(2);
        helper.assertTrue(!restored.isRoaming() && restored.isGatheringTask(), "New task must replace independent roaming");
        restored.stopGathering();
        helper.assertTrue(!restored.isRoaming() && !restored.isGatheringTask(), "Stop must end all work");
        helper.assertTrue(CompanionIdentity.isNameQuestion("Привет, как тебя зовут?") && "Олег".equals(CompanionIdentity.cleanName("\n§")), "Identity sanitization failed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dialogueDoesNotExposePlansOrInventory(GameTestHelper helper) {
        JsonObject model = new JsonObject();
        model.addProperty("content", "Окей, схожу за деревом.\n[Запланировано, ещё не выполнено: [PlannedAction[type=gather_wood, count=16]]]");
        String spoken = CompanionPlanCodec.decode(model).text();
        helper.assertTrue("Окей, схожу за деревом.".equals(spoken), "Internal record leaked into dialogue");
        PlannedAction wood = new PlannedAction("gather_wood", 0, 16);
        PlannedAction sword = new PlannedAction("craft_item", 0, 1, "minecraft:wooden_sword");
        String ack = CompanionDialogue.acknowledge(List.of(wood, sword));
        helper.assertTrue(!ack.contains("план") && !ack.contains("Результат"), "Acknowledgement is bureaucratic");
        String report = "Сбор завершён раньше цели: срубил 5 из 16 брёвен.\nСкрафтил 1 Wooden Sword. Цепочка: 4 Jungle Planks. В сумке: 3 Jungle Log.";
        spoken = CompanionDialogue.summarize(List.of(wood, sword), report);
        helper.assertTrue("Меч готов.".equals(spoken) && !spoken.contains("Цепочка"), "Completion report is not natural");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void incompleteWoodGatherCanStillCraftTools(GameTestHelper helper) {
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 5));
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        PlannedAction wood = new PlannedAction("gather_wood", 0, 16);
        PlannedAction pickaxe = new PlannedAction("craft_item", 0, 1, "minecraft:wooden_pickaxe");
        helper.assertTrue(CompanionDialogue.continueAfterPartialGather(wood, List.of(pickaxe), false), "Partial gather incorrectly stops crafting");
        helper.assertTrue(!CompanionDialogue.continueAfterPartialGather(wood, List.of(), false), "Pure failed gathering must not report success");
        helper.assertTrue(companion.craftItemResult("minecraft:wooden_pickaxe", 1).success(), "Existing five logs did not produce tool");
        helper.succeed();
    }

    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void independentlyWalksToTreeFiftyBlocksAway(GameTestHelper helper) {
        for (int x = 60; x < 123; x++) for (int z = 60; z < 69; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        BlockPos tree = new BlockPos(118, 1, 64);
        helper.setBlock(tree, Blocks.OAK_LOG);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 64, 1, 64);
        var owner = helper.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX(), companion.getY(), companion.getZ());
        companion.setOwnerId(owner.getUUID());
        companion.setSearchRadius(64);
        var small = CompanionLogSearch.find(helper.getLevel(), companion.blockPosition(), 16);
        var large = CompanionLogSearch.find(helper.getLevel(), companion.blockPosition(), 64);
        helper.assertTrue(!small.contains(helper.absolutePos(tree)) && large.contains(helper.absolutePos(tree)), "Search radius does not reach distant tree");
        companion.startGatheringLogs(1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask(), "Distant gathering still active");
            helper.assertTrue(helper.getBlockState(tree).isAir(), "Distant log was not harvested: " + companion.getLastTaskResult() + " at " + helper.relativePos(companion.blockPosition()));
            helper.assertTrue(companion.distanceToSqr(owner) > 32 * 32, "Companion teleported back instead of walking independently");
            helper.assertTrue(companion.didLastTaskSucceed(), "Distant gathering failed");
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 1000)
    public static void freelyRoamsAndReturnsWhenCalled(GameTestHelper helper) {
        for (int x = 29; x < 100; x++) for (int z = 29; z < 100; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 64, 1, 64);
        var owner = helper.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX(), companion.getY(), companion.getZ());
        companion.setOwnerId(owner.getUUID());
        companion.setSearchRadius(32);
        companion.getRandom().setSeed(42);
        companion.startRoaming();
        boolean[] recalled = {false};
        helper.succeedWhen(() -> {
            if (!recalled[0] && companion.distanceToSqr(owner) > 20 * 20) {
                companion.setFollowing(true);
                recalled[0] = true;
            }
            var path = companion.getNavigation().getPath();
            helper.assertTrue(recalled[0], "Companion never explored more than twenty blocks away: position="
                    + helper.relativePos(companion.blockPosition()) + ", owner=" + helper.relativePos(owner.blockPosition())
                    + ", roaming=" + companion.isRoaming() + ", path=" + (path == null ? "none" : path.getTarget())
                    + ", surfaceY=" + helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    companion.blockPosition().getX() + 10, companion.blockPosition().getZ()));
            helper.assertTrue(!companion.isRoaming() && companion.isFollowing(), "Calling did not replace roaming");
            helper.assertTrue(companion.distanceToSqr(owner) < 6 * 6, "Companion did not walk back after being called");
        });
    }
}

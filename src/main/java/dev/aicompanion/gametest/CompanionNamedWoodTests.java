package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** «Наруби берёзы»: a named species is the only wood he looks for, counts and takes; no name is the nearest tree as before. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionNamedWoodTests {
    private static final BlockPos NEAR_OAK = new BlockPos(18, 1, 16), FAR_BIRCH = new BlockPos(6, 1, 16);

    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void birchAskedTheNearerOakStands(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        trunk(h, NEAR_OAK, Blocks.OAK_LOG); trunk(h, FAR_BIRCH, Blocks.BIRCH_LOG);
        h.assertTrue(order(h, companion, "minecraft:birch_log", 2).success(), "Order not accepted");
        h.succeedWhen(() -> {
            idle(h, companion);
            h.assertTrue(count(companion, Items.BIRCH_LOG) >= 2 && count(companion, Items.OAK_LOG) == 0 && companion.didLastTaskSucceed(),
                    companion.getLastTaskResult() + " Bag=" + companion.getStoredItemsSummary());
            h.assertTrue(trunkLeft(h, NEAR_OAK, Blocks.OAK_LOG) == 3, "The oak was touched");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void birchAskedOnlyOaksRefusesAndNamesIt(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        trunk(h, NEAR_OAK, Blocks.OAK_LOG);
        h.assertTrue(order(h, companion, "minecraft:birch_log", 2).success(), "Order not accepted");
        h.succeedWhen(() -> {
            idle(h, companion);
            h.assertTrue(companion.getLastTaskResult().contains("Берёз") && !companion.didLastTaskSucceed(), "Refusal does not name birch: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.OAK_LOG) == 0 && trunkLeft(h, NEAR_OAK, Blocks.OAK_LOG) == 3, "An oak was chopped for a birch order");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void noSpeciesTakesTheNearestTree(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        trunk(h, NEAR_OAK, Blocks.OAK_LOG); trunk(h, FAR_BIRCH, Blocks.BIRCH_LOG);
        h.assertTrue(order(h, companion, "", 2).success(), "Order not accepted");
        h.succeedWhen(() -> {
            idle(h, companion);
            h.assertTrue(count(companion, Items.OAK_LOG) >= 2 && count(companion, Items.BIRCH_LOG) == 0 && companion.didLastTaskSucceed(),
                    companion.getLastTaskResult() + " Bag=" + companion.getStoredItemsSummary());
        });
    }

    @GameTest(template = "empty")
    public static void speciesSurvivesTheSplitAndOnlyWoodNamesAreValid(GameTestHelper h) {
        List<PlannedAction> plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"gather_wood\",\"item\":\"minecraft:birch_log\",\"count\":20}]"));
        h.assertTrue(plan.size() == 2 && plan.stream().allMatch(step -> step.item().equals("minecraft:birch_log")) && plan.stream().mapToInt(PlannedAction::count).sum() == 20,
                "Species lost in the split: " + plan);
        for (String bad : List.of("minecraft:stone", "minecraft:bamboo_block", "minecraft:birch_planks", "birch")) {
            boolean rejected = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"gather_wood\",\"item\":\"" + bad + "\",\"count\":2}]")); } catch (IllegalStateException expected) { rejected = true; }
            h.assertTrue(rejected, bad + " accepted as a wood species");
        }
        // Any item of the species tag names it; no item is any log.
        h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"gather_wood\",\"item\":\"minecraft:stripped_birch_log\",\"count\":2},{\"action\":\"gather_wood\",\"count\":2}]")).size() == 2, "Stripped log or empty item refused");
        h.succeed();
    }

    /** An urgent order parks the rest of the chopping: the species goes with it, or the resumed order would take any tree. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void parkedOrderKeepsTheSpecies(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        trunk(h, FAR_BIRCH, Blocks.BIRCH_LOG);
        h.assertTrue(order(h, companion, "minecraft:birch_log", 3).success(), "Order not accepted");
        h.runAfterDelay(20, () -> {
            h.assertTrue(companion.parkWork(), "Nothing parked");
            CompoundTag tag = new CompoundTag();
            companion.saveWithoutId(tag);
            h.assertTrue("minecraft:birch_log".equals(tag.getString("ParkedItem")), "Parked item: '" + tag.getString("ParkedItem") + "'");
            h.succeed();
        });
    }

    /** «Добудь берёзовые брёвна» names the species; the logs inside a craft chain stay any logs. */
    @GameTest(template = "tall_tree")
    public static void obtainedLogsKeepTheirSpeciesOnlyAtTheTop(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new net.minecraft.world.item.ItemStack(Items.OAK_LOG, 5));
        var top = companion.obtainPlan("minecraft:birch_log", 3);
        h.assertTrue(top.steps().size() == 1 && top.steps().get(0).type().equals("gather_wood") && top.steps().get(0).item().equals("minecraft:birch_log")
                && top.steps().get(0).count() == 3, "Top-level species lost (oaks in the bag are no birch): " + top);
        var chain = companion.obtainPlan("minecraft:birch_planks", 40);
        h.assertTrue(chain.steps().stream().filter(step -> step.type().equals("gather_wood")).allMatch(step -> step.item().isEmpty()), "Wood in a chain named a species: " + chain);
        h.succeed();
    }

    private static CompanionActionResult order(GameTestHelper h, CompanionEntity companion, String item, int count) {
        ServerPlayer owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        return CompanionEvents.executeAction(owner, "gather_wood", item, count);
    }
    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }
    private static void trunk(GameTestHelper h, BlockPos base, Block log) {
        for (int y = 0; y < 3; y++) h.setBlock(base.above(y), log);
    }
    private static int trunkLeft(GameTestHelper h, BlockPos base, Block log) {
        int left = 0; for (int y = 0; y < 3; y++) if (h.getBlockState(base.above(y)).is(log)) left++; return left;
    }
    private static void idle(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking() && !companion.isRecoveringSupports(), "Work still active: " + companion.getLastTaskResult());
    }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

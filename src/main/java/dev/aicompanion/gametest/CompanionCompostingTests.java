package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** «Сделай костную муку в компостере»: plant scrap of his bag goes into a composter, the bone meal comes back into the bag. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCompostingTests {
    /**
     * A composter eight blocks away, scrap in the bag: he walks there and makes two bone meal. The bread is food and stays, and
     * of the saplings and the seeds the reserve for his own planting stays.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void scrapBecomesBoneMealAtAComposterNearby(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(24, 1, 16), Blocks.COMPOSTER);
        companion.setItem(0, new ItemStack(Items.OAK_SAPLING, 64)); companion.setItem(1, new ItemStack(Items.WHEAT_SEEDS, 64));
        companion.setItem(2, new ItemStack(Items.BREAD, 5)); companion.setItem(3, new ItemStack(Items.OAK_LEAVES, 64));
        CompanionActionResult started = CompanionEvents.executeAction(owner(h, companion), "compost_items", "scrap", 2);
        h.assertTrue(started.success(), "Order refused: " + started.message());
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("костную муку: 2 шт."), companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.BONE_MEAL) == 2, "Bone meal in the bag: " + companion.getStoredItemsSummary());
            h.assertTrue(count(companion, Items.BREAD) == 5 && count(companion, Items.OAK_SAPLING) >= 4 && count(companion, Items.WHEAT_SEEDS) >= 16,
                    "Food or the reserve went in: " + companion.getStoredItemsSummary());
        });
    }

    /** Only the reserve is left of the scrap: with nothing named he puts nothing in; the saplings named by the player all go in. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void theReserveStaysUnlessThePlayerNamesIt(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos composter = new BlockPos(18, 1, 16);
        h.setBlock(composter, Blocks.COMPOSTER);
        companion.setItem(0, new ItemStack(Items.OAK_SAPLING, 4)); companion.setItem(1, new ItemStack(Items.WHEAT_SEEDS, 16));
        CompanionActionResult scrap = CompanionEvents.executeAction(owner(h, companion), "compost_items", "scrap", 0);
        h.assertTrue(!scrap.success() && scrap.message().contains("берегу"), "The reserve was offered: " + scrap.message());
        h.assertTrue(!CompanionEvents.executeAction(owner(h, companion), "compost_items", "minecraft:cobblestone", 64).success(), "Stone went into a composter");
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "compost_items", "saplings", 0).success(), "Named saplings refused");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.OAK_SAPLING) == 0 && count(companion, Items.WHEAT_SEEDS) == 16,
                    companion.getLastTaskResult() + " | " + companion.getStoredItemsSummary());
            h.assertTrue(h.getBlockState(composter).getValue(ComposterBlock.LEVEL) >= 1, "Nothing in the composter");
        });
    }

    /** A full composter is emptied first, and that bone meal counts: one asked, one taken, the saplings untouched. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aFullComposterIsEmptiedFirst(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos composter = new BlockPos(18, 1, 16);
        h.setBlock(composter, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, ComposterBlock.READY));
        companion.setItem(0, new ItemStack(Items.OAK_SAPLING, 64));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "compost_items", "scrap", 1).success(), "Order refused");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.BONE_MEAL) == 1 && count(companion, Items.OAK_SAPLING) == 64,
                    companion.getLastTaskResult() + " | " + companion.getStoredItemsSummary());
            h.assertTrue(h.getBlockState(composter).getValue(ComposterBlock.LEVEL) == 0, "The composter was not emptied");
        });
    }

    /**
     * Two composters by him: the nearest is full, the other empty and the first his look meets. He takes the bone meal of the
     * full one, the one he chose, and puts nothing into the other (review 10.11).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void theChosenComposterIsWorkedNotTheFirstSeen(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos full = new BlockPos(17, 1, 16), empty = new BlockPos(14, 1, 16);
        h.setBlock(full, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, ComposterBlock.READY));
        h.setBlock(empty, Blocks.COMPOSTER);
        companion.setItem(0, new ItemStack(Items.OAK_SAPLING, 64));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "compost_items", "scrap", 1).success(), "Order refused");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.BONE_MEAL) == 1 && count(companion, Items.OAK_SAPLING) == 64,
                    companion.getLastTaskResult() + " | " + companion.getStoredItemsSummary());
            h.assertTrue(h.getBlockState(empty).getValue(ComposterBlock.LEVEL) == 0, "The other composter was filled");
        });
    }

    /**
     * The composter he left full a moment ago is broken before it ripens; the empty one he goes on to is no sign of a hopper, and
     * no bone meal is counted that nobody made (review 10.11, round 2).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aBrokenFullComposterIsNoHopper(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos full = new BlockPos(17, 1, 16), other = new BlockPos(14, 1, 16);
        h.setBlock(full, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, ComposterBlock.MAX_LEVEL));
        h.setBlock(other, Blocks.COMPOSTER);
        companion.setItem(0, new ItemStack(Items.OAK_SAPLING, 64)); companion.setItem(1, new ItemStack(Items.OAK_SAPLING, 64));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "compost_items", "scrap", 1).success(), "Order refused");
        h.runAfterDelay(8, () -> h.setBlock(full, Blocks.AIR));
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && !companion.getLastTaskResult().contains("воронка") && count(companion, Items.BONE_MEAL) == 1,
                    companion.getLastTaskResult() + " | " + companion.getStoredItemsSummary());
        });
    }

    /** No composter near: his own is made from the slabs in his bag at the bench, set down and left; with no slabs he says so. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void hisOwnComposterIsMadeFromTheBag(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.OAK_SAPLING, 64)); companion.setItem(1, new ItemStack(Items.OAK_SAPLING, 64));
        CompanionActionResult none = CompanionEvents.executeAction(owner(h, companion), "compost_items", "scrap", 1);
        h.assertTrue(!none.success() && none.message().contains("сделать не из чего"), "No composter and nothing to make it of: " + none.message());
        h.setBlock(new BlockPos(18, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItem(2, new ItemStack(Items.OAK_SLAB, 7));
        CompanionActionResult started = CompanionEvents.executeAction(owner(h, companion), "compost_items", "scrap", 1);
        h.assertTrue(started.success(), "Order refused: " + started.message());
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.BONE_MEAL) == 1, companion.getLastTaskResult() + " | " + companion.getStoredItemsSummary());
            h.assertTrue(blocks(h, Blocks.COMPOSTER) == 1 && count(companion, Items.OAK_SLAB) == 0, "His composter: " + blocks(h, Blocks.COMPOSTER));
        });
    }

    /**
     * A hopper under the composter takes the bone meal before he can: it still counts toward the one asked for, so he stops
     * there instead of burning all his scrap, and says where it went.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void boneMealTakenByAHopperStillCounts(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.HOPPER);
        h.setBlock(new BlockPos(18, 2, 16), Blocks.COMPOSTER);
        companion.setItem(0, new ItemStack(Items.OAK_LEAVES, 64)); companion.setItem(1, new ItemStack(Items.OAK_LEAVES, 64)); companion.setItem(2, new ItemStack(Items.OAK_LEAVES, 64));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "compost_items", "scrap", 1).success(), "Order refused");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("воронка"), companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.OAK_LEAVES) >= 64 && count(companion, Items.BONE_MEAL) == 0, "All the scrap was burnt: " + companion.getStoredItemsSummary());
        });
    }

    /** The plain phrases are read without the model; «сделай костную муку» alone stays the craft from bones; the plan takes the action. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void thePhrasesAndThePlanAreRead(GameTestHelper h) {
        h.assertTrue("companion work compost_items 0 scrap".equals(CompanionLocalCommands.route("Олег, сделай костную муку в компостере", "Олег")), "No number");
        h.assertTrue("companion work compost_items 3 scrap".equals(CompanionLocalCommands.route("сделай 3 костной муки в компостере", "Олег")), "A number");
        h.assertTrue("companion work compost_items 0 saplings".equals(CompanionLocalCommands.route("закинь саженцы в компостер", "Олег")), "Saplings");
        h.assertTrue(CompanionLocalCommands.route("положи 0 семян в компостер", "Олег") == null || !CompanionLocalCommands.route("положи 0 семян в компостер", "Олег").contains("compost_items"), "A zero became an order");
        h.assertTrue("companion work compost_items 10 seeds".equals(CompanionLocalCommands.route("положи 10 семян в компостер", "Олег")), "Ten seeds");
        String bones = CompanionLocalCommands.route("сделай костную муку", "Олег");
        h.assertTrue(bones == null || !bones.contains("compost_items"), "Bones became compost: " + bones);
        String bread = CompanionLocalCommands.route("закинь хлеб в компостер", "Олег");
        h.assertTrue(bread == null || !bread.contains("compost_items"), "A thing by its name is for the model: " + bread);
        var steps = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"compost_items\",\"item\":\"scrap\",\"count\":2},{\"action\":\"compost_items\",\"item\":\"saplings\"}]"));
        h.assertTrue(steps.size() == 2 && "compost_items".equals(steps.getFirst().type()) && steps.getFirst().count() == 2 && steps.getLast().count() == 0, "Decoded: " + steps);
        boolean refused;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"compost_items\",\"item\":\"everything\"}]")); refused = false; } catch (IllegalStateException invalid) { refused = true; }
        h.assertTrue(refused, "A made-up group passed the codec");
        h.succeed();
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        return companion;
    }
    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
    }
    private static int blocks(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31))) if (h.getBlockState(pos).is(block)) result++;
        return result;
    }
    private static int count(CompanionEntity companion, Item item) {
        int result = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) result += companion.getItem(slot).getCount();
        return result;
    }
}

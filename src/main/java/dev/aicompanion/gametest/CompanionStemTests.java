package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.aicompanion.gametest.CompanionFarmTests.blocks;
import static dev.aicompanion.gametest.CompanionFarmTests.count;
import static dev.aicompanion.gametest.CompanionFarmTests.give;
import static dev.aicompanion.gametest.CompanionFarmTests.ground;
import static dev.aicompanion.gametest.CompanionFarmTests.lying;
import static dev.aicompanion.gametest.CompanionFarmTests.order;
import static dev.aicompanion.gametest.CompanionFarmTests.pond;
import static dev.aicompanion.gametest.CompanionFarmTests.result;
import static dev.aicompanion.gametest.CompanionFarmTests.spawn;

/**
 * Pumpkins and melons, as a player grows them (the player 16.11): the stems on farmland every other row, so each has free soil
 * beside it where its fruit grows; the seeds from the bag, else made of a pumpkin or a melon slice he carries. A harvest breaks the
 * fruit at its stem and leaves the stem to bear again.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionStemTests {
    private static final int GROUND = 5;

    /** Eight pumpkin stems by the pond: each on farmland, each with free soil beside it for the pumpkin, the seeds all sown. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void pumpkinStemsAreSownEveryOtherRow(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.PUMPKIN_SEEDS, 8));
        long id = order(h, companion, "farm_crops", "minecraft:pumpkin_seeds", 8);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            List<BlockPos> stems = blocks(h, GROUND + 1, Blocks.PUMPKIN_STEM);
            h.assertTrue(stems.size() == 8, "Stems " + stems.size() + ": " + result.message());
            for (BlockPos stem : stems) {
                h.assertTrue(h.getBlockState(stem.below()).is(Blocks.FARMLAND), "A stem off farmland: " + stem);
                h.assertTrue(roomBeside(h, stem), "No free soil beside the stem for its pumpkin: " + stem);
            }
            h.assertTrue(count(companion, Items.PUMPKIN_SEEDS) == 0 && lying(h).isEmpty(), "Seeds left " + count(companion, Items.PUMPKIN_SEEDS) + ", lying " + lying(h));
            h.assertTrue(result.message().contains("тыкву") && result.message().contains("8"), "Report: " + result.message());
        });
    }

    /** No seeds, two pumpkins: one is made into four seeds for the four stems, the other stays a pumpkin. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void pumpkinSeedsAreMadeOfAPumpkinHeCarries(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.PUMPKIN, 2));
        long id = order(h, companion, "farm_crops", "minecraft:pumpkin_seeds", 4);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success() && blocks(h, GROUND + 1, Blocks.PUMPKIN_STEM).size() == 4, "Stems " + blocks(h, GROUND + 1, Blocks.PUMPKIN_STEM).size() + ": " + result.message());
            h.assertTrue(count(companion, Items.PUMPKIN) == 1 && count(companion, Items.PUMPKIN_SEEDS) == 0, "Pumpkins " + count(companion, Items.PUMPKIN) + ", seeds " + count(companion, Items.PUMPKIN_SEEDS));
            h.assertTrue(result.message().contains("Семян сделал сам: 4"), "Report: " + result.message());
        });
    }

    /** Three melon slices for four stems: three seeds made, three stems sown, and the shortfall said. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void melonSlicesMakeAsManyStemsAsThereAreSlices(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.MELON_SLICE, 3));
        long id = order(h, companion, "farm_crops", "minecraft:melon_seeds", 4);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            List<BlockPos> stems = blocks(h, GROUND + 1, Blocks.MELON_STEM);
            h.assertTrue(stems.size() == 3 && count(companion, Items.MELON_SLICE) == 0, "Stems " + stems.size() + ", slices left " + count(companion, Items.MELON_SLICE));
            h.assertTrue(stems.stream().allMatch(stem -> roomBeside(h, stem)), "A melon stem with no room beside it: " + stems);
            h.assertTrue(!result.success() && result.message().contains("Посадил арбуз на 3 грядках из 4") && result.message().contains("хватило только на 3"), "Report: " + result.message());
        });
    }

    /** Neither seeds nor a slice: said before the hoe touches the ground. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "farm")
    public static void withoutSeedsOrMelonNothingIsTilled(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WHEAT_SEEDS, 8));
        long id = order(h, companion, "farm_crops", "minecraft:melon_seeds", 4);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("ни семян арбуза, ни ломтика арбуза"), "Report: " + result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).isEmpty() && count(companion, Items.WHEAT_SEEDS) == 8, "The field was touched for melons he does not have");
        });
    }

    /**
     * «Собери урожай»: the pumpkin and the melon at their stems are broken and picked up, the stems stay to bear again; a pumpkin
     * set down on the grass (no stem holds it) and a young stem are left alone.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void aHarvestTakesTheFruitAndLeavesTheStem(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        BlockPos pumpkinStem = new BlockPos(12, GROUND + 1, 12), melonStem = new BlockPos(12, GROUND + 1, 14), young = new BlockPos(14, GROUND + 1, 12);
        bearing(h, pumpkinStem, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.PUMPKIN);
        bearing(h, melonStem, Blocks.ATTACHED_MELON_STEM, Blocks.MELON);
        h.setBlock(young.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(young, Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE, 3));
        BlockPos decoration = new BlockPos(20, GROUND + 1, 20);
        h.setBlock(decoration, Blocks.PUMPKIN);
        CompanionEntity companion = spawn(h, 10, 10, 10, 10, 16);
        long id = order(h, companion, "harvest_crops", "any", 64);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            h.assertTrue(count(companion, Items.PUMPKIN) == 1 && count(companion, Items.MELON_SLICE) >= 3, "Pumpkins " + count(companion, Items.PUMPKIN) + ", slices " + count(companion, Items.MELON_SLICE));
            h.assertTrue(stem(h, pumpkinStem, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM) && stem(h, melonStem, Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM), "A stem was broken");
            h.assertTrue(h.getBlockState(young).is(Blocks.PUMPKIN_STEM) && h.getBlockState(decoration).is(Blocks.PUMPKIN), "The young stem or the pumpkin on the grass was taken");
            h.assertTrue(lying(h).isEmpty(), "Left lying: " + lying(h));
            h.assertTrue(result.message().contains("с 2 грядок") && !result.message().contains("заново") && result.message().contains("Ещё не созрело: 1"), "Report: " + result.message());
        });
    }

    /**
     * «Удобри огород» on two young melon stems: his bone meal brings both to their full height (vanilla: the meal stops there), and
     * he says the melon itself comes in its own time.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = "farm")
    public static void boneMealBringsTheStemsUpAndTheFruitIsLeftToTime(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        List<BlockPos> stems = List.of(new BlockPos(12, GROUND + 1, 12), new BlockPos(12, GROUND + 1, 14));
        for (BlockPos stem : stems) {
            h.setBlock(stem.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(stem, Blocks.MELON_STEM.defaultBlockState());
        }
        CompanionEntity companion = spawn(h, 10, 10, 10, 10, 16);
        give(companion, new ItemStack(Items.BONE_MEAL, 16));
        long id = order(h, companion, "use_bone_meal", "any", 64);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            for (BlockPos stem : stems) {
                BlockState state = h.getBlockState(stem);
                h.assertTrue(state.is(Blocks.ATTACHED_MELON_STEM) || state.is(Blocks.MELON_STEM) && state.getValue(StemBlock.AGE) == StemBlock.MAX_AGE, "Not grown up: " + stem + " " + state);
            }
            h.assertTrue(count(companion, Items.BONE_MEAL) < 16 && count(companion, Items.BONE_MEAL) >= 16 - 8, "Bone meal left " + count(companion, Items.BONE_MEAL));
            h.assertTrue(result.message().contains("дозрело посадок: 2") && result.message().contains("со временем"), "Report: " + result.message());
        });
    }

    /** «Посади тыкву», «посади 4 арбуза» need no model; a plan names them by seed, fruit or slice alike. */
    @GameTest(template = "empty", batch = "farm")
    public static void stemOrdersAreCheckedAndRoutedWithoutTheModel(GameTestHelper h) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"farm_crops\",\"item\":\"minecraft:pumpkin\"},"
                + "{\"action\":\"farm_crops\",\"item\":\"minecraft:melon_slice\",\"count\":4},{\"action\":\"harvest_crops\",\"item\":\"minecraft:melon\"}]"));
        h.assertTrue(plan.size() == 3 && plan.get(0).item().equals("minecraft:pumpkin_seeds") && plan.get(0).count() == 8 && plan.get(1).item().equals("minecraft:melon_seeds")
                && plan.get(2).item().equals("minecraft:melon_seeds"), "Plan: " + plan);
        h.assertTrue("companion work farm_crops 8 minecraft:pumpkin_seeds".equals(CompanionLocalCommands.route("Олег, посади тыкву", "Олег")), "Pumpkin phrase: " + CompanionLocalCommands.route("Олег, посади тыкву", "Олег"));
        h.assertTrue("companion work farm_crops 4 minecraft:melon_seeds".equals(CompanionLocalCommands.route("посади 4 арбуза", "Олег")), "Melon phrase: " + CompanionLocalCommands.route("посади 4 арбуза", "Олег"));
        // Said otherwise, it goes to the model, which must answer it with a plan, not words alone.
        h.assertTrue(dev.aicompanion.client.OpenRouterClient.requiresAction("посади тыкву у пруда") && dev.aicompanion.client.OpenRouterClient.requiresAction("засади арбузы"), "A melon field taken for small talk");
        String words = CompanionDialogue.describeAll(List.of(new PlannedAction("farm_crops", 0, 8, "minecraft:pumpkin_seeds")));
        h.assertTrue(words.contains("тыкву") && words.contains("грядок: 8"), "Steps in words: " + words);
        h.succeed();
    }

    /**
     * «Добудь тыкву» by the player's house: the pumpkin at his stem is his harvest and is taken (the stem stays), the pumpkin he set
     * down by his chest is his decoration and is not.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = "farm")
    public static void aPumpkinIsObtainedFromTheStemByThePlayersHouse(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 10, 10, 8, 8, 16);
        placedByPlayer(h, companion, new BlockPos(6, GROUND + 1, 6), Blocks.CHEST);
        BlockPos decoration = new BlockPos(6, GROUND + 1, 8), stem = new BlockPos(12, GROUND + 1, 12);
        placedByPlayer(h, companion, decoration, Blocks.PUMPKIN);
        bearing(h, stem, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.PUMPKIN);
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"obtain_item\",\"item\":\"minecraft:pumpkin\",\"count\":1}]"));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-stems", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.PUMPKIN) == 1, "Pumpkins " + count(companion, Items.PUMPKIN) + ": " + future.join().report());
            h.assertTrue(h.getBlockState(decoration).is(Blocks.PUMPKIN) && h.getBlockState(stem.east()).isAir(), "Not the pumpkin at the stem was taken");
            h.assertTrue(stem(h, stem, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM), "The stem was broken");
        });
    }

    /** A block set down by the player, as the game tells it (CompanionPlayerBuilds learns his buildings so). */
    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos cell, Block block) {
        h.setBlock(cell, block);
        var player = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(cell)), Blocks.GRASS_BLOCK.defaultBlockState(), player));
    }

    /**
     * Review: a second order beside an older field. The soil next to the old stems is where their pumpkins grow: no new stem is
     * sown on it, so none stands right beside an old one.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void aNewFieldLeavesTheOldStemsTheirRoom(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        List<BlockPos> old = new java.util.ArrayList<>();
        for (int x : new int[]{11, 13}) for (int z = 11; z <= 14; z++) {
            BlockPos stem = new BlockPos(x, GROUND + 1, z);
            h.setBlock(stem.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(stem, Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE, 2));
            old.add(stem);
        }
        CompanionEntity companion = spawn(h, 10, 10, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.PUMPKIN_SEEDS, 4));
        long id = order(h, companion, "farm_crops", "minecraft:pumpkin_seeds", 4);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            List<BlockPos> stems = blocks(h, GROUND + 1, Blocks.PUMPKIN_STEM);
            h.assertTrue(result.success() && stems.size() == 12, "Stems " + stems.size() + ": " + result.message());
            for (BlockPos stem : stems) {
                if (old.contains(stem)) continue;
                for (Direction side : Direction.Plane.HORIZONTAL) h.assertTrue(!old.contains(stem.relative(side)), "A new stem right beside an old one: " + stem);
                h.assertTrue(roomBeside(h, stem), "No room beside the new stem: " + stem);
            }
        });
    }

    /** A bag with no free cell: the pumpkin is there but its seeds have nowhere to go, and that is what he says. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "farm")
    public static void aFullBagSaysSoInsteadOfNoPumpkin(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        ItemStack[] bag = new ItemStack[companion.getContainerSize()];
        bag[0] = new ItemStack(Items.WOODEN_HOE);
        bag[1] = new ItemStack(Items.PUMPKIN, 3);
        for (int slot = 2; slot < bag.length; slot++) bag[slot] = new ItemStack(Items.COBBLESTONE, 64);
        give(companion, bag);
        long id = order(h, companion, "farm_crops", "minecraft:pumpkin_seeds", 4);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("ячейк") && !result.message().contains("ни тыквы"), "Report: " + result.message());
            h.assertTrue(count(companion, Items.PUMPKIN) == 3 && blocks(h, GROUND, Blocks.FARMLAND).isEmpty(), "The pumpkins or the ground were touched");
        });
    }

    /** One tile of soil by the water, stone all round: no stem would have room for its fruit, and he says so instead of «no seeds». */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "farm")
    public static void soilWithNoRoomForTheFruitIsSaid(GameTestHelper h) {
        ground(h);
        for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) if (x != 12 || z != 15) h.setBlock(new BlockPos(x, GROUND, z), Blocks.STONE);
        h.setBlock(new BlockPos(12, GROUND, 16), Blocks.WATER);
        CompanionEntity companion = spawn(h, 10, 12, 10, 12, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.PUMPKIN, 1));
        long id = order(h, companion, "farm_crops", "minecraft:pumpkin_seeds", 4);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("места под плод") && count(companion, Items.PUMPKIN) == 1, "Report: " + result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).isEmpty(), "Tilled where nothing would grow");
        });
    }

    /** Review: a strip of soil one block wide along the water. The fruit grows on any side, so the stems go every other block along it. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void aStripOfSoilIsSownEveryOtherBlockAlongIt(GameTestHelper h) {
        ground(h);
        for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) if (x != 12) h.setBlock(new BlockPos(x, GROUND, z), Blocks.STONE);
        h.setBlock(new BlockPos(12, GROUND, 16), Blocks.WATER);
        CompanionEntity companion = spawn(h, 10, 12, 10, 12, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.PUMPKIN_SEEDS, 4));
        long id = order(h, companion, "farm_crops", "minecraft:pumpkin_seeds", 4);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            List<BlockPos> stems = blocks(h, GROUND + 1, Blocks.PUMPKIN_STEM);
            h.assertTrue(result.success() && stems.size() == 4, "Stems " + stems.size() + ": " + result.message());
            h.assertTrue(stems.stream().allMatch(stem -> roomBeside(h, stem)), "A stem with no room beside it: " + stems);
        });
    }

    /** «Удобри огород» when the stems are already full-grown: bone meal does nothing more there, and he says that, not «nothing to reap». */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "farm")
    public static void fullGrownStemsAreNoWorkForBoneMeal(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        BlockPos stem = new BlockPos(12, GROUND + 1, 12);
        h.setBlock(stem.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(stem, Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE, StemBlock.MAX_AGE));
        CompanionEntity companion = spawn(h, 10, 10, 10, 10, 16);
        give(companion, new ItemStack(Items.BONE_MEAL, 8));
        long id = order(h, companion, "use_bone_meal", "any", 64);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("Удобрять нечего") && result.message().contains("со временем"), "Report: " + result.message());
            h.assertTrue(count(companion, Items.BONE_MEAL) == 8, "Bone meal spent: " + count(companion, Items.BONE_MEAL));
        });
    }

    /** A ripe stem on watered farmland, its fruit grown on the dirt east of it. */
    private static void bearing(GameTestHelper h, BlockPos stem, Block attached, Block fruit) {
        h.setBlock(stem.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(stem, attached.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.EAST));
        h.setBlock(stem.east().below(), Blocks.DIRT);
        h.setBlock(stem.east(), fruit);
    }

    /** The stem still stands: bare again, or (vanilla's random tick) with a new fruit already. */
    private static boolean stem(GameTestHelper h, BlockPos pos, Block bare, Block attached) {
        return h.getBlockState(pos).is(bare) || h.getBlockState(pos).is(attached);
    }

    /** Where vanilla grows the fruit of a ripe stem: a cell beside it with nothing in it, over farmland or dirt. */
    private static boolean roomBeside(GameTestHelper h, BlockPos stem) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos cell = stem.relative(side);
            BlockState ground = h.getBlockState(cell.below());
            if (h.getBlockState(cell).isAir() && (ground.getBlock() instanceof FarmBlock || ground.is(BlockTags.DIRT))) return true;
        }
        return false;
    }
}

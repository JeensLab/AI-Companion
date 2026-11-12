package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.LeavesBlock;
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
    /** The groves are heavy on the shared route searches of the tick: they run apart from the tests that time themselves in ticks. */
    @AfterBatch(batch = "house_rounds") public static void afterRounds(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    /** A night of building: a torch in the bag is a path light after dark. */
    private static long nightClock;
    @BeforeBatch(batch = "house_night") public static void nightForTorches(ServerLevel level) { nightClock = level.getDayTime(); level.setDayTime(18000); }
    @AfterBatch(batch = "house_night") public static void endNightForTorches(ServerLevel level) { level.setDayTime(nightClock); CompanionFixtureCleanup.afterAtomicTests(level); }

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

    @GameTest(template = "empty")
    public static void theHouseProtocolIsValidated(GameTestHelper helper) {
        helper.assertTrue("build_house".equals(CompanionWorkCommands.parse("Олег, построй нам небольшой деревянный дом", "Олег").type()), "House command not recognized");
        helper.assertTrue("build_house".equals(CompanionWorkCommands.parse("дострой дом", "Олег").type()), "Resume not recognized");
        helper.assertTrue("resume".equals(CompanionWorkCommands.parse("дострой дом", "Олег").item()) && CompanionWorkCommands.parse("построй дом", "Олег").item().isEmpty(), "Only «дострой» resumes; «построй дом» is the plank house");
        helper.assertTrue(CompanionWorkCommands.parse("не построй дом", "Олег") == null && CompanionWorkCommands.parse("построй дом и вернись", "Олег") == null, "Partial/negative command accepted");
        helper.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\"}]")).size() == 1, "House model protocol rejected");
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"count\":2}]")); } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Unsupported multiple houses accepted");
        helper.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void occupiedGroundIsPreserved(GameTestHelper helper) {
        CompanionEntity companion = setup(helper);
        // Every possible footprint intersects an existing block.
        for (int x = 1; x < 32; x += 4) for (int z = 1; z < 32; z += 4) helper.setBlock(new BlockPos(x, 1, z), Blocks.GOLD_BLOCK);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 32));
        start(helper, companion);
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(!companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) == 32, "Occupied site used or inventory changed");
            helper.assertTrue(countBlocks(helper, Blocks.GOLD_BLOCK) == 64 && countBlocks(helper, Blocks.OAK_PLANKS) == 0, "Existing blocks were removed");
        });
    }

    private static final String VILLAGE_HOUSE = "пустынный дом";
    private static final ResourceLocation VILLAGE_HOUSE_TEMPLATE = ResourceLocation.withDefaultNamespace("village/desert/houses/desert_small_house_4");

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void villageHouseStandsLikeItsTemplateAndSpendsTheBag(GameTestHelper helper) {
        CompanionEntity companion = groundedSetup(helper);
        Map<Item, Integer> bag = placeReference(helper, VILLAGE_HOUSE_TEMPLATE);
        fill(companion, bag);
        spareDirt(companion);
        helper.assertTrue(companion.startWork("build_house", VILLAGE_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains(VILLAGE_HOUSE), companion.getLastTaskResult());
            assertLooksLikeReference(helper, templateBase(companion));
            for (Item item : bag.keySet()) helper.assertTrue(count(companion, item) == 0, "Bag still holds " + item + ": blocks were not all taken from it");
            dirtBack(helper, companion);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 9000)
    public static void villageHouseResumesAfterStopAndReloadWithoutDuplicates(GameTestHelper helper) {
        CompanionEntity original = groundedSetup(helper);
        Map<Item, Integer> bag = placeReference(helper, VILLAGE_HOUSE_TEMPLATE);
        fill(original, bag);
        spareDirt(original);
        int total = bag.values().stream().mapToInt(Integer::intValue).sum();
        CompanionEntity[] current = {original};
        long[] stopped = {-1};
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && saved(current[0]).getCompound("TemplateBuild").getLongArray("Placed").length > 0
                    && bag.keySet().stream().mapToInt(item -> count(original, item)).sum() < total - 60) {
                current[0].stopGathering();
                stopped[0] = helper.getTick();
                CompoundTag data = new CompoundTag(); original.saveWithoutId(data); original.discard();
                helper.runAfterDelay(1, () -> {
                    CompanionEntity replacement = AICompanionMod.COMPANION.get().create(helper.getLevel());
                    replacement.load(data); helper.getLevel().addFreshEntity(replacement); current[0] = replacement;
                });
            }
            if (stopped[0] >= 0 && helper.getTick() - stopped[0] == 30) helper.assertTrue(current[0].startWork("build_house", "resume", 1).success(), "Resume rejected");
        });
        helper.assertTrue(original.startWork("build_house", VILLAGE_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            helper.assertTrue(stopped[0] >= 0 && helper.getTick() - stopped[0] > 40, "Not resumed yet");
            done(helper, current[0]);
            helper.assertTrue(current[0].didLastTaskSucceed(), current[0].getLastTaskResult());
            assertLooksLikeReference(helper, templateBase(current[0]));
            for (Item item : bag.keySet()) helper.assertTrue(count(current[0], item) == 0, "Resume spent or left a different amount of " + item);
            dirtBack(helper, current[0]);
        });
    }

    private static final int SPARE_DIRT = 8;
    /** Whether a block is reached from the ground or from a column of dirt depends on the cells the route offers that tick: both are right, so he has dirt for it. */
    private static void spareDirt(CompanionEntity companion) { companion.setItem(companion.getContainerSize() - 5, new ItemStack(Items.DIRT, SPARE_DIRT)); }
    private static void dirtBack(GameTestHelper helper, CompanionEntity companion) {
        helper.assertTrue(count(companion, Items.DIRT) == SPARE_DIRT && countBlocks(helper, Blocks.DIRT) == 0,
                "Scaffolding dirt: " + count(companion, Items.DIRT) + " in the bag, " + countBlocks(helper, Blocks.DIRT) + " in the world");
    }

    private static final String LOG_HOUSE = "бревенчатый дом";
    private static final ResourceLocation LOG_HOUSE_TEMPLATE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_3");
    /** What no skill of his makes: glass needs a furnace, a torch coal, a bed wool. The player gives them; the rest of the log house he gets himself. */
    private static final Set<Item> UNGETTABLE = Set.of(Items.GLASS_PANE, Items.TORCH, Items.YELLOW_BED);

    /** The reference's materials, the ones he cannot make or all the others. */
    private static Map<Item, Integer> only(Map<Item, Integer> reference, boolean ungettable) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        for (var entry : reference.entrySet()) if (UNGETTABLE.contains(entry.getKey()) == ungettable) result.put(entry.getKey(), entry.getValue());
        return result;
    }

    /** Trees of the given species in turn, a grove at the far side of the arena; the reference corner and his start stay clear. */
    private static void forest(GameTestHelper helper, String... species) {
        int index = 0;
        for (int x = 2; x < 32; x += 4) for (int z = 2; z < 32; z += 4) {
            if (x < 22 && z < 22) continue;
            String wood = species[index++ % species.length];
            var log = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(wood + "_log"));
            var leaves = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(wood + "_leaves"));
            for (int y = GROUND + 1; y <= GROUND + 4; y++) helper.setBlock(new BlockPos(x, y, z), log);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = GROUND + 4; y <= GROUND + 5; y++)
                if (helper.getBlockState(new BlockPos(x + dx, y, z + dz)).isAir()) helper.setBlock(new BlockPos(x + dx, y, z + dz), leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        }
    }
    /** A block of stone to mine at the near side of the arena, away from the corner of the reference. */
    private static void quarry(GameTestHelper helper) {
        for (int x = 12; x <= 17; x++) for (int z = 2; z <= 7; z++) for (int y = GROUND; y <= GROUND + 2; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }
    /** Soil for the scaffolding of the tall roof: a patch to dig at the middle of the arena, or a few blocks in the bag. */
    private static void dirtPatch(GameTestHelper helper) {
        for (int x = 12; x <= 17; x++) for (int z = 10; z <= 12; z++) helper.setBlock(new BlockPos(x, GROUND, z), Blocks.DIRT);
    }
    /** The mock player stands where it was put and blocks a cell of the scaffolding for good: it goes to a free spot between the trees. */
    private static void playerAway(GameTestHelper helper) {
        BlockPos spot = helper.absolutePos(new BlockPos(20, GROUND + 1, 30));
        // Only this arena's player: the other tests of the batch have theirs in the same level.
        for (var player : helper.getLevel().players()) if (helper.getBounds().inflate(1).contains(player.position())) player.teleportTo(spot.getX() + .5, spot.getY(), spot.getZ() + .5);
    }
    /** The template's own tree and anything that grew from it: no tree of that wood is left in the arena. */
    private static void noTrees(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 30, 31)))
            if (helper.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS) || helper.getBlockState(pos).is(net.minecraft.tags.BlockTags.LEAVES)) helper.setBlock(pos, Blocks.AIR);
    }
    private static void tools(CompanionEntity companion) {
        companion.setItem(companion.getContainerSize() - 3, new ItemStack(Items.IRON_AXE));
        companion.setItem(companion.getContainerSize() - 4, new ItemStack(Items.IRON_PICKAXE));
    }
    private static boolean wooden(Item item) {
        var stack = item.getDefaultInstance();
        return stack.is(net.minecraft.tags.ItemTags.PLANKS) || stack.is(net.minecraft.tags.ItemTags.LOGS)
                || stack.is(net.minecraft.tags.ItemTags.WOODEN_STAIRS) || stack.is(net.minecraft.tags.ItemTags.WOODEN_DOORS);
    }

    /**
     * An empty bag in a grove of two species and a heap of stone: the house of the template is got from the trees of its own wood,
     * the stone and the crafts, and finished without a stop (the closed attempt halted at the first tree of the other species).
     */
    @GameTest(template = "tall_tree", batch = "house_rounds", timeoutTicks = 36000)
    public static void villageHouseIsGatheredAndBuiltFromAnEmptyBagInAMixedForest(GameTestHelper helper) {
        CompanionEntity companion = groundedSetup(helper);
        Map<Item, Integer> reference = placeReference(helper, LOG_HOUSE_TEMPLATE);
        fill(companion, only(reference, true));
        tools(companion);
        noTrees(helper);
        forest(helper, "oak", "birch", "oak");
        quarry(helper);
        dirtPatch(helper);
        playerAway(helper);
        int stone = countBlocks(helper, Blocks.STONE);
        helper.assertTrue(companion.startWork("build_house", LOG_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains(LOG_HOUSE), companion.getLastTaskResult());
            assertLooksLikeReference(helper, templateBase(companion));
            helper.assertTrue(count(companion, Items.BIRCH_LOG) == 0, "Logs of the other species were cut for an oak house");
            helper.assertTrue(stone - countBlocks(helper, Blocks.STONE) >= reference.get(Items.COBBLESTONE), "The cobblestone did not come from the stone of the arena");
            for (Item item : only(reference, true).keySet()) helper.assertTrue(count(companion, item) == 0, "Bag still holds " + item);
        });
    }

    /**
     * An oak house where only spruce grows: the same house of spruce, all of its wooden parts of the one species. Also stopped and
     * reloaded in the middle of the gathering: the saved choice and the logs in the bag carry on.
     */
    @GameTest(template = "tall_tree", batch = "house_rounds", timeoutTicks = 30000)
    public static void villageHouseOfAnAbsentWoodIsBuiltOfTheOneThatGrowsAlsoAcrossAReload(GameTestHelper helper) {
        CompanionEntity original = groundedSetup(helper);
        Map<Item, Integer> reference = placeReference(helper, LOG_HOUSE_TEMPLATE);
        Map<Item, Integer> bag = new LinkedHashMap<>(reference);
        bag.keySet().removeIf(CompanionHouseTests::wooden);
        fill(original, bag);
        tools(original);
        original.setItem(original.getContainerSize() - 5, new ItemStack(Items.DIRT, 8));
        noTrees(helper);
        forest(helper, "spruce");
        quarry(helper);
        playerAway(helper);
        CompanionEntity[] current = {original};
        long[] stopped = {-1};
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && count(current[0], Items.SPRUCE_LOG) >= 8) {
                current[0].stopGathering();
                stopped[0] = helper.getTick();
                CompoundTag data = new CompoundTag(); original.saveWithoutId(data); original.discard();
                helper.runAfterDelay(1, () -> {
                    CompanionEntity replacement = AICompanionMod.COMPANION.get().create(helper.getLevel());
                    replacement.load(data); helper.getLevel().addFreshEntity(replacement); current[0] = replacement;
                });
            }
            if (stopped[0] >= 0 && helper.getTick() - stopped[0] == 30) helper.assertTrue(current[0].startWork("build_house", "resume", 1).success(), "Resume rejected");
        });
        helper.assertTrue(original.startWork("build_house", LOG_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            helper.assertTrue(stopped[0] >= 0 && helper.getTick() - stopped[0] > 40, "Not stopped and resumed yet");
            done(helper, current[0]);
            helper.assertTrue(current[0].didLastTaskSucceed(), current[0].getLastTaskResult());
            BlockPos base = templateBase(current[0]);
            int checked = 0;
            for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(7, 7, 7))) {
                BlockState want = helper.getBlockState(rel);
                if (want.isAir() || want.is(Blocks.JIGSAW) || want.is(Blocks.STRUCTURE_VOID)) continue;
                String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(want.getBlock()).getPath();
                var expected = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(path.replace("oak", "spruce")));
                BlockState got = helper.getLevel().getBlockState(base.offset(rel.getX() - 1, rel.getY() - 1, rel.getZ() - 1));
                helper.assertTrue(got.is(expected), "Block " + rel + " is " + got.getBlock() + ", expected " + expected);
                checked++;
            }
            helper.assertTrue(checked > 60, "Too little checked: " + checked);
            helper.assertTrue(count(current[0], Items.OAK_LOG) == 0 && count(current[0], Items.OAK_PLANKS) == 0, "Oak appeared from nowhere");
        });
    }

    /**
     * The closed attempt ordered 18 cobblestone with 11 in the bag, a stone pickaxe was made out of 3 of the 11 and the order stood
     * at 15. Here: no pickaxe, 11 of 37 cobblestone, a bench and planks to spare; the stone set aside for the building is not a tool's,
     * so the pickaxe is of wood and exactly the 26 missing are mined.
     */
    @GameTest(template = "tall_tree", batch = "house_rounds", timeoutTicks = 12000)
    public static void stoneSetAsideForTheBuildingIsNotMadeIntoATool(GameTestHelper helper) {
        CompanionEntity companion = groundedSetup(helper);
        Map<Item, Integer> reference = placeReference(helper, LOG_HOUSE_TEMPLATE);
        Map<Item, Integer> bag = new LinkedHashMap<>(reference);
        bag.put(Items.COBBLESTONE, 11);
        bag.merge(Items.OAK_PLANKS, 9, Integer::sum);
        fill(companion, bag);
        companion.setItem(companion.getContainerSize() - 4, new ItemStack(Items.STICK, 2));
        companion.setItem(companion.getContainerSize() - 5, new ItemStack(Items.DIRT, 8));
        helper.setBlock(new BlockPos(17, GROUND + 1, 16), Blocks.CRAFTING_TABLE);
        quarry(helper);
        playerAway(helper);
        int stone = countBlocks(helper, Blocks.STONE);
        helper.assertTrue(companion.startWork("build_house", LOG_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            assertLooksLikeReference(helper, templateBase(companion));
            helper.assertTrue(count(companion, Items.STONE_PICKAXE) == 0, "The stone of the building went into a pickaxe");
            helper.assertTrue(stone - countBlocks(helper, Blocks.STONE) == reference.get(Items.COBBLESTONE) - 11, "Mined " + (stone - countBlocks(helper, Blocks.STONE)) + " instead of exactly " + (reference.get(Items.COBBLESTONE) - 11));
            helper.assertTrue(count(companion, Items.COBBLESTONE) == 0, "Cobblestone left over: " + count(companion, Items.COBBLESTONE));
        });
    }

    /** The torches of the building are its material, not his path lights: through a whole night of work not one is put down on the way. */
    @GameTest(template = "tall_tree", batch = "house_night", timeoutTicks = 9000)
    public static void torchesSetAsideForTheBuildingAreNotSpentOnPathLightsAtNight(GameTestHelper helper) {
        CompanionEntity companion = groundedSetup(helper);
        Map<Item, Integer> reference = placeReference(helper, LOG_HOUSE_TEMPLATE);
        fill(companion, reference);
        companion.setItem(companion.getContainerSize() - 5, new ItemStack(Items.DIRT, 8));
        playerAway(helper);
        helper.assertTrue(helper.getLevel().getDayTime() % 24000 > 13000, "The premise: it is night");
        helper.assertTrue(companion.startWork("build_house", LOG_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            assertLooksLikeReference(helper, templateBase(companion));
            helper.assertTrue(countBlocks(helper, Blocks.TORCH) == 0, "A torch of the building was put down as a path light");
            for (Item item : reference.keySet()) helper.assertTrue(count(companion, item) == 0, "Bag still holds " + item);
        });
    }

    /** What he cannot make is said once with the counts, all the rest stands; after the player puts it into the bag «дострой» finishes the house. */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void whatHeCannotGetIsNamedOnceAndGivenLaterFinishesTheHouse(GameTestHelper helper) {
        CompanionEntity companion = groundedSetup(helper);
        Map<Item, Integer> reference = placeReference(helper, LOG_HOUSE_TEMPLATE);
        fill(companion, only(reference, false));
        companion.setItem(companion.getContainerSize() - 5, new ItemStack(Items.DIRT, 8));
        playerAway(helper);
        long[] stopped = {-1};
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && helper.getTick() > 40 && !companion.isWorking() && !companion.isGatheringTask()) {
                stopped[0] = helper.getTick();
                String said = companion.getLastTaskResult();
                helper.assertTrue(!companion.didLastTaskSucceed(), said);
                for (Item item : UNGETTABLE) helper.assertTrue(said.contains(reference.get(item) + " × " + new ItemStack(item).getHoverName().getString()), "Not named with its count: " + item + " in " + said);
                helper.assertTrue(!said.contains(new ItemStack(Items.OAK_STAIRS).getHoverName().getString()) && !said.contains(new ItemStack(Items.COBBLESTONE).getHoverName().getString()), "Named what he could get: " + said);
                helper.assertTrue(countBlocks(helper, Blocks.OAK_PLANKS) > 33 && countBlocks(helper, Blocks.COBBLESTONE) > 37, "The rest of the house was not built before stopping");
                // The door is the last block of all: it waits in the bag with the rest of what is carried in.
                for (Item item : only(reference, false).keySet()) helper.assertTrue(count(companion, item) == (item == Items.OAK_DOOR ? 1 : 0), "Lost or kept " + item + ": " + count(companion, item));
                helper.assertTrue(countBlocks(helper, Blocks.OAK_DOOR) == 2, "The door shut the house before what is carried in");
                int slot = companion.getContainerSize() - 9;
                for (Item item : UNGETTABLE) companion.setItem(slot++, new ItemStack(item, reference.get(item)));
                helper.assertTrue(companion.startWork("build_house", "resume", 1).success(), "Resume rejected");
            }
        });
        helper.assertTrue(companion.startWork("build_house", LOG_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            helper.assertTrue(stopped[0] >= 0 && helper.getTick() - stopped[0] > 20, "Not stopped and given yet");
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            assertLooksLikeReference(helper, templateBase(companion));
            for (Item item : reference.keySet()) helper.assertTrue(count(companion, item) == 0, "Bag still holds " + item);
        });
    }

    private static final String GABLED_HOUSE = "деревенский дом";
    private static final ResourceLocation GABLED_HOUSE_TEMPLATE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
    private static final int DIRT = 24;

    /** A gabled roof is out of reach from the ground: he stands on a dirt column for it, and nothing of it is left afterwards. */
    @GameTest(template = "tall_tree", timeoutTicks = 9000)
    public static void gabledHouseIsBuiltFromScaffoldingThatLeavesNoTrace(GameTestHelper helper) {
        CompanionEntity companion = groundedSetup(helper);
        Map<Item, Integer> bag = placeReference(helper, GABLED_HOUSE_TEMPLATE);
        fill(companion, bag);
        companion.setItem(companion.getContainerSize() - 1, new ItemStack(Items.DIRT, DIRT));
        boolean[] stoodOnDirt = {false};
        helper.onEachTick(() -> { if (helper.getTick() % 5 == 0 && countBlocks(helper, Blocks.DIRT) > 0) stoodOnDirt[0] = true; });
        helper.assertTrue(companion.startWork("build_house", GABLED_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains(GABLED_HOUSE), companion.getLastTaskResult());
            assertLooksLikeReference(helper, templateBase(companion));
            helper.assertTrue(stoodOnDirt[0], "The roof was reached without any scaffolding");
            helper.assertTrue(countBlocks(helper, Blocks.DIRT) == 0, "Scaffolding dirt is left in the world");
            helper.assertTrue(count(companion, Items.DIRT) == DIRT, "The dirt did not come back to the bag: " + count(companion, Items.DIRT));
            for (Item item : bag.keySet()) helper.assertTrue(count(companion, item) == 0, "Bag still holds " + item);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void gabledHouseWithoutDirtSaysHowMuchIsNeeded(GameTestHelper helper) {
        CompanionEntity companion = groundedSetup(helper);
        fill(companion, placeReference(helper, GABLED_HOUSE_TEMPLATE));
        helper.assertTrue(companion.startWork("build_house", GABLED_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            done(helper, companion);
            String said = companion.getLastTaskResult();
            helper.assertTrue(!companion.didLastTaskSucceed() && said.contains("земл") && said.matches(".*\\d.*"), said);
            helper.assertTrue(countBlocks(helper, Blocks.DIRT) == 0, "Dirt appeared in the world without any in the bag");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void scaffoldingLeftByAnInterruptionIsTakenDownOnResume(GameTestHelper helper) {
        CompanionEntity original = groundedSetup(helper);
        Map<Item, Integer> bag = placeReference(helper, LOG_HOUSE_TEMPLATE);
        fill(original, bag);
        original.setItem(original.getContainerSize() - 1, new ItemStack(Items.DIRT, DIRT));
        CompanionEntity[] current = {original};
        long[] stopped = {-1};
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && countBlocks(helper, Blocks.DIRT) >= 2) {
                current[0].stopGathering();
                var scaffolding = saved(current[0]).getCompound("TemplateBuild").getList("Scaffold", 4);
                helper.assertTrue(scaffolding.size() >= 2, "The scaffolding was not saved");
                BlockPos column = BlockPos.of(((net.minecraft.nbt.LongTag) scaffolding.get(0)).getAsLong());
                stopped[0] = helper.getTick();
                CompoundTag data = new CompoundTag(); original.saveWithoutId(data); original.discard();
                helper.runAfterDelay(1, () -> {
                    CompanionEntity replacement = AICompanionMod.COMPANION.get().create(helper.getLevel());
                    replacement.load(data);
                    // Resumed from a distance, as after «стоп» and a walk with the player: he has to come back to the column.
                    BlockPos far = null;
                    for (BlockPos corner : new BlockPos[]{new BlockPos(28, 1, 28), new BlockPos(28, 1, 4), new BlockPos(4, 1, 28)}) {
                        BlockPos at = helper.absolutePos(corner);
                        if (far == null || at.distSqr(column) > far.distSqr(column)) far = at;
                    }
                    helper.assertTrue(far.distSqr(column) > 12 * 12, "The far spot is not far from the column");
                    replacement.moveTo(far.getX() + .5, far.getY(), far.getZ() + .5);
                    helper.getLevel().addFreshEntity(replacement); current[0] = replacement;
                });
            }
            if (stopped[0] >= 0 && helper.getTick() - stopped[0] == 30) helper.assertTrue(current[0].startWork("build_house", "resume", 1).success(), "Resume rejected");
        });
        helper.assertTrue(original.startWork("build_house", LOG_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            helper.assertTrue(stopped[0] >= 0 && helper.getTick() - stopped[0] > 40, "Not resumed yet");
            done(helper, current[0]);
            helper.assertTrue(current[0].didLastTaskSucceed(), current[0].getLastTaskResult());
            assertLooksLikeReference(helper, templateBase(current[0]));
            helper.assertTrue(countBlocks(helper, Blocks.DIRT) == 0, "Scaffolding dirt is left in the world");
            helper.assertTrue(count(current[0], Items.DIRT) == DIRT, "The dirt did not come back to the bag: " + count(current[0], Items.DIRT));
            for (Item item : bag.keySet()) helper.assertTrue(count(current[0], item) == 0, "Resume spent or left a different amount of " + item);
        });
    }

    /**
     * The site is overgrown and uneven wherever he looks: a tree every four blocks (with its crown), a block of red sand sticking up
     * every four, grass and flowers between. The floor is sunk into the ground, so the door stands at the level of the ground.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 9000)
    public static void villageHouseIsBuiltOnAnOvergrownUnevenSiteWithItsDoorAtGroundLevel(GameTestHelper helper) {
        CompanionEntity companion = wildSetup(helper);
        Map<Item, Integer> bag = placeReference(helper, VILLAGE_HOUSE_TEMPLATE);
        fill(companion, bag);
        spareDirt(companion);
        helper.assertTrue(companion.startWork("build_house", VILLAGE_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains(VILLAGE_HOUSE), companion.getLastTaskResult());
            BlockPos base = templateBase(companion);
            assertLooksLikeReference(helper, base);
            var size = helper.getLevel().getServer().getStructureManager().get(VILLAGE_HOUSE_TEMPLATE).orElseThrow().getSize();
            BlockPos door = null;
            for (int x = 0; x < size.getX(); x++) for (int y = 0; y < size.getY(); y++) for (int z = 0; z < size.getZ(); z++) {
                BlockState reference = helper.getBlockState(new BlockPos(1 + x, 1 + y, 1 + z));
                if (reference.getBlock() instanceof DoorBlock && reference.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) door = base.offset(x, y, z);
                // The box above the floor is cleared: what the template leaves as air is air, not a bush or a leaf of the old tree.
                if (y >= 1 && reference.isAir()) helper.assertTrue(helper.getLevel().getBlockState(base.offset(x, y, z)).isAir(), "Not cleared: " + helper.getLevel().getBlockState(base.offset(x, y, z)) + " at " + helper.relativePos(base.offset(x, y, z)));
            }
            helper.assertTrue(door != null, "The reference has no door");
            helper.assertTrue(helper.relativePos(door).getY() == GROUND + 1, "The door is not at ground level: " + helper.relativePos(door));
            boolean walksIn = false;
            for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos outside = door.relative(side);
                var level = helper.getLevel();
                walksIn |= level.getBlockState(outside).getCollisionShape(level, outside).isEmpty() && level.getBlockState(outside.above()).getCollisionShape(level, outside.above()).isEmpty()
                        && level.getBlockState(outside.below()).isFaceSturdy(level, outside.below(), net.minecraft.core.Direction.UP);
            }
            helper.assertTrue(walksIn, "No level ground in front of the door: he would have to jump to get in");
            dirtBack(helper, companion);
        });
    }

    /** A chest with things in it is not his to break: he stops, says what is in the way and where, and leaves it as it was. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aChestInTheWayIsLeftIntactAndNamed(GameTestHelper helper) {
        CompanionEntity companion = groundedSetup(helper);
        Map<Item, Integer> bag = placeReference(helper, VILLAGE_HOUSE_TEMPLATE);
        fill(companion, bag);
        spareDirt(companion);
        var size = helper.getLevel().getServer().getStructureManager().get(VILLAGE_HOUSE_TEMPLATE).orElseThrow().getSize();
        BlockPos rel = null;
        for (int y = 2; y < size.getY() && rel == null; y++) for (int x = 0; x < size.getX() && rel == null; x++) for (int z = 0; z < size.getZ() && rel == null; z++) {
            BlockState reference = helper.getBlockState(new BlockPos(1 + x, 1 + y, 1 + z));
            if (!reference.isAir() && !(reference.getBlock() instanceof DoorBlock) && !(reference.getBlock() instanceof BedBlock)) rel = new BlockPos(x, y, z);
        }
        BlockPos wall = rel;
        BlockPos[] placedAt = {null};
        // The chest is put down after the site was chosen and the floor begun: what he cannot have known when he chose it.
        helper.onEachTick(() -> {
            var data = saved(companion).getCompound("TemplateBuild");
            if (placedAt[0] == null && data.getLongArray("Placed").length > 0) {
                BlockPos at = BlockPos.of(data.getLong("Base")).offset(wall);
                helper.getLevel().setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
                ((Container) helper.getLevel().getBlockEntity(at)).setItem(0, new ItemStack(Items.DIAMOND, 5));
                placedAt[0] = at;
            }
        });
        helper.assertTrue(companion.startWork("build_house", VILLAGE_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            helper.assertTrue(placedAt[0] != null, "The chest is not put down yet");
            done(helper, companion);
            String said = companion.getLastTaskResult();
            helper.assertTrue(!companion.didLastTaskSucceed() && said.contains(placedAt[0].toShortString()) && said.contains("Мешает"), said);
            helper.assertTrue(helper.getLevel().getBlockState(placedAt[0]).is(Blocks.CHEST)
                    && helper.getLevel().getBlockEntity(placedAt[0]) instanceof Container held && held.getItem(0).is(Items.DIAMOND) && held.getItem(0).getCount() == 5, "The chest or what it held was touched");
        });
    }

    /**
     * What he clears is his: the logs and the sand come to the bag as drops, nothing is left lying and nothing is lost, also when he is
     * stopped in the middle of it and the world is left and entered again.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void whatIsClearedIsPickedUpAndSurvivesAStopAndReload(GameTestHelper helper) {
        CompanionEntity original = wildSetup(helper);
        Map<Item, Integer> bag = placeReference(helper, VILLAGE_HOUSE_TEMPLATE);
        helper.assertTrue(!bag.containsKey(Items.RED_SAND) && !bag.containsKey(Items.OAK_LOG), "The premise: the building takes neither red sand nor logs");
        fill(original, bag);
        spareDirt(original);
        int logs = countBlocks(helper, Blocks.OAK_LOG), sand = countBlocks(helper, Blocks.RED_SAND);
        CompanionEntity[] current = {original};
        long[] stopped = {-1};
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && countBlocks(helper, Blocks.OAK_LOG) < logs && saved(current[0]).getCompound("TemplateBuild").contains("Base")) {
                current[0].stopGathering();
                stopped[0] = helper.getTick();
                CompoundTag data = new CompoundTag(); original.saveWithoutId(data); original.discard();
                helper.runAfterDelay(1, () -> {
                    CompanionEntity replacement = AICompanionMod.COMPANION.get().create(helper.getLevel());
                    replacement.load(data); helper.getLevel().addFreshEntity(replacement); current[0] = replacement;
                });
            }
            if (stopped[0] >= 0 && helper.getTick() - stopped[0] == 30) helper.assertTrue(current[0].startWork("build_house", "resume", 1).success(), "Resume rejected");
        });
        helper.assertTrue(original.startWork("build_house", VILLAGE_HOUSE, 1).success(), "Template house work rejected");
        helper.succeedWhen(() -> {
            helper.assertTrue(stopped[0] >= 0 && helper.getTick() - stopped[0] > 40, "Not stopped and resumed yet");
            done(helper, current[0]);
            helper.assertTrue(current[0].didLastTaskSucceed(), current[0].getLastTaskResult());
            int lying = 0;
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4))) if (item.getItem().is(Items.OAK_LOG) || item.getItem().is(Items.RED_SAND)) lying += item.getItem().getCount();
            helper.assertTrue(lying == 0, lying + " logs or sand are left lying");
            helper.assertTrue(count(current[0], Items.OAK_LOG) == logs - countBlocks(helper, Blocks.OAK_LOG), "Logs: cleared " + (logs - countBlocks(helper, Blocks.OAK_LOG)) + ", in the bag " + count(current[0], Items.OAK_LOG));
            helper.assertTrue(count(current[0], Items.RED_SAND) == sand - countBlocks(helper, Blocks.RED_SAND), "Sand: dug " + (sand - countBlocks(helper, Blocks.RED_SAND)) + ", in the bag " + count(current[0], Items.RED_SAND));
            helper.assertTrue(logs > countBlocks(helper, Blocks.OAK_LOG), "No log was cleared: the premise of the test is gone");
            dirtBack(helper, current[0]);
        });
    }

    @GameTest(template = "empty")
    public static void buildingNamesComeOnlyFromTheCatalogue(GameTestHelper helper) {
        var named = CompanionWorkCommands.parse("построй " + VILLAGE_HOUSE, "Олег");
        helper.assertTrue(named != null && "build_house".equals(named.type()) && VILLAGE_HOUSE.equals(named.item()), "Catalogue building not recognized");
        helper.assertTrue(CompanionWorkCommands.parse("построй дом", "Олег").item().isEmpty(), "Plain house got a building name");
        helper.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"item\":\"" + VILLAGE_HOUSE + "\"}]")).getFirst().item().equals(VILLAGE_HOUSE), "Catalogue name rejected");
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"wait\"},{\"action\":\"build_house\",\"item\":\"замок дракона\"}]")); } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "A building outside the catalogue was accepted");
        helper.succeed();
    }

    /** The vanilla template placed in the arena corner is the reference; returns what the bag needs to build one more like it. */
    private static Map<Item, Integer> placeReference(GameTestHelper helper, ResourceLocation id) {
        var template = helper.getLevel().getServer().getStructureManager().get(id).orElseThrow();
        // The ground of the arena is not part of the reference: its corner is cleared first.
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(7, 7, 7))) helper.setBlock(rel, Blocks.AIR);
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        template.placeInWorld(helper.getLevel(), at, at, new StructurePlaceSettings().setIgnoreEntities(true), RandomSource.create(1), 2);
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(7, 7, 7))) {
            BlockState state = helper.getBlockState(rel);
            if (state.isAir() || state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_VOID) || isSecondHalf(state)) continue;
            needed.merge(state.getBlock().asItem(), 1, Integer::sum);
        }
        return needed;
    }
    private static boolean isSecondHalf(BlockState state) {
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                || state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }
    private static void fill(CompanionEntity companion, Map<Item, Integer> needed) {
        int slot = 0;
        for (var entry : needed.entrySet()) companion.setItem(slot++, new ItemStack(entry.getKey(), entry.getValue()));
    }
    private static BlockPos templateBase(CompanionEntity companion) { return BlockPos.of(saved(companion).getCompound("TemplateBuild").getLong("Base")); }
    /** Every block of the reference stands at the same place of the new building: the kind, and the properties that turn it. */
    private static void assertLooksLikeReference(GameTestHelper helper, BlockPos base) {
        int checked = 0;
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(7, 7, 7))) {
            BlockState want = helper.getBlockState(rel);
            if (want.isAir() || want.is(Blocks.JIGSAW) || want.is(Blocks.STRUCTURE_VOID)) continue;
            BlockState got = helper.getLevel().getBlockState(base.offset(rel.getX() - 1, rel.getY() - 1, rel.getZ() - 1));
            helper.assertTrue(got.is(want.getBlock()), "Block " + rel + " is " + got.getBlock() + ", the template has " + want.getBlock());
            if (want.is(Blocks.CHEST)) helper.assertTrue(helper.getLevel().getBlockEntity(base.offset(rel.getX() - 1, rel.getY() - 1, rel.getZ() - 1)) instanceof Container chest && chest.isEmpty(), "Chest is not empty");
            for (var property : want.getProperties()) {
                if (Set.of("facing", "axis", "half", "part").contains(property.getName()))
                    helper.assertTrue(got.getValue(property).equals(want.getValue(property)), "Block " + rel + " is turned differently: " + property.getName());
            }
            checked++;
        }
        helper.assertTrue(checked > 60, "Reference too small to prove anything: " + checked);
    }

    /** The floor of a template building is sunk into the ground: the arena has red sand (no house of the catalogue is built of it) over its bedrock, and a shovel in the bag. */
    private static final int GROUND = 1;
    private static CompanionEntity groundedSetup(GameTestHelper helper) { return setup(helper, true); }
    /** {@link #groundedSetup} with trees, bumps of red sand, grass and flowers everywhere (nothing around his start and the player). */
    private static CompanionEntity wildSetup(GameTestHelper helper) {
        CompanionEntity companion = groundedSetup(helper);
        companion.setItem(companion.getContainerSize() - 3, new ItemStack(Items.IRON_AXE));
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            // Nothing around his start, the player and the reference building (the corner), which is read as it is.
            if (Math.abs(x - 16) <= 1 && Math.abs(z - 16) <= 1 || Math.abs(x - 11) <= 1 && Math.abs(z - 11) <= 1 || x < 10 && z < 10) continue;
            if (x % 4 == 2 && z % 4 == 2) {
                for (int y = GROUND + 1; y <= GROUND + 4; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = GROUND + 4; y <= GROUND + 5; y++)
                    if (helper.getBlockState(new BlockPos(x + dx, y, z + dz)).isAir()) helper.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            } else if (x % 4 == 0 && z % 4 == 0) helper.setBlock(new BlockPos(x, GROUND + 1, z), Blocks.RED_SAND);
            else if ((x + z) % 3 == 0) helper.setBlock(new BlockPos(x, GROUND + 1, z), (x + z) % 7 == 0 ? Blocks.DANDELION : Blocks.SHORT_GRASS);
        }
        return companion;
    }
    private static CompanionEntity setup(GameTestHelper helper) { return setup(helper, false); }
    private static CompanionEntity setup(GameTestHelper helper, boolean ground) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        if (ground) for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) if (helper.getBlockState(new BlockPos(x, GROUND, z)).isAir()) helper.setBlock(new BlockPos(x, GROUND, z), Blocks.RED_SAND);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, ground ? GROUND + 1 : 1, 16);
        if (ground) companion.setItem(companion.getContainerSize() - 2, new ItemStack(Items.IRON_SHOVEL));
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

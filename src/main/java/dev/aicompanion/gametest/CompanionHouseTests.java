package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.schematic.CompanionSchematic;
import dev.aicompanion.schematic.CompanionSchematics;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * «Построй дом» from a schematic in mods/oleh_ai_schemas: every file format reads the same building, the player's placement is
 * built from real items up to the last block, what stands in the way is broken (a chest with things is not), missing wood is
 * gathered, high walls are reached from scaffolding that is taken down again, and a stopped house is finished where it stands.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionHouseTests {
    private static final int GROUND = 3;
    /** The floor of the test house lies on the grass, here (relative to the test). */
    private static final BlockPos SITE = new BlockPos(18, GROUND + 1, 9);
    interface Shape { BlockState at(int x, int y, int z); }

    /** A 5×4×5 house: plank floor, cobblestone walls with a door, stairs on the east wall and a slab roof; air inside. */
    static BlockState house(int x, int y, int z) {
        if (y == 0) return Blocks.OAK_PLANKS.defaultBlockState();
        if (y == 3) return Blocks.OAK_SLAB.defaultBlockState();
        boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
        if (!wall) return Blocks.AIR.defaultBlockState();
        if (x == 2 && z == 0) return Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
        if (x == 4 && z == 2 && y == 1) return Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    @GameTest(template = "empty")
    public static void everyFormatReadsTheSameHouse(GameTestHelper h) {
        String name = "test_formats";
        write(name + ".schem", sponge(5, 4, 5, CompanionHouseTests::house));
        write(name + ".litematic", litematic(5, 4, 5, CompanionHouseTests::house));
        write(name + ".nbt", structure(5, 4, 5, CompanionHouseTests::house));
        // The old format knows planks (5), cobblestone (4) and air by number only.
        write(name + "_legacy.schematic", legacy(5, 4, 5, (x, y, z) -> y == 0 ? Blocks.OAK_PLANKS.defaultBlockState()
                : x == 0 || x == 4 || z == 0 || z == 4 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState()));
        try {
            for (String format : List.of(".schem", ".litematic", ".nbt")) {
                CompanionSchematic read = CompanionSchematics.load(name + format);
                h.assertTrue(read.sizeX() == 5 && read.sizeY() == 4 && read.sizeZ() == 5, format + ": wrong size " + read.describe());
                h.assertTrue(read.blocks() == 25 + 25 + 2 * 16, format + ": wrong block count " + read.blocks());
                for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++)
                    h.assertTrue(house(x, y, z).equals(read.get(x, y, z)), format + ": " + x + " " + y + " " + z + " is " + read.get(x, y, z));
            }
            CompanionSchematic legacy = CompanionSchematics.load(name + "_legacy.schematic");
            h.assertTrue(legacy.get(0, 0, 0).is(Blocks.OAK_PLANKS) && legacy.get(0, 1, 0).is(Blocks.COBBLESTONE) && legacy.get(2, 1, 2).isAir(), "Legacy ids were not upgraded");
            h.assertTrue(CompanionSchematics.list().containsAll(List.of(name + ".schem", name + ".litematic", name + ".nbt", name + "_legacy.schematic")), "Folder listing incomplete");
            h.assertTrue((name + ".litematic").equals(CompanionSchematics.match(List.of(name + ".litematic", "other.schem"), "Test Formats")), "Spoken name not matched");
            // Turned a quarter: the stairs on the east wall face south, and stand on the south side.
            var turned = new CompanionSchematic.Placement(name + ".schem", BlockPos.ZERO, Rotation.CLOCKWISE_90, Mirror.NONE);
            h.assertTrue(turned.state(house(4, 1, 2)).getValue(StairBlock.FACING) == Direction.SOUTH, "Rotation of states");
            h.assertTrue(turned.world(4, 1, 2).equals(new BlockPos(-2, 1, 4)), "Rotation of positions: " + turned.world(4, 1, 2));
        } catch (IOException broken) { throw new UncheckedIOException(broken); }
        finally { delete(name + ".schem", name + ".litematic", name + ".nbt", name + "_legacy.schematic"); }
        h.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void buildsTheHouseFromTheBagAndBreaksWhatIsInTheWay(GameTestHelper h) {
        ground(h);
        String file = "test_bag_house.litematic";
        write(file, litematic(5, 4, 5, CompanionHouseTests::house));
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.OAK_PLANKS, 25)); companion.setItem(1, new ItemStack(Items.COBBLESTONE, 29));
        companion.setItem(2, new ItemStack(Items.OAK_SLAB, 25)); companion.setItem(3, new ItemStack(Items.OAK_DOOR, 1));
        companion.setItem(4, new ItemStack(Items.OAK_STAIRS, 1));
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        // In the way: stone where a wall goes, a dirt pile and a flower inside, where the schematic wants air.
        h.setBlock(SITE.offset(0, 1, 2), Blocks.STONE);
        h.setBlock(SITE.offset(2, 1, 2), Blocks.DIRT); h.setBlock(SITE.offset(2, 2, 2), Blocks.DIRT);
        h.setBlock(SITE.offset(1, 1, 3), Blocks.POPPY);
        var future = run(h, companion, "[{\"action\":\"build_house\"}]", file, Rotation.NONE);
        h.succeedWhen(() -> {
            finished(h, companion, future);
            String report = future.join().report();
            h.assertTrue(report.contains("готов") && !report.contains("План остановлен"), report);
            assertHouse(h, SITE, Blocks.OAK_PLANKS);
            h.assertTrue(h.getBlockState(SITE.offset(2, 1, 2)).isAir() && h.getBlockState(SITE.offset(2, 2, 2)).isAir() && h.getBlockState(SITE.offset(1, 1, 3)).isAir(), "Inside was not cleared");
            h.assertTrue(count(companion, Items.COBBLESTONE) + count(companion, Items.STONE) <= 1 && count(companion, Items.OAK_PLANKS) == 0, "Materials not spent honestly: " + companion.getStoredItemsSummary());
            // Game log 27.10: the dirt of a clearing filled the bag. A low house needs none of it, so it stays on the ground.
            h.assertTrue(count(companion, Items.DIRT) == 0, "Clearing drops hoarded: " + companion.getStoredItemsSummary());
            delete(file);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void gathersTheWoodAPlankHutNeeds(GameTestHelper h) {
        ground(h);
        String file = "test_plank_hut.schem";
        // 3×2×3 of planks with air in the middle of the top layer: 17 planks, nothing in the bag.
        write(file, sponge(3, 2, 3, (x, y, z) -> y == 1 && x == 1 && z == 1 ? Blocks.AIR.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState()));
        for (int x : new int[]{6, 9}) for (int y = GROUND + 1; y <= GROUND + 4; y++) h.setBlock(new BlockPos(x, y, 22), Blocks.OAK_LOG);
        CompanionEntity companion = spawn(h);
        var future = run(h, companion, "[{\"action\":\"build_house\"}]", file, Rotation.NONE);
        h.succeedWhen(() -> {
            finished(h, companion, future);
            String report = future.join().report();
            h.assertTrue(report.contains("готов") && !report.contains("План остановлен"), report);
            int planks = 0;
            for (BlockPos pos : BlockPos.betweenClosed(SITE, SITE.offset(2, 1, 2))) if (h.getBlockState(pos).is(Blocks.OAK_PLANKS)) planks++;
            h.assertTrue(planks == 17, "Hut incomplete: " + planks);
            int logs = 0;
            for (int x : new int[]{6, 9}) for (int y = GROUND + 1; y <= GROUND + 4; y++) if (h.getBlockState(new BlockPos(x, y, 22)).is(Blocks.OAK_LOG)) logs++;
            h.assertTrue(logs <= 3, "Planks did not come from the trees: " + logs + " logs left");
            delete(file);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aChestWithThingsInTheWayIsNotBroken(GameTestHelper h) {
        ground(h);
        String file = "test_chest_house.nbt";
        write(file, structure(3, 1, 3, (x, y, z) -> Blocks.COBBLESTONE.defaultBlockState()));
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 9));
        h.setBlock(SITE.offset(1, 0, 1), Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(SITE.offset(1, 0, 1))).setItem(0, new ItemStack(Items.DIAMOND, 3));
        var future = run(h, companion, "[{\"action\":\"build_house\"}]", file, Rotation.NONE);
        h.succeedWhen(() -> {
            finished(h, companion, future);
            String report = future.join().report();
            h.assertTrue(report.contains("План остановлен") && report.contains("с вещами"), report);
            h.assertTrue(h.getBlockState(SITE.offset(1, 0, 1)).is(Blocks.CHEST) && !((Container) h.getBlockEntity(SITE.offset(1, 0, 1))).isEmpty(), "The chest or its things were lost");
            int cobble = 0;
            for (BlockPos pos : BlockPos.betweenClosed(SITE, SITE.offset(2, 0, 2))) if (h.getBlockState(pos).is(Blocks.COBBLESTONE)) cobble++;
            h.assertTrue(cobble == 8, "The rest was not built: " + cobble);
            delete(file);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void withoutSchematicsNothingIsBuilt(GameTestHelper h) {
        ground(h);
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        CompanionServerPlanPort.Placer empty = (hint, current, cancelled) -> CompletableFuture.completedFuture(new CompanionServerPlanPort.Choice(null, CompanionSchematics.noneFound()));
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\"}]"));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-house", () -> false, (p, i) -> { }, empty));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone(), "Plan still running");
            String report = future.join().report();
            h.assertTrue(report.contains("Схем в папке " + CompanionSchematics.FOLDER + " не найдено"), report);
            h.assertTrue(count(companion, Items.OAK_PLANKS) == 64 && companion.housePlacement() == null && !companion.isWorking(), "Something was started without a schematic");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 4200)
    public static void aStoppedHouseIsFinishedWhereItStands(GameTestHelper h) {
        ground(h);
        String file = "test_resume_house.schem";
        write(file, sponge(5, 4, 5, CompanionHouseTests::house));
        CompanionEntity original = spawn(h);
        original.setItem(0, new ItemStack(Items.OAK_PLANKS, 25)); original.setItem(1, new ItemStack(Items.COBBLESTONE, 29));
        original.setItem(2, new ItemStack(Items.OAK_SLAB, 25)); original.setItem(3, new ItemStack(Items.OAK_DOOR, 1));
        original.setItem(4, new ItemStack(Items.OAK_STAIRS, 1));
        CompanionEntity[] current = {original};
        @SuppressWarnings("unchecked") CompletableFuture<CompanionPlanRunner.Outcome>[] second = new CompletableFuture[1];
        long[] stopped = {-1};
        int[] atStop = {0};
        var first = run(h, original, "[{\"action\":\"build_house\"}]", file, Rotation.CLOCKWISE_180);
        h.onEachTick(() -> {
            if (stopped[0] < 0 && countBlocks(h, Blocks.OAK_PLANKS) >= 12) {
                original.stopGathering();
                stopped[0] = h.getTick(); atStop[0] = countBlocks(h, Blocks.OAK_PLANKS) + countBlocks(h, Blocks.COBBLESTONE);
                CompoundTag data = new CompoundTag(); original.saveWithoutId(data); original.discard();
                h.runAfterDelay(1, () -> {
                    CompanionEntity replacement = AICompanionMod.COMPANION.get().create(h.getLevel());
                    replacement.load(data); h.getLevel().addFreshEntity(replacement); current[0] = replacement;
                });
            }
            if (stopped[0] >= 0 && second[0] == null && h.getTick() - stopped[0] == 30) {
                h.assertTrue(countBlocks(h, Blocks.OAK_PLANKS) + countBlocks(h, Blocks.COBBLESTONE) == atStop[0], "Blocks placed after the stop");
                // «Дострой дом»: the saved house, without asking the player where again.
                CompanionServerPlanPort.Placer never = (hint, place, cancelled) -> CompletableFuture.completedFuture(new CompanionServerPlanPort.Choice(null, "Placement asked again"));
                var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"item\":\"resume\"}]"));
                second[0] = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), current[0].getOwnerId(), "gametest-house", () -> false, (p, i) -> { }, never));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(first.isDone() && second[0] != null, "Not resumed yet");
            finished(h, current[0], second[0]);
            String report = second[0].join().report();
            h.assertTrue(report.contains("готов") && !report.contains("План остановлен"), report);
            // Turned half around: the door is on the south side of the same site.
            BlockPos origin = SITE.offset(4, 0, 4);
            var door = h.getBlockState(origin.offset(-2, 1, 0));
            h.assertTrue(door.getBlock() instanceof DoorBlock && door.getValue(DoorBlock.FACING) == Direction.SOUTH, "Door not where the turned schematic has it: " + door);
            h.assertTrue(countBlocks(h, Blocks.OAK_PLANKS) == 25 && countBlocks(h, Blocks.COBBLESTONE) == 29 && countBlocks(h, Blocks.OAK_SLAB) == 25, "House incomplete after resume");
            h.assertTrue(count(current[0], Items.OAK_PLANKS) == 0 && count(current[0], Items.OAK_SLAB) == 0, "Resume spent extra materials or left them");
            delete(file);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 4800)
    public static void highWallsAreReachedFromScaffoldingThatIsTakenDown(GameTestHelper h) {
        ground(h);
        String file = "test_tall_wall.schem";
        // A 5×8×1 cobblestone wall: its top is far out of reach from the ground.
        write(file, sponge(5, 8, 1, (x, y, z) -> Blocks.COBBLESTONE.defaultBlockState()));
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.COBBLESTONE, 40)); companion.setItem(1, new ItemStack(Items.DIRT, 24));
        var future = run(h, companion, "[{\"action\":\"build_house\"}]", file, Rotation.NONE);
        h.succeedWhen(() -> {
            finished(h, companion, future);
            String report = future.join().report();
            h.assertTrue(report.contains("готов") && !report.contains("План остановлен"), report);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 5; x++)
                h.assertTrue(h.getBlockState(SITE.offset(x, y, 0)).is(Blocks.COBBLESTONE), "Wall missing at " + x + " " + y);
            int dirt = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, GROUND + 1, 1), new BlockPos(30, GROUND + 14, 30))) if (h.getBlockState(pos).is(Blocks.DIRT)) dirt++;
            h.assertTrue(dirt == 0, "Scaffolding left standing: " + dirt);
            delete(file);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void woodOfAnotherSpeciesIsUsedWhenThereIsNoSpruce(GameTestHelper h) {
        ground(h);
        String file = "test_spruce_floor.schem";
        write(file, sponge(3, 1, 3, (x, y, z) -> Blocks.SPRUCE_PLANKS.defaultBlockState()));
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.BIRCH_LOG, 3));
        var future = run(h, companion, "[{\"action\":\"build_house\"}]", file, Rotation.NONE);
        h.succeedWhen(() -> {
            finished(h, companion, future);
            String report = future.join().report();
            h.assertTrue(report.contains("готов") && !report.contains("План остановлен"), report);
            for (BlockPos pos : BlockPos.betweenClosed(SITE, SITE.offset(2, 0, 2))) h.assertTrue(h.getBlockState(pos).is(Blocks.BIRCH_PLANKS), "Not built from birch: " + h.getBlockState(pos));
            delete(file);
        });
    }

    /** Game log 27.10: «Stripped Oak Wood 0 из 7» — logs in the bag were taken for stripped wood, and nuggets came from a shovel. */
    @GameTest(template = "empty")
    public static void theObtainPlanGetsTheExactWoodAndNoRecycledTools(GameTestHelper h) {
        var logs = List.of(new ItemStack(Items.OAK_LOG, 16));
        var wood = dev.aicompanion.entity.CompanionObtain.plan(h.getLevel(), logs, true, "minecraft:oak_wood", 3);
        h.assertTrue(!wood.refused() && wood.steps().stream().anyMatch(step -> step.type().equals("craft_item") && step.item().equals("minecraft:oak_wood")), "Oak wood not crafted: " + wood);
        var stripped = dev.aicompanion.entity.CompanionObtain.plan(h.getLevel(), logs, true, "minecraft:stripped_oak_wood", 3);
        h.assertTrue(stripped.refused() || !stripped.steps().isEmpty(), "Logs counted as stripped wood: " + stripped);
        var nuggets = dev.aicompanion.entity.CompanionObtain.plan(h.getLevel(), List.of(new ItemStack(Items.IRON_INGOT, 2)), true, "minecraft:iron_nugget", 9);
        h.assertTrue(!nuggets.refused() && nuggets.steps().stream().noneMatch(step -> step.type().equals("smelt_items") || step.item().endsWith("_shovel")), "Nuggets by recycling: " + nuggets);
        h.succeed();
    }

    /** Game log 27.10: «начни именно стройку» — the first round went for iron for lanterns before one block stood. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void theBagIsBuiltFirstAndOreWaitsForTheEnd(GameTestHelper h) {
        ground(h);
        String file = "test_round_order.schem";
        write(file, sponge(3, 1, 3, (x, y, z) -> x == 1 && z == 1 ? Blocks.LANTERN.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState()));
        CompanionEntity carrying = spawn(h);
        carrying.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
        var placement = new CompanionSchematic.Placement(file, h.absolutePos(SITE), Rotation.NONE, Mirror.NONE);
        h.assertTrue(carrying.chooseHouse(placement).success(), "Placement refused");
        var first = carrying.houseRound();
        h.assertTrue(first.refusal() == null && first.needs().isEmpty() && first.batch() == 8, "The bag was not built first: " + first);
        CompanionEntity empty = spawn(h);
        h.assertTrue(empty.chooseHouse(placement).success(), "Placement refused");
        var round = empty.houseRound();
        h.assertTrue(round.refusal() == null && round.needs().stream().anyMatch(need -> need.item().equals("minecraft:oak_planks"))
                && round.needs().stream().noneMatch(need -> need.item().equals("minecraft:lantern")), "Ore before the cheap planks: " + round);
        delete(file);
        h.succeed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void strippedLogsAreStrippedWithAnAxe(GameTestHelper h) {
        ground(h);
        String file = "test_stripped.schem";
        write(file, sponge(3, 1, 1, (x, y, z) -> Blocks.STRIPPED_OAK_LOG.defaultBlockState()));
        CompanionEntity companion = spawn(h);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 3)); companion.setItem(1, new ItemStack(Items.WOODEN_AXE));
        var future = run(h, companion, "[{\"action\":\"build_house\"}]", file, Rotation.NONE);
        h.succeedWhen(() -> {
            finished(h, companion, future);
            String report = future.join().report();
            h.assertTrue(report.contains("готов") && !report.contains("План остановлен"), report);
            for (int x = 0; x < 3; x++) h.assertTrue(h.getBlockState(SITE.offset(x, 0, 0)).is(Blocks.STRIPPED_OAK_LOG), "Not stripped at " + x);
            int wear = -1;
            for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(Items.WOODEN_AXE)) wear = companion.getItem(slot).getDamageValue();
            if (companion.getMainHandItem().is(Items.WOODEN_AXE)) wear = companion.getMainHandItem().getDamageValue();
            h.assertTrue(count(companion, Items.OAK_LOG) == 0 && wear == 3, "Logs or axe wear not real: wear " + wear + ", " + companion.getStoredItemsSummary());
            delete(file);
        });
    }

    @GameTest(template = "empty")
    public static void houseRequestsAreUnderstood(GameTestHelper h) {
        h.assertTrue("build_house".equals(CompanionWorkCommands.parse("Олег, построй нам небольшой деревянный дом", "Олег").type()), "House command not recognized");
        PlannedAction resume = CompanionWorkCommands.parse("дострой дом", "Олег");
        h.assertTrue(resume != null && "build_house".equals(resume.type()) && CompanionServerPlanPort.HOUSE_RESUME.equals(resume.item()), "Resume not recognized");
        h.assertTrue(CompanionWorkCommands.parse("не построй дом", "Олег") == null && CompanionWorkCommands.parse("построй дом и вернись", "Олег") == null, "Partial/negative command accepted");
        h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\"}]")).size() == 1, "House model protocol rejected");
        h.assertTrue("Замок у реки.litematic".equals(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"item\":\"Замок у реки.litematic\"}]")).getFirst().item()), "Schematic name lost");
        for (String bad : List.of("[{\"action\":\"build_house\",\"count\":2}]", "[{\"action\":\"build_house\",\"item\":\"../../evil\"}]", "[{\"action\":\"build_house\",\"item\":\"continue\"}]")) {
            boolean rejected = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(bad)); } catch (IllegalStateException expected) { rejected = true; }
            h.assertTrue(rejected, "Accepted: " + bad);
        }
        boolean refused = false;
        try { CompanionSchematics.load("../options.txt"); } catch (IOException expected) { refused = true; }
        h.assertTrue(refused, "A file outside the schematic folder was read");
        h.succeed();
    }

    // ---- Helpers ----

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity companion, String json, String file, Rotation rotation) {
        BlockPos origin = h.absolutePos(rotation == Rotation.CLOCKWISE_180 ? SITE.offset(4, 0, 4) : SITE);
        CompanionServerPlanPort.Placer placer = (hint, current, cancelled) -> CompletableFuture.completedFuture(
                new CompanionServerPlanPort.Choice(new CompanionSchematic.Placement(file, origin, rotation, Mirror.NONE), null));
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-house", () -> false, (p, i) -> { }, placer));
    }

    private static void finished(GameTestHelper h, CompanionEntity companion, CompletableFuture<CompanionPlanRunner.Outcome> future) {
        h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + companion.getLastTaskResult() + " Bag: " + companion.getStoredItemsSummary());
    }

    private static void assertHouse(GameTestHelper h, BlockPos origin, Block floor) {
        for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
            BlockState wanted = house(x, y, z), actual = h.getBlockState(origin.offset(x, y, z));
            if (wanted.is(Blocks.OAK_PLANKS)) wanted = floor.defaultBlockState();
            h.assertTrue(actual.getBlock() == wanted.getBlock(), "House wrong at " + x + " " + y + " " + z + ": " + actual);
            if (wanted.getBlock() instanceof DoorBlock) h.assertTrue(actual.getValue(DoorBlock.HALF) == wanted.getValue(DoorBlock.HALF), "Door half wrong");
            if (wanted.getBlock() instanceof StairBlock) h.assertTrue(actual.getValue(StairBlock.FACING) == wanted.getValue(StairBlock.FACING), "Stairs face the wrong way");
        }
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 14, GROUND + 1, 16);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(12, GROUND + 1, 18));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(32);
        return companion;
    }

    private static int count(Container bag, Item item) {
        int count = 0; for (int slot = 0; slot < bag.getContainerSize(); slot++) if (bag.getItem(slot).is(item)) count += bag.getItem(slot).getCount();
        if (bag instanceof CompanionEntity companion) for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
    private static int countBlocks(GameTestHelper h, Block block) {
        int count = 0; for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, GROUND + 1, 0), new BlockPos(31, GROUND + 10, 31))) if (h.getBlockState(pos).is(block)) count++; return count;
    }

    // ---- Schematic files, written the way their tools write them ----

    static void write(String file, CompoundTag root) {
        try {
            Files.createDirectories(CompanionSchematics.folder());
            NbtIo.writeCompressed(root, CompanionSchematics.folder().resolve(file));
        } catch (IOException broken) { throw new UncheckedIOException(broken); }
    }
    static void delete(String... files) {
        for (String file : files) try { Files.deleteIfExists(CompanionSchematics.folder().resolve(file)); } catch (IOException ignored) { }
    }
    private static int version() { return SharedConstants.getCurrentVersion().getDataVersion().getVersion(); }

    static CompoundTag sponge(int width, int height, int length, Shape shape) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2); root.putInt("DataVersion", version());
        root.putShort("Width", (short) width); root.putShort("Height", (short) height); root.putShort("Length", (short) length);
        Map<String, Integer> palette = new LinkedHashMap<>();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int y = 0; y < height; y++) for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) {
            int id = palette.computeIfAbsent(BlockStateParser.serialize(shape.at(x, y, z)), key -> palette.size());
            while ((id & ~0x7F) != 0) { data.write(id & 0x7F | 0x80); id >>>= 7; }
            data.write(id);
        }
        CompoundTag paletteTag = new CompoundTag();
        palette.forEach(paletteTag::putInt);
        root.put("Palette", paletteTag); root.putInt("PaletteMax", palette.size()); root.putByteArray("BlockData", data.toByteArray());
        return root;
    }

    /** One region whose size runs towards smaller z, as Litematica saves a selection made from the far corner. */
    static CompoundTag litematic(int width, int height, int length, Shape shape) {
        List<BlockState> palette = new java.util.ArrayList<>(List.of(Blocks.AIR.defaultBlockState()));
        int volume = width * height * length;
        int[] cells = new int[volume];
        for (int y = 0; y < height; y++) for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) {
            BlockState state = shape.at(x, y, z);
            int id = palette.indexOf(state);
            if (id < 0) { palette.add(state); id = palette.size() - 1; }
            cells[(y * length + z) * width + x] = id;
        }
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long[] packed = new long[(int) ((volume * (long) bits + 63) / 64)];
        for (int index = 0; index < volume; index++) {
            long start = (long) index * bits;
            int first = (int) (start >> 6), offset = (int) (start & 63);
            packed[first] |= (long) cells[index] << offset;
            if (offset + bits > 64) packed[first + 1] |= (long) cells[index] >>> (64 - offset);
        }
        CompoundTag region = new CompoundTag();
        CompoundTag position = new CompoundTag(); position.putInt("x", 0); position.putInt("y", 0); position.putInt("z", length - 1);
        CompoundTag size = new CompoundTag(); size.putInt("x", width); size.putInt("y", height); size.putInt("z", -length);
        region.put("Position", position); region.put("Size", size);
        ListTag states = new ListTag();
        for (BlockState state : palette) states.add(NbtUtils.writeBlockState(state));
        region.put("BlockStatePalette", states); region.putLongArray("BlockStates", packed);
        CompoundTag regions = new CompoundTag(); regions.put("Main", region);
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 6); root.putInt("MinecraftDataVersion", version()); root.put("Regions", regions);
        return root;
    }

    static CompoundTag structure(int width, int height, int length, Shape shape) {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", version());
        ListTag size = new ListTag(); size.add(IntTag.valueOf(width)); size.add(IntTag.valueOf(height)); size.add(IntTag.valueOf(length));
        root.put("size", size);
        List<BlockState> palette = new java.util.ArrayList<>();
        ListTag blocks = new ListTag();
        for (int y = 0; y < height; y++) for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) {
            BlockState state = shape.at(x, y, z);
            int id = palette.indexOf(state);
            if (id < 0) { palette.add(state); id = palette.size() - 1; }
            CompoundTag block = new CompoundTag();
            ListTag pos = new ListTag(); pos.add(IntTag.valueOf(x)); pos.add(IntTag.valueOf(y)); pos.add(IntTag.valueOf(z));
            block.put("pos", pos); block.putInt("state", id);
            blocks.add(block);
        }
        ListTag states = new ListTag();
        for (BlockState state : palette) states.add(NbtUtils.writeBlockState(state));
        root.put("palette", states); root.put("blocks", blocks); root.put("entities", new ListTag());
        return root;
    }

    static CompoundTag legacy(int width, int height, int length, Shape shape) {
        CompoundTag root = new CompoundTag();
        root.putShort("Width", (short) width); root.putShort("Height", (short) height); root.putShort("Length", (short) length);
        root.putString("Materials", "Alpha");
        byte[] ids = new byte[width * height * length], data = new byte[ids.length];
        for (int y = 0; y < height; y++) for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) {
            BlockState state = shape.at(x, y, z);
            ids[(y * length + z) * width + x] = (byte) (state.is(Blocks.OAK_PLANKS) ? 5 : state.is(Blocks.COBBLESTONE) ? 4 : 0);
        }
        root.putByteArray("Blocks", ids); root.putByteArray("Data", data);
        return root;
    }
}

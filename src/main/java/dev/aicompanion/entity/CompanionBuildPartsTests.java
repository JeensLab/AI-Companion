package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.gametest.CompanionBuildReviewTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.TreeSet;

/**
 * The parts of the village buildings that are not reachable from outside the package: what the catalogue holds that he cannot get
 * himself, the scaffolding that comes down with a full bag or after grass has spread over it, and the dirt that is not dug under a building.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBuildPartsTests {
    /**
     * What the sixteen buildings hold that no skill of his makes (glass and bricks and stone need a furnace, a torch coal, a bed wool,
     * a smithing table and a blast furnace iron, a brewing stand a blaze rod, ice is had with silk touch, a stripped log with an axe and
     * dyed or baked clay in a furnace): the player brings these. Wood blocks (acacia_wood) too: a round plans a log for each, the recipe
     * takes four logs for three, so the round never makes enough. A new building with anything else on its palette fails the test.
     */
    private static final Set<String> PLAYER_BRINGS = Set.of("glass_pane", "torch", "white_bed", "yellow_bed", "cyan_bed", "orange_bed", "blue_bed", "purple_bed",
            "blast_furnace", "smithing_table", "brewing_stand", "bricks", "smooth_stone", "smooth_sandstone", "smooth_sandstone_slab", "smooth_sandstone_stairs",
            "terracotta", "white_terracotta", "yellow_terracotta", "red_terracotta", "blue_ice", "packed_ice", "stripped_oak_log", "acacia_wood");

    @GameTest(template = "empty")
    public static void everyBlockOfTheCatalogueIsGettableOrOnTheListOfWhatThePlayerBrings(GameTestHelper h) {
        CompanionEntity oleg = h.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2);
        Set<String> brought = new TreeSet<>();
        for (var building : CompanionVillageBuildings.all()) {
            var read = CompanionTemplates.load(h.getLevel(), building, Rotation.NONE);
            h.assertTrue(read != null, "«" + building.name() + "» is not read");
            // The species as the template has them: what grows around (the other tests' groves too) is no part of what the palette holds.
            var materials = new CompanionHouseMaterials(oleg);
            for (var cell : read.cells()) {
                BlockState built = materials.adapt(cell.state());
                h.assertTrue(!CompanionTemplates.groundCover(cell.state()), "«" + building.name() + "» still asks for " + cell.state().getBlock() + ": a plant or snow of its ground");
                h.assertTrue(!built.is(Blocks.DIRT_PATH) && !built.is(Blocks.GRASS_BLOCK) && !built.is(Blocks.PODZOL), "«" + building.name() + "» is built of " + built.getBlock() + ", which is had only with silk touch");
            }
            // A round plans only a few kinds at a time: the whole building says what is lacking of the first ones, each kind alone says it of every one.
            var required = CompanionBuildSummary.required(materials, read);
            java.util.List<java.util.Set<net.minecraft.world.item.Item>> lacking = new java.util.ArrayList<>();
            lacking.add(materials.next(h.getLevel(), required).lacking().keySet());
            for (var kind : required.entrySet()) lacking.add(new CompanionHouseMaterials(oleg).next(h.getLevel(), java.util.Map.of(kind.getKey(), kind.getValue())).lacking().keySet());
            for (var items : lacking) for (var item : items) {
                String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
                h.assertTrue(PLAYER_BRINGS.contains(path), "«" + building.name() + "» needs " + path + ", which he cannot get and which is not on the list of what the player brings (" + CompanionObtain.plan(h.getLevel(), java.util.List.of(), false, "minecraft:" + path, 4) + ")");
                brought.add(path);
            }
        }
        h.assertTrue(brought.equals(PLAYER_BRINGS), "The list of what the player brings is not what the catalogue holds: " + brought);
        h.assertTrue(materials(oleg).adapt(Blocks.DIRT_PATH.defaultBlockState()).is(Blocks.DIRT), "A path is not built as dirt");
        h.succeed();
    }

    private static CompanionHouseMaterials materials(CompanionEntity owner) { return new CompanionHouseMaterials(owner); }

    // ---- scaffolding

    private record Column(CompanionEntity oleg, CompanionScaffold scaffold) { }

    /** Stone ground at the floor of the arena, air above, a column of the given blocks at (18, 2.., 16) saved as a stopped build would leave it. */
    private static Column column(GameTestHelper h, BlockState... blocks) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK); h.setBlock(new BlockPos(x, 1, z), Blocks.STONE); for (int y = 2; y < 10; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR); }
        CompanionEntity oleg = h.spawn(AICompanionMod.COMPANION.get(), 16, 2, 16);
        oleg.setFollowing(false);
        oleg.setItem(oleg.getContainerSize() - 2, new ItemStack(Items.IRON_SHOVEL));
        ListTag saved = new ListTag();
        for (int level = 0; level < blocks.length; level++) {
            BlockPos at = new BlockPos(18, 2 + level, 16);
            h.setBlock(at, blocks[level]);
            saved.add(LongTag.valueOf(h.absolutePos(at).asLong()));
        }
        CompoundTag tag = new CompoundTag(); tag.put("Scaffold", saved);
        var scaffold = new CompanionScaffold(oleg);
        scaffold.load(tag); scaffold.startDescend();
        return new Column(oleg, scaffold);
    }

    /** Grass spreads onto dirt: a block of the column that turned into it is still his, comes down and leaves nothing in the world. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aColumnBlockThatTurnedIntoGrassIsStillHisAndComesDown(GameTestHelper h) {
        var column = column(h, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        CompanionScaffold.Outcome[] outcome = {CompanionScaffold.Outcome.WORKING};
        h.onEachTick(() -> { if (outcome[0] == CompanionScaffold.Outcome.WORKING) outcome[0] = column.scaffold().descend(h.getLevel()); });
        h.succeedWhen(() -> {
            h.assertTrue(outcome[0] == CompanionScaffold.Outcome.READY, "The column is not down: " + outcome[0]);
            h.assertTrue(h.getBlockState(new BlockPos(18, 2, 16)).isAir() && h.getBlockState(new BlockPos(18, 3, 16)).isAir(), "A block of the column was left in the world as " + h.getBlockState(new BlockPos(18, 2, 16)).getBlock());
        });
    }

    /** A full bag: the dirt of the column would lie on the ground and go. He stops with the reason, the column stays, saved for «дострой». */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aFullBagStopsTheDescentInsteadOfDroppingTheDirtOnTheGround(GameTestHelper h) {
        var column = column(h, Blocks.DIRT.defaultBlockState());
        for (int slot = 0; slot < column.oleg().getContainerSize(); slot++) column.oleg().setItem(slot, new ItemStack(Items.IRON_SWORD));
        CompanionScaffold.Outcome[] outcome = {CompanionScaffold.Outcome.WORKING};
        h.onEachTick(() -> { if (outcome[0] == CompanionScaffold.Outcome.WORKING) outcome[0] = column.scaffold().descend(h.getLevel()); });
        h.succeedWhen(() -> {
            h.assertTrue(outcome[0] == CompanionScaffold.Outcome.FAILED && column.scaffold().failure().contains("нет места"), "No stop with a reason: " + outcome[0] + " " + column.scaffold().failure());
            h.assertTrue(h.getBlockState(new BlockPos(18, 2, 16)).is(Blocks.DIRT) && column.scaffold().hasBlocks(), "The column was taken down into a full bag");
        });
    }

    /**
     * A full bag with him on top of the column: the first block broken is the one under his feet, and to stop there would leave him standing
     * up to ten blocks high with no way down. He comes down until a step is all that is left (there the stop for «освободи место» is safe),
     * and the dirt that does not fit lies where its blocks were, said with how much.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aFullBagDoesNotLeaveHimStandingOnTopOfTheColumn(GameTestHelper h) {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        var column = column(h, dirt, dirt, dirt, dirt);
        for (int slot = 0; slot < column.oleg().getContainerSize(); slot++) column.oleg().setItem(slot, new ItemStack(Items.IRON_SWORD));
        BlockPos top = h.absolutePos(new BlockPos(18, 6, 16));
        column.oleg().teleportTo(top.getX() + .5, top.getY(), top.getZ() + .5);
        CompanionScaffold.Outcome[] outcome = {CompanionScaffold.Outcome.WORKING};
        h.onEachTick(() -> { if (outcome[0] == CompanionScaffold.Outcome.WORKING) outcome[0] = column.scaffold().descend(h.getLevel()); });
        h.succeedWhen(() -> {
            h.assertTrue(outcome[0] == CompanionScaffold.Outcome.FAILED && column.scaffold().failure().contains("нет места"), "No stop with the reason at the foot of the column: " + outcome[0] + " " + column.scaffold().failure());
            for (int y = 3; y <= 5; y++) h.assertTrue(h.getBlockState(new BlockPos(18, y, 16)).isAir(), "A block of the column stands at height " + y + ": he is left up on it");
            h.assertTrue(h.getBlockState(new BlockPos(18, 2, 16)).is(Blocks.DIRT) && column.scaffold().hasBlocks(), "The first block, the step, is not saved for «дострой»");
            h.assertTrue(column.oleg().getY() <= h.absolutePos(new BlockPos(0, 3, 0)).getY() + .1 && column.oleg().onGround(), "He is not down at the step: y " + column.oleg().getY());
            String note = column.scaffold().takeNote();
            h.assertTrue(note.contains("3 блоков земли") && note.contains(h.absolutePos(new BlockPos(18, 3, 16)).toShortString()), "The dirt left on the ground was not said with how much and where: " + note);
        });
    }

    /** The wait for an occupied cell is counted over all of it since the last block: a mob that goes from one cell to another does not start it again. */
    @GameTest(template = "empty")
    public static void theWaitForAnOccupiedCellIsCountedInAllSinceTheLastBlock(GameTestHelper h) {
        var room = new CompanionHouseRoom(h.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2), null);
        for (int tick = 1; tick <= CompanionHouseRoom.WAIT_TICKS / 2; tick++) h.assertTrue(room.tickWait(), "The wait ended early at " + tick);
        // For a tick nobody stands in any cell.
        room.refresh(h.getLevel(), java.util.List.of(), new java.util.BitSet());
        for (int tick = 1; tick <= CompanionHouseRoom.WAIT_TICKS / 2; tick++) h.assertTrue(room.tickWait(), "The wait ended early after the cell was free: " + tick);
        h.assertTrue(!room.tickWait(), "A cell that was free for a tick started the five minutes again");
        room.placed();
        h.assertTrue(room.tickWait(), "A block set down does not start the wait again");
        h.succeed();
    }

    // ---- a building that already stands

    /**
     * Grass grows on dirt: a cell planned as dirt (a grass block, podzol or a path of the template) that stands as a grass block, podzol or a
     * path is built. At the final check he does not dig it up to put dirt back.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aGrassBlockWhereDirtWasPlannedIsBuiltAndNotDugUpToPutDirtBack(GameTestHelper h) {
        var level = h.getLevel();
        CompanionVillageBuildings.Building chosen = null;
        for (var building : CompanionVillageBuildings.all()) {
            var read = CompanionTemplates.load(level, building, Rotation.NONE);
            for (var cell : read.cells()) if (cell.state().is(Blocks.GRASS_BLOCK) || cell.state().is(Blocks.PODZOL) || cell.state().is(Blocks.DIRT_PATH)) chosen = building;
            if (chosen != null) break;
        }
        h.assertTrue(chosen != null, "No building of the catalogue has a cell of grass, podzol or a path: the premise is gone");
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK); h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            for (int y = 2; y < 14; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        BlockPos base = h.absolutePos(new BlockPos(4, 2, 4));
        level.getServer().getStructureManager().get(chosen.template()).orElseThrow()
                .placeInWorld(level, base, base, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setIgnoreEntities(true), net.minecraft.util.RandomSource.create(1), 2);
        CompanionEntity oleg = h.spawn(AICompanionMod.COMPANION.get(), 20, 2, 20);
        var player = h.makeMockServerPlayerInLevel(); player.teleportTo(oleg.getX() - 3, oleg.getY(), oleg.getZ() - 3);
        oleg.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", oleg.getUUID());
        CompanionEvents.getOwnedCompanion(player); oleg.setSearchRadius(16);
        oleg.setItem(oleg.getContainerSize() - 2, new ItemStack(Items.IRON_SHOVEL));
        CompanionEntity builder = CompanionBuildReviewTests.reloadedWithUnfinished(h, oleg, base, chosen.name());
        h.assertTrue(builder.startWork("build_house", "resume", 1).success(), "Resume rejected");
        h.runAfterDelay(200, () -> {
            h.assertTrue(!builder.isWorking() && builder.getLastTaskResult().startsWith("Построил"), "The building that stands was not found finished: " + builder.getLastTaskResult());
            h.succeed();
        });
    }

    // ---- what stands round the box

    /** When no way to the blocks is found, what is left standing above the roof and beside the walls is named with where it is (never the ground, never air). */
    @GameTest(template = "tall_tree")
    public static void whatStandsAboveTheRoofIsNamedWhereItCanBeTold(GameTestHelper h) {
        var level = h.getLevel();
        // The arena of the empty template has a ceiling of barriers, the tall one a tree: both are swept from the place.
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(6, 2, 6), new BlockPos(16, 12, 16))) h.setBlock(rel, Blocks.AIR);
        CompanionEntity oleg = h.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2);
        var read = CompanionTemplates.load(level, CompanionVillageBuildings.find("пустынный дом"), Rotation.NONE);
        var clearing = new CompanionSiteClearing(oleg, new CompanionHouse(oleg, true));
        clearing.use(read);
        BlockPos base = h.absolutePos(new BlockPos(8, 2, 8));
        h.assertTrue(clearing.inTheWay(level, base).isEmpty(), "Something was named in an empty place: " + clearing.inTheWay(level, base));
        level.setBlock(base.offset(-1, 0, 2), Blocks.DIRT.defaultBlockState(), 3);
        h.assertTrue(clearing.inTheWay(level, base).isEmpty(), "The ground was named");
        BlockPos beside = base.offset(-1, 2, 2), above = base.offset(2, read.sizeY(), 2);
        level.setBlock(beside, Blocks.OAK_LOG.defaultBlockState(), 3);
        String said = clearing.inTheWay(level, base);
        h.assertTrue(said.contains("Рядом со стенами") && said.contains(beside.toShortString()), "The log beside the wall was not named: " + said);
        level.setBlock(above, Blocks.DARK_OAK_LEAVES.defaultBlockState(), 3);
        said = clearing.inTheWay(level, base);
        h.assertTrue(said.contains("Над крышей") && said.contains(above.toShortString()), "What is above the roof comes first: " + said);
        level.setBlock(above, Blocks.AIR.defaultBlockState(), 3); level.setBlock(beside, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(base.offset(2, read.sizeY() + 2, 2), Blocks.OAK_LOG.defaultBlockState(), 3);
        h.assertTrue(clearing.inTheWay(level, base).isEmpty(), "A cell beyond the head room was named");
        h.succeed();
    }

    // ---- the dirt for the scaffolding

    /** Dirt is not dug from the ground the building stands on and walks in from, nor from its scaffolding; away from it, it is. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void dirtUnderAndAroundTheBuildingIsNotDugForScaffolding(GameTestHelper h) {
        // Dirt (sand falls: no cell beside it would be safe to dig), a few ticks of the order: the template is read at its first tick.
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK); h.setBlock(new BlockPos(x, 1, z), Blocks.DIRT); }
        CompanionEntity oleg = h.spawn(AICompanionMod.COMPANION.get(), 16, 2, 16);
        var player = h.makeMockServerPlayerInLevel(); player.teleportTo(oleg.getX() - 5, oleg.getY(), oleg.getZ() - 5);
        oleg.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", oleg.getUUID());
        CompanionEvents.getOwnedCompanion(player); oleg.setSearchRadius(16);
        BlockPos base = h.absolutePos(new BlockPos(4, 1, 4));
        CompanionEntity builder = CompanionBuildReviewTests.reloadedWithUnfinished(h, oleg, base, "деревенский дом");
        h.assertTrue(builder.startWork("build_house", "resume", 1).success(), "Resume rejected");
        h.runAfterDelay(3, () -> {
            var level = h.getLevel();
            h.assertTrue(builder.work.isActive(), "The building work is over: " + builder.getLastTaskResult());
            BlockPos beneath = base.offset(2, 0, 2), beside = base.offset(-1, 0, 2), away = h.absolutePos(new BlockPos(26, 1, 10));
            h.assertTrue(!CompanionGround.SOIL.safeCell(level, beneath, builder) && !CompanionGround.SOIL.safeCell(level, beside, builder), "Dirt under or beside the building is dug");
            h.assertTrue(CompanionGround.SOIL.safeCell(level, away, builder), "Dirt away from the building is not dug either");
            builder.stopGathering();
            h.succeed();
        });
    }
}

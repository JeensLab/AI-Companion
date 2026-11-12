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
            for (var item : materials.next(h.getLevel(), CompanionBuildPlacement.required(materials, read)).lacking().keySet()) {
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

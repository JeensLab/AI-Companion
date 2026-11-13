package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBuildPlacement;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionSight;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static dev.aicompanion.gametest.CompanionHouseTests.GROUND;
import static dev.aicompanion.gametest.CompanionHouseTests.fill;
import static dev.aicompanion.gametest.CompanionHouseTests.groundedSetup;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBuildReachTests {
    static final String TAIGA = "таёжный дом";
    static final ResourceLocation TAIGA_TEMPLATE = ResourceLocation.withDefaultNamespace("village/taiga/houses/taiga_small_house_4");
    /** Where the building goes (relative): clear of the reference at (1, 1, 1), the companion's start and the player. */
    static final BlockPos CORNER = new BlockPos(18, GROUND, 12);

    /** The vanilla template at the arena's corner as the reference; what the bag needs to build one more like it. */
    static Map<Item, Integer> reference(GameTestHelper h, ResourceLocation id) {
        var template = h.getLevel().getServer().getStructureManager().get(id).orElseThrow();
        var size = template.getSize();
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(size.getX() + 1, size.getY() + 1, size.getZ() + 1))) h.setBlock(rel, Blocks.AIR);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        template.placeInWorld(h.getLevel(), at, at, new StructurePlaceSettings().setIgnoreEntities(true), RandomSource.create(1), 2);
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(size.getX(), size.getY(), size.getZ()))) {
            BlockState state = h.getBlockState(rel);
            if (!counts(state) || secondHalf(state)) continue;
            needed.merge(state.getBlock().asItem(), 1, Integer::sum);
        }
        return needed;
    }
    static boolean counts(BlockState state) {
        return !state.isAir() && !state.is(Blocks.JIGSAW) && !state.is(Blocks.STRUCTURE_VOID) && !state.is(BlockTags.FLOWERS) && !state.is(Blocks.SHORT_GRASS)
                && !state.is(Blocks.TALL_GRASS) && !state.is(Blocks.FERN) && !state.is(Blocks.LARGE_FERN) && !state.is(Blocks.DEAD_BUSH) && !state.is(Blocks.SNOW);
    }
    static boolean secondHalf(BlockState state) {
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                || state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    /** Every block of the reference stands at the same place of the building at {@code corner}. */
    static void assertBuilt(GameTestHelper h, ResourceLocation id, BlockPos corner) { assertBuilt(h, id, corner, null, Set.of()); }
    static void assertBuilt(GameTestHelper h, ResourceLocation id, BlockPos corner, BlockPos except) { assertBuilt(h, id, corner, except, Set.of()); }
    /** As above, but the cells {@code except} and {@code others} (relative to the corner) are left out. */
    static void assertBuilt(GameTestHelper h, ResourceLocation id, BlockPos corner, BlockPos except, Set<BlockPos> others) {
        var size = h.getLevel().getServer().getStructureManager().get(id).orElseThrow().getSize();
        BlockPos reference = h.absolutePos(new BlockPos(1, 1, 1));
        int checked = 0;
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(size.getX(), size.getY(), size.getZ()))) {
            BlockState want = h.getBlockState(rel);
            if (!counts(want) || h.absolutePos(rel).subtract(reference).equals(except) || others.contains(h.absolutePos(rel).subtract(reference))) continue;
            BlockState got = h.getLevel().getBlockState(corner.offset(h.absolutePos(rel).subtract(reference)));
            // A path or podzol is built as plain dirt (a path block is had only with silk touch), and grass grows over it.
            boolean ground = want.is(Blocks.GRASS_BLOCK) || want.is(Blocks.PODZOL) || want.is(Blocks.DIRT_PATH) || want.is(Blocks.MYCELIUM);
            h.assertTrue(ground ? got.is(BlockTags.DIRT) || got.is(Blocks.DIRT_PATH) : got.is(want.getBlock()), "Block " + h.absolutePos(rel).subtract(reference) + " is " + got.getBlock() + ", the template has " + want.getBlock());
            checked++;
        }
        h.assertTrue(checked > 40, "Reference too small to prove anything: " + checked);
    }

    /** Air round and above the building's corner, as the arena's other building tests have it. */
    static void clearSite(GameTestHelper h, int width, int depth, int height) {
        for (BlockPos rel : BlockPos.betweenClosed(CORNER.offset(-2, 1, -2), CORNER.offset(width + 2, height + 6, depth + 2))) h.setBlock(rel, Blocks.AIR);
    }

    /** The live case of 12.11 (journal event build_blocked): 130 of 149 blocks of «таёжный дом» stand and these 19 are left. */
    private static final int[][] LEFT = {{2, 1, 2}, {3, 4, 5}, {3, 4, 3}, {3, 4, 2}, {3, 5, 7}, {3, 5, 5}, {3, 5, 3}, {2, 5, 3}, {4, 5, 3}, {0, 5, 3}, {3, 5, 2}, {2, 5, 2},
            {4, 5, 2}, {6, 5, 3}, {0, 5, 2}, {6, 5, 2}, {4, 1, 3}, {3, 1, 6}, {3, 1, 4}};

    /** The state of the journal event: 130 of 149 blocks stand, the player is not in the house, the bag has what the 19 take; «дострой» finishes the house. */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void theTaigaHouseLeftAsInTheLiveGameIsFinishedByResuming(GameTestHelper h) {
        liveState(h, false);
    }

    /**
     * The same, with a block of somebody else (diamond) in the cell of the ridge log above the roof cell (3, 4, 5): that cell is shut on every
     * side, and the log above it cannot be set down. Everything else is built, the doors too; the order says that the two are left and why
     * (the shut one cannot be seen from anywhere, the other has a block in its cell); once the diamond is gone, «дострой» builds the rest.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aRoofCellShutInBySomebodyElsesBlockIsNamedAndTheRestIsBuilt(GameTestHelper h) {
        liveState(h, true);
    }

    private static void liveState(GameTestHelper h, boolean shut) {
        CompanionEntity oleg = groundedSetup(h);
        reference(h, TAIGA_TEMPLATE);
        clearSite(h, 7, 8, 6);
        var template = h.getLevel().getServer().getStructureManager().get(TAIGA_TEMPLATE).orElseThrow();
        BlockPos corner = h.absolutePos(CORNER);
        template.placeInWorld(h.getLevel(), corner, corner, new StructurePlaceSettings().setIgnoreEntities(true), RandomSource.create(1), 2);
        for (BlockPos pos : BlockPos.betweenClosed(corner, corner.offset(8, 7, 9))) if (h.getLevel().getBlockState(pos).is(Blocks.JIGSAW)) h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        Map<Item, Integer> missing = new LinkedHashMap<>();
        for (int[] at : LEFT) {
            BlockPos pos = corner.offset(at[0], at[1], at[2]);
            BlockState state = h.getLevel().getBlockState(pos);
            h.assertTrue(counts(state), "The live case has " + state + " at " + pos.subtract(corner));
            missing.merge(state.getBlock().asItem(), 1, Integer::sum);
            if (state.getBlock() instanceof DoorBlock) h.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            if (state.getBlock() instanceof BedBlock) h.getLevel().setBlock(pos.relative(state.getValue(BedBlock.FACING)), Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        BlockPos roof = corner.offset(3, 4, 5), ridge = corner.offset(3, 5, 5);
        CompanionEntity c = CompanionBuildReviewTests.reloadedWithUnfinished(h, oleg, corner, TAIGA);
        fill(c, missing);
        c.setItem(c.getContainerSize() - 5, new ItemStack(Items.DIRT, 16));
        c.setFollowing(false);
        // He stands where he did in the game: on the doorstep, outside.
        BlockPos stand = corner.offset(3, 1, 7);
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        h.assertTrue(c.startWork("build_house", "resume", 1).success(), "Resume rejected");
        int[] phase = {0};
        // Once the building is read and prepared (a cell in the way is told at that moment, not later).
        if (shut) h.runAfterDelay(5, () -> h.getLevel().setBlock(ridge, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3));
        h.onEachTick(() -> {
            if (!shut || phase[0] != 0 || h.getTick() < 20 || c.isWorking() || c.isGatheringTask()) return;
            phase[0] = 1;
            String said = c.getLastTaskResult();
            h.assertTrue(!c.didLastTaskSucceed() && said.startsWith("Не могу подойти") && said.contains("осталось 2 из") && said.contains("Стройку сохранил"), "Not said as two blocks left: " + said);
            h.assertTrue(said.contains(roof.toShortString() + ": не вижу, откуда поставить") && said.contains(ridge.toShortString() + ": мешает: " + Blocks.DIAMOND_BLOCK.getName().getString()), "The two blocks and why are not named: " + said);
            h.assertTrue(h.getLevel().getBlockState(roof).isAir() && h.getLevel().getBlockState(ridge).is(Blocks.DIAMOND_BLOCK), "The shut cell was set down or the diamond dug out");
            // Everything else stands, the doors and the bed too.
            assertBuilt(h, TAIGA_TEMPLATE, corner, null, Set.of(roof.subtract(corner), ridge.subtract(corner)));
            h.getLevel().setBlock(ridge, Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(c.startWork("build_house", "resume", 1).success(), "Second resume rejected");
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 20 && (!shut || phase[0] == 1) && !c.isWorking() && !c.isGatheringTask(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            assertBuilt(h, TAIGA_TEMPLATE, corner);
        });
    }

    // ---- a block nobody can set down holds nothing else back

    /** The 5×5×5 desert house of the catalogue. */
    private static final String DESERT = "пустынный дом";
    private static final ResourceLocation DESERT_TEMPLATE = ResourceLocation.withDefaultNamespace("village/desert/houses/desert_small_house_4");

    /**
     * One block of the first wall layer gets somebody else's block (diamond) in its cell once the walls are begun: the cell is not his to dig.
     * Everything else is built, the door too; the order ends naming that block, why, and how many are left; after the diamond is taken away
     * «дострой» sets the block down and the building is done.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void aBlockInACellNotHisToDigHoldsNothingElseBackAndIsNamed(GameTestHelper h) {
        CompanionEntity c = groundedSetup(h);
        ServerPlayer player = CompanionBuildReviewTests.playerOf(h);
        CompanionBuildPlacement.clientReady(player);
        Map<Item, Integer> bag = reference(h, DESERT_TEMPLATE);
        BlockPos reference = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos corner = h.absolutePos(CORNER);
        fill(c, bag);
        c.setItem(c.getContainerSize() - 5, new ItemStack(Items.DIRT, 16));
        clearSite(h, 5, 5, 5);
        BlockPos[] obstacle = {null};
        int[] phase = {0};
        h.assertTrue(c.startWork("build_house", DESERT, 1).success(), "Order rejected");
        h.runAfterDelay(20, () -> h.assertTrue(CompanionBuildPlacement.confirm(player, DESERT, 0, corner) == null, "Confirmation refused"));
        h.onEachTick(() -> {
            if (h.getTick() < 40) return;
            if (obstacle[0] == null) {
                // The walls are begun when a block of the layer above the floor stands.
                boolean begun = false;
                for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 2, 1), new BlockPos(5, 2, 5)))
                    if (counts(h.getBlockState(rel)) && !h.getLevel().getBlockState(corner.offset(h.absolutePos(rel).subtract(reference))).isAir()) begun = true;
                if (!begun) return;
                for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 2, 1), new BlockPos(5, 2, 5))) {
                    BlockState want = h.getBlockState(rel);
                    BlockPos at = corner.offset(h.absolutePos(rel).subtract(reference));
                    if (counts(want) && !secondHalf(want) && !(want.getBlock() instanceof DoorBlock) && !(want.getBlock() instanceof BedBlock) && h.getLevel().getBlockState(at).isAir()) { obstacle[0] = at.immutable(); break; }
                }
                if (obstacle[0] != null) h.getLevel().setBlock(obstacle[0], Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                return;
            }
            if (phase[0] == 0 && !c.isWorking() && !c.isGatheringTask()) {
                phase[0] = 1;
                String said = c.getLastTaskResult();
                h.assertTrue(!c.didLastTaskSucceed(), "Said done with a block missing: " + said);
                h.assertTrue(said.startsWith("Не могу подойти") && said.contains("осталось 1 из") && said.contains(obstacle[0].toShortString()) && said.contains("мешает: " + Blocks.DIAMOND_BLOCK.getName().getString())
                        && said.contains("Стройку сохранил"), "The block and why are not named: " + said);
                h.assertTrue(h.getLevel().getBlockState(obstacle[0]).is(Blocks.DIAMOND_BLOCK), "The diamond block was dug out");
                assertBuilt(h, DESERT_TEMPLATE, corner, obstacle[0].subtract(corner));
                h.getLevel().setBlock(obstacle[0], Blocks.AIR.defaultBlockState(), 3);
                h.assertTrue(c.startWork("build_house", "resume", 1).success(), "Resume rejected");
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(phase[0] == 1 && !c.isWorking() && !c.isGatheringTask(), "Not finished: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed() && c.getLastTaskResult().contains(DESERT), c.getLastTaskResult());
            assertBuilt(h, DESERT_TEMPLATE, corner);
        });
    }

    /** The geometry of the live case: «таёжный дом» unrotated from a full bag in a flat arena is finished in one order. */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void theTaigaHouseIsFinishedInOneOrderFromAFullBag(GameTestHelper h) {
        CompanionEntity c = groundedSetup(h);
        ServerPlayer player = CompanionBuildReviewTests.playerOf(h);
        CompanionBuildPlacement.clientReady(player);
        fill(c, reference(h, TAIGA_TEMPLATE));
        c.setItem(c.getContainerSize() - 5, new ItemStack(Items.DIRT, 16));
        clearSite(h, 7, 8, 6);
        BlockPos corner = h.absolutePos(CORNER);
        h.assertTrue(c.startWork("build_house", TAIGA, 1).success(), "Order rejected");
        h.runAfterDelay(20, () -> h.assertTrue(CompanionBuildPlacement.confirm(player, TAIGA, 0, corner) == null, "Confirmation refused"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 40 && !c.isWorking() && !c.isGatheringTask(), "Not finished: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            assertBuilt(h, TAIGA_TEMPLATE, corner);
        });
    }

    /**
     * The hut with the hip roof of stairs (walls three high, no inner room worth the name): built from a full bag. With the sight of a block
     * taken from every face turned to him he saw the wall's cells from the top of the wall he had just set down into his own cell, and the wall
     * grew under his feet up to the roof, where he could not get down. A block is never set into the cell he stands in.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void aHutWithAHipRoofIsFinishedFromAFullBagWithoutBuildingItselfUp(GameTestHelper h) {
        String name = "саванный дом 2";
        ResourceLocation template = ResourceLocation.withDefaultNamespace("village/savanna/houses/savanna_small_house_8");
        CompanionEntity c = groundedSetup(h);
        ServerPlayer player = CompanionBuildReviewTests.playerOf(h);
        CompanionBuildPlacement.clientReady(player);
        fill(c, reference(h, template));
        c.setItem(c.getContainerSize() - 5, new ItemStack(Items.DIRT, 16));
        clearSite(h, 6, 7, 6);
        BlockPos corner = h.absolutePos(CORNER);
        h.assertTrue(c.startWork("build_house", name, 1).success(), "Order rejected");
        h.runAfterDelay(20, () -> h.assertTrue(CompanionBuildPlacement.confirm(player, name, 0, corner) == null, "Confirmation refused"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 40 && !c.isWorking() && !c.isGatheringTask(), "Not finished: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            assertBuilt(h, template, corner);
        });
    }

    // ---- the sight: what a player standing there could click

    private static boolean sees(GameTestHelper h, CompanionEntity c, BlockPos origin, double dx, double dy, double dz, BlockPos target) {
        return CompanionSight.visible(h.getLevel(), c, Vec3.atLowerCornerOf(origin).add(dx, dy, dz), target);
    }

    /**
     * A cell in a slot (a block on each of its four sides and one below it, open only above, as the top of an inner wall between two beams
     * is): seen from above at a slant, from straight above and from the height of a column's top; never from behind one of the blocks round
     * it or from under it, and not from farther than a player's reach.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void aCellInASlotIsSeenFromAboveAndNotThroughTheBlocksRoundIt(GameTestHelper h) {
        CompanionEntity c = groundedSetup(h);
        BlockPos o = h.absolutePos(new BlockPos(20, GROUND, 20));
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-5, 1, -5), o.offset(5, 9, 5))) h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        BlockPos slot = o.offset(0, 4, 0);
        for (BlockPos side : new BlockPos[]{slot.west(), slot.east(), slot.north(), slot.south(), slot.below()}) h.getLevel().setBlock(side, Blocks.STONE.defaultBlockState(), 3);
        // The air above it: a slant from the east, one from the north, one from the south-west, straight above, and a player's height on a column of three beside it.
        h.assertTrue(sees(h, c, o, 3.5, 6.2, .5, slot), "Not seen from above at a slant from the east");
        h.assertTrue(sees(h, c, o, .5, 6.2, -2.5, slot), "Not seen from above at a slant from the north");
        h.assertTrue(sees(h, c, o, -2.5, 6.2, 2.5, slot), "Not seen from above at a slant from the south-west");
        h.assertTrue(sees(h, c, o, .5, 7.0, .5, slot), "Not seen from straight above");
        h.assertTrue(sees(h, c, o, 2.5, 6.53, .5, slot), "Not seen from the top of a column beside it");
        // Behind the blocks round it, at the height of the cell, under it, and too far.
        h.assertTrue(!sees(h, c, o, 3.5, 4.5, .5, slot), "Seen through the block east of it");
        h.assertTrue(!sees(h, c, o, -3.5, 4.5, .5, slot), "Seen through the block west of it");
        h.assertTrue(!sees(h, c, o, .5, 4.5, -3.5, slot), "Seen through the block north of it");
        h.assertTrue(!sees(h, c, o, .5, 4.5, 3.5, slot), "Seen through the block south of it");
        h.assertTrue(!sees(h, c, o, .5, 1.5, .5, slot) && !sees(h, c, o, 2.5, 2.5, .5, slot), "Seen from below, through the block under it");
        h.assertTrue(!sees(h, c, o, .5, 9.6, .5, slot), "Seen from farther than a player reaches");
        // A wall of one block between the eye and a cell in the open: not seen through it, seen once the wall is lower than the eye's line.
        BlockPos open = o.offset(0, 3, 4);
        for (int x = -2; x <= 2; x++) for (int y = 1; y <= 4; y++) h.getLevel().setBlock(o.offset(x, y, 2), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(!sees(h, c, o, .5, 2.5, .5, open) && !sees(h, c, o, -1.5, 3.5, .5, open), "Seen through a wall");
        h.assertTrue(sees(h, c, o, .5, 2.5, 6.5, open), "Not seen from the open side of the wall");
        h.succeed();
    }

    /**
     * The live case of 12.11: the roof of the porch of «таёжный дом» has a cell (3, 4, 5) between a beam on each side, the roof under it and a
     * ridge log in front and behind; only the top is open, and the log above it is not set yet. The ground sees nothing of it; from a column of
     * three blocks by the porch the top is in plain view.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void theRoofCellOfThePorchIsSeenFromAColumnAndNotFromTheGround(GameTestHelper h) {
        CompanionEntity c = groundedSetup(h);
        clearSite(h, 7, 8, 6);
        var template = h.getLevel().getServer().getStructureManager().get(TAIGA_TEMPLATE).orElseThrow();
        BlockPos corner = h.absolutePos(CORNER);
        template.placeInWorld(h.getLevel(), corner, corner, new StructurePlaceSettings().setIgnoreEntities(true), RandomSource.create(1), 2);
        BlockPos cell = corner.offset(3, 4, 5);
        h.assertTrue(h.getLevel().getBlockState(cell).is(Blocks.SPRUCE_PLANKS) && h.getLevel().getBlockState(cell.north()).is(Blocks.SPRUCE_LOG), "The premise: the cell and the log beside it, " + h.getLevel().getBlockState(cell));
        h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 3);
        // The ground: the doorway of the inner door, the porch, in front of the outer door, the room.
        for (int[] at : new int[][]{{3, 4}, {3, 5}, {3, 7}, {3, 3}, {2, 3}, {3, 2}, {5, 7}, {1, 7}})
            h.assertTrue(!sees(h, c, corner, at[0] + .5, 1 + c.getEyeHeight(), at[1] + .5, cell), "Seen from the ground at " + at[0] + ", " + at[1]);
        // A column of three (feet at 4, eye at 5.5 and more) at the ends of the porch's roof, on both sides of the ridge.
        h.assertTrue(sees(h, c, corner, 5.5, 4 + c.getEyeHeight(), 5.5, cell), "Not seen from a column of three east of the porch");
        h.assertTrue(sees(h, c, corner, 1.5, 4 + c.getEyeHeight(), 5.5, cell), "Not seen from a column of three west of the porch");
        h.succeed();
    }
}

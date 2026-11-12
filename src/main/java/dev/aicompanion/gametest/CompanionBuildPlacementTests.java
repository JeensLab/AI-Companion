package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBuildPlacement;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The player sets a village building down before the companion builds it: nothing is placed or gathered until the placement is
 * confirmed, the server trusts nothing of what comes with the confirmation, and the building stands at the confirmed corner turned
 * the way the game's own placement of the template turns it. The ghost and the keys are client code and have no test here.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBuildPlacementTests {
    private static final String NAME = "пустынный дом", OTHER_NAME = "бревенчатый дом";
    private static final ResourceLocation TEMPLATE = ResourceLocation.withDefaultNamespace("village/desert/houses/desert_small_house_4");
    private static final int GROUND = 1;
    /** Where the building is set down in the arena (relative), clear of the reference at (1, 1, 1), the companion and the player. */
    private static final BlockPos CORNER = new BlockPos(18, GROUND, 14);

    private record Setup(CompanionEntity companion, ServerPlayer player) { }

    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aConfirmedPlacementBuildsTheTemplateRotatedExactlyThere(GameTestHelper helper) {
        Setup s = setup(helper);
        Rotation turn = Rotation.CLOCKWISE_90;
        Map<Item, Integer> bag = placeReference(helper, turn);
        fill(s.companion, bag);
        clearSite(helper);
        BlockPos corner = helper.absolutePos(CORNER);
        List<BlockState> before = snapshot(helper);
        String bagBefore = s.companion.getStoredItemsSummary();
        helper.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        boolean[] confirmed = {false};
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The order does not wait for a placement");
            helper.assertTrue(snapshot(helper).equals(before) && bagBefore.equals(s.companion.getStoredItemsSummary()), "Something was placed or taken before the confirmation");
            helper.assertTrue(!saved(s.companion).getCompound("TemplateBuild").contains("Base"), "A site was fixed before the confirmation");
            String refused = CompanionBuildPlacement.confirm(s.player, NAME, turn.ordinal(), corner);
            helper.assertTrue(refused == null, "Confirmation refused: " + refused);
            confirmed[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(confirmed[0], "Not confirmed yet");
            helper.assertTrue(!s.companion.isGatheringTask(), s.companion.getLastTaskResult());
            helper.assertTrue(s.companion.didLastTaskSucceed(), s.companion.getLastTaskResult());
            helper.assertTrue(BlockPos.of(saved(s.companion).getCompound("TemplateBuild").getLong("Base")).equals(corner), "Built elsewhere than the confirmed corner");
            assertLooksLikeReference(helper, corner);
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void cancellingThePlacementLeavesWorldAndBagUntouched(GameTestHelper helper) {
        Setup s = setup(helper);
        clearSite(helper);
        List<BlockState> before = snapshot(helper);
        String bagBefore = s.companion.getStoredItemsSummary();
        helper.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        // With an empty bag he would set out to gather at once: instead the player is told the building, its size and what it takes.
        var size = CompanionBuildPlacement.preview(helper.getLevel(), NAME, 0);
        helper.runAfterDelay(10, () -> {
            String told = s.companion.getLastTaskResult();
            helper.assertTrue(told.contains(NAME) && told.contains(size.sizeX() + "×" + size.sizeZ() + "×" + size.sizeY()) && told.contains("Сам добуду") && told.contains("подтверд"), "The order was not explained: " + told);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The order does not wait for a placement");
            CompanionBuildPlacement.cancel(s.player);
            helper.assertTrue(CompanionBuildPlacement.pendingName(s.player) == null && !s.companion.isGatheringTask(), "The order outlived the cancel: " + s.companion.getLastTaskResult());
            helper.assertTrue(CompanionBuildPlacement.confirm(s.player, NAME, 0, helper.absolutePos(CORNER)) != null, "A confirmation of a dropped order was accepted");
            helper.runAfterDelay(80, () -> {
                helper.assertTrue(snapshot(helper).equals(before), "The world changed");
                helper.assertTrue(bagBefore.equals(s.companion.getStoredItemsSummary()), "The bag changed: " + s.companion.getStoredItemsSummary());
                helper.assertTrue(!saved(s.companion).getCompound("TemplateBuild").contains("Base"), "A site was fixed");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void whatTheServerCannotTrustInAConfirmationIsRefusedAndNothingIsBuilt(GameTestHelper helper) {
        Setup s = setup(helper);
        fill(s.companion, placeReference(helper, Rotation.NONE));
        clearSite(helper);
        BlockPos corner = helper.absolutePos(CORNER);
        String bagBefore = s.companion.getStoredItemsSummary();
        helper.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The order does not wait for a placement");
            ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(CompanionBuildPlacement.confirm(stranger, NAME, 0, corner) != null, "A player who owns no companion placed the building");
            for (String name : new String[]{OTHER_NAME, "замок дракона", "", null})
                helper.assertTrue(CompanionBuildPlacement.confirm(s.player, name, 0, corner) != null, "Building «" + name + "» was accepted for a pending «" + NAME + "»");
            for (int turn : new int[]{4, -1, 1000})
                helper.assertTrue(CompanionBuildPlacement.confirm(s.player, NAME, turn, corner) != null, "Rotation " + turn + " was accepted");
            BlockPos beside = s.player.blockPosition();
            for (BlockPos far : new BlockPos[]{beside.offset(CompanionBuildPlacement.MAX_DISTANCE + 1, 0, 0), beside.offset(0, 0, -CompanionBuildPlacement.MAX_DISTANCE - 1), beside.offset(300, 0, 300)})
                helper.assertTrue(CompanionBuildPlacement.confirm(s.player, NAME, 0, far) != null, "A position " + far.toShortString() + " out of reach was accepted");
            // A chest of the player in the footprint: not his to break, so the site is refused with the reason, and the ghost stays.
            BlockPos chest = corner.offset(1, 1, 1);
            helper.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            List<BlockState> withChest = snapshot(helper);
            String why = CompanionBuildPlacement.confirm(s.player, NAME, 0, corner);
            helper.assertTrue(why != null && why.contains("Мешает") && why.contains(chest.toShortString()), "The chest was not named: " + why);
            helper.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The refusal ended the order");
            helper.assertTrue(snapshot(helper).equals(withChest) && bagBefore.equals(s.companion.getStoredItemsSummary()), "A refused confirmation changed the world or the bag");
            helper.assertTrue(!saved(s.companion).getCompound("TemplateBuild").contains("Base"), "A refused confirmation fixed a site");
            // Moved to a free place (turned), it is accepted, and the turn is saved with the build.
            helper.getLevel().setBlock(chest, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(CompanionBuildPlacement.confirm(s.player, NAME, 2, corner) == null, "The place that was free was refused");
            CompoundTag build = saved(s.companion).getCompound("TemplateBuild");
            helper.assertTrue(BlockPos.of(build.getLong("Base")).equals(corner) && build.getInt("Rotation") == 2, "Corner or turn not saved");
            helper.assertTrue(CompanionBuildPlacement.pendingName(s.player) == null, "Still waiting after the confirmation");
            helper.succeed();
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void resumeNeverAsksForAPlacementAgain(GameTestHelper helper) {
        Setup s = setup(helper);
        clearSite(helper);
        BlockPos corner = helper.absolutePos(CORNER);
        helper.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(CompanionBuildPlacement.confirm(s.player, NAME, 1, corner) == null, "Confirmation refused");
            s.companion.stopGathering();
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(s.companion.startWork("build_house", "resume", 1).success(), "Resume rejected");
                helper.assertTrue(CompanionBuildPlacement.pendingName(s.player) == null, "Resume asked for a placement");
                CompoundTag build = saved(s.companion).getCompound("TemplateBuild");
                helper.assertTrue(BlockPos.of(build.getLong("Base")).equals(corner) && build.getInt("Rotation") == 1, "Resume left the confirmed place");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 3200)
    public static void aPlacementNobodyConfirmsEndsAfterTheTimeout(GameTestHelper helper) {
        Setup s = setup(helper);
        clearSite(helper);
        helper.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        helper.runAfterDelay(CompanionBuildPlacement.TIMEOUT_TICKS - 100, () ->
                helper.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The placement ended before the timeout"));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > CompanionBuildPlacement.TIMEOUT_TICKS, "Not yet");
            helper.assertTrue(CompanionBuildPlacement.pendingName(s.player) == null && !s.companion.isGatheringTask(), "Still waiting: " + s.companion.getLastTaskResult());
            helper.assertTrue(!s.companion.didLastTaskSucceed() && s.companion.getLastTaskResult().contains("не выбрано"), s.companion.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree")
    public static void rotatedBlocksAreThoseOfTheGamesOwnPlacementForAllFourTurns(GameTestHelper helper) {
        for (String name : new String[]{NAME, "деревенский дом"}) {
            var template = helper.getLevel().getServer().getStructureManager().get(ResourceLocation.withDefaultNamespace(
                    name.equals(NAME) ? "village/desert/houses/desert_small_house_4" : "village/plains/houses/plains_small_house_1")).orElseThrow();
            for (Rotation turn : Rotation.values()) {
                for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(12, 12, 12))) helper.setBlock(rel, Blocks.AIR);
                var settings = new StructurePlaceSettings().setIgnoreEntities(true).setRotation(turn);
                BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
                var box = template.getBoundingBox(settings, at);
                // The game turns about the origin; the corner of the footprint is set to where the unturned one stands.
                BlockPos origin = new BlockPos(2 * at.getX() - box.minX(), at.getY(), 2 * at.getZ() - box.minZ());
                template.placeInWorld(helper.getLevel(), origin, origin, settings, RandomSource.create(1), 2);
                var preview = CompanionBuildPlacement.preview(helper.getLevel(), name, turn.ordinal());
                helper.assertTrue(preview != null, "No preview of «" + name + "»");
                helper.assertTrue(preview.sizeX() == box.getXSpan() && preview.sizeZ() == box.getZSpan() && preview.sizeY() == box.getYSpan(), "Size of «" + name + "» turned " + turn + ": " + preview.sizeX() + "×" + preview.sizeZ());
                Map<BlockPos, BlockState> wanted = new LinkedHashMap<>();
                for (BlockPos pos : BlockPos.betweenClosed(at, at.offset(box.getXSpan() - 1, box.getYSpan() - 1, box.getZSpan() - 1))) {
                    BlockState state = helper.getLevel().getBlockState(pos);
                    if (!state.isAir() && !state.is(Blocks.JIGSAW) && !state.is(Blocks.STRUCTURE_VOID)) wanted.put(pos.subtract(at), state);
                }
                helper.assertTrue(wanted.size() == preview.blocks().size(), "«" + name + "» turned " + turn + ": " + preview.blocks().size() + " blocks, the game places " + wanted.size());
                for (var block : preview.blocks()) {
                    BlockState want = wanted.get(block.rel());
                    helper.assertTrue(want != null && want.is(block.state().getBlock()), "«" + name + "» turned " + turn + ": nothing like " + block.state().getBlock() + " at " + block.rel() + " in the game's placement");
                    for (var property : want.getProperties())
                        if (Set.of("facing", "axis", "half", "part", "hinge").contains(property.getName()))
                            helper.assertTrue(block.state().getValue(property).equals(want.getValue(property)), "«" + name + "» turned " + turn + ": " + property.getName() + " of " + want.getBlock() + " at " + block.rel());
                }
            }
        }
        helper.succeed();
    }

    /** Leaves a player set down and a wall of logs with planks on it are not his to break (a tree and its crown are): the site is refused, with the cell named. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aPlayersHedgeAndLogWallAreNotBrokenButATreeIs(GameTestHelper helper) {
        Setup s = setup(helper);
        clearSite(helper);
        BlockPos corner = helper.absolutePos(CORNER);
        helper.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        helper.runAfterDelay(20, () -> {
            var level = helper.getLevel();
            BlockPos hedge = corner.offset(1, 2, 1);
            level.setBlock(hedge, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 3);
            String why = CompanionBuildPlacement.confirm(s.player, NAME, 0, corner);
            helper.assertTrue(why != null && why.contains("Мешает") && why.contains(hedge.toShortString()), "A hedge a player set down was not named: " + why);
            helper.assertTrue(level.getBlockState(hedge).is(Blocks.OAK_LEAVES), "The hedge was touched");
            level.setBlock(hedge, Blocks.AIR.defaultBlockState(), 3);
            for (int x = 1; x <= 3; x++) level.setBlock(corner.offset(x, 2, 1), Blocks.OAK_LOG.defaultBlockState(), 3);
            level.setBlock(corner.offset(2, 3, 1), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            why = CompanionBuildPlacement.confirm(s.player, NAME, 0, corner);
            helper.assertTrue(why != null && why.contains("Мешает") && why.contains(corner.offset(1, 2, 1).toShortString()), "A log wall was not named: " + why);
            // The same logs with a living crown and no planks are a tree.
            level.setBlock(corner.offset(2, 3, 1), Blocks.AIR.defaultBlockState(), 3);
            for (int x = 1; x <= 3; x++) level.setBlock(corner.offset(x, 3, 1), Blocks.OAK_LEAVES.defaultBlockState(), 3);
            helper.assertTrue(CompanionBuildPlacement.confirm(s.player, NAME, 0, corner) == null, "A tree with its crown was refused");
            helper.succeed();
        });
    }

    /** A decoration in the footprint refuses the site (nothing is dropped or pushed); a mob, a pet beside the player or the player himself does not: the block waits for it. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aDecorationInTheFootprintRefusesTheSiteButAnAnimalOrAPlayerDoesNot(GameTestHelper helper) {
        Setup s = setup(helper);
        clearSite(helper);
        BlockPos corner = helper.absolutePos(CORNER);
        helper.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        helper.runAfterDelay(20, () -> {
            var stand = helper.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, CORNER.offset(2, 1, 2));
            String why = CompanionBuildPlacement.confirm(s.player, NAME, 0, corner);
            helper.assertTrue(why != null && why.contains(stand.blockPosition().toShortString()), "An armor stand in the footprint did not refuse the site: " + why);
            stand.discard();
            // Animals in the box and a wolf sitting in the margin beside it: the build-time rule about occupied cells covers them.
            helper.spawn(net.minecraft.world.entity.EntityType.PIG, CORNER.offset(2, 1, 2));
            var wolf = helper.spawn(net.minecraft.world.entity.EntityType.WOLF, CORNER.offset(-1, 1, 2));
            wolf.setInSittingPose(true);
            s.player.teleportTo(corner.getX() + 2.5, corner.getY() + 1, corner.getZ() + 2.5);
            why = CompanionBuildPlacement.confirm(s.player, NAME, 0, corner);
            helper.assertTrue(why == null, "An animal or the player refused the site: " + why);
            helper.succeed();
        });
    }

    /**
     * The player hovers in a cell of the building (his body covers two cells): no block is put into it while he is there, the building
     * goes on with every other block, and only when nothing else is left it waits and says so (the work does not stop for it). Once he
     * steps aside the block is set down and the building is finished.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aBlockIsNotPutIntoAPlayerItWaitsUntilHeStepsAside(GameTestHelper helper) {
        Setup s = setup(helper);
        Map<Item, Integer> bag = placeReference(helper, Rotation.NONE);
        fill(s.companion, bag);
        clearSite(helper);
        BlockPos corner = helper.absolutePos(CORNER);
        // The highest cell of the building that is no door, no bed and no wall torch: the last one of its blocks, the door excepted.
        BlockPos top = null;
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(8, 8, 8))) {
            BlockState state = helper.getBlockState(rel);
            if (state.isAir() || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof BedBlock || state.is(Blocks.WALL_TORCH) || state.is(Blocks.JIGSAW)) continue;
            if (top == null || rel.getY() > top.getY()) top = rel.immutable();
        }
        BlockPos cell = corner.offset(helper.absolutePos(top).subtract(helper.absolutePos(new BlockPos(1, 1, 1))));
        int[] stage = {0};
        long[] since = {0};
        helper.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(CompanionBuildPlacement.confirm(s.player, NAME, 0, corner) == null, "Confirmation refused");
            s.player.setNoGravity(true);
            s.player.teleportTo(cell.getX() + .5, cell.getY(), cell.getZ() + .5);
        });
        helper.onEachTick(() -> {
            CompoundTag build = saved(s.companion).getCompound("TemplateBuild");
            int placed = java.util.BitSet.valueOf(build.getLongArray("Placed")).cardinality();
            if (helper.getTick() > 25 && stage[0] < 2)
                helper.assertTrue(helper.getLevel().getBlockState(cell).isAir() && helper.getLevel().getBlockState(cell.above()).isAir(), "A block was put into the player at " + cell.toShortString());
            // He only waits when nothing else is left: the cells of the player's body and the door.
            if (stage[0] == 0 && s.companion.getLastTaskResult().startsWith("Жду, пока освободится")) {
                helper.assertTrue(placed >= build.getInt("Total") - 3, "He waits with " + (build.getInt("Total") - placed) + " blocks left: " + s.companion.getLastTaskResult());
                stage[0] = 1; since[0] = helper.getTick();
            }
            if (stage[0] == 1) {
                if (helper.getTick() - since[0] >= 2400) {
                    helper.assertTrue(s.companion.hasActiveWorkOrder(), "The work ended instead of waiting: " + s.companion.getLastTaskResult());
                    s.player.setNoGravity(false);
                    s.player.teleportTo(corner.getX() - 6, corner.getY() + 1, corner.getZ() - 6);
                    stage[0] = 2;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] == 2, "The player has not stepped aside yet");
            helper.assertTrue(!s.companion.isGatheringTask(), s.companion.getLastTaskResult());
            helper.assertTrue(s.companion.didLastTaskSucceed(), s.companion.getLastTaskResult());
            assertLooksLikeReference(helper, corner);
        });
    }

    // ---- helpers

    private static Setup setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            if (helper.getBlockState(new BlockPos(x, GROUND, z)).isAir()) helper.setBlock(new BlockPos(x, GROUND, z), Blocks.RED_SAND);
        }
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, GROUND + 1, 16);
        companion.setItem(companion.getContainerSize() - 2, new ItemStack(Items.IRON_SHOVEL));
        companion.setItem(companion.getContainerSize() - 5, new ItemStack(Items.DIRT, 8));
        var player = helper.makeMockServerPlayerInLevel(); player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player); companion.setSearchRadius(16);
        // The client's own signal (CompanionPreferences.sync) that there is a ghost to place the building with.
        CompanionBuildPlacement.clientReady(player);
        return new Setup(companion, player);
    }

    /** Bare ground where the building is set down: the tree of the arena is not in its way. */
    private static void clearSite(GameTestHelper helper) {
        for (BlockPos rel : BlockPos.betweenClosed(CORNER.offset(-2, 1, -2), CORNER.offset(10, 12, 10))) helper.setBlock(rel, Blocks.AIR);
    }

    /** The blocks of the site and its margin: what the building and the clearing would touch. */
    private static List<BlockState> snapshot(GameTestHelper helper) {
        List<BlockState> all = new ArrayList<>();
        for (BlockPos rel : BlockPos.betweenClosed(CORNER.offset(-2, -1, -2), CORNER.offset(10, 12, 10))) all.add(helper.getBlockState(rel));
        return all;
    }

    /** The game's own placement of the template, turned, in the arena corner; returns what the bag needs to build one more like it. */
    private static Map<Item, Integer> placeReference(GameTestHelper helper, Rotation turn) {
        var template = helper.getLevel().getServer().getStructureManager().get(TEMPLATE).orElseThrow();
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(9, 9, 9))) helper.setBlock(rel, Blocks.AIR);
        var settings = new StructurePlaceSettings().setIgnoreEntities(true).setRotation(turn);
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        var box = template.getBoundingBox(settings, at);
        BlockPos origin = new BlockPos(2 * at.getX() - box.minX(), at.getY(), 2 * at.getZ() - box.minZ());
        template.placeInWorld(helper.getLevel(), origin, origin, settings, RandomSource.create(1), 2);
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(8, 8, 8))) {
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

    /** Every block of the reference stands at the same place of the building at {@code corner}: the kind, and the properties that turn it. */
    private static void assertLooksLikeReference(GameTestHelper helper, BlockPos corner) {
        int checked = 0;
        BlockPos reference = helper.absolutePos(new BlockPos(1, 1, 1));
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(8, 8, 8))) {
            BlockState want = helper.getBlockState(rel);
            if (want.isAir() || want.is(Blocks.JIGSAW) || want.is(Blocks.STRUCTURE_VOID)) continue;
            BlockPos at = corner.offset(helper.absolutePos(rel).subtract(reference));
            BlockState got = helper.getLevel().getBlockState(at);
            helper.assertTrue(got.is(want.getBlock()), "Block " + at.subtract(corner) + " is " + got.getBlock() + ", the placement has " + want.getBlock());
            if (want.is(Blocks.CHEST)) helper.assertTrue(helper.getLevel().getBlockEntity(at) instanceof Container chest && chest.isEmpty(), "Chest is not empty");
            for (var property : want.getProperties())
                if (Set.of("facing", "axis", "half", "part", "hinge").contains(property.getName()))
                    helper.assertTrue(got.getValue(property).equals(want.getValue(property)), "Block " + at.subtract(corner) + " is turned differently: " + property.getName());
            checked++;
        }
        helper.assertTrue(checked > 60, "Reference too small to prove anything: " + checked);
    }

    private static CompoundTag saved(CompanionEntity companion) { CompoundTag tag = new CompoundTag(); companion.addAdditionalSaveData(tag); return tag; }
}

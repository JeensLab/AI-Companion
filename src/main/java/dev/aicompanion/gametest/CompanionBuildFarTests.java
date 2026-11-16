package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBuildPlacement;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
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

import static dev.aicompanion.gametest.CompanionBuildReviewTests.reloadedWithUnfinished;
import static dev.aicompanion.gametest.CompanionHouseTests.groundedSetup;

/**
 * A building the player sets down far from where the companion stands (the ghost reaches 96 blocks) is walked to before anything is
 * cleared or built (game log 12.11: «Не могу подойти к «Дёрн»» two seconds after the confirmation, 15 blocks from the site); a site he
 * cannot get to at all is said as that. Also what the second review found: stone held back for a building that is not being built,
 * the note about dirt left lying, and the trees that overhang a roof.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBuildFarTests {
    private static final String NAME = "пустынный дом", TAIGA = "таёжный дом";
    private static final ResourceLocation TEMPLATE = ResourceLocation.withDefaultNamespace("village/desert/houses/desert_small_house_4");
    private static final ResourceLocation TAIGA_TEMPLATE = ResourceLocation.withDefaultNamespace("village/taiga/houses/taiga_small_house_4");
    private static final int GROUND = 1, SIZE = 128;
    /** Far from where he starts (14, 14), and the reference building lies in the corner behind him. */
    private static final BlockPos FAR = new BlockPos(100, GROUND, 100);

    private record Setup(CompanionEntity companion, ServerPlayer player) { }

    /**
     * Bedrock and a ground of red sand over all of the big arena; a ridge of stone four high across it at z=56 with its only gap at the
     * west end (x below 44); his search radius is the smallest (16), so no search of his sees even half of the way, and a grove between the ridge and the site: the way from where he stands to the site is no straight walk, and about 130 blocks.
     */
    private static Setup farSetup(GameTestHelper h, boolean grove, boolean ridge, int radius) {
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            h.setBlock(new BlockPos(x, GROUND, z), Blocks.RED_SAND);
        }
        if (ridge) for (int x = 44; x < SIZE; x++) for (int y = GROUND + 1; y <= GROUND + 4; y++) h.setBlock(new BlockPos(x, y, 56), Blocks.STONE);
        if (grove) for (int x = 50; x < 92; x += 6) for (int z = 62; z < 92; z += 6) {
            for (int y = GROUND + 1; y <= GROUND + 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = GROUND + 5; y <= GROUND + 6; y++)
                if (h.getBlockState(new BlockPos(x + dx, y, z + dz)).isAir()) h.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.OAK_LEAVES);
        }
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 14, GROUND + 1, 14);
        companion.setItem(companion.getContainerSize() - 2, new ItemStack(Items.IRON_SHOVEL));
        companion.setItem(companion.getContainerSize() - 3, new ItemStack(Items.IRON_AXE));
        companion.setItem(companion.getContainerSize() - 5, new ItemStack(Items.DIRT, 8));
        var player = h.makeMockServerPlayerInLevel();
        BlockPos near = h.absolutePos(new BlockPos(FAR.getX() - 6, GROUND + 1, FAR.getZ() - 6));
        player.teleportTo(near.getX() + .5, near.getY(), near.getZ() + .5);
        companion.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player); companion.setSearchRadius(radius);
        companion.setFollowing(false);
        CompanionBuildPlacement.clientReady(player);
        return new Setup(companion, player);
    }

    /** Two trees on the site, to be cleared once he is there. */
    private static void treesOnSite(GameTestHelper h) {
        for (int[] at : new int[][]{{1, 1}, {3, 3}}) {
            for (int y = 1; y <= 4; y++) h.setBlock(FAR.offset(at[0], y, at[1]), Blocks.OAK_LOG);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (h.getBlockState(FAR.offset(at[0] + dx, 4, at[1] + dz)).isAir()) h.setBlock(FAR.offset(at[0] + dx, 4, at[1] + dz), Blocks.OAK_LEAVES);
        }
    }

    /** His smallest search radius (16): no search of his sees even half the way, so the work's own routes find no way to the site (as the game log). */
    @GameTest(template = "large_search", timeoutTicks = 9000)
    public static void aBuildingSetDownFarAwayThroughAGroveIsWalkedToAndBuilt(GameTestHelper h) {
        farAndBuilt(h, farSetup(h, true, false, 16));
    }

    /** His usual radius and a ridge of stone across the way, with its gap at one end: round it, through the grove, to the site. */
    @GameTest(template = "large_search", timeoutTicks = 9000)
    public static void aBuildingSetDownFarAwayOverTheRidgeIsWalkedToAndBuilt(GameTestHelper h) {
        farAndBuilt(h, farSetup(h, true, true, 64));
    }

    private static void farAndBuilt(GameTestHelper h, Setup s) {
        Map<Item, Integer> bag = placeReference(h, TEMPLATE, 1);
        fill(s.companion, bag);
        treesOnSite(h);
        BlockPos corner = h.absolutePos(FAR);
        double[] start = {0};
        BlockPos[] last = {null};
        boolean[] confirmed = {false};
        h.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        h.runAfterDelay(30, () -> {
            start[0] = Math.sqrt(s.companion.blockPosition().distSqr(corner));
            String refused = CompanionBuildPlacement.confirm(s.player, NAME, 0, corner);
            h.assertTrue(refused == null, "Confirmation refused: " + refused);
            confirmed[0] = true;
        });
        h.onEachTick(() -> {
            // He walks: no step of the way is a jump of the position.
            BlockPos now = s.companion.blockPosition();
            if (confirmed[0] && last[0] != null) h.assertTrue(now.distSqr(last[0]) < 6 * 6, "He was moved from " + last[0].toShortString() + " to " + now.toShortString());
            last[0] = now;
        });
        h.succeedWhen(() -> {
            h.assertTrue(confirmed[0], "Not confirmed yet");
            h.assertTrue(start[0] > 100, "The premise: he starts far from the site, not " + start[0]);
            h.assertTrue(!s.companion.isWorking() && !s.companion.isGatheringTask(), s.companion.getLastTaskResult());
            h.assertTrue(s.companion.didLastTaskSucceed(), s.companion.getLastTaskResult());
            assertLooksLikeReference(h, corner, 1);
        });
    }

    /** The site is walled in four blocks high a few blocks round it: no way there at all. Said as that, nothing touched; «дострой» after the wall is opened builds it. */
    @GameTest(template = "large_search", timeoutTicks = 12000)
    public static void aSiteWalledInIsSaidAsOneHeCannotGetToAndResumesOnceOpened(GameTestHelper h) {
        Setup s = farSetup(h, false, false, 16);
        Map<Item, Integer> bag = placeReference(h, TEMPLATE, 1);
        fill(s.companion, bag);
        treesOnSite(h);
        BlockPos corner = h.absolutePos(FAR);
        for (int x = -3; x <= 7; x++) for (int z = -3; z <= 7; z++) {
            if (x > -3 && x < 7 && z > -3 && z < 7) continue;
            for (int y = GROUND + 1; y <= GROUND + 4; y++) h.setBlock(FAR.offset(x, y - GROUND, z), Blocks.STONE);
        }
        List<BlockState> before = new ArrayList<>();
        String[] bagBefore = {null};
        boolean[] ended = {false};
        h.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        h.runAfterDelay(30, () -> {
            // The leaves of the trees on the site settle (their distance to a log) in the first ticks: the snapshot is taken at the confirmation.
            before.addAll(region(h)); bagBefore[0] = s.companion.getStoredItemsSummary();
            String refused = CompanionBuildPlacement.confirm(s.player, NAME, 0, corner);
            h.assertTrue(refused == null, "Confirmation refused: " + refused);
        });
        h.onEachTick(() -> {
            if (ended[0] || h.getTick() < 40 || s.companion.isWorking()) return;
            ended[0] = true;
            String said = s.companion.getLastTaskResult();
            h.assertTrue(!s.companion.didLastTaskSucceed() && said.contains("дойти до места") && said.contains(corner.toShortString()), "Not said as a site he cannot get to: " + said);
            h.assertTrue(!said.contains("подойти к «"), "Said as a block out of reach: " + said);
            h.assertTrue(region(h).equals(before) && bagBefore[0].equals(s.companion.getStoredItemsSummary()), "The world or the bag changed");
            h.assertTrue(CompanionHouseTests.saved(s.companion).getCompound("TemplateBuild").contains("Base"), "The building was not saved for «дострой»");
            for (int x = 1; x <= 3; x++) for (int y = 1; y <= 4; y++) h.setBlock(FAR.offset(-3, y, x + 1), Blocks.AIR);
            h.assertTrue(s.companion.startWork("build_house", "resume", 1).success(), "Resume rejected");
        });
        h.succeedWhen(() -> {
            h.assertTrue(ended[0] && !s.companion.isWorking() && !s.companion.isGatheringTask(), "Not finished: " + s.companion.getLastTaskResult());
            h.assertTrue(s.companion.didLastTaskSucceed(), s.companion.getLastTaskResult());
            assertLooksLikeReference(h, corner, 1);
        });
    }

    // ---- stone held back for a building nobody is building

    /** An unfinished smithy keeps its stone only while its own order runs: «добудь камня» with a wooden pickaxe makes the stone one, as without the smithy. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void anUnfinishedBuildingDoesNotHoldItsStoneBackFromAStoneOrder(GameTestHelper h) {
        CompanionEntity oleg = groundedSetup(h);
        BlockPos bench = new BlockPos(14, GROUND + 1, 16);
        h.setBlock(bench, Blocks.CRAFTING_TABLE);
        for (int x = 20; x <= 22; x++) for (int z = 14; z <= 18; z++) for (int y = GROUND + 1; y <= GROUND + 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        CompanionEntity c = reloadedWithUnfinished(h, oleg, h.absolutePos(new BlockPos(4, GROUND, 4)), "кузница равнин");
        c.rememberPlaced(h.absolutePos(bench));
        c.setFollowing(false);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 10)); c.setItem(1, new ItemStack(Items.STICK, 2));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        h.runAtTickTime(10, () -> { h.assertTrue(c.onGround(), "Not landed"); h.assertTrue(c.startWork("gather_stone", "", 3).success(), "Order rejected"); });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 10 && !c.isWorking() && !c.isGatheringTask(), "Still working: " + c.getLastTaskResult());
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(c.getMainHandItem().is(Items.STONE_PICKAXE), "The stone pickaxe was not made: hand " + c.getMainHandItem() + ", " + c.getLastTaskResult());
        });
    }

    // ---- the note about dirt left lying

    /** Said at whatever end of the order (here: «стоп»), once, and not carried into another order. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void theNoteAboutDirtLeftLyingIsSaidAtTheEndOfTheOrderAndNotCarriedOn(GameTestHelper h) throws ReflectiveOperationException {
        CompanionEntity oleg = groundedSetup(h);
        CompanionEntity c = reloadedWithUnfinished(h, oleg, h.absolutePos(new BlockPos(6, GROUND, 6)), NAME);
        c.setFollowing(false);
        h.assertTrue(c.startWork("build_house", "resume", 1).success(), "Resume rejected");
        long first = c.activeWorkId();
        setLost(c, "5 блоков земли с лесов лежат на земле у 1, 2, 3.");
        c.stopGathering();
        var said = c.workResult(first);
        h.assertTrue(said != null && said.message().contains("лежат на земле у 1, 2, 3"), "Not said at the end of the order: " + (said == null ? null : said.message()));
        h.assertTrue(getLost(c).isEmpty(), "Kept after it was said: " + getLost(c));
        setLost(c, "ещё одно");
        h.assertTrue(c.startWork("build_house", "resume", 1).success(), "Second resume rejected");
        h.assertTrue(getLost(c).isEmpty(), "Carried into another order: " + getLost(c));
        long second = c.activeWorkId();
        c.stopGathering();
        h.assertTrue(!c.workResult(second).message().contains("ещё одно"), "Said at the end of another order: " + c.workResult(second).message());
        h.succeed();
    }
    private static Object house(CompanionEntity c) throws ReflectiveOperationException {
        var work = CompanionEntity.class.getDeclaredField("work"); work.setAccessible(true);
        var built = work.get(c).getClass().getDeclaredField("templateHouse"); built.setAccessible(true);
        return built.get(work.get(c));
    }
    private static void setLost(CompanionEntity c, String text) throws ReflectiveOperationException {
        Object house = house(c);
        var lost = house.getClass().getDeclaredField("lost"); lost.setAccessible(true); lost.set(house, text);
    }
    private static String getLost(CompanionEntity c) throws ReflectiveOperationException {
        Object house = house(c);
        var lost = house.getClass().getDeclaredField("lost"); lost.setAccessible(true); return (String) lost.get(house);
    }

    // ---- a roof under a tree

    /**
     * The taiga house under a canopy: leaves from three layers under the roof to two above it and four blocks out on every side, with a log
     * of the tree now and then (one and two blocks above the roof among them). A player's log above the roof (planks against it, no leaves
     * by it) is not his to break. The overhang above the roof and in the ring beside the walls is cut (the high part from a column of dirt) and the house is finished in one order.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 24000)
    public static void aRoofUnderTheBranchesOfATreeIsFinishedAfterTheOverhangIsCut(GameTestHelper h) {
        Setup s = bigSetup(h);
        Map<Item, Integer> bag = placeReference(h, TAIGA_TEMPLATE, 1);
        fill(s.companion, bag);
        var size = CompanionBuildPlacement.preview(h.getLevel(), TAIGA, 0);
        BlockPos corner = h.absolutePos(new BlockPos(14, GROUND, 8));
        int top = size.sizeY() - 1;
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(14 - 4, GROUND + 1, 8 - 4), new BlockPos(14 + size.sizeX() + 3, GROUND + 14, 8 + size.sizeZ() + 3))) h.setBlock(rel, Blocks.AIR);
        for (int x = -4; x < size.sizeX() + 4; x++) for (int z = -4; z < size.sizeZ() + 4; z++) for (int y = top - 2; y <= top + 2; y++) {
            BlockPos rel = new BlockPos(14 + x, GROUND + y, 8 + z);
            boolean log = Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 && (y == size.sizeY() || y == size.sizeY() + 1);
            h.setBlock(rel, log ? Blocks.DARK_OAK_LOG.defaultBlockState() : Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        }
        // The player's log: two logs and a plank against them, a pocket of air round, in the cell above the roof at the corner of the zone.
        BlockPos own = corner.offset(-1, size.sizeY(), -1);
        for (BlockPos pos : BlockPos.betweenClosed(own.offset(-2, -2, -2), own.offset(3, 2, 2))) h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(own, Blocks.OAK_LOG.defaultBlockState(), 3);
        h.getLevel().setBlock(own.east(), Blocks.OAK_LOG.defaultBlockState(), 3);
        h.getLevel().setBlock(own.south(), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        s.companion.setItem(s.companion.getContainerSize() - 1, new ItemStack(Items.DIRT, 24));
        h.assertTrue(s.companion.startWork("build_house", TAIGA, 1).success(), "Order rejected");
        h.runAfterDelay(30, () -> {
            String refused = CompanionBuildPlacement.confirm(s.player, TAIGA, 0, corner);
            h.assertTrue(refused == null, "Confirmation refused: " + refused);
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 40 && !s.companion.isWorking() && !s.companion.isGatheringTask(), "Not finished: " + s.companion.getLastTaskResult());
            h.assertTrue(s.companion.didLastTaskSucceed(), s.companion.getLastTaskResult());
            assertLooksLikeReference(h, corner, 1);
            // The layers above the roof over the building are free; the player's log stands.
            for (int x = 0; x < size.sizeX(); x++) for (int z = 0; z < size.sizeZ(); z++) for (int y = size.sizeY(); y <= size.sizeY() + 1; y++)
                h.assertTrue(h.getLevel().getBlockState(corner.offset(x, y, z)).isAir(), "The overhang stands above the roof at " + corner.offset(x, y, z).toShortString());
            h.assertTrue(h.getLevel().getBlockState(own).is(Blocks.OAK_LOG) && h.getLevel().getBlockState(own.east()).is(Blocks.OAK_LOG), "The player's log was cut");
        });
    }

    // ---- helpers

    private static Setup bigSetup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            h.setBlock(new BlockPos(x, GROUND, z), Blocks.RED_SAND);
        }
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 24, GROUND + 1, 24);
        companion.setItem(companion.getContainerSize() - 2, new ItemStack(Items.IRON_SHOVEL));
        companion.setItem(companion.getContainerSize() - 3, new ItemStack(Items.IRON_AXE));
        var player = h.makeMockServerPlayerInLevel(); player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player); companion.setSearchRadius(24);
        companion.setFollowing(false);
        CompanionBuildPlacement.clientReady(player);
        return new Setup(companion, player);
    }

    private static List<BlockState> region(GameTestHelper h) {
        List<BlockState> all = new ArrayList<>();
        for (BlockPos rel : BlockPos.betweenClosed(FAR.offset(-4, -1, -4), FAR.offset(8, 12, 8))) all.add(h.getBlockState(rel));
        return all;
    }

    /** The game's own placement of the template in the corner of the arena (at {@code at}); returns what the bag needs to build one more like it. */
    private static Map<Item, Integer> placeReference(GameTestHelper h, ResourceLocation id, int at) {
        var template = h.getLevel().getServer().getStructureManager().get(id).orElseThrow();
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(9, 9, 9))) h.setBlock(rel, Blocks.AIR);
        BlockPos origin = h.absolutePos(new BlockPos(at, at, at));
        template.placeInWorld(h.getLevel(), origin, origin, new StructurePlaceSettings().setIgnoreEntities(true), RandomSource.create(1), 2);
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(8, 8, 8))) {
            BlockState state = h.getBlockState(rel);
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

    /** Every block of the reference stands at the same place of the building at {@code corner}. */
    private static void assertLooksLikeReference(GameTestHelper h, BlockPos corner, int at) {
        int checked = 0;
        BlockPos reference = h.absolutePos(new BlockPos(at, at, at));
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(8, 8, 8))) {
            BlockState want = h.getBlockState(rel);
            if (want.isAir() || want.is(Blocks.JIGSAW) || want.is(Blocks.STRUCTURE_VOID)) continue;
            BlockPos pos = corner.offset(h.absolutePos(rel).subtract(reference));
            BlockState got = h.getLevel().getBlockState(pos);
            h.assertTrue(got.is(want.getBlock()), "Block " + pos.subtract(corner) + " is " + got.getBlock() + ", the placement has " + want.getBlock());
            if (want.is(Blocks.CHEST)) h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof Container chest && chest.isEmpty(), "Chest is not empty");
            for (var property : want.getProperties())
                if (Set.of("facing", "axis", "half", "part", "hinge").contains(property.getName()))
                    h.assertTrue(got.getValue(property).equals(want.getValue(property)), "Block " + pos.subtract(corner) + " is turned differently: " + property.getName());
            checked++;
        }
        h.assertTrue(checked > 40, "Reference too small to prove anything: " + checked);
    }

}

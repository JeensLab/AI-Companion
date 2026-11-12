package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBuildPlacement;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.aicompanion.gametest.CompanionHouseTests.*;

/**
 * A cell somebody stands in (a mob that does not move off): nothing is built above it, so nothing is sealed into the hole under a block, and
 * the materials still missing are gone for before he waits for the cell.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBuildRoomTests {
    /** Where the building is set down (relative), clear of the reference at (1, 1, 1), the companion and the player. */
    private static final BlockPos CORNER = new BlockPos(18, GROUND, 14);

    private record Scene(CompanionEntity companion, ServerPlayer player, BlockPos corner, Map<Item, Integer> bag, BlockPos reference, Chicken[] chicken) { }

    /** The cell of the reference (relative to its corner) a wall block stands in with another block right above: where a chicken is to stand. */
    private static BlockPos wallCell(GameTestHelper h) {
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 2, 1), new BlockPos(5, 3, 5))) {
            if (plain(h.getBlockState(rel)) && plain(h.getBlockState(rel.above()))) return rel.immutable();
        }
        throw new IllegalStateException("No wall cell with a block above it in the reference");
    }
    private static boolean plain(BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof BedBlock) && !state.is(Blocks.WALL_TORCH) && !state.is(Blocks.JIGSAW) && !state.is(Blocks.STRUCTURE_VOID);
    }

    private static Scene scene(GameTestHelper h, boolean missingTheTopBlock) {
        CompanionEntity companion = groundedSetup(h);
        ServerPlayer player = CompanionBuildReviewTests.playerOf(h);
        CompanionBuildPlacement.clientReady(player);
        Map<Item, Integer> bag = new java.util.LinkedHashMap<>(placeReference(h, VILLAGE_HOUSE_TEMPLATE));
        BlockPos cell = wallCell(h);
        if (missingTheTopBlock) {
            // One block of what the highest cell is made of: the last of that kind he would set down has none.
            BlockPos top = null;
            for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(5, 6, 5))) if (plain(h.getBlockState(rel)) && (top == null || rel.getY() > top.getY())) top = rel.immutable();
            Item item = h.getBlockState(top).getBlock().asItem();
            bag.merge(item, -1, Integer::sum);
            if (bag.get(item) <= 0) bag.remove(item);
        }
        fill(companion, bag);
        spareDirt(companion);
        for (BlockPos rel : BlockPos.betweenClosed(CORNER.offset(-2, 1, -2), CORNER.offset(10, 12, 10))) h.setBlock(rel, Blocks.AIR);
        Scene scene = new Scene(companion, player, h.absolutePos(CORNER), bag, cell, new Chicken[1]);
        // Nothing pushes the chicken off its cell: it is held there each tick until it is taken away (registered here, not in a callback: it adds an entry for every tick).
        h.onEachTick(() -> {
            Chicken chicken = scene.chicken[0];
            if (chicken == null || !chicken.isAlive()) return;
            chicken.setPos(Vec3.atBottomCenterOf(h.absolutePos(cellOf(scene, 0)))); chicken.setDeltaMovement(Vec3.ZERO);
        });
        return scene;
    }
    private static BlockPos cellOf(Scene scene, int up) {
        return CORNER.offset(scene.reference.getX() - 1, scene.reference.getY() - 1 + up, scene.reference.getZ() - 1);
    }

    /** A chicken that stays where it is put (no will, no weight): the occupant of one cell of the building. */
    private static void chickenIn(GameTestHelper h, Scene scene) {
        Chicken chicken = h.spawn(EntityType.CHICKEN, cellOf(scene, 0));
        chicken.setNoAi(true); chicken.setNoGravity(true);
        scene.chicken[0] = chicken;
    }

    /**
     * A chicken stays in a wall cell: the cells above it are not built while it is there, so it is never sealed in; he waits for it, names it,
     * and finishes the building once it has gone.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 9000)
    public static void nothingIsBuiltAboveACellSomeoneStandsIn(GameTestHelper h) {
        Scene s = scene(h, false);
        h.assertTrue(s.companion.startWork("build_house", VILLAGE_HOUSE, 1).success(), "Order rejected");
        boolean[] released = {false};
        h.runAfterDelay(20, () -> {
            h.assertTrue(CompanionBuildPlacement.confirm(s.player, VILLAGE_HOUSE, 0, s.corner) == null, "Confirmation refused");
            chickenIn(h, s);
        });
        h.onEachTick(() -> {
            if (s.chicken[0] == null || released[0] || !s.companion.getLastTaskResult().startsWith("Жду, пока освободится")) return;
            // Every cell of the reference above the chicken's, in its column.
            List<BlockPos> above = new ArrayList<>();
            for (int up = 1; up <= 4; up++) {
                BlockPos rel = s.reference.above(up);
                if (plain(h.getBlockState(rel))) above.add(s.corner.offset(rel.getX() - 1, rel.getY() - 1, rel.getZ() - 1));
            }
            h.assertTrue(!above.isEmpty(), "The premise: there are blocks above the chicken's cell");
            for (BlockPos cell : above) h.assertTrue(h.getLevel().getBlockState(cell).isAir(), "A block was set down above the chicken at " + cell.toShortString() + ": it is sealed in");
            h.assertTrue(s.companion.getLastTaskResult().contains("Курица") || s.companion.getLastTaskResult().toLowerCase().contains("chicken"), "He did not name who stands there: " + s.companion.getLastTaskResult());
            s.chicken[0].discard(); released[0] = true;
        });
        h.succeedWhen(() -> {
            h.assertTrue(released[0], "He has not waited for the chicken yet: " + s.companion.getLastTaskResult());
            done(h, s.companion);
            h.assertTrue(s.companion.didLastTaskSucceed(), s.companion.getLastTaskResult());
            assertLooksLikeReference(h, s.corner);
        });
    }

    /** What is still missing is got before he waits for an occupied cell: the wait is only for when there is nothing else to do. */
    @GameTest(template = "tall_tree", timeoutTicks = 9000)
    public static void aMissingMaterialIsGotBeforeWaitingForAnOccupiedCell(GameTestHelper h) {
        Scene s = scene(h, true);
        h.assertTrue(s.companion.startWork("build_house", VILLAGE_HOUSE, 1).success(), "Order rejected");
        h.runAfterDelay(20, () -> {
            h.assertTrue(CompanionBuildPlacement.confirm(s.player, VILLAGE_HOUSE, 0, s.corner) == null, "Confirmation refused");
            chickenIn(h, s);
        });
        h.onEachTick(() -> h.assertTrue(!s.companion.getLastTaskResult().startsWith("Жду, пока освободится"), "He waits for the cell with materials still to get: " + s.companion.getLastTaskResult()));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 40 && s.companion.getLastTaskResult().contains("Не хватает"), "The round for the missing material has not begun: " + s.companion.getLastTaskResult());
            s.companion.stopGathering();
        });
    }
}

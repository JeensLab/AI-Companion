package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The mine's temporary blocks (a bench on a step): the record reads their cell as open, so the staircase below is not cut off, a seal
 * still cuts it, the cells are saved with the record and an older save without them loads as before.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineTemporaryTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, STANDS = 8, BENCH_STAND = 4;

    private CompanionMineTemporaryTests() { }

    /** A bench in the column of a stand cuts the stairs at it unless it is a temporary block; a seal never saves them. */
    @GameTest(template = "marathon_cave")
    public static void aTemporaryBlockDoesNotCutTheStairsButASealDoes(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stairs = stairs(h, entrance);
        BlockPos bench = stairs.get(BENCH_STAND);
        level.setBlock(bench, Blocks.CRAFTING_TABLE.defaultBlockState(), 2 | 16);

        CompanionMineSite plain = site(entrance, stairs);
        h.assertTrue(plain.verify(level) == BENCH_STAND - 1, "A bench nobody owns kept the stairs: " + plain.stands().size());

        CompanionMineSite sealed = site(entrance, stairs);
        sealed.sealed(bench);
        h.assertTrue(sealed.verify(level) == BENCH_STAND - 1, "A seal did not cut the stairs: " + sealed.stands().size());

        CompanionMineSite temporary = site(entrance, stairs);
        temporary.temporary(bench);
        h.assertTrue(temporary.verify(level) == STANDS && temporary.temporary().contains(bench), "A temporary block cut the stairs: " + temporary.stands().size());

        level.setBlock(bench, Blocks.AIR.defaultBlockState(), 2 | 16);
        h.assertTrue(temporary.verify(level) == STANDS && temporary.temporary().isEmpty(), "A temporary block that is gone stays in the record");
        h.succeed();
    }

    /** A bench in the entrance's own column does not cost the whole mine. */
    @GameTest(template = "marathon_cave")
    public static void aTemporaryBlockInTheEntranceKeepsTheMine(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stairs = stairs(h, entrance);
        level.setBlock(entrance.above(), Blocks.CRAFTING_TABLE.defaultBlockState(), 2 | 16);
        CompanionMineSite shut = site(entrance, stairs);
        h.assertTrue(shut.verify(level) == 0, "The entrance is shut by a bench nobody owns: " + shut.stands().size());
        CompanionMineSite temporary = site(entrance, stairs);
        temporary.temporary(entrance.above());
        h.assertTrue(temporary.verify(level) == STANDS, "A bench in the entrance cut the mine: " + temporary.stands().size());
        h.succeed();
    }

    /** The cells are saved with the record, kept by a restart, dropped by clear(), and a save without the key loads with none. */
    @GameTest(template = "marathon_cave")
    public static void temporaryBlocksSurviveASaveAndARestartAndOldSavesLoad(GameTestHelper h) {
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stairs = stairs(h, entrance);
        CompanionMineSite site = site(entrance, stairs);
        BlockPos bench = stairs.get(BENCH_STAND), other = stairs.get(BENCH_STAND + 1);
        site.temporary(bench);
        site.temporary(other);
        site.taken(other);
        CompoundTag tag = new CompoundTag();
        site.save(tag);
        CompanionMineSite loaded = new CompanionMineSite();
        loaded.load(tag);
        h.assertTrue(loaded.temporary().equals(java.util.Set.of(bench)) && loaded.stands().size() == STANDS, "The temporary blocks were lost over a save: " + loaded.temporary());

        loaded.restart(stairs.get(STANDS), Direction.WEST);
        h.assertTrue(loaded.temporary().contains(bench), "A restart forgot the bench still standing");
        loaded.clear();
        h.assertTrue(loaded.temporary().isEmpty(), "clear() keeps the temporary blocks");

        CompanionMineSite fresh = site(entrance, stairs);
        CompoundTag plain = new CompoundTag();
        fresh.save(plain);
        h.assertTrue(!plain.getCompound(CompanionMineSite.KEY).contains("Temporary"), "An empty set is saved");
        CompanionMineSite fromOld = new CompanionMineSite();
        fromOld.load(plain);
        h.assertTrue(fromOld.exists() && fromOld.temporary().isEmpty() && fromOld.stands().size() == STANDS, "A save without the key loads wrong");

        CompanionMineSite unrecorded = new CompanionMineSite();
        unrecorded.temporary(bench);
        h.assertTrue(unrecorded.temporary().isEmpty(), "A temporary block was recorded without a mine");
        h.succeed();
    }

    private static List<BlockPos> stairs(GameTestHelper h, BlockPos entrance) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), (y == GROUND ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState(), 2 | 16);
        }
        List<BlockPos> stairs = CompanionShaftPlan.stands(entrance, Direction.NORTH, STANDS);
        for (BlockPos stand : stairs) for (BlockPos cell : CompanionShaftPlan.column(stand)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        return stairs;
    }

    private static CompanionMineSite site(BlockPos entrance, List<BlockPos> stairs) {
        CompanionMineSite site = new CompanionMineSite();
        site.start(entrance, Direction.NORTH);
        for (BlockPos stand : stairs.subList(1, stairs.size())) site.reached(stand);
        return site;
    }
}

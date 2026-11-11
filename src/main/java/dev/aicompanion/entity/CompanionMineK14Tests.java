package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The way out of a fuse, in the cases where the floor it leads over is not a plain one. The tests live beside the package-private classes they call. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK14Tests {
    private CompanionMineK14Tests() { }

    /**
     * A corridor with a nook to the north whose deep cells have a closed lava source in the stone at the foot of each wall: out of the creeper's
     * sight and far from it, they are the best end of the way, and the one that is not beside lava is the shallow one.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 100)
    public static void fuseEscapeNeverEndsAtTheEdgeOfLava(GameTestHelper h) {
        for (int x = 0; x <= 20; x++) for (int z = 0; z <= 14; z++) for (int y = 0; y <= 12; y++) {
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : y <= 8 ? Blocks.STONE : Blocks.AIR);
        }
        for (int x = 8; x <= 16; x++) carve(h, x, 10);
        for (int z = 4; z <= 9; z++) carve(h, 8, z);
        for (int z = 3; z <= 7; z++) {
            h.setBlock(new BlockPos(7, 2, z), Blocks.LAVA);
            h.setBlock(new BlockPos(9, 2, z), Blocks.LAVA);
        }
        h.setBlock(new BlockPos(8, 2, 3), Blocks.LAVA);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 10, 3, 10);
        Creeper creeper = h.spawn(EntityType.CREEPER, 13, 3, 10);
        creeper.setNoAi(true);

        CompanionFuseEscape escape = new CompanionFuseEscape(c);
        int length = escape.plan(h.getLevel(), List.of(creeper));
        BlockPos goal = escape.goal();
        h.assertTrue(goal != null && !lavaBeside(h.getLevel(), goal),
                "The way out of the fuse ends at " + rel(h, goal) + ", beside lava (length " + length + ")");
        h.succeed();
    }

    private static String rel(GameTestHelper h, BlockPos world) { return world == null ? "none" : h.relativePos(world).toShortString(); }

    private static void carve(GameTestHelper h, int x, int z) {
        for (int y = 3; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    /** Lava in the cube of a cell and its neighbours, as a body that stands there is burnt by it the moment the lava moves or he slips. */
    private static boolean lavaBeside(ServerLevel level, BlockPos cell) {
        for (BlockPos near : BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, 1, 1))) {
            if (level.getFluidState(near).is(net.minecraft.tags.FluidTags.LAVA)) return true;
        }
        return false;
    }
}

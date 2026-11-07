package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Down a staircase he walks facing the way down (#105, «лестница зигзагом»). The step he jerked round on: still a block
 * above the next node of his path, past its centre, the game does not count the node as reached (it wants less than a
 * block of height between them), so the move control turned him back to the centre behind him, up to 90° in a tick.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionStairDescentTests {
    /** A tick's turn on a straight staircase: the walk needs none, turning back to a node behind him is 90. */
    private static final float MOST_TICK_TURN = 45F;
    private static final int LANES = 8;

    private static final class Lane {
        final CompanionEntity c; final int z; float worst, last = Float.NaN; String worstAt = ""; int edges;
        Lane(CompanionEntity c, int z) { this.c = c; this.z = z; }
    }

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void downAStaircaseHeDoesNotTurnBack(GameTestHelper h) {
        // Eight corridors one wide, side by side: a landing five high, four steps down along +x, a floor at the foot. Each
        // walker starts a little further on, so one of them is at the edge of a step on the very tick the bug needs.
        for (int x = 1; x <= 20; x++) for (int z = 2; z <= 2 + LANES * 2; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            for (int y = 1; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), z % 2 == 0 ? Blocks.STONE : Blocks.AIR);
        }
        List<Lane> lanes = new ArrayList<>();
        for (int i = 0; i < LANES; i++) {
            int z = 3 + i * 2;
            for (int x = 1; x <= 7; x++) for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            for (int step = 1; step <= 4; step++) for (int y = 1; y <= 5 - step; y++) h.setBlock(new BlockPos(7 + step, y, z), Blocks.STONE);
            var c = h.spawn(AICompanionMod.COMPANION.get(), new Vec3(3.5 + (i % 4) * .07, 6, z + .5));
            c.setFollowing(false);
            BlockPos foot = h.absolutePos(new BlockPos(19, 1, z));
            double speed = i < 4 ? 1.0 : 1.3;
            h.runAfterDelay(2, () -> c.getNavigation().moveTo(foot.getX() + .5, foot.getY(), foot.getZ() + .5, speed));
            lanes.add(new Lane(c, z));
        }
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        h.onEachTick(() -> {
            for (Lane l : lanes) {
                double x = l.c.getX() - corner.getX();
                if (x >= 7.5 && x <= 13 && !Float.isNaN(l.last)) {
                    float turn = Math.abs(Mth.wrapDegrees(l.c.getYRot() - l.last));
                    if (turn > l.worst) { l.worst = turn; l.worstAt = String.format("x=%.2f y=%.2f", x, l.c.getY() - corner.getY()); }
                }
                l.last = l.c.getYRot();
                // The case itself: past the centre of a lower cell while still at the height of the step before it.
                int cell = Mth.floor(x);
                if (cell >= 8 && cell <= 12 && x - cell >= .5 && Math.abs(l.c.getY() - corner.getY() - standing(cell - 1)) < 1e-6) l.edges++;
            }
        });
        h.succeedWhen(() -> {
            StringBuilder all = new StringBuilder();
            for (Lane l : lanes) all.append(" lane ").append(l.z).append(": ").append(Math.round(l.worst)).append("° at ").append(l.worstAt);
            for (Lane l : lanes) h.assertTrue(l.c.getX() - corner.getX() > 17 && l.c.getY() - corner.getY() < 1.5, "Not down yet:" + all);
            h.assertTrue(lanes.stream().anyMatch(l -> l.edges > 0), "No walker was past a lower cell's centre at the upper height: the case was not met");
            for (Lane l : lanes) h.assertTrue(l.worst < MOST_TICK_TURN, "He turned back on a step:" + all);
        });
    }

    /**
     * The same edge a hair short of the lower node's centre instead of past it (about one walk in fifty): the node is then under
     * him, the way to it points anywhere, and the move control turned him by the drift of his walk, 72-78° in a tick. He is put
     * where such a walk stopped: at the step's height, still on the ground, the node below him next.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void aHairShortOfTheCentreBelowHimHeDoesNotTurn(GameTestHelper h) {
        int z = 3;
        for (int x = 1; x <= 20; x++) for (int side = 2; side <= 4; side++) {
            h.setBlock(new BlockPos(x, 0, side), Blocks.BEDROCK);
            for (int y = 1; y <= 8; y++) h.setBlock(new BlockPos(x, y, side), side == z ? Blocks.AIR : Blocks.STONE);
        }
        for (int x = 1; x <= 7; x++) for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        for (int step = 1; step <= 4; step++) for (int y = 1; y <= 5 - step; y++) h.setBlock(new BlockPos(7 + step, y, z), Blocks.STONE);
        var c = h.spawn(AICompanionMod.COMPANION.get(), new Vec3(3.5, 6, z + .5));
        c.setFollowing(false);
        BlockPos foot = h.absolutePos(new BlockPos(19, 1, z)), corner = h.absolutePos(BlockPos.ZERO);
        h.runAfterDelay(2, () -> c.getNavigation().moveTo(foot.getX() + .5, foot.getY(), foot.getZ() + .5, 1.0));
        float[] before = {Float.NaN}, turn = {Float.NaN};
        h.onEachTick(() -> {
            if (!Float.isNaN(before[0])) {
                if (Float.isNaN(turn[0])) turn[0] = Math.abs(Mth.wrapDegrees(c.getYRot() - before[0]));
                return;
            }
            var path = c.getNavigation().getPath();
            if (path == null || path.isDone() || path.getNextNodePos().getX() - corner.getX() != 9 || !c.onGround()
                    || Math.abs(c.getY() - corner.getY() - standing(8)) > 1e-6) return;
            // Three ten-thousandths short of x 9.5 and a thousandth off the lane's middle: as the red walk stood (x 9.500, y 5).
            c.teleportTo(corner.getX() + 9.5 - 3e-4, c.getY(), corner.getZ() + z + .5 + 1e-3);
            // Still on the ground from his last step, as on the edge tick: in the air the game's own rule would take the node.
            h.assertTrue(c.onGround(), "Put over the edge, he no longer counts as on the ground: the case is not met");
            before[0] = c.getYRot();
        });
        h.succeedWhen(() -> {
            h.assertTrue(!Float.isNaN(turn[0]), "He did not come to the step's edge with the node below him next");
            h.assertTrue(turn[0] < MOST_TICK_TURN, "He turned " + Math.round(turn[0]) + "° in a tick over the node below him");
        });
    }

    /** The height he stands at in a lane's column: the landing, the four steps, the floor. */
    private static int standing(int x) { return Math.max(1, Math.min(6, 13 - x)); }
}

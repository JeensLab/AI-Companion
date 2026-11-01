package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.DEEPSLATE_TOP;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * Stops of the stairs that ended or froze the order: a foe he can see and cannot reach, and a mine whose record is left
 * with no way back to its stairs. Arena as in the other mine tests.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineStopsTests {
    private static final int CHAMBER_REACH = 13, CHAMBER_HEIGHT = 9, PILLAR_AT = 6, PILLAR_HEIGHT = 5, OWN_STAND = 3, PIT = 11, FAR_ENTRANCE = 200, POCKET_AT = 8, NEAR_CELLS = 2;

    private CompanionMineStopsTests() { }

    /**
     * A zombie on a pillar in a chamber at the foot of the stairs: in sight, near, and out of his reach. He does not stand facing it for
     * the rest of the order; the foe he cannot get at is let go and the order goes on to the diamond.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void doesNotStandForeverFacingAFoeHeCannotReach(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        c.setItem(6, new ItemStack(Items.STONE_SWORD));
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = 0; dx <= CHAMBER_REACH; dx++) for (int dz = -CHAMBER_REACH / 2; dz <= CHAMBER_REACH / 2; dz++) for (int dy = 0; dy < CHAMBER_HEIGHT; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        BlockPos pillar = foot.offset(PILLAR_AT, 0, 0);
        for (int dy = 0; dy < PILLAR_HEIGHT; dy++) h.getLevel().setBlock(pillar.above(dy), Blocks.STONE.defaultBlockState(), 2 | 16);
        c.teleportTo(foot.getX() + OWN_STAND + .5, foot.getY(), foot.getZ() + .5);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, rel(h, pillar.above(PILLAR_HEIGHT)));
        zombie.setPersistenceRequired();
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not get past the foe he cannot reach: " + result.message() + " | zombie " + (zombie.isAlive() ? "alive" : "gone") + " | " + report(c));
        });
    }

    /**
     * He stands in a closed pit deep under the ground; the record's entrance is far away on the surface and no stairs are left near him,
     * and no spot for a new entrance is in reach. He does not give up with "no place for an entrance": the mine starts again in the pit.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainInAPitWhenTheEntranceIsFarAway(GameTestHelper h) {
        mountain(h, true);
        for (int x = PIT - 1; x <= PIT + 1; x++) for (int z = PIT - 1; z <= PIT + 1; z++) for (int y = DEEPSLATE_TOP; y <= DEEPSLATE_TOP + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        CompanionEntity c = rig(h);
        BlockPos pit = h.absolutePos(new BlockPos(PIT, DEEPSLATE_TOP, PIT));
        c.teleportTo(pit.getX() + .5, pit.getY(), pit.getZ() + .5);
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER + FAR_ENTRANCE, GROUND + 1, CENTER)), Direction.NORTH);
        // The light engine runs between ticks: the order starts once the pit reads as roofed over, as it does in the world.
        long[] id = {0};
        h.succeedWhen(() -> {
            if (id[0] == 0) {
                h.assertTrue(!h.getLevel().canSeeSky(pit.above()), "The light in the pit is not worked out yet");
                id[0] = start(h, c, 1);
            }
            CompanionActionResult result = ended(h, c, id[0]);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not start again in the pit: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 1, "Restarts " + c.mineSite().restarts());
        });
    }

    /**
     * The record's stairs were turned into flat stands deep down, away from the entrance, and he stands by the entrance on the surface with
     * every side of his cell refused: the way to the stands is gone and the mine cannot start again at his cell. He goes back to the entrance
     * and begins stairs to a side not tried from it, instead of ending with "could not get back to my stairs".
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void startsAgainFromTheEntranceWhenTheRecordedStandsAreCutOff(GameTestHelper h) {
        mountain(h, true);
        BlockPos pocket = h.absolutePos(new BlockPos(CENTER - POCKET_AT, DEEPSLATE_TOP + 2, CENTER + POCKET_AT));
        for (BlockPos cell : CompanionShaftPlan.column(pocket)) h.getLevel().setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionEntity c = rig(h);
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), here = h.absolutePos(new BlockPos(CENTER + OWN_STAND - 1, GROUND + 1, CENTER));
        c.teleportTo(here.getX() + .5, here.getY(), here.getZ() + .5);
        c.mineSite().start(entrance, Direction.NORTH);
        c.mineSite().reached(pocket);
        // The search takes him over the stands: every cell above them is refused on all sides, as the log showed, the entrance cell is not.
        BlockPos above = new BlockPos(pocket.getX(), entrance.getY(), pocket.getZ());
        for (BlockPos cell : BlockPos.betweenClosed(above.offset(-NEAR_CELLS, 0, -NEAR_CELLS), above.offset(NEAR_CELLS, 0, NEAR_CELLS))) {
            for (Direction d : Direction.Plane.HORIZONTAL) c.mineSite().refuse(cell.immutable(), d);
        }
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "He did not start again from the entrance: " + result.message() + " | " + report(c));
            h.assertTrue(c.mineSite().restarts() == 1, "Restarts " + c.mineSite().restarts());
        });
    }
}

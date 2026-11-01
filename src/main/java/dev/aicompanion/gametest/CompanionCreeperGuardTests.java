package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionMineSite;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

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
 * A live creeper (it walks, it swells, it explodes) on his way in the mine, in the cases the old guard did not look in. Nothing here is
 * switched off with NoAI: the creeper comes for him as it would, and what is asked is that he lives, the order goes on and the stairs stay whole.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCreeperGuardTests {
    private static final int OPEN_CAVE_TICKS = 600, CHAMBER_REACH = 13, CHAMBER_HEIGHT = 4, OPEN_CAVE_STAND = 3;

    private CompanionCreeperGuardTests() { }

    /** He digs at the foot of the stairs and a creeper comes down them behind him. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperComingDownTheStairsBehindHimLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        place(h, c, stairs.get(foot - 2));
        Creeper creeper = creeper(h, stairs.get(foot - 9));
        goesOn(h, c, creeper, start(h, c, 1), true);
    }

    /** He walks down the stairs and a creeper comes up them to meet him. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperComingUpTheStairsToMeetHimLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        place(h, c, stairs.get(2));
        Creeper creeper = creeper(h, stairs.get(12));
        goesOn(h, c, creeper, start(h, c, 1), true);
    }

    /** No pickaxe is left, so he climbs, and a creeper waits on the stairs above him. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperOnTheStairsDuringTheClimbLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.STONE_PICKAXE) || c.getItem(slot).is(Items.IRON_PICKAXE)) c.setItem(slot, ItemStack.EMPTY);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        int foot = stairs.size() - 1;
        place(h, c, stairs.get(foot - 1));
        Creeper creeper = creeper(h, stairs.get(foot - 8));
        goesOn(h, c, creeper, start(h, c, 1), false);
    }

    /** Coal in the stair wall: every block he mines drops a stack he turns to pick up, and a creeper comes down the stairs meanwhile. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperWhileHeGathersWhatHeMinedLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        for (int i = foot - 8; i <= foot - 2; i++) h.getLevel().setBlock(stairs.get(i).west(), Blocks.COAL_ORE.defaultBlockState(), 2 | 16);
        place(h, c, stairs.get(foot - 9));
        Creeper creeper = creeper(h, stairs.get(foot - 16));
        goesOn(h, c, creeper, start(h, c, 1), true);
    }

    /** He is three blocks into a wide open chamber at the foot of the stairs, no order given, and a creeper comes across it from the far wall: he gets to the stairs, shuts them and lives. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperInAnOpenCaveLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = 0; dx <= CHAMBER_REACH; dx++) for (int dz = -CHAMBER_REACH / 2; dz <= CHAMBER_REACH / 2; dz++) for (int dy = 0; dy < CHAMBER_HEIGHT; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(h, c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(CHAMBER_REACH, 0, 0));
        long begun = h.getTick();
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + ", creeper " + creeper.isAlive() + " | " + report(c));
            if (h.getTick() - begun < OPEN_CAVE_TICKS) return;
            h.assertTrue(creeper.isRemoved() || creeper.distanceTo(c) > 3, "The creeper is at his side: " + creeper.distanceTo(c) + " | " + report(c));
            h.succeed();
        });
    }

    private static void place(GameTestHelper h, CompanionEntity c, BlockPos stand) {
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
    }

    private static Creeper creeper(GameTestHelper h, BlockPos at) {
        Creeper creeper = h.spawn(EntityType.CREEPER, rel(h, at));
        creeper.setPersistenceRequired();
        return creeper;
    }

    /** A creeper that stays at the block this long has not wandered off as one would; it is taken away, as a despawn or a stroll would. */
    private static final int DISMISS_TICKS = 400;

    /** Alive all along; the order ends (with diamond when {@code mines}) and the stairs still stand after the mine is checked. */
    private static void goesOn(GameTestHelper h, CompanionEntity c, Creeper creeper, long id, boolean mines) {
        CompanionMineSite site = c.mineSite();
        int stands = site.stands().size();
        double[] last = {-1, 99};
        long[] shutAt = {-1};
        h.onEachTick(() -> {
            if (creeper.isAlive()) { last[0] = creeper.distanceTo(c); last[1] = Math.min(last[1], last[0]); }
            if (shutAt[0] < 0 && !site.temporary().isEmpty()) shutAt[0] = h.getTick();
            if (creeper.isAlive() && shutAt[0] >= 0 && h.getTick() - shutAt[0] > DISMISS_TICKS) creeper.discard();
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", last distance " + Math.round(last[0] * 10) / 10.0 + ", nearest " + Math.round(last[1] * 10) / 10.0
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + ", blocks " + site.temporary().stream().map(cell -> rel(h, cell).toShortString()).toList() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(!mines || result.success() && count(c, Items.DIAMOND) >= 1, "The order did not go on" + trace);
            int kept = site.verify(h.getLevel());
            h.assertTrue(kept == stands, "The stairs were cut: " + kept + " of " + stands + " stands" + trace);
            h.succeed();
        });
    }
}

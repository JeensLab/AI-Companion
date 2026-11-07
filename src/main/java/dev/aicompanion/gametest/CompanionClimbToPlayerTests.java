package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;

/**
 * Runs void-3 and come of seed 7 (07.11): Oleg stands in a pit or a basin walled on every side, the player waits higher up, and the walk to
 * him has no way at all; the hand-over and «ко мне» ended «Не могу к тебе пройти» where a player digs steps up out of the hole. Arena as in
 * the other mine tests; the pit is three by three and nine deep, its walls of andesite up to the turf and a ledge of it over his head (the self-rescue
 * cuts only stone, soil and deepslate, and jumps blocks up under his feet only while there is room to jump), the player stands on the surface ten blocks off its edge.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionClimbToPlayerTests {
    private static final int PIT_DEPTH = 9, ROOF_HEIGHT = 5, PLAYER_EAST = 11;

    private CompanionClimbToPlayerTests() { }

    /** The diamond is thrown at the player's feet, on the surface, after the stairs out of the pit. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void cutsStairsUpToThePlayerWhenNoWayLeadsOutOfTheCanyon(GameTestHelper h) {
        CompanionEntity c = pit(h);
        c.setItem(6, new ItemStack(Items.DIAMOND));
        h.assertTrue(c.startWork("give_items", "minecraft:diamond", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) == 0, "He said «" + result.message() + "» in the pit | " + report(c));
            h.assertTrue(c.getBlockY() >= h.absolutePos(new BlockPos(0, GROUND + 1, 0)).getY(), "He threw it from below the surface at " + c.blockPosition().toShortString() + " | " + report(c));
        });
    }

    /** «Ко мне» from the same pit ends beside the player. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void comesToThePlayerOutOfAPitWhenNoWayLeads(GameTestHelper h) {
        CompanionEntity c = pit(h);
        h.assertTrue(c.startWork("come", "", 1).success(), "«Ко мне» not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success(), "He said «" + result.message() + "» in the pit | " + report(c));
            h.assertTrue(c.getBlockY() >= h.absolutePos(new BlockPos(0, GROUND + 1, 0)).getY(), "He came from below the surface at " + c.blockPosition().toShortString() + " | " + report(c));
        });
    }

    /** Oleg on the floor of a pit cut into the middle of the mountain, the owner on the surface east of it; the bag of the other mine tests holds the pickaxes and the stone. */
    private static CompanionEntity pit(GameTestHelper h) {
        mountain(h, false);
        for (int x = CENTER - 3; x <= CENTER + PLAYER_EAST + 1; x++) for (int z = CENTER - 4; z <= CENTER + 4; z++) for (int y = GROUND - PIT_DEPTH; y < GROUND; y++) {
            BlockPos wall = new BlockPos(x, y, z);
            if (h.getBlockState(wall).is(Blocks.STONE) || h.getBlockState(wall).is(Blocks.DIRT)) h.setBlock(wall, Blocks.ANDESITE);
        }
        for (int x = CENTER - 1; x <= CENTER + 1; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND - PIT_DEPTH + 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = CENTER - 1; x <= CENTER + 1; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) h.setBlock(new BlockPos(x, GROUND - PIT_DEPTH + 1 + ROOF_HEIGHT, z), Blocks.ANDESITE);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos floor = h.absolutePos(new BlockPos(CENTER, GROUND - PIT_DEPTH + 1, CENTER)), owner = h.absolutePos(new BlockPos(CENTER + PLAYER_EAST, GROUND + 1, CENTER));
        c.teleportTo(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
        h.getLevel().getPlayerByUUID(c.getOwnerId()).teleportTo(owner.getX() + .5, owner.getY(), owner.getZ() + .5);
        return c;
    }
}

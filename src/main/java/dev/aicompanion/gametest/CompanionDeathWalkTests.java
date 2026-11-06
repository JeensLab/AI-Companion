package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionDeathTrip;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.support.DeathTestKit.place;
import static dev.aicompanion.gametest.support.DeathTestKit.put;
import static dev.aicompanion.gametest.support.DeathTestKit.setOut;
import static dev.aicompanion.gametest.support.DeathTestKit.settle;
import static dev.aicompanion.gametest.support.DeathTestKit.spawn;
import static dev.aicompanion.gametest.support.DeathTestKit.stack;
import static dev.aicompanion.gametest.support.DeathTestKit.walk;
import static dev.aicompanion.gametest.support.DeathTestKit.yard;

/**
 * The geometry, the navigation and the water of a walk to a death place: where the stack lies, what is in the way, and
 * whether the game's own way there — a step, a drop, a door, a ladder, a swim — is walked instead of guessed at.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDeathWalkTests {

    // ---- geometry: where the stack lies ----

    /** A stack in his own cell is taken at once, without a walk. */
    @GameTest(template = "empty")
    public static void aStackInHisOwnCellIsTakenAtOnce(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 4);
        var item = stack(h, feet, new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        int ticks = walk(h, c, 40);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack under him was not taken");
        h.assertTrue(ticks < 10, "He walked somewhere for a stack at his feet: " + ticks);
        h.assertTrue(c.deathTrip.lastLine().equals("Забрал свои вещи."), "He did not claim it: " + c.deathTrip.lastLine());
        h.succeed();
    }

    /** A stack a block below his feet is taken by stepping down into its cell. */
    @GameTest(template = "empty")
    public static void aStackOneBlockDownIsTakenBySteppingDown(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        BlockPos hole = feet.offset(3, -1, 0);
        put(h, hole, Blocks.AIR);
        var item = stack(h, hole, new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack a block down was not taken: " + item.getItem());
        h.assertTrue(c.deathTrip.verdict(item.getUUID()).fate() == CompanionDeathTrip.Fate.TAKEN,
                "The ledger: " + c.deathTrip.verdict(item.getUUID()));
        h.succeed();
    }

    /** A stack at the bottom of a two-block hole is taken by dropping into it. */
    @GameTest(template = "empty")
    public static void aStackInATwoDeepHoleIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        BlockPos bottom = feet.offset(3, -2, 0);
        put(h, feet.offset(3, -1, 0), Blocks.AIR);
        put(h, bottom, Blocks.AIR);
        var item = stack(h, bottom, new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack in the hole was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack behind a block he must walk round is fetched. */
    @GameTest(template = "empty")
    public static void aStackBehindABlockIsWalkedRound(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(2, 0, 0), Blocks.STONE);
        put(h, feet.offset(2, 1, 0), Blocks.STONE);
        var item = stack(h, feet.offset(3, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack behind the block was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack on a ledge above him is taken by stepping up. */
    @GameTest(template = "empty")
    public static void aStackOnALedgeAboveIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(3, 0, 0), Blocks.STONE);
        var item = stack(h, feet.offset(3, 1, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack on the ledge was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack behind a wall is fetched round its end. */
    @GameTest(template = "empty")
    public static void aStackBehindAWallIsWalkedRound(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        for (int dz = -2; dz <= 2; dz++) {
            put(h, feet.offset(2, 0, dz), Blocks.STONE);
            put(h, feet.offset(2, 1, dz), Blocks.STONE);
        }
        var item = stack(h, feet.offset(4, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack behind the wall was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack under leaves is taken from the side, where he can stand. */
    @GameTest(template = "empty")
    public static void aStackUnderLeavesIsTakenFromTheSide(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(3, 1, 0), Blocks.OAK_LEAVES);
        var item = stack(h, feet.offset(3, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack under the leaves was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack on a cactus is taken from beside it, and he does not have to stand on the spines. */
    @GameTest(template = "empty")
    public static void aStackOnACactusIsTakenFromBeside(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(3, 0, 0), Blocks.CACTUS);
        var item = stack(h, feet.offset(3, 1, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack on the cactus was not taken: " + item.getItem());
        h.succeed();
    }

    /** A fence is not stepped over: he walks round it. */
    @GameTest(template = "empty")
    public static void aStackBehindAFenceIsWalkedRound(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        for (int dz = -2; dz <= 2; dz++) put(h, feet.offset(2, 0, dz), Blocks.OAK_FENCE);
        var item = stack(h, feet.offset(4, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack behind the fence was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack on a slab is taken from the cell beside it. */
    @GameTest(template = "empty")
    public static void aStackOnASlabIsTakenFromBeside(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(3, 0, 0), Blocks.STONE_SLAB);
        var item = stack(h, feet.offset(3, 1, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack on the slab was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack on magma is taken, as a player takes it and puts the fire out afterwards. */
    @GameTest(template = "empty")
    public static void aStackOnMagmaIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(3, -1, 0), Blocks.MAGMA_BLOCK);
        var item = stack(h, feet.offset(3, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack on the magma was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack in a fire cell is taken from the edge of the fire. */
    @GameTest(template = "empty")
    public static void aStackInAFireCellIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(3, 0, 0), Blocks.FIRE);
        var item = stack(h, feet.offset(3, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack in the fire was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack on a closed trapdoor is taken from the side, the trapdoor being no floor to walk to. */
    @GameTest(template = "empty")
    public static void aStackOnAClosedTrapdoorIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(3, 0, 0), Blocks.OAK_TRAPDOOR);
        var item = stack(h, feet.offset(3, 1, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(),
                "The stack on the trapdoor was not taken: " + item.getItem() + " at " + item.blockPosition() + ", he at " + c.blockPosition());
        h.succeed();
    }

    // ---- navigation: the way there ----

    /** Up a hill of two steps, one at a time. */
    @GameTest(template = "empty")
    public static void aStackUpAHillIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(2, 0, 0), Blocks.STONE);
        put(h, feet.offset(3, 0, 0), Blocks.STONE);
        put(h, feet.offset(3, 1, 0), Blocks.STONE);
        var item = stack(h, feet.offset(3, 2, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack on the hill was not taken: " + item.getItem());
        h.succeed();
    }

    /** Through a tunnel in a wall, not over it. */
    @GameTest(template = "empty")
    public static void aStackThroughATunnelIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy <= 2; dy++) put(h, feet.offset(2, dy, dz), Blocks.STONE);
        put(h, feet.offset(2, 0, 0), Blocks.AIR);
        put(h, feet.offset(2, 1, 0), Blocks.AIR);
        var item = stack(h, feet.offset(4, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack through the tunnel was not taken: " + item.getItem());
        h.succeed();
    }

    /** Across a one-block gap: down into it and up the far side. */
    @GameTest(template = "empty")
    public static void aStackAcrossAGapIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(2, -1, 0), Blocks.AIR);
        var item = stack(h, feet.offset(3, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack across the gap was not taken: " + item.getItem());
        h.succeed();
    }

    /** Down a two-block drop to a shelf below. */
    @GameTest(template = "empty")
    public static void aStackDownADropIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(2, -1, 0), Blocks.AIR);
        put(h, feet.offset(3, -1, 0), Blocks.AIR);
        put(h, feet.offset(2, -2, 0), Blocks.AIR);
        put(h, feet.offset(3, -2, 0), Blocks.AIR);
        put(h, feet.offset(2, -3, 0), Blocks.STONE);
        put(h, feet.offset(3, -3, 0), Blocks.STONE);
        var item = stack(h, feet.offset(3, -2, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack down the drop was not taken: " + item.getItem());
        h.succeed();
    }

    /** A closed door is opened and walked through, as a player does. */
    @GameTest(template = "empty")
    public static void aStackBehindAClosedDoorIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy <= 2; dy++) put(h, feet.offset(2, dy, dz), Blocks.STONE);
        put(h, feet.offset(2, 0, 0), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(h, feet.offset(2, 1, 0), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        var item = stack(h, feet.offset(4, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack behind the door was not taken: " + item.getItem());
        h.succeed();
    }

    /** Up a ladder onto the wall's top, where the stack fell. */
    @GameTest(template = "empty")
    public static void aStackUpALadderIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        for (int dy = 0; dy <= 2; dy++) {
            put(h, feet.offset(3, dy, 0), Blocks.STONE);
            put(h, feet.offset(2, dy, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        }
        var item = stack(h, feet.offset(3, 3, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(),
                "The stack up the ladder was not taken: " + item.getItem() + " at " + item.blockPosition() + ", he at " + c.blockPosition()
                        + ", " + c.deathTrip.verdict(item.getUUID()));
        h.succeed();
    }

    /** Down a ladder in a shaft to the stack at its bottom. */
    @GameTest(template = "empty")
    public static void aStackDownALadderIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        for (int dy = 0; dy >= -3; dy--) {
            put(h, feet.offset(3, dy, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
            put(h, feet.offset(4, dy, 0), Blocks.STONE);
        }
        var item = stack(h, feet.offset(3, -3, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(),
                "The stack down the ladder was not taken: " + item.getItem() + " at " + item.blockPosition() + ", he at " + c.blockPosition()
                        + ", " + c.deathTrip.verdict(item.getUUID()));
        h.succeed();
    }

    /** A step of one block is stepped up and over. */
    @GameTest(template = "empty")
    public static void aStackBeyondAStepIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(2, 0, 0), Blocks.STONE);
        var item = stack(h, feet.offset(3, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack beyond the step was not taken: " + item.getItem());
        h.succeed();
    }

    /** Up a staircase of three steps to the stack on its top. */
    @GameTest(template = "empty")
    public static void aStackUpAStaircaseIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        for (int step = 1; step <= 3; step++)
            for (int dy = 0; dy < step; dy++) put(h, feet.offset(1 + step, dy, 0), Blocks.STONE);
        var item = stack(h, feet.offset(4, 3, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(),
                "The stack up the staircase was not taken: " + item.getItem() + " at " + item.blockPosition() + ", he at " + c.blockPosition());
        h.succeed();
    }

    /** A stack twenty blocks off is fetched on foot, in one walk. */
    @GameTest(template = "empty")
    public static void aStackTwentyBlocksOffIsFetched(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 24);
        var item = stack(h, feet.offset(20, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet.offset(20, 0, 0), 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        int ticks = walk(h, c, 1200);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack twenty blocks off was not taken: " + item.getItem());
        h.assertTrue(ticks < 400, "The walk took too long: " + ticks);
        h.succeed();
    }

    /** A stack eighty blocks off is fetched on foot: a long way there is not a stuck walk. */
    @GameTest(template = "empty")
    public static void aStackEightyBlocksOffIsFetched(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        // The stack lies where the owner stands, in a chunk the world hands its entities out from; the walk starts at the
        // far end of a road of its own, so what is tried here is the distance and not the ground it crosses.
        BlockPos feet = yard(h, c, 6);
        for (int dx = 7; dx <= 80; dx++) for (int dz = -8; dz <= 8; dz++) {
            put(h, feet.offset(dx, -1, dz), Blocks.STONE);
            for (int dy = 0; dy <= 2; dy++) put(h, feet.offset(dx, dy, dz), Blocks.AIR);
        }
        // Every chunk of the road is force-loaded: the way is searched over chunks the world hands its blocks out from.
        for (int chunkX = SectionPos.blockToSectionCoord(feet.getX()); chunkX <= SectionPos.blockToSectionCoord(feet.getX() + 80); chunkX++)
            h.getLevel().setChunkForced(chunkX, SectionPos.blockToSectionCoord(feet.getZ()), true);
        var item = stack(h, feet, new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 40, item);
        BlockPos start = feet.offset(80, 0, 0);
        c.teleportTo(start.getX() + .5, start.getY(), start.getZ() + .5);
        h.assertTrue(setOut(h, c), "He did not set out");
        int ticks = walk(h, c, 1800);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(),
                "The stack eighty blocks off was not taken: " + item.getItem() + " at " + item.blockPosition() + ", he at "
                        + c.blockPosition() + ", " + c.deathTrip.verdict(item.getUUID()));
        h.assertTrue(ticks < 1800, "The walk of eighty blocks ran into its bound: " + ticks);
        h.succeed();
    }

    /** A stack across a corner of two walls is fetched round them. */
    @GameTest(template = "empty")
    public static void aStackBehindACornerIsFetched(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        for (int d = 0; d <= 3; d++) {
            put(h, feet.offset(2, 0, d), Blocks.STONE);
            put(h, feet.offset(2, 1, d), Blocks.STONE);
            put(h, feet.offset(d, 0, 2), Blocks.STONE);
            put(h, feet.offset(d, 1, 2), Blocks.STONE);
        }
        var item = stack(h, feet.offset(3, 0, 3), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 800);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack behind the corner was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack on top of a pillar with no way up is unreachable, with the reason said. */
    @GameTest(template = "empty")
    public static void aStackOnATallPillarIsUnreachable(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        for (int dy = 0; dy <= 3; dy++) put(h, feet.offset(3, dy, 0), Blocks.STONE);
        var item = stack(h, feet.offset(3, 4, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        var verdict = c.deathTrip.verdict(item.getUUID());
        h.assertTrue(verdict != null && verdict.fate() == CompanionDeathTrip.Fate.UNREACHABLE, "The stack was not called unreachable: " + verdict);
        h.assertTrue(verdict.reason().equals("нет пути"), "The reason is not the way: " + verdict.reason());
        h.assertTrue(item.isAlive() && !item.getItem().isEmpty(), "The stack on the pillar was taken anyway");
        h.succeed();
    }

    // ---- water: wading, swimming, a current ----

    /** Water to the knee is waded, as a player wades it. */
    @GameTest(template = "empty")
    public static void aStackInShallowWaterIsWadedTo(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(3, 0, 0), Blocks.WATER);
        var item = stack(h, feet.offset(3, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack in the water was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack floating in deep water is swum to. */
    @GameTest(template = "empty")
    public static void aStackInDeepWaterIsSwumTo(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        BlockPos pool = feet.offset(4, 0, 0);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            put(h, pool.offset(dx, 0, dz), Blocks.WATER);
            put(h, pool.offset(dx, -1, dz), Blocks.WATER);
            put(h, pool.offset(dx, -2, dz), Blocks.WATER);
            put(h, pool.offset(dx, -3, dz), Blocks.STONE);
        }
        var item = stack(h, pool, new ItemStack(Items.DIAMOND, 2));
        place(h, c, pool, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        boolean swam = false;
        for (int tick = 0; tick < 400 && c.deathTrip.active(); tick++) {
            c.tick();
            if (c.isInWater()) swam = true;
        }
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack in the deep water was not taken: " + item.getItem());
        h.assertTrue(swam, "He never entered the water");
        h.succeed();
    }

    /** A stack at the foot of a waterfall is fetched. */
    @GameTest(template = "empty")
    public static void aStackAtAWaterfallIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        put(h, feet.offset(4, 0, 0), Blocks.WATER);
        put(h, feet.offset(4, 2, 0), Blocks.WATER);
        put(h, feet.offset(4, 3, 0), Blocks.WATER);
        var item = stack(h, feet.offset(4, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack at the waterfall was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack pushed along by a current is followed where it drifts. */
    @GameTest(template = "empty")
    public static void aStackDriftingInACurrentIsFollowed(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 12);
        for (int dx = 2; dx <= 8; dx++) put(h, feet.offset(dx, 0, 0), Blocks.WATER);
        var item = stack(h, feet.offset(3, 0, 0), new ItemStack(Items.DIAMOND, 2));
        item.setDeltaMovement(.12, 0, 0);
        place(h, c, feet.offset(3, 0, 0), 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The drifting stack was not taken: " + item.getItem() + " at " + item.blockPosition());
        h.succeed();
    }

    /** A pool with no shore at the water's level: he swims to the stack in the middle. */
    @GameTest(template = "empty")
    public static void aStackInAPoolWithNoShoreIsSwumTo(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 12);
        BlockPos centre = feet.offset(6, 0, 0);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            put(h, centre.offset(dx, 0, dz), Blocks.WATER);
            put(h, centre.offset(dx, -1, dz), Blocks.WATER);
            put(h, centre.offset(dx, -2, dz), Blocks.STONE);
        }
        var item = stack(h, centre, new ItemStack(Items.DIAMOND, 2));
        place(h, c, centre, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 800);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack in the pool was not taken: " + item.getItem());
        h.succeed();
    }

    /** Water under a ledge: the stack is taken from the cell beside it. */
    @GameTest(template = "empty")
    public static void aStackInWaterUnderALedgeIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 8);
        put(h, feet.offset(3, 0, 0), Blocks.WATER);
        put(h, feet.offset(3, 1, 0), Blocks.STONE);
        var item = stack(h, feet.offset(3, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet, 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 400);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack in the water under the ledge was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack washed twenty blocks down a stream is fetched along it. */
    @GameTest(template = "empty")
    public static void aStackWashedTwentyBlocksOffIsFetched(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 24);
        for (int dx = 2; dx <= 20; dx++) put(h, feet.offset(dx, 0, 0), Blocks.WATER);
        var item = stack(h, feet.offset(20, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet.offset(20, 0, 0), 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 1200);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The washed-away stack was not taken: " + item.getItem());
        h.succeed();
    }

    /** A stack in a flowing stream is taken, not left to the current. */
    @GameTest(template = "empty")
    public static void aStackInAFlowingStreamIsTaken(GameTestHelper h) {
        CompanionEntity c = spawn(h);
        settle(c);
        BlockPos feet = yard(h, c, 10);
        for (int dx = 2; dx <= 6; dx++) put(h, feet.offset(dx, 0, 0), Blocks.WATER);
        var item = stack(h, feet.offset(5, 0, 0), new ItemStack(Items.DIAMOND, 2));
        place(h, c, feet.offset(5, 0, 0), 20, item);
        h.assertTrue(setOut(h, c), "He did not set out");
        walk(h, c, 600);
        h.assertTrue(item.isRemoved() || item.getItem().isEmpty(), "The stack in the stream was not taken: " + item.getItem());
        h.succeed();
    }
}

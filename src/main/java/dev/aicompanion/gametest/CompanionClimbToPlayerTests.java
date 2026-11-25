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

import static dev.aicompanion.gametest.CompanionMineArena.CENTER;
import static dev.aicompanion.gametest.CompanionMineArena.GROUND;
import static dev.aicompanion.gametest.CompanionMineArena.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineArena.count;
import static dev.aicompanion.gametest.CompanionMineArena.ended;
import static dev.aicompanion.gametest.CompanionMineArena.mountain;
import static dev.aicompanion.gametest.CompanionMineArena.report;
import static dev.aicompanion.gametest.CompanionMineArena.rig;

/**
 * Runs void-3 and come of seed 7 (07.11): Oleg stands in a pit or a basin walled on every side, the player waits higher up, and the walk to
 * him has no way at all; the hand-over and «ко мне» ended «Не могу к тебе пройти» where a player digs steps up out of the hole. Arena as in
 * the other mine tests; the pit is three by three and nine deep, its walls of andesite up to the turf and a ledge of it over his head (the self-rescue
 * cuts only stone, soil and deepslate, and jumps blocks up under his feet only while there is room to jump), the player stands on the surface ten blocks off its edge.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionClimbToPlayerTests {
    private static final int PIT_DEPTH = 9, ROOF_HEIGHT = 5, PLAYER_EAST = 11, SHALLOW = 2, DEEP = 3, FOLLOW_RANGE_SHORT = 8, FOLLOW_RANGE_PIT = 6,
            RAVINE_WEST = 12, RAVINE_EAST = 4, RAVINE_DEPTH = 5, PLAYER_FAR = 20, FOLLOW_RANGE_RAVINE = 10, PLATEAU = 4, PLATEAU_HEIGHT = 6, PLAYER_BELOW_EAST = 14, RIM_HOLE = 9;

    private CompanionClimbToPlayerTests() { }

    /** The diamond is thrown at the player's feet, on the surface, after the stairs out of the pit. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
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

    /**
     * The same hand-over with the player out of the range where «no way» can be told (seed 7, lava: ninety-seven blocks off the pit): the search
     * does not see him, so the failed walk is «застрял», not «no way», and the stairs must be cut all the same, the climb not ended by a «walk is possible» that is only the distance.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void cutsStairsUpToThePlayerFromAPitWhenTheWalkFails(GameTestHelper h) {
        CompanionEntity c = pit(h);
        c.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(FOLLOW_RANGE_SHORT);
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
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
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

    /**
     * The real «ко мне» of run come (seed 7): the mouth of his own mine, a hole two deep with its lip all round, and the player at the same height
     * as the floor, in a dip of his own beyond the lip with a step down to it. The self-rescue has nothing to climb to (the player is not higher)
     * and cannot cut the andesite; the stairs go up onto the lip, and the walk goes on from there.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void climbsOutOfAPitToAPlayerAtHisOwnHeight(GameTestHelper h) {
        mountain(h, false);
        for (int x = CENTER - 3; x <= CENTER + 3; x++) for (int z = CENTER - 3; z <= CENTER + 3; z++) for (int y = GROUND - SHALLOW; y <= GROUND; y++) {
            BlockPos wall = new BlockPos(x, y, z);
            if (h.getBlockState(wall).is(Blocks.STONE) || h.getBlockState(wall).is(Blocks.DIRT) || h.getBlockState(wall).is(Blocks.GRASS_BLOCK)) h.setBlock(wall, Blocks.ANDESITE);
        }
        for (int x = CENTER - 1; x <= CENTER + 1; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND - SHALLOW + 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        int dip = CENTER + PLAYER_EAST;
        for (int x = dip - 1; x <= dip + 1; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND - SHALLOW + 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int z = CENTER - 1; z <= CENTER + 1; z++) h.setBlock(new BlockPos(dip - 2, GROUND, z), Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos floor = h.absolutePos(new BlockPos(CENTER, GROUND - SHALLOW + 1, CENTER)), owner = h.absolutePos(new BlockPos(dip, GROUND - SHALLOW + 1, CENTER));
        c.teleportTo(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
        h.getLevel().getPlayerByUUID(c.getOwnerId()).teleportTo(owner.getX() + .5, owner.getY(), owner.getZ() + .5);
        h.assertTrue(c.startWork("come", "", 1).success(), "«Ко мне» not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success(), "He said «" + result.message() + "» in the hole | " + report(c));
            h.assertTrue(c.distanceToSqr(h.getLevel().getPlayerByUUID(c.getOwnerId())) <= 9, "He is not beside the player at " + c.blockPosition().toShortString() + " | " + report(c));
        });
    }

    /**
     * Run restart of seed 7 (16.11): with the diamond he came up his stairs into a narrow hole five deep under the sky, and the player stood
     * sixty blocks off at the entrance, a block lower than his feet. The stairs were cut only for a player no lower, so the hand-over ended
     * «Не смог к тебе подойти» five times and the goal «не получилось отдать алмазы». A hole three deep in andesite, the player in a dip of his
     * own a block lower beyond its lip, with steps down to it (two drops of two in a row are one fall of four, more than he takes), out of the
     * range where «no way» can be told: the stairs go up out of the hole, and the walk goes on from there.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void climbsOutOfAPitToAPlayerLowerThanHim(GameTestHelper h) {
        mountain(h, false);
        for (int x = CENTER - 3; x <= CENTER + 3; x++) for (int z = CENTER - 3; z <= CENTER + 3; z++) for (int y = GROUND - DEEP; y <= GROUND; y++) {
            BlockPos wall = new BlockPos(x, y, z);
            if (h.getBlockState(wall).is(Blocks.STONE) || h.getBlockState(wall).is(Blocks.DIRT) || h.getBlockState(wall).is(Blocks.GRASS_BLOCK)) h.setBlock(wall, Blocks.ANDESITE);
        }
        for (int x = CENTER - 1; x <= CENTER + 1; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND - DEEP + 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        int dip = CENTER + PLAYER_EAST;
        for (int x = dip - 1; x <= dip + 1; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND - DEEP; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND - 1; y <= GROUND; y++) h.setBlock(new BlockPos(dip - 2, y, z), Blocks.AIR);
        for (int z = CENTER - 1; z <= CENTER + 1; z++) h.setBlock(new BlockPos(dip - 3, GROUND, z), Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(FOLLOW_RANGE_PIT);
        BlockPos floor = h.absolutePos(new BlockPos(CENTER, GROUND - DEEP + 1, CENTER)), owner = h.absolutePos(new BlockPos(dip, GROUND - DEEP, CENTER));
        c.teleportTo(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
        h.getLevel().getPlayerByUUID(c.getOwnerId()).teleportTo(owner.getX() + .5, owner.getY(), owner.getZ() + .5);
        c.setItem(6, new ItemStack(Items.DIAMOND));
        h.assertTrue(c.startWork("give_items", "minecraft:diamond", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) == 0, "He said «" + result.message() + "» in the hole | " + report(c));
        });
    }

    /**
     * Run water of seed 7 (21.11): with the diamond he stood on the floor of a ravine six wide and fifteen long, walled on every side, the player
     * seventy-seven blocks off and a block lower. No climb began (the pit was looked for only six cells round him), and the hand-over ended
     * «Не смог к тебе подойти» five times. The same ravine in andesite, he a cell off its east end, the player in a valley a block lower beyond a
     * ramp, out of the range where «no way» can be told.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void climbsOutOfARavineToAFarPlayerLowerThanHim(GameTestHelper h) {
        mountain(h, false);
        for (int x = CENTER - RAVINE_WEST - 3; x <= CENTER + RAVINE_EAST + 3; x++) for (int z = CENTER - 5; z <= CENTER + 6; z++) for (int y = GROUND - RAVINE_DEPTH - 1; y <= GROUND; y++) {
            BlockPos wall = new BlockPos(x, y, z);
            if (h.getBlockState(wall).is(Blocks.STONE) || h.getBlockState(wall).is(Blocks.DIRT) || h.getBlockState(wall).is(Blocks.GRASS_BLOCK)) h.setBlock(wall, Blocks.ANDESITE);
        }
        for (int x = CENTER - RAVINE_WEST; x <= CENTER + RAVINE_EAST; x++) for (int z = CENTER - 2; z <= CENTER + 3; z++) for (int y = GROUND - RAVINE_DEPTH + 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        int ramp = CENTER + RAVINE_EAST + 4, valley = ramp + RAVINE_DEPTH + 1;
        for (int i = 0; i <= RAVINE_DEPTH; i++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND - i; y <= GROUND; y++) h.setBlock(new BlockPos(ramp + i, y, z), Blocks.AIR);
        for (int x = valley; x < CENTER * 2 - 1; x++) for (int z = 1; z < CENTER * 2 - 1; z++) for (int y = GROUND - RAVINE_DEPTH; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(FOLLOW_RANGE_RAVINE);
        BlockPos floor = h.absolutePos(new BlockPos(CENTER + RAVINE_EAST - 1, GROUND - RAVINE_DEPTH + 1, CENTER)), owner = h.absolutePos(new BlockPos(CENTER + PLAYER_FAR, GROUND - RAVINE_DEPTH, CENTER));
        c.teleportTo(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
        h.getLevel().getPlayerByUUID(c.getOwnerId()).teleportTo(owner.getX() + .5, owner.getY(), owner.getZ() + .5);
        c.setItem(6, new ItemStack(Items.DIAMOND));
        h.assertTrue(c.startWork("give_items", "minecraft:diamond", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) == 0, "He said «" + result.message() + "» in the ravine | " + report(c));
        });
    }

    /**
     * Review of the ravine: a place he cannot walk off is no pit when a drop leads off it. On a plateau of andesite nine by nine and six high, the
     * player on the ground east of it, a climb «out of the pit» stepped level towards him and laid a bridge of cobblestone into the air.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void laysNoBridgeOffAPlateauToAPlayerBelow(GameTestHelper h) {
        mountain(h, false);
        for (int x = CENTER - PLATEAU; x <= CENTER + PLATEAU; x++) for (int z = CENTER - PLATEAU; z <= CENTER + PLATEAU; z++) for (int y = GROUND + 1; y <= GROUND + PLATEAU_HEIGHT; y++) h.setBlock(new BlockPos(x, y, z), Blocks.ANDESITE);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos top = h.absolutePos(new BlockPos(CENTER, GROUND + PLATEAU_HEIGHT + 1, CENTER)), owner = h.absolutePos(new BlockPos(CENTER + PLAYER_BELOW_EAST, GROUND + 1, CENTER));
        c.teleportTo(top.getX() + .5, top.getY(), top.getZ() + .5);
        h.getLevel().getPlayerByUUID(c.getOwnerId()).teleportTo(owner.getX() + .5, owner.getY(), owner.getZ() + .5);
        c.setItem(6, new ItemStack(Items.DIAMOND));
        h.assertTrue(c.startWork("give_items", "minecraft:diamond", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            for (int x = CENTER + PLATEAU + 1; x < CENTER + PLAYER_BELOW_EAST; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND + 2; y <= GROUND + PLATEAU_HEIGHT + 2; y++)
                h.assertTrue(h.getBlockState(new BlockPos(x, y, z)).isAir(), "A block laid in the air at " + x + "," + y + "," + z + " off the plateau; he said «" + result.message() + "» | " + report(c));
        });
    }

    /**
     * Review of the climb: cutting up to a player on the rim, a step east cut the block he stands on out from under him.
     * A hole nine deep in andesite with a roof over its lower part (no jumping and pillaring out), the player on the grass two blocks off its lip: the column of blocks under his feet is left whole, and the climb still gets out.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void leavesTheBlockThePlayerStandsOnWhenCuttingUpToHim(GameTestHelper h) {
        CompanionEntity c = rimHole(h, 3, 0);
        BlockPos owner = h.absolutePos(new BlockPos(CENTER + 3, GROUND + 1, CENTER));
        long id = c.activeWorkId();
        h.onEachTick(() -> {
            for (int y = 1; y <= RIM_HOLE - 3; y++)
                h.assertTrue(!h.getLevel().getBlockState(owner.below(y)).isAir(), "The column under the player was dug out at " + owner.below(y).toShortString() + " | " + report(c));
        });
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) == 0, "He said «" + result.message() + "» in the hole | " + report(c));
        });
    }

    /** The same with the player in the air (a jump, a fall) over the rim: the blocks he will land on are as safe as the ones he stands on. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void leavesTheGroundUnderAPlayerInTheAirWhenCuttingUpToHim(GameTestHelper h) {
        CompanionEntity c = rimHole(h, 3, 3);
        BlockPos ground = h.absolutePos(new BlockPos(CENTER + 3, GROUND, CENTER));
        long id = c.activeWorkId();
        h.onEachTick(() -> {
            for (int y = 0; y <= RIM_HOLE - 3; y++)
                h.assertTrue(!h.getLevel().getBlockState(ground.below(y)).isAir(), "The ground under the player in the air was dug out at " + ground.below(y).toShortString() + " | " + report(c));
        });
        h.succeedWhen(() -> ended(h, c, id));
    }

    /**
     * A player who walks onto the line of the stairs while they are being cut keeps his floor: the cells of the step under way were free when it
     * was planned. The player waits far off on the rim; as soon as the stairs have cut into the wall he steps onto the grass over the cell
     * just cut, where the rest of its step is still to be cut, and every block of the column under him stays.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void leavesTheFloorOfAPlayerWhoWalksOntoTheLineOfTheStairs(GameTestHelper h) {
        CompanionEntity c = rimHole(h, 8, 0);
        long id = c.activeWorkId();
        var player = h.getLevel().getPlayerByUUID(c.getOwnerId());
        java.util.List<BlockPos> watched = new java.util.ArrayList<>();
        h.onEachTick(() -> {
            if (watched.isEmpty()) {
                int frontX = Integer.MIN_VALUE, frontZ = CENTER;
                for (int x = CENTER + 2; x <= CENTER + 8; x++) for (int z = CENTER - 4; z <= CENTER + 4; z++) for (int y = GROUND - RIM_HOLE + 1; y < GROUND; y++)
                    if (h.getBlockState(new BlockPos(x, y, z)).isAir() && x > frontX) { frontX = x; frontZ = z; }
                if (frontX == Integer.MIN_VALUE) return;
                BlockPos on = h.absolutePos(new BlockPos(frontX, GROUND + 1, frontZ));
                player.teleportTo(on.getX() + .5, on.getY(), on.getZ() + .5);
                for (int y = GROUND - RIM_HOLE + 1; y <= GROUND; y++) {
                    BlockPos cell = new BlockPos(frontX, y, frontZ);
                    if (!h.getBlockState(cell).isAir()) watched.add(cell);
                }
                return;
            }
            for (BlockPos cell : watched) h.assertTrue(!h.getBlockState(cell).isAir(), "A block of the column under the player who stepped onto the line was dug out at " + cell.toShortString() + " | " + report(c));
        });
        h.succeedWhen(() -> {
            StringBuilder dbg = new StringBuilder();
            for (int x = CENTER - 4; x <= CENTER + 9; x++) { for (int y = GROUND - RIM_HOLE + 1; y <= GROUND; y++) dbg.append(h.getBlockState(new BlockPos(x, y, CENTER)).isAir() ? '_' : '#'); dbg.append('/'); }
            h.assertTrue(!watched.isEmpty(), "The stairs never came near the rim " + dbg + " he at " + h.relativePos(c.blockPosition()).toShortString() + " player at " + h.relativePos(player.blockPosition()).toShortString() + " | " + report(c));
            ended(h, c, id);
        });
    }

    /**
     * Oleg on the floor of a hole nine deep in andesite with a roof over its lower part (no jumping and pillaring out), the player on the grass
     * {@code east} blocks off its middle and {@code lift} blocks above it, a diamond to give.
     */
    private static CompanionEntity rimHole(GameTestHelper h, int east, int lift) {
        mountain(h, false);
        for (int x = CENTER - 3; x <= CENTER + 9; x++) for (int z = CENTER - 4; z <= CENTER + 4; z++) for (int y = GROUND - RIM_HOLE; y < GROUND; y++) {
            BlockPos wall = new BlockPos(x, y, z);
            if (h.getBlockState(wall).is(Blocks.STONE) || h.getBlockState(wall).is(Blocks.DIRT)) h.setBlock(wall, Blocks.ANDESITE);
        }
        for (int x = CENTER - 1; x <= CENTER + 1; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND - RIM_HOLE + 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = CENTER - 1; x <= CENTER + 1; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) h.setBlock(new BlockPos(x, GROUND - RIM_HOLE + 1 + ROOF_HEIGHT, z), Blocks.ANDESITE);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos floor = h.absolutePos(new BlockPos(CENTER, GROUND - RIM_HOLE + 1, CENTER)), owner = h.absolutePos(new BlockPos(CENTER + east, GROUND + 1 + lift, CENTER));
        c.teleportTo(floor.getX() + .5, floor.getY(), floor.getZ() + .5);
        h.getLevel().getPlayerByUUID(c.getOwnerId()).teleportTo(owner.getX() + .5, owner.getY(), owner.getZ() + .5);
        c.setItem(6, new ItemStack(Items.DIAMOND));
        h.assertTrue(c.startWork("give_items", "minecraft:diamond", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        return c;
    }

    /**
     * Review of the climb: a pocket sealed in a cliff face is no pit. A cell of three by three by three at the top of a mass of andesite nine
     * high, the player on the ground at its foot, lower than he is: no stairs out through the face and no bridge of cobblestone over the air, the hand-over ends
     * with «не могу пройти».
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void cutsNoStairsOutOfARoofedPocketToAPlayerBelow(GameTestHelper h) {
        mountain(h, false);
        for (int x = CENTER - 4; x <= CENTER + 4; x++) for (int z = CENTER - 4; z <= CENTER + 4; z++) for (int y = GROUND + 1; y <= GROUND + 9; y++) h.setBlock(new BlockPos(x, y, z), Blocks.ANDESITE);
        for (int x = CENTER - 1; x <= CENTER + 1; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND + 4; y <= GROUND + 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos in = h.absolutePos(new BlockPos(CENTER, GROUND + 4, CENTER)), owner = h.absolutePos(new BlockPos(CENTER + 7, GROUND + 1, CENTER));
        c.teleportTo(in.getX() + .5, in.getY(), in.getZ() + .5);
        h.getLevel().getPlayerByUUID(c.getOwnerId()).teleportTo(owner.getX() + .5, owner.getY(), owner.getZ() + .5);
        c.setItem(6, new ItemStack(Items.DIAMOND));
        h.assertTrue(c.startWork("give_items", "minecraft:diamond", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(!result.success(), "He gave it from a sealed pocket: «" + result.message() + "» | " + report(c));
            for (int x = CENTER - 6; x <= CENTER + 10; x++) for (int z = CENTER - 6; z <= CENTER + 6; z++) for (int y = GROUND + 1; y <= GROUND + 12; y++) {
                boolean mass = Math.abs(x - CENTER) <= 4 && Math.abs(z - CENTER) <= 4 && y <= GROUND + 9, pocket = Math.abs(x - CENTER) <= 1 && Math.abs(z - CENTER) <= 1 && y >= GROUND + 4 && y <= GROUND + 6;
                if (pocket) continue;
                h.assertTrue(mass != h.getBlockState(new BlockPos(x, y, z)).isAir(), "The cliff was cut or built on at " + x + "," + y + "," + z + "; he said «" + result.message() + "» | " + report(c));
            }
        });
    }

    /**
     * Review of the climb: two sealed chambers in the mountain, he in one, the player in the other at his own height with four blocks of rock
     * between: no stairs are cut through the rock to him, and nothing round the chambers is dug.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void digsNoWallToAPlayerAtHisOwnHeightBehindRock(GameTestHelper h) {
        mountain(h, false);
        int floorY = GROUND - 8;
        // Andesite all round: the self-rescue cuts only stone, soil and deepslate, so what is dug here is dug by the stairs.
        for (int x = CENTER - 5; x <= CENTER + 10; x++) for (int z = CENTER - 3; z <= CENTER + 3; z++) for (int y = floorY - 1; y < GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.ANDESITE);
        for (int x = CENTER - 3; x <= CENTER; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = floorY; y <= floorY + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = CENTER + 5; x <= CENTER + 8; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = floorY; y <= floorY + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos in = h.absolutePos(new BlockPos(CENTER - 1, floorY, CENTER)), owner = h.absolutePos(new BlockPos(CENTER + 6, floorY, CENTER));
        c.teleportTo(in.getX() + .5, in.getY(), in.getZ() + .5);
        h.getLevel().getPlayerByUUID(c.getOwnerId()).teleportTo(owner.getX() + .5, owner.getY(), owner.getZ() + .5);
        c.setItem(6, new ItemStack(Items.DIAMOND));
        h.assertTrue(c.startWork("give_items", "minecraft:diamond", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(!result.success(), "He gave it through the rock: «" + result.message() + "» | " + report(c));
            for (int x = CENTER - 5; x <= CENTER + 10; x++) for (int z = CENTER - 3; z <= CENTER + 3; z++) for (int y = floorY - 1; y <= GROUND + 1; y++) {
                boolean chamber = Math.abs(z - CENTER) <= 1 && y >= floorY && y <= floorY + 2 && (x >= CENTER - 3 && x <= CENTER || x >= CENTER + 5 && x <= CENTER + 8);
                if (y >= GROUND + 1 || chamber) continue;
                h.assertTrue(!h.getBlockState(new BlockPos(x, y, z)).isAir(), "Rock was dug at " + x + "," + y + "," + z + "; he said «" + result.message() + "» | " + report(c));
            }
        });
    }

    /**
     * Review of the climb: the gift is held back by number, not by kind. He has two stone pickaxes (one in the hand, one in the bag) and no other
     * tool, and is asked for one: he digs with the other, and the one is thrown.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void digsWithTheSecondPickaxeWhenGivingOneOfTwo(GameTestHelper h) {
        CompanionEntity c = pit(h);
        c.setItem(1, ItemStack.EMPTY);
        h.assertTrue(c.startWork("give_items", "minecraft:stone_pickaxe", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && thrown(h, c, Items.STONE_PICKAXE) >= 1, "He said «" + result.message() + "» | " + report(c));
        });
    }

    /** A pickaxe asked for is not worn down on the way: with no other he has nothing to cut with, and says so. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void doesNotDigWithThePickaxeHeIsGiving(GameTestHelper h) {
        CompanionEntity c = pit(h);
        for (int slot = 0; slot < 6; slot++) c.setItem(slot, ItemStack.EMPTY);
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.COBBLESTONE, 16));
        h.assertTrue(c.startWork("give_items", "minecraft:diamond_pickaxe", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(!result.success() && result.message().contains("нечем копать"), "He said «" + result.message() + "» | " + report(c));
            for (int slot = 0; slot < c.getContainerSize(); slot++)
                h.assertTrue(!c.getItem(slot).is(Items.DIAMOND_PICKAXE) || c.getItem(slot).getDamageValue() == 0, "The pickaxe he was giving is worn | " + report(c));
        });
    }

    /** The stone held back mid-column falls where it was cut when the order is stopped, and no more is held. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void letsTheHeldStoneFallWhenTheOrderIsStopped(GameTestHelper h) {
        CompanionEntity c = pit(h);
        c.setItem(4, new ItemStack(Items.COBBLESTONE, 8));
        c.setItem(5, ItemStack.EMPTY);
        c.setItem(6, new ItemStack(Items.DIAMOND));
        h.assertTrue(c.startWork("give_items", "minecraft:diamond", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        int[] held = {0, 0, 0}; // the stone held when he was stopped, the cobblestone in his bag then, the tick
        h.onEachTick(() -> {
            if (held[0] > 0) return;
            var tag = new net.minecraft.nbt.CompoundTag();
            c.saveWithoutId(tag);
            if (!tag.contains(dev.aicompanion.entity.CompanionMineSite.HELD_KEY)) return;
            for (var entry : tag.getList(dev.aicompanion.entity.CompanionMineSite.HELD_KEY, 10))
                for (var drop : ((net.minecraft.nbt.CompoundTag) entry).getList("Drops", 10)) held[0] += ((net.minecraft.nbt.CompoundTag) drop).getInt("Count");
            held[1] = count(c, Items.ANDESITE);
            held[2] = (int) h.getTick();
            c.setFollowing(false);
        });
        h.succeedWhen(() -> {
            h.assertTrue(held[0] > 0 && h.getTick() > held[2] + 60, "No stone was held back, or not yet long enough | " + report(c));
            var tag = new net.minecraft.nbt.CompoundTag();
            c.saveWithoutId(tag);
            h.assertTrue(!tag.contains(dev.aicompanion.entity.CompanionMineSite.HELD_KEY), "The stone is still held after the order was stopped | " + report(c));
            int lying = 0;
            for (var item : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, c.getBoundingBox().inflate(12))) if (item.getItem().is(Items.ANDESITE)) lying += item.getItem().getCount();
            h.assertTrue(lying == held[0], held[0] + " stones were held, " + lying + " lie where they were cut; bag " + held[1] + " then, " + count(c, Items.ANDESITE) + " now | " + report(c));
            h.assertTrue(count(c, Items.ANDESITE) == held[1], "He picked the stone up again after the stop: " + held[1] + " in the bag then, " + count(c, Items.ANDESITE) + " now | " + report(c));
        });
    }

    /** An ore the pickaxe in the bag cannot take shuts that side of the stairs, not the whole climb: he is in a well one block wide whose east wall is diamond ore (the first step east is in it), and he has only a stone pickaxe. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT, skyAccess = true)
    public static void goesRoundAnOreHeCannotMine(GameTestHelper h) {
        CompanionEntity c = pit(h);
        // A well one block wide: every first step goes into the wall, and the east one into diamond ore.
        for (int x = CENTER - 1; x <= CENTER + 1; x++) for (int z = CENTER - 1; z <= CENTER + 1; z++) for (int y = GROUND - PIT_DEPTH + 1; y <= GROUND; y++)
            if (x != CENTER || z != CENTER) h.setBlock(new BlockPos(x, y, z), x == CENTER + 1 && z == CENTER && y < GROUND ? Blocks.DIAMOND_ORE : Blocks.ANDESITE);
        c.setItem(1, ItemStack.EMPTY);
        c.setItem(6, new ItemStack(Items.DIAMOND));
        h.assertTrue(c.startWork("give_items", "minecraft:diamond", 1).success(), "Hand-over not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) == 0, "He said «" + result.message() + "» at the ore | " + report(c));
        });
    }

    /** What reached the player of an item: in his bag, or lying thrown at his feet. */
    private static int thrown(GameTestHelper h, CompanionEntity c, net.minecraft.world.item.Item item) {
        var player = h.getLevel().getPlayerByUUID(c.getOwnerId());
        int count = player.getInventory().countItem(item);
        for (var entity : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(16))) if (entity.getItem().is(item)) count += entity.getItem().getCount();
        return count;
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

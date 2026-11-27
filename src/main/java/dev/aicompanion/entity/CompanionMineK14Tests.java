package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The deaths and the dead ends of the mine run K14: a way out of a fuse that ends beside lava, a body in lava with nothing that takes him out,
 * and the plug that hides under another one; and, from later runs, the stairs up out of a lake and the wear kept for a pit
 * under his old stairs. The tests live beside the package-private classes they call.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK14Tests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, TIMEOUT = 1500, IN_LAVA_TICKS = 60;

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
        int length = escape.plan(h.getLevel(), List.of(creeper), creeper);
        BlockPos goal = escape.goal();
        h.assertTrue(goal != null && !lavaBeside(h.getLevel(), goal),
                "The way out of the fuse ends at " + rel(h, goal) + ", beside lava (length " + length + ")");
        h.succeed();
    }

    /** In the middle of a step down the stairs he is put onto the edge of a 3 by 3 pool of lava far from the entrance, a step from the bank: a minute on, he is alive and out of it. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void getsOutOfLavaInTheMiddleOfAMineStep(GameTestHelper h) {
        mountain(h);
        for (int x = 4; x <= 6; x++) for (int z = 4; z <= 6; z++) for (int y = GROUND - 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.LAVA);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(c.getX() - 3, c.getY(), c.getZ() - 3);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 16), new ItemStack(Items.COBBLESTONE, 64)};
        for (int slot = 0; slot < bag.length; slot++) c.setItem(slot, bag[slot]);
        h.assertTrue(c.startWork("dig_for_diamonds", "", 1).success(), "Diamond mine not accepted: " + c.getLastTaskResult());

        BlockPos pool = h.absolutePos(new BlockPos(4, GROUND, 5));
        long[] placedAt = {-1};
        h.onEachTick(() -> {
            long now = h.getTick();
            if (!c.isAlive()) h.fail(placedAt[0] < 0 ? "Died before the lava" : "Burned to death " + (now - placedAt[0]) + " ticks after he was put into the lava | " + c.getLastTaskResult());
            if (placedAt[0] < 0) {
                if (c.mineSite().stands().size() < 2) return;
                c.moveTo(pool.getX() + .5, pool.getY(), pool.getZ() + .5);
                c.getNavigation().stop();
                placedAt[0] = now;
                return;
            }
            if (now - placedAt[0] < IN_LAVA_TICKS) return;
            h.assertTrue(!c.isInLava(), "Still in the lava " + IN_LAVA_TICKS + " ticks on, hp " + c.getHealth() + " | " + c.getLastTaskResult());
            h.succeed();
        });
    }

    /**
     * A step down onto a half block under a plug, with a plug over the head of the step: the upper one is the one in sight and comes out
     * first. The geometry is made up for the probe; the bridge in the mine that showed it laid two plugs one over the other.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 100)
    public static void thePlugOfTheHeadLevelComesOutFirstOnAStepDown(GameTestHelper h) {
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) for (int y = 0; y <= 8; y++) {
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : y <= 2 ? Blocks.STONE : Blocks.AIR);
        }
        BlockPos stand = h.absolutePos(new BlockPos(5, 3, 5)), lower = h.absolutePos(new BlockPos(6, 2, 5)), upper = h.absolutePos(new BlockPos(6, 4, 5));
        h.setBlock(new BlockPos(6, 2, 5), Blocks.STONE_SLAB);
        h.setBlock(new BlockPos(6, 4, 5), Blocks.COBBLESTONE);
        CompanionMineSite mine = new CompanionMineSite();
        mine.start(stand, Direction.NORTH);
        mine.sealed(lower);
        mine.sealed(upper);

        var way = CompanionExpeditionPlugs.way(h.getLevel(), mine, stand, lower);
        h.assertTrue(way != null && way.walk().equals(List.of(stand)), "No walk of the stand alone to the step: " + way);
        h.assertTrue(upper.equals(way.cell()), "The plug to take out is " + rel(h, way == null ? null : way.cell()) + ", not the upper one at " + rel(h, upper));
        h.succeed();
    }

    /**
     * Run descent of seed 7 (16.11): with the diamond, the stairs up out of a lake at the bottom of a pit were planned while he bobbed a hair
     * under the top of the water; the cell under it was taken for his stand, every side of it was water, and the climb said «путь наверх
     * закрыт» until a higher bob. A pool three deep, its top three blocks under the turf: from the same bob the first step is under way.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 100)
    public static void theStairsUpOutOfAPoolStartAtTheTopOfTheWater(GameTestHelper h) {
        mountain(h);
        for (int x = CENTER - 2; x <= CENTER + 2; x++) for (int z = CENTER - 2; z <= CENTER + 2; z++) for (int y = GROUND - 4; y <= GROUND; y++) {
            h.setBlock(new BlockPos(x, y, z), y <= GROUND - 2 ? Blocks.WATER : Blocks.AIR);
        }
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        BlockPos top = h.absolutePos(new BlockPos(CENTER, GROUND - 2, CENTER));
        c.moveTo(top.getX() + .5, top.getY() - .05, top.getZ() + .5);
        var stairs = new CompanionExpeditionStairsUp(c.work, new CompanionExpeditionDig(c.work), false, null);
        stairs.begin(h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER + 10)));
        var result = stairs.tick(h.getLevel());
        h.assertTrue(result == CompanionExpeditionStairsUp.Result.WORKING, "The stairs up out of the pool: " + result + " «" + stairs.why() + "»");
        h.succeed();
    }

    /**
     * Run retreat of seed 7 (series d, 21.11): a mine started again in a pit four blocks under the foot of his old stairs kept the wear of a staircase up to
     * the surface, a hundred blocks. The way out of it is a short staircase up to that foot: four steps up toward it, its wear the reserve. Old stairs far off,
     * or lower than the pit (the staircase only climbs), leave the staircase to the surface. Twelve stands down from forty: the foot at 28. A tunnel off the
     * foot is the first of the old way kept by the restart, from its end twenty blocks off: the foot is still the cell cut up to. A pit started again under
     * the stairs of a pit has the gap of the first pit past the foot it is near: that gap is crossed as before, so the reserve is the staircase to the surface.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 100)
    public static void aPitJustUnderHisOldStairsKeepsTheWearOfAShortStaircaseUpToThem(GameTestHelper h) {
        BlockPos origin = h.absolutePos(new BlockPos(CENTER, 40, CENTER));
        java.util.function.UnaryOperator<BlockPos> under = foot -> foot.below(4).north(4);
        int near = pitWear(oldStairs(origin, 0), under), far = pitWear(oldStairs(origin, 0), foot -> foot.below(4).north(25));
        int higher = pitWear(oldStairs(origin, 0), foot -> foot.above(4).north(4)), tunnel = pitWear(oldStairs(origin, 20), under);
        CompanionMineSite pits = oldStairs(origin, 0);
        pits.restart(under.apply(pits.foot()), Direction.NORTH);
        pits.buried(true);
        descend(pits, pits.entrance(), 8);
        int nested = pitWear(pits, under);
        h.assertTrue(near == 3 * 4, "The wear kept for a pit four under the foot of his old stairs and four across: " + near + ", not four steps up (12)");
        h.assertTrue(far == 3 * (40 - 24), "The wear kept for a pit far from his old stairs: " + far + ", not the staircase to the surface (48)");
        h.assertTrue(higher == 3 * (40 - 32), "The wear kept for a pit over the foot of his old stairs: " + higher + ", not the staircase to the surface (24)");
        h.assertTrue(tunnel == 3 * 4, "The wear kept for a pit under the foot of old stairs with a tunnel off it: " + tunnel + ", not four steps up to the foot (12)");
        h.assertTrue(nested == 3 * (40 - 12), "The wear kept for a pit under the stairs of another pit: " + nested + ", not the staircase to the surface (84)");
        h.succeed();
    }

    /** A mine of twelve stands down north from {@code origin}, and a tunnel of {@code tunnel} cells west off its foot. */
    private static CompanionMineSite oldStairs(BlockPos origin, int tunnel) {
        CompanionMineSite mine = new CompanionMineSite();
        mine.start(origin, Direction.NORTH);
        descend(mine, origin, 12);
        if (tunnel == 0) return mine;
        mine.openTunnel(Direction.WEST);
        for (int k = 0; k < tunnel; k++) mine.extendTunnel();
        return mine;
    }

    private static void descend(CompanionMineSite mine, BlockPos from, int steps) {
        List<BlockPos> stands = CompanionShaftPlan.stands(from, Direction.NORTH, steps);
        for (BlockPos stand : stands.subList(1, stands.size())) mine.reached(stand);
    }

    /** The wear kept for the way out of the mine started again at {@code pit} of its foot. */
    private static int pitWear(CompanionMineSite mine, java.util.function.UnaryOperator<BlockPos> pit) {
        mine.restart(pit.apply(mine.foot()), Direction.NORTH);
        mine.buried(true);
        return CompanionExpeditionClimb.cutWear(mine);
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

    /** Bedrock floor and walls, stone, dirt, grass: a mountain with no ore. */
    private static void mountain(GameTestHelper h) {
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            Block block = y == 0 || edge ? Blocks.BEDROCK : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            h.setBlock(new BlockPos(x, y, z), block);
        }
    }
}

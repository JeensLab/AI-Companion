package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * Runs u1d/void-4 and u1a/void-4: the creeper he bobbed up into sight of and down out of it for 50000 ticks, its fuse going and coming with his
 * every bob, and the climb that gave up the rock with an iron pickaxe in the bag because its wear was kept for ore.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK17Tests {
    /** The cave of u1d/void-4, at the height of the arena: the water hole, one block of wall on its rim and the pit the creeper stands in. */
    private static final int HOLE_X = 24, HOLE_Y = 14, HOLE_Z = 21, CAVE_LOW_X = 20, CAVE_HIGH_X = 30, CAVE_LOW_Z = 17, CAVE_HIGH_Z = 31, CAVE_HEIGHT = 4, PIT_FROM_Z = 24;
    private static final double HE_Z = .7, CREEPER_Z = 24.2;
    /** A fuse lasts 30 ticks: far more than that, and a good walk to the creeper besides, is enough for it to be over one way or the other. */
    private static final int SETTLED_TICKS = 1200;
    private static final int IRON_WORN = 191;

    private CompanionMineK17Tests() { }

    /**
     * u1d/void-4: he is in a hole of water one deep, the creeper within fuse range in a pit behind a block on the rim. He bobs: at the top of a bob
     * each sees the other and the fuse lights, at the bottom neither does and it goes out. It never blows, it stands (its fuse goal holds its legs), and
     * he stood out of sight in the hole «отхожу, потом добью» with his sword until the time ran out. He is to deal with it, not to wait.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aFuseThatComesAndGoesWithHisBobbingDoesNotHoldHimForEver(GameTestHelper h) {
        mountain(h, false);
        for (int x = CAVE_LOW_X; x <= CAVE_HIGH_X; x++) for (int z = CAVE_LOW_Z; z <= CAVE_HIGH_Z; z++) {
            for (int y = HOLE_Y + 1; y <= HOLE_Y + CAVE_HEIGHT; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            if (z >= PIT_FROM_Z && Math.abs(x - HOLE_X) <= 1) h.setBlock(new BlockPos(x, HOLE_Y, z), Blocks.AIR);
        }
        h.setBlock(new BlockPos(HOLE_X, HOLE_Y, HOLE_Z), Blocks.WATER);
        h.setBlock(new BlockPos(HOLE_X, HOLE_Y + 1, HOLE_Z + 1), Blocks.COBBLESTONE);
        for (int up = 1; up <= 2; up++) for (BlockPos side : new BlockPos[]{new BlockPos(HOLE_X + 1, HOLE_Y + up, HOLE_Z), new BlockPos(HOLE_X - 1, HOLE_Y + up, HOLE_Z), new BlockPos(HOLE_X, HOLE_Y + up, HOLE_Z - 1)}) h.setBlock(side, Blocks.COBBLESTONE);
        h.setBlock(new BlockPos(HOLE_X, HOLE_Y + 3, HOLE_Z), Blocks.COBBLESTONE);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItem(5, new ItemStack(Items.STONE_SWORD));
        BlockPos hole = h.absolutePos(new BlockPos(HOLE_X, HOLE_Y, HOLE_Z));
        Vec3 at = h.absoluteVec(new Vec3(HOLE_X + .5, HOLE_Y + .5, HOLE_Z + HE_Z));
        c.teleportTo(at.x, at.y, at.z);
        Creeper creeper = h.spawn(EntityType.CREEPER, new Vec3(HOLE_X + .5, HOLE_Y, CREEPER_Z));
        creeper.setPersistenceRequired();
        creeper.setTarget(c);
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = "creeper " + (creeper.isAlive() ? "alive, fuse " + creeper.getSwellDir() + ", " + Math.round(creeper.distanceTo(c) * 10) / 10.0 + " off" : "gone")
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", hole " + rel(h, hole).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died: " + trace);
            if (!creeper.isAlive()) { h.succeed(); return; }
            h.assertTrue(h.getTick() - begun < SETTLED_TICKS, "The creeper still holds him: " + trace);
        });
    }

    /** u1a/void-4: the only pickaxe left is the iron one, its wear below what is kept for ore: the way out is worth more than the ore, and rock is cut with it. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void cutsHisWayOutWithTheWornIronPickaxeWhenItIsTheLastHeHas(GameTestHelper h) {
        CompanionEntity c = CompanionMineClimbOutTests.roofedPit(h, false);
        c.getItem(1).setDamageValue(IRON_WORN);
        BlockPos stand = c.mineSite().stands().get(0);
        int surface = h.absolutePos(new BlockPos(0, GROUND - 1, 0)).getY();
        CompanionMineClimbOutTests.climb(h, c, stand, result -> h.assertTrue(result.success() && c.getBlockY() >= surface,
                "He said «" + result.message() + "» at height " + c.getBlockY() + ", below the surface at " + surface + " | " + report(c)));
    }
}

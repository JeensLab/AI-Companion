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
 * Runs u1d/void-4 and u1a/void-4: the creeper he bobbed into sight of and out of it for 50000 ticks, its fuse going and coming with his
 * every bob, and the climb that gave up the rock with an iron pickaxe in the bag because its wear was kept for ore.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK17Tests {
    /** The cave of u1d/void-4 at the height of the arena: his cell in the plugs, the slit at his eye on its south side, and the creeper beyond it on a ledge. */
    private static final int NICHE_X = 24, NICHE_Y = 14, NICHE_Z = 21, CAVE_LOW_X = 20, CAVE_HIGH_X = 30, CAVE_LOW_Z = 18, CAVE_HIGH_Z = 30, CAVE_HEIGHT = 5, NICHE_ROOF = 3;
    private static final double CREEPER_OFF = 3.1;
    /** The beat of his bob and the ticks of it they saw each other, too few for the fuse to burn down. */
    private static final int BEAT_TICKS = 20, SIGHT_TICKS = 8;
    /** A fuse lasts 30 ticks: well past that, with some beats to strike in, it is over one way or the other. */
    private static final int SETTLED_TICKS = 800;
    private static final int IRON_WORN = 191;

    private CompanionMineK17Tests() { }

    /**
     * u1d/void-4: he is walled in by his own plugs, the creeper within fuse range outside, a slit between them that he bobbed up to in the water. Each saw the other only at
     * the top of a bob: the fuse lit, and went out at the bottom, it never blew and it stood (its fuse goal holds its legs), and he stood «отхожу, потом добью» with his sword
     * until the time ran out, the creeper in reach. The bob is here a slab under him, which lifts his eye over what the floor of his cell does not see past, and a slit that
     * opens and shuts on the beat of the bob, with the fuse lit in the tick it opens, as he saw it in the run. He has no way out of here, so an order that ends counts as well
     * as a dead creeper, once he has struck it.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aFuseThatComesAndGoesWithSightOfHimDoesNotHoldHimForEver(GameTestHelper h) {
        mountain(h, false);
        for (int x = CAVE_LOW_X; x <= CAVE_HIGH_X; x++) for (int z = CAVE_LOW_Z; z <= CAVE_HIGH_Z; z++) for (int y = NICHE_Y; y < NICHE_Y + CAVE_HEIGHT; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = NICHE_X - 1; x <= NICHE_X + 1; x++) {
            for (int z = NICHE_Z - 1; z <= NICHE_Z + 1; z++) for (int y = NICHE_Y; y <= NICHE_Y + NICHE_ROOF; y++) {
                if (x != NICHE_X || z != NICHE_Z || y == NICHE_Y + NICHE_ROOF) h.setBlock(new BlockPos(x, y, z), Blocks.COBBLESTONE);
            }
            for (int z = NICHE_Z + 2; z <= NICHE_Z + 4; z++) h.setBlock(new BlockPos(x, NICHE_Y, z), Blocks.COBBLESTONE);
        }
        h.setBlock(new BlockPos(NICHE_X, NICHE_Y, NICHE_Z), Blocks.SMOOTH_STONE_SLAB);
        BlockPos slit = new BlockPos(NICHE_X, NICHE_Y + 2, NICHE_Z + 1);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        c.setItem(5, new ItemStack(Items.STONE_SWORD));
        BlockPos niche = h.absolutePos(new BlockPos(NICHE_X, NICHE_Y, NICHE_Z));
        c.teleportTo(niche.getX() + .5, niche.getY() + .5, niche.getZ() + .5);
        Creeper creeper = h.spawn(EntityType.CREEPER, new Vec3(NICHE_X + .5, NICHE_Y + 1, NICHE_Z + CREEPER_OFF));
        creeper.setPersistenceRequired();
        start(h, c, 1);
        long begun = h.getTick();
        h.onEachTick(() -> {
            long beat = (h.getTick() - begun) % BEAT_TICKS;
            if (beat == 0 && creeper.isAlive()) {
                h.setBlock(slit, Blocks.AIR);
                creeper.setTarget(c);
                creeper.setSwellDir(1);
            } else if (beat == SIGHT_TICKS) h.setBlock(slit, Blocks.COBBLESTONE);
            String trace = "creeper " + (creeper.isAlive() ? "alive, fuse " + creeper.getSwellDir() + ", " + Math.round(creeper.distanceTo(c) * 10) / 10.0 + " off" : "gone")
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died: " + trace);
            boolean struck = creeper.getHealth() < creeper.getMaxHealth(), orderOver = !c.isWorking() && !c.isGatheringTask();
            if (!creeper.isAlive() || struck && orderOver) { h.succeed(); return; }
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

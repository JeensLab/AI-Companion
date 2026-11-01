package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
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
    private static final int CHAMBER_REACH = 13, CHAMBER_HEIGHT = 9, PILLAR_AT = 6, PILLAR_HEIGHT = 5, OWN_STAND = 3;

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
}

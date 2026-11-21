package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;
import static dev.aicompanion.gametest.CompanionMineFightTests.armed;
import static dev.aicompanion.gametest.CompanionMineFightTests.chamber;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;
import static dev.aicompanion.gametest.CompanionMineFightTests.skeleton;
import static dev.aicompanion.gametest.CompanionMineFightTests.zombie;

/** What keeps him alive in a mine fight: the meal that does not wait for the foe to die, the foe struck that hurts most, and no fist against a monster. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineSurvivalTests {
    private static final int FAR_STAND = 10, MEAL_WINDOW = 40, BREAD = 8, STRIKE_TICKS = 60, UNARMED_TICKS = 100;
    private static final float WOUNDED = 8, WEAK_ZOMBIE_BLOW = 1;
    private static final double SAFE_GAP = 4;

    private CompanionMineSurvivalTests() { }

    /** Eight health, a zombie on him, bread in the bag: the meal is eaten under the zombie's blows, not after it (a loaf is five health in 32 ticks, a zombie dies in 65). */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void woundedInAFightHeEatsWithTheZombieOnHim(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(FAR_STAND, 0, 0));
        c.setHealth(WOUNDED);
        Zombie zombie = zombie(h, foot.offset(FAR_STAND + 1, 0, 0));
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(WEAK_ZOMBIE_BLOW);
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            String trace = " | bread " + count(c, Items.BREAD) + ", zombie " + (zombie.isAlive() ? "health " + zombie.getHealth() : "dead") + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (h.getTick() - begun < MEAL_WINDOW) return;
            h.assertTrue(count(c, Items.BREAD) < BREAD, "No bread was eaten" + trace);
            h.assertTrue(c.getHealth() > WOUNDED, "The meal did not bring him above where he began" + trace);
            h.succeed();
        });
    }

    /** A zombie and a skeleton are both within his blade, the skeleton nearer and the one he is after: the zombie, which strikes harder for its health, is struck first. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void withAZombieAndASkeletonOnHimTheZombieIsStruckFirst(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(FAR_STAND, 0, 0));
        Skeleton skeleton = skeleton(h, foot.offset(FAR_STAND, 0, 1));
        skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Zombie zombie = zombie(h, foot.offset(FAR_STAND - 2, 0, 0));
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            if (h.getTick() == begun + 1) c.setTarget(skeleton);
            String trace = " | zombie health " + zombie.getHealth() + ", skeleton health " + skeleton.getHealth() + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + ", target " + c.getTarget() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(skeleton.getHealth() >= skeleton.getMaxHealth(), "The skeleton was struck before the zombie" + trace);
            if (zombie.getHealth() < zombie.getMaxHealth()) { h.succeed(); return; }
            h.assertTrue(h.getTick() - begun < STRIKE_TICKS, "Neither foe was struck" + trace);
        });
    }

    /** No sword or axe in the bag and an expedition under way: a zombie on him is no fist fight. He does not strike it once, and goes off from it at least four blocks. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void withNoWeaponHeStepsOffAZombieOnHimAndDoesNotPunchIt(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(FAR_STAND, 0, 0));
        Zombie zombie = zombie(h, foot.offset(FAR_STAND + 1, 0, 0));
        long begun = h.getTick();
        double[] widest = {0};
        start(h, c, 1);
        h.onEachTick(() -> {
            if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            widest[0] = Math.max(widest[0], Math.sqrt(c.distanceToSqr(zombie)));
            String trace = " | zombie health " + zombie.getHealth() + ", widest gap " + String.format("%.1f", widest[0]) + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + ", target " + c.getTarget() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(zombie.getHealth() >= zombie.getMaxHealth(), "He struck the zombie with his bare hand" + trace);
            if (h.getTick() - begun < UNARMED_TICKS) return;
            h.assertTrue(widest[0] >= SAFE_GAP, "He did not get away from the zombie" + trace);
            h.succeed();
        });
    }
}

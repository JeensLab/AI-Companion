package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;
import static dev.aicompanion.gametest.CompanionMineFightTests.armed;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;
import static dev.aicompanion.gametest.CompanionMineFightTests.skeleton;
import static dev.aicompanion.gametest.CompanionMineFightTests.zombie;

/** The fight on the retreat when his target is not the mob that is on him. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineRetreatFightTests {
    private static final int ANSWER_TICKS = 40, ANSWERS = 2, BEYOND_THE_FOOT = 2, STUB_HEIGHT = 4, FIRST_ZOMBIE = 6, SECOND_ZOMBIE = 12;
    private static final float RETREAT_HEALTH = 8, ONE_BLOW = 4;

    private CompanionMineRetreatFightTests() { }

    /**
     * Run tb20261031-n1 descent, ticks 476919 to 477205: wounded and on the retreat, his target was a skeleton out of his reach whose arrow
     * had hit him, and a zombie on his way up struck him thirteen times in 286 ticks with no blow in answer (the melee goal strikes its
     * target only, and that one is not fought on the retreat); he ate his twelve loaves under the blows and died. The arrow had come as
     * the zombie he fought fell, so the skeleton was his target when the next zombie came. The stairs of
     * {@link CompanionMineFightTests#aZombieThatCatchesHimOnTheRetreatIsStruckBack} with a skeleton in a stub of a passage beyond their foot, in sight and out
     * of his reach, whose arrow hits him as the first zombie (one blow from death) falls: each blow of the second zombie is answered within
     * {@link #ANSWER_TICKS} all the same.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aZombieThatCatchesHimOnTheRetreatIsStruckBackWithASkeletonForHisTarget(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        // A stub of a passage on from the foot of the stairs, high enough for him to see into from three steps up.
        for (int on = 0; on <= BEYOND_THE_FOOT; on++) for (int dy = 0; dy < STUB_HEIGHT; dy++) {
            h.getLevel().setBlock(stairs.get(foot).north(on).above(dy), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, stairs.get(foot - 3));
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.BREAD)) c.setItem(slot, ItemStack.EMPTY);
        c.setHealth(RETREAT_HEALTH);
        Skeleton skeleton = skeleton(h, stairs.get(foot).north(BEYOND_THE_FOOT));
        skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        List<Zombie> zombies = new ArrayList<>();
        for (int k : new int[]{FIRST_ZOMBIE, SECOND_ZOMBIE}) zombies.add(zombie(h, stairs.get(foot - k)));
        zombies.get(0).setHealth(ONE_BLOW);
        start(h, c, 1);
        float[] health = {c.getHealth()}, theirs = {0};
        long[] unanswered = {-1};
        int[] answers = {0};
        boolean[] aimed = {false}, shot = {false};
        h.onEachTick(() -> {
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null) zombie.setTarget(c);
            // The arrow is dealt by the test: a skeleton's own misses a walking man too often for a test, so it has no bow.
            if (!shot[0] && !zombies.get(0).isAlive()) { shot[0] = true; c.hurt(h.getLevel().damageSources().mobAttack(skeleton), 1); health[0] = c.getHealth(); }
            aimed[0] |= c.getTarget() == skeleton;
            float left = 0;
            for (Zombie zombie : zombies) left += zombie.isAlive() ? zombie.getHealth() : 0;
            if (c.getHealth() < health[0] && unanswered[0] < 0) unanswered[0] = h.getTick();
            if (left < theirs[0] && unanswered[0] >= 0) { unanswered[0] = -1; answers[0]++; }
            health[0] = c.getHealth();
            theirs[0] = left;
            String trace = " | stage " + c.expeditionStage() + ", target " + (c.getTarget() == null ? "none" : c.getTarget().getType().toShortString()) + ", answered " + answers[0]
                    + ", zombies' health " + left + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died before he struck back" + trace);
            h.assertTrue(unanswered[0] < 0 || h.getTick() - unanswered[0] <= ANSWER_TICKS, "A blow he took was not answered in " + ANSWER_TICKS + " ticks" + trace);
            if (answers[0] < ANSWERS) return;
            h.assertTrue(aimed[0], "The skeleton was never his target: the test is no longer about a target out of his reach" + trace);
            h.succeed();
        });
    }
}

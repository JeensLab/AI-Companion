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

import static dev.aicompanion.gametest.CompanionMineArena.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineArena.carveMine;
import static dev.aicompanion.gametest.CompanionMineArena.mountain;
import static dev.aicompanion.gametest.CompanionMineArena.rel;
import static dev.aicompanion.gametest.CompanionMineArena.report;
import static dev.aicompanion.gametest.CompanionMineArena.start;
import static dev.aicompanion.gametest.CompanionMineFightTests.armed;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;
import static dev.aicompanion.gametest.CompanionMineFightTests.skeleton;
import static dev.aicompanion.gametest.CompanionMineFightTests.zombie;

/**
 * A mine fight at low health is left up the stairs, not fought to the death (acceptance w1e: two deaths on zombies at 2-6 health), and
 * the fight on the retreat when his target is not the mob that is on him.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineRetreatFightTests {
    private static final int ANSWER_TICKS = 40, ANSWERS = 2, BEYOND_THE_FOOT = 2, STUB_HEIGHT = 4, FIRST_ZOMBIE = 6, SECOND_ZOMBIE = 12;
    private static final float RETREAT_HEALTH = 8, ONE_BLOW = 4;
    private static final int KILL_TICKS = 1200, FROM_STEP = 10, CLIMB = 8, CLIMB_TICKS = 300, LEAVE_TICKS = 60, PASSAGE_LENGTH = 4, PASSAGE_OFFSET = 2, PASSAGE_HEIGHT = 2, ACT_TICKS = 60;
    /** A zombie on him and five health: the wound that sends him off (CompanionExpeditionFight.wounded). */
    private static final float LOW = 4, ARMED_FOE_HEALTH = 12, WOUNDED = 5;
    /** A zombie's blow with nothing in its hand. */
    private static final double BARE_BLOW = 3, SLOW = .1, WEAK_BLOW = 1;

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
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
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
        // A blow of one health: that the blows are answered is what is asked, and with three a blow he is dead before the second answer now and then.
        for (Zombie zombie : zombies) zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(WEAK_BLOW);
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
            boolean blow = shot[0] && c.isAlive() && c.getHealth() < health[0];
            h.assertTrue(!blow || "RETREAT".equals(c.expeditionStage()), "A zombie's blow came outside the retreat, where the fight takes the one on him anyway: stage " + c.expeditionStage());
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

    /**
     * On his own stairs at four health with two zombies right below him on the same stairs and the way up free, the mine record real and the sword
     * in his hand: he climbs away at least {@link #CLIMB} steps and lands no blow while his health is that low (the old rule refused to leave a
     * foe within three blocks, so he stood and fought). A property of what he does, not a count of who lives. The bread in his bag may mend him
     * on the way up before the eighth step; past {@link #LOW} he may strike, and the zombies dead end the climb as well. The zombies are slow,
     * so that none catches him: one that does and lands a blow is struck back where he stands, whatever his health (the rule of
     * CompanionExpeditionFight.caught, which stands over this one), and so a blow of his within {@link #ANSWER_TICKS} of one he took is no failure.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void atFourHealthWithZombiesRightBelowHimOnTheStairsHeClimbsAwayAndStrikesNoBlow(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        place(c, stairs.get(FROM_STEP));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        c.setHealth(LOW);
        List<Zombie> foes = List.of(zombie(h, stairs.get(FROM_STEP + 2)), zombie(h, stairs.get(FROM_STEP + 3)));
        for (Zombie foe : foes) foe.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SLOW);
        float[] seen = {foes.get(0).getMaxHealth(), foes.get(1).getMaxHealth()}, health = {c.getHealth()};
        long[] blow = {-ANSWER_TICKS};
        int startY = rel(h, stairs.get(FROM_STEP)).getY();
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            int rise = rel(h, c.blockPosition()).getY() - startY;
            if (c.getHealth() < health[0]) blow[0] = h.getTick();
            health[0] = c.getHealth();
            String trace = " | climbed " + rise + " steps, zombie health " + foes.get(0).getHealth() + "/" + foes.get(1).getHealth() + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + ", stage " + c.expeditionStage() + " | " + report(c);
            for (int i = 0; i < foes.size(); i++) {
                Zombie foe = foes.get(i);
                if (foe.isAlive() && foe.getTarget() == null) foe.setTarget(c);
                boolean struck = !foe.isAlive() || foe.getHealth() < seen[i];
                h.assertTrue(!struck || c.getHealth() > LOW || h.getTick() - blow[0] <= ANSWER_TICKS, "He struck a zombie that had not caught him at " + LOW + " health or less" + trace);
                seen[i] = foe.isAlive() ? foe.getHealth() : seen[i];
            }
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (rise >= CLIMB || foes.stream().noneMatch(Zombie::isAlive)) h.succeed();
            h.assertTrue(h.getTick() - begun < CLIMB_TICKS, "He did not climb away in time" + trace);
        });
    }

    /**
     * Run r20261031-d2/water: wounded in a cave he was sealing, the fight let the zombies go for the climb, while the cave's own turn kept the
     * climb from starting; he neither struck nor left, and two zombies killed him in seven seconds. Wounded with a zombie on him while the cave
     * is being sealed, he either starts up his stairs or strikes it within three seconds.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void woundedWhileTheCaveIsBeingSealedHeLeavesOrStrikesNotNeither(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = -PASSAGE_LENGTH; dx <= 1; dx++) for (int dy = 0; dy < PASSAGE_HEIGHT; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, -PASSAGE_OFFSET), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, stairs.get(stairs.size() - 3));
        Zombie foe = zombie(h, foot.offset(-2, 0, -PASSAGE_OFFSET));
        foe.setNoAi(true);
        long begun = h.getTick();
        long[] sealing = {-1};
        start(h, c, 1);
        h.onEachTick(() -> {
            String phase = c.expeditionCavePhase();
            if (sealing[0] < 0 && phase.equals("SEAL")) {
                sealing[0] = h.getTick();
                place(c, foot.offset(0, 0, -PASSAGE_OFFSET));
                foe.setNoAi(false);
                foe.setTarget(c);
            }
            if (sealing[0] >= 0 && c.getHealth() != WOUNDED) c.setHealth(WOUNDED);
            String trace = " | cave phase " + phase + ", stage " + c.expeditionStage() + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", zombie health " + foe.getHealth() + ", target " + c.getTarget() + " | " + report(c);
            if (sealing[0] < 0) { h.assertTrue(h.getTick() - begun < KILL_TICKS, "SETUP: the cave was never sealed" + trace); return; }
            if (c.expeditionStage().equals("RETREAT") || foe.getHealth() < foe.getMaxHealth()) h.succeed();
            h.assertTrue(h.getTick() - sealing[0] < ACT_TICKS, "Wounded by the cave, he neither started up nor struck" + trace);
        });
    }

    /**
     * Run s7-d2/mobs: a zombie with a weapon (six and a half a blow) and a skeleton; the climb began at five health, a blow from death, and the
     * zombie dealt it. At twelve health with a zombie with a stone sword (seven a blow) right below him, the climb begins within three seconds
     * (the old rule waited for five health). The sword's blow is the zombie's once it has ticked with it.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aZombieWithASwordIsLeftWhileTwoOfItsBlowsAreStillToBeTaken(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        place(c, stairs.get(FROM_STEP));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        c.setHealth(ARMED_FOE_HEALTH);
        Zombie foe = zombie(h, stairs.get(FROM_STEP + 2));
        foe.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        foe.setNoAi(true);
        int startY = rel(h, stairs.get(FROM_STEP)).getY();
        long[] begun = {-1};
        h.onEachTick(() -> {
            if (begun[0] < 0) {
                if (foe.getAttributeValue(Attributes.ATTACK_DAMAGE) <= BARE_BLOW) return;
                foe.setNoAi(false);
                begun[0] = h.getTick();
                start(h, c, 1);
                return;
            }
            int rise = rel(h, c.blockPosition()).getY() - startY;
            String trace = " | climbed " + rise + " steps, zombie health " + foe.getHealth() + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + ", stage " + c.expeditionStage() + " | " + report(c);
            if (foe.isAlive() && foe.getTarget() == null) foe.setTarget(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (c.expeditionStage().equals("RETREAT") || rise >= CLIMB) h.succeed();
            h.assertTrue(h.getTick() - begun[0] < LEAVE_TICKS, "He did not begin the climb in time" + trace);
        });
    }
}

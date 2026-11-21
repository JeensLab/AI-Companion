package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
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
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;
import static dev.aicompanion.gametest.CompanionMineFightTests.armed;
import static dev.aicompanion.gametest.CompanionMineFightTests.chamber;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;
import static dev.aicompanion.gametest.CompanionMineFightTests.zombie;

/** A mine fight at low health is left up the stairs, not fought to the death (acceptance w1e: two deaths on zombies at 2-6 health); at full health the sword still kills. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK19Tests {
    private static final int KILL_TICKS = 1200, FROM_STEP = 10, CLIMB = 8, CLIMB_TICKS = 300;
    private static final float LOW = 4, ARMED_FOE_HEALTH = 12;
    /** A zombie's blow with nothing in its hand. */
    private static final double BARE_BLOW = 3;
    private static final int LEAVE_TICKS = 60;
    /** A zombie on him and five health: the wound that sends him off (CompanionExpeditionFight.wounded). */
    private static final float WOUNDED = 5;
    private static final int PASSAGE_LENGTH = 4, PASSAGE_OFFSET = 2, PASSAGE_HEIGHT = 2, ACT_TICKS = 60;

    private CompanionMineK19Tests() { }

    /**
     * On his own stairs at four health with two zombies right below him on the same stairs and the way up free, the mine record real and the sword
     * in his hand: he climbs away at least {@link #CLIMB} steps and lands no blow while his health is that low (the old rule refused to leave a
     * foe within three blocks, so he stood and fought). A property of what he does, not a count of who lives. The bread in his bag may mend him
     * on the way up before the eighth step; past {@link #LOW} he may strike, and the zombies dead end the climb as well.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void atFourHealthWithZombiesRightBelowHimOnTheStairsHeClimbsAwayAndStrikesNoBlow(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        place(c, stairs.get(FROM_STEP));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        c.setHealth(LOW);
        List<Zombie> foes = List.of(zombie(h, stairs.get(FROM_STEP + 2)), zombie(h, stairs.get(FROM_STEP + 3)));
        float[] seen = {foes.get(0).getMaxHealth(), foes.get(1).getMaxHealth()};
        int startY = rel(h, stairs.get(FROM_STEP)).getY();
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            int rise = rel(h, c.blockPosition()).getY() - startY;
            String trace = " | climbed " + rise + " steps, zombie health " + foes.get(0).getHealth() + "/" + foes.get(1).getHealth() + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + ", stage " + c.expeditionStage() + " | " + report(c);
            for (int i = 0; i < foes.size(); i++) {
                Zombie foe = foes.get(i);
                if (foe.isAlive() && foe.getTarget() == null) foe.setTarget(c);
                boolean struck = !foe.isAlive() || foe.getHealth() < seen[i];
                h.assertTrue(!struck || c.getHealth() > LOW, "He struck a zombie at " + LOW + " health or less" + trace);
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
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
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
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
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

    /** Full health, two zombies on him: the sword kills both, and he does not turn back from the fight. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void atFullHealthHeKillsTheZombiesAndDoesNotRunFromThem(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot);
        List<Zombie> foes = List.of(zombie(h, foot.offset(2, 0, 0)), zombie(h, foot.offset(2, 0, 1)));
        long begun = h.getTick();
        start(h, c, 1);
        h.onEachTick(() -> {
            for (Zombie foe : foes) if (foe.isAlive() && foe.getTarget() == null) foe.setTarget(c);
            String trace = " | foes alive " + foes.stream().filter(Zombie::isAlive).count() + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(!c.getLastTaskResult().contains("Прервал вылазку"), "He left the fight at full health" + trace);
            if (foes.stream().noneMatch(Zombie::isAlive)) h.succeed();
            h.assertTrue(h.getTick() - begun < KILL_TICKS, "The zombies were not killed in time" + trace);
        });
    }
}

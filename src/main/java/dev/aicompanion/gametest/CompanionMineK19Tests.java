package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private static final float LOW = 4;

    private CompanionMineK19Tests() { }

    /**
     * On his own stairs at four health with two zombies right below him on the same stairs and the way up free, the mine record real and the sword
     * in his hand: he climbs away at least {@link #CLIMB} steps and lands no blow while his health is that low (the old rule refused to leave a
     * foe within three blocks, so he stood and fought). A property of what he does, not a count of who lives.
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
            if (rise >= CLIMB) h.succeed();
            h.assertTrue(h.getTick() - begun < CLIMB_TICKS, "He did not climb away in time" + trace);
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

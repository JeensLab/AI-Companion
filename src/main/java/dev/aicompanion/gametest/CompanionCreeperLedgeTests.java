package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionSeal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineFightTests.armed;
import static dev.aicompanion.gametest.CompanionMineFightTests.creeper;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;

/**
 * The creeper guard on a slope of ledges with a dead end at its top, a creeper one ledge below him: the only way off is down, past it.
 * Run ta20261031-d2 void-4, tick 536974, at (-38, 11, -46): the fuse was lit a block and a half off, he struck it (the blow threw it on down
 * the slope) and ran down the same slope; they fell together, 18 ticks within two blocks of each other, and it blew 1.25 blocks from him,
 * 28 damage at full health.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCreeperLedgeTests {
    /** The slope by the height at which he stands on each ledge, west to east, as the run's by its row z -47: a landing, steps of one, then drops of three, one, two, two. */
    private static final int[] FEET = {15, 15, 15, 14, 13, 12, 11, 8, 7, 5, 3, 2, 2, 2, 2, 2, 2};
    private static final int WEST = 28, NORTH = 30, WIDTH = 7, ROOF = 18, HIS_LEDGE = 5, ITS_LEDGE = 6, MIDDLE = 3, WATCH_TICKS = 200, NO_LANDING = 3, WIDER = 2, BESIDE = 2;
    private static final float KEPT = 10, AS_IN_THE_RUN = 15;
    private static final double STEP_DOWN = .5;
    /** What he says when he steps away from a creeper he does not fight. */
    private static final String WARY = "Рядом крипер";

    private CompanionCreeperLedgeTests() { }

    /** With the bag of the run (cobblestone for a wall): while the fuse burns he does not go down the slope, which is past the creeper, and the blast, if any, leaves him more than half his health. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitCreeperOnTheLedgeBelowIsNotRunPastDownTheSlope(GameTestHelper h) {
        watch(h, true);
    }

    /** With no block for a wall (those of the run were set in the air and held nothing), so that the run alone is tried: the blast leaves him more than half his health. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitCreeperOnTheLedgeBelowAndNoBlockForAWallLeavesHimHalfHisHealth(GameTestHelper h) {
        watch(h, false);
    }

    /**
     * Review of the far side that only a lit creeper had: before the fuse is lit the guard judges whether he has a way out, and it must judge
     * as the run will find it. No landing behind him here, the slope is wide enough to pass the creeper, and the creeper stands still three
     * ledges below him, after him, too far for its fuse: the only way out that gets clear is past it, so that is no way out, he has no room
     * to fight it and does not go down to meet it (the player is beside him, as he meets a creeper outside a mine only by the player). Its
     * fuse is never lit.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void anUnlitCreeperBelowWithNoWayOutButPastItIsNotGoneDownTo(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        for (int i = NO_LANDING; i < FEET.length; i++) for (int dz = -WIDER; dz < WIDTH + WIDER; dz++) for (int y = FEET[i]; y <= ROOF; y++) {
            h.getLevel().setBlock(h.absolutePos(new BlockPos(WEST + i, y, NORTH + dz)), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        BlockPos top = h.absolutePos(new BlockPos(WEST + NO_LANDING, FEET[NO_LANDING], NORTH + MIDDLE));
        place(c, top);
        // The player beside him: outside a mine he meets a creeper only by the player. In creative the creeper hunts him, not the player.
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        player.setGameMode(GameType.CREATIVE);
        BlockPos beside = top.offset(top.subtract(h.absolutePos(new BlockPos(WEST + NO_LANDING, FEET[NO_LANDING], NORTH + MIDDLE - BESIDE))));
        player.teleportTo(beside.getX() + .5, beside.getY(), beside.getZ() + .5);
        Creeper creeper = creeper(h, h.absolutePos(new BlockPos(WEST + ITS_LEDGE, FEET[ITS_LEDGE], NORTH + MIDDLE)));
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        creeper.setTarget(c);
        long begun = h.getTick();
        boolean[] wary = {false};
        h.onEachTick(() -> {
            if (creeper.isAlive() && creeper.getTarget() == null) creeper.setTarget(c);
            String trace = " | tick " + (h.getTick() - begun) + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + ", creeper "
                    + (creeper.isAlive() ? "gap " + String.format("%.1f", c.distanceTo(creeper)) + ", health " + creeper.getHealth() : "gone") + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(creeper.isAlive() && creeper.getSwellDir() <= 0, "He went down to a creeper with no way out but past it, and its fuse was lit" + trace);
            wary[0] |= c.getLastTaskResult().contains(WARY);
            if (h.getTick() - begun < WATCH_TICKS) return;
            h.assertTrue(wary[0], "He took no notice of the creeper at all" + trace);
            h.succeed();
        });
    }

    private static void watch(GameTestHelper h, boolean blocks) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        if (!blocks) for (int slot = 0; slot < c.getContainerSize(); slot++) if (CompanionSeal.seals(c.getItem(slot))) c.setItem(slot, ItemStack.EMPTY);
        for (int i = 0; i < FEET.length; i++) for (int dz = 0; dz < WIDTH; dz++) for (int y = FEET[i]; y <= ROOF; y++) {
            h.getLevel().setBlock(h.absolutePos(new BlockPos(WEST + i, y, NORTH + dz)), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(c, h.absolutePos(new BlockPos(WEST + HIS_LEDGE, FEET[HIS_LEDGE], NORTH + MIDDLE)));
        Creeper creeper = creeper(h, h.absolutePos(new BlockPos(WEST + ITS_LEDGE, FEET[ITS_LEDGE], NORTH + MIDDLE)));
        creeper.setHealth(AS_IN_THE_RUN);
        creeper.setTarget(c);
        long begun = h.getTick();
        double ledge = c.getY();
        h.onEachTick(() -> {
            long elapsed = h.getTick() - begun;
            if (creeper.isAlive() && creeper.getTarget() == null) creeper.setTarget(c);
            String trace = " | tick " + elapsed + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + ", creeper "
                    + (creeper.isAlive() ? "at " + rel(h, creeper.blockPosition()).toShortString() + ", gap " + String.format("%.1f", c.distanceTo(creeper)) + ", fuse "
                    + String.format("%.2f", creeper.getSwelling(0)) + ", health " + creeper.getHealth() : "gone") + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(c.getHealth() >= KEPT, "The blast took him below half his health" + trace);
            if (blocks) h.assertTrue(!creeper.isAlive() || creeper.getSwellDir() <= 0 || c.getY() > ledge - STEP_DOWN, "He ran down the slope past the lit creeper" + trace);
            if (!creeper.isAlive() || elapsed >= WATCH_TICKS) h.succeed();
        });
    }
}

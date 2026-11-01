package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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
import static dev.aicompanion.gametest.CompanionMineFightTests.creeper;
import static dev.aicompanion.gametest.CompanionMineFightTests.place;
import static dev.aicompanion.gametest.CompanionMineFightTests.zombie;

/** The creeper guard with several creepers about, and what else may take his turn while one is near. */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineGuardTests {
    private static final int FUSE_TICKS = 200, STAND = 6, LIT_AT = 8, FRESH_AT = 1;
    private static final double SLACK = .25;
    private static final float KEPT = 10;
    private static final int PIT_FROM = 8, PIT_TO = 10, PIT_DEPTH = 4, DROP_AT = 12, PASSAGE_LENGTH = 4, PASSAGE_OFFSET = 2, GRACE_TICKS = 6, MAX_CHANGES = 2, HOLD_TICKS = 80, RELEASED_TICKS = 400;
    private static final double CLOSER = 1.5;

    private CompanionMineGuardTests() { }

    /**
     * A lit fuse on his east and a creeper with no fuse yet on his west (it stands still): the way out of the lit one does not lead to the other,
     * he is never nearer to either than he was, and the blast leaves him more than half his health.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aRetreatFromALitFuseDoesNotCloseOnAnotherCreeper(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(STAND, 0, 0));
        Creeper lit = creeper(h, foot.offset(LIT_AT, 0, 0));
        Creeper fresh = creeper(h, foot.offset(FRESH_AT, 0, 0));
        fresh.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        lit.ignite();
        start(h, c, 1);
        long begun = h.getTick();
        double[] start = {-1, -1};
        h.onEachTick(() -> {
            double toFresh = c.distanceTo(fresh), toLit = c.distanceTo(lit);
            if (start[0] < 0) { start[0] = toFresh; start[1] = toLit; }
            String trace = " | to the fresh one " + String.format("%.1f", toFresh) + " from " + String.format("%.1f", start[0]) + ", to the lit one "
                    + String.format("%.1f", toLit) + (lit.isAlive() ? "" : " (gone)") + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(!fresh.isAlive() || toFresh >= start[0] - SLACK, "He walked towards the creeper behind him" + trace);
            if (lit.isAlive()) {
                h.assertTrue(h.getTick() - begun < FUSE_TICKS, "The lit creeper never blew" + trace);
                return;
            }
            h.assertTrue(c.getHealth() >= KEPT, "The blast took him below half his health" + trace);
            h.succeed();
        });
    }

    /**
     * A creeper stands in a pit four blocks under the floor, in sight at the pit's edge: it is no foe (too far below) but it is near, and a drop
     * lies on the far side of the pit. He does not walk to the drop while the creeper lives; once it is gone he takes the drop.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLiveCreeperNearKeepsHimFromWalkingToADrop(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        BlockPos foot = chamber(h, carveMine(h, c, 0));
        place(c, foot.offset(PIT_FROM - 1, 0, 0));
        Creeper creeper = creeperInAPit(h, foot);
        BlockPos at = foot.offset(DROP_AT, 0, 0);
        ItemEntity drop = new ItemEntity(h.getLevel(), at.getX() + .5, at.getY(), at.getZ() + .5, new ItemStack(Items.COBBLESTONE));
        drop.setDeltaMovement(Vec3.ZERO);
        drop.setNoPickUpDelay();
        h.getLevel().addFreshEntity(drop);
        c.claimDrops(List.of(drop));
        start(h, c, 1);
        long begun = h.getTick();
        double[] from = {-1};
        h.onEachTick(() -> {
            if (from[0] < 0) from[0] = c.distanceTo(drop);
            long elapsed = h.getTick() - begun;
            String trace = " | creeper " + (creeper.isAlive() ? "lives" : "gone") + ", drop " + (drop.isAlive() ? "lies " + String.format("%.1f", c.distanceTo(drop)) + " off, was " + String.format("%.1f", from[0]) : "taken")
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (elapsed < HOLD_TICKS) {
                h.assertTrue(drop.isAlive() && c.distanceTo(drop) > from[0] - CLOSER, "He went for the drop with the creeper near" + trace);
                return;
            }
            if (elapsed == HOLD_TICKS) creeper.discard();
            if (!drop.isAlive()) h.succeed();
            h.assertTrue(elapsed < HOLD_TICKS + RELEASED_TICKS, "SETUP: the drop was not taken once the creeper was gone" + trace);
        });
    }

    /**
     * The breach of a cave with mobs in it is being sealed and a creeper in the cave has its fuse lit: nothing is sealed meanwhile, the
     * stone sword stays in his hand (it does not go back and forth with the pickaxe) until the creeper has blown.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseNearKeepsTheSwordInHandWhileTheCaveIsBeingSealed(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = -PASSAGE_LENGTH; dx <= 1; dx++) for (int dy = 0; dy < 2; dy++)
            h.getLevel().setBlock(foot.offset(dx, dy, -PASSAGE_OFFSET), Blocks.AIR.defaultBlockState(), 2 | 16);
        place(c, stairs.get(stairs.size() - 3));
        for (int dx : new int[]{-3, -4}) zombie(h, foot.offset(dx, 0, -PASSAGE_OFFSET)).setNoAi(true);
        Creeper creeper = creeper(h, foot.offset(-1, 0, -PASSAGE_OFFSET));
        creeper.setNoAi(true);
        long begun = h.getTick();
        long[] lit = {-1};
        int[] swaps = {0};
        Item[] held = {c.getMainHandItem().getItem()};
        start(h, c, 1);
        h.onEachTick(() -> {
            String phase = c.expeditionCavePhase();
            if (lit[0] < 0 && phase.equals("SEAL")) {
                lit[0] = h.getTick();
                creeper.setNoAi(false);
                creeper.ignite();
            }
            if (c.getMainHandItem().getItem() != held[0]) { swaps[0]++; held[0] = c.getMainHandItem().getItem(); }
            String trace = " | cave phase " + phase + ", creeper " + (creeper.isAlive() ? "lit" : "gone") + ", hand " + held[0] + ", changes " + swaps[0] + ", he at "
                    + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            if (lit[0] < 0) { h.assertTrue(h.getTick() - begun < FUSE_TICKS * 5, "SETUP: the cave was never sealed" + trace); return; }
            if (!creeper.isAlive()) { h.succeed(); return; }
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (h.getTick() - lit[0] > GRACE_TICKS) h.assertTrue(held[0] == Items.STONE_SWORD, "The sword was not in his hand with a fuse lit" + trace);
            h.assertTrue(swaps[0] <= MAX_CHANGES, "The tool went back and forth" + trace);
        });
    }

    /** A creeper at the bottom of a pit dug under the floor of the chamber, still (it has no legs to climb with). */
    private static Creeper creeperInAPit(GameTestHelper h, BlockPos foot) {
        for (int dx = PIT_FROM; dx <= PIT_TO; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -PIT_DEPTH; dy < 0; dy++)
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        Creeper creeper = creeper(h, foot.offset((PIT_FROM + PIT_TO) / 2, -PIT_DEPTH, 0));
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        return creeper;
    }
}

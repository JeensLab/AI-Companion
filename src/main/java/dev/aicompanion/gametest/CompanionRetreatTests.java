package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static dev.aicompanion.gametest.CompanionMineArena.GROUND;
import static dev.aicompanion.gametest.CompanionMineArena.SIZE;
import static dev.aicompanion.gametest.CompanionMineArena.carveMine;
import static dev.aicompanion.gametest.CompanionMineArena.ended;
import static dev.aicompanion.gametest.CompanionMineArena.mountain;
import static dev.aicompanion.gametest.CompanionMineArena.rel;
import static dev.aicompanion.gametest.CompanionMineArena.report;
import static dev.aicompanion.gametest.CompanionMineArena.rig;
import static dev.aicompanion.gametest.CompanionMineArena.start;

/**
 * Retreat from danger up his own staircase, a plug in the first step, the rest at the entrance, and the next step going back down.
 * Arena as in the other mine tests: origin at Y -60, Oleg picks NORTH at (24,22,24), stand k is (24,22-k,24-k), the tunnel feet at
 * relative height 2. The plug is a solid block in the columns of the first two stands below the entrance (the first step up).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionRetreatTests {
    private static final int DIAMOND_FEET = 2, TUNNEL_HEAD = DIAMOND_FEET + 1;
    private static final int RESUME_TIMEOUT = 9000, SCAN_EVERY = 5, PLUG_STANDS = 2, LOW_HEALTH = 5, FAR = 8, TUNNEL = 6;

    /**
     * Left a plug on the first step (as after a retreat) and is back at the entrance: the next step takes his own plug out, walks
     * down the same stairs to the tunnel and opens nothing outside the tunnel on the way.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = RESUME_TIMEOUT)
    public static void nextStepRemovesHisPlugAndGoesBackDown(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        start(h, c, 16);
        int[] phase = {0}, stands = {0};
        AtomicReference<Set<BlockPos>> before = new AtomicReference<>();
        AtomicReference<BlockPos> plugAt = new AtomicReference<>();
        AtomicReference<BlockPos> entrance = new AtomicReference<>();
        h.runAtTickTime(RESUME_TIMEOUT - 5, () -> h.fail("Stuck in phase " + phase[0] + " at " + rel(h, c.blockPosition()).toShortString() + ", plug " + (plugAt.get() == null ? "not set" : h.getBlockState(plugAt.get()).getBlock()) + " | " + report(c)));
        h.onEachTick(() -> {
            if (h.getTick() % SCAN_EVERY != 0) return;
            CompanionMineSite mine = c.mineSite();
            BlockPos feet = rel(h, c.blockPosition());
            if (phase[0] == 0) {
                if (feet.getY() != DIAMOND_FEET || advanced(c) < 1) return;
                c.setFollowing(false);
                BlockPos plug = CompanionShaftPlan.stand(mine.entrance(), mine.stairDirection(), PLUG_STANDS);
                h.getLevel().setBlock(plug, Blocks.COBBLESTONE.defaultBlockState(), 3);
                mine.plugged(plug);
                c.moveTo(mine.entrance().getX() + .5, mine.entrance().getY(), mine.entrance().getZ() + .5);
                stands[0] = mine.stands().size();
                entrance.set(mine.entrance());
                plugAt.set(rel(h, plug));
                before.set(airCells(h));
                start(h, c, 16);
                phase[0] = 1;
                return;
            }
            h.assertTrue(mine.exists() && mine.entrance().equals(entrance.get()), "Mine record lost or moved: " + mine.entrance());
            Set<BlockPos> added = airCells(h);
            added.removeAll(before.get());
            added.remove(plugAt.get());
            for (BlockPos cell : added) h.assertTrue(cell.getY() >= DIAMOND_FEET && cell.getY() <= TUNNEL_HEAD, "New cell outside the tunnel: " + cell.toShortString() + " (" + added.size() + " new) | at " + feet.toShortString() + " | " + report(c));
            if (feet.getY() != DIAMOND_FEET) return;
            h.assertTrue(!blocked(h, h.absolutePos(plugAt.get())), "Back in the tunnel but his plug still stands at " + plugAt.get().toShortString() + " | " + report(c));
            h.assertTrue(mine.plug() == null && mine.stands().size() >= stands[0], "The record kept a plug " + mine.plug() + " or lost stairs: " + mine.stands().size() + " of " + stands[0] + " stands | " + report(c));
            h.succeed();
        });
    }

    /**
     * Low health at the face of the tunnel and nothing to eat: he goes back along the tunnel and up the stairs, closes the first step behind
     * him with a plug and rests at the entrance until he is fit; the order ends as a retreat, said so.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = RESUME_TIMEOUT)
    public static void lowHealthSendsHimUpBehindAPlugAndTheRestEndsTheOrder(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        c.setItem(2, ItemStack.EMPTY);
        carveMine(h, c, TUNNEL);
        long id = start(h, c, 16);
        boolean[] hurt = {false};
        h.onEachTick(() -> {
            CompanionMineSite mine = c.mineSite();
            if (!hurt[0]) {
                if (rel(h, c.blockPosition()).getY() != DIAMOND_FEET) return;
                c.setHealth(LOW_HEALTH);
                hurt[0] = true;
                return;
            }
            // The rest at the entrance is over once he is fit again: the health is given back when he has climbed up.
            if (c.getBlockY() >= mine.entrance().getY() - 1 && "RETREAT".equals(c.expeditionStage())) c.setHealth(20);
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.message().contains("отступил наверх"), "The order did not end as a retreat: " + result.message() + " | " + report(c));
            h.assertTrue(mine.plug() != null && blocked(h, mine.plug()), "No plug in the first step: " + mine.plug() + " | " + report(c));
            h.assertTrue(c.getBlockY() >= mine.entrance().getY() - 1, "He did not rest at the entrance: " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            h.succeed();
        });
    }

    /**
     * Hurt by a mob far from his way (eight cells beyond the end of the tunnel in an open corridor): he walks back to his tunnel and goes up
     * by it instead of resting where he stands under the blows.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = RESUME_TIMEOUT)
    public static void farFromHisWayAMobOnHimSendsHimBackToIt(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        c.setItem(2, ItemStack.EMPTY);
        carveMine(h, c, TUNNEL);
        CompanionMineSite mine = c.mineSite();
        for (int k = TUNNEL + 1; k <= TUNNEL + FAR + 1; k++) for (int dy = 0; dy <= 1; dy++) h.getLevel().setBlock(mine.foot().relative(Direction.WEST, k).above(dy), Blocks.AIR.defaultBlockState(), 2 | 16);
        start(h, c, 16);
        boolean[] hurt = {false};
        h.onEachTick(() -> {
            if (!hurt[0]) {
                if (!"BRANCH".equals(c.expeditionStage())) return;
                BlockPos far = mine.foot().relative(Direction.WEST, TUNNEL + FAR);
                c.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
                Zombie zombie = h.spawn(EntityType.ZOMBIE, 1, DIAMOND_FEET + 12, 1);
                zombie.setNoAi(true);
                c.setLastHurtByMob(zombie);
                c.setHealth(LOW_HEALTH);
                hurt[0] = true;
                return;
            }
            h.assertTrue(c.isAlive(), "He died");
            // Closing the first step behind him is done only on the way up, which he took: resting where he stood would leave it open.
            if (mine.plug() != null && blocked(h, mine.plug())) h.succeed();
        });
    }

    private static boolean blocked(GameTestHelper h, BlockPos abs) { return !h.getLevel().getBlockState(abs).getCollisionShape(h.getLevel(), abs).isEmpty(); }

    private static int advanced(CompanionEntity c) { return c.mineSite().tunnels().stream().mapToInt(CompanionMineSite.Tunnel::length).sum(); }

    /** Relative positions of the air cells inside the mountain. */
    private static Set<BlockPos> airCells(GameTestHelper h) {
        Set<BlockPos> air = new HashSet<>();
        for (int x = 1; x < SIZE - 1; x++) for (int z = 1; z < SIZE - 1; z++) for (int y = 1; y <= GROUND; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (h.getBlockState(pos).isAir()) air.add(pos);
        }
        return air;
    }
}

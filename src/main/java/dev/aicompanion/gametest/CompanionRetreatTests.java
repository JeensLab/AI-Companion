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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static dev.aicompanion.gametest.CompanionMineArena.CENTER;
import static dev.aicompanion.gametest.CompanionMineArena.GROUND;
import static dev.aicompanion.gametest.CompanionMineArena.SIZE;
import static dev.aicompanion.gametest.CompanionMineArena.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineArena.carveMine;
import static dev.aicompanion.gametest.CompanionMineArena.count;
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
    private static final int RESUME_TIMEOUT = 9000, SCAN_EVERY = 5, PLUG_STANDS = 2, LOW_HEALTH = 5, FAR = 8, TUNNEL = 6, FOE_BEYOND = 3;
    private static final int NEAR_ENTRANCE_SQ = 9, RETARGET_EVERY = 20, SURFACE_DISTANCE = 22, SURFACE_CHECKS = 3, SWORD_SLOT = 6;
    private static final int CHAMBER_X0 = 26, CHAMBER_X1 = 30, CHAMBER_Z0 = 3, CHAMBER_Z1 = 7, STRIP_Z = 1, STRIP_X0 = 22, STRIP_X1 = 25;
    private static final float RECOVERED = 18;
    private static final String RETREATED = "отступил";
    private static final BlockPos BREACH_FEET = new BlockPos(25, DIAMOND_FEET, 3), BREACH_HEAD = BREACH_FEET.above();

    /** Four zombies in a dark chamber and no sword in the bag: the breach is sealed, he climbs his stairs, plugs the first step behind him, recovers at the entrance and says so. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void retreatsUpHisStairsFromACrowdedCaveAndRecovers(GameTestHelper h) { crowdedCave(h, false); }

    /** The same crowd with a sword in the bag and health to spare: he does not climb, fights what comes at him, seals the breach and takes the diamond. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void fightsACrowdedCaveWithTheSwordInsteadOfClimbing(GameTestHelper h) { crowdedCave(h, true); }

    /** The ore lies only in a strip north of the foot of the stairs, and a chamber with four zombies stands beside the tunnel to it. */
    private static void crowdedCave(GameTestHelper h, boolean sword) {
        mountain(h, false);
        for (int x = STRIP_X0; x <= STRIP_X1; x++) h.setBlock(new BlockPos(x, DIAMOND_FEET - 1, STRIP_Z), Blocks.DEEPSLATE_DIAMOND_ORE);
        for (int x = CHAMBER_X0; x <= CHAMBER_X1; x++) for (int z = CHAMBER_Z0; z <= CHAMBER_Z1; z++) for (int y = DIAMOND_FEET; y <= DIAMOND_FEET + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        CompanionEntity c = rig(h);
        if (sword) c.setItem(SWORD_SLOT, new ItemStack(Items.IRON_SWORD));
        List<Zombie> zombies = new ArrayList<>();
        for (int[] at : new int[][]{{27, 4}, {28, 5}, {29, 6}, {30, 7}}) {
            Zombie zombie = h.spawn(EntityType.ZOMBIE, at[0], DIAMOND_FEET, at[1]);
            zombie.setPersistenceRequired();
            // The sky light of a freshly built mountain lags behind it: by day a mob in the chamber can burn before the cave opens.
            zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
            zombies.add(zombie);
        }
        long id = start(h, c, 1);
        boolean[] breached = {false};
        h.onEachTick(() -> {
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null && h.getTick() % RETARGET_EVERY == 0) zombie.setTarget(c);
            breached[0] |= !blocked(h, h.absolutePos(BREACH_FEET)) || !blocked(h, h.absolutePos(BREACH_HEAD));
            BlockPos plug = plug(h, c);
            String state = " | at " + rel(h, c.blockPosition()).toShortString() + ", hp " + c.getHealth() + ", plug " + (plug == null ? "none" : rel(h, plug).toShortString()) + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + state);
            if (c.isGatheringTask() || c.isWorking() || c.workResult(id) == null) return;
            String result = c.workResult(id).message();
            List<String> problems = new ArrayList<>();
            if (!breached[0]) problems.add("SETUP: the tunnel never broke into the chamber");
            if (sword) {
                if (result.contains(RETREATED)) problems.add("he climbed away from a crowd he could fight");
                if (plug != null) problems.add("a plug stands in the first stair cells");
                if (count(c, Items.DIAMOND) < 1) problems.add("no diamond in the bag");
            } else {
                if (!result.contains(RETREATED)) problems.add("the result does not say «" + RETREATED + "»");
                if (plug == null) problems.add("no plug in the first stair cells");
                if (!c.mineSite().exists() || c.blockPosition().distSqr(c.mineSite().entrance()) > NEAR_ENTRANCE_SQ) problems.add("not at the entrance");
                if (c.getHealth() < RECOVERED) problems.add("health " + c.getHealth() + " is below " + RECOVERED);
                int level = plug == null ? 0 : rel(h, plug).getY();
                for (Zombie zombie : zombies) if (zombie.isAlive() && rel(h, zombie.blockPosition()).getY() >= level) problems.add("a zombie on the surface side of the plug at " + rel(h, zombie.blockPosition()).toShortString());
            }
            h.assertTrue(problems.isEmpty(), "Not as expected: " + problems + " | result: " + result + state);
            h.succeed();
        });
    }

    /** The player is far off and a zombie stands near him on the surface (he walks to his mine): the fight is not taken up, the target is dropped. */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void doesNotChaseAZombieOnTheSurfaceFarFromThePlayer(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        h.getLevel().getPlayerByUUID(c.getOwnerId()).teleportTo(c.getX() - SURFACE_DISTANCE, c.getY(), c.getZ() - SURFACE_DISTANCE);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, CENTER + 3, GROUND + 1, CENTER);
        zombie.setPersistenceRequired();
        zombie.setNoAi(true);
        start(h, c, 1);
        int[] checked = {0};
        boolean[] armed = {false};
        h.onEachTick(() -> {
            boolean onSurface = rel(h, c.blockPosition()).getY() > GROUND;
            if (armed[0] && onSurface) {
                h.assertTrue(c.getTarget() == null, "On the surface, far from the player, he took up the fight with the zombie | " + report(c));
                if (++checked[0] >= SURFACE_CHECKS) h.succeed();
            }
            armed[0] = onSurface && h.getTick() % RETARGET_EVERY == 0;
            if (armed[0]) c.setTarget(zombie);
        });
    }

    /**
     * Left a plug on the first step (as after a retreat) and is back at the entrance: the next step takes his own plug out, walks
     * down the same stairs to the tunnel and opens nothing outside the tunnel on the way.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = RESUME_TIMEOUT)
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
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = RESUME_TIMEOUT)
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
     * Wounded with a mob after him far from his way (eight cells beyond the end of the tunnel in an open corridor): he walks back to his tunnel
     * and goes up by it instead of resting where he stands.
     */
    @GameTest(batch = "mine_core", template = "marathon_cave", timeoutTicks = RESUME_TIMEOUT)
    public static void farFromHisWayAMobOnHimSendsHimBackToIt(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        c.setItem(2, ItemStack.EMPTY);
        carveMine(h, c, TUNNEL);
        CompanionMineSite mine = c.mineSite();
        for (int k = TUNNEL + 1; k <= TUNNEL + FAR + FOE_BEYOND + 1; k++) for (int dy = 0; dy <= 1; dy++) h.getLevel().setBlock(mine.foot().relative(Direction.WEST, k).above(dy), Blocks.AIR.defaultBlockState(), 2 | 16);
        start(h, c, 16);
        boolean[] hurt = {false};
        h.onEachTick(() -> {
            if (!hurt[0]) {
                if (!"BRANCH".equals(c.expeditionStage())) return;
                BlockPos far = mine.foot().relative(Direction.WEST, TUNNEL + FAR);
                c.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
                // In sight and after him, and it stands where it is: the way back to his tunnel is what is asked, not the race.
                Zombie zombie = h.spawn(EntityType.ZOMBIE, rel(h, mine.foot().relative(Direction.WEST, TUNNEL + FAR + FOE_BEYOND)));
                zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
                zombie.setTarget(c);
                c.setHealth(LOW_HEALTH);
                hurt[0] = true;
                return;
            }
            h.assertTrue(c.isAlive(), "He died");
            // Closing the first step behind him is done only on the way up, which he took: resting where he stood would leave it open.
            if (mine.plug() != null && blocked(h, mine.plug())) h.succeed();
        });
    }

    /** The first solid cell in the columns of the first stands below the entrance (absolute), or null. */
    private static BlockPos plug(GameTestHelper h, CompanionEntity c) {
        CompanionMineSite mine = c.mineSite();
        if (!mine.exists()) return null;
        for (int k = 1; k <= PLUG_STANDS; k++)
            for (BlockPos cell : CompanionShaftPlan.column(CompanionShaftPlan.stand(mine.entrance(), mine.stairDirection(), k))) if (blocked(h, cell)) return cell;
        return null;
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

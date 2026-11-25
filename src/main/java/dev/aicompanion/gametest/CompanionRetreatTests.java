package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
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
import java.util.function.Consumer;

/**
 * Retreat from danger up his own staircase, recovery at the entrance, the next step going back down, and fighting back in the
 * tunnel far from the player. Arena as in the cave tests: origin at Y -60, Oleg picks NORTH at (24,22,24), stand k is
 * (24,22-k,24-k), the tunnel feet at relative height 2, the first tunnel runs north on lanes x=24 and x=25. The chamber is the
 * one of the cave tests: x 26..30, y 2..4, z 3..7, breached by the first advance of the tunnel at (25,2..3,3).
 * The plug is a solid block in the columns of the first two stands below the entrance (the first step up).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionRetreatTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2, TUNNEL_HEAD = DIAMOND_FEET + 1;
    private static final int TIMEOUT = 16000, RESUME_TIMEOUT = 9000, SCAN_EVERY = 5, PLUG_STANDS = 2;
    private static final int RETARGET_EVERY = 20;
    private static final int CHAMBER_X0 = 26, CHAMBER_X1 = 30, CHAMBER_Z0 = 3, CHAMBER_Z1 = 7, STRIP_Z = 1, STRIP_X0 = 22, STRIP_X1 = 25;
    private static final float RECOVERED = 18, LOW = 6;
    private static final String RETREATED = "отступил";
    private static final BlockPos BREACH_FEET = new BlockPos(25, DIAMOND_FEET, 3), BREACH_HEAD = BREACH_FEET.above();

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

    /** Once the order has ended: the shared checks of a finished run, then the test's own. */
    private static void verdict(GameTestHelper h, CompanionEntity c, long id, boolean[] done, Consumer<List<String>> own) {
        if (done[0] || c.isGatheringTask() || c.isWorking() || c.workResult(id) == null) return;
        done[0] = true;
        List<String> problems = new ArrayList<>();
        if (!c.isAlive()) problems.add("dead");
        own.accept(problems);
        String state = state(h, c) + " | result: " + c.workResult(id) + " | " + report(c);
        if (problems.isEmpty()) h.succeed();
        else h.fail("Not as expected: " + problems + " | " + state);
    }

    private static String state(GameTestHelper h, CompanionEntity c) {
        BlockPos plug = plug(h, c);
        return "at " + rel(h, c.blockPosition()).toShortString() + ", hp " + c.getHealth() + ", plug " + (plug == null ? "none" : rel(h, plug).toShortString());
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

    private static Zombie hostile(GameTestHelper h, int x, int z) {
        Zombie zombie = h.spawn(EntityType.ZOMBIE, x, DIAMOND_FEET, z);
        zombie.setPersistenceRequired();
        // The sky light of a freshly built mountain lags behind it: by day a mob in the chamber can burn before the cave opens.
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION));
        return zombie;
    }

    /** Relative positions of the air cells inside the mountain. */
    private static Set<BlockPos> airCells(GameTestHelper h) {
        Set<BlockPos> air = new HashSet<>();
        for (int x = 1; x < SIZE - 1; x++) for (int z = 1; z < SIZE - 1; z++) for (int y = 1; y <= GROUND; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (h.getBlockState(pos).isAir()) air.add(pos);
        }
        return air;
    }

    /** Bedrock floor and walls, deepslate up to Y -52, stone, dirt, grass; the strip of deepslate diamond ore under the tunnel when asked. */
    private static void mountain(GameTestHelper h, boolean strip) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            boolean ore = strip && y == DIAMOND_FEET - 1 && z == STRIP_Z && x >= STRIP_X0 && x <= STRIP_X1;
            var block = y == 0 || edge ? Blocks.BEDROCK
                    : ore ? Blocks.DEEPSLATE_DIAMOND_ORE
                    : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    private static CompanionEntity rig(GameTestHelper h) { return rig(h, 3); }

    private static CompanionEntity rig(GameTestHelper h, int ownerDistance) { return rig(h, ownerDistance, true); }

    /** The player stands this many blocks off along both axes. */
    private static CompanionEntity rig(GameTestHelper h, int ownerDistance, boolean sword) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - ownerDistance, companion.getY(), companion.getZ() - ownerDistance);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 16),
                new ItemStack(Items.COBBLESTONE, 8), sword ? new ItemStack(Items.IRON_SWORD) : ItemStack.EMPTY};
        for (int slot = 0; slot < bag.length; slot++) companion.setItem(slot, bag[slot]);
        return companion;
    }

    private static long start(GameTestHelper h, CompanionEntity c, int count) {
        h.assertTrue(c.startWork("dig_for_diamonds", "", count).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
        return c.activeWorkId();
    }

    /** Position relative to the arena corner; GameTestHelper.relativePos mirrors X and Z in a rotated arena, so the frame is read from absolutePos. */
    private static BlockPos rel(GameTestHelper h, BlockPos world) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO), dx = h.absolutePos(new BlockPos(1, 0, 0)).subtract(origin), dz = h.absolutePos(new BlockPos(0, 0, 1)).subtract(origin);
        BlockPos d = world.subtract(origin);
        return new BlockPos(d.getX() * dx.getX() + d.getZ() * dx.getZ(), d.getY(), d.getX() * dz.getX() + d.getZ() * dz.getZ());
    }

    private static String report(CompanionEntity companion) { return companion.getDeedsForAi() + " | " + companion.getLastTaskResult() + " | bag: " + companion.getStoredItemsSummary(); }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

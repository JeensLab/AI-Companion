package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Caves met by the diamond mine. Arena as in the expedition tests: origin at Y -60, Oleg picks NORTH at (24,22,24), stand k is
 * (24,22-k,24-k), the tunnel feet at relative height 2, the first tunnel runs north on lanes x=24 and x=25 (feet y 2, head y 3).
 * <p>
 * The chamber is carved at x 26..30, y 2..4, z 3..7: its west wall is the plane x=25.5, so the first advance of the north tunnel
 * (z=3) opens lane 1 straight into it. The breach is the two cells (25,2,3) and (25,3,3) and happens at diamond depth. The chamber
 * touches neither the staircase (x=24) nor later advances of the tunnel (z=2 and 1 lie north of it). A long passage leaves its south
 * wall (z=7, x 31..44) clear of the east tunnel (z 4..5). Where a test needs a diamond
 * in the tunnel it is a strip at z=1, y=1, x 22..25, which becomes visible only after the breach is behind him.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCaveTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2, TIMEOUT = 12000;
    private static final int CHAMBER_X0 = 26, CHAMBER_X1 = 30, CHAMBER_Z0 = 3, CHAMBER_Z1 = 7, CORRIDOR_END = 44, CORRIDOR_Z = 7;
    private static final int STRIP_Z = 1, STRIP_X0 = 22, STRIP_X1 = 25, EXPLORE_RANGE = 16;
    private static final BlockPos BREACH_FEET = new BlockPos(25, DIAMOND_FEET, 3), BREACH_HEAD = BREACH_FEET.above();
    private static final BlockPos CAVE_DIAMOND = new BlockPos(29, DIAMOND_FEET - 1, 6);

    private static final class Watch {
        boolean breached, settled; // settled: the verdict is in; succeed() has cleared the arena, so per-tick checks stop
        long breachTick = -1;
        int insideTicks;
        BlockPos farthest = BREACH_FEET;
        BlockPos firstInside;
    }

    /**
     * A lit cave without mobs, one diamond in its floor and none in the tunnel: he walks in, takes it, and leaves the wall between
     * the cave and his mine closed again. He does not wander further than 16 blocks from the breach down a long passage.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void exploresASafeLitCaveTakesTheDiamondAndSealsTheBreach(GameTestHelper h) {
        Set<BlockPos> cave = build(h, false, true, true);
        CompanionEntity c = rig(h);
        h.setBlock(CAVE_DIAMOND, Blocks.DEEPSLATE_DIAMOND_ORE);
        long id = start(h, c, 1);
        Watch w = new Watch();
        h.onEachTick(() -> {
            if (w.settled) return;
            watch(h, c, w);
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            BlockPos feet = rel(h, c.blockPosition());
            if (cave.contains(feet) && feet.distSqr(BREACH_FEET) > EXPLORE_RANGE * EXPLORE_RANGE) w.farthest = feet;
        });
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time. " + summary(h, c, w, cave) + " | " + report(c)));
        h.onEachTick(() -> settle(h, c, id, w, cave, problems -> {
            if (count(c, Items.DIAMOND) < 1) problems.add("the diamond in the cave was not taken");
            if (w.farthest != BREACH_FEET) problems.add("walked " + w.farthest.toShortString() + " in the cave, farther than " + EXPLORE_RANGE + " from the breach");
        }));
    }

    /**
     * A dark cave with two zombies that come for him, no diamond in it, diamonds ahead in the tunnel: the breach is closed, he
     * never sets foot in the cave, digs on and ends with the diamond.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void sealsACaveWithTwoZombiesAndKeepsDigging(GameTestHelper h) {
        Set<BlockPos> cave = build(h, true, false, false);
        CompanionEntity c = rig(h);
        List<Zombie> zombies = List.of(hostile(h, EntityType.ZOMBIE, 28, 5), hostile(h, EntityType.ZOMBIE, 29, 6));
        long id = start(h, c, 1);
        Watch w = new Watch();
        h.onEachTick(() -> {
            if (w.settled) return;
            watch(h, c, w);
            for (Zombie zombie : zombies) if (zombie.isAlive() && zombie.getTarget() == null && h.getTick() % 20 == 0) zombie.setTarget(c);
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            enteredThe(h, c, cave, w);
        });
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time. " + summary(h, c, w, cave) + " | " + report(c)));
        h.onEachTick(() -> settle(h, c, id, w, cave, problems -> {
            if (count(c, Items.DIAMOND) < 1) problems.add("no diamond: he did not dig on");
            if (w.insideTicks > 0) problems.add("stood in the cave for " + w.insideTicks + " ticks, first at " + w.firstInside.toShortString());
        }));
    }

    /**
     * A creeper stands in a dark cave a few blocks from the breach. It is switched off (NoAI), so it can neither swell nor walk:
     * a live one would explode at the breach and kill or maim him before any judgement could be made, which tests luck, not
     * the decision. What is checked is what he does about it: he never touches it, never enters, and closes the breach.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void backsOffFromACreeperAndSealsWithoutHittingIt(GameTestHelper h) {
        Set<BlockPos> cave = build(h, true, false, false);
        CompanionEntity c = rig(h);
        Creeper creeper = hostile(h, EntityType.CREEPER, 28, 5);
        creeper.setNoAi(true);
        long id = start(h, c, 1);
        Watch w = new Watch();
        h.onEachTick(() -> {
            if (w.settled) return;
            watch(h, c, w);
            h.assertTrue(creeper.isAlive() && creeper.getHealth() >= creeper.getMaxHealth(), "He hurt the creeper: health " + creeper.getHealth() + "/" + creeper.getMaxHealth()
                    + ", alive " + creeper.isAlive() + " | " + summary(h, c, w, cave) + " | " + report(c));
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            enteredThe(h, c, cave, w);
        });
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time. " + summary(h, c, w, cave) + " | " + report(c)));
        h.onEachTick(() -> settle(h, c, id, w, cave, problems -> {
            if (w.insideTicks > 0) problems.add("stood in the cave for " + w.insideTicks + " ticks, first at " + w.firstInside.toShortString());
        }));
    }

    /** Once the order has ended: the breach was reached at all (otherwise the test proves nothing), no air path joins the cave and his mine, plus the test's own checks. */
    private static void settle(GameTestHelper h, CompanionEntity c, long id, Watch w, Set<BlockPos> cave, Consumer<List<String>> own) {
        if (c.isGatheringTask() || c.isWorking() || c.workResult(id) == null) return;
        w.settled = true;
        List<String> problems = new ArrayList<>();
        if (!w.breached) problems.add("SETUP: the tunnel never broke into the cave");
        BlockPos leak = leak(h, cave);
        if (leak != null) problems.add("the breach is open: air runs from the cave into " + leak.toShortString());
        if (!c.isAlive()) problems.add("dead");
        own.accept(problems);
        String state = summary(h, c, w, cave) + " | result: " + c.workResult(id) + " | " + report(c);
        if (problems.isEmpty()) h.succeed();
        else h.fail("Cave not handled: " + problems + " | " + state);
    }

    private static String summary(GameTestHelper h, CompanionEntity c, Watch w, Set<BlockPos> cave) {
        BlockPos leak = leak(h, cave);
        return "at " + rel(h, c.blockPosition()).toShortString() + ", hp " + c.getHealth() + ", breach opened at tick " + w.breachTick
                + ", breach cells " + h.getBlockState(BREACH_FEET).getBlock() + "/" + h.getBlockState(BREACH_HEAD).getBlock()
                + ", leak " + (leak == null ? "none" : leak.toShortString()) + ", ticks in cave " + w.insideTicks;
    }

    private static void watch(GameTestHelper h, CompanionEntity c, Watch w) {
        if (w.breached || !isOpen(h, BREACH_FEET) && !isOpen(h, BREACH_HEAD)) return;
        w.breached = true;
        w.breachTick = h.getTick();
        AICompanionMod.LOGGER.info("Cave test: breach {} / {} opened at tick {}, he stands at {}", BREACH_FEET.toShortString(), BREACH_HEAD.toShortString(), w.breachTick, rel(h, c.blockPosition()).toShortString());
    }

    private static void enteredThe(GameTestHelper h, CompanionEntity c, Set<BlockPos> cave, Watch w) {
        BlockPos feet = rel(h, c.blockPosition());
        if (!cave.contains(feet)) return;
        if (w.firstInside == null) w.firstInside = feet;
        w.insideTicks++;
    }

    /** The first open cell outside the cave reachable from the cave through open cells, or null when the cave is closed off. */
    private static BlockPos leak(GameTestHelper h, Set<BlockPos> cave) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos cell : cave) if (isOpen(h, cell) && seen.add(cell)) queue.add(cell);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (!isOpen(h, next) || !seen.add(next)) continue;
                if (!cave.contains(next)) return next;
                queue.add(next);
            }
        }
        return null;
    }

    /** No collision shape and no fluid: what a zombie walks through. A torch counts as open. */
    private static boolean isOpen(GameTestHelper h, BlockPos rel) {
        var state = h.getBlockState(rel);
        var abs = h.absolutePos(rel);
        return state.getCollisionShape(h.getLevel(), abs).isEmpty() && state.getFluidState().isEmpty();
    }

    /** The chamber (and, when asked, a long passage east of it), lit or dark, and the diamond strip in the tunnel when asked. */
    private static Set<BlockPos> build(GameTestHelper h, boolean strip, boolean corridor, boolean lit) {
        mountain(h, strip);
        Set<BlockPos> cells = new HashSet<>();
        for (int x = CHAMBER_X0; x <= CHAMBER_X1; x++) for (int z = CHAMBER_Z0; z <= CHAMBER_Z1; z++) for (int y = DIAMOND_FEET; y <= DIAMOND_FEET + 2; y++) cells.add(new BlockPos(x, y, z));
        if (corridor) for (int x = CHAMBER_X1 + 1; x <= CORRIDOR_END; x++) for (int y = DIAMOND_FEET; y <= DIAMOND_FEET + 1; y++) cells.add(new BlockPos(x, y, CORRIDOR_Z));
        for (BlockPos cell : cells) h.setBlock(cell, Blocks.AIR);
        if (lit) {
            h.setBlock(new BlockPos(27, DIAMOND_FEET, CHAMBER_Z1), Blocks.TORCH);
            h.setBlock(new BlockPos(29, DIAMOND_FEET, CHAMBER_Z1), Blocks.TORCH);
            h.setBlock(new BlockPos(28, DIAMOND_FEET + 3, 5), Blocks.GLOWSTONE);
            if (corridor) for (int x : new int[]{33, 39, CORRIDOR_END}) h.setBlock(new BlockPos(x, DIAMOND_FEET + 2, CORRIDOR_Z), Blocks.GLOWSTONE);
        }
        return cells;
    }

    private static <T extends Mob> T hostile(GameTestHelper h, EntityType<T> type, int x, int z) {
        T mob = h.spawn(type, x, DIAMOND_FEET, z);
        mob.setPersistenceRequired();
        return mob;
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

    private static CompanionEntity rig(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 16)};
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

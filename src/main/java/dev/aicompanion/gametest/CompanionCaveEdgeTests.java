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
import net.minecraft.world.entity.animal.Pig;
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
 * Edge cases of caves met by the diamond mine, from the review of the cave handling. Arena as in the cave tests: origin at Y -60,
 * Oleg picks NORTH at (24,22,24), stand k is (24,22-k,24-k), the tunnel feet at relative height 2, the first tunnel runs north on
 * lanes x=24 and x=25 (feet y 2, head y 3), its first advance being z=3. Mobs stand still (NoAI) wherever a live one would walk
 * out of the cave on its own and change what is being tested.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCaveEdgeTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2, TIMEOUT = 12000;
    private static final int TURN_WITHIN = 80, PIG_STAYS = 100;
    private static final BlockPos BREACH_FEET = new BlockPos(25, DIAMOND_FEET, 3), BREACH_HEAD = BREACH_FEET.above();
    private static final BlockPos PLAN_FEET = new BlockPos(24, DIAMOND_FEET, 3), PLAN_HEAD = PLAN_FEET.above();
    /** The cell ahead of the stand at height 11 of the staircase: the passage beside the staircase ends in it, and it is dug by the next step. */
    private static final String SEALED_SAID = "Пролом заделал", CAVE_SAID = "Наткнулся на пещеру";

    private static final class Watch {
        boolean breached, settled; // settled: the verdict is in; succeed() has cleared the arena, so per-tick checks stop
        long id, breachTick = -1, turnTick = -1;
        int insideTicks;
        BlockPos firstInside;
    }

    /**
     * The order is stopped the moment the last cell of the breach opens, before the cave has been judged, and given again: the
     * zombie chamber lies open to his tunnel, and he has to notice, close it and never set foot in it.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void judgesAnOpenBreachLeftByAnInterruptedStep(GameTestHelper h) {
        mountain(h, cells(22, 25, 1, 1, 1, 1));
        Set<BlockPos> cave = carve(h, 26, 30, 3, 5, 3, 7);
        CompanionEntity c = rig(h, 16);
        still(h, EntityType.ZOMBIE, 28, 3, 5);
        still(h, EntityType.ZOMBIE, 29, 3, 6);
        Watch w = new Watch();
        w.id = start(h, c, 1);
        h.onEachTick(() -> {
            if (w.settled) return;
            if (!w.breached && isOpen(h, BREACH_HEAD)) {
                w.breached = true;
                w.breachTick = h.getTick();
                c.setFollowing(false);
                w.id = start(h, c, 1);
            }
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            BlockPos feet = rel(h, c.blockPosition());
            if (!cave.contains(feet)) return;
            if (w.firstInside == null) w.firstInside = feet;
            w.insideTicks++;
        });
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time. " + summary(h, c, w, cave) + " | " + report(c)));
        h.onEachTick(() -> settle(h, c, w, cave, problems -> {
            if (!w.breached) problems.add("SETUP: the tunnel never broke into the cave");
            if (w.insideTicks > 0) problems.add("stood in the cave for " + w.insideTicks + " ticks, first at " + w.firstInside.toShortString());
        }));
    }

    /**
     * Two zombies stand in a pocket right ahead of the north tunnel, so the plug goes into the cell the advance has just dug and has
     * to walk through. He turns to the next direction at once instead of standing at the plug until the walk is given up.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void turnsAtACaveStraightAheadWithoutWaitingOutTheWalk(GameTestHelper h) {
        mountain(h, cells(18, 22, 1, 1, 3, 4));
        Set<BlockPos> cave = carve(h, 24, 24, DIAMOND_FEET, DIAMOND_FEET + 1, 1, 2);
        CompanionEntity c = rig(h, 16);
        still(h, EntityType.ZOMBIE, 24, DIAMOND_FEET, 2);
        still(h, EntityType.ZOMBIE, 24, DIAMOND_FEET, 1);
        Watch w = new Watch();
        w.id = start(h, c, 1);
        h.onEachTick(() -> {
            if (w.settled) return;
            if (!w.breached && isOpen(h, PLAN_FEET)) { w.breached = true; w.breachTick = h.getTick(); }
            if (w.breached && w.turnTick < 0 && c.mineSite().tunnels().size() >= 2) w.turnTick = h.getTick();
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            h.assertTrue(!w.breached || w.turnTick >= 0 || h.getTick() - w.breachTick <= TURN_WITHIN, "Still on the north tunnel " + (h.getTick() - w.breachTick)
                    + " ticks after the breach (turn within " + TURN_WITHIN + "), cells ahead " + h.getBlockState(PLAN_FEET).getBlock() + "/" + h.getBlockState(PLAN_HEAD).getBlock() + " | " + summary(h, c, w, cave) + " | " + report(c));
        });
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time. " + summary(h, c, w, cave) + " | " + report(c)));
        h.onEachTick(() -> settle(h, c, w, cave, problems -> {
            if (!w.breached) problems.add("SETUP: the tunnel never broke into the pocket");
            if (count(c, Items.DIAMOND) < 1) problems.add("the order did not go on elsewhere: no diamond");
            if (c.workResult(w.id).message().contains("застрял на ходу")) problems.add("the result blames a stuck walk");
        }));
    }

    /**
     * Two diamonds one above the other in the wall beside the tunnel: the second is broken next to the hole the first left. That
     * hole is an ore pit, not a cave: nothing is reported, nothing is plugged, he does not go to look.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void anOrePitBesideTheTunnelIsNotACave(GameTestHelper h) {
        Set<BlockPos> pit = cells(23, 23, 2, 3, 3, 3);
        Set<BlockPos> ores = cells(22, 25, 1, 1, 1, 1);
        ores.addAll(pit);
        mountain(h, ores);
        CompanionEntity c = rig(h, 16);
        Watch w = new Watch();
        w.id = start(h, c, 2);
        h.onEachTick(() -> h.assertTrue(w.settled || c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c)));
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time. " + summary(h, c, w, Set.of()) + " | " + report(c)));
        h.onEachTick(() -> settle(h, c, w, Set.of(), problems -> {
            for (BlockPos ore : pit) if (h.getBlockState(ore).is(Blocks.DEEPSLATE_DIAMOND_ORE)) problems.add("SETUP: the ore at " + ore.toShortString() + " was not mined");
            String said = c.workResult(w.id).message();
            if (said.contains(CAVE_SAID)) problems.add("the report speaks of a cave: " + said);
            if (said.contains(SEALED_SAID)) problems.add("the report speaks of a plug: " + said);
            if (!c.mineSite().seals().isEmpty()) problems.add("a sealing block was spent: " + c.mineSite().seals().size() + " in the record");
        }));
    }

    /**
     * A dark cave (no torches: dangerous) with a pig standing in its first cell, right at the breach. The pig is no threat and walks
     * off sooner or later; his own tunnel cell is not plugged for it, and the cave is closed from its side once the pig has gone.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aPassiveMobInADarkCaveDoesNotPlugHisTunnel(GameTestHelper h) {
        mountain(h, cells(22, 25, 1, 1, 1, 1));
        Set<BlockPos> cave = carve(h, 26, 30, DIAMOND_FEET, DIAMOND_FEET + 2, 3, 7);
        CompanionEntity c = rig(h, 0);
        Pig pig = still(h, EntityType.PIG, 26, DIAMOND_FEET, 3);
        Watch w = new Watch();
        w.id = start(h, c, 1);
        h.onEachTick(() -> {
            if (w.settled) return;
            if (!w.breached && (isOpen(h, BREACH_FEET) || isOpen(h, BREACH_HEAD))) { w.breached = true; w.breachTick = h.getTick(); }
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            if (!w.breached || h.getTick() - w.breachTick != PIG_STAYS) return;
            h.assertTrue(isOpen(h, BREACH_FEET) && isOpen(h, BREACH_HEAD), "He plugged his own tunnel cell because of a pig: " + h.getBlockState(BREACH_FEET).getBlock() + "/"
                    + h.getBlockState(BREACH_HEAD).getBlock() + " | " + summary(h, c, w, cave) + " | " + report(c));
            pig.discard();
        });
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time. " + summary(h, c, w, cave) + " | " + report(c)));
        h.onEachTick(() -> settle(h, c, w, cave, problems -> {
            if (!w.breached) problems.add("SETUP: the tunnel never broke into the cave");
            if (!isOpen(h, BREACH_FEET) || !isOpen(h, BREACH_HEAD)) problems.add("his own breach cells are plugged");
        }));
    }

    /** Once the order has ended: no air path joins the cave and his mine, plus the test's own checks. */
    private static void settle(GameTestHelper h, CompanionEntity c, Watch w, Set<BlockPos> cave, Consumer<List<String>> own) {
        if (w.settled || c.isGatheringTask() || c.isWorking() || c.workResult(w.id) == null) return;
        w.settled = true;
        List<String> problems = new ArrayList<>();
        BlockPos leak = leak(h, cave);
        if (leak != null) problems.add("the breach is open: air runs from the cave into " + leak.toShortString());
        if (!c.isAlive()) problems.add("dead");
        own.accept(problems);
        String state = summary(h, c, w, cave) + " | result: " + c.workResult(w.id) + " | " + report(c);
        if (problems.isEmpty()) h.succeed();
        else h.fail("Cave not handled: " + problems + " | " + state);
    }

    private static String summary(GameTestHelper h, CompanionEntity c, Watch w, Set<BlockPos> cave) {
        BlockPos leak = leak(h, cave);
        return "at " + rel(h, c.blockPosition()).toShortString() + ", hp " + c.getHealth() + ", breach at tick " + w.breachTick + ", turn at tick " + w.turnTick
                + ", leak " + (leak == null ? "none" : leak.toShortString()) + ", ticks in cave " + w.insideTicks;
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

    private static Set<BlockPos> cells(int x0, int x1, int y0, int y1, int z0, int z1) {
        Set<BlockPos> cells = new HashSet<>();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) cells.add(new BlockPos(x, y, z));
        return cells;
    }

    private static Set<BlockPos> carve(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
        Set<BlockPos> cells = cells(x0, x1, y0, y1, z0, z1);
        for (BlockPos cell : cells) h.setBlock(cell, Blocks.AIR);
        return cells;
    }

    private static <T extends Mob> T still(GameTestHelper h, EntityType<T> type, int x, int y, int z) {
        T mob = h.spawn(type, x, y, z);
        mob.setPersistenceRequired();
        mob.setNoAi(true);
        return mob;
    }

    /** Bedrock floor and walls, deepslate up to Y -52, stone, dirt, grass; diamond ore in the given cells. */
    private static void mountain(GameTestHelper h, Set<BlockPos> ores) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            var block = y == 0 || edge ? Blocks.BEDROCK
                    : ores.contains(new BlockPos(x, y, z)) ? Blocks.DEEPSLATE_DIAMOND_ORE
                    : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    private static CompanionEntity rig(GameTestHelper h, int torches) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        List<ItemStack> bag = new ArrayList<>(List.of(new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8),
                new ItemStack(Items.COBBLESTONE, 8), new ItemStack(Items.IRON_SWORD)));
        if (torches > 0) bag.add(new ItemStack(Items.TORCH, torches));
        for (int slot = 0; slot < bag.size(); slot++) companion.setItem(slot, bag.get(slot));
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

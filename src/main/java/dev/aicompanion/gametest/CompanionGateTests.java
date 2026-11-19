package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The player's garden behind a fence: a shut wooden gate is a fence to the game's path navigation (PathType.FENCE), so the
 * companion went round it or could not come in at all (idea BDGLW, #105). He opens a wooden gate on his route, walks through it
 * and shuts it behind him, as a door; a gate a redstone signal decides is left alone; a gate left open when he gives up is shut.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGateTests {
    private static final int GROUND = 5;
    /** The garden: the wall ring from (x0, z0) to (x0 + 10, z0 + 10), the gate in the middle of the west wall or of the south one. */
    private static final int SIZE = 10, GATE_Z = 5, GATE_X = 5;

    /** «Принеси морковь из-за забора» with a shut gate between him and the carrots: he opens it, walks in and shuts it behind him. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aShutGateIsOpenedWalkedThroughAndShutBehind(GameTestHelper h) {
        ground(h);
        garden(h, 16, 12, true, false);
        gate(h, westGate(16, 12), Direction.EAST, false, false);
        crops(h, 16, 12);
        CompanionEntity c = spawn(h, 11, 17);
        BlockPos gate = westGate(16, 12);
        boolean[] opened = {false};
        h.onEachTick(() -> { if (isOpen(h, gate)) opened[0] = true; });
        var order = sayModel(h, c, "принеси морковь из-за забора", "[{\"action\":\"obtain_item\",\"item\":\"minecraft:carrot\",\"count\":1}]",
                "gametest-gate-walk");
        h.succeedWhen(() -> {
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "The order is still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.CARROT) >= 1, "No carrot from the garden behind the gate: " + order.join().report());
            h.assertTrue(opened[0], "The gate was never opened");
            h.assertFalse(isOpen(h, gate), "The gate was left open");
        });
    }

    /** A gate the signal holds open: he walks through it and leaves it as the signal has it, never shutting it behind him. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aPoweredOpenGateIsWalkedThroughAndLeftOpen(GameTestHelper h) {
        ground(h);
        garden(h, 16, 12, true, false);
        h.setBlock(new BlockPos(16, GROUND + 1, 12 + GATE_Z - 1), Blocks.REDSTONE_BLOCK);
        gate(h, westGate(16, 12), Direction.EAST, true, true);
        crops(h, 16, 12);
        CompanionEntity c = spawn(h, 11, 17);
        BlockPos gate = westGate(16, 12);
        boolean[] shut = {false};
        h.onEachTick(() -> { if (!isOpen(h, gate)) shut[0] = true; });
        var order = say(h, c, "собери урожай", "gametest-gate-powered-open");
        h.succeedWhen(() -> {
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "The harvest is still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.CARROT) >= 1, "No carrot from the garden behind the open gate: " + order.join().report());
            h.assertTrue(isOpen(h, gate), "He shut a gate the redstone signal holds open");
            h.assertFalse(shut[0], "The powered gate was shut at some point");
        });
    }

    /** A shut gate under a signal cannot be opened by hand: he goes round through the gap and does not stall at it. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aShutPoweredGateIsLeftAloneAndTheGapIsTaken(GameTestHelper h) {
        ground(h);
        garden(h, 16, 12, true, true);
        h.setBlock(new BlockPos(16, GROUND + 1, 12 + GATE_Z - 1), Blocks.REDSTONE_BLOCK);
        gate(h, westGate(16, 12), Direction.EAST, false, true);
        crops(h, 16, 12);
        CompanionEntity c = spawn(h, 11, 17);
        BlockPos gate = westGate(16, 12);
        boolean[] opened = {false};
        h.onEachTick(() -> { if (isOpen(h, gate)) opened[0] = true; });
        var order = say(h, c, "собери урожай", "gametest-gate-powered-shut");
        h.succeedWhen(() -> {
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "He stalled at the shut gate: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.CARROT) >= 1, "No carrot: he did not go round through the gap: " + order.join().report());
            h.assertFalse(opened[0], "He opened a gate the redstone signal holds shut");
        });
    }

    /** Giving up at the gate: stopped while the gate stands open, he shuts it behind him and does not leave it open. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aGateIsShutWhenTheOrderIsGivenUp(GameTestHelper h) {
        ground(h);
        garden(h, 16, 12, true, false);
        gate(h, westGate(16, 12), Direction.EAST, false, false);
        crops(h, 16, 12);
        CompanionEntity c = spawn(h, 11, 17);
        BlockPos gate = westGate(16, 12);
        var givenUp = new AtomicBoolean();
        var port = new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-gate-give-up", givenUp::get);
        var order = say(h, c, "собери урожай", port);
        h.onEachTick(() -> {
            if (!givenUp.get() && isOpen(h, gate)) { givenUp.set(true); port.cancelWork(); }
        });
        h.succeedWhen(() -> {
            h.assertTrue(givenUp.get(), "He never came to the gate");
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "The stopped order is still running: " + c.getLastTaskResult());
            h.assertFalse(isOpen(h, gate), "The gate was left open when he gave up");
        });
    }

    /** A free way in in front of him, the shut gate in another wall: he takes the free way and never touches the gate. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aFreeWayInIsTakenAndTheGateIsNotTouched(GameTestHelper h) {
        ground(h);
        garden(h, 16, 12, false, false);
        gate(h, southGate(16, 12), Direction.SOUTH, false, false);
        crops(h, 16, 12);
        CompanionEntity c = spawn(h, 11, 17);
        BlockPos gate = southGate(16, 12);
        boolean[] opened = {false};
        h.onEachTick(() -> { if (isOpen(h, gate)) opened[0] = true; });
        var order = say(h, c, "собери урожай", "gametest-gate-free-way");
        h.succeedWhen(() -> {
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "The harvest is still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.CARROT) >= 1, "No carrot: " + order.join().report());
            h.assertFalse(opened[0], "He went to the shut gate though a free way in stood in front of him");
        });
    }

    /** The switch off: a shut gate is a fence to him as before, and he does not touch it. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void withTheSwitchOffTheGateIsNotTouched(GameTestHelper h) {
        ground(h);
        garden(h, 16, 12, true, false);
        gate(h, westGate(16, 12), Direction.EAST, false, false);
        crops(h, 16, 12);
        CompanionEntity c = spawn(h, 11, 17);
        c.setFenceGates(false);
        BlockPos gate = westGate(16, 12);
        boolean[] opened = {false};
        h.onEachTick(() -> { if (isOpen(h, gate)) opened[0] = true; });
        var order = say(h, c, "собери урожай", "gametest-gate-switch-off");
        h.succeedWhen(() -> {
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "The harvest is still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.CARROT) == 0, "He reached into the garden past the shut gate: " + order.join().report());
            h.assertFalse(opened[0], "The gate was opened with the switch off");
            h.assertFalse(isOpen(h, gate), "The gate was left open");
        });
    }

    /**
     * The gate he was stopped at opened by another hand: he walks through it and leaves it open, it was not his to shut.
     * The state he found the gate in is remembered, so a gate that stood open when he came to it stays open.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aGateOpenedByAnotherHandStaysOpen(GameTestHelper h) {
        ground(h);
        garden(h, 16, 12, true, false);
        gate(h, westGate(16, 12), Direction.EAST, false, false);
        crops(h, 16, 12);
        CompanionEntity c = spawn(h, 11, 17);
        BlockPos gate = westGate(16, 12);
        boolean[] byHand = {false};
        h.onEachTick(() -> {
            if (!byHand[0] && c.horizontalCollision && !isOpen(h, gate)) {
                byHand[0] = true;
                h.setBlock(gate, h.getBlockState(gate).setValue(FenceGateBlock.OPEN, true));
            }
        });
        var order = sayModel(h, c, "принеси морковь из-за забора", "[{\"action\":\"obtain_item\",\"item\":\"minecraft:carrot\",\"count\":1}]",
                "gametest-gate-opened-by-other");
        h.succeedWhen(() -> {
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "The order is still running: " + c.getLastTaskResult());
            h.assertTrue(byHand[0], "He never came to the shut gate");
            h.assertTrue(count(c, Items.CARROT) >= 1, "No carrot from the garden behind the gate: " + order.join().report());
            h.assertTrue(isOpen(h, gate), "He shut a gate someone else opened");
        });
    }

    /** The signal arrives while he stands in the gate he opened: he is not trapped in it and does not force it shut. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aGatePoweredWhileHeStandsInItIsLeftOpen(GameTestHelper h) {
        ground(h);
        garden(h, 16, 12, true, false);
        gate(h, westGate(16, 12), Direction.EAST, false, false);
        crops(h, 16, 12);
        CompanionEntity c = spawn(h, 11, 17);
        BlockPos gate = westGate(16, 12), at = h.absolutePos(gate);
        boolean[] powered = {false}, shutAfterPower = {false};
        h.onEachTick(() -> {
            if (!powered[0] && isOpen(h, gate) && c.distanceToSqr(at.getX() + .5, c.getY(), at.getZ() + .5) < 1.0) {
                powered[0] = true;
                h.setBlock(new BlockPos(16, GROUND + 1, 12 + GATE_Z - 1), Blocks.REDSTONE_BLOCK);
            } else if (powered[0] && !isOpen(h, gate)) shutAfterPower[0] = true;
        });
        var order = sayModel(h, c, "принеси морковь из-за забора", "[{\"action\":\"obtain_item\",\"item\":\"minecraft:carrot\",\"count\":1}]",
                "gametest-gate-powered-in-it");
        h.succeedWhen(() -> {
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "The order is still running: " + c.getLastTaskResult());
            h.assertTrue(powered[0], "The signal never arrived while he stood in the gate");
            h.assertTrue(count(c, Items.CARROT) >= 1, "No carrot: he was trapped in the powered gate: " + order.join().report());
            h.assertTrue(isOpen(h, gate), "He forced shut a gate the redstone signal holds open");
            h.assertFalse(shutAfterPower[0], "The gate was shut after the signal arrived");
        });
    }

    /** The signal drops while he is beside the powered gate: it shuts, and he opens it by hand, walks through and shuts it. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aGateWhoseSignalDropsBesideHimIsOpenedByHand(GameTestHelper h) {
        ground(h);
        garden(h, 16, 12, true, false);
        h.setBlock(new BlockPos(16, GROUND + 1, 12 + GATE_Z - 1), Blocks.REDSTONE_BLOCK);
        gate(h, westGate(16, 12), Direction.EAST, true, true);
        crops(h, 16, 12);
        CompanionEntity c = spawn(h, 11, 17);
        BlockPos gate = westGate(16, 12), at = h.absolutePos(gate);
        boolean[] dropped = {false}, openedByHand = {false};
        h.onEachTick(() -> {
            if (!dropped[0] && c.distanceToSqr(at.getX() + .5, c.getY(), at.getZ() + .5) < 9.0) {
                dropped[0] = true;
                h.setBlock(new BlockPos(16, GROUND + 1, 12 + GATE_Z - 1), Blocks.AIR);
            } else if (dropped[0] && isOpen(h, gate)) openedByHand[0] = true;
        });
        var order = sayModel(h, c, "принеси морковь из-за забора", "[{\"action\":\"obtain_item\",\"item\":\"minecraft:carrot\",\"count\":1}]",
                "gametest-gate-signal-drops");
        h.succeedWhen(() -> {
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "He stalled at the gate whose signal dropped: " + c.getLastTaskResult());
            h.assertTrue(dropped[0], "He never came beside the powered gate");
            h.assertTrue(count(c, Items.CARROT) >= 1, "No carrot: " + order.join().report());
            h.assertTrue(openedByHand[0], "He never opened the gate by hand after the signal dropped");
            h.assertFalse(isOpen(h, gate), "The gate he opened by hand was left open");
        });
    }

    /** Two shut gates one after the other on the way to the crop: both opened, both shut behind him, the crop taken. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void twoGatesOnTheWayAreOpenedWalkedThroughAndShutBehind(GameTestHelper h) {
        ground(h);
        corridor(h, 13, 17);
        BlockPos first = new BlockPos(16, GROUND + 1, 17), second = new BlockPos(21, GROUND + 1, 17);
        gate(h, first, Direction.EAST, false, false);
        gate(h, second, Direction.EAST, false, false);
        h.setBlock(new BlockPos(25, GROUND, 17), Blocks.FARMLAND);
        h.setBlock(new BlockPos(25, GROUND + 1, 17), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
        CompanionEntity c = spawn(h, 11, 17);
        boolean[] firstOpened = {false}, secondOpened = {false};
        h.onEachTick(() -> {
            if (isOpen(h, first)) firstOpened[0] = true;
            if (isOpen(h, second)) secondOpened[0] = true;
        });
        var order = sayModel(h, c, "принеси морковь из-за забора", "[{\"action\":\"obtain_item\",\"item\":\"minecraft:carrot\",\"count\":1}]",
                "gametest-gate-two-in-a-row");
        h.succeedWhen(() -> {
            h.assertTrue(order.isDone() && !order.isCompletedExceptionally(), "The order is still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.CARROT) >= 1, "No carrot from behind the two gates: " + order.join().report());
            h.assertTrue(firstOpened[0], "The first gate was never opened");
            h.assertTrue(secondOpened[0], "The second gate was never opened");
            h.assertFalse(isOpen(h, first), "The first gate was left open");
            h.assertFalse(isOpen(h, second), "The second gate was left open");
        });
    }

    /** Taken out of the world in the gateway: the gate he opened is shut, not left open behind him. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aGateIsShutWhenHeIsRemovedInIt(GameTestHelper h) {
        ground(h);
        garden(h, 16, 12, true, false);
        gate(h, westGate(16, 12), Direction.EAST, false, false);
        crops(h, 16, 12);
        CompanionEntity c = spawn(h, 11, 17);
        BlockPos gate = westGate(16, 12), at = h.absolutePos(gate);
        boolean[] removed = {false};
        sayModel(h, c, "принеси морковь из-за забора", "[{\"action\":\"obtain_item\",\"item\":\"minecraft:carrot\",\"count\":1}]",
                "gametest-gate-removed-in-it");
        h.onEachTick(() -> {
            if (!removed[0] && isOpen(h, gate) && c.distanceToSqr(at.getX() + .5, c.getY(), at.getZ() + .5) < 1.0) {
                removed[0] = true;
                c.discard();
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(removed[0], "He never stood in the gate");
            h.assertFalse(isOpen(h, gate), "The gate he opened was left open when he was removed from the world");
        });
    }

    // ---- the garden ----

    /**
     * A walled corridor east of him, two blocks high so the crop at its far end is seen only from inside, the gates' places
     * left for the caller to fill last.
     */
    private static void corridor(GameTestHelper h, int x0, int z0) {
        for (int x = 0; x <= 14; x++) for (int y = GROUND + 1; y <= GROUND + 2; y++) {
            h.setBlock(new BlockPos(x0 + x, y, z0 - 1), Blocks.OAK_FENCE);
            h.setBlock(new BlockPos(x0 + x, y, z0 + 1), Blocks.OAK_FENCE);
        }
        for (int y = GROUND + 1; y <= GROUND + 2; y++) h.setBlock(new BlockPos(x0 + 14, y, z0), Blocks.OAK_FENCE);
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            if (y <= GROUND) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            else if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
            else h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    /**
     * The fence ring and its farmland. The gate's place in the wall is left for the caller to fill, last, after anything that
     * would power it; {@code gap}: another opening three blocks along the west wall; {@code gateInWest}: the gate's place is
     * there, otherwise in the middle of the south wall, and the west wall is left open in front of him.
     */
    private static void garden(GameTestHelper h, int x0, int z0, boolean gateInWest, boolean gap) {
        for (int x = 0; x <= SIZE; x++) for (int z = 0; z <= SIZE; z++) {
            if (x != 0 && x != SIZE && z != 0 && z != SIZE) continue;
            if (gateInWest && x == 0 && z == GATE_Z) continue;
            if (!gateInWest && x == GATE_X && z == SIZE) continue;
            if (!gateInWest && x == 0 && z == GATE_Z) continue;
            if (gap && x == 0 && z == GATE_Z - 3) continue;
            h.setBlock(new BlockPos(x0 + x, GROUND + 1, z0 + z), Blocks.OAK_FENCE);
        }
        for (int x = 1; x < SIZE; x++) for (int z = 1; z < SIZE; z++)
            h.setBlock(new BlockPos(x0 + x, GROUND, z0 + z), Blocks.FARMLAND);
    }

    private static BlockPos westGate(int x0, int z0) { return new BlockPos(x0, GROUND + 1, z0 + GATE_Z); }
    private static BlockPos southGate(int x0, int z0) { return new BlockPos(x0 + GATE_X, GROUND + 1, z0 + SIZE); }

    /** The gate, with the state the caller found it in. */
    private static void gate(GameTestHelper h, BlockPos at, Direction facing, boolean open, boolean powered) {
        h.setBlock(at, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, facing)
                .setValue(FenceGateBlock.OPEN, open).setValue(FenceGateBlock.POWERED, powered));
    }

    /** Ripe carrots deep in the garden: far enough from every wall that he has to walk in, not reach over the fence. */
    private static void crops(GameTestHelper h, int x0, int z0) {
        for (int[] cell : new int[][]{{6, 5}, {8, 3}, {7, 7}}) {
            h.setBlock(new BlockPos(x0 + cell[0], GROUND + 1, z0 + cell[1]), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
        }
    }

    private static boolean isOpen(GameTestHelper h, BlockPos gate) { return h.getBlockState(gate).getValue(FenceGateBlock.OPEN); }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x - 2, GROUND + 1, z));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setLifestyle(false, false);
        return c;
    }

    /** The player's phrase through the router and the runner: a local order, no model. */
    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase, String trace) {
        return say(h, c, phrase, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), trace, () -> false));
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase, CompanionServerPlanPort port) {
        String command = CompanionLocalCommands.route(phrase, "Олег");
        h.assertTrue(command != null, "«" + phrase + "» is left to the model");
        return CompanionPlanRunner.run(CompanionLocalCommands.actions(command), port);
    }

    /** A phrase the router leaves to the model, with the plan the chat would hand the runner for it. */
    private static CompletableFuture<CompanionPlanRunner.Outcome> sayModel(GameTestHelper h, CompanionEntity c, String phrase, String json, String trace) {
        var reply = CompanionPlanRequirements.apply(phrase, new CompanionReply("", CompanionPlanCodec.parsePlan(JsonParser.parseString(json))));
        return CompanionPlanRunner.run(reply.actions(), new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), trace, () -> false));
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMineSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;

/**
 * Runs q1a/void-4, q1a/water, q1b/void-2 and q1e/void-4: the goal came back to the same spot with the same record and stopped five times with the
 * same words, each order walking the same dead chain. A stop that comes again must change the way: the stairs anew from where he stands with the
 * refusals forgotten, then the way up, whatever it takes. What was dangerous stays forbidden all the same.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineChangeTests {
    private static final String SURFACE = "surface";
    /** How high in the stone the shut pocket lies, how many stops of a goal fail the test, and the cells of the staircase the climbs go up. */
    private static final int POCKET_Y = 14, MAX_STOPS = 5, STAIR_STEPS = 7, SURFACE_MARGIN = 2;
    private static final BlockPos POCKET = new BlockPos(CENTER, POCKET_Y, CENTER);

    private CompanionMineChangeTests() { }

    /**
     * Run q1a/void-4: the stairs lost, the walk to the entrance out, every side of the spot refused, the floor banned, all sides of the entrance
     * tried, no record of a way up. Only refusals hold him: the stairs start anew from the spot and the diamond below is reached.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void startsTheStairsAnewWhenTheSameStopComesAgainAndReachesTheDiamond(GameTestHelper h) {
        ServerPlayer player = goalPlayer(h);
        CompanionEntity c = shutPocket(h, player);
        pursue(h, player, c, () -> count(c, Items.DIAMOND) >= 1, () -> { });
    }

    /**
     * Run q1a/water and q1b/void-2: two sides of the spot end in lava and two in a cave he plugged and fled from. No side is taken even with the refusals
     * forgotten, no plug and no lava is opened, and the rock over him is the way out to the surface.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void leavesForTheSurfaceWithoutTakingALavaSideOrAPluggedCave(GameTestHelper h) {
        ServerPlayer player = goalPlayer(h);
        CompanionEntity c = shutPocket(h, player);
        BlockPos pocket = POCKET;
        List<BlockPos> plugs = new ArrayList<>(), walls = new ArrayList<>();
        Direction[] across = {Direction.EAST, Direction.WEST}, along = {Direction.NORTH, Direction.SOUTH};
        for (Direction side : across) {
            for (int y = 0; y <= 1; y++) {
                h.setBlock(pocket.relative(side, 2).above(y), Blocks.LAVA);
                walls.add(pocket.relative(side).above(y));
            }
        }
        List<BlockPos> cave = new ArrayList<>();
        for (Direction side : along) {
            for (int y = 0; y <= 1; y++) {
                BlockPos plug = pocket.relative(side).above(y);
                h.setBlock(plug, Blocks.COBBLESTONE);
                plugs.add(plug);
                c.mineSite().sealed(h.absolutePos(plug));
                c.mineSite().forbid(h.absolutePos(plug));
                for (int far = 2; far <= 3; far++) {
                    h.setBlock(pocket.relative(side, far).above(y), Blocks.AIR);
                    cave.add(h.absolutePos(pocket.relative(side, far).above(y)));
                }
            }
        }
        c.mineSite().shun(cave);
        int surface = h.absolutePos(new BlockPos(0, GROUND + 1, 0)).getY() - SURFACE_MARGIN;
        pursue(h, player, c, () -> c.getBlockY() >= surface, () -> {
            for (BlockPos plug : plugs) h.assertTrue(h.getBlockState(plug).is(Blocks.COBBLESTONE), "A plug of the cave he fled from was opened at height " + plug.getY());
            for (BlockPos wall : walls) h.assertTrue(h.getBlockState(wall).is(Blocks.STONE), "The wall to the lava was opened at height " + wall.getY());
            h.assertTrue(!c.isInLava(), "He is in the lava");
        });
    }

    /** Run q1e/void-4: a plug of his own over his head where the next cell of the way up lies a block higher. The climb took the stall for its end, each order. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void takesOutAPlugOverHisHeadWhenTheClimbStalls(GameTestHelper h) {
        CompanionEntity c = stairs(h, 0);
        BlockPos pit = c.mineSite().foot();
        block(h, pit.above(2), Blocks.COBBLESTONE, c);
        climbs(h, c);
    }

    /** Run q1a/void: the way up first goes a block down, and a plug of his own hangs where his head would pass. The same stall, the same stop. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void takesOutAPlugAcrossADropOfTheWayUp(GameTestHelper h) {
        CompanionEntity c = stairs(h, 1);
        BlockPos low = c.mineSite().stands().get(c.mineSite().stands().size() - 2);
        block(h, low.above(2), Blocks.COBBLESTONE, c);
        climbs(h, c);
    }

    /** A stop is remembered across a save, and a save from before it reads as a mine with no stop. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void keepsTheStopAcrossASaveAndReadsAnOldSave(GameTestHelper h) {
        ServerPlayer player = goalPlayer(h);
        CompanionEntity c = shutPocket(h, player);
        long[] id = {0};
        h.onEachTick(() -> {
            if (id[0] == 0) {
                h.assertTrue(c.startWork("dig_for_diamonds", "", 1).success(), "Order not accepted: " + c.getLastTaskResult());
                id[0] = c.activeWorkId();
                return;
            }
            if (c.isWorking() || c.isGatheringTask()) return;
            CompoundTag saved = new CompoundTag();
            c.mineSite().save(saved);
            CompoundTag mine = saved.getCompound(CompanionMineSite.KEY);
            h.assertTrue(mine.contains("StopAt"), "The stop was not saved: " + mine.getAllKeys());
            CompanionMineSite read = new CompanionMineSite();
            read.load(saved);
            CompoundTag again = new CompoundTag();
            read.save(again);
            h.assertTrue(again.equals(saved), "A save read back differs: " + again + " | " + saved);
            for (String key : List.of("StopAt", "StopEnding", "StopRepeats")) mine.remove(key);
            CompanionMineSite old = new CompanionMineSite();
            old.load(saved);
            CompoundTag oldAgain = new CompoundTag();
            old.save(oldAgain);
            h.assertTrue(!oldAgain.getCompound(CompanionMineSite.KEY).contains("StopAt"), "An old save came back with a stop");
            h.succeed();
        });
    }

    /** The goal runs with the player's «продолжай» after each stop, as the mine-world probe does; five stops are the failure of the runs. */
    private static void pursue(GameTestHelper h, ServerPlayer player, CompanionEntity c, BooleanSupplier reached, Runnable always) {
        CompanionActionResult accepted = CompanionEvents.executeAction(player, "pursue_goal", "find_diamonds", 1);
        h.assertTrue(accepted.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + accepted.message());
        int[] stops = {0};
        h.onEachTick(() -> {
            always.run();
            if (reached.getAsBoolean()) { h.succeed(); return; }
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            if (goal == null || goal.state() != CompanionAgenda.State.BLOCKED) return;
            h.assertTrue(++stops[0] < MAX_STOPS, "The goal stopped " + stops[0] + " times, last: " + goal.reason());
            CompanionAgenda.resume(player, c);
        });
    }

    /**
     * A pocket of two cells, closed all round in the stone, the mine's entrance straight above it with every side tried and no record of the way between,
     * every side of the pocket refused and its floor banned: what the run left. A bag for the whole mine.
     */
    private static CompanionEntity shutPocket(GameTestHelper h, ServerPlayer player) {
        mountain(h, true);
        h.setBlock(POCKET, Blocks.AIR);
        h.setBlock(POCKET.above(), Blocks.AIR);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        player.teleportTo(c.getX() - 3, c.getY(), c.getZ() - 3);
        c.setOwnerId(player.getUUID());
        c.setLifestyle(false, false);
        c.setFollowing(false);
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE_SWORD),
                new ItemStack(Items.BREAD, 16), new ItemStack(Items.TORCH, 24), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.STICK, 8)};
        for (int slot = 0; slot < bag.length; slot++) c.setItem(slot, bag[slot]);
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), pocket = h.absolutePos(POCKET);
        CompanionMineSite site = c.mineSite();
        site.start(entrance, Direction.NORTH);
        for (Direction side : List.of(Direction.EAST, Direction.SOUTH, Direction.WEST)) site.restart(entrance, side);
        for (Direction side : Direction.Plane.HORIZONTAL) site.refuse(pocket, side);
        site.forbid(pocket.below());
        c.teleportTo(pocket.getX() + .5, pocket.getY(), pocket.getZ() + .5);
        return c;
    }

    /** His stairs from the surface down the arena's north, whole; {@code dropAtEnd} more steps go on at the bottom and back up a block, so the way up starts with a drop. */
    private static CompanionEntity stairs(GameTestHelper h, int dropAtEnd) {
        mountain(h, true);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        BlockPos entrance = new BlockPos(CENTER, GROUND + 1, CENTER);
        CompanionMineSite site = c.mineSite();
        site.start(h.absolutePos(entrance), Direction.NORTH);
        BlockPos stand = entrance;
        carve(h, stand);
        for (int step = 1; step <= STAIR_STEPS; step++) {
            stand = stand.north().below();
            carve(h, stand);
            site.reached(h.absolutePos(stand));
        }
        for (int step = 0; step < dropAtEnd; step++) {
            BlockPos low = stand.north().below(), rise = low.north().above();
            carve(h, low);
            carve(h, rise);
            site.reached(h.absolutePos(low));
            site.reached(h.absolutePos(rise));
            stand = rise;
        }
        BlockPos pit = h.absolutePos(stand);
        c.teleportTo(pit.getX() + .5, pit.getY(), pit.getZ() + .5);
        return c;
    }

    private static void carve(GameTestHelper h, BlockPos stand) {
        for (int y = 0; y <= 2; y++) h.setBlock(stand.above(y), Blocks.AIR);
    }

    /** A block of his own at a cell given in the world's coordinates, as his plugs are. */
    private static void block(GameTestHelper h, BlockPos cell, Block block, CompanionEntity c) {
        h.getLevel().setBlock(cell, block.defaultBlockState(), 2 | 16);
        c.mineSite().sealed(cell);
    }

    /** An order only for the way up: it must end on the surface, the plug out of the way. */
    private static void climbs(GameTestHelper h, CompanionEntity c) {
        BlockPos top = c.mineSite().entrance();
        int surface = top.getY() - SURFACE_MARGIN;
        long[] id = {0};
        h.onEachTick(() -> {
            if (id[0] == 0) {
                h.assertTrue(c.startWork("dig_for_diamonds", SURFACE, 1).success(), "The climb was not accepted: " + c.getLastTaskResult());
                id[0] = c.activeWorkId();
                return;
            }
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = c.workResult(id[0]);
            h.assertTrue(result != null, "No result for the order: " + report(c));
            h.assertTrue(result.success() && c.getBlockY() >= surface, "He said «" + result.message() + "» at " + (top.getY() - c.getBlockY()) + " below the entrance");
            h.succeed();
        });
    }

    /** A player the agenda sees: the mock one of the helper is not in the player list. */
    private static ServerPlayer goalPlayer(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setInvulnerable(true);
        return player;
    }
}

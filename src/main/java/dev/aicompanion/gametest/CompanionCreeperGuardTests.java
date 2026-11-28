package dev.aicompanion.gametest;

import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static dev.aicompanion.gametest.CompanionMineArena.CENTER;
import static dev.aicompanion.gametest.CompanionMineArena.GROUND;
import static dev.aicompanion.gametest.CompanionMineArena.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineArena.count;
import static dev.aicompanion.gametest.CompanionMineArena.mountain;
import static dev.aicompanion.gametest.CompanionMineArena.report;
import static dev.aicompanion.gametest.CompanionMineArena.rig;

/**
 * A lit fuse where there is no way out of its blast, with no order: under rock he walls it out with blocks from his bag, in
 * water he swims off it. The wall is not set under the open sky nor by his house, and never where a run would do.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCreeperGuardTests {
    private static final int TUNNEL_Y = 10, BEND_LEG = 3, BEND_LENGTH = 12, DEAD_END_LENGTH = 8, DEAD_END_CREEPER = 3, BLAST_TICKS = 200;
    private static final int TRENCH_LENGTH = 8, FUSE_SETTLED = 100, BAG_OF_COBBLESTONE = 128, ROOM = 6, ROOM_HEIGHT = 4, LIGHT_WAIT = 400, SWUM_OFF = 3;
    private static final float UNHURT = 10, WALLED = 4, SWUM_THROUGH = 4;

    private CompanionCreeperGuardTests() { }

    /** Stone all round him: a tunnel one block wide with a bend two blocks off and a lit fuse at his back. Round the corner the blast cannot reach: he runs, and sets no block. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseAtHisBackInATunnelWithABendIsLeftAroundTheCorner(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        for (int x = 0; x <= BEND_LEG; x++) bore(h, x, 0);
        for (int z = 1; z <= BEND_LENGTH; z++) bore(h, BEND_LEG, -z);
        place(c, cell(h, 1, 0));
        Creeper creeper = creeper(h, cell(h, 0, 0));
        creeper.ignite();
        survivesTheBlast(h, c, creeper, () -> {
            h.assertTrue(!creeper.isAlive() || count(c, Items.COBBLESTONE) == BAG_OF_COBBLESTONE, "He set a wall where a run would do | " + report(c));
            return true;
        });
    }

    /**
     * A dead end: nowhere to go and the creeper three blocks off with its fuse lit. He shuts the way with blocks from the bag, and the blast
     * does not reach him. The blocks are his own (the bag is that much lighter).
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseInADeadEndIsShutOutWithBlocks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        for (int x = 0; x <= DEAD_END_LENGTH; x++) bore(h, x, 0);
        place(c, cell(h, 0, 0));
        Creeper creeper = creeper(h, cell(h, DEAD_END_CREEPER, 0));
        creeper.ignite();
        int[] set = {0};
        survivesTheBlast(h, c, creeper, () -> {
            if (!creeper.isAlive()) return set[0] > 0;
            set[0] = setInTheTunnel(h);
            h.assertTrue(count(c, Items.COBBLESTONE) == BAG_OF_COBBLESTONE - set[0], "The wall did not come out of his bag: " + set[0] + " set, " + count(c, Items.COBBLESTONE) + " left");
            return set[0] > 0;
        });
    }

    /**
     * Run r314159-d3/retreat (and q314159-d3 at the same spot): in an underground lake a creeper came up beside him. The way out goes over
     * floor, and water has none, so there was no way and no wall; three blows were struck, a fourth was too late, dead at sixteen health.
     * In water up to the knee, a lit fuse a block off at the dead end: he wades off, and the blast does not reach him.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseBesideHimInWaterIsWadedOffFrom(GameTestHelper h) { inWater(h, 1, 0); }

    /** The same water with his back to the dead end and the creeper on the way out: no swim into the rock (the old blow saves him: no wall is set in water). */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseInWaterWithHisBackToRockIsNotSwumInto(GameTestHelper h) { inWater(h, 0, 1); }

    private static void inWater(GameTestHelper h, int his, int its) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        for (int x = 0; x <= DEAD_END_LENGTH; x++) {
            bore(h, x, 0);
            h.getLevel().setBlock(cell(h, x, 0), Blocks.WATER.defaultBlockState(), 2 | 16);
        }
        place(c, cell(h, his, 0));
        Creeper creeper = creeper(h, cell(h, its, 0));
        creeper.ignite();
        // Alive is the measure of the swim: a blast a block off kills (the blow or the wall is what the old order had), four blocks off it leaves him half.
        survivesTheBlast(h, c, creeper, SWUM_THROUGH, () -> {
            h.assertTrue(count(c, Items.COBBLESTONE) == BAG_OF_COBBLESTONE, "He set a wall in the water | " + report(c));
            return his == 0 || rel(h, c.blockPosition()).getX() >= CENTER + his + SWUM_OFF;
        });
    }

    /**
     * A dead end of the open ground, a creeper with a lit fuse three blocks off: a wall of blocks there would stay in the world for good,
     * nobody takes it out. He has the sword to strike with; the cobblestone stays in his bag.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void setsNoWallOnTheOpenGroundWhenAFuseIsLit(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        for (int x = 0; x <= TRENCH_LENGTH; x++) for (int y = GROUND - 2; y <= GROUND; y++) h.getLevel().setBlock(h.absolutePos(new BlockPos(CENTER + x, y, CENTER)), Blocks.AIR.defaultBlockState(), 2 | 16);
        BlockPos stand = h.absolutePos(new BlockPos(CENTER, GROUND - 2, CENTER));
        ownerAway(h, c);
        place(c, stand);
        h.assertTrue(h.getLevel().canSeeSky(stand), "SETUP: the trench is roofed");
        creeper(h, h.absolutePos(new BlockPos(CENTER + DEAD_END_CREEPER, GROUND - 2, CENTER))).ignite();
        setsNoBlock(h, c);
    }

    /** The same dead end under rock as the one he walls out, but it is his home (the player named it): no wall of cobblestone is left standing in the house. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void setsNoWallByHisHouseWhenAFuseIsLit(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        for (int x = 0; x <= DEAD_END_LENGTH; x++) bore(h, x, 0);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        BlockPos home = cell(h, 0, 0), above = player.blockPosition();
        player.teleportTo(home.getX() + .5, home.getY(), home.getZ() + .5);
        CompanionActionResult named = CompanionEvents.executeAction(player, "set_home", "", 1);
        h.assertTrue(named.status() != CompanionActionResult.Status.FAILED, "SETUP: home not named: " + named.message());
        player.teleportTo(above.getX() + .5, above.getY(), above.getZ() + .5);
        place(c, home);
        creeper(h, cell(h, DEAD_END_CREEPER, 0)).ignite();
        setsNoBlock(h, c);
    }

    /**
     * Stuck where he stands (two zombies' blows in turn kept him in the air in run ct52 20261031 void-4, and the creeper blew two and a half
     * blocks off), a lit creeper with its full health two blocks off in a room under rock: there is a way out, but the run gains nothing on
     * the creeper, so a wall goes up between them, and the blast costs him little.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseWithTheRunGoingNowhereIsWalledOut(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        for (int x = -ROOM; x <= ROOM; x++) for (int z = -ROOM; z <= ROOM; z++) for (int up = 0; up < ROOM_HEIGHT; up++) h.getLevel().setBlock(cell(h, x, up, z), Blocks.AIR.defaultBlockState(), 2 | 16);
        // A wall at the creeper's back, so that no blow throws it off.
        for (int up = 0; up < 2; up++) h.getLevel().setBlock(cell(h, 3, up, 0), Blocks.STONE.defaultBlockState(), 2 | 16);
        place(c, cell(h, 0, 0));
        c.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Creeper creeper = creeper(h, cell(h, 2, 0));
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        creeper.ignite();
        float[] before = {c.getHealth()};
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isAlive() ? "fuse " + String.format("%.2f", creeper.getSwelling(0)) : "gone") + "; he: health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.isAlive()) {
                h.assertTrue(h.getTick() - begun < BLAST_TICKS, "The lit creeper never blew" + trace);
                before[0] = c.getHealth();
                return;
            }
            h.assertTrue(before[0] - c.getHealth() < WALLED, "The blast cost him " + (before[0] - c.getHealth()) + trace);
            h.assertTrue(journal(c, "retreat_path") && journal(c, "retreat_wall"), "The wall was not the answer to a run that went nowhere (a way was not found, or no wall came) | " + trace);
            h.succeed();
        });
    }

    /** A pond two deep under the open sky: the water takes the light down a step, so "no sky above" held there. A wall in it would stay for good; he swims off. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseInAPondUnderTheOpenSkyIsSwumOffNotWalled(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        trench(h);
        for (int x = 0; x <= TRENCH_LENGTH; x++) for (int y = GROUND - 2; y <= GROUND - 1; y++) h.getLevel().setBlock(h.absolutePos(new BlockPos(CENTER + x, y, CENTER)), Blocks.WATER.defaultBlockState(), 2 | 16);
        BlockPos stand = h.absolutePos(new BlockPos(CENTER + 3, GROUND - 2, CENTER));
        litAfterTheLightSettles(h, c, stand, h.absolutePos(new BlockPos(CENTER + 1, GROUND - 2, CENTER)), "the water does not dim the sky");
    }

    /** A dry trench of the open ground with one block of leaves three blocks over him: the leaves dim the sky by a step, and no one calls that a roof. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseUnderALeafInTheOpenIsNotWalledOut(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        trench(h);
        h.getLevel().setBlock(h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true), 2 | 16);
        BlockPos stand = h.absolutePos(new BlockPos(CENTER, GROUND - 2, CENTER));
        ownerAway(h, c);
        litAfterTheLightSettles(h, c, stand, h.absolutePos(new BlockPos(CENTER + DEAD_END_CREEPER, GROUND - 2, CENTER)), "the leaf does not dim the sky");
    }

    /** A waterlogged candle on the cell of the wall: a block of the player's that holds water is no air. It stays, and the cobblestone stays in the bag. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aWallDoesNotReplaceABlockHoldingWater(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        for (int x = 0; x <= DEAD_END_LENGTH; x++) bore(h, x, 0);
        BlockPos candle = cell(h, 1, 0);
        h.getLevel().setBlock(candle, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 2 | 16);
        place(c, cell(h, 0, 0));
        Creeper creeper = creeper(h, cell(h, DEAD_END_CREEPER, 0));
        creeper.ignite();
        h.onEachTick(() -> {
            if (!creeper.isAlive()) h.succeed();
            h.assertTrue(h.getLevel().getBlockState(candle).is(Blocks.CANDLE) && count(c, Items.COBBLESTONE) == BAG_OF_COBBLESTONE, "The wall took the candle's cell | " + report(c));
        });
    }

    /** The same dead end in a house of planks, with no home named: a building of the player's, and a wall of his in it would stay for good. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void setsNoWallInABuildingOfThePlayer(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        for (int x = -2; x <= DEAD_END_LENGTH + 2; x++) for (int z = -2; z <= 2; z++) for (int up = -2; up <= 3; up++) h.getLevel().setBlock(cell(h, x, up, z), Blocks.OAK_PLANKS.defaultBlockState(), 2 | 16);
        for (int x = 0; x <= DEAD_END_LENGTH; x++) bore(h, x, 0);
        place(c, cell(h, 0, 0));
        creeper(h, cell(h, DEAD_END_CREEPER, 0)).ignite();
        setsNoBlock(h, c);
    }

    /**
     * A tunnel, the exit behind him, he is stuck, the player stands in it two blocks on and the creeper lit beside the player: a wall between him
     * and the player would shut the player off from the exit while the fuse burns on him.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void setsNoWallBetweenThePlayerAndTheWayOut(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        for (int x = -ROOM + 1; x <= 0; x++) for (int z = -2; z <= 2; z++) for (int up = 0; up < ROOM_HEIGHT; up++) h.getLevel().setBlock(cell(h, x, up, z), Blocks.AIR.defaultBlockState(), 2 | 16);
        for (int x = 1; x <= DEAD_END_LENGTH + 4; x++) bore(h, x, 0);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        BlockPos at = cell(h, 5, 0);
        place(c, cell(h, 3, 0));
        c.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        player.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        Creeper creeper = creeper(h, cell(h, 6, 0));
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        creeper.ignite();
        setsNoBlock(h, c);
    }

    /**
     * A room under rock with rock at his back, the lit creeper three blocks off: the way out goes sideways, and a run along a wall gains under a
     * block on the creeper in six ticks, but it is a run all the same. He runs, sets no block, and is not hurt.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aRunAlongAWallIsNotTakenForOneGoingNowhere(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = armed(h);
        for (int x = 0; x <= DEAD_END_CREEPER; x++) for (int z = -2 * ROOM; z <= 2 * ROOM; z++) for (int up = 0; up < ROOM_HEIGHT; up++) h.getLevel().setBlock(cell(h, x, up, z), Blocks.AIR.defaultBlockState(), 2 | 16);
        place(c, cell(h, 0, 0));
        Creeper creeper = creeper(h, cell(h, 3, 0));
        creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        creeper.ignite();
        survivesTheBlast(h, c, creeper, () -> {
            h.assertTrue(count(c, Items.COBBLESTONE) == BAG_OF_COBBLESTONE, "He walled a run that was going somewhere | " + report(c));
            return true;
        });
    }

    /** Whether the journal holds a line of the creeper guard with this action for him. */
    private static boolean journal(CompanionEntity c, String action) {
        CompanionDiagnostics.flush();
        var directory = FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion");
        List<String> lines = new ArrayList<>();
        for (String name : List.of("companion-1.jsonl", "companion-latest.jsonl")) {
            try { lines.addAll(Files.readAllLines(directory.resolve(name))); } catch (java.io.IOException | java.io.UncheckedIOException ignored) { }
        }
        return lines.stream().anyMatch(line -> line.contains(c.getUUID().toString()) && line.contains("\"action\":\"" + action + "\""));
    }

    /**
     * The light of the changed blocks is worked out on a thread of its own and may be late, so he is set down and the creeper lit only once the sky
     * is seen to be dimmed (a bound, then SETUP fails); then no block of his is set for a while.
     */
    private static void litAfterTheLightSettles(GameTestHelper h, CompanionEntity c, BlockPos stand, BlockPos creeperAt, String dimmed) {
        long begun = h.getTick();
        long[] lit = {-1};
        h.onEachTick(() -> {
            long t = h.getTick() - begun;
            if (lit[0] < 0) {
                if (h.getLevel().canSeeSky(stand)) { h.assertTrue(t < LIGHT_WAIT, "SETUP: " + dimmed); return; }
                lit[0] = t;
                place(c, stand);
                creeper(h, creeperAt).ignite();
            }
            h.assertTrue(count(c, Items.COBBLESTONE) >= BAG_OF_COBBLESTONE, "He set a wall: " + count(c, Items.COBBLESTONE) + " cobblestone left of " + BAG_OF_COBBLESTONE + " | " + report(c));
            if (t >= lit[0] + FUSE_SETTLED) h.succeed();
        });
    }

    /** The dead end of the open ground: air from the stand up to the sky. */
    private static void trench(GameTestHelper h) {
        for (int x = 0; x <= TRENCH_LENGTH; x++) for (int y = GROUND - 2; y <= GROUND; y++) h.getLevel().setBlock(h.absolutePos(new BlockPos(CENTER + x, y, CENTER)), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    /** While the fuse burns and after it, no cobblestone of his stands anywhere and none has left the bag. */
    private static void setsNoBlock(GameTestHelper h, CompanionEntity c) {
        h.onEachTick(() -> h.assertTrue(count(c, Items.COBBLESTONE) >= BAG_OF_COBBLESTONE, "He set a wall: " + count(c, Items.COBBLESTONE) + " cobblestone left of " + BAG_OF_COBBLESTONE + " | " + report(c)));
        h.runAfterDelay(FUSE_SETTLED, h::succeed);
    }

    private static int setInTheTunnel(GameTestHelper h) {
        int set = 0;
        for (int x = 0; x <= DEAD_END_LENGTH; x++) for (int up = 0; up < 2; up++) if (h.getLevel().getBlockState(cell(h, x, up, 0)).is(Blocks.COBBLESTONE)) set++;
        return set;
    }

    private static CompanionEntity armed(GameTestHelper h) {
        CompanionEntity c = rig(h);
        c.setItem(6, new ItemStack(Items.STONE_SWORD));
        return c;
    }

    private static void place(CompanionEntity c, BlockPos stand) {
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
    }

    private static Creeper creeper(GameTestHelper h, BlockPos at) {
        Creeper creeper = h.spawn(EntityType.CREEPER, rel(h, at));
        creeper.setPersistenceRequired();
        return creeper;
    }

    /** Position relative to the arena corner; GameTestHelper.relativePos mirrors X and Z in a rotated arena, so the frame is read from absolutePos. */
    private static BlockPos rel(GameTestHelper h, BlockPos world) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO), dx = h.absolutePos(new BlockPos(1, 0, 0)).subtract(origin), dz = h.absolutePos(new BlockPos(0, 0, 1)).subtract(origin);
        BlockPos d = world.subtract(origin);
        return new BlockPos(d.getX() * dx.getX() + d.getZ() * dx.getZ(), d.getY(), d.getX() * dz.getX() + d.getZ() * dz.getZ());
    }

    /** The owner far from the trench, so that the player by him is not what keeps the wall from going up. */
    private static void ownerAway(GameTestHelper h, CompanionEntity c) {
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        BlockPos far = h.absolutePos(new BlockPos(2, GROUND + 1, CENTER));
        player.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
    }

    private static BlockPos cell(GameTestHelper h, int x, int z) { return cell(h, x, 0, z); }

    private static BlockPos cell(GameTestHelper h, int x, int up, int z) { return h.absolutePos(new BlockPos(CENTER + x, TUNNEL_Y + up, CENTER + z)); }

    private static void bore(GameTestHelper h, int x, int z) {
        for (int up = 0; up < 2; up++) h.getLevel().setBlock(cell(h, x, up, z), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    /** Alive and not much hurt all along; once the creeper is gone (it blew or was killed) and {@code when} has held, the test is over. */
    private static void survivesTheBlast(GameTestHelper h, CompanionEntity c, Creeper creeper, BooleanSupplier when) { survivesTheBlast(h, c, creeper, UNHURT, when); }

    private static void survivesTheBlast(GameTestHelper h, CompanionEntity c, Creeper creeper, float least, BooleanSupplier when) {
        long begun = h.getTick();
        boolean[] held = {false};
        h.onEachTick(() -> {
            held[0] |= when.getAsBoolean();
            String trace = " | creeper " + (creeper.isAlive() ? "alive, fuse " + creeper.getSwellDir() : "gone") + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(c.getHealth() >= least, "Hurt by the blast" + trace);
            if (creeper.isAlive()) { h.assertTrue(h.getTick() - begun < BLAST_TICKS, "The creeper never blew" + trace); return; }
            h.assertTrue(held[0], "The way was not shut" + trace);
            h.succeed();
        });
    }
}

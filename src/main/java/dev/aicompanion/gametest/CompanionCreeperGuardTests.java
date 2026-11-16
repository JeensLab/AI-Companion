package dev.aicompanion.gametest;

import com.mojang.authlib.GameProfile;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMineSite;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.carveMine;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.ended;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.start;

/**
 * A live creeper (it walks, it swells, it explodes) on his way in the mine, in the cases the old guard did not look in. Nothing here is
 * switched off with NoAI: the creeper comes for him as it would, and what is asked is that he lives, the order goes on and the stairs stay whole.
 */
@GameTestHolder(dev.aicompanion.AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCreeperGuardTests {
    private static final int STEP_CHECK_TICKS = 2400, OPEN_CAVE_TICKS = 600, CHAMBER_REACH = 13, CHAMBER_HEIGHT = 4, OPEN_CAVE_STAND = 3;
    private static final int TUNNEL_Y = 10, BEND_LEG = 3, BEND_LENGTH = 12, DEAD_END_LENGTH = 8, DEAD_END_CREEPER = 3, BLAST_TICKS = 200, SURFACE_GAP = 5, SURFACE_TICKS = 400;
    private static final float UNHURT = 10;

    private CompanionCreeperGuardTests() { }

    /** He digs at the foot of the stairs and a creeper comes down them behind him. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperComingDownTheStairsBehindHimLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        place(h, c, stairs.get(foot - 2));
        Creeper creeper = creeper(h, stairs.get(foot - 9));
        goesOn(h, c, creeper, start(h, c, 1), true);
    }

    /** He walks down the stairs and a creeper comes up them to meet him. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperComingUpTheStairsToMeetHimLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        place(h, c, stairs.get(2));
        Creeper creeper = creeper(h, stairs.get(12));
        goesOn(h, c, creeper, start(h, c, 1), true);
    }

    /** No pickaxe is left, so he climbs, and a creeper waits on the stairs above him. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperOnTheStairsDuringTheClimbLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.STONE_PICKAXE) || c.getItem(slot).is(Items.IRON_PICKAXE)) c.setItem(slot, ItemStack.EMPTY);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        int foot = stairs.size() - 1;
        place(h, c, stairs.get(foot - 1));
        Creeper creeper = creeper(h, stairs.get(foot - 8));
        goesOn(h, c, creeper, start(h, c, 1), false);
    }

    /** Coal in the stair wall: every block he mines drops a stack he turns to pick up, and a creeper comes down the stairs meanwhile. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperWhileHeGathersWhatHeMinedLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        for (int i = foot - 8; i <= foot - 2; i++) h.getLevel().setBlock(stairs.get(i).west(), Blocks.COAL_ORE.defaultBlockState(), 2 | 16);
        place(h, c, stairs.get(foot - 9));
        Creeper creeper = creeper(h, stairs.get(foot - 16));
        goesOn(h, c, creeper, start(h, c, 1), true);
    }

    /** He is three blocks into a wide open chamber at the foot of the stairs, no order given, and a creeper comes across it from the far wall: he gets to the stairs, shuts them and lives. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aCreeperInAnOpenCaveLeavesHimAlive(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        BlockPos foot = stairs.get(stairs.size() - 1);
        for (int dx = 0; dx <= CHAMBER_REACH; dx++) for (int dz = -CHAMBER_REACH / 2; dz <= CHAMBER_REACH / 2; dz++) for (int dy = 0; dy < CHAMBER_HEIGHT; dy++) {
            h.getLevel().setBlock(foot.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        place(h, c, foot.offset(OPEN_CAVE_STAND, 0, 0));
        Creeper creeper = creeper(h, foot.offset(CHAMBER_REACH, 0, 0));
        long begun = h.getTick();
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + ", creeper " + creeper.isAlive() + " | " + report(c));
            if (h.getTick() - begun < OPEN_CAVE_TICKS) return;
            h.assertTrue(creeper.isRemoved() || creeper.distanceTo(c) > 3, "The creeper is at his side: " + creeper.distanceTo(c) + " | " + report(c));
            h.succeed();
        });
    }

    /** The guard takes the work off him for a while: the time of the step is given back, or the timeout would end the step for the time spent on the creeper. */
    @GameTest(template = "marathon_cave", timeoutTicks = STEP_CHECK_TICKS)
    public static void anEpisodeWithACreeperGivesTheStepItsTimeBack(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carveMine(h, c, 0);
        int foot = stairs.size() - 1;
        place(h, c, stairs.get(foot - 2));
        creeper(h, stairs.get(foot - 6)).getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        start(h, c, 1);
        long[] first = {-1};
        h.onEachTick(() -> {
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(c));
            if (first[0] < 0) {
                if (!c.expeditionStage().isEmpty()) first[0] = c.expeditionStepEnd();
                return;
            }
            if (c.expeditionStepEnd() > first[0]) h.succeed();
        });
        h.runAtTickTime(STEP_CHECK_TICKS - 5, () -> h.fail("The step kept its end " + first[0] + " after the creeper: " + c.expeditionStepEnd() + " | " + report(c)));
    }

    /** Stone all round him: a tunnel one block wide with a bend two blocks off and a lit fuse at his back. Straight on there is no floor to run along; round the corner the blast cannot reach. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseAtHisBackInATunnelWithABendIsLeftAroundTheCorner(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = CompanionMineFightTests.armed(h);
        for (int x = 0; x <= BEND_LEG; x++) bore(h, x, 0);
        for (int z = 1; z <= BEND_LENGTH; z++) bore(h, BEND_LEG, -z);
        place(h, c, cell(h, 1, 0));
        Creeper creeper = creeper(h, cell(h, 0, 0));
        creeper.ignite();
        survivesTheBlast(h, c, creeper, () -> true);
    }

    /** A dead end: nowhere to go and the creeper three blocks off with its fuse lit. He shuts the way with blocks from the bag, and the blast does not reach him. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aLitFuseInADeadEndIsShutOutWithBlocks(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = CompanionMineFightTests.armed(h);
        for (int x = 0; x <= DEAD_END_LENGTH; x++) bore(h, x, 0);
        place(h, c, cell(h, 0, 0));
        Creeper creeper = creeper(h, cell(h, DEAD_END_CREEPER, 0));
        creeper.ignite();
        boolean[] shut = {false};
        survivesTheBlast(h, c, creeper, () -> {
            for (int x = 1; x < DEAD_END_CREEPER; x++) shut[0] |= !h.getLevel().getBlockState(cell(h, x, 0)).isAir() || !h.getLevel().getBlockState(cell(h, x, 0).above()).isAir();
            return shut[0];
        });
    }

    /** Armed, on a long goal and out of the mine: a creeper that has not lit its fuse is struck, not run from (m15d: twelve flights in a row). */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void anArmedGoalWorkerStrikesAnUnlitCreeperAboveGround(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = CompanionMineFightTests.armed(h);
        ServerPlayer player = survivor(h, c);
        Creeper creeper = creeper(h, h.absolutePos(new BlockPos(CENTER + SURFACE_GAP, GROUND + 1, CENTER)));
        CompanionActionResult started = CompanionEvents.executeAction(player, "pursue_goal", "gear_up", 1);
        h.assertTrue(started.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + started.message());
        long begun = h.getTick();
        h.onEachTick(() -> {
            String trace = " | creeper " + (creeper.isDeadOrDying() ? "killed" : creeper.isAlive() ? "alive, fuse " + creeper.getSwellDir() : "gone") + ", he at "
                    + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (creeper.isDeadOrDying()) { h.succeed(); return; }
            h.assertTrue(creeper.isAlive(), "The creeper blew up, it was not struck" + trace);
            h.assertTrue(h.getTick() - begun < SURFACE_TICKS, "The creeper was not struck in time" + trace);
        });
    }

    private static BlockPos cell(GameTestHelper h, int x, int z) { return cell(h, x, 0, z); }

    private static BlockPos cell(GameTestHelper h, int x, int up, int z) { return h.absolutePos(new BlockPos(CENTER + x, TUNNEL_Y + up, CENTER + z)); }

    private static void bore(GameTestHelper h, int x, int z) {
        for (int up = 0; up < 2; up++) h.getLevel().setBlock(cell(h, x, up, z), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    /** The owner of the long goal: a survival player, as the goal's own tests make one. */
    private static ServerPlayer survivor(GameTestHelper h, CompanionEntity c) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(true);
        player.teleportTo(c.getX() - 3, c.getY(), c.getZ() - 3);
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        return player;
    }

    /** Alive and not much hurt all along; once the creeper is gone (it blew or was killed) and {@code when} has held, the test is over. */
    private static void survivesTheBlast(GameTestHelper h, CompanionEntity c, Creeper creeper, BooleanSupplier when) {
        long begun = h.getTick();
        boolean[] held = {false};
        h.onEachTick(() -> {
            held[0] |= when.getAsBoolean();
            String trace = " | creeper " + (creeper.isAlive() ? "alive, fuse " + creeper.getSwellDir() : "gone") + ", he at " + rel(h, c.blockPosition()).toShortString()
                    + ", health " + c.getHealth() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            h.assertTrue(c.getHealth() >= UNHURT, "Hurt by the blast" + trace);
            if (creeper.isAlive()) { h.assertTrue(h.getTick() - begun < BLAST_TICKS, "The creeper never blew" + trace); return; }
            h.assertTrue(held[0], "The way was not shut" + trace);
            h.succeed();
        });
    }

    private static void place(GameTestHelper h, CompanionEntity c, BlockPos stand) {
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
    }

    private static Creeper creeper(GameTestHelper h, BlockPos at) {
        Creeper creeper = h.spawn(EntityType.CREEPER, rel(h, at));
        creeper.setPersistenceRequired();
        return creeper;
    }

    /** A creeper that stays at the block this long has not wandered off as one would; it is taken away, as a despawn or a stroll would. */
    private static final int DISMISS_TICKS = 400;

    /** Alive all along; the order ends (with diamond when {@code mines}) and the stairs still stand after the mine is checked. */
    private static void goesOn(GameTestHelper h, CompanionEntity c, Creeper creeper, long id, boolean mines) {
        CompanionMineSite site = c.mineSite();
        int stands = site.stands().size();
        double[] last = {-1, 99};
        long[] shutAt = {-1};
        h.onEachTick(() -> {
            if (creeper.isAlive()) { last[0] = creeper.distanceTo(c); last[1] = Math.min(last[1], last[0]); }
            if (shutAt[0] < 0 && !site.temporary().isEmpty()) shutAt[0] = h.getTick();
            if (creeper.isAlive() && shutAt[0] >= 0 && h.getTick() - shutAt[0] > DISMISS_TICKS) creeper.discard();
            String trace = " | creeper " + (creeper.isAlive() ? "alive" : "gone") + ", last distance " + Math.round(last[0] * 10) / 10.0 + ", nearest " + Math.round(last[1] * 10) / 10.0
                    + ", he at " + rel(h, c.blockPosition()).toShortString() + ", health " + c.getHealth() + ", blocks " + site.temporary().stream().map(cell -> rel(h, cell).toShortString()).toList() + " | " + report(c);
            h.assertTrue(c.isAlive(), "Died" + trace);
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(!mines || result.success() && count(c, Items.DIAMOND) >= 1, "The order did not go on" + trace);
            int kept = site.verify(h.getLevel());
            h.assertTrue(kept == stands, "The stairs were cut: " + kept + " of " + stands + " stands" + trace);
            h.succeed();
        });
    }
}

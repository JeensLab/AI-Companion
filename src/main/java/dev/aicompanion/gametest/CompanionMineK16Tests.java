package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFacts;
import dev.aicompanion.entity.CompanionMineSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static dev.aicompanion.gametest.CompanionMineBacktrackTests.CENTER;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.GROUND;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.TIMEOUT;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.count;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.mountain;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rel;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.report;
import static dev.aicompanion.gametest.CompanionMineBacktrackTests.rig;

/**
 * Runs s1a/void-2, s1c/void-2 and s1e/void: the climb that went back down from the surface, the ore tunnel under the steps of the stairs above a
 * restart, the bench out of reach with logs in the bag, the kit left out on a hillside, and the walk back to the stairs where he was killed.
 * Arena as in the other mine tests: stand k is (24,22-k,24-k), its column three cells high.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK16Tests {
    private static final String SURFACE = "surface";
    private static final int TOP_STEPS = 10, CORRIDOR_Y = 12, CORRIDOR_Z = 14, CORRIDOR_X = 3, START_X = 18, SURFACE_FEET = GROUND - 1, BACK_DOWN_FEET = 14,
            PIT_LOW = 23, PIT_HIGH = 25, PIT_FLOOR = 17, BENCH_X = 29, LOGS = 4, INGOTS = 3, STICKS = 2, BAG_SLOTS = 6,
            HILL = 10, DEEP_STANDS = 12, ABOVE_THE_BODY = 10;

    private CompanionMineK16Tests() { }

    @AfterBatch(batch = "k16_agenda") public static void afterAgenda(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    private static BlockPos stand(int step) { return new BlockPos(CENTER, GROUND + 1 - step, CENTER - step); }

    private static void air(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    private static void emptyHands(CompanionEntity c) {
        for (int slot = 0; slot < BAG_SLOTS; slot++) c.setItem(slot, ItemStack.EMPTY);
        c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /**
     * s1a/void-2: the record has a gap below the upper stairs. The only walk across it is a corridor and a ramp to the surface, over the ground and down
     * the stairs. Once out on the surface the climb has done its work: he must not go back down the stairs for the cell beyond the gap, to walk up them again.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aClimbAcrossAGapEndsOnTheSurfaceItReaches(GameTestHelper h) {
        mountain(h, false);
        for (int k = 1; k <= TOP_STEPS; k++) air(h, CENTER, CENTER, stand(k).getY(), stand(k).getY() + 2, stand(k).getZ(), stand(k).getZ());
        air(h, CORRIDOR_X, START_X, CORRIDOR_Y, CORRIDOR_Y + 2, CORRIDOR_Z, CORRIDOR_Z);
        for (int rise = 1; rise <= GROUND - CORRIDOR_Y; rise++) air(h, CORRIDOR_X, CORRIDOR_X, CORRIDOR_Y + rise, Math.min(GROUND, CORRIDOR_Y + rise + 2), CORRIDOR_Z + rise, CORRIDOR_Z + rise);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        emptyHands(c);
        BlockPos start = new BlockPos(START_X, CORRIDOR_Y, CORRIDOR_Z);
        CompanionMineSite site = c.mineSite();
        site.start(h.absolutePos(stand(0)), Direction.NORTH);
        for (int k = 1; k <= TOP_STEPS; k++) site.reached(h.absolutePos(stand(k)));
        site.reached(h.absolutePos(start));
        BlockPos at = h.absolutePos(start);
        c.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        long[] id = {0};
        boolean[] up = {false};
        h.onEachTick(() -> {
            if (id[0] == 0) {
                h.assertTrue(c.startWork("dig_for_diamonds", SURFACE, 1).success(), "The climb was not accepted: " + c.getLastTaskResult());
                id[0] = c.activeWorkId();
                return;
            }
            int feet = rel(h, c.blockPosition()).getY();
            up[0] |= feet >= SURFACE_FEET;
            h.assertTrue(!up[0] || feet > BACK_DOWN_FEET, "Out on the surface he went back down to height " + feet + " | " + report(c));
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = c.workResult(id[0]);
            h.assertTrue(result != null && result.success() && up[0], "The climb did not end on the surface: " + (result == null ? "no result" : result.message()) + " | " + report(c));
            h.succeed();
        });
    }

    /** s1c/void-2: a mine started again under the ground knows its upper stairs only as the way above it, and an ore under them took a step's floor and the way out. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void theFloorOfAStepAboveARestartIsNoCellForATunnel(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 3, 2, 3);
        CompanionMineSite site = c.mineSite();
        BlockPos entrance = h.absolutePos(new BlockPos(16, 40, 16));
        site.start(entrance, Direction.NORTH);
        BlockPos step = entrance;
        for (int k = 1; k <= DEEP_STANDS; k++) { step = step.below().north(); site.reached(step); }
        BlockPos upper = entrance.below().north();
        site.restart(step, Direction.WEST);
        site.buried(true);
        h.assertTrue(site.above().contains(upper), "The step is not on the way above the restart: " + site.above());
        h.assertTrue(site.carriesStand(upper.below()), "The floor of the step at " + upper.toShortString() + " above the restart is free for a tunnel");
        h.assertTrue(site.carriesStand(entrance.below()), "The floor of the entrance above the restart is free for a tunnel");
        h.succeed();
    }

    /** s1e/void: down in a pit with sheer walls, the bench he remembers a few blocks off has no walk to it, and the logs in his bag buy a new one: no order stops on it. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void aNearBenchWithNoWalkToItIsReplacedWhenHeHasLogs(GameTestHelper h) {
        mountain(h, false);
        air(h, PIT_LOW, PIT_HIGH, PIT_FLOOR, GROUND, PIT_LOW, PIT_HIGH);
        BlockPos bench = new BlockPos(BENCH_X, GROUND + 1, CENTER);
        h.setBlock(bench, Blocks.CRAFTING_TABLE);
        CompanionEntity c = rig(h);
        c.setFollowing(false);
        emptyHands(c);
        c.setItem(0, new ItemStack(Items.OAK_LOG, LOGS));
        c.setItem(1, new ItemStack(Items.IRON_INGOT, INGOTS));
        c.setItem(2, new ItemStack(Items.STICK, STICKS));
        c.rememberPlaced(h.absolutePos(bench));
        BlockPos at = h.absolutePos(new BlockPos(CENTER, PIT_FLOOR, CENTER));
        c.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        long[] id = {0};
        h.onEachTick(() -> {
            if (id[0] == 0) {
                if (!c.onGround()) return;
                var order = CompanionEvents.executeAction(h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId()), "craft_item", "minecraft:iron_pickaxe", 1);
                h.assertTrue(order.success(), "The order was not accepted: " + order.message());
                id[0] = c.activeWorkId();
                return;
            }
            if (c.isWorking() || c.isGatheringTask()) return;
            CompanionActionResult result = c.workResult(id[0]);
            h.assertTrue(result != null && count(c, Items.IRON_PICKAXE) == 1, "No pickaxe from the pit: " + (result == null ? "no result" : result.message()) + " | " + report(c));
            h.succeed();
        });
    }

    /** s1e/void: on a hillside lower than the entrance he is not below the mine: under the open sky the kit is still to be made up, not skipped. */
    @GameTest(template = "marathon_cave", timeoutTicks = 100)
    public static void aHillsideLowerThanTheEntranceIsNotBelowTheMine(GameTestHelper h) {
        mountain(h, false);
        CompanionEntity c = rig(h);
        c.mineSite().start(h.absolutePos(new BlockPos(CENTER, GROUND + 1 + HILL, CENTER)), Direction.NORTH);
        h.assertTrue(!CompanionFacts.of(c).belowMine(), "Under the open sky " + HILL + " blocks lower than the entrance he counts as below the mine");
        h.succeed();
    }

    /**
     * s1e/void: killed in the mine, he comes back bare-handed and the monsters stand at his things, so he leaves them. The stairs that led there are no way
     * down any more: the end of them counts as a cave he fled from, or he walks down it to the same monsters with no armour and no food.
     */
    @GameTest(template = "tall_tree", batch = "k16_agenda", timeoutTicks = 600)
    public static void theStairsThatEndedInADeathAreLeftWhenMonstersHoldThePlace(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        CompanionEntity c = body(h, player);
        c.setHealth(c.getMaxHealth());
        BlockPos place = h.absolutePos(new BlockPos(3, 1, 3));
        ItemEntity item = new ItemEntity(h.getLevel(), place.getX() + .5, place.getY(), place.getZ() + .5, new ItemStack(Items.IRON_INGOT, 3));
        item.getPersistentData().putUUID("aicompanion:belongings", player.getUUID());
        h.getLevel().addFreshEntity(item);
        var zombie = h.spawn(EntityType.ZOMBIE, 4, 1, 3);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setPersistenceRequired();
        CompanionMineSite site = c.mineSite();
        BlockPos entrance = place.above(ABOVE_THE_BODY + DEEP_STANDS).south(DEEP_STANDS);
        site.start(entrance, Direction.NORTH);
        BlockPos step = entrance;
        for (int k = 1; k <= DEEP_STANDS; k++) { step = step.below().north(); site.reached(step); }
        var result = CompanionEvents.executeAction(player, "pursue_goal", "gear_up", 1);
        h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + result.message());
        CompanionAgenda.died(h.getLevel().getServer(), player.getUUID(), h.getLevel().dimension(), place);
        h.succeedWhen(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            h.assertTrue(goal != null && goal.deathPlace() == null, "The try for the things was not made");
            h.assertTrue(site.firstShunned() >= 0, "The stairs that ended where he died stay a way down: " + c.autonomy().said());
        });
    }

    private static CompanionEntity body(GameTestHelper h, ServerPlayer player) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        player.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5);
        c.setOwnerId(player.getUUID());
        c.setSearchRadius(16);
        c.setLifestyle(false, false);
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        return c;
    }

    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setInvulnerable(true);
        return p;
    }
}

package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.gametest.CompanionFixtureCleanup;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The deaths and the dead ends of the mine run K14: a way out of a fuse that ends beside lava, a body in lava with nothing that takes him out,
 * a goal that burns its tries on a wound it cannot mend, and the plug that hides under another one. The tests live beside the package-private
 * classes they call.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineK14Tests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, TIMEOUT = 1500, IN_LAVA_TICKS = 60, WAIT_TICKS = 1000;
    private static final float HURT = 7;
    private static final String NO_FOOD = "нет еды, а без сытости рана сама не заживает";

    private CompanionMineK14Tests() { }

    @AfterBatch(batch = "k14_agenda") public static void afterAgenda(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /**
     * A corridor with a nook to the north whose deep cells have a closed lava source in the stone at the foot of each wall: out of the creeper's
     * sight and far from it, they are the best end of the way, and the one that is not beside lava is the shallow one.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 100)
    public static void fuseEscapeNeverEndsAtTheEdgeOfLava(GameTestHelper h) {
        for (int x = 0; x <= 20; x++) for (int z = 0; z <= 14; z++) for (int y = 0; y <= 12; y++) {
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : y <= 8 ? Blocks.STONE : Blocks.AIR);
        }
        for (int x = 8; x <= 16; x++) carve(h, x, 10);
        for (int z = 4; z <= 9; z++) carve(h, 8, z);
        for (int z = 3; z <= 7; z++) {
            h.setBlock(new BlockPos(7, 2, z), Blocks.LAVA);
            h.setBlock(new BlockPos(9, 2, z), Blocks.LAVA);
        }
        h.setBlock(new BlockPos(8, 2, 3), Blocks.LAVA);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 10, 3, 10);
        Creeper creeper = h.spawn(EntityType.CREEPER, 13, 3, 10);
        creeper.setNoAi(true);

        CompanionFuseEscape escape = new CompanionFuseEscape(c);
        int length = escape.plan(h.getLevel(), List.of(creeper));
        BlockPos goal = escape.goal();
        h.assertTrue(goal == null || !lavaBeside(h.getLevel(), goal),
                "The way out of the fuse ends at " + rel(h, goal) + ", beside lava (length " + length + ")");
        h.succeed();
    }

    /** In the middle of a step down the stairs he is put onto the edge of a 3 by 3 pool of lava far from the entrance, a step from the bank: a minute on, he is alive and out of it. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void getsOutOfLavaInTheMiddleOfAMineStep(GameTestHelper h) {
        mountain(h);
        for (int x = 4; x <= 6; x++) for (int z = 4; z <= 6; z++) for (int y = GROUND - 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.LAVA);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(c.getX() - 3, c.getY(), c.getZ() - 3);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 16), new ItemStack(Items.COBBLESTONE, 64)};
        for (int slot = 0; slot < bag.length; slot++) c.setItem(slot, bag[slot]);
        h.assertTrue(c.startWork("dig_for_diamonds", "", 1).success(), "Diamond mine not accepted: " + c.getLastTaskResult());

        BlockPos pool = h.absolutePos(new BlockPos(4, GROUND, 5));
        long[] placedAt = {-1};
        h.onEachTick(() -> {
            long now = h.getTick();
            if (!c.isAlive()) h.fail(placedAt[0] < 0 ? "Died before the lava" : "Burned to death " + (now - placedAt[0]) + " ticks after he was put into the lava | " + c.getLastTaskResult());
            if (placedAt[0] < 0) {
                if (c.mineSite().stands().size() < 2) return;
                c.moveTo(pool.getX() + .5, pool.getY(), pool.getZ() + .5);
                c.getNavigation().stop();
                placedAt[0] = now;
                return;
            }
            if (now - placedAt[0] < IN_LAVA_TICKS) return;
            h.assertTrue(!c.isInLava(), "Still in the lava " + IN_LAVA_TICKS + " ticks on, hp " + c.getHealth() + " | " + c.getLastTaskResult());
            h.succeed();
        });
    }

    /** Hurt, with nothing to eat: the goal waits for food and tries again instead of spending the stops of the mine on the retreats it ends in. */
    @GameTest(template = "marathon_cave", batch = "k14_agenda", timeoutTicks = TIMEOUT)
    public static void aHurtGoalWithNoFoodWaitsInsteadOfBurningItsStops(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.TORCH, 16), new ItemStack(Items.COBBLESTONE, 32)};
        for (int slot = 0; slot < bag.length; slot++) c.setItem(slot, bag[slot]);
        ServerPlayer player = survivalPlayer(h);
        player.teleportTo(c.getX() - 3, c.getY(), c.getZ() - 3);
        c.setOwnerId(player.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        c.setHealth(HURT);
        c.satiety = 0;
        var accepted = CompanionEvents.executeAction(player, "pursue_goal", "find_diamonds", 1);
        h.assertTrue(accepted.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + accepted.message());

        h.onEachTick(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            h.assertTrue(goal != null && goal.state() != CompanionAgenda.State.BLOCKED,
                    "The goal stopped at tick " + h.getTick() + ": " + (goal == null ? null : goal.state() + " '" + goal.reason() + "'") + " | hp " + c.getHealth());
            if (h.getTick() < WAIT_TICKS) return;
            h.assertTrue(goal.reason().contains(NO_FOOD), "No wait for food by tick " + h.getTick() + ": " + goal.state() + " '" + goal.reason() + "' | " + c.getLastTaskResult());
            h.succeed();
        });
    }

    /**
     * A step down onto a half block under a plug, with a plug over the head of the step: the upper one is the one in sight and comes out
     * first. The geometry is made up for the probe; the bridge in the mine that showed it laid two plugs one over the other.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 100)
    public static void thePlugOfTheHeadLevelComesOutFirstOnAStepDown(GameTestHelper h) {
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) for (int y = 0; y <= 8; y++) {
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : y <= 2 ? Blocks.STONE : Blocks.AIR);
        }
        BlockPos stand = h.absolutePos(new BlockPos(5, 3, 5)), lower = h.absolutePos(new BlockPos(6, 2, 5)), upper = h.absolutePos(new BlockPos(6, 4, 5));
        h.setBlock(new BlockPos(6, 2, 5), Blocks.STONE_SLAB);
        h.setBlock(new BlockPos(6, 4, 5), Blocks.COBBLESTONE);
        CompanionMineSite mine = new CompanionMineSite();
        mine.start(stand, Direction.NORTH);
        mine.sealed(lower);
        mine.sealed(upper);

        var way = CompanionExpeditionPlugs.way(h.getLevel(), mine, stand, lower);
        h.assertTrue(way != null && way.walk().equals(List.of(stand)), "No walk of the stand alone to the step: " + way);
        h.assertTrue(upper.equals(way.cell()), "The plug to take out is " + rel(h, way == null ? null : way.cell()) + ", not the upper one at " + rel(h, upper));
        h.succeed();
    }

    private static String rel(GameTestHelper h, BlockPos world) { return world == null ? "none" : h.relativePos(world).toShortString(); }

    private static void carve(GameTestHelper h, int x, int z) {
        for (int y = 3; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    /** Lava in the cube of a cell and its neighbours, as a body that stands there is burnt by it the moment the lava moves or he slips. */
    private static boolean lavaBeside(ServerLevel level, BlockPos cell) {
        for (BlockPos near : BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, 1, 1))) {
            if (level.getFluidState(near).is(net.minecraft.tags.FluidTags.LAVA)) return true;
        }
        return false;
    }

    /** Bedrock floor and walls, stone, dirt, grass: a mountain with no ore. */
    private static void mountain(GameTestHelper h) {
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            Block block = y == 0 || edge ? Blocks.BEDROCK : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            h.setBlock(new BlockPos(x, y, z), block);
        }
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

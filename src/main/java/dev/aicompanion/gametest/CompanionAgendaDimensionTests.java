package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** A long goal while he and the player are in different dimensions: it waits, it does not fail its stage. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAgendaDimensionTests {
    @AfterBatch(batch = "agenda_dimension") public static void afterDimension(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Without the fix the step starts every second, is refused as «not loaded beside you» and the goal stops on the third refusal. */
    @GameTest(template = "tall_tree", batch = "agenda_dimension", timeoutTicks = 400)
    public static void goalWaitsWhileHeIsInAnotherDimension(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setInvulnerable(true);
        p.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5); c.setOwnerId(p.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID()); CompanionEvents.getOwnedCompanion(p);
        var started = CompanionEvents.executeAction(p, "pursue_goal", "gear_up", 0);
        h.assertTrue(started.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + started.message());

        var server = h.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        // The player goes, as through a portal, into a closed room; he stays behind in the test's own world. A player is there at once,
        // while a companion sent to a new Nether chunk takes up to some hundreds of ticks to load in a test server.
        BlockPos room = BlockPos.containing(c.getX(), 64, c.getZ());
        for (BlockPos pos : BlockPos.betweenClosed(room.offset(-2, -1, -2), room.offset(2, 3, 2)))
            nether.setBlockAndUpdate(pos, Math.abs(pos.getX() - room.getX()) == 2 || Math.abs(pos.getZ() - room.getZ()) == 2 || pos.getY() == room.getY() - 1
                    || pos.getY() == room.getY() + 3 ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
        double backX = p.getX(), backY = p.getY(), backZ = p.getZ();
        p.teleportTo(nether, room.getX() + .5, room.getY(), room.getZ() + .5, 0, 0);
        h.assertTrue(p.level() == nether && c.level() == h.getLevel(), "They are not in different dimensions");
        long from = h.getTick();
        h.onEachTick(() -> {
            var goal = CompanionAgenda.goal(server, p.getUUID());
            h.assertTrue(goal != null && goal.state() == CompanionAgenda.State.ACTIVE, "The goal left ACTIVE while the player is in the Nether: "
                    + (goal == null ? "gone" : goal.state() + " " + goal.reason()));
            if (h.getTick() - from < 200) return;
            p.teleportTo(h.getLevel(), backX, backY, backZ, 0, 0);
            h.succeed();
        });
    }
}

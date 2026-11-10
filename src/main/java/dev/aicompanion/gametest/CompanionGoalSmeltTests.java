package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** A long goal's smelt that parks as his own batch: the stage that starts again must wait for that furnace, not set down another. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGoalSmeltTests {
    @AfterBatch(batch = "goal_smelt") public static void afterGoalSmelt(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Eight logs cook 80 seconds, past the park limit: the first step ends parked, the stage's second run takes the same furnace. */
    @GameTest(template = "tall_tree", batch = "goal_smelt", timeoutTicks = 4000)
    public static void aGoalCharcoalBatchPastTheParkLimitUsesOneFurnaceAndIsCollected(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var p = player(h);
        p.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5); c.setOwnerId(p.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID()); CompanionEvents.getOwnedCompanion(p);
        c.setItem(0, new ItemStack(Items.OAK_LOG, 8)); c.setItem(1, new ItemStack(Items.COAL, 3)); c.setItem(2, new ItemStack(Items.FURNACE, 3));

        var first = goalStep(h, p);
        AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>> second = new AtomicReference<>();
        h.onEachTick(() -> { if (first.isDone() && second.get() == null) second.set(goalStep(h, p)); });
        h.succeedWhen(() -> {
            h.assertTrue(first.isDone() && first.join().cancelled(), "The first smelt did not park and end the goal's step");
            h.assertTrue(second.get() != null && second.get().isDone(), "The second run of the stage did not finish");
            h.assertTrue(furnaces(h) == 1, "Not exactly one furnace placed: " + furnaces(h));
            var bag = CompanionEvents.getOwnedCompanion(p);
            h.assertTrue(count(bag, Items.CHARCOAL) == 8 && count(bag, Items.OAK_LOG) == 0, "Not all the logs came back as charcoal: " + bag.getStoredItemsSummary());
            h.assertTrue(bag.batches().summaries().isEmpty(), "A batch is left behind: " + bag.batches().summaries());
        });
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> goalStep(GameTestHelper h, ServerPlayer p) {
        var port = CompanionServerPlanPort.forGoal(h.getLevel().getServer(), p.getUUID(), CompanionDiagnostics.newTrace(), () -> false);
        List<PlannedAction> plan = new ArrayList<>(List.of(new PlannedAction("smelt_items", 0, 8, "charcoal")));
        return CompanionPlanRunner.run(plan, port);
    }

    private static int furnaces(GameTestHelper h) {
        int n = 0;
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) if (h.getBlockState(new BlockPos(x, 1, z)).is(Blocks.FURNACE)) n++;
        return n;
    }

    private static int count(CompanionEntity c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    private static ServerPlayer player(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setInvulnerable(true);
        return p;
    }
}

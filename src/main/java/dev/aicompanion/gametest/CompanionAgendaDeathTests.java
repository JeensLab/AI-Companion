package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What the one who comes back does about his things at the death place: only a healed body goes for them. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAgendaDeathTests {
    /** Above the health at which a goal pauses for danger (6), below the fit health he goes for his things with. */
    private static final float HURT = 10;

    private CompanionAgendaDeathTests() { }

    @AfterBatch(batch = "agenda_recover_hurt") public static void afterHurt(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_recover_mend") public static void afterMend(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Hurt, with nothing to eat and nothing to heal him: the try for the things is spent at once, with the reason, and he does not set out. */
    @GameTest(template = "tall_tree", batch = "agenda_recover_hurt", timeoutTicks = 600)
    public static void aHurtBodyWithNothingToMendHimDoesNotGoForHisThings(GameTestHelper h) {
        var player = survivalPlayer(h);
        var c = body(h, player);
        c.setHealth(HURT);
        BlockPos place = lieAt(h, player);
        start(h, player);
        CompanionAgenda.died(h.getLevel().getServer(), player.getUUID(), h.getLevel().dimension(), place);
        h.succeedWhen(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            h.assertTrue(goal != null && goal.deathPlace() == null, "The try for the things was not made");
            h.assertTrue(c.autonomy().said().stream().noneMatch(line -> line.contains("Иду за своими вещами")),
                    "He set out for his things hurt: " + c.autonomy().said());
            h.assertTrue(c.autonomy().said().stream().anyMatch(line -> line.contains("ранен")), "The reason was not said: " + c.autonomy().said());
        });
    }

    /** Hurt, with food: he waits and mends, and goes for his things only at fit health. */
    @GameTest(template = "tall_tree", batch = "agenda_recover_mend", timeoutTicks = 3000)
    public static void aHurtBodyWithFoodMendsBeforeGoingForHisThings(GameTestHelper h) {
        var player = survivalPlayer(h);
        var c = body(h, player);
        c.setItem(0, new ItemStack(Items.COOKED_BEEF, 16));
        c.setHealth(HURT);
        BlockPos place = lieAt(h, player);
        start(h, player);
        CompanionAgenda.died(h.getLevel().getServer(), player.getUUID(), h.getLevel().dimension(), place);
        float[] healthAtTheTry = {-1};
        h.onEachTick(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            if (healthAtTheTry[0] < 0 && goal != null && goal.deathPlace() == null) healthAtTheTry[0] = c.getHealth();
        });
        h.succeedWhen(() -> {
            h.assertTrue(healthAtTheTry[0] >= 0, "The try for the things was never made: " + c.getHealth());
            h.assertTrue(healthAtTheTry[0] >= 18, "He went for his things at health " + healthAtTheTry[0]);
        });
    }

    private static void start(GameTestHelper h, ServerPlayer player) {
        var result = CompanionEvents.executeAction(player, "pursue_goal", "gear_up", 1);
        h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + result.message());
    }

    /** His things at the death place, tagged as the owner's, as they fall from a dead body. */
    private static BlockPos lieAt(GameTestHelper h, ServerPlayer player) {
        BlockPos place = h.absolutePos(new BlockPos(3, 1, 3));
        ItemEntity item = new ItemEntity(h.getLevel(), place.getX() + .5, place.getY(), place.getZ() + .5, new ItemStack(Items.IRON_INGOT, 3));
        item.getPersistentData().putUUID("aicompanion:belongings", player.getUUID());
        h.getLevel().addFreshEntity(item);
        return place;
    }

    private static CompanionEntity body(GameTestHelper h, ServerPlayer player) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        player.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5);
        c.setOwnerId(player.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
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

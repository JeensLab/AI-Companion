package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionGoals;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** «Будь самостоятельным» is a switch, not an order: the saved pickaxe order stays as it was; «просто будь рядом» calls him off it. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionNeutralRequestTests {
    @GameTest(template = "empty")
    public static void autonomySwitchLeavesPickaxeOrderAlone(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5);
        c.setOwnerId(p.getUUID()); c.setLifestyle(false, false);
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(p);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 6)); c.setItem(1, new ItemStack(Items.STICK, 4));
        h.setBlock(new BlockPos(17, 1, 16), Blocks.CRAFTING_TABLE);
        h.assertTrue(CompanionEvents.executeAction(p, "goal_pickaxes", "", 1).status() == dev.aicompanion.entity.CompanionActionResult.Status.ACCEPTED, "Goal not accepted");
        var before = c.goals().state();
        h.assertTrue(before != CompanionGoals.State.PAUSED_PLAYER, "Goal started paused: " + before);
        CompanionEvents.executeAction(p, "autonomy", "on", 1);
        h.assertTrue(c.goals().state() == before, "Autonomy switch changed the order: " + c.goals().status());
        CompanionEvents.executeAction(p, "autonomy", "off", 1);
        h.assertTrue(c.goals().state() == CompanionGoals.State.PAUSED_PLAYER, "«Просто будь рядом» did not pause it: " + c.goals().status());
        h.succeed();
    }
}

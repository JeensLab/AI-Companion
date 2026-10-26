package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * the player 22.10: «когда рядом со мной монстр, он его не бьёт, только если он уже на меня напал». Like a friend, Oleg goes for a
 * monster at the player's side or on its way to him without a guard order; a creeper is left alone (a hit beside the player
 * may end in its blast, the way wolves leave creepers), and a monster far away is no reason to leave.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGuardTests {
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void zombieBesideThePlayerIsFoughtWithoutWaitingForAHit(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        var zombie = zombie(h, 13, 18); zombie.setNoAi(true);
        h.succeedWhen(() -> h.assertTrue(zombie.getLastHurtByMob() == companion, "He left the zombie beside the player alone; target=" + companion.getTarget() + " activity=" + companion.getActivityForAi() + " zombieHp=" + zombie.getHealth()));
    }

    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void zombieComingForThePlayerIsMetBeforeItHits(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        ServerPlayer player = owner(h, companion);
        var zombie = zombie(h, 27, 14);
        zombie.setTarget(player);
        boolean[] playerHurtFirst = {false};
        h.onEachTick(() -> playerHurtFirst[0] |= player.getHealth() < player.getMaxHealth() && zombie.getLastHurtByMob() != companion);
        h.succeedWhen(() -> {
            h.assertTrue(!playerHurtFirst[0], "The zombie hit the player before Oleg met it");
            h.assertTrue(zombie.getLastHurtByMob() == companion, "Oleg has not hit the zombie yet");
        });
    }

    /** At work too: the zombie by the player first, then the stone. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void zombieBesideThePlayerInterruptsWorkAndWorkGoesOn(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        for (int x = 20; x <= 22; x++) for (int z = 14; z <= 18; z++) for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        companion.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        companion.startWork("gather_stone", "", 4);
        var zombie = zombie(h, 11, 14); zombie.setNoAi(true);
        h.succeedWhen(() -> {
            h.assertTrue(zombie.getLastHurtByMob() == companion, "He kept mining while a zombie stood by the player");
            h.assertTrue(!companion.isWorking() && companion.didLastTaskSucceed(), "The stone was not finished after the fight: " + companion.getLastTaskResult());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void creeperBesideThePlayerIsLeftAlone(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        var creeper = h.spawn(EntityType.CREEPER, 13, 1, 18); creeper.setNoAi(true);
        h.runAtTickTime(140, () -> {
            h.assertTrue(creeper.getLastHurtByMob() != companion, "He hit a creeper beside the player");
            h.succeed();
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void zombieFarFromThePlayerIsNotChased(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        var zombie = zombie(h, 29, 29); zombie.setNoAi(true);
        h.runAtTickTime(140, () -> {
            h.assertTrue(zombie.getLastHurtByMob() != companion, "He ran off to a zombie far from the player");
            h.succeed();
        });
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = h.absolutePos(new BlockPos(14, 1, 15));
        player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        return companion;
    }

    /** A helmet: the test world is in daylight, and a burning zombie is no evidence of a hit. */
    private static net.minecraft.world.entity.monster.Zombie zombie(GameTestHelper h, int x, int z) {
        var zombie = h.spawn(EntityType.ZOMBIE, x, 1, z);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    private static ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

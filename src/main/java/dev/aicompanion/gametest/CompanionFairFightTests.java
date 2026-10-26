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
 * Review 22.10, В1–В2: a stray hit from the player turned Oleg against him (vanilla «hit back whoever hurt me»), and guarding or
 * «перебей мобов» went for anything called a monster — zombified piglins (the whole group then attacks), endermen, the warden.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFairFightTests {
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void strayHitFromThePlayerIsForgiven(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        ServerPlayer owner = owner(h, companion);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        boolean[] turned = {false};
        h.onEachTick(() -> turned[0] |= companion.getTarget() == owner);
        h.runAtTickTime(2, () -> companion.hurt(owner.damageSources().playerAttack(owner), 1));
        h.runAtTickTime(80, () -> {
            h.assertTrue(companion.getLastHurtByMob() == owner, "Test setup: the hit did not register");
            h.assertTrue(!turned[0], "He turned against the player who hit him by accident");
            h.assertTrue(owner.getHealth() >= owner.getMaxHealth(), "He hit the player back: " + owner.getHealth());
            h.succeed();
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void guardLeavesPeacefulPiglinsAndEndermenAlone(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        companion.setProtecting(true);
        var piglin = h.spawn(EntityType.ZOMBIFIED_PIGLIN, 18, 1, 16); piglin.setNoAi(true);
        var enderman = h.spawn(EntityType.ENDERMAN, 16, 1, 18); enderman.setNoAi(true);
        h.runAtTickTime(120, () -> {
            h.assertTrue(piglin.getHealth() >= piglin.getMaxHealth() && enderman.getHealth() >= enderman.getMaxHealth(), "He attacked peaceful neutral mobs");
            h.succeed();
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void huntTakesTheZombieNotThePiglin(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        var piglin = h.spawn(EntityType.ZOMBIFIED_PIGLIN, 19, 1, 16); piglin.setNoAi(true);
        var zombie = h.spawn(EntityType.ZOMBIE, 13, 1, 16); zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // daylight: no burning instead of a kill
        h.assertTrue(companion.startWork("hunt_mobs", "", 1).success(), "Hunt refused");
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking(), "Still hunting: " + companion.getLastTaskResult());
            h.assertTrue(!zombie.isAlive(), "The zombie is still alive");
            h.assertTrue(piglin.getHealth() >= piglin.getMaxHealth(), "He went for the zombified piglin too");
        });
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(companion.getX() - 2, companion.getY(), companion.getZ() - 2);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        return companion;
    }

    private static ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
}

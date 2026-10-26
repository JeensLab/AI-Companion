package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionEquipmentCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.OpenRouterRetry;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.inventory.CompanionEquipmentContainer;
import dev.aicompanion.inventory.CompanionMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionEquipmentTests {
    @GameTest(template = "empty")
    public static void equipSwapUnequipAndPersistence(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        companion.setItem(0, new ItemStack(Items.WOODEN_SWORD));
        companion.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        companion.setItem(2, new ItemStack(Items.IRON_CHESTPLATE));
        companion.setItem(3, new ItemStack(Items.SHIELD));
        helper.assertTrue(companion.equipItem("sword", "mainhand").success(), "Sword equip failed");
        helper.assertTrue(count(companion, Items.WOODEN_SWORD) == 0 && companion.getMainHandItem().is(Items.WOODEN_SWORD), "Sword duplicated or lost");
        helper.assertTrue(companion.equipItem("pickaxe", "mainhand").success(), "Pickaxe equip failed");
        helper.assertTrue(count(companion, Items.WOODEN_SWORD) == 1 && companion.getMainHandItem().is(Items.IRON_PICKAXE), "Previous weapon not returned to bag");
        helper.assertTrue(companion.equipItem("minecraft:iron_chestplate", "auto").success(), "Armor equip failed");
        helper.assertTrue(companion.equipItem("shield", "offhand").success(), "Shield equip failed");
        CompoundTag saved = new CompoundTag();
        companion.addAdditionalSaveData(saved);
        CompanionEntity restored = AICompanionMod.COMPANION.get().create(helper.getLevel());
        restored.readAdditionalSaveData(saved);
        helper.assertTrue(restored.getMainHandItem().is(Items.IRON_PICKAXE) && restored.getOffhandItem().is(Items.SHIELD)
                && restored.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "Equipment not persisted");
        helper.assertTrue(restored.unequipItem("mainhand").success() && restored.getMainHandItem().isEmpty()
                && count(restored, Items.IRON_PICKAXE) == 1, "Unequip lost or copied pickaxe");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullBagAndWrongArmorDoNotLoseEquipment(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        companion.setItem(0, new ItemStack(Items.TORCH, 64));
        for (int slot = 1; slot < 27; slot++) companion.setItem(slot, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(!companion.equipItem("minecraft:torch", "mainhand").success(), "Swap into full bag should fail");
        helper.assertTrue(companion.getMainHandItem().is(Items.WOODEN_SWORD) && count(companion, Items.TORCH) == 64, "Failed swap consumed item");
        helper.assertTrue(!companion.unequipItem("mainhand").success() && companion.getMainHandItem().is(Items.WOODEN_SWORD), "Failed unequip lost sword");
        helper.assertTrue(!companion.equipItem("minecraft:torch", "head").success(), "Invalid armor accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void menuMovesRealGearAndChecksOwnership(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(companion.position());
        companion.setOwnerId(player.getUUID());
        companion.setItem(0, new ItemStack(Items.WOODEN_SWORD));
        CompanionMenu menu = new CompanionMenu(1, player.getInventory(), companion);
        helper.assertTrue(menu.slots.size() == 69 && menu.stillValid(player), "Menu layout or ownership incorrect");
        helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.WOODEN_SWORD)), "Sword allowed in helmet slot");
        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_HELMET)), "Helmet rejected");
        menu.quickMoveStack(player, 6);
        helper.assertTrue(companion.getMainHandItem().is(Items.WOODEN_SWORD) && count(companion, Items.WOODEN_SWORD) == 0, "Bag-to-hand shift transfer failed");
        menu.quickMoveStack(player, 4);
        helper.assertTrue(companion.getMainHandItem().isEmpty() && player.getInventory().countItem(Items.WOODEN_SWORD) == 1, "Gear-to-player shift transfer duplicated or lost sword");
        player.getInventory().setItem(9, new ItemStack(Items.IRON_HELMET));
        menu.quickMoveStack(player, 33);
        helper.assertTrue(companion.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "Player-to-armor transfer failed");
        var stranger = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        stranger.setPos(companion.position());
        helper.assertTrue(!menu.stillValid(stranger), "Other player can use companion bag");
        menu.removed(player);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void weaponDamageArmorAndDurabilityAreReal(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        companion.setItem(0, new ItemStack(Items.WOODEN_SWORD));
        companion.setItem(1, new ItemStack(Items.DIAMOND_CHESTPLATE));
        helper.assertTrue(companion.equipItem("sword", "mainhand").success(), "Equip failed");
        helper.assertTrue(companion.equipItem("chestplate", "auto").success(), "Armor failed");
        var target = helper.spawn(EntityType.PIG, 5, 1, 2);
        target.setNoAi(true);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(companion.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 4, "Sword does not affect attack damage");
            helper.assertTrue(companion.getAttributeValue(Attributes.ARMOR) >= 8, "Armor does not protect companion");
            float health = target.getHealth();
            helper.assertTrue(companion.doHurtTarget(target) && target.getHealth() <= health - 4, "Sword did not deal expected damage");
            helper.assertTrue(companion.getMainHandItem().getDamageValue() == 1, "Sword durability was not consumed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void automaticToolAndWeaponSelectionPreservesBag(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        companion.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        companion.setItem(1, new ItemStack(Items.WOODEN_AXE));
        companion.setItem(2, new ItemStack(Items.IRON_SHOVEL));
        companion.selectToolForBlock(Blocks.OAK_LOG.defaultBlockState());
        helper.assertTrue(companion.getMainHandItem().is(Items.WOODEN_AXE), "Axe not selected for wood");
        companion.selectToolForBlock(Blocks.DIRT.defaultBlockState());
        helper.assertTrue(companion.getMainHandItem().is(Items.IRON_SHOVEL) && count(companion, Items.WOODEN_AXE) == 1, "Shovel not selected or axe lost");
        companion.selectWeaponForCombat();
        helper.assertTrue(companion.getMainHandItem().is(Items.DIAMOND_SWORD) && count(companion, Items.IRON_SHOVEL) == 1, "Combat weapon choice wrong");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void duplicateMergePreservesBothHandsAndArmor(GameTestHelper helper) {
        CompanionEntity canonical = companion(helper);
        CompanionEntity duplicate = companion(helper);
        canonical.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        duplicate.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        duplicate.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        canonical.mergeStoredItemsFrom(duplicate);
        helper.assertTrue(canonical.getMainHandItem().is(Items.WOODEN_SWORD) && count(canonical, Items.IRON_AXE) == 1
                && canonical.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "Equipment lost merging duplicate");
        helper.assertTrue(new CompanionEquipmentContainer(duplicate).isEmpty(), "Duplicate kept a copy of equipment");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void localEquipmentPhrasesAndModelSlots(GameTestHelper helper) {
        helper.assertTrue("companion equip sword mainhand".equals(CompanionEquipmentCommands.parse("Хорошо, возьми меч в руки")), "Screenshot phrase not handled locally");
        helper.assertTrue("companion equip helmet head".equals(CompanionEquipmentCommands.parse("Надень шлем")), "Helmet phrase failed");
        helper.assertTrue("companion equip minecraft:diamond_sword mainhand".equals(CompanionEquipmentCommands.parse("Возьми алмазный меч")), "Specific material was ignored");
        helper.assertTrue("companion unequip mainhand".equals(CompanionEquipmentCommands.parse("Убери меч")), "Unequip phrase failed");
        helper.assertTrue(CompanionEquipmentCommands.parse("Сможешь использовать этот меч?") == null, "Question must not equip an item");
        helper.assertTrue(CompanionEquipmentCommands.parse("Не используй меч") == null, "Negation incorrectly equips weapon");
        helper.assertTrue(CompanionEquipmentCommands.parse("Возьми меч и защищай меня") == null, "Compound command was partially swallowed");
        var actions = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"equip_item\",\"item\":\"minecraft:shield\",\"slot\":\"offhand\"}]"));
        helper.assertTrue("offhand".equals(actions.getFirst().slot()), "Offhand plan lost slot");
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"equip_item\",\"item\":\"minecraft:shield\",\"slot\":\"unknown\"}]")); }
        catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Unknown slot accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void temporaryOpenRouterFailuresRetryWithoutReexecution(GameTestHelper helper) {
        AtomicInteger attempts = new AtomicInteger();
        ArrayList<Duration> delays = new ArrayList<>();
        int response = OpenRouterRetry.execute(() -> CompletableFuture.completedFuture(attempts.incrementAndGet() < 3 ? 429 : 200),
                value -> value, value -> "", delay -> { delays.add(delay); return CompletableFuture.completedFuture(null); }).join();
        helper.assertTrue(response == 200 && attempts.get() == 3 && delays.equals(java.util.List.of(Duration.ofSeconds(1), Duration.ofSeconds(2))), "429 retry policy failed");
        attempts.set(0);
        response = OpenRouterRetry.execute(() -> { attempts.incrementAndGet(); return CompletableFuture.completedFuture(401); },
                value -> value, value -> "", delay -> CompletableFuture.completedFuture(null)).join();
        helper.assertTrue(response == 401 && attempts.get() == 1, "Invalid credentials must not retry");
        attempts.set(0);
        response = OpenRouterRetry.execute(() -> attempts.incrementAndGet() == 1 ? CompletableFuture.<Integer>failedFuture(new IOException("offline"))
                        : CompletableFuture.completedFuture(200), value -> value, value -> "", delay -> CompletableFuture.completedFuture(null)).join();
        helper.assertTrue(response == 200 && attempts.get() == 2, "Network failures must retry");
        helper.assertTrue(OpenRouterRetry.retryDelay("9", 0).equals(Duration.ofSeconds(9)), "Retry-After ignored");
        attempts.set(0);
        response = OpenRouterRetry.execute(() -> { attempts.incrementAndGet(); return CompletableFuture.completedFuture(429); },
                value -> value, value -> "", delay -> CompletableFuture.completedFuture(null)).join();
        helper.assertTrue(response == 429 && attempts.get() == 3, "Retries must stop after three total attempts");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void realMiningSelectsAxeAndConsumesDurability(GameTestHelper helper) {
        CompanionEntity companion = companion(helper);
        var owner = helper.makeMockServerPlayerInLevel();
        owner.setPos(companion.position());
        companion.setOwnerId(owner.getUUID());
        companion.setItem(0, new ItemStack(Items.WOODEN_AXE));
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.OAK_LOG);
        companion.setNoAi(false);
        companion.startGatheringLogs(1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask(), "Mining still active");
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 2)).isAir(), "Log not mined");
            helper.assertTrue(count(companion, Items.OAK_LOG) == 1 && companion.getMainHandItem().is(Items.WOODEN_AXE), "Axe not used or log missing");
            helper.assertTrue(companion.getMainHandItem().getDamageValue() == 1, "Mining did not use axe durability");
        });
    }

    private static CompanionEntity companion(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        CompanionEntity result = helper.spawn(AICompanionMod.COMPANION.get(), 2, 1, 2);
        result.setNoAi(true);
        result.setOwnerId(UUID.randomUUID());
        return result;
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < 27; slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        return count;
    }
}

package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.*;
import dev.aicompanion.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLivingTests {
    @GameTest(template = "tall_tree", timeoutTicks = 180)
    public static void creeperTargetsNearestCompanionAndExplodes(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setNoAi(true);
        var creeper = helper.spawn(EntityType.CREEPER, 18, 1, 16);
        helper.succeedWhen(() -> helper.assertTrue(creeper.isRemoved(), "Creeper did not use its normal fuse near Oleg"));
    }
    @GameTest(template = "tall_tree", timeoutTicks = 110)
    public static void creeperDoesNotPrimeThroughSolidWall(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setNoAi(true);
        var creeper = helper.spawn(EntityType.CREEPER, 19, 1, 16);
        creeper.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        for (int z = 10; z <= 22; z++) for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(18, y, z), Blocks.BEDROCK);
        helper.runAtTickTime(70, () -> { helper.assertTrue(creeper.isAlive() && creeper.getSwellDir() <= 0, "Creeper primed through wall"); helper.succeed(); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void guardFollowsMovingOwner(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setProtecting(true); double start = companion.getX();
        var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos dest = helper.absolutePos(new BlockPos(27, 1, 16)); player.teleportTo(dest.getX() + .5, dest.getY(), dest.getZ() + .5);
        helper.succeedWhen(() -> helper.assertTrue(companion.isProtecting() && companion.getX() > start + 5 && companion.distanceToSqr(player) < 25, "Guard stayed behind"));
    }
    @GameTest(template = "tall_tree", timeoutTicks = 250)
    public static void guardAttacksThreatWhileFollowing(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)); companion.setProtecting(true);
        var zombie = helper.spawn(EntityType.ZOMBIE, 18, 1, 16); zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // daylight: no burning instead of a hit
        helper.succeedWhen(() -> helper.assertTrue(zombie.getHealth() < 20 && companion.isProtecting(), "Guard did not fight a nearby threat"));
    }
    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void retrievesExactAmountAndLeavesOtherItems(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container chest = chest(helper, 22); chest.setItem(0, new ItemStack(Items.OAK_LOG, 15)); chest.setItem(1, new ItemStack(Items.DIAMOND, 3));
        companion.startWork("take_items", "logs", 7);
        helper.succeedWhen(() -> { done(helper, companion); helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) == 7 && count(chest, Items.OAK_LOG) == 8 && count(chest, Items.DIAMOND) == 3, companion.getLastTaskResult()); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void takesAllIncludingToolsWithoutDuplication(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container chest = chest(helper, 22);
        chest.setItem(0, new ItemStack(Items.OAK_LOG, 8)); chest.setItem(1, new ItemStack(Items.WOODEN_AXE));
        companion.startWork("take_items", "all", 1);
        helper.succeedWhen(() -> { done(helper, companion); helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) == 8 && count(companion, Items.WOODEN_AXE) == 1 && chest.isEmpty(), companion.getLastTaskResult()); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void fullBagPreservesChestContents(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container chest = chest(helper, 22); chest.setItem(0, new ItemStack(Items.OAK_LOG, 10));
        for (int slot = 0; slot < 27; slot++) companion.setItem(slot, new ItemStack(Items.DIAMOND, 64));
        companion.startWork("take_items", "logs", 7);
        helper.succeedWhen(() -> { done(helper, companion); helper.assertTrue(!companion.didLastTaskSucceed() && count(chest, Items.OAK_LOG) == 10 && count(companion, Items.OAK_LOG) == 0, "Chest items lost with full bag"); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void partialWithdrawalReportsShortage(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container chest = chest(helper, 22); chest.setItem(0, new ItemStack(Items.OAK_LOG, 3)); companion.startWork("take_items", "logs", 7);
        helper.succeedWhen(() -> { done(helper, companion); helper.assertTrue(!companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) == 3 && chest.isEmpty(), "Partial withdrawal claimed full success or lost items"); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void chestMaterialsCanThenBeCrafted(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container chest = chest(helper, 22); chest.setItem(0, new ItemStack(Items.OAK_LOG, 4)); companion.startWork("take_items", "logs", 4);
        helper.succeedWhen(() -> { done(helper, companion); helper.assertTrue(count(companion, Items.OAK_LOG) == 4, companion.getLastTaskResult());
            helper.assertTrue(companion.craftItemResult("minecraft:wooden_pickaxe", 1).success() && count(companion, Items.WOODEN_PICKAXE) == 1 && chest.isEmpty(), "Retrieved resources not usable for crafting"); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 140)
    public static void placementAndCraftingExposeTimedAnimationAndItem(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setNoAi(true); companion.setItem(0, new ItemStack(Items.CHEST));
        helper.assertTrue(companion.placeBlockResult("minecraft:chest").success() && companion.actionAnimation() == 1 && companion.actionItem().is(Items.CHEST) && count(companion, Items.CHEST) == 0, "Placement missing animation or duplicated material");
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 4));
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(companion.actionAnimation() == 0, "Placement pose stuck");
            helper.assertTrue(companion.craftItemResult("minecraft:wooden_sword", 1).success() && companion.actionAnimation() != 0, "Crafting pose missing");
        });
        helper.runAtTickTime(40, () -> { helper.assertTrue(companion.actionAnimation() == 0 && count(companion, Items.WOODEN_SWORD) == 1, "Crafting did not complete or pose stuck"); helper.succeed(); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 220)
    public static void distantWorkHasBoundedTickingChunkLeasesAndReleasesThem(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setFollowing(false);
        var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        player.teleportTo(player.getX() + 180, player.getY(), player.getZ());
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(CompanionChunks.ticketCount(companion.getUUID()) == 25 && companion.tickCount > 80, "Distant companion not held in ticking chunks");
            companion.setLifestyle(false, false);
        });
        helper.runAtTickTime(160, () -> { helper.assertTrue(CompanionChunks.ticketCount(companion.getUUID()) == 0, "Chunk leases did not release"); helper.succeed(); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 40)
    public static void spontaneousObservationsUseChanceCooldownAndDisableSwitch(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setNoAi(true);
        // New conversations start quietly. Mature the saved opportunity explicitly to test chance/deduplication below.
        companion.tickCount = 300;
        var saved = new net.minecraft.nbt.CompoundTag(); companion.addAdditionalSaveData(saved);
        var cadence = new net.minecraft.nbt.CompoundTag(); cadence.putLong("Next", helper.getLevel().getGameTime());
        saved.put("SocialCadence", cadence); companion.readAdditionalSaveData(saved);
        for (long seed = 0; seed < 10000; seed++) { companion.getRandom().setSeed(seed); if (companion.getRandom().nextFloat() < .35F) { companion.getRandom().setSeed(seed); break; } }
        companion.observeEvent("test", "Рядом действительно находится дерево.");
        helper.assertTrue(companion.socialSequence() == 1 && companion.socialObservation().contains("дерево"), "Observation missing");
        for (int n = 0; n < 20; n++) companion.observeEvent("another:" + n, "Второе наблюдение");
        helper.assertTrue(companion.socialSequence() == 1, "Ambient speech flooded without cooldown");
        companion.setLifestyle(false, false); companion.observeEvent("disabled", "Не должно быть речи");
        helper.assertTrue(companion.socialSequence() == 1, "Disable switch ignored"); helper.succeed();
    }
    @GameTest(template = "empty")
    public static void proximityVoiceDecodesStereoWavToMonoWithoutAudioHardware(GameTestHelper helper) throws Exception {
        var stereo = new javax.sound.sampled.AudioFormat(24000, 16, 2, true, false);
        byte[] samples = new byte[960];
        for (int index = 0; index < samples.length; index += 4) { samples[index] = 50; samples[index + 2] = 50; }
        var bytes = new java.io.ByteArrayOutputStream();
        try (var input = new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(samples), stereo, samples.length / 4)) { javax.sound.sampled.AudioSystem.write(input, javax.sound.sampled.AudioFileFormat.Type.WAVE, bytes); }
        var decoded = VoicePcm.decode(bytes.toByteArray());
        helper.assertTrue(decoded.format().getChannels() == 1 && decoded.format().getSampleSizeInBits() == 16 && !decoded.format().isBigEndian() && decoded.bytes().length == samples.length / 2, "Voice is not positional-compatible mono PCM");
        boolean rejected = false; try { VoicePcm.decode(new byte[]{1, 2, 3}); } catch (Exception expected) { rejected = true; }
        helper.assertTrue(rejected, "Invalid audio accepted"); helper.succeed();
    }
    @GameTest(template = "empty")
    public static void withdrawalCommandsAndPlansPreserveCountsAndNegations(GameTestHelper helper) {
        var action = CompanionWorkCommands.parse("Олег, забери 7 бревен из сундука", "Олег");
        helper.assertTrue(action != null && action.type().equals("take_items") && action.item().equals("logs") && action.count() == 7, "Withdrawal command lost count");
        helper.assertTrue(CompanionWorkCommands.parse("не забирай вещи из сундука", "Олег") == null, "Negation became a withdrawal");
        helper.assertTrue(CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString("[{\"action\":\"take_items\",\"item\":\"minecraft:oak_log\",\"count\":3},{\"action\":\"craft_item\",\"item\":\"minecraft:wooden_pickaxe\"}]")).size() == 2, "Withdrawal crafting chain rejected"); helper.succeed();
    }
    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void groundPickupDoesNotPauseForTenSecondsAfterEachLog(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        BlockPos log = new BlockPos(19, 1, 16); helper.setBlock(log, Blocks.OAK_LOG); long[] broken = {-1}, picked = {-1};
        companion.startGatheringLogs(1);
        helper.onEachTick(() -> {
            if (broken[0] < 0 && helper.getBlockState(log).isAir()) broken[0] = helper.getTick();
            if (picked[0] < 0 && count(companion, Items.OAK_LOG) == 1) picked[0] = helper.getTick();
        });
        helper.succeedWhen(() -> { helper.assertTrue(picked[0] >= 0 && broken[0] >= 0, companion.getLastTaskResult()); done(helper, companion);
            helper.assertTrue(companion.didLastTaskSucceed() && picked[0] - broken[0] < 60 && helper.getTick() - broken[0] < 65, "Pickup or completion stalled after one log: picked=" + (picked[0] - broken[0]) + " done=" + (helper.getTick() - broken[0])); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 650)
    public static void mergedHarvestWithOlderUntaggedPileIsStillCollected(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); BlockPos log = new BlockPos(19, 1, 16); helper.setBlock(log, Blocks.OAK_LOG);
        // An item inside the still-solid log is ejected in a random direction and can
        // travel beyond merge range before harvest. Create the actual merge partner
        // at the drop after mining instead of accidentally testing two unrelated piles.
        boolean[] added = {false};
        helper.onEachTick(() -> {
            if (added[0] || !helper.getBlockState(log).isAir()) return;
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    companion.getBoundingBox().inflate(12), item -> item.getItem().is(Items.OAK_LOG));
            if (drops.isEmpty()) return;
            var source = drops.getFirst();
            var pile = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), source.getX(), source.getY(), source.getZ(), new ItemStack(Items.OAK_LOG, 8));
            pile.setDeltaMovement(0, 0, 0); helper.getLevel().addFreshEntity(pile); added[0] = true;
        });
        companion.startGatheringLogs(1);
        helper.succeedWhen(() -> { helper.assertTrue(added[0], "Merge fixture not created"); done(helper, companion); helper.assertTrue(count(companion, Items.OAK_LOG) == 9, "Merged log survivor lost from harvest tracking: " + companion.getStoredItemsSummary()); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void highTreeLootIsRecoveredAfterClimbingAndSupportCleanup(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setItem(0, new ItemStack(Items.DIRT, 6)); companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        for (int y = 1; y <= 7; y++) helper.setBlock(new BlockPos(19, y, 16), Blocks.OAK_LOG);
        companion.startGatheringLogs(6);
        helper.succeedWhen(() -> { done(helper, companion);
            helper.assertTrue(count(companion, Items.OAK_LOG) == 7 && count(companion, Items.DIRT) == 6 && companion.getTemporarySupportCount() == 0,
                    "Tree or support drop left behind: " + companion.getStoredItemsSummary() + " " + companion.getLastTaskResult()); });
    }
    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = helper.makeMockServerPlayerInLevel(); player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5); companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID()); CompanionEvents.getOwnedCompanion(player); return companion;
    }
    private static Container chest(GameTestHelper helper, int x) { BlockPos pos = new BlockPos(x, 1, 16); helper.setBlock(pos, Blocks.CHEST); return (Container)helper.getBlockEntity(pos); }
    private static int count(Container bag, Item item) { int count = 0; for (int slot = 0; slot < bag.getContainerSize(); slot++) if (bag.getItem(slot).is(item)) count += bag.getItem(slot).getCount(); return count; }
    private static void done(GameTestHelper helper, CompanionEntity companion) { helper.assertTrue(!companion.isGatheringTask(), companion.getLastTaskResult()); }
}

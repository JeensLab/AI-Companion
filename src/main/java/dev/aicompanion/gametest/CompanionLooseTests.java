package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Falling blocks (sand, red sand, gravel): dug with a real shovel, picked up from the ground, counted by the bag. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLooseTests {
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void digsSandWithAShovelAndPicksItUp(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        BlockPos[] patch = new BlockPos[25]; int index = 0;
        for (int x = 18; x <= 22; x++) for (int z = 19; z <= 23; z++) { patch[index] = new BlockPos(x, 1, z); helper.setBlock(patch[index++], Blocks.SAND); }
        var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var started = CompanionEvents.executeAction(player, "gather_loose", "minecraft:sand", 8);
        helper.assertTrue(started.success(), "Sand order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask(), diagnostic(companion));
            helper.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.SAND) >= 8, diagnostic(companion));
            helper.assertTrue(hasShovel(companion), "No shovel was prepared or carried");
            int left = 0; for (BlockPos at : patch) if (helper.getBlockState(at).is(Blocks.SAND)) left++;
            helper.assertTrue(left <= patch.length - 8, "Sand blocks are still in the world: " + left);
        });
    }

    /** A gravel block drops gravel or flint at random, so the check is a balance: every block removed is in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void digsGravelCountsOnlyGravelAndKeepsFlint(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        BlockPos[] patch = new BlockPos[25]; int index = 0;
        for (int x = 18; x <= 22; x++) for (int z = 19; z <= 23; z++) { patch[index] = new BlockPos(x, 1, z); helper.setBlock(patch[index++], Blocks.GRAVEL); }
        var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var started = CompanionEvents.executeAction(player, "gather_loose", "minecraft:gravel", 8);
        helper.assertTrue(started.success(), "Gravel order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask(), diagnostic(companion));
            int gravel = count(companion, Items.GRAVEL), flint = count(companion, Items.FLINT);
            helper.assertTrue(companion.didLastTaskSucceed() && gravel >= 8, diagnostic(companion));
            helper.assertTrue(hasShovel(companion), "No shovel was prepared or carried");
            int left = 0; for (BlockPos at : patch) if (helper.getBlockState(at).is(Blocks.GRAVEL)) left++;
            helper.assertTrue(patch.length - left >= gravel + flint, "Dug blocks " + (patch.length - left) + " are fewer than the bag holds: gravel " + gravel + ", flint " + flint);
            helper.assertTrue(patch.length - left <= gravel + flint, "Dug blocks " + (patch.length - left) + " are more than the bag holds: gravel " + gravel + ", flint " + flint);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3), item -> item.getItem().is(Items.FLINT)).isEmpty(),
                    "Flint was left on the ground");
        });
    }

    /** The column falls as one: taking blocks out of it must never bury Oleg or a player standing beside it. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void sandColumnIsDugWithoutSuffocatingAnyone(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        var player = owner(helper, companion);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(companion.getX() + 5, companion.getY(), companion.getZ() + 4);
        BlockPos[] column = new BlockPos[4];
        for (int y = 0; y < column.length; y++) { column[y] = new BlockPos(20, 1 + y, 20); helper.setBlock(column[y], Blocks.SAND); }
        String[] hurt = {null}; java.util.ArrayDeque<String> trail = new java.util.ArrayDeque<>();
        helper.onEachTick(() -> {
            var at = companion.position().subtract(helper.absolutePos(column[0]).getCenter());
            trail.add(helper.getTick() + ":" + String.format("%.2f,%.2f,%.2f", at.x, at.y, at.z) + columnShape(helper, column) + "f"
                    + helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, helper.getBounds().inflate(3)).size());
            if (trail.size() > 14) trail.poll();
            for (LivingEntity living : new LivingEntity[]{companion, player}) {
                if (hurt[0] == null && (living.getHealth() < living.getMaxHealth() || living.getLastDamageSource() != null))
                    hurt[0] = living.getName().getString() + " took " + living.getLastDamageSource() + ", health " + living.getHealth() + " at "
                            + living.blockPosition().subtract(helper.absolutePos(column[0])) + " from the column foot, column " + columnShape(helper, column) + ", falling "
                            + helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, helper.getBounds().inflate(3)).size() + ", left " + blocksLeft(helper, Blocks.SAND, column) + ", trail " + trail;
            }
        });
        var started = CompanionEvents.executeAction(player, "gather_loose", "minecraft:sand", 4);
        helper.assertTrue(started.success(), "Sand order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt[0] == null, "Damage during the dig: " + hurt[0]);
            helper.assertTrue(!companion.isGatheringTask() && companion.didLastTaskSucceed(), diagnostic(companion) + " Oleg " + companion.blockPosition().subtract(helper.absolutePos(column[0]))
                    + " player " + player.blockPosition().subtract(helper.absolutePos(column[0])) + " column " + columnShape(helper, column) + " ground " + onGround(helper, Items.SAND));
            helper.assertTrue(count(companion, Items.SAND) == 4, "The bag holds " + count(companion, Items.SAND) + " sand, not 4");
            helper.assertTrue(blocksLeft(helper, Blocks.SAND, column) == 0, "Sand is still standing: " + blocksLeft(helper, Blocks.SAND, column));
            helper.assertTrue(onGround(helper, Items.SAND) == 0, "Sand was left on the ground");
        });
    }

    /** Sand over their heads falls on them once its support is gone, so it stays while they stand under it. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void sandOverheadIsNotDugWhileTheyStandUnderIt(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        var player = owner(helper, companion);
        player.teleportTo(companion.getX() + 1, companion.getY(), companion.getZ());
        BlockPos[] roof = {new BlockPos(16, 3, 16), new BlockPos(17, 3, 16)};
        for (BlockPos at : roof) { helper.setBlock(at.below(), Blocks.STONE); helper.setBlock(at, Blocks.SAND); }
        boolean[] ordered = {false};
        // The support goes without a block update, as in a cave where the sand was generated hanging.
        helper.runAfterDelay(5, () -> {
            for (BlockPos at : roof) helper.getLevel().setBlock(helper.absolutePos(at.below()), Blocks.AIR.defaultBlockState(), 2 | 16);
            helper.assertTrue(blocksLeft(helper, Blocks.SAND, roof) == 2 && helper.getBlockState(roof[0].below()).isAir(), "The roof did not stay hanging");
            var started = CompanionEvents.executeAction(player, "gather_loose", "minecraft:sand", 2);
            helper.assertTrue(started.success(), "Sand order rejected: " + started.message());
            ordered[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ordered[0] && !companion.isGatheringTask() && !companion.isWorking(), "Still working: " + diagnostic(companion));
            helper.assertTrue(!companion.didLastTaskSucceed(), "Overhead sand was reported as dug: " + diagnostic(companion));
            helper.assertTrue(blocksLeft(helper, Blocks.SAND, roof) == 2, "The overhead sand was dug");
            helper.assertTrue(count(companion, Items.SAND) == 0 && onGround(helper, Items.SAND) == 0, "Sand came out of the roof");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void unsafeSandStaysAndTheOrderEndsHonestly(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        Hazards hazards = placeHazards(helper);
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:sand", 4);
        helper.assertTrue(started.success(), "Sand order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still working: " + diagnostic(companion));
            helper.assertTrue(!companion.didLastTaskSucceed(), "Unsafe sand was reported as dug: " + diagnostic(companion));
            hazards.assertUntouched(helper);
            helper.assertTrue(count(companion, Items.SAND) == 0 && onGround(helper, Items.SAND) == 0, "Sand was taken from an unsafe place");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void onlyTheSafePatchIsDugNextToUnsafeSand(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        Hazards hazards = placeHazards(helper);
        BlockPos[] patch = new BlockPos[9]; int index = 0;
        for (int x = 18; x <= 20; x++) for (int z = 19; z <= 21; z++) { patch[index] = new BlockPos(x, 1, z); helper.setBlock(patch[index++], Blocks.SAND); }
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:sand", 4);
        helper.assertTrue(started.success(), "Sand order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && companion.didLastTaskSucceed(), diagnostic(companion));
            hazards.assertUntouched(helper);
            int dug = patch.length - blocksLeft(helper, Blocks.SAND, patch);
            helper.assertTrue(dug >= 4 && dug == count(companion, Items.SAND) + onGround(helper, Items.SAND), "Dug " + dug + " blocks, the bag holds " + count(companion, Items.SAND));
        });
    }

    /** A drop that does not fit is never spawned: the block stays, nothing is lost. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void fullBagStopsTheDigWithoutLosingAnything(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper);
        for (int slot = 0; slot < companion.getContainerSize(); slot++) companion.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
        int cobblestone = count(companion, Items.COBBLESTONE);
        BlockPos[] patch = new BlockPos[9]; int index = 0;
        for (int x = 18; x <= 20; x++) for (int z = 19; z <= 21; z++) { patch[index] = new BlockPos(x, 1, z); helper.setBlock(patch[index++], Blocks.SAND); }
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:sand", 4);
        boolean[] refused = {!started.success()};
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still working: " + diagnostic(companion));
            helper.assertTrue(refused[0] || !companion.didLastTaskSucceed(), "A full bag was reported as success: " + diagnostic(companion));
            int standing = blocksLeft(helper, Blocks.SAND, patch);
            helper.assertTrue(standing + count(companion, Items.SAND) + onGround(helper, Items.SAND) == patch.length,
                    "Sand was lost: " + standing + " standing, " + count(companion, Items.SAND) + " in the bag, " + onGround(helper, Items.SAND) + " on the ground");
            helper.assertTrue(count(companion, Items.COBBLESTONE) == cobblestone, "The bag changed: " + companion.getStoredItemsSummary());
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void cancelMidDigStopsWithoutDuplicates(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
        var player = owner(helper, companion);
        BlockPos[] patch = sandPatch(helper);
        int[] dugAtStop = {-1}; long[] stoppedAt = {0};
        helper.onEachTick(() -> {
            if (dugAtStop[0] < 0 && companion.isGatheringTask() && patch.length - blocksLeft(helper, Blocks.SAND, patch) >= 3) {
                helper.assertTrue(CompanionEvents.executeAction(player, "stop", "", 1).success(), "Stop not accepted");
                dugAtStop[0] = patch.length - blocksLeft(helper, Blocks.SAND, patch); stoppedAt[0] = helper.getTick();
            }
        });
        var started = CompanionEvents.executeAction(player, "gather_loose", "minecraft:sand", 20);
        helper.assertTrue(started.success(), "Sand order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(dugAtStop[0] >= 0 && helper.getTick() - stoppedAt[0] >= 60, "Not cancelled yet");
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking() && !companion.didLastTaskSucceed(), "Still digging: " + diagnostic(companion));
            int dug = patch.length - blocksLeft(helper, Blocks.SAND, patch);
            helper.assertTrue(dug == dugAtStop[0], "Blocks were dug after the cancel: " + dugAtStop[0] + " -> " + dug);
            helper.assertTrue(dug == count(companion, Items.SAND) + onGround(helper, Items.SAND), "Dug " + dug + ", the bag and the ground hold "
                    + count(companion, Items.SAND) + " + " + onGround(helper, Items.SAND));
            helper.assertTrue(aliveCompanions(helper) == 1, "Companions in the world: " + aliveCompanions(helper));
        });
    }

    /**
     * Leaving the world interrupts a work order honestly (his things stay with him); the ground kind is saved with the task,
     * a tag from before it existed reads as soil, and nothing is dug or duplicated after loading.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void reloadMidDigKeepsTheGroundAndOldTagsReadSoil(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
        var player = owner(helper, companion);
        BlockPos[] patch = sandPatch(helper);
        CompanionEntity[] current = {companion}; int[] stage = {0, -1}; // stage, blocks dug at the reload
        long[] reloadedAt = {0};
        helper.onEachTick(() -> {
            if (stage[0] != 0 || !companion.isGatheringTask() || patch.length - blocksLeft(helper, Blocks.SAND, patch) < 3) return;
            stage[0] = 1;
            CompoundTag data = new CompoundTag(); companion.saveWithoutId(data);
            helper.assertTrue("SAND".equals(data.getString("GatheringGround")) && data.getBoolean("GatheringDirt"), "The ground was not saved: " + data.getString("GatheringGround"));
            CompoundTag oldTag = data.copy(); oldTag.remove("GatheringGround");
            CompanionEntity old = AICompanionMod.COMPANION.get().create(helper.getLevel()); old.load(oldTag);
            CompoundTag reread = new CompoundTag(); old.saveWithoutId(reread);
            helper.assertTrue("SOIL".equals(reread.getString("GatheringGround")), "An old tag did not read as soil: " + reread.getString("GatheringGround"));
            companion.discard();
            helper.runAfterDelay(1, () -> {
                CompanionEntity replacement = AICompanionMod.COMPANION.get().create(helper.getLevel());
                replacement.load(data); helper.getLevel().addFreshEntity(replacement); current[0] = replacement;
                CompoundTag loaded = new CompoundTag(); replacement.saveWithoutId(loaded);
                helper.assertTrue("SAND".equals(loaded.getString("GatheringGround")), "The ground was lost on loading: " + loaded.getString("GatheringGround"));
                stage[1] = patch.length - blocksLeft(helper, Blocks.SAND, patch); reloadedAt[0] = helper.getTick(); stage[0] = 2;
            });
        });
        var started = CompanionEvents.executeAction(player, "gather_loose", "minecraft:sand", 12);
        helper.assertTrue(started.success(), "Sand order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] == 2 && helper.getTick() - reloadedAt[0] >= 80, "Not reloaded yet");
            CompanionEntity loaded = current[0];
            helper.assertTrue(!loaded.isGatheringTask() && !loaded.isWorking() && !loaded.didLastTaskSucceed(), "The interrupted order goes on: " + diagnostic(loaded));
            helper.assertTrue(loaded.getDeedsForAi().contains("Работа прервалась"), "The interruption is not in the journal: " + loaded.getDeedsForAi());
            int dug = patch.length - blocksLeft(helper, Blocks.SAND, patch);
            helper.assertTrue(dug == stage[1], "Blocks were dug after the reload: " + stage[1] + " -> " + dug);
            helper.assertTrue(dug == count(loaded, Items.SAND) + onGround(helper, Items.SAND), "Dug " + dug + ", the bag and the ground hold "
                    + count(loaded, Items.SAND) + " + " + onGround(helper, Items.SAND));
            helper.assertTrue(aliveCompanions(helper) == 1, "Companions in the world: " + aliveCompanions(helper));
        });
    }

    /**
     * Flint is a gravel block's one-in-ten drop (one in four with Fortune III, which keeps two flint within the break budget).
     * Every gravel block is one unit: dug it becomes a gravel or a flint item, so blocks plus items stay constant, and the natural
     * patch loses about one block per flint because the gravel taken out of the bag is put down and dug again.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void digsFlintByPuttingGravelBackDown(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        companion.setItem(1, enchanted(helper, Items.STONE_SHOVEL, Enchantments.FORTUNE, 3));
        gravelPatch(helper);
        int before = blocksInWorld(helper, Blocks.GRAVEL), units = units(helper, companion);
        var drops = trackDrops(helper);
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:flint", 2);
        helper.assertTrue(started.success(), "Flint order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask(), diagnostic(companion));
            int flint = count(companion, Items.FLINT);
            helper.assertTrue(companion.didLastTaskSucceed() && flint >= 2, diagnostic(companion));
            helper.assertTrue(units(helper, companion) == units, "Gravel units " + units + " became " + units(helper, companion) + ": " + diagnostic(companion));
            helper.assertTrue(onGround(helper, Items.GRAVEL) + onGround(helper, Items.FLINT) == 0, "Gravel or flint was left on the ground");
            helper.assertTrue(fallingBlocks(helper) == 0, "A gravel block is still falling");
            helper.assertTrue(before - blocksInWorld(helper, Blocks.GRAVEL) <= flint + 1, "The patch lost " + (before - blocksInWorld(helper, Blocks.GRAVEL)) + " blocks for " + flint + " flint");
            helper.assertTrue(drops.size() >= flint, "Fewer drops " + drops.size() + " than flint " + flint);
        });
    }

    /** Without Fortune the order may run out of its budget; either way the balance holds and the landscape pays one block per flint at most. */
    @GameTest(template = "tall_tree", timeoutTicks = 6500)
    public static void flintCostsTheLandscapeOneBlockPerFlint(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        gravelPatch(helper);
        int before = blocksInWorld(helper, Blocks.GRAVEL), units = units(helper, companion);
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:flint", 2);
        helper.assertTrue(started.success(), "Flint order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still working: " + diagnostic(companion));
            int flint = count(companion, Items.FLINT);
            helper.assertTrue(companion.didLastTaskSucceed() == (flint >= 2), "Success does not match the flint " + flint + ": " + diagnostic(companion));
            helper.assertTrue(units(helper, companion) == units, "Gravel units " + units + " became " + units(helper, companion) + ": " + diagnostic(companion));
            helper.assertTrue(onGround(helper, Items.GRAVEL) + onGround(helper, Items.FLINT) == 0, "Gravel or flint was left on the ground");
            helper.assertTrue(before - blocksInWorld(helper, Blocks.GRAVEL) <= flint + 1, "The patch lost " + (before - blocksInWorld(helper, Blocks.GRAVEL)) + " blocks for " + flint + " flint");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void flintOrderWithoutGravelEndsHonestlyAndPlacesNothing(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:flint", 2);
        helper.assertTrue(started.success(), "Flint order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still working: " + diagnostic(companion));
            helper.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().startsWith("Выпало 0 из 2 кремня"), "Not an honest refusal: " + diagnostic(companion));
            helper.assertTrue(units(helper, companion) == 0 && blocksInWorld(helper, Blocks.GRAVEL) == 0, "Gravel appeared from nothing: " + diagnostic(companion));
        });
    }

    /**
     * One gravel block, no Fortune, one flint ordered: the block is dug and put down again until it becomes flint (a tenth of the
     * breaks) or the whole break budget is spent. Both ends are exact and the one block is never more than one unit.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void flintBudgetEndsHonestlyWithOneGravelBlock(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        helper.setBlock(new BlockPos(19, 1, 20), Blocks.GRAVEL);
        var gravelDrops = trackDrops(helper, Items.GRAVEL); var flintDrops = trackDrops(helper, Items.FLINT);
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:flint", 1);
        helper.assertTrue(started.success(), "Flint order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still working: " + diagnostic(companion));
            int flint = count(companion, Items.FLINT), breaks = gravelDrops.size() + flintDrops.size();
            helper.assertTrue(units(helper, companion) == 1, "One gravel block became " + units(helper, companion) + " units, flint " + flint);
            helper.assertTrue(onGround(helper, Items.GRAVEL) + onGround(helper, Items.FLINT) == 0 && fallingBlocks(helper) == 0, "Gravel or flint was left outside the bag");
            if (flint == 0) {
                helper.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().startsWith("Выпало 0 из 1 кремня"), "Not an honest refusal: " + diagnostic(companion));
                helper.assertTrue(breaks == 30 && flintDrops.isEmpty(), "The budget of 30 breaks ended after " + breaks + " breaks, flint drops " + flintDrops.size());
            } else {
                helper.assertTrue(companion.didLastTaskSucceed() && flint == 1 && flintDrops.size() == 1, "Not a success with one flint: " + diagnostic(companion));
                helper.assertTrue(breaks <= 30, "More breaks than the budget: " + breaks);
            }
        });
    }

    /** Stopping in the middle of the dig-and-put-down loop loses nothing: the placed gravel is a block of the world, the rest is in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void cancelMidFlintLoopKeepsTheBalance(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        gravelPatch(helper);
        int units = units(helper, companion);
        var drops = trackDrops(helper);
        var player = owner(helper, companion);
        int[] stopped = {-1}; long[] stoppedAt = {0};
        helper.onEachTick(() -> {
            if (stopped[0] < 0 && companion.isGatheringTask() && drops.size() >= 3) {
                helper.assertTrue(CompanionEvents.executeAction(player, "stop", "", 1).success(), "Stop not accepted");
                stopped[0] = drops.size(); stoppedAt[0] = helper.getTick();
            }
        });
        var started = CompanionEvents.executeAction(player, "gather_loose", "minecraft:flint", 8);
        helper.assertTrue(started.success(), "Flint order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(stopped[0] >= 0 && helper.getTick() - stoppedAt[0] >= 60, "Not cancelled yet");
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking() && !companion.didLastTaskSucceed(), "Still digging: " + diagnostic(companion));
            helper.assertTrue(drops.size() <= stopped[0] + 1, "Blocks were broken after the cancel: " + stopped[0] + " -> " + drops.size());
            helper.assertTrue(units(helper, companion) == units, "Gravel units " + units + " became " + units(helper, companion) + ": " + diagnostic(companion));
            helper.assertTrue(fallingBlocks(helper) == 0, "A gravel block is still falling");
            helper.assertTrue(aliveCompanions(helper) == 1, "Companions in the world: " + aliveCompanions(helper));
        });
    }

    /** Only what sits ON the column is cover: a canopy or a cave roof with air under it does not stop the dig. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void sandUnderATreeCanopyWithAnAirGapIsDug(GameTestHelper helper) {
        roofedPatchIsDug(helper, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 5, Blocks.SAND, Items.SAND);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void gravelUnderACaveCeilingIsDug(GameTestHelper helper) {
        roofedPatchIsDug(helper, Blocks.STONE.defaultBlockState(), 4, Blocks.GRAVEL, Items.GRAVEL);
    }

    private static void roofedPatchIsDug(GameTestHelper helper, net.minecraft.world.level.block.state.BlockState roof, int roofY, Block loose, Item item) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        for (int x = 10; x <= 28; x++) for (int z = 10; z <= 28; z++) helper.setBlock(new BlockPos(x, roofY, z), roof);
        BlockPos[] patch = new BlockPos[9]; int index = 0;
        for (int x = 19; x <= 21; x++) for (int z = 19; z <= 21; z++) { patch[index] = new BlockPos(x, 1, z); helper.setBlock(patch[index++], loose); }
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(), 4);
        helper.assertTrue(started.success(), "Order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still working: " + diagnostic(companion));
            helper.assertTrue(companion.didLastTaskSucceed() && count(companion, item) >= 4, "Nothing was dug under the roof: " + diagnostic(companion));
            helper.assertTrue(blocksLeft(helper, loose, patch) <= patch.length - 4, "The blocks under the roof are still standing: " + blocksLeft(helper, loose, patch));
            helper.assertTrue(helper.getBlockState(new BlockPos(20, roofY, 20)).is(roof.getBlock()), "The roof was touched");
        });
    }

    /** A cactus on the sand grows out of the column and breaks with it: that sand is not taken, the free sand is. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void sandUnderACactusIsKept(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        BlockPos[] covered = {new BlockPos(18, 1, 19), new BlockPos(20, 1, 19)}, free = {new BlockPos(18, 1, 22), new BlockPos(20, 1, 22)};
        for (BlockPos at : covered) { helper.setBlock(at, Blocks.SAND); helper.setBlock(at.above(), Blocks.CACTUS); }
        for (BlockPos at : free) helper.setBlock(at, Blocks.SAND);
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:sand", 4);
        helper.assertTrue(started.success(), "Sand order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still working: " + diagnostic(companion));
            helper.assertTrue(!companion.didLastTaskSucceed() && count(companion, Items.SAND) == 2, "Not the two free blocks only: " + diagnostic(companion));
            helper.assertTrue(blocksLeft(helper, Blocks.SAND, free) == 0, "The free sand was left");
            helper.assertTrue(blocksLeft(helper, Blocks.SAND, covered) == 2 && helper.getBlockState(covered[0].above()).is(Blocks.CACTUS)
                    && helper.getBlockState(covered[1].above()).is(Blocks.CACTUS), "Sand under a cactus was dug");
        });
    }

    /** Silk Touch would turn every gravel block into gravel for good: the plain shovel is the one in his hand at each break. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void flintIsNeverDugWithASilkTouchShovel(GameTestHelper helper) {
        CompanionEntity companion = setupClear(helper); companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        companion.setItem(1, enchanted(helper, Items.IRON_SHOVEL, Enchantments.SILK_TOUCH, 1)); companion.setItem(2, new ItemStack(Items.WOODEN_SHOVEL));
        gravelPatch(helper);
        int[] last = {blocksInWorld(helper, Blocks.GRAVEL)}; String[] silk = {null};
        helper.onEachTick(() -> {
            int now = blocksInWorld(helper, Blocks.GRAVEL);
            if (now < last[0] && silk[0] == null && companion.getMainHandItem().is(Items.IRON_SHOVEL)) silk[0] = "Gravel was dug with " + companion.getMainHandItem();
            last[0] = now;
        });
        var started = CompanionEvents.executeAction(owner(helper, companion), "gather_loose", "minecraft:flint", 1);
        helper.assertTrue(started.success(), "Flint order rejected: " + started.message());
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still working: " + diagnostic(companion));
            helper.assertTrue(silk[0] == null, silk[0]);
            helper.assertTrue(last[0] < 25, "No gravel was dug");
        });
    }

    /** Sand that is not to be taken: a brushable block, sand beside water, sand under a chest the player placed. */
    private record Hazards(BlockPos[] suspicious, BlockPos[] ring, BlockPos water, BlockPos underChest, BlockPos chest) {
        void assertUntouched(GameTestHelper helper) {
            helper.assertTrue(blocksLeft(helper, Blocks.SUSPICIOUS_SAND, suspicious) == suspicious.length, "Suspicious sand was dug");
            helper.assertTrue(blocksLeft(helper, Blocks.SAND, ring) == ring.length && helper.getBlockState(water).is(Blocks.WATER), "Sand beside the water was dug");
            helper.assertTrue(helper.getBlockState(underChest).is(Blocks.SAND) && helper.getBlockState(chest).is(Blocks.CHEST), "Sand under the chest was dug");
        }
    }

    private static Hazards placeHazards(GameTestHelper helper) {
        BlockPos[] suspicious = {new BlockPos(24, 1, 12), new BlockPos(25, 1, 12)};
        for (BlockPos at : suspicious) helper.setBlock(at, Blocks.SUSPICIOUS_SAND);
        BlockPos water = new BlockPos(24, 1, 24);
        BlockPos[] ring = {water.north(), water.south(), water.east(), water.west()};
        for (BlockPos at : ring) helper.setBlock(at, Blocks.SAND);
        helper.setBlock(water, Blocks.WATER);
        BlockPos underChest = new BlockPos(12, 1, 22);
        helper.setBlock(underChest, Blocks.SAND); helper.setBlock(underChest.above(), Blocks.CHEST);
        return new Hazards(suspicious, ring, water, underChest, underChest.above());
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(helper.getLevel().registryAccess().holderOrThrow(enchantment), level);
        return stack;
    }
    private static void gravelPatch(GameTestHelper helper) {
        for (int x = 18; x <= 22; x++) for (int z = 19; z <= 23; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.GRAVEL);
    }
    private static int blocksInWorld(GameTestHelper helper, Block block) {
        int total = 0;
        for (int x = 0; x < 32; x++) for (int y = 1; y <= 12; y++) for (int z = 0; z < 32; z++) if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) total++;
        return total;
    }
    private static int fallingBlocks(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, helper.getBounds().inflate(3)).size();
    }
    /** Gravel blocks, gravel and flint items anywhere in the plot: a gravel block dug becomes exactly one of the items. */
    private static int units(GameTestHelper helper, CompanionEntity companion) {
        return blocksInWorld(helper, Blocks.GRAVEL) + count(companion, Items.GRAVEL) + count(companion, Items.FLINT)
                + onGround(helper, Items.GRAVEL) + onGround(helper, Items.FLINT) + fallingBlocks(helper);
    }
    /** Every gravel or flint item that ever lay on the ground: one per block he broke, at least. */
    private static java.util.Set<java.util.UUID> trackDrops(GameTestHelper helper) { return trackDrops(helper, Items.GRAVEL, Items.FLINT); }
    private static java.util.Set<java.util.UUID> trackDrops(GameTestHelper helper, Item... items) {
        java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
        helper.onEachTick(() -> {
            for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3), found -> java.util.Arrays.stream(items).anyMatch(found.getItem()::is)))
                seen.add(entity.getUUID());
        });
        return seen;
    }
    private static BlockPos[] sandPatch(GameTestHelper helper) {
        BlockPos[] patch = new BlockPos[25]; int index = 0;
        for (int x = 18; x <= 22; x++) for (int z = 19; z <= 23; z++) { patch[index] = new BlockPos(x, 1, z); helper.setBlock(patch[index++], Blocks.SAND); }
        return patch;
    }
    private static String columnShape(GameTestHelper helper, BlockPos[] column) {
        StringBuilder shape = new StringBuilder();
        for (BlockPos at : column) shape.append(helper.getBlockState(at).is(Blocks.SAND) ? 'S' : '.');
        return shape.toString();
    }
    private static int blocksLeft(GameTestHelper helper, Block block, BlockPos[] positions) {
        int left = 0; for (BlockPos at : positions) if (helper.getBlockState(at).is(block)) left++;
        return left;
    }
    private static int onGround(GameTestHelper helper, Item item) {
        int total = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3), found -> found.getItem().is(item))) total += entity.getItem().getCount();
        return total;
    }
    private static int aliveCompanions(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(CompanionEntity.class, helper.getBounds().inflate(8), CompanionEntity::isAlive).size();
    }
    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper helper, CompanionEntity companion) {
        return helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
    /** The plot is cleared above the floor: the template's tree must not cover or support any of the sand. */
    private static CompanionEntity setupClear(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int y = 1; y <= 12; y++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        return setup(helper);
    }

    @GameTest(template = "empty")
    public static void looseOrdersAreValidatedAndParsed(GameTestHelper helper) {
        for (String item : new String[]{"minecraft:sand", "minecraft:red_sand", "minecraft:gravel"}) {
            var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{'action':'gather_loose','item':'" + item + "','count':64}]"));
            helper.assertTrue(plan.size() == 1 && item.equals(plan.getFirst().item()) && plan.getFirst().count() == 64, "Loose order for " + item + " was rejected or chunked");
        }
        helper.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{'action':'gather_loose','item':'minecraft:sand'}]")).getFirst().count() == 8, "Default loose count is not 8");
        for (String bad : new String[]{"[{'action':'gather_loose','item':'minecraft:suspicious_sand','count':8}]", "[{'action':'gather_loose','item':'minecraft:white_concrete_powder','count':8}]",
                "[{'action':'gather_loose','item':'minecraft:sand','count':0}]", "[{'action':'gather_loose','item':'minecraft:sand','count':65}]", "[{'action':'gather_loose','count':8}]"}) {
            boolean rejected = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(bad)); } catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "Invalid loose order accepted: " + bad);
        }
        expectLoose(helper, "Олег, накопай 12 песка", "minecraft:sand", 12);
        expectLoose(helper, "добудь мне красного песка", "minecraft:red_sand", 8);
        expectLoose(helper, "набери 20 блоков гравия поблизости", "minecraft:gravel", 20);
        expectLoose(helper, "выкопай гравий", "minecraft:gravel", 8);
        helper.assertTrue(CompanionWorkCommands.parse("не копай песок", "Олег") == null, "Negated loose command was parsed");
        helper.assertTrue(CompanionWorkCommands.parse("накопай песок и вернись", "Олег") == null, "Compound loose command was parsed");
        helper.succeed();
    }

    private static void expectLoose(GameTestHelper helper, String phrase, String item, int count) {
        PlannedAction action = CompanionWorkCommands.parse(phrase, "Олег");
        helper.assertTrue(action != null && "gather_loose".equals(action.type()) && item.equals(action.item()) && action.count() == count, "Phrase not parsed as " + item + " x" + count + ": " + phrase);
    }

    private static CompanionEntity setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16); var player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5); companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID()); CompanionEvents.getOwnedCompanion(player); companion.setSearchRadius(16); return companion;
    }
    private static boolean hasShovel(CompanionEntity companion) {
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(ItemTags.SHOVELS)) return true;
        return companion.getMainHandItem().is(ItemTags.SHOVELS) || companion.getOffhandItem().is(ItemTags.SHOVELS);
    }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0; for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount(); return count;
    }
    private static String diagnostic(CompanionEntity companion) { return companion.getLastTaskResult() + " Bag=" + companion.getStoredItemsSummary() + " At=" + companion.position(); }
}

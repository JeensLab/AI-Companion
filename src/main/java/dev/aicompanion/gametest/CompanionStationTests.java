package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The smoker, the blast furnace and the stonecutter like a player uses them: the faster block for its own kind of work. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionStationTests {
    /** A free smoker a few steps past a free furnace: the meat goes into the smoker and is done in half the furnace's time. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void meatGoesIntoTheSmokerAndCooksTwiceAsFast(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(18, 1, 16), smoker = new BlockPos(22, 1, 16);
        h.setBlock(furnace, Blocks.FURNACE); h.setBlock(smoker, Blocks.SMOKER);
        companion.setItem(0, new ItemStack(Items.BEEF, 3)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:beef", 3).success(), "Smelt order not accepted");
        long[] loadedAt = {-1}, doneAt = {-1};
        h.onEachTick(() -> {
            if (loadedAt[0] < 0 && container(h, smoker).getItem(0).is(Items.BEEF)) loadedAt[0] = h.getTick();
            if (doneAt[0] < 0 && container(h, smoker).getItem(2).getCount() == 3) doneAt[0] = h.getTick();
        });
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            // The smoker's 100 ticks an item (the tick of loading and of the last item counted by the same watch), not the furnace's 200.
            h.assertTrue(loadedAt[0] >= 0 && doneAt[0] - loadedAt[0] >= 3 * 100 - 5 && doneAt[0] - loadedAt[0] < 3 * 200,
                    "Not the smoker's time: loaded at " + loadedAt[0] + ", done at " + doneAt[0]);
            h.assertTrue(count(companion, Items.COOKED_BEEF) == 3 && count(companion, Items.COAL) == 1, "Wrong product or fuel: " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, furnace).isEmpty() && container(h, smoker).isEmpty(), "Something left behind or the furnace used");
        });
    }

    /** Raw iron goes into the blast furnace, not the nearer furnace. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void rawIronGoesIntoTheBlastFurnace(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos furnace = new BlockPos(18, 1, 16), blast = new BlockPos(22, 1, 16);
        h.setBlock(furnace, Blocks.FURNACE); h.setBlock(blast, Blocks.BLAST_FURNACE);
        companion.setItem(0, new ItemStack(Items.RAW_IRON, 3)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:iron_ingot", 3).success(), "Smelt order not accepted");
        boolean[] blastUsed = {false};
        h.onEachTick(() -> blastUsed[0] |= container(h, blast).getItem(0).is(Items.RAW_IRON));
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.IRON_INGOT) == 3, companion.getLastTaskResult());
            h.assertTrue(blastUsed[0] && container(h, furnace).isEmpty(), "The blast furnace was not used");
        });
    }

    /** Sand has no blasting recipe: the free blast furnace beside him stays empty and the furnace makes the glass. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void sandIsNotPutIntoTheBlastFurnace(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        BlockPos blast = new BlockPos(18, 1, 16), furnace = new BlockPos(22, 1, 16);
        h.setBlock(blast, Blocks.BLAST_FURNACE); h.setBlock(furnace, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.SAND, 2)); companion.setItem(1, new ItemStack(Items.COAL, 1));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:glass", 2).success(), "Smelt order not accepted");
        boolean[] blastUsed = {false};
        h.onEachTick(() -> blastUsed[0] |= !container(h, blast).isEmpty());
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.GLASS) == 2, companion.getLastTaskResult());
            h.assertTrue(!blastUsed[0] && blocks(h, Blocks.FURNACE) == 1, "Sand went into the blast furnace or a furnace was added");
        });
    }

    /** No smoker anywhere, only a free furnace: he makes a smoker from the furnace and four logs in his bag and cooks in it. */
    @GameTest(template = "tall_tree", timeoutTicks = 1400)
    public static void withNoSmokerHeMakesOneFromHisFurnaceAndLogs(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.CRAFTING_TABLE);
        BlockPos theirs = new BlockPos(13, 1, 16); h.setBlock(theirs, Blocks.FURNACE);
        companion.setItem(0, new ItemStack(Items.BEEF, 3)); companion.setItem(1, new ItemStack(Items.COAL, 2));
        companion.setItem(2, new ItemStack(Items.FURNACE)); companion.setItem(3, new ItemStack(Items.OAK_LOG, 4));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:beef", 3).success(), "Smelt order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COOKED_BEEF) == 3, companion.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.SMOKER) == 1 && count(companion, Items.OAK_LOG) == 0 && count(companion, Items.FURNACE) == 0,
                    "No smoker made from the bag: " + companion.getStoredItemsSummary());
            h.assertTrue(container(h, theirs).isEmpty(), "The furnace was used beside his smoker");
        });
    }

    /** The four logs are all he has to burn: they go under the meat, not into a smoker. */
    @GameTest(template = "tall_tree", timeoutTicks = 1400)
    public static void logsHeMustBurnAreNotMadeIntoASmoker(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.BEEF, 3)); companion.setItem(1, new ItemStack(Items.FURNACE));
        companion.setItem(2, new ItemStack(Items.OAK_LOG, 4));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:beef", 3).success(), "Smelt order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COOKED_BEEF) == 3, companion.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.SMOKER) == 0 && blocks(h, Blocks.FURNACE) == 1, "The fuel went into a smoker");
        });
    }

    /**
     * «Пожарь мясо» of two kinds, and the logs are fuel for the second: they stay fuel, no smoker of them and no walk for wood
     * (review 10.11: the fuel was counted for the first kind only).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void logsTheSecondMeatNeedsAreNotMadeIntoASmoker(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.CRAFTING_TABLE);
        companion.setItem(0, new ItemStack(Items.BEEF, 3)); companion.setItem(1, new ItemStack(Items.PORKCHOP, 3));
        companion.setItem(2, new ItemStack(Items.OAK_PLANKS, 3)); companion.setItem(3, new ItemStack(Items.OAK_LOG, 4));
        companion.setItem(4, new ItemStack(Items.FURNACE));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "smelt_items", "minecraft:meat", 0).success(), "Smelt order not accepted");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COOKED_BEEF) == 3 && count(companion, Items.COOKED_PORKCHOP) == 3, companion.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.SMOKER) == 0, "The fuel of the second kind went into a smoker");
        });
    }

    /** At a stonecutter in reach, with a known bench a few steps off and stone for the bench's recipe too: cut here, two stone left. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void stonecutterInReachIsNotLeftForAKnownBench(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.STONECUTTER);
        BlockPos bench = new BlockPos(26, 1, 16); h.setBlock(bench, Blocks.CRAFTING_TABLE);
        companion.rememberPlaced(h.absolutePos(bench));
        companion.setItem(0, new ItemStack(Items.STONE, 6));
        h.runAfterDelay(5, () -> {
            CompanionActionResult result = CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:stone_stairs", 4);
            h.assertTrue(result.success() && !companion.isWorking() && count(companion, Items.STONE_STAIRS) == 4 && count(companion, Items.STONE) == 2,
                    "Not cut here: " + result.message() + " " + companion.getStoredItemsSummary());
            h.succeed();
        });
    }

    /** Four stone make four stairs only at a stonecutter (the bench wants six): he walks to the one eight blocks away. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void stonecutterAFewStepsAwayCutsStairsOneForOne(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(24, 1, 16), Blocks.STONECUTTER);
        companion.setItem(0, new ItemStack(Items.STONE, 4));
        CompanionActionResult started = CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:stone_stairs", 4);
        h.assertTrue(started.success(), "Craft order refused: " + started.message());
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.STONE_STAIRS) == 4 && count(companion, Items.STONE) == 0, companion.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.CRAFTING_TABLE) == 0, "A bench was set down");
            // Game 10.11: four cuts of one were read out as «1 …; 1 …; 1 …; 1 …».
            h.assertTrue(companion.getLastTaskResult().contains("Цепочка: 4 ") && !companion.getLastTaskResult().contains("; 1 "), companion.getLastTaskResult());
        });
    }

    /** Ordered while he swims in a pool (game 10.11, at the shore): he goes on to the stonecutter, no stand-still until the stall check. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void fromTheWaterHeGoesOnToTheStonecutter(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(12, 1, 12), new BlockPos(18, 2, 20))) h.setBlock(pos, Blocks.STONE);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(13, 1, 13), new BlockPos(17, 2, 19))) h.setBlock(pos, Blocks.WATER);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(19, 1, 12), new BlockPos(27, 2, 20))) h.setBlock(pos, pos.getY() == 1 ? Blocks.STONE : Blocks.AIR);
        h.setBlock(new BlockPos(24, 2, 16), Blocks.STONECUTTER);
        CompanionEntity companion = setup(h, new BlockPos(15, 2, 16));
        companion.setItem(0, new ItemStack(Items.STONE, 4));
        h.runAfterDelay(10, () -> {
            h.assertTrue(companion.isInWater() && !companion.onGround(), "Not swimming at the order");
            h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:stone_stairs", 4).success(), "Craft order refused");
            h.succeedWhen(() -> {
                done(h, companion);
                h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.STONE_STAIRS) == 4, companion.getLastTaskResult());
            });
        });
    }

    /** A stonecutter walled in (game 10.11, in a hillside): no way to it, and he says so, not «not enough materials». */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void walledInStonecutterIsNamedAsTheReason(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(23, 1, 15), new BlockPos(25, 2, 17))) h.setBlock(pos, Blocks.STONE);
        h.setBlock(new BlockPos(24, 1, 16), Blocks.STONECUTTER);
        companion.setItem(0, new ItemStack(Items.STONE, 4));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:stone_stairs", 4).success(), "Craft order refused");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().startsWith("Не смог подойти к камнерезу")
                    && count(companion, Items.STONE) == 4, companion.getLastTaskResult());
        });
    }

    /** Slabs come out the same at the bench beside him: no walk to the stonecutter for them. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void slabsAreNotWorthTheWalkToTheStonecutter(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        h.setBlock(new BlockPos(18, 1, 16), Blocks.CRAFTING_TABLE); h.setBlock(new BlockPos(24, 1, 16), Blocks.STONECUTTER);
        companion.setItem(0, new ItemStack(Items.STONE, 3));
        CompanionActionResult result = CompanionEvents.executeAction(owner(h, companion), "craft_item", "minecraft:stone_slab", 6);
        h.assertTrue(result.success() && !companion.isWorking() && count(companion, Items.STONE_SLAB) == 6, "Not crafted at once: " + result.message());
        h.succeed();
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        return setup(h, new BlockPos(16, 1, 16));
    }
    private static CompanionEntity setup(GameTestHelper h, BlockPos at) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), at.getX(), at.getY(), at.getZ());
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        return companion;
    }
    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }
    private static Container container(GameTestHelper h, BlockPos pos) { return (Container) h.getBlockEntity(pos); }
    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
    }
    private static int blocks(GameTestHelper h, Block block) {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(31, 4, 31))) if (h.getBlockState(pos).is(block)) result++;
        return result;
    }
    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}

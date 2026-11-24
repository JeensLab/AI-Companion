package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The drop comes to him by the same way the block did (owner, 24.11: the lapis block was mined up a column, its lapis fell onto
 * the pumpkin hanging over his base, and he stood there and said «С этим пока не справился»). A drop no walk reaches is raised
 * to with a column of his own soil or a floor across the gap — PATH7's way, for a drop as for a block — and then the column
 * comes down with the soil in his bag. Every row here is the player's own phrase through his own router; none calls the pickup.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPickupWayTests {
    private static final int SIDE = 48, TOP = 32, GROUND = 5, FEET = GROUND + 1;
    private static final int HOME_X = 20, HOME_Z = 16;

    /**
     * A wool block two above a pumpkin he hung four up: mined up his column, the wool falls onto the pumpkin, and the pickup
     * climbs its own column to it — the column comes down, the soil comes back to the bag.
     */
    @GameTest(template = "way_far", timeoutTicks = 2400)
    public static void aDropRestingOnABlockAboveIsClimbedFor(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        BlockPos pumpkin = new BlockPos(HOME_X + 2, FEET + 6, HOME_Z);
        h.setBlock(pumpkin, Blocks.PUMPKIN);
        h.assertBlockPresent(Blocks.AIR, pumpkin.below());
        h.setBlock(pumpkin.above(6), Blocks.WHITE_WOOL);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        var future = say(h, c, "добудь белую шерсть");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.WHITE_WOOL) >= 1, "The wool that fell on the pumpkin is not in the bag: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports(), "The column still stands: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) >= 32, "The soil did not come back to the bag: " + count(c, Items.DIRT));
            String line = way(c, "minecraft:white_wool");

        });
    }

    /**
     * The owner's own scene: a lapis block two above the pumpkin he hung — «добудь блок лазурита» mines it up a column, the
     * lapis falls onto the pumpkin, and the pickup climbs for it. The block is then made of the lapis it brought, so the bag
     * holds both the drop and the ordered block.
     */
    @GameTest(template = "way_far", timeoutTicks = 2400)
    public static void theLapisOnHisPumpkinIsPickedUp(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        BlockPos pumpkin = new BlockPos(HOME_X + 2, FEET + 6, HOME_Z);
        h.setBlock(pumpkin, Blocks.PUMPKIN);
        h.assertBlockPresent(Blocks.AIR, pumpkin.below());
        h.setBlock(pumpkin.above(6), Blocks.LAPIS_BLOCK);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        c.setItem(2, new ItemStack(Items.CRAFTING_TABLE));
        var future = say(h, c, "добудь блок лазурита");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.LAPIS_BLOCK) >= 1, "The lapis block was not made of it: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column still stands: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) >= 32, "The soil did not come back to the bag: " + count(c, Items.DIRT));

        });
    }

    /**
     * A wool block mined up a column over a two-wide chasm four deep: its drop falls onto a stone block standing in the chasm,
     * two across and one up from the bank — no walk (the pit is too deep to jump), no column (no foot beside it), so the pickup
     * lays a floor across the gap and takes it. The floor is a way, not a scaffold: it stays where he laid it.
     */
    @GameTest(template = "way_far", timeoutTicks = 2400)
    public static void aDropBeyondAGapIsBridged(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        for (int x = HOME_X + 1; x <= HOME_X + 2; x++) for (int y = GROUND - 4; y < GROUND; y++) h.setBlock(new BlockPos(x, y, HOME_Z), Blocks.AIR);
        h.setBlock(new BlockPos(HOME_X + 2, GROUND, HOME_Z), Blocks.STONE);
        h.setBlock(new BlockPos(HOME_X + 2, FEET + 5, HOME_Z), Blocks.WHITE_WOOL);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        var future = say(h, c, "добудь белую шерсть");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.WHITE_WOOL) >= 1, "The wool across the chasm is not in the bag: " + report);

            // The floor of his own is laid: the cells under the walk across the chasm are no air any more.
            h.assertTrue(!h.getBlockState(new BlockPos(HOME_X + 1, GROUND, HOME_Z)).isAir(), "The floor across the chasm was not laid");
        });
    }

    /**
     * A drop no column and no floor reaches — the bag has the three soil the mining's own column needs and no more — is given up
     * honestly: the pickup_way line names the drop, how far up and across it lies, and the soil it lacks, never a silent
     * «С этим пока не справился».
     */
    @GameTest(template = "way_far", timeoutTicks = 2400)
    public static void aDropWithNoWaySaysWhy(GameTestHelper h) {
        flat(h, Blocks.STONE);
        CompanionEntity c = spawn(h);
        BlockPos pumpkin = new BlockPos(HOME_X + 2, FEET + 3, HOME_Z);
        h.setBlock(pumpkin, Blocks.PUMPKIN);
        h.setBlock(pumpkin.above(), Blocks.WHITE_WOOL);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        // One soil: enough for the one-step column the mining itself needs, not for the four the pickup's own column asks.
        c.setItem(1, new ItemStack(Items.DIRT, 1));
        var future = say(h, c, "добудь белую шерсть");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String line = way(c, "minecraft:white_wool");
            h.assertTrue(line != null, "No pickup_way line for the wool: " + journal(c, "pickup_way"));
            h.assertTrue(line.contains("\"shape\":\"none\""), "The way that is no way is not said as none: " + line);
            h.assertTrue(line.contains("земли в сумке"), "The reason is not the soil he lacks: " + line);
            h.assertTrue(count(c, Items.WHITE_WOOL) == 0, "A wool appeared from nowhere: " + count(c, Items.WHITE_WOOL));
        });
    }

    // ---- helpers ----

    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» went to the model");
        return CompanionPlanRunner.run(CompanionLocalCommands.actions(command),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-pickup-way", () -> false));
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), HOME_X, FEET, HOME_Z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(HOME_X - 2, FEET, HOME_Z));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(32);
        c.setLifestyle(false, false);
        return c;
    }

    private static void flat(GameTestHelper h, Block floor) {
        for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) for (int y = 0; y < TOP; y++) {
            boolean border = x == 0 || x == SIDE - 1 || z == 0 || z == SIDE - 1;
            if (y == 0 || border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
            else if (y == GROUND) h.setBlock(new BlockPos(x, y, z), floor);
            else if (y < GROUND) h.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
            else h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    /** The last pickup_way line naming this item, or null. */
    private static String way(CompanionEntity c, String item) {
        List<String> lines = journal(c, "pickup_way").stream().filter(line -> line.contains("\"item\":\"" + item + "\"")).toList();
        return lines.isEmpty() ? null : lines.getLast();
    }

    private static List<String> journal(CompanionEntity c, String event) {
        dev.aicompanion.CompanionDiagnostics.flush();
        var directory = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion");
        List<String> lines = new ArrayList<>();
        for (String name : List.of("companion-3.jsonl", "companion-2.jsonl", "companion-1.jsonl", "companion-latest.jsonl")) {
            try { lines.addAll(java.nio.file.Files.readAllLines(directory.resolve(name))); } catch (java.io.IOException | java.io.UncheckedIOException ignored) { }
        }
        return lines.stream().filter(line -> line.contains(c.getUUID().toString()) && line.contains("\"event\":\"" + event + "\"")).toList();
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }

    /** The Russian names a client shows for the things these rows use; the test server runs en_us. */
    private static final java.util.Map<String, String> RU_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("white_wool", "Белая шерсть"),
            java.util.Map.entry("pumpkin", "Тыква"),
            java.util.Map.entry("lapis_block", "Лазуритовый блок"),
            java.util.Map.entry("lapis_lazuli", "Лазурит"));

    private static final Function<Item, String> RU = CompanionPickupWayTests::russianName;

    private static String russianName(Item item) {
        String name = RU_NAMES.get(BuiltInRegistries.ITEM.getKey(item).getPath());
        return name != null ? name : new ItemStack(item).getHoverName().getString();
    }
}

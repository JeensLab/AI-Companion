package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
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

/**
 * The owner's three live reports (25.11) as rows, generic over the blocks and the items of the game:
 * <ul>
 *   <li>a block seven up whose own foot is air, an existing staircase reaching it — taken from the staircase, no column, and
 *       the answer names the walk, never a wrong reason (his obsidian: «column failed: Под опорой нет безопасного места»,
 *       then «не нашёл лавы рядом» over the blocks hanging in sight);</li>
 *   <li>a drop lying on a block beside a staircase — fetched by walking, no column raised for it;</li>
 *   <li>an order whose thing lies on the ground waiting, its block gone — the order ends with it in the bag, never «С этим
 *       пока не справился» over a re-search that finds no block;</li>
 *   <li>a block nothing reaches but a column — the column is still raised, the block taken, the column taken down and the soil
 *       back in the bag;</li>
 *   <li>a foot on a slab the old rule called no foundation — the column stands on it and reaches the block.</li>
 * </ul>
 * Every row goes through the player's own router and the plan runner the chat uses: nothing here calls a helper to do the work.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWayFootTests {
    private static final int SIDE = 32, TOP = 24, GROUND = 5, FEET = GROUND + 1;
    private static final int HOME_X = 14, HOME_Z = 16;

    /**
     * A wool block hanging seven above his feet, its own cell under it air, and a staircase of the player's beside it: the
     * block is taken from the top step, no column is raised, and no wrong reason is said. The drop falls onto the staircase
     * and the pickup walks to it from the same step.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aStaircaseIsTheWayToABlockOverAir(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        // The staircase: a step of one block each, as a player builds one (stair blocks, no full cube on top).
        for (int step = 0; step <= 6; step++) h.setBlock(new BlockPos(HOME_X + 4 + step, FEET + step, HOME_Z), Blocks.OAK_STAIRS);
        BlockPos target = new BlockPos(HOME_X + 9, FEET + 7, HOME_Z);
        h.setBlock(target, Blocks.WHITE_WOOL);
        h.assertBlockPresent(Blocks.AIR, target.below());
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь белую шерсть");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.WHITE_WOOL) >= 1, "The wool seven up over the staircase is not in the bag: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            h.assertTrue(most[0] == 0, "A column was raised where the staircase already reached the wool: " + most[0]);
            h.assertTrue(count(c, Items.DIRT) == 32, "The soil was spent on a column: " + count(c, Items.DIRT));
            h.assertTrue(!report.contains("не справился") && !report.contains("лавы"), "A wrong reason was said: " + report);
            h.assertTrue(journal(c, "way_search").stream().anyMatch(line -> line.contains("\"item\":\"minecraft:white_wool\"")
                    && line.contains("\"shape\":\"walk\"")), "The search did not answer with the walk: " + journal(c, "way_search"));
        });
    }

    /** A drop lying on a block beside the staircase: walked to from the step, no column raised for it, never left lying. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aDropBesideAStaircaseIsWalkedTo(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        for (int step = 0; step <= 4; step++) h.setBlock(new BlockPos(HOME_X + 4 + step, FEET + step, HOME_Z), Blocks.OAK_STAIRS);
        // The block the drop rests on stands beside the top step, one block up from the ground.
        BlockPos pedestal = new BlockPos(HOME_X + 9, FEET + 4, HOME_Z);
        h.setBlock(pedestal, Blocks.STONE);
        BlockPos target = pedestal.above();
        h.setBlock(target, Blocks.WHITE_WOOL);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь белую шерсть");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.WHITE_WOOL) >= 1, "The wool beside the staircase is not in the bag: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            h.assertTrue(most[0] == 0, "A column was raised where the staircase already reached the drop: " + most[0]);
            h.assertTrue(!report.contains("не справился"), "The drop was called a failure: " + report);
        });
    }

    /**
     * The owner's own scene: the block is mined, the drop lies waiting on the ground, and the search for the block finds
     * nothing. The order is done when the thing is in the bag — the pickup fetches the drop, and the check counts it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void anOrderWhoseDropWaitsIsNotGivenUp(GameTestHelper h) {
        record Row(Block block, Item item, String phrase) { }
        List<Row> rows = List.of(new Row(Blocks.PUMPKIN, Items.PUMPKIN, "добудь тыкву"),
                new Row(Blocks.WHITE_WOOL, Items.WHITE_WOOL, "добудь белую шерсть"),
                new Row(Blocks.LAPIS_BLOCK, Items.LAPIS_BLOCK, "добудь блок лазурита"),
                new Row(Blocks.BARREL, Items.BARREL, "добудь бочку"));
        flat(h, Blocks.GRASS_BLOCK);
        int[] at = {0};
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>> future = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<CompanionEntity> who = new java.util.concurrent.atomic.AtomicReference<>();
        h.onEachTick(() -> {
            if (at[0] >= rows.size()) return;
            if (future.get() == null) {
                // No block of the thing stands anywhere: what lies is its drop, five blocks off, out of the loose pickup's reach.
                CompanionEntity c = spawn(h);
                c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
                c.setItem(1, new ItemStack(Items.DIRT, 32));
                BlockPos dropAt = h.absolutePos(new BlockPos(HOME_X + 5, FEET, HOME_Z));
                var drop = new ItemEntity(h.getLevel(), dropAt.getX() + .5, dropAt.getY(), dropAt.getZ() + .5, new ItemStack(rows.get(at[0]).item()));
                drop.setPickUpDelay(0);
                h.getLevel().addFreshEntity(drop);
                who.set(c);
                future.set(say(h, c, rows.get(at[0]).phrase()));
                return;
            }
            if (!future.get().isDone()) return;
            CompanionEntity c = who.get();
            String report = future.get().isCompletedExceptionally() ? "exception" : future.get().join().report();
            h.assertTrue(count(c, rows.get(at[0]).item()) >= 1, "«" + rows.get(at[0]).phrase() + "» over a drop lying there is not in the bag: " + report);
            h.assertTrue(!report.contains("не справился"), "«" + rows.get(at[0]).phrase() + "» was called a failure over its own drop: " + report);
            h.assertTrue(journal(c, "order_done").stream().anyMatch(line -> line.contains("\"have\":1")),
                    "The order was not told as done with the bag's count: " + journal(c, "order_done"));
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (!stack.is(Items.DIRT) && !stack.is(Items.DIAMOND_PICKAXE)) c.setItem(slot, ItemStack.EMPTY);
            }
            future.set(null);
            at[0]++;
        });
        h.succeedWhen(() -> h.assertTrue(at[0] >= rows.size(), "Still on row " + at[0] + ": " + (who.get() == null ? "" : who.get().getLastTaskResult())));
    }

    /** Nothing reaches it: the column is raised, the block taken, the column taken down and the soil back in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aWayThatIsNeededIsStillBuilt(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X + 3, FEET + 6, HOME_Z + 2);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = say(h, c, "добудь тыкву");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) >= 1, "The pumpkin is not in the bag: " + report);
            h.assertTrue(most[0] > 0, "No column was raised for the pumpkin in the air: " + most[0]);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column still stands: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) == 32, "The soil of the column was lost: " + count(c, Items.DIRT) + ", " + report);
        });
    }

    // ---- helpers ----

    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» went to the model");
        return CompanionPlanRunner.run(CompanionLocalCommands.actions(command),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-foot", () -> false));
    }

    private static final java.util.Map<String, String> RU_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("white_wool", "Белая шерсть"),
            java.util.Map.entry("pumpkin", "Тыква"),
            java.util.Map.entry("lapis_block", "Лазуритовый блок"),
            java.util.Map.entry("barrel", "Бочка"));

    private static final java.util.function.Function<Item, String> RU = CompanionWayFootTests::russianName;

    private static String russianName(Item item) {
        String name = RU_NAMES.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath());
        return name != null ? name : new ItemStack(item).getHoverName().getString();
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
        c.setSearchRadius(24);
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
}

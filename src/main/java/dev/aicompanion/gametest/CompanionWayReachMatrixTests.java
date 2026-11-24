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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The reach of the way, row by row (owner, 24.11: «тесты должны быть РАЗНЫЕ, а не пара удобных строк — он устал от зелёных
 * прогонов, которые ломаются с первого раза»). A generated covering matrix: the kinds of the world — a gourd, a storage block,
 * plain wool, planks, a plant — every distance across the box (3, 8, 16, 32, 48, 64), every height (2, 6, 12, 20, 32), a block
 * standing on the ground and one floating in the air, a block the harness set down and one the player placed. Every row ends
 * with the thing in the bag, his column taken down and the soil back in the bag; a failing row names the block, the distance
 * and the height, so a red run reads as the row that broke.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWayReachMatrixTests {
    private static final int GROUND = 5, FEET = GROUND + 1, HOME = 68;
    private static final int SIDE = 136, TOP = 48;

    /** One row: the block, the phrase that asks for it, how far across, how far up, on the ground or floating, and whose block it is. */
    private record Row(Block block, Item item, String phrase, String id, int across, int height, boolean onGround, boolean playerPlaced) {
        String name() { return id + " " + across + " вбок, " + height + " вверх, " + (onGround ? "на опоре" : "в воздухе") + (playerPlaced ? ", блок игрока" : ""); }
    }

    /** The gourd: a pumpkin, the order the owner gave. */
    @GameTest(template = "way_wide", timeoutTicks = 20000)
    public static void everyGourdOfTheMatrixIsGot(GameTestHelper h) {
        rows(h, "добудь тыкву", "minecraft:pumpkin", Items.PUMPKIN, Blocks.PUMPKIN, List.of(
                new int[]{3, 2, 0, 0}, new int[]{16, 12, 1, 1}, new int[]{48, 20, 1, 0}, new int[]{64, 32, 0, 1}));
    }

    /** A storage block, made of the lapis it gives: the chain gathers the drop and crafts the block. */
    @GameTest(template = "way_wide", timeoutTicks = 20000)
    public static void everyStorageBlockOfTheMatrixIsGot(GameTestHelper h) {
        rows(h, "добудь блок лазурита", "minecraft:lapis_block", Items.LAPIS_BLOCK, Blocks.LAPIS_BLOCK, List.of(
                new int[]{8, 6, 1, 1}, new int[]{32, 20, 0, 0}, new int[]{64, 12, 1, 1}));
    }

    /** Plain wool and planks: a block that is its own item, at the far corner and high up. */
    @GameTest(template = "way_wide", timeoutTicks = 20000)
    public static void everyPlainBlockOfTheMatrixIsGot(GameTestHelper h) {
        rows(h, "добудь белую шерсть", "minecraft:white_wool", Items.WHITE_WOOL, Blocks.WHITE_WOOL, List.of(
                new int[]{3, 32, 0, 0}, new int[]{16, 6, 1, 1}, new int[]{64, 2, 0, 1}));
        rows(h, "добудь дубовые доски", "minecraft:oak_planks", Items.OAK_PLANKS, Blocks.OAK_PLANKS, List.of(
                new int[]{8, 12, 1, 1}, new int[]{48, 32, 1, 0}, new int[]{32, 6, 0, 0}));
    }

    /** A plant: a poppy is picked, not mined — the same way to it, the same column and the same soil back. */
    @GameTest(template = "way_wide", timeoutTicks = 20000)
    public static void everyPlantOfTheMatrixIsGot(GameTestHelper h) {
        rows(h, "сорви мак", "minecraft:poppy", Items.POPPY, Blocks.POPPY, List.of(
                new int[]{3, 2, 0, 1}, new int[]{32, 6, 1, 0}, new int[]{16, 20, 0, 1}, new int[]{48, 12, 1, 1}));
    }

    // ---- the generated rows ----

    private static void rows(GameTestHelper h, String phrase, String id, Item item, Block block, List<int[]> grid) {
        flat(h);
        CompanionEntity c = spawn(h);
        int[] at = {0};
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>> future = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Row> last = new java.util.concurrent.atomic.AtomicReference<>();
        h.onEachTick(() -> {
            if (at[0] >= grid.size()) return;
            if (future.get() == null) {
                int[] row = grid.get(at[0]);
                Row scene = new Row(block, item, phrase, id, row[0], row[1], row[2] == 1, row[3] == 1);
                place(h, c, scene, last.get());
                last.set(scene);
                future.set(say(h, c, phrase));
                return;
            }
            if (!future.get().isDone()) return;
            int[] row = grid.get(at[0]);
            Row scene = new Row(block, item, phrase, id, row[0], row[1], row[2] == 1, row[3] == 1);
            String report = future.get().isCompletedExceptionally() ? "exception" : future.get().join().report();
            h.assertTrue(count(c, item) >= 1, "The row «" + scene.name() + "» left nothing in the bag: " + report);
            h.assertTrue(!report.contains("С этим пока не справился") && !report.contains("Рядом не вижу"),
                    "The row «" + scene.name() + "» was refused: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The row «" + scene.name() + "» left the column standing: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) == 64, "The row «" + scene.name() + "» did not give the soil back: " + count(c, Items.DIRT));
            List<String> lines = journal(c, "way_search");
            h.assertTrue(lines.stream().anyMatch(line -> line.contains("\"item\":\"" + id + "\"") && line.contains("\"chosen\":\"")),
                    "The row «" + scene.name() + "» has no way_search line naming the chosen block: " + lines);
            // The next row starts with an empty bag but the pickaxe, the soil and the bench.
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (!stack.is(Items.DIRT) && !stack.is(Items.DIAMOND_PICKAXE) && !stack.is(Items.CRAFTING_TABLE)) c.setItem(slot, ItemStack.EMPTY);
            }
            c.setItem(1, new ItemStack(Items.DIRT, 64));
            future.set(null);
            at[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(at[0] >= grid.size(), "Still on row " + at[0] + " of «" + phrase + "»: " + c.getLastTaskResult());
        });
    }

    /** The block of one row: floating in the air, or on a pedestal of stone up to its own height; the player's own build where the row says so. */
    private static void place(GameTestHelper h, CompanionEntity c, Row scene, Row previous) {
        // The row before left a block and a pedestal behind: its own column and this row's are cleared to the air.
        if (previous != null) for (int y = GROUND; y <= FEET + 40; y++)
            h.setBlock(new BlockPos(HOME + previous.across(), y, HOME), Blocks.AIR);
        for (int y = GROUND; y <= FEET + 40; y++) h.setBlock(new BlockPos(HOME + scene.across(), y, HOME), Blocks.AIR);
        BlockPos target = new BlockPos(HOME + scene.across(), FEET + scene.height(), HOME);
        if (scene.onGround()) {
            for (int y = GROUND; y < target.getY(); y++) h.setBlock(new BlockPos(target.getX(), y, target.getZ()), Blocks.STONE);
        }
        h.setBlock(target, scene.block());
        if (scene.playerPlaced()) placedByPlayer(h, c, target, scene.block());
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 64));
        c.setItem(2, new ItemStack(Items.CRAFTING_TABLE));
    }

    // ---- helpers ----

    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» went to the model");
        return CompanionPlanRunner.run(CompanionLocalCommands.actions(command),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-matrix-reach", () -> false));
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), HOME, FEET, HOME);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(HOME - 2, FEET, HOME));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        // The owner's own box: sixty-four across (24.11), never the old thirty-two.
        c.setSearchRadius(64);
        c.setLifestyle(false, false);
        return c;
    }

    /** The empty arena of the template, floored and walled; the air cells are left as they are. */
    private static void flat(GameTestHelper h) {
        for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) {
            boolean border = x == 0 || x == SIDE - 1 || z == 0 || z == SIDE - 1;
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            for (int y = 1; y <= GROUND; y++) {
                if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
                else h.setBlock(new BlockPos(x, y, z), y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            }
            if (border) for (int y = GROUND + 1; y < TOP; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
    }

    /** A real placement by the player: the event the game fires, the bookkeeping the mod keeps. */
    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos cell, Block block) {
        var player = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                net.neoforged.neoforge.common.util.BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(cell)),
                Blocks.GRASS_BLOCK.defaultBlockState(), player));
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
            java.util.Map.entry("pumpkin", "Тыква"),
            java.util.Map.entry("lapis_block", "Лазуритовый блок"),
            java.util.Map.entry("white_wool", "Белая шерсть"),
            java.util.Map.entry("oak_planks", "Дубовые доски"),
            java.util.Map.entry("poppy", "Мак"));

    private static final Function<Item, String> RU = CompanionWayReachMatrixTests::russianName;

    private static String russianName(Item item) {
        String name = RU_NAMES.get(BuiltInRegistries.ITEM.getKey(item).getPath());
        return name != null ? name : new ItemStack(item).getHoverName().getString();
    }
}

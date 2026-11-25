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
 * the pumpkin hanging over his base, and he stood there and said «С этим пока не справился»). A drop no walk reaches is raised to
 * with a column of his own soil or a floor across the gap — PATH7's way, for a drop as for a block — and then the column comes
 * down with the soil in his bag. Every row here is the player's own phrase through his own router, and every row proves its way:
 * once the block is broken and the mining's own column or floor is down, the pickup's way is switched off («Строить себе путь:
 * выкл.») — the drop stays where it lies and the pickup says the way was off — and only that way turned on brings it into the bag,
 * by the column or the floor the row names.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPickupWayTests {
    private static final int SIDE = 48, TOP = 32, GROUND = 5, FEET = GROUND + 1;
    private static final int HOME_X = 20, HOME_Z = 16;
    /** The trench's banks: the near one he stands on, the far one the drop lies on. */
    private static final int TRENCH_FROM = 21, TRENCH_TO = 22;

    /**
     * A wool block on a stone block four above the ground, nothing walkable beside the drop: the block is mined up a column of one
     * (the pillar's own, not the pickup's), the wool falls onto the stone, and four above the ground is out of a walk's touch. The
     * bag holds one soil where the pickup's column asks four: it digs the other three about him, raises the column, takes the wool
     * and brings the soil back.
     */
    @GameTest(template = "way_wide", timeoutTicks = 2400)
    public static void aDropRestingOnABlockAboveIsClimbedFor(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos pedestal = new BlockPos(HOME_X + 2, FEET + 3, HOME_Z);
        h.setBlock(pedestal, Blocks.STONE);
        h.setBlock(pedestal.above(), Blocks.WHITE_WOOL);
        h.assertBlockPresent(Blocks.AIR, pedestal.above(2));
        CompanionEntity c = spawn(h, HOME_X, HOME_Z);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 1));
        int[] most = {0};
        boolean[] control = {false};
        int[] stage = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayWithControl(h, c, "добудь белую шерсть", "minecraft:white_wool", Items.WHITE_WOOL, control, stage);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(control[0], "The wool came with the way off (stage " + stage[0] + "): " + journal(c, "pickup_way"));
            h.assertTrue(count(c, Items.WHITE_WOOL) >= 1, "The wool on the stone is not in the bag: " + future.join().report());
            h.assertTrue(most[0] > 0, "No column was raised to the wool: " + most[0]);
            h.assertTrue(columnLine(c, "minecraft:white_wool", 4), "No column of four was planned to the wool: " + journal(c, "pickup_way"));
            h.assertTrue(c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports(), "The column still stands: " + c.getTemporarySupportCount());
            // The soil the column lacked was dug about him: one carried, three dug, and the column's four came back.
            h.assertTrue(count(c, Items.DIRT) == 4, "The dug soil is not in the bag: " + count(c, Items.DIRT));
            h.assertTrue(journal(c, "pickup_way").stream().anyMatch(line -> line.contains("\"item\":\"minecraft:white_wool\"")
                    && line.contains("копаю 3")), "The dig for the column is not said: " + journal(c, "pickup_way"));
            h.assertBlockPresent(Blocks.AIR, pedestal.above());
        });
    }

    /**
     * The owner's own scene: a lapis block on the pumpkin he hung four above the ground. The block is mined up a column, its drop
     * comes to rest on the pumpkin, and nothing walkable reaches it — the pickup climbs its own column of four for it, and the
     * column comes down with the soil in his bag.
     */
    @GameTest(template = "way_wide", timeoutTicks = 2400)
    public static void theLapisOnHisPumpkinIsPickedUp(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos pumpkin = new BlockPos(HOME_X + 2, FEET + 3, HOME_Z);
        h.setBlock(pumpkin, Blocks.PUMPKIN);
        h.setBlock(pumpkin.above(), Blocks.LAPIS_BLOCK);
        h.assertBlockPresent(Blocks.AIR, pumpkin.above(2));
        CompanionEntity c = spawn(h, HOME_X, HOME_Z);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 32));
        int[] most = {0};
        boolean[] control = {false};
        int[] stage = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayWithControl(h, c, "добудь блок лазурита", "minecraft:lapis_block", Items.LAPIS_BLOCK, control, stage);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(control[0], "The lapis came with the way off (stage " + stage[0] + "): " + journal(c, "pickup_way"));
            h.assertTrue(count(c, Items.LAPIS_BLOCK) >= 1, "The lapis block on the pumpkin is not in the bag: " + future.join().report());
            h.assertTrue(most[0] > 0, "No column was raised to the lapis: " + most[0]);
            h.assertTrue(columnLine(c, "minecraft:lapis_block", 4), "No column of four was planned to the lapis: " + journal(c, "pickup_way"));
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column still stands: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) >= 32, "The soil did not come back to the bag: " + count(c, Items.DIRT));
        });
    }

    /**
     * A real trench: its floor is air from bank to bank, five deep, so no walk crosses it and the cells at the walking level have
     * nothing under them. A wool block five above the far bank's edge is mined up a column of his own (the pillar's, not the
     * pickup's), and its drop comes to rest on a stone block standing in the trench. The soil the mining's column gives back is
     * three, short of the five the pickup's column would ask: it lays a floor over the cells that were air and takes the wool. The
     * floor is a way, not a scaffold, and stays where he laid it.
     */
    @GameTest(template = "way_wide", timeoutTicks = 2400)
    public static void aDropBeyondAGapIsBridged(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        for (int x = TRENCH_FROM; x <= TRENCH_TO; x++) for (int z = 1; z <= SIDE - 2; z++)
            for (int y = 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        BlockPos stone = new BlockPos(TRENCH_TO, GROUND, HOME_Z);
        h.setBlock(stone, Blocks.STONE);
        BlockPos drop = new BlockPos(TRENCH_TO, FEET, HOME_Z);
        BlockPos wool = new BlockPos(TRENCH_TO, FEET + 4, HOME_Z);
        h.setBlock(wool, Blocks.WHITE_WOOL);
        h.assertBlockPresent(Blocks.AIR, new BlockPos(TRENCH_FROM, GROUND, HOME_Z));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(TRENCH_FROM, FEET, HOME_Z));
        h.assertBlockPresent(Blocks.AIR, drop);
        CompanionEntity c = spawn(h, HOME_X, HOME_Z);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.OAK_PLANKS, 6));
        c.setItem(2, new ItemStack(Items.DIRT, 3));
        int[] bridge = {0};
        boolean[] control = {false};
        int[] stage = {0};
        h.onEachTick(() -> bridge[0] = Math.max(bridge[0], c.getTemporaryBridgeCount()));
        var future = sayWithControl(h, c, "добудь белую шерсть", "minecraft:white_wool", Items.WHITE_WOOL, control, stage);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(control[0], "The wool came with the way off (stage " + stage[0] + "): " + journal(c, "pickup_way"));
            h.assertTrue(count(c, Items.WHITE_WOOL) >= 1, "The wool across the trench is not in the bag: " + future.join().report());
            h.assertTrue(bridge[0] > 0, "No floor was laid: " + bridge[0]);
            h.assertBlockPresent(Blocks.AIR, drop);
            // The cell under the walk across the trench was air before the way and holds the floor of his own after it.
            h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(TRENCH_FROM, GROUND, HOME_Z));
            h.assertTrue(count(c, Items.OAK_PLANKS) == 5, "The planks of the floor were lost: " + count(c, Items.OAK_PLANKS));
        });
    }

    /**
     * A drop no column and no floor reaches — the ground is stone, the bag holds the one soil the mining's own column used, and no
     * plank is there to lay — is given up honestly: the pickup_way line names the drop, how far up it lies and the soil he lacks,
     * never a silent «С этим пока не справился». With the way off the same drop says the way was switched off, so the line is the
     * way's own answer.
     */
    @GameTest(template = "way_wide", timeoutTicks = 2400)
    public static void aDropWithNoWaySaysWhy(GameTestHelper h) {
        flat(h, Blocks.STONE);
        BlockPos pedestal = new BlockPos(HOME_X + 2, FEET + 3, HOME_Z);
        h.setBlock(pedestal, Blocks.STONE);
        h.setBlock(pedestal.above(), Blocks.WHITE_WOOL);
        CompanionEntity c = spawn(h, HOME_X, HOME_Z);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 1));
        boolean[] control = {false};
        int[] stage = {0};
        var future = sayWithControl(h, c, "добудь белую шерсть", "minecraft:white_wool", Items.WHITE_WOOL, control, stage);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(control[0], "The way-off line is not the one the control ran on (stage " + stage[0] + "): " + journal(c, "pickup_way"));
            String line = way(c, "minecraft:white_wool");
            h.assertTrue(line != null, "No pickup_way line for the wool: " + journal(c, "pickup_way"));
            h.assertTrue(line.contains("\"shape\":\"none\""), "The way that is no way is not said as none: " + line);
            h.assertTrue(line.contains("земли в сумке"), "The reason is not the soil he lacks: " + line);
            h.assertTrue(count(c, Items.WHITE_WOOL) == 0, "A wool appeared from nowhere: " + count(c, Items.WHITE_WOOL));
        });
    }

    // ---- helpers ----

    /**
     * The phrase through the player's own router, with the pickup's way switched off once the block is mined and the mining's own
     * column or floor is down — the moment the drop is left to the pickup's way and nothing else — and switched on again once the
     * pickup has said the way was off. The record of the control is whether the drop was still out of the bag then: that is what
     * proves the way fetches the drop, for with it off nothing else does.
     */
    private static CompletableFuture<CompanionPlanRunner.Outcome> sayWithControl(GameTestHelper h, CompanionEntity c, String phrase,
                                                                                String id, Item item, boolean[] control, int[] stage) {
        var future = say(h, c, phrase);
        h.onEachTick(() -> {
            if (stage[0] == 0) {
                // The block is broken and its drop has come to rest: what lies out of a walk now is the pickup's way's. The mining's
                // own column or floor is no way to the drop (it is taken down, and the descent never touches it), so the switch is
                // turned off the moment the drop lies there.
                if (!dropAtRest(h, c, item)) return;
                c.setBuildWay(false);
                stage[0] = 1;
                return;
            }
            if (stage[0] == 1) {
                String line = way(c, id);
                if (line == null || !line.contains("\"why\":\"switch off\"")) return;
                control[0] = count(c, item) == 0;
                c.setBuildWay(true);
                stage[0] = 2;
            }
        });
        return future;
    }

    /** Whether the pickup planned a column of this many steps to the item: the way it said it would raise. */
    private static boolean columnLine(CompanionEntity c, String item, int steps) {
        return journal(c, "pickup_way").stream().anyMatch(line -> line.contains("\"item\":\"" + item + "\"")
                && line.contains("\"shape\":\"column\"") && line.contains("\"steps\":" + steps));
    }

    /** Whether a drop of this item has come to rest about him: what the pickup is left with once the block is broken. */
    private static boolean dropAtRest(GameTestHelper h, CompanionEntity c, Item item) {
        var box = new net.minecraft.world.phys.AABB(c.blockPosition()).inflate(8, 6, 8);
        return h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
                found -> found.getItem().is(item) && found.onGround()).stream().findFirst().isPresent();
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» went to the model");
        return CompanionPlanRunner.run(CompanionLocalCommands.actions(command),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-pickup-way", () -> false));
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, FEET, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x - 2, FEET, z));
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

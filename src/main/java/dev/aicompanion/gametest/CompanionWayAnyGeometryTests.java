package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The way for any block and any geometry (idea PATH7, #105): «для любой цели в мире». A block up and to the side, a floor across
 * a gap, soil dug under himself when there is nothing to build with, a wall of somebody else's build walked round — and a
 * generated sample of the game's own block registry, each block asked for by its item and taken from a column that comes down
 * with its soil back. Every row goes through the plan the chat hands the runner for «добудь …»; none calls an internal helper
 * to do the work. Honest exceptions of the sample are named where they are filtered: fluids, blocks with no item form, a block
 * that falls out of the air, a drop that is not the item itself (Silk Touch, shears, a different drop).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWayAnyGeometryTests {
    private static final int GROUND = 5;
    private static final int FEET = GROUND + 1;
    private static final int SIDE = 32, TOP = 24;
    private static final int HOME_X = 14, HOME_Z = 16;
    /** The blocks of the registry each third of the sample asks for: enough to be a sample, few enough for one test. */
    private static final int SAMPLE = 24;

    // ---- a block up and to the side: every offset of the budget, every height of the way ----

    /** One to the side, four up: no stand cell reaches it, a column is raised beside it and comes down. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void pumpkinUpAndOneToTheSideIsTakenFromAColumn(GameTestHelper h) {
        sideAbove(h, 1, 0, 4, true);
    }

    /** Two to the side, five up: the way is the same geometry, not a column over his own head. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void pumpkinUpAndTwoToTheSideIsTakenFromAColumn(GameTestHelper h) {
        sideAbove(h, 2, 0, 5, true);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void pumpkinUpAndThreeToTheSideIsTakenFromAColumn(GameTestHelper h) {
        sideAbove(h, 3, 0, 5, true);
    }

    /** Four to the side, six up: the whole sideways budget of the way. */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void pumpkinUpAndFourToTheSideIsTakenFromAColumn(GameTestHelper h) {
        sideAbove(h, 4, 0, 6, true);
    }

    /** Diagonally up and to the side: two across each way, five up. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void pumpkinUpAndDiagonallyIsTakenFromAColumn(GameTestHelper h) {
        sideAbove(h, 2, 2, 5, true);
    }

    /** Two up, four to the side: within a swing from the ground, so no column is raised for it. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void pumpkinLowAndToTheSideIsWalkedTo(GameTestHelper h) {
        sideAbove(h, 4, 0, 2, false);
    }

    // ---- a bridge across a gap: laid, crossed, and left standing ----

    /**
     * A four-block gap with six planks: a floor is laid across it, he crosses to the pumpkin on the far bank, and the floor is
     * left standing — a bridge is a way, not a scaffold (the owner's rule for PATH7). No village and no remembered place here.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void aFourBlockGapIsBridgedAndTheBridgeIsLeftStanding(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        // The gap runs the whole width of the test: there is no walking round it, only a floor across it.
        for (int x = 18; x <= 21; x++) for (int z = 1; z <= SIDE - 2; z++) for (int y = 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        BlockPos target = new BlockPos(22, FEET, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.OAK_PLANKS, 6));
        int[] bridge = {0};
        h.onEachTick(() -> bridge[0] = Math.max(bridge[0], c.getTemporaryBridgeCount()));
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin across the gap is not in the bag: " + report);
            h.assertTrue(bridge[0] > 0, "No floor was laid: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            // The floor of his own stands: three cells of planks over the gap, none taken back.
            int laid = 0;
            for (int x = 18; x <= 21; x++) if (h.getBlockState(new BlockPos(x, GROUND, 16)).is(Blocks.OAK_PLANKS)) laid++;
            h.assertTrue(laid >= 3, "The bridge was taken back: " + laid + " planks left, " + report);
            // The journal says the decision the player can read: the block, its geometry and the shape of the way.
            h.assertTrue(journal(c, "way_decision").stream().anyMatch(line -> line.contains("\"shape\":\"bridge\"")
                    && line.contains("\"id\":\"minecraft:pumpkin\"") && line.contains("\"gap\":4")),
                    "No bridge decision in the journal: " + journal(c, "way_decision"));
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
        });
    }

    // ---- nothing to build with: soil dug under himself ----

    /**
     * No soil in the bag and none in a chest: the plan digs the ground under him for the column, the pumpkin is taken from it,
     * and the soil comes back to the bag with no support left in the air.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void withNothingToBuildWithHeDigsSoilUnderHimself(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X + 2, FEET + 5, HOME_Z);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin is not in the bag: " + report);
            h.assertTrue(most[0] > 0, "No column was raised: " + report);
            h.assertTrue(count(c, Items.DIRT) + looseDirt(h) > 0, "The soil dug for the column is not his: " + count(c, Items.DIRT) + ", " + report);
            h.assertTrue(dirtAboveGround(h) == 0, "A support was left in the air");
            h.assertBlockPresent(Blocks.AIR, target);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
        });
    }

    // ---- somebody else's build in the way ----

    /**
     * A wall of stone bricks between him and a pumpkin on the ground: the way is walked round it, the wall is never broken.
     * Stone bricks are no natural ground, so even the column would not take them — but the walk finds the way round.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aWallOfSomebodysBuildIsWalkedRoundNotBroken(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        for (int z = 13; z <= 18; z++) for (int y = FEET; y <= FEET + 2; y++) h.setBlock(new BlockPos(18, y, z), Blocks.STONE_BRICKS);
        BlockPos target = new BlockPos(22, FEET, 16);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        int[] before = snapshot(h);
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin behind the wall is not in the bag: " + report);
            for (int z = 13; z <= 18; z++) for (int y = FEET; y <= FEET + 2; y++)
                h.assertBlockPresent(Blocks.STONE_BRICKS, new BlockPos(18, y, z));
            assertRestored(h, before, target);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
        });
    }

    // ---- the generated sample of the game's own blocks ----

    /**
     * A broad, mechanically derived list of the game's blocks — every block with an item form, a full cube, no fall, whose own
     * drop with a diamond pickaxe is the item itself — asked for by its item, each placed up and to the side over a bedrock
     * floor. One looped row names the failing block. Honest exceptions, filtered by that rule: fluids (no item form), a block
     * that falls out of the air (sand, gravel), a drop that is not the item (Silk Touch: glass, ice; shears: leaves; a different
     * block: stone gives cobblestone), and blocks with a body that is no full cube (a torch, a slab).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 20000)
    public static void theFirstThirdOfTheBlockRegistryIsTakenFromAColumn(GameTestHelper h) {
        everyBlockOfTheGame(h, 0, 3);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 20000)
    public static void theSecondThirdOfTheBlockRegistryIsTakenFromAColumn(GameTestHelper h) {
        everyBlockOfTheGame(h, 1, 3);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 20000)
    public static void theLastThirdOfTheBlockRegistryIsTakenFromAColumn(GameTestHelper h) {
        everyBlockOfTheGame(h, 2, 3);
    }

    // ---- helpers ----

    /** A pumpkin {@code dx}, {@code dz} to the side and {@code height} above his feet; taken by a column or on foot. */
    private static void sideAbove(GameTestHelper h, int dx, int dz, int height, boolean column) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X + dx, FEET + height, HOME_Z + dz);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 1, "The pumpkin " + dx + "," + dz + " to the side and " + height + " up is not in the bag: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            assertRestored(h, before, target);
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of the column was lost: " + count(c, Items.DIRT) + ", " + report);
            h.assertTrue(column ? most[0] > 0 : most[0] == 0, "The column for " + height + " up is wrong: " + most[0] + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column is still standing: " + c.getTemporarySupportCount());
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
        });
    }

    /** The blocks of the game a way up can be asked for, sampled by {@code part} of {@code parts}, at most {@code most} of them. */
    private static List<Block> sampled(GameTestHelper h, int part, int parts, int most) {
        List<Block> all = anyBlocks(h);
        List<Block> sample = new ArrayList<>();
        for (int index = part; index < all.size() && sample.size() < most; index += parts) sample.add(all.get(index));
        return sample;
    }

    /**
     * The blocks with an item form whose own drop with a diamond pickaxe is that item: a full cube, no fluid, no fall. The rule
     * is the game's own loot table, not a list of names.
     */
    private static List<Block> anyBlocks(GameTestHelper h) {
        List<Block> all = new ArrayList<>();
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        Entity breaker = new Marker(EntityType.MARKER, h.getLevel());
        BlockPos at = h.absolutePos(new BlockPos(HOME_X, FEET + 5, HOME_Z));
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR || block.asItem() == Items.AIR) continue;                     // no item form: no order to give
            // The soil he carries answers a dirt order from the bag before any block of it is mined: no column would be asked.
            if (block.asItem() == Items.DIRT) continue;
            if (block instanceof FallingBlock) continue;                                          // falls out of the air: not a block to hang
            BlockState state = block.defaultBlockState();
            if (!state.getFluidState().isEmpty()) continue;                                       // a fluid: its own item, no drop
            if (!state.isCollisionShapeFullBlock(h.getLevel(), at)) continue;                      // no body of a cube to stand beside
            List<ItemStack> drops;
            try {
                drops = Block.getDrops(state, h.getLevel(), at, null, breaker, pickaxe);
            } catch (RuntimeException broken) {
                continue;                                                                          // a block whose loot table needs a block entity
            }
            if (drops.size() != 1 || drops.getFirst().getItem() != block.asItem()) continue;       // the item itself, surely
            all.add(block);
        }
        return all;
    }

    /** Every sampled block placed up and to the side, asked for by its item, taken from a column that comes down with its soil. */
    private static void everyBlockOfTheGame(GameTestHelper h, int part, int parts) {
        flat(h, Blocks.BEDROCK);
        List<Block> sample = sampled(h, part, parts, SAMPLE);
        System.out.println("WAY_SAMPLE part=" + part + "/" + parts + " blocks=" + anyBlocks(h).size() + " asked=" + sample.size());
        h.assertTrue(!sample.isEmpty(), "No block of the registry is a way's");
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] index = {0};
        int[] most = {0};
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>> future = new java.util.concurrent.atomic.AtomicReference<>();
        BlockPos[] target = new BlockPos[1];
        h.onEachTick(() -> {
            most[0] = Math.max(most[0], c.getTemporarySupportCount());
            if (future.get() == null) {
                if (index[0] >= sample.size()) return;
                Block block = sample.get(index[0]);
                target[0] = new BlockPos(HOME_X + 2, FEET + 5, HOME_Z + 2);
                h.setBlock(target[0], block);
                most[0] = 0;
                String id = BuiltInRegistries.ITEM.getKey(block.asItem()).toString();
                String name = new ItemStack(block.asItem()).getHoverName().getString();
                future.set(sayModel(h, c, "добудь " + name, obtainPlan(id)));
                return;
            }
            if (!future.get().isDone()) return;
            Block block = sample.get(index[0]);
            String name = BuiltInRegistries.BLOCK.getKey(block) + " (" + BuiltInRegistries.ITEM.getKey(block.asItem()) + ")";
            String report = future.get().isCompletedExceptionally() ? "exception" : future.get().join().report();
            h.assertTrue(count(c, block.asItem()) >= 1, "«добудь " + name + "» is not in the bag: " + report);
            h.assertBlockPresent(Blocks.AIR, target[0]);
            h.assertTrue(most[0] > 0, "No column was raised for " + name + ": " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column for " + name + " is still standing: " + c.getTemporarySupportCount());
            // The soil of the way is his again: in the bag or on the ground to be picked up, never left standing in the air.
            h.assertTrue(dirtAboveGround(h) == 0, "A support for " + name + " was left in the air: " + dirtAboveGround(h) + ", " + report);
            h.assertTrue(count(c, Items.DIRT) + looseDirt(h) >= 20, "The soil for " + name + " was lost: " + count(c, Items.DIRT)
                    + " in the bag and " + looseDirt(h) + " on the ground, " + report);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end for " + name + ": " + report);
            // The next order must not be answered by this block's drop: the bag keeps only the soil and the pickaxe.
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (!stack.is(Items.DIRT) && !stack.is(Items.DIAMOND_PICKAXE)) c.setItem(slot, ItemStack.EMPTY);
            }
            index[0]++;
            future.set(null);
        });
        h.succeedWhen(() -> { h.assertTrue(index[0] >= sample.size(), "Still on block " + index[0] + " of " + sample.size()); });
    }

    private static String obtainPlan(String item) {
        return "[{\"action\":\"obtain_item\",\"item\":\"" + item + "\",\"count\":1}]";
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> sayModel(GameTestHelper h, CompanionEntity c, String phrase, String json) {
        var reply = CompanionPlanRequirements.apply(phrase, new CompanionReply("", CompanionPlanCodec.parsePlan(JsonParser.parseString(json))));
        return CompanionPlanRunner.run(reply.actions(),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-any-model", () -> false));
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

    private static int[] snapshot(GameTestHelper h) {
        int[] cells = new int[SIDE * TOP * SIDE];
        int at = 0;
        for (int y = 0; y < TOP; y++) for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++)
            cells[at++] = Block.getId(h.getBlockState(new BlockPos(x, y, z)));
        return cells;
    }

    /** Every cell of the test as it was, but the cells that were mined: those stand empty. */
    private static void assertRestored(GameTestHelper h, int[] before, BlockPos... mined) {
        Set<BlockPos> allowed = new HashSet<>(List.of(mined));
        int at = 0;
        for (int y = 0; y < TOP; y++) for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            int now = Block.getId(h.getBlockState(pos));
            int was = before[at++];
            if (now == was) continue;
            h.assertTrue(allowed.contains(pos) && h.getBlockState(pos).isAir(),
                    "A cell changed at " + pos + ": " + BuiltInRegistries.BLOCK.getKey(h.getBlockState(pos).getBlock()) + ", allowed " + allowed);
        }
    }

    /** Dirt lying as a drop in the test: the soil of his own column comes back to the bag through the ground. */
    private static int looseDirt(GameTestHelper h) {
        var box = new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(1, 1, 1))).minmax(
                new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(SIDE - 2, TOP - 1, SIDE - 2))));
        return h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box, item -> item.getItem().is(Items.DIRT))
                .stream().mapToInt(item -> item.getItem().getCount()).sum();
    }

    private static int dirtAboveGround(GameTestHelper h) {
        int count = 0;
        for (int x = 1; x < SIDE - 1; x++) for (int z = 1; z < SIDE - 1; z++) for (int y = GROUND + 1; y < TOP - 1; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) count++;
        return count;
    }

    /** The journalled lines of this companion with this event name. */
    private static List<String> journal(CompanionEntity c, String event) {
        dev.aicompanion.CompanionDiagnostics.flush();
        var directory = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve("aicompanion");
        List<String> lines = new java.util.ArrayList<>();
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

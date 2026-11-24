package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.item.ItemEntity;
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
 * The owner's live scene (PATH7, #105): blocks hanging in the air near him — an obsidian block, a lapis block, a pumpkin —
 * which he does not «see» at all: the plan refuses before any way is asked. The defect is upstream of the way: the search
 * for what lies about him never found them, so no column was ever planned and no way_decision was journalled.
 *
 * These rows are the search's own: a block anywhere in the loaded box, floating in the air exactly like one on the ground,
 * is found and taken; a broad mechanical sample of the game's own blocks proves the rule is no list of names; and a block
 * outside the box is not claimed. Nothing here calls an internal helper to do the work: every row goes through the plan the
 * chat hands the runner for «добудь …».
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWaySearchTests {
    private static final int GROUND = 5;
    private static final int FEET = GROUND + 1;
    private static final int SIDE = 32, TOP = 24;
    private static final int HOME_X = 14, HOME_Z = 16;
    /** The blocks of the registry each third of the sample asks for. */
    private static final int SAMPLE = 24;

    // ---- the owner's scene: three blocks hanging in the air ----

    /** His obsidian: three to the side and six up, nothing under it — taken by a column of his own soil. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aFloatingObsidianBlockIsFoundAndTaken(GameTestHelper h) {
        floatingTaken(h, Blocks.OBSIDIAN, Items.OBSIDIAN, 5, 1, 6, "добудь обсидиан", "minecraft:obsidian");
    }

    /** His lapis block: two to the side and five up, nothing under it — mined instead of looking for ore. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aFloatingLapisBlockIsFoundAndTaken(GameTestHelper h) {
        floatingTaken(h, Blocks.LAPIS_BLOCK, Items.LAPIS_BLOCK, 6, 2, 5, "добудь блок лазурита", "minecraft:lapis_block");
    }

    /** His pumpkin: four to the side and seven up, nothing under it — taken, never «не справился». */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aFloatingPumpkinIsFoundAndTaken(GameTestHelper h) {
        floatingTaken(h, Blocks.PUMPKIN, Items.PUMPKIN, 7, 0, 8, "добудь тыкву", "minecraft:pumpkin");
    }

    /** «Лазурит» with the lapis block hanging there: the block is mined and made into lapis, not an ore looked for. */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void lapisLazuliComesFromTheFloatingBlock(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X + 5, FEET + 6, HOME_Z + 1);
        h.setBlock(target, Blocks.LAPIS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        var future = sayModel(h, c, "добудь лазурит", obtainPlan("minecraft:lapis_lazuli"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.LAPIS_LAZULI) >= 1, "No lapis lazuli from the block in the air: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            assertRestored(h, before, target);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
        });
    }

    /** All three of his blocks in one world, asked one after another: every one is found and taken. */
    @GameTest(template = "tall_tree", timeoutTicks = 16000)
    public static void theOwnersThreeFloatingBlocksAreAllTaken(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos obsidian = new BlockPos(HOME_X + 5, FEET + 6, HOME_Z + 1);
        BlockPos lapis = new BlockPos(HOME_X + 6, FEET + 5, HOME_Z + 2);
        BlockPos pumpkin = new BlockPos(HOME_X + 7, FEET + 8, HOME_Z);
        h.setBlock(obsidian, Blocks.OBSIDIAN);
        h.setBlock(lapis, Blocks.LAPIS_BLOCK);
        h.setBlock(pumpkin, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        List<String> ids = List.of("minecraft:obsidian", "minecraft:lapis_block", "minecraft:pumpkin");
        List<Item> items = List.of(Items.OBSIDIAN, Items.LAPIS_BLOCK, Items.PUMPKIN);
        List<String> names = List.of("добудь обсидиан", "добудь блок лазурита", "добудь тыкву");
        List<CompletableFuture<CompanionPlanRunner.Outcome>> futures = new ArrayList<>(java.util.Collections.nCopies(ids.size(), (CompletableFuture<CompanionPlanRunner.Outcome>) null));
        int[] at = {0};
        int[] before = snapshot(h);
        h.onEachTick(() -> {
            if (at[0] >= ids.size()) return;
            if (futures.get(at[0]) == null) { futures.set(at[0], sayModel(h, c, names.get(at[0]), obtainPlan(ids.get(at[0])))); return; }
            if (!futures.get(at[0]).isDone()) return;
            String report = futures.get(at[0]).join().report();
            h.assertTrue(count(c, items.get(at[0])) >= 1, "«" + names.get(at[0]) + "» not in the bag: " + report);
            at[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(at[0] >= ids.size(), "Only " + at[0] + " of three orders ran");
            h.assertBlockPresent(Blocks.AIR, obsidian);
            h.assertBlockPresent(Blocks.AIR, lapis);
            h.assertBlockPresent(Blocks.AIR, pumpkin);
            assertRestored(h, before, obsidian, lapis, pumpkin);
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of the columns was lost: " + count(c, Items.DIRT));
        });
    }

    // ---- the same rule for any block of the game, generated from its own registry ----

    @GameTest(template = "tall_tree", timeoutTicks = 20000)
    public static void theFirstThirdOfTheRegistryIsTakenFloating(GameTestHelper h) {
        everyBlockFloating(h, 0, 3);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 20000)
    public static void theSecondThirdOfTheRegistryIsTakenFloating(GameTestHelper h) {
        everyBlockFloating(h, 1, 3);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 20000)
    public static void theLastThirdOfTheRegistryIsTakenFloating(GameTestHelper h) {
        everyBlockFloating(h, 2, 3);
    }

    // ---- the box is bounded: a block outside it is not claimed ----

    /**
     * The same block on the ground at the far corner, forty blocks off: outside the search box, so the order is refused
     * honestly and the block stays where it lies — the search is no claim on the whole world.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void aBlockOutsideTheBoxIsNotClaimed(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos far = new BlockPos(SIDE - 1 - 1, FEET, SIDE - 1 - 1);
        h.setBlock(far, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h, 2, 2);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 0, "A pumpkin forty blocks off was claimed: " + report);
            h.assertBlockPresent(Blocks.PUMPKIN, far);
            h.assertTrue(!report.contains("Полторы минуты"), "The order stalled over a far block: " + report);
            assertRestored(h, before);
        });
    }

    /** The same three blocks on the ground right beside him are not claimed either when he asks for something else. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aNeighbouringBlockOfAnotherItemIsNotClaimed(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos beside = new BlockPos(HOME_X + 2, FEET, HOME_Z);
        h.setBlock(beside, Blocks.LAPIS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.PUMPKIN) == 0, "A lapis block answered a pumpkin order: " + report);
            h.assertTrue(count(c, Items.LAPIS_BLOCK) == 0, "The lapis block was taken for a pumpkin: " + report);
            h.assertBlockPresent(Blocks.LAPIS_BLOCK, beside);
        });
    }

    // ---- the search says its own line: the box, the choice, or that there was nothing ----

    /**
     * The journal line of the search itself: the box it looked through, how many blocks it found, which one it chose and the way
     * to it — so the player's log says what he «saw» and why he went, not only where the way went.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void theSearchJournalsTheBoxAndTheBlockItChose(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X + 5, FEET + 6, HOME_Z + 1);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        var future = sayModel(h, c, "добудь обсидиан", obtainPlan("minecraft:obsidian"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            List<String> lines = journal(c, "way_search");
            String line = lines.stream().filter(entry -> entry.contains("\"item\":\"minecraft:obsidian\"")
                    && entry.contains("\"chosen\":\"")).findFirst().orElse(null);
            h.assertTrue(line != null, "No way_search line naming the chosen obsidian: " + lines);
            h.assertTrue(line.contains("\"radius\":" + Math.min(32, c.getSearchRadius())) && line.contains("\"depth\":32"),
                    "The box is not named: " + line);
            h.assertTrue(line.contains("\"candidates\":") && line.contains("\"id\":\"minecraft:obsidian\"")
                    && line.contains("\"shape\":\"column\""), "The choice is not named: " + line);
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "The obsidian was not taken: " + future.join().report());
        });
    }

    /** Nothing in the box: the line says it looked and found nothing, with the radius it looked through. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void theSearchJournalsThatItFoundNothingInItsBox(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos far = new BlockPos(SIDE - 2, FEET, SIDE - 2);
        h.setBlock(far, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h, 2, 2);
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            List<String> lines = journal(c, "way_search");
            String line = lines.stream().filter(entry -> entry.contains("\"item\":\"minecraft:pumpkin\"")
                    && entry.contains("искал в радиусе")).findFirst().orElse(null);
            h.assertTrue(line != null, "No «искал в радиусе … ничего» line: " + lines);
            h.assertTrue(line.contains("\"candidates\":0") && line.contains("\"count\":0"), "A block was claimed: " + line);
            h.assertBlockPresent(Blocks.PUMPKIN, far);
        });
    }

    // ---- helpers ----

    /** A block floating {@code dx}, {@code dz} to the side and {@code height} up, nothing under it: found and taken. */
    private static void floatingTaken(GameTestHelper h, Block block, Item item, int dx, int dz, int height, String phrase, String id) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X + dx, FEET + height, HOME_Z + dz);
        h.setBlock(target, block);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 20));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var seen = c.obtainAvailable(id);
        BlockPos absolute = h.absolutePos(target);
        var how = dev.aicompanion.entity.CompanionReach.how(c, h.getLevel(), absolute, 3.5D, true, true, 16);
        h.assertTrue(seen.some(), "The reach answer does not see «" + id + "» floating " + height + " up and " + dx + " across: "
                + seen + ", how " + how + ", feet " + c.blockPosition() + ", block " + absolute + ", radius " + c.getSearchRadius());
        var future = sayModel(h, c, phrase, obtainPlan(id));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, item) >= 1, "«" + phrase + "» floating " + height + " up is not in the bag: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            assertRestored(h, before, target);
            h.assertTrue(count(c, Items.DIRT) == 20, "The soil of the column was lost: " + count(c, Items.DIRT) + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column is still standing: " + c.getTemporarySupportCount());
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
        });
    }

    /** Every sampled block placed floating up and to the side, asked for by its item, taken from a column that comes down. */
    private static void everyBlockFloating(GameTestHelper h, int part, int parts) {
        flat(h, Blocks.BEDROCK);
        List<Block> sample = sampled(h, part, parts, SAMPLE);
        System.out.println("WAY_SEARCH_SAMPLE part=" + part + "/" + parts + " blocks=" + anyBlocks(h).size() + " asked=" + sample.size());
        h.assertTrue(!sample.isEmpty(), "No block of the registry is the search's");
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
                // Floating up and to the side, nothing under it: the same geometry for every block of the sample, beyond the
                // four across the way used to stand within (PATH7: the blocks the owner saw hanging beside him).
                target[0] = new BlockPos(HOME_X + 6, FEET + 6, HOME_Z + 2);
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
            h.assertTrue(count(c, block.asItem()) >= 1, "«добудь " + name + "» floating in the air is not in the bag: " + report);
            h.assertBlockPresent(Blocks.AIR, target[0]);
            h.assertTrue(most[0] > 0, "No column was raised for " + name + ": " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column for " + name + " is still standing: " + c.getTemporarySupportCount());
            h.assertTrue(dirtAboveGround(h) == 0, "A support for " + name + " was left in the air: " + dirtAboveGround(h) + ", " + report);
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end for " + name + ": " + report);
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (!stack.is(Items.DIRT) && !stack.is(Items.DIAMOND_PICKAXE)) c.setItem(slot, ItemStack.EMPTY);
            }
            index[0]++;
            future.set(null);
        });
        h.succeedWhen(() -> { h.assertTrue(index[0] >= sample.size(), "Still on block " + index[0] + " of " + sample.size()); });
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
     * is the game's own loot table, not a list of names — the same broad sample the way's own tests use.
     */
    private static List<Block> anyBlocks(GameTestHelper h) {
        List<Block> all = new ArrayList<>();
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        Entity breaker = new Marker(EntityType.MARKER, h.getLevel());
        BlockPos at = h.absolutePos(new BlockPos(HOME_X, FEET + 5, HOME_Z));
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR || block.asItem() == Items.AIR) continue;
            if (block.asItem() == Items.DIRT) continue;
            if (block instanceof FallingBlock) continue;
            BlockState state = block.defaultBlockState();
            if (!state.getFluidState().isEmpty()) continue;
            if (!state.isCollisionShapeFullBlock(h.getLevel(), at)) continue;
            List<ItemStack> drops;
            try {
                drops = Block.getDrops(state, h.getLevel(), at, null, breaker, pickaxe);
            } catch (RuntimeException broken) {
                continue;
            }
            if (drops.size() != 1 || drops.getFirst().getItem() != block.asItem()) continue;
            all.add(block);
        }
        return all;
    }

    private static String obtainPlan(String item) {
        return "[{\"action\":\"obtain_item\",\"item\":\"" + item + "\",\"count\":1}]";
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> sayModel(GameTestHelper h, CompanionEntity c, String phrase, String json) {
        var reply = CompanionPlanRequirements.apply(phrase, new CompanionReply("", CompanionPlanCodec.parsePlan(JsonParser.parseString(json))));
        return CompanionPlanRunner.run(reply.actions(),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-search-model", () -> false));
    }

    private static CompanionEntity spawn(GameTestHelper h) { return spawn(h, HOME_X, HOME_Z); }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, FEET, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(Math.max(1, x - 2), FEET, Math.max(1, z)));
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

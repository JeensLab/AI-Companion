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
 * The owner's own distances (idea PATH7, #105): blocks hanging in the air 3 to 25 blocks to the side and 3 to 20 above him,
 * nothing under them, several of them of different kinds. The scene he played refused them all — «Рядом нечего копать: ни
 * обсидиана, ни лавы» and «Рядом не вижу, где взять «Pumpkin»» — because the way's own budgets stopped ten blocks short: a
 * column's foot had to stand within ten blocks of him and a block seventeen above was past the way's height, so the one search
 * answered «ни к одному нет пути» for every one of them. These rows are the owner's geometry, one per distance, every one
 * through the plan the chat hands the runner for «добудь …», and they read the line the player sees in the game log.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWayOwnerDistanceTests {
    /** The field is wider than the way's box: a block 25 across and 20 up is in one scene, as his were. */
    private static final int SIDE = 48, TOP = 32;
    private static final int GROUND = 5;
    private static final int FEET = GROUND + 1;
    private static final int HOME_X = 24, HOME_Z = 16;

    // ---- one distance each: across, up, nothing under it ----

    /** His obsidian fifteen to the side and ten up, nothing under it: the foot of the column is a walk of fifteen blocks. */
    @GameTest(template = "way_far", timeoutTicks = 16000)
    public static void obsidianFifteenAcrossTenUpIsTaken(GameTestHelper h) {
        floatingTaken(h, Blocks.OBSIDIAN, Items.OBSIDIAN, 15, 0, 10, "добудь обсидиан", "minecraft:obsidian");
    }

    /** His lapis block twenty-two to the side and four up: farther than the ten the column's foot was allowed. */
    @GameTest(template = "way_far", timeoutTicks = 20000)
    public static void lapisBlockTwentyTwoAcrossFourUpIsTaken(GameTestHelper h) {
        floatingTaken(h, Blocks.LAPIS_BLOCK, Items.LAPIS_BLOCK, 22, 0, 4, "добудь блок лазурита", "minecraft:lapis_block");
    }

    /** His pumpkin twenty to the side and twenty up: the way's own height, and the farthest across of his scene. */
    @GameTest(template = "way_far", timeoutTicks = 24000)
    public static void pumpkinTwentyAcrossTwentyUpIsTaken(GameTestHelper h) {
        floatingTaken(h, Blocks.PUMPKIN, Items.PUMPKIN, 20, 0, 20, "добудь тыкву", "minecraft:pumpkin");
    }

    /** A block a chain also makes, twenty across and eighteen up: taken as it lies from a column, never made again. */
    @GameTest(template = "way_far", timeoutTicks = 24000)
    public static void goldBlockTwentyAcrossEighteenUpIsTaken(GameTestHelper h) {
        floatingTaken(h, Blocks.GOLD_BLOCK, Items.GOLD_BLOCK, 20, 0, 18, "добудь золотой блок", "minecraft:gold_block");
    }

    // ---- his whole scene: several blocks of different kinds, every distance ----

    /**
     * Four blocks of different kinds hanging in the air at once — obsidian three across and three up, a lapis block eight and
     * eight, a pumpkin fourteen and fourteen, a gold block twenty and eighteen — asked one after another: every one is found
     * and taken, and no refusal names the old box.
     */
    @GameTest(template = "way_far", timeoutTicks = 40000)
    public static void theOwnersFloatingBlocksAtEveryDistanceAreAllTaken(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos obsidian = new BlockPos(HOME_X + 3, FEET + 3, HOME_Z);
        BlockPos lapis = new BlockPos(HOME_X + 8, FEET + 8, HOME_Z + 2);
        BlockPos pumpkin = new BlockPos(HOME_X + 14, FEET + 14, HOME_Z);
        BlockPos gold = new BlockPos(HOME_X + 20, FEET + 18, HOME_Z + 2);
        h.setBlock(obsidian, Blocks.OBSIDIAN);
        h.setBlock(lapis, Blocks.LAPIS_BLOCK);
        h.setBlock(pumpkin, Blocks.PUMPKIN);
        h.setBlock(gold, Blocks.GOLD_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 24));
        List<String> ids = List.of("minecraft:obsidian", "minecraft:lapis_block", "minecraft:pumpkin", "minecraft:gold_block");
        List<Item> items = List.of(Items.OBSIDIAN, Items.LAPIS_BLOCK, Items.PUMPKIN, Items.GOLD_BLOCK);
        List<String> names = List.of("добудь обсидиан", "добудь блок лазурита", "добудь тыкву", "добудь золотой блок");
        List<CompletableFuture<CompanionPlanRunner.Outcome>> futures = new ArrayList<>(java.util.Collections.nCopies(ids.size(), (CompletableFuture<CompanionPlanRunner.Outcome>) null));
        int[] at = {0};
        int[] before = snapshot(h);
        h.onEachTick(() -> {
            if (at[0] >= ids.size()) return;
            if (futures.get(at[0]) == null) { futures.set(at[0], sayModel(h, c, names.get(at[0]), obtainPlan(ids.get(at[0])))); return; }
            if (!futures.get(at[0]).isDone()) return;
            String report = futures.get(at[0]).isCompletedExceptionally() ? "exception" : futures.get(at[0]).join().report();
            h.assertTrue(count(c, items.get(at[0])) >= 1, "«" + names.get(at[0]) + "» of his floating scene is not in the bag: " + report);
            h.assertTrue(!report.contains("Рядом нечего копать") && !report.contains("Рядом не вижу"), "The old refusal is back: " + report);
            // The next order must not be answered by this block's drop: the bag keeps only the soil and the pickaxe.
            for (int slot = 0; slot < c.getContainerSize(); slot++) {
                ItemStack stack = c.getItem(slot);
                if (!stack.is(Items.DIRT) && !stack.is(Items.DIAMOND_PICKAXE)) c.setItem(slot, ItemStack.EMPTY);
            }
            at[0]++;
        });
        h.succeedWhen(() -> {
            h.assertTrue(at[0] >= ids.size(), "Still on block " + at[0] + " of " + ids.size() + ": " + c.getLastTaskResult());
            h.assertBlockPresent(Blocks.AIR, obsidian);
            h.assertBlockPresent(Blocks.AIR, lapis);
            h.assertBlockPresent(Blocks.AIR, pumpkin);
            h.assertBlockPresent(Blocks.AIR, gold);
            assertRestored(h, before, obsidian, lapis, pumpkin, gold);
            h.assertTrue(dirtAboveGround(h) == 0, "A support of a column was left in the air: " + dirtAboveGround(h));
        });
    }

    // ---- the line the player reads in the game log ----

    /**
     * The search of his obsidian fifteen across and ten up: the line names the box it looked through, how many blocks it found,
     * the nearest one with its distance up and across and the way's answer, and the one chosen — the player can read the
     * decision, not only its result.
     */
    @GameTest(template = "way_far", timeoutTicks = 16000)
    public static void theSearchLineNamesTheBoxTheDistanceAndTheChoice(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X + 15, FEET + 10, HOME_Z);
        h.setBlock(target, Blocks.OBSIDIAN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 24));
        var future = sayModel(h, c, "добудь обсидиан", obtainPlan("minecraft:obsidian"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OBSIDIAN) == 1, "The obsidian is not in the bag: " + report);
            List<String> lines = journal(c, "way_search");
            String line = lines.stream().filter(entry -> entry.contains("\"item\":\"minecraft:obsidian\"")
                    && entry.contains("\"chosen\":\"")).findFirst().orElse(null);
            h.assertTrue(line != null, "No way_search line naming the chosen obsidian: " + lines);
            h.assertTrue(line.contains("\"radius\":32") && line.contains("\"depth\":32"), "The box is not named: " + line);
            h.assertTrue(line.contains("коробка 32 вбок, 32 вверх и 32 вниз"), "The line does not say the box in words: " + line);
            h.assertTrue(line.contains("+10 вверх/15.0 вбок"), "The line does not say how far up and across the block is: " + line);
            h.assertTrue(line.contains("ближайшие:") && line.contains("выбран "), "The line does not name the nearest and the choice: " + line);
            h.assertTrue(line.contains("\"shape\":\"column\"") && line.contains("\"id\":\"minecraft:obsidian\""), "The choice is not named: " + line);
            // Every decision of the way is in the same log, with the block and its distance.
            h.assertTrue(!journal(c, "way_decision").isEmpty(), "No way_decision line: " + journal(c, "way_decision"));
        });
    }

    /**
     * A pumpkin twenty-one above, past the way's height: seen by the search, no way reaches it. The refusal names the distance
     * — «21 блок вверх» — and the line of the search says why: never only «Рядом не вижу».
     */
    @GameTest(template = "way_far", timeoutTicks = 400)
    public static void aSeenButUnreachableBlockIsRefusedWithItsDistance(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X, FEET + 21, HOME_Z);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.DIRT, 24));
        var plan = c.obtainPlan("minecraft:pumpkin", 1);
        h.assertTrue(plan.refused(), "The pumpkin twenty-one above is not refused: " + plan);
        h.assertTrue(plan.refusal().contains("21 блок") && plan.refusal().contains("вверх"),
                "The refusal does not name the distance: " + plan.refusal());
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            List<String> lines = journal(c, "way_search");
            String line = lines.stream().filter(entry -> entry.contains("\"item\":\"minecraft:pumpkin\"")
                    && entry.contains("+21 вверх/")).findFirst().orElse(null);
            h.assertTrue(line != null, "The search does not say the pumpkin is 21 up: " + lines);
            h.assertTrue(line.contains("rise 21") || line.contains("up 21"), "The search does not say why no way is there: " + line);
            h.assertBlockPresent(Blocks.PUMPKIN, target);
        });
    }

    /**
     * A gold block twenty-one above with no gold ore about: the chain would fall back to the ore and name it, but the block
     * itself is what the player asked for and it is seen — the refusal names the block and its distance, never «Руды … не нашёл».
     */
    @GameTest(template = "way_far", timeoutTicks = 400)
    public static void aChainFallbackRefusalNamesTheBlockNotTheOre(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X, FEET + 21, HOME_Z);
        h.setBlock(target, Blocks.GOLD_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        c.setItem(1, new ItemStack(Items.DIRT, 24));
        var plan = c.obtainPlan("minecraft:gold_block", 1);
        h.assertTrue(plan.refused(), "The gold block twenty-one above is not refused: " + plan);
        // The name is the game's own («Block of Gold» in the test's locale, «Золотой блок» for the player): what matters is
        // that the refusal names the block's distance and not the ore the chain fell back to.
        h.assertTrue(plan.refusal().contains("21 блок") && plan.refusal().contains("вверх"),
                "The refusal does not name the distance: " + plan.refusal());
        h.assertTrue(!plan.refusal().contains("Руды") && !plan.refusal().contains("gold_ore"),
                "The refusal names the ore: " + plan.refusal());
        h.assertBlockPresent(Blocks.GOLD_BLOCK, target);
        h.succeed();
    }

    /** A pumpkin thirty across with his search at twenty-four: it is not claimed, and the line says a block exists outside the box. */
    @GameTest(template = "way_far", timeoutTicks = 400)
    public static void aBlockOutsideTheBoxIsSaidAsOutside(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos far = new BlockPos(HOME_X + 30, FEET + 4, HOME_Z);
        h.setBlock(far, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setSearchRadius(24);
        c.setItem(1, new ItemStack(Items.DIRT, 24));
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            List<String> lines = journal(c, "way_search");
            String outside = lines.stream().filter(entry -> entry.contains("\"item\":\"minecraft:pumpkin\"")
                    && entry.contains("\"outside\":1")).findFirst().orElse(null);
            h.assertTrue(outside != null, "No line says the pumpkin is outside the box: " + lines);
            h.assertTrue(outside.contains("за коробкой 24 вбок есть 1"), "The line does not say it in words: " + outside);
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
        c.setItem(1, new ItemStack(Items.DIRT, 24));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        var seen = c.obtainAvailable(id);
        h.assertTrue(seen.some(), "The search does not see «" + id + "» floating " + height + " up and " + dx + " across: "
                + seen + ", feet " + c.blockPosition() + ", block " + h.absolutePos(target) + ", radius " + c.getSearchRadius());
        var future = sayModel(h, c, phrase, obtainPlan(id));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, item) >= 1, "«" + phrase + "» floating " + height + " up and " + dx + " across is not in the bag: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            assertRestored(h, before, target);
            h.assertTrue(count(c, Items.DIRT) + looseDirt(h) >= 24, "The soil of the column was lost: " + count(c, Items.DIRT)
                    + " in the bag and " + looseDirt(h) + " on the ground, " + report);
            h.assertTrue(most[0] > 0, "No column was raised for " + height + " up: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column is still standing: " + c.getTemporarySupportCount());
            h.assertTrue(!report.contains("Полторы минуты") && !report.contains("не выполнено")
                    && !report.contains("Рядом нечего копать") && !report.contains("Рядом не вижу"), "A wrong end: " + report);
        });
    }

    private static String obtainPlan(String item) {
        return "[{\"action\":\"obtain_item\",\"item\":\"" + item + "\",\"count\":1}]";
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> sayModel(GameTestHelper h, CompanionEntity c, String phrase, String json) {
        var reply = CompanionPlanRequirements.apply(phrase, new CompanionReply("", CompanionPlanCodec.parsePlan(JsonParser.parseString(json))));
        return CompanionPlanRunner.run(reply.actions(),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-distance-model", () -> false));
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
        // The box of a companion whose search is not cut down: thirty-two across, as the player's own default.
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

    /** The soil of a column on the ground, waiting for the pickup. */
    private static int looseDirt(GameTestHelper h) {
        int count = 0;
        var box = new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(1, 1, 1)).getCenter(),
                h.absolutePos(new BlockPos(SIDE - 2, TOP - 2, SIDE - 2)).getCenter());
        for (net.minecraft.world.entity.item.ItemEntity entity : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box))
            if (entity.getItem().is(Items.DIRT)) count += entity.getItem().getCount();
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

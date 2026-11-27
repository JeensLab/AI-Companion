package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionItemLookup;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * An order for a thing takes it from the world before it is made: a fence, a stair, a slab, wool or glass standing about
 * him is broken and picked up, and only a thing that is nowhere about is crafted. The word «блок» is no part of that rule,
 * «сломай» is a mining order (never a hand-over) and «все такие блоки» is every block of that kind in the search box,
 * answered without the model. Every phrase goes through the player's own router, as the chat screen routes it.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionOrderWorldFirstTests {
    private static final int SIDE = 48, TOP = 24, GROUND = 5, FEET = GROUND + 1;
    private static final int HOME_X = 20, HOME_Z = 16;

    /** «Добудь вишнёвый забор» over a cherry fence the player built: the fence is broken, no craft is made. */
    @GameTest(template = "way_wide", timeoutTicks = 12000)
    public static void aStandingFenceIsBrokenNotCrafted(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        BlockPos fence = new BlockPos(HOME_X + 3, FEET, HOME_Z + 2);
        placedByPlayer(h, c, fence, Blocks.CHERRY_FENCE);
        var future = say(h, c, "добудь вишнёвый забор");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.CHERRY_FENCE) >= 1, "The fence is not in the bag: " + report);
            h.assertTrue(h.getBlockState(fence).isAir(), "The standing fence was left where it was (it was crafted, not taken): " + report);
            h.assertTrue(!report.contains("План остановлен") && !report.contains("Добыл не всё"), "The order did not end on its own: " + report);
        });
    }

    /**
     * The same rule for every thing that is both craftable and standing: the plan takes the block, never its recipe.
     * Asked of the expansion alone, so the table is cheap and the red line names the item.
     */
    @GameTest(template = "way_wide")
    public static void everyStandingThingIsTakenNotCrafted(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        Map<String, Block> standing = Map.of(
                "minecraft:cherry_fence", Blocks.CHERRY_FENCE,
                "minecraft:cherry_stairs", Blocks.CHERRY_STAIRS,
                "minecraft:cherry_slab", Blocks.CHERRY_SLAB,
                "minecraft:white_wool", Blocks.WHITE_WOOL,
                "minecraft:glass", Blocks.GLASS);
        int at = 0;
        for (var entry : standing.entrySet()) {
            BlockPos pos = new BlockPos(HOME_X + 3 + at, FEET, HOME_Z + 2);
            placedByPlayer(h, c, pos, entry.getValue());
            var plan = port(h, c).expand(new PlannedAction("obtain_item", 0, 1, entry.getKey())).join();
            h.assertTrue(plan.refusal() == null, "«" + entry.getKey() + "» standing was refused: " + plan.refusal());
            h.assertTrue(plan.steps().stream().anyMatch(step -> step.type().equals("gather_block")),
                    "«" + entry.getKey() + "» standing is not taken from the world: " + plan.steps());
            h.assertTrue(plan.steps().stream().noneMatch(step -> step.type().equals("craft_item")),
                    "«" + entry.getKey() + "» standing is crafted instead: " + plan.steps());
            h.setBlock(pos, Blocks.AIR);
            at++;
        }
        h.succeed();
    }

    /** «Сломай вишнёвый забор» is a mining order: the fence is broken, and no hand-over is asked for it. */
    @GameTest(template = "way_wide", timeoutTicks = 12000)
    public static void breakIsAMiningOrderNotAHandOver(GameTestHelper h) {
        String command = CompanionLocalCommands.route("сломай вишнёвый забор", "Олег", RU);
        h.assertTrue(command != null, "«сломай вишнёвый забор» went to the model");
        var actions = CompanionLocalCommands.actions(command);
        h.assertTrue(actions.stream().noneMatch(action -> action.type().equals("give_items")),
                "The mining order ends with a hand-over: " + actions);
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        BlockPos fence = new BlockPos(HOME_X + 3, FEET, HOME_Z + 2);
        placedByPlayer(h, c, fence, Blocks.CHERRY_FENCE);
        var future = CompanionPlanRunner.run(actions,
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-break", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.CHERRY_FENCE) >= 1, "The fence is not in the bag: " + report);
            h.assertTrue(h.getBlockState(fence).isAir(), "The fence still stands: " + report);
            h.assertTrue(!report.contains("У меня нет того, что нужно отдать"), "The mining order asked to hand over what it had not got: " + report);
        });
    }

    /** No fence anywhere and planks and sticks in the bag: only now is it crafted, from what he carries. */
    @GameTest(template = "way_wide", timeoutTicks = 12000)
    public static void aFenceNowhereAboutIsCrafted(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        c.setItem(1, new ItemStack(Items.CHERRY_PLANKS, 8));
        c.setItem(2, new ItemStack(Items.STICK, 8));
        var plan = port(h, c).expand(new PlannedAction("obtain_item", 0, 1, "minecraft:cherry_fence")).join();
        h.assertTrue(plan.refusal() == null, "A fence of planks and sticks was refused: " + plan.refusal());
        h.assertTrue(plan.steps().stream().anyMatch(step -> step.type().equals("craft_item") && step.item().equals("minecraft:cherry_fence")),
                "No fence anywhere is not crafted: " + plan.steps());
        h.assertTrue(plan.steps().stream().noneMatch(step -> step.type().equals("gather_block")),
                "A block was taken though no fence stands: " + plan.steps());
        h.succeed();
    }

    /**
     * «Все такие блоки добудь что видишь» is every block of the kind in the box, answered locally — here after «добудь блок
     * забора», the order the words «такие блоки» point back to.
     */
    @GameTest(template = "way_wide", timeoutTicks = 20000)
    public static void allSuchBlocksAreEveryBlockOfTheKind(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        List<BlockPos> fences = List.of(new BlockPos(HOME_X + 3, FEET, HOME_Z + 2),
                new BlockPos(HOME_X + 4, FEET, HOME_Z + 2), new BlockPos(HOME_X + 5, FEET, HOME_Z + 2),
                new BlockPos(HOME_X + 6, FEET, HOME_Z + 2));
        for (BlockPos pos : fences) placedByPlayer(h, c, pos, Blocks.CHERRY_FENCE);
        String first = CompanionLocalCommands.route("добудь блок забора", "Олег", RU);
        h.assertTrue(first != null, "«добудь блок забора» went to the model");
        String command = CompanionLocalCommands.route("все такие блоки добудь что видишь", "Олег", RU);
        h.assertTrue(command != null, "«все такие блоки добудь что видишь» went to the model");
        h.assertTrue(command.startsWith("companion work obtain_item "), "The all-order is no obtain: " + command);
        int[] at = {0};
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<CompanionPlanRunner.Outcome>> future = new java.util.concurrent.atomic.AtomicReference<>();
        h.onEachTick(() -> {
            if (at[0] > 1) return;
            if (future.get() == null) {
                future.set(CompanionPlanRunner.run(CompanionLocalCommands.actions(at[0] == 0 ? first : command), port(h, c)));
                return;
            }
            if (!future.get().isDone()) return;
            String report = future.get().isCompletedExceptionally() ? "exception" : future.get().join().report();
            if (at[0] == 0) {
                h.assertTrue(count(c, Items.CHERRY_FENCE) >= 1, "The first order left no fence in the bag: " + report);
            } else {
                for (BlockPos pos : fences) h.assertTrue(h.getBlockState(pos).isAir(), "A fence of the kind still stands: " + report);
                h.assertTrue(count(c, Items.CHERRY_FENCE) >= 4, "Not every fence is in the bag: " + report);
            }
            future.set(null);
            at[0]++;
        });
        h.succeedWhen(() -> h.assertTrue(at[0] > 1, "Still on the fence orders: " + c.getLastTaskResult()));
    }

    /** «Добудь блок забора» still takes the fence that stands, the measure word being no part of the rule. */
    @GameTest(template = "way_wide", timeoutTicks = 12000)
    public static void theMeasureWordStillTakesTheBlock(GameTestHelper h) {
        String command = CompanionLocalCommands.route("добудь блок забора", "Олег", RU);
        h.assertTrue(command != null, "«добудь блок забора» went to the model");
        String item = CompanionLocalCommands.actions(command).getFirst().item();
        h.assertTrue(item.endsWith("_fence") || item.equals("#minecraft:fences"), "«добудь блок забора» names " + item);
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        BlockPos fence = new BlockPos(HOME_X + 3, FEET, HOME_Z + 2);
        placedByPlayer(h, c, fence, Blocks.CHERRY_FENCE);
        var future = CompanionPlanRunner.run(CompanionLocalCommands.actions(command), port(h, c));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(h.getBlockState(fence).isAir(), "The fence named by «блок забора» still stands: " + report);
            h.assertTrue(count(c, Items.CHERRY_FENCE) >= 1, "The fence is not in the bag: " + report);
        });
    }

    /**
     * «Добудь забор» names the kind, not a wood: the fence that stands — of any wood — is taken, and nothing is crafted for
     * it. At fb6601a7 the kind was resolved to one wood (the plainest name), so a cherry fence was left standing.
     */
    @GameTest(template = "way_wide", timeoutTicks = 12000)
    public static void aBareKindTakesTheMemberThatStands(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        BlockPos fence = new BlockPos(HOME_X + 3, FEET, HOME_Z + 2);
        placedByPlayer(h, c, fence, Blocks.CHERRY_FENCE);
        var future = say(h, c, "добудь забор");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(h.getBlockState(fence).isAir(), "The cherry fence that stood was left standing: " + report);
            h.assertTrue(count(c, Items.CHERRY_FENCE) >= 1, "The cherry fence is not in the bag: " + report);
        });
    }

    /**
     * The owner's live order, 27.11: five cherry fences stand about him, planks and sticks in the bag, and the count asked
     * is above what stands (his model queued sixteen). At 15593491 the plan left the world for the recipe chain —
     * gather_wood 8, then craft_item 16 — and the work failed on «Не нашёл доступного дерева для подготовки». What stands
     * is taken, and no chain of another material is asked for it.
     */
    @GameTest(template = "way_wide", timeoutTicks = 12000)
    public static void moreAskedThanStandsTakesWhatStands(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        c.setItem(1, new ItemStack(Items.CHERRY_PLANKS, 8));
        c.setItem(2, new ItemStack(Items.STICK, 8));
        c.setItem(3, new ItemStack(Items.CRAFTING_TABLE));
        fiveFences(h, c);
        var plan = port(h, c).expand(new PlannedAction("obtain_item", 0, 16, "minecraft:cherry_fence")).join();
        h.assertTrue(plan.refusal() == null, "Five standing fences were refused for sixteen asked: " + plan.refusal());
        h.assertTrue(plan.steps().stream().anyMatch(step -> step.type().equals("gather_block")),
                "The standing fences are not taken when more are asked: " + plan.steps());
        // The cap: the plan takes the five that stand, not sixteen and not a sixth made for the rest.
        h.assertTrue(plan.steps().stream().filter(step -> step.type().equals("gather_block")).allMatch(step -> step.count() == 5),
                "The plan does not take exactly the five that stand: " + plan.steps());
        h.assertTrue(plan.steps().stream().noneMatch(step -> step.type().equals("gather_wood")),
                "The fences that stand were left for wood: " + plan.steps());
        h.assertTrue(plan.steps().stream().noneMatch(step -> step.type().equals("craft_item")),
                "A fence is crafted though five stand: " + plan.steps());
        h.succeed();
    }

    /** The same order run for real: the fences that stand are broken and picked up, and no wood line is said. */
    @GameTest(template = "way_wide", timeoutTicks = 20000)
    public static void theFencesThatStandAreMinedWhenMoreAreAsked(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        c.setItem(1, new ItemStack(Items.CHERRY_PLANKS, 8));
        c.setItem(2, new ItemStack(Items.STICK, 8));
        List<BlockPos> fences = fiveFences(h, c);
        var future = CompanionPlanRunner.run(List.of(new PlannedAction("obtain_item", 0, 16, "minecraft:cherry_fence")), port(h, c));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            for (BlockPos pos : fences) h.assertTrue(h.getBlockState(pos).isAir(), "A standing fence was left: " + report);
            // The five that stood are the cap: sixteen were asked, five were taken, and no sixth was crafted for the rest.
            h.assertTrue(count(c, Items.CHERRY_FENCE) == 5, "Not exactly the five fences that stood are in the bag: "
                    + count(c, Items.CHERRY_FENCE) + " " + report);
            h.assertTrue(!report.contains("дерева"), "A wood line was said over standing fences: " + report);
        });
    }

    /** The same rule for a stair: two stand, eight are asked, and the world's two are taken instead of crafted. */
    @GameTest(template = "way_wide", timeoutTicks = 12000)
    public static void aStairAboveWhatStandsIsTaken(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        c.setItem(1, new ItemStack(Items.CHERRY_PLANKS, 8));
        c.setItem(2, new ItemStack(Items.CRAFTING_TABLE));
        BlockPos first = new BlockPos(HOME_X + 3, FEET, HOME_Z + 2), second = new BlockPos(HOME_X + 4, FEET, HOME_Z + 2);
        placedByPlayer(h, c, first, Blocks.CHERRY_STAIRS);
        placedByPlayer(h, c, second, Blocks.CHERRY_STAIRS);
        var plan = port(h, c).expand(new PlannedAction("obtain_item", 0, 8, "minecraft:cherry_stairs")).join();
        h.assertTrue(plan.refusal() == null, "Two standing stairs were refused for eight asked: " + plan.refusal());
        h.assertTrue(plan.steps().stream().anyMatch(step -> step.type().equals("gather_block")),
                "The standing stairs are not taken when more are asked: " + plan.steps());
        h.assertTrue(plan.steps().stream().noneMatch(step -> step.type().equals("craft_item")),
                "A stair is crafted though two stand: " + plan.steps());
        h.succeed();
    }

    /** «Добудь 16 заборов» with five cherry fences standing: the kind's standing member is taken, never its recipe. */
    @GameTest(template = "way_wide", timeoutTicks = 20000)
    public static void aBareKindAboveWhatStandsTakesTheMember(GameTestHelper h) {
        String command = CompanionLocalCommands.route("добудь 16 заборов", "Олег", RU);
        h.assertTrue(command != null, "«добудь 16 заборов» went to the model");
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        c.setItem(1, new ItemStack(Items.CHERRY_PLANKS, 8));
        List<BlockPos> fences = fiveFences(h, c);
        var future = CompanionPlanRunner.run(CompanionLocalCommands.actions(command), port(h, c));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            for (BlockPos pos : fences) h.assertTrue(h.getBlockState(pos).isAir(), "A fence of the kind was left: " + report);
            h.assertTrue(!report.contains("дерева"), "A wood line was said over fences that stand: " + report);
        });
    }

    /**
     * The owner's words with planks in the bag, five fences standing: the fence is broken, not crafted. A pin: the count
     * here is one, so 15593491 already takes it.
     */
    @GameTest(template = "way_wide", timeoutTicks = 20000)
    public static void theOrderForACherryFenceTakesTheFence(GameTestHelper h) {
        takesTheFencesFor(h, "добудь вишнёвый забор");
    }

    /** The same with the measure word: «блок вишнёвого забора» is the fence, and it is taken. */
    @GameTest(template = "way_wide", timeoutTicks = 20000)
    public static void theBlockOfACherryFenceTakesTheFence(GameTestHelper h) {
        takesTheFencesFor(h, "добудь блок вишнёвого забора");
    }

    private static void takesTheFencesFor(GameTestHelper h, String phrase) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» went to the model");
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        c.setItem(1, new ItemStack(Items.CHERRY_PLANKS, 8));
        c.setItem(2, new ItemStack(Items.STICK, 8));
        List<BlockPos> fences = fiveFences(h, c);
        var future = CompanionPlanRunner.run(CompanionLocalCommands.actions(command), port(h, c));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "«" + phrase + "» still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(fences.stream().anyMatch(pos -> h.getBlockState(pos).isAir()), "«" + phrase + "» left every fence standing: " + report);
            h.assertTrue(!report.contains("дерева"), "«" + phrase + "» said a wood line over fences that stand: " + report);
        });
    }

    /** No fence anywhere and planks and sticks in the bag: the fallback survives — he really crafts one. */
    @GameTest(template = "way_wide", timeoutTicks = 12000)
    public static void aFenceNowhereAboutIsCraftedForReal(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIAMOND_AXE));
        c.setItem(1, new ItemStack(Items.CHERRY_PLANKS, 8));
        c.setItem(2, new ItemStack(Items.STICK, 8));
        c.setItem(3, new ItemStack(Items.CRAFTING_TABLE));
        var future = CompanionPlanRunner.run(List.of(new PlannedAction("obtain_item", 0, 1, "minecraft:cherry_fence")), port(h, c));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.CHERRY_FENCE) >= 1, "No fence anywhere was not crafted: " + future.join().report());
        });
    }

    // ---- helpers ----

    /** Five cherry fences standing about him, the player's own build. */
    private static List<BlockPos> fiveFences(GameTestHelper h, CompanionEntity c) {
        List<BlockPos> fences = new ArrayList<>();
        for (int at = 0; at < 5; at++) {
            BlockPos pos = new BlockPos(HOME_X + 3 + at, FEET, HOME_Z + 2);
            placedByPlayer(h, c, pos, Blocks.CHERRY_FENCE);
            fences.add(pos);
        }
        return fences;
    }

    private static CompanionServerPlanPort port(GameTestHelper h, CompanionEntity c) {
        return new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-world-first", () -> false);
    }

    /** The player's phrase through the router and the runner: a local order, no model. */
    private static CompletableFuture<CompanionPlanRunner.Outcome> say(GameTestHelper h, CompanionEntity c, String phrase) {
        String command = CompanionLocalCommands.route(phrase, "Олег", RU);
        h.assertTrue(command != null, "«" + phrase + "» went to the model");
        return CompanionPlanRunner.run(CompanionLocalCommands.actions(command), port(h, c));
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

    /** A real placement by the player: the event the game fires, the bookkeeping the mod keeps (CompanionEvents.onBlockPlaced). */
    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos cell, Block block) {
        h.setBlock(cell, block);
        var player = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                net.neoforged.neoforge.common.util.BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(cell)),
                Blocks.GRASS_BLOCK.defaultBlockState(), player));
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }

    /**
     * The names a Russian client shows, hand-picked here because the GameTest server runs en_us. They stand in for the
     * player's language: the resolver is the same one the client runs, only the names come from here.
     */
    private static final Map<String, String> RU_NAMES = Map.ofEntries(
            Map.entry("cherry_fence", "Вишнёвый забор"),
            Map.entry("cherry_stairs", "Вишнёвые ступеньки"),
            Map.entry("cherry_slab", "Вишнёвая плита"),
            Map.entry("cherry_planks", "Вишнёвые доски"),
            Map.entry("cherry_log", "Вишнёвое бревно"),
            Map.entry("oak_fence", "Дубовый забор"),
            Map.entry("spruce_fence", "Еловый забор"),
            Map.entry("white_wool", "Белая шерсть"),
            Map.entry("glass", "Стекло"),
            Map.entry("stick", "Палка"));

    private static final Function<Item, String> RU = CompanionOrderWorldFirstTests::russianName;

    private static String russianName(Item item) {
        String name = RU_NAMES.get(BuiltInRegistries.ITEM.getKey(item).getPath());
        return name != null ? name : new ItemStack(item).getHoverName().getString();
    }
}

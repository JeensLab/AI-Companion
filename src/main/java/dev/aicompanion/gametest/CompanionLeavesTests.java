package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionNumbers;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionLeaves;
import dev.aicompanion.entity.CompanionObtain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * «Добудь листву» is the leaves of the tree he stands by, never its trunk (the owner, 17.11: he swapped the pickaxe for the axe
 * and chopped the trunk, and the shears he carried were never used). The order is the player's direct one, so it is routed here
 * without the model; the species is the tree's own, and with shears in the bag the shears are what is worn at its end.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLeavesTests {
    private static final int GROUND = 5;

    /**
     * «Добудь листву», «собери листву», «набери листья», «добудь листья»: one local order for the leaves of the tree nearest
     * him, a leaf block and no log in the chain the server plans from it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void aLeafOrderIsTheTreesLeavesNotItsTrunk(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        for (BlockPos cell : List.of(trunk.above(2).west(), trunk.above(2).east(), trunk.above(3).north(), trunk.above(3).south()))
            h.setBlock(cell, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        CompanionEntity c = spawn(h, 12, 16);
        String first = null;
        for (String phrase : List.of("добудь листву", "собери листву", "набери листья", "добудь листья")) {
            String command = CompanionLocalCommands.route(phrase, "Олег");
            h.assertTrue(command != null, "«" + phrase + "» went to the model");
            h.assertTrue(command.startsWith("companion work obtain_item "), "«" + phrase + "» is no obtain order: " + command);
            if (first == null) first = command; else h.assertTrue(first.equals(command), "«" + phrase + "» routes another way: " + command);
        }
        List<PlannedAction> actions = CompanionLocalCommands.actions(first);
        h.assertTrue(actions.getFirst().type().equals("obtain_item"), "Not an obtain order: " + actions);
        String item = CompanionLeaves.resolve(c, h.getLevel(), actions.getFirst().item());
        Item resolved = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(item)).orElse(Items.AIR);
        h.assertTrue(new ItemStack(resolved).is(ItemTags.LEAVES), "No leaf block in the order: " + item + ", " + actions);
        var plan = CompanionObtain.plan(h.getLevel(), List.of(new ItemStack(Items.SHEARS)), false, item, actions.getFirst().count(), thing -> true);
        h.assertTrue(!plan.refused(), "The chain refused: " + plan.refusal());
        h.assertTrue(plan.steps().stream().anyMatch(step -> step.type().equals("gather_block") && new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(step.item()))).is(ItemTags.LEAVES)),
                "The chain gathers no leaf block: " + plan);
        h.assertTrue(plan.steps().stream().noneMatch(step -> step.type().equals("gather_wood")), "The chain chops logs: " + plan);
        h.succeed();
    }

    /**
     * The owner's own case: the stone pickaxe in his hand, an iron pickaxe, an axe, shears and a shovel in the bag, a spruce by him.
     * «Добудь листву» ends with the leaves in the bag, the shears worn and not one log taken: the axe is never swung at the trunk.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void theOwnersCaseEndsWithTheShearsWornAndTheLeavesStored(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(20, GROUND + 1, 16);
        for (int up = 0; up < 4; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 5; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) h.setBlock(cell, leaves);
        }
        CompanionEntity c = withTheOwnersTools(h);
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        h.assertTrue(plan.getLast().type().equals("equip_item") && "minecraft:shears".equals(plan.getLast().item()),
                "The order does not end with the shears: " + plan);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) >= 16, "No spruce leaves stored: " + count(c, Items.SPRUCE_LEAVES) + ", " + report);
            h.assertTrue(c.getMainHandItem().is(Items.SHEARS), "The shears are not worn: " + c.getMainHandItem() + ", " + report);
            h.assertTrue(count(c, Items.SPRUCE_LOG) == 0 && count(c, Items.OAK_LOG) == 0, "A log was taken for leaves: " + report);
            h.assertTrue(damage(c, Items.SHEARS) >= 16, "The shears did not cut the leaves: " + damage(c, Items.SHEARS) + ", " + report);
            h.assertTrue(damage(c, Items.STONE_PICKAXE) == 0 && damage(c, Items.IRON_PICKAXE) == 0 && damage(c, Items.IRON_AXE) == 0,
                    "Another tool was worn: pickaxe " + damage(c, Items.STONE_PICKAXE) + ", axe " + damage(c, Items.IRON_AXE) + ", " + report);
            h.assertTrue(dev.aicompanion.client.CompanionDialogue.summarize(plan, report).equals("Собрал 16 листьев."),
                    "The answer is not the count: " + dev.aicompanion.client.CompanionDialogue.summarize(plan, report));
        });
    }

    /** «Сруби дерево» and «наруби дров» stay the trunk: a gather_wood with no species of its own, not a leaf order. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void aTreeIsStillWood(GameTestHelper h) {
        for (String phrase : List.of("сруби дерево", "наруби дров")) {
            String command = CompanionLocalCommands.route(phrase, "Олег");
            h.assertTrue(command != null, "«" + phrase + "» went to the model");
            PlannedAction action = CompanionLocalCommands.actions(command).getFirst();
            h.assertTrue(action.type().equals("gather_wood"), "«" + phrase + "» is no wood order: " + command);
            h.assertTrue(dev.aicompanion.entity.CompanionWoodKind.of(action.item()) == null, "«" + phrase + "» names a species: " + action.item());
        }
        h.succeed();
    }

    /** A spruce by him: the order names spruce leaves, not oak. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void theNearestTreeNamesItsOwnLeaves(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        for (BlockPos cell : List.of(trunk.above(2).west(), trunk.above(2).east(), trunk.above(3).north(), trunk.above(3).south()))
            h.setBlock(cell, Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        CompanionEntity c = spawn(h, 12, 16);
        String found = CompanionLeaves.nearest(c, h.getLevel());
        h.assertTrue("minecraft:spruce_leaves".equals(found), "The spruce is not named: " + found);
        h.assertTrue("minecraft:spruce_leaves".equals(CompanionLeaves.resolve(c, h.getLevel(), CompanionLeaves.FAMILY)),
                "The order did not resolve to the spruce");
        h.succeed();
    }

    /** Nothing of a tree about: no species is invented, and an item of its own is left as it was. */
    @GameTest(template = "tall_tree", timeoutTicks = 100)
    public static void noTreeAboutNamesNoSpecies(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h, 12, 16);
        h.assertTrue(CompanionLeaves.nearest(c, h.getLevel()) == null, "A tree was found in the open");
        h.assertTrue(CompanionLeaves.resolve(c, h.getLevel(), CompanionLeaves.FAMILY) == null,
                "A species was invented: " + CompanionLeaves.resolve(c, h.getLevel(), CompanionLeaves.FAMILY));
        h.assertTrue("minecraft:birch_leaves".equals(CompanionLeaves.resolve(c, h.getLevel(), "minecraft:birch_leaves")),
                "An item of its own was rewritten");
        h.succeed();
    }

    /**
     * The owner's tall tree (17.11): a tall spruce whose crown hangs over him out of reach stands nearest, while the oak
     * beside it has leaves he can stand by. The order must name the oak — the reachable tree, not the nearest block in the
     * air — and really gather, without «Рядом не вижу» and without the species lecture.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aTallTreeOutOfReachLeavesTheReachableTreeAsTheTarget(GameTestHelper h) {
        ground(h);
        BlockPos spruce = new BlockPos(18, GROUND + 1, 16);
        // The trunk runs through the whole crown, as a grown spruce's does: no stand cell on its top, so the crown is above his reach.
        for (int up = 0; up < 8; up++) h.setBlock(spruce.above(up), Blocks.SPRUCE_LOG);
        var spruceLeaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 5; dy <= 7; dy++) {
            BlockPos cell = spruce.offset(dx, dy, dz);
            if (!cell.equals(spruce.above(dy))) h.setBlock(cell, spruceLeaves);
        }
        BlockPos oak = new BlockPos(24, GROUND + 1, 16);
        for (int up = 0; up < 2; up++) h.setBlock(oak.above(up), Blocks.OAK_LOG);
        var oakLeaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 2; dy++) {
            BlockPos cell = oak.offset(dx, dy, dz);
            if (!cell.equals(oak.above(dy))) h.setBlock(cell, oakLeaves);
        }
        CompanionEntity c = withTheOwnersTools(h);
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        h.assertTrue("minecraft:oak_leaves".equals(CompanionLeaves.resolve(c, h.getLevel(), CompanionLeaves.FAMILY)),
                "The tall spruce's unreachable leaves were named: " + CompanionLeaves.resolve(c, h.getLevel(), CompanionLeaves.FAMILY));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-tall", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 16, "No sixteen oak leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(said.equals("Собрал 16 листьев."), "The answer is not the count: " + said);
            h.assertTrue(!said.contains("Рядом листвы не вижу") && !report.contains("Рядом не вижу"), "The leaves were refused: " + report);
            h.assertTrue(!report.contains("Еловая"), "The spruce was named in the failure: " + report);
        });
    }

    /**
     * The owner's tall tree grown with bone meal (17.11): its whole crown stands six and seven blocks above the ground, no
     * stand cell under it, and his bag carries soil. «Добудь листву»: he walks to the trunk, builds up on his own supports,
     * cuts the crown from the top, and comes back down with the soil taken back — no fall hurts him and no block is left.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void aCrownAboveEveryStandIsCutFromHisOwnColumn(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 5; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 5; dy <= 6; dy++)
            h.setBlock(trunk.offset(dx, dy, dz), leaves);
        CompanionEntity c = withTheOwnersTools(h);
        c.setItem(4, new ItemStack(Items.DIRT, 8));
        double groundY = c.getY();
        double[] highest = {0};
        int[] supports = {0};
        h.onEachTick(() -> {
            highest[0] = Math.max(highest[0], c.getY());
            supports[0] = Math.max(supports[0], c.getTemporarySupportCount());
        });
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        h.assertTrue("minecraft:spruce_leaves".equals(CompanionLeaves.resolve(c, h.getLevel(), CompanionLeaves.FAMILY)),
                "The crown above every stand was not named: " + CompanionLeaves.resolve(c, h.getLevel(), CompanionLeaves.FAMILY));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-crown", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) >= 16, "Not sixteen leaves: " + count(c, Items.SPRUCE_LEAVES) + ", " + report);
            h.assertTrue(said.equals("Собрал 16 листьев."), "The answer is not the count: " + said + ", " + report);
            h.assertTrue(supports[0] > 0 && highest[0] > groundY + 2, "He never built up: supports " + supports[0] + ", highest y " + highest[0] + ", " + report);
            h.assertTrue(c.onGround() && Math.abs(c.getY() - groundY) < 0.6, "He did not come back down: y " + c.getY() + ", " + report);
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "The climb hurt him: " + c.getHealth() + " of " + c.getMaxHealth() + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column is still his: " + c.getTemporarySupportCount());
            h.assertTrue(dirtAboveGround(h) == 0, "A pillar block was left standing");
            h.assertTrue(count(c, Items.DIRT) == 8, "The soil of his column was lost: " + count(c, Items.DIRT));
        });
    }

    /**
     * The owner's tall crown and nothing to stand on: no soil in the bag and a stone shelf under him — no dirt to dig about
     * at all. He cannot build up, so the order is refused naming the soil he lacks — not «Рядом листвы не вижу» for a crown
     * that stands right over him — nothing gathered, no soil invented.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aCrownAboveReachWithoutSoilNamesTheSoil(GameTestHelper h) {
        stoneGround(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 5; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 5; dy <= 6; dy++)
            h.setBlock(trunk.offset(dx, dy, dz), leaves);
        CompanionEntity c = withTheOwnersTools(h);
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-crown-none", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(said.equals("До листвы не достану — дай земли."), "Not the soil line: " + said + ", " + report);
            h.assertTrue(!said.contains("Рядом листвы не вижу"), "The short line came back for a crown overhead: " + said);
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) == 0, "A leaf was gathered out of reach: " + report);
            h.assertTrue(supports[0] == 0 && dirtAboveGround(h) == 0, "A column was built without soil");
        });
    }

    /**
     * A given-up obtain takes the shears step with it: nothing was cut, so they stay in the bag and their own line does not
     * speak over the chain's reason — the reason itself is the answer. A stone shelf under him: no soil to dig for a column.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aGivenUpObtainDoesNotWearTheShears(GameTestHelper h) {
        stoneGround(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 5; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 5; dy <= 6; dy++)
            h.setBlock(trunk.offset(dx, dy, dz), leaves);
        CompanionEntity c = withTheOwnersTools(h);
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-crown-wear", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(!report.contains("Уже экипирован") && !report.contains("Взял в правую руку"),
                    "The equip step ran after the obtain was given up: " + report);
            h.assertTrue(c.getMainHandItem().is(Items.STONE_PICKAXE), "The shears were worn for nothing: " + c.getMainHandItem() + ", " + report);
            h.assertTrue(said.equals("До листвы не достану — дай земли."), "The chain's reason was not spoken: " + said + ", " + report);
        });
    }

    /** A short tree is still cut from the ground: the soil in the bag raises no column, and nothing of it is spent. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aShortTreeIsStillCutFromTheGroundWithSoilInTheBag(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (BlockPos cell : List.of(trunk.above().west(), trunk.above().east(), trunk.above().north(),
                trunk.above(2).west(), trunk.above(2).east()))
            h.setBlock(cell, leaves);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        CompanionEntity c = withTheOwnersTools(h);
        c.setItem(4, new ItemStack(Items.DIRT, 8));
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":5}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 5, "Not five leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(supports[0] == 0 && dirtAboveGround(h) == 0, "A column was built to a crown he could reach: " + supports[0]);
            h.assertTrue(count(c, Items.DIRT) == 8, "The soil was spent on a reachable crown: " + count(c, Items.DIRT));
        });
    }

    /** No leaf of any tree at all: one short line, «листву» and no species he could not come at. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void noReachableLeafIsRefusedShortWithoutASpecies(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 6; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        CompanionEntity c = withTheOwnersTools(h);
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-none", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(said.equals("Рядом листвы не вижу."), "Not the short line: " + said);
            h.assertTrue(!said.contains("Остальное сделал") && !said.contains("Еловая") && !said.contains("Дубовая"), "Species or the old prefix: " + said);
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) == 0, "A leaf was gathered out of reach: " + report);
        });
    }

    /**
     * The owner's bag (17.11): sixteen oak leaves already in it beside an oak. «Добудь листву» cuts nothing — the bag meets
     * the order, the check passes, and the answer is that count («Готово: «Oak Leaves» у меня, 16 шт.»), not «Листвы рядом
     * не нашёл.»
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void anOrderTheBagAlreadyMeetsAnswersItsCount(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (BlockPos cell : List.of(trunk.above(2).west(), trunk.above(2).east(), trunk.above(3).north(), trunk.above(3).south()))
            h.setBlock(cell, leaves);
        CompanionEntity c = withTheOwnersTools(h);
        c.setItem(4, new ItemStack(Items.OAK_LEAVES, 16));
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-bag", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(report.contains("Готово: «Oak Leaves» у меня, 16 шт."), "The bag was not checked: " + report);
            h.assertTrue(said.equals("Готово: «Oak Leaves» у меня, 16 шт."), "Not the honest count: " + said + ", " + report);
            h.assertTrue(!said.contains("не нашёл") && !said.contains("не вижу"), "A refusal came back: " + said);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 16, "The bag changed: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(damage(c, Items.SHEARS) == 0, "Leaves were cut for a bag that had them: " + damage(c, Items.SHEARS) + ", " + report);
        });
    }

    /**
     * Sixteen oak leaves in the bag and no tree of any kind about: the order the bag meets is answered by that count, not
     * refused for the tree the world does not have.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void leavesCarriedWithoutATreeAnswerTheirCount(GameTestHelper h) {
        ground(h);
        CompanionEntity c = withTheOwnersTools(h);
        c.setItem(4, new ItemStack(Items.OAK_LEAVES, 16));
        h.assertTrue(CompanionLeaves.nearest(c, h.getLevel()) == null, "A tree was found in the open");
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-bag-open", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(report.contains("Готово: «Oak Leaves» у меня, 16 шт."), "The carried leaves were not checked: " + report);
            h.assertTrue(said.equals("Готово: «Oak Leaves» у меня, 16 шт."), "Not the honest count: " + said + ", " + report);
            h.assertTrue(!report.contains("Собрал: ") && !said.contains("не нашёл") && !said.contains("не вижу"), "A gather or a refusal came back: " + said + ", " + report);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 16, "The bag changed: " + count(c, Items.OAK_LEAVES) + ", " + report);
        });
    }

    /**
     * The owner's short tree (17.11): six leaves on the oak, «добудь 16 листвы». The gather brings six of the sixteen and the
     * order is given up: the answer is that part of the count — «Собрал 6 из 16 — больше рядом нет», not a flat refusal over a
     * bag that already holds six leaves.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aPartialGatherAnswersWhatItBroughtOfTheCount(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (BlockPos cell : List.of(trunk.above().west(), trunk.above().east(), trunk.above().north(),
                trunk.above(2).west(), trunk.above(2).east(), trunk.above(2).north()))
            h.setBlock(cell, leaves);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        CompanionEntity c = withTheOwnersTools(h);
        String command = CompanionLocalCommands.route("добудь 16 листвы", "Олег");
        h.assertTrue(command != null, "«добудь 16 листвы» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        h.assertTrue(plan.getFirst().count() == 16 && CompanionLeaves.isFamily(plan.getFirst().item()), "Not a sixteen-leaf order: " + plan);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-partial", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 6, "Not six leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(said.equals("Собрал 6 из 16 — больше рядом нет."), "The count was hidden: " + said + ", " + report);
            h.assertTrue(!said.equals("Рядом листвы не вижу.") && !said.equals("Листвы рядом не нашёл."), "A flat refusal came back: " + said);
        });
    }

    /**
     * Fifteen oak leaves carried and no tree of any kind about, «добудь 16 листвы»: the answer names the part he holds —
     * «У меня 15 из 16 — рядом листвы не вижу», never a bare refusal that hides the fifteen in his bag.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aBagShortOfTheCountAnswersWhatItHolds(GameTestHelper h) {
        ground(h);
        CompanionEntity c = withTheOwnersTools(h);
        c.setItem(4, new ItemStack(Items.OAK_LEAVES, 15));
        h.assertTrue(CompanionLeaves.nearest(c, h.getLevel()) == null, "A tree was found in the open");
        String command = CompanionLocalCommands.route("добудь 16 листвы", "Олег");
        h.assertTrue(command != null, "«добудь 16 листвы» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-short", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(said.equals("У меня 15 из 16 — рядом листвы не вижу."), "The bag's count was hidden: " + said + ", " + report);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 15, "The bag changed: " + count(c, Items.OAK_LEAVES) + ", " + report);
        });
    }

    /**
     * Sixteen oak leaves in the bag beside a spruce: «добудь листву» is answered by what he carries («Готово: «Oak Leaves» у
     * меня, 16 шт.»), and not one spruce leaf is cut for it — the bag is preferred to the tree that stands by.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aBagOfAnotherSpeciesIsNotCutFromTheTree(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (BlockPos cell : List.of(trunk.above(2).west(), trunk.above(2).east(), trunk.above(3).north(), trunk.above(3).south()))
            h.setBlock(cell, leaves);
        CompanionEntity c = withTheOwnersTools(h);
        c.setItem(4, new ItemStack(Items.OAK_LEAVES, 16));
        h.assertTrue("minecraft:spruce_leaves".equals(CompanionLeaves.nearest(c, h.getLevel())), "The spruce is not the tree beside him");
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-other-species", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(said.equals("Готово: «Oak Leaves» у меня, 16 шт."), "Not the bag he carries: " + said + ", " + report);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 16, "The oak leaves in the bag changed: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) == 0, "The spruce was cut for a bag that met the order: " + count(c, Items.SPRUCE_LEAVES) + ", " + report);
            h.assertTrue(damage(c, Items.SHEARS) == 0, "The shears cut a spruce for nothing: " + damage(c, Items.SHEARS) + ", " + report);
        });
    }

    /**
     * A crown above even the longest column of his soil (the owner, 17.11): the leaves are there, right over him, but six steps
     * of soil do not bring them within a swing. The answer names the height — «До листвы не достану даже со столбом», not
     * «Рядом листвы не вижу» over leaves that stand overhead.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aCrownAboveAnyColumnNamesItsHeight(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 9; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 10; dy <= 11; dy++)
            h.setBlock(trunk.offset(dx, dy, dz), leaves);
        CompanionEntity c = withTheOwnersTools(h);
        c.setItem(4, new ItemStack(Items.DIRT, 8));
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-too-high", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(said.equals("До листвы не достану даже со столбом."), "The height was not named: " + said + ", " + report);
            h.assertTrue(!said.contains("Рядом листвы не вижу"), "The short line came back under a crown overhead: " + said);
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) == 0, "A leaf was gathered out of reach: " + report);
            h.assertTrue(supports[0] == 0 && dirtAboveGround(h) == 0, "A column was built to a crown it cannot reach");
            h.assertTrue(count(c, Items.DIRT) == 8, "The soil was spent on a crown out of reach: " + count(c, Items.DIRT));
        });
    }

    /**
     * The count of a leaf order is what the bag holds after it, not only what this gather cut: ten carried and six cut answer
     * for sixteen of sixteen; a bag that already met the order answers with the check's own line, nothing cut.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void theCountOfALeafOrderIsWhatTheBagHoldsAfterIt(GameTestHelper h) {
        List<PlannedAction> plan = List.of(new PlannedAction("obtain_item", 0, 16, CompanionLeaves.FAMILY));
        String said = dev.aicompanion.client.CompanionDialogue.summarize(plan,
                "Собрал: «Дубовая листва», 6 шт.\nГотово: «Дубовая листва» у меня, 16 шт.");
        h.assertTrue(said.equals("Собрал 16 листьев."), "Only this gather was counted: " + said);
        String met = dev.aicompanion.client.CompanionDialogue.summarize(plan, "Готово: «Дубовая листва» у меня, 16 шт.");
        h.assertTrue(met.equals("Готово: «Дубовая листва» у меня, 16 шт."), "A refusal for a bag that had them: " + met);
        h.succeed();
    }

    /** The equip step of a plan says nothing; a standalone «надень ножницы» still answers. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void theEquipStepOfAPlanIsSilentWhileAStandaloneEquipAnswers(GameTestHelper h) {
        List<PlannedAction> plan = List.of(new PlannedAction("obtain_item", 0, 16, CompanionLeaves.FAMILY),
                new PlannedAction("equip_item", 0, 1, "minecraft:shears", "mainhand"));
        String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, "Собрал: «Дубовая листва», 16 шт.\nВзял в правую руку: Shears.");
        h.assertTrue(said.equals("Собрал 16 листьев."), "The equip line was spoken in a plan: " + said);
        String alone = dev.aicompanion.client.CompanionDialogue.summarize(List.of(new PlannedAction("equip_item", 0, 1, "minecraft:shears", "mainhand")),
                "Уже экипирован: Shears.");
        h.assertTrue(alone.equals("Уже экипирован: Shears."), "A standalone equip does not answer: " + alone);
        h.succeed();
    }

    /**
     * A part given up and then the shears taken in hand (the owner's plan «добудь ведро, надень ножницы»): nothing else came of
     * the plan, so the hand that took the tool is the one thing that happened and it is said — not swallowed as bookkeeping.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void theToolTakenAfterAGivenUpPartIsSaid(GameTestHelper h) {
        List<PlannedAction> plan = List.of(new PlannedAction("obtain_item", 0, 1, "minecraft:bucket"),
                new PlannedAction("equip_item", 0, 1, "minecraft:shears", "mainhand"));
        String said = dev.aicompanion.client.CompanionDialogue.summarize(plan,
                dev.aicompanion.client.CompanionDialogue.PARTIAL_PREFIX + "«Ведро»: Рядом не вижу, где взять «Ведро».\nВзял в правую руку: Shears.");
        h.assertTrue(said.contains("Взял в правую руку: Shears"), "The tool taken in hand was lost: " + said);
        h.assertTrue(said.contains("Ведро"), "The given-up part was lost: " + said);
        // A plan that really did other work keeps its equip step silent: the work is the answer.
        List<PlannedAction> worked = List.of(new PlannedAction("craft_item", 0, 1, "minecraft:bucket"),
                new PlannedAction("equip_item", 0, 1, "minecraft:shears", "mainhand"));
        String quiet = dev.aicompanion.client.CompanionDialogue.summarize(worked, "Готово, сделал.\nВзял в правую руку: Shears.");
        h.assertTrue(!quiet.contains("Взял в правую руку"), "The equip step spoke over the work: " + quiet);
        h.succeed();
    }

    /** A leaf order reports the count it really gathered; «Остальное сделал» is no part of it. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aLeafOrderReportsTheCountItGathered(GameTestHelper h) {
        String said = dev.aicompanion.client.CompanionDialogue.summarize(List.of(new PlannedAction("obtain_item", 0, 16, CompanionLeaves.FAMILY)),
                "Собрал: «Дубовая листва», 16 шт.");
        h.assertTrue(said.equals("Собрал 16 листьев.") && !said.contains("Остальное сделал"), said);
        List<PlannedAction> plan = List.of(new PlannedAction("obtain_item", 0, 16, CompanionLeaves.FAMILY),
                new PlannedAction("equip_item", 0, 1, "minecraft:shears", "mainhand"));
        String refused = dev.aicompanion.client.CompanionDialogue.summarize(plan,
                dev.aicompanion.client.CompanionDialogue.PARTIAL_PREFIX + "«Листва»: Рядом листвы не вижу.\nУже экипирован: Shears.");
        h.assertTrue(refused.equals("Рядом листвы не вижу."), "The old prefix came back: " + refused);
        h.succeed();
    }

    /**
     * Eight oak and eight spruce leaves in the bag beside an oak, «добудь 16 листвы»: a leaf order counts every kind it
     * carries, so the bag meets it — sixteen of sixteen, not eight of sixteen — and not one leaf of the tree is cut for it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aBagOfSeveralKindsMeetsTheOrderAsOneCount(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 2; dy <= 3; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) h.setBlock(cell, leaves);
        }
        CompanionEntity c = withTheOwnersTools(h);
        c.setItem(4, new ItemStack(Items.OAK_LEAVES, 8));
        c.setItem(5, new ItemStack(Items.SPRUCE_LEAVES, 8));
        h.assertTrue(CompanionLeaves.carried(c) == 16, "The bag is not sixteen leaves: " + CompanionLeaves.carried(c));
        h.assertTrue("minecraft:oak_leaves".equals(CompanionLeaves.nearest(c, h.getLevel())), "The oak is not the tree beside him");
        String command = CompanionLocalCommands.route("добудь 16 листвы", "Олег");
        h.assertTrue(command != null, "«добудь 16 листвы» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-mixed", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(said.equals("Готово: «Листва» у меня, 16 шт."), "The bag's leaves were not counted together: " + said + ", " + report);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 8 && count(c, Items.SPRUCE_LEAVES) == 8, "The bag changed: " + report);
            h.assertTrue(damage(c, Items.SHEARS) == 0, "The tree was cut for a bag that met the order: " + damage(c, Items.SHEARS) + ", " + report);
        });
    }

    /**
     * Fifteen oak leaves in the bag and a spruce beside him, «добудь 16 листвы»: the bag is one leaf short, so he cuts the
     * spruce — one leaf of the tree he can come at, not sixteen — and answers sixteen of sixteen, not «Собрал 5 из 16» over
     * a bag that holds twenty leaves once the spruce gives its own.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aBagShortOfTheCountCutsTheTreeBesideIt(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (BlockPos cell : List.of(trunk.above(2).west(), trunk.above(2).east(), trunk.above(3).north(), trunk.above(3).south(),
                trunk.above(3).west(), trunk.above(3).east()))
            h.setBlock(cell, leaves);
        CompanionEntity c = withTheOwnersTools(h);
        c.setItem(4, new ItemStack(Items.OAK_LEAVES, 15));
        h.assertTrue("minecraft:spruce_leaves".equals(CompanionLeaves.nearest(c, h.getLevel())), "The spruce is not the tree beside him");
        String command = CompanionLocalCommands.route("добудь 16 листвы", "Олег");
        h.assertTrue(command != null, "«добудь 16 листвы» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-short-spruce", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) == 1, "The spruce was not cut for the one missing leaf: " + count(c, Items.SPRUCE_LEAVES) + ", " + report);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 15, "The oak in the bag changed: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(said.equals("Собрал 16 листьев."), "The bag's oak was not counted: " + said + ", " + report);
            h.assertTrue(damage(c, Items.SHEARS) == 1, "The shears cut more than the one leaf: " + damage(c, Items.SHEARS) + ", " + report);
        });
    }

    /**
     * A counted refusal inside a longer plan is one sentence: «Не вышло: «Листва» — у меня 15 из 16, рядом листвы не вижу.»
     * The part and the reason are not joined by a second dash; the soil's refusal reads the same way.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aRefusalInsideALongerPlanIsOneSentence(GameTestHelper h) {
        List<PlannedAction> plan = List.of(new PlannedAction("obtain_item", 0, 16, CompanionLeaves.FAMILY),
                new PlannedAction("gather_wood", 0, 2, ""));
        String counted = dev.aicompanion.client.CompanionDialogue.summarize(plan,
                dev.aicompanion.client.CompanionDialogue.PARTIAL_PREFIX + "«Листва»: У меня 15 из 16 — рядом листвы не вижу.\nСобрал: «Дубовое бревно», 2 шт.");
        h.assertTrue(counted.equals("Остальное сделал. Не вышло: «Листва» — у меня 15 из 16, рядом листвы не вижу."),
                "The refusal is not one sentence: " + counted);
        String soil = dev.aicompanion.client.CompanionDialogue.summarize(plan,
                dev.aicompanion.client.CompanionDialogue.PARTIAL_PREFIX + "«Листва»: До листвы не достану — дай земли.\nСобрал: «Дубовое бревно», 2 шт.");
        h.assertTrue(soil.equals("Остальное сделал. Не вышло: «Листва» — до листвы не достану, дай земли."),
                "The soil's refusal is not one sentence: " + soil);
        h.succeed();
    }

    /**
     * The owner's crown and an empty bag of soil (18.11): no dirt carried, the flat ground about him is dirt under the grass.
     * «Добудь листву» digs the soil he needs, builds up on it, cuts the crown and comes back down — «До листвы не достану —
     * дай земли» is not said over soil he can dig himself.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aCrownWithoutSoilInTheBagIsDugForAndClimbed(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 5; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        var leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 5; dy <= 6; dy++)
            h.setBlock(trunk.offset(dx, dy, dz), leaves);
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue(count(c, Items.DIRT) == 0, "The bag already holds soil: " + count(c, Items.DIRT));
        double groundY = c.getY();
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-dig", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(!said.contains("дай земли") && !report.contains("дай земли"), "Soil was asked for instead of dug: " + said + ", " + report);
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) >= 16, "Not sixteen leaves: " + count(c, Items.SPRUCE_LEAVES) + ", " + report);
            h.assertTrue(count(c, Items.DIRT) > 0, "He dug no soil for the crown: " + count(c, Items.DIRT) + ", " + report);
            h.assertTrue(supports[0] > 0, "He never built up on the soil he dug: " + report);
            h.assertTrue(c.onGround() && Math.abs(c.getY() - groundY) < 0.6, "He did not come back down: y " + c.getY() + ", " + report);
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "A fall off the column hurt him: " + c.getHealth() + " of " + c.getMaxHealth() + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && dirtAboveGround(h) == 0, "The column was left standing: " + report);
        });
    }

    /**
     * The owner's hedge (18.11): the leaves beside him were set down by the player (persistent), and no tree's own crown is
     * there to meet the order. They are leaves all the same and a shears gives their own item: the order cuts them and counts
     * them — not «Рядом листвы не вижу» over leaves that stand right beside him.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void playerPlacedLeavesBesideHimAreCut(GameTestHelper h) {
        ground(h);
        BlockPos hedgeTrunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(hedgeTrunk.above(up), Blocks.OAK_LOG);
        var hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 7);
        List<BlockPos> beside = List.of(hedgeTrunk.above().west(), hedgeTrunk.above().east(), hedgeTrunk.above().north(),
                hedgeTrunk.above(2).west(), hedgeTrunk.above(2).east(), hedgeTrunk.above(2).north());
        for (BlockPos cell : beside) h.setBlock(cell, hedge);
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue("minecraft:oak_leaves".equals(CompanionLeaves.nearest(c, h.getLevel())),
                "The player's own leaves are not seen: " + CompanionLeaves.nearest(c, h.getLevel()));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":6}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 6, "The player's leaves were not cut: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(damage(c, Items.SHEARS) == 6, "The shears did not cut six leaves: " + damage(c, Items.SHEARS) + ", " + report);
        });
    }

    /** Natural leaves are cut before the player's own: the hedge stands while the oak beside it can meet the order. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void naturalLeavesArePreferredToThePlayersHedge(GameTestHelper h) {
        ground(h);
        BlockPos hedgeTrunk = new BlockPos(18, GROUND + 1, 16);
        var hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 7);
        List<BlockPos> beside = List.of(hedgeTrunk.above().west(), hedgeTrunk.above().east(), hedgeTrunk.above().north(),
                hedgeTrunk.above(2).west(), hedgeTrunk.above(2).east(), hedgeTrunk.above(2).north());
        for (BlockPos cell : beside) h.setBlock(cell, hedge);
        BlockPos oak = new BlockPos(22, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(oak.above(up), Blocks.OAK_LOG);
        var natural = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 2; dy <= 3; dy++) {
            BlockPos cell = oak.offset(dx, dy, dz);
            if (!cell.equals(oak.above(dy))) h.setBlock(cell, natural);
        }
        CompanionEntity c = withTheOwnersTools(h);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":6}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) >= 6, "Not six leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            for (BlockPos cell : beside) h.assertBlockPresent(Blocks.OAK_LEAVES, cell);
        });
    }

    /**
     * The soil is sized for the leaves the gather really comes at (19.11): a leaf the player set down is no crown of its
     * species, so «добудь 16 oak leaves» with only a set-down oak hedge about and a spruce crown six blocks up names the spruce
     * the gather comes at, digs the soil for that crown and climbs it — not zero soil for the oak, which would leave the crown
     * where it hangs.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void theSoilIsSizedForTheSpeciesTheGatherComesAt(GameTestHelper h) {
        ground(h);
        // The player's own oak leaves beside him: within his reach from the ground, and no oak crown of a tree.
        BlockPos hedgeTrunk = new BlockPos(14, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(hedgeTrunk.above(up), Blocks.OAK_LOG);
        var hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 7);
        List<BlockPos> beside = List.of(hedgeTrunk.above().west(), hedgeTrunk.above().east(), hedgeTrunk.above().north(),
                hedgeTrunk.above(2).west(), hedgeTrunk.above(2).east(), hedgeTrunk.above(2).north());
        for (BlockPos cell : beside) h.setBlock(cell, hedge);
        // A spruce crown six and seven blocks up: no stand cell under it, the soil about him is dirt.
        BlockPos trunk = new BlockPos(20, GROUND + 1, 16);
        for (int up = 0; up < 6; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        var crown = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 6; dy <= 7; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) h.setBlock(cell, crown);
        }
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue("minecraft:spruce_leaves".equals(CompanionLeaves.leafTarget(c, h.getLevel(), "minecraft:oak_leaves", 16)),
                "The set-down oak was named over the spruce the gather comes at: " + CompanionLeaves.leafTarget(c, h.getLevel(), "minecraft:oak_leaves", 16));
        h.assertTrue(CompanionLeaves.soilToDig(c, h.getLevel(), "minecraft:spruce_leaves", 16) > 0,
                "The spruce crown six blocks up asks no soil");
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":16}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) >= 16, "Not sixteen spruce leaves: " + count(c, Items.SPRUCE_LEAVES) + ", " + report);
            h.assertTrue(count(c, Items.DIRT) > 0, "He dug no soil for the spruce crown: " + report);
            for (BlockPos cell : beside) h.assertBlockPresent(Blocks.OAK_LEAVES, cell);
        });
    }

    /**
     * A small crown is no stand-in for a hedge that meets the order (19.11): six spruce leaves six blocks up, above every
     * stand cell, and sixteen oak leaves the player set down beside him. «Добудь 8 oak leaves» names the oak — the leaves that
     * can give the order — and gathers them from the hedge: no column is raised for the six spruce leaves, no soil dug.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aSmallCrownDoesNotOutbidTheHedgeThatMeetsTheOrder(GameTestHelper h) {
        ground(h);
        CompanionEntity c = withTheOwnersTools(h);
        // Sixteen oak leaves the player set down beside him, within his reach from the ground.
        var hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 7);
        for (int dx = 0; dx < 4; dx++) for (int dz = 0; dz < 4; dz++)
            placedByPlayer(h, c, new BlockPos(12 + dx, GROUND + 1, 12 + dz), hedge);
        // A small natural spruce crown: six leaves six blocks up, above every stand cell.
        BlockPos trunk = new BlockPos(20, GROUND + 1, 16);
        for (int up = 0; up < 6; up++) h.setBlock(trunk.above(up), Blocks.SPRUCE_LOG);
        var spruce = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (BlockPos cell : List.of(trunk.above(5).west(), trunk.above(5).east(), trunk.above(5).north(), trunk.above(5).south(),
                trunk.above(6).west(), trunk.above(6).east())) h.setBlock(cell, spruce);
        h.assertTrue("minecraft:oak_leaves".equals(CompanionLeaves.leafTarget(c, h.getLevel(), "minecraft:oak_leaves", 8)),
                "The small spruce crown was named over the hedge that meets the order: "
                        + CompanionLeaves.leafTarget(c, h.getLevel(), "minecraft:oak_leaves", 8));
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":8}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(CompanionLeaves.carried(c) == 8, "Not eight leaves: " + CompanionLeaves.carried(c) + ", " + report);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 8, "The hedge that meets the order was not the one taken: "
                    + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(supports[0] == 0 && count(c, Items.DIRT) == 0, "A column was raised for the small crown: supports "
                    + supports[0] + ", dirt " + count(c, Items.DIRT) + ", " + report);
        });
    }

    /**
     * The owner's modest crown (18.11): six and seven blocks up on flat ground, no stand cell under it, and no soil in the
     * bag — the ground about him is dirt. A four-step column reaches it, so «Добудь листву» digs the soil, builds the column
     * and cuts the crown: «Рядом листвы не вижу» is for no leaves at all, not for a modest crown over him. A leaf the column
     * cleared out of its way comes into the bag too, so the count is sixteen or more.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aCrownSixOrSevenBlocksUpIsClimbed(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 6; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 6; dy <= 7; dy++)
            h.setBlock(trunk.offset(dx, dy, dz), leaves);
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue(count(c, Items.DIRT) == 0, "The bag already holds soil: " + count(c, Items.DIRT));
        double groundY = c.getY();
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        String command = CompanionLocalCommands.route("добудь листву", "Олег");
        h.assertTrue(command != null, "«добудь листву» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        h.assertTrue("minecraft:oak_leaves".equals(CompanionLeaves.resolve(c, h.getLevel(), CompanionLeaves.FAMILY)),
                "The crown six blocks up was not named: " + CompanionLeaves.resolve(c, h.getLevel(), CompanionLeaves.FAMILY));
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-six-up", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(!said.contains("Рядом листвы не вижу") && !said.contains("дай земли"), "A crown six blocks up was refused: " + said + ", " + report);
            h.assertTrue(count(c, Items.OAK_LEAVES) >= 16, "Not sixteen leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(supports[0] >= 4, "He did not build the four-step column the crown asks: supports " + supports[0] + ", " + report);
            h.assertTrue(count(c, Items.DIRT) > 0, "He dug no soil for the column: " + count(c, Items.DIRT) + ", " + report);
            h.assertTrue(c.onGround() && Math.abs(c.getY() - groundY) < 0.6, "He did not come back down: y " + c.getY() + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && dirtAboveGround(h) == 0, "The column was left standing: " + report);
        });
    }

    /**
     * The owner's hand (18.11): the shears are seen in his hand from the first swing of a crown to the last leaf, and the
     * tool he held before comes back only when the crown is done — not after every leaf.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void theShearsStayInHisHandForTheWholeCrown(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        List<BlockPos> crown = List.of(trunk.above().west(), trunk.above().east(), trunk.above().north(),
                trunk.above(2).west(), trunk.above(2).east(), trunk.above(2).north());
        for (BlockPos cell : crown) h.setBlock(cell, leaves);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        CompanionEntity c = withTheOwnersTools(h);
        int[] midCrown = {0}, withShears = {0}, withoutShears = {0};
        h.onEachTick(() -> {
            int left = 0;
            for (BlockPos cell : crown) if (h.getBlockState(cell).is(Blocks.OAK_LEAVES)) left++;
            if (left > 0 && left < crown.size()) {
                midCrown[0]++;
                if (c.getMainHandItem().is(Items.SHEARS)) withShears[0]++;
                else withoutShears[0]++;
            }
        });
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":6}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 6, "Not six leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(midCrown[0] >= 3, "The crown was cut in too few ticks to see: " + midCrown[0] + ", " + report);
            h.assertTrue(withShears[0] >= 3, "The shears were not in his hand while the crown was cut: " + withShears[0] + " of " + midCrown[0] + ", " + report);
            h.assertTrue(withoutShears[0] == 0, "The previous tool was in his hand between two leaves: " + withoutShears[0] + " of " + midCrown[0] + ", " + report);
            h.assertTrue(c.getMainHandItem().is(Items.STONE_PICKAXE), "The hand he had before the crown did not come back: " + c.getMainHandItem() + ", " + report);
        });
    }

    /**
     * The owner's dirt stair (18.11): a crown seven and eight blocks up, a stair of dirt the player built to it, and no soil
     * in the bag. The stair is a stand like any other — he walks up it and cuts the crown from there, and no column of his is
     * raised nor a block of soil dug: «Вижу, где взять «Oak Leaves», но подойти не смог» was for a stair he could walk up.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void aCrownOverThePlayersStairIsCutFromItWithoutAColumn(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 8; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            h.setBlock(trunk.offset(dx, 8, dz), leaves);
        // The player's stair of dirt: one step up and one forward, its top beside the crown and level with its foot.
        for (int step = 0; step < 9; step++) h.setBlock(new BlockPos(18, GROUND + 1 + step, 5 + step), Blocks.DIRT);
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue(count(c, Items.DIRT) == 0, "The bag already holds soil: " + count(c, Items.DIRT));
        double groundY = c.getY();
        double[] highest = {0};
        int[] supports = {0};
        h.onEachTick(() -> {
            highest[0] = Math.max(highest[0], c.getY());
            supports[0] = Math.max(supports[0], c.getTemporarySupportCount());
        });
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":8}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 8, "Not eight leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(supports[0] == 0, "A column was built beside a stair he could walk up: " + supports[0] + ", " + report);
            h.assertTrue(count(c, Items.DIRT) == 0, "He dug soil for a column beside the stair: " + count(c, Items.DIRT) + ", " + report);
            h.assertTrue(highest[0] >= groundY + 8, "He never climbed the stair: highest y " + highest[0] + ", " + report);
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "A fall off the stair hurt him: " + c.getHealth() + " of " + c.getMaxHealth() + ", " + report);
            h.assertTrue(c.onGround() && Math.abs(c.getY() - groundY) < 0.6, "He did not come back down: y " + c.getY() + ", " + report);
        });
    }

    /**
     * The owner's own geometry (18.11): a tree eight blocks tall on flat ground, the dirt stair he built up to it — its top
     * two blocks below the crown, three from the trunk, the highest cell under the crown itself. The crown is cut from the
     * stair, no column raised and no soil dug: «Вижу, где взять «Oak Leaves», но подойти не смог» was for a stair that stands
     * right under the crown. The walk back down the stair leaves him on his feet: the step he stands over is stepped into
     * before the drop to the ground (19.11: a step skipped at the stair's edge was a fall of four, and a heart).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aCrownTwoBelowTheStairTopIsCutFromTheStair(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 8; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        List<BlockPos> crown = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 7; dy <= 8; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) { h.setBlock(cell, leaves); crown.add(cell); }
        }
        stair(h, 18, 9, GROUND + 1, 5);
        int stairDirt = dirtAboveGround(h);
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue(count(c, Items.DIRT) == 0, "The bag already holds soil: " + count(c, Items.DIRT));
        double groundY = c.getY();
        double[] highest = {0};
        int[] supports = {0};
        h.onEachTick(() -> {
            highest[0] = Math.max(highest[0], c.getY());
            supports[0] = Math.max(supports[0], c.getTemporarySupportCount());
        });
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":8}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 8, "Not eight leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(leavesLeft(h, crown) == crown.size() - 8, "Not eight of the crown were cut: " + (crown.size() - leavesLeft(h, crown)) + " of " + crown.size() + ", " + report);
            h.assertTrue(supports[0] == 0, "A column was built beside a stair under the crown: " + supports[0] + ", " + report);
            h.assertTrue(count(c, Items.DIRT) == 0, "He dug soil for a column beside the stair: " + count(c, Items.DIRT) + ", " + report);
            h.assertTrue(dirtAboveGround(h) == stairDirt, "Dirt of his own was left above the ground: " + dirtAboveGround(h) + " of " + stairDirt + ", " + report);
            h.assertTrue(highest[0] >= groundY + 5, "He never climbed the stair: highest y " + highest[0] + ", " + report);
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "A fall off the stair hurt him: " + c.getHealth() + " of " + c.getMaxHealth() + ", " + report);
            h.assertTrue(c.onGround() && Math.abs(c.getY() - groundY) < 1.6, "He did not come back down: y " + c.getY() + ", " + report);
        });
    }

    /**
     * The same tree and stair on a slope: the crown hangs over ground two blocks higher than the ground he walks from, and the
     * stair climbs the hillside. He walks up the stair and cuts the crown from it — no column, no soil dug, and the walk back
     * down the slope leaves him on his feet.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void aCrownOverAStairOnASlopeIsCutFromTheStair(GameTestHelper h) {
        slope(h);
        BlockPos trunk = new BlockPos(22, GROUND + 3, 16);
        for (int up = 0; up < 8; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        List<BlockPos> crown = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 7; dy <= 8; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) { h.setBlock(cell, leaves); crown.add(cell); }
        }
        stair(h, 22, 9, GROUND + 3, 6);
        int stairDirt = dirtAboveGround(h);
        CompanionEntity c = withTheOwnersTools(h);
        double groundY = c.getY();
        double[] highest = {0};
        int[] supports = {0};
        h.onEachTick(() -> {
            highest[0] = Math.max(highest[0], c.getY());
            supports[0] = Math.max(supports[0], c.getTemporarySupportCount());
        });
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":8}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 8, "Not eight leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(leavesLeft(h, crown) == crown.size() - 8, "Not eight of the crown were cut: " + (crown.size() - leavesLeft(h, crown)) + " of " + crown.size() + ", " + report);
            h.assertTrue(supports[0] == 0, "A column was built beside the stair on the slope: " + supports[0] + ", " + report);
            h.assertTrue(dirtAboveGround(h) == stairDirt, "Dirt of his own was left on the slope: " + dirtAboveGround(h) + " of " + stairDirt + ", " + report);
            h.assertTrue(highest[0] >= groundY + 5, "He never climbed the stair: highest y " + highest[0] + ", " + report);
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "A fall off the slope hurt him: " + c.getHealth() + " of " + c.getMaxHealth() + ", " + report);
            h.assertTrue(c.onGround(), "He did not come down: y " + c.getY() + ", " + report);
        });
    }

    /**
     * A stair five or six blocks from the trunk: no cell of it is within a swing of any leaf, so it is no stand at all. The
     * crown is then above every stand cell: he walks to the foot of a column beside the trunk, digs the soil for it, builds up
     * and cuts the crown from the top — the stair is walked past, not climbed, and the column comes back down with him.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void aStairOutOfReachLeavesTheCrownToHisOwnColumn(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 8; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 7; dy <= 8; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) h.setBlock(cell, leaves);
        }
        // The stair stands six blocks off along x, its top two below the crown: nothing of it is within the reach of a leaf.
        stair(h, 12, 11, GROUND + 1, 5);
        int stairDirt = dirtAboveGround(h);
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue(count(c, Items.DIRT) == 0, "The bag already holds soil: " + count(c, Items.DIRT));
        double groundY = c.getY();
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":8}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) >= 8, "Not eight leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(supports[0] > 0, "No column was built for a crown the stair cannot serve: " + supports[0] + ", " + report);
            // The soil of the column comes back to him: six blocks were dug for it and every one is in the bag, five of them
            // raised as supports — nothing of it left on the ground.
            h.assertTrue(count(c, Items.DIRT) == 6, "The soil of the column did not come back: " + count(c, Items.DIRT) + " of 6 dug, supports " + supports[0] + ", " + report);
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "A fall off the column hurt him: " + c.getHealth() + " of " + c.getMaxHealth() + ", " + report);
            h.assertTrue(c.onGround() && Math.abs(c.getY() - groundY) < 0.6, "He did not come back down: y " + c.getY() + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && dirtAboveGround(h) == stairDirt, "The column was left standing: " + dirtAboveGround(h) + " of " + stairDirt + ", " + report);
        });
    }

    /**
     * A flat crown six blocks over the ground with nothing to stand on but its own leaves: a cell on top of a leaf is no walk
     * for him (nothing gets up there without a column), and the ground is too far below. The crown is his all the same: he
     * builds a column beside the trunk and cuts the plate from its top — the leaves of the crown top are not given up.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void aCrownOnlyItsOwnTopCouldServeIsCutFromAColumn(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 6; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        List<BlockPos> plate = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos cell = trunk.offset(dx, 6, dz);
            if (!cell.equals(trunk.above(6))) { h.setBlock(cell, leaves); plate.add(cell); }
        }
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue(count(c, Items.DIRT) == 0, "The bag already holds soil: " + count(c, Items.DIRT));
        double groundY = c.getY();
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":8}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 8, "Not eight leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(leavesLeft(h, plate) == plate.size() - 8, "Not eight of the plate were cut: " + (plate.size() - leavesLeft(h, plate)) + " of " + plate.size() + ", " + report);
            h.assertTrue(supports[0] >= 4, "He did not build the column the plate asks: supports " + supports[0] + ", " + report);
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "A fall off the column hurt him: " + c.getHealth() + " of " + c.getMaxHealth() + ", " + report);
            h.assertTrue(c.onGround() && Math.abs(c.getY() - groundY) < 0.6, "He did not come back down: y " + c.getY() + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && dirtAboveGround(h) == 0, "The column was left standing: " + report);
        });
    }

    /**
     * The owner's complaint (18.11) in one world: a tree eight blocks tall with the dirt stair under its crown, and a bed of
     * leaves the player set down on the ground beside him. The tree's own leaves come first: eight of them are cut
     * from the stair and the bed is left standing — not the other way round, where only the leaves on the ground came into the
     * bag while the crown stood and he said «Вижу, где взять …, но подойти не смог».
     */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void theTreesLeavesComeBeforeTheOnesSetDownOnTheGround(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 8; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        List<BlockPos> crown = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 7; dy <= 8; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) { h.setBlock(cell, leaves); crown.add(cell); }
        }
        stair(h, 18, 9, GROUND + 1, 5);
        int stairDirt = dirtAboveGround(h);
        CompanionEntity c = withTheOwnersTools(h);
        c.setSearchRadius(24);
        var setDown = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 7);
        List<BlockPos> bed = new java.util.ArrayList<>();
        for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) {
            BlockPos cell = new BlockPos(10 + dx, GROUND + 1, 12 + dz);
            placedByPlayer(h, c, cell, setDown);
            bed.add(cell);
        }
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":8}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 8, "Not eight leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(leavesLeft(h, crown) == crown.size() - 8, "Not eight of the tree were cut: " + (crown.size() - leavesLeft(h, crown)) + " of " + crown.size() + ", " + report);
            for (BlockPos cell : bed) h.assertBlockPresent(Blocks.OAK_LEAVES, cell);
            h.assertTrue(dirtAboveGround(h) == stairDirt, "Dirt of his own was left above the ground: " + dirtAboveGround(h) + " of " + stairDirt + ", " + report);
            h.assertTrue(!report.contains("подойти не смог"), "The crown was given up: " + report);
        });
    }

    /** How many of these cells still hold a leaf. */
    private static int leavesLeft(GameTestHelper h, List<BlockPos> cells) {
        int left = 0;
        for (BlockPos cell : cells) if (h.getBlockState(cell).is(Blocks.OAK_LEAVES)) left++;
        return left;
    }

    /**
     * The owner's stair he cannot climb (18.11): a crown seven blocks up, a stair of dirt whose steps are two blocks high — its
     * top cell is within a swing of the leaves, yet no walk gets up there. A stand he cannot walk to is no stand:
     * the soil for a column is dug before the gather, he builds up beside the trunk, cuts the crown from the top and comes back
     * down — «Вижу, где взять «Oak Leaves», но подойти не смог» is not said over soil the ground holds.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void aStairHeCannotClimbLeavesTheCrownToHisOwnColumn(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 8; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        List<BlockPos> crown = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos cell = trunk.offset(dx, 7, dz);
            if (!cell.equals(trunk.above(7))) { h.setBlock(cell, leaves); crown.add(cell); }
        }
        // Steps of two: the top cell at (18, GROUND+7, 14) is within the reach of the crown, and the ground is two below it.
        for (int step = 0; step < 3; step++) h.setBlock(new BlockPos(18, GROUND + 2 + step * 2, 12 + step), Blocks.DIRT);
        int stairDirt = dirtAboveGround(h);
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue(count(c, Items.DIRT) == 0, "The bag already holds soil: " + count(c, Items.DIRT));
        double groundY = c.getY();
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":8}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) >= 8, "Not eight leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(supports[0] > 0, "No column was built for a stair he cannot climb: " + supports[0] + ", " + report);
            h.assertTrue(count(c, Items.DIRT) > 0, "He dug no soil for the column: " + count(c, Items.DIRT) + ", " + report);
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "A fall off the column hurt him: " + c.getHealth() + " of " + c.getMaxHealth() + ", " + report);
            h.assertTrue(c.onGround() && Math.abs(c.getY() - groundY) < 0.6, "He did not come back down: y " + c.getY() + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && dirtAboveGround(h) == stairDirt, "The column was left standing: " + dirtAboveGround(h) + " of " + stairDirt + ", " + report);
        });
    }

    /** A stair of dirt the player built: one step up and one forward from {@code yStart}, {@code steps} of them. */
    private static void stair(GameTestHelper h, int x, int zStart, int yStart, int steps) {
        for (int step = 0; step < steps; step++) h.setBlock(new BlockPos(x, yStart + step, zStart + step), Blocks.DIRT);
    }

    /** A hillside: flat ground to x=13, a block higher to x=19, two higher from x=20 on — the crown's ground is above his. */
    private static void slope(GameTestHelper h) {
        ground(h);
        for (int x = 14; x < 32; x++) for (int z = 1; z < 31; z++) {
            int top = GROUND + (x >= 20 ? 2 : x >= 14 ? 1 : 0);
            for (int y = GROUND + 1; y <= top; y++) h.setBlock(new BlockPos(x, y, z), y == top ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        }
    }

    /**
     * «Два стака листвы» (the owner, 18.11): the local number parser reads a stack as 64, so «два стака» is 128 and «полтора
     * стака» 96; the order stays local and the plan takes it in steps of a stack each, the bag checked against the whole count.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aLeafOrderOfSeveralStacksIsParsedAndPlannedLocally(GameTestHelper h) {
        h.assertTrue(CompanionNumbers.parse("добудь 128 листвы").count() == 128, "«128»: " + CompanionNumbers.parse("добудь 128 листвы").count());
        h.assertTrue(CompanionNumbers.parse("добудь два стака листвы").count() == 128, "«два стака»: " + CompanionNumbers.parse("добудь два стака листвы").count());
        h.assertTrue(CompanionNumbers.parse("добудь полтора стака листвы").count() == 96, "«полтора стака»: " + CompanionNumbers.parse("добудь полтора стака листвы").count());
        for (String phrase : List.of("добудь 128 листвы", "добудь два стака листвы")) {
            String command = CompanionLocalCommands.route(phrase, "Олег");
            h.assertTrue(command != null, "«" + phrase + "» went to the model");
            List<PlannedAction> plan = CompanionLocalCommands.actions(command);
            h.assertTrue(plan.getFirst().type().equals("obtain_item") && plan.getFirst().count() == 64
                    && CompanionLeaves.isFamily(plan.getFirst().item()), "«" + phrase + "» is no first step of 64 leaves: " + plan);
            h.assertTrue(plan.stream().filter(step -> "obtain_item".equals(step.type())).mapToInt(PlannedAction::count).max().orElse(0) == 128,
                    "«" + phrase + "» is no order of 128 leaves: " + plan);
        }
        // The model's own plan of 128 leaves is taken in the same steps, not refused for a count over a stack.
        List<PlannedAction> decoded = CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString(
                "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":128}]"));
        h.assertTrue(decoded.size() == 2 && decoded.get(0).count() == 64 && decoded.get(1).count() == 128,
                "The codec did not carry 128 in steps of a stack: " + decoded);
        // The chain the server plans splits the order into steps of a stack each.
        var chain = CompanionObtain.plan(h.getLevel(), List.of(new ItemStack(Items.SHEARS)), false, "minecraft:oak_leaves", 128, thing -> true);
        h.assertTrue(!chain.refused(), "The chain of 128 leaves was refused: " + chain.refusal());
        h.assertTrue(chain.steps().size() == 2 && chain.steps().stream().allMatch(step -> step.type().equals("gather_block") && step.count() == 64),
                "The chain is not two steps of a stack: " + chain);
        h.succeed();
    }

    /**
     * «Добудь 500 листвы» (19.11): a leaf order of several stacks is no reason to go to the model or to refuse it — the local
     * route reads it, the codec carries it, and both split it into steps of a stack each (the last one the rest of the count).
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aLeafOrderOfFiveHundredIsTakenInStacksNotRefused(GameTestHelper h) {
        String command = CompanionLocalCommands.route("добудь 500 листвы", "Олег");
        h.assertTrue(command != null, "«добудь 500 листвы» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        List<PlannedAction> obtains = plan.stream().filter(step -> "obtain_item".equals(step.type())).toList();
        h.assertTrue(obtains.size() == 8 && obtains.getLast().count() == 500 && CompanionLeaves.isFamily(obtains.getLast().item()),
                "The local plan is not eight steps ending at 500 leaves: " + plan);
        h.assertTrue(obtains.stream().allMatch(step -> step.count() % 64 == 0 || step == obtains.getLast()),
                "A step of the local plan is not a stack: " + plan);
        // The model's own plan of 500 leaves is carried the same way instead of being refused for a count over a stack.
        List<PlannedAction> decoded = CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString(
                "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":500}]"));
        h.assertTrue(decoded.size() == 8 && decoded.getLast().count() == 500, "The codec refused 500: " + decoded);
        // The chain the server plans splits it into steps of a stack each, the last of the rest.
        var chain = CompanionObtain.plan(h.getLevel(), List.of(new ItemStack(Items.SHEARS)), false, "minecraft:oak_leaves", 500, thing -> true);
        h.assertTrue(!chain.refused(), "The chain of 500 leaves was refused: " + chain.refusal());
        h.assertTrue(chain.steps().size() == 8 && chain.steps().getLast().count() == 52
                && chain.steps().stream().allMatch(step -> step.type().equals("gather_block")),
                "The chain is not eight steps of a stack: " + chain);
        h.succeed();
    }

    /**
     * The widest leaf order (19.11): 768 leaves is the most CompanionWork takes, and with another step beside it the split has
     * to fit the plan's twelve steps instead of refusing the whole plan. Eleven steps then carry twelve stacks: the last one
     * the rest, 128. Alone it keeps its steps of a stack; 769 is beyond the widest leaf order and stays refused.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aLeafOrderOf768FitsThePlanBesideAnotherStep(GameTestHelper h) {
        List<PlannedAction> decoded = CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString(
                "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":768},{\"action\":\"equip_item\",\"item\":\"minecraft:shears\"}]"));
        h.assertTrue(decoded.size() == CompanionPlanCodec.MAX_STEPS,
                "768 leaves and the shears do not take exactly the plan's steps: " + decoded);
        List<PlannedAction> obtains = decoded.stream().filter(step -> "obtain_item".equals(step.type())).toList();
        // Eleven steps for twelve stacks: the last one carries the rest, 128, as the codec's own split says.
        h.assertTrue(obtains.size() == 11 && obtains.getFirst().count() == 64 && obtains.get(9).count() == 640
                && obtains.getLast().count() == 768 && decoded.getLast().type().equals("equip_item"),
                "The order of 768 and the shears were not carried as eleven steps of a stack and the rest: " + decoded);
        int grew = 0;
        for (PlannedAction step : obtains) {
            h.assertTrue(step.count() > grew, "A step of the order does not grow: " + obtains);
            grew = step.count();
        }
        // Alone, the same order still goes in steps of a stack each.
        List<PlannedAction> alone = CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString(
                "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":768}]"));
        h.assertTrue(alone.size() == CompanionPlanCodec.MAX_STEPS && alone.getLast().count() == 768,
                "768 leaves alone are not twelve steps ending at the order: " + alone);
        boolean refused = false;
        try {
            CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString(
                    "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":769}]"));
        } catch (RuntimeException expected) { refused = true; }
        h.assertTrue(refused, "769 leaves were taken: the widest leaf order is 768");
        h.succeed();
    }

    /**
     * The owner's tree with a stair and his own leaves (18.11): the tree's crown hangs on a shelf three blocks over stone, no
     * soil about to build a column, so the leaf he first goes for is one he cannot walk to; the leaves he set down on the
     * ground about him are inside a site of his (a chest stands there) and were left for the site's rule. The order falls
     * through to those leaves and gathers them instead of «Вижу, где взять …, но подойти не смог».
     */
    @GameTest(template = "tall_tree", timeoutTicks = 6000)
    public static void leavesThePlayerSetDownAreGatheredWhenTheTreeCannotBeReached(GameTestHelper h) {
        stoneGround(h);
        CompanionEntity c = withTheOwnersTools(h);
        c.setSearchRadius(24);
        // A chest the player set down: a site of his reaches over the leaves he lays on the ground.
        placedByPlayer(h, c, new BlockPos(5, GROUND + 1, 5), Blocks.CHEST.defaultBlockState());
        // A leaf of a tree he cannot come at: a stone shelf three blocks over the ground, and no dirt to build a column.
        BlockPos shelf = new BlockPos(29, GROUND + 3, 16);
        h.setBlock(shelf, Blocks.STONE);
        h.setBlock(shelf.above(3), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        // The leaves the player set down on the ground beside him: a leaf that does not fade, on a site of his.
        var setDown = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 7);
        for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++)
            placedByPlayer(h, c, new BlockPos(12 + dx, GROUND + 1, 12 + dz), setDown);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":8}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 8, "The player's leaves were not gathered: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(!report.contains("подойти не смог") && !report.contains("Рядом не вижу"), "A refusal over the leaves beside him: " + report);
            h.assertTrue(damage(c, Items.SHEARS) == 8, "The shears did not cut the eight leaves: " + damage(c, Items.SHEARS) + ", " + report);
        });
    }

    /**
     * The owner's hedge beside a working tree (18.11, 19.11): the leaves he set down lie beside him on a site of his (a chest
     * stands there), and an oak crown six and seven blocks up stands within the same site. A tree is no wall of the player's:
     * its own leaves are cut, up a column of soil, and the hedge is left standing because a natural leaf is reachable for the
     * order — a leaf he set down is cut only when no tree's own is.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void aHedgeBesideAWorkingTreeSurvives(GameTestHelper h) {
        ground(h);
        CompanionEntity c = withTheOwnersTools(h);
        // A chest the player set down: a site of his reaches over the hedge and the oak alike.
        placedByPlayer(h, c, new BlockPos(12, GROUND + 1, 8), Blocks.CHEST.defaultBlockState());
        var hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 7);
        List<BlockPos> beside = List.of(new BlockPos(14, GROUND + 1, 16), new BlockPos(15, GROUND + 1, 16), new BlockPos(14, GROUND + 1, 17),
                new BlockPos(15, GROUND + 1, 17), new BlockPos(14, GROUND + 1, 15), new BlockPos(15, GROUND + 1, 15));
        for (BlockPos cell : beside) placedByPlayer(h, c, cell, hedge);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 6; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 6; dy <= 7; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) h.setBlock(cell, leaves);
        }
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":16}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) >= 16, "Not sixteen leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            for (BlockPos cell : beside) h.assertBlockPresent(Blocks.OAK_LEAVES, cell);
            h.assertTrue(damage(c, Items.SHEARS) >= 16, "The shears did not cut the crown: " + damage(c, Items.SHEARS) + ", " + report);
            h.assertTrue(!report.contains("подойти не смог") && !report.contains("Рядом не вижу"), "The crown was refused: " + report);
        });
    }

    /**
     * The owner's oak out of reach beside a spruce (19.11): the oak's crown hangs over a stair of two-high steps on stone, so
     * the stair is a stand no walk reaches and the stone gives no soil for a column; the spruce beside it has leaves he can
     * stand by. The order falls through to the spruce's leaves (any #minecraft:leaves) and gathers sixteen of them instead of
     * «Вижу, где взять «Oak Leaves», но подойти не смог».
     */
    @GameTest(template = "tall_tree", timeoutTicks = 8000)
    public static void anOakOutOfReachFallsThroughToTheSpruceBesideIt(GameTestHelper h) {
        stoneGround(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 8; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var oak = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos cell = trunk.offset(dx, 7, dz);
            if (!cell.equals(trunk.above(7))) h.setBlock(cell, oak);
        }
        // Steps of two, of stone: the top cell is within a swing of the crown, yet no walk gets up there, and the stone ground
        // gives no soil for a column either.
        for (int step = 0; step < 3; step++) h.setBlock(new BlockPos(18, GROUND + 2 + step * 2, 12 + step), Blocks.STONE);
        BlockPos spruce = new BlockPos(24, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(spruce.above(up), Blocks.SPRUCE_LOG);
        var spruceLeaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 2; dy <= 3; dy++) {
            BlockPos cell = spruce.offset(dx, dy, dz);
            if (!cell.equals(spruce.above(dy))) h.setBlock(cell, spruceLeaves);
        }
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue("minecraft:oak_leaves".equals(CompanionLeaves.nearest(c, h.getLevel())),
                "The oak is not the tree the order comes at: " + CompanionLeaves.nearest(c, h.getLevel()));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":16}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) == 16, "The spruce beside the unreachable oak was not taken: "
                    + count(c, Items.SPRUCE_LEAVES) + ", " + report);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 0, "A leaf of the unreachable oak came into the bag: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(!report.contains("подойти не смог") && !report.contains("Рядом не вижу"), "The leaves were refused: " + report);
        });
    }

    /**
     * The owner's stair that serves only part of a crown (19.11): a crown of a flat plate five blocks over the ground, a dirt
     * stair whose top is within a swing of its near rows — the far rows stand above every stand cell and only a column reaches
     * them. Sixteen leaves are more than the stair serves, so the soil for the far ones is dug before the gather (three steps
     * of it) and he climbs for them, instead of passing them over with «до остального не добрался».
     */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void aStairThatServesPartOfACrownIsDugForTheFarLeaves(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 7; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos cell = trunk.offset(dx, 7, dz);
            if (!cell.equals(trunk.above(7))) h.setBlock(cell, leaves);
        }
        // The stair's top is level with the crown's foot and within reach of its near rows (z 14..16), not of the far ones.
        stair(h, 18, 9, GROUND + 1, 5);
        int stairDirt = dirtAboveGround(h);
        CompanionEntity c = withTheOwnersTools(h);
        h.assertTrue(count(c, Items.DIRT) == 0, "The bag already holds soil: " + count(c, Items.DIRT));
        double groundY = c.getY();
        int[] supports = {0};
        h.onEachTick(() -> supports[0] = Math.max(supports[0], c.getTemporarySupportCount()));
        String command = CompanionLocalCommands.route("добудь 16 листвы", "Олег");
        h.assertTrue(command != null, "«добудь 16 листвы» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var port = new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-far", () -> false);
        // The soil for the leaves the stair cannot serve is sized before the gather, not only for the leaf it comes at first.
        var expansion = port.expand(plan.getFirst()).join();
        h.assertTrue(expansion.steps().getFirst().type().equals("gather_dirt"),
                "The soil for the far leaves was not planned before the gather: " + expansion.steps());
        var future = CompanionPlanRunner.run(plan, port);
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 16, "Not sixteen leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(supports[0] > 0, "No column was built for the far leaves the stair cannot serve: " + supports[0] + ", " + report);
            h.assertTrue(count(c, Items.DIRT) > 0, "He dug no soil for the far leaves: " + count(c, Items.DIRT) + ", " + report);
            h.assertTrue(!report.contains("не добрался") && !report.contains("больше рядом нет"), "The far leaves were passed over: " + report);
            h.assertTrue(c.onGround() && Math.abs(c.getY() - groundY) < 0.6, "He did not come back down: y " + c.getY() + ", " + report);
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "A fall off the column hurt him: " + c.getHealth() + " of " + c.getMaxHealth() + ", " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0 && dirtAboveGround(h) == stairDirt, "The column was left standing: " + dirtAboveGround(h) + " of " + stairDirt + ", " + report);
        });
    }

    /**
     * The owner's hand mid-gather (19.11): a crown eight leaves big above every stand cell, and six leaves he set down beside
     * him. The crown is cut from his column, and the bed on the ground after it: the column comes down between them, and the
     * shears stay in his hand all the while — the hand he had before the leaves comes back only when the gather ends.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 10000)
    public static void theShearsStayInHisHandWhileTheColumnComesDown(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(18, GROUND + 1, 16);
        for (int up = 0; up < 5; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        List<BlockPos> crown = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos cell = trunk.offset(dx, 6, dz);
            if (!cell.equals(trunk.above(6))) { h.setBlock(cell, leaves); crown.add(cell); }
        }
        CompanionEntity c = withTheOwnersTools(h);
        var bed = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 7);
        List<BlockPos> onGround = List.of(new BlockPos(14, GROUND + 1, 16), new BlockPos(15, GROUND + 1, 16), new BlockPos(14, GROUND + 1, 17),
                new BlockPos(15, GROUND + 1, 17), new BlockPos(14, GROUND + 1, 15), new BlockPos(15, GROUND + 1, 15));
        for (BlockPos cell : onGround) placedByPlayer(h, c, cell, bed);
        int[] cut = {0}, midGather = {0}, withoutShears = {0};
        h.onEachTick(() -> {
            int crownLeft = 0, bedLeft = 0;
            for (BlockPos cell : crown) if (h.getBlockState(cell).is(Blocks.OAK_LEAVES)) crownLeft++;
            for (BlockPos cell : onGround) if (h.getBlockState(cell).is(Blocks.OAK_LEAVES)) bedLeft++;
            cut[0] = crown.size() - crownLeft;
            // The descent is over and the gather is not: the shears are what cuts the next leaf, not the hand they replaced.
            if (cut[0] > 0 && bedLeft > 0 && c.getTemporarySupportCount() == 0 && !c.isRecoveringSupports() && c.onGround()) {
                midGather[0]++;
                if (!c.getMainHandItem().is(Items.SHEARS)) withoutShears[0]++;
            }
        });
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":14}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 14, "Not fourteen leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(midGather[0] >= 3, "The gather was over before the column came down: " + midGather[0] + ", " + report);
            h.assertTrue(withoutShears[0] == 0, "The shears were put away while the column came down: " + withoutShears[0] + " of " + midGather[0] + ", " + report);
            h.assertTrue(c.getMainHandItem().is(Items.STONE_PICKAXE) || c.getMainHandItem().isEmpty(),
                    "The shears were left in his hand after the gather: " + c.getMainHandItem() + ", bag " + c.getStoredItemsSummary() + ", " + report);
            h.assertTrue(count(c, Items.SHEARS) == 1, "The shears were lost: bag " + c.getStoredItemsSummary() + ", " + report);
        });
    }

    /**
     * «Добудь 128 листвы» really gathered (19.11): a wide oak crown two to four blocks over the ground, every leaf of it within
     * his reach from the ground, and the order taken in steps of a stack each. The bag holds 128 leaves at the end, not a plan
     * that says so.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 16000)
    public static void aBigLeafOrderIsReallyGatheredInStacks(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(16, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) for (int dy = 2; dy <= 4; dy++) {
            BlockPos cell = trunk.offset(dx, dy, dz);
            if (!cell.equals(trunk.above(dy))) h.setBlock(cell, leaves);
        }
        CompanionEntity c = withTheOwnersTools(h);
        c.setSearchRadius(24);
        String command = CompanionLocalCommands.route("добудь 128 листвы", "Олег");
        h.assertTrue(command != null, "«добудь 128 листвы» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-128", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            String said = dev.aicompanion.client.CompanionDialogue.summarize(plan, report);
            h.assertTrue(count(c, Items.OAK_LEAVES) == 128, "Not 128 leaves in the bag: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(said.equals("Собрал 128 листьев."), "The count was not spoken: " + said + ", " + report);
            h.assertTrue(damage(c, Items.SHEARS) == 128, "The shears did not cut 128 leaves: " + damage(c, Items.SHEARS) + ", " + report);
        });
    }

    /**
     * «Добудь 384 листвы» (19.11): six steps of a stack each, really gathered from a wide crown — the whole order in the bag,
     * not a refusal for a count over a few stacks.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 30000)
    public static void aLeafOrderOfSixStacksIsReallyGathered(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(16, GROUND + 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        // A plate two blocks over the ground, every leaf of it within a swing of the ground under it.
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
            BlockPos cell = trunk.offset(dx, 2, dz);
            if (!cell.equals(trunk.above(2))) h.setBlock(cell, leaves);
        }
        CompanionEntity c = withTheOwnersTools(h);
        c.setItem(4, new ItemStack(Items.SHEARS));
        c.setItem(5, new ItemStack(Items.SHEARS));
        c.setSearchRadius(24);
        String command = CompanionLocalCommands.route("добудь 384 листвы", "Олег");
        h.assertTrue(command != null, "«добудь 384 листвы» went to the model");
        List<PlannedAction> plan = CompanionLocalCommands.actions(command);
        var future = CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves-384", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 384, "Not 384 leaves in the bag: " + count(c, Items.OAK_LEAVES) + ", " + report);
        });
    }

    /** A block set down by the player, as the game tells it (CompanionPlayerBuilds learns his buildings so). */
    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos cell, net.minecraft.world.level.block.state.BlockState state) {
        h.setBlock(cell, state);
        var player = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(cell)),
                Blocks.GRASS_BLOCK.defaultBlockState(), player));
    }

    /** The owner's bag (in game 16.11): the stone pickaxe in his hand, an iron pickaxe, an axe, shears and a shovel in the bag. */
    private static CompanionEntity withTheOwnersTools(GameTestHelper h) {
        CompanionEntity c = spawn(h, 12, 16);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        c.setItem(1, new ItemStack(Items.IRON_AXE));
        c.setItem(2, new ItemStack(Items.SHEARS));
        c.setItem(3, new ItemStack(Items.IRON_SHOVEL));
        return c;
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            if (y <= GROUND) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            else if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
            else h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    /** The same shelf of stone: a world with no block that gives dirt, so a column has no soil to be dug for it. */
    private static void stoneGround(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            if (y <= GROUND) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : Blocks.STONE);
            else if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
            else h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    /** The soil he stood on, over the ground of the test: what a column of his leaves behind if it is not taken back. */
    private static int dirtAboveGround(GameTestHelper h) {
        int count = 0;
        for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) for (int y = GROUND + 1; y <= GROUND + 12; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) count++;
        return count;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-leaves", () -> false));
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x - 2, GROUND + 1, z - 2));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        // The least radius: what grows in the tests beside this one, beyond its walls, would be out of his way.
        c.setSearchRadius(16);
        return c;
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }

    /** The wear on the stack of this item, in the bag or in either hand; -1 when he has none. */
    private static int damage(CompanionEntity c, Item item) {
        int found = -1;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) found = Math.max(found, c.getItem(slot).getDamageValue());
        for (EquipmentSlot hand : EquipmentSlot.values())
            if (hand.getType() == EquipmentSlot.Type.HAND && c.getItemBySlot(hand).is(item)) found = Math.max(found, c.getItemBySlot(hand).getDamageValue());
        return found;
    }
}

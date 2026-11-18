package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
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
            h.assertTrue(count(c, Items.SPRUCE_LEAVES) == 16, "Not sixteen leaves: " + count(c, Items.SPRUCE_LEAVES) + ", " + report);
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
     * The owner's tall crown and nothing to stand on: no soil in the bag, no other tree's leaves about. He cannot build up, so
     * the order is refused naming the soil he lacks — not «Рядом листвы не вижу» for a crown that stands right over him —
     * nothing gathered, no soil invented.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aCrownAboveReachWithoutSoilNamesTheSoil(GameTestHelper h) {
        ground(h);
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
     * speak over the chain's reason — the reason itself is the answer.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aGivenUpObtainDoesNotWearTheShears(GameTestHelper h) {
        ground(h);
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

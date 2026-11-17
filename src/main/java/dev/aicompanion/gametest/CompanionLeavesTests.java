package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
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

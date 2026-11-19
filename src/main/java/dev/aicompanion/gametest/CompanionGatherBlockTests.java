package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionObtain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * the player 12.11: he should get everything a player gets. What a block gives when it is simply broken (sugar cane, flowers, clay,
 * cactus…) is one skill from the game's loot tables: found within his search radius, broken, picked up; the root of a cane is left
 * to grow and the player's flower bed is left alone.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGatherBlockTests {
    private static final int GROUND = 5;

    /** Plan only: what a broken block gives is picked; what grows here is chosen; stone, crops and saplings are not this skill's. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void whatABlockGivesIsPlannedAsPicking(GameTestHelper h) {
        var level = h.getLevel();
        var paper = CompanionObtain.plan(level, List.of(), true, "minecraft:paper", 3, item -> true);
        h.assertTrue(paper.steps().equals(List.of(new CompanionObtain.Step("gather_block", "minecraft:sugar_cane", 3), new CompanionObtain.Step("craft_item", "minecraft:paper", 3))), "Paper: " + paper);
        var brick = CompanionObtain.plan(level, List.of(), true, "minecraft:brick", 2, item -> true);
        h.assertTrue(!brick.refused() && brick.steps().contains(new CompanionObtain.Step("gather_block", "minecraft:clay_ball", 2))
                && brick.steps().getLast().equals(new CompanionObtain.Step("smelt_items", "minecraft:brick", 2)), "Brick: " + brick);
        for (String item : List.of("minecraft:cactus", "minecraft:pumpkin", "minecraft:melon_slice", "minecraft:brown_mushroom", "minecraft:bamboo", "minecraft:dandelion")) {
            var plan = CompanionObtain.plan(level, List.of(), false, item, 2, thing -> true);
            h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("gather_block", item, 2))), item + ": " + plan);
        }
        // Snow gives snowballs to a shovel only: any shovel of his, else a wooden one is made first, not found missing at the block.
        var shovel = CompanionObtain.plan(level, List.of(new ItemStack(Items.STONE_SHOVEL)), false, "minecraft:snowball", 2, thing -> true);
        h.assertTrue(shovel.steps().equals(List.of(new CompanionObtain.Step("gather_block", "minecraft:snowball", 2))), "Snowball with a shovel: " + shovel);
        var bare = CompanionObtain.plan(level, List.of(), false, "minecraft:snowball", 2, thing -> true);
        h.assertTrue(!bare.refused() && bare.steps().getLast().equals(new CompanionObtain.Step("gather_block", "minecraft:snowball", 2))
                && bare.steps().contains(new CompanionObtain.Step("craft_item", "minecraft:wooden_shovel", 1)), "Snowball with no shovel: " + bare);
        // Chance drops, the mine's stone, crops and saplings, someone's lamp, egg or trap: no block is broken for them.
        for (String item : List.of("minecraft:cobblestone", "minecraft:wheat_seeds", "minecraft:oak_sapling", "minecraft:apple",
                "minecraft:glowstone_dust", "minecraft:prismarine_crystals", "minecraft:dragon_egg", "minecraft:sniffer_egg", "minecraft:ochre_froglight", "minecraft:stripped_bamboo_block", "minecraft:string")) {
            var plan = CompanionObtain.plan(level, List.of(), false, item, 1, thing -> true);
            h.assertTrue(plan.steps().stream().noneMatch(step -> step.type().equals("gather_block") && step.item().equals(item)), item + " is not picked: " + plan);
        }
        // What has a recipe is still crafted, not taken from where it stands.
        var bench = CompanionObtain.plan(level, List.of(), false, "minecraft:crafting_table", 1, item -> true);
        h.assertTrue(!bench.refused() && bench.steps().getLast().type().equals("craft_item") && bench.steps().stream().noneMatch(step -> step.type().equals("gather_block")), "Bench: " + bench);
        // Red dye: of the flowers that give it, the one that grows here.
        var dye = CompanionObtain.plan(level, List.of(), false, "minecraft:red_dye", 2, item -> item == Items.ROSE_BUSH);
        h.assertTrue(dye.steps().equals(List.of(new CompanionObtain.Step("gather_block", "minecraft:rose_bush", 1), new CompanionObtain.Step("craft_item", "minecraft:red_dye", 2))), "Red dye by a rose bush: " + dye);
        var none = CompanionObtain.plan(level, List.of(), false, "minecraft:red_dye", 2, item -> false);
        h.assertTrue(none.refused() && none.steps().isEmpty() && none.refusal().contains("Рядом не вижу") && none.refusal().contains("Rose Bush") && none.refusal().contains("Poppy"), "Red dye with no flower about: " + none);
        // Blue dye: the lapis he carries before a walk to a flower, the flower before a mine.
        var carried = CompanionObtain.plan(level, List.of(new ItemStack(Items.LAPIS_LAZULI)), false, "minecraft:blue_dye", 1, item -> true);
        h.assertTrue(carried.steps().equals(List.of(new CompanionObtain.Step("craft_item", "minecraft:blue_dye", 1))), "Blue dye with lapis in the bag: " + carried);
        var flower = CompanionObtain.plan(level, List.of(), false, "minecraft:blue_dye", 1, item -> true);
        h.assertTrue(flower.steps().equals(List.of(new CompanionObtain.Step("gather_block", "minecraft:cornflower", 1), new CompanionObtain.Step("craft_item", "minecraft:blue_dye", 1))), "Blue dye by a cornflower: " + flower);
        // Wool stands in the world as a block too, but it is made: never taken from where it stands.
        var wool = CompanionObtain.plan(level, List.of(), false, "minecraft:gray_wool", 1, item -> true);
        h.assertTrue(wool.steps().stream().noneMatch(step -> step.type().equals("gather_block") && step.item().endsWith("_wool")), "Gray wool: " + wool);
        h.succeed();
    }

    /** «Добудь 3 бумаги» by two canes three high: each is cut above its root and comes down whole, both roots stand, the paper is in his bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void sugarCaneIsCutAboveItsRootAndMadeIntoPaper(GameTestHelper h) {
        ground(h);
        List<BlockPos> roots = List.of(new BlockPos(20, GROUND + 1, 16), new BlockPos(20, GROUND + 1, 20));
        for (BlockPos root : roots) {
            h.setBlock(root.below().east(), Blocks.WATER);
            for (int up = 0; up < 3; up++) h.setBlock(root.above(up), Blocks.SUGAR_CANE);
        }
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:paper\",\"count\":3}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.PAPER) == 3, "Not three paper: " + count(c, Items.PAPER) + ", " + future.join().report());
            for (BlockPos root : roots) h.assertBlockPresent(Blocks.SUGAR_CANE, root);
        });
    }

    /**
     * Clay in the ground gives its four balls, the clay under water nearer to him is left; the poppies by the player's chest are his, so
     * the red dye is made of the rose bush farther off. The bush stands in a hollow, its top half the nearer one: it is taken by its foot.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void clayIsDugAndThePlayersFlowersAreLeftForTheWildOnes(GameTestHelper h) {
        ground(h);
        h.setBlock(new BlockPos(18, GROUND, 12), Blocks.CLAY);
        BlockPos wet = new BlockPos(15, GROUND - 1, 14);
        h.setBlock(wet, Blocks.CLAY);
        h.setBlock(wet.above(), Blocks.WATER);
        CompanionEntity c = spawn(h, 14, 16);
        c.setItem(0, new ItemStack(Items.WOODEN_SHOVEL));
        // A chest of his on the surface makes it a building of his (CompanionPlayerBuilds.HOME_SCORE): the flower bed by it is part of it.
        placedByPlayer(h, c, new BlockPos(3, GROUND + 1, 3), Blocks.CHEST);
        List<BlockPos> bed = List.of(new BlockPos(4, GROUND + 1, 5), new BlockPos(5, GROUND + 1, 5));
        for (BlockPos cell : bed) placedByPlayer(h, c, cell, Blocks.POPPY);
        BlockPos bush = new BlockPos(24, GROUND, 24);
        h.setBlock(bush, Blocks.AIR);
        h.setBlock(bush, Blocks.ROSE_BUSH);
        h.setBlock(bush.above(), Blocks.ROSE_BUSH.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:clay_ball\",\"count\":4},{\"action\":\"obtain_item\",\"item\":\"minecraft:red_dye\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.CLAY_BALL) == 4, "Not four clay balls: " + count(c, Items.CLAY_BALL) + ", " + report);
            h.assertTrue(count(c, Items.RED_DYE) == 2, "Not two red dye: " + count(c, Items.RED_DYE) + ", " + report);
            for (BlockPos cell : bed) h.assertBlockPresent(Blocks.POPPY, cell);
            h.assertBlockNotPresent(Blocks.ROSE_BUSH, bush);
            h.assertBlockPresent(Blocks.CLAY, wet);
        });
    }

    /** Plan only: what a block gives to shears alone (leaves, a vine, a cobweb, grass) is picked with shears, made first if he has none. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void whatShearsTakeIsPlannedWithShears(GameTestHelper h) {
        var level = h.getLevel();
        for (String item : List.of("minecraft:oak_leaves", "minecraft:cherry_leaves", "minecraft:vine", "minecraft:cobweb", "minecraft:short_grass", "minecraft:fern", "minecraft:dead_bush", "minecraft:hanging_roots")) {
            var plan = CompanionObtain.plan(level, List.of(new ItemStack(Items.SHEARS)), false, item, 2, thing -> true);
            h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("gather_block", item, 2))), item + " with shears: " + plan);
        }
        var made = CompanionObtain.plan(level, List.of(new ItemStack(Items.IRON_INGOT, 2)), false, "minecraft:vine", 2, thing -> true);
        h.assertTrue(made.steps().equals(List.of(new CompanionObtain.Step("craft_item", "minecraft:shears", 1), new CompanionObtain.Step("gather_block", "minecraft:vine", 2))), "Vine with two ingots: " + made);
        // No shears and no iron: the whole chain makes them, there is no other way to a leaf.
        var chain = CompanionObtain.plan(level, List.of(), false, "minecraft:oak_leaves", 3, thing -> true);
        int craft = chain.steps().indexOf(new CompanionObtain.Step("craft_item", "minecraft:shears", 1));
        h.assertTrue(!chain.refused() && craft >= 0 && chain.steps().getLast().equals(new CompanionObtain.Step("gather_block", "minecraft:oak_leaves", 3))
                && chain.steps().stream().anyMatch(step -> step.type().equals("mine_ore")), "Leaves from nothing: " + chain);
        // None about him: said at once, no shears are made for it.
        var none = CompanionObtain.plan(level, List.of(new ItemStack(Items.IRON_INGOT, 2)), false, "minecraft:oak_leaves", 3, thing -> false);
        h.assertTrue(none.refused() && none.steps().isEmpty() && none.refusal().contains("Рядом не вижу"), "Leaves with none about: " + none);
        // The composter is fed with what a hand picks: no leaves are shorn for it.
        var meal = CompanionObtain.plan(level, List.of(new ItemStack(Items.SHEARS)), false, "minecraft:bone_meal", 1, thing -> thing == Items.OAK_LEAVES || thing == Items.FERN, thing -> false);
        h.assertTrue(meal.steps().stream().noneMatch(step -> step.type().equals("gather_block")), "Bone meal by leaves only: " + meal);
        h.succeed();
    }

    /**
     * «Добудь 3 листвы и 3 лианы» with shears in his other hand: the leaves of the tree and the vines that hang from a log are
     * cut (the vines from the lower end, or the ones under a cut fall with nothing) and carried as themselves, the shears wear,
     * and the leaf block someone set down (it does not fade) is left where it stands.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void leavesAndAVineAreCutWithShears(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(20, GROUND + 1, 16);
        List<BlockPos> crown = List.of(trunk.above().west(), trunk.above().east(), trunk.above().north(), trunk.above(2).west());
        for (BlockPos cell : crown) h.setBlock(cell, Blocks.OAK_LEAVES);
        // The trunk after the leaves: they count their way to it and do not fade.
        for (int up = 0; up < 3; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        // Held by a log at its top only: the two below hang from it.
        BlockPos vine = new BlockPos(24, GROUND + 1, 21);
        h.setBlock(vine.above(2).north(), Blocks.OAK_LOG);
        for (int up = 2; up >= 0; up--) h.setBlock(vine.above(up), Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.NORTH, true));
        BlockPos hedge = new BlockPos(14, GROUND + 1, 16);
        h.setBlock(hedge, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        CompanionEntity c = spawn(h, 12, 16);
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHEARS));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":3},{\"action\":\"obtain_item\",\"item\":\"minecraft:vine\",\"count\":3}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.OAK_LEAVES) == 3, "Not three leaves: " + count(c, Items.OAK_LEAVES) + ", " + report);
            h.assertTrue(count(c, Items.VINE) == 3, "Not three vines: " + count(c, Items.VINE) + ", " + report);
            h.assertBlockPresent(Blocks.OAK_LEAVES, hedge);
            h.assertBlockNotPresent(Blocks.VINE, vine);
            int worn = 0;
            for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.SHEARS)) worn = c.getItem(slot).getDamageValue();
            if (c.getMainHandItem().is(Items.SHEARS)) worn = c.getMainHandItem().getDamageValue();
            h.assertTrue(worn == 6, "Shears worn by " + worn + ", not by the six cuts");
        });
    }

    /** A crown high over the ground is out of his reach: said at once, and the two ingots he carries are not spent on shears for it. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void leavesOutOfReachAreNotPlanned(GameTestHelper h) {
        ground(h);
        BlockPos trunk = new BlockPos(16, GROUND + 1, 16);
        h.setBlock(trunk.above(5).west(), Blocks.OAK_LEAVES);
        for (int up = 0; up < 6; up++) h.setBlock(trunk.above(up), Blocks.OAK_LOG);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:oak_leaves\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Рядом не вижу") && count(c, Items.IRON_INGOT) == 2 && count(c, Items.SHEARS) == 0, "Leaves out of reach: " + report + ", ingots " + count(c, Items.IRON_INGOT));
        });
    }

    /**
     * Plan only: what grows on the bed of the sea is picked too: kelp, a sea pickle, seagrass (with shears), and a coral block
     * mined with a pickaxe for the dead block it gives. A coral itself and its fan give anything only to Silk Touch: no source.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void whatGrowsUnderWaterIsPlannedAsPicking(GameTestHelper h) {
        var level = h.getLevel();
        for (String item : List.of("minecraft:kelp", "minecraft:sea_pickle")) {
            var plan = CompanionObtain.plan(level, List.of(), false, item, 2, thing -> true);
            h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("gather_block", item, 2))), item + ": " + plan);
        }
        var grass = CompanionObtain.plan(level, List.of(new ItemStack(Items.SHEARS)), false, "minecraft:seagrass", 2, thing -> true);
        h.assertTrue(grass.steps().equals(List.of(new CompanionObtain.Step("gather_block", "minecraft:seagrass", 2))), "Seagrass: " + grass);
        var dried = CompanionObtain.plan(level, List.of(), false, "minecraft:dried_kelp", 2, thing -> true);
        h.assertTrue(dried.steps().getFirst().equals(new CompanionObtain.Step("gather_block", "minecraft:kelp", 2)) && dried.steps().getLast().type().equals("smelt_items"), "Dried kelp: " + dried);
        var dead = CompanionObtain.plan(level, List.of(new ItemStack(Items.WOODEN_PICKAXE)), false, "minecraft:dead_tube_coral_block", 1, thing -> true);
        h.assertTrue(dead.steps().equals(List.of(new CompanionObtain.Step("gather_block", "minecraft:dead_tube_coral_block", 1))), "Dead coral block: " + dead);
        var bare = CompanionObtain.plan(level, List.of(), false, "minecraft:dead_tube_coral_block", 1, thing -> true);
        h.assertTrue(!bare.refused() && bare.steps().getLast().equals(new CompanionObtain.Step("gather_block", "minecraft:dead_tube_coral_block", 1))
                && bare.steps().stream().anyMatch(step -> step.type().equals("craft_item") && step.item().endsWith("_pickaxe")), "Dead coral block with no pickaxe: " + bare);
        for (String item : List.of("minecraft:tube_coral", "minecraft:tube_coral_fan", "minecraft:dead_tube_coral", "minecraft:tube_coral_block")) {
            var plan = CompanionObtain.plan(level, List.of(new ItemStack(Items.WOODEN_PICKAXE)), false, item, 1, thing -> true);
            h.assertTrue(plan.steps().stream().noneMatch(step -> step.type().equals("gather_block") && step.item().equals(item)), item + " is not picked: " + plan);
        }
        h.succeed();
    }

    /**
     * «Добудь 3 ламинарии, траву, огурец и мёртвый коралл» from a pond five deep: he swims over, dives for what is out of his reach
     * from the surface, cuts the kelp above its root (the root stays and grows), shears the seagrass and mines the coral block.
     * The sea pickle lies by a shelf one block under the water: from the shelf it is out of reach, he dives over the pickle itself.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void theBedOfAPondIsPickedUnderWater(GameTestHelper h) {
        ground(h);
        pond(h, 16, 14, 22, 20, GROUND);
        BlockPos root = new BlockPos(18, 1, 16);
        for (int up = 0; up < 3; up++) h.setBlock(root.above(up), Blocks.KELP_PLANT);
        h.setBlock(root.above(3), Blocks.KELP);
        h.setBlock(new BlockPos(20, 1, 18), Blocks.SEAGRASS);
        for (int z = 14; z <= 20; z++) for (int y = 1; y < GROUND; y++) h.setBlock(new BlockPos(16, y, z), Blocks.STONE);
        for (int z = 14; z <= 20; z++) for (int y = 1; y < GROUND; y++) if (z != 16) h.setBlock(new BlockPos(17, y, z), Blocks.STONE);
        h.setBlock(new BlockPos(18, 1, 19), Blocks.SEA_PICKLE);
        h.setBlock(new BlockPos(21, 1, 15), Blocks.TUBE_CORAL_BLOCK);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.SHEARS));
        c.setItem(1, new ItemStack(Items.WOODEN_PICKAXE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:kelp\",\"count\":3},{\"action\":\"obtain_item\",\"item\":\"minecraft:seagrass\",\"count\":1},"
                + "{\"action\":\"obtain_item\",\"item\":\"minecraft:sea_pickle\",\"count\":1},{\"action\":\"obtain_item\",\"item\":\"minecraft:dead_tube_coral_block\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            for (Item item : List.of(Items.SEAGRASS, Items.SEA_PICKLE, Items.DEAD_TUBE_CORAL_BLOCK)) h.assertTrue(count(c, item) == 1, "Not one " + item + ": " + count(c, item) + ", " + report);
            h.assertTrue(count(c, Items.KELP) == 3, "Not three kelp: " + count(c, Items.KELP) + ", " + report);
            h.assertTrue(h.getBlockState(root).is(Blocks.KELP) || h.getBlockState(root).is(Blocks.KELP_PLANT), "The root of the kelp is gone");
        });
    }

    /**
     * No dive under a roof, said at once: kelp in a lake of a cave (air over the water, rock over the air). Nor for a dry kind that
     * stands in water (mangrove roots in a pond, a coral block under another): those are not dived for.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void kelpUnderARoofIsNotPlanned(GameTestHelper h) {
        ground(h);
        pond(h, 16, 14, 22, 20, GROUND - 2);
        for (int x = 16; x <= 22; x++) for (int z = 14; z <= 20; z++) { h.setBlock(new BlockPos(x, GROUND - 1, z), Blocks.AIR); h.setBlock(new BlockPos(x, GROUND, z), Blocks.STONE); }
        BlockPos root = new BlockPos(18, 1, 16);
        h.setBlock(root, Blocks.KELP_PLANT);
        h.setBlock(root.above(), Blocks.KELP);
        pond(h, 6, 22, 10, 26, GROUND);
        h.setBlock(new BlockPos(8, 1, 24), Blocks.MANGROVE_ROOTS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));
        h.setBlock(new BlockPos(7, 1, 23), Blocks.TUBE_CORAL_BLOCK);
        h.setBlock(new BlockPos(7, 2, 23), Blocks.BRAIN_CORAL_BLOCK);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:kelp\",\"count\":1},{\"action\":\"obtain_item\",\"item\":\"minecraft:mangrove_roots\",\"count\":1},"
                + "{\"action\":\"obtain_item\",\"item\":\"minecraft:dead_tube_coral_block\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Рядом не вижу") && count(c, Items.KELP) == 0 && count(c, Items.MANGROVE_ROOTS) == 0 && count(c, Items.DEAD_TUBE_CORAL_BLOCK) == 0, "Under a roof, roots, a covered coral: " + report);
            h.assertBlockPresent(Blocks.TUBE_CORAL_BLOCK, new BlockPos(7, 1, 23));
        });
    }

    /**
     * Plan only: the Nether's own stone is picked with any pickaxe, but only in the Nether: in the overworld (where it is almost always the
     * player's) the plan is the portal's, and with no lit portal it is the portal's refusal. Blackstone is no new source.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void netherStoneIsPlannedAsPicking(GameTestHelper h) {
        var level = h.getLevel();
        var nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        var pick = List.of(new ItemStack(Items.WOODEN_PICKAXE));
        for (String item : List.of("minecraft:netherrack", "minecraft:basalt")) {
            var plan = CompanionObtain.plan(nether, pick, false, item, 8, thing -> true, null, new CompanionObtain.Trip(null, true));
            h.assertTrue(plan.steps().contains(new CompanionObtain.Step("gather_block", item, 8)), item + " in the Nether: " + plan);
            var home = CompanionObtain.plan(level, pick, false, item, 8, thing -> true);
            h.assertTrue(home.refused() && home.refusal().contains("портал") && home.steps().isEmpty(), item + " in the overworld with no portal: " + home);
        }
        var blackstone = CompanionObtain.plan(level, List.of(new ItemStack(Items.WOODEN_PICKAXE)), false, "minecraft:blackstone", 8, thing -> true);
        h.assertTrue(blackstone.steps().stream().noneMatch(step -> step.type().equals("gather_block")), "Blackstone is no source: " + blackstone);
        h.succeed();
    }

    /** In the overworld the Nether's stone is no source: a netherrack block in reach is not taken (a player's portal surround stays whole). */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void netherrackInTheOverworldIsNotGathered(GameTestHelper h) {
        h.setBlock(new BlockPos(3, 2, 3), Blocks.NETHERRACK);
        h.setBlock(new BlockPos(4, 2, 3), Blocks.BASALT);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 2, 2, 2);
        var owner = h.makeMockServerPlayerInLevel();
        c.setOwnerId(owner.getUUID());
        c.setSearchRadius(16);
        c.setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        for (String item : List.of("minecraft:netherrack", "minecraft:basalt")) {
            var started = c.startWork("gather_block", item, 1);
            h.assertTrue(!started.success() && !c.hasActiveWorkOrder(), item + " was taken up as a source: " + started.message());
        }
        h.assertBlockPresent(Blocks.NETHERRACK, new BlockPos(3, 2, 3));
        h.succeed();
    }

    /** In the Nether he takes eight netherrack with a wooden pickaxe; the ninth stands beside lava and is left whole, and he says so. */
    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void netherrackIsMinedInTheNetherButNotBesideLava(GameTestHelper h) {
        netherStone(h, Blocks.NETHERRACK, Items.NETHERRACK, new BlockPos(3008, 150, 3008));
    }

    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void basaltIsMinedInTheNetherButNotBesideLava(GameTestHelper h) {
        netherStone(h, Blocks.BASALT, Items.BASALT, new BlockPos(3008, 150, 3040));
    }

    private static void netherStone(GameTestHelper h, net.minecraft.world.level.block.Block block, Item item, BlockPos base) {
        var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        var chunk = new net.minecraft.world.level.ChunkPos(base);
        nether.setChunkForced(chunk.x, chunk.z, true);
        var area = new net.minecraft.world.phys.AABB(base).inflate(16, 0, 16).expandTowards(0, 12, 0);
        CompanionEntity[] c = {null};
        BlockPos[] kept = {null}, hung = {null};
        h.onEachTick(() -> {
            if (c[0] != null) return;
            if (!nether.areEntitiesLoaded(chunk.toLong())) return;
            nether.getEntities((net.minecraft.world.entity.Entity) null, area, e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
            // Above the Nether's roof, so no natural netherrack is in his search radius: a bedrock box 14 by 14: no wall of it is a source and no lava runs in.
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y <= 9; y++) {
                boolean shell = x == 0 || x == 15 || z == 0 || z == 15 || y == 0 || y == 9;
                nether.setBlockAndUpdate(base.offset(x, y, z), (shell ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState());
            }
            for (int i = 0; i < 8; i++) nether.setBlockAndUpdate(base.offset(3 + i % 4, 1, 4 + i / 4), block.defaultBlockState());
            // The lava is shut in by bedrock on every side but the stone's.
            kept[0] = base.offset(10, 1, 10);
            nether.setBlockAndUpdate(kept[0], block.defaultBlockState());
            nether.setBlockAndUpdate(kept[0].east(), Blocks.LAVA.defaultBlockState());
            for (BlockPos wall : List.of(kept[0].east(2), kept[0].east().north(), kept[0].east().south(), kept[0].east().above())) nether.setBlockAndUpdate(wall, Blocks.BEDROCK.defaultBlockState());
            // One with nothing under it, within his reach: a floor over a cave in the real Nether, he never digs the way under him open.
            hung[0] = base.offset(7, 3, 11);
            nether.setBlockAndUpdate(hung[0], block.defaultBlockState());
            c[0] = new CompanionEntity(AICompanionMod.COMPANION.get(), nether);
            c[0].moveTo(base.getX() + 7.5, base.getY() + 1, base.getZ() + 8.5, 0, 0);
            var owner = h.makeMockServerPlayerInLevel();
            c[0].setOwnerId(owner.getUUID());
            owner.getPersistentData().putUUID("aicompanion:canonical_companion", c[0].getUUID());
            CompanionEvents.getOwnedCompanion(owner);
            c[0].setSearchRadius(16);
            nether.addFreshEntity(c[0]);
            c[0].setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
            var started = c[0].startWork("gather_block", BuiltInRegistries.ITEM.getKey(item).toString(), 9);
            h.assertTrue(started.success(), "Not started: " + started.message());
        });
        h.succeedWhen(() -> {
            h.assertTrue(c[0] != null, "The Nether's entities are not loaded yet");
            String report = c[0].getLastTaskResult();
            h.assertTrue(count(c[0], item) == 8 && report.contains("лавой"), "Eight, and the lava one said: " + count(c[0], item) + ", " + report);
            h.assertTrue(nether.getBlockState(kept[0]).is(block), "The block beside lava was broken");
            h.assertTrue(nether.getBlockState(hung[0]).is(block), "The block with nothing under it was broken");
            c[0].discard();
            nether.setChunkForced(chunk.x, chunk.z, false);
        });
    }

    /**
     * Plan only (the player 17.11): granite, diorite, andesite, tuff, calcite and cobbled deepslate are dug by name where a face of
     * them shows about him, as a player does it; none in sight is said, never fetched by a trip to the Nether for quartz. Quartz in his
     * bag makes them at home, a diorite about him makes andesite, the deepslate about him makes polished deepslate.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void stoneByNameIsDugWhereItShows(GameTestHelper h) {
        var level = h.getLevel();
        var portal = new CompanionObtain.Trip(h.absolutePos(new BlockPos(1, 2, 1)), false);
        var pick = List.of(new ItemStack(Items.STONE_PICKAXE));
        for (String item : List.of("minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff", "minecraft:calcite", "minecraft:cobbled_deepslate")) {
            var plan = CompanionObtain.plan(level, pick, false, item, 6, thing -> true, null, portal);
            h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("gather_block", item, 6))), item + " seen about him: " + plan);
        }
        var bare = CompanionObtain.plan(level, List.of(), false, "minecraft:granite", 2, thing -> true);
        h.assertTrue(!bare.refused() && bare.steps().getLast().equals(new CompanionObtain.Step("gather_block", "minecraft:granite", 2))
                && bare.steps().contains(new CompanionObtain.Step("craft_item", "minecraft:wooden_pickaxe", 1)), "Granite with no pickaxe: " + bare);
        var unseen = CompanionObtain.plan(level, pick, false, "minecraft:granite", 2, thing -> false, null, portal);
        h.assertTrue(unseen.refused() && unseen.steps().isEmpty() && unseen.refusal().contains("Рядом не вижу") && unseen.refusal().contains("пещер"), "Granite not seen: " + unseen);
        var quartz = CompanionObtain.plan(level, List.of(new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.QUARTZ, 4)), false, "minecraft:granite", 1, thing -> false, null, portal);
        h.assertTrue(!quartz.refused() && quartz.steps().getLast().equals(new CompanionObtain.Step("craft_item", "minecraft:granite", 1))
                && quartz.steps().stream().noneMatch(step -> step.type().startsWith("nether_") || step.type().equals("mine_ore")), "Granite of the quartz he carries: " + quartz);
        var andesite = CompanionObtain.plan(level, pick, false, "minecraft:andesite", 2, thing -> thing == Items.DIORITE, null, portal);
        h.assertTrue(!andesite.refused() && andesite.steps().contains(new CompanionObtain.Step("gather_block", "minecraft:diorite", 1))
                && andesite.steps().getLast().equals(new CompanionObtain.Step("craft_item", "minecraft:andesite", 2)), "Andesite of a diorite about him: " + andesite);
        var polished = CompanionObtain.plan(level, pick, true, "minecraft:polished_deepslate", 4, thing -> thing == Items.COBBLED_DEEPSLATE);
        h.assertTrue(polished.steps().equals(List.of(new CompanionObtain.Step("gather_block", "minecraft:cobbled_deepslate", 4), new CompanionObtain.Step("craft_item", "minecraft:polished_deepslate", 4))),
                "Polished deepslate of the deepslate about him, not of any stone: " + polished);
        // Four crafts deep and no pickaxe in the bag: the pickaxe is made too, it is no link of that chain.
        var tiles = CompanionObtain.plan(level, List.of(), true, "minecraft:deepslate_tiles", 4, thing -> thing == Items.COBBLED_DEEPSLATE);
        h.assertTrue(!tiles.refused() && tiles.steps().contains(new CompanionObtain.Step("gather_block", "minecraft:cobbled_deepslate", 4))
                && tiles.steps().contains(new CompanionObtain.Step("craft_item", "minecraft:wooden_pickaxe", 1))
                && tiles.steps().getLast().equals(new CompanionObtain.Step("craft_item", "minecraft:deepslate_tiles", 4)), "Deepslate tiles with no pickaxe: " + tiles);
        // «Добудь 2 диорита» given to gather_stone (in game 17.11: two of plain stone dug, and «готово»): that action digs any stone,
        // so the stone dug by name is obtain_item's, named with its namespace or without; its own kinds and anything else stay with it.
        var given = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"gather_stone\",\"item\":\"minecraft:diorite\",\"count\":2},"
                + "{\"action\":\"gather_stone\",\"item\":\"granite\",\"count\":2},{\"action\":\"gather_stone\",\"item\":\"minecraft:cobblestone\",\"count\":3},"
                + "{\"action\":\"gather_stone\",\"item\":\"minecraft:deepslate\",\"count\":3},{\"action\":\"gather_stone\",\"item\":\"minecraft:furnace\",\"count\":3}]"));
        h.assertTrue(given.size() == 5 && given.get(0).type().equals("obtain_item") && given.get(0).item().equals("minecraft:diorite") && given.get(0).count() == 2
                && given.get(1).type().equals("obtain_item") && given.get(1).item().equals("minecraft:granite")
                && given.subList(2, 5).stream().allMatch(step -> step.type().equals("gather_stone")), "Stone named to gather_stone: " + given);
        // In game 17.11 a longer line was heard as «С этим пока не справился»: with the longest name the ask is said whole.
        String line = "«Колотый глубинный сланец» вижу только у лавы, не трогал: отведи меня к склону или в пещеру.";
        String said = dev.aicompanion.client.CompanionDialogue.summarize(List.of(new dev.aicompanion.client.PlannedAction("gather_block", 0, 2, "minecraft:cobbled_deepslate")),
                "План остановлен: " + line);
        h.assertTrue(said.equals(line), "Said: " + said);
        h.succeed();
    }

    /**
     * «Добудь 2 гранита, потом туф»: two blocks of granite show on the ground farther off. Nearer ones he leaves: one lies under the
     * dirt, one the player set down (his path), one holds a torch, one holds a pond back. The tuff shows only into a sealed hollow
     * under the ground: he says he cannot get to it and asks to be led to a slope or a cave, and it is left whole.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void graniteThatShowsIsDugAndTheHiddenIsLeft(GameTestHelper h) {
        ground(h);
        BlockPos hidden = new BlockPos(14, GROUND - 2, 16);
        h.setBlock(hidden, Blocks.GRANITE);
        List<BlockPos> shown = List.of(new BlockPos(20, GROUND, 16), new BlockPos(20, GROUND + 1, 20));
        for (BlockPos cell : shown) h.setBlock(cell, Blocks.GRANITE);
        BlockPos lit = new BlockPos(16, GROUND + 1, 13), torch = lit.north();
        h.setBlock(lit, Blocks.GRANITE);
        h.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, net.minecraft.core.Direction.NORTH));
        BlockPos shore = new BlockPos(17, GROUND, 19);
        h.setBlock(shore, Blocks.GRANITE);
        h.setBlock(shore.south(), Blocks.WATER);
        BlockPos tuff = new BlockPos(9, GROUND - 2, 12);
        h.setBlock(tuff, Blocks.TUFF);
        for (int y = GROUND - 3; y <= GROUND - 2; y++) h.setBlock(new BlockPos(8, y, 12), Blocks.AIR);
        CompanionEntity c = spawn(h, 12, 16);
        BlockPos path = new BlockPos(15, GROUND, 18);
        placedByPlayer(h, c, path, Blocks.GRANITE);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:granite\",\"count\":2},{\"action\":\"obtain_item\",\"item\":\"minecraft:tuff\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.GRANITE) == 2, "Not two granite: " + count(c, Items.GRANITE) + ", " + report);
            h.assertTrue(count(c, Items.TUFF) == 0 && report.contains("подойти не смог") && report.contains("склону"), "Tuff in a sealed hollow: " + report);
            for (BlockPos cell : shown) h.assertBlockNotPresent(Blocks.GRANITE, cell);
            for (BlockPos cell : List.of(hidden, path, lit, shore)) h.assertBlockPresent(Blocks.GRANITE, cell);
            h.assertBlockPresent(Blocks.WALL_TORCH, torch);
            h.assertBlockPresent(Blocks.TUFF, tuff);
        });
    }

    /**
     * «Добудь туф»: a sealed hollow under the ground nearer to him is lined with twelve faces of tuff, a block of it stands on the
     * ground farther off. The first face he finds no way to marks the whole hollow: its other faces are passed without a search and
     * do not end the order, and the tuff on the ground is dug. The hollow is left whole.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1600)
    public static void tuffBeyondASealedHollowIsReached(GameTestHelper h) {
        ground(h);
        List<BlockPos> lining = new java.util.ArrayList<>();
        for (int x = 6; x <= 8; x++) for (int z = 10; z <= 12; z++) for (int y = GROUND - 3; y <= GROUND - 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int y = GROUND - 3; y <= GROUND - 2; y++) for (int i = 0; i < 3; i++) { lining.add(new BlockPos(5, y, 10 + i)); lining.add(new BlockPos(6 + i, y, 9)); }
        for (BlockPos cell : lining) h.setBlock(cell, Blocks.TUFF);
        BlockPos shown = new BlockPos(22, GROUND + 1, 22);
        h.setBlock(shown, Blocks.TUFF);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:tuff\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.TUFF) == 1, "Not the tuff on the ground: " + count(c, Items.TUFF) + ", " + future.join().report());
            h.assertBlockNotPresent(Blocks.TUFF, shown);
            for (BlockPos cell : lining) h.assertBlockPresent(Blocks.TUFF, cell);
        });
    }

    /** Water from the bedrock up to {@code top} over the given square of the ground. */
    private static void pond(GameTestHelper h, int x0, int z0, int x1, int z1, int top) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = 1; y <= top; y++) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
    }

    /** A block set down by the player, as the game tells it (CompanionPlayerBuilds learns his buildings so). */
    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos cell, net.minecraft.world.level.block.Block block) {
        h.setBlock(cell, block);
        var player = (net.minecraft.server.level.ServerPlayer) h.getLevel().getPlayerByUUID(c.getOwnerId());
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(cell)), Blocks.GRASS_BLOCK.defaultBlockState(), player));
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

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-gather-block", () -> false));
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

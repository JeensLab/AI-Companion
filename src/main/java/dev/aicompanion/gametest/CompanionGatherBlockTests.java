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
        for (String item : List.of("minecraft:cactus", "minecraft:pumpkin", "minecraft:melon_slice", "minecraft:brown_mushroom", "minecraft:snowball", "minecraft:bamboo", "minecraft:dandelion")) {
            var plan = CompanionObtain.plan(level, List.of(), false, item, 2, thing -> true);
            h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("gather_block", item, 2))), item + ": " + plan);
        }
        // Chance drops, the mine's stone, crops and saplings, what grows under water, someone's lamp, egg or trap: no block is broken for them.
        for (String item : List.of("minecraft:tuff", "minecraft:wheat_seeds", "minecraft:oak_sapling", "minecraft:apple", "minecraft:kelp",
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

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
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * the player 12.11: he should get everything a player gets. A ripe field about him is reaped for «добудь пшеницу» and for the
 * bread made of it (none ripe: said before anything starts, nothing is sown and waited for); bone meal comes out of a composter,
 * of the plant scrap in his bag and the plants he picks, before any skeleton is fought for a bone.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFarmObtainTests {
    private static final int GROUND = 5;

    /** Plan only: a ripe crop is a harvest, none ripe is a refusal that names the field he can make. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aRipeFieldIsPlannedAsAHarvest(GameTestHelper h) {
        var level = h.getLevel();
        var wheat = CompanionObtain.plan(level, List.of(), true, "minecraft:wheat", 3, thing -> true);
        h.assertTrue(wheat.steps().equals(List.of(new CompanionObtain.Step("harvest_crops", "minecraft:wheat_seeds", 3))), "Wheat: " + wheat);
        // A carrot plant gives two and more, one goes back into the ground.
        var carrots = CompanionObtain.plan(level, List.of(), true, "minecraft:carrot", 5, thing -> true);
        h.assertTrue(carrots.steps().equals(List.of(new CompanionObtain.Step("harvest_crops", "minecraft:carrot", 3))), "Carrots: " + carrots);
        var bread = CompanionObtain.plan(level, List.of(), true, "minecraft:bread", 1, thing -> true);
        h.assertTrue(bread.steps().equals(List.of(new CompanionObtain.Step("harvest_crops", "minecraft:wheat_seeds", 3), new CompanionObtain.Step("craft_item", "minecraft:bread", 1))), "Bread: " + bread);
        for (String item : List.of("minecraft:bread", "minecraft:potato", "minecraft:beetroot")) {
            var none = CompanionObtain.plan(level, List.of(), true, item, 1, thing -> false);
            h.assertTrue(none.refused() && none.steps().isEmpty() && none.refusal().contains("огород"), item + " with no ripe field: " + none);
        }
        // Bone meal in his bag: the harvest may ripen young ones («meal:»), a step a model cannot order.
        var meal = CompanionObtain.plan(level, List.of(new ItemStack(Items.BONE_MEAL, 8)), true, "minecraft:wheat", 3, thing -> true);
        h.assertTrue(meal.steps().equals(List.of(new CompanionObtain.Step("harvest_crops", "meal:minecraft:wheat_seeds", 3))), "Wheat with meal: " + meal);
        boolean mealTaken = true;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"harvest_crops\",\"item\":\"meal:minecraft:wheat_seeds\"}]")); } catch (RuntimeException refused) { mealTaken = false; }
        h.assertTrue(!mealTaken, "meal: was taken from a model");
        // Seeds are his own business at sowing: no field is reaped for them.
        var seeds = CompanionObtain.plan(level, List.of(), true, "minecraft:wheat_seeds", 1, thing -> true);
        h.assertTrue(seeds.steps().stream().noneMatch(step -> step.type().equals("harvest_crops")), "Seeds: " + seeds);
        h.succeed();
    }

    /** Plan only: bones he carries first, then the scrap of his bag, then plants picked for the composter, a skeleton last. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void boneMealIsPlannedOfACompostBeforeAFight(GameTestHelper h) {
        var level = h.getLevel();
        var meal = new CompanionObtain.Step("compost_meal", "scrap", 1);
        var bones = CompanionObtain.plan(level, List.of(new ItemStack(Items.BONE)), true, "minecraft:bone_meal", 2, thing -> true, thing -> true);
        h.assertTrue(bones.steps().equals(List.of(new CompanionObtain.Step("craft_item", "minecraft:bone_meal", 2))), "Bones carried: " + bones);
        // Twenty poppies are thirteen layers on average, seven make one bone meal; a composter stands by.
        var scrap = CompanionObtain.plan(level, List.of(new ItemStack(Items.POPPY, 20)), true, "minecraft:bone_meal", 1, thing -> true, thing -> true);
        h.assertTrue(scrap.steps().equals(List.of(meal)), "Scrap carried: " + scrap);
        // The four saplings and sixteen seeds he keeps for planting are no scrap.
        var reserve = CompanionObtain.plan(level, List.of(new ItemStack(Items.OAK_SAPLING, 4), new ItemStack(Items.WHEAT_SEEDS, 16)), true, "minecraft:bone_meal", 1, thing -> false, thing -> false);
        h.assertTrue(reserve.refused() && reserve.refusal().contains("компостера"), "The reserve was planned into a composter: " + reserve);
        // Food and harvest he carries are the player's, whatever a composter would make of them; a few flowers are not worth a composter of his own.
        for (ItemStack kept : List.of(new ItemStack(Items.MELON_SLICE, 64), new ItemStack(Items.SUGAR_CANE, 64), new ItemStack(Items.POPPY, 3))) {
            var plan = CompanionObtain.plan(level, List.of(kept), true, "minecraft:bone_meal", 1, thing -> false, thing -> false);
            h.assertTrue(plan.refused(), kept + " was planned into a composter: " + plan);
        }
        // The step is the chain's own: a model cannot order it.
        boolean taken = true;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"compost_meal\",\"item\":\"scrap\",\"count\":1}]")); } catch (RuntimeException refused) { taken = false; }
        h.assertTrue(!taken, "compost_meal was taken from a model");
        // No composter about: wood for his own first (the work makes it, of whatever wood that is).
        var own = CompanionObtain.plan(level, List.of(new ItemStack(Items.POPPY, 20)), true, "minecraft:bone_meal", 1, thing -> false, thing -> false);
        h.assertTrue(own.steps().equals(List.of(new CompanionObtain.Step("gather_wood", "", 2), meal)), "No composter about: " + own);
        // An empty bag among plants: they are picked and composted, the skeleton about is left alone.
        var picked = CompanionObtain.plan(level, List.of(), true, "minecraft:bone_meal", 1, thing -> true, thing -> true);
        h.assertTrue(picked.steps().size() == 2 && picked.steps().getFirst().type().equals("gather_block") && picked.steps().getFirst().count() == 14
                && picked.steps().getLast().equals(meal), "Plants about: " + picked);
        // Nothing to compost: the skeleton it is.
        var fight = CompanionObtain.plan(level, List.of(new ItemStack(Items.STONE_AXE)), true, "minecraft:bone_meal", 1, thing -> false, thing -> true);
        h.assertTrue(fight.steps().equals(List.of(new CompanionObtain.Step("hunt_drop", "minecraft:bone", 1), new CompanionObtain.Step("craft_item", "minecraft:bone_meal", 1))), "Nothing to compost: " + fight);
        h.succeed();
    }

    /** «Сделай хлеб» by a ripe field: three ears are reaped (and sown again), the bread is made at his bench. */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void breadIsMadeOfARipeField(GameTestHelper h) {
        ground(h);
        h.setBlock(new BlockPos(20, GROUND, 16), Blocks.WATER);
        for (int z = 14; z <= 18; z++) {
            h.setBlock(new BlockPos(21, GROUND, z), Blocks.FARMLAND);
            h.setBlock(new BlockPos(21, GROUND + 1, z), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
        }
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:bread\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.BREAD) >= 1, "No bread: " + future.join().report());
        });
    }

    /** «Добудь костную муку» among poppies, a composter standing by: the poppies are picked, put in, and the bone meal is in his bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void boneMealIsMadeOfPoppiesPickedForTheComposter(GameTestHelper h) {
        ground(h);
        for (int x = 16; x <= 25; x++) for (int z = 13; z <= 19; z += 2) h.setBlock(new BlockPos(x, GROUND + 1, z), Blocks.POPPY);
        h.setBlock(new BlockPos(14, GROUND + 1, 16), Blocks.COMPOSTER);
        CompanionEntity c = spawn(h, 12, 16);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:bone_meal\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.BONE_MEAL) >= 1, "No bone meal: " + future.join().report());
        });
    }

    /** «Удобри огород»: the bone meal of his bag on every young crop about the player until it is ripe; nothing is reaped. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void youngCropsAreGrownWithHisBoneMeal(GameTestHelper h) {
        ground(h);
        h.setBlock(new BlockPos(20, GROUND, 16), Blocks.WATER);
        for (int z = 14; z <= 18; z++) {
            h.setBlock(new BlockPos(21, GROUND, z), Blocks.FARMLAND);
            h.setBlock(new BlockPos(21, GROUND + 1, z), Blocks.WHEAT);
        }
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.BONE_MEAL, 32));
        var future = run(h, c, "[{\"action\":\"use_bone_meal\",\"item\":\"any\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            for (int z = 14; z <= 18; z++) {
                var state = h.getBlockState(new BlockPos(21, GROUND + 1, z));
                h.assertTrue(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state), "Not ripe at z=" + z + ": " + state + "; " + future.join().report());
            }
            h.assertTrue(count(c, Items.BONE_MEAL) < 32 && count(c, Items.WHEAT) == 0, "Meal " + count(c, Items.BONE_MEAL) + ", wheat " + count(c, Items.WHEAT));
        });
    }

    /** «Удобри две грядки»: the two nearest young crops ripen, the rest keep growing and his meal stays in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aNamedNumberOfCropsIsGrown(GameTestHelper h) {
        // The direct phrases: the number said is kept (in game 16.11 «удобри две грядки» ripened all five), a sapling is one.
        for (String[] phrase : new String[][]{{"удобри две грядки", "companion work use_bone_meal 2 any"}, {"Олег, удобри огород", "companion work use_bone_meal 64 any"},
                {"посыпь грядки костной мукой", "companion work use_bone_meal 64 any"}, {"вырасти дерево", "companion work use_bone_meal 1 sapling"}})
            h.assertTrue(phrase[1].equals(dev.aicompanion.client.CompanionLocalCommands.route(phrase[0], "Олег")), phrase[0] + ": " + dev.aicompanion.client.CompanionLocalCommands.route(phrase[0], "Олег"));
        ground(h);
        h.setBlock(new BlockPos(20, GROUND, 16), Blocks.WATER);
        for (int z = 14; z <= 18; z++) {
            h.setBlock(new BlockPos(21, GROUND, z), Blocks.FARMLAND);
            h.setBlock(new BlockPos(21, GROUND + 1, z), Blocks.WHEAT);
        }
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.BONE_MEAL, 32));
        var future = run(h, c, "[{\"action\":\"use_bone_meal\",\"item\":\"any\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            int ripe = 0;
            for (int z = 14; z <= 18; z++) if (h.getBlockState(new BlockPos(21, GROUND + 1, z)).getBlock() instanceof CropBlock crop && crop.isMaxAge(h.getBlockState(new BlockPos(21, GROUND + 1, z)))) ripe++;
            h.assertTrue(ripe == 2, "Ripe " + ripe + " of the two asked; " + future.join().report());
        });
    }

    /** «Вырасти дерево»: bone meal on the sapling nearest the player until a tree stands there. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aSaplingIsGrownIntoATree(GameTestHelper h) {
        ground(h);
        BlockPos sapling = new BlockPos(18, GROUND + 1, 16);
        h.setBlock(sapling, Blocks.OAK_SAPLING.defaultBlockState().setValue(net.minecraft.world.level.block.SaplingBlock.STAGE, 1));
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.BONE_MEAL, 32));
        var future = run(h, c, "[{\"action\":\"use_bone_meal\",\"item\":\"sapling\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            // In game 16.11 the grown trunk stood in his way to the cell, and the tree that grew was reported as not reached.
            h.assertTrue(h.getBlockState(sapling).is(Blocks.OAK_LOG) && report.contains("дерево выросло"), "No tree: " + h.getBlockState(sapling) + "; " + report);
            h.assertTrue(count(c, Items.BONE_MEAL) < 32, "No meal spent");
        });
    }

    /** A mangrove grows on its roots, its trunk above the cell of the propagule: that is a tree grown too. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aMangroveIsGrownOnItsRoots(GameTestHelper h) {
        ground(h);
        BlockPos sapling = new BlockPos(18, GROUND + 1, 16);
        h.setBlock(sapling, Blocks.MANGROVE_PROPAGULE.defaultBlockState().setValue(net.minecraft.world.level.block.SaplingBlock.STAGE, 1));
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.BONE_MEAL, 32));
        var future = run(h, c, "[{\"action\":\"use_bone_meal\",\"item\":\"sapling\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(!h.getBlockState(sapling).is(Blocks.MANGROVE_PROPAGULE) && report.contains("дерево выросло"), h.getBlockState(sapling) + "; " + report);
        });
    }

    /** A sapling under a roof never grows: at most twelve bone meal go on it, then he says so and keeps the rest. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aSaplingWithNoRoomIsGivenUp(GameTestHelper h) {
        ground(h);
        BlockPos sapling = new BlockPos(18, GROUND + 1, 16);
        h.setBlock(sapling, Blocks.OAK_SAPLING);
        h.setBlock(sapling.above(2), Blocks.STONE);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.BONE_MEAL, 32));
        var future = run(h, c, "[{\"action\":\"use_bone_meal\",\"item\":\"sapling\"}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(h.getBlockState(sapling).is(Blocks.OAK_SAPLING) && report.contains("Не растёт"), "Report: " + report);
            h.assertTrue(count(c, Items.BONE_MEAL) == 32 - 12, "Meal left " + count(c, Items.BONE_MEAL));
        });
    }

    /** «Сделай хлеб» by a young field and bone meal in his bag: the meal ripens three, they are reaped, the bread is made. */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void breadIsMadeOfAYoungFieldWithHisBoneMeal(GameTestHelper h) {
        ground(h);
        h.setBlock(new BlockPos(20, GROUND, 16), Blocks.WATER);
        for (int z = 14; z <= 18; z++) {
            h.setBlock(new BlockPos(21, GROUND, z), Blocks.FARMLAND);
            h.setBlock(new BlockPos(21, GROUND + 1, z), Blocks.WHEAT);
        }
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        c.setItem(1, new ItemStack(Items.BONE_MEAL, 32));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:bread\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.BREAD) >= 1, "No bread: " + future.join().report());
        });
    }

    /** A ripe ear walled in where he cannot reach it does not stop «сделай хлеб»: the young ones by the water ripen in its place. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aRipeCropOutOfReachIsMadeUpWithYoungOnes(GameTestHelper h) {
        ground(h);
        h.setBlock(new BlockPos(20, GROUND, 16), Blocks.WATER);
        for (int z = 14; z <= 18; z++) {
            h.setBlock(new BlockPos(21, GROUND, z), Blocks.FARMLAND);
            h.setBlock(new BlockPos(21, GROUND + 1, z), Blocks.WHEAT);
        }
        for (int x = 15; x <= 19; x++) for (int y = GROUND; y <= GROUND + 3; y++) for (int z = 21; z <= 25; z++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        h.setBlock(new BlockPos(17, GROUND + 1, 23), Blocks.FARMLAND);
        h.setBlock(new BlockPos(17, GROUND + 2, 23), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        c.setItem(1, new ItemStack(Items.BONE_MEAL, 32));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:bread\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.BREAD) >= 1, "No bread: " + future.join().report());
        });
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
        // The least radius: the plants and fields of the tests beside this one, beyond its walls, are out of his way.
        c.setSearchRadius(16);
        return c;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-farm-obtain", () -> false));
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

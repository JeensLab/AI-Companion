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
        h.assertTrue(reserve.refused(), "The reserve was planned into a composter: " + reserve);
        // No composter about: his own is made first.
        var own = CompanionObtain.plan(level, List.of(new ItemStack(Items.POPPY, 20)), true, "minecraft:bone_meal", 1, thing -> false, thing -> false);
        h.assertTrue(!own.refused() && own.steps().contains(new CompanionObtain.Step("craft_item", "minecraft:composter", 1)) && own.steps().getLast().equals(meal), "No composter about: " + own);
        // An empty bag among plants: they are picked and composted, the skeleton about is left alone.
        var picked = CompanionObtain.plan(level, List.of(), true, "minecraft:bone_meal", 1, thing -> true, thing -> true);
        h.assertTrue(picked.steps().size() == 2 && picked.steps().getFirst().type().equals("gather_block")
                && picked.steps().getLast().equals(new CompanionObtain.Step("compost_meal", picked.steps().getFirst().item(), 1)), "Plants about: " + picked);
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

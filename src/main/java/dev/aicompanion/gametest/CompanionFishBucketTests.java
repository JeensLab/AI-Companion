package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionObtain;
import dev.aicompanion.entity.CompanionObtain.Step;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The player 15.11: a fish in a bucket, as a player gets it: a bucket of water of his scooped over one that swims under open
 * water and that he sees. None is struck for it, none let out of somebody's bucket is taken; an axolotl or a tadpole never.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFishBucketTests {
    private static final int GROUND = 5;

    /** Plan only: a bucket of water of his is scooped; an empty one is filled first; with none of the kind about he says so. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aBucketOfFishIsPlanned(GameTestHelper h) {
        var level = h.getLevel();
        for (String bucket : List.of("minecraft:cod_bucket", "minecraft:salmon_bucket", "minecraft:tropical_fish_bucket", "minecraft:pufferfish_bucket")) {
            var plan = CompanionObtain.plan(level, List.of(new ItemStack(Items.WATER_BUCKET)), false, bucket, 1, thing -> true, thing -> false);
            h.assertTrue(plan.steps().equals(List.of(new Step("scoop_mob", bucket, 1))), bucket + ": " + plan);
        }
        var empty = CompanionObtain.plan(level, List.of(new ItemStack(Items.BUCKET, 2)), false, "minecraft:salmon_bucket", 2, thing -> true, thing -> false);
        h.assertTrue(empty.steps().equals(List.of(new Step("fill_bucket", "minecraft:water_bucket", 2), new Step("scoop_mob", "minecraft:salmon_bucket", 2))), "Empty buckets: " + empty);
        var none = CompanionObtain.plan(level, List.of(new ItemStack(Items.WATER_BUCKET)), false, "minecraft:cod_bucket", 1, thing -> false, thing -> false);
        h.assertTrue(none.refused() && none.steps().isEmpty() && none.refusal().contains("Рядом не вижу трески"), "Cod bucket with no cod about: " + none);
        for (String pet : List.of("minecraft:axolotl_bucket", "minecraft:tadpole_bucket")) {
            var plan = CompanionObtain.plan(level, List.of(new ItemStack(Items.WATER_BUCKET)), false, pet, 1, thing -> true, thing -> false);
            h.assertTrue(plan.refused() && plan.steps().isEmpty() && plan.refusal().contains("чьи-то"), pet + ": " + plan);
        }
        h.succeed();
    }

    /** «Добудь ведро трески и ведро лосося» in a pond: both scooped alive with his two buckets of water; the player's cod let out of a bucket is left. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aCodAndASalmonAreScoopedInAPond(GameTestHelper h) {
        ground(h);
        for (int x = 16; x <= 24; x++) for (int z = 12; z <= 20; z++) for (int y = 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        // At the bottom by the bank and up the water: the test is of his swim and dive, not of their wandering.
        var cod = h.spawnWithNoFreeWill(EntityType.COD, 17, 1, 14);
        var salmon = h.spawnWithNoFreeWill(EntityType.SALMON, 22, 3, 18);
        var pet = h.spawnWithNoFreeWill(EntityType.COD, 19, 4, 13);
        pet.setFromBucket(true);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:cod_bucket\",\"count\":1},{\"action\":\"obtain_item\",\"item\":\"minecraft:salmon_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.COD_BUCKET) == 1 && count(c, Items.SALMON_BUCKET) == 1 && count(c, Items.WATER_BUCKET) == 0,
                    "Cod " + count(c, Items.COD_BUCKET) + ", salmon " + count(c, Items.SALMON_BUCKET) + ", water " + count(c, Items.WATER_BUCKET) + ": " + report);
            h.assertTrue(cod.isRemoved() && salmon.isRemoved(), "Not the cod and the salmon that are in his buckets");
            h.assertTrue(pet.isAlive() && pet.getHealth() == pet.getMaxHealth(), "The player's cod was taken or struck");
        });
    }

    /** A cod in a glass tank by his side, within his arm's length: what he does not see is not scooped through the glass. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aFishBehindGlassIsNotScooped(GameTestHelper h) {
        ground(h);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 2; dy++)
            h.setBlock(new BlockPos(16 + dx, GROUND + 1 + dy, 16 + dz), dx == 0 && dz == 0 && dy < 2 ? Blocks.WATER : Blocks.GLASS);
        var cod = h.spawnWithNoFreeWill(EntityType.COD, 16, GROUND + 1, 16);
        CompanionEntity c = spawn(h, 14, 16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:cod_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(cod.isAlive() && !cod.isRemoved() && count(c, Items.COD_BUCKET) == 0 && count(c, Items.WATER_BUCKET) == 1,
                    "The cod was scooped through the glass: " + report);
            // He went for it and gave it up, not «none about» before he tried.
            h.assertTrue(report.contains("добраться не смог") && !report.contains("Рядом не вижу"), "Not tried: " + report);
        });
    }

    /**
     * A pond 18 blocks deep, he on its bank: a cod down by its floor is in the sea about him, not a cave under him (de-flandres
     * #178/#179: deeper than 12 under him «рядом не вижу»). One is fought for its meat from the bank, farther than his least
     * search radius from his eyes though within it across; the other is scooped into his bucket.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void codDownByTheFloorOfDeepWaterAreFoughtAndScooped(GameTestHelper h) {
        int bank = 19;
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y < 24; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31, pond = x >= 16 && x <= 24 && z >= 12 && z <= 20 && y >= 1;
            h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : pond && y <= bank ? Blocks.WATER : y < bank ? Blocks.DIRT : y == bank ? Blocks.GRASS_BLOCK : Blocks.AIR);
        }
        var first = h.spawnWithNoFreeWill(EntityType.COD, 17, 1, 14);
        var second = h.spawnWithNoFreeWill(EntityType.COD, 21, 2, 18);
        first.setPersistenceRequired(); second.setPersistenceRequired();
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 12, bank + 1, 16);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(10, bank + 1, 14));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        c.setItem(1, new ItemStack(Items.STONE_SWORD));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:cod\",\"count\":1},{\"action\":\"obtain_item\",\"item\":\"minecraft:cod_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.COD_BUCKET) == 1 && count(c, Items.COD) >= 1, "Cod bucket " + count(c, Items.COD_BUCKET) + ", cod " + count(c, Items.COD) + ": " + report);
            h.assertTrue(first.isRemoved() && second.isRemoved(), "Not the two cod down by the floor");
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
        // The least radius: the fish of the tests beside this one, beyond its walls, are out of his way.
        c.setSearchRadius(16);
        return c;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-fish-bucket", () -> false));
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

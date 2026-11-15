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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The player 15.11: a bucket of lava and of powder snow, as a player fills them: an empty bucket of his over lava's own source or
 * the powder snow, from the firm ground beside it; he never steps into either.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBucketFillTests {
    private static final int GROUND = 5;

    /** Plan only: an empty bucket of his is filled; with none about he says so before anything starts. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aBucketOfLavaOrSnowIsPlanned(GameTestHelper h) {
        var level = h.getLevel();
        for (String bucket : List.of("minecraft:lava_bucket", "minecraft:powder_snow_bucket")) {
            var plan = CompanionObtain.plan(level, List.of(new ItemStack(Items.BUCKET, 2)), false, bucket, 2, thing -> true, thing -> false);
            h.assertTrue(plan.steps().equals(List.of(new Step("scoop_block", bucket, 2))), bucket + ": " + plan);
        }
        var lava = CompanionObtain.plan(level, List.of(new ItemStack(Items.BUCKET)), false, "minecraft:lava_bucket", 1, thing -> false, thing -> false);
        h.assertTrue(lava.refused() && lava.steps().isEmpty() && lava.refusal().contains("Рядом не вижу лавы"), "Lava bucket with no lava about: " + lava);
        var snow = CompanionObtain.plan(level, List.of(new ItemStack(Items.BUCKET)), false, "minecraft:powder_snow_bucket", 1, thing -> false, thing -> false);
        h.assertTrue(snow.refused() && snow.steps().isEmpty() && snow.refusal().contains("Рядом не вижу рыхлого снега"), "Snow bucket with no snow about: " + snow);
        h.succeed();
    }

    /**
     * «Добудь 2 ведра лавы» by a pool of lava in the ground: two of its sources in his buckets, and he did not burn. A layer of lava
     * shut in the ground under him is nearer and none of his (in the Nether 15.11 such pockets in the rock were all he found first,
     * and he gave up: «Вижу лаву, но подойти не смог»).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void lavaIsScoopedFromTheBank(GameTestHelper h) {
        ground(h);
        for (int x = 18; x <= 20; x++) for (int z = 15; z <= 17; z++) {
            h.setBlock(new BlockPos(x, GROUND - 1, z), Blocks.STONE);
            h.setBlock(new BlockPos(x, GROUND, z), Blocks.LAVA);
        }
        for (int x = 4; x <= 16; x++) for (int z = 10; z <= 22; z++) h.setBlock(new BlockPos(x, GROUND - 2, z), Blocks.LAVA);
        // Magma on the near bank, as by the lava sea of the Nether: he does not stand on it.
        for (int x = 16; x <= 17; x++) for (int z = 13; z <= 19; z++) h.setBlock(new BlockPos(x, GROUND, z), Blocks.MAGMA_BLOCK);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.BUCKET, 2));
        // Lava set down has its flow tick due for a while; lava of the world lies still, so the order waits for it to settle.
        List<CompletableFuture<CompanionPlanRunner.Outcome>> order = new java.util.ArrayList<>();
        h.runAfterDelay(40, () -> order.add(run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:lava_bucket\",\"count\":2}]")));
        h.succeedWhen(() -> {
            h.assertTrue(!order.isEmpty(), "Not ordered yet");
            var future = order.getFirst();
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.LAVA_BUCKET) == 2 && count(c, Items.BUCKET) == 0, "Lava " + count(c, Items.LAVA_BUCKET) + ": " + future.join().report());
            h.assertTrue(c.getHealth() == c.getMaxHealth() && !c.isOnFire(), "He burned: " + c.getHealth());
        });
    }

    /**
     * «Добудь 2 ведра рыхлого снега» by a patch of it: filled from the edge, he never stood in it (no frost on him). A strip of it
     * lies flush with the ground across his way: the game takes the cell over it for floor, he goes round it.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void powderSnowIsScoopedFromTheEdge(GameTestHelper h) {
        ground(h);
        for (int x = 19; x <= 22; x++) for (int z = 14; z <= 18; z++) for (int y = GROUND - 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.POWDER_SNOW);
        for (int z = 8; z <= 24; z++) for (int y = GROUND - 1; y <= GROUND; y++) h.setBlock(new BlockPos(15, y, z), Blocks.POWDER_SNOW);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.BUCKET, 2));
        int[] frozen = {0};
        h.onEachTick(() -> frozen[0] = Math.max(frozen[0], c.getTicksFrozen()));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:powder_snow_bucket\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.POWDER_SNOW_BUCKET) == 2 && count(c, Items.BUCKET) == 0, "Snow " + count(c, Items.POWDER_SNOW_BUCKET) + ": " + future.join().report());
            h.assertTrue(frozen[0] == 0, "He stood in the powder snow: frozen " + frozen[0]);
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
        c.setSearchRadius(16);
        return c;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-bucket-fill", () -> false));
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

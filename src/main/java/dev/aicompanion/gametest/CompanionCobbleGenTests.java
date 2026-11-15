package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * «Сделай генератор булыжника» (ticket 01 of cobble-gen-build): he digs a channel on flat ground near the player and pours the lava
 * bucket and the water bucket of his bag into it; both buckets stay in his bag, empty.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCobbleGenTests {
    /** The top of the platform: three layers of earth (y 2..4) over empty air. */
    private static final int TOP = 4;

    private CompanionCobbleGenTests() { }

    /**
     * On a platform in the air, said in the player's words (no model): a cobblestone grows beside the lava in a channel he dug, the
     * water next to its hole, the two empty buckets are in his bag, and he never burnt. Nothing but what he carried was used.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aCobbleGeneratorIsBuiltFromTheBucketsOfHisBag(GameTestHelper h) {
        platform(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        boolean[] burnt = {false};
        h.onEachTick(() -> burnt[0] |= c.isOnFire() || c.isInLava());
        var future = run(h, c, "сделай генератор булыжника");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            List<BlockPos> stone = find(h, Blocks.COBBLESTONE);
            h.assertTrue(stone.size() == 1, "Not one cobblestone: " + stone + " | " + report);
            BlockPos at = stone.getFirst();
            h.assertTrue(report.contains("Готово, генератор у " + h.absolutePos(at).getX() + ", " + h.absolutePos(at).getY() + ", " + h.absolutePos(at).getZ() + "."), "Not said where: " + report);
            boolean row = false;
            for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL)
                row |= h.getBlockState(at.relative(side.getOpposite())).is(Blocks.LAVA) && h.getBlockState(at.relative(side)).is(Blocks.WATER)
                        && h.getBlockState(at.relative(side).below()).is(Blocks.WATER) && h.getBlockState(at.relative(side, 2)).is(Blocks.WATER);
            h.assertTrue(row, "Not the row lava, stone, water over a hole, water at " + at);
            h.assertTrue(count(c, Items.BUCKET) == 2 && count(c, Items.LAVA_BUCKET) == 0 && count(c, Items.WATER_BUCKET) == 0, "Buckets: " + c.getStoredItemsSummary());
            h.assertTrue(!burnt[0] && c.getHealth() >= c.getMaxHealth(), "He burnt or was hurt: " + c.getHealth());
        });
    }

    /** Without a bucket of lava he says so at once, digs nothing and keeps his water. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void withNoLavaBucketHeRefusesAtOnceAndDigsNothing(GameTestHelper h) {
        platform(h);
        CompanionEntity c = spawn(h);
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "сделай генератор булыжника");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Нужно ведро лавы."), "Not said that he needs lava: " + report);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && find(h, Blocks.GRASS_BLOCK, TOP).size() == 256, "He dug or poured: " + c.getStoredItemsSummary());
        });
    }

    /**
     * No water bucket, a block of ice (ticket 02): he sets the ice in the water's cell and breaks it, it melts into a source as for a
     * player, and the generator works. The ice is spent; the lava bucket is left empty.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aBlockOfIceMakesTheWater(GameTestHelper h) {
        platform(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        c.setItem(1, new ItemStack(Items.ICE));
        var future = run(h, c, "сделай генератор булыжника");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Готово, генератор у"), "Not built: " + future.join().report());
            h.assertTrue(find(h, Blocks.COBBLESTONE).size() == 1 && find(h, Blocks.ICE).isEmpty(), "Not one stone, or ice is left: " + find(h, Blocks.ICE));
            h.assertTrue(count(c, Items.ICE) == 0 && count(c, Items.BUCKET) == 1, "Ice or bucket: " + c.getStoredItemsSummary());
        });
    }

    /**
     * No water bucket, his own empty one and a pond on the platform: he fills it there first, then builds (the ice he also carries is
     * kept, spec Г5 order). Both buckets end empty in his bag.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void hisEmptyBucketIsFilledAtAPondNearby(GameTestHelper h) {
        platform(h);
        for (int x = 20; x <= 21; x++) for (int z = 20; z <= 21; z++) h.setBlock(new BlockPos(x, TOP, z), Blocks.WATER);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        c.setItem(1, new ItemStack(Items.BUCKET));
        c.setItem(2, new ItemStack(Items.ICE));
        var future = run(h, c, "сделай генератор булыжника");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Готово, генератор у"), "Not built: " + future.join().report());
            h.assertTrue(count(c, Items.BUCKET) == 2 && count(c, Items.ICE) == 1, "Buckets or ice: " + c.getStoredItemsSummary());
        });
    }

    /**
     * Review of ticket 02: water is seen but shut in glass, so the fill of his empty bucket gives up; the ice of his bag makes the
     * water then, and the generator is built (spec Г5: the bucket, else the ice).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void waterHeCannotGetToLeavesTheIce(GameTestHelper h) {
        platform(h);
        for (int x = 19; x <= 22; x++) for (int z = 19; z <= 22; z++) for (int y = TOP + 1; y <= TOP + 3; y++) {
            boolean inside = x >= 20 && x <= 21 && z >= 20 && z <= 21 && y < TOP + 3;
            h.setBlock(new BlockPos(x, y, z), !inside ? Blocks.GLASS : y == TOP + 1 ? Blocks.WATER : Blocks.AIR);
        }
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        c.setItem(1, new ItemStack(Items.BUCKET));
        c.setItem(2, new ItemStack(Items.ICE));
        var future = run(h, c, "сделай генератор булыжника");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Готово, генератор у"), "Not built: " + future.join().report());
            h.assertTrue(count(c, Items.ICE) == 0 && count(c, Items.BUCKET) == 2, "Ice or buckets: " + c.getStoredItemsSummary());
        });
    }

    /** No water bucket, no ice, no empty bucket: he says what water he needs at once and digs nothing. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void withNoWaterHeSaysWhatWaterHeNeeds(GameTestHelper h) {
        platform(h);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        var future = run(h, c, "сделай генератор булыжника");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Нужна вода: ведро воды или лёд."), "Not said what water: " + report);
            h.assertTrue(count(c, Items.LAVA_BUCKET) == 1 && find(h, Blocks.GRASS_BLOCK, TOP).size() == 256, "He dug or poured: " + c.getStoredItemsSummary());
        });
    }

    /**
     * A strip of ground three wide and six long with one block missing beside the channel's only place: the gap would let the lava
     * out, so he sets a block of his bag in it (the softest: the earth he has just dug, not his cobblestone), and the generator still works. The player stands on a block
     * of his own away from the strip.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void aGapBesideTheChannelIsWalledWithABlockOfHisBag(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 12; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 10; x <= 15; x++) for (int z = 12; z <= 14; z++) for (int y = TOP - 2; y <= TOP; y++) h.setBlock(new BlockPos(x, y, z), y == TOP ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        BlockPos gap = new BlockPos(13, TOP, 12);
        h.setBlock(gap, Blocks.AIR);
        h.setBlock(new BlockPos(19, TOP, 13), Blocks.DIRT);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 10, TOP + 1, 13);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(19, TOP + 1, 13));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setLifestyle(false, false);
        c.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        c.setItem(2, new ItemStack(Items.COBBLESTONE, 3));
        var future = run(h, c, "сделай генератор булыжника");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(report.contains("Готово, генератор у"), "Not built: " + report);
            h.assertTrue(h.getBlockState(gap).is(Blocks.DIRT), "The gap is not walled: " + h.getBlockState(gap));
            h.assertTrue(find(h, Blocks.COBBLESTONE).size() == 1 && count(c, Items.COBBLESTONE) == 3, "Not the one stone and his cobblestone: " + find(h, Blocks.COBBLESTONE) + " | " + c.getStoredItemsSummary());
            h.assertTrue(count(c, Items.BUCKET) == 2, "The buckets are not in his bag: " + c.getStoredItemsSummary());
        });
    }

    /**
     * Game 15.11: on an island six by six the lava cell was the block the player stood on; he dug it, the player fell in the channel and
     * the lava was poured into him. Nothing within a block and a half of the player's feet is dug, the ground under him included.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void theGroundUnderThePlayerIsNotDug(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 12; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 10; x <= 15; x++) for (int z = 10; z <= 15; z++) for (int y = TOP - 2; y <= TOP; y++) h.setBlock(new BlockPos(x, y, z), y == TOP ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 14, TOP + 1, 11);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(11, TOP + 1, 11));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setLifestyle(false, false);
        c.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        var future = run(h, c, "сделай генератор булыжника");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            for (int x = 10; x <= 12; x++) for (int z = 10; z <= 12; z++)
                h.assertTrue(h.getBlockState(new BlockPos(x, TOP, z)).is(Blocks.GRASS_BLOCK), "Dug beside the player at " + x + ", " + z + ": " + future.join().report());
            h.assertTrue(future.join().report().contains("Готово, генератор у"), "Not built: " + future.join().report());
        });
    }

    /** A player or a mob in the channel: no fluid is poured into his cell; when he leaves, the generator is finished. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void noLavaIsPouredOntoAMobStandingInTheChannel(GameTestHelper h) {
        CompanionEntity c = strip(h, null, TOP);
        List<net.minecraft.world.entity.animal.Pig> pigs = new ArrayList<>();
        for (int x = 12; x <= 13; x++) {
            var pig = h.spawn(net.minecraft.world.entity.EntityType.PIG, x, TOP, 13);
            pig.setNoAi(true);
            pig.setInvulnerable(true);
            pigs.add(pig);
        }
        var future = run(h, c, "сделай генератор булыжника");
        int[] dug = {0};
        h.onEachTick(() -> {
            if (pigs.getFirst().isAlive()) {
                for (var pig : pigs) h.assertTrue(h.getLevel().getFluidState(pig.blockPosition()).isEmpty(), "Fluid poured onto the mob at " + pig.blockPosition());
                if (h.getBlockState(new BlockPos(11, TOP, 13)).isAir() && h.getBlockState(new BlockPos(14, TOP, 13)).isAir() && ++dug[0] > 400) pigs.forEach(pig -> pig.discard());
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Готово, генератор у"), "Not built: " + future.join().report());
        });
    }

    /**
     * Short grass on the whole strip, a poppy and a dead bush among it: wild plants are cut, not a reason to pass the place over. The
     * generator is built and no plant is left within the lava's fire range.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void plantsNearTheLavaAreCutAndTheGeneratorIsBuilt(GameTestHelper h) {
        CompanionEntity c = strip(h, null, TOP);
        for (int x = 10; x <= 15; x++) for (int z = 12; z <= 14; z++) h.setBlock(new BlockPos(x, TOP + 1, z), Blocks.SHORT_GRASS);
        h.setBlock(new BlockPos(13, TOP + 1, 13), Blocks.POPPY);
        h.setBlock(new BlockPos(11, TOP + 1, 12), Blocks.DEAD_BUSH);
        var future = run(h, c, "сделай генератор булыжника");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Готово, генератор у"), "Not built: " + future.join().report());
            BlockPos stone = find(h, Blocks.COBBLESTONE).getFirst();
            for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                if (!h.getBlockState(stone.relative(side)).is(Blocks.LAVA)) continue;
                for (BlockPos center : new BlockPos[]{stone, stone.relative(side)})
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++) {
                        var state = h.getBlockState(center.offset(dx, dy, dz));
                        h.assertTrue(!state.is(Blocks.SHORT_GRASS) && !state.is(Blocks.POPPY) && !state.is(Blocks.DEAD_BUSH), "A plant is left at " + center.offset(dx, dy, dz));
                    }
            }
        });
    }

    /** A wall of planks, ice or sand burns, melts or falls beside the lava: with only such a place he refuses. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aWallOfPlanksIsNotAReadyWall(GameTestHelper h) { refuses(h, Blocks.OAK_PLANKS, TOP); }

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aWallOfIceIsNotAReadyWall(GameTestHelper h) { refuses(h, Blocks.ICE, TOP); }

    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void aWallOfSandIsNotAReadyWall(GameTestHelper h) { refuses(h, Blocks.SAND, TOP); }

    /** Wool two blocks above the ground beside the channel is within the lava's fire range. */
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void flammableBlocksNearTheLavaRuleThePlaceOut(GameTestHelper h) { refuses(h, Blocks.WHITE_WOOL, TOP + 2); }

    private static void refuses(GameTestHelper h, net.minecraft.world.level.block.Block block, int y) {
        CompanionEntity c = strip(h, block, y);
        var future = run(h, c, "сделай генератор булыжника");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Не нашёл ровного твёрдого места"), "Not refused: " + future.join().report());
            h.assertTrue(find(h, Blocks.LAVA).isEmpty(), "Lava was poured");
        });
    }

    /** A strip of earth three wide and six long, the player beyond it; {@code line} (if any) is set along its south edge at height y. */
    private static CompanionEntity strip(GameTestHelper h, net.minecraft.world.level.block.Block line, int y) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int yy = 0; yy <= 12; yy++) h.setBlock(new BlockPos(x, yy, z), Blocks.AIR);
        for (int x = 10; x <= 15; x++) for (int z = 12; z <= 14; z++) for (int yy = TOP - 2; yy <= TOP; yy++) h.setBlock(new BlockPos(x, yy, z), yy == TOP ? Blocks.GRASS_BLOCK : Blocks.DIRT);
        if (line != null) for (int x = 10; x <= 15; x++) h.setBlock(new BlockPos(x, y, 12), line);
        h.setBlock(new BlockPos(19, TOP, 13), Blocks.DIRT);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 10, TOP + 1, 13);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(19, TOP + 1, 13));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setLifestyle(false, false);
        c.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        return c;
    }

    /** The platform: 16 by 16, three layers over air, earth with grass on top. */
    private static void platform(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 12; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 8; x < 24; x++) for (int z = 8; z < 24; z++) for (int y = TOP - 2; y <= TOP; y++) h.setBlock(new BlockPos(x, y, z), y == TOP ? Blocks.GRASS_BLOCK : Blocks.DIRT);
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, TOP + 1, 16);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(11, TOP + 1, 11));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setLifestyle(false, false);
        return c;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String said) {
        String command = CompanionLocalCommands.route(said, "Олег");
        h.assertTrue(command != null, "«" + said + "» is left to the model");
        var plan = CompanionLocalCommands.actions(command);
        var model = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_cobble_gen\",\"count\":1}]"));
        h.assertTrue(plan.size() == 1 && model.size() == 1 && plan.getFirst().type().equals(model.getFirst().type()) && plan.getFirst().count() == 1, "Not the model's action: " + plan);
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-cobble-gen", () -> false));
    }

    /** The cells (relative) of the platform's area holding this block, from the top layer down to {@code floor}. */
    private static List<BlockPos> find(GameTestHelper h, net.minecraft.world.level.block.Block block, int floor) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 8; x < 24; x++) for (int z = 8; z < 24; z++) for (int y = floor; y <= TOP + 1; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).is(block)) cells.add(new BlockPos(x, y, z));
        return cells;
    }

    private static List<BlockPos> find(GameTestHelper h, net.minecraft.world.level.block.Block block) { return find(h, block, TOP - 2); }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

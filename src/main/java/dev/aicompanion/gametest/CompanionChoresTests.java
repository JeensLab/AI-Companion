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
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * the player 12.11: he should get everything a player gets. What a tool in hand gets rather than a blow: wool shorn off a sheep
 * that lives, milk of a cow, the eggs that lie about the hens, a log of his stripped with an axe, the combs and the honey of a
 * full hive with a campfire smoking under it.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionChoresTests {
    private static final int GROUND = 5;

    /** Plan only: shears, a bucket and an axe make the step; without the tool or the animal the old way or an honest refusal. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void whatAToolInHandGetsIsPlanned(GameTestHelper h) {
        var level = h.getLevel();
        for (String wool : List.of("minecraft:white_wool", "minecraft:blue_wool")) {
            var shorn = CompanionObtain.plan(level, List.of(new ItemStack(Items.SHEARS)), false, wool, 3, thing -> true, thing -> false);
            h.assertTrue(shorn.steps().equals(List.of(new Step("shear_sheep", wool, 3))), wool + " with shears: " + shorn);
        }
        // Two ingots he carries become the shears; no mine is dug for them: bare hands hunt as before.
        var made = CompanionObtain.plan(level, List.of(new ItemStack(Items.IRON_INGOT, 2)), false, "minecraft:white_wool", 2, thing -> true, thing -> false);
        h.assertTrue(made.steps().equals(List.of(new Step("craft_item", "minecraft:shears", 1), new Step("shear_sheep", "minecraft:white_wool", 2))), "Wool with two ingots: " + made);
        var bare = CompanionObtain.plan(level, List.of(), false, "minecraft:white_wool", 2, thing -> true, thing -> false);
        h.assertTrue(bare.steps().equals(List.of(new Step("hunt_animals", "minecraft:sheep", 2))), "Wool with bare hands: " + bare);
        // No sheep about wears wool (they are shorn): none is killed for the wool it does not have.
        var none = CompanionObtain.plan(level, List.of(new ItemStack(Items.SHEARS)), false, "minecraft:white_wool", 2, thing -> false, thing -> false);
        h.assertTrue(none.refused() && none.steps().isEmpty() && none.refusal().contains("Рядом не вижу овцы"), "Wool with no sheep that wears it: " + none);

        var milk = CompanionObtain.plan(level, List.of(new ItemStack(Items.BUCKET)), false, "minecraft:milk_bucket", 1, thing -> true, thing -> false);
        h.assertTrue(milk.steps().equals(List.of(new Step("milk_animal", "minecraft:milk_bucket", 1))), "Milk: " + milk);
        var noCow = CompanionObtain.plan(level, List.of(new ItemStack(Items.BUCKET)), false, "minecraft:milk_bucket", 1, thing -> false, thing -> false);
        h.assertTrue(noCow.refused() && noCow.steps().isEmpty() && noCow.refusal().contains("Рядом не вижу коровы"), "Milk with no cow about: " + noCow);

        var eggs = CompanionObtain.plan(level, List.of(), false, "minecraft:egg", 2, thing -> true, thing -> false);
        h.assertTrue(eggs.steps().equals(List.of(new Step("collect_eggs", "minecraft:egg", 2))), "Eggs: " + eggs);
        var noEggs = CompanionObtain.plan(level, List.of(), false, "minecraft:egg", 2, thing -> false, thing -> false);
        h.assertTrue(noEggs.refused() && noEggs.steps().isEmpty() && noEggs.refusal().contains("несутся"), "Eggs with none lying: " + noEggs);

        // A stripped log: the logs he lacks chopped by name, then stripped; the wood block made first; an axe made when he has none.
        var log = CompanionObtain.plan(level, List.of(new ItemStack(Items.STONE_AXE), new ItemStack(Items.OAK_LOG)), false, "minecraft:stripped_oak_log", 3);
        h.assertTrue(log.steps().equals(List.of(new Step("gather_wood", "minecraft:oak_log", 2), new Step("strip_log", "minecraft:stripped_oak_log", 3))), "Stripped log: " + log);
        var wood = CompanionObtain.plan(level, List.of(new ItemStack(Items.STONE_AXE)), false, "minecraft:stripped_birch_wood", 3);
        h.assertTrue(wood.steps().equals(List.of(new Step("gather_wood", "minecraft:birch_log", 4), new Step("craft_item", "minecraft:birch_wood", 3),
                new Step("strip_log", "minecraft:stripped_birch_wood", 3))), "Stripped wood: " + wood);
        var noAxe = CompanionObtain.plan(level, List.of(), false, "minecraft:stripped_oak_log", 1);
        int axe = noAxe.steps().indexOf(new Step("craft_item", "minecraft:wooden_axe", 1));
        h.assertTrue(!noAxe.refused() && axe >= 0 && noAxe.steps().indexOf(new Step("gather_wood", "minecraft:oak_log", 1)) > axe
                && noAxe.steps().getLast().equals(new Step("strip_log", "minecraft:stripped_oak_log", 1)), "Stripped log with no axe: " + noAxe);
        // Inside a chain too: a hanging sign takes six stripped logs of its species, not any six logs.
        var sign = CompanionObtain.plan(level, List.of(new ItemStack(Items.STONE_AXE), new ItemStack(Items.CHAIN, 2)), true, "minecraft:birch_hanging_sign", 6);
        h.assertTrue(sign.steps().equals(List.of(new Step("gather_wood", "minecraft:birch_log", 6), new Step("strip_log", "minecraft:stripped_birch_log", 6),
                new Step("craft_item", "minecraft:birch_hanging_sign", 6))), "Hanging sign: " + sign);
        // Stripped logs he carries are logs still: planks are made of them, no tree is cut.
        var planks = CompanionObtain.plan(level, List.of(new ItemStack(Items.STRIPPED_OAK_LOG, 4)), false, "minecraft:oak_planks", 16);
        h.assertTrue(planks.steps().equals(List.of(new Step("craft_item", "minecraft:oak_planks", 16))), "Planks of stripped logs: " + planks);
        h.succeed();
    }

    /** «Добудь 2 шерсти» with shears in the bag: the sheep are shorn and live. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void sheepAreShornAndLive(GameTestHelper h) {
        ground(h);
        List<Sheep> flock = new ArrayList<>();
        for (int i = 0; i < 3; i++) flock.add(h.spawnWithNoFreeWill(EntityType.SHEEP, 18 + i * 2, GROUND + 1, 14 + i * 2));
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.SHEARS));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:white_wool\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.WHITE_WOOL) >= 2, "Not two wool: " + count(c, Items.WHITE_WOOL) + ", " + future.join().report());
            h.assertTrue(flock.stream().allMatch(Sheep::isAlive) && flock.stream().anyMatch(Sheep::isSheared), "A sheep was killed, or none is shorn");
            h.assertTrue(count(c, Items.SHEARS) == 1 && c.getMainHandItem().getDamageValue() > 0, "The shears are not worn");
        });
    }

    /** «Добудь ведро молока»: his empty bucket comes back full from the cow, which lives. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aCowIsMilkedIntoHisBucket(GameTestHelper h) {
        ground(h);
        var cow = h.spawnWithNoFreeWill(EntityType.COW, 20, GROUND + 1, 16);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.BUCKET));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:milk_bucket\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.MILK_BUCKET) == 1 && count(c, Items.BUCKET) == 0 && cow.isAlive(), "No milk for the bucket: " + future.join().report());
        });
    }

    /** «Добудь 3 яйца» with two lying about: both are picked up and the missing one is said, no hen is waited for or killed. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void theEggsThatLieArePickedUp(GameTestHelper h) {
        ground(h);
        var hen = h.spawnWithNoFreeWill(EntityType.CHICKEN, 20, GROUND + 1, 16);
        h.spawnItem(Items.EGG, 19, GROUND + 1, 14);
        h.spawnItem(Items.EGG, 21, GROUND + 1, 18);
        CompanionEntity c = spawn(h, 12, 16);
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:egg\",\"count\":3}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.EGG) == 2 && hen.isAlive(), "Not the two eggs that lay: " + count(c, Items.EGG) + ", " + future.join().report());
            h.assertTrue(future.join().report().contains("несутся"), "The missing egg is not explained: " + future.join().report());
        });
    }

    /** «Добудь 2 обтёсанных дубовых бревна» with two logs and an axe: each is set down, stripped, broken and picked up; nothing is left standing. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void hisLogsAreStrippedWithTheAxe(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.STONE_AXE));
        c.setItem(1, new ItemStack(Items.OAK_LOG, 2));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:stripped_oak_log\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.STRIPPED_OAK_LOG) == 2 && count(c, Items.OAK_LOG) == 0, "Not two stripped logs: " + future.join().report());
            for (int x = 8; x <= 16; x++) for (int z = 12; z <= 20; z++) for (int y = GROUND + 1; y <= GROUND + 3; y++)
                h.assertTrue(h.getBlockState(new BlockPos(x, y, z)).isAir(), "A log is left standing at " + x + " " + y + " " + z);
        });
    }

    /** The axe breaks at the first log: the log is taken back by hand, nothing of his is left standing, both logs are in his bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void aLogIsNotLeftStandingWhenTheAxeBreaks(GameTestHelper h) {
        ground(h);
        CompanionEntity c = spawn(h, 12, 16);
        ItemStack axe = new ItemStack(Items.STONE_AXE);
        axe.setDamageValue(axe.getMaxDamage() - 1);
        c.setItem(0, axe);
        c.setItem(1, new ItemStack(Items.OAK_LOG, 2));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:stripped_oak_log\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            for (int x = 8; x <= 16; x++) for (int z = 12; z <= 20; z++) for (int y = GROUND + 1; y <= GROUND + 3; y++)
                h.assertTrue(h.getBlockState(new BlockPos(x, y, z)).isAir(), "A log is left standing at " + x + " " + y + " " + z);
            h.assertTrue(count(c, Items.STRIPPED_OAK_LOG) + count(c, Items.OAK_LOG) == 2, "A log of his is lost: " + future.join().report());
        });
    }

    /** Plan only: a full hive about him gives combs to shears and honey to a bottle; with no smoke under it a campfire of his goes first. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void aFullHiveIsPlannedWithSmokeUnderIt(GameTestHelper h) {
        var level = h.getLevel();
        Step cut = new Step("harvest_hive", "minecraft:honeycomb", 3);
        var comb = CompanionObtain.plan(level, List.of(new ItemStack(Items.SHEARS)), false, "minecraft:honeycomb", 3, thing -> true, thing -> false);
        h.assertTrue(comb.steps().equals(List.of(cut)), "Combs with shears: " + comb);
        var honey = CompanionObtain.plan(level, List.of(new ItemStack(Items.GLASS_BOTTLE)), false, "minecraft:honey_bottle", 1, thing -> true, thing -> false);
        h.assertTrue(honey.steps().equals(List.of(new Step("harvest_hive", "minecraft:honey_bottle", 1))), "Honey with a bottle: " + honey);
        // No smoke under the hive: the campfire he carries is enough, and none carried is made first.
        var carried = CompanionObtain.plan(level, List.of(new ItemStack(Items.SHEARS), new ItemStack(Items.CAMPFIRE)), false, "minecraft:honeycomb", 3, thing -> thing != Items.CAMPFIRE, thing -> false);
        h.assertTrue(carried.steps().equals(List.of(cut)), "Combs with his campfire: " + carried);
        var made = CompanionObtain.plan(level, List.of(new ItemStack(Items.SHEARS)), false, "minecraft:honeycomb", 3, thing -> thing != Items.CAMPFIRE, thing -> false);
        int fire = made.steps().indexOf(new Step("craft_item", "minecraft:campfire", 1));
        h.assertTrue(!made.refused() && fire >= 0 && made.steps().indexOf(cut) > fire && made.steps().getLast().equals(cut), "Combs with no smoke and no campfire: " + made);
        var none = CompanionObtain.plan(level, List.of(new ItemStack(Items.SHEARS)), false, "minecraft:honeycomb", 3, thing -> false, thing -> false);
        h.assertTrue(none.refused() && none.steps().isEmpty() && none.refusal().contains("улья"), "Combs with no full hive about: " + none);
        // Inside a chain too: a candle is a string over a comb.
        var candle = CompanionObtain.plan(level, List.of(new ItemStack(Items.SHEARS), new ItemStack(Items.STRING)), false, "minecraft:candle", 1, thing -> true, thing -> false);
        h.assertTrue(candle.steps().equals(List.of(new Step("harvest_hive", "minecraft:honeycomb", 1), new Step("craft_item", "minecraft:candle", 1))), "A candle: " + candle);
        h.succeed();
    }

    /** «Добудь 3 соты» at a full nest with no smoke under it: his campfire is set under the nest first, the combs are cut, the bee stays in. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void combsAreCutOverHisCampfireAndTheBeeStaysIn(GameTestHelper h) {
        ground(h);
        BlockPos nest = new BlockPos(20, GROUND + 3, 16);
        h.setBlock(nest, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
        BeehiveBlockEntity home = h.getBlockEntity(nest);
        home.addOccupant(h.spawn(EntityType.BEE, 20, GROUND + 1, 18));
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.SHEARS));
        c.setItem(1, new ItemStack(Items.CAMPFIRE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:honeycomb\",\"count\":3}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.HONEYCOMB) == 3 && h.getBlockState(nest).getValue(BeehiveBlock.HONEY_LEVEL) == 0, "Not three combs of the nest: " + future.join().report());
            h.assertTrue(CampfireBlock.isLitCampfire(h.getBlockState(new BlockPos(20, GROUND + 1, 16))) && count(c, Items.CAMPFIRE) == 0, "His campfire does not burn under the nest");
            h.assertTrue(home.getOccupantCount() == 1, "The bee was smoked out: the nest was cut with no smoke under it");
            h.assertTrue(count(c, Items.SHEARS) == 1 && c.getMainHandItem().getDamageValue() > 0, "The shears are not worn");
            h.assertTrue(future.join().report().contains("Костёр"), "The campfire he left is not told of: " + future.join().report());
        });
    }

    /** «Добудь бутылочку мёда» at a full nest over a campfire that burns: his bottle comes back full, his own campfire stays in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void honeyIsBottledOverACampfireThatBurns(GameTestHelper h) {
        ground(h);
        BlockPos nest = new BlockPos(20, GROUND + 3, 16);
        h.setBlock(nest, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
        h.setBlock(new BlockPos(20, GROUND + 1, 16), Blocks.CAMPFIRE);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.GLASS_BOTTLE));
        c.setItem(1, new ItemStack(Items.CAMPFIRE));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:honey_bottle\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.HONEY_BOTTLE) == 1 && count(c, Items.GLASS_BOTTLE) == 0 && h.getBlockState(nest).getValue(BeehiveBlock.HONEY_LEVEL) == 0,
                    "No honey for the bottle: " + future.join().report());
            h.assertTrue(count(c, Items.CAMPFIRE) == 1, "His campfire was spent where one burns already");
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
        // The least radius: the animals of the tests beside this one, beyond its walls, are out of his way.
        c.setSearchRadius(16);
        return c;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-chores", () -> false));
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

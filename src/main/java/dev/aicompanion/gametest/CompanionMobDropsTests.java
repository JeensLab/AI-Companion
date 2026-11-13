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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * the player 12.11: he should get everything a player gets. What a monster drops (string, bones, gunpowder, ender pearls, slime)
 * is one skill from the game's loot tables: the monsters of that kind about him are fought, a spider first struck by him too, and
 * the drops picked up; with none about he says so before anything starts.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMobDropsTests {
    private static final int GROUND = 5;

    /** Plan only: what a monster about him drops is fought for; what only a player's kill, a witch's luck or the water gives is not. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void whatAMonsterDropsIsPlannedAsAHunt(GameTestHelper h) {
        var level = h.getLevel();
        for (String item : List.of("minecraft:string", "minecraft:bone", "minecraft:gunpowder", "minecraft:ender_pearl", "minecraft:slime_ball", "minecraft:rotten_flesh")) {
            var plan = CompanionObtain.plan(level, List.of(new ItemStack(Items.STONE_AXE)), false, item, 3, thing -> false, thing -> true);
            h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("hunt_drop", item, 3))), item + ": " + plan);
        }
        var bow = CompanionObtain.plan(level, List.of(), true, "minecraft:bow", 1, thing -> false, thing -> true);
        h.assertTrue(!bow.refused() && bow.steps().contains(new CompanionObtain.Step("hunt_drop", "minecraft:string", 3))
                && bow.steps().getLast().equals(new CompanionObtain.Step("craft_item", "minecraft:bow", 1)), "Bow: " + bow);
        // Bare hands: a wooden sword is made first, once for the whole plan.
        var bare = CompanionObtain.plan(level, List.of(), true, "minecraft:string", 2, thing -> false, thing -> true);
        h.assertTrue(bare.steps().equals(List.of(new CompanionObtain.Step("gather_wood", "", 2), new CompanionObtain.Step("craft_item", "minecraft:wooden_sword", 1),
                new CompanionObtain.Step("hunt_drop", "minecraft:string", 2))), "String with bare hands: " + bare);
        // None about: said before anything starts, with who he looked for.
        var none = CompanionObtain.plan(level, List.of(), true, "minecraft:bow", 1, thing -> false, thing -> false);
        h.assertTrue(none.refused() && none.steps().isEmpty() && none.refusal().contains("Рядом не вижу") && none.refusal().contains("Spider"), "Bow with no spider about: " + none);
        // A fish and a squid are fought for what they drop, in the water.
        for (String item : List.of("minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish", "minecraft:ink_sac", "minecraft:glow_ink_sac")) {
            var plan = CompanionObtain.plan(level, List.of(new ItemStack(Items.STONE_SWORD)), false, item, 2, thing -> false, thing -> true);
            h.assertTrue(plan.steps().equals(List.of(new CompanionObtain.Step("hunt_drop", item, 2))), item + ": " + plan);
        }
        // Cod is the fish's own: neither a dolphin, nor a polar bear, nor a guardian is named for it.
        var nobody = CompanionObtain.plan(level, List.of(), false, "minecraft:cod", 1, thing -> false, thing -> false);
        h.assertTrue(nobody.refused() && nobody.refusal().contains("Cod") && !nobody.refusal().contains("Dolphin") && !nobody.refusal().contains("Polar") && !nobody.refusal().contains("Guardian"), "Cod: " + nobody);
        // A player's kill only (spider eye, blaze rod), a witch's odds and ends, a guardian's, a piglin's: no monster is fought for them.
        for (String item : List.of("minecraft:spider_eye", "minecraft:blaze_rod", "minecraft:glowstone_dust", "minecraft:glass_bottle",
                "minecraft:prismarine_shard", "minecraft:gold_nugget", "minecraft:nether_star")) {
            var plan = CompanionObtain.plan(level, List.of(), true, item, 1, thing -> true, thing -> true);
            h.assertTrue(plan.steps().stream().noneMatch(step -> step.type().equals("hunt_drop")), item + " is not hunted for: " + plan);
        }
        // What has a recipe is still made: arrows of flint, a stick and a feather, not of a skeleton.
        var arrows = CompanionObtain.plan(level, List.of(), true, "minecraft:arrow", 4, thing -> true, thing -> true);
        h.assertTrue(!arrows.refused() && arrows.steps().getLast().type().equals("craft_item") && arrows.steps().stream().noneMatch(step -> step.type().equals("hunt_drop")), "Arrows: " + arrows);
        h.succeed();
    }

    /** «Добудь 2 нитки» by day among spiders that leave him alone: he strikes first, picks the string up; the named spider is somebody's. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void spidersAreFoughtForStringAndTheNamedOneIsSpared(GameTestHelper h) {
        ground(h);
        for (int i = 0; i < 8; i++) weak(h, EntityType.SPIDER, 20 + i % 4 * 2, 12 + i / 4 * 8);
        Mob pet = weak(h, EntityType.SPIDER, 16, 22);
        pet.setCustomName(Component.literal("Пушок"));
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.IRON_SWORD));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:string\",\"count\":2}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.STRING) >= 2, "Not two string: " + count(c, Items.STRING) + ", " + future.join().report());
            h.assertTrue(pet.isAlive(), "The named spider was killed");
        });
    }

    /** A spider in a closed cave under his feet is not «about»: he says he sees none and stays where he is. */
    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void aSpiderUnderTheGroundIsNotAbout(GameTestHelper h) {
        ground(h);
        for (int x = 13; x <= 15; x++) for (int z = 15; z <= 17; z++) for (int y = GROUND - 3; y <= GROUND - 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        Mob spider = h.spawnWithNoFreeWill(EntityType.SPIDER, 14, GROUND - 3, 16);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.IRON_SWORD));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:string\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(future.join().report().contains("Рядом не вижу") && spider.isAlive() && !c.isWorking(), future.join().report());
        });
    }

    /** A slime is no Monster for the game, yet an enemy: its balls are fought for the same way. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void smallSlimesAreFoughtForSlimeBalls(GameTestHelper h) {
        ground(h);
        for (int i = 0; i < 8; i++) weak(h, EntityType.SLIME, 20 + i % 4 * 2, 12 + i / 4 * 8);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.IRON_SWORD));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:slime_ball\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            h.assertTrue(count(c, Items.SLIME_BALL) >= 1, "No slime ball: " + future.join().report());
        });
    }

    /** «Добудь 2 трески и чернила» in a pond: the fish and the squid are fought in the water; the dolphin and a cod let out of the player's bucket are left alone. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void fishAndASquidAreFoughtInAPond(GameTestHelper h) {
        ground(h);
        for (int x = 16; x <= 24; x++) for (int z = 12; z <= 20; z++) for (int y = 1; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        for (int i = 0; i < 3; i++) h.spawn(EntityType.COD, 18 + i * 2, 3, 14);
        h.spawn(EntityType.SQUID, 21, 2, 18);
        Mob dolphin = h.spawn(EntityType.DOLPHIN, 23, 3, 16);
        var pet = h.spawn(EntityType.COD, 17, 3, 13);
        pet.setFromBucket(true);
        CompanionEntity c = spawn(h, 12, 16);
        c.setItem(0, new ItemStack(Items.STONE_SWORD));
        var future = run(h, c, "[{\"action\":\"obtain_item\",\"item\":\"minecraft:cod\",\"count\":2},{\"action\":\"obtain_item\",\"item\":\"minecraft:ink_sac\",\"count\":1}]");
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Plan still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, Items.COD) >= 2 && count(c, Items.INK_SAC) >= 1, "Cod " + count(c, Items.COD) + ", ink " + count(c, Items.INK_SAC) + ": " + report);
            h.assertTrue(dolphin.isAlive() && dolphin.getHealth() == dolphin.getMaxHealth(), "The dolphin was struck");
            h.assertTrue(pet.isAlive() && pet.getHealth() == pet.getMaxHealth(), "The player's cod was struck");
        });
    }

    /** A monster that stands where it is put (the test is of his skill, not of its temper) and falls to one blow. */
    private static <T extends Mob> T weak(GameTestHelper h, EntityType<T> type, int x, int z) {
        T mob = h.spawnWithNoFreeWill(type, x, GROUND + 1, z);
        mob.setHealth(1);
        return mob;
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
        // The least radius: the monsters of the tests beside this one, beyond its walls, are out of his way.
        c.setSearchRadius(16);
        return c;
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> run(GameTestHelper h, CompanionEntity c, String json) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString(json));
        return CompanionPlanRunner.run(plan, new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-mob-drops", () -> false));
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        if (c.getMainHandItem().is(item)) count += c.getMainHandItem().getCount();
        return count;
    }
}

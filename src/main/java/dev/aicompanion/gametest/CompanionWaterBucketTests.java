package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The speedrunners' water bucket: falling to a landing that would hurt, with a water bucket in his bag, he pours it where he is about
 * to land a moment before he does, and scoops it back the moment he is down.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWaterBucketTests {
    /** What he may say after the bucket saved him (the owner's choice, 05.11). */
    private static final List<String> SAVED = List.of("Фух, ведро выручило.", "Выучил спидранерскую фишку — крутая, да?",
            "Ведро воды — и ни царапины.", "Как у спидранеров, видал?", "Вот это я понимаю — приземление.");

    /** Thirty blocks onto stone would kill him: with the bucket he lands unhurt, the water is back in it and none is left. */
    // Sky access: the framework otherwise roofs the template with barrier blocks five blocks up.
    @GameTest(template = "empty", timeoutTicks = 200, skyAccess = true)
    public static void aFallOfThirtyIsCaughtWithTheBucketAndTheWaterTakenBack(GameTestHelper h) {
        floor(h, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 31, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        h.succeedWhen(() -> {
            unhurtAndDry(h, c, 8);
            h.assertTrue(SAVED.contains(c.autonomyLine()), "Said: " + c.autonomyLine());
        });
    }

    /** Onto a bottom slab the water goes into the slab itself, as a player's bucket does; it is dry again after. */
    @GameTest(template = "empty", timeoutTicks = 200, skyAccess = true)
    public static void aFallOntoASlabIsCaughtInTheSlab(GameTestHelper h) {
        floor(h, Blocks.STONE);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.STONE_SLAB);
        CompanionEntity c = spawn(h, 4, 31, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        h.succeedWhen(() -> {
            unhurtAndDry(h, c, 8);
            h.assertTrue(h.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.STONE_SLAB), "The slab is gone");
        });
    }

    /** Flung sideways, as the dragon's ram flings him: the water goes where the flight ends, not under where it began. */
    @GameTest(template = "tall_tree", timeoutTicks = 200, skyAccess = true)
    public static void aFallWithASidewaysFlingIsCaughtWhereItEnds(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, 0, z, Blocks.STONE);
        // Eight blocks down and three blocks a tick across (the ram pushes four): he still flies fast as he lands.
        CompanionEntity c = spawn(h, 2, 9, 16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        var start = c.position();
        var across = h.absoluteVec(new net.minecraft.world.phys.Vec3(30.5, 9, 16.5)).subtract(start).normalize().scale(3);
        h.runAfterDelay(1, () -> c.setDeltaMovement(across));
        h.succeedWhen(() -> {
            h.assertTrue(c.position().distanceTo(start) > 15, "Not flung: " + c.position().distanceTo(start) + " blocks");
            unhurtAndDry(h, c, 32);
        });
    }

    /**
     * Onto ripe wheat: the water goes over the wheat, about his body, and washes nothing away (the owner, 05.11: the player's crops
     * before two hearts of his). The fall itself would trample the farmland.
     */
    @GameTest(template = "empty", timeoutTicks = 200, skyAccess = true)
    public static void aFallOntoWheatWashesNothingAway(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            h.setBlock(x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(x, 1, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        CompanionEntity c = spawn(h, 4, 10, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        h.succeedWhen(() -> {
            unhurtAndDry(h, c, 8);
            for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++)
                h.assertTrue(h.getBlockState(new BlockPos(x, 0, z)).is(Blocks.FARMLAND) && h.getBlockState(new BlockPos(x, 1, z)).is(Blocks.WHEAT), "Wheat lost at " + x + ", " + z);
        });
    }

    /** Tall grass fills all his height: with no free cell he takes a fall that does not kill him rather than wash it away. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallIntoTallGrassIsTakenRatherThanWashItAway(GameTestHelper h) {
        tallGrass(h);
        CompanionEntity c = spawn(h, 4, 10, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = {false};
        h.onEachTick(() -> poured[0] |= water(h, 8) != null || count(c, Items.BUCKET) > 0);
        h.runAfterDelay(40, () -> {
            h.assertTrue(!poured[0], "Poured into the grass");
            h.assertTrue(c.isAlive() && c.getHealth() < c.getMaxHealth() - 3, "The fall did not hurt: health " + c.getHealth());
            h.assertTrue(h.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.TALL_GRASS), "The grass is gone");
            h.succeed();
        });
    }

    /** The same fall would kill him: then the grass goes. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallIntoTallGrassThatWouldKillHimWashesItAway(GameTestHelper h) {
        tallGrass(h);
        CompanionEntity c = spawn(h, 4, 10, 4);
        c.setHealth(4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        h.succeedWhen(() -> {
            h.assertTrue(c.isAlive() && c.onGround() && c.getHealth() == 4, "Health " + c.getHealth());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && water(h, 8) == null, "Bag: " + c.getStoredItemsSummary() + ", water at " + water(h, 8));
        });
    }

    /**
     * Flung away the moment he poured (as the dragon does), he lands out of reach of his water: he says he goes for it, walks back and
     * scoops it up, or the next fling finds his bucket empty (the owner, 05.11).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 900, skyAccess = true)
    public static void flungAwayFromHisWaterHeGoesBackForIt(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, 0, z, Blocks.STONE);
        CompanionEntity c = spawn(h, 16, 31, 16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        List<String> said = said(h, c);
        flingOnPour(h, c, water -> {});
        h.succeedWhen(() -> {
            h.assertTrue(said.contains("Сбегаю за водой — ведро пустое."), "Did not say he goes for it: " + said);
            h.assertTrue(said.stream().noneMatch(SAVED::contains), "Boasted of a save the water did not make: " + said);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.BUCKET) == 0, "Bag: " + c.getStoredItemsSummary());
            h.assertTrue(water(h, 32) == null, "Water left at " + water(h, 32));
        });
    }

    /** His water gone meanwhile (the player scooped it): he says so, and the bucket stays empty. */
    @GameTest(template = "tall_tree", timeoutTicks = 300, skyAccess = true)
    public static void hisWaterGoneHeSaysTheBucketStaysEmpty(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, 0, z, Blocks.STONE);
        CompanionEntity c = spawn(h, 16, 31, 16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        List<String> said = said(h, c);
        flingOnPour(h, c, water -> h.setBlock(water, Blocks.AIR));
        h.succeedWhen(() -> {
            h.assertTrue(said.contains("Воду не забрал, ведро осталось пустым."), "Did not say the bucket is empty: " + said);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 0 && count(c, Items.BUCKET) == 1, "Bag: " + c.getStoredItemsSummary());
        });
    }

    /** Two falls within a minute: he says what the bucket did once (the owner, 05.11: a dragon flings him again and again). */
    @GameTest(template = "empty", timeoutTicks = 300, skyAccess = true)
    public static void twoFallsInAMinuteAreToldOnce(GameTestHelper h) {
        floor(h, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 31, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        List<String> said = said(h, c);
        int[] falls = {1};
        h.onEachTick(() -> {
            if (falls[0] == 1 && h.getTick() > 40 && c.onGround() && count(c, Items.WATER_BUCKET) == 1) {
                falls[0] = 2;
                var up = h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 31, 4.5));
                c.teleportTo(up.x, up.y, up.z);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(falls[0] == 2 && h.getTick() > 100, "Second fall not yet");
            unhurtAndDry(h, c, 8);
            h.assertTrue(said.stream().filter(SAVED::contains).count() == 1, "Said: " + said);
        });
    }

    /** Ten blocks onto hay costs two health (HayBlock.fallOn takes a fifth): not worth the bucket. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallOntoHayIsTakenWithoutTheBucket(GameTestHelper h) {
        floor(h, Blocks.HAY_BLOCK);
        CompanionEntity c = spawn(h, 4, 11, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = watchPour(h, c);
        h.runAfterDelay(40, () -> {
            h.assertTrue(!poured[0], "Poured the bucket over hay");
            h.assertTrue(c.getHealth() == c.getMaxHealth() - 2, "The hay did not take the fall: health " + c.getHealth());
            h.succeed();
        });
    }

    /** Boots of Feather Falling IV make ten blocks cost under two hearts (the enchantment's protection): not worth the bucket. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void featherFallingBootsMakeTenBlocksNotWorthTheBucket(GameTestHelper h) {
        floor(h, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 11, 4);
        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
        boots.enchant(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.FEATHER_FALLING), 4);
        c.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = watchPour(h, c);
        h.runAfterDelay(40, () -> {
            h.assertTrue(!poured[0], "Poured the bucket in Feather Falling boots");
            h.assertTrue(c.getHealth() < c.getMaxHealth() && c.getHealth() > c.getMaxHealth() - 4, "Health " + c.getHealth());
            h.succeed();
        });
    }

    /** Resistance IV takes four fifths of the ten blocks' seven (LivingEntity.getDamageAfterMagicAbsorb): not worth the bucket. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void resistanceMakesTenBlocksNotWorthTheBucket(GameTestHelper h) {
        floor(h, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 11, 4);
        c.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 600, 3));
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = watchPour(h, c);
        h.runAfterDelay(40, () -> {
            h.assertTrue(!poured[0], "Poured the bucket under Resistance IV");
            h.assertTrue(c.getHealth() < c.getMaxHealth() && c.getHealth() > c.getMaxHealth() - 2, "Health " + c.getHealth());
            h.succeed();
        });
    }

    /** A golden apple's absorption (four) takes most of eight blocks' five: his health loses one, not worth the bucket. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void absorptionMakesEightBlocksNotWorthTheBucket(GameTestHelper h) {
        floor(h, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 9, 4);
        c.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 600, 0));
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = watchPour(h, c);
        h.runAfterDelay(40, () -> {
            h.assertTrue(!poured[0], "Poured the bucket with absorption to spare");
            h.assertTrue(c.getHealth() == c.getMaxHealth() - 1 && c.getAbsorptionAmount() == 0, "Health " + c.getHealth() + ", absorption " + c.getAbsorptionAmount());
            h.succeed();
        });
    }

    /** Flung into a pond after pouring: the pond broke the fall, not his bucket, so he does not boast; he still fetches his water. */
    @GameTest(template = "tall_tree", timeoutTicks = 900, skyAccess = true)
    public static void flungIntoAPondHeDoesNotBoastButFetchesHisWater(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, 0, z, Blocks.STONE);
        for (int x = 25; x <= 27; x++) for (int z = 15; z <= 17; z++) {
            h.setBlock(x, 0, z, Blocks.WATER);
            h.setBlock(x, -1, z, Blocks.STONE);
        }
        CompanionEntity c = spawn(h, 16, 31, 16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        List<String> said = said(h, c);
        flingOnPour(h, c, water -> {
            var pond = h.absoluteVec(new net.minecraft.world.phys.Vec3(26.5, 0.2, 16.5));
            c.teleportTo(pond.x, pond.y, pond.z);
        });
        h.succeedWhen(() -> {
            h.assertTrue(said.stream().noneMatch(SAVED::contains), "Boasted of the pond: " + said);
            h.assertTrue(said.contains("Сбегаю за водой — ведро пустое."), "Did not go for his water: " + said);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1, "Bag: " + c.getStoredItemsSummary());
        });
    }

    /** Onto a stalagmite five blocks cost (5 + 2 - 3) x 2 = 8 or more (PointedDripstoneBlock.fallOn): worth the bucket. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallOfFiveOntoAStalagmiteIsCaught(GameTestHelper h) {
        floor(h, Blocks.STONE);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION, net.minecraft.core.Direction.UP));
        CompanionEntity c = spawn(h, 4, 7, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        h.succeedWhen(() -> {
            h.assertTrue(c.isAlive() && c.getHealth() == c.getMaxHealth(), "Hurt: health " + c.getHealth());
            h.assertTrue(h.getTick() > 30 && count(c, Items.WATER_BUCKET) == 1 && water(h, 8) == null, "Bag: " + c.getStoredItemsSummary() + ", water at " + water(h, 8));
        });
    }

    /** A cobweb on the way down ends the fall (it resets it, as ladders and powder snow do): nothing to pour for. */
    @GameTest(template = "empty", timeoutTicks = 200, skyAccess = true)
    public static void aCobwebOnTheWayDownIsNotPouredFor(GameTestHelper h) {
        floor(h, Blocks.STONE);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 2, z, Blocks.COBWEB);
        CompanionEntity c = spawn(h, 4, 25, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = watchPour(h, c);
        h.runAfterDelay(150, () -> {
            h.assertTrue(!poured[0], "Poured the bucket over a cobweb");
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "Hurt: health " + c.getHealth());
            h.succeed();
        });
    }

    /**
     * Fast from 140 blocks he pours only as a player can: on a block within reach of his eyes (4.5 to its outline: a bucket is used
     * through Item.getPlayerPOVHitResult, without the allowance of useItemOn), in the last tick if not before. Coming down faster than
     * that reach leaves him a tick, some falls cannot be caught, as a player's could not.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 300, skyAccess = true)
    public static void fromHighUpHePoursOnlyWithinReach(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, 0, z, Blocks.STONE);
        // Six falls from 140 to 145 blocks, each a column of its own: each comes to the ground at another moment of a tick.
        List<CompanionEntity> all = new java.util.ArrayList<>();
        double[] reach = new double[6];
        java.util.Arrays.fill(reach, -1);
        for (int i = 0; i < 6; i++) {
            CompanionEntity c = spawn(h, 3 + 5 * i, 141 + i, 16);
            c.setItem(0, new ItemStack(Items.WATER_BUCKET));
            all.add(c);
            int column = 3 + 5 * i, index = i;
            h.onEachTick(() -> {
                if (reach[index] >= 0 || count(c, Items.BUCKET) == 0) return;
                BlockPos water = null;
                for (BlockPos pos : BlockPos.betweenClosed(column - 2, 0, 14, column + 2, 3, 18))
                    if (h.getLevel().getFluidState(h.absolutePos(pos)).is(FluidTags.WATER)) { water = pos.immutable(); break; }
                if (water == null) return;
                // Where his eyes were when he poured: the start of this tick, before the move that came after the pour.
                var eyes = new net.minecraft.world.phys.Vec3(c.xo, c.yo + c.getEyeHeight(), c.zo);
                reach[index] = Math.sqrt(new net.minecraft.world.phys.AABB(h.absolutePos(water).below()).distanceToSqr(eyes));
            });
        }
        h.succeedWhen(() -> {
            int poured = 0;
            for (int i = 0; i < 6; i++) {
                CompanionEntity c = all.get(i);
                h.assertTrue(!c.isAlive() || c.onGround(), "Fall " + i + " not down yet");
                if (reach[i] < 0) continue;
                poured++;
                h.assertTrue(reach[i] <= 4.5, "Fall " + i + " poured from " + reach[i] + " blocks: " + java.util.Arrays.toString(reach));
                h.assertTrue(c.isAlive() && c.getHealth() == c.getMaxHealth() && count(c, Items.WATER_BUCKET) == 1,
                        "Fall " + i + " poured but: health " + c.getHealth() + ", bag " + c.getStoredItemsSummary());
            }
            h.assertTrue(poured >= 3, "Only " + poured + " of 6 poured: " + java.util.Arrays.toString(reach));
        });
    }

    /**
     * A column of twisting vines beside his fall, his side within a few hundredths of a block of it: the game counts a climbable block
     * only at his feet (LivingEntity.onClimbable), so the vines do not end this fall and the bucket must.
     */
    @GameTest(template = "empty", timeoutTicks = 200, skyAccess = true)
    public static void vinesBesideHisFallDoNotKeepTheBucketBack(GameTestHelper h) {
        floor(h, Blocks.STONE);
        for (int y = 1; y <= 25; y++) h.setBlock(5, y, 4, Blocks.TWISTING_VINES);
        CompanionEntity c = spawn(h, 4.8, 26, 4.5);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 30, "Not down yet");
            unhurtAndDry(h, c, 8);
        });
    }

    /**
     * The dragon's case: rammed up and away the moment he lands in his water, before he could scoop it. With a second bucket he
     * catches the next landing too, scoops that water, then goes back for the first: both buckets full, nothing left.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 900, skyAccess = true)
    public static void rammedAwayFromHisWaterHeCatchesTheNextFallWithTheSecondBucketAndFetchesBoth(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, 0, z, Blocks.STONE);
        CompanionEntity c = spawn(h, 16, 31, 16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        c.setItem(1, new ItemStack(Items.WATER_BUCKET));
        boolean[] rammed = {false};
        h.onEachTick(() -> {
            if (rammed[0] || count(c, Items.BUCKET) == 0 || !c.isInWater()) return;
            rammed[0] = true;
            // Down in his water (the fall reset), thirteen blocks up and ten across: the next landing would cost him ten.
            var up = h.absoluteVec(new net.minecraft.world.phys.Vec3(26.5, 14, 16.5));
            c.teleportTo(up.x, up.y, up.z);
            c.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        });
        h.succeedWhen(() -> {
            h.assertTrue(rammed[0], "Not rammed yet");
            h.assertTrue(c.isAlive() && c.getHealth() == c.getMaxHealth(), "Hurt: health " + c.getHealth());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 2 && count(c, Items.BUCKET) == 0, "Bag: " + c.getStoredItemsSummary());
            h.assertTrue(water(h, 32) == null, "Water left at " + water(h, 32));
        });
    }

    /** Into a pond the water breaks his fall by itself: nothing to pour for. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallIntoAPondIsNotPouredFor(GameTestHelper h) {
        floor(h, Blocks.STONE);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.WATER);
        CompanionEntity c = spawn(h, 4, 26, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = {false};
        h.onEachTick(() -> poured[0] |= count(c, Items.BUCKET) > 0);
        h.runAfterDelay(60, () -> {
            h.assertTrue(!poured[0], "Poured the bucket over a pond");
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "Hurt: health " + c.getHealth());
            h.succeed();
        });
    }

    /** Flung more than 48 blocks from his water he does not go back for it (the owner, 05.11): he says the bucket stays empty. */
    @GameTest(template = "large_search", timeoutTicks = 300, skyAccess = true)
    public static void flungFartherThanFortyEightBlocksHeLeavesHisWater(GameTestHelper h) {
        for (int x = 0; x < 72; x++) for (int z = 8; z < 24; z++) h.setBlock(x, 0, z, Blocks.STONE);
        CompanionEntity c = spawn(h, 10, 31, 16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        List<String> said = said(h, c);
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || count(c, Items.BUCKET) == 0) return;
            done[0] = true;
            var away = h.absoluteVec(new net.minecraft.world.phys.Vec3(66.5, 1, 16.5));
            c.teleportTo(away.x, away.y, away.z);
            c.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            c.resetFallDistance();
        });
        h.succeedWhen(() -> {
            h.assertTrue(said.contains("Воду не забрал, ведро осталось пустым."), "Did not give it up: " + said);
            h.assertTrue(!said.contains("Сбегаю за водой — ведро пустое."), "Went for water 50 blocks away: " + said);
            h.assertTrue(count(c, Items.BUCKET) == 1 && count(c, Items.WATER_BUCKET) == 0, "Bag: " + c.getStoredItemsSummary());
        });
    }

    /** With the player looking into his bag he does not walk off for his water; the bag closed, he goes. */
    @GameTest(template = "tall_tree", timeoutTicks = 900, skyAccess = true)
    public static void withHisBagOpenHeWaitsToFetchHisWater(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, 0, z, Blocks.STONE);
        CompanionEntity c = spawn(h, 16, 31, 16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        List<String> said = said(h, c);
        var viewer = h.makeMockServerPlayerInLevel();
        long[] openedAt = {-1};
        net.minecraft.world.phys.Vec3[] at = {null};
        flingOnPour(h, c, water -> { c.startOpen(viewer); openedAt[0] = h.getTick(); at[0] = c.position(); });
        h.onEachTick(() -> {
            if (openedAt[0] < 0 || h.getTick() != openedAt[0] + 100) return;
            h.assertTrue(!said.contains("Сбегаю за водой — ведро пустое.") && c.position().distanceTo(at[0]) < 1.5,
                    "Went for his water with his bag open: " + said + ", moved " + c.position().distanceTo(at[0]));
            c.stopOpen(viewer);
        });
        h.succeedWhen(() -> {
            h.assertTrue(openedAt[0] >= 0 && h.getTick() > openedAt[0] + 100, "The bag not closed yet");
            h.assertTrue(said.contains("Сбегаю за водой — ведро пустое."), "Did not go for it: " + said);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && water(h, 32) == null, "Bag: " + c.getStoredItemsSummary() + ", water at " + water(h, 32));
        });
    }

    /**
     * Into lava a block deep: the game does not end a fall in lava (it halves it a tick later, too late for this landing), so twenty
     * blocks would kill him there. The water goes over the lava, about his body: the fall is caught, the lava under it sets.
     */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallIntoShallowLavaIsCaughtOverIt(GameTestHelper h) {
        floor(h, Blocks.STONE);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, x >= 3 && x <= 5 && z >= 3 && z <= 5 ? Blocks.LAVA : Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 21, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        h.runAfterDelay(60, () -> {
            h.assertTrue(c.isAlive() && c.getHealth() > 10, "Not caught over the lava: health " + c.getHealth());
            h.succeed();
        });
    }

    /**
     * Onto farmland beside its water: the water's surface is below the farmland's top, so it does not count as him in water (Entity
     * .updateFluidHeightAndDoFluidPushing) and the fall would hurt him in full. He pours.
     */
    @GameTest(template = "empty", timeoutTicks = 200, skyAccess = true)
    public static void farmlandWaterBesideHisLandingDoesNotKeepTheBucketBack(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(5, 0, 4, Blocks.WATER);
        h.setBlock(5, -1, 4, Blocks.STONE);
        CompanionEntity c = spawn(h, 4.8, 21, 4.5);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 30 && c.isAlive() && c.onGround() && c.getHealth() == c.getMaxHealth(), "Hurt: health " + c.getHealth());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.BUCKET) == 0, "Bag: " + c.getStoredItemsSummary());
        });
    }

    /** Powder snow takes a fall whole (PowderSnowBlock.fallOn does no damage): nothing to pour for. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallOntoPowderSnowIsNotPouredFor(GameTestHelper h) {
        floor(h, Blocks.STONE);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.POWDER_SNOW);
        CompanionEntity c = spawn(h, 4, 21, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = {false};
        h.onEachTick(() -> poured[0] |= count(c, Items.BUCKET) > 0);
        h.runAfterDelay(60, () -> {
            h.assertTrue(!poured[0], "Poured the bucket over powder snow");
            h.assertTrue(c.getHealth() == c.getMaxHealth(), "Hurt: health " + c.getHealth());
            h.succeed();
        });
    }

    /** A carpet on hay: the game counts the hay under it (Entity.getOnPos), so ten blocks cost two: not worth the bucket. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallOntoACarpetOverHayIsTakenWithoutTheBucket(GameTestHelper h) {
        floor(h, Blocks.HAY_BLOCK);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.WHITE_CARPET);
        CompanionEntity c = spawn(h, 4, 11, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = watchPour(h, c);
        h.runAfterDelay(40, () -> {
            h.assertTrue(!poured[0], "Poured the bucket over a carpet on hay");
            h.assertTrue(c.getHealth() >= c.getMaxHealth() - 2, "The hay did not take the fall: health " + c.getHealth());
            h.succeed();
        });
    }

    /** Whether water ever appears in the template or an empty bucket in his bag, from now on. */
    private static boolean[] watchPour(GameTestHelper h, CompanionEntity c) {
        boolean[] poured = {false};
        h.onEachTick(() -> poured[0] |= water(h, 8) != null || count(c, Items.BUCKET) > 0);
        return poured;
    }

    /** What he says, line by line, from now on. */
    private static List<String> said(GameTestHelper h, CompanionEntity c) {
        List<String> said = new java.util.ArrayList<>();
        int[] serial = {c.autonomyLineSerial()};
        h.onEachTick(() -> {
            if (c.autonomyLineSerial() != serial[0]) { serial[0] = c.autonomyLineSerial(); said.add(c.autonomyLine()); }
        });
        return said;
    }

    /**
     * The tick he pours (an empty bucket in his bag, water over the floor), he is set down ten blocks away on the ground, out of his water's reach (a fling ending softly), and
     * {@code then} is done to the water's cell.
     */
    private static void flingOnPour(GameTestHelper h, CompanionEntity c, java.util.function.Consumer<BlockPos> then) {
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || count(c, Items.BUCKET) == 0) return;
            BlockPos water = null;
            for (BlockPos pos : BlockPos.betweenClosed(0, 1, 0, 31, 4, 31))
                if (h.getLevel().getFluidState(h.absolutePos(pos)).is(FluidTags.WATER)) { water = pos.immutable(); break; }
            if (water == null) return;
            done[0] = true;
            var away = h.absoluteVec(new net.minecraft.world.phys.Vec3(26.5, 1, 16.5));
            c.teleportTo(away.x, away.y, away.z);
            c.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            c.resetFallDistance();
            then.accept(water);
        });
    }

    /**
     * His water walled in glass: he sees it but cannot reach it, as a player's bucket cannot through a wall. After thirty seconds he
     * gives it up and says so; it stays where it is.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 900, skyAccess = true)
    public static void hisWaterBehindGlassIsGivenUpNotScoopedThroughIt(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, 0, z, Blocks.STONE);
        CompanionEntity c = spawn(h, 16, 31, 16);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        List<String> said = said(h, c);
        flingOnPour(h, c, water -> {
            for (var side : net.minecraft.core.Direction.values()) if (side != net.minecraft.core.Direction.DOWN) h.setBlock(water.relative(side), Blocks.GLASS);
        });
        h.succeedWhen(() -> {
            h.assertTrue(said.contains("Воду не забрал, ведро осталось пустым."), "Did not give it up: " + said);
            h.assertTrue(count(c, Items.WATER_BUCKET) == 0 && count(c, Items.BUCKET) == 1, "Bag: " + c.getStoredItemsSummary());
            h.assertTrue(water(h, 32) != null, "The water is gone from behind the glass");
        });
    }

    private static void tallGrass(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            h.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            h.setBlock(x, 1, z, Blocks.TALL_GRASS.defaultBlockState());
            h.setBlock(x, 2, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }

    /** Down, alive and unhurt, the water back in the bucket and none left within {@code size} blocks. */
    private static void unhurtAndDry(GameTestHelper h, CompanionEntity c, int size) {
        h.assertTrue(c.isAlive() && c.onGround() && c.getHealth() == c.getMaxHealth(), "Hurt: health " + c.getHealth() + " at " + h.relativePos(c.blockPosition()).toShortString());
        h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && count(c, Items.BUCKET) == 0, "Bag: " + c.getStoredItemsSummary());
        h.assertTrue(water(h, size) == null, "Water left at " + water(h, size));
    }

    /**
     * Six and a half blocks cost him three health, under the two hearts that are worth the bucket (the owner, 05.11): he takes them.
     * The game does not count the landing move into the fall (Entity.checkFallDamage); counted, it would make these four.
     */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallOfSixAndAHalfIsTakenWithoutTheBucket(GameTestHelper h) {
        floor(h, Blocks.STONE);
        CompanionEntity c = spawn(h, 4.5, 7.4, 4.5);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = {false};
        h.onEachTick(() -> poured[0] |= water(h, 8) != null || count(c, Items.BUCKET) > 0);
        h.runAfterDelay(40, () -> {
            h.assertTrue(!poured[0], "Poured the bucket for a fall of six and a half");
            h.assertTrue(c.getHealth() == c.getMaxHealth() - 3, "The fall did not cost three: health " + c.getHealth());
            h.succeed();
        });
    }

    /** The same six blocks with two health left would kill him: that is worth the bucket. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallOfSixThatWouldKillHimIsCaught(GameTestHelper h) {
        floor(h, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 7, 4);
        c.setHealth(2);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        h.succeedWhen(() -> {
            h.assertTrue(c.isAlive() && c.onGround() && c.getHealth() == 2, "Health " + c.getHealth());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1 && water(h, 8) == null, "Bag: " + c.getStoredItemsSummary() + ", water at " + water(h, 8));
        });
    }

    private static void floor(GameTestHelper h, Block block) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, block);
    }

    /** The first water within {@code size} blocks across and four up, or null. */
    private static BlockPos water(GameTestHelper h, int size) {
        for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, size - 1, 4, size - 1))
            if (h.getLevel().getFluidState(h.absolutePos(pos)).is(FluidTags.WATER)) return pos.immutable();
        return null;
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        return count;
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int y, int z) { return spawn(h, x + .5, y, z + .5); }

    private static CompanionEntity spawn(GameTestHelper h, double x, double y, double z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), new net.minecraft.world.phys.Vec3(x, y, z));
        var owner = h.makeMockServerPlayerInLevel();
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5));
        owner.teleportTo(at.x, at.y, at.z);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        return companion;
    }
}

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
        flingOnPour(h, c, false);
        h.succeedWhen(() -> {
            h.assertTrue(said.contains("Сбегаю за водой — ведро пустое."), "Did not say he goes for it: " + said);
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
        flingOnPour(h, c, true);
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
     * The tick his water appears, he is set down ten blocks away on the ground, out of his water's reach (a fling ending softly);
     * with {@code scooped} the water is taken away then too.
     */
    private static void flingOnPour(GameTestHelper h, CompanionEntity c, boolean scooped) {
        boolean[] done = {false};
        h.onEachTick(() -> {
            BlockPos water = water(h, 32);
            if (done[0] || water == null) return;
            done[0] = true;
            var away = h.absoluteVec(new net.minecraft.world.phys.Vec3(26.5, 1, 16.5));
            c.teleportTo(away.x, away.y, away.z);
            c.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            c.resetFallDistance();
            if (scooped) h.setBlock(water, Blocks.AIR);
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

    /** Six blocks cost him three health, under the two hearts that are worth the bucket (the owner, 05.11): he takes them. */
    @GameTest(template = "empty", timeoutTicks = 100, skyAccess = true)
    public static void aFallOfSixIsTakenWithoutTheBucket(GameTestHelper h) {
        floor(h, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 7, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        boolean[] poured = {false};
        h.onEachTick(() -> poured[0] |= water(h, 8) != null || count(c, Items.BUCKET) > 0);
        h.runAfterDelay(40, () -> {
            h.assertTrue(!poured[0], "Poured the bucket for a fall of six");
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

    private static CompanionEntity spawn(GameTestHelper h, int x, int y, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5));
        owner.teleportTo(at.x, at.y, at.z);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        return companion;
    }
}

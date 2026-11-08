package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The speedrunners' boat: falling to a landing that would hurt where his water bucket cannot catch him, with a boat in his bag he
 * puts it down where he lands and gets into it as he does; a moment later he steps out, breaks it and picks it up.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBoatClutchTests {
    /** What he may say after the boat saved him (08.11). */
    private static final List<String> SAVED = List.of("Лодка лучше парашюта, видал?", "Спидранерская лодка — и ни царапины.",
            "Лодочный клатч, как у спидранеров.");

    /** Thirty blocks onto stone with a boat and no bucket: he lands in the boat unhurt, and the boat is back in his bag. */
    // Sky access: the framework otherwise roofs the template with barrier blocks five blocks up.
    @GameTest(template = "empty", timeoutTicks = 300, skyAccess = true)
    public static void aFallOfThirtyWithoutABucketIsCaughtInTheBoatAndTheBoatTakenBack(GameTestHelper h) {
        floor(h);
        CompanionEntity c = spawn(h, 4, 31, 4);
        c.setItem(0, new ItemStack(Items.OAK_BOAT));
        boolean[] seated = watchSeat(h, c);
        h.succeedWhen(() -> {
            unhurtWithTheBoatBack(c, seated[0], h.getLevel(), h.getBounds());
            h.assertTrue(SAVED.contains(c.autonomyLine()), "Said: " + c.autonomyLine());
        });
    }

    /**
     * The most common hurtful falls, eight to eleven blocks (and fifteen): in the tick he lands his feet are often lower than a boat is
     * high, his body where the boat goes. A player puts it down a tick before and gets in as he comes down; he is caught all the same.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 300, skyAccess = true)
    public static void fallsOfEightToElevenAndFifteenAreCaughtToo(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, 0, z, Blocks.STONE);
        int[] heights = {8, 9, 10, 11, 15};
        List<CompanionEntity> all = new java.util.ArrayList<>();
        List<boolean[]> seats = new java.util.ArrayList<>();
        for (int i = 0; i < heights.length; i++) {
            CompanionEntity c = spawn(h, 3 + 6 * i, 1 + heights[i], 16);
            own(h, c, new Vec3(5.5 + 6 * i, 1, 16.5));
            c.setItem(0, new ItemStack(Items.OAK_BOAT));
            all.add(c);
            seats.add(watchSeat(h, c));
        }
        h.succeedWhen(() -> {
            for (int i = 0; i < heights.length; i++) {
                CompanionEntity c = all.get(i);
                check(seats.get(i)[0] && c.isAlive() && c.onGround() && !c.isPassenger() && c.getHealth() == c.getMaxHealth()
                        && count(c, Items.OAK_BOAT) == 1, "Fall of " + heights[i] + ": seated " + seats.get(i)[0] + ", health "
                        + c.getHealth() + ", bag " + c.getStoredItemsSummary());
            }
            check(boats(h.getLevel(), h.getBounds()).isEmpty(), "A boat is left: " + boats(h.getLevel(), h.getBounds()));
        });
    }

    /** The player far off: once down he would go after them, but the boat is broken and back in his bag first. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, skyAccess = true)
    public static void withThePlayerFarOffHeStillTakesTheBoatBack(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, 0, z, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 31, 4);
        own(h, c, new Vec3(28.5, 1, 28.5));
        c.setItem(0, new ItemStack(Items.OAK_BOAT));
        boolean[] seated = watchSeat(h, c);
        h.succeedWhen(() -> unhurtWithTheBoatBack(c, seated[0], h.getLevel(), h.getBounds()));
    }

    /**
     * Told to stay («стой» puts his pickup aside till the next order), he breaks the boat from a couple of blocks off: he walks to
     * where it fell and takes it all the same.
     */
    @GameTest(template = "empty", timeoutTicks = 300, skyAccess = true)
    public static void toldToStayHeStillWalksToTheBoatHeBrokeAndTakesIt(GameTestHelper h) {
        floor(h);
        CompanionEntity c = spawn(h, 4, 31, 4);
        c.setFollowing(false);
        c.setItem(0, new ItemStack(Items.OAK_BOAT));
        boolean[] seated = watchSeat(h, c), moved = {false};
        // The tick he is out of the boat he is set two and a half blocks off it, still within reach to break it.
        h.onEachTick(() -> {
            if (moved[0] || !seated[0] || c.isPassenger()) return;
            moved[0] = true;
            c.teleportTo(c.getX() + 2.5, c.getY(), c.getZ());
        });
        h.succeedWhen(() -> {
            check(moved[0], "He never stepped out of the boat");
            unhurtWithTheBoatBack(c, seated[0], h.getLevel(), h.getBounds());
        });
    }

    /**
     * The boat broken on the edge of a step, its drop falls a block lower beside it (seen in AgentTest 08.11): he steps down and takes
     * it. The drop is set there the tick it appears; he lands on the upper step.
     */
    @GameTest(template = "empty", timeoutTicks = 300, skyAccess = true)
    public static void theBoatFallenDownAStepIsTakenToo(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) { h.setBlock(x, 0, z, Blocks.STONE); if (x < 4) h.setBlock(x, 1, z, Blocks.STONE); }
        CompanionEntity c = spawn(h, 2, 32, 4);
        c.setItem(0, new ItemStack(Items.OAK_BOAT));
        boolean[] seated = watchSeat(h, c), moved = {false};
        h.onEachTick(() -> {
            if (moved[0]) return;
            for (var drop : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, h.getBounds(), e -> e.getItem().is(Items.OAK_BOAT))) {
                moved[0] = true;
                var at = h.absoluteVec(new Vec3(5.5, 1, 4.5));
                drop.teleportTo(at.x, at.y, at.z);
                drop.setDeltaMovement(Vec3.ZERO);
            }
        });
        h.succeedWhen(() -> {
            check(moved[0], "No boat was broken");
            unhurtWithTheBoatBack(c, seated[0], h.getLevel(), h.getBounds());
        });
    }

    /** With a water bucket too, in the Overworld the water goes first: the boat never leaves his bag. */
    @GameTest(template = "empty", timeoutTicks = 300, skyAccess = true)
    public static void withABucketTooTheWaterGoesFirst(GameTestHelper h) {
        floor(h);
        CompanionEntity c = spawn(h, 4, 31, 4);
        c.setItem(0, new ItemStack(Items.WATER_BUCKET));
        c.setItem(1, new ItemStack(Items.OAK_BOAT));
        boolean[] seated = watchSeat(h, c), poured = {false}, boatOut = {false};
        h.onEachTick(() -> {
            poured[0] |= count(c, Items.BUCKET) > 0;
            boatOut[0] |= count(c, Items.OAK_BOAT) == 0 || !boats(h.getLevel(), h.getBounds()).isEmpty();
        });
        h.succeedWhen(() -> {
            h.assertTrue(poured[0], "The bucket was not poured");
            h.assertTrue(!seated[0] && !boatOut[0], "The boat came out though the bucket caught the fall");
            h.assertTrue(c.isAlive() && c.onGround() && c.getHealth() == c.getMaxHealth(), "Hurt: health " + c.getHealth());
            h.assertTrue(count(c, Items.WATER_BUCKET) == 1, "Bag: " + c.getStoredItemsSummary());
        });
    }

    /**
     * In the Nether the water boils away, so a full bucket is no help there: thirty blocks onto netherrack, he is caught by the boat,
     * and the bucket stays full.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void inTheNetherTheBoatCatchesWhatTheBucketCannot(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        // Far from every other test's place there, in a shut box of netherrack (no lava runs in), its floor at y 40.
        BlockPos base = new BlockPos(3000, 40, 3000);
        ChunkPos chunk = new ChunkPos(base);
        nether.setChunkForced(chunk.x, chunk.z, true);
        AABB area = new AABB(base).inflate(12, 0, 12).expandTowards(0, 40, 0);
        CompanionEntity[] c = {null};
        boolean[] seated = {false};
        h.onEachTick(() -> {
            if (c[0] != null) { seated[0] |= c[0].getVehicle() instanceof Boat; return; }
            // The world outlives a test: what an earlier run left there is cleared once its entities are loaded, after the chunk.
            if (!nether.areEntitiesLoaded(chunk.toLong())) return;
            nether.getEntities((Entity) null, area, e -> !(e instanceof Player)).forEach(Entity::discard);
            for (int x = -1; x <= 8; x++) for (int z = -1; z <= 8; z++) for (int y = 0; y <= 36; y++) {
                boolean inside = x >= 0 && x < 8 && z >= 0 && z < 8 && y > 0 && y < 36;
                nether.setBlockAndUpdate(base.offset(x, y, z), (inside ? Blocks.AIR : Blocks.NETHERRACK).defaultBlockState());
            }
            c[0] = new CompanionEntity(AICompanionMod.COMPANION.get(), nether);
            c[0].moveTo(base.getX() + 4.5, base.getY() + 31, base.getZ() + 4.5, 0, 0);
            own(h, c[0]);
            nether.addFreshEntity(c[0]);
            c[0].setItem(0, new ItemStack(Items.WATER_BUCKET));
            c[0].setItem(1, new ItemStack(Items.OAK_BOAT));
        });
        h.succeedWhen(() -> {
            check(c[0] != null, "The Nether's entities are not loaded yet");
            unhurtWithTheBoatBack(c[0], seated[0], nether, area);
            check(count(c[0], Items.WATER_BUCKET) == 1, "The bucket is not full: " + c[0].getStoredItemsSummary());
            c[0].discard();
            nether.setChunkForced(chunk.x, chunk.z, false);
        });
    }

    /** Down a shaft one block wide a boat has no room, as a player's would not: he takes the fall and the boat stays in his bag. */
    @GameTest(template = "empty", timeoutTicks = 200, skyAccess = true)
    public static void downAShaftWithNoRoomForTheBoatHeTakesTheFall(GameTestHelper h) {
        floor(h);
        for (int y = 1; y <= 14; y++) for (int x = 3; x <= 5; x++) for (int z = 3; z <= 5; z++)
            if (x != 4 || z != 4) h.setBlock(x, y, z, Blocks.STONE);
        CompanionEntity c = spawn(h, 4, 13, 4);
        c.setItem(0, new ItemStack(Items.OAK_BOAT));
        boolean[] seated = watchSeat(h, c);
        h.runAfterDelay(60, () -> {
            h.assertTrue(!seated[0] && count(c, Items.OAK_BOAT) == 1 && boats(h.getLevel(), h.getBounds()).isEmpty(),
                    "A boat came out in a shaft it has no room in: bag " + c.getStoredItemsSummary());
            h.assertTrue(c.getHealth() < c.getMaxHealth(), "Twelve blocks cost him nothing: health " + c.getHealth());
            h.succeed();
        });
    }

    /** Down, alive and unhurt, he sat in the boat on the way, it is back in his bag, and no boat or water is left about. */
    private static void unhurtWithTheBoatBack(CompanionEntity c, boolean seated, Level level, AABB area) {
        check(seated, "He never sat in a boat");
        check(c.isAlive() && c.onGround() && !c.isPassenger() && c.getHealth() == c.getMaxHealth(),
                "Hurt or not down: health " + c.getHealth() + ", riding " + c.getVehicle());
        check(count(c, Items.OAK_BOAT) == 1, "Bag: " + c.getStoredItemsSummary());
        check(boats(level, area).isEmpty(), "A boat is left: " + boats(level, area));
        check(BlockPos.betweenClosedStream(area.inflate(0, 4, 0)).noneMatch(pos -> level.getFluidState(pos).is(FluidTags.WATER)), "Water is left");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    /** Whether he ever sits in a boat from now on. */
    private static boolean[] watchSeat(GameTestHelper h, CompanionEntity c) {
        boolean[] seated = {false};
        h.onEachTick(() -> seated[0] |= c.getVehicle() instanceof Boat);
        return seated;
    }

    private static List<Boat> boats(Level level, AABB area) { return level.getEntitiesOfClass(Boat.class, area.inflate(2, 40, 2)); }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        return count;
    }

    /** Him, with a player of his own at the template's corner. */
    private static CompanionEntity spawn(GameTestHelper h, int x, int y, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), new Vec3(x + .5, y, z + .5));
        own(h, companion);
        return companion;
    }

    private static void own(GameTestHelper h, CompanionEntity companion) { own(h, companion, new Vec3(1.5, 1, 1.5)); }

    /** A player of his own, standing there in the template: he is the one Oleg of that player (one player each is set again). */
    private static void own(GameTestHelper h, CompanionEntity companion, Vec3 where) {
        var owner = h.makeMockServerPlayerInLevel();
        var at = h.absoluteVec(where);
        owner.teleportTo(at.x, at.y, at.z);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
    }
}

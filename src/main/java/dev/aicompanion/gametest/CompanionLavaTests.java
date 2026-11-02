package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Lava the way a player reads it (CompanionLava): a lake that stands still is a wall to walk along, lava that is running, fire
 * and magma are not; his things after a death are fetched unless they lie in lava or lava runs beside them.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLavaTests {
    private static final int LAKE_X1 = 5, LAKE_X2 = 13, LAKE_Z1 = 11, LAKE_Z2 = 17;
    /** The lava is given this long to stop (a fluid in the Overworld is touched every 30 ticks). */
    private static final int SETTLE = 60;

    @AfterBatch(batch = "lava_death") public static void afterDeath(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "lava_in_lava") public static void afterInLava(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "lava_running") public static void afterRunning(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /**
     * A lake stands still beside the road: along the shore it is 14 blocks, round the lake more than twice that. He walks the shore
     * like a player walks it, a block from the lava, and does not burn.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void heWalksAlongAStillLakeInsteadOfRoundIt(GameTestHelper h) {
        shoreArena(h, Blocks.LAVA);
        walkAcross(h, true);
    }

    /** The same road, but the lava is running (it has a tick of its own scheduled every tick): he keeps off it and goes round. */
    @GameTest(template = "tall_tree", batch = "lava_running", timeoutTicks = 1200)
    public static void heGoesRoundLavaThatIsStillRunning(GameTestHelper h) {
        shoreArena(h, Blocks.LAVA);
        h.onEachTick(() -> {
            for (int x = LAKE_X1; x <= LAKE_X2; x++) for (int z = LAKE_Z1; z <= LAKE_Z2; z++)
                h.getLevel().scheduleTick(h.absolutePos(new BlockPos(x, 1, z)), Fluids.LAVA, 20);
        });
        walkAcross(h, false);
    }

    /** Magma is no lake: the price of the shore is the game's own, as before. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void heGoesRoundMagmaAsBefore(GameTestHelper h) {
        shoreArena(h, Blocks.MAGMA_BLOCK);
        walkAcross(h, false);
    }

    /**
     * He died at the lake; his things lie on the shore two blocks from the lava. The place is judged by what the lava does, not by
     * the distance to it: he fetches them, picks them up for real and does not burn.
     */
    @GameTest(template = "tall_tree", batch = "lava_death", timeoutTicks = 2400)
    public static void heFetchesThingsLyingAtAStillLake(GameTestHelper h) {
        var f = deathArena(h);
        ItemEntity things = drop(h, f, Items.DIAMOND, 3, 10.5, 2.5, 16.5);
        died(h, f, new BlockPos(10, 2, 16));
        boolean[] burnt = {false};
        h.onEachTick(() -> burnt[0] |= f.c.isOnFire() || f.c.isInLava());
        h.succeedWhen(() -> {
            h.assertTrue(!things.isAlive() && count(f.c, Items.DIAMOND) == 3, "Did not pick his things up: " + f.c.autonomy().said() + " " + f.c.getStoredItemsSummary());
            h.assertTrue(!burnt[0] && f.c.isAlive(), "Burnt fetching them");
        });
    }

    /** Things that lie in the lava itself stay there: he does not go for them and does not step into the lake. */
    @GameTest(template = "tall_tree", batch = "lava_in_lava", timeoutTicks = 1200)
    public static void heLeavesThingsLyingInTheLava(GameTestHelper h) {
        var f = deathArena(h);
        // Netherite does not burn: it floats on the lava the whole test.
        ItemEntity things = drop(h, f, Items.NETHERITE_INGOT, 1, 6.5, 1.6, 16.5);
        died(h, f, new BlockPos(6, 2, 16));
        boolean[] burnt = {false};
        h.onEachTick(() -> burnt[0] |= f.c.isOnFire() || f.c.isInLava());
        h.runAfterDelay(500, () -> {
            h.assertTrue(things.isAlive() && things.getItem().is(Items.NETHERITE_INGOT), "The things are gone from the lava");
            h.assertTrue(!burnt[0] && f.c.isAlive(), "Went into the lava after them");
            h.assertTrue(f.c.autonomy().said().stream().anyMatch(line -> line.contains("Мои вещи остались там") && line.contains("лав")), "Not told why: " + f.c.autonomy().said());
            h.succeed();
        });
    }

    /** Part of his things lie on the shore, part in the lava: he goes for the first and says in the same line that the rest stays. */
    @GameTest(template = "tall_tree", batch = "lava_half", timeoutTicks = 2400)
    public static void heSaysWhenPartOfHisThingsAreLeftInTheLava(GameTestHelper h) {
        var f = deathArena(h);
        drop(h, f, Items.DIAMOND, 3, 10.5, 2.5, 16.5);
        // Netherite does not burn: it floats on the lava the whole test.
        ItemEntity inLava = drop(h, f, Items.NETHERITE_INGOT, 1, 6.5, 1.6, 16.5);
        died(h, f, new BlockPos(10, 2, 16));
        h.succeedWhen(() -> {
            h.assertTrue(f.c.autonomy().said().stream().anyMatch(line -> line.contains("Иду за своими вещами") && line.contains("лав")), "Not told that part stays: " + f.c.autonomy().said());
            h.assertTrue(inLava.isAlive(), "The things in the lava are gone");
        });
    }

    // ---- Arenas ----

    /**
     * Bedrock, a stone floor, walkers on y 2. A walled yard (x 1..17, z 10..23) with a lake of {@code liquid} in its middle, set
     * into the floor: the only short way from the west end to the east end is the shore row z 10, next to the lake; the other is
     * round the lake's south side, 30 blocks and more.
     */
    private static void shoreArena(GameTestHelper h, Block liquid) {
        box(h, 0, 0, 0, 31, 10, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 1, 31, Blocks.STONE);
        box(h, 0, 2, 9, 18, 4, 9, Blocks.STONE);
        box(h, 0, 2, 24, 18, 4, 24, Blocks.STONE);
        box(h, 0, 2, 9, 0, 4, 24, Blocks.STONE);
        box(h, 18, 2, 9, 18, 4, 24, Blocks.STONE);
        box(h, LAKE_X1, 1, LAKE_Z1, LAKE_X2, 1, LAKE_Z2, liquid);
    }

    /** From the middle of the shore row the shore route stays within 7 blocks, the detour round the lake goes 9 and more away. */
    private static void walkAcross(GameTestHelper h, boolean alongTheShore) {
        CompanionEntity c = spawn(h, 2, 2, 10);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        BlockPos stand = h.absolutePos(new BlockPos(16, 2, 10));
        double[] farthest = {0};
        net.minecraft.world.phys.Vec3 middle = net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(9, 2, 10)));
        boolean[] burnt = {false};
        h.onEachTick(() -> {
            farthest[0] = Math.max(farthest[0], c.position().distanceTo(middle));
            burnt[0] |= c.isOnFire() || c.isInLava();
        });
        h.runAfterDelay(SETTLE, () -> {
            player.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
            h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted");
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE && !c.isWorking() && c.didLastTaskSucceed() && c.distanceToSqr(player) <= 16, "Not there yet: " + c.getLastTaskResult() + " at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(!burnt[0] && c.isAlive(), "Burnt on the way");
            h.assertTrue(alongTheShore ? farthest[0] <= 8 : farthest[0] >= 9, (alongTheShore ? "Went round the lake: " : "Walked the shore: ") + "farthest " + Math.round(farthest[0]) + " blocks from the middle of the shore");
        });
    }

    /** A yard with a bench and a lake of still lava at x 4..8, set into the floor; he has the ingots to finish the goal at the bench. */
    private static Fixture deathArena(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 10, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 1, 31, Blocks.STONE);
        box(h, 4, 1, 12, 8, 1, 20, Blocks.LAVA);
        h.setBlock(new BlockPos(17, 2, 16), Blocks.CRAFTING_TABLE);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 2, 16);
        var p = survivalPlayer(h);
        p.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5); c.setOwnerId(p.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID()); CompanionEvents.getOwnedCompanion(p);
        c.setItem(0, new ItemStack(Items.IRON_INGOT, 29)); c.setItem(1, new ItemStack(Items.STICK, 3));
        return new Fixture(h, c, p);
    }

    private static ItemEntity drop(GameTestHelper h, Fixture f, Item item, int count, double x, double y, double z) {
        BlockPos at = h.absolutePos(new BlockPos(0, 0, 0));
        ItemEntity entity = new ItemEntity(h.getLevel(), at.getX() + x, at.getY() + y, at.getZ() + z, new ItemStack(item, count), 0, 0, 0);
        entity.getPersistentData().putUUID("aicompanion:belongings", f.p.getUUID());
        entity.setUnlimitedLifetime();
        h.getLevel().addFreshEntity(entity);
        return entity;
    }

    /** The goal runs; its owner's things lie where he died, the place is told to the goal after the lava has stopped. */
    private static void died(GameTestHelper h, Fixture f, BlockPos place) {
        var result = CompanionEvents.executeAction(f.p, "pursue_goal", "gear_up", 1);
        h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + result.message());
        h.runAfterDelay(SETTLE, () -> CompanionAgenda.died(h.getLevel().getServer(), f.p.getUUID(), h.getLevel().dimension(), h.absolutePos(place)));
    }

    private record Fixture(GameTestHelper h, CompanionEntity c, ServerPlayer p) { }

    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setInvulnerable(true);
        return p;
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int y, int z) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x, y, z));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        return c;
    }

    private static int count(CompanionEntity c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        for (var slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (c.getItemBySlot(slot).is(item)) n += c.getItemBySlot(slot).getCount();
        return n;
    }

    private static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(new BlockPos(x, y, z), block);
    }
}

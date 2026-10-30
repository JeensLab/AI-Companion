package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * After the player through a portal, on foot: the player leaves through the real portal block (a mock player is sent over the way the
 * game sends one), Oleg walks there and the game takes him over, one and the same with all he carries. «Стой» and an order keep him.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPortalTests {
    private static final int GROUND = 5;

    @AfterBatch(batch = "portal")
    public static void afterPortal(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "portal")
    public static void heGoesThroughANetherPortalAfterThePlayer(GameTestHelper h) {
        ground(h);
        netherPortal(h);
        CompanionEntity companion = spawn(h, 6, 16);
        companion.setItem(0, new ItemStack(Items.DIAMOND, 5));
        UUID id = companion.getUUID();
        var server = h.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        Player owner = stepInto(h, companion, new BlockPos(15, GROUND + 1, 16));
        leaveFor(h, owner, nether, 0, 100, 0);
        h.succeedWhen(() -> {
            h.assertTrue(nether.getEntity(id) instanceof CompanionEntity, "Still at " + (h.getLevel().getEntity(id) == null ? "nowhere" : h.getLevel().getEntity(id).blockPosition()));
            h.assertTrue(h.getLevel().getEntity(id) == null, "He is in two worlds");
            CompanionEntity over = (CompanionEntity) nether.getEntity(id);
            h.assertTrue(over.getItem(0).is(Items.DIAMOND) && over.getItem(0).getCount() == 5, "The bag did not come along: " + over.getItem(0));
            h.assertTrue(over.isFollowing(), "He stopped following");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "portal")
    public static void heGoesThroughAnEndPortalAfterThePlayer(GameTestHelper h) {
        ground(h);
        for (int x = 14; x <= 18; x++) for (int z = 14; z <= 18; z++) {
            boolean rim = x == 14 || x == 18 || z == 14 || z == 18;
            h.setBlock(new BlockPos(x, GROUND + 1, z), rim ? Blocks.END_PORTAL_FRAME : Blocks.END_PORTAL);
        }
        CompanionEntity companion = spawn(h, 6, 16);
        UUID id = companion.getUUID();
        var server = h.getLevel().getServer();
        var end = server.getLevel(Level.END);
        h.assertTrue(end != null, "No End to test with");
        Player owner = stepInto(h, companion, new BlockPos(16, GROUND + 2, 16));
        leaveFor(h, owner, end, 100, 50, 0);
        h.succeedWhen(() -> {
            h.assertTrue(end.getEntity(id) instanceof CompanionEntity, "Still at " + (h.getLevel().getEntity(id) == null ? "nowhere" : h.getLevel().getEntity(id).blockPosition()));
            h.assertTrue(h.getLevel().getEntity(id) == null, "He is in two worlds");
        });
    }

    /** «Стой» holds: the player's way out is seen, and he stays. */
    @GameTest(template = "tall_tree", timeoutTicks = 500, batch = "portal")
    public static void toldToWaitHeStays(GameTestHelper h) {
        ground(h);
        netherPortal(h);
        CompanionEntity companion = spawn(h, 12, 16);
        companion.setFollowing(false);
        UUID id = companion.getUUID();
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        BlockPos stood = companion.blockPosition();
        Player owner = stepInto(h, companion, new BlockPos(15, GROUND + 1, 16));
        leaveFor(h, owner, nether, 0, 100, 0);
        h.runAtTickTime(200, () -> h.succeedIf(() -> {
            h.assertTrue(h.getLevel().getEntity(id) == companion && nether.getEntity(id) == null, "He left");
            h.assertTrue(companion.blockPosition().distManhattan(stood) <= 2, "He walked to " + companion.blockPosition());
        }));
    }

    /** A portal the player did not take is nobody's way: gone by a command, Oleg stays. */
    @GameTest(template = "tall_tree", timeoutTicks = 500, batch = "portal")
    public static void aPlayerSentByACommandIsNotFollowed(GameTestHelper h) {
        ground(h);
        netherPortal(h);
        CompanionEntity companion = spawn(h, 6, 16);
        UUID id = companion.getUUID();
        var nether = h.getLevel().getServer().getLevel(Level.NETHER);
        Player owner = stepInto(h, companion, new BlockPos(3, GROUND + 1, 26));
        leaveFor(h, owner, nether, 0, 100, 0);
        h.runAtTickTime(200, () -> h.succeedIf(() -> h.assertTrue(h.getLevel().getEntity(id) == companion && nether.getEntity(id) == null, "He left")));
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 8; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            h.setBlock(new BlockPos(x, y, z), y <= GROUND ? Blocks.BEDROCK : border ? Blocks.BEDROCK : Blocks.AIR);
        }
    }

    /** Facing along z: the frame from x 14 to 17, the portal two wide at x 15 and 16, z 16. */
    private static void netherPortal(GameTestHelper h) {
        for (int x = 14; x <= 17; x++) for (int y = GROUND + 1; y <= GROUND + 5; y++)
            if (x == 14 || x == 17 || y == GROUND + 1 || y == GROUND + 5) h.setBlock(new BlockPos(x, y, 16), Blocks.OBSIDIAN);
        for (int x = 15; x <= 16; x++) for (int y = GROUND + 2; y <= GROUND + 4; y++)
            h.setBlock(new BlockPos(x, y, 16), Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(x + 1, GROUND + 1, z + 1));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    /** The player moved next to the portal cell, as he stands the last tick before the game takes him. */
    private static Player stepInto(GameTestHelper h, CompanionEntity companion, BlockPos cell) {
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos at = h.absolutePos(cell);
        owner.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        return owner;
    }

    /** Two ticks on, so Oleg has seen him there; then the crossing, as the game makes it for a player. */
    private static void leaveFor(GameTestHelper h, Player owner, ServerLevel to, double x, double y, double z) {
        h.runAfterDelay(2, () -> ((net.minecraft.server.level.ServerPlayer) owner).teleportTo(to, x, y, z, 0, 0));
    }
}

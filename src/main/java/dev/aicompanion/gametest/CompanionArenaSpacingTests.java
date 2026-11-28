package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Arenas of one batch run at once; one must not see the tables and chests the next one puts down. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionArenaSpacingTests {
    /** Wider than his searches for tables and chests (32) and for logs, stone and ore (up to 128); vanilla leaves 5 by X and 6 by Z. */
    public static final int GAP = 129;
    private static final List<AABB> ARENAS = new ArrayList<>();
    /**
     * The second arena stands 129 blocks off, in chunks the server makes off its thread, while a test server ticks as fast as it
     * can: on two cores (CI shards) the first test used up the default 100 ticks before the second had started. It waits longer,
     * and gives the chunk work a few milliseconds a tick, as CompanionNetherErrandTests#forcedLoaded does.
     */
    private static final int WAIT_TICKS = 1200;

    @BeforeBatch(batch = "arena_spacing")
    public static void forgetEarlierArenas(ServerLevel level) { synchronized (ARENAS) { ARENAS.clear(); } }

    @GameTest(template = "empty", batch = "arena_spacing", timeoutTicks = WAIT_TICKS)
    public static void anArenaStandsFarFromItsLeftNeighbour(GameTestHelper h) { apart(h); }

    @GameTest(template = "empty", batch = "arena_spacing", timeoutTicks = WAIT_TICKS)
    public static void anArenaStandsFarFromItsRightNeighbour(GameTestHelper h) { apart(h); }

    private static void apart(GameTestHelper h) {
        synchronized (ARENAS) { ARENAS.add(h.getBounds()); }
        h.succeedWhen(() -> {
            if (ARENAS.size() < 2) { try { Thread.sleep(5); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } }
            h.assertTrue(ARENAS.size() >= 2, "The other arena has not started (the two tests run only as a pair)");
            AABB a = ARENAS.get(0), b = ARENAS.get(1);
            double gap = Math.max(Math.max(a.minX, b.minX) - Math.min(a.maxX, b.maxX), Math.max(a.minZ, b.minZ) - Math.min(a.maxZ, b.maxZ));
            h.assertTrue(gap >= GAP, "Neighbouring arenas are only " + gap + " blocks apart");
        });
    }
}

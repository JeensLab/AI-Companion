package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Arenas of one batch run at once; one must not see the tables and chests the next one puts down. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionArenaSpacingTests {
    /** Wider than the 32-block searches for tables, chests and beds; vanilla leaves 5 blocks by X and 6 by Z. */
    public static final int GAP = 33;
    private static final List<AABB> ARENAS = new ArrayList<>();

    @GameTest(template = "empty", batch = "arena_spacing")
    public static void anArenaStandsFarFromItsLeftNeighbour(GameTestHelper h) { apart(h); }

    @GameTest(template = "empty", batch = "arena_spacing")
    public static void anArenaStandsFarFromItsRightNeighbour(GameTestHelper h) { apart(h); }

    private static void apart(GameTestHelper h) {
        synchronized (ARENAS) { ARENAS.add(h.getBounds()); }
        h.succeedWhen(() -> {
            h.assertTrue(ARENAS.size() >= 2, "The other arena has not started");
            AABB a = ARENAS.get(0), b = ARENAS.get(1);
            double gap = Math.max(Math.max(a.minX, b.minX) - Math.min(a.maxX, b.maxX), Math.max(a.minZ, b.minZ) - Math.min(a.maxZ, b.maxZ));
            h.assertTrue(gap >= GAP, "Neighbouring arenas are only " + gap + " blocks apart");
        });
    }
}

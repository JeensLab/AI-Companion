package dev.aicompanion.gametest.support;

import net.minecraft.gametest.framework.GameTestHelper;

/** One callback per real server tick, without onEachTick's preallocated map of every future tick (quadratic scans). */
public final class MarathonTicks {
    private MarathonTicks() { }
    public static void each(GameTestHelper helper,Runnable callback) {
        helper.startSequence().thenExecuteFor(Integer.MAX_VALUE,callback);
    }
}

package dev.aicompanion.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/** One callback per real server tick, without onEachTick's preallocated map of every future tick (quadratic scans). */
final class MarathonTicks {
    private MarathonTicks() { }
    static void each(GameTestHelper helper,Runnable callback) {
        helper.startSequence().thenExecuteFor(Integer.MAX_VALUE,callback);
    }
}

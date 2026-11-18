package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;

/**
 * The way to a remembered spot far off, shared by the scouting round and the trade order: in legs like any long walk, never a
 * teleport, with its own eight minutes (apart from the work's own limit, which starts on arrival).
 */
final class CompanionFarWalk {
    enum Step { WALKING, ARRIVED, FAILED, TIMEOUT }

    static final int LIMIT = 20 * 60 * 8;
    private final CompanionWork w;
    private BlockPos target;
    private long startedAt;

    CompanionFarWalk(CompanionWork w) { this.w = w; }

    boolean active() { return target != null; }
    BlockPos target() { return target; }

    void begin(BlockPos spot) { cancel(); target = spot; startedAt = 0; }

    /** One tick of the walk. ARRIVED ends it; on FAILED or TIMEOUT the caller reads {@link #target()} and calls {@link #cancel()}. */
    Step step(long now, String status) {
        if (startedAt == 0) startedAt = now;
        if (now - startedAt > LIMIT) return Step.TIMEOUT;
        w.owner.workStatus(status);
        return switch (w.route.legs(target, 8)) {
            case WALKING -> Step.WALKING;
            case NEAR -> { w.route.legsEnd(true); target = null; startedAt = 0; yield Step.ARRIVED; }
            case FAILED -> Step.FAILED;
        };
    }

    void cancel() {
        if (target != null) w.route.legsEnd(false);
        target = null; startedAt = 0;
    }
}

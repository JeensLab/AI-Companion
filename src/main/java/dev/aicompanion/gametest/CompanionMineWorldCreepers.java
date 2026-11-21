package dev.aicompanion.gametest;

import dev.aicompanion.CompanionDiagnostics;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;

import java.util.HashSet;
import java.util.Set;

/**
 * Development only. Writes the creepers around Oleg into the mine's own diagnostics: when one first comes into his sight and when its
 * fuse is lit, with where each stood and what he was doing. Looks only; nothing here reaches the creeper or him.
 */
final class CompanionMineWorldCreepers {
    private static final double RADIUS = 16;
    private static final int EVERY = 5;

    private final Set<Integer> seen = new HashSet<>(), swelling = new HashSet<>();

    void watch(ServerLevel level, CompanionEntity oleg, int tick) {
        if (tick % EVERY != 0) return;
        Set<Integer> lit = new HashSet<>();
        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, oleg.getBoundingBox().inflate(RADIUS), Creeper::isAlive)) {
            if (creeper.getSwellDir() > 0) {
                lit.add(creeper.getId());
                if (swelling.add(creeper.getId())) record("creeper_swell", oleg, creeper, tick);
            }
            if (!seen.contains(creeper.getId()) && oleg.hasLineOfSight(creeper)) {
                seen.add(creeper.getId());
                record("creeper_seen", oleg, creeper, tick);
            }
        }
        swelling.retainAll(lit);
    }

    private static void record(String event, CompanionEntity oleg, Creeper creeper, int tick) {
        CompanionDiagnostics.event("probe", event, "entity", oleg.getUUID(), "probeTick", tick, "creeper", creeper.getId(),
                "creeperPosition", creeper.blockPosition(), "distance", oleg.distanceTo(creeper), "position", oleg.blockPosition(),
                "health", oleg.getHealth(), "stage", oleg.expeditionStage(), "cavePhase", oleg.expeditionCavePhase(), "air", oleg.getAirSupply());
    }
}

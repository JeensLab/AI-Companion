package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * No ore of the kind in sight or remembered: a player's search, never x-ray. First a look down into the caves, ravines and
 * cuts he can see from where he stands: he walks to the lowest open ground in sight and looks round again. With nowhere left
 * to look, a short exploration tunnel, stairs a few blocks down and then straight on, 1x2, its walls looked at as he goes.
 * Whatever opens up is mined by the usual ore work; every tunnel cell keeps the safety rules of the ore tunnel.
 */
final class CompanionOreScouting {
    /** Places walked to for a look. */
    static final int LOOKS = 10;
    /** How far down and round he looks for open ground. */
    static final int LOOK_RANGE = 24;
    private static final int[] PITCHES = {10, 20, 30, 40, 45, 50, 60, 75};
    /** Legs of the exploration tunnel, each up to CompanionOre.MAX_REACH long. */
    static final int LEGS = 4;
    /** The first leg goes down this far below his feet. */
    static final int DEPTH = 5;
    /** Fewer cells to dig is no tunnel into the ground (open air, a cave already there). */
    private static final int MIN_CELLS = 6;
    private final CompanionWork w;
    private final List<BlockPos> lookedFrom = new ArrayList<>();
    private BlockPos viewpoint;
    private Direction heading;
    private int looks, legs, dug;
    private boolean digging, blocked;

    CompanionOreScouting(CompanionWork w) { this.w = w; }

    void reset() { lookedFrom.clear(); viewpoint = null; heading = null; looks = legs = dug = 0; digging = blocked = false; }
    boolean digging() { return digging; }
    /** A cell of the exploration tunnel dug and its drop on the way to the bag. */
    void cellDug() { dug++; }
    /** A cell of the current exploration leg turned out unsafe: the leg ends there, the next one turns. */
    void legBlocked() { blocked = true; }

    /** One step of the search. False when there is nowhere left to look or dig: the order then ends honestly. */
    boolean next(ServerLevel level) {
        // A deed of his own keeps to his workplace and its known ore; searching is for the player's orders.
        if (w.owner.autonomy().running()) return false;
        if (digging) { legDone(); return true; }
        if (viewpoint != null) return walk(level);
        if (legs == 0 && looks < LOOKS) {
            w.resetApproach();
            viewpoint = lowestGroundInSight(level);
            if (viewpoint != null) {
                looks++;
                w.owner.workStatus("Руды «" + w.ore.oreName + "» не вижу. Спущусь посмотреть, что там внизу.");
                dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "ore_scouting_look", "entity", w.owner.getUUID(),
                        "ore", w.ore.key(), "from", w.owner.blockPosition(), "to", viewpoint, "look", looks);
                return true;
            }
        }
        return planLeg(level);
    }

    /** What he did to find it, for the report when nothing turned up. */
    String summary() {
        String looked = looks > 0 ? "спускался посмотреть в " + looks + " " + places(looks) : "спуститься посмотреть было некуда";
        String tunnel = dug > 0 ? "прокопал разведочный ход (" + CompanionWorkItems.blocks(dug) + " породы)" : "разведочный ход тут не прокопать";
        return looked + ", " + tunnel + ".";
    }
    private static String places(int n) { return n % 10 == 1 && n % 100 != 11 ? "место" : n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20) ? "места" : "мест"; }

    /**
     * The lowest open ground he sees, reachable on foot: rays from his eyes down and round, like a player looking about, stop at
     * the first block or liquid in the way. Ground below him (caves, ravines and cuts go down), or at his height under a roof of
     * rock (on along a cave); lower first, the farthest of the same height first.
     */
    private BlockPos lowestGroundInSight(ServerLevel level) {
        Vec3 eye = w.owner.getEyePosition();
        BlockPos here = w.owner.blockPosition();
        int floor = (w.workOrigin == null ? here : w.workOrigin).getY() - CompanionWork.MAX_MINE_DEPTH;
        List<Vec3> eyes = new ArrayList<>(List.of(eye));
        // Like a player looking round, a step to a side shows into a cut or a hole beside him that the edge hides from here.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Vec3 step = eye.add(side.getStepX() * 1.5, 0, side.getStepZ() * 1.5);
            if (CompanionMiningNavigation.standable(w.owner, here.relative(side))
                    && level.clip(new ClipContext(eye, step, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w.owner)).getType() == HitResult.Type.MISS) eyes.add(step);
        }
        List<BlockPos> seen = new ArrayList<>();
        for (Vec3 from : eyes) for (int yaw = 0; yaw < 360; yaw += 15) for (int pitch : PITCHES) {
            Vec3 end = from.add(Vec3.directionFromRotation(pitch, yaw).scale(LOOK_RANGE));
            var hit = level.clip(new ClipContext(from, end, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, w.owner));
            if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) continue;
            BlockPos stand = hit.getBlockPos().above();
            if (stand.getY() < floor || seen.contains(stand) || stand.distSqr(here) < 16) continue;
            if (stand.getY() > here.getY() - 2 && !(stand.getY() <= here.getY() && underRock(level, stand))) continue;
            if (lookedFrom.stream().anyMatch(old -> old.distSqr(stand) < 16) || !CompanionMiningNavigation.standable(w.owner, stand)) continue;
            seen.add(stand.immutable());
        }
        seen.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).thenComparingDouble(pos -> -pos.distSqr(here)));
        for (BlockPos stand : seen.subList(0, Math.min(4, seen.size()))) {
            Path route = w.owner.getNavigation().createPath(stand, 0);
            if (route != null && route.canReach()) { w.path = route; return stand; }
            lookedFrom.add(stand); // no way down there: not tried again
        }
        return null;
    }

    /** Natural rock or soil a few blocks overhead (not leaves): a cave or an overhang, worth a walk along at his own height. */
    private static boolean underRock(ServerLevel level, BlockPos stand) {
        for (int up = 2; up <= 8; up++) if (CompanionOre.tunnelable(level.getBlockState(stand.above(up)))) return true;
        return false;
    }

    private boolean walk(ServerLevel level) {
        BlockPos here = w.owner.blockPosition();
        if (here.equals(viewpoint) || w.owner.getNavigation().isDone() && here.distSqr(viewpoint) <= 2) {
            // A fresh look from here: what is in sight now and what he passed on the way (CompanionSight).
            lookedFrom.add(viewpoint); viewpoint = null; w.owner.getNavigation().stop();
            w.oreSearch = null; w.oreCandidates = null;
            return true;
        }
        double distance = w.owner.distanceToSqr(Vec3.atBottomCenterOf(viewpoint));
        if (distance + .05 < w.bestDistance) { w.bestDistance = distance; w.lastProgressTick = level.getGameTime(); }
        if (w.path == null || w.owner.getNavigation().isDone() || w.owner.getNavigation().isStuck()) w.path = w.owner.getNavigation().createPath(viewpoint, 0);
        if (level.getGameTime() - w.lastProgressTick > 100 || w.path == null || !w.path.canReach()) {
            lookedFrom.add(viewpoint); viewpoint = null; w.owner.getNavigation().stop();
            return true;
        }
        w.owner.movementIntent(viewpoint);
        CompanionMiningNavigation.walk(w.owner, w.path);
        return true;
    }

    /**
     * The next leg: the first goes down DEPTH blocks by stairs and on (or straight into a slope), later ones straight on the
     * same way, turning where the way on is unsafe. The way back up is the own stairs (oreExit), as for an ore tunnel.
     */
    private boolean planLeg(ServerLevel level) {
        if (legs >= LEGS) return false;
        BlockPos stand = w.owner.blockPosition();
        int floor = (w.workOrigin == null ? stand : w.workOrigin).getY() - CompanionWork.MAX_MINE_DEPTH;
        Direction first = heading != null ? heading : w.owner.getDirection();
        for (int turn = 0; turn < 4; turn++) {
            Direction way = turn == 0 ? first : turn == 1 ? first.getClockWise() : turn == 2 ? first.getCounterClockWise() : first.getOpposite();
            for (int down : legs == 0 ? new int[]{DEPTH, 0} : new int[]{0}) {
                BlockPos end = stand.relative(way, CompanionOre.MAX_REACH).below(down);
                if (end.getY() < floor) continue;
                List<BlockPos> cells = CompanionOre.tunnel(level, stand, end);
                if (cells == null || cells.size() < MIN_CELLS || cells.stream().anyMatch(cell -> !w.owner.leashAllows(cell))) continue;
                if (w.oreExit == null && down > 0) w.oreExit = stand;
                w.oreTunnel.addAll(cells);
                heading = way; legs++; digging = true; blocked = false;
                w.owner.workStatus("Руды «" + w.ore.oreName + "» не видно. Копаю разведочный ход и смотрю стены.");
                dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "ore_scouting_tunnel", "entity", w.owner.getUUID(),
                        "ore", w.ore.key(), "stand", stand, "heading", way.getName(), "down", down, "cells", cells.size(), "leg", legs);
                return true;
            }
        }
        return false;
    }

    /** The leg is dug (or given up at an unsafe cell): look again from where he stands now. */
    private void legDone() {
        if (blocked) heading = heading.getClockWise();
        digging = blocked = false;
        w.oreTunnel.clear();
        w.oreSearch = null; w.oreCandidates = null;
    }
}

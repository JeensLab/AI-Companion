package dev.aicompanion.terrain;

import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionNavigation;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Set;

/** A short lived work order: look around, optionally take one visible level approach, then observe again. */
public final class TerrainSurvey {
    public static final Set<String> TOPICS = Set.of("around", "descent", "ford", "cave", "biome");
    private final String topic;
    private TerrainSight sight = new TerrainSight();
    private TerrainReading reading;
    private int turns;
    private int slice;
    private final Direction initial;
    private BlockPos viewpoint;
    private boolean approached;
    private boolean walking;

    public TerrainSurvey(CompanionEntity c, String topic) {
        this.topic = topic;
        initial = Direction.fromYRot(c.getYHeadRot());
    }

    /** An in-place look in the current direction, also available during other work. */
    public static String glance(CompanionEntity c, String topic) {
        TerrainSight view = new TerrainSight();
        view.look(c, Direction.fromYRot(c.getYHeadRot()), 0, 8);
        return report(c, new TerrainReading(view, c.blockPosition()), topic, false);
    }

    private static String report(CompanionEntity c, TerrainReading reading, String topic, boolean checked) {
        LandmarkMemory.Entry cave = null, ore = null;
        if ((topic.equals("around") || topic.equals("cave")) && c.onGround() && !c.isPassenger()
                && !c.isInWaterOrBubble() && !c.isInLava() && c.blockPosition().equals(reading.origin.above())) {
            String dimension = c.level().dimension().location().toString();
            long now = c.level().getGameTime();
            for (BlockPos floor : reading.coveredFloors) {
                if (LandmarkObserver.stillVisible(c, "cave", floor)) {
                    cave = c.landmarks().discuss(dimension, "cave", floor, c.blockPosition(), now);
                    break;
                }
            }
            if (topic.equals("around") && reading.sight.nearestOre != null
                    && LandmarkObserver.stillVisible(c, reading.sight.nearestOreKind, reading.sight.nearestOre))
                ore = c.landmarks().discuss(dimension, reading.sight.nearestOreKind, reading.sight.nearestOre, c.blockPosition(), now);
        }
        return reading.answer(topic, checked, cave, ore);
    }

    /** Null while looking or walking. The caller owns cancellation and the overall deadline. */
    public String tick(CompanionEntity c) {
        if (walking) {
            if (c.position().distanceToSqr(Vec3.atBottomCenterOf(viewpoint)) < .36 && c.onGround()) {
                c.getNavigation().stop();
                walking = false;
                reading = null;
                sight = new TerrainSight();
                turns = 0;
            } else if (c.getNavigation().isDone()) return report(c, reading, topic, false);
            else return null;
        }
        if (turns < 4) {
            Direction direction = Direction.from2DDataValue(initial.get2DDataValue() + turns);
            c.lookAt(EntityAnchorArgument.Anchor.EYES, c.getEyePosition().add(direction.getStepX() * 4, 0, direction.getStepZ() * 4));
            sight.look(c, direction, slice * 3, slice * 3 + 2);
            if (++slice == 5) { turns++; slice = 0; }
            return null;
        }
        if (reading == null) reading = new TerrainReading(sight, c.blockPosition());
        BlockPos target = topic.equals("ford") ? reading.ford : topic.equals("around") || topic.equals("descent") ? reading.slopeFoot : null;
        var nav = (CompanionNavigation)c.getNavigation();
        if (target != null) {
            Path path = nav.search(Set.of(target.above()), 0, 18);
            if (path == CompanionNavigation.BUSY) return null;
            if (visiblePath(path, target.above(), false)) {
                String answer = report(c, reading, topic, true);
                if (!topic.equals("ford")) answer += nav.canRetrace(path, 18) ? " Тем же путём можно подняться." : " Подъём обратно пока не подтвердил.";
                return answer;
            }
        }
        if (!approached && sight.monster == null && !c.autonomy().playerStopped()) {
            if (viewpoint == null) viewpoint = vantage();
            if (viewpoint != null) {
                Path path = nav.search(Set.of(viewpoint), 0, 8);
                if (path == CompanionNavigation.BUSY) return null;
                if (visiblePath(path, viewpoint, true) && nav.moveTo(path, .9)) {
                    approached = true;
                    walking = true;
                    return null;
                }
            }
        }
        return report(c, reading, topic, false);
    }

    private BlockPos vantage() {
        BlockPos feature = switch (topic) {
            case "descent", "around" -> reading.rim != null ? reading.rim : reading.cliff;
            case "ford" -> reading.bank;
            case "cave" -> reading.coveredFloors.isEmpty() ? null : reading.coveredFloors.getFirst();
            default -> null;
        };
        if (feature == null || feature.equals(reading.origin)) return null;
        // Only approach a fully seen, level stand. The path is checked again before the first step.
        return sight.ground.stream().filter(p -> p.getY() == reading.origin.getY() && p.distSqr(reading.origin) >= 1
                        && p.distSqr(reading.origin) <= 25 && p.distSqr(feature) < feature.distSqr(reading.origin)
                        && (!topic.equals("cave") || p.distSqr(feature) >= 4))
                .min(Comparator.comparingDouble(p -> p.distSqr(feature))).map(BlockPos::above).orElse(null);
    }

    /** A hidden shortcut in the navigator cannot establish a visible descent, crossing or safe viewing position. */
    private boolean visiblePath(Path path, BlockPos end, boolean levelWalk) {
        if (path == null || !path.canReach() || path.getEndNode() == null || !path.getEndNode().asBlockPos().equals(end)) return false;
        BlockPos previous = reading.origin.above();
        boolean crossedWater = false;
        for (int i = 0; i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            BlockPos p = node.asBlockPos();
            boolean wet = topic.equals("ford") && !levelWalk && sight.shallow.contains(p);
            if (!wet && ((node.type != PathType.WALKABLE && node.type != PathType.WATER_BORDER) || !sight.ground.contains(p.below()))) return false;
            if (Math.abs(p.getY() - previous.getY()) > (levelWalk ? 0 : 1)
                    || Math.abs(p.getX() - previous.getX()) > 1 || Math.abs(p.getZ() - previous.getZ()) > 1) return false;
            previous = p;
            crossedWater |= wet;
        }
        return levelWalk || !topic.equals("ford") || crossedWater;
    }
}

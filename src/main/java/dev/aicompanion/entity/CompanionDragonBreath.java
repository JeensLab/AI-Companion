package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The dragon's breath, as a player reads it: the purple cloud its fireball leaves on the ground (30 seconds, growing from 3 blocks
 * across to 7) and the flame it breathes sitting on the portal, both hurting through any armour (DragonFireball.onHit,
 * DragonSittingFlamingPhase). He keeps clear of it by a margin: caught in it he drops what he does (the work only waits) and walks
 * out. Only the dragon's: a lingering potion's cloud does not show from outside whether it hurts or heals.
 */
final class CompanionDragonBreath {
    /** How far clear of a cloud's edge he keeps: a fireball's cloud grows by a block in some eight seconds. */
    static final double MARGIN = 2;
    /** A cell this near a cloud is a wall to his ways: half a block more than the margin, for his width. */
    static final double WALL = MARGIN + .5;
    /** Caught, he walks out until this far clear: past the margin, so its edge is not stepped over back and forth. */
    private static final double CLEAR = MARGIN + 1.5;
    /** How far out a way out is looked for, along each of the rays round him. */
    private static final int OUT = 14, RAYS = 16;
    /** The widest a cloud gets from its middle: a fireball's at the end of its 30 seconds. */
    private static final double WIDEST = 7;
    /** How far about him the breath counts as walling off his ways (a search goes this far and more). */
    private static final double ABOUT = 64;
    /** How far from a player in the breath its edge is looked for, on his side. */
    private static final int EDGE = 24;
    /** The cells to walk out to are looked for again this often (ticks): the cloud grows. */
    private static final int AGAIN = 10;

    static final String WAITING = "В драконье дыхание не полезу — подожду тут.";

    private final CompanionEntity c;
    private Set<BlockPos> out = Set.of();
    private long lookedAt;
    /** He has said why he waits at the breath's edge in the fight with this dragon (null: a cloud of nobody's): once in a fight. */
    private boolean told;
    private UUID toldFor;
    /** When the breath was last about him, and how long it counts as about after that (ticks). */
    private long seenAt = Long.MIN_VALUE / 2;
    private static final int SEEN = 60;

    CompanionDragonBreath(CompanionEntity c) { this.c = c; }

    /** The dragon's breath whose cloud reaches into the box. */
    static List<AreaEffectCloud> clouds(Level level, AABB box) {
        return level.getEntitiesOfClass(AreaEffectCloud.class, box, cloud -> cloud.isAlive() && cloud.getParticle().getType() == ParticleTypes.DRAGON_BREATH);
    }

    /**
     * Whether feet at x, y, z are in the cloud or within {@code margin} of its edge. Across, its radius counts (the game measures
     * the middles); up and down it is a layer half a block high, so a block over it or more is clear, and so is a head under it.
     */
    static boolean near(AreaEffectCloud cloud, double x, double y, double z, double margin) {
        double dx = x - cloud.getX(), dz = z - cloud.getZ(), reach = cloud.getRadius() + margin;
        return dx * dx + dz * dz <= reach * reach && y < cloud.getY() + 1 && cloud.getY() < y + 2;
    }

    static boolean near(List<AreaEffectCloud> clouds, double x, double y, double z, double margin) {
        for (AreaEffectCloud cloud : clouds) if (near(cloud, x, y, z, margin)) return true;
        return false;
    }

    /** The breath whose middle is within {@code reach} of a place across and {@code up} up or down. */
    private List<AreaEffectCloud> around(double x, double y, double z, double reach, double up) {
        return clouds(c.level(), new AABB(x - reach, y - up, z - reach, x + reach, y + up, z + reach));
    }

    private boolean caught(double margin) {
        return c.isAlive() && near(around(c.getX(), c.getY(), c.getZ(), WIDEST + margin, 3), c.getX(), c.getY(), c.getZ(), margin);
    }

    // No order cancels this, as with his air (CompanionBreath): an order waits while he walks out.
    boolean canUse() { return caught(MARGIN); }
    boolean canContinue() { return caught(CLEAR); }

    void begin() { c.getNavigation().stop(); out = Set.of(); lookedAt = Long.MIN_VALUE; }
    void end() { c.getNavigation().stop(); out = Set.of(); }

    void tick() {
        if (!c.getNavigation().isDone() && !c.getNavigation().isStuck()) return;
        long now = c.level().getGameTime();
        if (out.isEmpty() || now - lookedAt >= AGAIN) { out = cellsOut(); lookedAt = now; }
        if (out.isEmpty()) return;
        Path path = c.work.route.toCells(out);
        if (path != null) c.work.route.walk(path);
    }

    /**
     * Whether the dragon's breath is about him, or was a moment ago: a way it walls off may be there once it is gone, and the
     * searches made while it was there are kept a while (CompanionNavigation.KEEP).
     */
    boolean about() {
        long now = c.level().getGameTime();
        if (!around(c.getX(), c.getY(), c.getZ(), ABOUT, 16).isEmpty()) seenAt = now;
        return now - seenAt <= SEEN;
    }

    /**
     * Where he goes to the player: the player's cell, or with the player in the breath the nearest cell clear of it on his side,
     * where he waits, saying why once in the fight; null with no such cell, and he waits where he is.
     */
    BlockPos meetingPoint(Player player) {
        double x = player.getX(), y = player.getY(), z = player.getZ();
        List<AreaEffectCloud> clouds = around(x, y, z, WIDEST + CLEAR + EDGE, 3);
        AreaEffectCloud in = clouds.stream().filter(cloud -> near(cloud, x, y, z, MARGIN)).findFirst().orElse(null);
        if (in == null) return player.blockPosition();
        tell(in);
        double dx = c.getX() - x, dz = c.getZ() - z, length = Math.sqrt(dx * dx + dz * dz);
        if (length < .01) { dx = 1; dz = 0; length = 1; }
        for (int step = 1; step <= EDGE; step++) {
            BlockPos cell = standAt(x + dx / length * step, player.getBlockY(), z + dz / length * step);
            if (cell != null && !near(clouds, cell.getX() + .5, cell.getY(), cell.getZ() + .5, CLEAR)) return cell;
        }
        return null;
    }

    private void tell(AreaEffectCloud cloud) {
        UUID dragon = cloud.getOwner() == null ? null : cloud.getOwner().getUUID();
        if (told && Objects.equals(toldFor, dragon)) return;
        told = true; toldFor = dragon;
        c.autonomySay(WAITING, c.hasActiveWorkOrder());
    }

    /** Along each ray round him, the first cell he can stand in that is clear of all the breath: the nearest ways out. */
    private Set<BlockPos> cellsOut() {
        List<AreaEffectCloud> clouds = around(c.getX(), c.getY(), c.getZ(), WIDEST + CLEAR + OUT, 3);
        Set<BlockPos> cells = new HashSet<>();
        for (int ray = 0; ray < RAYS; ray++) {
            double angle = 2 * Math.PI * ray / RAYS;
            for (int step = 1; step <= OUT; step++) {
                BlockPos cell = standAt(c.getX() + Math.cos(angle) * step, c.getBlockY(), c.getZ() + Math.sin(angle) * step);
                if (cell == null || near(clouds, cell.getX() + .5, cell.getY(), cell.getZ() + .5, CLEAR + .5)) continue;
                cells.add(cell);
                break;
            }
        }
        return cells;
    }

    /** A cell at x, z near the height y with a floor under it and room for him. */
    private BlockPos standAt(double x, int y, double z) {
        int bx = (int)Math.floor(x), bz = (int)Math.floor(z);
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            BlockPos feet = new BlockPos(bx, y + dy, bz);
            if (fits(feet)) return feet;
        }
        return null;
    }

    private boolean fits(BlockPos feet) {
        Level level = c.level();
        return level.hasChunkAt(feet) && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }
}

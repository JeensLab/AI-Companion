package dev.aicompanion.gametest;

import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Development only. Finds, in the pristine generated overworld, the entrances that the real mine would choose and
 * classifies the staircase line under each one by what the world has there, using the mine's own hazard check.
 */
final class CompanionMineWorldSites {
    /** The void scenario runs on several separate floor-void entrances ("void-2", ...): one cavity under one staircase proves little. */
    static final List<String> SCENARIOS = List.of("descent", "void", "void-2", "void-3", "void-4", "void-5", "lava", "mobs", "water", "retreat", "come", "restart", "deepdark");
    private static final int GRID = 8, ENTRANCE_RADIUS = 12, CAVE_MIN_DEPTH = 3, APART = 40;

    enum Cause { NONE, FLOOR, LIQUID, FALLING, SOLID, BUILT }

    /** One candidate mine; failAt is the first step the hazard check refuses on the untouched world, -1 when none. */
    record Site(BlockPos feet, Direction dir, int steps, int failAt, Cause cause, String floor, boolean lava, boolean water,
                BlockPos cave, boolean deepDark, boolean diamonds, double distance) {
        boolean clean() { return failAt < 0; }

        String facts() {
            return "steps=" + steps + " failAt=" + failAt + " cause=" + cause + (floor.isEmpty() ? "" : " floor=" + floor) + " lava=" + lava + " water=" + water
                    + " cave=" + (cave == null ? "none" : cave.toShortString()) + " deepDark=" + deepDark + " diamonds=" + diamonds + " distance=" + (int) distance;
        }
    }

    private CompanionMineWorldSites() { }

    static List<Site> scan(ServerLevel level, BlockPos centre, int halfSpan) {
        Set<BlockPos> seen = new HashSet<>();
        List<Site> sites = new ArrayList<>();
        for (int x = centre.getX() - halfSpan; x <= centre.getX() + halfSpan; x += GRID) {
            for (int z = centre.getZ() - halfSpan; z <= centre.getZ() + halfSpan; z += GRID) {
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
                BlockPos from = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                var entrance = CompanionShaftPlan.chooseEntrance(level, from, ENTRANCE_RADIUS);
                if (entrance == null || !seen.add(entrance.feet())) continue;
                Site site = evaluate(level, entrance, centre);
                if (site != null) sites.add(site);
            }
        }
        sites.sort(Comparator.comparingDouble(Site::distance));
        return sites;
    }

    /** Null when any cell of the line is outside the generated area: such a site would only measure the edge of the test world. */
    static Site evaluate(ServerLevel level, CompanionShaftPlan.Entrance entrance, BlockPos centre) {
        BlockPos feet = entrance.feet();
        Direction d = entrance.direction();
        int steps = CompanionShaftPlan.stepsTo(feet.getY(), CompanionShaftPlan.targetFeetY(level.getMinBuildHeight()));
        int failAt = -1;
        Cause cause = Cause.NONE;
        String floor = "";
        boolean lava = false, water = false, deepDark = false;
        BlockPos cave = null;
        for (int k = 0; k < steps; k++) {
            BlockPos stand = CompanionShaftPlan.stand(feet, d, k);
            List<BlockPos> cells = CompanionShaftPlan.advanceCells(stand, d);
            for (BlockPos cell : cells) if (!surrounded(level, cell)) return null;
            for (BlockPos cell : cells) {
                for (BlockPos near : around(cell)) {
                    var fluid = level.getFluidState(near);
                    if (fluid.isEmpty()) continue;
                    if (fluid.is(FluidTags.LAVA)) lava = true; else water = true;
                }
            }
            BlockPos next = CompanionShaftPlan.nextStand(stand, d);
            boolean deep = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, next.getX(), next.getZ()) - next.getY() >= CAVE_MIN_DEPTH;
            if (cave == null && deep && level.getBlockState(next).isAir()) cave = next;
            if (level.getBiome(next).is(Biomes.DEEP_DARK)) deepDark = true;
            if (failAt >= 0) continue;
            var hazard = CompanionShaftPlan.assessStep(level, stand, d, Set.of());
            if (hazard.kind() == CompanionShaftPlan.Kind.OK) continue;
            failAt = k;
            cause = causeOf(level, stand, cells, hazard);
            if (cause == Cause.FLOOR) floor = BuiltInRegistries.BLOCK.getKey(level.getBlockState(next.below()).getBlock()).getPath();
        }
        BlockPos foot = CompanionShaftPlan.stand(feet, d, steps);
        boolean diamonds = false;
        for (Direction t : CompanionShaftPlan.tunnelOrder(d).subList(0, 3)) {
            for (int k = 0; k < CompanionShaftPlan.MAX_TUNNEL_CELLS && !diamonds; k++) {
                for (BlockPos cell : CompanionShaftPlan.tunnelCells(foot.relative(t, k), t)) {
                    if (!level.hasChunkAt(cell)) break;
                    if (level.getBiome(cell).is(Biomes.DEEP_DARK)) deepDark = true;
                    if (touches(level, cell, BlockTags.DIAMOND_ORES)) { diamonds = true; break; }
                }
            }
        }
        return new Site(feet, d, steps, failAt, cause, floor, lava, water, cave, deepDark, diamonds, Math.sqrt(feet.distSqr(centre)));
    }

    private static Cause causeOf(ServerLevel level, BlockPos stand, List<BlockPos> cells, CompanionShaftPlan.Hazard hazard) {
        if (hazard.kind() == CompanionShaftPlan.Kind.SEAL) return Cause.LIQUID;
        for (BlockPos cell : cells) {
            if (CompanionShaftPlan.classify(level, stand, cell, Set.of()).kind() != CompanionShaftPlan.Kind.BLOCKED) continue;
            for (BlockPos near : around(cell)) if (!level.getFluidState(near).isEmpty()) return Cause.LIQUID;
            if (level.getBlockEntity(cell) != null) return Cause.BUILT;
            if (level.getBlockState(cell.above()).getBlock() instanceof FallingBlock) return Cause.FALLING;
            return Cause.SOLID;
        }
        return Cause.FLOOR;
    }

    private static boolean touches(ServerLevel level, BlockPos cell, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag) {
        for (BlockPos near : around(cell)) if (level.hasChunkAt(near) && level.getBlockState(near).is(tag)) return true;
        return false;
    }

    private static boolean surrounded(ServerLevel level, BlockPos cell) {
        return level.hasChunkAt(cell.offset(-2, 0, -2)) && level.hasChunkAt(cell.offset(2, 0, 2))
                && level.hasChunkAt(cell.offset(-2, 0, 2)) && level.hasChunkAt(cell.offset(2, 0, -2));
    }

    private static List<BlockPos> around(BlockPos cell) {
        List<BlockPos> cells = new ArrayList<>(7);
        cells.add(cell);
        for (Direction side : Direction.values()) cells.add(cell.relative(side));
        return cells;
    }

    /** The nearest site of the kind a scenario needs (the n-th, at least APART blocks from the earlier ones, for "void-n"); null when the world has none. */
    static Site pick(String scenario, List<Site> sites) {
        String base = scenario.replaceFirst("-[0-9]+$", "");
        int index = base.equals(scenario) ? 0 : Integer.parseInt(scenario.substring(base.length() + 1)) - 1;
        Predicate<Site> wanted = switch (base) {
            case "void" -> s -> s.cause() == Cause.FLOOR && s.failAt() >= 3;
            case "lava" -> s -> s.lava() && s.diamonds();
            case "mobs" -> s -> s.cave() != null;
            case "water" -> s -> s.water();
            case "retreat", "come", "restart" -> s -> s.clean() && s.diamonds();
            case "deepdark" -> Site::deepDark;
            default -> Site::diamonds;
        };
        List<Site> chosen = new ArrayList<>();
        for (Site site : sites) {
            if (!wanted.test(site) || chosen.stream().anyMatch(other -> other.feet().distSqr(site.feet()) < (double) APART * APART)) continue;
            chosen.add(site);
            if (chosen.size() > index) return site;
        }
        boolean cleanOnly = List.of("retreat", "come", "restart").contains(base);
        return cleanOnly ? sites.stream().filter(Site::diamonds).findFirst().orElse(null) : null;
    }

    static void store(Properties out, String scenario, Site site) {
        if (site == null) {
            out.setProperty(scenario + ".status", scenario.equals("deepdark") || scenario.matches("void-[0-9]+") ? "SKIPPED" : "NONE");
            return;
        }
        out.setProperty(scenario + ".status", "OK");
        out.setProperty(scenario + ".entrance", site.feet().getX() + "," + site.feet().getY() + "," + site.feet().getZ());
        out.setProperty(scenario + ".dir", site.dir().name());
        if (site.cave() != null) out.setProperty(scenario + ".cave", site.cave().getX() + "," + site.cave().getY() + "," + site.cave().getZ());
        out.setProperty(scenario + ".facts", site.facts());
    }

    static BlockPos parse(String packed) {
        String[] part = packed.split(",");
        return new BlockPos(Integer.parseInt(part[0]), Integer.parseInt(part[1]), Integer.parseInt(part[2]));
    }
}

package dev.aicompanion.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Landmarks inferred from visible surfaces. Positions refer to supporting blocks, including the surface of water. */
final class TerrainReading {
    final TerrainSight sight;
    final BlockPos origin;
    final List<BlockPos> coveredFloors = new ArrayList<>();
    BlockPos cliff, ledge, slope, slopeFoot, bank, ford, rim, ridge, ravine;

    TerrainReading(TerrainSight sight, BlockPos feet) {
        this.sight = sight;
        origin = feet.below();
        sight.shallow.retainAll(sight.water);
        for (BlockPos p : sight.ground) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos next = p.relative(d);
                if (sight.ground.contains(next)) {
                    Direction side = d.getClockWise();
                    if (hasFlanks(p, side, -1) && hasFlanks(next, side, -1)) ridge = nearest(ridge, p);
                    if (hasFlanks(p, side, 1) && hasFlanks(next, side, 1)) ravine = nearest(ravine, p);
                }
                if (sight.ground.contains(next.below()) && sight.ground.contains(p.relative(d, 2).below(2))
                        && p.getY() <= origin.getY()) {
                    BlockPos step = p.relative(d, 2).below(2);
                    slope = nearest(slope, step);
                    while (sight.ground.contains(step.relative(d).below())) step = step.relative(d).below();
                    if (sight.ground.contains(step.relative(d))) slopeFoot = nearest(slopeFoot, step);
                }
                if (sheer(p, next) && sight.ground.contains(p.relative(d.getClockWise())) &&
                        sheer(p.relative(d.getClockWise()), next.relative(d.getClockWise()))) cliff = nearest(cliff, p);
                if (p.getY() < origin.getY() && sight.ground.contains(next) && lowerInSight(p, d)
                        && higherBehind(p, d)) ledge = nearest(ledge, p);
                if (sight.water.contains(next) || sight.water.contains(next.below())) {
                    bank = nearest(bank, p);
                    if (p.getY() >= origin.getY() - 1) findFord(p, d);
                }
                if (p.getY() == origin.getY() && !sight.ground.contains(next) && (sight.openAtFoot.contains(next) || lowerInSight(p, d))) rim = nearest(rim, p);
            }
        }
        // The top of a rock face can be above eye level. Its exposed vertical extent is still visible.
        for (BlockPos top : sight.walls) {
            if (wallBelow(top) && (wallBelow(top.east()) || wallBelow(top.south()))) cliff = nearest(cliff, top);
        }
        for (BlockPos floor : sight.ground) {
            for (int depth = 2; depth <= 5; depth++) {
                if (sight.roofs.contains(floor.above(depth + 1))) {
                    coveredFloors.add(floor);
                    break;
                }
            }
        }
        coveredFloors.sort(Comparator.comparingDouble(origin::distSqr));
    }

    private boolean hasFlanks(BlockPos p, Direction side, int vertical) {
        return hasFlank(p, side, vertical) && hasFlank(p, side.getOpposite(), vertical);
    }

    private boolean hasFlank(BlockPos p, Direction side, int vertical) {
        for (int width = 1; width <= 3; width++) {
            BlockPos column = p.relative(side, width);
            for (int rise = 2; rise <= 6; rise++)
                if (sight.ground.contains(column.above(vertical * rise))) return true;
        }
        return false;
    }

    private boolean wallBelow(BlockPos top) {
        for (int down = 0; down < 4; down++) if (!sight.walls.contains(top.below(down))) return false;
        return true;
    }

    private boolean sheer(BlockPos lip, BlockPos next) {
        for (int depth = 4; depth <= 8; depth++) if (sight.ground.contains(next.below(depth))) return true;
        // A cliff viewed from below or from the side can expose the face right up to its lip.
        return wallBelow(lip);
    }

    private boolean lowerInSight(BlockPos p, Direction d) {
        for (int ahead = 1; ahead <= 3; ahead++)
            for (int down = 2; down <= 8; down++)
                if (sight.ground.contains(p.relative(d, ahead).below(down))) return true;
        return false;
    }

    private boolean higherBehind(BlockPos p, Direction d) {
        for (int back = 1; back <= 3; back++)
            for (int up = 1; up <= 4; up++)
                if (sight.ground.contains(p.relative(d.getOpposite(), back).above(up))
                        || sight.walls.contains(p.relative(d.getOpposite(), back).above(up))) return true;
        return false;
    }

    private void findFord(BlockPos near, Direction d) {
        BlockPos first = near.relative(d);
        if (!sight.shallow.contains(first)) first = first.below();
        if (!sight.shallow.contains(first)) return;
        for (int n = 1; n <= 5; n++) {
            BlockPos next = first.relative(d, n);
            if (sight.shallow.contains(next)) continue;
            BlockPos far = sight.ground.contains(next) ? next : next.above();
            if (sight.ground.contains(far) && far.distSqr(origin) > near.distSqr(origin)) ford = nearest(ford, far);
            return;
        }
    }

    private BlockPos nearest(BlockPos old, BlockPos candidate) {
        return old == null || candidate.distSqr(origin) < old.distSqr(origin) ? candidate : old;
    }

    static String where(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) return "рядом со мной";
        String ns = dz < 0 ? "север" : "юг";
        String ew = dx < 0 ? "запад" : "восток";
        if (Math.abs(dx) * 2 < Math.abs(dz)) return dz < 0 ? "к северу от меня" : "к югу от меня";
        if (Math.abs(dz) * 2 < Math.abs(dx)) return dx < 0 ? "к западу от меня" : "к востоку от меня";
        return "на " + (ns.equals("север") ? "северо" : "юго") + "-" + ew + " от меня";
    }

    String answer(String focus, boolean checked, LandmarkMemory.Entry cave, LandmarkMemory.Entry ore) {
        List<String> lines = new ArrayList<>();
        boolean all = focus.equals("around");
        if (all && sight.monster != null) lines.add("Вижу монстра " + where(origin, sight.monster) + ".");
        if (all || focus.equals("descent")) {
            if (ridge != null) lines.add("Вижу гребень " + where(origin, ridge) + ", по обе стороны склоны.");
            if (ravine != null) lines.add("Похоже на овраг " + where(origin, ravine) + ", видны дно и оба борта.");
            if (cliff != null) lines.add("Вижу обрыв " + where(origin, cliff) + ".");
            else if (rim != null) lines.add("Вижу край " + where(origin, rim) + ", дно отсюда разглядеть трудно.");
            if (ledge != null) lines.add("Ниже виден уступ " + where(origin, ledge) + ".");
            if (slope != null) lines.add((checked ? "По видимым ступеням можно спуститься " : "Вижу ступенчатый склон ")
                    + where(origin, checked ? slopeFoot : slope) + (checked ? " до видимой площадки." : "."));
            if (!all && !checked) lines.add("Проход вниз с этого места не подтвердил.");
        }
        if (all || focus.equals("ford")) {
            if (bank != null) lines.add("Вижу берег " + where(origin, bank) + ".");
            else if (!sight.water.isEmpty()) lines.add("Вижу воду.");
            if (ford != null) lines.add((checked && focus.equals("ford") ? "Есть проход вброд " : "Похоже на брод ") + where(origin, ford) + ".");
            else if (!all) lines.add("Сквозного мелкого прохода отсюда не вижу.");
        }
        if (all || focus.equals("cave")) {
            if (cave != null) lines.add("Вижу проём под каменным сводом " + where(origin, cave.pos()) + ". Похоже на пещеру.");
            else if (!all) lines.add("Входа в пещеру отсюда не разглядел.");
        }
        if (all || focus.equals("biome")) {
            String biome = sight.biome(origin);
            if (!biome.isEmpty()) lines.add(biome);
            else if (!all) lines.add("По тому, что вижу, биом пока не определю.");
        }
        if (ore != null) lines.add("Вижу " + ore.label() + " " + where(origin, ore.pos()) + ".");
        return lines.isEmpty() ? "Осмотрелся. Заметных ориентиров отсюда не вижу." : String.join(" ", lines);
    }
}

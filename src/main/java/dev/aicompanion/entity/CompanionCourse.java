package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Locale;
import java.util.Map;

/**
 * walk_heading (issue #71): he leads the player along a compass heading, «веди на северо-восток». The way is a line from where
 * he started; he walks to points a dozen blocks ahead on it (CompanionTravel goes round walls and hills), and every next point is
 * on the line again, so a way round does not lead him off course. A point in lava or in water with no shore near is moved aside;
 * when no point is fit, he stops and says why; before wide water he names the nearer way round it. He waits for a player left
 * behind and hurries him on, and stops after the blocks asked, at the thing looked for, or at «стоп». The moments of the walk
 * are said by the model in his own words (the urgent remark of CompanionSocial); the work status keeps the plain line.
 */
public final class CompanionCourse {
    public static final int MAX_BLOCKS = 500;
    /** A walk until «стоп» is still bounded: this long, then he stops by himself. */
    public static final int LIMIT_SECONDS = 20 * 60;
    /** Points this far ahead are walked to at a walk (CompanionTravel runs to goals farther than twelve blocks). */
    private static final int LEG = 12;
    /** The next point is taken this near the current one, so the walk goes on without a stop at every point. */
    private static final double NEXT_AT = 5;
    /** Points tried when the one on the line is unfit or out of reach: on it, then aside of it. */
    private static final int[] ASIDE = {0, 8, -8, 16, -16};
    /** Water this wide along the way is a crossing (a brook, a river); wider is a lake or the sea. */
    private static final int CROSSING = 12;
    /** A player farther behind than this is waited for, at most WAIT_TICKS. */
    private static final int LAG = 24;
    private static final int WAIT_TICKS = 20 * 60;
    /** A player still behind after this is hurried on once more, before the wait runs out. */
    private static final int URGE_AGAIN = 20 * 30;
    /** A way round wide water is looked for this far to either side of the line. */
    private static final int DETOUR = 96;
    /**
     * Where the way was closed is remembered this long. A new walk is not led into it again: the same heading from this near,
     * or any heading that points (within thirty degrees) at a closing this far ahead.
     */
    private static final long REMEMBER_TICKS = 20L * 60 * 30;
    private static final int REMEMBER_NEAR = 32, REMEMBER_AHEAD = 400;
    /**
     * Said once wide water has closed the way twice, instead of a new heading at random (game 01.11: north — water, back south —
     * water, and so on): the way on is over water, which he fears, and a boat he cannot steer yet.
     */
    private static final String AFRAID = "Похоже, дальше отсюда только через большую воду. Её я побаиваюсь, а лодкой править пока не умею.";
    /** Without coming on along the line for this long, the way is closed. */
    private static final int STUCK_TICKS = 20 * 60;
    private static final int SCAN_RADIUS = 24;

    private record Heading(int x, int z, String name) { }
    /** A heading the way was closed on: where and when, and by what («большая вода», «лава», «нет прохода»). */
    private record Closed(Heading heading, double x, double z, long time, String what) { }
    private final java.util.List<Closed> closedWays = new java.util.ArrayList<>();
    private static final Map<String, Heading> HEADINGS = Map.of(
            "north", new Heading(0, -1, "север"), "northeast", new Heading(1, -1, "северо-восток"),
            "east", new Heading(1, 0, "восток"), "southeast", new Heading(1, 1, "юго-восток"),
            "south", new Heading(0, 1, "юг"), "southwest", new Heading(-1, 1, "юго-запад"),
            "west", new Heading(-1, 0, "запад"), "northwest", new Heading(-1, -1, "северо-запад"));
    /** What he can look for on the way, and how it is said when found. */
    private static final Map<String, String> TARGETS = Map.of("village", "деревню", "water", "воду", "forest", "лес", "animals", "животных");

    private final CompanionWork w;
    private Heading heading;
    private String target;
    private CompanionGame.Kind animal;
    private double startX, startZ, dirX, dirZ, best;
    private int blocks, aside;
    private BlockPos point;
    private long began, lastAdvance, waitingSince, nextScan;
    private int urged;
    private String closed;

    CompanionCourse(CompanionWork w) { this.w = w; }

    /** «east», «east:village», «east:cow»: a heading and, after a colon, what to look for on the way. */
    public static boolean valid(String item) {
        if (item == null) return false;
        String[] parts = item.trim().toLowerCase(Locale.ROOT).split(":", -1);
        if (parts.length > 2 || !HEADINGS.containsKey(parts[0])) return false;
        return parts.length == 1 || TARGETS.containsKey(parts[1]) || CompanionGame.Kind.of(parts[1]) != null;
    }

    /** «на северо-восток» for the dialogue; empty for an unknown heading. */
    public static String where(String item) {
        Heading heading = item == null ? null : HEADINGS.get(item.trim().toLowerCase(Locale.ROOT).split(":")[0]);
        return heading == null ? "" : "на " + heading.name;
    }

    /** {@code count}: blocks to walk, 0 for «until told to stop». Null when it can begin, else the refusal. */
    CompanionActionResult begin(String item, int count) {
        if (!valid(item)) return CompanionActionResult.failed("Не понял направление: север, юг, запад, восток или между ними.");
        if (count < 0 || count > MAX_BLOCKS) return CompanionActionResult.failed("За раз веду не дальше " + MAX_BLOCKS + " блоков.");
        String[] parts = item.trim().toLowerCase(Locale.ROOT).split(":");
        heading = HEADINGS.get(parts[0]);
        target = parts.length > 1 ? parts[1] : null;
        animal = target == null ? null : CompanionGame.Kind.of(target);
        // Into the water again that stopped him a while ago: no walk, he says so (game 01.11: stopped by the sea in the south, then
        // told to look for a village, the model sent him south again).
        if (w.owner.level() instanceof ServerLevel level) {
            Closed ahead = closedAhead(level, heading);
            if (ahead != null) {
                int far = (int) Math.round(Math.hypot(ahead.x - w.owner.getX(), ahead.z - w.owner.getZ()));
                return CompanionActionResult.failed((far <= REMEMBER_NEAR ? "На " + heading.name + " отсюда мы уже упёрлись: " + ahead.what + "."
                        : "На " + heading.name + " мы уже ходили: в " + far + " " + blocksWord(far) + " отсюда " + ahead.what + ".")
                        + (waters(level) >= 2 ? " " + AFRAID : ""));
            }
        }
        double length = Math.hypot(heading.x, heading.z);
        dirX = heading.x / length; dirZ = heading.z / length;
        startX = w.owner.getX(); startZ = w.owner.getZ();
        blocks = count; best = 0; aside = 0; point = null; closed = null;
        began = lastAdvance = nextScan = w.owner.level().getGameTime(); waitingSince = -1;
        return null;
    }

    void cancel() { heading = null; point = null; }

    String jobName() { return heading == null ? "путь" : "путь на " + heading.name; }

    void tick(ServerLevel level) {
        var owner = w.owner;
        long now = level.getGameTime();
        double along = along(owner.getX(), owner.getZ());
        if (along > best + 1) { best = along; lastAdvance = now; }
        if (blocks > 0 && along >= blocks - 3) { w.finish(true, "Прошли " + blocks + " " + blocksWord(blocks) + " на " + heading.name + "."); return; }
        if (now - began >= LIMIT_SECONDS * 20L) { w.finish(true, "Идём на " + heading.name + " уже двадцать минут, остановился передохнуть."); return; }
        // What was in sight at the start is not what was looked for: «пока не найдём воду» by a pond means another water.
        if (target != null && now >= nextScan && along >= SCAN_RADIUS) {
            nextScan = now + 20;
            if (found(level)) { w.route.stop(); w.finish(true, "Нашли " + (animal != null ? animal.many : TARGETS.get(target)) + ", вон там."); return; }
        }
        if (owner.fighting()) { lastAdvance = now; return; }
        var player = owner.getOwnerPlayer();
        if (player != null && player.level() == level && lags(player.getX(), player.getZ(), along)) {
            int behind = (int) Math.round(Math.hypot(player.getX() - owner.getX(), player.getZ() - owner.getZ()));
            if (waitingSince < 0) {
                waitingSince = now; urged = 1; w.route.stop(); owner.travel().reset(); point = null;
                say("Веду товарища на " + heading.name + ", а он отстал на " + behind + " " + blocksWord(behind)
                        + ". Я остановился и жду: подгони его, пусть догоняет.", "Не отставай, жду тебя.");
            } else if (urged == 1 && now - waitingSince >= URGE_AGAIN) {
                urged = 2;
                say("Товарищ так и не догнал: отстал на " + behind + " " + blocksWord(behind) + ", жду уже полминуты. "
                        + "Поторопи его: ещё полминуты, и дальше без него не пойду.", "Ну ты идёшь? Жду.");
            }
            owner.getLookControl().setLookAt(player, 30, 30);
            if (now - waitingSince > WAIT_TICKS) { w.finish(false, "Ты отстал, дальше без тебя не пойду. Стою тут."); return; }
            lastAdvance = now;
            return;
        }
        if (waitingSince >= 0) { waitingSince = -1; owner.workStatus("Веду на " + heading.name + "."); }
        if (now - lastAdvance > STUCK_TICKS) { closedWay(level, along); return; }
        if (point == null) {
            point = nextPoint(level, along);
            if (point == null) {
                if (aside >= ASIDE.length) closedWay(level, along);
                return;
            }
        }
        var step = w.route.legs(point, 2);
        if (step == CompanionTravel.Step.FAILED) { w.route.legsEnd(false); point = null; aside++; return; }
        if (step == CompanionTravel.Step.NEAR || Math.hypot(point.getX() + .5 - owner.getX(), point.getZ() + .5 - owner.getZ()) <= NEXT_AT) {
            if (step == CompanionTravel.Step.NEAR) w.route.legsEnd(true);
            point = null; aside = 0; closed = null;
        }
    }

    /** How far along the line from the start a place is. */
    private double along(double x, double z) { return (x - startX) * dirX + (z - startZ) * dirZ; }

    /** The player is far and not ahead of him: he waits. */
    private boolean lags(double x, double z, double along) {
        return Math.hypot(x - w.owner.getX(), z - w.owner.getZ()) > LAG && along(x, z) < along;
    }

    /** The next fit point from the current {@code aside} on, or null: not loaded yet (aside kept), or none fit (aside past the end). */
    private BlockPos nextPoint(ServerLevel level, double along) {
        double ahead = blocks > 0 ? Math.min(along + LEG, blocks) : along + LEG;
        for (; aside < ASIDE.length; aside++) {
            if (!level.hasChunkAt(column(along + LEG + CROSSING, ASIDE[aside]))) return null;
            BlockPos fit = fit(level, along, ahead, ASIDE[aside]);
            if (fit != null) return fit;
        }
        return null;
    }

    private BlockPos column(double along, int aside) {
        return BlockPos.containing(startX + dirX * along - dirZ * aside, 0, startZ + dirZ * along + dirX * aside);
    }

    /**
     * The standing place {@code ahead} on the line moved {@code aside}, or past a narrow water there; null (and the reason noted)
     * when lava lies on the way to it or the water on the way is wider than a crossing. Every block of the way is looked at, not
     * the point alone: lava between him and a fit point is walked into by no search, and he is not to stand at its edge either.
     */
    private BlockPos fit(ServerLevel level, double along, double ahead, int aside) {
        int water = 0;
        for (double at = along + 1; at <= ahead + CROSSING; at++) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column(at, aside));
            if (lavaNear(level, top)) { closed = "лава, обойти не вышло."; return null; }
            if (!level.getFluidState(top.below()).is(FluidTags.WATER)) { if (at >= ahead) return top; water = 0; continue; }
            if (++water > CROSSING) break;
        }
        if (closed == null || !closed.startsWith("лава")) closed = "большая вода, вплавь не пойду.";
        return null;
    }

    /**
     * The way on is closed: he says by what and remembers it. Wide water met the first time gets the nearer way round it; met
     * again, or all round him (an island), he says honestly that the way on is over water, and names no new heading at random.
     */
    private void closedWay(ServerLevel level, double along) {
        String reason = closed == null ? "не пройти: обойти не вышло." : closed;
        boolean water = closed != null && closed.startsWith("большая вода");
        closedWays.add(new Closed(heading, w.owner.getX(), w.owner.getZ(), level.getGameTime(),
                water ? "большая вода" : closed != null && closed.startsWith("лава") ? "лава" : "нет прохода"));
        while (closedWays.size() > 16) closedWays.removeFirst();
        boolean island = water && HEADINGS.values().stream().noneMatch(other -> dry(level, other));
        String more = island ? " Кругом вода, мы на острове. " + AFRAID : water && waters(level) >= 2 ? " " + AFRAID : water ? " " + detour(level, along) : "";
        w.finish(false, "Дальше на " + heading.name + " " + reason + more);
    }

    /** A remembered closing this walk would lead into again, or null. */
    private Closed closedAhead(ServerLevel level, Heading heading) {
        long now = level.getGameTime();
        double length = Math.hypot(heading.x, heading.z);
        for (Closed earlier : closedWays) {
            if (now - earlier.time > REMEMBER_TICKS) continue;
            double dx = earlier.x - w.owner.getX(), dz = earlier.z - w.owner.getZ(), far = Math.hypot(dx, dz);
            if (far <= REMEMBER_NEAR ? earlier.heading == heading
                    : far <= REMEMBER_AHEAD && !earlier.what.equals("нет прохода") && (dx * heading.x + dz * heading.z) / (far * length) >= Math.cos(Math.toRadians(30)))
                return earlier;
        }
        return null;
    }

    /** How many times wide water closed the way lately. */
    private long waters(ServerLevel level) {
        long now = level.getGameTime();
        return closedWays.stream().filter(earlier -> now - earlier.time <= REMEMBER_TICKS && earlier.what.equals("большая вода")).count();
    }

    /** Dry ground this heading from where he stands: land a leg away, with no lava and no water wider than a crossing before it. */
    private boolean dry(ServerLevel level, Heading heading) {
        double length = Math.hypot(heading.x, heading.z);
        int water = 0;
        for (int d = 1; d <= LEG + 2 * CROSSING; d++) {
            BlockPos column = BlockPos.containing(w.owner.getX() + heading.x / length * d, 0, w.owner.getZ() + heading.z / length * d);
            if (!level.hasChunkAt(column)) return false;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (lavaNear(level, top)) return false;
            if (level.getFluidState(top.below()).is(FluidTags.WATER)) { if (++water > CROSSING) return false; continue; }
            water = 0;
            if (d >= LEG) return true;
        }
        return false;
    }

    /**
     * The nearer way round wide water, as he offers it to the player: so far to the side where the line ahead has dry ground
     * again, then on along the heading; the player decides. Looked for up to DETOUR blocks to either side.
     */
    private String detour(ServerLevel level, double along) {
        String reason = closed;
        try {
            for (int off = 24; off <= DETOUR; off += 8) for (int side : new int[]{-1, 1}) {
                if (!level.hasChunkAt(column(along + LEG + CROSSING, side * off))) continue;
                if (fit(level, along, along + LEG, side * off) == null) continue;
                // Positive aside is to the right of the heading: (-z, x); the left is (z, -x).
                Heading turn = byVector(side > 0 ? -heading.z : heading.z, side > 0 ? heading.x : -heading.x);
                int walk = off + 4;
                return "Можно обойти: примерно " + walk + " " + blocksWord(walk) + " на " + turn.name + ", потом снова на " + heading.name + ". Идём?";
            }
            return "Обхода поблизости не видно.";
        } finally { closed = reason; }
    }

    private static Heading byVector(int x, int z) {
        for (Heading heading : HEADINGS.values()) if (heading.x == x && heading.z == z) return heading;
        throw new IllegalArgumentException(x + "," + z);
    }

    /** A moment of the walk for the model to say in his words, at once (not after the two-minute gap of other urgent remarks). */
    private void say(String fact, String status) {
        w.owner.workStatus(status);
        w.owner.socialEvent(dev.aicompanion.CompanionConversation.URGENT_MARK + fact);
    }

    private static boolean lavaNear(ServerLevel level, BlockPos top) {
        for (BlockPos pos : BlockPos.betweenClosed(top.offset(-2, -2, -2), top.offset(2, 0, 2)))
            if (level.getFluidState(pos).is(FluidTags.LAVA)) return true;
        return false;
    }

    private boolean found(ServerLevel level) {
        var owner = w.owner;
        AABB around = owner.getBoundingBox().inflate(SCAN_RADIUS, 8, SCAN_RADIUS);
        return switch (target) {
            case "village" -> level.structureManager().getStructureWithPieceAt(owner.blockPosition(), StructureTags.VILLAGE).isValid()
                    || !level.getEntitiesOfClass(Villager.class, around, Villager::isAlive).isEmpty();
            case "water", "forest" -> surface(level, target.equals("water")) >= 3;
            default -> !level.getEntitiesOfClass(Animal.class, around, animal -> animal.isAlive()
                    && (this.animal == null ? CompanionGame.Kind.of(animal) != null : animal.getType() == this.animal.type)).isEmpty();
        };
    }

    /** Columns round him (every fourth block) whose top is water, or a tree trunk. */
    private int surface(ServerLevel level, boolean water) {
        int count = 0;
        BlockPos at = w.owner.blockPosition();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx += 4) for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz += 4) {
            BlockPos column = at.offset(dx, 0, dz);
            if (!level.hasChunkAt(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
            if (water ? level.getFluidState(top).is(FluidTags.WATER) : level.getBlockState(top).is(BlockTags.LOGS)) count++;
        }
        return count;
    }

    /** «1 блок», «3 блока», «200 блоков». */
    static String blocksWord(int count) {
        int last = count % 10, tens = count % 100;
        return last == 1 && tens != 11 ? "блок" : last >= 2 && last <= 4 && (tens < 12 || tens > 14) ? "блока" : "блоков";
    }
}

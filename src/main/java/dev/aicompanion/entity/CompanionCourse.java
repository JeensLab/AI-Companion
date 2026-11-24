package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
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
 *
 * «Веди к крепости» (item «stronghold»): the heading is where an eye of ender of his flies. He throws one as a player does
 * (the game's own entity, one eye spent; four in five fall back and he picks his up), walks where it flew, throws a second some
 * two hundred blocks on, as a player who finds the place by two throws, walks to that place and throws the last over it: that
 * one goes down. The game names the place at the first throw; he walks as one who learns it by the second. He does not dig down.
 */
public final class CompanionCourse {
    public static final int MAX_BLOCKS = 500;
    /** A walk until «стоп» is still bounded: this long, then he stops by himself. */
    public static final int LIMIT_SECONDS = 20 * 60;
    /** Points this far ahead are walked to at a walk (CompanionTravel runs to goals farther than twelve blocks). */
    private static final int LEG = 12;
    /** The next point is taken this near the current one, so the walk goes on without a stop at every point. */
    private static final double NEXT_AT = 5;
    /** The last point lies this far past the end: the route stops two blocks short of its point, the walk ends on the count. */
    private static final int PAST = 3;
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
    private static final String STRONGHOLD = "stronghold";
    /** This near the place the last eye is thrown: within a dozen blocks an eye flies down into it. */
    private static final int OVER = 10;
    /** Times the rest of the way is laid anew from where a way round has brought him, before he says he cannot come nearer. */
    private static final int AIMS = 3;
    /** His fallen eye is gone for at most this long before he walks on without it (it fell into water, up a tree). */
    private static final int PICK_TICKS = 20 * 15;
    /**
     * Where an eye of ender thrown here flies: the game's own search (EnderEyeItem.use). A GameTest, whose flat world has no
     * stronghold, puts its point here.
     */
    static java.util.function.BiFunction<ServerLevel, BlockPos, BlockPos> stronghold =
            (level, from) -> level.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, from, 100, false);
    /** Blocks walked where the first eye flew before the second is thrown; fewer in a GameTest's arena. */
    static int secondThrow = 192;

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
    /** The eye's walk: where it flies, eyes thrown so far, whether the last was thrown over the place, ways laid anew to it. */
    private BlockPos goal;
    private int thrown, aims;
    private boolean over;
    /**
     * His eye in the air (kept past «стоп»: it is still his), whether it goes into the ground, and of the last one down: when,
     * whether it broke, and whether he is picking it up.
     */
    private EyeOfEnder flying;
    private boolean sinks, broke, picking;
    private long fell;
    private int urged;
    private String closed;

    CompanionCourse(CompanionWork w) { this.w = w; }

    /** «east», «east:village», «east:cow»: a heading and, after a colon, what to look for on the way. */
    public static boolean valid(String item) {
        if (item == null) return false;
        if (STRONGHOLD.equals(item.trim().toLowerCase(Locale.ROOT))) return true;
        String[] parts = item.trim().toLowerCase(Locale.ROOT).split(":", -1);
        if (parts.length > 2 || !HEADINGS.containsKey(parts[0])) return false;
        return parts.length == 1 || TARGETS.containsKey(parts[1]) || CompanionGame.Kind.of(parts[1]) != null;
    }

    /** «на северо-восток», «к крепости Края» for the dialogue; empty for an unknown heading. */
    public static String where(String item) {
        if (item != null && STRONGHOLD.equals(item.trim().toLowerCase(Locale.ROOT))) return "к крепости Края";
        Heading heading = item == null ? null : HEADINGS.get(item.trim().toLowerCase(Locale.ROOT).split(":")[0]);
        return heading == null ? "" : "на " + heading.name;
    }

    /** {@code count}: blocks to walk, 0 for «until told to stop». Null when it can begin, else the refusal. */
    CompanionActionResult begin(String item, int count) {
        if (!valid(item)) return CompanionActionResult.failed("Не понял направление: север, юг, запад, восток или между ними.");
        if (count < 0 || count > MAX_BLOCKS) return CompanionActionResult.failed("За раз веду не дальше " + MAX_BLOCKS + " блоков.");
        String[] parts = item.trim().toLowerCase(Locale.ROOT).split(":");
        goal = null;
        if (STRONGHOLD.equals(parts[0])) return beginEye();
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

    void cancel() { heading = null; point = null; goal = null; }

    String jobName() { return goal != null ? "путь к крепости Края" : heading == null ? "путь" : "путь на " + heading.name; }

    /** The first eye is thrown on the first tick of the walk, not at the order: an order refused after this would have spent it. */
    private CompanionActionResult beginEye() {
        if (!(w.owner.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Сейчас не могу.");
        // The Nether and the End have no stronghold: the game's search finds none there, and a player's eye does not fly either.
        // Asked first: «веди к крепости» in the Nether is answered about the place, not about his bag.
        BlockPos found = stronghold.apply(level, w.owner.blockPosition());
        if (found == null) return CompanionActionResult.failed("Здесь око не полетит: крепости Края отсюда не найти.");
        if (w.items.countCarried(Items.ENDER_EYE) == 0) return CompanionActionResult.failed("Нет ока Края: дай мне или скажи сделать.");
        goal = found; target = null; animal = null; thrown = 0; aims = 0; over = false; broke = false;
        began = nextScan = level.getGameTime(); waitingSince = -1;
        aim(began);
        return null;
    }

    /** The line from where he stands to the place: walked whole once two eyes have shown it, till then only to where the second is thrown. */
    private void aim(long now) {
        double dx = goal.getX() + .5 - w.owner.getX(), dz = goal.getZ() + .5 - w.owner.getZ(), far = Math.hypot(dx, dz);
        startX = w.owner.getX(); startZ = w.owner.getZ();
        dirX = far < 1 ? 1 : dx / far; dirZ = far < 1 ? 0 : dz / far;
        heading = byVector((int) Math.round(dirX), (int) Math.round(dirZ));
        blocks = (int) Math.max(1, Math.round(thrown < 2 ? Math.min(far, secondThrow) : far));
        best = 0; aside = 0; point = null; closed = null; lastAdvance = now;
    }

    /** An eye of his thrown as a player throws one (EnderEyeItem.use): it flies a dozen blocks toward the stronghold, or down into it. */
    private boolean throwEye(ServerLevel level) {
        var owner = w.owner;
        if (!owner.getMainHandItem().is(Items.ENDER_EYE) && !owner.equipForWork("minecraft:ender_eye").success()) return false;
        ItemStack held = owner.getMainHandItem();
        if (!held.is(Items.ENDER_EYE)) return false;
        EyeOfEnder eye = new EyeOfEnder(level, owner.getX(), owner.getY(.5), owner.getZ());
        eye.setItem(held);
        eye.signalTo(goal);
        level.gameEvent(GameEvent.PROJECTILE_SHOOT, eye.position(), GameEvent.Context.of(owner));
        level.addFreshEntity(eye);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.NEUTRAL, 1F, Mth.lerp(level.random.nextFloat(), .33F, .5F));
        held.shrink(1);
        owner.setChanged();
        owner.swing(InteractionHand.MAIN_HAND);
        owner.refreshInventorySummary();
        w.route.stop(); owner.travel().reset(); point = null;
        flying = eye; sinks = over; thrown++;
        return true;
    }

    /**
     * Every tick, also while a fight or the pickup has his legs and after «стоп»: the eye in the air is his until it breaks or
     * falls, and the fallen one is claimed the tick it falls (a tick of the walk that came late found none where it had hung and
     * called it broken). The one thrown over the place goes into the ground and is left there. An eye he cannot get to (in
     * water, up a tree) is given up, so the pickup does not turn him back for it again and again.
     */
    void watch() {
        if (!(w.owner.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (flying != null && !flying.isAlive()) {
            var fallen = sinks ? java.util.List.<ItemEntity>of()
                    : level.getEntitiesOfClass(ItemEntity.class, flying.getBoundingBox().inflate(2), item -> item.getItem().is(Items.ENDER_EYE));
            w.owner.claimDrops(fallen);
            flying = null; broke = fallen.isEmpty(); picking = !broke; fell = now;
        }
        if (picking && !w.owner.hasPendingLoot()) picking = false;
        if (picking && now - fell > PICK_TICKS) { w.owner.loot.shun(now + 20 * 300); picking = false; }
    }

    /** The eye's part of a tick; true when it took the tick (an eye in the air, his fallen one being picked up, a throw, the end). */
    private boolean eyeTick(ServerLevel level, long now, double along) {
        var owner = w.owner;
        // He watches it, as a player does: four seconds, then it falls or breaks.
        if (flying != null) { owner.getLookControl().setLookAt(flying, 30, 30); lastAdvance = now; return true; }
        if (over && thrown > 0) { w.finish(true, "Крепость под нами: око ушло в землю."); return true; }
        // His eye that fell is picked up before he walks on.
        if (picking) { lastAdvance = now; return true; }
        boolean eye = w.items.countCarried(Items.ENDER_EYE) > 0;
        // The place is not known by one throw: with no eye left he stops at once, and says where it flew.
        if (thrown == 1 && !eye) { w.finish(false, (broke ? "Око разбилось, больше нет." : "Око подобрать не смог, больше нет.") + " Летело на " + heading.name + "."); return true; }
        int far = (int) Math.round(Math.hypot(goal.getX() + .5 - owner.getX(), goal.getZ() + .5 - owner.getZ()));
        if (thrown > 0 && along < blocks - .5) return false;
        if (thrown >= 2 && far > OVER) {
            // Come off the line by a way round: the rest from here, a few times at most (the place may be in a lake, on a rock).
            aim(now);
            if (++aims > AIMS) w.finish(false, "Крепость в " + far + " " + blocksWord(far) + " на " + heading.name + ", но ближе подойти не смог.");
            return true;
        }
        if (!eye) {
            if (thrown == 0) w.finish(false, "Нет ока Края: дай мне или скажи сделать.");
            else w.finish(true, "По двум броскам крепость должна быть под нами. Проверить нечем: очи кончились.");
            return true;
        }
        over = far <= OVER;
        if (!throwEye(level)) { w.finish(false, "Не могу взять око в руку: в сумке нет места."); return true; }
        if (over) owner.workStatus("Бросил око над крепостью.");
        else if (thrown == 1) owner.workStatus("Бросил око Края: летит на " + heading.name + ".");
        else {
            aim(now);
            say("Бросил второе око Края и по двум броскам понял, где крепость: ещё " + far + " " + blocksWord(far) + " на " + heading.name
                    + ". Веду товарища туда.", "Крепость в " + far + " " + blocksWord(far) + " на " + heading.name + ".");
        }
        return true;
    }

    void tick(ServerLevel level) {
        var owner = w.owner;
        long now = level.getGameTime();
        double along = along(owner.getX(), owner.getZ());
        if (along > best + 1) { best = along; lastAdvance = now; }
        if (goal != null && eyeTick(level, now, along)) return;
        if (blocks > 0 && along >= blocks - .5) { w.finish(true, "Прошли " + blocks + " " + blocksWord(blocks) + " на " + heading.name + "."); return; }
        if (now - began >= LIMIT_SECONDS * 20L) {
            if (goal != null) w.finish(false, "Идём к крепости уже двадцать минут, остановился передохнуть. Скажи «веди к крепости» — пойдём дальше.");
            else w.finish(true, "Идём на " + heading.name + " уже двадцать минут, остановился передохнуть.");
            return;
        }
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
        double ahead = blocks > 0 ? Math.min(along + LEG, blocks + PAST) : along + LEG;
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

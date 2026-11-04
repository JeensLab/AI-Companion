package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The mine expedition of one work order: finds or reuses the entrance, walks down his own staircase, digs on down to diamond
 * depth, then 2x2 tunnels, and mines the ore he sees on the way. Every break, step and torch is a real one on the server tick;
 * it ends honestly with what was found, why it stopped and what is left undone.
 */
final class CompanionExpedition {
    private enum Stage { LOCATE, CHECK_NEAR, WALK_DOWN, DESCEND, BRANCH, BACKTRACK, DIG_OUT, CLIMB, RETREAT }

    private enum Ending { FOUND, SUPPLIES, BLOCKED, TIMEOUT, RETREAT }

    /** Extra time past the step's end for a cave episode under way to close its breach; a reserve used up ends the step anyway. */
    private static final int CAVE_RESERVE = 20 * 60;
    /** The most time the creeper guard may add to one step: a creeper that never leaves cannot hold the step open for ever. */
    private static final int GUARD_RESERVE = 20 * 120;
    private static final int STEP_TICKS = 20 * 540, NEAR_RADIUS = 12, CRAFT_EVERY = 200, LIGHT_CLIMB_RETRY = 600;
    private static final int SOFT_RETRIES = 3, WALK_RETRY = 40, OWN_STEPS = 4, MAX_TURNS = 8, FAR_ENTRANCE = 64, CLIMB_TICKS = 20 * 120, MAX_FRESH = 2;
    static final int UNDERGROUND_DEPTH = 2;
    private static final int WET_TICKS = 100;
    /** Within this many blocks of the entrance he walks down his own stairs from it instead of searching a route to their far bottom. */
    private static final int NEAR_ENTRANCE = 16;
    /** What the climb says of a way up it could not open (see {@link CompanionExpeditionClimb}). */
    /** How long a meal is given to raise his health before the climb, and how recent a blow from a mob keeps the climb. */
    private static final int MEAL_GRACE = 200, RECENT_HURT = 100;
    private long mealStart = -1;
    private float mealHealth;
    /** Wear of the iron pickaxe kept for the diamond itself, and how near a diamond in sight must be to spend the rest on rock. */
    private static final int DIAMOND_IRON = 4, DIAMOND_SIGHT = 8;
    private static final double ENTRANCE_NEAR = 1.0;
    private static final String CAVE_DANGER = "в пещере слишком опасно", NO_WAY_HERE = "Отсюда не свернуть и наверх не выйти.";
    /** The item of an order that is only the way up his own stairs, for a goal whose step needs the surface (wood) while he is deep in the mine. */
    static final String SURFACE = "surface";

    private final CompanionWork w;
    private final CompanionEntity owner;
    private final CompanionExpeditionDig dig;
    private final CompanionExpeditionClimb climb;
    private final CompanionExpeditionWalk lead;
    private final CompanionExpeditionOre ore;
    private final CompanionExpeditionCave cave;
    private final CompanionExpeditionRetreat retreat;
    private final CompanionExpeditionBacktrack backtrack;
    private final CompanionExpeditionDigOut digOut;
    private final CompanionExpeditionSupply supply;
    private final CompanionExpeditionBench bench;
    private final CompanionExpeditionStock stock;
    private final CompanionExpeditionFight fight;
    private List<BlockPos> route = List.of();
    private CompanionExpeditionStep plan;
    private Stage stage = Stage.LOCATE;
    private boolean active, hadStone, unheld, joining, atFoot, towardOrigin, surfaceOnly;
    private long stepEnd, nextCraft, nextArm, nextLightClimb;
    /** The surface entrance of the mine whose way up ended closed: kept over the steps (not cleared by {@link #reset}) until a climb gets out, so no climb walks into the same trap again. */
    private BlockPos noWayUpFrom;
    /** The walk to the stairs' foot began under the ground off the stairs, so surfacing on the way is no arrival at them. */
    private boolean joinedBelow;
    private long credited;
    private int startDiamonds, progress, stairSteps, turns, routeIndex, softFails, fresh;
    private BlockPos stand, head, sealCell;
    private Direction dir, tunnel;
    private String tunnelWhy = "", digOutWhy = "", resurfaceWhy;

    CompanionExpedition(CompanionWork w) {
        this.w = w;
        this.owner = w.owner;
        this.dig = new CompanionExpeditionDig(w);
        this.climb = new CompanionExpeditionClimb(w, dig);
        this.lead = new CompanionExpeditionWalk(dig).sealingLiquid(w);
        this.ore = new CompanionExpeditionOre(w, dig);
        this.cave = new CompanionExpeditionCave(w, dig, ore, () -> gained() >= w.requestedCount, this::cutStone);
        this.retreat = new CompanionExpeditionRetreat(w, dig);
        this.backtrack = new CompanionExpeditionBacktrack(w, dig);
        this.digOut = new CompanionExpeditionDigOut(w, dig);
        this.supply = new CompanionExpeditionSupply(w);
        this.bench = new CompanionExpeditionBench(w);
        this.stock = new CompanionExpeditionStock(w, dig);
        this.fight = new CompanionExpeditionFight(w);
    }

    /** What the stall watchdog sees move: blocks broken, steps finished, plugs placed in a cave, health regained while resting. */
    int progress() { return progress + dig.broken + dig.walked + lead.sealed() + cave.progress() + bench.progress() + stock.progress() + (stage == Stage.RETREAT ? (int) owner.getHealth() : 0); }

    boolean active() { return active; }

    /** The retreat is under way: the climb and the rest at the entrance, where health is got back. */
    boolean retreating() { return active && stage == Stage.RETREAT; }

    /** The retreat is still on the move up: a foe slower than he is left behind, not fought. */
    boolean fleeing() { return retreating() && retreat.leaving(); }

    String stageName() { return active ? stage.name() : ""; }

    String cavePhase() { return active ? cave.phaseName() : ""; }

    void expireStep(ServerLevel level) { stepEnd = level.getGameTime(); }

    long stepEnd() { return stepEnd; }

    /** The climb is cutting its way out of rock: the self-rescue may work. */
    boolean cuttingOut() { return active && stage == Stage.CLIMB && climb.cuttingOut(); }

    /** The whole time of a climb from now on: a cut out of a pit can start with that time nearly gone. */
    void climbTime() { holdStep(CLIMB_TICKS); }

    private void holdStep(long ticks) {
        stepEnd = Math.max(stepEnd, owner.level().getGameTime() + ticks);
        w.deadline = Math.max(w.deadline, stepEnd + 40);
    }

    /** The time the creeper guard took from the work is given back to the step, its order and the cave's explore time, up to a reserve. */
    void credit(long ticks) {
        long given = Math.min(ticks, GUARD_RESERVE - credited);
        if (!active || given <= 0) return;
        credited += given;
        stepEnd += given;
        w.deadline += given;
        cave.delay(given);
    }

    /** Below the mine's entrance: there a monster comes up the tunnel and is fought off wherever the player is. */
    boolean underground() { return active && w.mine.exists() && (owner.getY() < w.mine.entrance().getY() - UNDERGROUND_DEPTH || w.mine.buried()); }

    void begin(ServerLevel level) {
        reset();
        active = true;
        stepEnd = level.getGameTime() + STEP_TICKS;
        credited = 0;
        w.deadline = stepEnd + CAVE_RESERVE + 40;
        startDiamonds = w.items.countCarried(Items.DIAMOND);
        hadStone = w.items.countCarried(Items.STONE_PICKAXE) > 0;
        w.mine.newStep(level.getGameTime());
        unheld = !CompanionChunkTicket.hold(owner);
        if (unheld) owner.workStatus("Не смог удержать чанки вокруг шахты загруженными: если уйти далеко, могу встать.");
        if (SURFACE.equals(w.requestedItem)) {
            surfaceOnly = true;
            climb.begin("Под землёй дерева нет.", owner.blockPosition());
            stage = Stage.CLIMB;
            owner.workStatus("Поднимаюсь по своей лестнице на поверхность: под землёй дерева нет.");
        } else changeWay(level);
    }

    /**
     * The same stop came again: not the chain of the last order once more, but the next way of getting on. First the stairs anew from the spot, the
     * refusals forgotten and the dangers kept; with no side for that, and at the next repeat, the way up whatever it takes, to a new mine on the surface.
     */
    private void changeWay(ServerLevel level) {
        int repeats = w.mine.stopRepeats();
        BlockPos here = CompanionExpeditionRecord.standingCell(level, owner);
        if (repeats < 1 || repeats > CompanionMineSite.STOP_WAYS || !CompanionExpeditionRecord.deep(level, w.mine, here)) return;
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "stop_again", "here", here.toShortString(), "repeats", repeats, "restarts", w.mine.restarts());
        w.mine.refusalsForgotten();
        Direction side = repeats == 1 ? CompanionExpeditionRecord.cleanSide(level, w.mine, here) : null;
        if (side != null) {
            restartAt(level, here, side, 0, true);
            owner.workStatus("Снова встал на том же месте: начинаю лестницу заново отсюда, в другую сторону.");
        } else leaveForSurface(level, NO_WAY_HERE, true);
    }

    void reset() {
        dig.reset();
        climb.reset();
        lead.clear();
        CompanionChunkTicket.release(owner);
        active = hadStone = unheld = joining = joinedBelow = atFoot = towardOrigin = surfaceOnly = false;
        ore.reset();
        cave.reset();
        retreat.reset();
        backtrack.reset();
        digOut.reset();
        supply.reset();
        bench.reset();
        stock.reset();
        fight.reset();
        tunnelWhy = digOutWhy = "";
        resurfaceWhy = null;
        stage = Stage.LOCATE;
        plan = null;
        route = List.of();
        stand = head = sealCell = null;
        dir = tunnel = null;
        progress = stairSteps = turns = routeIndex = softFails = fresh = wetTicks = 0;
    }

    /** Ticks in water with nothing broken and no step walked since {@link #wetProgress}. */
    private int wetTicks, wetProgress;

    /**
     * His eyes in water for {@link #WET_TICKS} ticks with no progress (see {@link #progress()}), no cell being hit and no seal placed:
     * the stairs are lost under the water, not a step to wait out. Mining in water is slow, so a cell under the pick is never idle time.
     */
    private boolean stuckUnderwater() {
        int now = progress();
        boolean busy = plan != null && plan.busy(owner.level().getGameTime());
        // On a walk along his tunnel (no plan), water at the feet is as much a stall as water at the eyes.
        boolean wet = owner.isEyeInFluid(FluidTags.WATER) || plan == null && stage == Stage.BRANCH && owner.level().getFluidState(owner.blockPosition()).is(FluidTags.WATER);
        if (!wet || busy || now != wetProgress) { wetTicks = 0; wetProgress = now; return false; }
        if (++wetTicks <= WET_TICKS) return false;
        wetTicks = 0;
        wetProgress = -1;
        return true;
    }

    /** The game time of the next try after a failed walk to the stairs. */
    private long walkAgain;

    void tick(ServerLevel level) {
        if (!active) return;
        CompanionChunkTicket.follow(owner);
        boolean expired = level.getGameTime() >= stepEnd;
        boolean digging = stage == Stage.DESCEND || stage == Stage.BRANCH;
        // A foe is fought before anything else is done; the time it takes is given back to the step.
        if (!expired && fight.hold(level, stage == Stage.RETREAT)) return;
        // The cave looks at a breach a tick after it opens, so it must look before the timeout may end the step.
        boolean caveTurn = cave.tick(level, plan == null ? List.of() : plan.cells(), digging);
        if (expired && (!cave.busy() || level.getGameTime() >= stepEnd + CAVE_RESERVE)) { end(Ending.TIMEOUT, "Время вылазки вышло, остановился на месте."); return; }
        BlockPos plugged = cave.takePlugged();
        if (plugged != null && plan != null && plan.needs(plugged)) { plan.why = "дальше пещера"; plan.lasting = true; w.mine.forbid(plugged); blocked(); }
        if (caveTurn) {
            if (cave.failure() != null) end(Ending.BLOCKED, cave.failure());
            return;
        }
        if (gained() >= w.requestedCount && !cave.busy()) { end(Ending.FOUND, ""); return; }
        if (expired) return;
        // A bench he set down on a step shuts the stairs: it comes up before anything else, a retreat apart.
        if (stage != Stage.LOCATE && stage != Stage.RETREAT && bench.tick(level)) return;
        // The first tick locates the mine and then takes his hands for a craft: with a mob on him at low health the climb comes first, not ten ticks later.
        String danger = stage == Stage.CLIMB || stage == Stage.RETREAT || stage == Stage.LOCATE && !(w.mine.exists() && fight.hounded(level)) ? null : dangerNow();
        if (danger != null) { startRetreat(level, danger); return; }
        if (plan == null && digging && ore.pending() && mineOre(level, stage == Stage.BRANCH ? CompanionExpeditionOre.Mode.TUNNEL : CompanionExpeditionOre.Mode.NEAR)) return;
        if (plan == null && stage == Stage.DESCEND && stock.tick(level, stand, dir)) return;
        if (plan == null) craftTorches(level);
        if (plan == null && digging && level.getGameTime() >= nextLightClimb && outOfLight(level)) {
            if (climbForLight()) {
                owner.workStatus("Факелы кончились, а сделать их не из чего: поднимаюсь за припасами.");
                return;
            }
            nextLightClimb = level.getGameTime() + LIGHT_CLIMB_RETRY;
            owner.workStatus("Факелы кончились, а пути наверх нет: копаю дальше в темноте.");
        }
        if (plan == null) arm(level);
        if ((digging || stage == Stage.CHECK_NEAR || stage == Stage.WALK_DOWN) && stuckUnderwater()) { w.mine.underwaterStalled(); restartHere("Застрял в воде на своей лестнице."); return; }
        switch (stage) {
            case LOCATE -> locate(level);
            case CHECK_NEAR -> { if (!mineOre(level, CompanionExpeditionOre.Mode.WALK)) stage = Stage.WALK_DOWN; }
            case WALK_DOWN -> walkDown(level);
            case DESCEND -> descend();
            case BRANCH -> branch();
            case BACKTRACK -> backtrack();
            case DIG_OUT -> digOut();
            case CLIMB -> { if (climb.tick() == CompanionExpeditionClimb.Result.DONE) climbed(level); }
            case RETREAT -> { if (retreat.tick(level) == CompanionExpeditionRetreat.Result.DONE) end(Ending.RETREAT, retreat.text()); }
        }
    }

    /** Stone from the walls where he stands, for the plugs of a cave: true while it is being cut. */
    private boolean cutStone() {
        Direction along = stage == Stage.BRANCH && tunnel != null ? tunnel : dir;
        return stand != null && along != null && stock.tick((ServerLevel) owner.level(), owner.blockPosition(), along);
    }

    /** Why he must go up now (a very dangerous cave dealt with, or health at the cave limit), or null. */
    private String dangerNow() {
        if (!cave.busy() && cave.takeRetreat()) return CAVE_DANGER;
        // The cave sense reads a fire only inside a cave episode, and the goal no longer stops a step that owns the expedition for one.
        if (owner.isOnFire() || owner.isInLava()) return "горю";
        float health = owner.getHealth();
        if (health > CompanionCaveSense.HEALTH_VERY && !fight.wounded()) { mealStart = -1; return null; }
        // With food in the bag and no mob on him he eats on the spot (a fall, a blast, /damage); climbing at once would give up the order for a meal.
        // A living attacker that struck lately (a skeleton out of the fight's sight) or a harmful effect means a wound a meal does not end, and so does a meal that does not mend.
        if (CompanionEating.hasFood(owner) && !fight.hounded((ServerLevel) owner.level()) && !hurtByMobLately() && !hasHarmfulEffect() && mealMends(health)) return null;
        return "мало здоровья (" + (int) Math.ceil(health) + ")";
    }

    private boolean hurtByMobLately() { return owner.getLastHurtByMob() != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < RECENT_HURT; }

    private boolean hasHarmfulEffect() {
        for (var effect : owner.getActiveEffects()) if (!effect.getEffect().value().isBeneficial()) return true;
        return false;
    }

    /** True while the meal is young, or the health has risen since it began: the next check starts from the health now. */
    private boolean mealMends(float health) {
        long now = owner.level().getGameTime();
        if (mealStart < 0 || health > mealHealth) { mealStart = now; mealHealth = health; return true; }
        return now - mealStart < MEAL_GRACE;
    }

    /** The rest of the step is the climb and the rest at the entrance: the step's time and the order's deadline grow to hold them. */
    private void startRetreat(ServerLevel level, String why) {
        // A repeat danger from a cave with health to spare is not climbed away from again: he backs up the stairs and turns off.
        mealStart = -1;
        boolean bareHanded = cave.bareHanded();
        boolean again = why.equals(CAVE_DANGER) && (w.mine.retreats() >= 1 || bareHanded) && owner.getHealth() > CompanionCaveSense.HEALTH_WARY;
        CompanionExpeditionRecord.rememberMove(w.mine, stage == Stage.BRANCH ? tunnel : null, plan, stand, dir);
        plan = null;
        cave.close();
        lead.clear();
        backtrack.reset();
        dig.reset();
        if (again && backtrack.begin(level)) {
            // A bag without blocks says nothing of the cave: the next danger is no repeat of it.
            if (!bareHanded) w.mine.retreated();
            stage = Stage.BACKTRACK;
            owner.workStatus(bareHanded ? "Заделать пещеру нечем, поднимаюсь по лестнице и сверну в сторону." : "Пещера снова опасна, поднимаюсь по лестнице и сверну в сторону.");
            return;
        }
        w.mine.retreated();
        holdStep(CLIMB_TICKS + CompanionExpeditionRetreat.REST_TICKS);
        retreat.begin(why, fight.hounded(level));
        stage = Stage.RETREAT;
    }

    private boolean mineOre(ServerLevel level, CompanionExpeditionOre.Mode mode) {
        CompanionExpeditionOre.Result result = ore.step(level, mode);
        if (result == CompanionExpeditionOre.Result.FULL) end(Ending.BLOCKED, "Сумка полна, добытое некуда класть.");
        return result != CompanionExpeditionOre.Result.NONE;
    }

    /** True when a fight would draw him into a cave the mine has opened: see {@link CompanionExpeditionCave#shuns}. */
    boolean shuns(net.minecraft.world.entity.LivingEntity target) {
        return active && (cave.shuns(target) || fight.avoids(target, stage == Stage.RETREAT)
                || target instanceof net.minecraft.world.entity.monster.Creeper && !owner.creeperGuard.fights());
    }

    /** A mob the expedition fights now, wherever it stands within sight: the melee goal goes for it even from afar. */
    boolean engages(net.minecraft.world.entity.LivingEntity target) { return active && fight.isFoe(target); }

    void fightBegan() { if (active) fight.began(); }

    void fightGoing() {
        if (!active) return;
        fight.going();
        CompanionChunkTicket.follow(owner);
    }

    private int gained() { return w.items.countCarried(Items.DIAMOND) - startDiamonds; }

    private void locate(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) { end(Ending.BLOCKED, "Шахту копаю только в Верхнем мире."); return; }
        CompanionMineSite mine = w.mine;
        BlockPos here = owner.blockPosition();
        joining = joinedBelow = false;
        // Nothing to dig with and nothing to make a pickaxe of: the walk down his stairs would end at their foot with the climb back.
        // Not in a pit under the ground (a restarted mine's entrance is the pit itself): there the climb for wood is the way, and ending here would repeat for ever.
        boolean inPit = mine.buried() && !CompanionExpeditionRecord.underSky(level, here.above());
        if (mine.exists() && !inPit && here.getY() >= mine.entrance().getY() - UNDERGROUND_DEPTH && !hadStone && !dig.hasRockTool() && !supply.canMakePickaxe()) {
            end(Ending.SUPPLIES, CompanionExpeditionClimb.shortage(false, false) + " Копать нечем, остался наверху.");
            return;
        }
        if (mine.exists()) {
            mine.verify(level, here);
            int shunned = mine.firstShunned();
            if (shunned >= 0) { sealCell = mine.stands().get(shunned).above(); mine.forbid(sealCell); mine.truncate(shunned); }
            route = new ArrayList<>();
            // On the surface with a mine started again under the ground, the way down is the way he climbed, reversed: from the entrance on the surface down to the pit.
            boolean onSurface = mine.buried() && !mine.above().isEmpty() && mine.origin() != null && here.getY() >= mine.origin().getY() - UNDERGROUND_DEPTH && CompanionExpeditionRecord.underSky(level, here.above());
            if (onSurface) for (int i = mine.above().size() - 1; i >= 0; i--) route.add(mine.above().get(i));
            route.add(mine.entrance());
            route.addAll(mine.stands());
            routeIndex = nearest(here);
            if (routeIndex < 0 && atTunnel(here)) routeIndex = route.size() - 1;
            boolean nearEntrance = here.distSqr(mine.entrance()) <= NEAR_ENTRANCE * NEAR_ENTRANCE;
            if (routeIndex < 0 && !nearEntrance && !mine.stands().isEmpty() && here.getY() < mine.entrance().getY() - UNDERGROUND_DEPTH && !CompanionExpeditionRecord.underSky(level, here.above())) { joining = joinedBelow = true; stage = Stage.CHECK_NEAR; return; }
            if (routeIndex < 0 && onSurface) { stage = Stage.CHECK_NEAR; return; }
            if (routeIndex >= 0 || here.distSqr(mine.entrance()) <= FAR_ENTRANCE * FAR_ENTRANCE) { stage = Stage.CHECK_NEAR; return; }
        }
        // Far from the entrance and under the ground with no stairs around: a new mine starts from where he stands, as the surface is no place to choose from.
        // A buried record is no reason to start again while he stands on the surface: only under the ground is the pit the place to go on from.
        if (mine.exists() && (mine.buried() && !CompanionExpeditionRecord.underSky(level, here.above()) || here.getY() < mine.entrance().getY() - UNDERGROUND_DEPTH && !CompanionExpeditionRecord.underSky(level, here.above()))) { restartHere("Я под землёй, а своей лестницы рядом нет."); return; }
        CompanionShaftPlan.Entrance entrance = CompanionShaftPlan.chooseEntrance(level, here, NEAR_RADIUS);
        // No spot for a new entrance here (a lake shore): the known mine is the way out, the walk to it ends in a restart at the entrance if it fails.
        if (entrance == null && mine.exists()) { stage = Stage.CHECK_NEAR; return; }
        if (entrance == null) { end(Ending.BLOCKED, "Рядом нет места для входа в шахту: нужна твёрдая земля без построек и воды."); return; }
        mine.start(entrance.feet(), entrance.direction());
        route = List.of(entrance.feet());
        routeIndex = -1;
        stage = Stage.CHECK_NEAR;
    }

    private int nearest(BlockPos here) {
        int best = -1;
        double bestDistance = CompanionMineSite.NEAR_STAND;
        for (int i = 0; i < route.size(); i++) {
            double distance = route.get(i).distSqr(here);
            if (distance <= bestDistance) { best = i; bestDistance = distance; }
        }
        return best;
    }

    /** Standing in one of the dug tunnels, away from the stairs: the route's last stand leads on into it. */
    private boolean atTunnel(BlockPos here) {
        for (CompanionMineSite.Tunnel t : w.mine.tunnels()) {
            for (int k = 0; k <= t.length(); k++) {
                if (w.mine.foot().relative(t.direction(), k).distSqr(here) <= CompanionMineSite.NEAR_STAND) return true;
            }
        }
        return false;
    }

    private void walkDown(ServerLevel level) {
        if (level.getGameTime() < walkAgain) return;
        if (routeIndex < 0) {
            // A failed search can leave him on a partial path out to the surface: the way down is then his own stairs from the entrance, not the far foot.
            if (joining && joinedBelow && !w.mine.buried() && !belowTop((ServerLevel) owner.level()) && owner.getY() >= w.mine.entrance().getY() - UNDERGROUND_DEPTH) {
                w.route.legsEnd(false);
                joining = joinedBelow = false;
                softFails = 0;
            }
            int target = joining ? route.size() - 1 : 0;
            // A search stops up to two blocks short of its goal: the last steps are his own.
            if (owner.blockPosition().distManhattan(route.get(target)) <= OWN_STEPS) {
                if (dig.reach(route.get(target))) { routeIndex = target; softFails = 0; }
                else if (dig.walkStuck() && ++softFails > SOFT_RETRIES) restartHere(joining ? "Не смог вернуться на свою лестницу." : "Не смог дойти до входа в шахту.");
                else if (dig.walkStuck()) walkAgain = level.getGameTime() + WALK_RETRY;
                return;
            }
            CompanionTravel.Step walk = w.route.legs(route.get(target), ENTRANCE_NEAR);
            if (walk == CompanionTravel.Step.NEAR) { routeIndex = target; softFails = 0; }
            // A failed search can be a passing one (a mob in the way), and a restart gives up the whole staircase.
            else if (walk == CompanionTravel.Step.FAILED && ++softFails > SOFT_RETRIES) restartHere(joining ? "Не смог вернуться на свою лестницу." : "Не смог дойти до входа в шахту.");
            else if (walk == CompanionTravel.Step.FAILED) walkAgain = level.getGameTime() + WALK_RETRY;
            return;
        }
        if (routeIndex + 1 < route.size()) {
            if (route.get(routeIndex + 1).equals(w.mine.plug())) { takeOutPlug(level); return; }
            if (dig.reach(route.get(routeIndex + 1))) routeIndex++;
            // No way round to the next step (a chamber he cut beside the stairs): back to the stairs as a whole, the search of the walk finds a stand.
            else if (dig.walkStuck()) { joining = true; routeIndex = -1; softFails = 0; }
            return;
        }
        if (sealCell != null) {
            if (backtrack.seal(level, sealCell)) sealCell = null;
            return;
        }
        stand = route.get(routeIndex);
        dir = facing();
        ore.scan = true;
        stage = reachedDepth(level) ? Stage.BRANCH : Stage.DESCEND;
        if (stage == Stage.BRANCH) enterBranch();
    }

    /** His own plug from the last retreat comes out of the step before he goes down it; the block drops and is picked up like any other. */
    private void takeOutPlug(ServerLevel level) {
        BlockPos plug = w.mine.plug();
        CompanionExpeditionDig.Status status = dig.dig(level, plug);
        switch (status) {
            case WORKING -> { }
            case DONE -> { dig.flush(); w.mine.plugged(null); }
            case FULL -> end(Ending.BLOCKED, "Сумка полна, свою пробку на лестнице убрать некуда.");
            case NO_TOOL -> {
                // The plug is rock like any other: a stone pickaxe made on the spot, else the iron one while it has wear to spare.
                if (supply.craftStonePickaxe()) return;
                if (!dig.ironForRock && dig.spareIron()) { dig.ironForRock = true; return; }
                switch (dig.takeOut(level, plug)) {
                    case WORKING -> { }
                    case DONE -> { dig.flush(); w.mine.plugged(null); }
                    default -> end(Ending.SUPPLIES, CompanionExpeditionClimb.shortage(false, hadStone) + " Свою пробку на лестнице убрать нечем.");
                }
            }
            default -> end(Ending.BLOCKED, "Свою пробку на лестнице убрать не смог (" + switch (status) {
                case NO_HAND -> "не взял кирку в руку";
                default -> "не вижу её, не дотягиваюсь или не разбирается";
            } + ").");
        }
    }

    /** The direction of the last way down: the last move of the route, the stairs' own while there is none. */
    private Direction facing() {
        if (route.size() < 2) return w.mine.stairDirection();
        BlockPos before = route.get(route.size() - 2), last = route.get(route.size() - 1);
        if (before.getX() == last.getX() && before.getZ() == last.getZ()) return w.mine.stairDirection();
        return Direction.getNearest(last.getX() - before.getX(), 0, last.getZ() - before.getZ());
    }

    private boolean reachedDepth(ServerLevel level) {
        return stand.getY() <= CompanionShaftPlan.targetFeetY(level.getMinBuildHeight());
    }

    private void descend() {
        if (plan == null) {
            plan = CompanionExpeditionDetour.next(w, dig, (ServerLevel) owner.level(), stand, dir, turns < MAX_TURNS);
            if (plan == null) { startBacktrack("Дальше лестницу не прокопать: некуда свернуть"); return; }
        }
        advance();
    }

    /** The stairs end here: back up them to a stand with a side to turn off to, or start again where he stands. */
    private void startBacktrack(String why) {
        if (!backtrack.begin((ServerLevel) owner.level())) { restartHere(why + "."); return; }
        stage = Stage.BACKTRACK;
        owner.workStatus("Лестница упёрлась, поднимаюсь по ней и сверну в сторону.");
    }

    private void backtrack() {
        switch (backtrack.tick((ServerLevel) owner.level())) {
            case WORKING -> { }
            case STUCK -> restartHere("Не смог подняться по своей лестнице, чтобы свернуть в сторону.");
            case DONE -> {
                stand = backtrack.target();
                dir = backtrack.side();
                turns = softFails = 0;
                plan = CompanionExpeditionStep.landing(w, dig, stand, dir);
                stage = Stage.DESCEND;
            }
        }
    }

    /** The stairs are lost: the mine starts again where he stands, if the ground there allows and the restarts are not used up. */
    private void restartHere(String why) {
        ServerLevel level = (ServerLevel) owner.level();
        BlockPos here = CompanionExpeditionRecord.standingCell(level, owner);
        Direction side = CompanionExpeditionRecord.restartSide(level, w.mine, here);
        if (side == null) {
            CompanionExpeditionRecord.Shore shore = CompanionExpeditionRecord.shore(level, w.mine, here);
            if (shore != null) {
                restartAt(level, shore.cell(), shore.side(), -1);
                joining = false;
                owner.workStatus("Я в воде, выбираюсь на берег и начну шахту там.");
                return;
            }
            restartAtOrigin(level, CompanionExpeditionRecord.inWater(level, here) ? "Я в подземном озере, берега не нашёл." : why);
            return;
        }
        restartAt(level, here, side, 0);
        owner.workStatus("Потерял свою лестницу, начинаю шахту заново отсюда.");
    }

    /** No side is left where he stands: back to the entrance on the surface, once, to begin stairs to a side not tried from it. */
    private void restartAtOrigin(ServerLevel level, String why) {
        // A walk to the entrance that failed once is not tried again through yet another restart: the order ends instead of burning them all.
        Direction side = towardOrigin || !originNear() ? null : CompanionExpeditionRecord.originSide(level, w.mine);
        if (side == null) {
            if (startDigOut(level)) digOutWhy = why;
            else if (!leaveForSurface(level, why, false)) end(Ending.BLOCKED, why);
            return;
        }
        towardOrigin = true;
        restartAt(level, w.mine.origin(), side, -1);
        joining = false;
        owner.workStatus("Здесь не свернуть, возвращаюсь ко входу и начну лестницу в другую сторону.");
    }

    /** The entrance on the surface is close enough to the pit for a way to it to be built; from further off the search finds no leg. */
    private boolean originNear() {
        BlockPos origin = w.mine.origin();
        return origin != null && origin.distSqr(w.mine.entrance()) <= FAR_ENTRANCE * FAR_ENTRANCE;
    }

    /**
     * Nothing left to restart from under the ground: up the recorded way to the surface, where a mine starts at an entrance of the ground there.
     * False when there is no way up or the surface starts are used up; on the surface already, the new mine starts at once.
     * {@code anyway}: a stop that came again, with no recorded way the whole way to the entrance is opened or cut.
     */
    private boolean leaveForSurface(ServerLevel level, String why, boolean anyway) {
        CompanionMineSite mine = w.mine;
        if (!mine.exists() || mine.origin() == null || fresh >= MAX_FRESH) return false;
        if (owner.getY() >= mine.origin().getY() - UNDERGROUND_DEPTH) return startOnSurface(level);
        if (noWayUp()) return false;
        if (anyway) climb.beginAnyway(why, owner.blockPosition());
        else climb.begin(why, owner.blockPosition());
        if (!climb.walking()) { climb.reset(); return false; }
        resurfaceWhy = why;
        dropPlan();
        climbTime();
        stage = Stage.CLIMB;
        owner.workStatus("Ни свернуть, ни начать заново здесь не выходит, поднимаюсь по своей лестнице к поверхности.");
        return true;
    }

    private BlockPos top() { return w.mine.origin() != null ? w.mine.origin() : w.mine.entrance(); }

    private boolean noWayUp() { return w.mine.exists() && noWayUpFrom != null && noWayUpFrom.equals(top()); }

    private boolean belowTop(ServerLevel level) {
        BlockPos here = owner.blockPosition();
        return w.mine.exists() && here.getY() < top().getY() - UNDERGROUND_DEPTH && !CompanionExpeditionRecord.underSky(level, here.above());
    }

    private void climbed(ServerLevel level) {
        // A climb that ended closed, still under the ground, is no way up: the next lack of light digs on instead of walking into the same trap.
        if (!belowTop(level)) noWayUpFrom = null;
        else if (climb.text().contains(CompanionExpeditionClimb.CLOSED)) noWayUpFrom = top().immutable();
        if (surfaceOnly) {
            if (CompanionFacts.of(owner).belowMine()) end(Ending.BLOCKED, climb.text());
            else end(Ending.FOUND, "Поднялся на поверхность.");
            return;
        }
        if (resurfaceWhy == null) { end(Ending.SUPPLIES, climb.text()); return; }
        String why = resurfaceWhy;
        resurfaceWhy = null;
        if (!startOnSurface(level)) end(Ending.BLOCKED, CompanionExpeditionRecord.deep(level, w.mine, owner.blockPosition()) ? climb.text() : why);
    }

    /** On the surface with the old entrance out of reach: the mine begins at another entrance near him, on a side not tried from the first one. */
    private boolean startOnSurface(ServerLevel level) {
        CompanionMineSite mine = w.mine;
        BlockPos here = owner.blockPosition();
        if (fresh >= MAX_FRESH || mine.origin() == null || here.getY() < mine.origin().getY() - UNDERGROUND_DEPTH) return false;
        CompanionShaftPlan.Entrance entrance = CompanionShaftPlan.chooseEntrance(level, here, NEAR_RADIUS);
        if (entrance == null) return false;
        Direction side = entrance.direction();
        if (entrance.feet().equals(mine.origin())) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (mine.originTried(d) || CompanionShaftPlan.assessStep(level, entrance.feet(), d, mine.seals(), mine.avoid()).kind() == CompanionShaftPlan.Kind.BLOCKED) continue;
                side = d;
                break;
            }
        }
        fresh++;
        restartAt(level, entrance.feet(), side, -1);
        mine.buried(false);
        joining = false;
        owner.workStatus("Ко входу не пройти, начинаю шахту в другом месте на поверхности.");
        return true;
    }

    /** No side and no way to the entrance: the last restart, past the cap, here or, if his own plugs wall him in, below the ring he digs out of. */
    private boolean startDigOut(ServerLevel level) {
        if (!digOut.begin()) return false;
        dropPlan();
        stage = Stage.DIG_OUT;
        owner.workStatus("Ко входу не пройти и свернуть некуда, пробую начать шахту заново, если надо, прокопав выход вниз.");
        return true;
    }

    private void digOut() {
        ServerLevel level = (ServerLevel) owner.level();
        switch (digOut.tick(level)) {
            case WORKING -> { }
            case STUCK -> { if (!leaveForSurface(level, digOutWhy, false)) end(Ending.BLOCKED, digOutWhy); }
            case DONE -> {
                restartAt(level, digOut.at(), digOut.side(), 0);
                owner.workStatus("Потерял свою лестницу, начинаю шахту заново отсюда.");
            }
        }
    }

    private void dropPlan() {
        plan = null;
        sealCell = null;
        lead.clear();
        backtrack.reset();
        dig.reset();
    }

    private void restartAt(ServerLevel level, BlockPos at, Direction side, int index) { restartAt(level, at, side, index, false); }

    private void restartAt(ServerLevel level, BlockPos at, Direction side, int index, boolean keepCaves) {
        dropPlan();
        w.mine.restart(at, side, keepCaves);
        w.mine.buried(!CompanionExpeditionRecord.underSky(level, at.above()));
        route = new ArrayList<>(List.of(at));
        routeIndex = index;
        tunnel = null;
        turns = softFails = 0;
        stage = Stage.CHECK_NEAR;
    }

    private void branch() {
        if (plan == null) {
            if (tunnel == null && !startTunnel()) return;
            if (!lead.done()) {
                if (lead.tick() == CompanionExpeditionWalk.Result.STUCK) tunnel = null;
                return;
            }
            List<CompanionMineSite.Tunnel> tunnels = w.mine.tunnels();
            if (tunnels.get(tunnels.size() - 1).length() >= CompanionShaftPlan.MAX_TUNNEL_CELLS) { tunnel = null; return; }
            if (cave.crossesSeal(CompanionShaftPlan.tunnelCells(head, tunnel))) { tunnel = null; tunnelWhy = "дальше пещера"; return; }
            plan = CompanionExpeditionStep.tunnel(w, dig, head, tunnel);
        }
        advance();
    }

    /** Back into the last tunnel of a mine from before, or none yet. A refused last tunnel leaves him at the foot only when he stands there: after a reload he may be at its far end, and the next tunnel starts with the walk back. */
    private void enterBranch() {
        List<CompanionMineSite.Tunnel> tunnels = w.mine.tunnels();
        if (tunnels.isEmpty()) { tunnel = null; return; }
        CompanionMineSite.Tunnel last = tunnels.get(tunnels.size() - 1);
        if (w.mine.refused(w.mine.foot(), last.direction())) { tunnel = null; atFoot = w.mine.foot().distSqr(owner.blockPosition()) <= CompanionMineSite.NEAR_STAND; return; }
        tunnel = last.direction();
        head = w.mine.foot().relative(tunnel, last.length());
        lead.inTunnel(w.mine.foot(), last, true, owner.blockPosition());
    }

    /** The next direction from the stairs' foot; the way back is skipped, it would break through the step above. */
    private boolean startTunnel() {
        List<Direction> order = CompanionShaftPlan.tunnelOrder(dir);
        int used = w.mine.tunnels().size();
        if (used >= order.size() - 1) {
            CompanionExpeditionRecord.Base base = CompanionExpeditionRecord.moveBase(w.mine, atFoot);
            if (base == null) { startBacktrack("Все тоннели от лестницы пройдены" + (tunnelWhy.isEmpty() ? "" : " (" + tunnelWhy + ")")); return false; }
            dir = base.direction();
            order = CompanionShaftPlan.tunnelOrder(dir);
            used = 0;
            lead.set(base.path(), 0);
            owner.workStatus("Все тоннели пройдены, переношу начало тоннелей на конец длинного.");
        } else if (used > 0 && !atFoot) lead.inTunnel(w.mine.foot(), w.mine.tunnels().get(used - 1), false, owner.blockPosition());
        atFoot = false;
        tunnel = order.get(used);

        w.mine.openTunnel(tunnel);
        head = w.mine.foot();
        return true;
    }

    private void advance() {
        switch (plan.tick((ServerLevel) owner.level())) {
            case WORKING -> { }
            case DONE -> arrived();
            case BLOCKED -> blocked();
            case NO_TOOL -> {
                plan = null;
                if (!dig.lackedOre && supply.craftStonePickaxe()) return;
                // Normally the iron one is kept for ore and he goes up for supplies; from a mine started under the ground with no cells of the way up in the record there is no way up,
                // a step that began without a stone pickaxe has been up for them already: the way back down with nothing new is no use; and from a tunnel at the foot the climb costs more than the wear.
                if (!dig.lackedOre && !dig.ironForRock && (w.mine.buried() && w.mine.above().isEmpty() || !hadStone || stage == Stage.BRANCH || supply.stuckCrafting()) && dig.spareIron()) { dig.ironForRock = true; return; }
                // A diamond in sight is worth the last wear of the iron pickaxe: the wear kept for ore is for this very ore, and the way up needs none.
                if (!dig.lackedOre && !dig.ironForRock && dig.ironLeft(DIAMOND_IRON) && dig.diamondInSight((ServerLevel) owner.level(), DIAMOND_SIGHT)) { dig.ironForRock(DIAMOND_IRON); return; }
                // A pit under the ground has no supplies above it, and its entrance leads nowhere: wood and stone are at the first entrance on the surface.
                ServerLevel level = (ServerLevel) owner.level();
                if (!dig.lackedOre && !towardOrigin && w.mine.buried() && originNear() && CompanionExpeditionRecord.originSide(level, w.mine) != null) {
                    restartAtOrigin(level, CompanionExpeditionClimb.shortage(false, hadStone));
                    return;
                }
                climbForSupplies(CompanionExpeditionClimb.shortage(dig.lackedOre, hadStone));
            }
            case FULL -> end(Ending.BLOCKED, "Сумка полна, добытое некуда класть.");
        }
    }

    private void arrived() {
        CompanionExpeditionStep done = plan;
        plan = null;
        progress++;
        softFails = 0;
        supply.forgiveFailedCrafts();
        // The iron pickaxe digging the rock never runs out of a tool, so the stone one that failed for want of room is tried again on each new stand.
        if (dig.ironForRock && w.items.countCarried(Items.STONE_PICKAXE) == 0 && supply.canMakePickaxe() && supply.craftStonePickaxe()) dig.keepIron();
        ore.scan = true;
        switch (done.kind) {
            case STAIR -> {
                stand = done.to;
                w.mine.reached(stand);
                stairSteps++;
                owner.workStatus("Спускаюсь по лестнице: ступень " + stairSteps + ", ноги на высоте " + stand.getY() + ".");
                if (reachedDepth((ServerLevel) owner.level())) { stage = Stage.BRANCH; enterBranch(); }
            }
            case LANDING -> {
                dir = done.turn;
                stand = done.to;
                w.mine.reached(stand);
                turns++;
                owner.workStatus("Лестницу пришлось повернуть на " + name(dir) + ".");
            }
            case TUNNEL -> {
                w.mine.extendTunnel();
                head = done.to;
                owner.workStatus("Копаю тоннель на " + name(tunnel) + ", прошёл " + w.mine.tunnels().get(w.mine.tunnels().size() - 1).length() + ".");
            }
        }
    }

    private void blocked() {
        CompanionExpeditionStep failed = plan;
        plan = null;
        switch (failed.kind) {
            case STAIR -> {
                if (!failed.lasting && ++softFails <= SOFT_RETRIES) return;
                if (failed.lasting) w.mine.refuse(stand, dir);
                if (failed.lost) { restartHere("Потерял свою лестницу: " + failed.why + "."); return; }
                Direction side = turns >= MAX_TURNS ? null : CompanionShaftPlan.chooseTurn((ServerLevel) owner.level(), stand, dir, w.mine.seals(), w.mine.avoid());
                if (side == null) startBacktrack("Дальше лестницу не прокопать: " + failed.why);
                else plan = CompanionExpeditionStep.landing(w, dig, stand, side);
            }
            case LANDING -> {
                if (!failed.lasting && ++softFails <= SOFT_RETRIES) { plan = CompanionExpeditionStep.landing(w, dig, failed.from, failed.turn); return; }
                if (failed.lost) { restartHere("Потерял свою лестницу: " + failed.why + "."); return; }
                if (!failed.lasting) { startBacktrack("Свернуть в сторону тоже не вышло: " + failed.why); return; }
                w.mine.refuse(failed.from, failed.turn);
                Direction side = turns >= MAX_TURNS ? null : CompanionShaftPlan.chooseTurn((ServerLevel) owner.level(), failed.from, dir, w.mine.seals(), w.mine.avoid());
                if (side == null) startBacktrack("Свернуть в сторону тоже не вышло: " + failed.why);
                else plan = CompanionExpeditionStep.landing(w, dig, failed.from, side);
            }
            case TUNNEL -> { tunnel = null; tunnelWhy = failed.why; }
        }
    }

    /** Torches are laid by the lighting as he works; here the stock the step started with is kept up from coal and sticks. */
    private void craftTorches(ServerLevel level) {
        if (level.getGameTime() < nextCraft) return;
        nextCraft = level.getGameTime() + CRAFT_EVERY;
        if (w.items.countCarried(Items.TORCH) >= CompanionMilestones.TORCHES_STOCK) return;
        if (supply.canMakeTorch()) owner.craftFromBag("minecraft:torch", 4);
    }

    /** Underground in the dark with no torch and nothing to make one of: the next stretch would be dug unlit, so the way up for supplies is taken. */
    private boolean outOfLight(ServerLevel level) {
        return w.items.countCarried(Items.TORCH) == 0 && underground() && !supply.canMakeTorch() && owner.lighting().dark(level);
    }

    private void climbForSupplies(String why) {
        climbTime();
        stage = Stage.CLIMB;
        resurfaceWhy = null;
        climb.begin(why, owner.blockPosition());
    }

    /** The way up for light is only taken when the climb can really start; with none he digs on, and the way is looked for again after a while. */
    private boolean climbForLight() {
        if (noWayUp()) return false;
        climb.begin(CompanionExpeditionClimb.NO_TORCHES, owner.blockPosition());
        if (!climb.walking()) { climb.reset(); return false; }
        climbTime();
        stage = Stage.CLIMB;
        resurfaceWhy = null;
        return true;
    }

    /** A sword in the bag, or one made from the stone and sticks he carries, at a bench set down and taken up like the pickaxe's. */
    private void arm(ServerLevel level) {
        if (level.getGameTime() < nextArm) return;
        nextArm = level.getGameTime() + CRAFT_EVERY;
        if (!owner.equipment.hasMeleeWeapon() && supply.spareForSword()) supply.craftStoneSword();
    }

    private void end(Ending ending, String why) {
        dig.flush();
        if (ending == Ending.FOUND) w.mine.retreatsForgotten();
        String text = report() + (cave.notes().isEmpty() ? "" : " " + cave.notes()) + " " + why + rememberStop(ending);
        if (ore.missedDiamond && gained() == 0) text += " Видел алмазную руду, но взять её нечем: нужна железная кирка.";
        if (unheld) text += " Чанки вокруг шахты удержать не удалось.";
        text = text.strip();
        w.finish(switch (ending) {
            case FOUND -> CompanionActionResult.ok(text);
            case SUPPLIES -> CompanionActionResult.shortage(text);
            case BLOCKED -> CompanionActionResult.blocked(text);
            case TIMEOUT -> CompanionActionResult.timeout(text);
            case RETREAT -> CompanionActionResult.blocked(text);
        });
    }

    /** Keeps the stop for the next order, and says so when every way from it was tried. */
    private String rememberStop(Ending ending) {
        if (surfaceOnly || ending == Ending.RETREAT) { w.mine.stopForgotten(); return ""; }
        BlockPos here = owner.blockPosition();
        if (ending == Ending.FOUND || gained() > 0 || !CompanionExpeditionRecord.deep((ServerLevel) owner.level(), w.mine, here)) { w.mine.stopForgotten(); return ""; }
        w.mine.stopped(here, ending.ordinal());
        return w.mine.stopFinal() ? " Лестницу заново отсюда и подъём наверх уже пробовал: сам отсюда не выберусь." : "";
    }

    private String report() {
        StringBuilder text = new StringBuilder("Алмазов в сумке: " + w.items.countCarried(Items.DIAMOND) + " (добыто за вылазку: " + gained()
                + ", нужно " + w.requestedCount + "). Ноги на высоте " + owner.blockPosition().getY() + ", ступеней вниз " + stairSteps + ".");
        List<CompanionMineSite.Tunnel> tunnels = w.mine.tunnels();
        if (tunnels.isEmpty()) return text.toString();
        text.append(tunnels.size() == 1 ? " Тоннель " : " Тоннели ");
        for (int i = 0; i < tunnels.size(); i++) {
            text.append(i == 0 ? "" : ", ").append("на ").append(name(tunnels.get(i).direction())).append(' ').append(tunnels.get(i).length());
        }
        return text.append('.').toString();
    }

    private static String name(Direction direction) {
        return switch (direction) {
            case NORTH -> "север";
            case EAST -> "восток";
            case SOUTH -> "юг";
            case WEST -> "запад";
            default -> direction.getName();
        };
    }
}

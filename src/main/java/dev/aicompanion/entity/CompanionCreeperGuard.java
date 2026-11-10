package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The watch against a creeper that sees him: a goal of its own at the top priority, so that nothing (an order, a pickup, a fight)
 * keeps him from it, in any stage of the mine and with no order at all.
 * <p>
 * With health to spare and a way out of the blast from where he stands, he fights a creeper as a wary player does: the moment its
 * fuse is lit he strikes it first, while the fuse is young and the creeper within a lunge, and then runs, fast and away from it, until the
 * fuse is out ({@link Creeper#getSwellDir}); the melee goal, or the creeper itself, brings them together for the next blow. His reach is a
 * player's three blocks and the fuse lights at three, so the blow needs no step in most times. A sword or an axe is taken out for it when he
 * has one; with none he fights with his fists, and the fight only takes more of these rounds. Vanilla's fuse goes out beyond 7 blocks or
 * without a line of sight, and a blast reaches 6 blocks.
 * <p>
 * By the house a blast takes the walls with it: an unlit creeper there is first walked away from it, and a lit one there is run from with no
 * blow; one that would not be walked off is fought where it is for a while. On his own stairs or tunnel a blast takes the stands with it:
 * with blocks in the bag he first shuts the way between them (below), and fights only where that cannot be done.
 * <p>
 * The rest is the fallback, for when he cannot fight it (low health, too many retreats with no blow landed, nowhere to run): on his own
 * stairs or tunnel he shuts the way between them with a block from the bag, a few stands off, and waits behind it; anywhere else he walks
 * away from a creeper whose fuse is not lit (along his own passage when he is on it, or towards it if it is farther from the creeper than he
 * is), and dashes from one whose fuse is. The block is one of the mine's temporary blocks: it is taken up again when the walk reaches it
 * ({@link CompanionExpeditionDig}) and the creeper is gone.
 */
final class CompanionCreeperGuard {
    /** How near a creeper in sight is a reason to act: its fuse starts at three blocks and a blast kills from full health inside about 2.5. */
    static final double NEAR = 9;
    private static final double ON_HIM = 3;
    private static final int LOOK_EVERY = 5, FIGHT_LOOK_EVERY = 2, PLAN_EVERY = 10, EPISODE_TICKS = 20 * 12, CALM_TICKS = 20, PLACE_TRIES = 20;
    private static final int FLEE_CELLS = 6, SHUT_STANDS = 3, ON_ROUTE_SQR = 2, ROUTE_CREEPER_SQR = 4, PASSAGE_RANGE = 24, FLEE_RANGE = 12, FLEE_HEIGHT = 6;
    /** A step away from an unlit creeper keeps this near the player, when he is within the second range (else he is off on his own work). */
    private static final double LEASH = 12, LEASH_FROM = 32;
    private static final double FLEE_SPEED = 1.3;
    /**
     * The fight. Health above which a creeper is fought; the retreats with no blow landed he bears before he gives up the fight,
     * and how long they are remembered.
     */
    static final float FIGHT_HEALTH = 12;
    private static final int GIVE_UP = 6, FORGET_TICKS = 20 * 60;
    /**
     * The blow before the run: only while the fuse has burned less than this share (the run out of a blast takes some ten of its 30 ticks),
     * and with the creeper no farther than this.
     */
    private static final float STRIKE_BEFORE = .3F;
    private static final double LUNGE = 4;
    /** {@link Creeper#getSwelling} counts the fuse over this many ticks. */
    private static final int SWELL_TICKS = 28;
    /**
     * The house: a creeper this near its middle is walked away before the fight, until it is this far from it; he waits for it when it falls
     * this far behind, so that it keeps after him.
     */
    private static final double HOUSE_NEAR = 10, HOUSE_CLEAR = 16, LEAD_WAIT = 6, LEAD_REACHED = 1.5;
    /** Steps away from a creeper that has not left the things he went for, before he lets them lie, and for how long. */
    private static final int FLEES_FOR_LOOT = 4, LEAVE_LOOT_TICKS = 20 * 300;
    /** The way out of a lit fuse: the fuse goes out only beyond 7 blocks (a little more for a margin); the speed of that dash. */
    static final double SAFE_GAP = 7.4;
    private static final double FIGHT_FLEE_SPEED = 1.6;
    /**
     * A fuse burns out in 30 ticks. A creeper that has kept lighting one for this long, never out for more than the gap, without blowing is not burning down: it loses sight of
     * him and gets it back, as when he bobs in water behind a block. Such a fuse is no reason to stand and wait, until it has burned this share of its length.
     */
    private static final int STALE_FUSE_TICKS = 60, FUSE_LOOK_GAP = 30;
    private static final float BURNING = .35F;
    /** A fuse seen lit below this share is a new one: the one before went all the way out, as between the blows of a fight. */
    private static final float FRESH = .1F;
    /** A run along a heading (see {@link #runTo}) takes a rise or a drop of one block as a step. */
    private static final double TURN_AT = 30;
    private static final double[] RISES = {0, 1, -1};
    /** A creeper with a lit fuse is looked at before any other: this much nearer in the sort. */
    private static final double LIT_FIRST = 10_000;
    /**
     * The way out must not lead to another creeper: the creepers about are taken in to this range (in sight or not), the way out keeps off each
     * of them by what he has now or the blast gap, whichever is less, give or take the slack; and the places tried when his own preference fails.
     */
    private static final double THREAT_RANGE = 16, GAP_SLACK = .25;
    private static final int RING_HEADINGS = 8, AWAY_TRIES = 4, PATH_TESTS = 6;
    private static final int[] RING_RADII = {5, 9};
    /** How far the search for a way to a spot may go. */
    private static final float REACH_RANGE = 24;

    private final CompanionEntity owner;
    private boolean active, finished, noBlock, fighting, shielded, dashing, leading, mayStrike, room = true;
    private long begun, lastSeen, nextLook, nextPlan, nextProbe, lastRetreat, lastFlee, fuseSince, fuseLook, leadAgainAt;
    private int tries, retreats, flees, leadTried;
    private List<BlockPos> cells = List.of();
    private Vec3 flee, house;
    private Creeper watched, fuseOf;
    private String shown = "";
    private final CompanionShieldGuard shield;
    /** The way out while he runs, and a second one only to judge, before the fuse is lit, whether he would have one. */
    private final CompanionFuseEscape escape, probe;

    CompanionCreeperGuard(CompanionEntity owner) {
        this.owner = owner;
        shield = new CompanionShieldGuard(owner);
        escape = new CompanionFuseEscape(owner);
        probe = new CompanionFuseEscape(owner);
    }

    /** The shield is up against a lit fuse: the lowering of it with no other danger near ({@link CompanionShieldDefense}) waits. */
    boolean shielding() { return shield.holding(); }

    /** True when a creeper is fought: healthy, with a way out of its blast, and not after a run of retreats with no blow landed. */
    boolean fights() {
        if (owner.getHealth() <= FIGHT_HEALTH || !room) return false;
        if (retreats > 0 && owner.level().getGameTime() - lastRetreat > FORGET_TICKS) retreats = 0;
        return retreats < GIVE_UP;
    }

    boolean canStart() {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()) return false;
        long now = level.getGameTime();
        if (now < nextLook) return false;
        nextLook = now + (fights() ? FIGHT_LOOK_EVERY : LOOK_EVERY);
        Creeper creeper = seen(level);
        watched = creeper;
        noBlock = false;
        lookAtFuse(creeper, now);
        if (creeper != null && now >= nextProbe && owner.getHealth() > FIGHT_HEALTH) {
            nextProbe = now + PLAN_EVERY;
            probe.plan(level, threats(level));
            room = probe.clears();
        }
        shielded = creeper != null && burns(creeper) && shield.covers(level, creeper);
        return creeper != null && (shielded || !(!lit(creeper) && engaged()) && plan(level, creeper));
    }

    /**
     * Armed on an expedition with another hostile on him: a creeper whose fuse is not lit is no reason to turn his back on it, whatever his
     * health. The turn is the fight's; the guard takes it back when the fuse is lit.
     */
    private boolean engaged() {
        if (!owner.work.expedition.active() || !owner.equipment.hasMeleeWeapon()) return false;
        return !owner.level().getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(ON_HIM),
                foe -> !(foe instanceof Creeper) && foe.isAlive() && !foe.isNoAi() && owner.hasLineOfSight(foe)).isEmpty();
    }

    /** A creeper that can hurt him stands in sight within this many blocks, foe or not: a pickup or a torch must not take his turn then. */
    boolean nearby(double range) {
        return !owner.level().getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(range),
                creeper -> creeper.isAlive() && !creeper.isNoAi() && creeper.distanceTo(owner) < range && owner.hasLineOfSight(creeper)).isEmpty();
    }

    /** Vanilla sets the swell direction only on its next tick after {@code ignite()}, so the flag counts as a lit fuse too. */
    static boolean lit(Creeper creeper) { return creeper.getSwellDir() > 0 || creeper.isIgnited(); }

    /** The creeper whose fuse he has seen lit, and since when it has kept lighting. */
    private void lookAtFuse(Creeper creeper, long now) {
        if (creeper == null || !lit(creeper)) return;
        if (creeper != fuseOf || now - fuseLook > FUSE_LOOK_GAP || creeper.getSwelling(0) < FRESH) { fuseOf = creeper; fuseSince = now; }
        fuseLook = now;
    }

    /** A lit fuse that is a reason to leave everything else: not one that has stayed lit past its time without burning down (see {@link #STALE_FUSE_TICKS}). */
    boolean burns(Creeper creeper) {
        return lit(creeper) && (creeper != fuseOf || owner.level().getGameTime() - fuseSince < STALE_FUSE_TICKS || creeper.getSwelling(0) >= BURNING);
    }

    boolean canContinue() { return active && !finished && owner.isAlive() && owner.level().getGameTime() - begun < EPISODE_TICKS; }

    void start() {
        long now = owner.level().getGameTime();
        active = true;
        finished = false;
        begun = lastSeen = now;
        nextPlan = now + PLAN_EVERY;
        tries = 0;
        owner.getNavigation().stop();
        escape.reset();
        shown = "";
        int path = -1;
        // Neither the melee goal nor the work (with the fight's hold) runs while the guard holds the turn, so they cannot bring a weapon out for it.
        if (fighting || dashing) {
            owner.work.expedition.fightBegan();
            owner.selectWeaponForCombat();
            path = escape.plan((ServerLevel) owner.level(), threats((ServerLevel) owner.level()));
        }
        // The blow first only with a way out of the blast after it, and not by the house unless walking it off has failed: the melee goal
        // fights it there then too.
        mayStrike = fighting && escape.clears() && (houseNear(watched) == null || now < leadAgainAt);
        if (fighting && !escape.clears()) room = false;
        if (fighting) {
            retreats++;
            lastRetreat = now;
        }
        owner.workStatus(shielded ? "Крипер поджёг фитиль, прикрываюсь щитом." : leading ? "Увожу крипера от дома." : mayStrike ? "Бью крипера и отскакиваю от взрыва."
                : fighting ? "Крипер поджёг фитиль, отхожу, потом добью." : "Рядом крипер, " + (cells.isEmpty() ? "ухожу от него." : "закрываю проход блоком."));
        event(shielded ? "shield" : leading ? "lead" : mayStrike ? "strike_first" : fighting ? "retreat" : dashing ? "dash" : cells.isEmpty() ? "flee" : "block",
                watched, path, "cells", cells);
        if (!shielded && !fighting && !leading && cells.isEmpty()) leaveLootAfterFlees(now);
    }

    /** A creeper that stays by the things he goes for, with nothing to meet it, would have him step away and come back for ever: the things are let lie. */
    private void leaveLootAfterFlees(long now) {
        if (now - lastFlee > FORGET_TICKS || !owner.loot.hasPending()) flees = 0;
        lastFlee = now;
        if (!owner.loot.hasPending() || ++flees < FLEES_FOR_LOOT) return;
        flees = 0;
        owner.loot.shun(now + LEAVE_LOOT_TICKS);
        owner.autonomy().report("Крипер не отходит от вещей, оставляю их там.");
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "creeper_guard", "entity", owner.getUUID(), "action", "leave_loot");
    }

    void tick() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Creeper creeper = seen(level);
        lookAtFuse(creeper, now);
        if (shielded) {
            shielded = creeper != null && burns(creeper) && shield.hold(level, creeper);
            if (shielded) lastSeen = now; else finished = true;
            return;
        }
        if (creeper == null) {
            if (now - lastSeen >= CALM_TICKS) finished = true;
            return;
        }
        lastSeen = now;
        owner.getLookControl().setLookAt(creeper, 30, 30);
        if (lit(creeper) && !burns(creeper) && (fighting || dashing)) event("stale_fuse", creeper, -1);
        if ((fighting || engaged()) && !burns(creeper)) { finished = true; return; }
        if (fighting) {
            if (!mayStrike || !strikeFirst(level, creeper)) retreat(level, creeper);
            return;
        }
        if (leading) { lead(level, creeper, now); return; }
        if (dashing && burns(creeper)) { retreat(level, creeper); return; }
        if (now >= nextPlan || cells.isEmpty() && flee == null || cells.isEmpty() && dashing != burns(creeper)) {
            nextPlan = now + PLAN_EVERY;
            if (!plan(level, creeper)) { finished = true; return; }
        }
        if (!cells.isEmpty()) { shut(); return; }
        if (flee != null) owner.getNavigation().moveTo(flee.x, flee.y, flee.z, fighting ? FIGHT_FLEE_SPEED : FLEE_SPEED);
    }

    void stop() {
        if (!active) return;
        // A creeper that would not be walked off in a whole episode is fought where it is for a while.
        if (leading && owner.level().getGameTime() - begun >= EPISODE_TICKS) leadAgainAt = owner.level().getGameTime() + FORGET_TICKS;
        active = fighting = shielded = dashing = leading = mayStrike = false;
        shield.release();
        escape.reset();
        flee = null;
        cells = List.of();
        owner.getNavigation().stop();
        owner.work.credit(owner.level().getGameTime() - begun);
    }

    /** One tick of placing the blocks; with none in the bag, or after too many tries, he walks away instead. */
    private void shut() {
        List<BlockPos> left = new ArrayList<>(cells);
        for (BlockPos cell : cells) {
            CompanionSeal.Result result = CompanionSeal.place(owner, cell);
            if (result == CompanionSeal.Result.PLACED) {
                owner.work.mine.temporary(cell);
                left.remove(cell);
            } else if (result == CompanionSeal.Result.NO_BLOCK || ++tries > PLACE_TRIES) {
                noBlock = true;
                left.clear();
                break;
            }
        }
        cells = left;
        if (cells.isEmpty()) nextPlan = 0;
    }

    /** The nearest creeper with a way to hurt him that he can see (one with a lit fuse first); one with its AI off can neither walk nor swell. */
    private Creeper seen(ServerLevel level) {
        Creeper nearest = null;
        double best = Double.MAX_VALUE;
        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(NEAR),
                creeper -> creeper.isAlive() && !creeper.isNoAi() && creeper.distanceTo(owner) < NEAR && owner.hasLineOfSight(creeper))) {
            double distance = creeper.distanceToSqr(owner) - (lit(creeper) ? LIT_FIRST : 0);
            if (distance < best) { nearest = creeper; best = distance; }
        }
        return nearest;
    }

    /**
     * What to do about this creeper. Fighting it: the blow and the run once its fuse is lit ({@link #strikeFirst}, {@link #retreat}), and
     * before that nothing, save walking it off the house. Otherwise: blocks to set on his route underground; with its fuse lit and no blocks
     * to set, the same way out as in the fight (the navigation has a start to wait for and may lead past the creeper or nowhere, and the fuse
     * is 30 ticks); else a place to walk to: along his route underground, by the player when he is near, else away. False when there is
     * nothing to do.
     */
    private boolean plan(ServerLevel level, Creeper creeper) {
        cells = List.of();
        flee = null;
        dashing = leading = false;
        if (lit(creeper) && !burns(creeper)) return false;
        CompanionMineSite mine = owner.work.mine;
        List<BlockPos> way = mine.exists() && level.dimension() == net.minecraft.world.level.Level.OVERWORLD ? CompanionExpeditionClimb.wayUp(mine) : List.of();
        int at = indexOf(way, owner.blockPosition(), ON_ROUTE_SQR);
        boolean underground = at >= 0 && !level.canSeeSky(owner.blockPosition());
        if (underground && !noBlock && CompanionSeal.available(owner) > 0) {
            cells = shutCells(mine, way, at, creeper);
            if (!cells.isEmpty()) return true;
        }
        if (fights()) {
            if (lit(creeper)) { fighting = true; return true; }
            house = level.getGameTime() < leadAgainAt ? null : houseNear(creeper);
            leading = house != null;
            return leading;
        }
        if (lit(creeper)) { dashing = true; return true; }
        List<Creeper> threats = threats(level);
        if (underground) {
            Vec3 target = alongRoute(way, at, creeper, FLEE_CELLS), clear = retreatSpot(way, threats, target);
            flee = clear != null ? clear : target;
            return flee != null;
        }
        // By the player a step away stays by him: a creeper that hunts him would else lead him off a step at a time (review of PR #95).
        var player = owner.getOwnerPlayer();
        if (player != null && player.level() == level && owner.distanceTo(player) <= LEASH_FROM) {
            flee = byThePlayer(threats, creeper, player.position());
            return true;
        }
        Vec3 target = awayFrom(way, creeper);
        Vec3 clear = retreatSpot(way, threats, target);
        flee = clear != null ? clear : target;
        return flee != null;
    }

    /**
     * A step away from the creeper no farther than {@link #LEASH} from the player: the spot that keeps clear of every creeper and gains on
     * this one most, the nearer to the player the better. With none, to the player himself: he is the one who can deal with it.
     */
    private Vec3 byThePlayer(List<Creeper> threats, Creeper creeper, Vec3 player) {
        double now = owner.distanceTo(creeper);
        List<Vec3> spots = ring();
        for (int i = 0; i < AWAY_TRIES; i++) {
            Vec3 spot = LandRandomPos.getPosAway(owner, FLEE_RANGE, FLEE_HEIGHT, creeper.position());
            if (spot != null) spots.add(spot);
        }
        List<Vec3> ranked = spots.stream().filter(spot -> spot.distanceTo(player) <= LEASH && spot.distanceTo(creeper.position()) > now
                        && clear(spot, threats, GAP_SLACK))
                .sorted(Comparator.comparingDouble((Vec3 spot) -> -gap(spot, threats)).thenComparingDouble(spot -> spot.distanceTo(player)))
                .limit(PATH_TESTS).toList();
        for (Vec3 spot : ranked) if (reachable(spot)) return spot;
        return player;
    }

    /**
     * The fuse is lit and he is to be out of its sight before it blows: along the way {@link CompanionFuseEscape} finds over the floor as it
     * is, a push at a time to the next cell. Where there is none, under rock a wall of blocks between him and the creeper (in his mine one of its
     * temporary blocks, taken up again); by the house, or with no blocks, a blow at it: it throws it back, which is more than standing.
     */
    private void retreat(ServerLevel level, Creeper creeper) {
        Vec3 step = escape.step(level, threats(level));
        if (step != null) {
            mention(escape.holding() ? "hold" : "path", creeper, escape.goal());
            owner.getNavigation().stop();
            faceTowards(owner, step);
            owner.getMoveControl().setWantedPosition(step.x, step.y, step.z, FIGHT_FLEE_SPEED);
            return;
        }
        // No wall of cobblestone is left standing in the player's house.
        if (!byTheHouse() && escape.shut(level, creeper)) { mention("wall", creeper, null); return; }
        mention("strike", creeper, null);
        strike(creeper);
    }

    /**
     * The blow before the run: while the fuse is young and the creeper within a lunge he stands in reach (stepping in when it is not) until
     * his swing is ready, strikes, and runs that very tick. True while the blow is still to come; once it is struck or its time is gone, the
     * run ({@link #retreat}) is all there is.
     */
    private boolean strikeFirst(ServerLevel level, Creeper creeper) {
        // A blow just dealt by the melee goal leaves no swing ready in time: that blow was the first one, and now he runs.
        float left = STRIKE_BEFORE - creeper.getSwelling(0);
        if (left <= 0 || owner.distanceTo(creeper) > LUNGE || owner.combat.ticksToSwing() > left * SWELL_TICKS) {
            mayStrike = false;
            event("strike_late", creeper, -1);
            return false;
        }
        if (!owner.isWithinMeleeAttackRange(creeper)) {
            owner.getNavigation().moveTo(creeper, FIGHT_FLEE_SPEED);
            return true;
        }
        owner.getNavigation().stop();
        if (!owner.combat.swingReady()) return true;
        mayStrike = false;
        // The way out was judged where the fuse was seen; the step in may have left it behind.
        if (escape.plan(level, threats(level)) < 0 || !escape.clears()) { event("strike_cornered", creeper, -1); return false; }
        boolean landed = strike(creeper);
        if (landed) landed();
        event("struck", creeper, -1, "landed", landed);
        return false;
    }

    /**
     * By the house: he walks the creeper off it, to a spot farther from the house than he is, and waits for it when it falls behind, until
     * it stands {@link #HOUSE_CLEAR} from the house; then the fight. A lit fuse on the way, or nowhere to walk, ends the walk.
     */
    private void lead(ServerLevel level, Creeper creeper, long now) {
        if (lit(creeper)) { finished = true; return; }
        if (creeper.position().distanceTo(house) >= HOUSE_CLEAR) { event("led_away", creeper, -1); finished = true; return; }
        if (owner.distanceTo(creeper) > LEAD_WAIT) { owner.getNavigation().stop(); return; }
        // Off the ground (a jump, a step down, a teleport) no way is searched at all: that is no proof there is nowhere to go.
        if (!owner.onGround() && flee == null) return;
        if (owner.onGround() && (flee == null || now >= nextPlan || owner.position().distanceTo(flee) < LEAD_REACHED)) {
            nextPlan = now + PLAN_EVERY;
            flee = leadSpot(threats(level));
            if (flee == null) {
                leadAgainAt = now + FORGET_TICKS;
                event("lead_nowhere", creeper, -1, "candidates", leadTried);
                finished = true;
                return;
            }
        }
        owner.getNavigation().moveTo(flee.x, flee.y, flee.z, FLEE_SPEED);
    }

    /** The middle of his home when this creeper stands near enough to it for a blast to take its walls; else null. */
    private Vec3 houseNear(Creeper creeper) {
        CompanionPlaces.Home home = owner.places.home();
        if (home == null) return null;
        Vec3 middle = Vec3.atBottomCenterOf(home.pos());
        return creeper.position().distanceTo(middle) < HOUSE_NEAR ? middle : null;
    }

    /** He stands near enough to his home for a blast there to take its walls. */
    private boolean byTheHouse() {
        CompanionPlaces.Home home = owner.places.home();
        return home != null && owner.position().distanceTo(Vec3.atBottomCenterOf(home.pos())) < HOUSE_NEAR;
    }

    /** Where to walk the creeper: a spot farther from the house than he stands, whose walk keeps clear of every creeper, the farthest first. */
    private Vec3 leadSpot(List<Creeper> threats) {
        double from = owner.position().distanceTo(house);
        List<Vec3> spots = ring();
        for (int i = 0; i < AWAY_TRIES; i++) {
            Vec3 spot = LandRandomPos.getPosAway(owner, FLEE_RANGE, FLEE_HEIGHT, house);
            if (spot != null) spots.add(spot);
        }
        List<Vec3> ranked = spots.stream().filter(spot -> spot.distanceTo(house) > from && clear(spot, threats, GAP_SLACK))
                .sorted(Comparator.comparingDouble((Vec3 spot) -> -spot.distanceTo(house))).limit(PATH_TESTS).toList();
        leadTried = ranked.size();
        for (Vec3 spot : ranked) if (reachable(spot)) return spot;
        return null;
    }

    /** Spots round him on the level he stands at, in every heading and at each ring radius. */
    private List<Vec3> ring() {
        List<Vec3> spots = new ArrayList<>();
        for (int heading = 0; heading < RING_HEADINGS; heading++) for (int radius : RING_RADII) {
            double angle = heading * 2 * Math.PI / RING_HEADINGS;
            spots.add(owner.position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        }
        return spots;
    }

    /** A retreat event when the way or the mode changes, not every tick. */
    private void mention(String mode, Creeper creeper, BlockPos goal) {
        String key = mode + goal;
        if (key.equals(shown)) return;
        shown = key;
        event("retreat_" + mode, creeper, escape.length(), "goal", String.valueOf(goal));
    }

    private void event(String action, Creeper creeper, int path, Object... more) {
        List<Object> fields = new ArrayList<>(List.of("entity", owner.getUUID(), "action", action, "tick", owner.level().getGameTime(), "health", owner.getHealth(), "path", path));
        if (creeper != null) fields.addAll(List.of("distance", Math.round(owner.distanceTo(creeper) * 10) / 10.0, "swell", Math.round(creeper.getSwelling(0) * 100) / 100.0));
        fields.addAll(Arrays.asList(more));
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnostics.trace(), "creeper_guard", fields.toArray());
    }

    /** The move control turns him 90 degrees a tick and walks as he faces meanwhile: from facing the creeper that first step would go sideways, so he is turned to the heading at once. */
    static void faceTowards(CompanionEntity owner, Vec3 spot) {
        float yaw = (float) (Mth.atan2(spot.z - owner.getZ(), spot.x - owner.getX()) * 180 / Math.PI) - 90;
        if (Math.abs(Mth.wrapDegrees(yaw - owner.getYRot())) > TURN_AT) owner.setYRot(yaw);
    }

    /** Where a run along the heading ends, or null where the floor fails him, a wall stops him or a fluid lies in the way. */
    static Vec3 runTo(CompanionEntity owner, ServerLevel level, double dx, double dz, int samples, double step) {
        Vec3 from = owner.position(), at = from;
        for (int i = 1; i <= samples; i++) {
            double x = from.x + dx * i * step, z = from.z + dz * i * step;
            Vec3 next = null;
            for (double rise : RISES) {
                if (CompanionExpeditionLedge.stands(owner, level, x, at.y + rise, z)) { next = new Vec3(x, at.y + rise, z); break; }
            }
            if (next == null) return null;
            at = next;
        }
        return at;
    }

    /** A blow of his landed on a creeper, the melee goal's or his own: the retreats behind it were a fight, not a flight. */
    void landed() { retreats = 0; }

    /** A blow at the player's pace, shared with the melee goal; true when it hurt the creeper. */
    private boolean strike(Creeper creeper) {
        if (!owner.combat.swingReady() || !owner.isWithinMeleeAttackRange(creeper)) return false;
        owner.selectWeaponForCombat();
        owner.combat.swung();
        owner.swing(InteractionHand.MAIN_HAND);
        return owner.doHurtTarget(creeper);
    }

    /** Every creeper about that could hurt him, in sight or not: the way out of one must not lead to another. */
    private List<Creeper> threats(ServerLevel level) {
        return level.getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(THREAT_RANGE), creeper -> creeper.isAlive() && !creeper.isNoAi());
    }

    /**
     * The preferred place to go if the walk to it keeps clear of every creeper about; else the best place that is: farthest from the nearest
     * of them (the blast gap is enough), and one he can walk to. Null when there is none.
     */
    private Vec3 retreatSpot(List<BlockPos> way, List<Creeper> threats, Vec3 preferred) {
        if (preferred != null && clear(preferred, threats, GAP_SLACK)) return preferred;
        if (threats.isEmpty()) return null;
        List<Vec3> spots = ring();
        for (BlockPos cell : way) {
            Vec3 spot = Vec3.atBottomCenterOf(cell);
            if (owner.distanceToSqr(spot) <= PASSAGE_RANGE * PASSAGE_RANGE) spots.add(spot);
        }
        Vec3 centre = Vec3.ZERO;
        for (Creeper creeper : threats) centre = centre.add(creeper.position());
        centre = centre.scale(1.0 / threats.size());
        for (int i = 0; i < AWAY_TRIES; i++) {
            Vec3 spot = LandRandomPos.getPosAway(owner, FLEE_RANGE, FLEE_HEIGHT, centre);
            if (spot != null) spots.add(spot);
        }
        List<Vec3> ranked = spots.stream().filter(spot -> clear(spot, threats, GAP_SLACK))
                .sorted(Comparator.comparingDouble((Vec3 spot) -> -gap(spot, threats)).thenComparingDouble(owner.position()::distanceToSqr)).limit(PATH_TESTS).toList();
        for (Vec3 spot : ranked) if (reachable(spot)) return spot;
        return null;
    }

    /** The walk from where he stands to the spot passes no creeper nearer than he is to it now (or than the blast gap), give or take the slack. */
    private boolean clear(Vec3 spot, List<Creeper> threats, double slack) {
        Vec3 from = owner.position();
        for (Creeper creeper : threats) {
            double floor = Math.min(from.distanceTo(creeper.position()), SAFE_GAP);
            if (segmentDistance(from, spot, creeper.position()) < floor - slack) return false;
        }
        return true;
    }

    /** How far the spot is from the nearest creeper, counted up to the blast gap and no farther. */
    private static double gap(Vec3 spot, List<Creeper> threats) {
        double nearest = SAFE_GAP + 1;
        for (Creeper creeper : threats) nearest = Math.min(nearest, spot.distanceTo(creeper.position()));
        return nearest;
    }

    private static double segmentDistance(Vec3 from, Vec3 to, Vec3 point) {
        Vec3 line = to.subtract(from);
        double length = line.lengthSqr();
        double along = length == 0 ? 0 : Math.max(0, Math.min(1, point.subtract(from).dot(line) / length));
        return from.add(line.scale(along)).distanceTo(point);
    }

    /** Searched to its end now: the navigation's createPath answers null while a search outlasts this tick's share, as for most spots tried at once. */
    private boolean reachable(Vec3 spot) {
        Path path = ((CompanionNavigation) owner.getNavigation()).search(Set.of(BlockPos.containing(spot)), 0, REACH_RANGE, true);
        return path != null && path.canReach();
    }

    /** The head-height cells of the stand a few steps off on the creeper's side, far enough that its fuse is not lit; empty when that is shut already or out of reach. */
    private List<BlockPos> shutCells(CompanionMineSite mine, List<BlockPos> way, int at, Creeper creeper) {
        int side = sideOf(way, at, creeper), creeperAt = indexOf(way, creeper.blockPosition(), ROUTE_CREEPER_SQR);
        ServerLevel level = (ServerLevel) owner.level();
        for (int k = SHUT_STANDS; k >= 1; k--) {
            int index = at + side * k;
            if (index < 0 || index >= way.size() || creeperAt >= 0 && Math.abs(creeperAt - at) <= k) continue;
            List<BlockPos> open = new ArrayList<>();
            for (BlockPos cell : headRow(mine, way, index)) if (CompanionSeal.replaceable(level, cell)) open.add(cell);
            if (open.isEmpty()) return List.of();
            if (open.stream().allMatch(cell -> CompanionSeal.reachable(owner, cell))) return open;
        }
        return List.of();
    }

    /** The cell above the stand, and in a 2x2 tunnel the one beside it too: a creeper is nearly two blocks tall and cannot pass under them. */
    private static List<BlockPos> headRow(CompanionMineSite mine, List<BlockPos> way, int index) {
        BlockPos cell = way.get(index);
        List<CompanionMineSite.Tunnel> tunnels = mine.tunnels();
        if (!tunnels.isEmpty() && index < tunnels.get(tunnels.size() - 1).length()) {
            BlockPos beside = cell.relative(CompanionShaftPlan.right(tunnels.get(tunnels.size() - 1).direction()));
            return List.of(cell.above(), beside.above());
        }
        return List.of(cell.above());
    }

    /** +1 when the creeper is towards the entrance, -1 when it is towards the depth; one off the route is at the end of it that it is nearer to (a cave opens onto the depth end). */
    private int sideOf(List<BlockPos> way, int at, Creeper creeper) {
        int creeperAt = indexOf(way, creeper.blockPosition(), ROUTE_CREEPER_SQR);
        if (creeperAt >= 0 && creeperAt != at) return Integer.signum(creeperAt - at);
        double depth = creeper.distanceToSqr(Vec3.atCenterOf(way.get(0))), entrance = creeper.distanceToSqr(Vec3.atCenterOf(way.get(way.size() - 1)));
        return entrance < depth ? 1 : -1;
    }

    /** On his route, away from the creeper's side for a few cells; null where the route ends. */
    private Vec3 alongRoute(List<BlockPos> way, int at, Creeper creeper, int cells) {
        int side = sideOf(way, at, creeper);
        int target = Math.max(0, Math.min(way.size() - 1, at - side * cells));
        return target == at ? null : Vec3.atBottomCenterOf(way.get(target));
    }

    /** Off the route: to the nearest cell of his own passage that is farther from the creeper than he is, else simply away from it. */
    private Vec3 awayFrom(List<BlockPos> way, Creeper creeper) {
        double mine = creeper.distanceToSqr(owner);
        BlockPos best = null;
        double bestDistance = PASSAGE_RANGE * PASSAGE_RANGE;
        for (BlockPos cell : way) {
            double distance = owner.distanceToSqr(Vec3.atBottomCenterOf(cell));
            if (distance < bestDistance && creeper.distanceToSqr(Vec3.atBottomCenterOf(cell)) > mine) { best = cell; bestDistance = distance; }
        }
        if (best != null) return Vec3.atBottomCenterOf(best);
        return LandRandomPos.getPosAway(owner, FLEE_RANGE, FLEE_HEIGHT, creeper.position());
    }

    private static int indexOf(List<BlockPos> way, BlockPos here, int within) {
        int best = -1;
        double bestDistance = within + 1;
        for (int i = 0; i < way.size(); i++) {
            double distance = way.get(i).distSqr(here);
            if (distance <= within && distance < bestDistance) { best = i; bestDistance = distance; }
        }
        return best;
    }

    /** The goal: priority 0 beats every goal that moves him, and a pickup or an order at a higher number cannot take the turn from it. */
    static final class GuardGoal extends Goal {
        private final CompanionEntity c;

        GuardGoal(CompanionEntity c) {
            this.c = c;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() { return c.creeperGuard.canStart(); }
        @Override public boolean canContinueToUse() { return c.creeperGuard.canContinue(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { c.creeperGuard.start(); }
        @Override public void tick() { c.creeperGuard.tick(); }
        @Override public void stop() { c.creeperGuard.stop(); }
    }
}

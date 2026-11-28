package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A crowd after him is not met on its own ground, as a player does not: he lays two blocks from his bag under his feet and strikes down
 * from the column, where the arm of a zombie does not reach him and his blade reaches it. In the open he keeps out of reach between two
 * swings anyway; in a corner or a passage there is nowhere to step back to, and each of three zombies lands its blow (run ct78 20261031
 * mobs). Probes of 23.11, five zombies and a stone sword: a corner 12–15 blows taken, his own tunnel 14–18 and a death in four, a column
 * none. The column is the one of {@link CompanionPillar}: real blocks, a jump for each, taken down and back into the bag when the crowd is
 * dead or gone. Where it cannot stand (no blocks, a low roof, no floor) the goal does not start and he fights as before. A creeper is still
 * for the guard, at a higher priority; under a guard order he stays on the ground, where the player is, and so he does with a skeleton
 * shooting at him: a column is a stand for its arrows.
 * <p>
 * With more than one of them near him the block does not go under his feet in time: it is not laid into a cell a mob stands in, and one
 * blow throws back one mob (series ta 20261031 d3 mobs: zombies two by two, both on him, eight killed and dead at the ninth). He runs
 * clear of them first, a few cells over the floor to where a column stands, and raises it there. Probes of 24.11, two on him and three
 * pairs more coming: as before two deaths in four; nine cells run and the column up in some fifty ticks with no blow taken, four of four.
 * <p>
 * The column does not hold him: a mob that reaches him on it (from a ledge beside it, a spider up its side), an arrow or a blast that
 * throws him off, a column that does not go up (a roof, a mob in the cell), the guard that runs him from a fuse, all give the fight back
 * to the melee goal, and the column is left standing. Its blocks are taken back (the descent of {@link CompanionPillar} walks him to it)
 * the descent is asked for at once and waits while a fight is on ({@link CompanionPillar#waitsForFight}); he walks back to it, within the
 * budget of the descent. Farther than {@link #GIVE_UP_SQR} the blocks stay where they are, like any block a player leaves behind: he could
 * not come back for them.
 * <p>
 * Only mobs that can get at him are a crowd: those across a ravine, a river or a fence (the way to them does not lead there) are not, and a
 * stand that was waited out with none of them in reach is not raised again until they come near.
 * <p>
 * The crowd is a crowd of zombies of full height: the ones that strike with their arms and that the blade reaches from the column. A
 * baby zombie is out of the reach of a column of three (its head is under the eye of the one on it by more than a blade reaches), so
 * with one among the mobs about the column is of two; a silverfish or a cave spider is out of reach of both and there is none.
 */
final class CompanionHighGround extends Goal {
    private enum Phase { BREAK, RAISE, STAND }

    /**
     * How many of them after him are a crowd, and how many when he is already hurt; how high he stands: three blocks where the roof lets
     * him, as a player does (a zombie that jumps at the column does not reach him there), else two, where a jump may land a blow now and then.
     */
    private static final int CROWD = 3, HURT_CROWD = 2, TALL = 3, LOW = 2;
    /** At this health or below he is wounded and two of them are a crowd. */
    private static final float WARY_HEALTH = 13;
    /** A mob that struck him on the column from this much above its foot stands where it reaches him: a ledge beside it, a spider up its side. */
    private static final double REACHES_FROM = .9;
    /** How far from the middle of the top block he lets a blow move him before he steps back to it, and the pace of that step. */
    private static final double OFF_CENTRE_SQR = .2 * .2, CENTRE_SPEED = .3;
    /** How far from the middle of the column he leans to look down its side: to the edge of its top block. */
    private static final double LEAN = .5;
    /** How far he counts them, and how near one presses on him so that his blow must throw it back before a block goes under his feet. */
    private static final double LOOKOUT = 10, TOUCH_SQR = 1.5 * 1.5;
    /**
     * How near more than one of them leaves no time for a column where he stands: a zombie walks some three cells while a column of
     * three goes up, and the second of them is on him before it is.
     */
    private static final double CLOSE_SQR = 6 * 6;
    /** How far from a column left standing he is too far to come back for its blocks, how near a mob is for certain at him (no search of the way), and how near the crowd comes to raise a column again after one waited out. */
    private static final double GIVE_UP_SQR = 32 * 32, AT_HIM_SQR = 2.5 * 2.5, COME_NEAR_SQR = 5 * 5;
    /**
     * The least height of a mob about that a column of three, and of two, reaches from its top: the eye is 1.62 over the feet and the
     * blade reaches 3.0 from the eye to the nearest point of the mob, a mob pressed against the column being half a block off its middle.
     */
    private static final double TALL_REACHES = 1.7, LOW_REACHES = .7;
    /** How far a mob that shoots at him keeps him on the ground. */
    private static final double ARCHERS = 20;
    /** How long with nobody about before he comes down, and how long after a column that did not stand before the next one is tried. */
    private static final int CALM_TICKS = 40, RETRY_TICKS = 200;
    /** How often they are counted, and how long he stands with none of them in his reach (behind a fence, across a gap) before he leaves the column to fight on the ground. */
    private static final int COUNT_EVERY = 5, PATIENCE = 20 * 30, AFTER_PATIENCE = 20 * 60;

    /**
     * The run clear of them: how far a cell for the column is looked for, the least run that gains the time a column takes, how many
     * ways are searched, the pace, how long the run may take, how near a ledge is one a mob reaches him from, and how long after no
     * way was found the next is looked for: the searches are out of the share of the tick his fight finds its own steps with. And how
     * near one of them a step of the way is a step into its reach, and how near the line to it (the square of the cosine: sixty
     * degrees) a step toward it.
     */
    private static final int RUN = 9, LEAST_RUN_SQR = 4 * 4, PATH_TESTS = 3, RUN_TICKS = 80, LEDGE = 2, LOOK_EVERY = 40;
    private static final double RUN_SPEED = 1.3, ARRIVED_SQR = 2 * 2, REACH_SQR = 2 * 2, TOWARD_SQR = .5 * .5;
    private static final float RUN_RANGE = 16;

    private final CompanionEntity c;
    private Phase phase = Phase.RAISE;
    /** The way clear of them, when more than one is on him; null when the column goes up where he stands. */
    private Path run;
    private int ran, lookAt;
    /** The goal is over; the column comes down now (nobody about), or is left standing. */
    private boolean over, takeDown;
    private int calm, idle, retryAt, hurtAt, height;
    /** His own tick until which a crowd that was found out of reach is not looked at again, and whether the last column was waited out with nobody coming at him. */
    private int unreachedUntil;
    private boolean idledOut;

    CompanionHighGround(CompanionEntity c) {
        this.c = c;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() {
        if (!c.highGroundOn || c.tickCount % COUNT_EVERY != 0 || c.inventoryViewers != 0 || !c.onGround() || c.temporaryPillar.isRecovering() || !(c.level() instanceof ServerLevel level)) return false;
        if (c.temporaryPillar.leftStanding()) { settle(); return false; }
        if (c.tickCount < retryAt || c.temporaryPillar.isActive() || CompanionGuardWatch.watching(c) || shotAt(level)) return false;
        int after = 0, touching = 0, close = 0;
        List<Monster> foes = about(c, level);
        for (Monster foe : foes) {
            if (crowd(foe) && foe.getTarget() == c) after++;
            if (c.distanceToSqr(foe) < TOUCH_SQR) touching++;
            if (c.distanceToSqr(foe) < CLOSE_SQR) close++;
        }
        int need = c.getHealth() <= WARY_HEALTH ? HURT_CROWD : CROWD;
        if (after < need || shortest(foes) < LOW_REACHES || c.tickCount < unreachedUntil) return false;
        if (idledOut && !comeNear(foes)) return false;
        // The cheap answers first, and every no after the searches below is kept for a while: they are not repeated all through a fight.
        if (c.temporaryPillar.soilCarried() < LOW) return false;
        int fit = touching <= 1 ? heightFor(foes) : 0;
        boolean here = fit > 0;
        if (close <= 1 && !here) return false;
        // They are after him: can they get at him? Three across a ravine are not a crowd, and a column against them goes up and down for ever.
        if (reaching(foes, need) < need) return notNow();
        // One on him is thrown back by a blow and the block goes under his feet; with more of them near, the second is in the cell before
        // the column is up (series t 20261031 d1 mobs: 130 ticks and seven blows for it): he runs clear of them first. With nowhere to
        // run the column goes up where he stands, and with more than one on him already he fights there, as before.
        run = close > 1 && c.tickCount >= lookAt ? clearOf(level, foes) : null;
        if (close > 1 && run == null && c.tickCount >= lookAt) lookAt = c.tickCount + LOOK_EVERY;
        if (run != null) return true;
        if (!here) return notNow();
        height = fit;
        return true;
    }

    private boolean notNow() {
        unreachedUntil = c.tickCount + LOOK_EVERY;
        return false;
    }

    private boolean comeNear(List<Monster> foes) {
        for (Monster foe : foes) if (crowd(foe) && foe.getTarget() == c && c.distanceToSqr(foe) < COME_NEAR_SQR) return true;
        return false;
    }

    private int reaching(List<Monster> foes, int need) {
        int reaching = 0;
        for (Monster foe : foes) if (crowd(foe) && foe.getTarget() == c && reaches(c, foe) && ++reaching >= need) break;
        return reaching;
    }

    /** It can get at him: it stands at him, or his own search finds a way to it (his legs and a zombie's are the same on this). */
    private static boolean reaches(CompanionEntity c, Monster foe) {
        if (c.distanceToSqr(foe) < AT_HIM_SQR && Math.abs(foe.getY() - c.getY()) < 1.5) return true;
        Path way = ((CompanionNavigation) c.getNavigation()).search(Set.of(foe.blockPosition()), 1, RUN_RANGE, true);
        return way != null && way.canReach();
    }

    /** A zombie of full height that strikes with its arms: the crowd that sends him up a column (a drowned with a trident shoots, a baby is out of reach of a tall one). */
    private static boolean crowd(Monster foe) { return foe instanceof Zombie zombie && !zombie.isBaby() && !shoots(foe); }

    /** It strikes from afar: a bow, a crossbow, a trident (a drowned), or a shot of its own (a blaze, a witch, a ghast). The other zombies strike with their arms. */
    private static boolean shoots(Monster foe) {
        if (foe instanceof Zombie) return foe.getMainHandItem().is(Items.TRIDENT);
        return foe instanceof RangedAttackMob || foe instanceof CrossbowAttackMob;
    }

    private static double shortest(List<Monster> foes) {
        double least = Double.MAX_VALUE;
        for (Monster foe : foes) least = Math.min(least, foe.getBbHeight());
        return least;
    }

    /** How high the column stands where the mobs about are in reach of the blade from its top, and the roof and the bag allow; 0 where none does. */
    private int heightFor(List<Monster> foes) {
        double least = shortest(foes);
        if (least < LOW_REACHES) return 0;
        if (least >= TALL_REACHES && c.temporaryPillar.canStandUp(TALL)) return TALL;
        return c.temporaryPillar.canStandUp(LOW) ? LOW : 0;
    }

    /**
     * The way to a cell clear of them where a column stands: a cell of the floor about him, a step up or down at most, farther from the
     * middle of the crowd than he is, those on his side of it before those beyond it, the farthest from the nearest of them first, and
     * none with a ledge beside it. Null with no such cell in reach by a way that does not lead through them.
     */
    private Path clearOf(ServerLevel level, List<Monster> foes) {
        Vec3 crowd = Vec3.ZERO;
        for (Monster foe : foes) crowd = crowd.add(foe.position().scale(1.0 / foes.size()));
        double now = c.distanceToSqr(crowd);
        BlockPos feet = c.blockPosition();
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -RUN; dx <= RUN; dx++) for (int dz = -RUN; dz <= RUN; dz++) for (int dy = -1; dy <= 1; dy++) {
            BlockPos cell = feet.offset(dx, dy, dz);
            if (dx * dx + dz * dz >= LEAST_RUN_SQR && crowd.distanceToSqr(cell.getX() + .5, cell.getY(), cell.getZ() + .5) > now) cells.add(cell);
        }
        // The cells beyond the crowd are many where the open ground is there, their ways lead through it, and they would take all of the few searches.
        Vec3 middle = crowd;
        cells.sort(Comparator.comparing((BlockPos cell) -> (cell.getX() + .5 - c.getX()) * (middle.x - c.getX()) + (cell.getZ() + .5 - c.getZ()) * (middle.z - c.getZ()) > 0)
                .thenComparingDouble(cell -> -gap(cell, foes)));
        int searched = 0;
        for (BlockPos cell : cells) {
            if (!c.temporaryPillar.standsAt(cell, LOW) || overlooked(level, cell)) continue;
            Path path = ((CompanionNavigation) c.getNavigation()).search(Set.of(cell), 0, RUN_RANGE, true);
            if (path != null && path.canReach() && !through(path, foes)) return path;
            if (++searched == PATH_TESTS) break;
        }
        return null;
    }

    /**
     * The way leads through them: a step of it is in the reach of one of them and toward it, within sixty degrees of the line from him
     * to it. The cell it ends in is clear of them and the way to it is not (series te 20261031 n1 mobs: hurt, two zombies on him on a
     * ledge, the cells for a column beyond them; he walked into them for forty ticks, three blows taken and none dealt, and died). A
     * step beside one of them, or away from it, is none into it, however near it presses on him.
     */
    private boolean through(Path path, List<Monster> foes) {
        for (int step = 1; step < path.getNodeCount(); step++) {
            Node node = path.getNode(step);
            double dx = node.x + .5 - c.getX(), dz = node.z + .5 - c.getZ();
            for (Monster foe : foes) {
                double fx = foe.getX() - c.getX(), fz = foe.getZ() - c.getZ(), toward = dx * fx + dz * fz;
                if (toward > 0 && toward * toward > TOWARD_SQR * (dx * dx + dz * dz) * (fx * fx + fz * fz) && foe.distanceToSqr(node.x + .5, node.y, node.z + .5) < REACH_SQR) return true;
            }
        }
        return false;
    }

    /**
     * A mob has somewhere to stand from where it reaches him on a column in this cell: a ledge or a step within {@link #LEDGE} cells,
     * above the floor of the column and no higher than his head on it (the corner of a hall by his own stairs: a zombie two steps up
     * them struck him off a column of three).
     */
    private static boolean overlooked(ServerLevel level, BlockPos cell) {
        for (int dx = -LEDGE; dx <= LEDGE; dx++) for (int dz = -LEDGE; dz <= LEDGE; dz++) for (int dy = 1; dy <= TALL + 1; dy++) {
            BlockPos at = cell.offset(dx, dy, dz);
            if (!open(level, at.below()) && open(level, at) && open(level, at.above())) return true;
        }
        return false;
    }

    /** A cell he can pass: no collision shape and no fluid. */
    private static boolean open(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static double gap(BlockPos cell, List<Monster> foes) {
        double gap = Double.MAX_VALUE;
        for (Monster foe : foes) gap = Math.min(gap, foe.distanceToSqr(cell.getX() + .5, cell.getY(), cell.getZ() + .5));
        return gap;
    }

    /**
     * What becomes of the column left standing: taken down, the descent asked for at once (it waits for a fight to be over, and walks him
     * back to it: a fight that lasts, or one that took him a few cells off, ends with him at the column and its blocks in his bag). Too far
     * to come back, they are left where they are.
     */
    private void settle() {
        BlockPos foot = c.temporaryPillar.foot();
        if (foot == null || c.distanceToSqr(foot.getX() + .5, foot.getY(), foot.getZ() + .5) > GIVE_UP_SQR) c.temporaryPillar.abandon();
        else c.temporaryPillar.requestCleanupAfterFight();
    }

    /**
     * The mobs about that strike with a blade or a fist: awake, in sight, fair game, no creeper (the guard has it) and none with a bow. Unlike
     * {@link CompanionFootwork#foes} every such mob in {@link #LOOKOUT} counts, however far from his reach, so that a column is not taken
     * down into a crowd at its foot (series t 20261031 d1 mobs: «nobody about» with the crowd at its foot).
     */
    private static List<Monster> about(CompanionEntity c, ServerLevel level) {
        return level.getEntitiesOfClass(Monster.class, c.getBoundingBox().inflate(LOOKOUT), foe -> foe.isAlive() && !foe.isNoAi() && !(foe instanceof Creeper)
                && !shoots(foe) && c.combat.fairTarget(foe, c.combat.provokes(foe)) && sees(c, foe));
    }

    /** 0 with no mob about, 1 with some about, 2 with one after him that sees him: what a descent of the column waits for ({@link CompanionPillar#waitsForFight}). */
    static int nearby(CompanionEntity c) {
        if (!(c.level() instanceof ServerLevel level)) return 0;
        if (sought(c, level)) return 2;
        return about(c, level).isEmpty() ? 0 : 1;
    }

    /**
     * In his sight; on his column also over its edge. From the middle of its top block a mob pressed against the side of the column
     * is under that block's edge, and his eyes do not meet its eyes (in game 24.11: the others dead, he saw nobody about, took the
     * column down onto it and went from 12 health to 3). He leans to the edge and looks down, as a player does.
     */
    private static boolean sees(CompanionEntity c, Monster foe) {
        if (c.hasLineOfSight(foe)) return true;
        BlockPos foot = c.temporaryPillar.foot();
        if (foot == null || !c.temporaryPillar.onTop()) return false;
        Vec3 its = foe.getEyePosition(), lean = new Vec3(its.x - foot.getX() - .5, 0, its.z - foot.getZ() - .5);
        if (lean.lengthSqr() > LEAN * LEAN) lean = lean.normalize().scale(LEAN);
        Vec3 eyes = new Vec3(foot.getX() + .5 + lean.x, c.getEyeY(), foot.getZ() + .5 + lean.z);
        return c.level().clip(new ClipContext(eyes, its, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c)).getType() == HitResult.Type.MISS;
    }

    /** A monster that sees him and can get at him is after him. */
    private static boolean sought(CompanionEntity c, ServerLevel level) {
        List<Monster> after = new ArrayList<>(level.getEntitiesOfClass(Monster.class, c.getBoundingBox().inflate(LOOKOUT), foe -> foe.isAlive() && !foe.isNoAi() && foe.getTarget() == c && sees(c, foe)));
        after.sort(Comparator.comparingDouble(c::distanceToSqr));
        for (Monster foe : after) if (reaches(c, foe)) return true;
        return false;
    }

    /** A mob that shoots is after him and sees him: on a column he would stand still for its arrows, on the ground he closes in on it or raises the shield. */
    private boolean shotAt(ServerLevel level) {
        return !level.getEntitiesOfClass(Monster.class, c.getBoundingBox().inflate(ARCHERS), foe -> foe.isAlive() && !foe.isNoAi() && foe.getTarget() == c
                && shoots(foe) && c.hasLineOfSight(foe)).isEmpty();
    }

    /** An order of the player (stop, follow) takes the column down at once: that descent is the turn of the recovery goal. */
    @Override public boolean canContinueToUse() { return !over && !c.temporaryPillar.isRecovering(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void start() {
        over = takeDown = idledOut = false;
        calm = idle = ran = 0;
        phase = run != null ? Phase.BREAK : Phase.RAISE;
        if (run != null) CompanionMiningNavigation.walk(c, run, RUN_SPEED);
        else c.getNavigation().stop();
        if (c.hasActiveWorkOrder()) c.workStatus(run != null ? "Их много, и они уже вплотную: отбегаю и встаю на столбик из своих блоков."
                : "Их много: встаю на столбик из своих блоков, сверху меня не достанут.");
    }

    @Override public void tick() {
        if (!(c.level() instanceof ServerLevel level)) return;
        List<Monster> foes = about(c, level);
        Monster nearest = null;
        for (Monster foe : foes) if (nearest == null || c.distanceToSqr(foe) < c.distanceToSqr(nearest)) nearest = foe;
        switch (phase) {
            case BREAK -> breakAway(foes);
            case RAISE -> raise(nearest);
            case STAND -> stand(nearest);
        }
    }

    /**
     * The run is over where its way ends, and the column goes up there. Cut short (an order of the player stops his walk and with it
     * forgets the way; a walk gone to its end keeps it), longer than {@link #RUN_TICKS}, with more than one of them on him all the
     * same, or with no room for a column after all, it gains none: the fight is on the ground, as before.
     */
    private void breakAway(List<Monster> foes) {
        if (++ran < RUN_TICKS && !(c.getNavigation().isDone() && c.onGround())) return;
        Path walked = c.getNavigation().getPath();
        boolean arrived = walked != null && walked.isDone();
        c.getNavigation().stop();
        Node end = run.getEndNode();
        int touching = 0;
        for (Monster foe : foes) if (c.distanceToSqr(foe) < TOUCH_SQR) touching++;
        height = heightFor(foes);
        if (!arrived || ran >= RUN_TICKS || touching > 1 || end == null || c.distanceToSqr(end.x + .5, end.y, end.z + .5) > ARRIVED_SQR
                || height == 0) {
            retryAt = c.tickCount + RETRY_TICKS;
            over = true;
            return;
        }
        phase = Phase.RAISE;
    }

    private void raise(Monster nearest) {
        // A mob pressed against him stands in the cell the block must take: his blow throws it back, and the jump follows.
        if (nearest != null && c.distanceToSqr(nearest) < TOUCH_SQR && c.combat.swingReady() && c.onGround()) {
            c.selectWeaponForCombat();
            c.getLookControl().setLookAt(nearest);
            c.combat.strike(nearest);
        }
        CompanionPillar.Outcome outcome = c.temporaryPillar.tickUp(height);
        if (outcome == CompanionPillar.Outcome.FAILED) { retryAt = c.tickCount + RETRY_TICKS; over = true; }
        if (outcome != CompanionPillar.Outcome.READY) return;
        phase = Phase.STAND;
        hurtAt = c.getLastHurtByMobTimestamp();
    }

    private void stand(Monster nearest) {
        c.getNavigation().stop();
        BlockPos foot = c.temporaryPillar.foot();
        // Thrown off it (a blast), shot on it, or struck on it by a mob that stands where it reaches him: the fight is the turn of the melee
        // goal again. A zombie that jumps at the column lands a blow now and then (in the air, from the floor): far fewer than on the ground.
        boolean reached = false;
        if (c.getLastHurtByMobTimestamp() != hurtAt) {
            hurtAt = c.getLastHurtByMobTimestamp();
            reached = foot != null && c.getLastHurtByMob() instanceof Monster by && (by instanceof RangedAttackMob || by.onGround() && by.getY() >= foot.getY() + REACHES_FROM);
        }
        if (reached || c.onGround() && !c.temporaryPillar.onTop()) { retryAt = c.tickCount + RETRY_TICKS; over = true; return; }
        if (c.onGround() && foot != null && c.distanceToSqr(foot.getX() + .5, c.getY(), foot.getZ() + .5) > OFF_CENTRE_SQR) c.getMoveControl().setWantedPosition(foot.getX() + .5, c.getY(), foot.getZ() + .5, CENTRE_SPEED);
        if (nearest == null) { over = takeDown = ++calm > CALM_TICKS; return; }
        calm = 0;
        c.selectWeaponForCombat();
        c.setTarget(nearest);
        c.getLookControl().setLookAt(nearest);
        // Seen over the edge of the column or not, it is one of those about him: the blow does not ask his eyes from the middle of the column again.
        c.combat.strike(nearest, true);
        if (c.isWithinMeleeAttackRange(nearest)) idle = 0;
        else if (++idle > PATIENCE) { retryAt = c.tickCount + AFTER_PATIENCE; over = idledOut = true; }
    }

    @Override public void stop() {
        c.getNavigation().stop();
        CompanionPillar pillar = c.temporaryPillar;
        if (!pillar.isActive() || pillar.isRecovering()) return;
        // With no support laid there is nothing to come back for: the cleanup only forgets the column.
        if (takeDown || !pillar.hasSupports()) { pillar.requestCleanup(); return; }
        if (!pillar.leftStanding()) pillar.leaveStanding();
    }
}

package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A short, inventory-backed dirt pillar. Movement and falling use normal entity physics. */
public final class CompanionPillar {
    public static final int MAX_SUPPORTS = 6;
    private static final double REACH_SQR = 16.0D;
    private static final int MAX_ATTEMPT_TICKS = 200;
    private static final int STALLED_TICKS = 80;
    private static final int STEP_INTERVAL = 10;
    /** A stand out of reach ({@link #tickUp}): how far from his cell it may be, how far the mobs it keeps away from are counted, the samples of the walk to it. */
    private static final int STAND_REACH = 2, STAND_LOOKOUT = 8, STRAIGHT_SAMPLES = 8, STAND_JUMPS = 4, STAND_INTERVAL = 2;
    /** How often the way back to a column out of his reach is set while it is taken down, and how far a stand left standing is given up from. */
    private static final int WALK_BACK_EVERY = 10;
    private static final double LEFT_FAR_SQR = 8 * 8, STAND_OFF_CENTRE_SQR = .25 * .25;

    public enum Outcome { WORKING, READY, FAILED }

    private record Support(BlockPos pos, BlockState state) { }
    private record Column(BlockPos feet, Path path, int steps, double score) { }

    private final CompanionEntity owner;
    private final CompanionBlockBreaking breaking;
    private final List<Support> supports = new ArrayList<>();
    private BlockPos base;
    private BlockPos target;
    private BlockPos jumpCell;
    private BlockPos branch;
    private Path approach;
    private Vec3 lastProgress;
    private int attemptTicks;
    private int stalledTicks;
    private int jumpTicks;
    private int failedJumps;
    private int cooldown;
    private int cleanupTicks;
    private boolean recovering;
    /** The column is a stand of {@link #tickUp}, not a climb toward a block. */
    private boolean stand;
    /** The stand is left standing with him off it ({@link #leaveStanding}). */
    private boolean leftStanding;
    private String failureReason = "";
    /** The hand a gather keeps while its column comes down: the tool for a support goes back into it after each break. */
    private ItemStack keptHand;
    /** Whether the tool of the gather that asked for this descent stays in his hand: also a failed climb leaves it there. */
    private boolean keepTool;

    public CompanionPillar(CompanionEntity owner) {
        this.owner = owner;
        breaking = new CompanionBlockBreaking(owner);
    }

    public boolean isActive() { return base != null || jumpCell != null || !supports.isEmpty(); }
    public boolean hasSupports() { return !supports.isEmpty(); }
    public boolean isRecovering() { return recovering; }
    public int supportCount() { return supports.size(); }
    public String failureReason() { return failureReason; }

    /** Called every server tick while harvesting. READY means that a normal log swing is in range. */
    public Outcome tickToward(BlockPos log) { return tickToward(log, true); }

    /**
     * The same climb toward a block he breaks — a log of the trunk, or a leaf of a crown that stands above every stand cell
     * (a tree grown with bone meal, the owner 17.11: «он должен понимать что до него можно добраться если прокопать что то
     * или подстроиться»). A crown of leaves has no branch to switch to, so {@code wood} only decides what a swing over the
     * column means.
     */
    public Outcome tickToward(BlockPos block, boolean wood) {
        if (!(owner.level() instanceof ServerLevel level) || recovering) return Outcome.FAILED;
        if (!level.hasChunkAt(block) || (wood ? !level.getBlockState(block).is(BlockTags.LOGS) : level.getBlockState(block).isAir())) return Outcome.FAILED;
        if (owner.distanceToSqr(block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D) <= REACH_SQR) {
            if (jumpCell == null && owner.onGround()) return Outcome.READY;
        }
        if (base == null) {
            int busy = owner.work.route.busyAnswers();
            Column column = planColumn(level, block);
            // A column it could not search to for want of this tick's cells is not one without a way: planned again next tick.
            if (column == null) return owner.work.route.busyAnswers() != busy ? Outcome.WORKING : Outcome.FAILED;
            base = column.feet();
            approach = column.path();
            resetWatchdog(block);
        } else if (!block.equals(target)) {
            resetWatchdog(block);
        }

        int needed = requiredSteps(block, base);
        if (needed < 0) return fail("С этой опоры не достану до соседней ветки");
        if (needed > MAX_SUPPORTS) return fail("Верхушка слишком высокая для короткой опоры");
        if (soilCount() < Math.max(0, needed - supports.size())) return fail("Для подъёма не хватает земли в сумке");
        if (++attemptTicks > MAX_ATTEMPT_TICKS || !level.hasChunkAt(base)) return fail("Не получилось подняться, поищу дерево пониже");
        if (lastProgress == null || owner.position().distanceToSqr(lastProgress) >= 0.0025D) {
            lastProgress = owner.position();
            stalledTicks = 0;
        } else if (++stalledTicks > STALLED_TICKS) {
            return fail("У дерева не получается пройти, поищу другой подход");
        }
        if (cooldown > 0) cooldown--;

        if (jumpCell != null) return tickJump(level);

        int surfaceY = base.getY() + supports.size();
        double horizontal = horizontalDistanceSqr(base);
        if (horizontal > 0.0144D || Math.abs(owner.getY() - surfaceY) > .2D) {
            if (!supports.isEmpty() && owner.getY() < surfaceY - 0.2D) return fail("Соскользнул с опоры, сначала уберу землю");
            // A nearby column can still be one level above/below us after digging its soil.
            // Follow the reachable route across that change instead of steering into the lip.
            if (supports.isEmpty() && (horizontal > 2.25D || Math.abs(owner.getY() - surfaceY) > .2D)) {
                if (approach == null || owner.getNavigation().isDone() && attemptTicks % 20 == 0) {
                    int busy = owner.work.route.busyAnswers();
                    approach = owner.work.route.toCells(Set.of(base));
                    if (approach == null && owner.work.route.busyAnswers() != busy) return Outcome.WORKING;
                }
                if (approach == null || !approach.canReach()) return fail("Под опорой нет безопасного места");
                CompanionMiningNavigation.walk(owner, approach);
            } else {
                owner.getNavigation().stop();
                owner.getMoveControl().setWantedPosition(base.getX() + 0.5D, surfaceY, base.getZ() + 0.5D, 0.6D);
            }
            return Outcome.WORKING;
        }
        owner.getNavigation().stop();
        if (!owner.onGround()) return Outcome.WORKING;
        if (Math.abs(owner.getY() - surfaceY) > 0.2D) return fail("Здесь неудобно подниматься, поищу другое место");
        if (owner.distanceToSqr(block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D) <= REACH_SQR) return Outcome.READY;
        if (supports.size() >= needed || supports.size() >= MAX_SUPPORTS || cooldown > 0) return Outcome.WORKING;

        BlockPos placement = base.above(supports.size());
        // A branch of the same crown hanging over the column is not an obstacle: it is the next log to chop from here.
        BlockPos overhead = wood ? reachableLogAbove(level, placement) : null;
        if (overhead != null) { branch = overhead; return Outcome.WORKING; }
        if (!clearJumpSpace(level, placement)) return fail("Над опорой не хватает свободного места");
        // Clearing one leaf at a time is visible; wait until the next tick before jumping.
        if (!jumpSpaceClear(level, placement)) return Outcome.WORKING;
        if (!prepareSoil()) return fail("Не могу взять землю в руку, проверь место в моей сумке");
        if (!safeFoundation(level, placement.below())) return fail("Под опорой нет безопасного места");
        Vec3 motion = owner.getDeltaMovement();
        owner.setDeltaMovement(motion.x * 0.15D, motion.y, motion.z * 0.15D);
        jumpCell = placement.immutable();
        jumpTicks = 0;
        owner.getJumpControl().jump();
        return Outcome.WORKING;
    }

    /**
     * A column raised where he stands, this many supports high, with nothing to reach from it: a stand out of the reach of a crowd
     * ({@link CompanionHighGround}). READY once he stands on it; a failure with supports laid takes them down as any other.
     */
    public Outcome tickUp(int height) {
        if (!(owner.level() instanceof ServerLevel level) || recovering) return Outcome.FAILED;
        if (base == null) {
            BlockPos feet = owner.onGround() && soilCount() >= height ? standFor(level, height) : null;
            if (feet == null) return Outcome.FAILED;
            base = feet;
            stand = true;
            resetWatchdog(base);
        }
        if (jumpCell != null) return tickJump(level);
        if (supports.size() >= height) return owner.onGround() ? Outcome.READY : Outcome.WORKING;
        if (++attemptTicks > MAX_ATTEMPT_TICKS) return fail("Не получилось подняться на опору");
        if (cooldown > 0) cooldown--;
        int surfaceY = base.getY() + supports.size();
        if (!supports.isEmpty() && owner.getY() < surfaceY - 0.2D) return fail("Соскользнул с опоры, сначала уберу её");
        owner.getNavigation().stop();
        // Not the middle of the cell to a hair, as for a climb at a tree: blows move him, and the block holds him from anywhere over it.
        if (horizontalDistanceSqr(base) > STAND_OFF_CENTRE_SQR) {
            owner.getMoveControl().setWantedPosition(base.getX() + 0.5D, surfaceY, base.getZ() + 0.5D, 0.6D);
            return Outcome.WORKING;
        }
        if (!owner.onGround() || cooldown > 0) return Outcome.WORKING;
        BlockPos placement = base.above(supports.size());
        if (!roomFor(level, placement, height - supports.size())) return fail("Над опорой не хватает свободного места");
        // Grass, a fern or thin snow in the cell comes out first, as for a plug of the stairs: the support takes a cell of air.
        if (!level.getBlockState(placement).isAir()) { level.destroyBlock(placement, true, owner); return Outcome.WORKING; }
        if (!prepareSoil()) return fail("Не могу взять блок для опоры в руку");
        Vec3 motion = owner.getDeltaMovement();
        owner.setDeltaMovement(motion.x * 0.15D, motion.y, motion.z * 0.15D);
        jumpCell = placement.immutable();
        jumpTicks = 0;
        owner.getJumpControl().jump();
        return Outcome.WORKING;
    }

    /**
     * Where a column of this height can stand: his own cell, else the cell within {@link #STAND_REACH} on his level that he walks straight
     * to and that is the farthest from the mobs about (the roof of a cave is seldom even: run ct78, four cells of air over the corner he
     * was pinned in and five two cells away). Null when there is none.
     */
    private BlockPos standFor(ServerLevel level, int height) {
        BlockPos feet = owner.blockPosition();
        if (stands(level, feet, height)) return feet.immutable();
        List<net.minecraft.world.entity.monster.Monster> mobs = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, owner.getBoundingBox().inflate(STAND_LOOKOUT), LivingEntity::isAlive);
        BlockPos best = null;
        double bestGap = -1;
        for (int dx = -STAND_REACH; dx <= STAND_REACH; dx++) for (int dz = -STAND_REACH; dz <= STAND_REACH; dz++) {
            BlockPos cell = feet.offset(dx, 0, dz);
            if (dx == 0 && dz == 0 || !stands(level, cell, height) || !walksStraightTo(level, cell)) continue;
            double gap = Double.MAX_VALUE;
            for (var mob : mobs) gap = Math.min(gap, mob.distanceToSqr(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D));
            if (gap > bestGap) { best = cell.immutable(); bestGap = gap; }
        }
        return best;
    }

    private boolean stands(ServerLevel level, BlockPos feet, int height) {
        return safeFoundation(level, feet.below()) && roomFor(level, feet, height) && flooredAround(level, feet)
                && level.getEntities(owner, new AABB(feet).expandTowards(0, height, 0), CompanionPillar::blocksSupport).isEmpty();
    }

    /**
     * A blow may throw him off the column: every cell around its foot is a wall or has a floor no more than a block lower, so that he
     * lands beside it and not down a pit (series p 20261031 d3 mobs: a column at the edge of a cavern, thrown off, three falls, dead),
     * and none of them holds lava or water, nor does the floor under it.
     */
    private boolean flooredAround(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos beside = feet.offset(dx, 0, dz);
            if (dx == 0 && dz == 0) continue;
            if (!level.getFluidState(beside).isEmpty() || !level.getFluidState(beside.below()).isEmpty()) return false;
            if (CompanionShaftPlan.open(level, beside) && !safeFoundation(level, beside.below()) && !safeFoundation(level, beside.below(2))) return false;
        }
        return true;
    }

    /**
     * Room for the supports and for him on top of them: the cell at the feet takes a block (air or a plant), the cells above it up to his
     * head on the column are open. A roof right over that head is no obstacle: the jump for the last support ends under it.
     */
    private boolean roomFor(ServerLevel level, BlockPos feet, int height) {
        for (int dy = 0; dy <= height + 1; dy++) {
            BlockPos pos = feet.above(dy);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos).isEmpty()) return false;
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || dy < height && !state.isAir() && !state.canBeReplaced()) return false;
        }
        return true;
    }

    /** Nothing in the way of his body on the straight line to the middle of the cell, and a floor under every step of it. */
    private boolean walksStraightTo(ServerLevel level, BlockPos cell) {
        double dx = cell.getX() + 0.5D - owner.getX(), dz = cell.getZ() + 0.5D - owner.getZ();
        for (int step = 1; step <= STRAIGHT_SAMPLES; step++) {
            double part = step / (double) STRAIGHT_SAMPLES;
            AABB body = owner.getBoundingBox().move(dx * part, 0, dz * part);
            if (!level.noCollision(owner, body) || level.containsAnyLiquid(body)) return false;
            if (!safeFoundation(level, BlockPos.containing(owner.getX() + dx * part, owner.getY() - 0.5D, owner.getZ() + dz * part))) return false;
        }
        return true;
    }

    /** A column of this height can be raised now: nothing of his stands, the bag has the blocks, and there is a cell for it ({@link #tickUp}). */
    public boolean canStandUp(int height) {
        return owner.level() instanceof ServerLevel level && !isActive() && !recovering && owner.onGround() && soilCount() >= height && standFor(level, height) != null;
    }

    /** The cell his column stands in, or null with no column. */
    public BlockPos foot() { return base; }

    /** The fight took him off his stand: it stays in the world and in the record until he comes back for it, takes it down by an order, or gives it up. */
    public void leaveStanding() {
        if (!stand || supports.isEmpty() || recovering) return;
        leftStanding = true;
        jumpCell = null;
    }

    /** His stand is left standing and he is off it: no climb is under way, so the pickup of a drop does not wait for it. */
    public boolean leftStanding() { return leftStanding; }

    /** The supports stay in the world as blocks anybody may take, and the column is forgotten: he could not come back for them. Nothing is refunded. */
    public void abandon() {
        supports.clear();
        failureReason = "Опоры оставил на месте: вернуться за ними не вышло";
        clearColumn();
    }

    /** He stands on the top support of his column. */
    public boolean onTop() {
        return !supports.isEmpty() && owner.onGround() && Math.abs(owner.getY() - (supports.getLast().pos().getY() + 1.0D)) < 0.2D && overlapsColumn(owner.getBoundingBox(), supports.getLast().pos());
    }

    private Outcome tickJump(ServerLevel level) {
        owner.getNavigation().stop();
        owner.getMoveControl().setWantedPosition(base.getX() + 0.5D, owner.getY(), base.getZ() + 0.5D, 0.35D);
        jumpTicks++;
        AABB cell = new AABB(jumpCell);
        if (owner.getBoundingBox().minY >= jumpCell.getY() + 1.001D
                && !owner.getBoundingBox().intersects(cell)) {
            // A stand in a fight: a mob pressed against him is in the cell. Not the end of the column: his next blow throws it back, and the jump is tried again.
            if (stand && !level.getEntities(owner, cell, CompanionPillar::blocksSupport).isEmpty()) {
                jumpCell = null;
                cooldown = STEP_INTERVAL;
                return ++failedJumps >= STAND_JUMPS ? fail("Место для опоры занято") : Outcome.WORKING;
            }
            if (!prepareSoil() || !level.getBlockState(jumpCell).isAir()
                    || !level.getFluidState(jumpCell).isEmpty() || !level.getWorldBorder().isWithinBounds(jumpCell)
                    || !level.getEntities(owner, cell, CompanionPillar::blocksSupport).isEmpty()) return fail("Место для опоры занято, попробую другое дерево");
            ItemStack hand = owner.getMainHandItem();
            BlockState state = ((BlockItem) hand.getItem()).getBlock().defaultBlockState();
            if (!state.canSurvive(level, jumpCell) || !level.setBlockAndUpdate(jumpCell, state)) return fail("Не удалось поставить опору");
            owner.rememberPlaced(jumpCell);
            supports.add(new Support(jumpCell.immutable(), state));
            owner.animateAction(1, jumpCell, hand);
            hand.shrink(1);
            if (hand.isEmpty()) owner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            owner.setChanged();
            owner.swing(InteractionHand.MAIN_HAND);
            var sound = state.getSoundType(level, jumpCell, owner);
            level.playSound(null, jumpCell, sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) * 0.5F, sound.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, jumpCell, GameEvent.Context.of(owner, state));
            jumpCell = null;
            // A stand goes up at the pace of a player with mobs on the way; a climb at a tree is unhurried and seen.
            cooldown = stand ? STAND_INTERVAL : STEP_INTERVAL;
            failedJumps = 0;
            stalledTicks = 0;
            lastProgress = owner.position();
            return Outcome.WORKING;
        }
        if (jumpTicks > 28 && owner.onGround()) {
            jumpCell = null;
            cooldown = STEP_INTERVAL;
            if (++failedJumps >= 2) return fail("Здесь не получается подпрыгнуть, поищу другое место");
        }
        return Outcome.WORKING;
    }

    /** Cancel wood swings immediately; keep the pillar until normal descent has finished. */
    public void requestCleanup() { requestCleanup(false); }

    /**
     * The same, with {@code keepTool} for a gather whose own tool stays in his hand: the shears of a leaf order are not put
     * away while its column comes down — the tool for a support goes back into that hand after each break, and the hand the
     * gather replaced waits for the end of the gather (the owner, 19.11).
     */
    public void requestCleanup(boolean keepTool) {
        breaking.reset();
        this.keepTool = keepTool && isActive();
        if (this.keepTool) {
            keptHand = owner.getMainHandItem().copy();
        } else {
            // A tool taken for the last swing goes back with the hand it replaced: the pillar may stand for a while before it is taken down.
            keptHand = null;
            owner.putToolAway();
        }
        if (!recovering) cleanupTicks = 0;
        recovering = isActive();
        jumpCell = null;
        approach = null;
        owner.getNavigation().stop();
        if (!recovering || supports.isEmpty() && owner.onGround()) clearColumn();
    }

    /** Called every tick by the recovery goal, including while falling between two supports. */
    public boolean tickCleanup() {
        if (!(owner.level() instanceof ServerLevel level)) return true;
        if (!recovering) requestCleanup();
        if (!recovering) return true;
        cleanupTicks++;
        if (cooldown > 0) cooldown--;

        // The budget is per support (reset at each one taken): six stones with a wooden pickaxe take longer than one budget.
        if (cleanupTicks > MAX_ATTEMPT_TICKS && owner.onGround()) {
            // Another player may now be using this soil. Leave it without issuing any refund.
            // Relinquishing only this bounded list also prevents an endless recovery-goal loop.
            supports.clear();
            failureReason = "Опоры заняты или изменены, оставил их на месте";
            clearColumn();
            return true;
        }

        // A player may have broken/replaced an owned block. Never turn that into a free item.
        while (!supports.isEmpty()) {
            Support highest = supports.getLast();
            if (!level.hasChunkAt(highest.pos())) return false;
            if (level.getBlockState(highest.pos()) == highest.state()) break;
            supports.removeLast();
        }
        if (supports.isEmpty()) {
            if (!owner.onGround()) return false;
            clearColumn();
            return true;
        }

        Support highest = supports.getLast();
        BlockPos pos = highest.pos();
        if (!owner.onGround()) return false;
        if (cooldown > 0) return false;
        boolean standingOnTop = Math.abs(owner.getBoundingBox().minY - (pos.getY() + 1.0D)) < 0.15D
                && overlapsColumn(owner.getBoundingBox(), pos);
        if (standingOnTop) {
            // Do not remove the last landing if somebody has dug out the middle/foundation.
            int landing = findLandingY(level, pos.below());
            if (landing == Integer.MIN_VALUE || owner.getY() - landing > 2.1D) return false;
        } else {
            double distance = owner.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distance > REACH_SQR) {
                // A stand the fight took him far away from: its blocks are not worth the walk back through what he fought.
                if (leftStanding && distance > LEFT_FAR_SQR) { abandon(); return true; }
                // Out of his reach, he walks back to the cell beside it instead of standing out the budget: a blow that took him a few
                // steps off in the middle of the descent, or a fight on the ground beside a stand.
                if (base != null && cleanupTicks % WALK_BACK_EVERY == 1) {
                    Path back = owner.work.route.toCells(Set.of(base.north(), base.south(), base.east(), base.west()));
                    if (back != null) CompanionMiningNavigation.walk(owner, back);
                }
                return false;
            }
        }
        owner.getNavigation().stop();
        if (anotherEntityDependsOn(level, pos)) return false;
        owner.getLookControl().setLookAt(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        owner.takeToolForBlock(highest.state());
        if (!canDrop(highest.state())) {
            // The pickaxe is gone (broken, or taken): by hand the block would vanish without a drop. It stays where it is.
            owner.putToolAway();
            supports.clear();
            failureReason = "Опоры оставил на месте: нечем их забрать";
            clearColumn();
            return true;
        }
        if (!breaking.tick(level, pos)) return false;
        if (!breakAndStore(level, pos, highest.state())) return false;
        breaking.reset();
        supports.removeLast();
        cleanupTicks = 0;
        cooldown = 5;
        return false;
    }

    /**
     * The cells a short column could be built from to reach this block: ground under the crown, room over the feet for the
     * body and the supports, and at least one step up — a cell he is already within a swing from is no column's cell — no more
     * than his soil and {@link #MAX_SUPPORTS} allow. Empty when he cannot build up to it: no soil in the bag, a ceiling over
     * the column, or a crown too high for a short column.
     */
    public Set<BlockPos> buildCells(BlockPos block) { return buildCells(block, soilCount()); }

    /**
     * The same cells as if the bag held the soil for the longest column: what the crown asks of him with the soil aside. When
     * these are there and {@link #buildCells} has none, the soil is what he lacks for it.
     */
    public Set<BlockPos> buildCellsWithAnySoil(BlockPos block) { return buildCells(block, MAX_SUPPORTS); }

    /** The soil he carries for a column now: what the bag and the hands hold. */
    public int soilCarried() { return soilCount(); }

    /**
     * The longest column this block asks of him, in steps of soil: the most steps among the cells a column could stand at, so
     * the soil gathered for it is enough for whichever of them he raises. 0 when no column reaches it at all.
     */
    public int columnSteps(BlockPos block) {
        int longest = 0;
        for (int steps : columns(block).values()) longest = Math.max(longest, steps);
        return longest;
    }

    private Set<BlockPos> buildCells(BlockPos block, int supply) {
        Set<BlockPos> cells = new HashSet<>();
        for (var column : columns(block).entrySet())
            if (column.getValue() > 0 && column.getValue() <= MAX_SUPPORTS && column.getValue() <= supply) cells.add(column.getKey());
        return cells;
    }

    /** The cells a column to this block could stand at, each with the steps it asks: ground below, room over it, the reach. */
    private java.util.Map<BlockPos, Integer> columns(BlockPos block) {
        java.util.Map<BlockPos, Integer> cells = new java.util.LinkedHashMap<>();
        if (!(owner.level() instanceof ServerLevel level) || !level.hasChunkAt(block)) return cells;
        for (int down = 1; down <= MAX_SUPPORTS + 2; down++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos feet = block.offset(dx, -down, dz);
            if (!safeFoundation(level, feet.below())) continue;
            int steps = requiredSteps(block, feet);
            if (steps < 0 || steps > MAX_SUPPORTS || !columnClearable(level, feet, steps)) continue;
            cells.put(feet.immutable(), steps);
        }
        return cells;
    }

    /** Whether the column is being raised toward this block: the climb for it is what stands, not soil to be taken back. */
    public boolean climbingTo(BlockPos block) { return !recovering && target != null && target.equals(block); }

    /** Whether a swing from where he stands now reaches this block: the same reach the climb works to. */
    public boolean inReach(BlockPos block) {
        return jumpCell == null && owner.onGround()
                && owner.distanceToSqr(block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D) <= REACH_SQR;
    }

    private Column planColumn(ServerLevel level, BlockPos log) {
        int supply = soilCount();
        if (supply == 0) { failureReason = "Для подъёма не хватает земли в сумке"; return null; }
        failureReason = log.getY() + 0.5D - owner.getY() > MAX_SUPPORTS + 5.0D
                ? "Верхушка слишком высокая для короткой опоры" : "Не нашёл безопасного места для подъёма";
        Column best = null;
        BlockPos origin = owner.blockPosition();
        // Directly under the log is allowed: with the trunk chopped away, that is where a hanging crown is reached from.
        // While the trunk still stands, columnClearable rejects that column because the trunk itself is solid.
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos feet = new BlockPos(log.getX() + dx, origin.getY() + dy, log.getZ() + dz);
                if (feet.distSqr(origin) > 100.0D || !safeFoundation(level, feet.below())) continue;
                int steps = requiredSteps(log, feet);
                if (steps > supply && steps <= MAX_SUPPORTS) failureReason = "Для подъёма не хватает земли в сумке";
                if (steps <= 0 || steps > MAX_SUPPORTS || steps > supply || !columnClearable(level, feet, steps)) continue;
                if (!level.getEntities(owner, new AABB(feet).expandTowards(0, steps + 2, 0), CompanionPillar::blocksSupport).isEmpty()) continue;
                Path path = owner.work.route.toCells(Set.of(feet));
                if (path == null) continue;
                double score = feet.distSqr(origin) + steps * 4.0D + dx * dx + dz * dz;
                if (best == null || score < best.score()) best = new Column(feet.immutable(), path, steps, score);
            }
        }
        return best;
    }

    private int requiredSteps(BlockPos log, BlockPos feet) {
        double dx = log.getX() - feet.getX();
        double dz = log.getZ() - feet.getZ();
        double horizontal = dx * dx + dz * dz;
        if (horizontal > 8.0D) return -1;
        return Math.max(0, (int) Math.ceil(log.getY() + 0.5D - feet.getY() - Math.sqrt(REACH_SQR - horizontal)));
    }

    private boolean columnClearable(ServerLevel level, BlockPos feet, int steps) {
        for (int dy = 0; dy <= steps + 2; dy++) {
            BlockPos pos = feet.above(dy);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getFluidState(pos).isEmpty()) return false;
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() && !isFoliage(state)) return false;
        }
        return true;
    }

    private boolean safeFoundation(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    private boolean clearJumpSpace(ServerLevel level, BlockPos feet) {
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos pos = feet.above(dy);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getFluidState(pos).isEmpty()) return false;
            BlockState state = level.getBlockState(pos);
            if (isFoliage(state)) {
                if (cooldown > 0) return true;
                owner.selectToolForBlock(state);
                if (!breaking.tick(level, pos)) return true;
                if (!breakAndStore(level, pos, state)) return false;
                breaking.reset();
                cooldown = 4;
                return true;
            }
            if (!state.getCollisionShape(level, pos).isEmpty()) return false;
            if (dy == 0 && !state.isAir()) return false;
        }
        return true;
    }

    /** The overhead log that blocks the next jump, if a normal swing from where Oleg stands reaches it. */
    private BlockPos reachableLogAbove(ServerLevel level, BlockPos feet) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos pos = feet.above(dy);
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS)) return owner.distanceToSqr(pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D) <= REACH_SQR ? pos.immutable() : null;
            if (!state.getCollisionShape(level, pos).isEmpty() && !isFoliage(state)) return null;
        }
        return null;
    }

    /** A log found over the column during the last tick; the harvester switches to it and keeps the pillar. */
    public BlockPos takeBranch() { BlockPos found = branch; branch = null; return found; }

    private boolean jumpSpaceClear(ServerLevel level, BlockPos feet) {
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos pos = feet.above(dy);
            BlockState state = level.getBlockState(pos);
            if (isFoliage(state) || !state.getCollisionShape(level, pos).isEmpty() || dy == 0 && !state.isAir()) return false;
        }
        return true;
    }

    private static boolean isFoliage(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(Blocks.VINE);
    }

    private int soilCount() {
        int count = isSoil(owner.getMainHandItem()) ? owner.getMainHandItem().getCount() : 0;
        if (isSoil(owner.getOffhandItem())) count += owner.getOffhandItem().getCount();
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack stack = owner.getItem(slot);
            if (isSoil(stack)) count += stack.getCount();
        }
        return count;
    }

    private boolean prepareSoil() {
        if (isSoil(owner.getMainHandItem())) return true;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack stack = owner.getItem(slot);
            if (isSoil(stack)) {
                owner.equipForSupport(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                return isSoil(owner.getMainHandItem());
            }
        }
        if (isSoil(owner.getOffhandItem())) {
            String item = BuiltInRegistries.ITEM.getKey(owner.getOffhandItem().getItem()).toString();
            owner.unequipForSupport("offhand");
            owner.equipForSupport(item);
        }
        return isSoil(owner.getMainHandItem());
    }

    /** Stone goes under his feet only with a pickaxe at hand: broken by hand it would drop nothing, and the support would be lost. */
    private boolean isSoil(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && isSoilBlock(item.getBlock())
                && (item.getBlock() == Blocks.DIRT || item.getBlock() == Blocks.COARSE_DIRT || item.getBlock() == Blocks.ROOTED_DIRT || hasPickaxe());
    }

    private boolean hasPickaxe() {
        if (owner.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES)) return true;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(net.minecraft.tags.ItemTags.PICKAXES)) return true;
        return false;
    }

    private static boolean isSoilBlock(Block block) {
        // Not planks, logs, gravel or sand: those are worth more in the bag than under his feet (skyblock probe 15.11).
        return block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT
                || block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE || block == Blocks.NETHERRACK;
    }

    /** Whether the tool in his hand makes this block drop: a stone support broken without a pickaxe would be lost. */
    private boolean canDrop(BlockState state) {
        return !state.requiresCorrectToolForDrops() || owner.getMainHandItem().isCorrectToolForDrops(state);
    }

    private boolean breakAndStore(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.hasChunkAt(pos) || level.getBlockState(pos) != state) return false;
        owner.takeToolForBlock(state);
        if (!canDrop(state)) { owner.putToolAway(); return false; }
        ItemStack tool = owner.getMainHandItem();
        var drops = CompanionTools.drops(state, level, pos, level.getBlockEntity(pos), owner, tool);
        if (!level.destroyBlock(pos, false, owner, 512)) { owner.putToolAway(); return false; }
        CompanionTools.wearOnBreak(tool, level, state, pos, owner);
        owner.swing(InteractionHand.MAIN_HAND);
        owner.dropHarvest(pos, drops);
        // A gather whose tool is kept gets it back into his hand after the support's own tool; the hand the gather replaced
        // still waits for the end of the gather (CompanionWork.cancel).
        if (keptHand == null || keptHand.isEmpty()) owner.putToolAway();
        else owner.equipForSupport(BuiltInRegistries.ITEM.getKey(keptHand.getItem()).toString());
        return true;
    }

    private boolean anotherEntityDependsOn(ServerLevel level, BlockPos pos) {
        AABB above = new AABB(pos).move(0, 1, 0).inflate(0.01D, 0.15D, 0.01D);
        for (Entity entity : level.getEntities(owner, above, CompanionPillar::blocksSupport)) {
            double feet = entity.getBoundingBox().minY;
            if (feet >= pos.getY() + 0.85D && feet <= pos.getY() + 1.3D
                    && overlapsColumn(entity.getBoundingBox(), pos)) return true;
        }
        return false;
    }

    private static boolean overlapsColumn(AABB box, BlockPos pos) {
        return box.maxX > pos.getX() && box.minX < pos.getX() + 1
                && box.maxZ > pos.getZ() && box.minZ < pos.getZ() + 1;
    }

    private static boolean blocksSupport(Entity entity) {
        return entity.isAlive() && (entity instanceof LivingEntity || entity.canBeCollidedWith());
    }

    private int findLandingY(ServerLevel level, BlockPos first) {
        for (int below = 0; below <= 2; below++) {
            BlockPos pos = first.below(below);
            if (!level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty()) return Integer.MIN_VALUE;
            if (level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) return pos.getY() + 1;
        }
        return Integer.MIN_VALUE;
    }

    private double horizontalDistanceSqr(BlockPos pos) {
        double dx = owner.getX() - (pos.getX() + 0.5D);
        double dz = owner.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    private void resetWatchdog(BlockPos log) {
        target = log.immutable();
        attemptTicks = stalledTicks = 0;
        lastProgress = owner.position();
        failureReason = "";
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        requestCleanup(keepTool);
        return Outcome.FAILED;
    }

    private void clearColumn() {
        breaking.reset();
        base = target = jumpCell = branch = null;
        approach = null;
        lastProgress = null;
        attemptTicks = stalledTicks = jumpTicks = failedJumps = cooldown = cleanupTicks = 0;
        recovering = stand = leftStanding = false;
        keptHand = null;
        keepTool = false;
    }

    public void save(CompoundTag tag) {
        if (base != null) {
            tag.putLong("PillarBase", base.asLong());
            tag.putString("PillarDimension", owner.level().dimension().location().toString());
        }
        ListTag saved = new ListTag();
        for (Support support : supports) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", support.pos().asLong());
            entry.putString("Block", BuiltInRegistries.BLOCK.getKey(support.state().getBlock()).toString());
            saved.add(entry);
        }
        tag.put("PillarSupports", saved);
    }

    public void load(CompoundTag tag) {
        clearColumn();
        supports.clear();
        if (!tag.contains("PillarBase", Tag.TAG_LONG)) return;
        if (!owner.level().dimension().location().toString().equals(tag.getString("PillarDimension"))) return;
        base = BlockPos.of(tag.getLong("PillarBase"));
        ListTag saved = tag.getList("PillarSupports", Tag.TAG_COMPOUND);
        for (int index = 0; index < saved.size() && supports.size() < MAX_SUPPORTS; index++) {
            CompoundTag entry = saved.getCompound(index);
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("Block"));
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) continue;
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (!isSoilBlock(block) || pos.getX() != base.getX() || pos.getZ() != base.getZ()
                    || pos.getY() != base.getY() + supports.size()) continue;
            supports.add(new Support(pos.immutable(), block.defaultBlockState()));
        }
        requestCleanup();
    }
}

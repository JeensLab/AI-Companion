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

/**
 * A short, inventory-backed dirt pillar. Movement and falling use normal entity physics.
 * The steps are a per-climb limit: a leaf crown's column keeps its six, a way up to a block he cannot walk to (PATH7, #105)
 * may take thirty-two — a block thirty-two blocks above his feet asks twenty-nine steps, the height the owner's blocks hung at.
 */
public final class CompanionPillar {
    /** The steps a leaf crown's column may take: a crown stands close over the trunk. */
    public static final int LEAF_SUPPORTS = 6;
    /** The most steps a way up may take (PATH7): enough for a block thirty-two blocks above his feet (owner, 24.11). */
    public static final int WAY_SUPPORTS = 32;
    /** How far above his feet a block may stand for a way up (PATH7): the owner's blocks hung up to thirty-two above him. */
    public static final int WAY_HEIGHT = 32;
    /**
     * How far from him the foot of a column may stand, in blocks: the walk to it is his own legs (CompanionTravel), and a
     * block up and to the side is reached from the foot under it (a pumpkin eight across, PATH7). Ten blocks stopped the owner's
     * column short of a block twenty-five to the side; the foot may stand anywhere in the way's own box across — sixty-four,
     * the box the search itself looks through (owner, 24.11).
     */
    public static final int COLUMN_WALK = 64;
    /** Blocks placed and broken by one way, per task (PATH7): thirty-two steps up and the same down again. */
    public static final int WAY_BUDGET = 128;
    private static final double REACH_SQR = 16.0D;
    private static final int MAX_ATTEMPT_TICKS = 200;
    /** The ticks of one way's climb: its own steps at this much each, so thirteen steps still have room. */
    private static final int WAY_STEP_TICKS = 40;
    private static final int STALLED_TICKS = 80;
    private static final int STEP_INTERVAL = 10;

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
    /** The highest his feet reached in the last jump: said with a failed one, so the reason is read from the world. */
    private double jumpTop;
    private int failedJumps;
    private int cooldown;
    private int cleanupTicks;
    /** Ticks the column could not be planned for want of a search (he is in the air): waited for, bounded. */
    private int unplannedTicks;
    private static final int UNPLANNED_TICKS = 40;
    /** The feet a column to this target could stand at, best first: a foot that fails is traded for the next one, and only every
     *  one of them failing gives the column up (the owner, 25.11: it refused over one foot while other cells were about). */
    private List<Column> feet = new ArrayList<>();
    /** How many of those feet were asked of the route: each answer is a search, and a search is not free. */
    private int feetAsked;
    /** The target those feet belong to: its candidates are kept while they are asked, a few per tick. */
    private BlockPos feetTarget;
    /** A foot could not be answered this tick (the cells went elsewhere, or no search came about): not a failure, ask again. */
    private boolean footUnknown;
    private boolean recovering;
    /** The steps this climb may take: a leaf crown's six, a way up's sixteen (set by tickToward). */
    private int limit = LEAF_SUPPORTS;
    /** Whether this column is a way up to a block no walk reaches (PATH7), not a leaf crown's climb. */
    private boolean way;
    /** Whether the climb is toward a cell a drop lies in rather than a block (PATH7: a drop is the same question). */
    private boolean cellWay;
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
    /** Whether the column standing is the way up of PATH7, not a leaf crown's climb. */
    public boolean isWayActive() { return way && isActive(); }
    public boolean hasSupports() { return !supports.isEmpty(); }
    /** Whether a body or a drop resting on the column is on it: its box may sit on a support's corner, not only in the cell above it. */
    public boolean carries(AABB box) {
        for (Support support : supports) {
            BlockPos pos = support.pos();
            if (box.maxX > pos.getX() && box.minX < pos.getX() + 1 && box.maxZ > pos.getZ() && box.minZ < pos.getZ() + 1
                    && Math.abs(box.minY - (pos.getY() + 1)) < 0.2D) return true;
        }
        return false;
    }
    public boolean isRecovering() { return recovering; }
    public int supportCount() { return supports.size(); }
    public String failureReason() { return failureReason; }

    /** Called every server tick while harvesting. READY means that a normal log swing is in range. */
    public Outcome tickToward(BlockPos log) { return tickToward(log, true, LEAF_SUPPORTS); }

    /**
     * The same climb toward a block he breaks — a log of the trunk, or a leaf of a crown that stands above every stand cell
     * (a tree grown with bone meal, the owner 17.11: «он должен понимать что до него можно добраться если прокопать что то
     * или подстроиться»). A crown of leaves has no branch to switch to, so {@code wood} only decides what a swing over the
     * column means.
     */
    public Outcome tickToward(BlockPos block, boolean wood) { return tickToward(block, wood, LEAF_SUPPORTS); }

    /**
     * The same climb with its own limit on the steps: a leaf crown keeps {@link #LEAF_SUPPORTS}, a way up to a solid block no
     * walk reaches (PATH7, #105) may take {@link #WAY_SUPPORTS}. {@code wood} false means the block is not a log: any block
     * that is not air, so obsidian and stone are climbed to as a leaf crown is.
     */
    public Outcome tickToward(BlockPos block, boolean wood, int maxSupports) { return tickToward(block, wood, maxSupports, false); }

    /**
     * The same climb toward a cell a drop lies in rather than a block (PATH7, #105: a drop and a block are one question of
     * geometry). A drop is taken from a cell his body touches it from, so the column rises until his feet are level with that
     * cell — the top support one below it, or beside it where that cell's floor is the drop's own block. The cell is air (the
     * drop lies in it), which a block's climb refuses.
     */
    public Outcome tickTowardCell(BlockPos cell, int maxSupports) { return tickToward(cell, false, maxSupports, true); }

    private Outcome tickToward(BlockPos block, boolean wood, int maxSupports, boolean cellWay) {
        if (!(owner.level() instanceof ServerLevel level) || recovering) return Outcome.FAILED;
        this.cellWay = cellWay;
        limit = Math.clamp(maxSupports, 1, WAY_SUPPORTS);
        way = limit > LEAF_SUPPORTS;
        if (!level.hasChunkAt(block) || (wood ? !level.getBlockState(block).is(BlockTags.LOGS) : !cellWay && level.getBlockState(block).isAir())) return Outcome.FAILED;
        if (reached(block)) {
            if (jumpCell == null && owner.onGround()) return Outcome.READY;
        }
        if (base == null) {
            int busy = owner.work.route.busyAnswers();
            int unknown = owner.work.route.notSearchedAnswers();
            Column column = planColumn(level, block);
            // A column it could not search to for want of this tick's cells is not one without a way: planned again next tick.
            // Nor is one whose search did not run at all (he is in the air, in a jump): waited for, but not for ever.
            if (column == null) {
                boolean notKnown = footUnknown || owner.work.route.busyAnswers() != busy || owner.work.route.notSearchedAnswers() != unknown;
                if (notKnown && ++unplannedTicks <= UNPLANNED_TICKS) return Outcome.WORKING;
                return Outcome.FAILED;
            }
            unplannedTicks = 0;
            base = column.feet();
            approach = column.path();
            resetWatchdog(block);
        } else if (!block.equals(target)) {
            resetWatchdog(block);
        }

        int needed = stepsTo(block, base);
        if (needed < 0) return footFailed(level, block, "с этой опоры не достану");
        if (needed > limit) return footFailed(level, block, "верхушка слишком высокая для короткой опоры");
        if (soilCount() < Math.max(0, needed - supports.size())) return fail("Для подъёма не хватает земли в сумке");
        if (++attemptTicks > attemptBudget() || !level.hasChunkAt(base)) return fail("Не получилось подняться, поищу дерево пониже");
        if (way && attemptTicks % 40 == 0) dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "way_climb", "entity", owner.getUUID(),
                "base", base, "supports", supports.size(), "needed", needed, "limit", limit,
                "jump", jumpCell, "jumpTicks", jumpTicks, "ground", owner.onGround(), "feetY", Math.round(owner.getY() * 100) / 100D,
                "cooldown", cooldown, "soil", soilCount());
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
                if (approach == null || !approach.canReach()) return footFailed(level, block, "к опоре не пройти");
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
        if (owner.distanceToSqr(block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D) <= REACH_SQR && !cellWay) return Outcome.READY;
        if (supports.size() >= needed || supports.size() >= limit || cooldown > 0) return Outcome.WORKING;

        BlockPos placement = base.above(supports.size());
        // A branch of the same crown hanging over the column is not an obstacle: it is the next log to chop from here.
        BlockPos overhead = wood ? reachableLogAbove(level, placement) : null;
        if (overhead != null) { branch = overhead; return Outcome.WORKING; }
        if (!clearJumpSpace(level, placement)) return footFailed(level, block, "над опорой не хватает свободного места");
        // Clearing one leaf at a time is visible; wait until the next tick before jumping.
        if (!jumpSpaceClear(level, placement)) return Outcome.WORKING;
        if (!prepareSoil()) return fail("Не могу взять землю в руку, проверь место в моей сумке");
        if (!CompanionMiningNavigation.standable(owner, placement)) return footFailed(level, block, "под опорой нет безопасного места");
        Vec3 motion = owner.getDeltaMovement();
        owner.setDeltaMovement(motion.x * 0.15D, motion.y, motion.z * 0.15D);
        jumpCell = placement.immutable();
        jumpTicks = 0;
        jumpTop = owner.getBoundingBox().minY;
        owner.getJumpControl().jump();
        return Outcome.WORKING;
    }

    private Outcome tickJump(ServerLevel level) {
        owner.getNavigation().stop();
        owner.getMoveControl().setWantedPosition(base.getX() + 0.5D, owner.getY(), base.getZ() + 0.5D, 0.35D);
        jumpTicks++;
        jumpTop = Math.max(jumpTop, owner.getBoundingBox().minY);
        // The game holds a jump back for ten ticks after any other one (a hop onto a step on the way here): asked again
        // every eighth tick, as the self-rescue asks its own step, so a held-back first ask is not a failed climb.
        if (owner.onGround() && jumpTicks % 8 == 1 && owner.getDeltaMovement().y <= 0.01D) owner.getJumpControl().jump();
        // A goal holding the jump flag can keep the ask from the game's own jump: while the feet stay down, the mob's jump is
        // made straight (the same call the game makes for it), so a step is raised even then.
        if (jumpTicks > 2 && owner.onGround() && owner.getDeltaMovement().y <= 0.01D) owner.jumpFromGround();
        AABB cell = new AABB(jumpCell);
        if (owner.getBoundingBox().minY >= jumpCell.getY() + 1.001D
                && !owner.getBoundingBox().intersects(cell)) {
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
            cooldown = STEP_INTERVAL;
            failedJumps = 0;
            stalledTicks = 0;
            lastProgress = owner.position();
            return Outcome.WORKING;
        }
        if (jumpTicks > 28 && owner.onGround()) {
            BlockPos failedCell = jumpCell;
            jumpCell = null;
            cooldown = STEP_INTERVAL;
            if (way) dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "way_jump_failed", "entity", owner.getUUID(),
                    "failed", failedJumps + 1, "ticks", jumpTicks, "feetY", owner.getY(), "base", base,
                    "top", Math.round(jumpTop * 100) / 100D, "cell", failedCell);
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
        owner.getNavigation().stop();
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
                return false;
            }
        }
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
     * than his soil and {@link #LEAF_SUPPORTS} allow. Empty when he cannot build up to it: no soil in the bag, a ceiling over
     * the column, or a crown too high for a short column.
     */
    public Set<BlockPos> buildCells(BlockPos block) { return buildCells(block, soilCount()); }

    /**
     * The same cells as if the bag held the soil for the longest column: what the crown asks of him with the soil aside. When
     * these are there and {@link #buildCells} has none, the soil is what he lacks for it.
     */
    public Set<BlockPos> buildCellsWithAnySoil(BlockPos block) { return buildCells(block, LEAF_SUPPORTS); }

    /** The soil he carries for a column now: what the bag and the hands hold. */
    public int soilCarried() { return soilCount(); }

    /**
     * The longest column this block asks of him, in steps of soil: the most steps among the cells a column could stand at, so
     * the soil gathered for it is enough for whichever of them he raises. 0 when no column reaches it at all.
     */
    public int columnSteps(BlockPos block) {
        int longest = 0;
        for (int steps : columns(block, LEAF_SUPPORTS).values()) longest = Math.max(longest, steps);
        return longest;
    }

    /**
     * Whether a column can stand at this cell: its floor's top is the cell's own bottom (the ground, a stair's top, a path), so
     * the first support goes into the cell he stands in and one jump puts him on it. A bottom slab's top is half a cell up, and
     * the support would stand half a cell over his feet: a jump of one and a half blocks, which he does not make.
     */
    private boolean footForColumn(BlockPos foot) {
        double feet = CompanionMiningNavigation.standOr(owner, foot);
        return feet >= foot.getY() - 0.07D && feet <= foot.getY() + 0.07D;
    }

    /** The fewest steps of soil a column to this block takes, at most {@code limit}: 0 when no column reaches it at all. */
    public int leastSteps(BlockPos block, int limit) {
        int least = Integer.MAX_VALUE;
        for (int steps : columns(block, Math.clamp(limit, 1, WAY_SUPPORTS)).values()) least = Math.min(least, steps);
        return least == Integer.MAX_VALUE ? 0 : least;
    }

    /**
     * The fewest steps of a column whose foot he can walk to reaches this block, at most {@code limit}: the same feet the climb
     * itself plans from (beside the block, level with him, within {@link #COLUMN_WALK}), so the answer is no promise the climb
     * cannot keep. 0 when no such column reaches it.
     */
    public int leastStepsNear(BlockPos block, int limit) {
        if (!(owner.level() instanceof ServerLevel level) || !level.hasChunkAt(block)) return 0;
        BlockPos origin = owner.blockPosition();
        int least = 0;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -2; dy <= 2; dy++) {
            BlockPos feet = new BlockPos(block.getX() + dx, origin.getY() + dy, block.getZ() + dz);
            if (feet.distSqr(origin) > (double) COLUMN_WALK * COLUMN_WALK || !footForColumn(feet)) continue;
            int steps = requiredSteps(block, feet);
            if (steps <= 0 || steps > limit || !columnClearable(level, feet, steps)) continue;
            if (least == 0 || steps < least) least = steps;
        }
        return least;
    }

    /**
     * The fewest steps of a column to a drop's cell, at most {@code limit}: a drop is taken from a cell his body touches it
     * from, so the feet end level with the cell and the foot may stand anywhere under it within {@link #COLUMN_WALK}. 0 when
     * no column reaches it.
     */
    public int leastStepsNearCell(BlockPos cell, int limit) {
        if (!(owner.level() instanceof ServerLevel level) || !level.hasChunkAt(cell)) return 0;
        BlockPos origin = owner.blockPosition();
        int least = 0;
        for (var column : columnsForCell(cell, Math.clamp(limit, 1, WAY_SUPPORTS)).entrySet()) {
            if (column.getKey().distSqr(origin) > (double) COLUMN_WALK * COLUMN_WALK) continue;
            if (least == 0 || column.getValue() < least) least = column.getValue();
        }
        return least;
    }

    /** The steps a column from these feet asks to reach the block — or, for a drop's cell, to stand level with it and touch it. */
    private int stepsTo(BlockPos target, BlockPos feet) { return cellWay ? stepsToCell(target, feet) : requiredSteps(target, feet); }

    /**
     * The steps of a column from these feet to a drop's cell: the feet level with the cell, at most one across from it, so the
     * body touches the drop there. -1 when the feet are too far to the side or above the cell.
     */
    private int stepsToCell(BlockPos cell, BlockPos feet) {
        if (Math.abs(cell.getX() - feet.getX()) > 1 || Math.abs(cell.getZ() - feet.getZ()) > 1) return -1;
        int steps = cell.getY() - feet.getY();
        return steps < 0 ? -1 : steps;
    }

    /** Whether a swing from where he stands reaches the block — or his body the drop's cell. */
    private boolean reached(BlockPos target) {
        return cellWay ? CompanionMiningNavigation.touchesCell(owner, owner.position(), target)
                : owner.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) <= REACH_SQR;
    }

    /** The cells a column to a drop's cell could stand at, each with the steps it asks: ground below, room over it, the reach. */
    private java.util.Map<BlockPos, Integer> columnsForCell(BlockPos cell, int limit) {
        java.util.Map<BlockPos, Integer> cells = new java.util.LinkedHashMap<>();
        if (!(owner.level() instanceof ServerLevel level) || !level.hasChunkAt(cell)) return cells;
        for (int down = 1; down <= limit + 2; down++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos feet = cell.offset(dx, -down, dz);
            if (!footForColumn(feet)) continue;
            int steps = stepsToCell(cell, feet);
            if (steps <= 0 || steps > limit || !columnClearable(level, feet, steps)) continue;
            cells.put(feet.immutable(), steps);
        }
        return cells;
    }

    /** The cells a column could stand at to this block, with a way of up to {@code limit} steps and this much soil. */
    public Set<BlockPos> buildCells(BlockPos block, int limit, int supply) {
        Set<BlockPos> cells = new HashSet<>();
        for (var column : columns(block, Math.clamp(limit, 1, WAY_SUPPORTS)).entrySet())
            if (column.getValue() > 0 && column.getValue() <= limit && column.getValue() <= supply) cells.add(column.getKey());
        return cells;
    }

    private Set<BlockPos> buildCells(BlockPos block, int supply) {
        Set<BlockPos> cells = new HashSet<>();
        for (var column : columns(block, LEAF_SUPPORTS).entrySet())
            if (column.getValue() > 0 && column.getValue() <= LEAF_SUPPORTS && column.getValue() <= supply) cells.add(column.getKey());
        return cells;
    }

    /** The cells a column to this block could stand at, each with the steps it asks: ground below, room over it, the reach. */
    private java.util.Map<BlockPos, Integer> columns(BlockPos block, int limit) {
        java.util.Map<BlockPos, Integer> cells = new java.util.LinkedHashMap<>();
        if (!(owner.level() instanceof ServerLevel level) || !level.hasChunkAt(block)) return cells;
        // The feet may stand as far below the block as a swing from the top of the column reaches: four blocks and the block
        // itself, so a crown nine above the ground is no crown above its own six-step column. Sideways the feet stand within two
        // of the block: from there the top of the column swings to it whatever its offset up to the way's four (PATH7, #105).
        for (int down = 1; down <= limit + 5; down++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos feet = block.offset(dx, -down, dz);
            if (!footForColumn(feet)) continue;
            int steps = requiredSteps(block, feet);
            if (steps < 0 || steps > limit || !columnClearable(level, feet, steps)) continue;
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
        if (cellWay) return planCellColumn(level, log, supply);
        failureReason = log.getY() + 0.5D - owner.getY() > limit + 5.0D
                ? "Верхушка слишком высокая для короткой опоры" : "Не нашёл безопасного места для подъёма";
        BlockPos origin = owner.blockPosition();
        // The candidates of one target are kept while they are asked, a few feet a tick: a search is not free, and the feet after
        // the first six are asked at the next tick rather than dropped.
        if (!log.equals(feetTarget)) {
            List<Column> candidates = new ArrayList<>();
            // Directly under the log is allowed: with the trunk chopped away, that is where a hanging crown is reached from.
            // While the trunk still stands, columnClearable rejects that column because the trunk itself is solid.
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos foot = new BlockPos(log.getX() + dx, origin.getY() + dy, log.getZ() + dz);
                    // A foot a player walks on is a foot: the ground, a stair, a path (CompanionMiningNavigation.standable).
                    if (foot.distSqr(origin) > (double) COLUMN_WALK * COLUMN_WALK || !footForColumn(foot)) continue;
                    int steps = requiredSteps(log, foot);
                    if (steps > supply && steps <= limit) failureReason = "Для подъёма не хватает земли в сумке";
                    if (steps <= 0 || steps > limit || steps > supply || !columnClearable(level, foot, steps)) continue;
                    if (!level.getEntities(owner, new AABB(foot).expandTowards(0, steps + 2, 0), CompanionPillar::blocksSupport).isEmpty()) continue;
                    candidates.add(new Column(foot.immutable(), null, steps, foot.distSqr(origin) + steps * 4.0D + dx * dx + dz * dz));
                }
            }
            candidates.sort(java.util.Comparator.comparingDouble(Column::score));
            feetTarget = log.immutable(); feet = candidates; feetAsked = 0;
        }
        return nextFoot(level, log);
    }

    /**
     * The next foot of this column, asked of the route: the candidates best first, and a foot no walk reaches is passed over for
     * the next one. Null when none of them is reached — or when this tick's search cells went elsewhere, which is no answer at
     * all ({@link #footUnknown}).
     */
    private Column nextFoot(ServerLevel level, BlockPos target) {
        footUnknown = false;
        for (int asked = 0; asked < FEET_TRIES && feetAsked < feet.size(); asked++) {
            Column candidate = feet.get(feetAsked);
            int busy = owner.work.route.busyAnswers();
            Path path = owner.work.route.toCells(Set.of(candidate.feet()));
            if (path == null) {
                if (owner.work.route.busyAnswers() != busy) { footUnknown = true; return null; }
                sayFoot(target, candidate.feet(), "к опоре не пройти");
                feetAsked++;
                continue;
            }
            feetAsked++;
            return new Column(candidate.feet(), path, candidate.steps(), candidate.score());
        }
        // This tick's share of the searches is spent: the feet after these are asked at the next tick, not dropped.
        if (feetAsked < feet.size()) footUnknown = true;
        return null;
    }

    /** One line per foot refused: which cell was tried, why it was no good, and that another is asked. */
    private void sayFoot(BlockPos target, BlockPos foot, String why) {
        dev.aicompanion.CompanionDiagnostics.say(owner.diagnosticTrace(), "way_foot",
                "опора " + foot.toShortString() + " к " + target.toShortString() + " не годится: " + why + "; пробую следующую",
                "entity", owner.getUUID(), "target", target.toShortString(), "foot", foot.toShortString(), "why", why,
                "asked", feetAsked, "candidates", feet.size());
    }

    /**
     * The foot the column planned at is no good — no walk to it, no room over it, no foundation under its first support: the next
     * foot about the block is asked, and only when every one of them has failed is the column given up with the real reason.
     * While a support already stands the foot is settled, and the failure is the column's own.
     */
    private Outcome footFailed(ServerLevel level, BlockPos block, String why) {
        if (!supports.isEmpty()) return fail(why);
        if (base != null) sayFoot(block, base, why);
        owner.getNavigation().stop();
        approach = null; jumpCell = null; cooldown = 0;
        Column next = nextFoot(level, block);
        if (next == null) return footUnknown ? Outcome.WORKING : fail(why);
        base = next.feet(); approach = next.path(); resetWatchdog(block);
        return Outcome.WORKING;
    }

    /**
     * The column a drop's cell asks of him: a foot on the ground under the cell, at most one across from it, and the steps that
     * put his feet level with it (the top support one below, or beside it where that cell's floor is the drop's own block).
     * Only the nearest few feet are asked of the route: each answer is a whole search, and fifty of them in one tick is a
     * second of a stalled server (the host was seven seconds behind on the first run of this way).
     */
    private Column planCellColumn(ServerLevel level, BlockPos cell, int supply) {
        failureReason = cell.getY() - owner.getY() > limit + 5 ? "Слишком высоко для короткой опоры" : "Не нашёл безопасного места для подъёма";
        BlockPos origin = owner.blockPosition();
        if (!cell.equals(feetTarget)) {
            List<Column> candidates = new ArrayList<>();
            for (var entry : columnsForCell(cell, limit).entrySet()) {
                if (entry.getValue() > supply) { if (entry.getValue() <= limit) failureReason = "Для подъёма не хватает земли в сумке"; continue; }
                if (entry.getKey().distSqr(origin) > (double) COLUMN_WALK * COLUMN_WALK) continue;
                if (!level.getEntities(owner, new AABB(entry.getKey()).expandTowards(0, entry.getValue() + 2, 0), CompanionPillar::blocksSupport).isEmpty()) continue;
                candidates.add(new Column(entry.getKey(), null, entry.getValue(), entry.getValue() + entry.getKey().distSqr(origin)));
            }
            candidates.sort(java.util.Comparator.comparingDouble(Column::score));
            feetTarget = cell.immutable(); feet = candidates; feetAsked = 0;
        }
        return nextFoot(level, cell);
    }

    /** The feet of a column asked of the route in one tick: each answer is a search, and a search is not free. */
    private static final int FEET_TRIES = 6;

    private int requiredSteps(BlockPos log, BlockPos feet) {
        double dx = log.getX() - feet.getX();
        double dz = log.getZ() - feet.getZ();
        double horizontal = dx * dx + dz * dz;
        // The top of the column stands within a swing of the block: the feet are within two across of it, so the top of the
        // column reaches the block's middle, half a block over its cell.
        if (horizontal > 8.0D) return -1;
        double vertical = Math.sqrt(REACH_SQR - horizontal);
        int steps = Math.max(0, (int) Math.ceil(log.getY() + 0.5D - feet.getY() - vertical));
        // A step rounded up may leave the top over the block: what the top does not reach is no column.
        double over = log.getY() + 0.5D - (feet.getY() + steps);
        return horizontal + over * over <= REACH_SQR ? steps : -1;
    }

    private boolean columnClearable(ServerLevel level, BlockPos feet, int steps) {
        for (int dy = 0; dy <= steps + 2; dy++) {
            BlockPos pos = feet.above(dy);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getFluidState(pos).isEmpty()) return false;
            BlockState state = level.getBlockState(pos);
            // Foliage in the column's own cells is taken as it rises; a solid block of any kind — the player's, or a natural
            // ceiling — stands in the way for good, and the refusal says so (the matrix ratified it).
            if (!state.getCollisionShape(level, pos).isEmpty() && !isFoliage(state)) return false;
        }
        return true;
    }

    private boolean clearJumpSpace(ServerLevel level, BlockPos feet) {
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos pos = feet.above(dy);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getFluidState(pos).isEmpty()) return false;
            BlockState state = level.getBlockState(pos);
            if (isFoliage(state) || inTheWay(level, pos, state)) {
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

    /**
     * Whether the block in the way of the column is natural ground he may take: soil, stone, ore or netherrack, its drop in his
     * hand. A wall or a floor the player built is never broken — the way is walked round it (idea PATH7, #105: «убирает мешающий
     * блок», но чужую постройку не ломает).
     */
    private boolean inTheWay(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getCollisionShape(level, pos).isEmpty()) return false;
        if (!(CompanionQuarry.soil(state) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER))) return false;
        return !CompanionPicking.kept(owner, level).test(pos) && toolForIt(state);
    }

    /** Whether a tool of his (in hand or in the bag) makes this block drop: a stone cell is no way without a pickaxe. */
    private boolean toolForIt(BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return true;
        if (owner.getMainHandItem().isCorrectToolForDrops(state) || owner.getOffhandItem().isCorrectToolForDrops(state)) return true;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).isCorrectToolForDrops(state)) return true;
        return false;
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

    /** The ticks one climb may take: a leaf crown's six steps in 200, a way's steps at {@link #WAY_STEP_TICKS} each. */
    private int attemptBudget() { return way ? Math.max(MAX_ATTEMPT_TICKS, limit * WAY_STEP_TICKS) : MAX_ATTEMPT_TICKS; }

    private void clearColumn() {
        breaking.reset();
        base = target = jumpCell = branch = null;
        approach = null;
        feetTarget = null; feet.clear(); feetAsked = 0; footUnknown = false;
        lastProgress = null;
        attemptTicks = stalledTicks = jumpTicks = failedJumps = cooldown = cleanupTicks = unplannedTicks = 0;
        recovering = false;
        limit = LEAF_SUPPORTS;
        way = false;
        cellWay = false;
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
        for (int index = 0; index < saved.size() && supports.size() < WAY_SUPPORTS; index++) {
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

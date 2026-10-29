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
import java.util.List;
import java.util.Set;

/** A short, inventory-backed dirt pillar. Movement and falling use normal entity physics. */
public final class CompanionPillar {
    public static final int MAX_SUPPORTS = 6;
    private static final double REACH_SQR = 16.0D;
    private static final int MAX_ATTEMPT_TICKS = 200;
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
    private int failedJumps;
    private int cooldown;
    private int cleanupTicks;
    private boolean recovering;
    private String failureReason = "";

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
    public Outcome tickToward(BlockPos log) {
        if (!(owner.level() instanceof ServerLevel level) || recovering) return Outcome.FAILED;
        if (!level.hasChunkAt(log) || !level.getBlockState(log).is(BlockTags.LOGS)) return Outcome.FAILED;
        if (owner.distanceToSqr(log.getX() + 0.5D, log.getY() + 0.5D, log.getZ() + 0.5D) <= REACH_SQR) {
            if (jumpCell == null && owner.onGround()) return Outcome.READY;
        }
        if (base == null) {
            int busy = owner.work.route.busyAnswers();
            Column column = planColumn(level, log);
            // A column it could not search to for want of this tick's cells is not one without a way: planned again next tick.
            if (column == null) return owner.work.route.busyAnswers() != busy ? Outcome.WORKING : Outcome.FAILED;
            base = column.feet();
            approach = column.path();
            resetWatchdog(log);
        } else if (!log.equals(target)) {
            resetWatchdog(log);
        }

        int needed = requiredSteps(log, base);
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
        if (owner.distanceToSqr(log.getX() + 0.5D, log.getY() + 0.5D, log.getZ() + 0.5D) <= REACH_SQR) return Outcome.READY;
        if (supports.size() >= needed || supports.size() >= MAX_SUPPORTS || cooldown > 0) return Outcome.WORKING;

        BlockPos placement = base.above(supports.size());
        // A branch of the same crown hanging over the column is not an obstacle: it is the next log to chop from here.
        BlockPos overhead = reachableLogAbove(level, placement);
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

    private Outcome tickJump(ServerLevel level) {
        owner.getNavigation().stop();
        owner.getMoveControl().setWantedPosition(base.getX() + 0.5D, owner.getY(), base.getZ() + 0.5D, 0.35D);
        jumpTicks++;
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
            jumpCell = null;
            cooldown = STEP_INTERVAL;
            if (++failedJumps >= 2) return fail("Здесь не получается подпрыгнуть, поищу другое место");
        }
        return Outcome.WORKING;
    }

    /** Cancel wood swings immediately; keep the pillar until normal descent has finished. */
    public void requestCleanup() {
        breaking.reset();
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
        owner.selectToolForBlock(highest.state());
        if (!owner.getMainHandItem().isEmpty() && !owner.getMainHandItem().is(net.minecraft.tags.ItemTags.SHOVELS)) owner.unequipForSupport("mainhand");
        if (!breaking.tick(level, pos)) return false;
        if (!breakAndStore(level, pos, highest.state())) return false;
        breaking.reset();
        supports.removeLast();
        cooldown = 5;
        return false;
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

    private static boolean isSoil(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                && isSoilBlock(item.getBlock());
    }

    private static boolean isSoilBlock(Block block) {
        return block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT;
    }

    private boolean breakAndStore(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.hasChunkAt(pos) || level.getBlockState(pos) != state) return false;
        owner.selectToolForBlock(state);
        ItemStack tool = owner.getMainHandItem();
        var drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), owner, tool);
        if (!level.destroyBlock(pos, false, owner, 512)) return false;
        tool.getItem().mineBlock(tool, level, state, pos, owner);
        owner.swing(InteractionHand.MAIN_HAND);
        owner.dropHarvest(pos, drops);
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
        requestCleanup();
        return Outcome.FAILED;
    }

    private void clearColumn() {
        breaking.reset();
        base = target = jumpCell = branch = null;
        approach = null;
        lastProgress = null;
        attemptTicks = stalledTicks = jumpTicks = failedJumps = cooldown = cleanupTicks = 0;
        recovering = false;
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

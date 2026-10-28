package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded assistance for a genuinely blocked route. Supports remain as real terrain after escape. */
final class CompanionRecovery {
    private final CompanionEntity owner;
    private final CompanionBlockBreaking breaking;
    private Vec3 observed;
    private BlockPos destination;
    private BlockPos jumpCell;
    private BlockPos digTarget;
    private Vec3 start;
    private int stalled;
    private int elapsed;
    private int placed;
    private int dug;
    private int jumpTicks;
    private long retryAfter;
    private boolean active;
    private boolean exitingToPlayer;
    private String previousHand;
    private BlockPos checkedIntent;
    private long nextRouteCheck;
    private boolean missing;
    /** Cutting steps up a wall: the direction kept while it works, the step being taken, and steps that failed. */
    private boolean climbing;
    private Direction stairDirection;
    private BlockPos stepUp, stepFrom;
    private int stepTicks;
    private final java.util.Set<Long> blockedStairs = new java.util.HashSet<>();
    CompanionRecovery(CompanionEntity owner) { this.owner = owner; breaking = new CompanionBlockBreaking(owner); }
    boolean active() { return active; }

    void observe() {
        if (active) return;
        BlockPos intent = owner.recoveryDestination();
        if (!owner.canRecoverRoute() || intent == null || owner.isMiningAnimation() || owner.actionAnimation() != 0) { stalled = 0; observed = owner.position(); return; }
        if (observed == null || owner.position().distanceToSqr(observed) > .25) { observed = owner.position(); stalled = 0; return; }
        boolean trying = !owner.getNavigation().isDone();
        int walls = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = owner.blockPosition().relative(direction).above();
            if (!owner.level().getBlockState(side).getCollisionShape(owner.level(), side).isEmpty()) walls++;
        }
        // Game log 22.10 (b65ec97a, efc17045): the stone a block above his feet, right in front of him, and an idle moment
        // between swings made a «trap»: he dug the soil around and stacked blocks where a player simply mines on. A block
        // within arm's reach is work, not a destination; and standing still is a failed route only if no route is found.
        boolean inHand = withinHand(intent);
        boolean trappedBelow = !inHand && walls >= 3 && intent.getY() - owner.getY() > 1.25 && intent.distSqr(owner.blockPosition()) < 32 * 32;
        boolean failedRoute = !inHand && owner.getNavigation().isDone() && (intent.getY() - owner.getY() > .75
                || intent.distSqr(owner.blockPosition()) > 16) && routeMissing(intent);
        if (trying || trappedBelow || failedRoute) stalled++; else stalled = 0;
    }
    private boolean withinHand(BlockPos intent) {
        var level = owner.level();
        return level.hasChunkAt(intent) && !level.getBlockState(intent).getCollisionShape(level, intent).isEmpty()
                && owner.getEyePosition().distanceToSqr(intent.getCenter()) <= 4.5 * 4.5
                && CompanionMiningNavigation.visible(owner, owner.getEyePosition(), intent);
    }
    /** A real route search, once a second per destination: an idle navigation alone is not a missing way. */
    private boolean routeMissing(BlockPos intent) {
        long now = owner.level().getGameTime();
        if (!intent.equals(checkedIntent) || now >= nextRouteCheck) {
            checkedIntent = intent.immutable(); nextRouteCheck = now + 20;
            missing = owner.work.route.toCells(java.util.Set.of(intent), 1) == null;
        }
        return missing;
    }
    boolean canStart() {
        return !active && owner.level().getGameTime() >= retryAfter && stalled >= 60 && owner.canRecoverRoute() && owner.onGround();
    }
    void begin() {
        previousHand = owner.getMainHandItem().isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(owner.getMainHandItem().getItem()).toString();
        active = true; elapsed = placed = dug = jumpTicks = 0; climbing = false; stairDirection = null; stepUp = null; blockedStairs.clear();
        destination = owner.recoveryDestination(); start = owner.position();
        var player = owner.getOwnerPlayer();
        exitingToPlayer = player != null && destination != null && destination.equals(player.blockPosition());
        jumpCell = digTarget = null; breaking.reset(); owner.getNavigation().stop(); owner.syncWorkState();
    }
    void cancel() {
        restoreHand();
        active = false; stalled = 0; observed = owner.position(); jumpCell = digTarget = null; stepUp = null;
        breaking.reset(); retryAfter = owner.level().getGameTime() + 40;
    }
    void tick() {
        if (!(owner.level() instanceof ServerLevel level) || !active) return;
        // Steps dug up a wall take longer and dig more than a short clearance; still bounded.
        if (++elapsed > (climbing ? 1800 : 600) || placed >= 8 || dug >= (climbing ? 60 : 12) || destination == null) { stop(false); return; }
        if (jumpCell != null) { jump(level); return; }
        if (stepUp != null) { stepUp(); return; }
        if (!owner.onGround()) return;
        Path exit = owner.work.route.toCells(java.util.Set.of(destination), 1);
        if (exit != null) {
            CompanionMiningNavigation.walk(owner, exit);
            // Finish an ascent toward the player before yielding: following may be inactive or cross its start threshold
            // between goal updates. Work destinations retain their existing movement handoff and time budget.
            if (exitingToPlayer && belowExit(exit)) return;
            double dx = owner.getX() - start.x, dz = owner.getZ() - start.z;
            // Gaining height on a pillar is not yet an exit. Walk off the column before
            // yielding movement back to the task, otherwise a short route is cancelled mid-jump.
            if (dx * dx + dz * dz > 2.25 || owner.distanceToSqr(destination.getCenter()) < 9) stop(true);
            return;
        }
        if (digTarget != null) {
            if (!safeTarget(level, digTarget)) { digTarget = null; breaking.reset(); }
            else { dig(level); return; }
        }
        // Down like a player (a pillar, a ledge with no way off): the block under the feet is dug where ground is seen at most
        // three blocks lower — a drop that does not hurt (marathon 23.10: on a pillar of soil seven high, «иди ко мне» ended in
        // «застрял» while every block under him was soil).
        // Not for a drop lying below (he walks to it), and not back down the steps he has just cut up a wall.
        if (destination.getY() < owner.getY() - 1.5 && !climbing && !owner.hasPendingLoot() && safeBelow(level, owner.blockPosition().below())) {
            digTarget = owner.blockPosition().below().immutable(); dig(level); return;
        }
        // Up a wall like a player (game log 23.10: a ravine, nothing to build a column from): dig the cell over the head and the
        // two in front one higher, step up, again — a staircase cut into the wall, then the ordinary way once there is one.
        if (destination.getY() > owner.getY() + 1.5 && stairStep(level)) return;
        if (destination.getY() > owner.getY() + .5 && prepareBlock() && canJump(level)) {
            BlockPos cell = owner.blockPosition();
            double horizontal = Math.pow(owner.getX() - cell.getX() - .5, 2) + Math.pow(owner.getZ() - cell.getZ() - .5, 2);
            if (horizontal > .0144) {
                owner.getNavigation().stop(); owner.getMoveControl().setWantedPosition(cell.getX() + .5, cell.getY(), cell.getZ() + .5, .6); return;
            }
            jumpCell = cell.immutable(); jumpTicks = 0;
            owner.getNavigation().stop(); owner.getJumpControl().jump();
            owner.workStatus("Выбираюсь: подпрыгну и поставлю блок под ноги."); return;
        }
        digTarget = findDigTarget(level);
        if (digTarget != null) { dig(level); return; }
        // An item just dislodged above the mob may still be falling toward pickup range.
        if (owner.hasPendingLoot() && elapsed < 150) return;
        stop(false);
    }

    /** A block destination can end the path beside it, below its own height. */
    private boolean belowExit(Path exit) {
        var end = exit.getEndNode();
        int height = end == null ? destination.getY() : Math.min(destination.getY(), end.y);
        return owner.getY() < height - .5;
    }

    /**
     * A block to stand on: soil first, then stone the work does not need, then wood only when the work needs none
     * (review 22.10, В8). Nothing spare: digging a way out is tried instead.
     */
    private boolean prepareBlock() {
        if (spare(owner.getMainHandItem())) return true;
        ItemStack chosen = ItemStack.EMPTY;
        int best = Integer.MAX_VALUE;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack candidate = owner.getItem(slot);
            if (!spare(candidate)) continue;
            int rank = CompanionTools.soilItem(candidate) ? 0 : candidate.is(ItemTags.PLANKS) ? 2 : 1;
            if (rank < best) { best = rank; chosen = candidate; }
        }
        if (!chosen.isEmpty()) return owner.equipForSupport(BuiltInRegistries.ITEM.getKey(chosen.getItem()).toString()).success();
        if (!owner.workWoodSpare()) return false;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack logs = owner.getItem(slot);
            if (!logs.is(ItemTags.LOGS)) continue;
            String type = BuiltInRegistries.ITEM.getKey(logs.getItem()).getPath().replaceFirst("^stripped_", "").replaceFirst("_(log|wood|stem|hyphae)$", "");
            if (owner.craftFromBag("minecraft:" + type + "_planks", 1).success()) return prepareBlock();
        }
        return false;
    }
    private boolean spare(ItemStack stack) {
        if (!buildingBlock(stack)) return false;
        if (CompanionTools.soilItem(stack) || stack.is(Items.STONE)) return true;
        if (stack.is(ItemTags.PLANKS)) return owner.workWoodSpare();
        long reserve = owner.workStoneReserve();
        return CompanionTools.count(owner, ItemTags.STONE_TOOL_MATERIALS) - reserve >= 1;
    }
    private boolean buildingBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem && (CompanionTools.soilItem(stack) || stack.is(ItemTags.PLANKS)
                || stack.is(Items.COBBLESTONE) || stack.is(Items.STONE) || stack.is(Items.COBBLED_DEEPSLATE));
    }
    private boolean canJump(ServerLevel level) {
        BlockPos cell = owner.blockPosition();
        if (!level.hasChunkAt(cell) || !level.getFluidState(cell).isEmpty() || !level.getBlockState(cell).canBeReplaced()
                || !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) return false;
        for (int y = 1; y <= 3; y++) {
            BlockPos above = cell.above(y);
            if (!level.hasChunkAt(above) || !level.getFluidState(above).isEmpty() || !level.getBlockState(above).getCollisionShape(level, above).isEmpty()) return false;
        }
        return true;
    }
    private void jump(ServerLevel level) {
        owner.getNavigation().stop(); jumpTicks++;
        owner.getMoveControl().setWantedPosition(jumpCell.getX() + .5, owner.getY(), jumpCell.getZ() + .5, .35);
        if (owner.getBoundingBox().minY > jumpCell.getY() + 1.001 && !owner.getBoundingBox().intersects(new AABB(jumpCell))) {
            if (!buildingBlock(owner.getMainHandItem()) || !level.getBlockState(jumpCell).canBeReplaced()
                    || !level.getFluidState(jumpCell).isEmpty() || !level.getWorldBorder().isWithinBounds(jumpCell)
                    || !level.getEntities(owner, new AABB(jumpCell), e -> e instanceof LivingEntity && e.isAlive()).isEmpty()) { stop(false); return; }
            BlockState state = ((BlockItem)owner.getMainHandItem().getItem()).getBlock().defaultBlockState();
            if (!state.canSurvive(level, jumpCell) || !level.setBlockAndUpdate(jumpCell, state)) { stop(false); return; }
            owner.rememberPlaced(jumpCell);
            owner.animateAction(1, jumpCell, owner.getMainHandItem());
            owner.getMainHandItem().shrink(1); owner.setChanged(); owner.swing(InteractionHand.MAIN_HAND); placed++;
            var sound = state.getSoundType(level, jumpCell, owner);
            level.playSound(null, jumpCell, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, .7F, 1);
            jumpCell = null; return;
        }
        if (jumpTicks > 30 && owner.onGround()) { jumpCell = null; digTarget = findDigTarget(level); }
    }
    private boolean stairStep(ServerLevel level) {
        BlockPos feet = owner.blockPosition();
        java.util.List<Direction> directions = new java.util.ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        directions.sort(java.util.Comparator.comparingDouble((Direction direction) -> direction == stairDirection ? -1 : feet.relative(direction).distSqr(destination)));
        for (Direction direction : directions) {
            if (blockedStairs.contains(feet.asLong() * 4 + direction.get2DDataValue())) continue;
            BlockPos step = feet.relative(direction), next = step.above();
            // A wall to cut steps into: solid underfoot in front, not a drop.
            if (!level.getBlockState(step).isFaceSturdy(level, step, Direction.UP)) continue;
            BlockPos closed = null;
            for (BlockPos cell : new BlockPos[]{feet.above(2), next, next.above()})
                if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) { closed = cell; break; }
            if (closed != null && !safeToDig(level, closed)) continue;
            stairDirection = direction; climbing = true;
            owner.workStatus("Выбираюсь: копаю ступеньки наверх.");
            if (closed != null) { digTarget = closed.immutable(); dig(level); return true; }
            stepUp = next.immutable(); stepFrom = feet; stepTicks = 0;
            owner.getNavigation().stop(); owner.getJumpControl().jump();
            return true;
        }
        // No wall beside him (the middle of a ravine floor): a step towards the goal first, to the wall the steps go into.
        for (Direction direction : directions) {
            BlockPos aside = feet.relative(direction);
            if (blockedStairs.contains(feet.asLong() * 4 + direction.get2DDataValue()) || !CompanionMiningNavigation.standable(owner, aside)) continue;
            if (aside.distSqr(destination) >= feet.distSqr(destination)) continue;
            stairDirection = direction; stepUp = aside.immutable(); stepFrom = feet; stepTicks = 0; climbing = true;
            owner.getNavigation().stop();
            return true;
        }
        return false;
    }
    /** Onto the step just cut; a step that cannot be taken is not tried again from the same cell. */
    private void stepUp() {
        owner.getMoveControl().setWantedPosition(stepUp.getX() + .5, stepUp.getY(), stepUp.getZ() + .5, .6);
        if (owner.getY() >= stepUp.getY() - .01 && owner.blockPosition().equals(stepUp)) { stepUp = null; return; }
        if (owner.onGround() && ++stepTicks % 8 == 0) owner.getJumpControl().jump();
        if (stepTicks > 48) { blockedStairs.add(stepFrom.asLong() * 4 + stairDirection.get2DDataValue()); stairDirection = null; stepUp = null; }
    }

    private BlockPos findDigTarget(ServerLevel level) {
        BlockPos feet = owner.blockPosition();
        for (int up : new int[]{2, 3}) if (safeToDig(level, feet.above(up))) return feet.above(up);
        ListDirection[] directions = new ListDirection[4]; int index = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) directions[index++] = new ListDirection(direction, feet.relative(direction).distSqr(destination));
        java.util.Arrays.sort(directions, java.util.Comparator.comparingDouble(ListDirection::score));
        for (ListDirection candidate : directions) for (int up : new int[]{2, 1, 0}) {
            BlockPos pos = feet.relative(candidate.direction).above(up);
            if (safeToDig(level, pos)) return pos;
        }
        return null;
    }
    private record ListDirection(Direction direction, double score) { }
    private boolean safeToDig(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || owner.distanceToSqr(pos.getCenter()) > 16) return false;
        BlockState state = level.getBlockState(pos);
        if (!(CompanionQuarry.soil(state) || CompanionWork.isStone(state) || state.is(net.minecraft.tags.BlockTags.LEAVES)) || state.getDestroySpeed(level, pos) < 0) return false;
        for (Direction direction : Direction.values()) {
            BlockPos near = pos.relative(direction);
            var fluid = level.getFluidState(near);
            // Marathon 23.10: out of a flooded shaft at the bottom of a ravine every wall cell touches the water, and no step was ever
            // cut. Water only wets the new step (a player digs there); lava never. Sand or gravel falls in only from above.
            if (!level.hasChunkAt(near) || fluid.is(net.minecraft.tags.FluidTags.LAVA) || level.getBlockEntity(near) != null
                    || level.getBlockState(near).is(Blocks.CRAFTING_TABLE)
                    || direction == Direction.UP && level.getBlockState(near).getBlock() instanceof net.minecraft.world.level.block.FallingBlock) return false;
        }
        return !CompanionMiningNavigation.supportsLivingEntity(owner, pos)
                && CompanionMiningNavigation.visible(owner, owner.getEyePosition(), pos);
    }
    /** The block under his feet may go: soil or stone, no lava round it, and below it ground within three blocks, nothing hot. */
    private boolean safeBelow(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (!(CompanionQuarry.soil(state) || CompanionWork.isStone(state)) || state.getDestroySpeed(level, pos) < 0
                || state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) return false;
        for (Direction direction : Direction.values()) {
            BlockPos near = pos.relative(direction);
            if (!level.hasChunkAt(near) || level.getFluidState(near).is(net.minecraft.tags.FluidTags.LAVA) || level.getBlockEntity(near) != null
                    || level.getBlockState(near).is(Blocks.CRAFTING_TABLE)) return false;
        }
        for (int down = 1; down <= 3; down++) {
            BlockPos cell = pos.below(down);
            if (!level.hasChunkAt(cell) || level.getFluidState(cell).is(net.minecraft.tags.FluidTags.LAVA)) return false;
            if (level.getBlockState(cell).isFaceSturdy(level, cell, Direction.UP)) return true;
            if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) return false;
        }
        return false;
    }
    private boolean safeTarget(ServerLevel level, BlockPos pos) {
        return pos.equals(owner.blockPosition().below()) ? safeBelow(level, pos) : safeToDig(level, pos);
    }
    private void dig(ServerLevel level) {
        BlockState state = level.getBlockState(digTarget);
        if (CompanionQuarry.soil(state)) {
            CompanionTools.equip(owner, ItemTags.SHOVELS, state);
            if (!owner.getMainHandItem().isEmpty() && !owner.getMainHandItem().is(ItemTags.SHOVELS)) owner.unequipForSupport("mainhand");
        } else if (CompanionWork.isStone(state)) {
            CompanionTools.equip(owner, ItemTags.PICKAXES, state);
        }
        owner.getLookControl().setLookAt(digTarget.getX() + .5, digTarget.getY() + .5, digTarget.getZ() + .5);
        owner.workStatus(digTarget.equals(owner.blockPosition().below()) ? "Спускаюсь: раскапываю блок под ногами." : "Расчищаю небольшой проход, чтобы выбраться.");
        if (!breaking.tick(level, digTarget)) return;
        var tool = owner.getMainHandItem();
        var drops = net.minecraft.world.level.block.Block.getDrops(state, level, digTarget, null, owner, tool);
        if (!owner.canStoreDrops(drops)) { stop(false); return; }
        if (safeTarget(level, digTarget) && level.destroyBlock(digTarget, false, owner, 512)) {
            tool.getItem().mineBlock(tool, level, state, digTarget, owner); owner.dropHarvest(digTarget, drops); dug++;
        }
        breaking.reset(); digTarget = null;
    }
    private void stop(boolean success) {
        restoreHand();
        if (success && (placed > 0 || dug > 0)) owner.routeRecovered();
        active = false; stalled = 0; observed = owner.position(); jumpCell = digTarget = null; stepUp = null; breaking.reset();
        retryAfter = owner.level().getGameTime() + (success ? 40 : 200);
        // With no work running, the status still holds the result of the finished one (game log 22.10: «Выбрался…» replaced it).
        if (owner.hasActiveWorkOrder()) owner.workStatus(success ? "Выбрался, продолжаю задачу." : "Здесь пока не получилось выбраться безопасным способом.");
    }
    private void restoreHand() {
        if (previousHand == null) return;
        if (previousHand.isEmpty()) owner.unequipForSupport("mainhand");
        else owner.equipForSupport(previousHand);
        previousHand = null;
    }
}

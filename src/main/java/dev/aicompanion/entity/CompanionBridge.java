package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A floor across a gap, laid of what he carries and taken back when he comes home — the same way as a column, sideways (idea
 * PATH7, #105: «мост через провал»). He walks to the bank, sets the cells of floor one by one, crosses to the cell he works the
 * block from, and on the way back breaks his own cells behind him, the drop of each going into his bag. Geometry comes from
 * CompanionReach.bridge: this class only walks, lays and breaks — it never asks what the block it is reaching for is.
 */
final class CompanionBridge {
    enum Outcome { WORKING, READY, FAILED }

    /** How many walks to the bank or the far cell one bridge may spend, and how long it waits for a search. */
    private static final int LEGS = 12, WAIT = 60;
    /** Ticks between two cells laid, and between two of his own taken back. */
    private static final int LAY_INTERVAL = 4, BREAK_INTERVAL = 5;
    /** Ticks the bridge may stand without the block coming in reach. */
    private static final int PATIENCE = 400;
    /** Ticks the way back may spend before his own floor is left standing (another player may need it). */
    private static final int CLEANUP_TICKS = 600;

    private final CompanionEntity owner;
    private final CompanionBlockBreaking breaking;
    private CompanionReach.BridgePlan plan;
    private BlockPos target;
    private final List<BlockPos> placed = new ArrayList<>();
    private Path path;
    private int legs, waited, cooldown, ticks, cleanupTicks, stalledTicks;
    private double best;
    private boolean recovering;
    private String failureReason = "";

    CompanionBridge(CompanionEntity owner) { this.owner = owner; this.breaking = new CompanionBlockBreaking(owner); }

    boolean isActive() { return plan != null || !placed.isEmpty(); }
    boolean isRecovering() { return recovering; }
    boolean hasSupports() { return !placed.isEmpty(); }
    int supportCount() { return placed.size(); }
    String failureReason() { return failureReason; }

    /** One tick toward a block no walk reaches: to the bank, the floor laid, across, and the block in reach. */
    Outcome tickToward(BlockPos block) {
        if (!(owner.level() instanceof ServerLevel level) || recovering) return Outcome.FAILED;
        if (!level.hasChunkAt(block) || level.getBlockState(block).isAir()) return Outcome.FAILED;
        if (inReach(level, block)) { stalledTicks = 0; return Outcome.READY; }
        if (plan == null || !block.equals(target)) {
            CompanionReach.BridgePlan found = CompanionReach.bridge(owner, level, block, 3.5D, true, CompanionPillar.WAY_SUPPORTS, false);
            if (found == null) return fail("негде класть мост");
            plan = found;
            target = block.immutable();
            placed.clear();
            path = null;
            legs = waited = ticks = stalledTicks = 0;
            best = owner.distanceToSqr(block.getCenter());
            CompanionReach.decision(owner, block, new CompanionReach.How(CompanionReach.Way.BRIDGE, 0, found.length(), ""));
            dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "bridge_planned", "entity", owner.getUUID(),
                    "target", block.toShortString(), "start", found.start().toShortString(), "end", found.end().toShortString(),
                    "cells", found.length());
        }
        if (++ticks > PATIENCE && placed.size() == plan.cells().size()) return fail("мост есть, а не достаю");
        if (cooldown > 0) cooldown--;
        // The bank first: the floor is laid from the side he can walk to, never from across the gap.
        if (!at(plan.start())) {
            owner.workStatus("Иду к берегу провала.");
            if (!walkTo(plan.start())) return fail("не дошёл до берега");
            return Outcome.WORKING;
        }
        if (placed.size() < plan.cells().size()) {
            BlockPos cell = plan.cells().get(placed.size());
            BlockState floor = level.getBlockState(cell);
            if (!floor.getCollisionShape(level, cell).isEmpty()) { placed.add(cell); return Outcome.WORKING; }
            if (cooldown > 0) return Outcome.WORKING;
            if (!lay(level, cell)) return fail("нечем мостить: в сумке нет блоков");
            cooldown = LAY_INTERVAL;
            return Outcome.WORKING;
        }
        if (!at(plan.end())) {
            owner.workStatus("Иду по мосту.");
            if (!walkTo(plan.end())) return fail("по мосту не прошёл");
            return Outcome.WORKING;
        }
        owner.getNavigation().stop();
        if (inReach(level, block)) return Outcome.READY;
        // He stands where the geometry said the block is in reach: the world changed (a block was set there since).
        if (owner.distanceToSqr(block.getCenter()) < best - .04D) { best = owner.distanceToSqr(block.getCenter()); stalledTicks = 0; }
        else if (++stalledTicks > 60) return fail("мост проложен, а блок не в руках");
        return Outcome.WORKING;
    }

    /** The floor comes back first, then the drops: he walks back over his own cells, breaking each behind him. */
    void requestCleanup() {
        breaking.reset();
        if (recovering) return;
        recovering = isActive();
        path = null;
        owner.getNavigation().stop();
        cleanupTicks = 0;
        if (!recovering) clear();
    }

    /** Called every tick by the recovery goal: true once the last of his own floor is back in his bag or left standing. */
    boolean tickCleanup() {
        if (!(owner.level() instanceof ServerLevel level)) return true;
        if (!recovering) return true;
        owner.getNavigation().stop();
        if (++cleanupTicks > CLEANUP_TICKS && owner.onGround()) {
            failureReason = "Мост оставил на месте: не успел разобрать";
            clear();
            return true;
        }
        if (placed.isEmpty()) { clear(); return true; }
        if (cooldown > 0) { cooldown--; return false; }
        BlockPos bank = plan == null ? null : plan.start();
        double mine = bank == null ? 0 : owner.distanceToSqr(bank.getCenter());
        // The farthest of his own cells behind him that he can reach: breaking it never takes the floor from under his feet.
        BlockPos pick = null;
        double far = -1;
        for (BlockPos cell : placed) {
            if (cell.above().equals(owner.blockPosition())) continue;
            if (owner.distanceToSqr(cell.getCenter()) > 16.0D) continue;
            double distance = bank == null ? 0 : cell.distSqr(bank);
            if (distance <= mine + .01D) continue;
            if (distance > far) { far = distance; pick = cell; }
        }
        if (pick != null) {
            if (!breakCell(level, pick)) return false;
            placed.remove(pick);
            cooldown = BREAK_INTERVAL;
            return false;
        }
        // Nothing behind him in reach: back to the bank, or out to the nearest cell still standing.
        if (bank != null && !at(bank)) {
            owner.workStatus("Возвращаюсь по мосту.");
            walkTo(bank);
            return false;
        }
        BlockPos nearest = placed.stream().min(java.util.Comparator.comparingDouble(cell -> owner.distanceToSqr(cell.getCenter()))).orElse(null);
        if (nearest != null && !owner.blockPosition().equals(nearest.above())) walkTo(nearest.above());
        return false;
    }

    private boolean inReach(ServerLevel level, BlockPos block) {
        return owner.distanceToSqr(block.getCenter()) <= 16.0D
                && CompanionMiningNavigation.visible(owner, owner.getEyePosition(), block);
    }

    private boolean at(BlockPos cell) {
        return owner.blockPosition().equals(cell) && Math.abs(owner.getY() - cell.getY()) < .5D;
    }

    /** A walk to the bank or to the far cell: false when there is no way (or no progress). */
    private boolean walkTo(BlockPos cell) {
        CompanionRoute route = owner.work.route;
        if (path != null && !route.legOver()) return true;
        if (path != null && route.done() && path.getEndNode() != null && path.getEndNode().asBlockPos().equals(cell)) {
            owner.getMoveControl().setWantedPosition(cell.getX() + .5D, cell.getY(), cell.getZ() + .5D, .7);
            if (owner.distanceToSqr(cell.getX() + .5D, cell.getY(), cell.getZ() + .5D) < 1) return true;
        }
        if (++legs > LEGS) return false;
        CompanionRoute.Reach reach = route.cells(Set.of(cell), 0);
        if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; return ++waited < WAIT; }
        if (!reach.found()) return false;
        waited = 0;
        path = reach.path();
        route.walk(path);
        return true;
    }

    /** One cell of floor of what he carries: a block item of the bag set down, with its sound and his hand. */
    private boolean lay(ServerLevel level, BlockPos cell) {
        ItemStack material = CompanionReach.bridgeMaterial(owner);
        if (material.isEmpty()) return false;
        if (!owner.getMainHandItem().is(material.getItem())) {
            String id = BuiltInRegistries.ITEM.getKey(material.getItem()).toString();
            CompanionActionResult equipped = owner.equipForSupport(id);
            if (!equipped.success() || !owner.getMainHandItem().is(material.getItem())) return false;
        }
        ItemStack hand = owner.getMainHandItem();
        BlockState state = ((BlockItem) hand.getItem()).getBlock().defaultBlockState();
        if (!state.canSurvive(level, cell) || !level.setBlockAndUpdate(cell, state)) return false;
        owner.rememberPlaced(cell);
        placed.add(cell.immutable());
        owner.animateAction(1, cell, hand);
        hand.shrink(1);
        if (hand.isEmpty()) owner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        owner.setChanged();
        owner.swing(InteractionHand.MAIN_HAND);
        var sound = state.getSoundType(level, cell, owner);
        level.playSound(null, cell, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) * 0.5F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, cell, GameEvent.Context.of(owner, state));
        return true;
    }

    /** One of his own cells taken back, its drop into the world for the pickup; false while the tool cannot take it. */
    private boolean breakCell(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        owner.takeToolForBlock(state);
        if (state.requiresCorrectToolForDrops() && !owner.getMainHandItem().isCorrectToolForDrops(state)) { owner.putToolAway(); return false; }
        ItemStack tool = owner.getMainHandItem();
        var drops = CompanionTools.drops(state, level, pos, null, owner, tool);
        if (!level.destroyBlock(pos, false, owner, 512)) { owner.putToolAway(); return false; }
        CompanionTools.wearOnBreak(tool, level, state, pos, owner);
        owner.swing(InteractionHand.MAIN_HAND);
        owner.dropHarvest(pos, drops);
        owner.putToolAway();
        return true;
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "bridge_failed", "entity", owner.getUUID(),
                "target", target, "why", reason, "placed", placed.size(), "cells", plan == null ? 0 : plan.length());
        requestCleanup();
        return Outcome.FAILED;
    }

    private void clear() {
        breaking.reset();
        plan = null;
        target = null;
        placed.clear();
        path = null;
        legs = waited = cooldown = ticks = cleanupTicks = stalledTicks = 0;
        best = 0;
        recovering = false;
    }

    /** What the bridge holds, for the journal of a save and a reload. */
    void save(net.minecraft.nbt.CompoundTag tag) {
        if (plan == null) return;
        tag.putLong("BridgeStart", plan.start().asLong());
        tag.putLong("BridgeEnd", plan.end().asLong());
        net.minecraft.nbt.ListTag cells = new net.minecraft.nbt.ListTag();
        for (BlockPos cell : plan.cells()) cells.add(net.minecraft.nbt.LongTag.valueOf(cell.asLong()));
        tag.put("BridgeCells", cells);
        net.minecraft.nbt.ListTag laid = new net.minecraft.nbt.ListTag();
        for (BlockPos cell : placed) laid.add(net.minecraft.nbt.LongTag.valueOf(cell.asLong()));
        tag.put("BridgePlaced", laid);
    }

    void load(net.minecraft.nbt.CompoundTag tag) {
        clear();
        if (!tag.contains("BridgeStart", net.minecraft.nbt.Tag.TAG_LONG)) return;
        BlockPos start = BlockPos.of(tag.getLong("BridgeStart"));
        BlockPos end = BlockPos.of(tag.getLong("BridgeEnd"));
        List<BlockPos> cells = new ArrayList<>();
        var saved = tag.getList("BridgeCells", net.minecraft.nbt.Tag.TAG_LONG);
        for (int index = 0; index < saved.size(); index++) cells.add(BlockPos.of(((net.minecraft.nbt.LongTag) saved.get(index)).getAsLong()));
        if (cells.isEmpty()) return;
        plan = new CompanionReach.BridgePlan(start, end, List.copyOf(cells));
        var laid = tag.getList("BridgePlaced", net.minecraft.nbt.Tag.TAG_LONG);
        for (int index = 0; index < laid.size(); index++) placed.add(BlockPos.of(((net.minecraft.nbt.LongTag) laid.get(index)).getAsLong()));
        requestCleanup();
    }
}

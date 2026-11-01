package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class CompanionPlacement {
    final CompanionEntity c;

    CompanionPlacement(CompanionEntity c) { this.c = c; }

    /** The recipe needs a bench and one is a few steps away: walk there first, like a player (any recipe, not only the known chains). */
    public boolean benchWalkNeeded(String requestedItemId, int requestedCount) {
        if (!(c.level() instanceof ServerLevel serverLevel) || hasCraftingTableNearby() || !c.hasKnownCraftingTableWithin(CompanionToolSense.BENCH_WALK)) return false;
        var atBench = CompanionCrafting.plan(serverLevel, c.companionInventory, requestedItemId, requestedCount, true);
        if (!atBench.success()) return false;
        // Possible at a bench, and here it either needs one set down or is impossible without one.
        var here = CompanionCrafting.plan(serverLevel, c.companionInventory, requestedItemId, requestedCount, false);
        return here.placeTable() || !here.success();
    }

    public CompanionActionResult craftItemResult(String requestedItemId, int requestedCount) {
        if (c.hasActiveWorkOrder()) return CompanionActionResult.failed("Сначала нужно завершить или отменить текущую добычу.");
        return craftFromBag(requestedItemId, requestedCount);
    }

    CompanionActionResult craftFromBag(String requestedItemId, int requestedCount) {
        if (!(c.level() instanceof ServerLevel serverLevel)) return CompanionActionResult.failed("Крафт доступен только в загруженном мире.");
        CompanionCrafting.Plan plan = CompanionCrafting.plan(serverLevel, c.companionInventory, requestedItemId,
                requestedCount, hasCraftingTableNearby());
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "craft_decision", "entity", c.getUUID(),
                "item", requestedItemId, "count", requestedCount, "success", plan.success(), "placeTable", plan.placeTable(), "message", plan.message());
        if (!plan.success()) return recordActionResult(plan.shortage() ? CompanionActionResult.shortage(plan.message()) : CompanionActionResult.failed(plan.message()));
        // A remembered bench far away is not worth the walk: a player would set down another one from own wood.
        if (plan.placeTable() && !c.newBenchAllowed && c.hasKnownCraftingTableWithin(CompanionToolSense.BENCH_WALK))
            return recordActionResult(CompanionActionResult.failed("Верстак рядом, но я сейчас не у него. Попроси ещё раз — дойду до него и сделаю."));
        // With a bench already at the base, a bench set down out here is a travelling one: taken along after use.
        boolean fieldBench = plan.placeTable() && c.hasKnownCraftingTable();
        BlockPos tablePosition = plan.placeTable() ? findPlacementPosition(serverLevel, Blocks.CRAFTING_TABLE, c.blockPosition(), pos -> true) : null;
        if (plan.placeTable() && (tablePosition == null
                || !serverLevel.setBlock(tablePosition, Blocks.CRAFTING_TABLE.defaultBlockState(), 3))) {
            return recordActionResult(CompanionActionResult.failed("Не нашёл свободное безопасное место для верстака. Материалы сохранены."));
        }
        if (tablePosition != null) c.rememberPlaced(tablePosition);
        if (tablePosition != null && fieldBench) c.fieldBenches.add(tablePosition.immutable());
        for (int slot = 0; slot < c.companionInventory.size(); slot++) c.companionInventory.set(slot, plan.inventory().get(slot).copy());
        if (tablePosition != null) dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "block_placed",
                "entity", c.getUUID(), "block", "minecraft:crafting_table", "position", tablePosition, "reason", "craft_prerequisite");
        c.refreshInventorySummary();
        c.animateAction(tablePosition == null ? 2 : 1, tablePosition == null ? c.blockPosition() : tablePosition,
                tablePosition == null ? new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(requestedItemId))) : new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE));
        if (tablePosition != null) {
            var table = Blocks.CRAFTING_TABLE.defaultBlockState(); var sound = table.getSoundType();
            serverLevel.playSound(null, tablePosition, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, .7F, 1F);
        }
        c.madeAt.put(requestedItemId, c.level().getGameTime());
        return recordActionResult(CompanionActionResult.ok((plan.placeTable() ? "Сделал и поставил верстак. " : "")
                + plan.message() + " В сумке: " + c.getStoredItemsSummary() + "."));
    }

    public CompanionActionResult placeBlockResult(String requestedBlockId) {
        return placeBlockNearResult(requestedBlockId, c.blockPosition());
    }

    CompanionActionResult placeBlockNearResult(String requestedBlockId, BlockPos origin) {
        return placeBlockNearResult(requestedBlockId, origin, pos -> true);
    }

    CompanionActionResult placeBlockNearResult(String requestedBlockId, BlockPos origin, java.util.function.Predicate<BlockPos> allowed) {
        if (!(c.level() instanceof ServerLevel serverLevel)) return CompanionActionResult.failed("Не могу поставить блок без загруженного мира.");
        ResourceLocation blockId = ResourceLocation.tryParse(requestedBlockId.trim());
        if (blockId == null) return recordActionResult(CompanionActionResult.failed("Не понял ID блока."));
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null || block == Blocks.AIR) return recordActionResult(CompanionActionResult.failed("Неизвестный или пустой блок: " + blockId + "."));
        ItemStack item = findBlockItem(block);
        if (item == null) return recordActionResult(CompanionActionResult.failed("У меня в сумке нет блока " + blockId + "."));
        BlockPos position = findPlacementPosition(serverLevel, block, origin, allowed);
        if (position == null || !serverLevel.setBlock(position, block.defaultBlockState(), 3)) {
            return recordActionResult(CompanionActionResult.failed("Не нашёл безопасное место для " + blockId + " рядом со мной."));
        }
        c.animateAction(1, position, item);
        c.lastPlacedPosition = position.immutable();
        c.rememberPlaced(position);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "block_placed", "entity", c.getUUID(),
                "block", blockId, "position", position, "reason", "place_block");
        item.shrink(1);
        c.refreshInventorySummary();
        var placedSound = block.defaultBlockState().getSoundType();
        serverLevel.playSound(null, position, placedSound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, .7F, 1F);
        return recordActionResult(CompanionActionResult.ok("Поставил " + blockId + ". В сумке: " + c.getStoredItemsSummary() + "."));
    }

    /** Whether a block of this kind could be set down here now; nothing is placed and nothing goes into the journal. */
    boolean canPlaceNear(String requestedBlockId, BlockPos origin, java.util.function.Predicate<BlockPos> allowed) {
        return c.level() instanceof ServerLevel level && findPlacementPosition(level, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(requestedBlockId)), origin, allowed) != null;
    }

    public CompanionActionResult recordActionResult(CompanionActionResult result) {
        c.lastTaskSucceeded = result.success();
        c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, result.message());
        if (result.status() != CompanionActionResult.Status.ACCEPTED) c.noteDeed(result.message(), result.success());
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "engine_result", "entity", c.getUUID(),
                "success", result.success(), "status", result.status(), "message", result.message());
        return result;
    }

    public boolean hasCraftingTableNearby() {
        BlockPos origin = c.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -2, -4), origin.offset(4, 2, 4))) {
            if (c.level().hasChunkAt(pos) && c.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                    && c.distanceToSqr(pos.getCenter()) <= 3.2 * 3.2) {
                // Walking targets are chosen by the view from a cell's centre. Standing a few hundredths off it
                // must not turn "I am at the bench" into "cannot reach the bench" (game log 22.10, a step beside the bench).
                Vec3 centredEye = Vec3.atBottomCenterOf(c.blockPosition()).add(0, c.getEyeHeight(), 0);
                if (seesBlock(c.getEyePosition(), pos) || c.onGround() && seesBlock(centredEye, pos)) {
                    if (!c.level().isClientSide) c.rememberObserved(pos);
                    return true;
                }
            }
        }
        return false;
    }
    private boolean seesBlock(Vec3 eye, BlockPos pos) { return seesBlock(c, eye, pos); }

    /** Like a player: a bench or chest is usable when any face turned to the eye can be looked at, not only its exact centre. */
    static boolean seesBlock(net.minecraft.world.entity.Entity viewer, Vec3 eye, BlockPos pos) {
        Vec3 center = pos.getCenter();
        Vec3[] points = {center, center.add(0, .45, 0), center.add(Math.signum(eye.x - center.x) * .45, 0, 0),
                center.add(0, 0, Math.signum(eye.z - center.z) * .45), center.add(0, -.45, 0)};
        for (Vec3 point : points) {
            var hit = viewer.level().clip(new net.minecraft.world.level.ClipContext(eye, point,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, viewer));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return true;
        }
        return false;
    }
    private ItemStack findBlockItem(Block block) {
        for (ItemStack stack : c.companionInventory) {
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block) return stack;
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack stack = c.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block) return stack;
        }
        return null;
    }

    private BlockPos findPlacementPosition(ServerLevel serverLevel, Block block, BlockPos origin, java.util.function.Predicate<BlockPos> allowed) {
        BlockState state = block.defaultBlockState();
        int returnHeight = CompanionMiningNavigation.provenReturnHeight(c);
        // Where he stands and a block up or down first; only then as high or as low as a player reaches (marathon 23.10, «долина»:
        // in a crack of the ravine the bench took the one cell beside him, the furnace found no room, though the top of the bench
        // was right in front of his face).
        for (int dy : new int[]{0, -1, 1, 2, -2}) for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos pos = origin.offset(dx, dy, dz);
                if (!allowed.test(pos)) continue;
                // Not on the cell his own stairs begin from or on a step of them: a bench there shut his way back up (marathon, 15 s on the stairs).
                // A step dug already has left quarrySoil, but the block it stands on is still in quarrySupports.
                if (pos.equals(c.work.exitCell) || c.work.quarrySoil.contains(pos) || c.work.quarrySupports.contains(pos.below())) continue;
                if (!serverLevel.hasChunkAt(pos) || !serverLevel.getBlockState(pos).canBeReplaced()) continue;
                if (!serverLevel.getFluidState(pos).isEmpty() || !serverLevel.getWorldBorder().isWithinBounds(pos)) continue;
                if (!serverLevel.getBlockState(pos.below()).isFaceSturdy(serverLevel, pos.below(), Direction.UP)) continue;
                if (!state.canSurvive(serverLevel, pos)) continue;
                if (c.distanceToSqr(pos.getCenter()) > (Math.abs(dy) == 2 ? 3.2 * 3.2 : 16)) continue;
                var hit = serverLevel.clip(new net.minecraft.world.level.ClipContext(c.getEyePosition(), pos.getCenter(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, c));
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && !hit.getBlockPos().equals(pos)) continue;
                if (block instanceof net.minecraft.world.level.block.ChestBlock) {
                    if (!serverLevel.getBlockState(pos.above()).getCollisionShape(serverLevel, pos.above()).isEmpty()) continue;
                    boolean adjacentChest = false;
                    for (Direction direction : Direction.Plane.HORIZONTAL) if (serverLevel.getBlockState(pos.relative(direction)).getBlock() instanceof net.minecraft.world.level.block.ChestBlock) adjacentChest = true;
                    if (adjacentChest) continue;
                }
                AABB collision = new AABB(pos);
                if (c.getBoundingBox().intersects(collision) || !serverLevel.getEntities(c, collision).isEmpty()) continue;
                if (!CompanionMiningNavigation.preservesReturnOnPlacement(c, pos, returnHeight)) continue;
                return pos.immutable();
            }
        }
        return null;
    }
}

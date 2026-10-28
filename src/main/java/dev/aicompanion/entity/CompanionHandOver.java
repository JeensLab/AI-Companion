package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class CompanionHandOver {
    final CompanionWork w;
    /** Where a walk in legs gave up without a single leg, and in which order: a new walk is tried only from elsewhere. */
    private BlockPos legsFailedAt;
    private long legsTask = -1;
    /** The walk in legs went at least one leg in this order since it was last reset. */
    private boolean legsWalked;

    CompanionHandOver(CompanionWork w) { this.w = w; }

    private boolean giftMatches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (w.owner.goals().ownsWork(w.taskId) && !CompanionGoals.usable(stack)) return false;
        if ("logs".equals(w.requestedItem)) return stack.is(ItemTags.LOGS);
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(w.requestedItem);
    }

    /** Walk up to the player and toss the items at their feet: a real item on the ground, picked up by the player. */
    void handOver(ServerLevel level) {
        var player = w.owner.getOwnerPlayer();
        if (player == null) { w.finish(false, "Не вижу тебя рядом, отдать не могу."); return; }
        int carried = 0;
        for (int slot = 0; slot < w.owner.getContainerSize(); slot++) if (giftMatches(w.owner.getItem(slot))) carried += w.owner.getItem(slot).getCount();
        if (giftMatches(w.owner.getMainHandItem())) carried += w.owner.getMainHandItem().getCount();
        if (carried == 0) { w.finish(false, "У меня нет того, что нужно отдать."); return; }
        if (legsTask != w.taskId) { legsTask = w.taskId; legsFailedAt = null; legsWalked = false; }
        w.owner.movementIntent(player.blockPosition());
        w.owner.getLookControl().setLookAt(player, 30, 30);
        if (w.owner.distanceToSqr(player) > 3.2 * 3.2 || !w.owner.hasLineOfSight(player)) {
            boolean walking = w.owner.travel().goal() != null;
            // In a jump the leg stays as it is.
            if (walking && !w.owner.onGround() && !w.owner.isInWater()) return;
            // In legs round walls and ridges, as far as the way goes, like «иди ко мне» (CompanionWork.returnToPlayer): a short
            // search would give up while the way round is longer than it walks.
            boolean elsewhere = legsFailedAt == null || !legsFailedAt.closerThan(w.owner.blockPosition(), 3);
            if ((walking || elsewhere) && (w.owner.onGround() || w.owner.isInWater())) {
                var step = w.route.follow(player.blockPosition(), 3);
                if (step == CompanionTravel.Step.WALKING) { legsWalked = true; w.orderProgressPosition = null; w.owner.workStatus("Несу тебе вещи."); return; }
                w.owner.travel().reset();
                w.owner.movementIntent(player.blockPosition());
                boolean walked = legsWalked;
                legsWalked = false;
                // Legs that went and then stood half a minute without coming closer: the end (the player is out of reach, like behind
                // a closed wall).
                if (step == CompanionTravel.Step.FAILED && walked) {
                    w.finish(false, "Не смог к тебе подойти: полминуты не мог подойти ближе, остановился на " + w.owner.blockPosition().toShortString() + ". Вещи остались у меня.");
                    return;
                }
                // No leg at all from here (down a mine, game 28.10): the self-rescue takes the walk over while the work waits (the
                // hand-over does not give up at once without a leg); new legs only from another spot.
                if (step == CompanionTravel.Step.FAILED) legsFailedAt = w.owner.blockPosition();
            }
            if (w.orderHasStalled(level, 240)) { w.finish(false, "Не смог к тебе подойти, вещи остались у меня."); return; }
            w.owner.workStatus("Несу тебе вещи.");
            return;
        }
        if (w.owner.travel().goal() != null) w.owner.travel().reset();
        w.owner.getNavigation().stop();
        int left = Math.min(w.requestedCount, carried), given = 0;
        ItemStack shown = ItemStack.EMPTY;
        // Give from the bag first; the tool in his own hand goes only if nothing else is left.
        for (int index = 0; index <= w.owner.getContainerSize() && left > 0; index++) {
            int slot = index == w.owner.getContainerSize() ? -1 : index;
            ItemStack stack = slot < 0 ? w.owner.getMainHandItem() : w.owner.getItem(slot);
            if (!giftMatches(stack)) continue;
            ItemStack gift = stack.split(Math.min(left, stack.getCount()));
            if (slot < 0 && stack.isEmpty()) w.owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            var operation = w.owner.goals().prepareGift(w.taskId, gift);
            shown = gift.copyWithCount(1);
            var eye = w.owner.getEyePosition();
            var toward = player.position().add(0, .3, 0).subtract(eye).normalize().scale(.3);
            var entity = new net.minecraft.world.entity.item.ItemEntity(level, eye.x, eye.y - .3, eye.z, gift);
            entity.setDeltaMovement(toward.x, .2, toward.z);
            entity.setPickUpDelay(10);
            entity.setTarget(player.getUUID()); // only the player can pick it up
            entity.getPersistentData().putBoolean("aicompanion:gift", true);
            if (!level.addFreshEntity(entity)) {
                // Spawn refusal must not consume the real item or acknowledge delivery.
                stack.grow(gift.getCount());
                if (slot < 0) w.owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack);
                w.finish(false, "Не удалось бросить предмет. Он остался у меня."); return;
            }
            left -= gift.getCount(); given += gift.getCount();
            w.owner.goals().giftSpawned(operation, entity.getUUID());
        }
        w.owner.setChanged();
        w.owner.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        w.owner.animateAction(3, player.blockPosition(), shown);
        level.playSound(null, w.owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, .4F, .8F);
        w.finish(CompanionActionResult.ok("Бросил тебе " + given + " «" + shown.getHoverName().getString() + "» — подбери у ног.")
                .withEvidence(new CompanionActionResult.Evidence(w.taskId, "give_items", w.requestedItem, given, player.getUUID(),
                        w.owner.goals().ownsWork(w.taskId) ? w.owner.goals().operation() : null, w.owner.goals().ownsWork(w.taskId) ? w.owner.goals().giftEntity() : null)));
    }

    void packBench(ServerLevel level) {
        if (w.packTarget == null) {
            boolean pickedUp = w.items.countCarried(Items.CRAFTING_TABLE) > w.packedBenchBefore;
            String note = pickedUp ? " Походный верстак забрал с собой."
                    : " Разобранный верстак пока не подобрал" + (w.packedBenchFrom == null ? "." : " на " + w.packedBenchFrom.toShortString() + ".");
            w.finish(new CompanionActionResult(w.packResult.success(), w.packResult.message() + note, w.packResult.status(), w.packResult.evidence())); return;
        }
        if (!level.hasChunkAt(w.packTarget) || !level.getBlockState(w.packTarget).is(Blocks.CRAFTING_TABLE)) { w.packTarget = null; w.finish(w.packResult); return; }
        if (!w.owner.onGround()) return;
        String left = " Походный верстак оставил на " + w.packTarget.toShortString() + ".";
        if (w.orderHasStalled(level, 300)) { BlockPos at = w.packTarget; w.packTarget = null;
            w.finish(new CompanionActionResult(w.packResult.success(), w.packResult.message() + " Походный верстак оставил на " + at.toShortString() + ".", w.packResult.status(), w.packResult.evidence())); return; }
        w.owner.movementIntent(w.packTarget);
        w.owner.getLookControl().setLookAt(w.packTarget.getX() + .5D, w.packTarget.getY() + .5D, w.packTarget.getZ() + .5D);
        int reach = w.items.reachBlock(level, w.packTarget, true);
        if (reach < 0) { w.packTarget = null; w.finish(new CompanionActionResult(w.packResult.success(), w.packResult.message() + left, w.packResult.status(), w.packResult.evidence())); return; }
        if (reach == 0) return;
        if (!CompanionMiningNavigation.preservesAutonomousReturn(w.owner, w.packTarget)) {
            w.packTarget = null; w.finish(new CompanionActionResult(w.packResult.success(), w.packResult.message() + left + " Он нужен для обратного хода.", w.packResult.status(), w.packResult.evidence())); return;
        }
        w.owner.getNavigation().stop();
        BlockState state = level.getBlockState(w.packTarget);
        w.owner.selectToolForBlock(state);
        w.owner.workStatus("Забираю свой походный верстак.");
        if (!w.breaking.tick(level, w.packTarget)) return;
        ItemStack tool = w.owner.getMainHandItem();
        var drops = Block.getDrops(state, level, w.packTarget, level.getBlockEntity(w.packTarget), w.owner, tool);
        if (!w.items.canStore(drops)) { w.packTarget = null;
            w.finish(new CompanionActionResult(w.packResult.success(), w.packResult.message() + left + " В сумке нет места.", w.packResult.status(), w.packResult.evidence())); return; }
        if (!level.destroyBlock(w.packTarget, false, w.owner, 512)) return;
        tool.getItem().mineBlock(tool, level, state, w.packTarget, w.owner);
        w.owner.dropHarvest(w.packTarget, drops);
        w.breaking.reset();
        w.packedBenchFrom = w.packTarget;
        w.packTarget = null; // finished on the next tick, once the dropped bench has been picked up
    }
}

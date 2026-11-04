package dev.aicompanion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What fell out of the dead player, picked up so it does not despawn. Kept apart from his bag, so nothing of it is eaten, worn,
 * spent or stored, and nothing is marked on the items: once the player is beside him again it is tossed at the player's feet
 * as a gift only the player can take. Saved with him and dropped where he dies, like his own things.
 */
public final class CompanionPlayersThings {
    /** On a drop of the dead player (LivingDropsEvent): his to keep for the player, not loot of Oleg's. */
    static final String MARK = "aicompanion:players";
    /** A player's inventory and armour, with room for stacks the merge left split. */
    private static final int MAX_STACKS = 64;
    private static final double HAND_REACH = 3.2;

    private final CompanionEntity c;
    private final List<ItemStack> held = new ArrayList<>();
    /** What of the player's he is carrying, for the price of a walk back to it. */
    List<ItemStack> held() { return List.copyOf(held); }

    CompanionPlayersThings(CompanionEntity c) { this.c = c; }

    /** Takes what fits of the stack; the rest stays in it, on the floor. */
    public void keep(ItemStack stack) {
        if (held.isEmpty()) c.autonomySay("Подобрал твои вещи, отдам, когда подойдёшь.", false);
        for (ItemStack kept : held) {
            if (!ItemStack.isSameItemSameComponents(kept, stack)) continue;
            int move = Math.min(stack.getCount(), kept.getMaxStackSize() - kept.getCount());
            kept.grow(move); stack.shrink(move);
            if (stack.isEmpty()) return;
        }
        if (held.size() < MAX_STACKS) { held.add(stack.copy()); stack.setCount(0); }
    }

    int count(net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack kept : held) if (kept.is(item)) total += kept.getCount();
        return total;
    }

    /** The player alive and within reach and sight: everything goes back at once, aimed at the player. */
    void tick(ServerLevel level) {
        if (held.isEmpty() || c.tickCount % 10 != 0) return;
        ServerPlayer player = c.getOwnerPlayer();
        if (player == null || !player.isAlive() || player.isSpectator() || player.level() != level
                || c.distanceToSqr(player) > HAND_REACH * HAND_REACH || !c.hasLineOfSight(player)) return;
        var eye = c.getEyePosition();
        var toward = player.position().add(0, .3, 0).subtract(eye).normalize().scale(.3);
        for (var it = held.iterator(); it.hasNext(); ) {
            var entity = new ItemEntity(level, eye.x, eye.y - .3, eye.z, it.next().copy());
            entity.setDeltaMovement(toward.x, .2, toward.z);
            entity.setPickUpDelay(10);
            entity.setTarget(player.getUUID()); // only the player can pick it up
            if (level.addFreshEntity(entity)) it.remove();
        }
        c.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.playSound(null, c.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, .4F, .8F);
        if (held.isEmpty()) c.autonomySay("Вот твои вещи, подбери у ног.", false);
    }

    /** He died: the player's things fall with his own, still marked as the player's, so he takes them up for the player again. */
    java.util.List<ItemEntity> drop(UUID spot) {
        java.util.List<ItemEntity> fallen = new java.util.ArrayList<>();
        for (ItemStack stack : held) {
            var item = c.spawnAtLocation(stack.copy(), 0.5F);
            if (item == null) continue;
            item.setUnlimitedLifetime();
            item.getPersistentData().putBoolean(MARK, true);
            if (spot != null) item.getPersistentData().putUUID(CompanionBag.BELONGINGS_SPOT, spot);
            fallen.add(item);
        }
        held.clear();
        return fallen;
    }

    /** A duplicate companion merged into this one hands its kept things over too. */
    void takeFrom(CompanionPlayersThings other) {
        for (ItemStack stack : other.held) {
            ItemStack rest = stack.copy();
            keep(rest);
            if (!rest.isEmpty() && c.level() instanceof ServerLevel level) net.minecraft.world.level.block.Block.popResource(level, c.blockPosition(), rest);
        }
        other.held.clear();
    }

    void save(CompoundTag tag) {
        if (held.isEmpty()) return;
        ListTag list = new ListTag();
        for (ItemStack stack : held) list.add(stack.save(c.level().registryAccess()));
        tag.put("PlayersThings", list);
    }

    /** Absent in older saves: nothing kept. */
    void load(CompoundTag tag) {
        held.clear();
        ListTag list = tag.getList("PlayersThings", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_STACKS, list.size()); i++)
            ItemStack.parse(c.level().registryAccess(), list.getCompound(i)).ifPresent(held::add);
    }
}

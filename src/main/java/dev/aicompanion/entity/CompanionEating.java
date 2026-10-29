package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class CompanionEating {
    final CompanionEntity c;

    CompanionEating(CompanionEntity c) { this.c = c; }

    void tickEating() {
        if (!(c.level() instanceof ServerLevel serverLevel)) return;
        if (c.eatTicks > 0) {
            c.eatTicks--;
            if (c.eatTicks % 4 == 0) {
                c.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, .5F, .9F + c.getRandom().nextFloat() * .2F);
                var mouth = c.getEyePosition().add(c.getLookAngle().scale(.3)).add(0, -.15, 0);
                serverLevel.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, c.eating),
                        mouth.x, mouth.y, mouth.z, 4, .08, .08, .08, .04);
            }
            if (c.eatTicks == 0) finishEating();
            return;
        }
        if (c.inventoryViewers > 0 || !c.isAlive()) return;
        float missing = c.getMaxHealth() - c.getHealth();
        // Fed, out of a fight for ten seconds: slowly better, as a player with a full stomach.
        if (missing >= 1 && c.satiety > 0 && c.tickCount - c.lastHurtTick > 200 && c.tickCount % 80 == 0) { c.heal(1); c.satiety--; }
        if (c.tickCount % 20 != 0 || missing < 4 && c.getHealth() > c.getMaxHealth() / 2) return;
        if (c.getTarget() != null && c.getTarget().isAlive() && c.distanceToSqr(c.getTarget()) < 16 && c.getHealth() > 6) return;
        ItemStack food = bestFood(missing);
        if (food.isEmpty()) return;
        c.eating = food.copyWithCount(1); c.eatTicks = CompanionEntity.EAT_TICKS;
        c.animateAction(4, c.blockPosition(), c.eating);
    }

    private void finishEating() {
        var properties = c.eating.get(net.minecraft.core.component.DataComponents.FOOD);
        for (ItemStack stack : c.companionInventory) if (ItemStack.isSameItemSameComponents(stack, c.eating)) {
            stack.shrink(1); c.setChanged();
            if (properties != null) { c.heal(properties.nutrition()); c.satiety = Math.min(40, c.satiety + Math.round(properties.saturation())); }
            c.playSound(net.minecraft.sounds.SoundEvents.PLAYER_BURP, .4F, 1F);
            dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "companion_ate", "entity", c.getUUID(),
                    "item", BuiltInRegistries.ITEM.getKey(c.eating.getItem()), "health", c.getHealth());
            c.observeEvent("eat", "Перекусил: " + c.eating.getHoverName().getString() + ", стало лучше.");
            break;
        }
        c.eating = ItemStack.EMPTY;
    }

    /**
     * His own food first. The player's food he carries from the chests only when he has none and is at half health or less: then a
     * friend eats what there is rather than die (Oleg, 29.10; in the game he ate it at 15 and died at night with it uneaten).
     */
    private ItemStack bestFood(float missing) {
        ItemStack own = bestFood(missing, false);
        return own.isEmpty() && c.getHealth() <= c.getMaxHealth() / 2 ? bestFood(missing, true) : own;
    }

    /** Safe food only (no rotten flesh, spider eyes, poison, chorus fruit); the one that best fits the missing health. */
    private ItemStack bestFood(float missing, boolean playersToo) {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        for (ItemStack stack : c.companionInventory) {
            var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
            if (stack.isEmpty() || food == null || !playersToo && c.carriedForPlayer(stack) || stack.is(net.minecraft.world.item.Items.CHORUS_FRUIT) || stack.is(net.minecraft.world.item.Items.SUSPICIOUS_STEW)) continue;
            if (food.effects().stream().anyMatch(effect -> !effect.effect().getEffect().value().isBeneficial())) continue;
            int score = Math.round(Math.min(missing, food.nutrition()) * 10 + food.saturation());
            if (score > bestScore) { bestScore = score; best = stack; }
        }
        return best;
    }
}

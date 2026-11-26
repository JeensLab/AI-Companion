package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-level ownership survives unloading either entity and the companion returning with a new UUID. */
final class CompanionPetBonds extends SavedData {
    private static final Factory<CompanionPetBonds> CATS = factory(CompanionPet.CAT);
    private static final Factory<CompanionPetBonds> DOGS = factory(CompanionPet.DOG);
    record Pet(UUID id, GlobalPos lastSeen, long fedAt) { }

    private final CompanionPet kind;
    private final Map<UUID, Pet> pets = new HashMap<>();

    private CompanionPetBonds(CompanionPet kind) { this.kind = kind; }

    private static Factory<CompanionPetBonds> factory(CompanionPet kind) {
        return new Factory<>(() -> new CompanionPetBonds(kind), (tag, registries) -> load(tag, kind), null);
    }

    static CompanionPetBonds get(MinecraftServer server, CompanionPet kind) {
        return server.overworld().getDataStorage().computeIfAbsent(kind == CompanionPet.CAT ? CATS : DOGS,
                "aicompanion_" + kind.id + "s");
    }

    private static String keeperKey(CompanionPet kind) { return "aicompanion:" + kind.id + "_keeper"; }

    Pet pet(CompanionEntity owner) {
        return pets.get(owner.getOwnerId());
    }

    GlobalPos lastSeen(MinecraftServer server, UUID keeper) {
        var pet = pets.get(keeper);
        if (pet == null) return null;
        var level = server.getLevel(pet.lastSeen().dimension());
        if (level != null && level.getEntity(pet.id()) instanceof TamableAnimal animal && animal.isAlive())
            return GlobalPos.of(level.dimension(), animal.blockPosition());
        return pet.lastSeen();
    }

    boolean adopt(ServerLevel level, CompanionEntity owner, TamableAnimal animal) {
        UUID keeper = owner.getOwnerId();
        if (keeper == null || pets.containsKey(keeper) || !kind.isStray(animal)) return false;
        animal.getPersistentData().putUUID(keeperKey(kind), keeper);
        animal.setTarget(null);
        animal.setOwnerUUID(owner.getUUID());
        animal.setTame(true, true);
        animal.setPersistenceRequired();
        animal.setOrderedToSit(false);
        level.broadcastEntityEvent(animal, (byte) 7);
        pets.put(keeper, snapshot(level, animal, level.getServer().overworld().getGameTime()));
        setDirty();
        install(level, animal, keeper);
        return true;
    }

    private static Pet snapshot(ServerLevel level, TamableAnimal animal, long fedAt) {
        return new Pet(animal.getUUID(), GlobalPos.of(level.dimension(), animal.blockPosition()), fedAt);
    }

    private void observe(ServerLevel level, TamableAnimal animal, UUID keeper) {
        Pet old = pets.get(keeper);
        if (old == null || !old.id().equals(animal.getUUID())) return;
        Pet next = snapshot(level, animal, old.fedAt());
        if (!next.equals(old)) {
            pets.put(keeper, next);
            setDirty();
        }
    }

    static boolean belongsTo(CompanionEntity owner, Entity entity) {
        if (owner.getOwnerId() == null || !(entity instanceof TamableAnimal animal)) return false;
        for (var kind : CompanionPet.values()) {
            String key = keeperKey(kind);
            if (kind.animal.isInstance(animal) && animal.getPersistentData().hasUUID(key))
                return owner.getOwnerId().equals(animal.getPersistentData().getUUID(key));
        }
        return false;
    }

    static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof TamableAnimal animal)) return;
        for (var kind : CompanionPet.values()) {
            String key = keeperKey(kind);
            if (kind.animal.isInstance(animal) && animal.getPersistentData().hasUUID(key))
                get(level.getServer(), kind).install(level, animal, animal.getPersistentData().getUUID(key));
        }
    }

    static void died(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof TamableAnimal animal) || !(animal.level() instanceof ServerLevel level)) return;
        for (var kind : CompanionPet.values()) {
            String key = keeperKey(kind);
            if (!kind.animal.isInstance(animal) || !animal.getPersistentData().hasUUID(key)) continue;
            var bonds = get(level.getServer(), kind);
            UUID keeper = animal.getPersistentData().getUUID(key);
            Pet pet = bonds.pets.get(keeper);
            if (pet != null && pet.id().equals(animal.getUUID())) {
                bonds.pets.remove(keeper);
                bonds.setDirty();
            }
        }
    }

    private void install(ServerLevel level, TamableAnimal animal, UUID keeper) {
        Pet pet = pets.get(keeper);
        if (pet == null || !pet.id().equals(animal.getUUID())) return;
        animal.goalSelector.removeAllGoals(goal -> goal instanceof SitWhenOrderedToGoal || goal instanceof StayWithCompanion);
        animal.goalSelector.addGoal(2, new StayWithCompanion(animal, keeper));
        if (animal instanceof Wolf wolf) {
            wolf.targetSelector.removeAllGoals(goal -> goal instanceof OwnerHurtByTargetGoal || goal instanceof DefendCompanion);
            wolf.targetSelector.addGoal(1, new DefendCompanion(wolf, keeper));
        }
        refreshOwner(level, animal, keeper);
    }

    private CompanionEntity refreshOwner(ServerLevel level, TamableAnimal animal, UUID keeper) {
        observe(level, animal, keeper);
        CompanionEntity companion = CompanionEvents.loadedCompanion(level, keeper, animal.getOwnerUUID());
        if (companion != null && !companion.getUUID().equals(animal.getOwnerUUID())) animal.setOwnerUUID(companion.getUUID());
        return companion;
    }

    private final class StayWithCompanion extends Goal {
        private final TamableAnimal animal;
        private final UUID keeper;
        private CompanionEntity companion;
        private int nextCheck;

        StayWithCompanion(TamableAnimal animal, UUID keeper) {
            this.animal = animal;
            this.keeper = keeper;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        private void refresh() {
            if (animal.tickCount < nextCheck) return;
            nextCheck = animal.tickCount + 10;
            companion = animal.level() instanceof ServerLevel level ? refreshOwner(level, animal, keeper) : null;
        }

        @Override public boolean canUse() {
            refresh();
            if (animal.isOrderedToSit()) return animal.onGround() && !animal.isInWaterOrBubble();
            return canFollow() && animal.distanceToSqr(companion) > 16;
        }

        @Override public boolean canContinueToUse() {
            if (animal.isOrderedToSit()) return animal.onGround() && !animal.isInWaterOrBubble();
            return canFollow() && animal.distanceToSqr(companion) > 4;
        }

        private boolean canFollow() {
            return companion != null && companion.isAlive() && !companion.isRemoved() && companion.level() == animal.level()
                    && !animal.isPassenger() && !animal.isLeashed() && (!(animal instanceof Wolf) || animal.getTarget() == null);
        }

        @Override public void start() { animal.getNavigation().stop(); }

        @Override public void tick() {
            boolean update = animal.tickCount >= nextCheck;
            refresh();
            animal.setInSittingPose(animal.isOrderedToSit());
            if (animal.isOrderedToSit() || !canFollow()) animal.getNavigation().stop();
            else if (update) animal.getNavigation().moveTo(companion, 1.1);
        }

        @Override public void stop() {
            animal.setInSittingPose(false);
            animal.getNavigation().stop();
        }
    }

    private static final class DefendCompanion extends TargetGoal {
        private final Wolf wolf;
        private final UUID keeper;
        private LivingEntity attacker;
        private int seenHurt, pendingHurt;

        DefendCompanion(Wolf wolf, UUID keeper) {
            super(wolf, false);
            this.wolf = wolf;
            this.keeper = keeper;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override public boolean canUse() {
            if (wolf.isOrderedToSit() || !(wolf.level() instanceof ServerLevel level)) return false;
            var companion = CompanionEvents.loadedCompanion(level, keeper, wolf.getOwnerUUID());
            if (companion == null || wolf.distanceToSqr(companion) > 16 * 16) return false;
            attacker = companion.getLastHurtByMob();
            pendingHurt = companion.getLastHurtByMobTimestamp();
            return pendingHurt != seenHurt && companion.tickCount - pendingHurt < 100 && attacker instanceof Enemy
                    && canAttack(attacker, TargetingConditions.DEFAULT) && wolf.wantsToAttack(attacker, companion);
        }

        @Override public boolean canContinueToUse() { return !wolf.isOrderedToSit() && super.canContinueToUse(); }

        @Override public void start() {
            wolf.setTarget(attacker);
            seenHurt = pendingHurt;
            super.start();
        }
    }

    private String animalKey() { return kind == CompanionPet.CAT ? "Cat" : "Dog"; }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        var list = new ListTag();
        pets.forEach((keeper, pet) -> {
            var tag = new CompoundTag();
            tag.putUUID("Keeper", keeper);
            tag.putUUID(animalKey(), pet.id());
            tag.putString("Dimension", pet.lastSeen().dimension().location().toString());
            tag.putLong("Pos", pet.lastSeen().pos().asLong());
            tag.putLong("FedAt", pet.fedAt());
            list.add(tag);
        });
        root.put("Pets", list);
        return root;
    }

    private static CompanionPetBonds load(CompoundTag root, CompanionPet kind) {
        var bonds = new CompanionPetBonds(kind);
        String animalKey = bonds.animalKey();
        var list = root.getList("Pets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            var dimension = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimension == null || !tag.hasUUID("Keeper") || !tag.hasUUID(animalKey) || !tag.contains("Pos", Tag.TAG_LONG)) continue;
            var pos = GlobalPos.of(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension),
                    BlockPos.of(tag.getLong("Pos")));
            bonds.pets.put(tag.getUUID("Keeper"), new Pet(tag.getUUID(animalKey), pos, tag.getLong("FedAt")));
        }
        return bonds;
    }
}

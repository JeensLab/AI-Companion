package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;

import java.util.Optional;

final class CompanionEntityNbt {
    final CompanionEntity c;

    CompanionEntityNbt(CompanionEntity c) { this.c = c; }

    void save(CompoundTag tag) {
        if (c.ownerId != null) tag.putUUID("Owner", c.ownerId);
        tag.putBoolean("Following", c.following);
        tag.putBoolean("Protecting", c.protecting);
        tag.putBoolean("ChunkLoading", c.chunkLoading); tag.putBoolean("AutonomousSpeech", c.autonomousSpeech);
        tag.putString("PreferredWeapon", c.preferredWeapon);
        tag.putString("CompanionName", c.getName().getString());
        tag.putInt("SearchRadius", c.searchRadius);
        tag.putBoolean("Roaming", c.roaming);
        if (c.roamOrigin != null) tag.putLong("RoamOrigin", c.roamOrigin.asLong());
        tag.putBoolean("GatheringLogs", c.gatheringLogs);
        tag.putBoolean("GatheringDirt", c.gatheringDirt);
        tag.putBoolean("SupportSoilTask", c.supportSoilTask);
        tag.putInt("LogsRemaining", c.logsRemaining);
        tag.putInt("RequestedLogs", c.requestedLogs);
        tag.putString("LastTaskResult", c.getLastTaskResult());
        tag.putInt("Satiety", c.satiety);
        ContainerHelper.saveAllItems(tag, c.companionInventory, c.level().registryAccess());
        c.temporaryPillar.save(tag);
        c.work.save(tag);
        c.places.save(tag);
        c.deeds.save(tag);
        c.autonomy.save(tag);
        c.goals.save(tag);
        c.social.save(tag);
    }

    void load(CompoundTag tag) {
        c.chunkLoading = !tag.contains("ChunkLoading") || tag.getBoolean("ChunkLoading");
        c.autonomousSpeech = !tag.contains("AutonomousSpeech") || tag.getBoolean("AutonomousSpeech");
        c.preferredWeapon = tag.getString("PreferredWeapon");
        if (tag.contains("CompanionName")) c.setCompanionName(tag.getString("CompanionName"));
        c.setSearchRadius(tag.contains("SearchRadius") ? tag.getInt("SearchRadius") : 64);
        c.roaming = tag.getBoolean("Roaming");
        c.roamOrigin = tag.contains("RoamOrigin") ? BlockPos.of(tag.getLong("RoamOrigin")) : c.blockPosition();
        c.getEntityData().set(CompanionEntity.DATA_ROAMING, c.roaming);
        if (tag.hasUUID("Owner")) c.ownerId = tag.getUUID("Owner");
        c.getEntityData().set(CompanionEntity.DATA_OWNER, Optional.ofNullable(c.ownerId));
        c.following = !tag.contains("Following") || tag.getBoolean("Following");
        c.getEntityData().set(CompanionEntity.DATA_FOLLOWING, c.following);
        c.protecting = tag.getBoolean("Protecting");
        c.getEntityData().set(CompanionEntity.DATA_PROTECTING, c.protecting);
        c.gatheringLogs = tag.getBoolean("GatheringLogs");
        c.gatheringDirt = tag.getBoolean("GatheringDirt");
        c.getEntityData().set(CompanionEntity.DATA_GATHERING_LOGS, c.gatheringLogs || c.gatheringDirt);
        c.logsRemaining = Math.max(0, Math.min(64, tag.getInt("LogsRemaining")));
        c.getEntityData().set(CompanionEntity.DATA_LOGS_REMAINING, c.logsRemaining);
        c.requestedLogs = Math.max(0, Math.min(64, tag.getInt("RequestedLogs")));
        c.satiety = Math.max(0, Math.min(40, tag.getInt("Satiety"))); // absent in older saves: 0
        ContainerHelper.loadAllItems(tag, c.companionInventory, c.level().registryAccess());
        c.deeds.load(tag); c.places.load(tag); c.autonomy.load(tag); c.refreshPlaces();
        c.social.load(tag);
        c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, tag.getString("LastTaskResult"));
        c.refreshInventorySummary();
        if (c.protecting || c.roaming) c.following = false;
        if (c.gatheringLogs || c.gatheringDirt) {
            c.following = false;
            c.protecting = false;
        }
        c.getEntityData().set(CompanionEntity.DATA_FOLLOWING, c.following);
        c.getEntityData().set(CompanionEntity.DATA_PROTECTING, c.protecting);
        c.getEntityData().set(CompanionEntity.DATA_GATHERING_LOGS, c.gatheringLogs || c.gatheringDirt);
        c.temporaryPillar.load(tag);
        c.work.load(tag);
        c.setCanPickUpLoot(false);
        if (c.temporaryPillar.isRecovering() || tag.getBoolean("WorkActive") || tag.getBoolean("SupportSoilTask")) {
            c.setFollowing(true);
            c.lastTaskSucceeded = false;
            c.getEntityData().set(CompanionEntity.DATA_LAST_TASK_RESULT, c.temporaryPillar.isRecovering()
                    ? "Добыча была прервана выходом из мира. Сначала спущусь и уберу свою опору."
                    : "Работа прервалась выходом из мира. Вещи остались у меня.");
            // Pickup/navigation statuses soon replace the visible line. Keep the interruption in saved factual memory too.
            c.noteDeed(c.getEntityData().get(CompanionEntity.DATA_LAST_TASK_RESULT), false);
        }
        // His own harvest still lying round him (it carries his UUID) is picked up after the world is entered again, like a player
        // who comes back to the tree he was cutting (marathon 23.10: a log stayed on the ground after the reload).
        if (c.following || tag.getBoolean("WorkActive") || c.gatheringLogs) c.loot.resumeCollection();
        c.goals.load(tag);
        // Interrupted gathering belongs to the outcome now: old remaining counts are not a new order.
        if (c.goals.outstanding()) {
            boolean savedFollowing = tag.getBoolean("Following"), savedProtecting = tag.getBoolean("Protecting");
            c.setFollowing(savedFollowing);
            if (savedProtecting) c.setProtecting(true);
            c.goals.restorePlayerBehavior();
        }
        if (!c.level().isClientSide) c.social.updateExpression();
    }

    /** What the companion who comes back remembers: name, settings, journal of deeds, places, the house and the chest. */
    CompoundTag memory() {
        CompoundTag full = new CompoundTag(), memory = new CompoundTag();
        c.addAdditionalSaveData(full);
        for (String key : new String[]{"CompanionName", "SearchRadius", "ChunkLoading", "AutonomousSpeech", "KnownPlaces", "Deeds",
                "SmallHouse", "StorageChest", "StorageDimension", "Autonomy", CompanionGoals.KEY}) if (full.contains(key)) memory.put(key, full.get(key).copy());
        return memory;
    }

    void inheritMemory(CompoundTag memory) {
        if (memory.contains("CompanionName")) c.setCompanionName(memory.getString("CompanionName"));
        if (memory.contains("SearchRadius")) c.setSearchRadius(memory.getInt("SearchRadius"));
        c.chunkLoading = !memory.contains("ChunkLoading") || memory.getBoolean("ChunkLoading");
        c.autonomousSpeech = !memory.contains("AutonomousSpeech") || memory.getBoolean("AutonomousSpeech");
        c.deeds.load(memory); c.places.load(memory); c.work.load(memory); c.autonomy.load(memory); c.refreshPlaces();
        c.goals.load(memory);
        c.noteDeed("Вернулся к тебе после гибели. Вещи, что были при мне, остались там, где я погиб.", true);
    }
}
